/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client;

import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.net.RemoteFSElemWrapper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 *
 * @author Administrator
 */
public abstract class FSElemAccessor
{
    // MORE THAN TWO DONT MAKWE SENSE
    public static final int MAX_FILE_READERS = 1;

    final List<MultiThreadedFileReader> mtfrBufferList;
    final private HashMap<Long, MultiThreadedFileReader> mtfrMap;
    final protected HashMap<Long,FileHandleData> hash_map;

    protected NetAgentApi api;
    public FSElemAccessor( NetAgentApi api )
    {
        this.api = api;
        mtfrMap = new HashMap<Long, MultiThreadedFileReader>();
        mtfrBufferList = new ArrayList<MultiThreadedFileReader>();

        for (int i = 0; i < MAX_FILE_READERS; i++)
        {
            mtfrBufferList.add(  MultiThreadedFileReader.MTFRFactory(api) );
        }
        hash_map = new HashMap<Long, FileHandleData>();
    }

    boolean loggedEmpty = false;
    public MultiThreadedFileReader createMultiThreadedFileReader( NetAgentApi api, RemoteFSElemWrapper wrapper ) throws IOException
    {
        if (mtfrBufferList.isEmpty())
        {
            if (!loggedEmpty)
            {
                System.out.println("MultiThreadedFileHandles are empty, Map size is " + mtfrMap.size());
                loggedEmpty = true;
            }
            return null;
        }

        MultiThreadedFileReader mtfr = mtfrBufferList.remove(0);

        mtfr.initQueues();
        mtfrMap.put(wrapper.getHandle(), mtfr);
        
        return mtfr;
    }

    public MultiThreadedFileReader getMultiThreadedFileReader( RemoteFSElemWrapper wrapper )
    {
        return mtfrMap.get(wrapper.getHandle());
    }

     public abstract RemoteFSElemWrapper open_handle( RemoteFSElem elem, int flags );
     public abstract RemoteFSElemWrapper open_xa_handle( RemoteFSElem elem, int flags );


    public boolean close_handle( RemoteFSElemWrapper wrapper ) throws IOException
    {
        MultiThreadedFileReader mtfr = mtfrMap.remove(wrapper.getHandle());
        if (mtfr != null)
        {
            mtfrBufferList.add(0, mtfr);
        }
        return true;
    }

    public void resetFileReaders()
    {
        Collection<MultiThreadedFileReader> coll = mtfrMap.values();
        if (!coll.isEmpty())
        {
            mtfrMap.clear();
            mtfrBufferList.addAll(coll);
        }
        for (int i = 0; i < mtfrBufferList.size(); i++)
        {
            MultiThreadedFileReader multiThreadedFileReader = mtfrBufferList.get(i);
            multiThreadedFileReader.resetQueues();
        }

    }


    public void close()
    {
        Collection<MultiThreadedFileReader> coll = mtfrMap.values();
        for (MultiThreadedFileReader multiThreadedFileReader : coll)
        {
            multiThreadedFileReader.shutdown();
        }
        mtfrMap.clear();

        for (int i = 0; i < mtfrBufferList.size(); i++)
        {
            MultiThreadedFileReader multiThreadedFileReader = mtfrBufferList.get(i);
            multiThreadedFileReader.shutdown();
        }
        mtfrBufferList.clear();
    }

    static protected boolean test_flag( int flags, int flag)
    {
        return (flags & flag) == flag;
    }
    

    public FileHandleData get_handleData( RemoteFSElemWrapper wrapper)
    {
        FileHandleData data = hash_map.get(wrapper.getHandle());

        return data;
    }

    public abstract boolean createSymlink( String path, String linkPath );
    
    public boolean mkDir( File f )
    {
         return api.getFsFactory().mkDir(f);
    }

    public static String[] nulltermList2Array( byte[] arr )
    {
        if (arr.length == 0)
        {
            return new String[0];
        }

        ArrayList<String> l = new ArrayList<String>();

        int start = 0;
        int end = 0;

        while (true)
        {
            end++;
            if (end == arr.length)
            {
                if (end - start > 1)
                {
                    l.add(new String(arr, start, end - start - 1));
                }

                break;
            }
            else if (arr[end] == 0)
            {
                if (start < end)
                    l.add(new String(arr, start,  end - start));

                start = end + 1;
                if (start >= arr.length)
                    break;
            }
        }
        return l.toArray(new String[0]);
    }


}
