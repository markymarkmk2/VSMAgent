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



/*
 * Aus fce_api.h, netatalk-Code
 * 
 * Network payload of an FCE packet, version 1
 *
 *      1         2         3         4         5         6         7          8
 * +---------+---------+---------+---------+---------+---------+----------+----------+
 * |                                   FCE magic                                     |
 * +---------+---------+---------+---------+---------+---------+----------+----------+
 * | version |
 * +---------+
 * |  event  |
 * +---------+-----------------------------+
 * |               event ID                |
 * +-------------------+-------------------+ . . . .
 * |     pathlen       | path
 * +-------------------+------ . . . . . . . . . . .
 *
 *
 * Network payload of an FCE packet, version 2
 *
 *      1         2         3         4         5         6         7          8
 * +---------+---------+---------+---------+---------+---------+----------+----------+
 * |                                   FCE magic                                     |
 * +---------+---------+---------+---------+---------+---------+----------+----------+
 * | version |
 * +---------+
 * | options |
 * +---------+
 * |  event  |
 * +---------+
 * | padding |
 * +---------+---------+---------+---------+---------+---------+----------+----------+
 * |                                    reserved                                     |
 * +---------+---------+---------+---------+---------+---------+----------+----------+
 * |               event ID                |
 * +---------+---------+---------+---------+
 * ... optional:
 * +---------+---------+---------+---------+---------+---------+----------+----------+
 * |                                      pid                                        |
 * +---------+---------+---------+---------+---------+---------+----------+----------+
 * ...
 * ... optional:
 * +-------------------+----------  . . . .
 * |  username length  | username
 * +-------------------+----------  . . . .
 * ...
 * +-------------------+------  . . . . . .
 * |     pathlen       | path
 * +-------------------+------  . . . . . .
 * ... optional:
 * +-------------------+------------- . . .
 * |     pathlen       | source path
 * +-------------------+------------- . . .
 *
 * version      = 2
 * options      = bitfield:
 *                    0: pid present
 *                    1: username present
 *                    2: source path present
 * pid          = optional pid
 * username     = optional username
 * source path  = optional source path
 */

class FCEPort
{
    CDP_Param param;
    ArrayBlockingQueue<FceEvent> fce_queue;

    public FCEPort( CDP_Param param)
    {
        this.param = param;
        this.fce_queue = new ArrayBlockingQueue<>(50);
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
        if (path.startsWith(param.getPath().getPath()))
        {
            int plen = param.getPath().getPath().length();
            // SAME SIZE
            if (path.length() == plen)
                return true;

            // LONGER, THEN CHECK IF WE MATCH CORRECT PATH
            char ch = path.charAt(plen);
            if (ch == '/' || ch == '\\')
                return true;
        }
        return false;
    }

    void addEvent( FceEvent ev )
    {
        fce_queue.add(ev);
    }
}


class NetatalkUDPEventSource
{
    private static final int AT_FCE_VERSION_1 = 1;
    private static final int AT_FCE_VERSION_2 = 2;
    
    private static final int OPT_PID_PRESENT = 1;
    private static final int OPT_USERNAMER_PRESENT = 2;
    private static final int OPT_SRC_PATH_PRESENT = 4;
    
    byte[] buffer = new byte[FCECdpHandler.FCE_PACKET_HEADER_SIZE + 4096];
    String ipFilter;
    DatagramSocket socket;
    int port;

    HashMap<CdpTicket,FCEPort> fce_queue_map;
    Thread thr;
    boolean finishThread = false;

    NetatalkUDPEventSource( int port, String ipFilter )
    {
         this.port = port;
         this.ipFilter = ipFilter;
         fce_queue_map = new HashMap<>();
    }

    
    public void open(CDP_Param param)  throws SocketException
    {
        if (socket == null)
        {
            finishThread = false;
            socket = new DatagramSocket(port);
            socket.setSoTimeout(1000);
            thr = new Thread( new Runnable() {

                @Override
                public void run()
                {
                    while(!finishThread)
                    {
                        FceEvent ev = acceptEvent();
                        if (ev != null)
                        {
                            try
                            {
                                Collection<FCEPort> col = fce_queue_map.values();
                                for (FCEPort fCEPort : col)
                                {
                                    if (fCEPort.matches(ev))
                                    {
                                        fCEPort.addEvent(ev);
                                        break;
                                    }
                                }
                            }
                            catch (Exception e)
                            {
                                System.out.println("Event konnte nicht abgeliefert werden " + ev.toString() + ": " + e.getMessage() );
                            }
                        }
                    }
                    System.out.println("Stopped NetatalkUDPEventSource");
                }
            }, "NetatalkFCEPortListener" );
            thr.start();

            System.out.println("Started NetatalkUDPEventSource");
        }
        
        fce_queue_map.remove(param.getTicket());

        fce_queue_map.put(param.getTicket(), new FCEPort(param));
        System.out.println("Valid Ports:" + fce_queue_map.size() );
    }


    public void close(CdpTicket ticket)
    {
        fce_queue_map.remove(ticket);
        if (fce_queue_map.isEmpty())
        {
            finishThread = true;
            socket.close();
            socket = null;
        }
        System.out.println("Valid Ports:" + fce_queue_map.size() );
    }
    
    public FceEvent acceptEvent(CdpTicket ticket)
    {
        FCEPort fcePort = fce_queue_map.get(ticket);
        if (fcePort != null)
        {
            try
            {
                return fcePort.fce_queue.poll(1000, TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException interruptedException)
            {
            }
        }
        else
        {
            System.out.println("Unbekannter FCE-Port für ticket " + ticket.toString());
        }

        try
        {
            Thread.sleep(1000);
        }
        catch (InterruptedException interruptedException)
        {
        }
        return null;
    }

    static boolean test = false;
    static int testEvId = 1;

    private FceEvent acceptEvent()
    {
        try
        {
            //Receive request from client
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try
            {
                // WAIT FOR A SECOND
                socket.receive(packet);
            }
            catch (SocketTimeoutException socketTimeoutException)
            {
                if (test)
                {
                    FceEvent ev = new FceEvent(socket.getInetAddress(), (byte)1, (byte)0, testEvId++, 
                            (testEvId & 1) == 1 ? "/atroot/test".getBytes():"/srpool/test".getBytes());
                    return ev;
                }
                return null;
            }

            InetAddress client = packet.getAddress();
            

            if (ipFilter != null && !ipFilter.contains(client.getHostAddress()))
            {
                return null;
            }

            
            boolean skip = false;

            if (packet.getLength() < FCECdpHandler.FCE_PACKET_HEADER_SIZE)
            {
                skip = true;
            }

            if (!skip)
            {
                for (int i = 0; i < FCECdpHandler.FCE_PACKET_MAGIC.length; i++)
                {
                    if (buffer[i] != FCECdpHandler.FCE_PACKET_MAGIC[i])
                    {
                        skip = true;
                        break;
                    }
                }
            }
            if (!skip) {
                return renderEvent(client, buffer);
            }
        }
        catch (Exception ue)
        {
            System.out.println("Exception in FCE-Event Queue:" + ue.getMessage());
        }
        return null;
    }

    private FceEvent renderAtEventV1( byte[] buffer, InetAddress client, byte version ) {
        boolean skip = false;
        
        byte mode = buffer[9];

        ByteBuffer bb = ByteBuffer.wrap(buffer);
        bb.order(ByteOrder.BIG_ENDIAN);

        int event_id = bb.getInt(10);
        int path_len = bb.getChar(14);


        if (path_len < 0 || path_len > 1024)
            skip = true;

        if (skip)
            return null;

        byte[] event_data = new byte[path_len];
        System.arraycopy(buffer, 16, event_data, 0, path_len);

        FceEvent event = new FceEvent( client, version, mode, event_id, event_data);

        return event;
    }

    private FceEvent renderAtEventV2( byte[] buffer, InetAddress client, byte version ) {
        boolean skip = false;
        ByteBuffer bb = ByteBuffer.wrap(buffer);
        bb.order(ByteOrder.BIG_ENDIAN);
        
        byte options = buffer[9];
        byte mode = buffer[10]; // event in fce_api
        // Byte 11 ist gap / reserved
        int event_id = bb.getInt(12);
        int aktPos = 16;
        if ((options & OPT_PID_PRESENT) != 0) {
            //long pid = bb.getLong(aktPos);
            
        }
        aktPos += 8;
        
        // GetChar weil vorzeichenlos
        if ((options & OPT_USERNAMER_PRESENT) != 0) {
            int uNameLen = bb.getChar(aktPos);
            aktPos += 2;
            
//            byte[] uName = new byte[uNameLen];
//            System.arraycopy(buffer, aktPos + 2, uName, 0, uNameLen); // bb.get(uName, aktPos + 2, uNameLen);
            aktPos += uNameLen;
        }
        
        
        int path_len = bb.getChar(aktPos);
        aktPos += 2;

        // Plausibilität 2 Byte
        if (path_len < 0 || path_len > 32678)
            skip = true;

        if (skip)
            return null;

        byte[] event_data = new byte[path_len];
        System.arraycopy(buffer, aktPos, event_data, 0, path_len);

        FceEvent event = new FceEvent( client, version, mode, event_id, event_data);

        return event;
    }

    public FceEvent renderEvent(InetAddress client, byte[] buffer) {
        byte version = buffer[8];
        if (version == AT_FCE_VERSION_1 ) {
            return renderAtEventV1( buffer, client, version);
        }
        else if (version == AT_FCE_VERSION_2 ) {
            return renderAtEventV2( buffer, client, version);
        }
        return null;
    }
}
/**
 *
 * @author Administrator
 */
public class VSMFCEEventSource implements FCEEventSource
{
    
    static NetatalkUDPEventSource eventSource;

    public VSMFCEEventSource( int port, String ipFilter  )
    {
        if (eventSource == null)
        {
            eventSource = new NetatalkUDPEventSource(port, ipFilter);
        }
    }

    @Override
    public void open(CDP_Param cdp_param)  throws SocketException
    {
        eventSource.open(cdp_param);
    }


    @Override
    public void close(CdpTicket ticket)
    {
        eventSource.close(ticket);
    }

    @Override
    public FceEvent acceptEvent(CdpTicket ticket)
    {
        return eventSource.acceptEvent(ticket);
    }
    
}