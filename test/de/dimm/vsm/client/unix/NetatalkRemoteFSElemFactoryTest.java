/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.unix;

import de.dimm.vsm.net.AttributeContainer;
import de.dimm.vsm.client.AttributeContainerImpl;
import de.dimm.vsm.client.Main;
import java.util.TimeZone;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.records.FileSystemElemNode;
import java.io.File;
import java.util.GregorianCalendar;
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
public class NetatalkRemoteFSElemFactoryTest {

    public NetatalkRemoteFSElemFactoryTest() {
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
     * Test of create_elem method, of class NetatalkRemoteFSElemFactory.
     */
    @Test
    public void testCreate_elem()
    {
        System.out.println("create_elem");
        
        File fh = new File("Z:\\Test\\attest\\ATTest\\Disc Burner");
        File fha = new File("Z:\\Test\\attest\\ATTest\\.AppleDouble\\Disc Burner");
        long mtime = fh.lastModified();
        GregorianCalendar cal = new GregorianCalendar();
        TimeZone tz = cal.getTimeZone();
        //mtime += tz.getDSTSavings();
        
        RemoteFSElem expResult = new RemoteFSElem(fh.getAbsolutePath(), FileSystemElemNode.FT_FILE, mtime, mtime, mtime, fh.length(), fha.length());

        NetatalkRemoteFSElemFactory f = new NetatalkRemoteFSElemFactory();        
        RemoteFSElem result = f.create_elem(fh, true);

        assertEquals(expResult.getPath(), result.getPath());
        assertEquals(expResult.getDataSize(), result.getDataSize());
        assertEquals(expResult.getStreamSize(), result.getStreamSize());
        assertEquals(mtime, result.getMtimeMs());          
    }
  
    /**
     * Test of create_elem method, of class NetatalkRemoteFSElemFactory.
     */
    @Test
    public void testReadACL()
    {
        System.out.println("testReadACL");
        if (Main.is_solaris())
        {
            File fh = new File("/home/mw/VSM/InstallAgent/start_agent.sh");
            long mtime = fh.lastModified();
            GregorianCalendar cal = new GregorianCalendar();
            TimeZone tz = cal.getTimeZone();
            //mtime += tz.getDSTSavings();

            RemoteFSElem expResult = new RemoteFSElem(fh.getAbsolutePath(), FileSystemElemNode.FT_FILE, mtime, mtime, mtime, fh.length(), 0);

            NetatalkRemoteFSElemFactory f = new NetatalkRemoteFSElemFactory();
            RemoteFSElem result = f.create_elem(fh, true);

            AttributeContainer aci;
            aci = AttributeContainer.unserialize(result.getAclinfoData());

            aci.getUserName();

            assertEquals(aci.getUserName(), result.getUidName());
        }
    }

}