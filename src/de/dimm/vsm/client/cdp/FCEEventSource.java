/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.cdp;

import de.dimm.vsm.net.CdpTicket;
import java.net.SocketException;



/**
 *
 * @author Administrator
 */
public interface FCEEventSource
{
    FceEvent acceptEvent(CdpTicket ticket);
    void open(CDP_Param cdp_param)  throws SocketException;
    void close(CdpTicket ticket);
}