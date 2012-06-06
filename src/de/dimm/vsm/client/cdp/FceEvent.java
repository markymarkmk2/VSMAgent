/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.cdp;

import java.io.File;
import java.net.InetAddress;

/**
 *
 * @author Administrator
 */
public class FceEvent
{
    public static final byte FCE_FILE_MODIFY = 1;
    public static final byte FCE_FILE_DELETE = 2;
    public static final byte FCE_DIR_DELETE = 3;
    public static final byte FCE_FILE_CREATE = 4;
    public static final byte FCE_DIR_CREATE = 5;
    public static final byte FCE_TM_SIZE = 6;
    public static final byte FCE_CONN_START = 42;
    public static final byte FCE_OVERFLOW = 98;
    public static final byte FCE_CONN_BROKEN = 99;

    InetAddress client;
    byte version;
    byte mode;
    int event_id;
    byte[] data;

    public FceEvent( InetAddress client, byte version, byte mode, int event_id, byte[] data )
    {
        this.client = client;
        this.version = version;
        this.mode = mode;
        this.event_id = event_id;

        this.data = data;
    }
    public FceEvent( FceEvent ev )
    {
        this.client = ev.client;
        this.version = ev.version;
        this.mode = ev.mode;
        this.event_id = ev.event_id;

        if (data != null)
        {
            this.data = new byte[data.length];
            System.arraycopy(this.data, 0, data, 0, this.data.length);
        }

    }

    public void setMode( byte mode )
    {
        this.mode = mode;
    }

    
    String getModeString( byte m )
    {
        switch(m)
        {
            case FCE_FILE_MODIFY: return "FileModify";
            case FCE_FILE_DELETE: return "FileDelete";
            case FCE_DIR_DELETE: return "DirDelete";
            case FCE_FILE_CREATE: return "FileCreate";
            case FCE_DIR_CREATE: return "DirCreate";
            case FCE_TM_SIZE: return "TMSize";
            case FCE_CONN_START: return "ConnStart";
            case FCE_CONN_BROKEN: return "ConnBroken";
            case FCE_OVERFLOW: return "Overflow";
        }
        return "?";
    }
    @Override
    public String toString()
    {
        String c = "";
        if (client != null)
            c = "C:" + client.getHostAddress() + " ";

        String s = new String(data);
        return c + "V:" + version + " M:" + getModeString(mode) + " ID:" + event_id + " <" + s + ">";
    }

    public String getPath()
    {
        return new String(data);
    }
    public void setPath(String s)
    {
        data = s.getBytes();
    }

    public InetAddress getClient()
    {
        return client;
    }
    

    public String getParentPath()
    {
        String p = getPath();

        int idx = p.lastIndexOf( File.separatorChar);
        if (idx >= 0)
        {
            // PRESERVE ROOT
            if (idx == 0)
                idx++;
            return p.substring(0, idx);
        }
        return p;
    }

    String getName()
    {
        String p = getPath();

        int idx = p.lastIndexOf( File.separatorChar);
        if (idx >= 0 && idx < p.length())
        {
            return p.substring(idx + 1);
        }
        return p;
    }
}