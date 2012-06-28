/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.cdp.fce;

import de.dimm.vsm.client.cdp.CDP_Param;
import de.dimm.vsm.client.cdp.FCEEventSource;
import de.dimm.vsm.client.cdp.FceEvent;
import de.dimm.vsm.net.CdpTicket;
import java.net.SocketException;





/**
 *
 * @author Administrator
 */
public class MacFCEEventSource implements FCEEventSource
{
   

    
    static FileSystemWatcher eventSource;

    public MacFCEEventSource( )
    {
        if (eventSource == null)
        {
            eventSource = FileSystemWatcher.getDefault();
        }
    }

    @Override
    public void open(CDP_Param cdp_param)  throws SocketException
    {
        // START ON DEMAND
        if (eventSource.countWatches() == 0)
        {
            try
            {
                eventSource.start();
            }
            catch (Exception exception)
            {
                System.out.println("Starting of MAc Eventqueue failed: " + exception.getMessage());
                exception.printStackTrace();
            }
        }
        eventSource.addWatch(cdp_param);
    }


    @Override
    public void close(CdpTicket ticket)
    {
        eventSource.removeWatch(ticket);

        // STOP ON DEMAND
        if (eventSource.countWatches() == 0)
        {
            try
            {
                eventSource.stop();
            }
            catch (Exception exception)
            {
                System.out.println("Starting of MAc Eventqueue failed: " + exception.getMessage());
                exception.printStackTrace();
            }
        }
    }

    @Override
    public FceEvent acceptEvent(CdpTicket ticket)
    {
        FceEvent ev = null;
        try
        {
            ev = eventSource.nextEvent();
        }
        catch (Exception exc)
        {
            System.out.println("acceptEvent of MAc Eventqueue failed: " + exc.getMessage());
        }
        return ev;
    }
    
}