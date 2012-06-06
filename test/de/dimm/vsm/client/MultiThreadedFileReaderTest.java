/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client;


import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

class TestMultiThreadedFileReader extends MultiThreadedFileReader
{
    public TestMultiThreadedFileReader()
    {
        super(50);
    }
    static void writeData( byte[] block, long offset )
    {
        for (int i = 0; i < 50; i++)
        {
            if (block[i] == 0)
                break;

            block[i] = 0;
        }
        String s = Long.toString(offset);
        byte[] d = s.getBytes();
        System.arraycopy(d, 0, block, 0, d.length);
    }

    @Override
    protected void read( byte[] block, FileCacheElem elem )
    {
        writeData(block, elem.getOffset());
        elem.data = block;
    }

    public void startFile( long len,  int blocksize )
    {
        initQueues();

        readyElem = null;

        int idx = 0;
        for( long offset = 0; offset < len; offset += blocksize )
        {
            int bs = blocksize;
            if (offset + blocksize > len)
                bs = (int)(len - offset);

            workList.add( new FileCacheElem(null, offset, bs, idx++) );
        }
    }
}
/**
 *
 * @author Administrator
 */
public class MultiThreadedFileReaderTest {

    public MultiThreadedFileReaderTest() {
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
     * Test of getBlock method, of class MultiThreadedFileReader.
     */
   

  

  

    /**
     * Test of startFile method, of class MultiThreadedFileReader.
     */
    @Test
    public void testStartFile()
    {
        TestMultiThreadedFileReader thr = new TestMultiThreadedFileReader();

        long len = 6*1000*1024*1024l;
        int bsize = 1024*1024;

        thr.startFile(len,bsize);

        long a = System.currentTimeMillis();
        for (long o = 0; o < len; o += bsize)
        {
            thr.getHash(o, bsize);
            thr.getData(o, bsize);
        }
        long b = System.currentTimeMillis();

        int diff = (int) (b-a);
        if (diff == 0)
            diff = 1;

        System.out.println("Speed " + (len / 1000) / diff + " MB/s");

        thr.startFile(len,bsize);


        byte[] expect = new byte[bsize];
        int n = 0;
        for (long o = 0; o < len; o += bsize)
        {
            byte[] d = thr.getData(o, bsize);
            n++;
            if ((n % 19) == 0)
            {
                System.out.println("Checking at pos " + o);
                TestMultiThreadedFileReader.writeData(expect, o);
                for (int i = 0; i < bsize; i++)
                {
                    assertEquals(expect[i], d[i]);

                    if (d[i] == 0)
                        break;
                }
            }
        }
        
        
        
    }


    
}