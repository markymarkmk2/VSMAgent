/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client;

import de.dimm.vsm.Utilities.CommThread;
import de.dimm.vsm.Utilities.CryptTools;
import de.dimm.vsm.Utilities.ZipUtilities;
import de.dimm.vsm.client.cdp.CdpHandler;
import de.dimm.vsm.fsutils.MountVSMFS;
import de.dimm.vsm.fsutils.IVSMFS;
import de.dimm.vsm.hash.HashFunctionPool;
import de.dimm.vsm.net.AttributeList;
import de.dimm.vsm.net.CdpTicket;
import de.dimm.vsm.net.CompEncDataResult;
import de.dimm.vsm.net.HashDataResult;
import de.dimm.vsm.net.InvalidCdpTicketException;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.net.RemoteFSElemWrapper;
import de.dimm.vsm.net.StoragePoolWrapper;
import de.dimm.vsm.net.interfaces.AgentApi;
import de.dimm.vsm.net.interfaces.SnapshotHandle;
import de.dimm.vsm.net.interfaces.SnapshotHandler;
import de.dimm.vsm.records.Excludes;
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
    public static final int RSRC_NETATALK = 1;
    public static final int RSRC_ES = 2;
    public static final int RSRC_XINET = 3;
    public static final int RSRC_USCORE = 4;
    public static final int RSRC_HFS = 5;
    
    public static final String NETATALK_RSRCDIR = ".AppleDouble";
    public static final String ES_RSRCDIR = ".rsrc";
    public static final String XINET_RSRCDIR = ".HSResource";

    //UnixFSElemAccessor fsAcess;

    protected int rsrcMode;


    protected HashFunctionPool hash_pool;
    protected Properties options;
    protected SnapshotHandler snapshot;
    //protected CdpHandler cdp_handler;

    private HashMap<CdpTicket,CdpHandler> cdpHandlerMap = new HashMap<CdpTicket, CdpHandler>();
    
    protected HFManager hfManager;
    
    HashMap<Long, IVSMFS> mountMap = new HashMap<Long, IVSMFS>();

    //protected FileCacheManager cacheManager;

    int lastRdqlen = -1;
    int lastRdyqlen = -1;
    int lastHTherads = -1;
    void idle()
    {
    }




    abstract public FSElemAccessor getFSElemAccessor();
    abstract public RemoteFSElemFactory getFsFactory();


    protected abstract void detectRsrcMode( File parent, File[] list );    
    protected abstract boolean isRsrcEntry( File f );

    public int getRsrcMode()
    {
        return rsrcMode;
    }
    

    @Override
    public ArrayList<RemoteFSElem> list_dir( RemoteFSElem dir, boolean lazyAclInfo )
    {

        File fh = new File(getFsFactory().convSystem2NativePath(dir.getPath()));
        File[] files = fh.listFiles();


        ArrayList<RemoteFSElem> list = new ArrayList<RemoteFSElem>();
        if (files == null)
        {
            System.out.println("No files for " + dir.getPath());
            return list;
        }
        detectRsrcMode(fh, files);

        Arrays.sort(files, new FileComparator());


        for (int i = 0; i < files.length; i++)
        {
            File file = files[i];
           
            if (isRsrcEntry(file))
            {
                continue;
            }

            //if ()
            RemoteFSElem elem = getFsFactory().create_elem(file, lazyAclInfo);

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

            RemoteFSElem elem = getFsFactory().create_elem(file, true);

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
        p.setProperty(OP_AG_ENC, Boolean.TRUE.toString());
        p.setProperty(OP_AG_COMP, Boolean.TRUE.toString());
        p.setProperty(OP_CDP_EXCLUDES, Boolean.TRUE.toString());

        return p;
    }
    boolean adressWarned = false;

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
                    if (!adressWarned)
                    {
                        System.out.println("Remote address does not match, using Socket address");
                        adressWarned= true;
                    }
                    addr = iadr.getAddress();
                }
            }
        }
        return addr;
    }

    @Override
    public RemoteFSElemWrapper open_data( RemoteFSElem file, int flags ) throws IOException
    {
        RemoteFSElemWrapper wrapper = getFSElemAccessor().open_handle(file, flags);

        return wrapper;
    }

    @Override
    public RemoteFSElemWrapper open_stream_data( RemoteFSElem file, int flags ) throws IOException
    {
        RemoteFSElemWrapper wrapper = getFSElemAccessor().open_xa_handle(file, flags);

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
        return getFSElemAccessor().close_handle(wrapper);
    }


    static int errShowCnt = 0;
    @Override
    public String read_hash( RemoteFSElemWrapper wrapper, long pos, int bsize, String alg ) throws IOException
    {
        byte[] data;

        FileHandleData hdata = getFSElemAccessor().get_handleData(wrapper);

        if (pos == 0)
        {
            long totalLen;
            if (wrapper.isXa())
                totalLen = hdata.elem.getStreamSize();
            else
                totalLen = hdata.elem.getDataSize();
            if (totalLen > 2* bsize)
            {
                MultiThreadedFileReader mtfr = getFSElemAccessor().createMultiThreadedFileReader(this, wrapper);
                if (mtfr != null)
                {
                    hdata.setPrefetch(true);
                    mtfr.startFile(this, wrapper, hdata.elem, bsize);
                }
            }
        }

        if (hdata.isPrefetch())
        {
            MultiThreadedFileReader mtfr = getFSElemAccessor().getMultiThreadedFileReader(wrapper);
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
        byte[] data;

        // READ VIA CACHE MANAGER
        FileHandleData hdata = getFSElemAccessor().get_handleData(wrapper);

        if (hdata.isPrefetch())
        {
            MultiThreadedFileReader mtfr = getFSElemAccessor().getMultiThreadedFileReader(wrapper);
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
    @Override
    public CompEncDataResult readEncryptedCompressed( RemoteFSElemWrapper wrapper, long pos, int bsize, boolean enc, boolean comp )
    {
        byte[] data = read(wrapper, pos, bsize);
        if (data == null)
            return null;


        // FIRST COMPRESS, THEN ENCRYPT
        if (comp)
        {
            data = ZipUtilities.lzf_compressblock(data);
            if (data == null)
                return null;
        }
        int compLen = data.length;

        if (enc)
            data = CryptTools.encryptXTEA8(data);

        int encLen = data.length;
        
        CompEncDataResult res = new CompEncDataResult(data, compLen, encLen);


        return res;
    }

    public byte[] rawRead( RemoteFSElemWrapper wrapper, long pos, int bsize )
    {
        byte[] block = new byte[bsize];
        return rawRead(block, wrapper, pos, bsize);
    }

    public abstract byte[] rawRead( byte[] data, RemoteFSElemWrapper wrapper, long pos, int bsize );
    
    
   @Override
    public CompEncDataResult read_and_hash_encrypted_compressed( RemoteFSElemWrapper wrapper, long pos, int bsize, boolean enc, boolean comp ) throws IOException
    {
        HashDataResult hdr = read_and_hash(wrapper, pos, bsize);

        if (hdr == null)
            return null;

        byte[] data = hdr.getData();

        // FIRST COMPRESS, THEN ENCRYPT
        if (comp)
        {
            byte[] compData = ZipUtilities.lzf_compressblock(hdr.getData());
            if (compData == null)
                return null;

            data = compData;
        }
        int compLen = data.length;

        if (enc)
        {
            data = CryptTools.encryptXTEA8(data);
        }
        int encLen = data.length;

        CompEncDataResult res = new CompEncDataResult(data, compLen, encLen, hdr.getHashValue());
        
        return res;
    }


    @Override
    public HashDataResult read_and_hash( RemoteFSElemWrapper wrapper, long pos, int bsize ) throws IOException
    {
        byte[] data;

        FileHandleData hdata = getFSElemAccessor().get_handleData(wrapper);

        if (pos == 0)
        {
            long totalLen;
            if (wrapper.isXa())
                totalLen = hdata.getElem().getStreamSize();
            else
                totalLen = hdata.getElem().getDataSize();
            if (totalLen > 2* bsize)
            {
                MultiThreadedFileReader mtfr = getFSElemAccessor().createMultiThreadedFileReader(this, wrapper);
                if (mtfr != null)
                {
                    hdata.setPrefetch(true);
                    mtfr.startFile(this, wrapper, hdata.getElem(), bsize);
                }
            }
        }

        if (hdata.isPrefetch())
        {
            MultiThreadedFileReader cacheManager = getFSElemAccessor().getMultiThreadedFileReader(wrapper);
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
    public boolean stop_cdp( CdpTicket ticket ) throws InvalidCdpTicketException
    {
        CdpHandler cdp_handler = removeCdpHandler(ticket);
        if (cdp_handler != null)
        {
            cdp_handler.stop_cdp();
            cdp_handler.cleanup_cdp();
            cdp_handler = null;
            return true;
        }
        throw new InvalidCdpTicketException(ticket);
    }


    @Override
    public List<CdpTicket> getCdpTickets()
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void set_cdp_excludes(  CdpTicket ticket, List<Excludes> exclList ) throws InvalidCdpTicketException
    {
        CdpHandler cdpHandler = getCdpHandler( ticket );
        if (cdpHandler == null)
            throw new InvalidCdpTicketException(ticket );

        cdpHandler.setExcludes( exclList );

    }





    
    public boolean mountVSMFS( InetAddress addr, int port, StoragePoolWrapper poolWrapper, String drive )
    {
        addr = validateAdressFromCommThread(addr);

        IVSMFS fs;
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
            fs = MountVSMFS.mount_vsmfs(addr, port, poolWrapper, drive, useFuse, !poolWrapper.getQry().isReadOnly());

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

        IVSMFS fs;
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
        String p = getFsFactory().convSystem2NativePath(path.getPath());
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
        try
        {
            String s = getFsFactory().readAclInfo(dir);
            return s;
        }
        catch (IOException iOException)
        {
            System.out.println("Fehler beim Lesen der ACLInfos von " + dir.getPath() + ": " + iOException.getMessage());
        }

        return null;
    }

    @Override
    public AttributeList get_attributes( RemoteFSElem file )
    {
        throw new RuntimeException( "get_attributes ist obsolet" );
    }

    @Override
    public boolean set_attributes( RemoteFSElemWrapper wrapper )
    {
        boolean ret = true;
        RemoteFSElem elem = getFSElemAccessor().get_handleData(wrapper).getElem();

        try
        {
            if (elem.getAclinfoData() != null)
            {
                getFsFactory().writeAclInfo(elem);
            }
        }
        catch (IOException iOException)
        {
            ret = false;
        }
        set_filetimes( wrapper );

        return ret;
    }




    @Override
    public boolean create_symlink( RemoteFSElem remoteFSElem )
    {
        try
        {
            return getFSElemAccessor().createSymlink( remoteFSElem.getPath(), remoteFSElem.getLinkPath());
        }
        catch (Exception e)
        {
        }
        return false;
    }

    @Override
    public boolean create_dir( RemoteFSElem elem )
    {
        File f = new File(getFsFactory().convSystem2NativePath(elem.getPath()));
        try
        {
            if (getFSElemAccessor().mkDir( f ))
            {
                try
                {
                    if (elem.getAclinfoData() != null)
                    {
                        getFsFactory().writeAclInfo(elem);
                    }
                }
                catch (Exception e)
                {
                    System.out.println("Error writing ACL-Info " +  f.getAbsolutePath() + ": " + e.getMessage());
                }
                set_filetimes_named( elem );
                return true;
            }
        }
        catch (Exception e)
        {
            System.out.println("Error creating directory " + f.getAbsolutePath() + ": " + e.getMessage());
        }
        return false;
    }



    protected boolean getBooleanOption( String name )
    {
        return getBooleanOption(name, false);
    }
    protected boolean getBooleanOption( String name, boolean def )
    {
        if( options == null)
            return def;

        String o = options.getProperty(name);
        if (o == null)
            return def;

        char ch = o.charAt(0);
        if ("1yYjJtT".indexOf(ch) != -1)
            return true;

        return false;
    }
    protected String getOption( String name)
    {
        return getOption(name, null);
    }
    protected String getOption( String name, String def )
    {
        if( options == null)
            return def;

        String o = options.getProperty(name);
        if (o == null)
            return def;

        return o;
    }
    protected int getIntOption( String name )
    {
        return getIntOption(name, 0);
    }
    protected int getIntOption( String name, int def )
    {
        if( options == null)
            return def;

        String o = options.getProperty(name);
        if (o == null)
            return def;

        try
        {
            return Integer.parseInt(o);
        }
        catch (NumberFormatException numberFormatException)
        {
        }
        return 0;
    }
    protected long getLongOption( String name )
    {
        return getLongOption(name, 0);
    }
    protected long getLongOption( String name, long def )
    {
        if( options == null)
            return def;

        String o = options.getProperty(name);
        if (o == null)
            return def;

        try
        {
            return Long.parseLong(o);
        }
        catch (NumberFormatException numberFormatException)
        {
        }
        return 0;
    }

    @Override
    public int writeEncryptedCompressed( RemoteFSElemWrapper wrapper, byte[] data, long pos, int encLen, boolean enc, boolean comp )
    {
        if (enc)
        {
            data = CryptTools.decryptXTEA8(data, encLen);
        }
        if (comp)
        {
            data = ZipUtilities.lzf_decompressblock(data);
        }
        return write( wrapper, data, pos);
    }



}
