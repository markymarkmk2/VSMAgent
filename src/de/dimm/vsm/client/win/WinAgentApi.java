/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.win;

import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import de.dimm.vsm.Utilities.CommThread;
import de.dimm.vsm.Utilities.CryptTools;
import de.dimm.vsm.client.FSElemAccessor;
import de.dimm.vsm.client.MultiThreadedFileReader;
import de.dimm.vsm.client.Main;
import de.dimm.vsm.client.NetAgentApi;
import de.dimm.vsm.client.cdp.CDP_Param;
import de.dimm.vsm.client.cdp.CdpHandler;
import de.dimm.vsm.client.cdp.WinCdpHandler;
import de.dimm.vsm.client.cdp.WinPlatformData;
import de.dimm.vsm.client.cdp.fce.VSMCDPEventProcessor;
import de.dimm.vsm.client.jna.LibKernel32;
import de.dimm.vsm.fsutils.MountVSMFS;
import de.dimm.vsm.fsutils.VSMFS;
import de.dimm.vsm.hash.HashFunctionPool;
import de.dimm.vsm.net.AttributeList;
import de.dimm.vsm.net.CdpTicket;
import de.dimm.vsm.net.HashDataResult;
import de.dimm.vsm.net.StoragePoolWrapper;
import de.dimm.vsm.net.interfaces.AgentApi;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.net.RemoteFSElemWrapper;
import de.dimm.vsm.net.interfaces.CDPEventProcessor;
import de.dimm.vsm.net.interfaces.SnapshotHandle;
import fr.cryptohash.Digest;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
    WinFSElemAccessor cache;
    
    public static boolean fake_read = false;
    public static boolean fake_hash = false;
    

    public WinAgentApi( HashFunctionPool hash_pool )
    {
        this.hash_pool = hash_pool;
        this.cache = new WinFSElemAccessor(this);
        options = new Properties();


        factory = new WinRemoteFSElemFactory();
        hfManager = new WinHFManager();
        
        snapshot = new WinSnapShotHandler();
        snapshot.init();

        
    }


    InetAddress validateAdressFromCommThread( InetAddress addr)
    {

        Thread thr = Thread.currentThread();
        if (thr instanceof CommThread)
        {
            CommThread cthr = (CommThread)thr;
            Socket s = cthr.getSocket();
            SocketAddress sadr = s.getRemoteSocketAddress();
            if (sadr instanceof InetSocketAddress)
            {
                InetSocketAddress iadr = (InetSocketAddress)sadr;
                if (!iadr.getAddress().equals(addr))
                {
                    System.out.println("Remote address does not match, using Socket address");
                    addr = iadr.getAddress();
                }
            }
        }
        return addr;
    }

    @Override
    public ArrayList<RemoteFSElem> list_dir( RemoteFSElem dir, boolean lazyAclInfo )
    {
        File fh = new File(  dir.getPath() );
        File[] files = fh.listFiles();

        ArrayList<RemoteFSElem> list = new ArrayList<RemoteFSElem>();
        if (files == null)
        {
            System.out.println("No files for " + dir.getPath());
            return list;
        }

        Arrays.sort(files, new FileComparator());

        for (int i = 0; i < files.length; i++)
        {
            File file = files[i];
            if (file.exists())
            {
                RemoteFSElem elem = factory.create_elem(file, lazyAclInfo);
                list.add( elem );
            }
        }
        return list;
    }
    
    @Override
    public ArrayList<RemoteFSElem> list_roots()
    {
        File[] files = File.listRoots();

        ArrayList<RemoteFSElem> list = new ArrayList<RemoteFSElem>();
        if (files == null)
        {
            System.out.println("No Roots available");
            return list;
        }

        Arrays.sort(files, new FileComparator());
        
        for (int i = 0; i < files.length; i++)
        {
            File file = files[i];
            if (file.exists())
            {
                RemoteFSElem elem = factory.create_elem(file, true);
                list.add( elem );
            }
        }
        return list;

    }
/*
    @Override
    public InputStream open_is_data( RemoteFSElem file ) throws FileNotFoundException
    {
        return new FileInputStream(new File( file.getPath() ) );
        
        
    }

    @Override
    public InputStream open_is_attribute( RemoteFSElem file, String attr_name )
    {
        return null;
    }
    @Override
    public InputStream open_is_acls( RemoteFSElem file)
    {
        return null;
    }

    @Override
    public OutputStream open_os_data( RemoteFSElem file ) throws FileNotFoundException
    {
        return new FileOutputStream(new File( file.getPath() ) );

    }

    @Override
    public OutputStream open_os_attribute( RemoteFSElem file, String attr_name )
    {
        return null;
    }

    @Override
    public OutputStream open_os_acls( RemoteFSElem file )
    {
        return null;
    }
*/
    @Override
    public Properties get_properties()
    {
        Properties p = new Properties();

        p.setProperty(OP_OS, System.getProperty("os.name"));
        p.setProperty(OP_OS_VER, System.getProperty("os.version"));
        p.setProperty(OP_AG_VER, Main.get_version());

        return p;
    }

    @Override
    public void set_options( Properties p )
    {
        options = p;
    }


    @Override
    public String read_hash( RemoteFSElemWrapper wrapper, long pos, int bsize, String alg ) throws IOException
    {
        byte[] data = null;

        // READ VIA CACHE MANAGER
        WinFileHandleData hdata = cache.get_handleData(wrapper);

        if (pos == 0)
        {
            long totalLen = 0;
            if (wrapper.isXa())
                totalLen = hdata.elem.getStreamSize();
            else
                totalLen = hdata.elem.getDataSize();
            if (totalLen > 2* bsize)
            {
                MultiThreadedFileReader mtfr = cache.createMultiThreadedFileReader(this, wrapper);
                if (mtfr != null)
                {
                    hdata.setPrefetch(true);
                    mtfr.startFile(this, wrapper, hdata.elem, bsize);
                }
            }
        }

        if (hdata.isPrefetch())
        {
            MultiThreadedFileReader mtfr = cache.getMultiThreadedFileReader(wrapper);
            if (mtfr != null)
            {
                String hashValue = mtfr.getHash(pos, bsize);
                if (hashValue != null)
                    return hashValue;
                else
                    System.out.println("Error getting hash from cache manager pos " + pos + " " + bsize);
            }
            else
            {
                System.out.println("Cannot get mtfr in read_hash");
            }
        }

        data = _read(wrapper, pos, bsize);

        String ret = null;
        try
        {
            Digest digest = hash_pool.get();

            byte[] hash = digest.digest(data);
            ret = CryptTools.encodeUrlsafe(hash);

            hash_pool.release(digest);
        }
        catch (Exception iOException)
        {
            System.out.println("Cannot create hash");
        }
        return ret;
    }

    @Override
    public RemoteFSElemWrapper open_data( RemoteFSElem file, int flags ) throws IOException
    {
        RemoteFSElemWrapper wrapper = cache.open_handle(file, flags);

        return wrapper;
    }

    @Override
    public RemoteFSElemWrapper open_stream_data( RemoteFSElem file, int flags ) throws IOException
    {
        RemoteFSElemWrapper wrapper = cache.open_xa_handle(file, flags);

        return wrapper;
    }

    @Override
    public boolean close_data( RemoteFSElemWrapper wrapper ) throws IOException
    {
        return cache.close_handle(wrapper);
    }



    byte[] read_xa( WinFileHandleData data, long pos, int bsize ) throws IOException
    {
        byte[] retbuff = new byte[bsize];

        return read_xa(retbuff, data, pos, bsize);
    }


    byte[] read_xa( byte[] retbuff, WinFileHandleData data, long pos, int bsize ) throws IOException
    {        
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
    public byte[] read( RemoteFSElemWrapper wrapper, long pos, int bsize )
    {
        byte[] data = null;

        // READ VIA CACHE MANAGER
        WinFileHandleData hdata = cache.get_handleData(wrapper);

        if (hdata.isPrefetch())
        {
            MultiThreadedFileReader mtfr = cache.getMultiThreadedFileReader(wrapper);
            if (mtfr != null)
            {
                if (wrapper.isXa())
                {

                    data = mtfr.getXAData( pos, bsize);
                }
                else
                {
                    data = mtfr.getData( pos, bsize);
                }
                if (data != null)
                {
                    return data;
                }
            }
            else
            {
                System.out.println("Cannot get mtfr in read");
            }
        }
        data = _read(wrapper, pos, bsize);

        return data;
    }

    public byte[] _read( RemoteFSElemWrapper wrapper, long pos, int bsize )
    {
        byte[] block = new byte[bsize];
        return _read(block, wrapper, pos, bsize);
    }

    public byte[] _read( byte[] data, RemoteFSElemWrapper wrapper, long pos, int bsize )
    {
        WinFileHandleData hdata = cache.get_handleData(wrapper);
        if (hdata == null)
            return null;
        
        HANDLE h = hdata.handle;
        if (h == null)
            return null;

        if (wrapper.isXa())
        {
            try
            {
                return read_xa(data, hdata, pos, bsize);
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

    static byte[] fake_data;
    @Override
    public HashDataResult read_and_hash( RemoteFSElemWrapper wrapper, long pos, int bsize ) throws IOException
    {
        byte[] data = null;

        // READ VIA CACHE MANAGER
        
        WinFileHandleData hdata = cache.get_handleData(wrapper);

        if (pos == 0)
        {
            long totalLen = 0;
            if (wrapper.isXa())
                totalLen = hdata.elem.getStreamSize();
            else
                totalLen = hdata.elem.getDataSize();
            if (totalLen > 2* bsize)
            {
                MultiThreadedFileReader mtfr = cache.createMultiThreadedFileReader(this, wrapper);
                if (mtfr != null)
                {
                    hdata.setPrefetch(true);
                    mtfr.startFile(this, wrapper, hdata.elem, bsize);
                }
            }
        }

        if (hdata.isPrefetch())
        {
            MultiThreadedFileReader mtfr = cache.getMultiThreadedFileReader(wrapper);
            if (mtfr != null)
            {
                if (wrapper.isXa())
                {
                    data = mtfr.getXAData( pos, bsize);
                }
                else
                {
                    data = mtfr.getData( pos, bsize);
                }
                if (data != null)
                {
                    String hashValue = mtfr.getHash(pos, bsize);

                    HashDataResult ret = new HashDataResult( hashValue, data);

                    return ret;
                }
            }
            else
            {
                System.out.println("Missing mtfr in read_and_hash");
            }
        }
        

        if (fake_read)
        {
            if (fake_data == null || fake_data.length != bsize)
                fake_data = new byte[bsize];

            data = fake_data;
        }
        else
        {
            data = _read( wrapper, pos, bsize );
        }

        try
        {
            String hashValue = null;

            if (fake_hash)
            {
                hashValue  ="Dies ist ein fake hash";
            }
            else
            {
                Digest digest = hash_pool.get();

                // TODO: PARALLEL WITH THREADPOOL
                byte[] hash = digest.digest(data);
                hashValue = CryptTools.encodeUrlsafe(hash);

                hash_pool.release(digest);
            }

            HashDataResult ret = new HashDataResult( hashValue, data);
            
            return ret;
        }
        catch (IOException iOException)
        {
            System.out.println("Cannot create hash");
        }
        return null;
    }

    // THIS HAS TO BE ASCENDING AND W/O GAPS
    int write_xa( RemoteFSElemWrapper wrapper, byte[] data, long pos )
    {
        HANDLE h = cache.get_handle(wrapper);
        if (h == null)
            return -1;

        // APPEND TO EXISTING FILE
        if (!LibKernel32.SetFilePointerEx(h, 0, null, LibKernel32.FILE_END))
            return -1;

        WinFileHandleData hd = cache.get_handleData(wrapper);

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
            return write_xa( wrapper, data, pos);
        }

        HANDLE h = cache.get_handle(wrapper);
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
            HANDLE h = cache.get_handle(wrapper);

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
            HANDLE h = cache.get_handle(wrapper);

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
    public boolean create_dir( RemoteFSElem dir ) throws IOException
    {
        File f = new File(dir.getPath());
        if (f.mkdir())
        {
            RemoteFSElemWrapper wrapper = null;
            try
            {
                wrapper = open_data(dir, AgentApi.FL_RDONLY);
                if (wrapper == null)
                {
                    throw new IOException("cannot open dir");
                }
                HANDLE h = cache.get_handle(wrapper);

                cache.setFiletime( h, dir );

                cache.close_handle(wrapper);
                
                return true;
            }
            catch (Exception e)
            {
            }
            finally
            {
                close_data(wrapper);
            }
        }
        return false;
    }

    @Override
    public SnapshotHandle create_snapshot( RemoteFSElem file )
    {
        SnapshotHandle handle = snapshot.create_snapshot( file );
        return handle;
    }

    @Override
    public boolean release_snapshot( SnapshotHandle handle )
    {
        return snapshot.release_snapshot( handle );
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
    public boolean check_cdp( CdpTicket ticket )
    {

        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean pause_cdp( CdpTicket ticket )
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean stop_cdp( CdpTicket ticket )
    {
        CdpHandler cdp_handler = removeCdpHandler(ticket);
        if (cdp_handler != null)
        {
            cdp_handler.stop_cdp();
            cdp_handler.cleanup_cdp();
            cdp_handler = null;
            return true;
        }
        return false;
    }

    @Override
    public List<CdpTicket> getCdpTickets()
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    HashMap<Long, VSMFS> mountMap = new HashMap<Long, VSMFS>();

    @Override
    public boolean mountVSMFS( InetAddress addr, int port, StoragePoolWrapper poolWrapper/*, Date timestamp, String subPath, User user*/, String drive)
    {
        addr = validateAdressFromCommThread(addr);

        VSMFS fs = null;
        try
        {
            fs = mountMap.get(poolWrapper.getPoolIdx());
            if (fs != null)
            {
                System.out.println("Unmounting pool " + poolWrapper.getPoolIdx());
                fs.unmount();
            }
            mountMap.remove(poolWrapper.getPoolIdx());
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        try
        {            
            fs = MountVSMFS.mount_vsmfs(addr, port, poolWrapper/*, timestamp, subPath, user*/, drive, false);

            mountMap.put(poolWrapper.getPoolIdx(), fs);
            return true;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean unmountVSMFS( InetAddress addr, int port, StoragePoolWrapper poolWrapper )
    {
        addr = validateAdressFromCommThread(addr);

        VSMFS fs = null;
        try
        {
            fs = mountMap.get(poolWrapper.getPoolIdx());
            if (fs != null)
            {
                System.out.println("Unmounting pool " + poolWrapper.getPoolIdx());
                fs.unmount();
            }
            mountMap.remove(poolWrapper.getPoolIdx());

            return true;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean isMountedVSMFS( InetAddress addr, int port, StoragePoolWrapper poolWrapper )
    {
        addr = validateAdressFromCommThread(addr);

        boolean ret = false;
        try
        {
            if ( mountMap.containsKey( poolWrapper.getPoolIdx()))
                ret = true;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return ret;
    }

    static byte[] null_array = null;
    @Override
    public byte[] fetch_null_data( int bsize )
    {
        if (null_array == null || null_array.length != bsize)
        {
            null_array = new byte[bsize];
            for (int i = 0; i < null_array.length; i++)
            {
                null_array[i] = (byte)(i&0xFF);
            }
        }

        /*Random rand = new Random();

        rand.nextBytes(ret);*/

        return null_array;
    }

    @Override
    public boolean set_filetimes_named( RemoteFSElem elem )
    {
        HANDLE h = cache.open_raw_handle(elem, FL_RDWR);
        if (h != null)
        {
            cache.setFiletime(h, elem);
            cache.close_raw_handle(h);
            return true;
        }
        return false;
    }

    @Override
    public AttributeList get_attributes( RemoteFSElem file )
    {
        return null;
    }

    @Override
    public boolean set_filetimes( RemoteFSElemWrapper wrapper )
    {
        HANDLE h = cache.get_handleData(wrapper).handle;
        RemoteFSElem elem  = cache.get_handleData(wrapper).elem;
        if (h != null)
        {
            cache.setFiletime(h, elem);            
            return true;
        }
        return false;
    }
    @Override
    public boolean set_attributes( RemoteFSElemWrapper wrapper )
    {
        RemoteFSElem elem = cache.get_handleData(wrapper).elem;

        cache.setAttributes( elem);
        set_filetimes( wrapper );

        return true;
    }

    @Override
    public RemoteFSElem check_hotfolder( RemoteFSElem mountPath, long getSetttleTime_s, final String filter, boolean onlyFiles, boolean onlyDirs, int itemIdx )
    {
        return hfManager.checkHotfolder(mountPath, getSetttleTime_s, filter, onlyFiles, onlyDirs, itemIdx);
    }

    @Override
    public void deleteDir( RemoteFSElem path, boolean b ) throws IOException
    {
        String p = path.getPath();
        File f = new File(p);
        if (f.exists() && f.isDirectory())
        {
            if (!b)
            {
                if (!f.delete())
                {
                    throw new IOException("Cannot delete dir " + p);
                }
            }
            else
            {
                if (!deleteRecursive(f))
                {
                    throw new IOException("Cannot recursive delete dir " + p);
                }
            }
        }
    }

    private boolean deleteRecursive( File dir ) throws IOException
    {
        if (dir.isDirectory())
        {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++)
            {
                boolean success = deleteRecursive(new File(dir, children[i]));
                if (!success)
                {
                    return false;
                }
            }
        }
        // TODO REMOVE WITH NETATLK / HELIOS CONFORMING TOOLS
        boolean ret = dir.delete();
        if (!ret)
        {
            // RETRY
            try
            {
                Thread.sleep(1000);
            }
            catch (InterruptedException interruptedException)
            {
            }
            ret = dir.delete();
        }
        if (!ret)
        {
            throw new IOException( "Cannot recursive delete  " + dir.getAbsolutePath() );
        }
        return ret;
    }

    @Override
    public boolean create_other( RemoteFSElem remoteFSElem )
    {
        int mode = remoteFSElem.getPosixMode();

        System.out.println("Cannot create Filesystem elem " + mode);

        return false;
    }

    @Override
    public boolean create_symlink( RemoteFSElem remoteFSElem )
    {
        try
        {
            return cache.createSymlink( remoteFSElem.getPath(), remoteFSElem.getLinkPath());
        }
        catch (Exception e)
        {
        }
        return false;
    }

    @Override
    public String readAclInfo( RemoteFSElem dir )
    {
        return factory.readAclInfo(dir);
    }
    @Override
    public FSElemAccessor getFSElemAccessor()
    {
        return cache;
    }
    
}
