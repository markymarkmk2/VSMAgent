/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.jna;

import de.dimm.vsm.Utilities.CmdExecutor;


/**
 *
 * @author Administrator
 */

public class WinSnapshot
{

    public static void init()
    {
/*        System.load( "J:\\Develop\\VSM\\Eval\\ProjectEval\\SnapshotDLL.dll" );
        System.loadLibrary( "SnapshotDLL" );
        SnapShot INSTANCE = (SnapShot) Native.loadLibrary((Platform.isWindows() ? "SnapshotDLL" : "c"),
                SnapShot.class);
  */  }

//    public interface SnapShot extends Library
//    {
//        String dllName = Platform.isWindows() ?  Platform.is64Bit() ? "SnapshotDLL64": "SnapshotDLL32" : "snapshot";
//
//        SnapShot INSTANCE = (SnapShot) Native.loadLibrary(dllName, SnapShot.class);
//
//        int release_vcsi_snapshot( WString prog_path, int portnr,  int vcsi_handle );
//        int create_vcsi_snapshot( WString prog_path, int portnr,  WString _path_list/*, ByteBuffer err_text, int err_len*/ );
//        void set_vcsci_params( WString _log_path, int snap_no_writers, int _debug );
//
//        int vslib_call(int argc, WString[] argv);
//    }
//
//
//    public static int release_vcsi_snapshot( String prog_path, int portnr,  int vcsi_handle )
//    {
//        WString path = new WString(prog_path);
//         return SnapShot.INSTANCE.release_vcsi_snapshot(path, portnr, vcsi_handle);
//    }
//
//    public static  int create_vcsi_snapshot( String prog_path, int portnr,  String _path_list/*, ByteBuffer err_text, int err_len */)
//    {
//        WString path = new WString(prog_path);
//        WString list = new WString(_path_list);
//        return SnapShot.INSTANCE.create_vcsi_snapshot(path, portnr, list/*, err_text, err_len*/);
//    }
//
//    public static void set_vcsci_params( String _log_path, int snap_no_writers, int _debug )
//    {
//        WString path = new WString(_log_path);
//        SnapShot.INSTANCE.set_vcsci_params(path, snap_no_writers, _debug);
//
//        String[] s1 = {"test", "-q", null};
//        WString[] args = new WString[s1.length];
//        for (int i = 0; i < args.length; i++)
//        {
//            if (s1[i] != null)
//                args[i] = new WString(s1[i]);
//            else
//                args[i] = null;
//
//        }
//
//    }
//    public static int call_api( String[] s1 )
//    {
//        WString[] args = new WString[s1.length + 2];
//        args[0] = new WString("VSM");
//        for (int i = 0; i < s1.length; i++)
//        {
//                args[i + 1] = new WString(s1[i]);
//        }
//        args[s1.length +1] = null;
//
//        return SnapShot.INSTANCE.vslib_call(s1.length + 1, args);
//    }

    // WE CANNOT CALL SHADOW COPY (MUST BE 64 BIT ON 64 BIT SYSTEM AND jDOKAN (MUST BE 32 BIT ON ANY SYSTEM) FROM DLL,
    // BECAUSE JAVA CAN ALSO ONLY LOAD DLLS WITH CORRECT BIT SIZE

    // WE NOW CALL SNAPSHOT AS CMD-TOOL

    public static int release_vcsi_snapshot( String prog_path, int portnr,  int vcsi_handle )
    {
        String command = LibKernel32.is64BitOS() ? "SnapshotDLL64.exe" : "SnapshotDLL32.exe";
        String[] args = { command, "release_vcsi_snapshot", prog_path, Integer.toString(portnr), Integer.toString(vcsi_handle) };

        System.out.print("Releasing Snapshot with " + command + " on path " + prog_path +  " ...");
        CmdExecutor cmd = new CmdExecutor(args);
        int ret = cmd.exec();
        System.out.println("done");

        return ret;
    }

    public static int create_vcsi_snapshot( String prog_path, int portnr,  String _path_list)
    {
        String command = LibKernel32.is64BitOS() ? "SnapshotDLL64.exe" : "SnapshotDLL32.exe";
        String[] args = { command, "create_vcsi_snapshot", prog_path, Integer.toString(portnr), _path_list };
        CmdExecutor cmd = new CmdExecutor(args);

        System.out.println("Creating Snapshot with " + command + " on path " + prog_path + " ...");
        int ret = cmd.exec();
        if (ret < 0)
        {
            System.out.println("Err: " + cmd.get_out_text() + " Err: " + cmd.get_err_text());
        }
        System.out.println("done");
        return ret;
    }
}
