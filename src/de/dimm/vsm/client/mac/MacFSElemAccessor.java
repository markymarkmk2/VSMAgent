/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.mac;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.thoughtworks.xstream.XStream;
import de.dimm.vsm.Utilities.ZipUtilities;
import de.dimm.vsm.client.AttributeContainerImpl;
import de.dimm.vsm.client.FSElemAccessor;
import de.dimm.vsm.client.FileHandleData;
import de.dimm.vsm.client.NetAgentApi;
import de.dimm.vsm.client.jna.PosixWrapper;
import de.dimm.vsm.client.jna.VSMLibC;
import de.dimm.vsm.client.mac.MacRemoteFSElemFactory.Attrlist;
import de.dimm.vsm.net.AttributeContainer;
import de.dimm.vsm.net.AttributeEntry;
import de.dimm.vsm.net.AttributeList;
import de.dimm.vsm.net.interfaces.AgentApi;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.net.RemoteFSElemWrapper;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import org.apache.commons.codec.binary.Base64;
import org.jruby.ext.posix.POSIX;



class MacFileHandleData extends FileHandleData
{
    RandomAccessFile handle;

    public MacFileHandleData( RemoteFSElem elem, RandomAccessFile handle )
    {
        super(elem);
        this.handle = handle;
        
    }

    @Override
    public boolean close()
    {
        try
        {
            handle.close();
        }
        catch (IOException iOException)
        {
            return false;
        }
        return true;
    }
}
/**
 *
 * @author Administrator
 */
public class MacFSElemAccessor extends FSElemAccessor
{
    
    static long newHandleValue = 1;
    NetAgentApi api;
    private static String FNDRINFONAME = "FndrInfo";

   
    public MacFSElemAccessor( NetAgentApi api)
    {
        super(api);
        this.api = api;
    }

    @Override
    public RemoteFSElemWrapper open_handle( RemoteFSElem elem, int flags )
    {
        RandomAccessFile fh = null;
        try
        {
            if (flags == AgentApi.FL_RDONLY)
            {
                fh = new RandomAccessFile(elem.getPath(), "r");
            }
            else if (test_flag(flags, AgentApi.FL_RDWR))
            {
                fh = new RandomAccessFile(elem.getPath(), "rw");
            }
        }
        catch (FileNotFoundException fileNotFoundException)
        {
            return null;
        }

        MacFileHandleData data = new MacFileHandleData(elem,fh);

        RemoteFSElemWrapper wrapper = new RemoteFSElemWrapper(newHandleValue++, /*xa*/false);

        hash_map.put(wrapper.getHandle(), data);

        return wrapper;
    }

    @Override
    public RemoteFSElemWrapper open_xa_handle( RemoteFSElem elem, int flags )
    {
        RandomAccessFile fh = null;
        String path = MacRemoteFSElemFactory.getRsrcPath( elem.getPath() );
        try
        {
            if (flags == AgentApi.FL_RDONLY)
            {
                fh = new RandomAccessFile(path, "r");
            }
            else if (test_flag(flags, AgentApi.FL_RDWR))
            {
                try
                {
                    fh = new RandomAccessFile(path, "rw");
                }
                catch (FileNotFoundException fileNotFoundException)
                {
                    File parent = new File( path );
                    if (!parent.getParentFile().exists())
                    {
                        parent.getParentFile().mkdirs();
                        fh = new RandomAccessFile(path, "rw");
                    }
                }
            }
        }
        catch (FileNotFoundException fileNotFoundException)
        {
            return null;
        }

        MacFileHandleData data = new MacFileHandleData(elem,fh);

        RemoteFSElemWrapper wrapper = new RemoteFSElemWrapper(newHandleValue++, /*xa*/true);

        hash_map.put(wrapper.getHandle(), data);

        return wrapper;
    }

    @Override
    public boolean close_handle(RemoteFSElemWrapper wrapper) throws IOException
    {

        if (wrapper == null)
            return false;
        
        super.close_handle(wrapper);

        FileHandleData data = hash_map.remove(wrapper.getHandle());
        if (data == null)
            return false;

        return data.close();
    }
    
    public RandomAccessFile get_handle( RemoteFSElemWrapper wrapper)
    {
        MacFileHandleData data = (MacFileHandleData)hash_map.get(wrapper.getHandle());
        if (data == null)
            return null;

        return data.handle;
    }
    @Override
    public FileHandleData get_handleData( RemoteFSElemWrapper wrapper)
    {
        FileHandleData data = hash_map.get(wrapper.getHandle());

        return data;
    }

    void setFiletime( String path, RemoteFSElem dir )
    {
        long[] atimes = new long[2];
        long[] mtimes = new long[2];

        atimes[0] = dir.getAtimeMs() / 1000;
        atimes[1] = (dir.getAtimeMs() % 1000) * 1000;
        mtimes[0] = dir.getMtimeMs() / 1000;
        mtimes[1] = (dir.getMtimeMs() % 1000) * 1000;

        PosixWrapper.getPosix().utimes(path, atimes, mtimes );
    }

    @Override
    public boolean createSymlink( String path, String linkPath )
    {
        POSIX posix = PosixWrapper.getPosix();
        try
        {
            if (posix.symlink(path, linkPath) == 0)
                return true;
        }
        catch (Exception e)
        {
        }
        return false;
   }


    private String[] nulltermList2Array( byte[] arr )
    {
        if (arr.length == 0)
        {
            return new String[0];
        }

        ArrayList<String> l = new ArrayList<String>();

        int start = 0;
        int end = 0;

        while (true)
        {
            end++;
            if (end == arr.length)
            {
                if (end - start > 1)
                {
                    l.add(new String(arr, start, end - start - 1));
                }

                break;
            }
            else if (arr[end] == 0)
            {
                l.add(new String(arr, start, end));

                start = end + 1;
            }
        }
        return l.toArray(new String[0]);
    }

    AttributeEntry getFinderAttributeEntry(String path) throws IOException
    {
         int             err;
         Attrlist      attrList = new Attrlist();
         ByteBuffer  buff = MacRemoteFSElemFactory.allocByteBuffer(4096);

         attrList.bitmapcount = MacRemoteFSElemFactory.ATTR_BIT_MAP_COUNT;
         attrList.commonattr  =MacRemoteFSElemFactory.ATTR_CMN_FNDRINFO;

         err = MacRemoteFSElemFactory.getattrlist(path, attrList, buff, buff.limit(), 0);

         if (err == 0)
         {

             System.out.println("Finder information for " +  path);

             int length = buff.getInt(0);
             if (length != 36)
                 throw new IOException( "Invalid FinderInfoLen" );

             byte[] info = new byte[32];
             buff.get(info, 4, 32);

             AttributeEntry entry = new AttributeEntry(FNDRINFONAME, info);
             return entry;
        }
        return null;
    }


    boolean setFinderAttributeEntry(String path, byte[] info) throws IOException
    {
         int             err;
         Attrlist      attrList = new Attrlist();
         ByteBuffer  buff = MacRemoteFSElemFactory.allocByteBuffer(36);


         attrList.bitmapcount = MacRemoteFSElemFactory.ATTR_BIT_MAP_COUNT;
         attrList.commonattr  =MacRemoteFSElemFactory.ATTR_CMN_FNDRINFO;
         buff.putInt(0, 36);
         buff.put(info, 4, 32);

         err = MacRemoteFSElemFactory.setattrlist(path, attrList, buff, buff.limit(), 0);

         return (err == 0);

    }
    private void set_attributes( RemoteFSElem elem,  AttributeList attrs ) throws IOException
    {
        String path = elem.getPath();

        for (int i = 0; i < attrs.getList().size(); i++)
        {
            AttributeEntry entry = attrs.getList().get(i);
            String name = entry.getEntry();
            if (name.equals("system.posix_acl_access") || name.equals("system.posix_acl_default"))
            {
                Pointer acl = VSMLibC.ACLLibrary.INSTANCE.acl_from_text(new String(entry.getData()));
                try
                {
                    VSMLibC.ACLLibrary.INSTANCE.acl_set_file(path, VSMLibC.ACL_TYPE_ACCESS, acl);
                }
                finally
                {
                    VSMLibC.ACLLibrary.INSTANCE.acl_free(acl);
                }
            }
            else if (name.equals(FNDRINFONAME))
            {
                if (!setFinderAttributeEntry(path, entry.getData()))
                {
                    throw new IOException("Cannot set Finderinfo");
                }
            }
            else
            {
                ByteBuffer buff = ByteBuffer.wrap(entry.getData());
                VSMLibC.setxattr(path, name, buff, buff.capacity(), 0);
            }
        }
    }

    AttributeList get_attributes( RemoteFSElem elem ) throws IOException
    {
       AttributeList list = new AttributeList();

        String path = elem.getPath();

        ByteBuffer buff = ByteBuffer.allocate(4096);
        int len = VSMLibC.CLibrary.INSTANCE.listxattr(path, buff, buff.capacity());

        byte[] arr = new byte[len];
        buff.get(arr, 0, len);
        String[] names = nulltermList2Array(arr);

        for (int i = 0; i < names.length; i++)
        {
            String name = names[i];
            byte[] data = null;
            if (name.equals("system.posix_acl_access") || name.equals("system.posix_acl_default"))
            {
                Pointer acl = VSMLibC.ACLLibrary.INSTANCE.acl_get_file(path, VSMLibC.ACL_TYPE_ACCESS);
                IntByReference acl_len = new IntByReference(0);
                Pointer text = VSMLibC.ACLLibrary.INSTANCE.acl_to_text(acl, acl_len);

                String s = String.copyValueOf(text.getString(0).toCharArray());

                VSMLibC.ACLLibrary.INSTANCE.acl_free(text);
                VSMLibC.ACLLibrary.INSTANCE.acl_free(acl);

                // STRING
                data = s.getBytes();
            }
            else
            {
                buff.rewind();
                len = VSMLibC.getxattr(path, name, buff, buff.capacity());
                data = new byte[len];
                buff.get(data, 0, len);
                // CONVERT TO STRING
                data = Base64.decodeBase64(data);
            }
            AttributeEntry entry = new AttributeEntry(name, data);
            list.getList().add(entry);
        }

        AttributeEntry entry = getFinderAttributeEntry(path);
        list.getList().add(entry);
        return list;
    }
    
    @Override
    public void setAttributes( RemoteFSElem elem ) throws IOException
    {
        if (elem.getAclinfo() == RemoteFSElem.ACLINFO_OSX)
        {
            XStream xs = new XStream();
            String s = ZipUtilities.uncompress(xs.toXML(elem.getAclinfoData()));
            Object o = xs.fromXML(s);
            if (o instanceof AttributeList)
            {
                set_attributes(elem, (AttributeList) o);
            }
        }    
        else 
        {
            AttributeContainer ac = AttributeContainer.unserialize(elem.getAclinfoData());
            if (ac != null)
            {
                AttributeContainerImpl.set(elem, ac);
            }            
        }
    }

}
