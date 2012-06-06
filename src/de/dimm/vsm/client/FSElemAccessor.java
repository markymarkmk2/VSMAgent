/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client;

import de.dimm.vsm.net.AttributeContainer;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.net.RemoteFSElemWrapper;
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

    List<MultiThreadedFileReader> mtfrBufferList;
    private HashMap<Long, MultiThreadedFileReader> mtfrMap;
    protected HashMap<Long,FileHandleData> hash_map;

    public FSElemAccessor( NetAgentApi api )
    {
        mtfrMap = new HashMap<Long, MultiThreadedFileReader>();
        mtfrBufferList = new ArrayList<MultiThreadedFileReader>();

        for (int i = 0; i < MAX_FILE_READERS; i++)
        {
            mtfrBufferList.add(  MultiThreadedFileReader.MTFRFactory(api) );
        }
        hash_map = new HashMap<Long, FileHandleData>();
    }

    public MultiThreadedFileReader createMultiThreadedFileReader( NetAgentApi api, RemoteFSElemWrapper wrapper ) throws IOException
    {
        if (mtfrBufferList.isEmpty())
        {
            System.out.println("MultiThreadedFileHandles are empty");
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
    public void setAttributes( RemoteFSElem dir )
    {
        if (dir.getAclinfoData() != null)
        {
            AttributeContainer ac = AttributeContainer.unserialize(dir.getAclinfoData());
            AttributeContainerImpl.set(dir, ac);
        }
    }
    

    public FileHandleData get_handleData( RemoteFSElemWrapper wrapper)
    {
        FileHandleData data = hash_map.get(wrapper.getHandle());

        return data;
    }

    public abstract boolean createSymlink( String path, String linkPath );




}
