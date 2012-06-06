/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.dimm.vsm.client.cdp.fce;

import de.dimm.vsm.client.cdp.FceEvent;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
/*
 *
#define FCE_PACKET_HEADER_SIZE 8+1+1+4+2
struct fce_packet
{
char magic[8];
unsigned char version;
unsigned char mode;
uint32_t event_id;
uint16_t datalen;
char data[MAXPATHLEN];
};
 */

/**
 *
 * @author Administrator
 */
public class FCEListener implements Runnable
{
    /* fce_packet.mode */

    /* fce_packet.fce_magic */
    public static final byte[] FCE_PACKET_MAGIC = "at_fcapi".getBytes();
    private static final int FCE_PACKET_HEADER_SIZE = 8 + 1 + 1 + 4 + 2;

    private static final int MAX_FCE_EVENTS = 100000;

    DatagramSocket socket;
    int port;

    final ArrayBlockingQueue<FceEvent> fce_event_list;

    boolean abort;
    boolean finished;
    String ipFilter;

    public FCEListener(String ipFilter )
    {
        this(12250, ipFilter);
    }
    public FCEListener( int port, String ipFilter )
    {
        this.port = port;
        this.ipFilter = ipFilter;
        this.fce_event_list = new ArrayBlockingQueue<FceEvent>(MAX_FCE_EVENTS, /*fair*/true);
    }

    static int lc = 0;
    public void start()
    {
        abort = false;
        finished = false;

        Thread thr = new Thread( this, "FCEListener");

        thr.start();


        Thread _thr = new Thread( new Runnable()
        {

            public void run()
            {
                while (!abort)
                {
                    try
                    {
                        FceEvent ev = fce_event_list.poll(100, TimeUnit.MILLISECONDS);
                        if (ev != null)
                        {
                            process( ev );
                        }
                    }
                    catch (InterruptedException interruptedException)
                    {
                    }
                }
            }
        }, "FCEWorker");

        _thr.start();
    }

    public void stop()
    {
        abort = true;
        if (socket != null)
            socket.close();

        // WAIT 5 SECS
        int wait_s = 500;
        while(!finished && wait_s > 0)
        {
            try
            {
                Thread.sleep(100);
            }
            catch (InterruptedException interruptedException)
            {
            }
        }
    }

    public boolean isFinished()
    {
        return finished;
    }

    public void process( FceEvent ev )
    {
        System.out.println("Processed from " + ev.getClient().getHostAddress() +  ": " + ev.toString());

    }



//
//
//    byte[] htonl( int x )
//    {
//        byte[] res = new byte[4];
//        for (int i = 0; i < 4; i++)
//        {
//            res[i] = (new Integer(x >>> 24)).byteValue();
//            x <<= 8;
//        }
//        return res;
//    }
//
//    int ntohl( byte[] x, int offset )
//    {
//        int res = 0;
//        for (int i = 0; i < 4; i++)
//        {
//            res <<= 8;
//            res |= (int) x[i + offset];
//        }
//        return res;
//    }
//    int ntohs( byte[] x, int offset )
//    {
//        int res = 0;
//        for (int i = 0; i < 2; i++)
//        {
//            res <<= 8;
//            res |= (int) x[i + offset];
//        }
//        return res;
//    }

    public void run()
    {
        byte[] buffer = new byte[FCE_PACKET_HEADER_SIZE + 1024];

        try
        {
            socket = new DatagramSocket(port);
            while (true)
            {
                try
                {
                    //Receive request from client
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    InetAddress client = packet.getAddress();
                    int client_port = packet.getPort();

                    if (ipFilter != null && ipFilter.equals(client.getHostAddress()))
                    {
                        continue;
                    }
                    
                    System.out.println("received packet form " + client.getHostAddress() +  " with " + packet.getLength() + " byte");

                    boolean skip = false;

                    if (packet.getLength() < FCE_PACKET_HEADER_SIZE)
                    {
                        skip = true;
                    }

                    if (!skip)
                    {
                        for (int i = 0; i < FCE_PACKET_MAGIC.length; i++)
                        {
                            if (buffer[i] != FCE_PACKET_MAGIC[i])
                            {
                                skip = true;
                                break;
                            }
                        }
                    }

                    byte version = buffer[8];
                    byte mode = buffer[9];

   
                    ByteBuffer bb = ByteBuffer.wrap(buffer);
                    bb.order(ByteOrder.BIG_ENDIAN);

                    int event_id = bb.getInt(10);
                    int path_len = bb.getChar(14);
                    

                    if (path_len < 0 || path_len > 1024)
                        skip = true;

                    if (skip)
                        continue;

                    byte[] event_data = new byte[path_len];
                    System.arraycopy(buffer, 16, event_data, 0, path_len);

                    FceEvent event = new FceEvent( client, version, mode, event_id, event_data);

                    if (! fce_event_list.offer(event))
                    {
                        System.out.println("FCE-Event Queue Overrun!");
                    }
                }
                catch (Exception ue)
                {
                    System.out.println("Exception in FCE-Event Queue:" + ue.getMessage());
                }
            }
        }
        catch (Exception b)
        {
            System.out.println("cannot start FCE-Event Listener: " + b.getMessage());
        }
        finished = true;
    }
}
