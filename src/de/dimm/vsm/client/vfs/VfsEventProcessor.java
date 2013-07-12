/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.dimm.vsm.client.vfs;

import de.dimm.vsm.VSMFSLogger;
import de.dimm.vsm.client.AgentPreferences;
import de.dimm.vsm.vfs.IVfsEventProcessor;
import de.dimm.vsm.client.Main;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.net.StoragePoolWrapper;
import de.dimm.vsm.net.interfaces.ServerApi;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 *
 * @author Administrator
 */
public class VfsEventProcessor implements IVfsEventProcessor
{
    StoragePoolWrapper ticket;
    ServerApi api;
    //ThreadPoolExecutor thrExe = new ThreadPoolExecutor();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    
    Map<Long,Future<Boolean>> futureMap = new HashMap<>();

    public VfsEventProcessor( InetAddress addr, int port, StoragePoolWrapper ticket ) throws UnknownHostException
    {
        this.ticket = ticket;        
        api = Main.getServerConn().getServerApi(addr, port, /*isSSL*/ false, /*is TCP*/ false);
    }        
    
    @Override
    public boolean process(final List<RemoteFSElem> elems) throws IOException
    {
        Callable<Boolean> callable = new Callable<Boolean>() {

            @Override
            public Boolean call() throws Exception
            {
                boolean ret = api.vfs_call(elems, ticket);
                return ret;
            }
        };
        Future<Boolean> future = executor.submit( callable );
                
        try
        {
            return future.get().booleanValue();
        }
        catch (InterruptedException interruptedException)
        {
            throw new IOException("Interrupted:", interruptedException);
        }
        catch (ExecutionException executionException)
        {
            throw new IOException("ExecutionException:", executionException);
        }
    }

    @Override
    public long startProcess( final List<RemoteFSElem> elems ) throws IOException
    {
        Callable<Boolean> callable = new Callable<Boolean>() {

            @Override
            public Boolean call() throws Exception
            {
                boolean ret = api.vfs_call(elems, ticket);
                return ret;
            }
        };
        Future<Boolean> future = executor.submit( callable );
        long id = getNextFutureId();
        futureMap.put( id, future);
        return id;
    }

    @Override
    public boolean waitProcess( long id, int timout ) throws IOException
    {
        Future<Boolean> future = futureMap.get(id);
        try
        {
            future.get(timout, TimeUnit.MILLISECONDS).booleanValue();
            return future.isDone();
        }
        catch (InterruptedException interruptedException)
        {
            throw new IOException("Interrupted:", interruptedException);
        }
        catch (ExecutionException executionException)
        {
            throw new IOException("ExecutionException:", executionException);
        }
        catch (TimeoutException ex)
        {
            return false;
        }
    }
    
    long getNextFutureId()
    {
        long id = System.currentTimeMillis();
        while (futureMap.containsKey(id))
        {
            id++;
        }    
        return id;
    }

    @Override
    public boolean fetchResult( long id ) throws IOException
    {
        Future<Boolean> future = futureMap.remove(id);
        try
        {
            if (future != null)
                return future.get().booleanValue();
            
            VSMFSLogger.getLog().error("Fehlendes Ergebnis fuer Future " + id);
            return false;
        }
        catch (InterruptedException interruptedException)
        {
            throw new IOException("Interrupted:", interruptedException);
        }
        catch (ExecutionException executionException)
        {
            throw new IOException("ExecutionException:", executionException);
        }
    }

    @Override
    public void abortProcess( long id )
    {        
        Future<Boolean> future = futureMap.remove(id);
        if (future != null)
        {
            future.cancel(true);
        }
    }

    @Override
    public long getMaxLocalFileThreshold()
    {
        return AgentPreferences.getPrefs().getMaxLocalFileThreshold();
    }
}
