/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2008 Sun
 * Microsystems, Inc. All Rights Reserved.
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 */
package de.dimm.vsm.client.cdp.fce;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import de.dimm.vsm.client.cdp.CDP_Param;
import de.dimm.vsm.client.cdp.FceEvent;
import de.dimm.vsm.net.CdpTicket;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tomas Zezula
 */
public final class FileSystemWatcher extends Notify
{

    private static final Level DEBUG_LOG_LEVEL = Level.FINE;
    private static final Level PERF_LOG_LEVEL = Level.FINE;
    private static final long kFSEventStreamEventIdSinceNow = 0xFFFFFFFFFFFFFFFFL;
    private static final int kFSEventStreamCreateFlagNone = 0;
    private static final int kFSEventStreamCreateFlagNoDefer = 0x00000002;
    private static final int kFSEventStreamEventFlagMustScanSubDirs = 0x00000001;
    private static final int kFSEventStreamCreateFlagFileEvent = 0x10;



    private static final int kFSEventStreamEventFlagUserDropped = 0x0000002;
    private static final int kFSEventStreamEventFlagKernelDropped = 0x0000004;
    private static final int kFSEventStreamEventFlagEventIdsWrapped = 0x0000008;
    private static final int kFSEventStreamEventFlagHistoryDone = 0x00000010;
    private static final int kFSEventStreamEventFlagRootChanged = 0x00000020;
    private static final int kFSEventStreamEventFlagMount = 0x00000040;
    private static final int kFSEventStreamEventFlagUnmount = 0x00000080;
    private static final int kFSEventStreamEventFlagItemCreated       = 0x00000100;
    private static final int kFSEventStreamEventFlagItemRemoved       = 0x00000200;
    private static final int kFSEventStreamEventFlagItemInodeMetaMod  = 0x00000400;
    private static final int kFSEventStreamEventFlagItemRenamed       = 0x00000800;
    private static final int kFSEventStreamEventFlagItemModified      = 0x00001000;
    private static final int kFSEventStreamEventFlagItemFinderInfoMod = 0x00002000;
    private static final int kFSEventStreamEventFlagItemChangeOwner   = 0x00004000;
    private static final int kFSEventStreamEventFlagItemXattrMod      = 0x00008000;
    private static final int kFSEventStreamEventFlagItemIsFile        = 0x00010000;
    private static final int kFSEventStreamEventFlagItemIsDir         = 0x00020000;
    private static final int kFSEventStreamEventFlagItemIsSymlink     = 0x00040000;


    private static final double LATENCY = 1.0f;
    private static final int ENC_MAC_ROMAN = 0;
    private static final String DEFAULT_RUN_LOOP_MODE = "kCFRunLoopDefaultMode";    //NOI18N
    private static final Logger LOG = Logger.getLogger(FileSystemWatcher.class.getName());
    private final CoreFoundation cf;
    private final CoreServices cs;
    private final EventCallback callback;
    private final BlockingQueue<FceEvent> events;
    private final ConcurrentHashMap<String, Key> listensOn;
    //@GuardedBy("this")
    private ExecutorService worker;
    //@GuardedBy("this")
    private Pointer[] rtData;


    static String[] skip_cdp_files =
    {
    ".Desktop",
    ".DeskServer",
    "TheVolumeSettingsFolder",
    ".DS_Store",
    ".Trashes",
    "AppleShare PDS",
    "Desktop DF",
    "Desktop DB",
    "VM Storage"
    };

    static String[] skip_cdp_roots =
    {
    "proc",
    "dev"
    };

    static int eventId = 0;

    private FileSystemWatcher()
    {
        cf = (CoreFoundation) Native.loadLibrary("CoreFoundation", CoreFoundation.class);    //NOI18N
        cs = (CoreServices) Native.loadLibrary("CoreServices", CoreServices.class);          //NOI18N
        callback = new EventCallbackImpl();
        events = new LinkedBlockingQueue<FceEvent>();
        listensOn = new ConcurrentHashMap<String, Key>();
    }

    @Override
    public Key addWatch( CDP_Param param )
    {
        //Mask not supported on MacOS X
        assert param != null;
        assert param.getPath() != null;
        assert param.getPath().getPath() != null;
        String path = param.getPath().getPath();
        
        final KeyImpl key = new KeyImpl(path, param.getTicket());
        listensOn.put(path, key);
        return key;
    }

    @Override
    public FceEvent nextEvent() throws IOException, InterruptedException
    {
        return events.take();
    }

    public synchronized void start() throws IOException, InterruptedException
    {
        if (worker != null)
        {
            throw new IllegalStateException("FileSystemWatcher already started.");  //NOI18N
        }
        worker = Executors.newSingleThreadExecutor(new DaemonThreadFactory());
        final Exchanger<Object> exchanger = new Exchanger<Object>();
        worker.execute(new Runnable()
        {

            @Override
            public void run()
            {
                try
                {
                    Pointer[] _rtData = null;
                    try
                    {
                        _rtData = createFSEventStream();
                    }
                    catch (Throwable ex)
                    {
                        exchanger.exchange(ex);
                    }
                    finally
                    {
                        if (_rtData != null)
                        {
                            exchanger.exchange(_rtData);
                            cf.CFRunLoopRun();
                        }
                    }
                }
                catch (InterruptedException ie)
                {
                    LOG.log(Level.WARNING, "Watcher interruped during start", ie);  //NOI18N
                }
            }
        });
        final Object _data = exchanger.exchange(null);
        assert _data != null;
        if (_data instanceof Throwable)
        {
            worker.shutdown();
            worker = null;
            throw new IOException((Throwable) _data);
        }
        else
        {
            rtData = (Pointer[]) _data;
        }
    }

    public synchronized void stop() throws IOException
    {
        if (worker == null)
        {
            throw new IllegalStateException("FileSystemWatcher is not started.");  //NOI18N
        }
        assert rtData != null;
        assert rtData.length == 2;
        assert rtData[0] != null;
        assert rtData[1] != null;
        cs.FSEventStreamStop(rtData[0]);
        cs.FSEventStreamInvalidate(rtData[0]);
        cs.FSEventStreamRelease(rtData[0]);
        cf.CFRunLoopStop(rtData[1]);
        worker.shutdown();
        worker = null;
        rtData = null;
    }

    private Pointer[] createFSEventStream() throws IOException
    {
        final Pointer root = cf.CFStringCreateWithCString(Pointer.NULL, "/", ENC_MAC_ROMAN);  //NOI18N
        if (root == Pointer.NULL)
        {
            throw new IOException("Path creation failed.");     //NOI18N
        }
        final Pointer arr = cf.CFArrayCreateMutable(Pointer.NULL, new NativeLong(1), Pointer.NULL);
        if (arr == Pointer.NULL)
        {
            throw new IOException("Path list creation failed.");    //NOI18N
        }
        cf.CFArrayAppendValue(arr, root);

        int flags = kFSEventStreamCreateFlagFileEvent |kFSEventStreamCreateFlagNoDefer;
        final Pointer eventStream = cs.FSEventStreamCreate(Pointer.NULL, callback, Pointer.NULL, arr, kFSEventStreamEventIdSinceNow, LATENCY, flags );
        if (eventStream == Pointer.NULL)
        {
            throw new IOException("Creation of FSEventStream failed."); //NOI18N
        }
        final Pointer loop = cf.CFRunLoopGetCurrent();
        if (eventStream == Pointer.NULL)
        {
            throw new IOException("Cannot find run loop for caller.");  //NOI18N
        }
        final Pointer kCFRunLoopDefaultMode = findDefaultMode(loop);
        if (kCFRunLoopDefaultMode == null)
        {
            throw new IOException("Caller has no defaul run loop mode.");   //NOI18N
        }
        cs.FSEventStreamScheduleWithRunLoop(eventStream, loop, kCFRunLoopDefaultMode);
        if (LOG.isLoggable(DEBUG_LOG_LEVEL))
        {
            LOG.log(DEBUG_LOG_LEVEL, getStreamDescription(eventStream));
        }
        cs.FSEventStreamStart(eventStream);
        return new Pointer[]
                {
                    eventStream, loop
                };
    }

    private Pointer findDefaultMode( final Pointer runLoop )
    {
        final Pointer modes = cf.CFRunLoopCopyAllModes(runLoop);
        if (modes != Pointer.NULL)
        {
            final int modesCount = cf.CFArrayGetCount(modes).intValue();
            for (int i = 0; i < modesCount; i++)
            {
                final Pointer mode = cf.CFArrayGetValueAtIndex(modes, new NativeLong(i));
                if (mode != Pointer.NULL && DEFAULT_RUN_LOOP_MODE.equals(cf.CFStringGetCStringPtr(mode, ENC_MAC_ROMAN)))
                {
                    return mode;
                }
            }
        }
        return null;
    }

    private String getStreamDescription( final Pointer eventStream )
    {
        final Pointer desc = cs.FSEventStreamCopyDescription(eventStream);
        return desc == Pointer.NULL ? "" : cf.CFStringGetCStringPtr(desc, ENC_MAC_ROMAN);   //NOI18N
    }

    public static FileSystemWatcher getDefault()
    {
        return H.INSTANCE;
    }

    int countWatches()
    {
        return listensOn.size();
    }

    public static interface EventCallback extends Callback
    {
        void invoke( Pointer streamRef,
                Pointer clientCallBackInfo,
                NativeLong numEvents,
                Pointer eventPaths,
                Pointer eventFlags,
                Pointer eventIds );
    }

    public static interface CoreFoundation extends Library
    {

        Pointer CFRunLoopGetCurrent();

        void CFRunLoopRun();

        void CFRunLoopStop( Pointer loop );

        Pointer CFRunLoopCopyAllModes( Pointer loop );

        Pointer CFArrayCreateMutable( Pointer allocator, NativeLong size, Pointer callback );

        void CFArrayAppendValue( Pointer theArray, Pointer value );

        Pointer CFArrayGetValueAtIndex( Pointer theArray, NativeLong index );

        NativeLong CFArrayGetCount( Pointer theArray );

        Pointer CFStringCreateWithCString( Pointer allocator, String string, int encoding );

        String CFStringGetCStringPtr( Pointer theString, int encoding );
    }

    public static interface CoreServices extends Library
    {

        Pointer FSEventStreamCreate( Pointer allocator, EventCallback callback, Pointer ctx, Pointer pathsToWatch, long sinceWhen, double latency, int flags );

        Pointer FSEventStreamCopyDescription( Pointer stream );

        void FSEventStreamScheduleWithRunLoop( Pointer stream, Pointer loop, Pointer mode );

        void FSEventStreamUnscheduleFromRunLoop( Pointer stream, Pointer loop, Pointer mode );

        void FSEventStreamStart( Pointer stream );

        void FSEventStreamStop( Pointer stream );

        void FSEventStreamInvalidate( Pointer stream );

        void FSEventStreamRelease( Pointer stream );
    }

    private static class H
    {
        final static FileSystemWatcher INSTANCE = new FileSystemWatcher();
    }

    private static class DaemonThreadFactory implements ThreadFactory
    {

        @Override
        public Thread newThread( Runnable r )
        {
            final Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        }
    }

    private final class KeyImpl extends Key
    {

        private final String path;
        private volatile boolean canceled;
        private CdpTicket ticket;

        private KeyImpl( final String path, CdpTicket ticket )
        {
            assert path != null;
            this.path = path;
            this.ticket = ticket;
        }

        @Override
        public boolean isValid()
        {
            return !canceled;
        }

        @Override
        public synchronized void cancel()
        {
            if (!canceled)
            {
                listensOn.remove(path);
                canceled = true;
            }
        }

        @Override
        public int hashCode()
        {
            return path.hashCode();
        }

        @Override
        public boolean equals( final Object other )
        {
            if (!(other instanceof KeyImpl))
            {
                return false;
            }
            return this.path.equals(((KeyImpl) other).path);
        }

        @Override
        public String toString()
        {
            return String.format("%s[%s]", KeyImpl.class.getSimpleName(), path);    //NOI18N
        }

        String getPath()
        {
            return path;
        }

        CdpTicket getTicket()
        {
            return ticket;
        }

        @Override
        public boolean matches( String checkPath )
        {
            if (checkPath.startsWith(path))
            {
                int plen = path.length();
                // SAME SIZE
                if (checkPath.length() == plen)
                    return true;

                // LONGER, THEN CHECK IF WE MATCH CORRECT PATH
                char ch = checkPath.charAt(plen);
                if (ch == '/' || ch == '\\')
                    return true;
            }
            return false;
        }

        
    }
    public void removeWatch( CdpTicket ticket )
    {
        Set<Entry<String,Key>> col = listensOn.entrySet();
        for (Iterator<Entry<String,Key>> it = col.iterator(); it.hasNext();)
        {
            Entry<String,Key> e = it.next();

            KeyImpl k = (KeyImpl) e.getValue();
            if (k.getTicket().equals(ticket))
            {
                listensOn.remove(e.getKey());
                break;
            }
         }
    }

    static boolean testFlag( long flags, long v )
    {
        return (flags & v) == v;
    }
    static byte getModeFromMacFCE( long flags )
    {
        if (testFlag(flags, kFSEventStreamEventFlagItemIsDir))
        {
            if (testFlag(flags, kFSEventStreamEventFlagItemCreated))
                return FceEvent.FCE_DIR_CREATE;
            if (testFlag(flags, kFSEventStreamEventFlagItemRemoved))
                return FceEvent.FCE_DIR_DELETE;
        }
        else
        {
            if (testFlag(flags, kFSEventStreamEventFlagItemCreated))
                return FceEvent.FCE_FILE_CREATE;
            if (testFlag(flags, kFSEventStreamEventFlagItemRemoved))
                return FceEvent.FCE_FILE_DELETE;
        }

        if (testFlag(flags, kFSEventStreamEventFlagKernelDropped))
            return FceEvent.FCE_OVERFLOW;
        if (testFlag(flags, kFSEventStreamEventFlagUserDropped))
            return FceEvent.FCE_OVERFLOW;
        if (testFlag(flags, kFSEventStreamEventFlagMustScanSubDirs))
            return FceEvent.FCE_DIR_CREATE;



        return FceEvent.FCE_FILE_MODIFY;
    }

    protected final FceEvent createEvent(final Key key, long flags, final String name)
    {
        byte mode = FileSystemWatcher.getModeFromMacFCE( flags );
        return new FceEvent( null, (byte)0, (byte)mode, eventId++, name.getBytes());
    }

    private class EventCallbackImpl implements EventCallback
    {

        @Override
        public void invoke( Pointer streamRef, Pointer clientCallBackInfo, NativeLong numEvents, Pointer eventPaths, Pointer eventFlags, Pointer eventIds )
        {
            final long st = System.currentTimeMillis();
            final int length = numEvents.intValue();
            final Pointer[] pointers = eventPaths.getPointerArray(0, length);
            final int[] flags = eventFlags.getIntArray(0, length);
            for (int i = 0; i < length; i++)
            {
                final Pointer p = pointers[i];
                int flag = flags[i];

                // IGNORE THIS ONE
                if (testFlag(flag, kFSEventStreamEventFlagHistoryDone))
                    continue;

                boolean found = false;
                final String path = p.getString(0);
                Set<Entry<String,Key>> col = listensOn.entrySet();

                for (Iterator<Entry<String,Key>> it = col.iterator(); it.hasNext();)
                {
                    Entry<String,Key> e = it.next();

                    if (e.getValue().matches(path))
                    {
                        events.add(createEvent(e.getValue(), flag, path));
                        found = true;
                    }
                }
                LOG.log(DEBUG_LOG_LEVEL, "Event on {0} interesting: {1}", new Object[]
                {
                    path, found
                });
            }
            LOG.log(PERF_LOG_LEVEL, "Callback time: {0}", (System.currentTimeMillis() - st));
        }
    }
}
