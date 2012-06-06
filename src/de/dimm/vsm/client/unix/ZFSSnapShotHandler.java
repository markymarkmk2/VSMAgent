/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.dimm.vsm.client.unix;

import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.net.interfaces.SnapshotHandle;
import de.dimm.vsm.net.interfaces.SnapshotHandler;

/**
 *
 * @author mw
 */
class ZFSSnapShotHandler implements SnapshotHandler {

    public ZFSSnapShotHandler() 
    {
    }

    @Override
    public SnapshotHandle create_snapshot(RemoteFSElem file) 
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean release_snapshot(SnapshotHandle handle) 
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void init() 
    {
        
    }
    
}
