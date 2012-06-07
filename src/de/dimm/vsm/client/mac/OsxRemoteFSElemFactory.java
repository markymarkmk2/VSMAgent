/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.mac;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import de.dimm.vsm.client.AttributeContainerImpl;
import de.dimm.vsm.client.RemoteFSElemFactory;
import de.dimm.vsm.client.jna.PosixWrapper;
import de.dimm.vsm.net.AttributeContainer;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.records.FileSystemElemNode;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import org.jruby.ext.posix.FileStat;
import org.jruby.ext.posix.Group;
import org.jruby.ext.posix.POSIX;
import org.jruby.ext.posix.Passwd;



/**
 *
 * @author Administrator
 */
public class OsxRemoteFSElemFactory implements RemoteFSElemFactory
{

    static public class StatStructure extends Structure
{

    public long st_dev; /* ID of device containing file */
    public long st_ino; /* inode number */
    public long st_mode; /* protection */
    public long st_nlink; /* number of hard links */
    public long st_uid; /* user ID of owner */
    public long st_gid; /* group ID of owner */
    public long st_rdev; /* device ID (if special file) */
    public long st_size; /* total size, in bytes */
    public long st_blksize; /* blocksize for filesystem I/O */
    public long st_blocks; /* number of blocks allocated */
    public long st_atime; /* time of last access */
    public long st_mtime; /* time of last modification */
    public long st_ctime; /* time of last status change */


    
    }
///*
// * struct statfs {
//         short   f_otype;    /* type of file system (reserved: zero) */
//         short   f_oflags;   /* copy of mount flags (reserved: zero) */
//         long    f_bsize;    /* fundamental file system block size */
//         long    f_iosize;   /* optimal transfer block size */
//         long    f_blocks;   /* total data blocks in file system */
//         long    f_bfree;    /* free blocks in fs */
//         long    f_bavail;   /* free blocks avail to non-superuser */
//         long    f_files;    /* total file nodes in file system */
//         long    f_ffree;    /* free file nodes in fs */
//         fsid_t  f_fsid;     /* file system id */
//         uid_t   f_owner;    /* user that mounted the file system */
//         short   f_reserved1;        /* reserved for future use */
//         short   f_type;     /* type of file system (reserved) */
//         long    f_flags;    /* copy of mount flags (reserved) */
//         long    f_reserved2[2];     /* reserved for future use */
//         char    f_fstypename[MFSNAMELEN]; /* fs type name */
//         char    f_mntonname[MNAMELEN];    /* directory on which mounted */
//         char    f_mntfromname[MNAMELEN];  /* mounted file system */
//         char    f_reserved3;        /* reserved for future use */
//         long    f_reserved4[4];     /* reserved for future use */
//     };
// */
static public class StatFSStructure extends Structure
{
         public short       f_otype;    /* type of file system (reserved: zero) */
         public short       f_oflags;   /* copy of mount flags (reserved: zero) */
         public long        f_bsize;        /* fundamental file system block size */
         public long          f_iosize;       /* optimal transfer block size */
         public long         f_blocks;       /* total data blocks in file system */
         public long         f_bfree;        /* free blocks in fs */
         public long         f_bavail;       /* free blocks avail to non-superuser */
         public long         f_files;        /* total file nodes in file system */
         public long         f_ffree;        /* free file nodes in fs */
         public long          f_fsid;         /* file system id */
         public short           f_owner;        /* user that mounted the filesystem */
         public short         f_type;         /* type of filesystem */
         public long         f_flags;        /* copy of mount exported flags */
         public long         f_fssubtype;    /* fs sub-type (flavor) */
         public long         f_freserved1;    /* fs sub-type (flavor) */
         
         
//         ByteBuffer          f_fstypename = ByteBuffer.allocate(16);
//         ByteBuffer          f_mntonname = ByteBuffer.allocate(1024);
//         ByteBuffer          f_mntfromname = ByteBuffer.allocate(1024);
     };
     
    String getTypFromStat(FileStat stat)
    {
        if (stat.isFile())
            return FileSystemElemNode.FT_FILE;
        if (stat.isDirectory())
            return FileSystemElemNode.FT_DIR;
        if (stat.isSymlink())
            return FileSystemElemNode.FT_SYMLINK;

        return FileSystemElemNode.FT_OTHER;
    }


    private static StatInterface delegate = (StatInterface)Native.loadLibrary( "c", StatInterface.class);



    public static int stat(String path, StatStructure stat )
    {
        return delegate.stat( path, stat );
    }
    public static int statfs(String path, StatFSStructure stat )
    {
        return delegate.statfs( path, stat );
    }
    public static int statbfs(String path, ByteBuffer stat )
    {
        return delegate.statfs( path, stat );
    }



    interface StatInterface extends Library
    {
        int mkfifo( String pathname, int mode_t );
        int unlink( String path );
        int chmod( String path, int mode_t );
        int stat( String path, StatStructure stat );
        int statfs( String path, StatFSStructure stat );
        int statfs( String path, ByteBuffer bb);
    }
    static String getFSType( ByteBuffer bb )
    {
        byte[] b = new byte[15];
        byte[] arr = bb.array();
        System.arraycopy(arr, 104, b, 0,  b.length);
        return new String(b);
    }
    static String getMntFrom( ByteBuffer bb )
    {
        byte[] b = new byte[90];
        byte[] arr = bb.array();
        System.arraycopy(arr, 119, b, 0,  b.length);
        return new String(b);
    }
    static String getMntTo( ByteBuffer bb )
    {
        byte[] b = new byte[255];
        byte[] arr = bb.array();
        System.arraycopy(arr, 209, b, 0,  b.length);
        return new String(b);
    }
    static long getLong(ByteBuffer bb, int offset)
    {
        byte[] arr = bb.array();
        ByteBuffer bb2 = ByteBuffer.wrap(arr);
        bb2.order(ByteOrder.LITTLE_ENDIAN);
        return bb2.getLong(offset);    
    }
    static short getShort(ByteBuffer bb, int offset)
    {
        byte[] arr = bb.array();
        ByteBuffer bb2 = ByteBuffer.wrap(arr);
        bb2.order(ByteOrder.LITTLE_ENDIAN);
        return bb2.getShort(offset);    
    }

    public static void main( String[] args)
    {
    
        StatFSStructure stat = new StatFSStructure();
        ByteBuffer bb = ByteBuffer.allocate(500);
        
//        statfs("/", stat);
        statbfs("/", bb);
//        long b = stat.f_bsize;
//        if (b == 0)
//            b = stat.f_iosize;
        long b = getLong(bb, 8) / 1024;
        if (b == 0)
            b = getLong(bb, 8);
        long iosize = getLong(bb, 16);
        long blocks = getLong(bb, 24);
        long bfree = getLong(bb, 40);
        long bavail = getLong(bb, 48);
        
        
        System.out.println( getFSType(bb) );  
        System.out.println( getMntFrom(bb) );  
        System.out.println( getMntTo(bb) ); 
        System.out.println( "Free: " + b*bfree ); 
        System.out.println( "Total: " + b*bavail );  
        System.out.println( "Used: " + b*(bavail-bfree) );  
        
        
    }

    @Override
    public synchronized  RemoteFSElem create_elem( File fh, boolean lazyAclInfo )
    {
        String typ = fh.isDirectory() ? FileSystemElemNode.FT_DIR : FileSystemElemNode.FT_FILE;

        long len = get_flen( fh );
        long streamLen = evalStreamLen( fh );


        POSIX posix = PosixWrapper.getPosix();

        FileStat stat = null;
        int ret = -1;
        try
        {
            stat = posix.stat(fh.getAbsolutePath() );
        }
        catch (Exception e)
        {
        }
        
        RemoteFSElem elem = null;
        if (stat != null)
        {            
            typ = getTypFromStat(stat);

            String gidName = "";
            Group gr = posix.getgrgid( stat.gid());
            if (gr != null)
                gidName = gr.getName();

            Passwd pw = posix.getpwuid(stat.uid());
            String uidName = "";
            if (pw != null)
                uidName = pw.getLoginName();
            
            elem = new RemoteFSElem( fh.getAbsolutePath(), typ,
                    stat.mtime() * 1000, stat.ctime() * 1000, stat.atime() * 1000,
                    len, streamLen );

            elem.setPosixData(stat.mode(), stat.uid(), stat.gid(), uidName, gidName);

            if (stat.isSymlink())
            {
                try
                {
                    String rl = posix.readlink(fh.getAbsolutePath());
                    elem.setLinkPath(rl);
                }
                catch (IOException iOException)
                {
                    return null;
                }
            }
        }
        else
        {
            elem = new RemoteFSElem( fh.getAbsolutePath(), typ,
                    fh.lastModified(), fh.lastModified(), fh.lastModified(),
                    len, streamLen );
        }

        elem.setStreaminfo(RemoteFSElem.STREAMINFO_APPLEDOUBLE);

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
    
    static String getADPath( String path )
    {
        StringBuilder sb = new StringBuilder(path);
        int fidx = sb.lastIndexOf(File.separator);
        if (fidx > 0)
            sb.insert(fidx, "/.AppleDouble");
        
        return sb.toString();
    }


    static long get_flen( File fh )
    {
        return fh.length();
    }
    static String get_path( File fh )
    {
        return fh.getAbsolutePath();
    }

    
    public static long evalStreamLen( File fh )
    {
        String adpPath = getADPath( fh.getAbsolutePath() );

        File f = new File(adpPath);

        if (f.exists())
            return f.length();

        return 0;
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
        RemoteFSElemFactory factory = new OsxRemoteFSElemFactory();
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
//
//    public static void main( String[] args )
//    {
//
//        last_ts = System.currentTimeMillis();
//        speed_test(new File("M:\\ITunes Michael"));
//
//
//        //long l = eval_xa_len( new File("manifest.mf") );
//        
//    }


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


   
}
