/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.cdp.fce;

import de.dimm.vsm.client.cdp.CDP_Param;
import de.dimm.vsm.client.cdp.FCECdpHandler;
import de.dimm.vsm.client.cdp.FCEEventSource;
import de.dimm.vsm.client.cdp.FceEvent;
import de.dimm.vsm.net.CdpTicket;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

class FCEPort
{
    CDP_Param param;
    ArrayBlockingQueue<FceEvent> fce_queue;

    public FCEPort( CDP_Param param)
    {
        this.param = param;
        this.fce_queue = new ArrayBlockingQueue<FceEvent>(50);
    }



    @Override
    public boolean equals( Object obj )
    {
        if (obj instanceof FCEPort)
        {
            FCEPort p = (FCEPort)obj;
            return p.param.getTicket().equals(param.getTicket());
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode()
    {
        return param.getTicket().hashCode();
    }

    String getPath()
    {
        return param.getPath().getPath();
    }

    ArrayBlockingQueue getQueue()
    {
        return fce_queue;
    }

    boolean matches( FceEvent ev )
    {
        String path = ev.getPath();
        return path.startsWith(param.getPath().getPath());
    }

    void addEvent( FceEvent ev )
    {
        fce_queue.add(ev);
    }
}



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