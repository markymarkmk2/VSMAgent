/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.dimm.vsm.client;

/**
 *
 * @author Administrator
 */
public class AgentPreferences
{
    
    public static final int MAX_WRITE_BLOCKS = 250;
    public static final int ASYNC_THRESHOLD_PERCENT = 30;
    public static final long MAX_WAIT_MS = 120*1000;
    
    
    public static int getAsyncThresholdPercent()
    {
        return ASYNC_THRESHOLD_PERCENT;
    }
    public static int getMaxWriteBlocks()
    {
        return MAX_WRITE_BLOCKS;
    }
    public static long getMaxWaitMS()
    {
        return MAX_WAIT_MS;
    }
    
}
