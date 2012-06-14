/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client;

import de.dimm.vsm.Utilities.CommThread;
import de.dimm.vsm.Utilities.CryptTools;
import de.dimm.vsm.client.cdp.CdpHandler;
import de.dimm.vsm.fsutils.MountVSMFS;
import de.dimm.vsm.fsutils.VSMFS;
import de.dimm.vsm.hash.HashFunctionPool;
import de.dimm.vsm.net.CdpTicket;
import de.dimm.vsm.net.HashDataResult;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.net.RemoteFSElemWrapper;
import de.dimm.vsm.net.StoragePoolWrapper;
import de.dimm.vsm.net.interfaces.AgentApi;
import de.dimm.vsm.net.interfaces.SnapshotHandle;
import de.dimm.vsm.net.interfaces.SnapshotHandler;
import fr.cryptohash.Digest;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
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
public abstract class NetAgentApi implements AgentApi
{

    protected HashFunctionPool hash_pool;
    protected Properties options;
    protected SnapshotHandler snapshot;
    //protected CdpHandler cdp_handler;

    private HashMap<CdpTicket,CdpHandler> cdpHandlerMap = new HashMap<CdpTicket, CdpHandler>();
    protected RemoteFSElemFactory factory;
    protected HFManager hfManager;
    protected FSElemAccessor fsAcess;
    HashMap<Long, VSMFS> mountMap = new HashMap<Long, VSMFS>();

    //protected FileCacheManager cacheManager;

    int lastRdqlen = -1;
    int lastRdyqlen = -1;
    int lastHTherads = -1;
    void idle()
    {
    }

    public FSElemAccessor getFSElemAccessor()
    {
        return fsAcess;
    }

    public RemoteFSElemFactory getFsFactory()
    {
        return factory;
    }


    protected abstract void detectRsrcMode( File[] list );
    protected abstract boolean isRsrcEntry( File f );

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

            if (isRsrcEntry(file))
            {
                continue;
            }

            //if ()
            RemoteFSElem elem = factory.create_elem(file, lazyAclInfo);

            list.add(elem);
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
            if (isRsrcEntry(file))
            {
                continue;
            }

            RemoteFSElem elem = factory.create_elem(file, true);

            list.add(elem);
        }
        return list;
    }

    @Override
    public void set_options( Properties p )
    {
        options = p;
    }
    @Override
    public Properties get_properties()
    {
        Properties p = new Properties();

        p.setProperty(OP_OS, System.getProperty("os.name"));
        p.setProperty(OP_OS_VER, System.getProperty("os.version"));
        p.setProperty(OP_AG_VER, Main.get_version());

        return p;
    }

    protected InetAddress validateAdressFromCommThread( InetAddress addr)
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
    public RemoteFSElemWrapper open_data( RemoteFSElem file, int flags ) throws IOException
    {
        RemoteFSElemWrapper wrapper = fsAcess.open_handle(file, flags);

        return wrapper;
    }

    @Override
    public RemoteFSElemWrapper open_stream_data( RemoteFSElem file, int flags ) throws IOException
    {
        RemoteFSElemWrapper wrapper = fsAcess.open_xa_handle(file, flags);

        return wrapper;
    }

    @Override
    public RemoteFSElem check_hotfolder( RemoteFSElem mountPath, long getSetttleTime_s, final String filter, boolean onlyFiles, boolean onlyDirs, int itemIdx )
    {
        return hfManager.checkHotfolder(mountPath, getSetttleTime_s, filter, onlyFiles, onlyDirs, itemIdx);
    }



    @Override
    public boolean close_data( RemoteFSElemWrapper wrapper ) throws IOException
    {
        return fsAcess.close_handle(wrapper);
    }


    static int errShowCnt = 0;
    @Override
    public String read_hash( RemoteFSElemWrapper wrapper, long pos, int bsize, String alg ) throws IOException
    {
        byte[] data = null;

        FileHandleData hdata = fsAcess.get_handleData(wrapper);

        if (pos == 0)
        {
            long totalLen = 0;
            if (wrapper.isXa())
                totalLen = hdata.elem.getStreamSize();
            else
                totalLen = hdata.elem.getDataSize();
            if (totalLen > 2* bsize)
            {
                MultiThreadedFileReader mtfr = fsAcess.createMultiThreadedFileReader(this, wrapper);
                if (mtfr != null)
                {
                    hdata.setPrefetch(true);
                    mtfr.startFile(this, wrapper, hdata.elem, bsize);
                }
            }
        }

        if (hdata.isPrefetch())
        {
            MultiThreadedFileReader mtfr = fsAcess.getMultiThreadedFileReader(wrapper);
            if (mtfr != null)
            {
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
            else
            {
                System.out.println("Cannot get mtfr in read_hash");
            }
        }


        data = rawRead(wrapper, pos, bsize);

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
    public byte[] read( RemoteFSElemWrapper wrapper, long pos, int bsize )
    {
        byte[] data = null;

        // READ VIA CACHE MANAGER
        FileHandleData hdata = fsAcess.get_handleData(wrapper);

        if (hdata.isPrefetch())
        {
            MultiThreadedFileReader mtfr = fsAcess.getMultiThreadedFileReader(wrapper);
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
        data = rawRead(wrapper, pos, bsize);

        return data;
    }

    public byte[] rawRead( RemoteFSElemWrapper wrapper, long pos, int bsize )
    {
        byte[] block = new byte[bsize];
        return rawRead(block, wrapper, pos, bsize);
    }

    public abstract byte[] rawRead( byte[] data, RemoteFSElemWrapper wrapper, long pos, int bsize );
    
    


 @Override
    public HashDataResult read_and_hash( RemoteFSElemWrapper wrapper, long pos, int bsize ) throws IOException
    {
        byte[] data = null;

        FileHandleData hdata = fsAcess.get_handleData(wrapper);

        if (pos == 0)
        {
            long totalLen = 0;
            if (wrapper.isXa())
                totalLen = hdata.getElem().getStreamSize();
            else
                totalLen = hdata.getElem().getDataSize();
            if (totalLen > 2* bsize)
            {
                MultiThreadedFileReader mtfr = fsAcess.createMultiThreadedFileReader(this, wrapper);
                if (mtfr != null)
                {
                    hdata.setPrefetch(true);
                    mtfr.startFile(this, wrapper, hdata.getElem(), bsize);
                }
            }
        }

        if (hdata.isPrefetch())
        {
            MultiThreadedFileReader cacheManager = fsAcess.getMultiThreadedFileReader(wrapper);
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

        data = rawRead(wrapper, pos, bsize);

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
    

    protected CdpHandler getCdpHandler( CdpTicket t )
    {
        return cdpHandlerMap.get(t);
    }
    protected CdpHandler removeCdpHandler( CdpTicket t )
    {
        return cdpHandlerMap.remove(t);
    }
    protected void addCdpHandler( CdpTicket t, CdpHandler h )
    {
        cdpHandlerMap.put(t, h);
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

        boolean useFuse = !Main.is_win();

        try
        {
            fs = MountVSMFS.mount_vsmfs(addr, port, poolWrapper/*, timestamp, subPath, user*/, drive, useFuse);

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
    public String readAclInfo( RemoteFSElem dir )
    {
        return factory.readAclInfo(dir);
    }
    


    @Override
    public boolean create_symlink( RemoteFSElem remoteFSElem )
    {
        try
        {
            return fsAcess.createSymlink( remoteFSElem.getPath(), remoteFSElem.getLinkPath());
        }
        catch (Exception e)
        {
        }
        return false;
    }

}
