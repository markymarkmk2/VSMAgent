/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.win;

import com.thoughtworks.xstream.XStream;
import de.dimm.vsm.client.Main;
import de.dimm.vsm.client.jna.WinSnapshot;
import de.dimm.vsm.net.WinSnapshotHandle;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.net.interfaces.SnapshotHandle;
import de.dimm.vsm.net.interfaces.SnapshotHandler;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;


/**
 *
 * @author Administrator
 */
public class WinSnapShotHandler implements SnapshotHandler
{

    public WinSnapShotHandler()
    {
        read_snap_list();

        // REMOVE EXISTING SNAPSHOTS
        for (int i = 0; i < snap_shots.size(); i++)
        {
            SnapshotHandle snapShot = snap_shots.get(i);
            release_snapshot( snapShot );
        }
    }


    private void read_snap_list()
    {
        File path = new File(Main.get_snaps_path(), "snaplist.xml");

        if (path.exists())
        {
            FileInputStream fis = null;

            try
            {
                XStream xs =new XStream();
                fis = new FileInputStream(path);
                Object o = xs.fromXML(fis);
                if (o instanceof ArrayList<?>)
                {
                    snap_shots.clear();
                    snap_shots.addAll( (ArrayList<SnapshotHandle>) o );
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            finally
            {
                if (fis != null)
                {
                    try
                    {
                        fis.close();
                    }
                    catch (IOException iOException)
                    {
                    }
                }
            }
        }
    }
    private void write_snap_list()
    {
        File path = new File(Main.get_snaps_path(), "snaplist.xml");
        FileOutputStream fos = null;

        try
        {
            XStream xs =new XStream();
            fos = new FileOutputStream(path);
            xs.toXML(snap_shots, fos);
        }
        catch (FileNotFoundException fileNotFoundException)
        {
        }
        finally
        {
            if (fos != null)
            {
                try
                {
                    fos.close();
                }
                catch (IOException iOException)
                {
                }
            }
        }
    }

    @Override
    public SnapshotHandle create_snapshot( RemoteFSElem file )
    {
        String path = Main.get_snaps_path();
        int portnr = Main.get_port_nr();
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024);

        path = path.replace('/', '\\');
        
        int handle = WinSnapshot.create_vcsi_snapshot(path, portnr, file.getPath() + "\n"/*, buffer, buffer.capacity()*/);
        if (handle < 0)
        {
            try
            {
                //String s = new String(buffer.array(), "UTF8");
                System.out.println("Error while creasting snapshot: " + handle);
                return null;
            }
            catch (Exception unsupportedEncodingException)
            {
            }
        }
        
        SnapshotHandle sn = new WinSnapshotHandle(handle);
        snap_shots.add(sn);
        write_snap_list();
        
        return sn;
    }

    @Override
    public final boolean release_snapshot( SnapshotHandle snapShot )
    {
        if (snapShot instanceof WinSnapshotHandle)
        {
            
            WinSnapshotHandle ws = (WinSnapshotHandle)snapShot;
            String path = Main.get_snaps_path();
            int portnr = Main.get_port_nr();
            int err = WinSnapshot.release_vcsi_snapshot(path, portnr, ws.getHandle());

            snap_shots.remove(snapShot);
            write_snap_list();
            
            return (err == 0);
        }
        return false;
    }

    public void init()
    {
        
    }

   
    

}
