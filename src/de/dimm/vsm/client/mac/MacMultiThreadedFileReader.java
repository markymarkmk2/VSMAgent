/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.mac;



import de.dimm.vsm.client.FileCacheElem;
import de.dimm.vsm.client.MultiThreadedFileReader;
import de.dimm.vsm.client.Main;

/**
 *
 * @author Administrator
 */
public class MacMultiThreadedFileReader extends MultiThreadedFileReader
{
    
    public MacMultiThreadedFileReader()
    {        
        super(Main.CACHE_FILE_FLOCKS);
        
    }

    @Override
    protected void read( byte[] block, FileCacheElem elem )
    {
        MacAgentApi api = (MacAgentApi) elem.getApi();
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
