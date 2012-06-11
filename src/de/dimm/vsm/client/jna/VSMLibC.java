/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.jna;

import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;
import java.nio.ByteBuffer;

/**
 *
 * @author Administrator
 */
public class VSMLibC
{
    public static class FileStat extends Structure
    {
        public int st_dev; // device inode resides on (dev_t)
        public int st_ino; // inode's number (ino_t)
        public short st_mode; // inode protection mode (mode_t - uint16)
        public short st_nlink; // number or hard links to the file (nlink_y - uint16)
        public int st_uid; // user-id of owner (uid_t)
        public int st_gid; // group-id of owner (gid_t)
        public int st_rdev; // device type, for special file inode (st_rdev - dev_t)
        public int st_atime; // Time of last access (time_t)
        public int st_atimensec; // Time of last access (nanoseconds)
        public int st_mtime; // Last data modification time (time_t)
        public int st_mtimensec; // Last data modification time (nanoseconds)
        public int st_ctime; // Time of last status change (time_t)
        public int st_ctimensec; // Time of last status change (nanoseconds)
        public long st_size; // file size, in bytes
        public long st_blocks; // blocks allocated for file
        public int st_blksize; // optimal file system I/O ops blocksize
        public int st_flags; // user defined flags for file
        public int st_gen; // file generation number
        public int st_lspare; // RESERVED: DO NOT USE!
        public long[] st_qspare; // RESERVED: DO NOT USE!
    }
    public static final int ACL_TYPE_ACCESS = 0x8000;
    public static final int ACL_TYPE_DEFAULT = 0x4000;





    public interface CLibrary extends Library
    {
        CLibrary INSTANCE = (CLibrary) Native.loadLibrary((Platform.isWindows() ? "msvcrt" : "c"),
                CLibrary.class);

        public void printf( String format, Object... args );
        public int chmod(String filename, int mode);
        public int chown(String filename, int user, int group);
        public int getegid();
        public int geteuid();
        public int getgid();
        public int getpgid();
        public int getpgrp();
        public int getppid();
        public int getpid();
        public int getuid();
        public int lchmod(String filename, int mode);
        public int lchown(String filename, int user, int group);
        public int link(String oldpath,String newpath);
        public int lstat(String path, FileStat stat);
        public int stat(String path, FileStat stat);
        public int __xstat( int x, String path, FileStat stat);
        public int symlink(String oldpath,String newpath);

        public Pointer acl_get_file(String name, int type);
        public Pointer acl_to_text( Pointer acl, IntByReference len );
        public Pointer acl_from_text(String acl_text);
        public int acl_free(Pointer acl);

        public  int listxattr (String path, ByteBuffer data, int len );
        public  int getxattr (String path, String name, ByteBuffer data, int len );
        public  int setxattr (String path, String name, ByteBuffer data, int len, int flags );
        public  int osx_setxattr (String path, String name, ByteBuffer data, int len, int offset, int flags );
        public  int osx_getxattr (String path, String name, ByteBuffer data, int offset, int len );
    }

    public interface ACLLibrary extends Library
    {
        ACLLibrary INSTANCE = (ACLLibrary) Native.loadLibrary("acl", ACLLibrary.class);

        
        public Pointer acl_get_file(String name, int type);
        public Pointer acl_to_text( Pointer acl, IntByReference len );
        public Pointer acl_from_text(String acl_text);
        public int acl_free(Pointer acl);
    }

//    static
//    {
//        NativeLibrary nl;
//        if (Platform.isWindows())
//        {
//          /*  Map UNICODE_OPTIONS = new HashMap()
//            {
//                {
//                    put("type-mapper", W32APITypeMapper.UNICODE);
//                    put("function-mapper", W32APIFunctionMapper.UNICODE);
//                }
//            };
//*/
//            nl = NativeLibrary.getInstance("msvcrt"/*, UNICODE_OPTIONS*/);
//        }
//        else
//        {
//            nl = NativeLibrary.getInstance("c");
//        }
//
//        Native.register(nl);
//    }

    

    public static int stat(String path, FileStat stat)
    {
        String osname = System.getProperty("os.name");
        if (osname.startsWith("Linux"))
        {
            return CLibrary.INSTANCE.__xstat( 3, path, stat );
        }
        return CLibrary.INSTANCE.stat( path, stat );
    }

    public static int getxattr (String path, String name, ByteBuffer data, int len )
    {
        String osname = System.getProperty("os.name");
        if (osname.startsWith("OSX"))
        {
            return CLibrary.INSTANCE.osx_getxattr( path, name, data, 0, len);
        }
        return CLibrary.INSTANCE.getxattr(path, name, data, len);
    }
    public static int setxattr (String path, String name, ByteBuffer data, int len, int flags )
    {
        String osname = System.getProperty("os.name");
        if (osname.startsWith("OSX"))
        {
            return CLibrary.INSTANCE.osx_setxattr( path, name, data, 0, len, flags);
        }
        return CLibrary.INSTANCE.setxattr(path, name, data, len, flags);
    }



    public static void printf( String format, Object... args )
    {
         CLibrary.INSTANCE.printf(format, args);
    }

}
