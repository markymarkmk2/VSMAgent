/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.mac;

import com.sun.jna.Native;
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
import java.text.Normalizer;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
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
    private static String ACLNAME = "OsxAcl";

   
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
                if (start < end)
                    l.add(new String(arr, start, end));

                start = end + 1;
                if (start >= arr.length)
                    break;
            }
        }
        return l.toArray(new String[0]);
    }

    public List<AttributeEntry> getACLFinderAttributeEntry(String path) throws IOException
    {
         int             err;
         Attrlist      attrList = new Attrlist();
         List<AttributeEntry> al = new ArrayList<AttributeEntry>();
         
         ByteBuffer  buff = MacRemoteFSElemFactory.allocByteBuffer(4096);

         attrList.bitmapcount = MacRemoteFSElemFactory.ATTR_BIT_MAP_COUNT;
         attrList.commonattr  =MacRemoteFSElemFactory.ATTR_CMN_FNDRINFO | MacRemoteFSElemFactory.ATTR_CMN_EXTENDED_SECURITY;

         err = MacRemoteFSElemFactory.getattrlist(path, attrList, buff, buff.limit(), MacRemoteFSElemFactory.FSOPT_REPORT_FULLSIZE);
         
         if (err == 0)
         {

             int length = buff.getInt();
             if (length <= 36)
                 throw new IOException( "Invalid FinderInfoLen" );
             
             if (length > buff.capacity())
             {
                 buff = MacRemoteFSElemFactory.allocByteBuffer(length);
                 MacRemoteFSElemFactory.getattrlist(path, attrList, buff, buff.limit(), MacRemoteFSElemFactory.FSOPT_REPORT_FULLSIZE);
                 int newlen = buff.getInt();
                 
                 if (newlen != length)
                     throw new IOException("Len mismatch in getattrlist");
             }

             byte[] info = new byte[32];
             buff.get(info, 0, 32);
             
             AttributeEntry entry;
             
             // DETECT A NON-EMPTY FINDERINFO
             for (int i = 0; i < info.length; i++)
             {
                 byte b = info[i];
                 if (b != 0)
                 {
                    entry = new AttributeEntry(FNDRINFONAME, info);
                    al.add(entry);
                    //System.out.println("Finderattribute für " + path + " " + entry.toString());
                    break;
                 }                 
             }

             
//           typedef struct attrreference
//           {   
//               int32_t        attr_dataoffset;
//               u_int32_t      attr_length;
//           }
//           attrreference_t;
             int attr_dataoffset = buff.getInt();
             int attr_length = buff.getInt();
             if (attr_length > 0)
             {
                byte[] acl = new byte[attr_length];
                buff.get(acl, 0, attr_length);

                entry = new AttributeEntry(ACLNAME, acl);
                al.add(entry);

              //  System.out.println("ACLattribute für " + path + " " + entry.toString());
             }   
        }
         else
         {
             System.out.println("Fehler beim Lesen der ACL-Finderattribute für " + path);
         }
        return al;
    }


    public boolean setACLFinderAttributeEntry(String path, List<AttributeEntry> al) throws Exception
    {
         int err = 0;
         Attrlist attrList = new Attrlist();
         
         byte[] info = null;
         byte[] acl = null;
         int len = 0;
         for (int i = 0; i < al.size(); i++)
         {
             
             if (al.get(i).getEntry().equals(FNDRINFONAME))
             {
                 len += al.get(i).getData().length;
                 attrList.commonattr |= MacRemoteFSElemFactory.ATTR_CMN_FNDRINFO;
                 info = al.get(i).getData();
                 if( info.length != 32)
                     throw new IOException("Invalid FinderInfoLen");
             }
             if (al.get(i).getEntry().equals(ACLNAME))
             {
                 len += al.get(i).getData().length;
                 attrList.commonattr |= MacRemoteFSElemFactory.ATTR_CMN_EXTENDED_SECURITY;
                 acl = al.get(i).getData();
                 len += 8; // ATTRENTRY STRUCT
             }
         }

         if (len > 0)
         {

            // NO LEADING LEN setattrlist
            ByteBuffer buff = MacRemoteFSElemFactory.allocByteBuffer(len );
            attrList.bitmapcount = MacRemoteFSElemFactory.ATTR_BIT_MAP_COUNT;

            
            if (info != null)
                buff.put(info, 0, 32);
            if (acl != null)
            {
//           typedef struct attrreference
//           {   
//               int32_t        attr_dataoffset;
//               u_int32_t      attr_length;
//           }
//           attrreference_t;
                buff.putInt( 8 );
                buff.putInt(acl.length);               
                buff.put(acl, 0, acl.length);
            }
            byte[] bb = buff.array();
            err = MacRemoteFSElemFactory.setattrlist(path, attrList, bb, bb.length, 0);
         }
         return (err == 0);

    }
    
    private void set_attributes( RemoteFSElem elem,  AttributeList attrs ) throws Exception
    {
        String path = elem.getPath();
        path = Normalizer.normalize(path, Normalizer.Form.NFD);

        if (!setACLFinderAttributeEntry( path, attrs.getList() ))
        {
            System.out.println("Fehler beim Setzen der ACL-Finderattribute für " + path);
        }
        
        for (int i = 0; i < attrs.getList().size(); i++)
        {
            AttributeEntry entry = attrs.getList().get(i);
            String name = entry.getEntry();
            
            if (!name.equals(FNDRINFONAME) && !name.equals(ACLNAME))
            {
                ByteBuffer buff = ByteBuffer.wrap(entry.getData());
                if (VSMLibC.setxattr(path, name, buff, buff.capacity(), 0) != 0)
                {
                    System.out.println("Fehler beim Setzen des Attributes " + name + " für " + path);
                }
            }
        }
    }

    AttributeList get_attributes( RemoteFSElem elem ) throws Exception
    {
       AttributeList list = new AttributeList();

        String path = elem.getPath();

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
            String[] names = nulltermList2Array(arr);

            for (int i = 0; i < names.length; i++)
            {
                String name = names[i];
                byte[] data = null;
                if (!name.equals("system.posix_acl_access") && !name.equals("system.posix_acl_default"))
                {
                    System.out.println("Adding Mac attribute " + name + " for " + path);
                    
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
        // ADD FINDERINFO
        List<AttributeEntry> entries = getACLFinderAttributeEntry(path);
        list.getList().addAll(entries);
        return list;
    }
    
    @Override
    public void setAttributes( RemoteFSElem elem ) throws IOException
    {
        try
        {
            if (elem.getAclinfo() == RemoteFSElem.ACLINFO_OSX)
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
            else            
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

}
