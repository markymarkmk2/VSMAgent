/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.dimm.vsm.client.vfs;

import static de.dimm.vsm.client.vfs.BufferedEventProcessor.BFL_SUFFIX;
import static de.dimm.vsm.client.vfs.BufferedEventProcessor.DATA_SUFFIX;
import de.dimm.vsm.net.RemoteFSElem;
import java.io.Serializable;

/**
 *
 * @author Administrator
 */
public class BufferFileListEntry implements Serializable
{
    RemoteFSElem fseNode;
    String file;  
    boolean writtenComplete;   
    long creationTime;

    public BufferFileListEntry( RemoteFSElem fseNode, String file )
    {
        this.fseNode = fseNode;
        this.file = file;
        creationTime = System.currentTimeMillis();
    }

    public String getDataFile()
    {
        return file + DATA_SUFFIX;
    }
    public String getEntryFile()
    {
        return file + BFL_SUFFIX;
    }

    public RemoteFSElem getFseNode()
    {
        return fseNode;
    }

    public boolean isWrittenComplete()
    {
        return writtenComplete;
    }

    public void setWrittenComplete( boolean writtenComplete )
    {
        this.writtenComplete = writtenComplete;
    }    

    public long getCreationTime()
    {
        return creationTime;
    }
    
}