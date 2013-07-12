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
    
    private int maxWriteBlocks = 250;
    private int asyncThresholdPercent = 30;
    private long maxWaitMS = 120*1000;
    private int maxLocalFileThreshold = 1024*1024;
    private String bufferPath = "vfs_buffer";
    private int maxBufferedFiles = 200;              // Achtung nicht zu groß: Das hier muss innerhlab des JDokan/Fuse-Threads abgearbeitet werden!
    private long maxBufferedSize = 50l*(1024*1024);  // Achtung nicht zu groß: Das hier muss innerhlab des JDokan/Fuse-Threads abgearbeitet werden!
    private int maxIdleAgeS = 10;                    // Länger als das bleibt eine Datei nicht im LocalBuffer
    private int flushTimeoutS = 10;                  // Länger als dauert das warten auf den flush nicht
    
    static AgentPreferences prefs = new AgentPreferences();
    
    public static AgentPreferences getPrefs()
    {
        return prefs;
    }
    
    
    public int getAsyncThresholdPercent()
    {
        return asyncThresholdPercent;
    }
    public int getMaxWriteBlocks()
    {
        return maxWriteBlocks;
    }
    public long getMaxWaitMS()
    {
        return maxWaitMS;
    }

    public String getBufferPath()
    {
        return bufferPath;
    }

    public int getMaxBufferedFiles()
    {
        return maxBufferedFiles;
    }

    public long getMaxBufferedSize()
    {
        return maxBufferedSize;
    }

    public long getMaxLocalFileThreshold()
    {
        return maxLocalFileThreshold;
    }

    public int maxIdleAgeS()
    {
        return maxIdleAgeS;
    }

    public int getFlushTimeoutS()
    {
        return flushTimeoutS;
    }
    
}
