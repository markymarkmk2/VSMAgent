/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.unix;

import com.sun.jna.Native;
import com.thoughtworks.xstream.XStream;
import de.dimm.vsm.Utilities.ZipUtilities;
import de.dimm.vsm.client.AttributeContainerImpl;
import de.dimm.vsm.client.FSElemAccessor;
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

    @Override
    public synchronized  RemoteFSElem create_elem( File fh, boolean lazyAclInfo)
    {
        String typ = fh.isDirectory() ? FileSystemElemNode.FT_DIR : FileSystemElemNode.FT_FILE;

        long len = get_flen( fh );
        long streamLen = evalStreamLen( fh );


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
            
            elem = new RemoteFSElem( fh.getAbsolutePath(), typ,
                    stat.mtime() * 1000, stat.ctime() * 1000, stat.atime() * 1000,
                    len, streamLen );

            elem.setPosixData(stat.mode(), stat.uid(), stat.gid(), uidName, gidName);

            if (stat.isSymlink())
            {
                try
                {
                    String rl = posix.readlink(fh.getAbsolutePath());
                    elem.setLinkPath(rl);
                }
                catch (IOException iOException)
                {
                    return null;
                }
            }
        }
        else
        {
            elem = new RemoteFSElem( fh.getAbsolutePath(), typ,
                    fh.lastModified(), fh.lastModified(), fh.lastModified(),
                    len, streamLen );
        }

        elem.setStreaminfo(RemoteFSElem.STREAMINFO_ETHERSHARE);

        if (!elem.isSymbolicLink()/* && Main.hasNFSv4()*/)
        {

            if (lazyAclInfo)
            {
                elem.setAclinfoData(RemoteFSElem.LAZY_ACLINFO);
            }
            else
            {
                try
                {
                    AttributeContainer info = new AttributeContainer();

                    if (AttributeContainerImpl.fill( elem, info ))
                    {
                        int hash = info.hashCode();
                        String aclStream = getHashMap(hash);
                        if (aclStream == null)
                        {
                            aclStream = AttributeContainer.serialize(info);
                            putHashMap( hash, aclStream );
                        }
                        elem.setAclinfoData(aclStream);
                        elem.setAclinfo(RemoteFSElem.ACLINFO_WIN);
                    }
                }
                catch (Exception exc)
                {
                    exc.printStackTrace();
                }
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
    String get_path( File fh )
    {
        return fh.getAbsolutePath();
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
        String path = elem.getPath();


        String xaPath = getXAPath(path);
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

                    ByteBuffer buff = wrapByteBuffer(entry.getData());
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
                    AttributeContainerImpl.set(elem, info);
                }
            }
        }
    }

    private AttributeList get_attributes( RemoteFSElem elem ) 
    {
       AttributeList list = new AttributeList();

        String path = elem.getPath();
        String xaPath = getXAPath(path);
        File rsrc = new File(xaPath);

        // READ ES ATTRIBUTES (FILEINFO, FINDERINFO, COMMENT)
        if (rsrc.exists())
        {
            int rsrcLen = (int)rsrc.length();
            ByteBuffer buff = allocByteBuffer(rsrcLen);

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
                        buff.position(32);
                        buff.get(info);
                        AttributeEntry entry = new AttributeEntry( ESFILEINFONAME, info);
                        list.getList().add(entry);
                    }
                    if (rsrcLen >= 64)
                    {
                        byte[] info = new byte[32];
                        buff.position(32);
                        buff.get(info);
                        AttributeEntry entry = new AttributeEntry( FNDRINFONAME, info);
                        list.getList().add(entry);
                    }
                    if (rsrcLen >= 116)
                    {
                        int commentlen = 200;
                        if (rsrcLen < 316)
                            commentlen = rsrcLen - 116;

                        byte[] info = new byte[commentlen];
                        buff.position(116);
                        buff.get(info);
                        AttributeEntry entry = new AttributeEntry( OSXCOMMENT, info);
                        list.getList().add(entry);
                    }
                }
                else
                {
                    System.out.println("Unbekanntes Resourceformat " + magic);
                }
            }
        }

        // READ EXTENDED ATTRIBUTES
        int len = VSMLibC.listxattr(path, null, 0);
        if (len > 0)
        {
            ByteBuffer buff = ByteBuffer.allocate(4096);
            len = VSMLibC.listxattr(path, buff, buff.capacity());
            int errno = Native.getLastError();

            if (len <= 0)
                return null;

            byte[] arr = new byte[len];
            buff.get(arr, 0, len);
            String[] names = FSElemAccessor.nulltermList2Array(arr);

            for (int i = 0; i < names.length; i++)
            {
                String name = names[i];
                byte[] data = null;

                // SKIP ALL AUTOMATICALLY HANDLED ATTRIBUTES
                boolean skipAttribute = false;

                for (int j = 0; j < skipAttributes.length; j++)
                {
                    String attrName = skipAttributes[j];
                    if (name.equals(attrName))
                    {
                        skipAttribute = true;
                        break;
                    }
                }

                if (!skipAttribute)
                {
                    System.out.println("Adding extended attribute " + name + " for " + path);

                    len = VSMLibC.getxattr(path, name, null, 0);
                    if (len > 0)
                    {
                        buff = ByteBuffer.allocate(len);
                        len = VSMLibC.getxattr(path, name, buff, buff.capacity());
                        data = new byte[len];
                        buff.get(data, 0, len);

                        AttributeEntry entry = new AttributeEntry(name, data);
                        list.getList().add(entry);
                    }
                }
            }
        }

        // READ NFSv4 ACL (WIN/SOLARIS ZFS ONLY)
        AttributeContainer info = new AttributeContainer();
        if (AttributeContainerImpl.fill( elem, info ))
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
                    AttributeContainerImpl.set(elem, ac);
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
