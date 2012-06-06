/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client;

import de.dimm.vsm.client.cdp.CdpHandler;
import de.dimm.vsm.hash.HashFunctionPool;
import de.dimm.vsm.net.CdpTicket;
import de.dimm.vsm.net.interfaces.AgentApi;
import de.dimm.vsm.net.interfaces.SnapshotHandler;
import java.util.HashMap;
import java.util.Properties;

/**
 *
 * @author Administrator
 */
public abstract class NetAgentApi implements AgentApi
{

    protected HashFunctionPool hash_pool;
    protected Properties options;
    protected SnapshotHandler snapshot;
    //protected CdpHandler cdp_handler;

    private HashMap<CdpTicket,CdpHandler> cdpHandlerMap = new HashMap<CdpTicket, CdpHandler>();
    protected RemoteFSElemFactory factory;
    protected HFManager hfManager;
    //protected FileCacheManager cacheManager;

    int lastRdqlen = -1;
    int lastRdyqlen = -1;
    int lastHTherads = -1;
    void idle()
    {
//        int rdqlen = cacheManager.getReadQueueLen();
//        int hThreads = cacheManager.getActiveHashThreads();
//        int rdyqlen = cacheManager.getReadyQueueLen();
//
//        if (rdqlen != lastRdqlen || hThreads != lastHTherads || rdyqlen != lastRdyqlen)
//        {
//            lastRdqlen = rdqlen;
//            lastRdyqlen = rdyqlen;
//            lastHTherads = hThreads;
//            if (Main.isVerbose())
//            {
//                System.out.println("ReadQ: " + rdqlen + " ReadyQ: " + rdyqlen + " HashThreads: " + hThreads);
//            }
//        }
    }

    public abstract FSElemAccessor getFSElemAccessor();

    protected CdpHandler getCdpHandler( CdpTicket t )
    {
        return cdpHandlerMap.get(t);
    }
    protected CdpHandler removeCdpHandler( CdpTicket t )
    {
        return cdpHandlerMap.remove(t);
    }
    protected void addCdpHandler( CdpTicket t, CdpHandler h )
    {
        cdpHandlerMap.put(t, h);
    }

}
