/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.cdp.fce;

import com.thoughtworks.xstream.XStream;
import de.dimm.vsm.client.Main;
import de.dimm.vsm.client.cdp.CDP_Param;
import de.dimm.vsm.log.LogManager;
import de.dimm.vsm.net.CdpEvent;
import de.dimm.vsm.net.interfaces.CDPEventProcessor;
import de.dimm.vsm.net.interfaces.ServerApi;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

/**
 *
 * @author Administrator
 */
public class FCEEventBuffer
{

     final String fceBufferLock = "FCEBufferLock";
     CDP_Param cdp_param;
     CDPEventProcessor eventProcessor;

    public FCEEventBuffer( CDP_Param cdp_param, CDPEventProcessor eventProcessor )
    {
        this.cdp_param = cdp_param;
        this.eventProcessor = eventProcessor;
    }

     

    public boolean check_buffered_events()
    {
        synchronized(fceBufferLock)
        {
            boolean ret = true;
            File fh = new File("FCEBuffer.txt");
            if (!fh.exists())
                return true;


            // FIRST CHECK CONNECTION
            ServerApi api = Main.getServerConn().getServerApi(cdp_param.getServer(),cdp_param.getPort(), cdp_param.isSsl(), cdp_param.isTcp());
            Properties p = api.get_properties();
            if (p == null)
                return false;

            ArrayList<String> list = new ArrayList<String>();
            FileReader fr = null;

            try
            {
                fr = new FileReader(fh);
                BufferedReader rd = new BufferedReader(fr);
                while (true)
                {
                    String line = rd.readLine();
                    if (line == null)
                    {
                        break;

                    }
                    list.add(line);
                }
            }
            catch (IOException iOException)
            {
            }
            finally
            {
                if (fr != null)
                {
                    try
                    {
                        fr.close();
                    }
                    catch (IOException iOException)
                    {
                    }
                }
            }


            for (int i = 0; i < list.size(); i++)
            {
                String string = list.get(i);
                XStream xs = new XStream();
                Object o = xs.fromXML(string);
                if (o instanceof CdpEvent)
                {
                    CdpEvent elem = (CdpEvent)o;
                    if (!eventProcessor.process(elem))
                    {
                        ret = false;
                        break;
                    }
                }
            }
            if (ret)
                fh.delete();

            return ret;
        }
    }

    public void buffer_event(CdpEvent ev )
    {
        synchronized(fceBufferLock)
        {
            XStream xs = new XStream();
            String s = xs.toXML(ev);
            s = s.replace('\n', ' ');
            s = s.replace('\r', ' ');

            File fh = new File("FCEBuffer.txt");
            FileWriter fw = null;
            try
            {
                fw = new FileWriter(fh, true);
                fw.write(s);
                fw.write("\n");

            }
            catch (IOException iOException)
            {
                LogManager.msg_system(LogManager.LVL_ERR, "Lost cdp file_change, cannot save to buffer");
            }
            finally
            {
                if (fw != null)
                {
                    try
                    {
                        fw.close();
                    }
                    catch (IOException iOException)
                    {
                    }
                }
            }
        }
    }
}
