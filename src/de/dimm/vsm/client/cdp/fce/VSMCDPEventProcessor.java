/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.cdp.fce;

import de.dimm.vsm.client.Main;
import de.dimm.vsm.client.cdp.CDP_Param;
import de.dimm.vsm.net.CdpEvent;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.net.interfaces.CDPEventProcessor;
import de.dimm.vsm.net.interfaces.ServerApi;

/**
 *
 * @author Administrator
 */
public class VSMCDPEventProcessor implements CDPEventProcessor
{
    CDP_Param cdp_param;

    public VSMCDPEventProcessor( CDP_Param hdl )
    {
        this.cdp_param = hdl;
    }


    @Override
    public boolean process( CdpEvent ev )
    {
        
        // REQUEST SERVER CONNET
        try
        {
            RemoteFSElem elem = ev.getElem();
            if (elem.getPath() != null && elem.getPath().isEmpty())
            {
                System.out.println("Skipping event " + ev.toString());
                return true;
            }
            System.out.println("Processing <" + elem.getPath() + ">");
            ServerApi api = Main.getServerConn().getServerApi(cdp_param.getServer(), cdp_param.getPort(), cdp_param.isSsl(), cdp_param.isTcp());


            // AND SEND CALL
            boolean ret = api.cdp_call(ev, cdp_param.getTicket());
            return ret;
        }
        catch (Exception e)
        {
            System.out.println("Error processing " + ev.toString() + ": " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
}