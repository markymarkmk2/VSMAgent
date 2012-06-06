/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.win;


import de.dimm.vsm.client.FileCacheElem;
import de.dimm.vsm.client.MultiThreadedFileReader;
import de.dimm.vsm.client.Main;

/**
 *
 * @author Administrator
 */
public class WinMultiThreadedFileReader extends MultiThreadedFileReader
{
    

    public WinMultiThreadedFileReader()
    {        
        super(Main.CACHE_FILE_FLOCKS);
    }

    @Override
    protected void read( byte[] block, FileCacheElem elem )
    {
        WinAgentApi api = (WinAgentApi)elem.getApi();
        byte[] data = api.rawRead(actWrapper, elem.getOffset(), elem.getLen());
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
