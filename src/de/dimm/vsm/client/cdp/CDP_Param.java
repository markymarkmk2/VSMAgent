/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.cdp;

import de.dimm.vsm.net.CdpTicket;
import de.dimm.vsm.net.ExclListEntry;
import de.dimm.vsm.net.RemoteFSElem;
import java.net.InetAddress;
import java.util.ArrayList;

/**
 *
 * @author Administrator
 */
public class CDP_Param
{
    public static final String CDP_PROP_MIN_QUEUE_ENTRY_AGE = "CDP_MinQueueEntryAge";
    public static final String CDP_PROP_MAX_QUEUE_LEN = "CDP_MaxnQueueLen";

    RemoteFSElem path;
    //Callbacks callbacks;
    PlatformData platformData;
    ArrayList<ExclListEntry> excludeList;
    boolean wantsFinish;

    CdpTicket ticket;
    InetAddress server;
    int port;
    boolean ssl;
    boolean tcp;

    public CDP_Param(InetAddress server, int port, boolean ssl, boolean tcp, 
            RemoteFSElem _path, CdpTicket ticket,  PlatformData pd)
    {
        this.server = server;
        this.port = port;
        this.ssl = ssl;
        this.tcp = tcp;
        this.ticket = ticket;

        path = _path;
//        int _min_queue_entry_age = PropHelper.getIntProp( cdp_props, CDP_PROP_MIN_QUEUE_ENTRY_AGE, 1000 );
//        int _max_queue_len =  PropHelper.getIntProp( cdp_props, CDP_PROP_MAX_QUEUE_LEN, 500 );
        

        //callbacks = new Callbacks(_min_queue_entry_age, _max_queue_len, this);
        platformData = pd;
        wantsFinish = false;
    }

    public InetAddress getServer()
    {
        return server;
    }

    public int getPort()
    {
        return port;
    }

    public boolean isSsl()
    {
        return ssl;
    }

    public boolean isTcp()
    {
        return tcp;
    }

    public CdpTicket getTicket()
    {
        return ticket;
    }
    
    

    public void setPlatformData(PlatformData pfd)
    {
        platformData = pfd;
    }

    public PlatformData getPlatformData()
    {
        return platformData;
    }
//    public Callbacks getCallbacks()
//    {
//        return callbacks;
//    }
    

    public RemoteFSElem getPath()
    {
        return path;
    }

    public void set_error(  String string )
    {
        ticket.setErrorText(string);
    }

    public boolean wants_finish()
    {
        return wantsFinish;
    }

    public void setWantsFinish( boolean wantsFinish )
    {
        this.wantsFinish = wantsFinish;
    }
    

    public ArrayList<ExclListEntry> getExcludeList()
    {
        return excludeList;
    }

   
}
