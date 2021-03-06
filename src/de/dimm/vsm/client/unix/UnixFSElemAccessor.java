/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.unix;

import de.dimm.vsm.client.FSElemAccessor;
import de.dimm.vsm.client.FileHandleData;
import de.dimm.vsm.client.jna.PosixWrapper;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.net.RemoteFSElemWrapper;
import de.dimm.vsm.net.interfaces.AgentApi;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import org.jruby.ext.posix.POSIX;



class UnixFileHandleData extends FileHandleData
{
    private final RandomAccessFile handle;

    public UnixFileHandleData( RemoteFSElem elem, RandomAccessFile handle )
    {
        super(elem);
        this.handle = handle;
        
    }

    @Override
    public boolean close()
    {
        try
        {
            handle.close();
        }
        catch (IOException iOException)
        {
            return false;
        }
        return true;
    }

    public RandomAccessFile getHandle() {
        return handle;
    }

}
/**
 *
 * @author Administrator
 */
public class UnixFSElemAccessor extends FSElemAccessor
{
    
    static private long newHandleValue = 1;
    

   
    public UnixFSElemAccessor( UnixAgentApi api)
    {
        super(api);        
    }

    @Override
    public boolean exists( RemoteFSElem path )
    {
        String npath = getApi().getFsFactory().convSystem2NativePath(path.getPath());
        return new File(npath).exists();
    }
    

    @Override
    public RemoteFSElemWrapper open_handle( RemoteFSElem elem, int flags )
    {        
        String npath = getApi().getFsFactory().convSystem2NativePath(elem.getPath());

        RandomAccessFile fh = null;
        FileLock lock = null;
        try
        {
            if (flags == AgentApi.FL_RDONLY)
            {
                fh = new RandomAccessFile(npath, "r");
            }
            else if (test_flag(flags, AgentApi.FL_RDWR))
            {
                fh = new RandomAccessFile(npath, "rw");
            }
            
            // Try Lock
            if (fh != null)
            {
                lock = fh.getChannel().tryLock(0, Integer.MAX_VALUE, /*shared*/ true);
                if (lock == null) 
                {
                    fh.close();
                    System.out.println("File is locked, skipping: " + npath );
                    return null;
                }                
            }
        }
        catch (IOException  exc)
        {            
            System.out.println("Exception on open, skipping: " + npath + ": " + exc.getMessage() );
            return null;
        }
        finally
        {
            if (lock != null)
            {
                try
                {
                    lock.release();
                }
                catch (IOException iOException)
                {
                }
            }
        }

        UnixFileHandleData data = new UnixFileHandleData(elem,fh);

        RemoteFSElemWrapper wrapper = new RemoteFSElemWrapper(newHandleValue++, /*xa*/false, elem.isVirtualFS());

        hash_map.put(wrapper.getHandle(), data);

        return wrapper;
    }

    @Override
    public RemoteFSElemWrapper open_xa_handle( RemoteFSElem elem, int flags )
    {
        RandomAccessFile fh = null;
        FileLock lock = null;
        
        String npath = getApi().getFsFactory().convSystem2NativePath(elem.getPath());
        String path = getXAPath( npath );
        try
        {
            if (flags == AgentApi.FL_RDONLY)
            {
                fh = new RandomAccessFile(path, "r");
            }
            else if (test_flag(flags, AgentApi.FL_RDWR))
            {
                try
                {
                    fh = new RandomAccessFile(path, "rw");
                }
                catch (FileNotFoundException fileNotFoundException)
                {
                    File parent = new File( path );
                    if (!parent.getParentFile().exists())
                    {
                        parent.getParentFile().mkdirs();
                        fh = new RandomAccessFile(path, "rw");
                    }
                }
            }
            // Try Lock
            if (fh != null)
            {
                lock = fh.getChannel().tryLock(0, Integer.MAX_VALUE, /*shared*/ true);
                if (lock == null) 
                {
                    fh.close();
                    System.out.println("File is locked, skipping: " + npath );
                    return null;
                }                
            }
        }
        catch (IOException  exc)
        {            
            System.out.println("Exception on open, skipping: " + npath + ": " + exc.getMessage() );
            return null;
        }
        finally
        {
            if (lock != null)
            {
                try
                {
                    lock.release();
                }
                catch (IOException iOException)
                {
                }
            }
        }

        UnixFileHandleData data = new UnixFileHandleData(elem,fh);

        RemoteFSElemWrapper wrapper = new RemoteFSElemWrapper(newHandleValue++, /*xa*/true, elem.isVirtualFS());

        hash_map.put(wrapper.getHandle(), data);

        return wrapper;
    }

    @Override
    public boolean close_handle(RemoteFSElemWrapper wrapper) throws IOException
    {

        if (wrapper == null)
            return false;
        
        super.close_handle(wrapper);

        FileHandleData data = hash_map.remove(wrapper.getHandle());
        if (data == null)
            return false;

        return data.close();
    }
    
    public RandomAccessFile get_handle( RemoteFSElemWrapper wrapper)
    {
        UnixFileHandleData data = (UnixFileHandleData)hash_map.get(wrapper.getHandle());
        if (data == null)
            return null;

        return data.getHandle();
    }
    @Override
    public FileHandleData get_handleData( RemoteFSElemWrapper wrapper)
    {
        FileHandleData data = hash_map.get(wrapper.getHandle());

        return data;
    }


    void setFiletime( String path, RemoteFSElem dir )
    {
        long[] atimes = new long[2];
        long[] mtimes = new long[2];

        atimes[0] = dir.getAtimeMs() / 1000;
        atimes[1] = (dir.getAtimeMs() % 1000) * 1000;
        mtimes[0] = dir.getMtimeMs() / 1000;
        mtimes[1] = (dir.getMtimeMs() % 1000) * 1000;

        path = getApi().getFsFactory().convSystem2NativePath(path);

        PosixWrapper.getPosix().utimes(path, atimes, mtimes );
    }

    @Override
    public boolean createSymlink( String path, String linkPath )
    {
        path = getApi().getFsFactory().convSystem2NativePath(path);
        linkPath = getApi().getFsFactory().convSystem2NativePath(linkPath);

        POSIX posix = PosixWrapper.getPosix();
        try
        {
            if (posix.symlink(path, linkPath) == 0)
                return true;
        }
        catch (Exception e)
        {
            System.out.println("Exception on createSymlink: " + path + "->" + linkPath + ": " + e.getMessage() );
        }
        return false;
    }



    private String getXAPath( String path )
    {
        return getApi().getFsFactory().getXAPath(path);
    }
   
}
