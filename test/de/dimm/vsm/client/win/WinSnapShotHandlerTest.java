/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.dimm.vsm.client.win;

import de.dimm.vsm.client.jna.WinSnapshot;
import de.dimm.vsm.client.Main;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.net.interfaces.SnapshotHandle;
import de.dimm.vsm.records.FileSystemElemNode;
import junit.framework.TestCase;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author Administrator
 */
public class WinSnapShotHandlerTest extends TestCase
{

    public WinSnapShotHandlerTest()
    {
        instance = new WinSnapShotHandler();
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
    }

    @Before
    public void setUp()
    {
    }

    @After
    public void tearDown()
    {
    }
    static SnapshotHandle result;
    WinSnapShotHandler instance;

    /**
     * Test of create_snapshot method, of class WinSnapShotHandler.
     */
//    @Test
//    public void testset_vcsci_params()
//    {
//        System.out.println("testset_vcsci_params");
//
//        String path = Main.get_log_path();
//        WinSnapshot.set_vcsci_params(path, 0, 3);
//
//     /*   String[] args = {"-p", "Z:"};
//        int ret = WinSnapshot.call_api( args );
//
//        assertTrue(ret == 0);*/
//
//    }

    /**
     * Test of create_snapshot method, of class WinSnapShotHandler.
     */
    @Test
    public void testCreate_snapshot()
    {
        System.out.println("create_snapshot");
        RemoteFSElem file = new RemoteFSElem("z:", FileSystemElemNode.FT_DIR, 0, 0, 0, 0, 0);

        result = instance.create_snapshot(file);
        assertTrue(result != null);

    }

    /**
     * Test of release_snapshot method, of class WinSnapShotHandler.
     */
    @Test
    public void testRelease_snapshot()
    {
        System.out.println("release_snapshot");
        
        boolean r = instance.release_snapshot(result);
        assertTrue(r);

    }
}
