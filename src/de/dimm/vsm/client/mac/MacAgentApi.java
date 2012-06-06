/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.dimm.vsm.client.mac;

import de.dimm.vsm.client.unix.*;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import de.dimm.vsm.Utilities.CryptTools;
import de.dimm.vsm.client.AttributeContainerImpl;
import de.dimm.vsm.client.Main;
import de.dimm.vsm.client.NetAgentApi;
import de.dimm.vsm.client.cdp.CDP_Param;
import de.dimm.vsm.client.cdp.CdpHandler;
import de.dimm.vsm.client.cdp.FCECdpHandler;
import de.dimm.vsm.client.cdp.FCEEventSource;
import de.dimm.vsm.client.cdp.PlatformData;
import de.dimm.vsm.client.cdp.fce.VSMCDPEventProcessor;
import de.dimm.vsm.client.cdp.fce.VSMFCEEventSource;
import de.dimm.vsm.client.jna.VSMLibC;
import de.dimm.vsm.hash.HashFunctionPool;
import de.dimm.vsm.net.AttributeContainer;
import de.dimm.vsm.net.AttributeEntry;
import de.dimm.vsm.net.AttributeList;
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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Properties;
import org.apache.commons.codec.binary.Base64;


/**
 *
 * @author Administrator
 */
public class MacAgentApi extends NetAgentApi
{

    public static final int RSRC_NETATALK = 1;
    public static final int RSRC_ES = 2;
    public static final int RSRC_XINET = 3;
    public static final int RSRC_USCORE = 4;
    public static final String NETATALK_RSRCDIR = ".AppleDouble";
    public static final String ES_RSRCDIR = ".rsrc";
    public static final String XINET_RSRCDIR = ".HSResource";
    
    //UnixFSElemAccessor fsAcess;
    
    private int rsrcMode;
    String cdpIpFilter = null;
   
   

    public MacAgentApi( HashFunctionPool hash_pool, String cdpIpFilter )
    {
        this.hash_pool = hash_pool;

        fsAcess = new MacFSElemAccessor(this);
        
        options = new Properties();

        hfManager = new MacHFManager();

       
        //if ()
        factory = new OsxRemoteFSElemFactory();
        this.cdpIpFilter = cdpIpFilter;
        
    }

    private MacFSElemAccessor getNativeAccesor()
    {
        return (MacFSElemAccessor) fsAcess;
    }

    
    

    @Override
    protected void detectRsrcMode( File[] list )
    {
        for (int i = 0; i < list.length; i++)
        {
            File file = list[i];
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
        switch (rsrcMode)
        {
            case RSRC_USCORE:
                return f.getName().startsWith("._");
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
        int real_rlen = 0;

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
        catch (IOException iOException)
        {
            System.out.println("IOException in read: " + iOException.getMessage());
            iOException.printStackTrace();
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

            byte[] data = null;
            try
            {
                data = read(wrapper, 0, (int) h.length());
            }
            catch (IOException iOException)
            {
                return null;
            }
            return data;
        }
        catch (Exception e)
        {
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

    @Override
    public boolean create_dir( RemoteFSElem dir )
    {
        File f = new File(dir.getPath());
        try
        {
            if (f.mkdir())
            {
                getNativeAccesor().setFiletime( f.getPath(), dir );

                String aclinfoData = dir.getAclinfoData();
                if (aclinfoData != null)
                {
                    AttributeContainer ac = AttributeContainer.unserialize(aclinfoData);
                    AttributeContainerImpl.set(dir, ac);
                }
                return true;
            }
        }
        catch (Exception e)
        {
        }
        return false;
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
        FCEEventSource source = new VSMFCEEventSource(12250, cdpIpFilter);
        CDPEventProcessor evp = new VSMCDPEventProcessor(cdp_param);

        cdp_handler = new FCECdpHandler( cdp_param, source, evp);
             
        if (cdp_handler.init_cdp())
        {
            ticket.setOk(true);
            addCdpHandler(ticket, cdp_handler);
            return ticket;
        }
        return ticket;
    }

   

    @Override
    public AttributeList get_attributes( RemoteFSElem elem )
    {
        AttributeList list = new AttributeList();

        // TODO SOLARIS 11 HAS NO POSIX ACL ANYMORE
        if (Main.is_solaris())
            return list;

        String path = elem.getPath();

        try
        {
            ByteBuffer buff = ByteBuffer.allocate(4096);
            int len = VSMLibC.CLibrary.INSTANCE.listxattr(path, buff, buff.capacity());

            byte[] arr = new byte[len];
            buff.get(arr, 0, len);
            String[] names = nulltermList2Array(arr);

            for (int i = 0; i < names.length; i++)
            {
                String name = names[i];
                byte[] data = null;
                if (name.equals("system.posix_acl_access") || name.equals("system.posix_acl_default"))
                {
                    Pointer acl = VSMLibC.ACLLibrary.INSTANCE.acl_get_file(path, VSMLibC.ACL_TYPE_ACCESS);
                    IntByReference acl_len = new IntByReference(0);
                    Pointer text = VSMLibC.ACLLibrary.INSTANCE.acl_to_text(acl, acl_len);

                    String s = text.getString(0);

                    VSMLibC.ACLLibrary.INSTANCE.acl_free(text);
                    VSMLibC.ACLLibrary.INSTANCE.acl_free(acl);

                    // STRING
                    data = s.getBytes();
                }
                else
                {
                    buff.rewind();
                    len = VSMLibC.getxattr(path, name, buff, buff.capacity());
                    data = new byte[len];
                    buff.get(data, 0, len);
                    // CONVERT TO STRING
                    data = Base64.decodeBase64(data);
                }
                AttributeEntry entry = new AttributeEntry(name, data);
                list.getList().add(entry);
            }
        }
        catch (Exception e)
        {
            System.out.println("Exception during get_attributes of " + path + ": " + e.getMessage());
            return null;
        }
        return list;
    }

    private String[] nulltermList2Array( byte[] arr )
    {
        if (arr.length == 0)
        {
            return new String[0];
        }

        ArrayList<String> l = new ArrayList<String>();

        int start = 0;
        int end = 0;

        while (true)
        {
            end++;
            if (end == arr.length)
            {
                if (end - start > 1)
                {
                    l.add(new String(arr, start, end - start - 1));
                }

                break;
            }
            else if (arr[end] == 0)
            {
                l.add(new String(arr, start, end));

                start = end + 1;
            }
        }
        return l.toArray(new String[0]);
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
        RemoteFSElem elem = fsAcess.get_handleData(wrapper).getElem();

        getNativeAccesor().setFiletime(elem.getPath(), elem);

        return true;
    }
    @Override
    public boolean set_attributes( RemoteFSElemWrapper wrapper )
    {
        RemoteFSElem elem = fsAcess.get_handleData(wrapper).getElem();

        getNativeAccesor().setAttributes( elem);
        getNativeAccesor().setFiletime(elem.getPath(), elem);

        return true;
    }




    @Override
    public String readAclInfo( RemoteFSElem dir )
    {
        return factory.readAclInfo(dir);
    }



}
