/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.dimm.vsm.client.vfs;

import com.thoughtworks.xstream.XStream;
import de.dimm.vsm.Utilities.WinFileUtilities;
import de.dimm.vsm.VSMFSLogger;
import de.dimm.vsm.client.AgentPreferences;
import de.dimm.vsm.fsutils.IVirtualFSFile;
import de.dimm.vsm.fsutils.RemoteStoragePoolHandler;
import de.dimm.vsm.fsutils.VfsProxyFile;
import de.dimm.vsm.fsutils.VirtualFSFile;
import de.dimm.vsm.fsutils.VirtualFsFilemanager;
import de.dimm.vsm.fsutils.VirtualLocalFSFile;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.vfs.IBufferedEventProcessor;
import de.dimm.vsm.vfs.IVfsBuffer;
import de.dimm.vsm.vfs.IVfsEventProcessor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Administrator
 */
public class BufferedEventProcessor implements IBufferedEventProcessor, IVfsBuffer
{
    IVfsEventProcessor proc;
    String bufferPath;
    int maxBufferedFiles;
    long maxBufferedSize;
    int maxIdleAgeS;
    public static final String BFL_SUFFIX = ".bfl";
    public static final String DATA_SUFFIX = ".dat";
    ConcurrentHashMap<String,BufferFileListEntry> map;
    boolean inited = false;
    boolean shutdown = false;
    Thread idleThread;
    RemoteStoragePoolHandler sp_handler;
    private String actPathName;
    

    public BufferedEventProcessor( IVfsEventProcessor proc)
    {        
        this.proc = proc;
        this.bufferPath = AgentPreferences.getPrefs().getBufferPath();
        this.maxBufferedFiles = AgentPreferences.getPrefs().getMaxBufferedFiles();
        this.maxBufferedSize = AgentPreferences.getPrefs().getMaxBufferedSize();
        maxIdleAgeS = AgentPreferences.getPrefs().maxIdleAgeS();
        map = new ConcurrentHashMap<>();
        idleThread = new Thread( new Runnable() {

            @Override
            public void run()
            {
                runIdle();
            }
        }, "BufferedEventProcessor idle");
        
    }

    @Override
    public void init()
    {
        if (inited)
            return;
        inited = true;
        File dir = new File(getBufferPath());
        if (!dir.exists())
            dir.mkdir();
        
        File[] files = dir.listFiles( new FilenameFilter() {

            @Override
            public boolean accept( File dir, String name )
            {
                return name.endsWith(BFL_SUFFIX);
            }
        });
        
        for (File file : files)
        {
            BufferFileListEntry bfle = readBufferFileListEntry(file);         
            if (bfle.isWrittenComplete())
            {
                File dataFile = new File(bfle.getDataFile());
                if (dataFile.exists())
                {
                    map.put(bfle.getDataFile(),bfle);    
                    
                    // Create IVirtualFSFile
                    VfsProxyFile proxy = new VfsProxyFile(bfle.getFseNode(), this);                    
                    IVirtualFSFile vfs = new VirtualLocalFSFile(this, bfle.getFseNode(), dataFile);  
                    proxy.setDelegate(vfs);
                    
                    // And add to FSManager for fetch from server
                    VirtualFsFilemanager.getSingleton().addFile(bfle.getFseNode().getPath(), proxy);                    
                }
                else
                {
                    VSMFSLogger.getLog().error("VirtualFsFilemanager init found entry w/o datafile " + bfle.getFseNode().getPath());
                    removeEntry( bfle);
                }
            }
            else
            {
                VSMFSLogger.getLog().error("VirtualFsFilemanager init found unfinished entry" + bfle.getFseNode().getPath());     
                removeEntry( bfle);
            }
        }  
        
        VSMFSLogger.getLog().debug("VirtualFsFilemanager init " + getBufferPath() +" size " + files.length);        
        idleThread.start();
        
    }
    
    @Override
    public void shutdown()
    {
        shutdown = true;
        try
        {
            idleThread.join(120 * 1000);
            if (idleThread.isAlive())
            {
                VSMFSLogger.getLog().error("VirtualFsFilemanager shutdown timed out ");                
                idleThread.interrupt();
            }
        }
        catch (InterruptedException interruptedException)
        {
            VSMFSLogger.getLog().error("VirtualFsFilemanager shutdown error", interruptedException);
        }
        // get rid of pending data synchronuosly
        flush();
    }    

    private void writeBufferFileListEntry(BufferFileListEntry entry)
    {
        XStream xstr = new XStream();
        
        try(FileOutputStream os = new FileOutputStream(new File(entry.getEntryFile()));)
        {            
            xstr.toXML(entry, os);
        }
        catch (IOException iOException)
        {
            VSMFSLogger.getLog().error("VirtualFsFilemanager writeBufferFileListEntry failed ", iOException);
        }
    }
    
    private BufferFileListEntry readBufferFileListEntry(File file)
    {
        XStream xstr = new XStream();
        try( FileInputStream is = new FileInputStream(file))
        {
            Object o = xstr.fromXML(is);
            
            if (o != null && o instanceof BufferFileListEntry)
            {
                return (BufferFileListEntry) o;
            }
            else 
                throw new IOException("Invalid FS entry " + file.getAbsolutePath());
        }
        catch (IOException iOException)
        {
            VSMFSLogger.getLog().error("VirtualFsFilemanager readBufferFileListEntry failed ", iOException);
        }
        return null;            
    }

    @Override
    public void removeEntry( IVirtualFSFile ret )
    {
        if (!(ret instanceof VfsProxyFile))
        {
            VSMFSLogger.getLog().error("VirtualFsFilemanager removeEntry unknown type " + ret.getClass().getSimpleName());
            return;
        }
        
        VfsProxyFile proxy = (VfsProxyFile)ret;
        if (proxy.getDelegate() instanceof VirtualLocalFSFile) 
        {
            VirtualLocalFSFile vfs = (VirtualLocalFSFile)proxy.getDelegate();
            BufferFileListEntry entry = map.remove(vfs.getDataFile().getAbsolutePath());
            if (entry != null)
            {
                File dataFile = new File(entry.getDataFile());
                if (dataFile.exists())
                    dataFile.delete();
                
                File entryFile = new File(entry.getEntryFile());
                if (entryFile.exists())
                    entryFile.delete();
            }
        }
    }    
    
    private void removeEntry( BufferFileListEntry entry )
    {        
        File dataFile = new File(entry.getDataFile());
        if (dataFile.exists())
            dataFile.delete();

        File entryFile = new File(entry.getEntryFile());
        if (entryFile.exists())
            entryFile.delete();
    }    

    
    @Override
    public boolean process( List<RemoteFSElem> elems ) throws IOException
    {
        return proc.process(elems);
    }

    @Override
    public long startProcess( List<RemoteFSElem> elems ) throws IOException
    {
        return proc.startProcess(elems);
    }

    @Override
    public boolean waitProcess( long id, int timout ) throws IOException
    {
        return proc.waitProcess(id, timout);
    }

    @Override
    public boolean fetchResult( long id ) throws IOException
    {
        return proc.fetchResult(id);
    }

    @Override
    public void abortProcess( long id )
    {
        proc.abortProcess(id);
    }
    
    
    @Override
    public String getBufferPath()
    {
        return bufferPath;
    }
    long checkedSizeLastTime = 0;

    @Override
    public boolean isFsBufferFree()
    {
        if (map.size() > maxBufferedFiles)
            return false;
        
        long now = System.currentTimeMillis();
        if ((now - checkedSizeLastTime) > 60*1000 )
        {
            try
            {
                FileStore fst = Files.getFileStore(Paths.get(getBufferPath()));
                if (fst.getUsableSpace() < 100*1024*1024)
                {
                    return false;

                }
                // OKAY CHECK AGAIN LATER
                checkedSizeLastTime = now;
            }
            catch (IOException iOException)
            {
                return false;
            }
        }
        
        long sum = 0;
        Set<String> dataFiles = map.keySet();
        for (String fileName : dataFiles)
        {
            File file = new File(fileName);
            sum += file.length();
            
        }
        if (sum < maxBufferedSize)
            return true;
        
        return false;
    }

    @Override
    public boolean isBufferReady()
    {
        if (map.size() > maxBufferedFiles / 2)
            return true;
        
        long sum = 0;
        long now = System.currentTimeMillis();
        
        Collection<BufferFileListEntry> coll = map.values();
        for (BufferFileListEntry entry : coll)
        {            
            if ((now - entry.getCreationTime()) > maxIdleAgeS*1000l)
                return true;
            
            sum += entry.getFseNode().getDataSize();   
        }
        if (sum > maxBufferedSize / 2)
            return true;
        
        return false;
    }

    @Override
    public long getMaxLocalFileThreshold()
    {
        return proc.getMaxLocalFileThreshold();
    }

    @Override
    public IVirtualFSFile createDelegate(RemoteFSElem fseNode)
    {
        // Small enough for Local FileBuff and not 0 (we want to create empty files at once) ?
        if (fseNode.getDataSize() > 0 && fseNode.getDataSize() < getMaxLocalFileThreshold() && isFsBufferFree())
        {
            // New Random Filename
            String filename = UUID.randomUUID().toString();
            File baseFile = new File( getBufferPath(), filename);            

            BufferFileListEntry entry = new BufferFileListEntry(fseNode, baseFile.getAbsolutePath());

            // New Datafile
            File dataFile = new File(entry.getDataFile());

            // Slow Down if buffer gets too full
            int percentFilled = getMapPercentFilled();
            if (percentFilled > 100)
            {
                VSMFSLogger.getLog().debug("Slowing down file creation");
                sleepMs(100);
            }
            
            // Put to map
            map.put( dataFile.getAbsolutePath(), entry );        
                            
            // Create IVirtualFSFile
            IVirtualFSFile file = new VirtualLocalFSFile(this, fseNode, dataFile);
            return file;
        }
        else
        {
            // Create sync VFS
            //waitForBufferFlush();
            return new VirtualFSFile(this, fseNode);
        }
    }
         
    
    
    
    @Override
    public IVirtualFSFile createFile( RemoteFSElem fseNode )
    {
        return new VfsProxyFile(fseNode, this);
//        // Small enough for Local FileBuff ?
//        if (fseNode.getDataSize() < getMaxLocalFileThreshold() && isFsBufferFree())
//        {
//            // New Random Filename
//            String filename = UUID.randomUUID().toString();
//            File baseFile = new File( getBufferPath(), filename);            
//
//            BufferFileListEntry entry = new BufferFileListEntry(fseNode, baseFile.getAbsolutePath());
//
//            // New Datafile
//            File dataFile = new File(entry.getDataFile());
//
//            // Slow Down if buffer gets too full
//            int percentFilled = getMapPercentFilled();
//            if (percentFilled > 100)
//            {
//                VSMFSLogger.getLog().debug("Slowing down file creation");
//                sleepMs(100);
//            }
//            
//            // Put to map
//            map.put( dataFile.getAbsolutePath(), entry );        
//                            
//            // Create IVirtualFSFile
//            IVirtualFSFile file = new VirtualLocalFSFile(this, fseNode, dataFile);
//            return file;
//        }
//        else
//        {
//            // Create sync VFS
//            //waitForBufferFlush();
//            return new VirtualFSFile(this, fseNode);
//        }
    }  
    
    @Override
    public boolean close(VirtualLocalFSFile file )
    {
        File dataFile = file.getDataFile();
       
        // Get from map
        // Ready for fetch
        BufferFileListEntry entry =  map.get(dataFile.getAbsolutePath());
        entry.setWrittenComplete(true);
        
        // Write XML to Disk for crash recovery
        writeBufferFileListEntry( entry );
        
        // ON EACH CLOSE DO AN IDLE TO PREVENT LOCKING
        //idle();
        
        return true;
    }
    
    @Override
    public void flush()
    {
        synchronized(this)
        {
            Set<Entry<String,BufferFileListEntry>> set = map.entrySet();
            List<RemoteFSElem> list = new ArrayList<>();
            for (Entry<String,BufferFileListEntry> entry : set)
            {
                if (entry.getValue().isWrittenComplete())
                {
                    list.add(entry.getValue().getFseNode());                
                }
            }

            try
            {
                if (!list.isEmpty())
                {
                    process(list);
                }
            }
            catch (IOException iOException)
            {
                VSMFSLogger.getLog().error("BufferedEventProcessor idle failed ", iOException);
            }  
            lastTimeChecked = System.currentTimeMillis();
        }
    }
    
    @Override
    public void idle()
    {        
        // Achting, das wird bei jeder Datei aud´fegrufen, muss schnell sein und darf nicht zu lange dauern 
        long now = System.currentTimeMillis();
        
        // DO NOT CHECK MORE OFTEN THAN ONCE A SECOND
        if (now - lastTimeChecked < 1000)
            return;
        
        // IS SOMETHING TO DO?
        if (isBufferReady())
        {
            flush();
        }  
        // REWIND CLOCK
        lastTimeChecked = System.currentTimeMillis();
    }
    
    int maxThreadIdleMs = 10000;
    
    long getTimeTillAutoIdle()
    {
        return  maxThreadIdleMs - (System.currentTimeMillis() - lastTimeChecked);
    }
    boolean isSpHandlerBusy()
    {
        if (sp_handler.isInsideApi())
        {
            return true;
        }
        if (sp_handler.timeSinceLastCall() < maxThreadIdleMs/2)
        {
            return true;
        }
        return false;
    }
    
    long lastTimeChecked = 0;
    boolean doFlush;
    boolean wasFlushed;
    private void runIdle()
    {                                 
        lastTimeChecked = 0;
        while (!shutdown)
        {
            try
            {
                Thread.sleep(100);
                                
                long now = System.currentTimeMillis();
                                
                // Do nothing until spHandler is idle (read Dir etc)
                /*if (isSpHandlerBusy())
                {                    
                    continue;
                }*/
                
                // Wants flush?
                if (doFlush)
                {
                    doFlush = false;
                    wasFlushed = false;
                    flush();
                    wasFlushed = true;
                    lastTimeChecked = now;
                    continue;
                }
                
                // Lasttime idle check was more than maxThreadIdleMs ago?
                if ( (now - lastTimeChecked) > maxThreadIdleMs)
                {
                    idle();      
                    lastTimeChecked = now;
                }
                
            }
            catch (Exception exc)
            {
                VSMFSLogger.getLog().error("Idle failed ", exc);
            }                    
        }  
        VSMFSLogger.getLog().debug("BufferedEventProcessor idle finished");
    }

    String lastPathName;
//    private void waitForBufferFlush()
//    {
//        // WENN WIR DAS VERZ NICHT GEWECHSELT HABEN, MÜSSEN WIR NICHT SYNCHEN
//        if (!StringUtils.isEmpty(lastPathName) && !StringUtils.isEmpty(actPathName) 
//                && actPathName.equals(actPathName))
//        {
//            return;
//        }
//       
//        VSMFSLogger.getLog().debug("flushing buffer with " + map.size() + " entries...");
//        
//        // kommt der nächste flush per idle bald (Lock auf Server vermeiden)
//        // der Idle-Flush darf nur kommen, wenn der Client nix macht (10s nach der zuletzt angefassten Datei
//        if (getTimeTillAutoIdle() < 1000)
//        {
//            // Flush Asynchron mit wait
//            VSMFSLogger.getLog().debug("flushing buffer waiting fer next idle...");
//            doFlush = true;
//            wasFlushed = false;
//            long start = System.currentTimeMillis();
//            while(!wasFlushed)
//            {
//                sleepMs( 100 );
//                long now = System.currentTimeMillis();
//                if (now - start > AgentPreferences.getPrefs().getFlushTimeoutS()*1000)
//                {
//                    VSMFSLogger.getLog().error("waitForBufferFlush timeout after " + (now - start) + " ms");
//                    break;
//                }            
//            }
//        }
//        else
//        {
//            // Flush Synchron
//            flush();
//            wasFlushed = true;
//        }
//        if (wasFlushed)
//            VSMFSLogger.getLog().debug("flushing done");
//    }
//    
    void sleepMs(int ms)
    {
        try
        {
            Thread.sleep(ms);
        }
        catch (InterruptedException ex)
        {
            Logger.getLogger(BufferedEventProcessor.class.getName()).log(Level.SEVERE, null, ex);
        }
    }    

    @Override
    public void setRemoteStoragePoolHandler( RemoteStoragePoolHandler sp_handler )
    {
        this.sp_handler = sp_handler;
    }

   

    private int getMapPercentFilled()
    {
        return (map.size() * 100) / maxBufferedFiles;
    }

    @Override
    public void checkForFlush( String path )
    {
        BufferFileListEntry bfe = map.remove(path);
        if (bfe != null)
        {
            List<RemoteFSElem> list = new ArrayList<>();
            list.add(bfe.getFseNode());
            try
            {
                process(list);                
            }
            catch (IOException iOException)
            {
                VSMFSLogger.getLog().error("checkForFlush failed for " + bfe.getFseNode().getPath(), iOException);
            }
        }
    }

    
}
