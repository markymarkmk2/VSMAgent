/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client;

import de.dimm.vsm.net.RemoteFSElem;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;

/**
 *
 * @author Administrator
 */

class HFEntry
{
    String path;
    long youngestFileStamp;
    long size;

    public HFEntry( )
    {
        this.youngestFileStamp = 0;
    }

    public void setPath( String path )
    {
        this.path = path;
    }

    public void setYoungestFileStamp( long youngestFileStamp, String path, long size )
    {
        this.youngestFileStamp = youngestFileStamp;
        this.path = path;
        this.size = size;
    }
    

}

public abstract class HFManager
{
   
    HashMap<String, HFEntry> hfMap = new HashMap<String, HFEntry>();

    public abstract RemoteFSElemFactory getFactory();

    public RemoteFSElem checkHotfolder( RemoteFSElem mountPath, long getSetttleTime_s, final String filter, boolean onlyFiles, boolean onlyDirs, int itemIdx )
    {
        if (mountPath == null)
            return null;

        String path = mountPath.getPath();

        HFEntry newHfEntry = new HFEntry();
        

        File hff = new File( path );
        if (!hff.exists())
            return null;
        if (!hff.isDirectory())
            return null;

        
        FilenameFilter ff = null;

        if (filter != null && !filter.isEmpty())
        {
            ff = new FilenameFilter()
            {
                @Override
                public boolean accept( File dir, String name )
                {
                    return name.matches(filter);
                }
            };
        }

        File[] list = hff.listFiles(ff);
        if (list.length == 0)
            return null;


        for (int i = 0; i < list.length; i++)
        {
            File file = list[i];
            if (file.isDirectory() && onlyFiles)
                continue;
            if (file.isFile() && onlyDirs)
                continue;

            String hashPath = file.getAbsolutePath();

            if (checkHotfolder( getSetttleTime_s, file, newHfEntry ))
            {
                if (itemIdx > 0)
                    continue;

                // DO WE HAVE CHACHED ENTRY ?
                HFEntry lastHfEntry = hfMap.get(hashPath);
                if (lastHfEntry == null)
                {
                    // NO
                    hfMap.put(hashPath , newHfEntry);
                    return null;
                }

                // YES BUT HAS IT CHANGED?
                if (lastHfEntry.youngestFileStamp != newHfEntry.youngestFileStamp ||
                        lastHfEntry.size != newHfEntry.size)
                {
                    // YES, LEAVE
                    hfMap.remove(hashPath);
                    hfMap.put(hashPath, newHfEntry);
                    return null;
                }

                hfMap.remove(hashPath);
                RemoteFSElem ret = getFactory().create_elem(file,/*withAcl*/ true);
                return ret;
            }
        }
        return null;
    }

    private boolean checkHotfolder( long getSetttleTime_s, File file, HFEntry entry )
    {
        long now = System.currentTimeMillis();
        boolean ret = true;

        RemoteFSElem  elem = getFactory().create_elem(file,/*withAcl*/ true);

        long newest_ts = elem.getCtimeMs();
        if (newest_ts < elem.getMtimeMs())
            newest_ts = elem.getMtimeMs();

        if (file.isFile() && newest_ts > entry.youngestFileStamp)
        {
            entry.setYoungestFileStamp( newest_ts, file.getAbsolutePath(), file.length() );
        }

        // TOO YOUNG ?
        if (newest_ts > now - getSetttleTime_s * 1000)
            return false;

        if (file.isDirectory())
        {
            File[] list = file.listFiles();

            for (int i = 0; i < list.length; i++)
            {
                File child = list[i];

                if (!checkHotfolder(getSetttleTime_s, child, entry))
                    return false;
            }
        }
        else
        {
            // CHECK FOR ALL FILES WHICH ARE BIGGER THAN 1M AND NEWER THAN 2h IF THEY ARE FULLY ADRESSABLE
            if (file.length() > 1000*1000)
            {
                RandomAccessFile raf = null;
                try
                {
                    raf = new RandomAccessFile(file, "r");
                    raf.seek(file.length() - 1);
                    byte b = raf.readByte();

                }
                catch (Exception iOException)
                {
                    // STILL COPYING OR CURRUPT
                    return false;
                }
                finally
                {
                    try
                    {
                        raf.close();
                    }
                    catch (IOException iOException)
                    {
                    }
                }
            }
        }

        // NOTHING WRONG WITH IT, GO AHEAD
        return true;
    }
}
