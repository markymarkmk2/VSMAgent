/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client;

import com.caucho.hessian.server.HessianServlet;
import de.dimm.vsm.VSMFSLogger;
import de.dimm.vsm.client.unix.UnixAgentApi;
import de.dimm.vsm.client.win.WinAgentApi;
import de.dimm.vsm.hash.HashFunctionPool;
import de.dimm.vsm.net.AttributeList;
import de.dimm.vsm.net.CdpTicket;
import de.dimm.vsm.net.CompEncDataResult;
import de.dimm.vsm.net.HashDataResult;
import de.dimm.vsm.net.InvalidCdpTicketException;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.net.RemoteFSElemWrapper;
import de.dimm.vsm.net.StoragePoolWrapper;
import de.dimm.vsm.net.VfsTicket;
import de.dimm.vsm.net.interfaces.AgentApi;
import de.dimm.vsm.net.interfaces.SnapshotHandle;
import de.dimm.vsm.records.Excludes;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 *
 * @author Administrator
 */
public class NetServlet extends HessianServlet implements AgentApi
{

    NetAgentApi api;


    private NetServlet(NetAgentApi api)
    {               
        this.api = api;
    }
    static public NetServlet createWinNetServlet() throws Exception
    {       
        HashFunctionPool pool;
        
        pool = new HashFunctionPool(10);
        return new NetServlet( new WinAgentApi(pool));
    }

    static public NetServlet createUnixNetServlet(String cdpIpFilter ) throws Exception
    {        
        HashFunctionPool pool;
        
        pool = new HashFunctionPool(10);
        return new NetServlet( new UnixAgentApi(pool, cdpIpFilter));
    }
    static public NetServlet createMacNetServlet(String cdpIpFilter ) throws Exception
    {        
        HashFunctionPool pool;
        
        pool = new HashFunctionPool(10);
        return new NetServlet( new UnixAgentApi(pool, cdpIpFilter));
    }



    @Override
    public ArrayList<RemoteFSElem> list_dir( RemoteFSElem dir, boolean listAcl )
    {
        return api.list_dir(dir, listAcl);
    }

    @Override
    public ArrayList<RemoteFSElem> list_roots()
    {
        return api.list_roots();
    }

   

    @Override
    public Properties get_properties()
    {
        return api.get_properties();
    }

    @Override
    public void set_options( Properties p )
    {
        api.set_options(p);
    }

    @Override
    public String read_hash( RemoteFSElemWrapper file, long pos, int bsize, String alg ) throws IOException
    {
        return api.read_hash(file, pos, bsize, alg);
    }

    @Override
    public RemoteFSElemWrapper open_data( RemoteFSElem file, int flags ) throws IOException
    {
        return api.open_data(file, flags);
    }

    @Override
    public RemoteFSElemWrapper open_stream_data( RemoteFSElem file, int flags ) throws IOException
    {
        return api.open_stream_data(file, flags);
    }

    @Override
    public boolean close_data( RemoteFSElemWrapper file ) throws IOException
    {
        return api.close_data(file);
    }

    @Override
    public byte[] read( RemoteFSElemWrapper file, long pos, int bsize ) throws IOException
    {
        VSMFSLogger.getLog().debug("read " + pos + "/" + bsize + " für " + file.getHandle());
        return api.read(file, pos, bsize);
    }
    @Override
    public CompEncDataResult readEncryptedCompressed( RemoteFSElemWrapper file, long pos, int bsize, boolean enc, boolean comp ) throws IOException
    {
        return api.readEncryptedCompressed(file, pos, bsize, enc, comp);
    }

    @Override
    public int write( RemoteFSElemWrapper file, byte[] data, long pos )
    {
        return api.write(file, data, pos);
    }

    @Override
    public byte[] read_complete( RemoteFSElem file ) throws IOException
    {
        return api.read_complete(file);
    }

    @Override
    public String read_hash_complete( RemoteFSElem file, String alg ) throws IOException
    {
        return api.read_hash_complete(file, alg);
    }

    @Override
    public boolean create_dir( RemoteFSElem dir ) throws IOException
    {
        return api.create_dir(dir);
    }

    @Override
    public HashDataResult read_and_hash( RemoteFSElemWrapper file, long pos, int bsize ) throws IOException
    {
        return api.read_and_hash(file, pos, bsize);
    }

    @Override
    public CompEncDataResult read_and_hash_encrypted_compressed( RemoteFSElemWrapper file, long pos, int bsize, boolean enc, boolean comp ) throws IOException
    {
        return api.read_and_hash_encrypted_compressed(file, pos, bsize, enc, comp);
    }

    @Override
    public SnapshotHandle create_snapshot( RemoteFSElem file )
    {
        return api.create_snapshot(file);
    }

    @Override
    public boolean release_snapshot( SnapshotHandle handle )
    {
        return api.release_snapshot(handle);
    }

    @Override
    public CdpTicket init_cdp( InetAddress addr, int port, boolean ssl, boolean tcp, RemoteFSElem file, long poolIdx, long schedIdx, long clientInfoIdx, long clientVolumeIdx ) throws IOException
    {
        return api.init_cdp( addr, port, ssl, tcp, file, poolIdx, schedIdx, clientInfoIdx, clientVolumeIdx);
    }

    @Override
    public boolean check_cdp( CdpTicket ticket )
    {
        return api.check_cdp(ticket);
    }

    @Override
    public boolean pause_cdp( CdpTicket ticket )
    {
        return api.pause_cdp(ticket);
    }

    @Override
    public boolean stop_cdp( CdpTicket ticket ) throws InvalidCdpTicketException
    {
        return api.stop_cdp(ticket);
    }

    @Override
    public List<CdpTicket> getCdpTickets()
    {
        return api.getCdpTickets();
    }

    @Override
    public boolean mountVSMFS( InetAddress addr, int port, StoragePoolWrapper pool/*, Date timestamp, String subPath, User user*/, String drive )
    {
        return api.mountVSMFS(addr, port, pool, /*timestamp, subPath, user, */drive);
    }

    @Override
    public boolean unmountVSMFS( InetAddress addr, int port, StoragePoolWrapper pool )
    {
        return api.unmountVSMFS(addr, port, pool);
    }

    @Override
    public boolean isMountedVSMFS( InetAddress addr, int port, StoragePoolWrapper pool )
    {
        return api.isMountedVSMFS(addr, port, pool);
    }

    @Override
    public byte[] fetch_null_data( int bsize )
    {
        return api.fetch_null_data(bsize);
    }

    @Override
    public AttributeList get_attributes( RemoteFSElem file )
    {
        return api.get_attributes(file);
    }
    @Override
    public boolean set_filetimes_named( RemoteFSElem elem )
    {
        return api.set_filetimes_named( elem );
    }

    @Override
    public boolean set_filetimes( RemoteFSElemWrapper elem )
    {
        return api.set_filetimes(elem);
    }

    @Override
    public boolean set_attributes( RemoteFSElemWrapper elem )
    {
        return api.set_attributes(elem);
    }
    

    @Override
    public RemoteFSElem check_hotfolder( RemoteFSElem mountPath, long getSetttleTime_s, final String filter, boolean onlyFiles, boolean onlyDirs, int itemIdx )
    {
        return api.check_hotfolder(mountPath, getSetttleTime_s, filter, onlyFiles, onlyDirs, itemIdx);
    }

    @Override
    public void deleteDir( RemoteFSElem path, boolean b ) throws IOException
    {
        api.deleteDir(path, b);
    }

    @Override
    public boolean create_symlink( RemoteFSElem remoteFSElem )
    {
        return api.create_symlink(remoteFSElem);
    }

    @Override
    public boolean create_other( RemoteFSElem remoteFSElem )
    {
        return api.create_other(remoteFSElem);
    }



    @Override
    public String readAclInfo( RemoteFSElem dir )
    {
        return api.readAclInfo(dir);
    }

    void idle()
    {
        api.idle();
    }

    public void resetFileReaders()
    {
        api.getFSElemAccessor().resetFileReaders();
    }

    @Override
    public int writeEncryptedCompressed( RemoteFSElemWrapper file, byte[] data, long pos, int realLen, boolean enc, boolean comp )
    {
        return api.writeEncryptedCompressed(file, data, pos, realLen, enc, comp);
    }

    @Override
    public void set_cdp_excludes( CdpTicket ticket, List<Excludes> exclList ) throws InvalidCdpTicketException
    {
        api.set_cdp_excludes(ticket, exclList);
    }

    @Override
    public ArrayList<RemoteFSElem> list_roots( int mode )
    {
        return api.list_roots(mode);
    }

  


}
