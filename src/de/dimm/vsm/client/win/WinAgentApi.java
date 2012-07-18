/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.win;

import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import de.dimm.vsm.Utilities.CryptTools;
import de.dimm.vsm.client.FSElemAccessor;
import de.dimm.vsm.client.FileHandleData;
import de.dimm.vsm.client.NetAgentApi;
import de.dimm.vsm.client.RemoteFSElemFactory;
import de.dimm.vsm.client.cdp.CDP_Param;
import de.dimm.vsm.client.cdp.CdpHandler;
import de.dimm.vsm.client.cdp.WinCdpHandler;
import de.dimm.vsm.client.cdp.WinPlatformData;
import de.dimm.vsm.client.cdp.fce.VSMCDPEventProcessor;
import de.dimm.vsm.client.jna.LibKernel32;
import de.dimm.vsm.hash.HashFunctionPool;
import de.dimm.vsm.net.AttributeList;
import de.dimm.vsm.net.CdpTicket;
import de.dimm.vsm.net.interfaces.AgentApi;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.net.RemoteFSElemWrapper;
import de.dimm.vsm.net.interfaces.CDPEventProcessor;
import fr.cryptohash.Digest;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Properties;

class FileComparator implements Comparator<File>
{

  @Override
  public int compare(File b1, File b2)
  {
      if (b1.isDirectory())
      {
          if (!b2.isDirectory())
              return 1;
      }
      else
      {
          if (b2.isDirectory())
              return -1;
      }
      return b1.getName().compareToIgnoreCase(b2.getName());
  }
}
/**
 *
 * @author Administrator
 */
public class WinAgentApi extends NetAgentApi
{
    //WinFSElemAccessor fsAcess;
    
    public static boolean fake_read = false;
    public static boolean fake_hash = false;

    WinFSElemAccessor _fsAccess;
    RemoteFSElemFactory _factory;


    @Override
    public FSElemAccessor getFSElemAccessor()
    {
        return _fsAccess;
    }

    @Override
    public RemoteFSElemFactory getFsFactory()
    {
        return _factory;
    }


    public WinAgentApi( HashFunctionPool hash_pool )
    {
        this.hash_pool = hash_pool;
        _fsAccess = new WinFSElemAccessor(this);
        options = new Properties();


        _factory = new WinRemoteFSElemFactory();
        hfManager = new WinHFManager();
        
        snapshot = new WinSnapShotHandler();
        snapshot.init();

        
    }


    WinFSElemAccessor getNativeAccesor()
    {
        return _fsAccess;
    }


    @Override
    protected boolean isRsrcEntry( File f )
    {
        return false;
    }

    @Override
    protected void detectRsrcMode( File parent, File[] list )
    {
    }




    




    byte[] rawReadXA( WinFileHandleData data, long pos, int bsize ) throws IOException
    {
        byte[] retbuff = new byte[bsize];

        return rawReadXA(retbuff, data, pos, bsize);
    }

    
    public byte[] rawReadXA( byte[] retbuff, FileHandleData _data, long pos, int bsize ) throws IOException
    {
        WinFileHandleData data = (WinFileHandleData) _data;
        if (data.streamList == null)
        {
            data.streamList = data.readStreamList();
            if (data.streamList.isEmpty())
                throw new IOException("Cannot read stream list");
        }

        int retbuff_offset = 0;


        // SKIP OVER ALREADY READ DATA
        Iterator<StreamEntry> it = data.streamList.iterator();

        // FIND THE LAST BEGUN BLOCK
        StreamEntry actStream = it.next();
        long act_stream_start_pos = 0;
        while (act_stream_start_pos < pos)
        {
            if (act_stream_start_pos + actStream.getSize() < pos)
            {
                act_stream_start_pos += actStream.getSize();
                actStream = it.next();
                continue;
            }
            break;
        }

        while(retbuff_offset < bsize)
        {
            // NOW WE ARE IN THE CORRECT STREAM
            byte[] dataArr = actStream.getArray();

            // THIS IS WHAT WE STILL NEED TO READ FROM THE ACTUAL BLOCK
            // RESTLEN IS <= SIZE OF THIS STREAM
            long act_stream_end = act_stream_start_pos + actStream.getSize();

            // WE START HERE IN THIS STREAM
            int offset_into_stream = (int)(pos - act_stream_start_pos);
            
            // IF WE STARTED IN AN EARLIER STREAM, WE START IN THIS ONE FROM THE BEGINNING
            if (offset_into_stream < 0)
                offset_into_stream = 0;

            // SEST FOR THIS BLOCK
            int restlen = (int)( bsize - retbuff_offset);
            

            // TOO LARGE FOR THIS STREAM?
            if (restlen > (actStream.getSize() - offset_into_stream))
                restlen = (int)(actStream.getSize() - offset_into_stream);

           

            System.arraycopy(dataArr, offset_into_stream, retbuff, retbuff_offset, restlen);

            retbuff_offset += restlen;

            // IF WE STILL NEED MORE DATA, WE TAKE NEXT STREAM
            if (retbuff_offset < bsize)
            {
                act_stream_start_pos  += actStream.getSize();

                if (!it.hasNext())
                {
                    // WE HAVE MORE TO READ MORE DATA BUT STREAM DATA IST EMPTY -> SHORT READ
                    byte[] buff = new byte[retbuff_offset];

                    // CREATE SHIRT RESULT AND LEAVE
                    System.arraycopy(retbuff, 0, buff, 0, retbuff_offset);
                    retbuff = buff;
                    break;
                }
                actStream = it.next();
            }
        }
        return retbuff;
    }



    @Override
    public byte[] rawRead( byte[] data, RemoteFSElemWrapper wrapper, long pos, int bsize )
    {
        WinFileHandleData hdata = (WinFileHandleData)getFSElemAccessor().get_handleData(wrapper);
        if (hdata == null)
            return null;
        
        HANDLE h = hdata.handle;
        if (h == null)
            return null;

        if (wrapper.isXa())
        {
            try
            {
                return rawReadXA(data, hdata, pos, bsize);
            }
            catch (IOException iOException)
            {
                System.out.println("Error reading xa_data: " + iOException.getMessage());
                return null;
            }
        }

        LongByReference actPos = new LongByReference();

        LibKernel32.SetFilePointerEx(h, 0, actPos, LibKernel32.FILE_CURRENT);

        if (pos != actPos.getValue())
        {
            if (!LibKernel32.SetFilePointerEx(h, pos, null, LibKernel32.FILE_BEGIN))
                return null;
        }

       
        IntByReference real_rlen = new IntByReference();
        
        if (!LibKernel32.ReadFile(h, data, bsize, real_rlen, null))
            return null;

        // EMPTY REST
        if (real_rlen.getValue() < data.length)
        {
            byte[] _data = new byte[real_rlen.getValue()];
            System.arraycopy(data, 0, _data, 0, _data.length);
            data = _data;
        }
        return data;
    }

    

    // THIS HAS TO BE ASCENDING AND W/O GAPS
    
    public int rawWriteXA( RemoteFSElemWrapper wrapper, byte[] data, long pos )
    {
        HANDLE h = getNativeAccesor().get_handle(wrapper);
        if (h == null)
            return -1;

        // APPEND TO EXISTING FILE
        if (!LibKernel32.SetFilePointerEx(h, 0, null, LibKernel32.FILE_END))
            return -1;

        WinFileHandleData hd = (WinFileHandleData)getFSElemAccessor().get_handleData(wrapper);

            byte[] arr = new byte[LibKernel32.WINSTREAM_ID_SIZE];
            System.arraycopy(data, 0, arr, 0, arr.length);
            LibKernel32.WIN32_STREAM_ID streamInfo =  WinRemoteFSElemFactory.get_stream_id( arr, 0 );


        int written = 0;
        while (written < data.length)
        {
            IntByReference real_wlen = new IntByReference();
            byte[] d = data;
            if (written > 0)
            {
                d = new byte[data.length - written];
                System.arraycopy(data, written, d, 0, data.length - written);
            }

            if (!LibKernel32.BackupWrite(h, d, d.length, real_wlen, /*abort*/ false, /*processSec*/true, /*context*/hd.context))
            {
                int err = LibKernel32.GetLastError();
                return -1;
            }
            written += real_wlen.getValue();
        }

        return written;
    }

    @Override
    public int write( RemoteFSElemWrapper wrapper, byte[] data, long pos )
    {
        if (wrapper.isXa())
        {
            return rawWriteXA( wrapper, data, pos);
        }

        HANDLE h = getNativeAccesor().get_handle(wrapper);
        if (h == null)
            return -1;

        if (pos >= 0)
        {
            if (!LibKernel32.SetFilePointerEx(h, pos, null, LibKernel32.FILE_BEGIN))
                return -1;
        }


        IntByReference real_wlen = new IntByReference();
        if (!LibKernel32.WriteFile(h, data, data.length, real_wlen, null))
        {
            int err = LibKernel32.GetLastError();
            return -1;
        }

        return real_wlen.getValue();
    }

    @Override
    public byte[] read_complete( RemoteFSElem file ) throws IOException
    {
        if (file.getDataSize() > Integer.MAX_VALUE)
            return null;

        RemoteFSElemWrapper wrapper = null;
        try
        {
            wrapper = open_data(file, AgentApi.FL_RDONLY);
            if (wrapper == null)
            {
                return null;
            }
            HANDLE h = getNativeAccesor().get_handle(wrapper);

            IntByReference real_rlen = new IntByReference();

            byte[] data = new byte[(int) file.getDataSize()];

            if (!LibKernel32.ReadFile(h, data, data.length, real_rlen, null))
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
        try
        {
            wrapper = open_data(file, AgentApi.FL_RDONLY);
            if (wrapper == null)
            {
                return null;
            }
            HANDLE h = getNativeAccesor().get_handle(wrapper);

            IntByReference real_rlen = new IntByReference();

            byte[] data = new byte[64*1024];
            Digest digest = this.hash_pool.get();
            

            long len = file.getDataSize();

            while (len > 0)
            {
                int rlen = data.length;
                if (len < rlen)
                    rlen = (int) len;

                LibKernel32.ReadFile(h, data, rlen, real_rlen, null);

                if ( real_rlen.getValue() != rlen)
                {
                    throw new IOException("Read error");
                }
                digest.update(data, 0, rlen);
                len -= rlen;
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
        }

        return null;
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
        
        WinPlatformData pd = new WinPlatformData();
        CDP_Param cdp_param = new CDP_Param(addr, port, ssl, tcp, file, ticket, pd);
        CDPEventProcessor evp = new VSMCDPEventProcessor(cdp_param);

        cdp_handler = new WinCdpHandler( cdp_param, evp );
        
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
        HANDLE h = getNativeAccesor().open_raw_handle(elem, FL_RDWR);
        if (h != null)
        {
            getNativeAccesor().setFiletime(h, elem);
            getNativeAccesor().close_raw_handle(h);
            return true;
        }
        return false;
    }

   
    @Override
    public boolean set_filetimes( RemoteFSElemWrapper wrapper )
    {
        HANDLE h = getNativeAccesor().get_handle(wrapper);
//        HANDLE h = getNativeAccesor().get_handleData(wrapper).handle;
        RemoteFSElem elem  = getFSElemAccessor().get_handleData(wrapper).getElem();
        if (h != null)
        {
            getNativeAccesor().setFiletime(h, elem);
            return true;
        }
        else
        {
            set_filetimes_named( elem );
        }
        return false;
    }







  
    
}
