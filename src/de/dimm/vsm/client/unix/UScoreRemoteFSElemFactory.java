/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.unix;

import java.io.File;


/**
 *
 * @author Administrator
 */
public class UScoreRemoteFSElemFactory extends NetatalkRemoteFSElemFactory
{


    
    @Override
    public String getXAPath( String path )
    {
        StringBuilder sb = new StringBuilder(path);
        int fidx = sb.lastIndexOf(File.separator);
        if (fidx > 0)
            sb.insert(fidx, "._");

        return sb.toString();
    }


    @Override
    public boolean mkDir( File f )
    {
        boolean ret = f.mkdir();
        return ret;
    }

   
}
