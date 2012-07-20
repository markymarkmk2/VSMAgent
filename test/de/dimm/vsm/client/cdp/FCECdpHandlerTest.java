/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.cdp;

import java.io.File;
import de.dimm.vsm.client.unix.UnixPlatformData;
import de.dimm.vsm.net.RemoteFSElem;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Administrator
 */
public class FCECdpHandlerTest {

    public FCECdpHandlerTest() {
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
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of init_cdp method, of class FCECdpHandler.
     */
    @Test
    public void testInit_cdp()
    {
        System.out.println("init_cdp");
        CDP_Param p = new CDP_Param(null, 0, true, true, null, null, new UnixPlatformData());
        FCECdpHandler instance = new FCECdpHandler(null, p, null, null);


        
        RemoteFSElem elem = new RemoteFSElem("/dev/null", "dir", 0, 0, 0, 0, 0 );
        elem.setUnixStylePath(true);
        boolean result = instance.check_invalid_cdp_path(elem, "null");
        assertEquals(true, result);


       
        elem = new RemoteFSElem("/user/local/syncsrv/Logs", "dir", 0, 0, 0, 0, 0 );
        elem.setUnixStylePath(true);
        result = instance.check_invalid_cdp_path(elem, "Logs");
        assertEquals(true, result);

        
        elem = new RemoteFSElem("/user/local/syncsrv/hiberfil.sys", "file", 0, 0, 0, 0, 0 );
        elem.setUnixStylePath(true);
        result = instance.check_invalid_cdp_path(elem, "hiberfil.sys");
        assertEquals(true, result);

        elem = new RemoteFSElem("/user/dev/Logs/hiberfile.sys", "file", 0, 0, 0, 0, 0 );
        elem.setUnixStylePath(true);
        result = instance.check_invalid_cdp_path(elem, "hiberfile.sys");
        assertEquals(false, result);
    }

}