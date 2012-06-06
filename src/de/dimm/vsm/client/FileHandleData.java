/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client;

import de.dimm.vsm.net.RemoteFSElem;

/**
 *
 * @author Administrator
 */
public abstract class FileHandleData
{
    protected RemoteFSElem elem;
    boolean prefetch;

    public FileHandleData( RemoteFSElem elem)
    {
        this.elem = elem;
        this.prefetch = false;
    }


    public void setPrefetch( boolean prefetch )
    {
        this.prefetch = prefetch;
    }

    public boolean isPrefetch()
    {
        return prefetch;
    }

    public abstract boolean close();

    public RemoteFSElem getElem()
    {
        return elem;
    }

    

}
