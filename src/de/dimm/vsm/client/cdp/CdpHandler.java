/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.cdp;

import de.dimm.vsm.Utilities.WinFileUtilities;
import de.dimm.vsm.client.RemoteFSElemFactory;
import de.dimm.vsm.client.cdp.fce.FCEEventBuffer;
import de.dimm.vsm.client.unix.NetatalkRemoteFSElemFactory;
import de.dimm.vsm.net.CdpEvent;
import de.dimm.vsm.net.ExclListEntry;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.net.interfaces.CDPEventProcessor;
import de.dimm.vsm.records.FileSystemElemNode;
import java.io.File;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;


/*
 *
 * Event Coalescing:
 *
 *
 * In worklist are only directories, we call a sync for each directory in worklist if it has no activity for more
 * than FCE_SETTLE_MS
 *
 * Rule 1:
 * If we have a single Directory Creation FCE Event
 *   Add FCE-Event Sync-Dir-Recursive and delete all existing Events under this path
 *
 * Rule 2:
 * If we have a single Directory Delete FCE Event
 *   Add FCE-Event Delete-Dir-Recursive and delete all existing Events under this path
 *
 * Rule 3:
 * If we have a single FCE Event (File/Dir) and no pending Events in worklist
 *   Add FCE-Event SyncDir for parent and kick actual Event
 *
 * Rule 4:
 * If we have a single FCE Event (File/Dir) and we find Parent SyncDir in worklist
 *   Kick actual Event
 *
 * Rule 5:
 * If we have a single FCE Event (File/Dir) and we find a Recursive-Parent Dir in out Path
 *   Kick actual Event
 *
 * Rule 6:
 * If we have a single FCE Event (File/Dir) and no other Rule fires
 *   Add FCE-Event SyncDir for parent and kick actual Event

 * Rule 7:
 * If Queue goes into overrun
 *   Add FCE-Event FullSync, clear working queue and kick actual Event
 *
 * Rule 7:
 * If Queue goes into overrun
 *   Add FCE-Event FullSync, clear working queue and kick actual Event
 *
 * Rule 8:
 * If we have a single FCE Event (File/Dir) and the one and only entry in Workqueue is FullSync
 *   kick actual Event
 *

 *
 */

/**
 *
 * @author Administrator
 */
public abstract class CdpHandler  implements Runnable
{
    CDP_Param cdp_param;

    

    protected boolean abort;
    protected boolean finished;

    
    RemoteFSElemFactory factory;
    CDPEventProcessor eventProcessor;
    FCEEventSource eventSource;

    FCEEventBuffer eventBuffer;

   

    private static final int FCE_POLL_MS = 1000;
    private static final int FCE_SETTLE_MS = 10000;
    private static final int FCE_FULLSYNCSETTLE_MS = 3600*1000;  // DELAY FOR CREATION IF FULLSYNC
    private static final int MAX_FCE_EVENTS = 100000;
    private static final int MAX_FCE_WORK_WAIT = 30000; // AT LEAST EVERY N s WE CHECK EVENTLOOP




    final ArrayBlockingQueue<FceEvent> fce_event_list;
    final LinkedList<FceEvent> fce_copy_list;
    final LinkedList<CdpEvent> cdp_work_list;



    public void cdp_log( String string )
    {
        System.out.println(string);
    }
    public void cdp_dbg( String string )
    {
        System.out.println(string);
    }
    public abstract boolean init_cdp();
   

    public void cleanup_cdp()
    {
    }

    public boolean isFinished()
    {
        return finished;
    }

    public CDP_Param getCdp_param()
    {
        return cdp_param;
    }
    
   
    public CdpHandler( CDP_Param p, FCEEventSource eventSource, CDPEventProcessor eventProcessor)
    {
        this.cdp_param = p;
        
        this.eventProcessor = eventProcessor;
        this.eventSource = eventSource;

        fce_event_list = new ArrayBlockingQueue<FceEvent>(MAX_FCE_EVENTS, /*fair*/true);
        cdp_work_list = new LinkedList<CdpEvent>();
        fce_copy_list = new LinkedList<FceEvent>();

        eventBuffer = new FCEEventBuffer(cdp_param, eventProcessor);

        factory = new NetatalkRemoteFSElemFactory();

    }

//
//    // ENTRY FOR ALL PLATFORMS
//    void handle_change( RemoteFSElem fs_elem, String object_name,  int action )
//    {
//        if (action == CDP_Entry.CDP_NOTHING)
//        {
//            return;
//        }
//
//        if (action != CDP_Entry.CDP_DELETED && fs_elem.isDirectory())
//        {
//            cdp_param.getCallbacks().dir_change(fs_elem, object_name, action, 0);
//        }
//        else // ??????? IMPROVE HANDLING FOR WIN
//        {
//           cdp_param.getCallbacks().dir_change(fs_elem, object_name, action, 0);
//        }
//    }
    abstract boolean check_invalid_cdp_path( RemoteFSElem fullpath, String object_name );



    public static boolean check_excluded(  ArrayList<ExclListEntry> excl_list, RemoteFSElem path, String name )
    {
        if (excl_list == null || excl_list.isEmpty())
        {
            return false;
        }
        boolean ret = false;

        // IF DIR WE ALWAYS HANDLE FULL PATH,
        for (int i = 0; i < excl_list.size(); i++)
        {
            ExclListEntry entry = excl_list.get(i);

            if (entry.matches( path, name ))
            {
                ret = true;
                break;
            }
        }
        return ret;
    }


    protected void startFCEListener()
    {
        Thread thr = new Thread( this, "FCEListener");
        thr.start();
    }


    public void start_cdp() throws SocketException
    {

        eventSource.open(cdp_param);
        abort = false;
        finished = false;

        startFCEListener();

        Thread _thr = new Thread( new Runnable()
        {
            @Override
            public void run()
            {
                long next_buffer_check = System.currentTimeMillis();
                long next_event_work = System.currentTimeMillis() + MAX_FCE_WORK_WAIT;

                while (!abort)
                {
                    long now = System.currentTimeMillis();

                    try
                    {
                        FceEvent ev = null;
                        // THIS IS THE CONSUMER OF PENDING FCE-EVENTS
                        ev = fce_event_list.poll(FCE_POLL_MS, TimeUnit.MILLISECONDS);
                        if (ev != null)
                        {
                            synchronized(fce_event_list)
                            {
                                fce_copy_list.clear();
                                fce_event_list.drainTo(fce_copy_list);
                                // DO NOT FOGET POLLED EVENT
                                fce_copy_list.addFirst(ev);
                            }

                            coalesceEvents( fce_copy_list, cdp_work_list );

                            // THIS ONE IS HANDLED IF EVENTS ARE TOO BUSY FOR POLL TIMEOUT
                            if (next_event_work <= now)
                            {
                                workEvents(cdp_work_list);
                                next_event_work = now + MAX_FCE_WORK_WAIT;
                            }
                        }
                        else
                        {
                            // THIS ONE IS HANDLED AFTER FCE_POLL_MS WITHOUT EVENTS
                            workEvents(cdp_work_list);
                            next_event_work = now + MAX_FCE_WORK_WAIT;
                        }


                        // TRY TO RESOLVE BUFFER EVERY 10 s
                        if (now > next_buffer_check)
                        {
                            next_buffer_check = now + 10000;

                            if (!eventBuffer.check_buffered_events())
                            {
                                // ERROR RESOLVING BUFFERED EVENTS, WAIT A MINUTE
                                next_buffer_check = now + 60000;
                            }
                        }
                    }
                    catch (Exception exc)
                    {
                        exc.printStackTrace();
                    }
                }
                finished = true;
            }
        }, "FCEWorker");

        _thr.start();
    }



    public void stop_cdp()
    {
        abort = true;
        eventSource.close(cdp_param.getTicket());

        // WAIT 5 SECS
        int wait_s = 500;
        while(!finished && wait_s > 0)
        {
            try
            {
                Thread.sleep(100);
            }
            catch (InterruptedException interruptedException)
            {
            }
        }
    }


    public void coalesceEvents(  LinkedList<FceEvent> newList,  LinkedList<CdpEvent> workList )
    {
        Iterator<FceEvent> it = newList.listIterator();
        while (it.hasNext())
        {
            FceEvent ev = it.next();

            try
            {
                coalesceEvent(ev, workList);
            }
            catch (Exception e)
            {
                cdp_log("Cannot coalesce event " + ev.toString() + ": " + e.toString());e.printStackTrace();
            }
        }
    }


    RemoteFSElem fromPath( String path, boolean isDir )
    {
        RemoteFSElem elem = null;
        File f = new File( path );
        if (f.exists())
        {
            elem = factory.create_elem(f, true);
        }
        else
        {
            cdp_dbg("Path " + path + " is not existent" );
            long now = System.currentTimeMillis();
            elem = new RemoteFSElem(path, isDir ? FileSystemElemNode.FT_DIR : FileSystemElemNode.FT_FILE, now, now, now, 0, 0);
        }
        return elem;
    }

    public void coalesceEvent(  FceEvent ev,  LinkedList<CdpEvent> workList )
    {
        // RULE 7:
        if (ev.mode == FceEvent.FCE_OVERFLOW)
        {
            cdp_dbg("Coalescing "  + ev.toString() + " Reason: overflow");
            workList.clear();
            workList.add(CdpEvent.createFullSyncRecursiveEvent(ev.client));
            return;
        }

        // RULE 8:
        if (workList.size() == 1 && workList.get(0).getMode() == CdpEvent.CDP_FULLSYNC)
        {
            cdp_dbg("Coalescing "  + ev.toString() + " Reason: Fullsync");
            workList.get(0).touch();
            return;
        }
        boolean isDir = ev.mode == FceEvent.FCE_DIR_CREATE  || ev.mode == FceEvent.FCE_DIR_DELETE;

        // DETECT IF WE HAVE A DELETED PARENT DIR
        boolean deleted = false;
        String path = ev.getPath();
        if (!new File(path).exists())
        {
            deleted = true;
        }

        RemoteFSElem elem = fromPath( ev.getPath(), isDir);

        // HACK, WE COULD BE A DIRECTORY, SO HANDLE ALL CHILDS
        if (netatalk_fce_has_no_del_dir() && ev.mode == FceEvent.FCE_FILE_MODIFY && deleted)
        {
            ev.setMode(FceEvent.FCE_DIR_DELETE);

            while (path.length() > 1 && deleted)
            {
                ev.setPath(path);
                path = ev.getParentPath();
                deleted = !new File(path).exists();
            }
        }


        if (check_invalid_cdp_path(elem, ev.getName() ))
        {
            cdp_dbg("Coalescing "  + ev.toString() + " Reason: invalid cdp path");
            return;
        }

        // RULE 1 & 2: LOOSE ALL CHILDREN
        if (ev.mode == FceEvent.FCE_DIR_CREATE || ev.mode == FceEvent.FCE_DIR_DELETE )
        {
            for (int i = 0; i < workList.size(); i++)
            {
                CdpEvent cdpEvent = workList.get(i);
                if (cdpEvent.isChildof( ev.getPath() ))
                {
                    workList.remove(cdpEvent);
                    cdp_dbg("Coalescing "  + cdpEvent.toString() + " Reason: is child of recursive sync/delete");
                    i--;
                }
                else if (cdpEvent.getMode() == CdpEvent.CDP_DELETE_DIR_RECURSIVE || cdpEvent.getMode() == CdpEvent.CDP_SYNC_DIR_RECURSIVE)
                {
                    // RULE 5
                    if (cdpEvent.isParentof(ev.getPath()))
                    {
                        cdp_dbg("Coalescing "  + ev.toString() + " Reason: Child of recursive sync");
                        cdpEvent.touch();
                        return;
                    }
                }
            }

            // IN CASE OF DIR WE SYNC PARENT DIR ONLY, THIS IS SUFFICIENT
            String ppath = ev.getParentPath();
            elem = fromPath( ppath, true);
            CdpEvent parentDirEvent = CdpEvent.createParentDirEvent(ev.client, ppath, elem);
            addSingularEvent( workList, parentDirEvent);

            return;
        }
        if (workList.isEmpty())
        {
            //Rule 3:
            String ppath = ev.getParentPath();
            elem = fromPath( ppath, true);
            CdpEvent parentDirEvent = CdpEvent.createParentDirEvent(ev.client, ppath, elem);
            addSingularEvent( workList, parentDirEvent);
            return;
        }


        for (int i = 0; i < workList.size(); i++)
        {
            CdpEvent cdpEvent = workList.get(i);

            // SKIP ALL RECURSIVE CHILDREN, FCE IN NETATALK DOESNT PROVIDE DELETE DIR
            if (netatalk_fce_has_no_del_dir())
            {
                // RULE 4:
                if (cdpEvent.getMode() == CdpEvent.CDP_SYNC_DIR && cdpEvent.isParentof(ev.getPath()))
                {
                    cdp_dbg("Coalescing "  + ev.toString() + " Reason: Direct parent has sync");
                    cdpEvent.touch();
                    return;
                }
            }
            else
            {
                // RULE 4:
                if (cdpEvent.getMode() == CdpEvent.CDP_SYNC_DIR && cdpEvent.isDirectParentof(ev.getPath()))
                {
                    cdp_dbg("Coalescing "  + ev.toString() + " Reason: Direct parent has sync");
                    cdpEvent.touch();
                    return;
                }
            }

            // RULE 5:
            if (cdpEvent.getMode() == CdpEvent.CDP_DELETE_DIR_RECURSIVE || cdpEvent.getMode() == CdpEvent.CDP_SYNC_DIR_RECURSIVE)
            {
                if (cdpEvent.isParentof(ev.getPath()))
                {
                    cdp_dbg("Coalescing "  + ev.toString() + " Reason: Parent has sync");
                    cdpEvent.touch();
                    return;
                }
            }
        }

        // rule 6:        
        String ppath = ev.getParentPath();
        elem = fromPath( ppath, true);
        CdpEvent parentDirEvent = CdpEvent.createParentDirEvent(ev.client, ppath, elem);
        addSingularEvent( workList, parentDirEvent);
    }
    
    void addSingularEvent( LinkedList<CdpEvent> workList, CdpEvent event)
    {
        // SKIP (AND TOUCH) DOUBLES
        for (int i = 0; i < workList.size(); i++)
        {
            CdpEvent ev = workList.get(i);
            if (ev.getPath().equals( event.getPath()) )
            {
                ev.touch();
                return;
            }
        }
        
        workList.add(event);
    }

    void workEvents(LinkedList<CdpEvent> workList )
    {
        long now = System.currentTimeMillis();

        for (int i = 0; i < workList.size(); i++)
        {
            CdpEvent cdpEvent = workList.get(i);

            // EVENT WASNT TOUCHED SETTLE_TIME?
            if (now - cdpEvent.getLastTouched() > FCE_SETTLE_MS)
            {
                // SPECIAL: FULLSYNC HAS A TIMEOUT of 1h AFTER CREATION
                if (cdpEvent.getMode() == CdpEvent.CDP_FULLSYNC)
                {
                    if ( now - cdpEvent.getCreated() < FCE_FULLSYNCSETTLE_MS)
                        continue;
                }

                workList.remove(cdpEvent);
                i--;
                if (!eventProcessor.process( cdpEvent ))
                {
                    cdp_log("Lost cdp file_change, cannot send to Server");
                    eventBuffer.buffer_event( cdpEvent );
                }
            }
        }
    }


    @Override
    public void run()
    {
        while (!abort)
        {
            try
            {
                FceEvent event = eventSource.acceptEvent(cdp_param.getTicket());
                if (event == null)
                    continue;

                cdp_dbg("New event "  + event.toString());
                
                if (!fce_event_list.offer(event))
                {
                    synchronized( fce_event_list )
                    {
                        System.out.println("FCE-Event Queue Overrun!");

                        fce_event_list.clear();
                        event = new FceEvent( event.client, (byte)0, (byte)FceEvent.FCE_OVERFLOW, (byte)0, (byte[])null);

                        
                        fce_event_list.put( event );
                    }
                }
            }
            catch (Exception ue)
            {
                System.out.println("Exception in FCE-Event Queue:" + ue.getMessage());
            }
        }        
    }

    private boolean netatalk_fce_has_no_del_dir()
    {
        return true;
    }
}