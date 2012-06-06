/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.dimm.vsm.client.cdp;

import de.dimm.vsm.client.Main;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.net.interfaces.ServerApi;
import java.util.ArrayList;
import sun.awt.Mutex;

/**
 *
 * @author Administrator
 */
public class Callbacks
{

    // SYNC PARAM
    Mutex queue_mtx;
    ArrayList<CDP_Entry> cdp_file_queue_root;
    ArrayList<CDP_Entry> cdp_dir_queue_root;
    boolean queues_exhausted;
    int event_counter;
    boolean close_after_call;
    CDP_Param cdp_param;
    int MIN_QUEUE_ENTRY_AGE;
    int MAX_QUEUE_LEN;

    public Callbacks( int _min_queue_entry_age, int _max_queue_len, CDP_Param _param )
    {
        cdp_param = _param;
        close_after_call = true;
        queues_exhausted = true;

        cdp_dir_queue_root = new ArrayList<CDP_Entry>();
        cdp_file_queue_root = new ArrayList<CDP_Entry>();
        queue_mtx = new Mutex();

        MIN_QUEUE_ENTRY_AGE = _min_queue_entry_age;
        MAX_QUEUE_LEN = _max_queue_len;
    }

    public void close()
    {

        cdp_file_queue_root.clear();
        cdp_dir_queue_root.clear();

    }

//    public void idle()
//    {
//        long now = System.currentTimeMillis();
//
//        // DIRECTORY QUEUE
//        CDP_Entry entry = get_next_valid_entry(cdp_dir_queue_root, now);
//
//        while (entry != null && !cdp_param.wants_finish())
//        {
//            boolean ret = work_change_entry(entry, true);
//
//            // NOT OKAY?
//            if (!ret)
//                break;
//
//            queue_mtx.lock();
//            cdp_dir_queue_root.remove(entry);
//            queue_mtx.unlock();
//
//            entry = get_next_valid_entry(cdp_dir_queue_root, now);
//        }
//
//        // FILE QUEUE
//        entry = get_next_valid_entry(cdp_file_queue_root, now);
//
//        while (entry != null && !cdp_param.wants_finish())
//        {
//            boolean ret = work_change_entry(entry, false);
//
//            // NOT OKAY?
//            if (!ret)
//                break;
//
//            queue_mtx.lock();
//            cdp_file_queue_root.remove(entry);
//            queue_mtx.unlock();
//
//            entry = get_next_valid_entry(cdp_file_queue_root, now);
//        }
//    }

    void delete_queue( ArrayList<CDP_Entry> cdp_queue_root )
    {
        queue_mtx.lock();
        cdp_queue_root.clear();
        queue_mtx.unlock();
    }

    int add_to_queue( ArrayList<CDP_Entry> cdp_queue_root, RemoteFSElem path, int flags, long timecode, long now,
            boolean queue_is_dir, boolean entry_is_dir )
    {
        int ret = 0;

        queue_mtx.lock();
        
        // ADD ONLY MATCHING ENTRIES TO QUEUE
        if (cdp_queue_root.isEmpty() && queue_is_dir == entry_is_dir)
        {
            CDP_Entry entry = new CDP_Entry( path, flags, timecode);
            entry.setLast_touched( now );
            cdp_queue_root.add(entry);
        }
        else
        {
            for (int i = 0; i < cdp_queue_root.size(); i++)
            {
                CDP_Entry p = cdp_queue_root.get(i);

                // IS IN QUEUE ALREADY?

                // NOT HANDLED?
                if (ret == 0)
                {
                    // IS SUBDIR
                    // THEN WE NEED FULL RECURSIVE
                    // SET RECURSIVE IF THIS IS NOT A NEW DIRECTORY
                    if (queue_is_dir && (p.getFlags() & CDP_Entry.CDP_CREATED) != 0)
                    {
                        // WE ARE SUBDIR, IGNORE, THIS IS HANDLED BY SYNC
                        if (path.getPath().startsWith(p.getPath().getPath()))
                        {
                            // WILL BE HANDLED IN SYNC AUTOMATICALLY
                            ret = CDP_Entry.IS_IN_QUEUE;
                        }
                    }
                    // IF WE HAVE A PARENT DIR TO DELETE,
                    if (queue_is_dir && (flags & CDP_Entry.CDP_DELETED) != 0)
                    {
                        // WE ARE PARENT DIR, THIS IS HANDLED BY SYNC
                        if (p.getPath().getPath().startsWith(path.getPath()))
                        {
                            p.setPath(path);
                            p.setFlag(CDP_Entry.CDP_DELETED);
                            // WILL BE HANDLED IN SYNC AUTOMATICALLY
                            ret = CDP_Entry.IS_IN_QUEUE;
                        }
                    }
                }

                // NOT HANDLED?
                if (ret == 0)
                {
                    // FILE COMPARE
                    if (p.getPath().getPath().equals(path.getPath()))
                    {
                        ret = CDP_Entry.IS_IN_QUEUE;
                    }
                }

                if (ret == CDP_Entry.IS_IN_QUEUE)
                {
                    // SET TIMESTAMP
                    p.setLast_touched( now );

                    // AND LEAVE, WE ARE DONE
                    break;
                }


                // QUEUE IS FULL ?
                if (i >= MAX_QUEUE_LEN)
                {
                    ret = CDP_Entry.QUEUE_IS_FULL;
                    break;
                }
            }

            // OKAY, ADD TO QUUE
            if (ret == 0)
            {
                // ADD ONLY MATCHING ENTRIES TO QUEUE
                if (queue_is_dir == entry_is_dir)
                {
                    CDP_Entry entry = new CDP_Entry(path, flags, timecode);
                    entry.setLast_touched( now );
                    cdp_queue_root.add(entry);
                }
            }
        }

        queue_mtx.unlock();

        return ret;
    }

    CDP_Entry get_next_valid_entry( ArrayList<CDP_Entry> cdp_queue_root, long now )
    {
        CDP_Entry ret = null;

        queue_mtx.lock();

        if (cdp_queue_root.isEmpty())
        {
            ret = null;
        }
        else
        {
            for (int i = 0; i < cdp_queue_root.size(); i++)
            {
                CDP_Entry p = cdp_queue_root.get(i);

                // OLD ENOUGH TO BE HANDLED?
                if ((now - p.getLast_touched()) > MIN_QUEUE_ENTRY_AGE)
                {
                    ret = cdp_queue_root.remove(i);
                    break;
                }
            }
        }

        queue_mtx.unlock();

        return ret;
    }

// EXTERNAL ENTRYPOINT FROM PLATFORM SPECIFIC CDP-CALLBACKS
    public int dir_change( RemoteFSElem path, String name, int flags, long timecode )
    {
        long now = System.currentTimeMillis();

        if (WinCdpHandler.check_excluded(cdp_param.getExcludeList(), path, name))
        {
            // IS IN QUEUE, TS OF DIR WAS TOUCHED, WE ARE SYNCING RECUSIVE, OKAY NOTHING MORE TO DO;
            cdp_log("CDP excluded " + path);
            return 0;
        }

        cdp_log("CDP tryadd " + path);

        // CHECK IF PARENT IS IN DIR_QUEUE
        int ret = add_to_queue(cdp_dir_queue_root, path, flags, timecode, now, /*queue_is_dir*/ true, /* ENTRY IS_DIR*/ true);
        if (ret == CDP_Entry.IS_IN_QUEUE)
        {
            // IS IN QUEUE, TS OF DIR WAS TOUCHED, WE ARE SYNCING RECUSIVE, OKAY NOTHING MORE TO DO;
            cdp_log("CDP skipped " + path);
            return 0;
        }

        if (ret == CDP_Entry.QUEUE_IS_FULL)
        {
            cdp_log("CDP dir queue is exhausted");
            queues_exhausted = true;
        }

        return ret;
    }

    public int file_change( RemoteFSElem path, String name, int flags, long timecode )
    {
        // TODO: CHECK IF WE HAVE PARENT DIR ALREADY IN DIR QUEUE
        long now = System.currentTimeMillis();

        if (WinCdpHandler.check_excluded(cdp_param.getExcludeList(), path, name))
        {
            // IS IN QUEUE, TS OF DIR WAS TOUCHED, WE ARE SYNCING RECUSIVE, OKAY NOTHING MORE TO DO;
            cdp_log("CDP excluded " + path);
            return 0;
        }

        cdp_log("CDP tryadd " + path);

        // CHECK IF PARENT IS IN DIR_QUEUE
        int ret = add_to_queue(cdp_file_queue_root, path, flags, timecode, now, /*queue_is_dir*/ false, /* ENTRY IS_DIR*/ false);
        if (ret == CDP_Entry.IS_IN_QUEUE)
        {
            // IS IN QUEUE, TS OF DIR WAS TOUCHED, WE ARE SYNCING RECUSIVE, OKAY NOTHING MORE TO DO;
            cdp_log("CDP skipped " + path);
            return 0;
        }

        if (ret == CDP_Entry.QUEUE_IS_FULL)
        {
            cdp_log("CDP file_queue is exhausted");
            queues_exhausted = true;
        }

        return ret;
    }
//
//    boolean work_change_entry( CDP_Entry entry, boolean is_dir )
//    {
//        boolean ret = false;
//
//        String mode = (is_dir) ? "FOLDER" : "FILE";
//
//       /* if ((entry.getFlags() & CDP_Entry.CDP_DELETED) != 0)
//        {
//            File f = new File(entry.getPath().getPath());
//            if (!f.exists())
//            {
//                cdp_log("Skipping deleted object " + entry.getPath());
//                return true;
//            }
//        }*/
//
//        cdp_log("CDP work   " + entry.getPath());
//
//        // REQUEST SERVER CONNET
//        ServerApi api = Main.getServerConn().getServerApi(cdp_param.getServer(),cdp_param.getPort(), cdp_param.isSsl(), cdp_param.isTcp());
//
//        // AND SEND CALL
//        ret = api.cdp_call( null, cdp_param.getTicket());
//        if (!ret)
//        {
//            cdp_log("Lost cdp file_change, cannot send to Server");
//            // TODO: SAVE FOR LATER EVAL IN BUFFER AND RETURN 0
//            return ret;
//        }
//
//        return ret;
//    }

    private void cdp_log( String string )
    {
        System.out.println(string);
    }

   
}
