/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.dimm.vsm.client;

import com.caucho.hessian.server.HessianSkeleton;
import de.dimm.vsm.Utilities.CommThread;
import de.dimm.vsm.Utilities.SizeStr;
import de.dimm.vsm.Utilities.ThreadPoolWatcher;

import de.dimm.vsm.VSMFSLogger;
import de.dimm.vsm.client.jna.LibKernel32;
import de.dimm.vsm.client.jna.WinSnapshot;
import de.dimm.vsm.client.win.WinAgentApi;
import de.dimm.vsm.net.interfaces.AgentApi;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ThreadPoolExecutor;
import org.apache.log4j.Level;

/**
 *
 * @author Administrator
 */
public class Main
{

    static String source_str = "trunk";
    static String version = "0.8.1";
    static Main me;
    private static boolean agent_tcp = true;
    String work_dir;
    ServerConnector server_conn;
    //FCEListener fce_listener;
    ThreadPoolWatcher threadPoolWatcher;
    
    // START WITH 50 BLOCKS AKA 50MB CACHE
    public static int CACHE_FILE_FLOCKS = 50;

    static void print_system_property( String key )
    {
        System.out.println("Property " + key + ": " + System.getProperty(key));
    }

    void init()
    {

//        try
//        {
//            ACLTest.test();
//        }
//        catch (IOException iOException)
//        {
//            System.out.println(iOException.getMessage());
//        }
        
        me = this;

        work_dir = new File(".").getAbsolutePath();
        if (work_dir.endsWith("."))
        {
            work_dir = work_dir.substring(0, work_dir.length() - 2);
        }

        String jlibpath =  System.getProperty("java.library.path");
        if (is_win())
            jlibpath = System.getProperty("user.dir") + ";" + jlibpath;
        else
            jlibpath = System.getProperty("user.dir") + ":" + jlibpath;

        System.setProperty("java.library.path", jlibpath);
        try
        {
            // THIS HACK REREADS SYSTEMPATH FOR LIBRARY LOADING!!!
            Field fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
            fieldSysPath.setAccessible(true);
            fieldSysPath.set(null, null);
        }
        catch (Exception exception)
        {
            System.out.println("Cannot set java.library.path to user dir");
        }

        System.out.println("VSMAgent V" + version + " " + source_str);

        print_system_property("java.version");
        print_system_property("java.vendor");
        print_system_property("java.home");
        print_system_property("java.class.path");
        print_system_property("os.name");
        print_system_property("os.arch");
        print_system_property("os.version");
        print_system_property("user.dir");
        print_system_property("java.library.path");


        File f = new File(".");
        f = f.getAbsoluteFile();
        System.out.println(f.getAbsolutePath());


        try
        {
            f = new File(get_user_path());
            if (!f.exists())
            {
                f.mkdirs();
            }

            f = new File(get_log_path());
            if (!f.exists())
            {
                f.mkdirs();
            }

            f = new File(get_prefs_path());
            if (!f.exists())
            {
                f.mkdirs();
            }

            f = new File(get_update_path());
            if (!f.exists())
            {
                f.mkdirs();
            }

            f = new File(get_tmp_path());
            if (!f.exists())
            {
                f.mkdirs();
            }

            f = new File(get_snaps_path());
            if (!f.exists())
            {
                f.mkdirs();
            }

        }
        catch (Exception exc)
        {
            System.out.println("Cannot create local dirs: " + exc.getMessage());
        }

        server_conn = new ServerConnector();

//        fce_listener = new FCEListener();
//        fce_listener.start();

        threadPoolWatcher = new ThreadPoolWatcher("CommWatcher");
        

    }

    public static ServerConnector getServerConn()
    {
        return me.server_conn;
    }

    /**
     * @param args the command line arguments
     */
    static boolean debug = false;
    static boolean verbose = false;

    public static boolean isVerbose()
    {
        return verbose;
    }

    public static boolean isDebug()
    {
        return debug;
    }


    public static void main( String[] args )
    {
        //NetServer agent_net_server = null;

        //DokanVSMFS.test();

        int port = 8082;
        
        for (int i = 0; i < args.length; i++)
        {
            String string = args[i];
            if (string.equals("-d"))
            {
                debug = true;
            }
            if (string.equals("-v"))
            {
                verbose = true;
            }
            if (string.equals("-version"))
            {
                System.out.println(Main.version);
                System.exit(0);
            }
            if (string.equals("-p") && (i + 1) <  args.length)
            {
                port = Integer.parseInt(args[i+1]);
                i++;
            }
            if (string.equals("-fake-read"))
            {
                WinAgentApi.fake_read = true;
            }
            if (string.equals("-fake-hash"))
            {
                WinAgentApi.fake_hash = true;
            }
        }

       
        if (!debug)
        {
            VSMFSLogger.getLog().setLevel(Level.WARN);
        }


        final Main mn = new Main();
        mn.init();

        if (is_win())
        {
            try
            {
                System.loadLibrary("dokan");
                System.loadLibrary("JDokan");
            }
            catch (Exception exc)
            {
                System.out.println("You have to install Microsft Visual c++ 2010 Redistributables (x86)");
            }
        }



        if (args.length > 0 && args[0].equals("testsnapshot"))
        {
            String path = new File("snaps").getAbsolutePath();
            int portnr = 11111;
            File file = new File("Z:\\tmp");

            int handle = WinSnapshot.create_vcsi_snapshot(path, portnr, file.getPath() + "\n"/*, buffer, buffer.capacity()*/);

            WinSnapshot.release_vcsi_snapshot(path, portnr, handle);
        }


        mn.startCommLoop( port, 15, 30);


        int cnt = 0;
        while(!doFinish)
        {
            Sleep(1000);
            mn.idle();
            cnt++;


            // EVERY MINUTE
            if ((cnt %60) == 0)
            {
                long fm = Runtime.getRuntime().freeMemory();

                System.out.println("Free Mem: " + SizeStr.format(fm));
            }
        }

        //mn.fce_listener.stop();
    }
    
    void idle()
    {
        netServlet.idle();
    }

    void run_comm( Socket s, NetServlet netServlet )
    {
        Thread thr = Thread.currentThread();

        // STORE SOCKET FOR INNER USAGE HESSIAN HIDES IT BUT WE NEED TO DETECT REMOTE ADDRESS INSIDE HESSIAN FUNCS
        if (thr instanceof CommThread)
        {
            ((CommThread)thr).setSocket(s);
        }
        try
        {            
            s.setTcpNoDelay(true);
            s.setSendBufferSize(256 * 1024);
            s.setReceiveBufferSize(256 * 1024);
            s.setReuseAddress(true);
            HessianSkeleton sk = new HessianSkeleton(netServlet, AgentApi.class);
            while (s.isConnected())
            {
                InputStream is = s.getInputStream();
                OutputStream os = s.getOutputStream();
                sk.invoke(is, os);
            }
        }
        catch (Exception exception)
        {
            System.out.println("Connection closed"/* + exception.getMessage()*/);
        }
        finally
        {
            if (s != null)
            {
                try
                {
                    s.close();
                }
                catch (IOException iOException)
                {
                }
            }
        }
    }

    static NetServlet netServlet;
    static boolean doFinish = false;
    static Thread commThr;

    boolean startCommLoop( final int port, int threads, int qlen)
    {
        String cdpIpFilter = readCdpIpFilter();

        final ServerSocket ss;
        try
        {
            if (is_win())
                netServlet = NetServlet.createWinNetServlet();
            else if (is_osx())
                netServlet = NetServlet.createMacNetServlet(cdpIpFilter);
            else 
                netServlet = NetServlet.createUnixNetServlet(cdpIpFilter);

            ss = new ServerSocket(port);
            ss.setReuseAddress(true);
        }
        catch (Exception exception)
        {
            System.out.println("Netzwerk kann nicht gestartet werden, ist bereits ein Dienst aktiv? " + exception.getMessage());
            return false;
        }
        final ThreadPoolExecutor poolexecutor = threadPoolWatcher.create_blocking_thread_pool("CommThread",/*Threads*/ 5, /*Queue*/ 20, /*commThreads*/ true);


        Runnable r = new Runnable()
        {
            @Override
            public void run()
            {
                ServerSocket threadss = ss;
                // MOVE OUT HERE!
                while(!doFinish)
                {
                    Socket s = null;
                    try
                    {
                        s = threadss.accept();
                    }
                    catch (Exception exception)
                    {
                        System.out.println("Accept failed: " + exception.getMessage());
                        Sleep( 5000 );

                        try
                        {
                            threadss = new ServerSocket(port);
                            threadss.setReuseAddress(true);
                        }
                        catch (IOException iOException)
                        {
                            System.out.println("Netzwerk kann nicht gestartet werden, ist bereits ein Dienst aktiv? " + iOException.getMessage());
                        }
                        continue;
                    }


                    // SOCKET IS CLOSED INSIDE run_comm
                    final Socket final_socket = s;

                    System.out.println("New connection, active threads: " + poolexecutor.getActiveCount() );


                    Runnable r = new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            run_comm(final_socket, netServlet);

                            // IF WE ARE THE LAST MAN STANDING, THEN CLEAN UP READERS
                            synchronized( poolexecutor )
                            {
                                if (poolexecutor.getActiveCount() <= 1)
                                {
                                    netServlet.resetFileReaders();
                                }
                            }
                        }
                    };

                    synchronized( poolexecutor )
                    {
                        poolexecutor.execute(r);
                    }
                }
            }            
        };
        
        commThr = new Thread(r, "ComLoop");
        commThr.start();
        
        return true;
    }

    static void Sleep( int ms )
    {
        try
        {
            Thread.sleep(1000);
        }
        catch (InterruptedException interruptedException)
        {
        }
    }


    public static String get_version()
    {
        return version + " " + source_str;
    }

    public static boolean is_win()
    {
        return (System.getProperty("os.name").startsWith("Win"));
    }

    public static boolean is_win64()
    {
        return LibKernel32.is64BitOS();
    }

    public static boolean is_linux()
    {
        return (System.getProperty("os.name").startsWith("Linux"));
    }

    public static boolean is_osx()
    {
        return (System.getProperty("os.name").startsWith("Mac"));
    }

    public static boolean is_solaris()
    {
        return (System.getProperty("os.name").startsWith("SunOS"));
    }
    private static String local_app_data_path = "VSMA";

    public static String get_user_path()
    {
        String programGroupName = local_app_data_path;

        String userHome = System.getProperty("user.home");

        File workingDirectoryPath = new File(userHome, "." + programGroupName);

        if (is_win())
        {
            String applicationData = System.getenv("APPDATA");
            if (applicationData != null)
            {
                workingDirectoryPath = new File(applicationData, programGroupName);
            }
            else
            {
                workingDirectoryPath = new File(userHome, programGroupName);
            }
        }
        if (!workingDirectoryPath.exists())
        {
            workingDirectoryPath.mkdir();
        }
        return workingDirectoryPath.getAbsolutePath();
    }

    public static String get_dlg_props_path()
    {
        return get_user_path() + "/properties";
    }

    public static String get_prefs_path()
    {
        return get_user_path();
    }

    public static String get_update_path()
    {
        return get_user_path() + "/update";
    }

    public static String get_log_path()
    {
        return get_user_path() + "/logs";
    }

    public static String get_tmp_path()
    {
        return get_user_path() + "/tmp";
    }

    public static String get_snaps_path()
    {
        return get_user_path() + "/snaps";
    }

    public static int get_port_nr()
    {
        return 8082;
    }
    public static void set_service_shutdown()
    {
        doFinish = true;

        try
        {
            commThr.join(3000);
        }
        catch (InterruptedException interruptedException)
        {
        }

        

        System.exit(0);
    }

    private String readCdpIpFilter()
    {
        File fh = new File("cdp_ipfilter.txt");
        if (!fh.exists())
            return null;


        FileReader fr = null;
        StringBuilder sb = new StringBuilder();

        try
        {
            fr = new FileReader(fh);
            BufferedReader rd = new BufferedReader(fr);
            while (true)
            {
                String line = rd.readLine();
                if (line == null)
                {
                    break;
                }
                line  = line.trim();
                if (line.isEmpty())
                    continue;
                if (line.startsWith("#"))
                    continue;

                System.out.println("Detected IP-Filter " + line);

                if (sb.length() > 0)
                    sb.append(" ");
                sb.append(line);
                return line;
            }
        }
        catch (IOException iOException)
        {
        }
        finally
        {
            if (fr != null)
            {
                try
                {
                    fr.close();
                }
                catch (IOException iOException)
                {
                }
            }
        }
        return null;
    }
}
