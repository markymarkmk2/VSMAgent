/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client;

import de.dimm.vsm.net.RemoteFSElem;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 *
 * @author Administrator
 */
public abstract class RemoteFSElemFactory
{

    public static String FNDRINFONAME = "FndrInfo";
    public static String ESFILEINFONAME = "ESFileInfo";
    public static String OSXCOMMENT = "OsxComment";
    public static String ACLNAME = "OsxAcl";
    public static String ACLFILENAME = "AclFileAttribute";

    public abstract RemoteFSElem create_elem( File fh, boolean lazyAclInfo );
    public abstract String readAclInfo( RemoteFSElem elem ) throws IOException;
    public abstract void writeAclInfo( RemoteFSElem elem ) throws IOException;
    public abstract String getFsName( String path );

    public abstract String getXAPath( String path );

    public abstract boolean mkDir( File f );

     public static ByteBuffer allocByteBuffer( int len)
     {
         ByteBuffer  buff = ByteBuffer.allocate(len);
         buff.order(ByteOrder.LITTLE_ENDIAN);
         if (System.getProperty("os.arch").startsWith("ppc"))
         {
             buff.order(ByteOrder.BIG_ENDIAN);
         }

         return buff;
     }
     public static ByteBuffer wrapByteBuffer( byte[] data)
     {
         ByteBuffer  buff = ByteBuffer.wrap(data);
         buff.order(ByteOrder.LITTLE_ENDIAN);
         if (System.getProperty("os.arch").startsWith("ppc"))
         {
             buff.order(ByteOrder.BIG_ENDIAN);
         }

         return buff;
     }

}
