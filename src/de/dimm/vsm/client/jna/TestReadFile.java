/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.dimm.vsm.client.jna;

import com.sun.jna.WString;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import de.dimm.vsm.client.jna.LibKernel32.FILETIME;
import de.dimm.vsm.client.win.WinRemoteFSElemFactory;
import java.io.File;

/**
 *
 * @author Administrator
 */
public class TestReadFile
{
  
    public static void main( String[] args )
    {
        VSMLibC.printf("Hello, World\n");
        VSMLibC.printf("Dummes Au");
        
        FILETIME idletime = new FILETIME();
        FILETIME kerneltime = new FILETIME();
        FILETIME usertime = new FILETIME();

        System.out.println(LibKernel32.GetSystemTimes(idletime, kerneltime, usertime));
        System.out.println(idletime);
        System.out.println(kerneltime);
        System.out.println(usertime);

        long last_idletime = idletime.GetRelMS();
        long last_kerneltime = kerneltime.GetRelMS();
        long last_usertime = usertime.GetRelMS();

        long last_icpu = 0;
        int i = 1;
        while (i-- > 0)
        {
            try
            {
                Thread.sleep(50);
            }
            catch (InterruptedException interruptedException)
            {
            }

            LibKernel32.GetSystemTimes(idletime, kerneltime, usertime);

            long usr = usertime.GetRelMS() - last_usertime;
            long ker = kerneltime.GetRelMS() - last_kerneltime;
            long idl = idletime.GetRelMS() - last_idletime;

            double sys = ker + usr;

            int icpu = (int) ((sys - idl) * 1000 / sys);

            if (last_icpu != icpu)
            {
                double cpu = icpu / 10.0;
                System.out.println("CPU: " + cpu + "%");
                last_icpu = icpu;
            }

            last_idletime = idletime.GetRelMS();
            last_kerneltime = kerneltime.GetRelMS();
            last_usertime = usertime.GetRelMS();
        }
   

        String testfile = "J:\\tmp\\TREFFER.exp";
        File f = new File ( testfile );
        if (f.exists())
        {
            long len = f.length();

            WString str = new WString(WinRemoteFSElemFactory.getLongPath(f));
            HANDLE h = LibKernel32.CreateFile(str, LibKernel32.GENERIC_READ, 0, null, LibKernel32.OPEN_EXISTING, 0, null);

            int err = LibKernel32.GetLastError();

            int cnt = 0;
            if (!LibKernel32.isInvalidHandleValue(h) && err == 0)
            {
                byte[] b = new byte[8*8192];
                IntByReference lpNumberOfBytesRead = new IntByReference(0);

                long s = System.currentTimeMillis();

                while (len > 0)
                {
                    int rlen = b.length;
                    if (len < rlen)
                        rlen = (int)len;

                    boolean ret = LibKernel32.ReadFile( h, b,rlen, lpNumberOfBytesRead, null);

                    int real_rlen = lpNumberOfBytesRead.getValue();
                    
                    len -= real_rlen;
                    cnt++;
                }

                LibKernel32.CloseHandle( h );

                long e = System.currentTimeMillis();

                long dur = e - s;
                if (dur == 0)
                    dur = 1;

                double speed = (f.length()/1000000.0) /(dur/1000.0);
                System.out.println("It took " + dur + " ms (" + cnt + " calls) to get " + (f.length() / 1000000) + " MB " + speed + " MB/s");
            }
        }
        System.out.println("Bye");
    }
}
