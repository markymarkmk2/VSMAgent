/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.dimm.vsm.client;

import de.dimm.vsm.net.AttributeList;
import de.dimm.vsm.net.CdpTicket;
import de.dimm.vsm.net.CompEncDataResult;
import de.dimm.vsm.net.HashDataResult;
import de.dimm.vsm.net.InvalidCdpTicketException;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.net.RemoteFSElemWrapper;
import de.dimm.vsm.net.StoragePoolWrapper;
import de.dimm.vsm.net.interfaces.AgentApi;
import de.dimm.vsm.net.interfaces.SnapshotHandle;
import de.dimm.vsm.records.Excludes;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 *
 * @author Administrator
 */
public class TestMagicNetAgentApi implements AgentApi {

    NetAgentApi realApi;
    
    Map<Long, RemoteFSElem> siteMap = new HashMap<>();
    Map<String, Long> sizeMap = new HashMap<>();
    
    public TestMagicNetAgentApi( NetAgentApi reapApi ) {
        this.realApi = reapApi;
    }
    
    
    @Override
    public ArrayList<RemoteFSElem> list_dir( RemoteFSElem dir, boolean lazyAclInfo ) {
        return magicListDir( dir, lazyAclInfo);
    }

    @Override
    public ArrayList<RemoteFSElem> list_dir_local( RemoteFSElem dir, boolean lazyAclInfo ) {
        return magicListDir( dir, lazyAclInfo);
    }

    @Override
    public String readAclInfo( RemoteFSElem dir ) {
        return realApi.readAclInfo(mapFile(dir));
    }

    @Override
    public ArrayList<RemoteFSElem> list_roots() {
        return realApi.list_roots();
    }

    @Override
    public ArrayList<RemoteFSElem> list_roots( int mode ) {
        return realApi.list_roots(mode);
    }

    @Override
    public boolean create_dir( RemoteFSElem dir ) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public RemoteFSElemWrapper open_data( RemoteFSElem file, int flags ) throws IOException {
        RemoteFSElemWrapper ret = realApi.open_data(mapFile( file), flags );
        if (isMagicSize(file))
        {
            siteMap.put(ret.getHandle(), file);
        }
                
        return ret;

    }

    @Override
    public boolean close_data( RemoteFSElemWrapper file ) throws IOException {
        siteMap.remove(file.getHandle());
        return realApi.close_data(file);
    }

    @Override
    public AttributeList get_attributes( RemoteFSElem file ) {
        return realApi.get_attributes(mapFile( file) );
    }

    @Override
    public boolean exists( RemoteFSElem file ) {
        return realApi.exists(mapFile( file) );
    }

    @Override
    public Properties get_properties() {
        return realApi.get_properties();
    }

    @Override
    public void set_options( Properties p ) {
        realApi.set_options(p);
    }

    @Override
    public String read_hash( RemoteFSElemWrapper file, long pos, int bsize, String alg ) throws IOException {
        RemoteFSElem elem = siteMap.get(file.getHandle());
        if (isMagicSize(elem))
        {
            pos = 0;
            //bsize = sizeMap.get(elem.getPath()).intValue();
        }
        return realApi.read_hash(file, pos, bsize, alg);
    }

    @Override
    public String read_hash_complete( RemoteFSElem file, String alg ) throws IOException {
        return realApi.read_hash_complete(mapFile( file), alg);
    }

    @Override
    public byte[] read( RemoteFSElemWrapper file, long pos, int bsize ) throws IOException {
        RemoteFSElem elem = siteMap.get(file.getHandle());
        if (isMagicSize(elem))
        {
            pos = 0;
            //bsize = sizeMap.get(elem.getPath()).intValue();           
        }        
        return realApi.read(file, pos, bsize);
    }

    @Override
    public CompEncDataResult readEncryptedCompressed( RemoteFSElemWrapper file, long pos, int bsize, boolean enc, boolean comp ) throws IOException {
        return realApi.readEncryptedCompressed(file, pos, bsize, enc, comp);
    }

    @Override
    public HashDataResult read_and_hash( RemoteFSElemWrapper file, long pos, int bsize ) throws IOException {
        RemoteFSElem elem = siteMap.get(file.getHandle());
        if (isMagicSize(elem))
        {
            pos = 0;
            //bsize = sizeMap.get(elem.getPath()).intValue();          
        }              
        return realApi.read_and_hash(file, pos, bsize);
    }

    @Override
    public CompEncDataResult read_and_hash_encrypted_compressed( RemoteFSElemWrapper file, long pos, int bsize, boolean enc, boolean comp ) throws IOException {
        RemoteFSElem elem = siteMap.get(file.getHandle());
        if (isMagicSize(elem))
        {
            pos = 0;
            //bsize = sizeMap.get(elem.getPath()).intValue();        
        }              
        return realApi.read_and_hash_encrypted_compressed(file, pos, bsize, enc, comp);
    }

    @Override
    public int write( RemoteFSElemWrapper file, byte[] data, long pos ) {
        return realApi.write(file, data, pos);
    }

    @Override
    public int writeEncryptedCompressed( RemoteFSElemWrapper file, byte[] data, long pos, int encLen, boolean enc, boolean comp ) {
        return realApi.writeEncryptedCompressed(file, data, pos, encLen, enc, comp);
    }

    @Override
    public byte[] read_complete( RemoteFSElem file ) throws IOException {
        return realApi.read_complete(mapFile(file));
    }

    @Override
    public SnapshotHandle create_snapshot( RemoteFSElem file ) {
        return realApi.create_snapshot(mapFile(file));
    }

    @Override
    public boolean release_snapshot( SnapshotHandle handle ) {
        return realApi.release_snapshot(handle);
    }

    @Override
    public CdpTicket init_cdp( InetAddress addr, int port, boolean ssl, boolean tcp, RemoteFSElem file, long poolIdx, long schedIdx, long clientInfoIdx, long clientVolumeIdx ) throws IOException {
        return realApi.init_cdp(addr, port, ssl, tcp, file, poolIdx, schedIdx, clientInfoIdx, clientVolumeIdx);
    }

    @Override
    public boolean check_cdp( CdpTicket ticket ) throws InvalidCdpTicketException {
        return realApi.check_cdp(ticket);
    }

    @Override
    public boolean pause_cdp( CdpTicket ticket ) throws InvalidCdpTicketException {
        return realApi.pause_cdp(ticket);
    }

    @Override
    public boolean stop_cdp( CdpTicket ticket ) throws InvalidCdpTicketException {
        return realApi.stop_cdp(ticket);
    }

    @Override
    public List<CdpTicket> getCdpTickets() {
        return realApi.getCdpTickets();
    }

    @Override
    public void set_cdp_excludes( CdpTicket ticket, List<Excludes> exclList ) throws InvalidCdpTicketException {
        realApi.set_cdp_excludes(ticket, exclList);
    }

    @Override
    public boolean mountVSMFS( InetAddress addr, int port, StoragePoolWrapper pool, String drive ) {
        return realApi.mountVSMFS(addr, port, pool, drive);
    }

    @Override
    public boolean unmountVSMFS( InetAddress addr, int port, StoragePoolWrapper pool ) {
        return realApi.unmountVSMFS(addr, port, pool);
    }

    @Override
    public boolean isMountedVSMFS( InetAddress addr, int port, StoragePoolWrapper pool ) {
        return realApi.isMountedVSMFS(addr, port, pool);
    }

    @Override
    public RemoteFSElemWrapper open_stream_data( RemoteFSElem remoteFSElem, int FL_RDONLY ) throws IOException {
        return realApi.open_stream_data(remoteFSElem, FL_RDONLY);
    }

    @Override
    public byte[] fetch_null_data( int bsize ) {
        return realApi.fetch_null_data(bsize);
    }

    @Override
    public boolean set_filetimes_named( RemoteFSElem elem ) {
        return realApi.set_filetimes_named(mapFile(elem));
    }

    @Override
    public boolean set_filetimes( RemoteFSElemWrapper elem ) {
        return realApi.set_filetimes(elem);
    }

    @Override
    public boolean set_attributes( RemoteFSElemWrapper elem ) {
        return realApi.set_attributes(elem);
    }

    @Override
    public RemoteFSElem check_hotfolder( RemoteFSElem mountPath, long getSetttleTime_s, String filter, boolean onlyFiles, boolean onlyDirs, int itemIdx ) {
        return realApi.check_hotfolder(mountPath, getSetttleTime_s, filter, onlyFiles, onlyDirs, itemIdx);
    }

    @Override
    public void deleteDir( RemoteFSElem path, boolean b ) throws IOException {
        realApi.deleteDir(path, b);
    }

    @Override
    public boolean create_symlink( RemoteFSElem remoteFSElem ) {
        return realApi.create_symlink(remoteFSElem);
    }

    @Override
    public boolean create_other( RemoteFSElem remoteFSElem ) {
        return realApi.create_other(remoteFSElem);
    }

    private RemoteFSElem mapFile( RemoteFSElem file ) {
        if (file.getName().startsWith("MagicN."))
        {
            String[] parts = file.getName().split("\\.");
            if (parts.length == 3)
            {
                int lastIdx = file.getPath().lastIndexOf(".");
                RemoteFSElem newElem = new RemoteFSElem(file);
                newElem.setPath(file.getPath().substring(0, lastIdx));
                
                return newElem;
            }
        }
        return file;        
    }

    private ArrayList<RemoteFSElem> magicListDir( RemoteFSElem dir, boolean lazyAclInfo ) {
                
        dir = mapFile( dir );
        ArrayList<RemoteFSElem> list = realApi.list_dir(dir, lazyAclInfo );
        ArrayList<RemoteFSElem> addList = new ArrayList<>();
        
        for (RemoteFSElem elem: list)
        {
            if (elem.getName().startsWith("MagicN."))
            {
                int cnt = Integer.parseInt(elem.getName().split("\\.")[1]);
                for (int i = 0; i < cnt; i++)
                {
                    RemoteFSElem newElem = new RemoteFSElem(elem);
                    newElem.setPath( elem.getPath() + "." + i);         
                    addList.add(newElem);
                }
            }
            if (isMagicSize(elem))
            {
                sizeMap.put(elem.getPath(),elem.getDataSize());
                long cnt = Integer.parseInt(elem.getName().split("\\.")[1]);
                elem.setDataSize(cnt*1000*1000);
            }
        }        
        
        list.addAll(addList);        
        return list;
    }

    private boolean isMagicSize( RemoteFSElem file ) {
        if (file == null)
            return false;
        
        return file.getName().startsWith("MagicS.");
    }
    
}
