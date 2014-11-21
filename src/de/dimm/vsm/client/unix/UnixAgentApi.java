/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.dimm.vsm.client.unix;

import de.dimm.vsm.Utilities.CryptTools;
import de.dimm.vsm.client.FSElemAccessor;
import de.dimm.vsm.client.Main;
import de.dimm.vsm.client.NetAgentApi;
import de.dimm.vsm.client.RemoteFSElemFactory;
import de.dimm.vsm.client.cdp.CDP_Param;
import de.dimm.vsm.client.cdp.CdpHandler;
import de.dimm.vsm.client.cdp.FCECdpHandler;
import de.dimm.vsm.client.cdp.FCEEventSource;
import de.dimm.vsm.client.cdp.PlatformData;
import de.dimm.vsm.client.cdp.fce.MacFCEEventSource;
import de.dimm.vsm.client.cdp.fce.VSMCDPEventProcessor;
import de.dimm.vsm.client.cdp.fce.VSMFCEEventSource;
import de.dimm.vsm.client.mac.MacRemoteFSElemFactory;
import de.dimm.vsm.hash.HashFunctionPool;
import de.dimm.vsm.net.CdpTicket;
import de.dimm.vsm.net.interfaces.AgentApi;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.net.RemoteFSElemWrapper;
import de.dimm.vsm.net.interfaces.CDPEventProcessor;
import fr.cryptohash.Digest;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Properties;


/**
 *
 * @author Administrator
 */
public class UnixAgentApi extends NetAgentApi
{

    UnixFSElemAccessor _fsAccess;
    RemoteFSElemFactory mac_factory;
    RemoteFSElemFactory uscore_factory;
    RemoteFSElemFactory ethershare_factory;
    RemoteFSElemFactory netatalk_factory;
    //RemoteFSElemFactory xinet_factory;
  
    String cdpIpFilter = null;

    @Override
    public FSElemAccessor getFSElemAccessor()
    {
        return _fsAccess;
    }

    @Override
    public RemoteFSElemFactory getFsFactory()
    {
        int r = getRsrcMode();
        switch(r)
        {
            case RSRC_HFS: return mac_factory;
            case RSRC_USCORE: return uscore_factory;
            case RSRC_ES: return ethershare_factory;
            case RSRC_NETATALK: return netatalk_factory;
            //case RSRC_XINET: return xinet_factory;
        }
        return netatalk_factory;
    }
    String getRsrcModeName( int r )
    {
        switch(r)
        {
            case RSRC_HFS: return "HFS+";
            case RSRC_USCORE: return "Apple UnderScore";
            case RSRC_ES: return "Helios EtherShare";
            case RSRC_NETATALK: return "Netatalk";
            //case RSRC_XINET: return xinet_factory;
        }
        return "unknown";
    }

    int lastrsrcMode = -1;
    @Override
    public int getRsrcMode()
    {
        int r = rsrcMode;

        int forceRsrcMode = getIntOption(OP_FORCE_RSRC, -1);
        if (forceRsrcMode != -1)
            r = forceRsrcMode;

        if (r != lastrsrcMode)
        {
            lastrsrcMode = r;
            System.out.println("Switching to RsrcMode " + getRsrcModeName(r));
        }
        return r;
    }
 

    public UnixAgentApi( HashFunctionPool hash_pool, String cdpIpFilter )
    {
        this.hash_pool = hash_pool;

        netatalk_factory = new NetatalkRemoteFSElemFactory();
        ethershare_factory = new EtherShareRemoteFSElemFactory();
        uscore_factory = new UScoreRemoteFSElemFactory();
        //xinet_factory = new XinetRemoteFSElemFactory();
        mac_factory = new MacRemoteFSElemFactory();


        _fsAccess = new UnixFSElemAccessor(this);
        
        options = new Properties();

        hfManager = new UnixHFManager(this);

        if (Main.is_solaris())
        {
            snapshot = new ZFSSnapShotHandler();
            snapshot.init();
        }
        
        this.cdpIpFilter = cdpIpFilter;

        // DEFAULT MODES AT STARTUP
        if (Main.is_osx())
        {
            rsrcMode = RSRC_HFS;
        }
        else
        {
            rsrcMode = RSRC_NETATALK;
        }
    }

    private UnixFSElemAccessor getNativeAccesor()
    {
        return  _fsAccess;
    }

    @Override
    public ArrayList<RemoteFSElem> list_roots(int mode)
    {
        return list_roots();
    }


    @Override
    protected void detectRsrcMode( File parent, File[] list )
    {
        // NO AUTO-DETECT ON MAC
        if (Main.is_osx())
        {
            rsrcMode = RSRC_HFS;
            return;
        }

        // TODO: SPEED UP WITH TEST IF RSRSC DIR UF CURRENT RSRCMODE EXISTS
        
        for (int i = 0; i < list.length; i++)
        {
            File file = list[i];
            if (!file.getName().startsWith("._"))
            {
                if (new File( file.getParentFile(), "._" + file.getName()).exists())
                {
                    rsrcMode = RSRC_USCORE;
                    break;
                }
            }
            if (file.getName().equals(NETATALK_RSRCDIR))
            {
                rsrcMode = RSRC_NETATALK;
                break;
            }
            if (file.getName().equals(ES_RSRCDIR))
            {
                rsrcMode = RSRC_ES;
                break;
            }
            if (file.getName().equals(XINET_RSRCDIR))
            {
                rsrcMode = RSRC_XINET;
                break;
            }
        }
    }



    @Override
    protected boolean isRsrcEntry( File f )
    {
        int r = getRsrcMode();

        switch (r)
        {
            case RSRC_NETATALK:
                return f.getName().equals(NETATALK_RSRCDIR);
            case RSRC_ES:
                return f.getName().equals(ES_RSRCDIR);
            case RSRC_XINET:
                return f.getName().equals(XINET_RSRCDIR);
        }
        return false;
    }


    @Override
    public byte[] rawRead(  byte[] data, RemoteFSElemWrapper wrapper, long pos, int bsize )
    {
        RandomAccessFile h = getNativeAccesor().get_handle(wrapper);
        int real_rlen;
        int xa_offset;

        int r = getRsrcMode();

        // ETHERSHARE WRITES FINDERINFO INTO FIRST 512 BYTE
        if (wrapper.isXa() && r == RSRC_ES)
        {
            xa_offset = 512;
            pos += xa_offset;
        }

        try
        {

            if (pos != h.getFilePointer())
            {
                h.seek(pos);
            }

            real_rlen = h.read(data, 0, bsize);
            
            if (real_rlen == -1)
            {
                System.out.println("read err in read at pos " + pos + " bsize:" + bsize );
                return null;
            }
        }
        catch (Exception iOException)
        {
            System.out.println("IOException in read: " + iOException.getMessage());
            //iOException.printStackTrace();
            return null;
        }

        // EMPTY REST
        if (real_rlen > 0 && real_rlen < data.length)
        {
            byte[] _data = new byte[real_rlen];
            System.arraycopy(data, 0, _data, 0, _data.length);
            data = _data;
        }
        return data;
    }

   

    @Override
    public int write( RemoteFSElemWrapper wrapper, byte[] data, long pos )
    {
        RandomAccessFile h = getNativeAccesor().get_handle(wrapper);
        if (h == null)
        {
            return -1;
        }

        int xa_offset;

        int r = getRsrcMode();

        // ETHERSHARE WRITES FINDERINFO INTO FIRST 512 BYTE
        if (wrapper.isXa() && r == RSRC_ES)
        {
            xa_offset = 512;
            pos += xa_offset;
        }


        try
        {
            if (pos >= 0)
            {
                h.seek(pos);
            }

            h.write(data);
        }
        catch (IOException iOException)
        {
            System.out.println("IOException in write: " + iOException.getMessage());
            return -1;
        }
        return data.length;
    }

    @Override
    public byte[] read_complete( RemoteFSElem file ) throws IOException
    {
        if (file.getDataSize() > Integer.MAX_VALUE)
        {
            return null;
        }

        RemoteFSElemWrapper wrapper = null;
        try
        {
            wrapper = open_data(file, AgentApi.FL_RDONLY);
            if (wrapper == null)
            {
                return null;
            }
            RandomAccessFile h = getNativeAccesor().get_handle(wrapper);

            byte[] data;
            try
            {
                data = read(wrapper, 0, (int) h.length());
            }
            catch (IOException iOException)
            {
                System.out.println("IOException in read: " + iOException.getMessage());
                return null;
            }
            return data;
        }
        catch (Exception e)
        {
            System.out.println("Exception in read: " + e.getMessage());
        }
        finally
        {
            close_data(wrapper);
        }

        return null;
    }

    @Override
    public String read_hash_complete( RemoteFSElem file, String alg ) throws IOException
    {
        RemoteFSElemWrapper wrapper = null;
        Digest digest = null;
        try
        {
            wrapper = open_data(file, AgentApi.FL_RDONLY);
            if (wrapper == null)
            {
                return null;
            }
            RandomAccessFile h = getNativeAccesor().get_handle(wrapper);


            digest = this.hash_pool.get();
            

            long len = file.getDataSize();
            long pos = 0;
            while (len > 0)
            {
                int rlen = 64 * 1024;
                if (len < rlen)
                {
                    rlen = (int) len;
                }

                byte[] data = read(wrapper, pos, rlen);

                if (data.length != rlen)
                {
                    throw new IOException("Read error");
                }
                digest.update(data, 0, rlen);
                len -= rlen;
                pos += rlen;
            }
            byte[] hash = digest.digest();
            String ret = CryptTools.encodeUrlsafe(hash);
            return ret;
        }
        catch (Exception e)
        {
            System.out.println("Exception in read: " + e.getMessage());
        }
        finally
        {
            close_data(wrapper);
            
            if (digest != null)
            {
                try
                {
                    hash_pool.release(digest);
                }
                catch (IOException iOException)
                {
                }
            }
        }


        return null;
    }


    boolean existsNetatalk()
    {
        // TODO:
        return Main.is_solaris() || Main.is_linux();
    }


    @Override
    public CdpTicket init_cdp( InetAddress addr, int port, boolean ssl, boolean tcp, RemoteFSElem file, long poolIdx, long schedIdx, long clientInfoIdx, long clientVolumeIdx ) throws SocketException
    {
        addr = validateAdressFromCommThread(addr);

        CdpTicket ticket = new CdpTicket(poolIdx, schedIdx, clientInfoIdx, clientVolumeIdx);

        CdpHandler cdp_handler = removeCdpHandler(ticket);
        if (cdp_handler != null)
        {
            cdp_handler.stop_cdp();
            cdp_handler.cleanup_cdp();
        }
        
        PlatformData pd = new UnixPlatformData();
        CDP_Param cdp_param = new CDP_Param(addr, port, ssl, tcp, file, ticket, pd);
        FCEEventSource source = null;
        if (Main.is_osx())
            source = new MacFCEEventSource();
        else if (existsNetatalk())
            source = new VSMFCEEventSource(12250, cdpIpFilter);

        CDPEventProcessor evp = new VSMCDPEventProcessor(cdp_param);

        cdp_handler = new FCECdpHandler( this, cdp_param, source, evp);
             
        if (cdp_handler.init_cdp())
        {
            ticket.setOk(true);
            addCdpHandler(ticket, cdp_handler);
            return ticket;
        }
        return ticket;
    }

   

    @Override
    public boolean set_filetimes_named( RemoteFSElem elem )
    {
        getNativeAccesor().setFiletime(elem.getPath(), elem);

        return true;
    }

    @Override
    public boolean set_filetimes( RemoteFSElemWrapper wrapper )
    {
        RemoteFSElem elem = getFSElemAccessor().get_handleData(wrapper).getElem();

        getNativeAccesor().setFiletime(elem.getPath(), elem);

        return true;
    }


}
