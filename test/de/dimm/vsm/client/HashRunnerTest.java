/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.dimm.vsm.client;

import org.bouncycastle.crypto.BlockCipher;
import java.security.Key;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.util.encoders.Hex;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

class XTEA
{
    private static final int ROUNDS = 8;
    private static final int DELTA = 0x9E3779B9;
    private int k0, k1, k2, k3, k4, k5, k6, k7, k8, k9, k10, k11, k12, k13, k14, k15;
//    private int k16, k17, k18, k19, k20, k21, k22, k23, k24, k25, k26, k27, k28, k29, k30, k31;

    XTEA()
    {
        // do nothing
    }

    public void setKey( byte[] b )
    {
        int[] key = new int[4];
        for (int i = 0; i < ROUNDS;)
        {
            key[i / 4] = (b[i++] << 24) + ((b[i++] & 255) << 16) + ((b[i++] & 255) << 8) + (b[i++] & 255);
        }
        int[] r = new int[ROUNDS];
        for (int i = 0, sum = 0; i < ROUNDS;)
        {
            r[i++] = sum + key[sum & 3];
            sum += DELTA;
            r[i++] = sum + key[(sum >>> 11) & 3];
        }
        k0 = r[0];
        k1 = r[1];
        k2 = r[2];
        k3 = r[3];
        k4 = r[4];
        k5 = r[5];
        k6 = r[6];
        k7 = r[7];
//        k8 = r[8];
//        k9 = r[9];
//        k10 = r[10];
//        k11 = r[11];
//        k12 = r[12];
//        k13 = r[13];
//        k14 = r[14];
//        k15 = r[15];
//        k16 = r[16];
//        k17 = r[17];
//        k18 = r[18];
//        k19 = r[19];
//        k20 = r[20];
//        k21 = r[21];
//        k22 = r[22];
//        k23 = r[23];
//        k24 = r[24];
//        k25 = r[25];
//        k26 = r[26];
//        k27 = r[27];
//        k28 = r[28];
//        k29 = r[29];
//        k30 = r[30];
//        k31 = r[31];
    }

    public void encrypt( byte[] bytes, int off, int len )
    {

        for (int i = off; i < off + len; i += 8)
        {
            encryptBlock(bytes, bytes, i);
        }
    }

    public void decrypt( byte[] bytes, int off, int len )
    {

        for (int i = off; i < off + len; i += 8)
        {
            decryptBlock(bytes, bytes, i);
        }
    }

    private void encryptBlock( byte[] in, byte[] out, int off )
    {
        int y = (in[off] << 24) | ((in[off + 1] & 255) << 16) | ((in[off + 2] & 255) << 8) | (in[off + 3] & 255);
        int z = (in[off + 4] << 24) | ((in[off + 5] & 255) << 16) | ((in[off + 6] & 255) << 8) | (in[off + 7] & 255);
        y += (((z << 4) ^ (z >>> 5)) + z) ^ k0;
        z += (((y >>> 5) ^ (y << 4)) + y) ^ k1;
        y += (((z << 4) ^ (z >>> 5)) + z) ^ k2;
        z += (((y >>> 5) ^ (y << 4)) + y) ^ k3;
        y += (((z << 4) ^ (z >>> 5)) + z) ^ k4;
        z += (((y >>> 5) ^ (y << 4)) + y) ^ k5;
        y += (((z << 4) ^ (z >>> 5)) + z) ^ k6;
        z += (((y >>> 5) ^ (y << 4)) + y) ^ k7;
//        y += (((z << 4) ^ (z >>> 5)) + z) ^ k8;
//        z += (((y >>> 5) ^ (y << 4)) + y) ^ k9;
//        y += (((z << 4) ^ (z >>> 5)) + z) ^ k10;
//        z += (((y >>> 5) ^ (y << 4)) + y) ^ k11;
//        y += (((z << 4) ^ (z >>> 5)) + z) ^ k12;
//        z += (((y >>> 5) ^ (y << 4)) + y) ^ k13;
//        y += (((z << 4) ^ (z >>> 5)) + z) ^ k14;
//        z += (((y >>> 5) ^ (y << 4)) + y) ^ k15;
//        y += (((z << 4) ^ (z >>> 5)) + z) ^ k16;
//        z += (((y >>> 5) ^ (y << 4)) + y) ^ k17;
//        y += (((z << 4) ^ (z >>> 5)) + z) ^ k18;
//        z += (((y >>> 5) ^ (y << 4)) + y) ^ k19;
//        y += (((z << 4) ^ (z >>> 5)) + z) ^ k20;
//        z += (((y >>> 5) ^ (y << 4)) + y) ^ k21;
//        y += (((z << 4) ^ (z >>> 5)) + z) ^ k22;
//        z += (((y >>> 5) ^ (y << 4)) + y) ^ k23;
//        y += (((z << 4) ^ (z >>> 5)) + z) ^ k24;
//        z += (((y >>> 5) ^ (y << 4)) + y) ^ k25;
//        y += (((z << 4) ^ (z >>> 5)) + z) ^ k26;
//        z += (((y >>> 5) ^ (y << 4)) + y) ^ k27;
//        y += (((z << 4) ^ (z >>> 5)) + z) ^ k28;
//        z += (((y >>> 5) ^ (y << 4)) + y) ^ k29;
//        y += (((z << 4) ^ (z >>> 5)) + z) ^ k30;
//        z += (((y >>> 5) ^ (y << 4)) + y) ^ k31;
        out[off] = (byte) (y >> 24);
        out[off + 1] = (byte) (y >> 16);
        out[off + 2] = (byte) (y >> 8);
        out[off + 3] = (byte) y;
        out[off + 4] = (byte) (z >> 24);
        out[off + 5] = (byte) (z >> 16);
        out[off + 6] = (byte) (z >> 8);
        out[off + 7] = (byte) z;
    }

    private void decryptBlock( byte[] in, byte[] out, int off )
    {
        int y = (in[off] << 24) | ((in[off + 1] & 255) << 16) | ((in[off + 2] & 255) << 8) | (in[off + 3] & 255);
        int z = (in[off + 4] << 24) | ((in[off + 5] & 255) << 16) | ((in[off + 6] & 255) << 8) | (in[off + 7] & 255);
//        z -= (((y >>> 5) ^ (y << 4)) + y) ^ k31;
//        y -= (((z << 4) ^ (z >>> 5)) + z) ^ k30;
//        z -= (((y >>> 5) ^ (y << 4)) + y) ^ k29;
//        y -= (((z << 4) ^ (z >>> 5)) + z) ^ k28;
//        z -= (((y >>> 5) ^ (y << 4)) + y) ^ k27;
//        y -= (((z << 4) ^ (z >>> 5)) + z) ^ k26;
//        z -= (((y >>> 5) ^ (y << 4)) + y) ^ k25;
//        y -= (((z << 4) ^ (z >>> 5)) + z) ^ k24;
//        z -= (((y >>> 5) ^ (y << 4)) + y) ^ k23;
//        y -= (((z << 4) ^ (z >>> 5)) + z) ^ k22;
//        z -= (((y >>> 5) ^ (y << 4)) + y) ^ k21;
//        y -= (((z << 4) ^ (z >>> 5)) + z) ^ k20;
//        z -= (((y >>> 5) ^ (y << 4)) + y) ^ k19;
//        y -= (((z << 4) ^ (z >>> 5)) + z) ^ k18;
//        z -= (((y >>> 5) ^ (y << 4)) + y) ^ k17;
//        y -= (((z << 4) ^ (z >>> 5)) + z) ^ k16;
//        z -= (((y >>> 5) ^ (y << 4)) + y) ^ k15;
//        y -= (((z << 4) ^ (z >>> 5)) + z) ^ k14;
//        z -= (((y >>> 5) ^ (y << 4)) + y) ^ k13;
//        y -= (((z << 4) ^ (z >>> 5)) + z) ^ k12;
//        z -= (((y >>> 5) ^ (y << 4)) + y) ^ k11;
//        y -= (((z << 4) ^ (z >>> 5)) + z) ^ k10;
//        z -= (((y >>> 5) ^ (y << 4)) + y) ^ k9;
//        y -= (((z << 4) ^ (z >>> 5)) + z) ^ k8;
        z -= (((y >>> 5) ^ (y << 4)) + y) ^ k7;
        y -= (((z << 4) ^ (z >>> 5)) + z) ^ k6;
        z -= (((y >>> 5) ^ (y << 4)) + y) ^ k5;
        y -= (((z << 4) ^ (z >>> 5)) + z) ^ k4;
        z -= (((y >>> 5) ^ (y << 4)) + y) ^ k3;
        y -= (((z << 4) ^ (z >>> 5)) + z) ^ k2;
        z -= (((y >>> 5) ^ (y << 4)) + y) ^ k1;
        y -= (((z << 4) ^ (z >>> 5)) + z) ^ k0;
        out[off] = (byte) (y >> 24);
        out[off + 1] = (byte) (y >> 16);
        out[off + 2] = (byte) (y >> 8);
        out[off + 3] = (byte) y;
        out[off + 4] = (byte) (z >> 24);
        out[off + 5] = (byte) (z >> 16);
        out[off + 6] = (byte) (z >> 8);
        out[off + 7] = (byte) z;
    }

}

/**
 *
 * @author Administrator
 */
public class HashRunnerTest
{

    public HashRunnerTest()
    {
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

    /**
     * Test of run method, of class HashRunner.
     */
    @Test
    public void testRun()
    {
        System.out.println("run");
    }

    @Test
    public void testRC4()
    {
        String key = "0123456789abcdef";

        byte[] data = new byte[1024 * 1024];
        for (int i = 0; i < data.length; i++)
        {
            data[i] = (byte) (i % 256);
        }


        byte[] binaryKey = Hex.decode(key);

        Key secretKeySpec = new SecretKeySpec(binaryKey, "ARCFOUR");

        try
        {
            Cipher rc4 = Cipher.getInstance("ARCFOUR");

            long start = System.currentTimeMillis();
            byte[] bytes = null;
            byte[] decbytes = null;

            for (int i = 0; i < 1000; i++)
            {
                rc4.init(Cipher.ENCRYPT_MODE, secretKeySpec);
                bytes = rc4.update(data);


                rc4.init(Cipher.DECRYPT_MODE, secretKeySpec);
                decbytes = rc4.update(bytes);
            }
            long end = System.currentTimeMillis();

            System.out.println("it Took " + (end - start) + " ms to run 1GB RC4 en-/decryption");
            assertEquals(bytes.length, data.length);

            for (int i = 0; i < data.length; i++)
            {
                assertEquals(decbytes[i], data[i]);
            }
        }
        catch (Exception e)
        {
            fail("Exception: " + e.getMessage());
            e.printStackTrace();
        }



    }
    @Test
    public void testXTEA()
    {
        String key = "0123456789abcdef";

        XTEA xt = new XTEA();

        xt.setKey(key.getBytes());


        byte[] data = new byte[1024 * 1024];
        for (int i = 0; i < data.length; i++)
        {
            data[i] = (byte) (i % 256);
        }

        try
        {
            long start = System.currentTimeMillis();
            byte[] bytes = null;           

            for (int i = 0; i < 1000; i++)
            {
                xt.encrypt(data, 0, data.length);

                xt.decrypt(data, 0, data.length);
            }
            long end = System.currentTimeMillis();

            System.out.println("it Took " + (end - start) + " ms to run 1GB XTEA en-/decryption");
            
            for (int i = 0; i < data.length; i++)
            {
                assertEquals((byte) (i % 256), data[i]);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("Exception: " + e.getMessage());
        }



    }
}
