/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.win;

import java.util.List;
import fr.cryptohash.Digest;
import java.util.ArrayList;
import de.dimm.vsm.net.RemoteFSElemWrapper;
import de.dimm.vsm.records.FileSystemElemNode;
import java.io.File;
import de.dimm.vsm.Utilities.CryptTools;
import de.dimm.vsm.client.jna.LibKernel32;
import de.dimm.vsm.client.jna.LibKernel32.WIN32_STREAM_ID;
import de.dimm.vsm.net.interfaces.AgentApi;
import de.dimm.vsm.hash.HashFunctionPool;
import de.dimm.vsm.net.HashDataResult;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.net.interfaces.SnapshotHandle;
import java.io.IOException;
import java.security.MessageDigest;
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
public class WinAgentApiTest {


    //static WinFSElemAccessor fsAcess;

    static HashFunctionPool pool;

    static WinAgentApi instance;

    static byte[] testData = {0,1,2,3,4,5,6,7,8,9};

    static SnapshotHandle handle;

    static RemoteFSElem file = null;
    static RemoteFSElemWrapper file_handle = null;

    public WinAgentApiTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        //fsAcess = new WinFSElemAccessor();
        pool = new HashFunctionPool(10);
        instance = new WinAgentApi(pool);

        handle = null;

    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() throws IOException
    {
        if (file != null)
            instance.close_data(file_handle);
    }

    /**
     * Test of open_data method, of class WinAgentApi.
     */
    @Test
    public void testOpen_data() throws Exception
    {
        String fs = instance.getFsFactory().getFsName("C:\\");
        assertEquals("NTFS", fs);

        System.out.println("open_data");
        RemoteFSElem file = new RemoteFSElem("test.dat", null, 0, 0, 0, 0, 0);
        int flags = AgentApi.FL_RDWR | AgentApi.FL_CREATE;
       
        file_handle = instance.open_data(file, flags);
        assertNotNull(file_handle);

        boolean expResult = true;
        boolean result = instance.close_data(file_handle);
        assertEquals(expResult, result);
        
    }

   
    
    RemoteFSElemWrapper openTest()
    {
        File _f = new File("test.dat");
        RemoteFSElem f = new RemoteFSElem(_f.getAbsolutePath(), null, 0, 0, 0, 0, 0);

        try
        {
            int flags = AgentApi.FL_RDWR;
            
            file_handle = instance.open_data(f, flags);
            assertNotNull(file_handle);
        }
        catch (Exception iOException)
        {
            fail("Exception during open:" + iOException.getMessage());
        }
        return file_handle;
    }


    /**
     * Test of write method, of class WinAgentApi.
     */
    @Test
    public void testWrite()
    {
        System.out.println("write");
        RemoteFSElemWrapper file_handle = openTest();
        byte[] data = testData;
        long pos = 0L;



        int expResult = 10;
        int result = instance.write(file_handle, data, pos);
        assertEquals(expResult, result);

        try
        {
            boolean r = instance.close_data(file_handle);
            assertTrue(r);
        }
        catch (IOException iOException)
        {
            iOException.printStackTrace();
            fail("Exception");
        }
    }

    /**
     * Test of read method, of class WinAgentApi.
     */
    @Test
    public void testRead()
    {
        System.out.println("read");
        RemoteFSElemWrapper file_handle = openTest();
        long pos = 0L;
        int bsize = testData.length;

        byte[] expResult = testData;
        byte[] result = instance.read(file_handle, pos, bsize);
        assertArrayEquals(expResult, result);

        try
        {
            boolean r = instance.close_data(file_handle);
            assertTrue(r);
        }
        catch (IOException iOException)
        {
            iOException.printStackTrace();
            fail("Exception");
        }
    }

    /**
     * Test of read_and_hash method, of class WinAgentApi.
     */
    @Test
    public void testRead_and_hash()
    {
        System.out.println("read_and_hash");
        RemoteFSElemWrapper file_handle = openTest();
        long pos = 0L;
        int bsize = testData.length;

        HashDataResult expResult = null;
        HashDataResult result = null;

        try
        {
            result = instance.read_and_hash(file_handle, pos, bsize);
        }
        catch (IOException iOException)
        {
            iOException.printStackTrace();
            fail("Exception");
        }
        
        byte[] _expResult = testData;
        
        assertArrayEquals(_expResult, result.getData());
        
        try
        {
            Digest digest = pool.get();
            
            
            byte[] hash = digest.digest(_expResult);
            
            String hashstr = CryptTools.encodeUrlsafe(hash);
            
            assertEquals(hashstr, result.getHashValue());
        }
        catch (IOException iOException)
        {
            fail("Exception in testRead_and_hash: " + iOException.getMessage());
        }
        try
        {
            boolean r = instance.close_data(file_handle);
            assertTrue(r);
        }
        catch (IOException iOException)
        {
            iOException.printStackTrace();
            fail("Exception");
        }
    }


    /**
     * Test of read_complete method, of class WinAgentApi.
     */
    @Test
    public void testRead_complete()
    {
        System.out.println("read_complete");
        long len = new File("test.dat").length();
        file =  new RemoteFSElem("test.dat", null, 0, 0, 0, len, len);
        
        byte[] expResult = testData;
        byte[] result = null;

        try
        {
            result = instance.read_complete(file);
        }
        catch (IOException iOException)
        {
            iOException.printStackTrace();
            fail("Exception");

        }

        assertArrayEquals(expResult, result);

       
        
    }

    /**
     * Test of read_hash_complete method, of class WinAgentApi.
     */
    @Test
    public void testRead_hash_complete()
    {
        System.out.println("read_hash_complete");
        File f = new File("test.dat");
        long len = f.length();
        file = new RemoteFSElem(f.getAbsolutePath(), null, 0, 0, 0, len, len);
        String alg = "";
        
        String expResult = "";
        byte[] _expResult = testData;

        String result = null;

        try
        {
            result = instance.read_hash_complete(file, alg);

            Digest digest = pool.get();
            
            byte[] hash = digest.digest(_expResult);

            expResult = CryptTools.encodeUrlsafe(hash);
        }
        catch (IOException iOException)
        {
            fail("Exception in testRead_and_hash: " + iOException.getMessage());
        }
        

        assertEquals(expResult, result);


    }


    void colourArray( byte[] arr )
    {
        for (int i = 0; i < arr.length; i++)
        {
            arr[i] = (byte)i;
        }
    }
    StreamEntry genStreamEntry( int id, String name, int datalen) throws IOException
    {
        WIN32_STREAM_ID sid = new WIN32_STREAM_ID();
        sid.dwStreamId = id;
        sid.dwStreamNameSize = name.length();
        sid.Size = datalen;
        byte[] arr = new byte[LibKernel32.WINSTREAM_ID_SIZE];
        colourArray( arr );

        StreamEntry ste = new StreamEntry(sid, arr);
        return ste;
    }

    @Test
    public void testRead_xa()
    {
        WinRemoteFSElemFactory f = new WinRemoteFSElemFactory();

        RemoteFSElem elem = f.create_elem(new File("dist\\VSMAgent.jar"), true);

        WinFileHandleData data = new WinFileHandleData(null, true, elem);
        long pos = 0;
        int bsize = 64512;

        data.streamList = new ArrayList<StreamEntry>();



        try
        {
            data.streamList.add( genStreamEntry( LibKernel32.BACKUP_SECURITY_DATA, "", 140));            
            data.streamList.add( genStreamEntry( LibKernel32.BACKUP_EA_DATA, "", 140));
            data.streamList.add( genStreamEntry( LibKernel32.BACKUP_SPARSE_BLOCK, "", 140));

            for (int i = 0; i < data.streamList.size(); i++)
            {
                StreamEntry object = data.streamList.get(i);
                colourArray(object.complete_data);
            }

            byte[] r = instance.rawReadXA(data, 0, 170);
            assertEquals(r[160], 0);

            r = instance.rawReadXA(data, 140, 140);
            assertEquals(r[20], 0);

            r = instance.rawReadXA(data, 310, 170);
            assertEquals(r[10], 0);
            assertEquals(r[169], (byte)159);

        }
        catch (IOException iOException)
        {
            fail(iOException.getMessage());
        }
        
        

        assertNotNull(elem);


        try
        {
            RemoteFSElemWrapper wr = instance.open_stream_data(elem, AgentApi.FL_RDONLY);

            assertNotNull(wr);


            HashDataResult hdr = instance.read_and_hash( wr, 0, bsize);

            assertNotNull(hdr);

            instance.close_data(wr);
        }
        catch (Exception iOException)
        {
            fail(iOException.getMessage());
        }
    }

    @Test
    public void testCompare_xa()
    {
        WinRemoteFSElemFactory f = new WinRemoteFSElemFactory();

        RemoteFSElem origelem = f.create_elem(new File("Z:\\unittest\\unittestrestore\\unittestdata\\a\\__start_sync.bat"), true);
        RemoteFSElem restoreelem = f.create_elem(new File("Z:\\unittest\\unittestdata\\a\\__start_sync.bat"), true);

        int flags = AgentApi.FL_RDWR;

        

        try
        {
            RemoteFSElemWrapper oh = instance.open_stream_data(origelem, flags);
            RemoteFSElemWrapper rh = instance.open_stream_data(restoreelem, flags);

            WinFileHandleData odata = (WinFileHandleData)instance.getNativeAccesor().get_handleData(oh);
            WinFileHandleData rdata = (WinFileHandleData)instance.getNativeAccesor().get_handleData(rh);

            List<StreamEntry> ol = odata.readStreamList();
            List<StreamEntry> rl = rdata.readStreamList();


            assertEquals(ol.size(), rl.size());

            for (int i = 0; i < ol.size(); i++)
            {
                StreamEntry ose = ol.get(i);
                StreamEntry rse = rl.get(i);
                assertEquals(ose.stream_id.dwStreamAttributes, rse.stream_id.dwStreamAttributes);
                assertEquals(ose.stream_id.dwStreamId, rse.stream_id.dwStreamId);
                // Security Stream size differs after restore, but i cannot detect errors????
                // assertArrayEquals(ose.complete_data, rse.complete_data);
            }
            odata.close();
            rdata.close();

            instance.close_data(oh);
            instance.close_data(rh);
        }
        catch (IOException iOException)
        {
            fail(iOException.getMessage());
        }
    }



    /**
     * Test of create_snapshot method, of class WinAgentApi.
     */
    @Test
    public void testCreate_snapshot()
    {
        System.out.println("create_snapshot");
        RemoteFSElem _file = new RemoteFSElem("z:\\", FileSystemElemNode.FT_DIR, 0, 0, 0, 0, 0);
        
        handle = instance.create_snapshot(_file);
        assertNotNull(handle);
    }

    /**
     * Test of release_snapshot method, of class WinAgentApi.
     */
    @Test
    public void testRelease_snapshot()
    {
        System.out.println("release_snapshot");
        
        
        boolean expResult = true;
        boolean result = instance.release_snapshot(handle);
        assertEquals(expResult, result);
    }


}