/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.cdp;

/**
 *
 * @author Administrator
 */
public interface PlatformData
{
    public char getDelim();
    public String[] getSkipRoots();
    public String[] getSkipFiles();
    public String[] getSkipPaths();

}
