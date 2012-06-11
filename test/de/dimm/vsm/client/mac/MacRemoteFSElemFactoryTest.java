/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.dimm.vsm.client.mac;

import de.dimm.vsm.client.mac.MacRemoteFSElemFactory.StatStructure;
import de.dimm.vsm.net.RemoteFSElem;
import java.io.File;
import org.jruby.ext.posix.FileStat;
import org.junit.*;
import static org.junit.Assert.*;

/**
 *
 * @author mw
 */
public class MacRemoteFSElemFactoryTest {
    
    public MacRemoteFSElemFactoryTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }



    /**
     * Test of getFSType method, of class OsxRemoteFSElemFactory.
     */
    @Test
    public void testGetFSType() {
        System.out.println("getFSType");
        MacRemoteFSElemFactory instance = new MacRemoteFSElemFactory();
        String expResult = "hfs";
        instance.statbfs("/" );
        String result = instance.getFSType();
        assertEquals(expResult, result);
        
    }



    /**
     * Test of getMntTo method, of class OsxRemoteFSElemFactory.
     */
    @Test
    public void testGetMntTo() {
        System.out.println("getMntTo");
        MacRemoteFSElemFactory instance = new MacRemoteFSElemFactory();
        String expResult = "/";
        instance.statbfs("/" );
        String result = instance.getMntTo();
        assertEquals(expResult, result);
        
    }

    /**
     * Test of getLong method, of class OsxRemoteFSElemFactory.
     */
    @Test
    public void testGetLong() {
        System.out.println("getLong");
        int offset = 0;
        MacRemoteFSElemFactory instance = new MacRemoteFSElemFactory();
        instance.statbfs("/" );
        long expResult = 4096L;
        long result = instance.getLong(8);
        assertEquals(expResult, result);
        
    }

    /**
     * Test of getShort method, of class OsxRemoteFSElemFactory.
     */
    @Test
    public void testGetShort() {
        System.out.println("getShort");
        int offset = 0;
        MacRemoteFSElemFactory instance = new MacRemoteFSElemFactory();
        short expResult = 0;
        short result = instance.getShort(0);
        assertEquals(expResult, result);
        
    }


    /**
     * Test of getShort method, of class OsxRemoteFSElemFactory.
     */
    @Test
    public void testMB_Toolkit() {
        System.out.println("testMB_Toolkit");
        int offset = 0;
        MacRemoteFSElemFactory instance = new MacRemoteFSElemFactory();
        String[] args = new String[1];
//        args[0] = "/Users/mw/Desktop/VSM/1.0/VSMLibPool/MB-Toolkit/libMacOSXForkerIO.jnilib";
//        About.main(args);
        args[0] = "/Volumes/Macintosh HD/Users/mw/G4_Save/CW10 Gold/CodeWarrior Quick Start";
        
        boolean arg = instance.isReffable( args[0]);
        
        assertTrue(arg);
        
        arg = instance.isReffable( "/asdhajsh");
        
        assertFalse(arg);
        
        instance.FInfoDemo(args[0] );
        
        instance.FInfoDemo("/Volumes/VOLUME/CodeWarrior Quick Start");
        
    }
    

    

}
