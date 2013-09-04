/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.unix;

import com.thoughtworks.xstream.XStream;
import de.dimm.vsm.Utilities.ZipUtilities;
import de.dimm.vsm.client.AttributeContainerImpl;
import de.dimm.vsm.client.NetAgentApi;
import de.dimm.vsm.client.RemoteFSElemFactory;
import de.dimm.vsm.client.jna.PosixWrapper;
import de.dimm.vsm.client.jna.VSMLibC;
import de.dimm.vsm.net.AttributeContainer;
import de.dimm.vsm.net.AttributeEntry;
import de.dimm.vsm.net.AttributeList;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.records.FileSystemElemNode;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.HashMap;
import org.jruby.ext.posix.FileStat;
import org.jruby.ext.posix.Group;
import org.jruby.ext.posix.POSIX;
import org.jruby.ext.posix.Passwd;


/**
 *
 * @author Administrator
 */
public class EtherShareRemoteFSElemFactory extends RemoteFSElemFactory
{


    public final static int VERSION  = 0x0102;
    public final static int MAGIC  = 0x3681093;
    
    public final static int INFOLEN  = 32;

    //typedef struct FileInfo {
    //uint32 magic;
    //uint32 serno; /* written only, never read */
    //uint16 version;
    //uint16 attr; /* invisible... */
    //uint16 openMax; /* max number of opens */
    //uint16 filler0;
    //uint32 backupCleared; /* time backup bit cleared */
    //uint32 id; /* dir/file id */
    //uint32 createTime; /* unix format */
    //uint32 backupTime; /* unix format */
    //uint8 finderInfo[INFOLEN];
    //} FileInfo;
    //
    //typedef struct XFileInfo {
    //FileInfo fi;
    //uint8 shortFileName[12 + 1 + 3]; /* not used anymore */
    //uint8 longFileName[32 + 1 + 3]; /* not used anymore */
    //uint8 comment[199 + 1];
    //} XFileInfo;


    Charset utf8Charset;

    public EtherShareRemoteFSElemFactory()
    {
        utf8Charset = Charset.forName("UTF-8");
    }



    String getTypFromStat(FileStat stat)
    {
        if (stat.isFile())
            return FileSystemElemNode.FT_FILE;
        if (stat.isDirectory())
            return FileSystemElemNode.FT_DIR;
        if (stat.isSymlink())
            return FileSystemElemNode.FT_SYMLINK;

        return FileSystemElemNode.FT_OTHER;
    }
    static String hex = "0123456789abcdef";
    static String es_cpecial_chars = "/^\\\"<>|?*";
    static byte get2Hex( char c1, char c2 ) throws IllegalArgumentException
    {
        int i1 = hex.indexOf(Character.toLowerCase(c1));
        int i2 = hex.indexOf(Character.toLowerCase(c2));
        if (i1 < 0 || i2 < 0)
            throw new IllegalArgumentException("Wrong CAP-Code " + c1 + c2);
        return (byte)((i1<<4) + i2);
    }
    @Override
    public String convNative2SystemPath( String p )
    {
        int idx = p.indexOf('^');
        if (idx == -1)
            return p;
        String[] parr = p.split("/");
        StringBuilder sb = new StringBuilder(p.length());
        for (int i = 0; i < parr.length; i++)
        {
            String string = parr[i];
            String sys =  convNative2SystemName( string );
            if (i > 0)
                sb.append("/");

            sb.append(sys);
        }
        return sb.toString();
    }
    @Override
    public String convSystem2NativePath( String p )
    {
        if (p.equals("/"))
            return p;
        String[] parr = p.split("/");
        StringBuilder sb = new StringBuilder(p.length());
        for (int i = 0; i < parr.length; i++)
        {
            String string = parr[i];
            String sys =  convSystem2NativeName( string );
            if (i > 0)
                sb.append("/");

            sb.append(sys);
        }
        return sb.toString();
    }

    public String convNative2SystemName( String p )
    {
        int idx = p.indexOf('^');
        if (idx == -1)
            return p;

        StringBuilder sb = new StringBuilder(p.length());

        try
        {
            for (int i = 0; i < p.length(); i++)
            {
                char ch = p.charAt(i);
                if (ch != '^')
                {
                    sb.append(ch);
                    continue;
                }
                char c1 = p.charAt(i + 1);
                char c2 = p.charAt(i + 2);
                char b = (char)get2Hex(c1, c2);
                if (b == '/')
                    b = 0xf022;
                sb.append(b);
                i+= 2;
            }
        }
        catch (Exception e)
        {
            System.out.println("Error in Es2SystemPath of " + p + ": " + e.getMessage());
            return p;
        }
        return sb.toString();
    }

    public String convSystem2NativeName( String f )
    {
        StringBuilder sb = new StringBuilder(f.length());

        try
        {
            for (int i = 0; i < f.length(); i++)
            {
                char ch = f.charAt(i);
                if (ch == 0xf022)
                {
                    ch = '/';
                }
                
                if (Character.isLetterOrDigit(ch) || ch >= 0x80)
                {
                    sb.append(ch);
                    continue;
                }
                if (ch < 0x20 || es_cpecial_chars.indexOf(ch) >= 0)
                {
                    sb.append('^');
                    sb.append(hex.charAt((ch >> 4) & 0xf));
                    sb.append(hex.charAt(ch & 0xf));
                    continue;
                }
                sb.append(ch);
            }
        }
        catch (Exception e)
        {
            System.out.println("Error in System2EsPath of " + f + ": " + e.getMessage());
            return f;
        }
        return sb.toString();
    }



    @Override
    public synchronized  RemoteFSElem create_elem( File fh, boolean lazyAclInfo)
    {
        String typ = fh.isDirectory() ? FileSystemElemNode.FT_DIR : FileSystemElemNode.FT_FILE;

        long len = get_flen( fh );
        long streamLen = evalStreamLen( fh );


        String path = convNative2SystemPath(fh.getAbsolutePath());
        POSIX posix = PosixWrapper.getPosix();

        FileStat stat = null;
        int ret = -1;
        try
        {
            stat = posix.lstat(fh.getAbsolutePath() );            
        }
        catch (Exception e)
        {
        }
        
        RemoteFSElem elem = null;
        if (stat != null)
        {            
            typ = getTypFromStat(stat);
            
            String gidName = "";
            Group gr = posix.getgrgid( stat.gid());
            if (gr != null)
                gidName = gr.getName();

            Passwd pw = posix.getpwuid(stat.uid());
            String uidName = "";
            if (pw != null)
                uidName = pw.getLoginName();
            
            elem = new RemoteFSElem( path, typ,
                    stat.mtime() * 1000, stat.ctime() * 1000, stat.atime() * 1000,
                    len, streamLen );

            elem.setPosixData(stat.mode(), stat.uid(), stat.gid(), uidName, gidName);

            if (stat.isSymlink())
            {
                try
                {
                    String rl = posix.readlink(fh.getAbsolutePath());
                    elem.setLinkPath(convNative2SystemPath(rl));
                }
                catch (IOException iOException)
                {
                    return null;
                }
            }
            elem.setStDev(stat.dev());            
        }
        else
        {
            elem = new RemoteFSElem( path, typ,
                    fh.lastModified(), fh.lastModified(), fh.lastModified(),
                    len, streamLen );
        }

        elem.setStreaminfo(RemoteFSElem.STREAMINFO_ETHERSHARE);

        if (!elem.isSymbolicLink()/* && Main.hasNFSv4()*/)
        {

            if (lazyAclInfo)
            {
                elem.setAclinfoData(RemoteFSElem.LAZY_ACLINFO);
                elem.setAclinfo(RemoteFSElem.ACLINFO_ES);
            }
            else
            {

                try
                {
                    elem.setAclinfoData(readAclInfo(elem));
                }
                catch (Exception iOException)
                {
                    System.out.println("DFehler beim Lesen der Attribute " + iOException.getMessage() );
                }
                elem.setAclinfo(RemoteFSElem.ACLINFO_ES);
            }
        }
       
        return elem;
    }
    
    @Override
    public String getXAPath( String path )
    {
        StringBuilder sb = new StringBuilder(path);
        int fidx = sb.lastIndexOf(File.separator);
        if (fidx > 0)
            sb.insert(fidx, "/.rsrc");
        
        return sb.toString();
    }


    long get_flen( File fh )
    {
        return fh.length();
    }

    
    public long evalStreamLen( File fh )
    {
        String adpPath = getXAPath( fh.getAbsolutePath() );

        File f = new File(adpPath);

        if (f.exists())
        {
            long l = f.length();
            if (l > 512)
                return l - 512;
        }

        return 0;
    }



    static HashMap<Integer, String> attrHashMap = new HashMap<Integer, String>();
    void putHashMap( int hash, String aclStream)
    {
        if (attrHashMap.size() > 10000)
            attrHashMap.clear();

        attrHashMap.put(hash, aclStream);
    }
    String getHashMap( int hash)
    {
        return attrHashMap.get(hash);
    }

    public static final String[] skipAttributes=
    {
        "system.posix_acl_access",
        "system.posix_acl_default",
        "com.apple.FinderInfo",
        "com.apple.ResourceFork"
    };




    private void set_attributes( RemoteFSElem elem,  AttributeList attrs ) throws IOException
    {
        String path = convSystem2NativePath( elem.getPath() );


        String xaPath = getXAPath(path);
        File data = new File(path);
        File rsrc = new File(xaPath);



        RandomAccessFile raf = null;
        try
        {
            for (int i = 0; i < attrs.getList().size(); i++)
            {
                AttributeEntry entry = attrs.getList().get(i);
                String name = entry.getEntry();

                // HANDLE OPENING AND HEADER ON FIRST WRITE, WE DONT NEED TO WRITE
                // A HEADER IF WE DONT HAVE FINDERINFO
                if (name.equals(ESFILEINFONAME))
                {
                    if (raf == null)
                        raf = new RandomAccessFile( rsrc, "rw" );
                    ByteBuffer  buff = ByteBuffer.wrap(entry.getData());
                    buff.order(ByteOrder.BIG_ENDIAN);
                    // RESET FILENO/DIRID
                    buff.position(20);
                    buff.putInt(0);
                    // UPDATE CTIME IN ES FILEINFO
                    buff.position(24);
                    buff.putInt((int)(elem.getCtimeMs()/1000));

                    raf.seek(0);
                    raf.write(entry.getData());
                }
                if (name.equals(FNDRINFONAME))
                {
                    if (raf == null)
                        throw new IOException("Ungültige Resourcereihgenfolge in " + xaPath);
                    raf.seek(32);
                    raf.write(entry.getData());
                }
                if (name.equals(OSXCOMMENT))
                {
                    if (raf == null)
                        throw new IOException("Ungültige Resourcereihgenfolge in " + xaPath);

                    raf.seek(116);
                    raf.write(entry.getData());
                }
            }
        }
        finally
        {
            if (raf != null)
            {
                raf.close();
            }
        }

     
        for (int i = 0; i < attrs.getList().size(); i++)
        {
            AttributeEntry entry = attrs.getList().get(i);
            String name = entry.getEntry();


            if (!name.equals(FNDRINFONAME) && !name.equals(ACLNAME) 
                    && !name.equals(OSXCOMMENT) && !name.equals(ESFILEINFONAME)
                    && !name.equals(ACLFILENAME) )
            {
                ByteBuffer buff = ByteBuffer.wrap(entry.getData());
                if (VSMLibC.setxattr(path, name, buff, buff.capacity(), 0) != 0)
                {
                    System.out.println("Fehler beim Setzen des Attributes " + name + " für " + path);
                }
            }
            // EMBEDDED WINACL
            if (name.equals(ACLFILENAME))
            {
                String aclStream = new String( entry.getData(),utf8Charset);
                AttributeContainer info = AttributeContainer.unserialize(aclStream);
                if (info != null)
                {
                    AttributeContainerImpl.set(path, info);
                }
            }
        }

        POSIX posix = PosixWrapper.getPosix();
        if (elem.getPosixMode() != 0)
        {
            posix.chmod(data.getAbsolutePath(), elem.getPosixMode());
            posix.chmod(rsrc.getAbsolutePath(), elem.getPosixMode());
            posix.chown(data.getAbsolutePath(), elem.getUid(), elem.getGid());
            posix.chown(rsrc.getAbsolutePath(), elem.getUid(), elem.getGid());
        }

    }

    private static boolean isDataEmpty( byte[] d )
    {
        for (int i = 0; i < d.length; i++)
        {
            byte b = d[i];
            if (b != 0)
                return false;
        }
        return true;
    }
    private AttributeList get_attributes( RemoteFSElem elem ) 
    {
       AttributeList list = new AttributeList();

        String path = convSystem2NativePath( elem.getPath() );
        String xaPath = getXAPath(path);
        File rsrc = new File(xaPath);

        // READ ES ATTRIBUTES (FILEINFO, FINDERINFO, COMMENT)
        if (rsrc.exists())
        {
            int rsrcLen = (int)rsrc.length();
            ByteBuffer  buff = ByteBuffer.allocate(rsrcLen);
            buff.order(ByteOrder.BIG_ENDIAN);

            FileInputStream fis = null;
            try
            {
                fis = new FileInputStream(rsrc);
                fis.read(buff.array());
                fis.close();
            }
            catch (IOException iOException)
            {
                rsrcLen = 0;
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

            if (rsrcLen > 0)
            {
                // CHECK CONTENT
                buff.rewind();
                int magic = buff.getInt();
                if (magic == MAGIC)
                {
                    if (rsrcLen >= 32)
                    {
                        byte[] info = new byte[32];
                        buff.position(0);
                        buff.get(info);
                        if (!isDataEmpty( info ) )
                        {
                            AttributeEntry entry = new AttributeEntry( ESFILEINFONAME, info);
                            list.getList().add(entry);
                        }
                    }
                    if (rsrcLen >= 64)
                    {
                        byte[] info = new byte[32];
                        buff.position(32);
                        buff.get(info);
                        if (!isDataEmpty( info ) )
                        {
                            AttributeEntry entry = new AttributeEntry( FNDRINFONAME, info);
                            list.getList().add(entry);
                        }
                    }
                    if (rsrcLen >= 116)
                    {
                        int commentlen = 200;
                        if (rsrcLen < 316)
                            commentlen = rsrcLen - 116;

                        byte[] info = new byte[commentlen];
                        buff.position(116);
                        buff.get(info);
                        if (!isDataEmpty( info ) )
                        {
                            AttributeEntry entry = new AttributeEntry( OSXCOMMENT, info);
                            list.getList().add(entry);
                        }
                    }
                }
                else
                {
                    System.out.println("Unbekanntes Resourceformat " + magic);
                }
            }
        }

        // READ EXTENDED ATTRIBUTES
//        int len = VSMLibC.listxattr(path, null, 0);
//        if (len > 0)
//        {
//            ByteBuffer buff = ByteBuffer.allocate(4096);
//            len = VSMLibC.listxattr(path, buff, buff.capacity());
//            int errno = Native.getLastError();
//
//            if (len <= 0)
//                return null;
//
//            byte[] arr = new byte[len];
//            buff.get(arr, 0, len);
//            String[] names = FSElemAccessor.nulltermList2Array(arr);
//
//            for (int i = 0; i < names.length; i++)
//            {
//                String name = names[i];
//                byte[] data = null;
//
//                // SKIP ALL AUTOMATICALLY HANDLED ATTRIBUTES
//                boolean skipAttribute = false;
//
//                for (int j = 0; j < skipAttributes.length; j++)
//                {
//                    String attrName = skipAttributes[j];
//                    if (name.equals(attrName))
//                    {
//                        skipAttribute = true;
//                        break;
//                    }
//                }
//
//                if (!skipAttribute)
//                {
//                    System.out.println("Adding extended attribute " + name + " for " + path);
//
//                    len = VSMLibC.getxattr(path, name, null, 0);
//                    if (len > 0)
//                    {
//                        buff = ByteBuffer.allocate(len);
//                        len = VSMLibC.getxattr(path, name, buff, buff.capacity());
//                        data = new byte[len];
//                        buff.get(data, 0, len);
//
//                        AttributeEntry entry = new AttributeEntry(name, data);
//                        list.getList().add(entry);
//                    }
//                }
//            }
//        }

        // READ NFSv4 ACL (WIN/SOLARIS ZFS ONLY)
        AttributeContainer info = new AttributeContainer();
        if (AttributeContainerImpl.fill( path, info ))
        {
            int hash = info.hashCode();
            String aclStream = getHashMap(hash);
            if (aclStream == null)
            {
                aclStream = AttributeContainer.serialize(info);
                putHashMap( hash, aclStream );
            }
            AttributeEntry entry = new AttributeEntry(ACLFILENAME, aclStream.getBytes(utf8Charset));
            list.getList().add(entry);
        }
       
        return list;
    }

    @Override
    public synchronized  String readAclInfo( RemoteFSElem elem )
    {
        AttributeList attrs = get_attributes(elem);
        if (attrs == null)
        {
            return null;
        }
        if (attrs.getList().isEmpty())
        {
            return null;
        }
        XStream xs = new XStream();
        String s = ZipUtilities.compress(xs.toXML(attrs));
        return s;
    }

    @Override
    public String getFsName( String path )
    {
        // TODO
        return "zfs";
    }

   

    @Override
    public void writeAclInfo( RemoteFSElem elem ) throws IOException
    {
        if (elem.getAclinfoData() == null)
            return;
        
        try
        {
            if (elem.getAclinfo() == RemoteFSElem.ACLINFO_ES)
            {
                if (elem.getAclinfoData() != null)
                {
                    XStream xs = new XStream();
                    String s = ZipUtilities.uncompress(elem.getAclinfoData());
                    Object o = xs.fromXML(s);
                    if (o instanceof AttributeList)
                    {
                        set_attributes(elem, (AttributeList) o);
                    }
                }

            }
            else if(elem.getAclinfo() == RemoteFSElem.ACLINFO_WIN || elem.getAclinfo() == 0)
            {
                AttributeContainer ac = AttributeContainer.unserialize(elem.getAclinfoData());
                if (ac != null)
                {
                    String path = convSystem2NativePath( elem.getPath() );
                    AttributeContainerImpl.set(path, ac);
                }
            }
        }
        catch (Exception exception)
        {
            throw new IOException("Error while setting ACL and FinderInfo:" + exception.getMessage() );
        }
    }

    @Override
    public boolean mkDir( File f )
    {
        boolean ret = f.mkdir();

        File rsrc = new File(f, NetAgentApi.ES_RSRCDIR);
        rsrc.mkdir();
        return ret;
    }


}
