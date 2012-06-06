/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.mac;

import de.dimm.vsm.client.unix.*;
import de.dimm.vsm.client.cdp.PlatformData;

/**
 *
 * @author Administrator
 */
public class MacPlatformData implements PlatformData
{
    @Override
    public char getDelim()
    {
        return '/';
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
        return skip_cdp_paths;
    }

    String skip_cdp_roots[] =
    {
        "dev",
        "tmp",
        "proc",
        "devices"
    };

    String skip_cdp_files[] =
    {
        "pagefile.sys",
        "hiberfil.sys"
    };
    String skip_cdp_paths[] =
    {
        "syncsrv/Logs"
    };
}