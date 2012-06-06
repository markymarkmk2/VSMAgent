/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.dimm.vsm.client.unix;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import de.dimm.vsm.Utilities.CommThread;
import de.dimm.vsm.Utilities.CryptTools;
import de.dimm.vsm.client.AttributeContainerImpl;
import de.dimm.vsm.client.FSElemAccessor;
import de.dimm.vsm.client.MultiThreadedFileReader;
import de.dimm.vsm.client.Main;
import de.dimm.vsm.client.NetAgentApi;
import de.dimm.vsm.client.cdp.CDP_Param;
import de.dimm.vsm.client.cdp.CdpHandler;
import de.dimm.vsm.client.cdp.FCECdpHandler;
import de.dimm.vsm.client.cdp.FCEEventSource;
import de.dimm.vsm.client.cdp.PlatformData;
import de.dimm.vsm.client.cdp.fce.VSMCDPEventProcessor;
import de.dimm.vsm.client.cdp.fce.VSMFCEEventSource;
import de.dimm.vsm.client.jna.PosixWrapper;
import de.dimm.vsm.client.jna.VSMLibC;
import de.dimm.vsm.fsutils.MountVSMFS;
import de.dimm.vsm.fsutils.VSMFS;
import de.dimm.vsm.hash.HashFunctionPool;
import de.dimm.vsm.net.AttributeContainer;
import de.dimm.vsm.net.AttributeEntry;
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
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import org.apache.commons.codec.binary.Base64;
import org.jruby.ext.posix.POSIX;


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
public class UnixAgentApi extends NetAgentApi
{

    public static final int RSRC_NETATALK = 1;
    public static final int RSRC_ES = 2;
    public static final int RSRC_XINET = 3;
    public static final String NETATALK_RSRCDIR = ".AppleDouble";
    public static final String ES_RSRCDIR = ".rsrc";
    public static final String XINET_RSRCDIR = ".HSResource";
    
    UnixFSElemAccessor cache;
    
    private int rsrcMode;
    String cdpIpFilter = null;
   
   

    public UnixAgentApi( HashFunctionPool hash_pool, String cdpIpFilter )
    {
        this.hash_pool = hash_pool;

        cache = new UnixFSElemAccessor(this);
        
        options = new Properties();

        hfManager = new UnixHFManager();

        if (Main.is_solaris())
        {
            snapshot = new ZFSSnapShotHandler();
            snapshot.init();
        }
        //if ()
        factory = new NetatalkRemoteFSElemFactory();
        this.cdpIpFilter = cdpIpFilter;
        
    }

    @Override
    public ArrayList<RemoteFSElem> list_dir( RemoteFSElem dir, boolean lazyAclInfo )
    {

        File fh = new File(dir.getPath());
        File[] files = fh.listFiles();


        ArrayList<RemoteFSElem> list = new ArrayList<RemoteFSElem>();
        if (files == null)
        {
            System.out.println("No files for " + dir.getPath());
            return list;
        }
        detectRsrcMode(files);

        Arrays.sort(files, new FileComparator());


        for (int i = 0; i < files.length; i++)
        {
            File file = files[i];

            // TODO: SWITCH DEPENDING ON ACTUAL FS 

            if (isRsrcDir(file))
            {
                continue;
            }

            //if ()
            RemoteFSElem elem = factory.create_elem(file, lazyAclInfo);

            list.add(elem);
        }
        return list;
    }

    void detectRsrcMode( File[] list )
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
            if (isRsrcDir(file))
            {
                continue;
            }

            RemoteFSElem elem = factory.create_elem(file, true);

            list.add(elem);
        }
        return list;
    }

    boolean isRsrcDir( File f )
    {
        switch (rsrcMode)
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
    }*/

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

    static int errShowCnt = 0;
    @Override
    public String read_hash( RemoteFSElemWrapper wrapper, long pos, int bsize, String alg ) throws IOException
    {
        byte[] data = null;

        UnixFileHandleData hdata = cache.get_handleData(wrapper);

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
            String hashValue = mtfr.getHash(pos, bsize);
            if (hashValue != null)
            {
                errShowCnt = 0;
                return hashValue;
            }
            else
            {
                if (errShowCnt == 0)
                {
                    errShowCnt++;
                    System.out.println("Error getting hash from cache manager pos " + pos + " " + bsize);
                }
            }
        }

      
        data = _read(wrapper, pos, bsize);

        if (data == null)
                System.out.println("read of pos:" + pos + " bsize:" + bsize + " gave null");

        String ret = null;
        Digest digest = null;
        try
        {
            digest = hash_pool.get();
            

            byte[] hash = digest.digest(data);
            if (hash == null)
                throw new IOException("Digest of date returned null");

            ret = CryptTools.encodeUrlsafe(hash);

            if (ret == null)
                throw new IOException("Cannot encodeUrlsafe Crypttools: " + hash);
        }
        catch (Exception iOException)
        {
            System.out.println("Cannot create hash: " + iOException.getMessage());
            iOException.printStackTrace();
        }
        finally
        {
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

    @Override
    public byte[] read( RemoteFSElemWrapper wrapper, long pos, int bsize )
    {
        byte[] data = null;

        // READ VIA CACHE MANAGER
        UnixFileHandleData hdata = cache.get_handleData(wrapper);

        if (hdata.isPrefetch())
        {
            MultiThreadedFileReader cacheManager = cache.getMultiThreadedFileReader(wrapper);
            if (wrapper.isXa())
            {
                data = cacheManager.getXAData( pos, bsize);
            }
            else
            {
                data = cacheManager.getData( pos, bsize);
            }
            if (data != null)
            {
                return data;
            }
        }

        data = _read(wrapper, pos, bsize);

        return data;

    }

    public byte[] _read( RemoteFSElemWrapper wrapper, long pos, int bsize )
    {
        byte[] data = new byte[bsize];
        return _read(data, wrapper, pos, bsize);
    }


    public byte[] _read(  byte[] data, RemoteFSElemWrapper wrapper, long pos, int bsize )
    {
        RandomAccessFile h = cache.get_handle(wrapper);
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
    public HashDataResult read_and_hash( RemoteFSElemWrapper wrapper, long pos, int bsize ) throws IOException
    {
        byte[] data = null;

        UnixFileHandleData hdata = cache.get_handleData(wrapper);

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
            MultiThreadedFileReader cacheManager = cache.getMultiThreadedFileReader(wrapper);
            if (wrapper.isXa())
            {
                data = cacheManager.getXAData( pos, bsize);
            }
            else
            {
                data = cacheManager.getData( pos, bsize);
            }
            if (data != null)
            {
                String hashValue = cacheManager.getHash(pos, bsize);

                HashDataResult ret = new HashDataResult( hashValue, data);

                return ret;
            }
        }



        data = _read(wrapper, pos, bsize);

        Digest digest = null;
        try
        {
            digest = hash_pool.get();
            

            byte[] hash = digest.digest(data);
            String hashValue = CryptTools.encodeUrlsafe(hash);

            HashDataResult ret = new HashDataResult(hashValue, data);

            return ret;
        }
        catch (IOException iOException)
        {
            System.out.println("Cannot create hash");
        }
        finally
        {
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
    public int write( RemoteFSElemWrapper wrapper, byte[] data, long pos )
    {
        RandomAccessFile h = cache.get_handle(wrapper);
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
            RandomAccessFile h = cache.get_handle(wrapper);

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
            RandomAccessFile h = cache.get_handle(wrapper);


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
                cache.setFiletime( f.getPath(), dir );

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
    public SnapshotHandle create_snapshot( RemoteFSElem file )
    {
        if (snapshot == null)
        {
            return null;
        }

        SnapshotHandle handle = snapshot.create_snapshot(file);
        return handle;
    }

    @Override
    public boolean release_snapshot( SnapshotHandle handle )
    {
        if (snapshot == null)
        {
            return false;
        }

        return snapshot.release_snapshot(handle);
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
    public boolean mountVSMFS( InetAddress addr, int port, StoragePoolWrapper poolWrapper/*, Date timestamp, String subPath, User user*/, String drive )
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
            fs = MountVSMFS.mount_vsmfs(addr, port, poolWrapper/*, timestamp, subPath, user*/, drive, true);

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
            if (mountMap.containsKey(poolWrapper.getPoolIdx()))
            {
                ret = true;
            }
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
                null_array[i] = (byte) (i & 0xFF);
            }
        }

        /*Random rand = new Random();

        rand.nextBytes(ret);*/

        return null_array;
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
        cache.setFiletime(elem.getPath(), elem);

        return true;
    }

    @Override
    public boolean set_filetimes( RemoteFSElemWrapper wrapper )
    {
        RemoteFSElem elem = cache.get_handleData(wrapper).elem;

        cache.setFiletime(elem.getPath(), elem);

        return true;
    }
    @Override
    public boolean set_attributes( RemoteFSElemWrapper wrapper )
    {
        RemoteFSElem elem = cache.get_handleData(wrapper).elem;

        cache.setAttributes( elem);
        cache.setFiletime(elem.getPath(), elem);

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
            if (!deleteRecursive(f))
            {
                throw new IOException("Cannot recursive delete dir " + p);
            }
        }
    }

    private boolean deleteRecursive( File dir )
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
        return dir.delete();
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
        POSIX posix = PosixWrapper.getPosix();
        try
        {
            if (posix.symlink(remoteFSElem.getPath(), remoteFSElem.getLinkPath()) == 0)
                return true;
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
