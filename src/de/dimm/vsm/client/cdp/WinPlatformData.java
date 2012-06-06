/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.cdp;

/**
 *
 * @author Administrator
 */
public class WinPlatformData implements PlatformData
{


    @Override
    public char getDelim()
    {
        return '\\';
    }

    @Override
    public String[] getSkipRoots()
    {
        return skip_cdp_roots;
    }
    @Override
    public String[] getSkipFiles()
    {
        return skip_cdp_files;
    }
    @Override
    public String[] getSkipPaths()
    {
        return skip_cdp_files;
    }

    String skip_cdp_roots[] =
    {
        "System Volume Information",
        "RECYCLER"
    };

    String skip_cdp_files[] =
    {
        "agent.log",
        "syncsrv.log",
        "pagefile.sys",
        "hiberfil.sys"
    };
    String skip_cdp_paths[] =
    {
        "SyncServer/Logs"
    };
}