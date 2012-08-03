/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.unix;

import de.dimm.vsm.client.FSElemAccessor;
import de.dimm.vsm.client.FileHandleData;
import de.dimm.vsm.client.jna.PosixWrapper;
import de.dimm.vsm.net.interfaces.AgentApi;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.net.RemoteFSElemWrapper;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import org.jruby.ext.posix.POSIX;



class UnixFileHandleData extends FileHandleData
{
    RandomAccessFile handle;

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
}
/**
 *
 * @author Administrator
 */
public class UnixFSElemAccessor extends FSElemAccessor
{
    
    static long newHandleValue = 1;
    

   
    public UnixFSElemAccessor( UnixAgentApi api)
    {
        super(api);        
    }

    @Override
    public RemoteFSElemWrapper open_handle( RemoteFSElem elem, int flags )
    {        
        String npath = api.getFsFactory().convSystem2NativePath(elem.getPath());

        RandomAccessFile fh = null;
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
        }
        catch (FileNotFoundException fileNotFoundException)
        {
            return null;
        }

        UnixFileHandleData data = new UnixFileHandleData(elem,fh);

        RemoteFSElemWrapper wrapper = new RemoteFSElemWrapper(newHandleValue++, /*xa*/false);

        hash_map.put(wrapper.getHandle(), data);

        return wrapper;
    }

    @Override
    public RemoteFSElemWrapper open_xa_handle( RemoteFSElem elem, int flags )
    {
        RandomAccessFile fh = null;
        String npath = api.getFsFactory().convSystem2NativePath(elem.getPath());
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
        }
        catch (FileNotFoundException fileNotFoundException)
        {
            return null;
        }

        UnixFileHandleData data = new UnixFileHandleData(elem,fh);

        RemoteFSElemWrapper wrapper = new RemoteFSElemWrapper(newHandleValue++, /*xa*/true);

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

        return data.handle;
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

        path = api.getFsFactory().convSystem2NativePath(path);

        PosixWrapper.getPosix().utimes(path, atimes, mtimes );
    }

    @Override
    public boolean createSymlink( String path, String linkPath )
    {
        path = api.getFsFactory().convSystem2NativePath(path);
        linkPath = api.getFsFactory().convSystem2NativePath(linkPath);

        POSIX posix = PosixWrapper.getPosix();
        try
        {
            if (posix.symlink(path, linkPath) == 0)
                return true;
        }
        catch (Exception e)
        {
        }
        return false;
    }



    private String getXAPath( String path )
    {
         return api.getFsFactory().getXAPath(path);
    }
   
}
