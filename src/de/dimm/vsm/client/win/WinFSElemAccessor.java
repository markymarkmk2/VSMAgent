/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.win;

import com.sun.jna.WString;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import de.dimm.vsm.client.FSElemAccessor;
import de.dimm.vsm.client.FileHandleData;
import de.dimm.vsm.client.NetAgentApi;
import de.dimm.vsm.client.jna.LibKernel32;
import de.dimm.vsm.client.jna.LibKernel32.FILETIME;
import de.dimm.vsm.client.jna.LibKernel32.WIN32_STREAM_ID;
import de.dimm.vsm.net.interfaces.AgentApi;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.net.RemoteFSElemWrapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


class StreamEntry
{
    LibKernel32.WIN32_STREAM_ID stream_id;
   

    byte[] complete_data;

    public StreamEntry( WIN32_STREAM_ID stream_id,  byte[] stream_id_arr) throws IOException
    {
        this.stream_id = stream_id;
        if (stream_id.Size > Integer.MAX_VALUE)
            throw new IOException("Stream is too large:" + stream_id.toString());


        try
        {
            complete_data = new byte[(int)getSize()];

            // COPY STREAM DATA
            System.arraycopy(stream_id_arr, 0, complete_data, 0, stream_id_arr.length);
        }
        catch (Exception e)
        {
            throw new IOException("Invalid stream data:" + stream_id.toString());
        }
    }
    final long getSize()
    {
        return stream_id.Size + stream_id.dwStreamNameSize + LibKernel32.WINSTREAM_ID_SIZE;
    }
    void setName( byte[] name ) throws IOException
    {
        try
        {
            System.arraycopy(name, 0, complete_data, LibKernel32.WINSTREAM_ID_SIZE, stream_id.dwStreamNameSize);
        }
        catch (Exception e)
        {
            throw new IOException("Copying invalid stream name:" + stream_id.toString());
        }
    }
    void setData( byte[] data, int pos, int len ) throws IOException
    {
        try
        {
            System.arraycopy(data, 0, complete_data, LibKernel32.WINSTREAM_ID_SIZE + stream_id.dwStreamNameSize + pos, len );
        }
        catch (Exception e)
        {
            throw new IOException("Copying invalid stream data:" + stream_id.toString());
        }
    }

    byte[] getArray()
    {
        return complete_data;
    }

}

class WinFileHandleData extends FileHandleData
{
    HANDLE handle;
    
    PointerByReference context;
    List<StreamEntry> streamList;
    
   

    public WinFileHandleData(HANDLE h, boolean xa, RemoteFSElem elem)
    {
        super(elem);
        this.handle = h;

        streamList = null;

        if (xa)
            context = new PointerByReference();

    }

    @Override
    public boolean close()
    {
        if (streamList != null)
        {
            streamList.clear();
        }
        boolean ret = LibKernel32.CloseHandle(handle);
        //System.out.println("Close: " + handle.toString() + " " + ret);
        return ret;
        
    }

    @Override
    public RemoteFSElem getElem()
    {
        return elem;
    }



    // READ A COMPLETE STREAM AND RETURN STRUCT
    StreamEntry read_next_stream( LibKernel32.WIN32_STREAM_ID streamInfo, byte[] streamArray ) throws IOException
    {
        StreamEntry entry = new StreamEntry(streamInfo, streamArray);
        IntByReference bytes_read = new IntByReference();

        if (streamInfo.dwStreamNameSize > 0)
        {
            int name_byte_len = streamInfo.dwStreamNameSize;
            byte[] name = new byte[name_byte_len];
            boolean ret = LibKernel32.BackupRead(handle, name, name_byte_len, bytes_read, /*abort*/false, /*security*/true, context);
            if (!ret || bytes_read.getValue() != name_byte_len)
                throw new IOException("Cannot read stream name");

            entry.setName(name);
        }
        if (streamInfo.Size > 0)
        {
            if (streamInfo.Size > Integer.MAX_VALUE)
            {
                throw new IOException("Stream size is too big");
            }

            // READ COMPLETE DATA STREAM
            byte[] dataArr = new byte[(int)streamInfo.Size];
            boolean ret = LibKernel32.BackupRead(handle, dataArr, (int)streamInfo.Size, bytes_read, /*abort*/false, /*security*/true, context);

            if (!ret || bytes_read.getValue() <= 0)
                throw new IOException("Cannot read stream data");

            entry.setData(dataArr, 0, bytes_read.getValue());

            // DETECT SHORT READ
            int restLen = (int)(streamInfo.Size - bytes_read.getValue());
            int pos = bytes_read.getValue();
            while (restLen > 0)
            {
                // TRY THE REST UNTIL ALL IS READ
                byte[] bb = new byte[restLen];
                ret = LibKernel32.BackupRead(handle, bb, restLen, bytes_read, /*abort*/false, /*security*/true, context);

                if (!ret || bytes_read.getValue() <= 0)
                    throw new IOException("Cannot read stream data");

                // ADD TO DATA ARRAY
                entry.setData(dataArr, pos, bytes_read.getValue());


                restLen -= bytes_read.getValue();
                pos += bytes_read.getValue();
            }
        }
        return entry;
    }

    List<StreamEntry> readStreamList() throws IOException
    {
        List<StreamEntry> list = new ArrayList<StreamEntry>();
        boolean finished = false;


        while (!finished)
        {
            byte[] arr = new byte[LibKernel32.WINSTREAM_ID_SIZE];
            IntByReference bytes_read = new IntByReference();

            boolean ret = LibKernel32.BackupRead(handle, arr, LibKernel32.WINSTREAM_ID_SIZE, bytes_read, /*abort*/false, /*security*/true, context);

            if (ret && bytes_read.getValue() == 0)
            {
                // REGULAR EXIT
                finished = true;
                break;
            }
            LibKernel32.WIN32_STREAM_ID streamInfo =  WinRemoteFSElemFactory.get_stream_id( arr, 0 );

            // SKIP REG DATA STREAM
            if (streamInfo.dwStreamId == LibKernel32.BACKUP_DATA)
            {
                WinRemoteFSElemFactory.Seek(handle, streamInfo.Size + streamInfo.dwStreamNameSize, context);
                continue;
            }

            StreamEntry entry = read_next_stream( streamInfo, arr);
            list.add(entry);

        }
        return list;
    }
}
/**
 *
 * @author Administrator
 */
public class WinFSElemAccessor extends FSElemAccessor
{
    //HashMap<Long,WinFileHandleData> hash_map;

    static long newHandleValue = 1;

    public WinFSElemAccessor( NetAgentApi api)
    {
        super(api);
//        hash_map = new HashMap<Long, WinFileHandleData>();
    }



    public HANDLE open_raw_handle( RemoteFSElem elem, int flags )
    {
        WString path = new WString( WinRemoteFSElemFactory.getLongPath(elem));

        HANDLE h = null;
        int dwCreationDisposition = 0;
        int attrib = LibKernel32.FILE_ATTRIBUTE_NORMAL | LibKernel32.FILE_FLAG_BACKUP_SEMANTICS;

        if (flags == AgentApi.FL_RDONLY)
        {            
            h = LibKernel32.CreateFile(path, LibKernel32.GENERIC_READ, LibKernel32.FILE_SHARE_READ, null, LibKernel32.OPEN_EXISTING, attrib, null);
        }
        else if(test_flag( flags, AgentApi.FL_RDWR))
        {
            dwCreationDisposition = LibKernel32.OPEN_EXISTING;
            
            if (test_flag( flags, AgentApi.FL_CREATE ))
                dwCreationDisposition = LibKernel32.OPEN_ALWAYS;


            h = LibKernel32.CreateFile(path, LibKernel32.GENERIC_READ | LibKernel32.GENERIC_WRITE, LibKernel32.FILE_SHARE_READ, null, dwCreationDisposition, attrib, null);

        }
        //System.out.println("Try  : " +  path + ": " + flags);
        if (LibKernel32.isInvalidHandleValue(h))
        {
            int err = LibKernel32.GetLastError();
            return null;
        }
        if (test_flag( flags, AgentApi.FL_RDWR) && test_flag( flags, AgentApi.FL_TRUNC ))
        {
            LibKernel32.SetEndOfFile( h );
        }
        //System.out.println("Open : " + h.toString() + " P: " + elem.getPath());
        return h;
    }
    public boolean close_raw_handle( HANDLE h )
    {
        boolean ret = LibKernel32.CloseHandle(h);
        //System.out.println("Close: " + h.toString() + " " + ret);
        return ret;        
    }

    @Override
    public RemoteFSElemWrapper open_handle( RemoteFSElem elem, int flags )
    {
        HANDLE h = open_raw_handle( elem, flags );

        if (h == null)
            return null;
        
        WinFileHandleData data = new WinFileHandleData(h, false, elem);

        RemoteFSElemWrapper wrapper = new RemoteFSElemWrapper(newHandleValue++, /*xa*/false);

        hash_map.put(wrapper.getHandle(), data);

        return wrapper;
    }

    @Override
    public RemoteFSElemWrapper open_xa_handle( RemoteFSElem elem, int flags )
    {
        WString path = null;
        path = new WString( WinRemoteFSElemFactory.getLongPath(elem) );

        HANDLE h = null;
        if (flags == AgentApi.FL_RDONLY)
        {
            int attrib = LibKernel32.FILE_ATTRIBUTE_NORMAL  | LibKernel32.FILE_FLAG_BACKUP_SEMANTICS;
            h = LibKernel32.CreateFile(path, LibKernel32.GENERIC_READ, LibKernel32.FILE_SHARE_READ, null, LibKernel32.OPEN_EXISTING, attrib, null);
        }
        else if(test_flag( flags, AgentApi.FL_RDWR))
        {
            int dwCreationDisposition = LibKernel32.OPEN_EXISTING;
            if (test_flag( flags, AgentApi.FL_CREATE ))
                dwCreationDisposition = LibKernel32.OPEN_ALWAYS;

            // APPEND!
            int attrib = LibKernel32.FILE_ATTRIBUTE_NORMAL  | LibKernel32.FILE_FLAG_BACKUP_SEMANTICS;
            int access = LibKernel32.WRITE_OWNER | LibKernel32.WRITE_DAC | LibKernel32.FILE_APPEND_DATA | LibKernel32.READ_CONTROL;
            h = LibKernel32.CreateFile(path, access, LibKernel32.FILE_SHARE_READ, null, dwCreationDisposition, attrib, null);
        }

        if (LibKernel32.isInvalidHandleValue(h))
        {
            // DOES NOT WORK, MAYBE WE HAVE TO GRANT SE_BACKUP!
            int err = LibKernel32.GetLastError();
            return null;
        }

        WinFileHandleData data = new WinFileHandleData(h, true, elem);

        RemoteFSElemWrapper wrapper = new RemoteFSElemWrapper(newHandleValue++, /*xa*/true);

        hash_map.put(wrapper.getHandle(), data);

        //System.out.println("OpenX: " + h.toString());
        return wrapper;
    }

    @Override
    public boolean close_handle(RemoteFSElemWrapper wrapper) throws IOException
    {
        if (wrapper == null)
            return false;

        super.close_handle(wrapper);

        WinFileHandleData data = (WinFileHandleData) hash_map.remove(wrapper.getHandle());
        if (data == null)
            return false;

        return data.close();
    }

    public HANDLE get_handle( RemoteFSElemWrapper wrapper)
    {
        WinFileHandleData data = (WinFileHandleData) hash_map.get(wrapper.getHandle());
        if (data == null)
            return null;

        return data.handle;
    }

//    WinFileHandleData get_handleData( RemoteFSElemWrapper wrapper)
//    {
//        WinFileHandleData data = hash_map.get(wrapper.getHandle());
//
//        return data;
//    }

    void setFiletime( HANDLE h, RemoteFSElem dir )
    {
        FILETIME mtime = new LibKernel32.FILETIME();
        mtime.SetAbsMS(dir.getMtimeMs());
        FILETIME ctime = new LibKernel32.FILETIME();
        ctime.SetAbsMS(dir.getCtimeMs());
        FILETIME atime = new LibKernel32.FILETIME();
        atime.SetAbsMS(dir.getAtimeMs());

        LibKernel32.SetFileTime(h, ctime, atime, mtime);
    }
    
    
    @Override
    public boolean createSymlink( String path, String linkPath )
    {
        return false;

//        int dwFlags = 0;
//        if (new File(path).isDirectory())
//            dwFlags = 1;
//
//        return LibKernel32.CreateSymbolicLink(path, linkPath, dwFlags);
    }

}
