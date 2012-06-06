/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client;

/**
 *
 * @author Administrator
 */
public class FileCacheElem
{
    long offset;
    int len;
    byte[] data;
    String hash;
    byte[] xa_data;
    int idx;

    boolean hashready;
    HashReadyLock lock;
    NetAgentApi api;

    public FileCacheElem( NetAgentApi api, long offset, int len, int idx)
    {
        this.api = api;
        this.offset = offset;
        this.len = len;
        this.idx = idx;
    }

   
    public long getOffset()
    {
        return offset;
    }

    public int getLen()
    {
        return len;
    }

    public void setXAData( byte[] read )
    {
        xa_data = read;
    }
    public void setData( byte[] read )
    {
        data = read;
    }

    public int getIdx()
    {
        return idx;
    }

    public NetAgentApi getApi()
    {
        return api;
    }

    void clean()
    {
        data = null;
        xa_data = null;
        hash = null;
        api = null;
        lock = null;
    }

    
   
    
}