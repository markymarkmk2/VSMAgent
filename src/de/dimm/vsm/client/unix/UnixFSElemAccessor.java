/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.unix;

import de.dimm.vsm.client.FSElemAccessor;
import de.dimm.vsm.client.NetAgentApi;
import de.dimm.vsm.client.jna.PosixWrapper;
import de.dimm.vsm.net.interfaces.AgentApi;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.net.RemoteFSElemWrapper;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;



class UnixFileHandleData
{
    RandomAccessFile handle;
    RemoteFSElem elem;
    boolean prefetch;

    public UnixFileHandleData( RemoteFSElem elem, RandomAccessFile handle )
    {
        this.elem = elem;
        this.handle = handle;
        prefetch = false;
    }

    boolean close()
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

    public void setPrefetch( boolean prefetch )
    {
        this.prefetch = prefetch;
    }

    public boolean isPrefetch()
    {
        return prefetch;
    }


}
/**
 *
 * @author Administrator
 */
public class UnixFSElemAccessor extends FSElemAccessor
{
    HashMap<Long,UnixFileHandleData> hash_map;
    

    static long newHandleValue = 1;

   
    public UnixFSElemAccessor( NetAgentApi api)
    {
        super(api);
        hash_map = new HashMap<Long, UnixFileHandleData>();
    }

    public RemoteFSElemWrapper open_handle( RemoteFSElem elem, int flags )
    {
        RandomAccessFile fh = null;
        try
        {
            if (flags == AgentApi.FL_RDONLY)
            {
                fh = new RandomAccessFile(elem.getPath(), "r");
            }
            else if (test_flag(flags, AgentApi.FL_RDWR))
            {
                fh = new RandomAccessFile(elem.getPath(), "rw");
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

    public RemoteFSElemWrapper open_xa_handle( RemoteFSElem elem, int flags )
    {
        RandomAccessFile fh = null;
        String path = NetatalkRemoteFSElemFactory.getADPath( elem.getPath() );
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

        UnixFileHandleData data = hash_map.remove(wrapper.getHandle());
        if (data == null)
            return false;

        return data.close();
    }
    
    public RandomAccessFile get_handle( RemoteFSElemWrapper wrapper)
    {
        UnixFileHandleData data = hash_map.get(wrapper.getHandle());
        if (data == null)
            return null;

        return data.handle;
    }
    public UnixFileHandleData get_handleData( RemoteFSElemWrapper wrapper)
    {
        UnixFileHandleData data = hash_map.get(wrapper.getHandle());

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

        PosixWrapper.getPosix().utimes(path, atimes, mtimes );
    }
   
}
