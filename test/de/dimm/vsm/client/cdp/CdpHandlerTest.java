/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.cdp;

import java.io.File;
import de.dimm.vsm.net.CdpEvent;
import de.dimm.vsm.net.CdpTicket;
import de.dimm.vsm.net.interfaces.CDPEventProcessor;
import de.dimm.vsm.net.ExclListEntry;
import de.dimm.vsm.net.RemoteFSElem;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedList;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Administrator
 */
public class CdpHandlerTest {

    public CdpHandlerTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

   


    class TestFCEEventSource implements FCEEventSource
    {

        @Override
        public FceEvent acceptEvent( CdpTicket ticket )
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void open( CDP_Param cdp_param ) throws SocketException
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void close( CdpTicket ticket )
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }

      
    }
    class TestCDPEventProcessor implements CDPEventProcessor
    {

        @Override
        public boolean process( CdpEvent ev )
        {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }
    
    CdpHandlerImpl createImpl()
    {
        CDP_Param param = new CDP_Param(null, 0, false, true, null, null, new WinPlatformData());
        
        CdpHandlerImpl impl = new CdpHandlerImpl(param, new TestFCEEventSource(), new TestCDPEventProcessor());
        
        return impl;
    }

    public class CdpHandlerImpl extends CdpHandler
    {
        public CdpHandlerImpl(CDP_Param p,  FCEEventSource eventSource, CDPEventProcessor eventProcessor)
        {
            super(null, p, eventSource, eventProcessor);
        }

        @Override
        public boolean init_cdp()
        {
            return true;
        }

        @Override
        boolean check_invalid_cdp_path( RemoteFSElem fullpath, String object_name )
        {
            if (object_name.startsWith("ignorename"))
                return true;
            
            if (fullpath.getPath().startsWith("ignorepath"))
                return true;
            
            return false;
        }
    }

/*
 *
 * Event Coalescing:
 *
 *
 * In worklist are only directories, we call a sync for each directory in worklist if it has no activity for more
 * than FCE_SETTLE_MS
 *
 * Rule 1:
 * If we have a single Directory Creation FCE Event
 *   Add FCE-Event Sync-Dir-Recursive and delete all existing Events under this path
 *
 * Rule 2:
 * If we have a single Directory Delete FCE Event
 *   Add FCE-Event Delete-Dir-Recursive and delete all existing Events under this path
 *
 * Rule 3:
 * If we have a single FCE Event (File/Dir) and no pending Events in worklist
 *   Add FCE-Event SyncDir for parent and kick actual Event
 *
 * Rule 4:
 * If we have a single FCE Event (File/Dir) and we find Parent SyncDir in worklist
 *   Kick actual Event
 *
 * Rule 5:
 * If we have a single FCE Event (File/Dir) and we find a Recursive-Parent Dir in out Path
 *   Kick actual Event
 *
 * Rule 6:
 * If we have a single FCE Event (File/Dir) and no other Rule fires
 *   Add FCE-Event SyncDir for parent and kick actual Event

 * Rule 7:
 * If Queue goes into overrun
 *   Add FCE-Event FullSync, clear working queue and kick actual Event
 *
 * Rule 7:
 * If Queue goes into overrun
 *   Add FCE-Event FullSync, clear working queue and kick actual Event
 *
 * Rule 8:
 * If we have a single FCE Event (File/Dir) and the one and only entry in Workqueue is FullSync
 *   kick actual Event
 *
 */
   

    /**
     * Test of coalesceEvents method, of class CdpHandler.
     */
    @Test
    public void testCoalesceEvents()
    {
        int event_id = 0;
        System.out.println("coalesceEvents");
        LinkedList<FceEvent> newList = new LinkedList<FceEvent>();

        LinkedList<CdpEvent> workList = new LinkedList<CdpEvent>();
        CdpTicket ticket = new CdpTicket(1, 1, 1, 1);
        try
        {
            FceEvent ev;
            byte version= 0;
            InetAddress adr = InetAddress.getLocalHost();
            CDP_Param p = new CDP_Param(adr, 0, false, true, new RemoteFSElem(new File(".")), ticket, new WinPlatformData());
            CdpHandler instance = new CdpHandlerImpl(null, null, null);


            // RULE 3
            ev =  new FceEvent(adr, version, FceEvent.FCE_FILE_MODIFY, event_id++, "\\opt\\data\\test".getBytes());
            instance.coalesceEvent(ev, workList);
            assertEquals(1, workList.size());
            assertEquals(CdpEvent.CDP_SYNC_DIR, workList.get(0).getMode());
            // WE ASSUME ROOT DIR BECAUSE coalesceEvent WILL TRAVERSE UP TILL ROOT BECAUSE OPT AND DATA ARE NOT EXISTANT
            assertEquals("\\", workList.get(0).getPath());


            // RULE 4
            ev =  new FceEvent(adr, version, FceEvent.FCE_FILE_MODIFY, event_id++, "\\opt\\data\\tust".getBytes());
            instance.coalesceEvent(ev, workList);
            assertEquals(1, workList.size());

            // RULE 1
            ev =  new FceEvent(adr, version, FceEvent.FCE_DIR_CREATE, event_id++, "\\opt\\data\\tast".getBytes());
            instance.coalesceEvent(ev, workList);
            assertEquals(2, workList.size());
            assertEquals(CdpEvent.CDP_SYNC_DIR, workList.get(1).getMode());

            // RULE 5
            ev =  new FceEvent(adr, version, FceEvent.FCE_DIR_CREATE, event_id++, "\\opt\\data\\tast\\data33".getBytes());
            instance.coalesceEvent(ev, workList);
            ev =  new FceEvent(adr, version, FceEvent.FCE_FILE_CREATE, event_id++, "\\opt\\data\\tast\\data33\\f1".getBytes());
            instance.coalesceEvent(ev, workList);
            ev =  new FceEvent(adr, version, FceEvent.FCE_FILE_MODIFY, event_id++, "\\opt\\data\\tast\\data33\\f2".getBytes());
            instance.coalesceEvent(ev, workList);
            assertEquals(1, workList.size());

            // RULE 2
            ev =  new FceEvent(adr, version, FceEvent.FCE_DIR_DELETE, event_id++, "\\opt".getBytes());
            instance.coalesceEvent(ev, workList);            
            assertEquals(1, workList.size());
            assertEquals(CdpEvent.CDP_SYNC_DIR, workList.get(0).getMode());
        }
        catch (UnknownHostException unknownHostException)
        {
            fail(unknownHostException.getLocalizedMessage());
        }
        // TODO review the generated test code and remove the default call to fail.
        
    }



}