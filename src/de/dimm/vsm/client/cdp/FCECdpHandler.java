/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.dimm.vsm.client.cdp;


import de.dimm.vsm.client.NetAgentApi;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.net.interfaces.CDPEventProcessor;
import java.net.SocketException;











/**
 *
 * @author Administrator
 */
public class FCECdpHandler extends CdpHandler
{
    Thread idle_thr;

    /* fce_packet.fce_magic */
    public static final byte[] FCE_PACKET_MAGIC = "at_fcapi".getBytes();
    public static final int FCE_PACKET_HEADER_SIZE = 8 + 1 + 1 + 4 + 2;


    public FCECdpHandler(NetAgentApi agentApi, CDP_Param p, FCEEventSource eventSource, CDPEventProcessor eventProcessor)
    {
        super(agentApi, p,  eventSource, eventProcessor);               
    }

    @Override
    public boolean init_cdp()
    {
        abort = false;
        finished = false;

        try
        {
            start_cdp();
        }
        catch (Exception exception)
        {
            cdp_param.set_error("Start CDP schlägt fehl" + exception.toString());
            return false;
        }
        
        return true;
    }



    @Override
     boolean check_invalid_cdp_path( RemoteFSElem fullpath, String object_name )
    {
        char deli = fullpath.getSeparatorChar();
        String[] roots = cdp_param.getPlatformData().getSkipRoots();
        String rootdir = null;
        int idx = fullpath.getPath().indexOf(deli, 1);
        if (idx > 0)
        {
            rootdir = fullpath.getPath().substring(1, idx);
        }
        else if (fullpath.getPath().length() > 1)
        {
            rootdir = fullpath.getPath().substring(1);
        }
        if (rootdir != null)
        {
            for (int i = 0; i < roots.length; i++)
            {
                if (rootdir.equals(roots[i]))
                    return true;
            }
        }
        
        String file = object_name;
//        idx = fullpath.getPath().lastIndexOf('/');
//        if (idx > 0)
//        {
//            file = fullpath.getPath().substring(idx + 1);
//        }
//        else
//        {
//            file = fullpath.getPath().substring(1);
//        }


        String[] files = cdp_param.getPlatformData().getSkipFiles();

        if (file != null)
        {
            for (int i = 0; i < files.length; i++)
            {
                if (file.equals(files[i]))
                    return true;
            }
        }
        String path = fullpath.getPath();
        String[] paths = cdp_param.getPlatformData().getSkipPaths();
        for (int i = 0; i < paths.length; i++)
        {
            if (path.indexOf(paths[i]) >= 0)
                return true;
        }

        return false;
    }

}
