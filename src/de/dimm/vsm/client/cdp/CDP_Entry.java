/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.cdp;

import de.dimm.vsm.net.RemoteFSElem;
import java.io.Serializable;

/**
 *
 * @author Administrator
 */
public class CDP_Entry implements Serializable
{
    public static final int IS_IN_QUEUE = 1;
    public static final int  QUEUE_IS_FULL = 2;


    public static final int CDP_NOTHING = 0x00;
    public static final int CDP_MODIFIED = 0x01;
    public static final int CDP_DELETED = 0x02;
    public static final int CDP_RENAMED = 0x03;
    public static final int CDP_MODIFIED_RECURSIVE = 0x04;
    public static final int CDP_CREATED = 0x08;


    private RemoteFSElem path;
    private int flags;
    private long time_code;
    private long last_touched;

    CDP_Entry( RemoteFSElem _path, int _flags, long _time_code )
    {
        path = _path;
        flags = _flags;
        time_code = _time_code;
        last_touched = 0;
    }
    void setPath(RemoteFSElem _path)
    {
        path = _path;
    }

    void setFlag( int f )
    {
        flags = f;
    }

    public int getFlags()
    {
        return flags;
    }

    public RemoteFSElem getPath()
    {
        return path;
    }

    public long getTime_code()
    {
        return time_code;
    }

    public long getLast_touched()
    {
        return last_touched;
    }

    void setLast_touched( long now )
    {
        last_touched = now;
    }
    
}
