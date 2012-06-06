/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.dimm.vsm.client.cdp;

import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.WinBase.OVERLAPPED;
import com.sun.jna.platform.win32.WinNT.FILE_NOTIFY_INFORMATION;
import de.dimm.vsm.Utilities.WinFileUtilities;
import de.dimm.vsm.client.jna.LibKernel32;
import de.dimm.vsm.net.RemoteFSElem;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import de.dimm.vsm.client.win.WinRemoteFSElemFactory;
import de.dimm.vsm.net.CdpTicket;
import de.dimm.vsm.net.interfaces.CDPEventProcessor;
import de.dimm.vsm.records.FileSystemElemNode;
import java.io.File;
import java.net.SocketException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;




/**
 *
 * @author Administrator
 */
public class WinCdpHandler extends CdpHandler implements FCEEventSource
{
    Thread idle_thr;

    // WIN_API:
    public static final int FILE_ACTION_ADDED = 0x0001;
    public static final int FILE_ACTION_REMOVED = 0x00000002;
    public static final int FILE_ACTION_MODIFIED = 0x00000003;
    public static final int FILE_ACTION_RENAMED_OLD_NAME = 0x00000004;
    public static final int FILE_ACTION_RENAMED_NEW_NAME = 0x00000005;

    HANDLE hDir;
    HANDLE hCompPort;
    WString lpszDirName;
    int filter;
    //Memory mem;
    IntByReference readLength;
    WinNT.FILE_NOTIFY_INFORMATION info;
    WinBase.OVERLAPPED overlapped;

    public WinCdpHandler( CDP_Param cdp, CDPEventProcessor eventProcessor)
    {
        super(cdp,  null, eventProcessor);
       
        this.eventSource = this;
    }


    @Override
    public boolean init_cdp( )
    {

        factory = new WinRemoteFSElemFactory();

        WString object_name = new WString(cdp_param.getPath().getPath());

        // Get a handle to the directory
        hDir = LibKernel32.CreateFile(object_name,
                LibKernel32.FILE_LIST_DIRECTORY,
                LibKernel32.FILE_SHARE_READ
                | LibKernel32.FILE_SHARE_WRITE
                | LibKernel32.FILE_SHARE_DELETE,
                null,
                LibKernel32.OPEN_EXISTING,
                LibKernel32.FILE_FLAG_BACKUP_SEMANTICS
                | LibKernel32.FILE_FLAG_OVERLAPPED,
                null);

        if (LibKernel32.isInvalidHandleValue(hDir))
        {
            cdp_param.set_error( "Unable to open directory");
            return false;
        }

        lpszDirName = object_name;


        // Set up a key(directory info) for each directory
        Pointer commandState = Pointer.createConstant(1);

        hCompPort = Kernel32.INSTANCE.CreateIoCompletionPort(hDir, null, commandState, 0);

        if (LibKernel32.isInvalidHandleValue(hCompPort))
        {
            cdp_param.set_error( "Unable to open completion port");
            return false;
        }

        

        // Start watching each of the directories of interest
        filter = LibKernel32.FILE_NOTIFY_CHANGE_CREATION
                | LibKernel32.FILE_NOTIFY_CHANGE_LAST_WRITE
                | LibKernel32.FILE_NOTIFY_CHANGE_FILE_NAME
                | LibKernel32.FILE_NOTIFY_CHANGE_DIR_NAME
                | LibKernel32.FILE_NOTIFY_CHANGE_LAST_WRITE
                | LibKernel32.FILE_NOTIFY_CHANGE_SIZE;


        
        readLength = new IntByReference(0);
        info = new FILE_NOTIFY_INFORMATION(4096);
        overlapped = new OVERLAPPED();

        boolean ret = Kernel32.INSTANCE.ReadDirectoryChangesW(hDir, // HANDLE TO DIRECTORY
                info, // Formatted buffer into which read results are returned.  This is a
                info.size(), // Length of previous parameter, in bytes
                true, // Monitor sub trees?
                filter, // What we are watching for
                readLength, // Number of bytes returned into second parameter
                overlapped, // OVERLAPPED structure that supplies data to be used during an asynchronous operation.  If this is NULL, ReadDirectoryChangesW does not return immediately.
                null);                           // Completion routine

        if (!ret)
        {
            cdp_param.set_error( "Unable to open ReadDirectoryChangesW");
            return false;
        }

        try
        {
            start_cdp();
        }
        catch (Exception exc)
        {
            cdp_param.set_error( "Unable to open start cdp:" + exc.getMessage());
            return false;
        }
        
        return ret;
    }

    @Override
    public void start_cdp() throws SocketException
    {
         // Create a thread to sit on the directory changes
        idle_thr = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                watch_directory();
                
            }
        }, "WinCDPWatchDirectory");
        idle_thr.start();

        super.start_cdp();
    }

    



    void change_callback()
    {
        IntByReference numBytes = new IntByReference();
        PointerByReference commandState = new PointerByReference();        

        PointerByReference overlapdata = new PointerByReference();
        WinPlatformData wpd = (WinPlatformData)cdp_param.getPlatformData();

        int eventId = 0;

        do
        {
            // Retrieve the directory info for this directory
            // through the completion key
            boolean ook = Kernel32.INSTANCE.GetQueuedCompletionStatus(hCompPort,
                    numBytes,
                    commandState, // This is the structure that was passed in the call to CreateIoCompletionPort below.
                    overlapdata,
                    -1 /* infinite*/);


            if (abort || cdp_param.wants_finish())
                break;

            eventId++;

            
            do
            {
                String object_name = info.getFilename();
                int action = info.Action;
                

                String fullpath = cdp_param.getPath().getPath();

                if (!object_name.isEmpty())
                {
                    if (fullpath.endsWith("\\"))
                        fullpath += object_name;
                    else
                        fullpath += "\\" + object_name;
                

                    // BUILD REMOTEFSELEM FOR THIS ENTRY
                    RemoteFSElem elem = null;
                    File f = new File(fullpath);
                    if (f.exists())
                    {
                        elem = factory.create_elem(f, true);
                    }
                    else
                    {
                        // ALREADY DELETED? THEN JUST A PLACEHOLDER WITHOUT STATS
                        elem = new RemoteFSElem(fullpath, FileSystemElemNode.FT_FILE, 0, 0, 0, 0, 0);
                    }


                    try
                    {
                        // CHECK PATH
                        if (!check_invalid_cdp_path(elem, object_name))
                        {
                            cdp_log("CDP new    :" + object_name + " fni:" + action);

                            byte mode = getFceModeFromWinAPI( f, info );
                            queue.add( new FceEvent(null, (byte)0xff, mode, eventId, fullpath.getBytes()));
                            //handle_change(elem, object_name, action);
                        }
                    }
                    catch (Exception e)
                    {
                        cdp_log("Ouch: " + e.getMessage());
                        e.printStackTrace(System.err);
                    }
                }
                try
                {
                    info = info.next();
                }
                catch (Exception e)
                {
                    cdp_log("Ouch: " + e.getMessage());
                }

            }
            while (info != null);

            // Reissue the watch command
            info = new FILE_NOTIFY_INFORMATION(4096);
            boolean ok = Kernel32.INSTANCE.ReadDirectoryChangesW(hDir, info,
                    info.size(),
                    true,
                    filter,
                    readLength,
                    overlapped,
                    null);

            // IF WE GET A ZERO PACKAGE, WARE DONE
        }
        while (!abort);
    }


    public boolean watch_directory()
    {      
        // Create a thread to sit on the directory changes
        Thread thr = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                change_callback();
            }
        });
        thr.start();


        // Just loop and wait for finish
        while (!abort && !cdp_param.wants_finish())
        {
            try
            {
                Thread.sleep(100);
            }
            catch (InterruptedException interruptedException)
            {
            }
            //cdp_param.getCallbacks().idle();
        }

        // CLEAN UP, SEND A NULL PACKET
        Pointer commandState = Pointer.createConstant(0);

        
        Kernel32.INSTANCE.PostQueuedCompletionStatus(hCompPort, 0, commandState, null);

        // Wait for the Directory thread to finish before exiting
        try
        {
            thr.join();
        }
        catch (InterruptedException interruptedException)
        {
        }
        

        return true;
    }

    @Override
    public void cleanup_cdp()
    {

    }

    // THIS IS JUST A BUFFER BETWEEN PRODUCER (US) AND CONSUMER (OUR EVENTSOURCE)
    ArrayBlockingQueue<FceEvent> queue = new ArrayBlockingQueue<FceEvent>(10000);

    @Override
    public FceEvent acceptEvent(CdpTicket ticket)
    {
        try
        {
            return queue.poll(1000, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException interruptedException)
        {
        }
        return null;
    }

    @Override
    public void close(CdpTicket ticket)
    {
        
    }

    @Override
    public void open(CDP_Param ticket) throws SocketException
    {
        
    }


    // TODO: RENAME FILE
    private byte getFceModeFromWinAPI( File file, FILE_NOTIFY_INFORMATION info )
    {
        switch (info.Action)
        {
            case FILE_ACTION_ADDED: return file.isDirectory() ? FceEvent.FCE_DIR_CREATE : FceEvent.FCE_FILE_CREATE;
            case FILE_ACTION_MODIFIED: return FceEvent.FCE_FILE_MODIFY;
            case FILE_ACTION_REMOVED:
            {
                if (file.exists())
                {
                    return file.isDirectory() ? FceEvent.FCE_DIR_DELETE : FceEvent.FCE_FILE_DELETE;
                }
                return FceEvent.FCE_FILE_DELETE;
            }
        }
        return FceEvent.FCE_FILE_MODIFY;
    }

    @Override
    boolean check_invalid_cdp_path( RemoteFSElem fullpath, String object_name )
    {
        String[] roots = cdp_param.getPlatformData().getSkipRoots();
        int idx = WinFileUtilities.getFirstDirOffset( fullpath.getPath() );
        if (idx > 0)
        {
            String rootdir = fullpath.getPath().substring(idx);
            for (int i = 0; i < roots.length; i++)
            {
                if (rootdir.startsWith(roots[i]))
                    return true;
            }
        }

        return false;
    }

    

}
