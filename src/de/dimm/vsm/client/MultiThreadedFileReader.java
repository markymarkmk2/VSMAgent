/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client;


import de.dimm.vsm.client.mac.MacAgentApi;
import de.dimm.vsm.client.mac.MacMultiThreadedFileReader;
import de.dimm.vsm.client.unix.UnixAgentApi;
import de.dimm.vsm.client.unix.UnixMultiThreadedFileReader;
import de.dimm.vsm.client.win.WinAgentApi;
import de.dimm.vsm.client.win.WinMultiThreadedFileReader;
import de.dimm.vsm.hash.HashFunctionPool;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.net.RemoteFSElemWrapper;
import fr.cryptohash.Digest;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.commons.codec.binary.Base64;


/**
 *
 * @author Administrator
 */

// TO READ AND CREATE HASH EFFICIANTLY WE READ DATA IN A SEPERATE THREAD (ReadRunner), PUT THE BLOCKS INTO TWO
// BLOCKED QUEUES, hashQueue AND readyQueue. THE GETTER WAITS FOR ENTRIES IN THE readyQueue, TAKES THEM AND
// BLOCKS ON A LOCK (hashReady) IF THE HASH IS NOT READY. UP TO MAX_HASH_QUEUE  THREADS BLOCK ON THE hashQueue,
// TAKE THE BLOCKS, CALC THE HASH AND SIGNAL THE LOCK.
// THIS GUARANTEES, THAT THE ORDER OF THE INPUT IS PRESERVED AND WE CAN CALC THE HASH FOR EACH BLOCK IN PARALLEL
// THE FASTER THE INPUT READ IS, THE MORE HASH THREADS ARE WORKING
// AN A FAIRLY MODERN CPU A SHA-1 HASH IS CAPABLE OF




class ReadRunner implements Runnable
{
    final MultiThreadedFileReader aiw;

    public ReadRunner( MultiThreadedFileReader aiw )
    {
        this.aiw = aiw;
    }

    @Override
    public void run()
    {
        
        long start = System.currentTimeMillis();
        int n = 0;
        while (aiw.keepRunning)
        {
            try
            {
//                long a = System.currentTimeMillis();
                FileCacheElem elem = aiw.workList.poll(aiw.sleepMilisecondOnEmpty, TimeUnit.MILLISECONDS);
//                long b = System.currentTimeMillis();
//                if (b - a > 30 && !aiw.workList.isEmpty())
//                {
//                    System.out.println("Wread " + Long.toString(b-a));
//                }
//                if ( elem != null )
//                    n++;
//
//                if (n >= 100)
//                {
//                    int diff = (int)(b - start);
//                    if (diff == 0)
//                        diff = 1;
//                    long speed = 100*1000 / diff;
//                    System.out.println("Read " + n + " blocks in " + Long.toString(b - start) + " ms " + speed + " MB/s");
//                    start = b;
//                    n = 0;
//                }

                if (elem != null)
                {
//                    a = System.currentTimeMillis();
                    byte[] block = aiw.getBlock(elem.len);
                    aiw.handleRead(block, elem);
//                    b = System.currentTimeMillis();
//                    if (b - a > 30 && !aiw.workList.isEmpty())
//                    {
//                        System.out.println("WhandleRead " + Long.toString(b-a));
//                    }


                    // PUT ELEM IN BOTH LISTS, TO KEEP EVETRYTHING IN CORRCT ORDER, WE SYNCHRONIZE READYNESS OVER THE LOCK
//                    a = System.currentTimeMillis();
                    HashReadyLock lock = aiw.lockQueue.take();
                    
//                    b = System.currentTimeMillis();
//
//                    if (b - a > 30 && !aiw.workList.isEmpty())
//                    {
//                        System.out.println("WlockQueue Take " + Long.toString(b-a));
//                    }


//                    a = System.currentTimeMillis();
                    elem.lock = lock;
//                    int hq1 = aiw.hashQueue.size();
                    aiw.hashQueue.put(elem);
//                    int hq2 = aiw.hashQueue.size();
//                    b = System.currentTimeMillis();
//
//                    if (b - a > 30 && !aiw.workList.isEmpty())
//                    {
//                        System.out.println("WputHashQueue Take " + Long.toString(b-a) + " RDYQ: " + aiw.readyQueue.size() + " HashQ1/2: " + hq1 + "/" + hq2 +
//                                " ReadQ: " + aiw.workList.size() + " FinishQ: " + aiw.finishQueue.size() + " LQ: " + aiw.lockQueue.size()
//                                + " BQ: " + aiw.blockBuffer.size());
//                    }

//                    a = System.currentTimeMillis();
//                    int rq1 = aiw.readyQueue.size();
                    aiw.readyQueue.put(elem);
//                    int rq2 = aiw.readyQueue.size();
//                    b = System.currentTimeMillis();
//
//                    if (b - a > 30 && !aiw.workList.isEmpty())
//                    {
//                        System.out.println("WputreadyQueue Take " + Long.toString(b-a) + " RDYQ 1/2: " + rq1 + "/" + rq2  + " HashQ: " + aiw.hashQueue.size() +
//                                " ReadQ: " + aiw.workList.size() + " FinishQ: " + aiw.finishQueue.size() + " LQ: " + aiw.lockQueue.size()
//                                + " BQ: " + aiw.blockBuffer.size());
//                    }

                }
                else
                {
                   // System.out.println("ReadQ Poll: " + aiw.workList.size());
                }
            }
            catch (Exception e)
            {
                System.out.println("Exception in ReadRunner " + e.getMessage());
                e.printStackTrace();
                aiw.readError = true;
            }
        }
        aiw.isWRunning = false;
    }
}

class HashRunner implements Runnable
{
    final MultiThreadedFileReader aiw;


    public HashRunner( MultiThreadedFileReader aiw )
    {
        this.aiw = aiw;        
    }

    @Override
    public void run()
    {
        // CREATE URLSAFE ENCODE
        Base64 b64 = new Base64(0, null, true);

        Digest digest = new fr.cryptohash.SHA1();
        HashReadyLock lock = null;
        FileCacheElem fce = null;
        ReentrantLock readyLock = null;
        Condition hashReady = null;
        while (aiw.keepRunning)
        {
            try
            {
               
//                long a = System.currentTimeMillis();
                fce = aiw.hashQueue.poll(aiw.sleepMilisecondOnEmpty, TimeUnit.MILLISECONDS);
//                long b = System.currentTimeMillis();
//                if (b - a > 30 && !aiw.hashQueue.isEmpty())
//                {
//                    if (fce == null)
//                        System.out.println("FCE-NULL");
//
//                    System.out.println("Whash " + Long.toString(b-a) + " RDYQ: " + aiw.readyQueue.size() + " HashQ: " + aiw.hashQueue.size() +
//                                " ReadQ: " + aiw.workList.size() + " FinishQ: " + aiw.finishQueue.size() + " LQ: " + aiw.lockQueue.size()
//                                + " BQ: " + aiw.blockBuffer.size());
//                }
                if (fce != null)
                {
                    lock = fce.lock;
                    readyLock = lock.readyLock;
                    hashReady = lock.hashReady;

                    try
                    {
                        aiw.handleHash(digest, b64, fce);
                        readyLock.lock();
                        fce.hashready = true;
                        hashReady.signal();
                    }
                    finally
                    {
                        readyLock.unlock();
                    }                    
                }
                else
                {
                    lock = null;
                    fce = null;
                    readyLock = null;
                    hashReady = null;                    
                }

            }
            catch (Exception e)
            {
                System.out.println("Exception in HashRunner " + e.getMessage());
                e.printStackTrace();
                aiw.hashError = true;
            }
        }
        digest.reset();
        aiw.isWRunning = false;
    }
}

class FinishRunner implements Runnable
{
    final MultiThreadedFileReader aiw;

    public FinishRunner( MultiThreadedFileReader aiw )
    {
        this.aiw = aiw;
    }

    @Override
    public void run()
    {
        long start = System.currentTimeMillis();
        while (aiw.keepRunning)
        {
            HashReadyLock lock = null;
            try
            {
//                long a = System.currentTimeMillis();
                FileCacheElem readyElem = aiw.readyQueue.poll(aiw.sleepMilisecondOnEmpty, TimeUnit.MILLISECONDS);
//                long b = System.currentTimeMillis();
//                if (b - a > 1 && !aiw.workList.isEmpty())
//                {
//                    System.out.println("Wfinish " + Long.toString(b-a));
//                }

//                long now = System.currentTimeMillis();
//                if (now - start > 1000)
//                {
//                    start = now;
//                    if (!aiw.workList.isEmpty())
//                    {
//                        System.out.println(".....................RDYQ: " + aiw.readyQueue.size() + " HashQ: " + aiw.hashQueue.size() +
//                                " ReadQ: " + aiw.workList.size() + " FinishQ: " + aiw.finishQueue.size()
//                                + " BQ: " + aiw.blockBuffer.size());
//                    }
//                }

                if (readyElem != null)
                {
                    boolean isReady = readyElem.hashready;
                    lock = readyElem.lock;

                   
                    if (!isReady)
                    {
                        try
                        {
                            lock.readyLock.lock();
                            isReady = readyElem.hashready;

                            if (!isReady)
                                isReady = lock.hashReady.await(120000, TimeUnit.MILLISECONDS);
                        }
                        finally
                        {
                            lock.readyLock.unlock();
                        }
                    }


                    aiw.lockQueue.put(lock);
                    lock = null;

                    if (isReady)
                    {
                        aiw.finishQueue.put(readyElem);
                    }
                    else
                    {
                        System.out.println("Hash is not ready");
                        aiw.hashError = true;
                    }
                } 
                else
                {
                    //System.out.println("RDYQ Poll: " + aiw.workList.size());
                }
            }
            catch (InterruptedException interruptedException)
            {
                System.out.println("Interrupt in fetchElem");
                interruptedException.printStackTrace();
                if (lock != null)
                {
                    try
                    {
                        aiw.lockQueue.put(lock);
                    }
                    catch (InterruptedException ex)
                    {

                    }
                }
            }
            catch (RuntimeException exc)
            {
                System.out.println("Interrupt in fetchElem");
                exc.printStackTrace();
                if (lock != null)
                {
                    try
                    {
                        aiw.lockQueue.put(lock);
                    }
                    catch (InterruptedException ex)
                    {

                    }
                }
            }

        }        
    }
}

class HashReadyLock
{
    int idx;
    ReentrantLock readyLock;
    Condition hashReady;

    public HashReadyLock( int idx)
    {
        this.idx = idx;
        readyLock = new ReentrantLock();
        hashReady = readyLock.newCondition();
    }

    @Override
    public String toString()
    {
        return "HRL " + idx;
    }

}

public abstract class MultiThreadedFileReader
{
    LinkedBlockingQueue<FileCacheElem> hashQueue;
    LinkedBlockingQueue<FileCacheElem> readyQueue;
    LinkedBlockingQueue<FileCacheElem> finishQueue;
    BlockingQueue<FileCacheElem> workList;

    public static final int MAX_BIGBLOCKS = 50;
    boolean keepRunning = true;

    long sleepMilisecondOnEmpty = 50;
    boolean isWRunning;
    boolean readError;

    Thread writerThread;
    Thread finishThread;
    boolean hashError;
   // HashFunctionPool hashPool;
    public static final int MAX_HASH_RUNNERS = 8;
    Thread[] hashThreads;

    protected RemoteFSElem actPath;
    protected RemoteFSElemWrapper actWrapper;
    FileCacheElem readyElem;

    LinkedBlockingQueue<HashReadyLock> lockQueue;
    static boolean verbose = false;

    int lockQueueSize = MAX_HASH_RUNNERS * 2;
    int hashThreadCnt = MAX_HASH_RUNNERS;

    final ArrayList<byte[]> blockBuffer;
    int bigBlockSize = 0;

    public MultiThreadedFileReader( int maxElems)
    {
        hashQueue = new LinkedBlockingQueue<FileCacheElem>(MAX_HASH_RUNNERS*2);
        lockQueue = new LinkedBlockingQueue<HashReadyLock>(lockQueueSize);
        readyQueue = new LinkedBlockingQueue<FileCacheElem>(MAX_HASH_RUNNERS*2);
        workList = new LinkedBlockingQueue<FileCacheElem>();
        finishQueue = new LinkedBlockingQueue<FileCacheElem>(15);


        //hashPool = new HashFunctionPool(MAX_HASH_RUNNERS);
        hashThreads = new Thread[hashThreadCnt];

        for (int i = 0; i < lockQueueSize; i++)
        {
            lockQueue.add( new HashReadyLock(i));
        }
       
        startThreads();

        blockBuffer = new ArrayList<byte[]>();
    }

    public byte[] getBlock( int len )
    {
        synchronized( blockBuffer )
        {
            if (len > bigBlockSize)
            {
                blockBuffer.clear();
                bigBlockSize = len;
            }

            if (blockBuffer.isEmpty() || len < bigBlockSize)
            {
                return new byte[len];
            }
            if (blockBuffer.get(0).length == len)
            {
                return blockBuffer.remove(0);
            }
        }
        throw new RuntimeException("BlockCalc Error");
    }
    public void returnBlock( byte[] block )
    {
        synchronized( blockBuffer )
        {
            if (block.length < bigBlockSize)
                return;

            /*if (blockBuffer.isEmpty())
            {
                for (int i = 0; i < 5; i++)
                {
                    blockBuffer.add( new byte[block.length]);
                }
            }*/
            
            if (blockBuffer.size() < MAX_BIGBLOCKS)
            {
                blockBuffer.add(block);
            }
        }
    }

    public static MultiThreadedFileReader MTFRFactory(NetAgentApi api )
    {
        if (api instanceof UnixAgentApi)
            return new UnixMultiThreadedFileReader();
        if (api instanceof WinAgentApi)
            return new WinMultiThreadedFileReader();
        if (api instanceof MacAgentApi)
            return new MacMultiThreadedFileReader();

        throw new RuntimeException("Unknown API Type in MTFRFactory");
    }

    private void startThreads()
    {
        ReadRunner wr = new ReadRunner(this);
        writerThread = new Thread(wr, "ReadRunner");
        writerThread.start();
        for (int i = 0; i < hashThreads.length; i++)
        {
            HashRunner hr = new HashRunner(this);
            hashThreads[i] = new Thread(hr, "HashRunner");
            hashThreads[i].start();
        }
        FinishRunner fr = new FinishRunner(this);
        finishThread = new Thread(fr, "FinishRunner");
        finishThread.start();
    }
    public void shutdown()
    {
        this.keepRunning = false;
        try
        {
            while (isWRunning)
            {
                //using the same sleep duration as writer uses
                Thread.sleep(sleepMilisecondOnEmpty);
            }
        }
        catch (InterruptedException e)
        {            
        }
    }

    public void startFile( NetAgentApi api, RemoteFSElemWrapper wrapper, RemoteFSElem path, int blocksize )
    {
        initQueues();

        long len = 0;

        if (wrapper.isXa())
            len = path.getStreamSize();
        else
            len = path.getDataSize();

        actWrapper = wrapper;

        actPath = path;
        readyElem = null;

        int idx = 0;
        for( long offset = 0; offset < len; offset += blocksize )
        {
            int bs = blocksize;
            if (offset + blocksize > len)
                bs = (int)(len - offset);

            workList.add( new FileCacheElem(api, offset, bs, idx++) );
        }
        if (verbose)
            System.out.println("**** Started with " + path.getName() + " len " + len  + " blocks  " + idx + " HQ:" + hashQueue.size() + " RQ:" + readyQueue.size() + " LQ:" + lockQueue.size() + " ****");
    }

    void initQueues()
    {
        if (!hashQueue.isEmpty())
        {
            System.out.println("Init detected filled hashQueue" );
            hashQueue.clear();
        }
        if (!readyQueue.isEmpty())
        {
            System.out.println("Init detected filled readyQueue" );
            readyQueue.clear();
        }
        if (!workList.isEmpty())
        {
            System.out.println("Init detected filled workList" );
            workList.clear();
        }
        if (!finishQueue.isEmpty())
        {
            System.out.println("Init detected filled finishQueue" );
            finishQueue.clear();
        }

        if (lockQueue.size() != lockQueueSize)
        {
            lockQueue.clear();
        }

        if (lockQueue.isEmpty())
        {
            lockQueue = new LinkedBlockingQueue<HashReadyLock>(Main.CACHE_FILE_FLOCKS);
            for (int i = 0; i < lockQueueSize; i++)
            {
                lockQueue.add( new HashReadyLock(i));
            }
        }

        hashError = false;
        readError = false;
    }

    public void resetQueues()
    {
        if (readyQueue.isEmpty() && workList.isEmpty() && hashQueue.isEmpty() && lockQueue.size() == MAX_HASH_RUNNERS)
            return;

        System.out.println("Resetting cache queues" );
        workList.clear();
        hashQueue.clear();

        while (!readyQueue.isEmpty())
        {
            readyQueue.poll();
        }
        lockQueue.clear();

        initQueues();
    }


    abstract protected void read( byte[] block, FileCacheElem elem);
    void hash( Digest digest, Base64 b64, FileCacheElem elem)
    {
       
        try
        {
            byte[] data = null;

            if (actWrapper != null && actWrapper.isXa())
            {
                data = elem.xa_data;
            }
            else
            {
                data = elem.data;
            }
            if (data == null)
            {
                System.out.println("Null data in hash");
                hashError = true;
                elem.hash = null;
                return;
            }
            
            
            byte[] hash = digest.digest(data);
            elem.hash = encodeUrlsafe( b64, hash);
            
        }
        catch (IOException ex)
        {
            hashError = true;
        }
        
    }

    boolean isCorrectBlock( FileCacheElem elem,  RemoteFSElem path, long offset, int len)
    {
        return (actPath.getPath().equals(path.getPath()) && elem.offset == offset && elem.len == len);
    }

    private FileCacheElem fetchElem(  long offset, int len )
    {
        if (readError || hashError)
        {
            if (readError)
                System.out.println("Read Error");
            if (hashError)
                System.out.println("Hash Error");
            return null;
        }

        // FETCH SAME ELEMENT AGAIN?
        if (readyElem != null && readyElem.offset == offset && readyElem.len == len)
            return readyElem;

        
        while(!hashError && !readError)
        {
           
            try
            {
//                long a = System.currentTimeMillis();
                if (readyElem != null)
                {
                    if (readyElem.data != null)
                        returnBlock(readyElem.data);
                    readyElem.clean();
                }

                readyElem = finishQueue.poll(120000, TimeUnit.MILLISECONDS);
//                long b = System.currentTimeMillis();
//                if (b - a > 30 && !workList.isEmpty())
//                {
//                    System.out.println("Wfetch " + Long.toString(b-a) + " RDYQ: " + readyQueue.size() + " HashQ: " + hashQueue.size() +
//                                " ReadQ: " + workList.size() + " FinishQ: " + finishQueue.size() + " LQ: " + lockQueue.size() );
//                }

                if (readyElem != null)
                {
                      break; // REGULAR EXIT
                }
                System.out.println("Hash has timeout");
                hashError = true;
            }
            catch (InterruptedException interruptedException)
            {
                System.out.println("Interrupt in fetchElem");
                interruptedException.printStackTrace();                
            }
        }
        if (readError || hashError)
        {
            if (readError)
                System.out.println("Read Error");
            if (hashError)
                System.out.println("Hash Error");

            return null;
        }
        if (readyElem.offset != offset)
        {
            System.out.println("Offset mismatch: " + readyElem.offset + "/" + offset);
            return null;
        }
        if ( readyElem.len != len)
        {
            System.out.println("Len mismatch: " + readyElem.len + "/" + len);
            return null;
        }

        //System.out.println(readyElem.idx);
        return readyElem;
    }

    public byte[] getData(long offset, int len )
    {
        FileCacheElem elem = fetchElem(offset, len);
        if (elem == null)
            return null;

        

        return elem.data;
    }
    public byte[] getXAData( long offset, int len )
    {
        FileCacheElem elem = fetchElem(offset, len);
        if (elem == null)
            return null;

        return elem.xa_data;
    }
    public String getHash( long offset, int len )
    {
        FileCacheElem elem = fetchElem( offset, len);
        if (elem == null)
            return null;

        
        
        return elem.hash;
    }


    // CALLED BY WRITER TASK
    void handleRead( byte[] block, FileCacheElem elem )
    {
        try
        {
            read( block, elem );
        }
        catch (Exception interruptedException)
        {
            System.out.println("handleRead exception: " + interruptedException.getMessage());
            interruptedException.printStackTrace();
            readError = true;
        }
    }

    // CALLED BY HASH TASKS
    void handleHash( Digest digest, Base64 b64, FileCacheElem elem )
    {
        incHashCount();
        try
        {            
            hash( digest, b64, elem );
        }
        catch (Exception interruptedException)
        {
            System.out.println("handleHash exception: " + interruptedException.getMessage());
            interruptedException.printStackTrace();
            hashError = true;
        }
        decHashCount();
    }

    int hashCount = 0;
    private void incHashCount()
    {
        hashCount++;
    }

    private void decHashCount()
    {
        hashCount--;        
    }

    public int getActiveHashThreads()
    {
        return hashCount;
    }
    public int getReadQueueLen()
    {
        return workList.size();
    }
    public int getReadyQueueLen()
    {
        return readyQueue.size();
    }

    private String encodeUrlsafe( Base64 b64, byte[] hash ) throws UnsupportedEncodingException
    {
        byte[] b = b64.encode(hash);
        return new String( b,  "UTF8");
    }

    


}
