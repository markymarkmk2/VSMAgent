/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client;

import com.ning.compress.lzf.LZFInputStream;
import java.io.IOException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author Administrator
 */
public class ReadRunnerTest {

    public ReadRunnerTest() {
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
     * Test of run method, of class ReadRunner.
     */
    @Test
    public void testRun()
    {
        System.out.println("run");
        try
        {
            LZFInputStream lzf = new LZFInputStream(null);
        }
        catch (IOException iOException)
        {
        }
        
    }

}