/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.mac;

import com.sun.jna.*;
import com.thoughtworks.xstream.XStream;
import de.dimm.vsm.Utilities.ZipUtilities;
import de.dimm.vsm.client.AttributeContainerImpl;
import de.dimm.vsm.client.FSElemAccessor;
import de.dimm.vsm.client.RemoteFSElemFactory;
import de.dimm.vsm.client.jna.PosixWrapper;
import de.dimm.vsm.client.jna.VSMLibC;
import de.dimm.vsm.net.AttributeContainer;
import de.dimm.vsm.net.AttributeEntry;
import de.dimm.vsm.net.AttributeList;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.records.FileSystemElemNode;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.jruby.ext.posix.FileStat;
import org.jruby.ext.posix.Group;
import org.jruby.ext.posix.POSIX;
import org.jruby.ext.posix.Passwd;



/**
 *
 * @author Administrator
 */
public class MacRemoteFSElemFactory extends RemoteFSElemFactory
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

    
    public static final int FSOPT_NOFOLLOW =                           0x00000001;
    public static final int FSOPT_NOINMEMUPDATE =                      0x00000002;
    public static final int FSOPT_REPORT_FULLSIZE =                    0x00000004;

    @Override
    public String getXAPath( String path )
    {
        return getRsrcPath(path);
    }

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
    public static int stat(String path, StatStructure stat )
    {
        return delegate.stat( path, stat );
    }
    public int statfs(String path, ByteBuffer statBuffer )
    {
        return delegate.statfs( path, statBuffer  );
    }

    public int statbfs(String path )
    {
        return delegate.statfs( path, statBuffer  );
    }
    
    interface StatInterface extends Library
    {
        int mkfifo( String pathname, int mode_t );
        int unlink( String path );
        int chmod( String path, int mode_t );
        int stat( String path, StatStructure stat );
      
        int statfs( String path, ByteBuffer bb);
        int getattrlist(String path, Attrlist  attrList, ByteBuffer attrBuf, int attrBufSize, long options);
        int setattrlist(String path, Attrlist  attrList, byte[] attrBuf, int attrBufSize, long options);
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

    

    public String getFsName( String path )
    {
        statBuffer.rewind();
        statbfs(path);
        return getFSType();
    }

    public synchronized  RemoteFSElem create_elem( File fh, boolean lazyAclInfo )
    {
        String path = fh.getAbsolutePath();

        
        String typ = fh.isDirectory() ? FileSystemElemNode.FT_DIR : FileSystemElemNode.FT_FILE;

        fh.length();
        long len = get_flen( fh );
        long streamLen = evalStreamLen( fh );


        POSIX posix = PosixWrapper.getPosix();

        FileStat stat = null;
        int ret = -1;
        try
        {
            stat = posix.lstat(path);
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
            
            elem = new RemoteFSElem( path, typ,
                    stat.mtime() * 1000, stat.ctime() * 1000, stat.atime() * 1000,
                    len, streamLen );

            elem.setPosixData(stat.mode(), stat.uid(), stat.gid(), uidName, gidName);

            if (stat.isSymlink())
            {
                try
                {
                    String rl = posix.readlink(path);
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
            elem = new RemoteFSElem( path, typ,
                    fh.lastModified(), fh.lastModified(), fh.lastModified(),
                    len, streamLen );
        }

        elem.setStreaminfo(RemoteFSElem.STREAMINFO_APPLEDOUBLE);

        if (!elem.isSymbolicLink()/* && Main.hasNFSv4()*/)
        {
            if (lazyAclInfo)
            {
                elem.setAclinfoData(RemoteFSElem.LAZY_ACLINFO);
                elem.setAclinfo(RemoteFSElem.ACLINFO_OSX);
            }
            else
            {

                try 
                {
                    elem.setAclinfoData(readAclInfo(elem));
                } 
                catch (IOException iOException) 
                {
                    System.out.println("DFehler beim Lesen der Attribute " + iOException.getMessage() );
                }
                elem.setAclinfo(RemoteFSElem.ACLINFO_OSX);
            }
        }
       
        return elem;
    }
    
    static String getRsrcPath( String path )
    {
        return path + "/..namedfork/rsrc";
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
        String adpPath = getRsrcPath( fh.getAbsolutePath() );

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
        MacRemoteFSElemFactory factory = new MacRemoteFSElemFactory();
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
    public synchronized  String readAclInfo( RemoteFSElem elem ) throws IOException
    {
        AttributeList attrs = get_attributes(elem);
        if (attrs == null)
        {
            return null;
        }
        if (attrs.getList().isEmpty())
        {
            return null;
        }
        XStream xs = new XStream();
        String s = ZipUtilities.compress(xs.toXML(attrs));
        return s;
    }
     
//    struct FInfoAttrBuf {
//         u_int32_t       length;
//         fsobj_type_t    objType;
//         char            finderInfo[32];
//     }  __attribute__((aligned(4), packed));


     
//
//
//         public ByteBuffer readFromFs(String path)
//         {
//             return readFromFsA(path);
//         }
         
         public ByteBuffer getResourceData( String path ) throws IOException
         {
             File f = new File(path + "/..namedfork/rsrc");
             if (!f.exists())
                 return ByteBuffer.allocate(0);
             if (f.length() == 0)
                 return ByteBuffer.allocate(0);
             
             if (f.length() >= Integer.MAX_VALUE - 1000)
                 throw new IOException("Resourcefork too large (" + f.length() + "): " + path);
             
             byte[] data = new byte[(int)f.length()];
             
             FileInputStream fio = null;
             try 
             {
                 fio = new FileInputStream(f);
                 int rlen = fio.read(data);
                 if (rlen != data.length) {
                     throw new IOException("Resourcefork incomplete (" + rlen + "/" + f.length() + "): " + path);
                 }
             } 
             finally
             {
                 if (fio != null)
                 {
                     fio.close();
                 }                 
             }
             return ByteBuffer.wrap( data );
         }
         
         public void putResourceData( String path, byte[] data ) throws IOException
         {
             File f = new File(path + "/..namedfork/rsrc");
                          
             FileOutputStream fio = null;
             try 
             {
                 fio = new FileOutputStream(f);
                 fio.write(data);                 
             } 
             finally
             {
                 if (fio != null)
                 {
                     fio.close();
                 }                 
             }             
         }
//
//         ByteBuffer readFromFsA(String path)
//         {
//             ByteBuffer  buff = allocByteBuffer(4096);
//             char version = 'a';
//             Attrlist attrList = new Attrlist();
//
//             attrList.bitmapcount = ATTR_BIT_MAP_COUNT;
//             attrList.commonattr = ATTR_CMN_FNDRINFO |ATTR_CMN_EXTENDED_SECURITY;
//
//             int err = delegate.getattrlist(path, attrList, buff, buff.limit(), FSOPT_REPORT_FULLSIZE);
//             if (err != 0)
//                 return null;
//
//            int length = buff.getInt(0);
//            if (length >= buff.limit())
//            {
//                buff = allocByteBuffer(length);
//                err = delegate.getattrlist(path, attrList, buff, buff.limit(), FSOPT_REPORT_FULLSIZE);
//                if (err != 0)
//                    return null;
//            }
//            buff.limit(length);
//            ByteBuffer data = allocByteBuffer( length + 2 );
//            data.putChar(version);
//            data.put(buff.array(), 2, length);
//
//
//            return data;
//         }
//
//         public boolean writeToFs(String path, byte[] buff)
//         {
//             ByteBuffer data = ByteBuffer.wrap(buff);
//             char version = data.getChar();
//
//             ByteBuffer wbuff = ByteBuffer.wrap(buff, 2, buff.length - 2);
//
//             if (version == 'a')
//                 return writeToFsA(path, wbuff);
//
//             return false;
//         }
//
//         boolean writeToFsA(String path, ByteBuffer buff)
//         {
//             Attrlist attrList = new Attrlist();
//
//             attrList.bitmapcount = ATTR_BIT_MAP_COUNT;
//             attrList.commonattr = ATTR_CMN_FNDRINFO |ATTR_CMN_EXTENDED_SECURITY;
//
//             byte[] bb = buff.array();
//             int err = delegate.setattrlist(path, attrList, bb, bb.length, 0);
//
//             if (err != 0)
//                 return false;
//
//            return true;
//         }
//
//
         

    public static int getattrlist(String path, Attrlist  attrList, ByteBuffer attrBuf, int attrBufSize, long options)
    {
         return delegate.getattrlist(path, attrList, attrBuf, attrBufSize, options);
    }
    public static int setattrlist(String path, Attrlist  attrList, byte[] attrBuf, int attrBufSize, long options)
    {
         return delegate.setattrlist(path, attrList, attrBuf, attrBufSize, options);
    }


     public int FInfoDemo(String path)
     {
         int             err;
         Attrlist      attrList = new Attrlist();
         ByteBuffer  buff = allocByteBuffer(4096);
             

         
         attrList.bitmapcount = ATTR_BIT_MAP_COUNT;
         attrList.commonattr  = ATTR_CMN_OBJTYPE | ATTR_CMN_FNDRINFO |ATTR_CMN_EXTENDED_SECURITY;
         attrList.commonattr  =  ATTR_CMN_FNDRINFO;

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
     
//    public File[] listFiles( File dir )
//    {
//        
//        // WORK AROUND MAC BUG
//        FileSystem fs = FileSystems.getDefault();
//        Path p = fs.getPath(dir.getAbsolutePath());
//        
//        List <File> ret = new ArrayList<File>();
//        
//        try
//        {
//            DirectoryStream<Path> files = Files.newDirectoryStream(p);
//            
//            for (Path path : files)
//            {
//                ret.add( path.toFile() );
//            }
//            files.close();
//        }
//        catch (IOException iOException)
//        {
//            iOException.printStackTrace();
//        }
//        return ret.toArray( new File[0]);
//        
//    }
//
//        
//    static void checkEnc()
//    {
//        Main.print_system_property("sun.jnu.encoding");
//        Main.print_system_property("file.encoding");
//        File dir = new File("/Users/mw/Desktop/A");
//        
//        FileSystem fs = FileSystems.getDefault();
//        Path p = fs.getPath(dir.getAbsolutePath());
//        
//        try
//        {
//            DirectoryStream<Path> files = Files.newDirectoryStream(p);
//            
//            for (Path path : files)
//            {
//                
//                System.out.println(path.toString());
//                File f = path.toFile();
//                
//                System.out.println(f.getAbsolutePath() + ": " + (f.exists()?"ok" : "nok") + " : len: " + f.length());
//                String nfc_path = Normalizer.normalize(path.toString(), Normalizer.Form.NFC);
//                System.out.println(nfc_path);
//            }
//            files.close();
//        }
//        catch (IOException iOException)
//
//        {
//            iOException.printStackTrace();
//        }
//        Main.print_system_property("sun.jnu.encoding");
//        Main.print_system_property("file.encoding");
//        
//        
//            
//            
//    }



    

    public List<AttributeEntry> getACLFinderAttributeEntry(String path) throws IOException
    {
         int             err;
         Attrlist      attrList = new Attrlist();
         List<AttributeEntry> al = new ArrayList<AttributeEntry>();

         ByteBuffer  buff = MacRemoteFSElemFactory.allocByteBuffer(4096);

         attrList.bitmapcount = MacRemoteFSElemFactory.ATTR_BIT_MAP_COUNT;
         attrList.commonattr  =MacRemoteFSElemFactory.ATTR_CMN_FNDRINFO | MacRemoteFSElemFactory.ATTR_CMN_EXTENDED_SECURITY;

         err = MacRemoteFSElemFactory.getattrlist(path, attrList, buff, buff.limit(), MacRemoteFSElemFactory.FSOPT_REPORT_FULLSIZE);

         if (err == 0)
         {

             int length = buff.getInt();
             if (length <= 36)
                 throw new IOException( "Invalid FinderInfoLen" );

             if (length > buff.capacity())
             {
                 buff = MacRemoteFSElemFactory.allocByteBuffer(length);
                 MacRemoteFSElemFactory.getattrlist(path, attrList, buff, buff.limit(), MacRemoteFSElemFactory.FSOPT_REPORT_FULLSIZE);
                 int newlen = buff.getInt();

                 if (newlen != length)
                     throw new IOException("Len mismatch in getattrlist");
             }

             byte[] info = new byte[32];
             buff.get(info, 0, 32);

             AttributeEntry entry;

             // DETECT A NON-EMPTY FINDERINFO
             for (int i = 0; i < info.length; i++)
             {
                 byte b = info[i];
                 if (b != 0)
                 {
                    entry = new AttributeEntry(FNDRINFONAME, info);
                    al.add(entry);
                    //System.out.println("Finderattribute für " + path + " " + entry.toString());
                    break;
                 }
             }


//           typedef struct attrreference
//           {
//               int32_t        attr_dataoffset;
//               u_int32_t      attr_length;
//           }
//           attrreference_t;
             int attr_dataoffset = buff.getInt();
             int attr_length = buff.getInt();
             if (attr_length > 0)
             {
                byte[] acl = new byte[attr_length];
                buff.get(acl, 0, attr_length);

                entry = new AttributeEntry(ACLNAME, acl);
                al.add(entry);

              //  System.out.println("ACLattribute für " + path + " " + entry.toString());
             }
        }
         else
         {
             System.out.println("Fehler beim Lesen der ACL-Finderattribute für " + path);
         }
        return al;
    }


    public boolean setACLFinderAttributeEntry(String path, List<AttributeEntry> al) throws Exception
    {
         int err = 0;
         Attrlist attrList = new Attrlist();

         byte[] info = null;
         byte[] acl = null;
         int len = 0;
         for (int i = 0; i < al.size(); i++)
         {

             if (al.get(i).getEntry().equals(FNDRINFONAME))
             {
                 len += al.get(i).getData().length;
                 attrList.commonattr |= MacRemoteFSElemFactory.ATTR_CMN_FNDRINFO;
                 info = al.get(i).getData();
                 if( info.length != 32)
                     throw new IOException("Invalid FinderInfoLen");
             }
             if (al.get(i).getEntry().equals(ACLNAME))
             {
                 len += al.get(i).getData().length;
                 attrList.commonattr |= MacRemoteFSElemFactory.ATTR_CMN_EXTENDED_SECURITY;
                 acl = al.get(i).getData();
                 len += 8; // ATTRENTRY STRUCT
             }
         }

         if (len > 0)
         {

            // NO LEADING LEN setattrlist
            ByteBuffer buff = MacRemoteFSElemFactory.allocByteBuffer(len );
            attrList.bitmapcount = MacRemoteFSElemFactory.ATTR_BIT_MAP_COUNT;


            if (info != null)
                buff.put(info, 0, 32);
            if (acl != null)
            {
//           typedef struct attrreference
//           {
//               int32_t        attr_dataoffset;
//               u_int32_t      attr_length;
//           }
//           attrreference_t;
                buff.putInt( 8 );
                buff.putInt(acl.length);
                buff.put(acl, 0, acl.length);
            }
            byte[] bb = buff.array();
            err = MacRemoteFSElemFactory.setattrlist(path, attrList, bb, bb.length, 0);
         }
         return (err == 0);

    }

    private void set_attributes( RemoteFSElem elem,  AttributeList attrs ) throws Exception
    {
        String path = elem.getPath();
        path = Normalizer.normalize(path, Normalizer.Form.NFD);

        if (!setACLFinderAttributeEntry( path, attrs.getList() ))
        {
            System.out.println("Fehler beim Setzen der ACL-Finderattribute für " + path);
        }

        for (int i = 0; i < attrs.getList().size(); i++)
        {
            AttributeEntry entry = attrs.getList().get(i);
            String name = entry.getEntry();

            if (!name.equals(FNDRINFONAME) && !name.equals(ACLNAME))
            {
                ByteBuffer buff = ByteBuffer.wrap(entry.getData());
                if (VSMLibC.setxattr(path, name, buff, buff.capacity(), 0) != 0)
                {
                    System.out.println("Fehler beim Setzen des Attributes " + name + " für " + path);
                }
            }
        }
    }

    public static final String[] skipAttributes=
    {
        "system.posix_acl_access",
        "system.posix_acl_default",
        "com.apple.FinderInfo",
        "com.apple.ResourceFork"
    };


        
    private AttributeList get_attributes( RemoteFSElem elem ) throws IOException
    {
       AttributeList list = new AttributeList();

        String path = elem.getPath();

        int len = VSMLibC.listxattr(path, null, 0);


        if (len > 0)
        {
            ByteBuffer buff = ByteBuffer.allocate(4096);
            len = VSMLibC.listxattr(path, buff, buff.capacity());
            int errno = Native.getLastError();

            if (len <= 0)
                return null;

            byte[] arr = new byte[len];
            buff.get(arr, 0, len);
            String[] names = FSElemAccessor.nulltermList2Array(arr);

            for (int i = 0; i < names.length; i++)
            {
                String name = names[i];
                byte[] data = null;

                // SKIP ALL AUTOMATICALLY HANDLED ATTRIBUTES
                boolean skipAttribute = false;

                for (int j = 0; j < skipAttributes.length; j++)
                {
                    String attrName = skipAttributes[j];
                    if (name.equals(attrName))
                    {
                        skipAttribute = true;
                        break;
                    }
                }

                if (!skipAttribute)
                {
                    System.out.println("Adding Mac attribute " + name + " for " + path);

                    len = VSMLibC.getxattr(path, name, null, 0);
                    if (len > 0)
                    {
                        buff = ByteBuffer.allocate(len);
                        len = VSMLibC.getxattr(path, name, buff, buff.capacity());
                        data = new byte[len];
                        buff.get(data, 0, len);

                        AttributeEntry entry = new AttributeEntry(name, data);
                        list.getList().add(entry);
                    }
                }
            }
        }
        // ADD FINDERINFO
        List<AttributeEntry> entries = getACLFinderAttributeEntry(path);
        list.getList().addAll(entries);
        return list;
    }
    
    @Override
    public void writeAclInfo( RemoteFSElem elem ) throws IOException
    {
        try
        {
            if (elem.getAclinfo() == RemoteFSElem.ACLINFO_OSX)
            {
                if (elem.getAclinfoData() != null)
                {
                    XStream xs = new XStream();
                    String s = ZipUtilities.uncompress(elem.getAclinfoData());
                    Object o = xs.fromXML(s);
                    if (o instanceof AttributeList)
                    {
                        set_attributes(elem, (AttributeList) o);
                    }
                }
            }
            else
            {
                AttributeContainer ac = AttributeContainer.unserialize(elem.getAclinfoData());
                if (ac != null)
                {
                    AttributeContainerImpl.set(elem, ac);
                }
            }
        }
        catch (Exception exception)
        {
            throw new IOException("Error while setting ACL and FinderInfo:" + exception.getMessage() );
        }
    }

    static void testCharSet()
    {
        File dir = new File("/tmp/A");
        File[] list = dir.listFiles();
        for (int f = 0; f < list.length; f++)
        {
            File fh = list[f];


            String path = fh.getAbsolutePath();
            System.out.println(path + " :len: " + fh.length() );

            MacAgentApi api = new MacAgentApi(null, "");
            MacFSElemAccessor fs = new MacFSElemAccessor(api);
            MacRemoteFSElemFactory factory = new MacRemoteFSElemFactory();
            try
            {
                AttributeList al = new AttributeList();
                List<AttributeEntry> all = factory.getACLFinderAttributeEntry(path);
                System.out.println( "Alen: " + all);
                al.getList().addAll(all);
                System.out.println( al.toString() );

                if (!factory.setACLFinderAttributeEntry( path, all ))
                {
                    System.out.println( "Error while setting " + path );
                }
            }
            catch (Exception iOException)
            {
                iOException.printStackTrace();
            }

            System.out.println(Normalizer.normalize(path, Normalizer.Form.NFC).getBytes().length);
            System.out.println(Normalizer.normalize(path, Normalizer.Form.NFD).getBytes().length);
            System.out.println(Normalizer.normalize(path, Normalizer.Form.NFKC).getBytes().length);
            System.out.println(Normalizer.normalize(path, Normalizer.Form.NFKD).getBytes().length);
        }
    }

    @Override
    public boolean mkDir( File f )
    {
        return f.mkdir();
    }


}
