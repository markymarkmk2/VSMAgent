/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.unix;


import de.dimm.vsm.client.FileCacheElem;
import de.dimm.vsm.client.MultiThreadedFileReader;
import de.dimm.vsm.client.Main;

/**
 *
 * @author Administrator
 */
public class UnixMultiThreadedFileReader extends MultiThreadedFileReader
{
    
    public UnixMultiThreadedFileReader()
    {        
        super(Main.CACHE_FILE_FLOCKS);
        
    }

    @Override
    protected void read( byte[] block, FileCacheElem elem )
    {
        UnixAgentApi api = (UnixAgentApi) elem.getApi();
        byte[] data = api.rawRead( block, actWrapper, elem.getOffset(), elem.getLen());
        if (data == null || data.length == 0)
        {
            data = data;
        }
        if (actWrapper.isXa())
            elem.setXAData( data );
        else
            elem.setData( data );
    }

}
