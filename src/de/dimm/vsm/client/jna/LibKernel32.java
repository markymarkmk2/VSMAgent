/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.dimm.vsm.client.jna;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.Union;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.W32APIFunctionMapper;
import com.sun.jna.win32.W32APITypeMapper;
import java.text.SimpleDateFormat;
import java.util.Date;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Administrator
 */
public class LibKernel32
{

    public static final int CREATE_NEW = 1;
    public static final int CREATE_ALWAYS = 2;
    public static final int OPEN_EXISTING = 3;
    public static final int OPEN_ALWAYS = 4;
    public static final int TRUNCATE_EXISTING = 5;

    public static final int FILE_LIST_DIRECTORY = 1;

    public static final int GENERIC_ALL = 0x10000000;
    public static final int GENERIC_READ = 0x80000000;
    public static final int GENERIC_WRITE = 0x40000000;
    public static final int GENERIC_EXECUTE = 0x20000000;

    public static final int READ_CONTROL = 0x00020000;
    public static final int FILE_APPEND_DATA = 0x0004;

    public static final int WRITE_OWNER = 0x00080000;
    public static final int WRITE_DAC =   0x00040000;
    public static final int FILE_ATTRIBUTE_DIRECTORY = 0x10;
    public static final int FILE_SHARE_NONE = 0x00000000;
    public static final int FILE_SHARE_READ = 0x00000001;
    public static final int FILE_SHARE_WRITE = 0x00000002;
    public static final int FILE_SHARE_DELETE = 0x00000004;
    public static final int FILE_BEGIN = 0;
    public static final int FILE_CURRENT = 1;
    public static final int FILE_END = 2;
    public static final int FILE_ATTRIBUTE_READONLY = 1;
    public static final int FILE_ATTRIBUTE_HIDDEN = 2;
    public static final int FILE_ATTRIBUTE_SYSTEM = 4;
    public static final int FILE_ATTRIBUTE_ARCHIVE = 32;
    public static final int FILE_ATTRIBUTE_NORMAL = 128;
    public static final int FILE_ATTRIBUTE_TEMPORARY = 256;
    public static final int FILE_ATTRIBUTE_ENCRYPTED = 16384;
    public static final int FILE_ATTRIBUTE_OFFLINE = 4096;
    public static final int FILE_FLAG_BACKUP_SEMANTICS = 0x02000000;
    public static final int FILE_FLAG_DELETE_ON_CLOSE = 0x04000000;
    public static final int FILE_FLAG_NO_BUFFERING = 0x20000000;
    public static final int FILE_FLAG_OPEN_NO_RECALL = 0x00100000;
    public static final int FILE_FLAG_OPEN_REPARSE_POINT = 0x00200000;
    public static final int FILE_FLAG_OVERLAPPED = 0x40000000;
    public static final int FILE_FLAG_POSIX_SEMANTICS = 0x0100000;
    public static final int FILE_FLAG_RANDOM_ACCESS = 0x10000000;
    public static final int FILE_FLAG_SEQUENTIAL_SCAN = 0x08000000;
    public static final int FILE_FLAG_WRITE_THROUGH = 0x80000000;

    public static final int FILE_NOTIFY_CHANGE_FILE_NAME   = 0x00000001;
    public static final int FILE_NOTIFY_CHANGE_DIR_NAME    = 0x00000002;
    public static final int FILE_NOTIFY_CHANGE_ATTRIBUTES  = 0x00000004;
    public static final int FILE_NOTIFY_CHANGE_SIZE        = 0x00000008;
    public static final int FILE_NOTIFY_CHANGE_LAST_WRITE  = 0x00000010;
    public static final int FILE_NOTIFY_CHANGE_LAST_ACCESS = 0x00000020;
    public static final int FILE_NOTIFY_CHANGE_CREATION    = 0x00000040;
    public static final int FILE_NOTIFY_CHANGE_SECURITY    = 0x00000100;



    public static final int BACKUP_ALTERNATE_DATA = 0x00000004;
    public static final int BACKUP_DATA = 0x00000001;
    public static final int BACKUP_EA_DATA = 0x00000002;
    public static final int BACKUP_LINK = 0x00000005;
    public static final int BACKUP_OBJECT_ID = 0x00000007;
    public static final int BACKUP_PROPERTY_DATA = 0x00000006;
    public static final int BACKUP_REPARSE_DATA = 0x00000008;
    public static final int BACKUP_SECURITY_DATA = 0x00000003;
    public static final int BACKUP_SPARSE_BLOCK = 0x00000009;
    public static final int BACKUP_TXFS_DATA = 0x0000000A;



    static
    {
        Map UNICODE_OPTIONS = new HashMap()
        {


            {
                put("type-mapper", W32APITypeMapper.UNICODE);
                put("function-mapper", W32APIFunctionMapper.UNICODE);
            }
        };

        NativeLibrary nl = NativeLibrary.getInstance("kernel32", UNICODE_OPTIONS);

        Native.register(nl);
    }


    public static class SECURITY_ATTRIBUTES extends Structure
    {

        public final int nLength;
        public Pointer lpSecurityDescriptor;
        public boolean bInheritHandle;

        public SECURITY_ATTRIBUTES()
        {
            nLength = 0;
            bInheritHandle = false;
            lpSecurityDescriptor = null;
        }
    }
    public static long get_unsigned( int n )
    {
        long ll = (n & 0x7fffffff);
        if ((n & 0x80000000L) != 0)
        {
            ll += 0x80000000L;
        }

        return ll;
    }


    public static class FILETIME extends Structure
    {

        private static long TIME_DIFFERENCE_IN_MILLISECONDS = 11644473600000L;
        public int dwLowDateTime;
        public int dwHighDateTime;

        long get_unsigned( int n )
        {
            long ll = (n & 0x7fffffff);
            if ((n & 0x80000000L) != 0)
            {
                ll += 0x80000000L;
            }

            return ll;
        }

        public long Get100Nanosecs()
        {
            long ll = get_unsigned(dwLowDateTime);

            long l = get_unsigned(dwHighDateTime);
            l <<= 32;
            return l + ll;
        }
        public void Set100Nanosecs( long l)
        {
            dwLowDateTime = (int)(l & 0xFFFFFFFFL);
            dwHighDateTime = (int)((l>>32) & 0xFFFFFFFFL);
        }

        public long GetRelMS()
        {
            long l = Get100Nanosecs();
            l /= 10000;
            return l;
        }
        public void SetRelMS(long l)
        {
            l*= 10000;
            Set100Nanosecs(l);
        }

        public long GetAbsMS()
        {
            long l = GetRelMS();
            return l - TIME_DIFFERENCE_IN_MILLISECONDS;
        }
        public void SetAbsMS( long l)
        {
            l += TIME_DIFFERENCE_IN_MILLISECONDS;
            SetRelMS(l);
        }

        public String toHMSString()
        {
            long l = GetRelMS();

            long ms = l % 1000;
            long s = (l / 1000) % 60;
            long m = (l / (1000 * 60)) % 60;
            long h = (l / (1000 * 3600));

            return h + ":" + m + ":" + s + "." + ms;
        }

        @Override
        public String toString()
        {
            long l = GetAbsMS();
            return new Date(l).toString();
        }

        public String toString( SimpleDateFormat sdf )
        {
            long l = GetAbsMS();
            return sdf.format(new Date(l));
        }
    }

    public static class WIN32_FILE_ATTRIBUTE_DATA extends Structure
    {

        public DWORD dwFileAttributes;
        public FILETIME ftCreationTime;
        public FILETIME ftLastAccessTime;
        public FILETIME ftLastWriteTime;
        public DWORD nFileSizeHigh;
        public DWORD nFileSizeLow;
    }


    public static final int WINSTREAM_ID_SIZE = 20;

    public static class WIN32_STREAM_ID
    {
      public int         dwStreamId;
      public int         dwStreamAttributes;
      public long          Size;
      public int         dwStreamNameSize;

        @Override
        public String toString()
        {
            return "dwStreamId: " + dwStreamId + "dwStreamAttributes: " +dwStreamAttributes + " Size: " + Size + " dwStreamNameSize: " + dwStreamNameSize;
        }


    };

    public static final int GetFileExInfoStandard = 0;
    

    public static boolean isInvalidHandleValue( HANDLE h )
    {
        if (h == null)
            return true;

        if (h.equals(WinBase.INVALID_HANDLE_VALUE))
            return true;

        return false;
    }

    public static native boolean GetSystemTimes( FILETIME fi, FILETIME fg, FILETIME fh );

    public static native boolean GetFileTime( HANDLE h, FILETIME fi, FILETIME fg, FILETIME fh );
    public static native boolean SetFileTime( HANDLE h, FILETIME fi, FILETIME fg, FILETIME fh );

    public static native HANDLE CreateFile( WString lpFileName, int dwDesiredAccess, int dwShareMode, SECURITY_ATTRIBUTES lpSecurityAttributes, int dwCreationDisposition, int dwFlagsAndAttributes, HANDLE hTemplateFile );

    public static native boolean CloseHandle( HANDLE hObject );

    public static native int GetLastError();

    public static native boolean GetFileAttributesEx( WString lpFileName, int level, WIN32_FILE_ATTRIBUTE_DATA data );

    public static native boolean ReadFile( HANDLE hFile, byte[] lpBuffer, int nNumberOfBytesToRead, IntByReference lpNumberOfBytesRead, WinBase.OVERLAPPED lpOverlapped );

    public static native boolean BackupRead( HANDLE hFile, byte[] lpBuffer, int nNumberOfBytesToRead,IntByReference lpNumberOfBytesRead, boolean bAbort, boolean bProcessSecurity, PointerByReference lpContext);
    public static native boolean BackupWrite( HANDLE hFile, byte[] lpBuffer, int nNumberOfBytesToWrite,IntByReference lpNumberOfBytesWritten, boolean bAbort, boolean bProcessSecurity, PointerByReference lpContext);
    public static native boolean BackupSeek( HANDLE hFile, int dwLowBytesToSeek, int dwHighBytesToSeek, IntByReference lpdwLowByteSeeked, IntByReference lpdwHighByteSeeked, PointerByReference lpContext );

    public static native boolean WriteFile( HANDLE hFile, byte[] lpBuffer, int nNumberOfBytesToWrite, IntByReference lpNumberOfBytesWritten, WinBase.OVERLAPPED lpOverlapped );

    public static native boolean SetFilePointerEx( HANDLE hFile, long liDistanceToMove, LongByReference lpNewFilePointer, int dwMoveMethod );

//    public static native HANDLE CreateIoCompletionPort( HANDLE hDir, HANDLE existing_cport, Pointer data, int threads );

    public static native boolean IsWow64Process( HANDLE process, IntByReference ok );

    public static native HANDLE GetCurrentProcess();

    public static native boolean SetEndOfFile( HANDLE h );

    //public static native boolean CreateSymbolicLink( String lpSymlinkFileName, String lpTargetFileName, int dwFlags );


    public static native boolean GetVolumeInformation(WString path, char[] lpVolumeNameBuffer, int nVolumeNameSize,
            LongByReference lpVolumeSerialNumber,
            LongByReference lpMaximumComponentLength,
            LongByReference lpFileSystemFlags,
            char[] lpFileSystemNameBuffer, int nFileSystemNameSize);



    public static String getFsName( String path )
    {
        char[] lpVolumeNameBuffer = new char[256];
        char[] lpFileSystemNameBuffer = new char[256];
        LongByReference lpVolumeSerialNumber  = new LongByReference(0);
        LongByReference lpMaximumComponentLength  = new LongByReference(0);
        LongByReference lpFileSystemFlags  = new LongByReference(0);
        WString p = new WString(path);
        if (GetVolumeInformation( p, lpVolumeNameBuffer, lpVolumeNameBuffer.length, lpVolumeSerialNumber, lpMaximumComponentLength, lpFileSystemFlags,
                lpFileSystemNameBuffer, lpFileSystemNameBuffer.length ))
        {
            int l = 0;
            while (l < lpFileSystemNameBuffer.length - 1 && lpFileSystemNameBuffer[l] != 0)
                l++;
            return new String( lpFileSystemNameBuffer, 0, l );
        }
        return null;
    }

    public static boolean is64BitOS()
    {
        // IF WEE ARE ABLE TO RUN AS &$ BIT, WE ARE SURELY 64 BIT
        if (System.getProperty("os.arch").endsWith("64"))
            return true;
        if (System.getProperty("os.name").endsWith("2003"))
            return false;

        try
        {
            HANDLE h = GetCurrentProcess();

            IntByReference ok = new IntByReference(0);
            if (IsWow64Process( h, ok ))
            {
                return (ok.getValue() != 0);
            }
        }
        catch (Exception exc )
        {
        }
        return false;
    }
}
