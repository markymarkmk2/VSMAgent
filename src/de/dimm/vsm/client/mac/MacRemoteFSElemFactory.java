/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.mac;

import com.sun.jna.*;
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
public class MacRemoteFSElemFactory implements RemoteFSElemFactory
{
    ByteBuffer statBuffer = ByteBuffer.allocate(500);
    
    public static final int ATTR_CMN_NAME =                            0x00000001;
    public static final int ATTR_CMN_DEVID =                           0x00000002;
    public static final int ATTR_CMN_FSID =                            0x00000004;
    public static final int ATTR_CMN_OBJTYPE =                         0x00000008;
    public static final int ATTR_CMN_OBJTAG =                          0x00000010;
    public static final int ATTR_CMN_OBJID =                           0x00000020;
    public static final int ATTR_CMN_OBJPERMANENTID =                  0x00000040;
    public static final int ATTR_CMN_PAROBJID =                        0x00000080;
    public static final int ATTR_CMN_SCRIPT =                          0x00000100;
    public static final int ATTR_CMN_CRTIME =                          0x00000200;
    public static final int ATTR_CMN_MODTIME =                         0x00000400;
    public static final int ATTR_CMN_CHGTIME =                         0x00000800;
    public static final int ATTR_CMN_ACCTIME =                         0x00001000;
    public static final int ATTR_CMN_BKUPTIME =                        0x00002000;
    public static final int ATTR_CMN_FNDRINFO =                        0x00004000;
    public static final int ATTR_CMN_OWNERID =                         0x00008000;
    public static final int ATTR_CMN_GRPID =                           0x00010000;
    public static final int ATTR_CMN_ACCESSMASK =                      0x00020000;
    public static final int ATTR_CMN_FLAGS =                           0x00040000;
/*  #define ATTR_CMN_NAMEDATTRCOUNT             0x00080000       not implemented */
/*  #define ATTR_CMN_NAMEDATTRLIST              0x00100000       not implemented */
    public static final int ATTR_CMN_USERACCESS =                      0x00200000;
    public static final int ATTR_CMN_EXTENDED_SECURITY =               0x00400000;
    public static final int ATTR_CMN_UUID =                            0x00800000;
    public static final int ATTR_CMN_GRPUUID =                         0x01000000;
    public static final int ATTR_CMN_FILEID =                          0x02000000;
    public static final int ATTR_CMN_PARENTID =                        0x04000000;
    public static final int ATTR_CMN_FULLPATH =                        0x08000000;
    public static final int ATTR_CMN_ADDEDTIME =                       0x10000000;

/*
 * ATTR_CMN_RETURNED_ATTRS is only valid with getattrlist(2).
 * It is always the first attribute in the return buffer.
 */
    public static final int ATTR_CMN_RETURNED_ATTRS =                  0x80000000;

    public static final int ATTR_CMN_VALIDMASK =                       0x9FE7FFFF;
    public static final int ATTR_CMN_SETMASK =                         0x01C7FF00;
    public static final int TTR_CMN_VOLSETMASK =                       0x00006700;


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
    
//    typedef u_int32_t attrgroup_t;

     static public class Attrlist extends Structure  
     {
         
         public short  bitmapcount; /* number of attr. bit sets in list */
         public short   reserved;    /* (to maintain 4-byte alignment) */
         public int  commonattr;  /* common attribute group */
         public int   volattr;     /* volume attribute group */
         public int   dirattr;     /* directory attribute group */
         public int   fileattr;    /* file attribute group */
         public int   forkattr;    /* fork attribute group */
     };
     public static final int ATTR_BIT_MAP_COUNT = 5;
     
     static public class Attrreference  extends Structure  
     {
         public int        attr_dataoffset;
         public int      attr_length;
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
    private static P4 p4 = (P4) Native.loadLibrary( "P4", P4.class);

    
    interface P4 extends Library
    {
         int getRef( String s, FSRef r );
         boolean IsFSRefValid( FSRef  ref);
    }

    public static int stat(String path, StatStructure stat )
    {
        return delegate.stat( path, stat );
    }

    public int statbfs(String path )
    {
        return delegate.statfs( path, statBuffer  );
    }
    
    public boolean isReffable( String s )
    {
        Class cl = FSRef.class;
        try {
            FSRef ref = (FSRef) cl.newInstance();
            
            p4.getRef(s, ref);
            
            return p4.IsFSRefValid(ref);
        } catch (Exception instantiationException) 
        {
            instantiationException.printStackTrace();
        }
        return false;
   }



    interface StatInterface extends Library
    {
        int mkfifo( String pathname, int mode_t );
        int unlink( String path );
        int chmod( String path, int mode_t );
        int stat( String path, StatStructure stat );
      
        int statfs( String path, ByteBuffer bb);
        int getattrlist(String path, Attrlist  attrList, ByteBuffer attrBuf, int attrBufSize, long options);
    }
    
    
     String getFSType( )
    {
        byte[] b = new byte[15];
        byte[] arr = statBuffer.array();
        System.arraycopy(arr, 104, b, 0,  b.length);
        int l = 0;
        while (l < b.length - 1 && b[l]!= 0)
            l++;
        return new String(b, 0, l);
    }
     String getMntTo()
    {
        byte[] b = new byte[90];
        byte[] arr = statBuffer.array();
        System.arraycopy(arr, 119, b, 0,  b.length);
        int l = 0;
        while (l < b.length - 1 && b[l]!= 0)
            l++;
        return new String(b, 0, l);
    }
     String getMntFrom()
    {
        byte[] b = new byte[255];
        byte[] arr = statBuffer.array();
        System.arraycopy(arr, 209, b, 0,  b.length);
        int l = 0;
        while (l < b.length - 1 && b[l]!= 0)
            l++;
        return new String(b, 0, l);
    }
     long getLong( int offset)
    {
        byte[] arr = statBuffer.array();
        ByteBuffer bb2 = ByteBuffer.wrap(arr);
        bb2.order(ByteOrder.LITTLE_ENDIAN);
        return bb2.getLong(offset);    
    }
     short getShort(int offset)
    {
        byte[] arr = statBuffer.array();
        ByteBuffer bb2 = ByteBuffer.wrap(arr);
        bb2.order(ByteOrder.LITTLE_ENDIAN);
        return bb2.getShort(offset);    
    }

    public static void main( String[] args)
    {
        MacRemoteFSElemFactory f = new MacRemoteFSElemFactory();        
        
//        statfs("/", stat);
        f.statbfs("/");
//        long b = stat.f_bsize;
//        if (b == 0)
//            b = stat.f_iosize;
        long b = f.getLong( 8) / 1024;
        if (b == 0)
            b = f.getLong( 8);
        long iosize = f.getLong( 16);
        long blocks = f.getLong( 24);
        long bfree = f.getLong( 40);
        long bavail = f.getLong( 48);
        
        
        System.out.println( f.getFSType() );
        System.out.println( f.getMntFrom() );
        System.out.println( f.getMntTo() );
        System.out.println( "Free: " + b*bfree ); 
        System.out.println( "Total: " + b*bavail );  
        System.out.println( "Used: " + b*(bavail-bfree) );                  
    }

    
    public String getFSname( String path )
    {
        statBuffer.rewind();
        statbfs(path);
        return getFSType();
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
        RemoteFSElemFactory factory = new MacRemoteFSElemFactory();
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
     
//    struct FInfoAttrBuf {
//         u_int32_t       length;
//         fsobj_type_t    objType;
//         char            finderInfo[32];
//     }  __attribute__((aligned(4), packed));

     ByteBuffer allocByteBuffer( int len)
     {
         ByteBuffer  buff = ByteBuffer.allocate(len);
         buff.order(ByteOrder.LITTLE_ENDIAN);
         if (System.getProperty("os.arch").startsWith("ppc"))
         {
             buff.order(ByteOrder.BIG_ENDIAN);
         }
         
         return buff;
     }


     public int FInfoDemo(String path)
     {
         int             err;
         Attrlist      attrList = new Attrlist();
         ByteBuffer  buff = allocByteBuffer(4096);
             

         
         attrList.bitmapcount = ATTR_BIT_MAP_COUNT;
         attrList.commonattr  = ATTR_CMN_OBJTYPE | ATTR_CMN_FNDRINFO;

         err = delegate.getattrlist(path, attrList, buff, buff.limit(), 0);

         if (err == 0) 
         {

             System.out.println("Finder information for " +  path);
             
             int length = buff.getInt(0);
             int objType = buff.getInt(4);

             byte[] arr = buff.array();
             String type = new String( arr, 8, 4 );
             String creator = new String( arr, 12, 4 );
             System.out.println("Object type " +  objType);
             System.out.println("Type / creator " +  type + " " + creator);
             
         }
         
         File fr = new File(path + "/..namedfork/rsrc");
         long rlen = fr.length();
         File fd = new File(path);
         long dlen = fd.length();
         System.out.println("Datafork: " + dlen + " RsrcFork: " + rlen );
         
         return err;
     }
     
   
}
