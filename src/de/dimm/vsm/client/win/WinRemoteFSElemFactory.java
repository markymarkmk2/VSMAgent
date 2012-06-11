/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.win;

import de.dimm.vsm.client.RemoteFSElemFactory;
import com.sun.jna.Memory;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import de.dimm.vsm.client.AttributeContainerImpl;
import de.dimm.vsm.client.jna.LibKernel32;
import de.dimm.vsm.client.jna.LibKernel32.WIN32_STREAM_ID;
import de.dimm.vsm.net.AttributeContainer;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.records.FileSystemElemNode;
import java.io.File;
import java.util.HashMap;

// H:\Archiv_H\444440497502D075\zm\referenzen\444440497501_593085\zm\referenzen\444440497500_582452\angeliefert\07.03.11\29831_DF_PHILADELPHIA_Snack_MP_Milka\29831 DF PHILADELPHIA Snack Milka MP Collection\ausgabe\norm\29831 DF PHILADELPHIA Snack Milka MP_norm.pdf
/**
 *
 * @author Administrator
 */
public class WinRemoteFSElemFactory implements RemoteFSElemFactory
{

    public static String getLongPath( RemoteFSElem elem )
    {
        String path = elem.getPath();
        path = path.replace('/', '\\');
        return getLongPath(path);
    }
    public static String getLongPath( File fh )
    {
        return getLongPath(fh.getAbsolutePath());
    }
    public static String getLongPath( String fpath )
    {
        if (fpath.length() > 200)
        {
            // DRIVE ?
            if (fpath.charAt(1) == ':')
            {
               fpath = "\\\\?\\" + fpath;
            }
            // UNC
            else if (fpath.startsWith("//") || fpath.startsWith("\\\\"))
            {
               fpath = "\\\\?\\UNC\\" + fpath;
            }
        }
        return fpath;
    }

    @Override
    public synchronized  RemoteFSElem create_elem( File fh, boolean lazyAclInfo )
    {
        String fpath = getLongPath( fh );

        WString path = new WString(fpath);

        LibKernel32.WIN32_FILE_ATTRIBUTE_DATA data =  new LibKernel32.WIN32_FILE_ATTRIBUTE_DATA();
        
        if (!read_win_data( path, data ))
        {
            System.out.println("Cannot GetFileAttributesEx for " + fh.getAbsolutePath() + ": " + LibKernel32.GetLastError());            
        }


        RemoteFSElem elem = populate_elem( fh, data );

        elem.setStreaminfo(RemoteFSElem.STREAMINFO_NTFS);

        if (!elem.isSymbolicLink()/* && Main.hasNFSv4()*/)
        {
            if (lazyAclInfo)
            {
                elem.setAclinfoData(RemoteFSElem.LAZY_ACLINFO);
            }
            else
            {

                try
                {
                    AttributeContainer info = new AttributeContainer();

                    if (AttributeContainerImpl.fill( elem, info ))
                    {
                        int hash = info.hashCode();
                        String aclStream = getHashMap(hash);
                        if (aclStream == null)
                        {
                            aclStream = AttributeContainer.serialize(info);
                            putHashMap( hash, aclStream );
                        }
                        elem.setAclinfoData(aclStream);
                        elem.setAclinfo(RemoteFSElem.ACLINFO_WIN);
                    }
                }
                catch (Exception exc)
                {
                    exc.printStackTrace();
                }
            }
        }

        return elem;
    }

    @Override
    public synchronized  String readAclInfo( RemoteFSElem elem )
    {
        try
        {
            AttributeContainer info = new AttributeContainer();

            if (AttributeContainerImpl.fill( elem, info ))
            {
                int hash = info.hashCode();
                String aclStream = getHashMap(hash);
                if (aclStream == null)
                {
                    aclStream = AttributeContainer.serialize(info);
                    putHashMap( hash, aclStream );
                }
                elem.setAclinfoData(aclStream);
                elem.setAclinfo(RemoteFSElem.ACLINFO_WIN);
                return aclStream;
            }
        }
        catch (Exception exc)
        {
            exc.printStackTrace();
        }
        return null;
    }


    static HashMap<Integer, String> attrHashMap = new HashMap<Integer, String>();
    void putHashMap( int hash, String aclStream)
    {
        if (attrHashMap.size() > 10000)
            attrHashMap.clear();
        
        attrHashMap.put(hash, aclStream);
    }
    String getHashMap( int hash)
    {
        return attrHashMap.get(hash);
    }


    static RemoteFSElem populate_elem( File fh, LibKernel32.WIN32_FILE_ATTRIBUTE_DATA data )
    {
        long total_size = LibKernel32.get_unsigned( data.nFileSizeHigh.intValue() );
        total_size <<= 32;
        total_size += LibKernel32.get_unsigned( data.nFileSizeLow.intValue() );

        long len = total_size; //get_flen( fh );

        boolean dir = ((data.dwFileAttributes.longValue() & LibKernel32.FILE_ATTRIBUTE_DIRECTORY) == LibKernel32.FILE_ATTRIBUTE_DIRECTORY);
        String typ = dir ? FileSystemElemNode.FT_DIR : FileSystemElemNode.FT_FILE;

        long streamLen = evalStreamLen( fh );

        RemoteFSElem elem = new RemoteFSElem( fh.getAbsolutePath(), typ,
                data.ftLastWriteTime.GetAbsMS(), data.ftCreationTime.GetAbsMS(), data.ftLastAccessTime.GetAbsMS(),
                len, streamLen );

        
        return elem;
    }
    static long get_flen( File fh )
    {
        return fh.length();
    }
    static String get_path( File fh )
    {
        return getLongPath(fh);
    }

    static boolean read_win_data( WString path, LibKernel32.WIN32_FILE_ATTRIBUTE_DATA data )
    {
         return LibKernel32.GetFileAttributesEx( path, LibKernel32.GetFileExInfoStandard, data );
    }

    
    public static long evalStreamLen( File fh )
    {
        int attrib = LibKernel32.FILE_ATTRIBUTE_NORMAL;
        WString path = new WString(getLongPath(fh));

        HANDLE handle = LibKernel32.CreateFile( path, LibKernel32.GENERIC_READ, LibKernel32.FILE_SHARE_READ, null, LibKernel32.OPEN_EXISTING, attrib, null);

        if (LibKernel32.isInvalidHandleValue(handle))
            return -1;

        IntByReference bytes_read = new IntByReference(0);
        PointerByReference context = new PointerByReference();

        byte[] arr = new byte[LibKernel32.WINSTREAM_ID_SIZE];

        boolean finished = false;
        long streamLen = 0;

        while (!finished)
        {

            boolean ret = LibKernel32.BackupRead(handle, arr, LibKernel32.WINSTREAM_ID_SIZE, bytes_read, /*abort*/false, /*security*/true, context);

            if (ret && bytes_read.getValue() == 0)
            {
                // REGULAR EXIT
                finished = true;
                break;
            }

            if (!ret)
            {
                System.out.println("Read error in stream of " + path.toString());
                streamLen = -1;
                break;
            }

            WIN32_STREAM_ID id =  get_stream_id( arr, 0 );

            // ALL KNOWN VALID IDS ARE BELOW 255, THIS IS JUST A CHECK FOR PLAUSIBILITY
            if (id.dwStreamId > 255)
            {
                System.out.println("Wrong stream id in stream of " + path.toString() + ": " + id.toString());
                streamLen = -1;
                break;
            }
            if (id.dwStreamId != LibKernel32.BACKUP_DATA)
            {
                streamLen += LibKernel32.WINSTREAM_ID_SIZE;
                streamLen += id.Size;
                streamLen += id.dwStreamNameSize;
            }
            if (id.dwStreamNameSize > 0 && id.dwStreamNameSize < 64000)
            {
                byte[] streamName = new byte[id.dwStreamNameSize];
                ret = LibKernel32.BackupRead(handle, streamName, streamName.length, bytes_read, /*abort*/false, /*security*/true, context);

                char[] cstreamName = new char[id.dwStreamNameSize/2];
                for (int i = 0; i < cstreamName.length; i++)
                {
                    char ch = (char) streamName[ 2*i ];
                    ch += streamName[ 2*i + 1 ];
                    cstreamName[i] = ch;                     
                }
                //System.out.println("Detected Streamname: " + new String(cstreamName) );
            }
            long skip = id.Size;

            Seek( handle, skip, context );
        }
                
        // CLOSE CONTEXT
        LibKernel32.BackupRead(handle, null, 0, null, /*abort*/true, /*security*/true, context);

        LibKernel32.CloseHandle(handle);

        return streamLen;

    }

    public static boolean Seek( HANDLE handle, long skip, PointerByReference context )
    {
        IntByReference hbytes_skipped = new IntByReference(0);
        IntByReference lbytes_skipped = new IntByReference(0);

        boolean ret = LibKernel32.BackupSeek(handle, (int)(skip & 0xffffffff), (int)(skip >> 32), lbytes_skipped, hbytes_skipped, context);

        hbytes_skipped.getValue();
        return ret;
    }
    

    /*
    public static class WIN32_STREAM_ID
    {
      public DWORD         dwStreamId;
      public DWORD         dwStreamAttributes;
      public long          Size;
      public DWORD         dwStreamNameSize;
    };*/

    public static WIN32_STREAM_ID  get_stream_id( byte[] arr, int offset )
    {
        Memory mem = new Memory(LibKernel32.WINSTREAM_ID_SIZE);

        mem.write(0, arr, offset, LibKernel32.WINSTREAM_ID_SIZE);

        WIN32_STREAM_ID id = new LibKernel32.WIN32_STREAM_ID();

        id.dwStreamId = mem.getInt(0);
        id.dwStreamAttributes = mem.getInt(4);
        id.Size = mem.getLong(8);
        id.dwStreamNameSize = mem.getInt(16);

        return id;

    }

    static int files = 0;
    static int dirs = 0;
    static long size = 0;
    static long streamSize = 0;
    static int lastfiles = 0;
    static long lastsize = 0;
    static long last_ts = 0;
    static void check_stat()
    {
        long ts = System.currentTimeMillis();
        if (ts - last_ts > 1000)
        {
            long fps = ((files - lastfiles) * 1000) / (ts - last_ts);
            long mbs = ((size - lastsize) / 1000) / (ts - last_ts);
            System.out.println("F: " + files +  " D: " + dirs + " MB: " + size / 1000000 + " SMB: " + streamSize/1000000 + " Fps: " + fps + " MBs: " + mbs);

            last_ts = ts;
            lastfiles = files;
            lastsize = size;
        }
    }
    static void speed_test( File f  )
    {
        RemoteFSElemFactory factory = new WinRemoteFSElemFactory();
        RemoteFSElem elem = factory.create_elem(f, false);
        
        if (elem.isDirectory())
        {
            dirs++;
        }
        else
        {
            files++;
            size += elem.getDataSize();
            if (elem.getStreamSize() > 0)
                streamSize += elem.getStreamSize();
        }

        check_stat();

        File[] list = f.listFiles();
        if (list != null && list.length > 0)
        {
            for (int i = 0; i < list.length; i++)
            {
                File file = list[i];
                speed_test(file);
            }
        }
    }

    public static void main( String[] args )
    {

        last_ts = System.currentTimeMillis();
        speed_test(new File("M:\\ITunes Michael"));


        //long l = eval_xa_len( new File("manifest.mf") );
        
    }

    @Override
    public String getFsName( String path )
    {
        return LibKernel32.getFsName(path);
    }

   
}
