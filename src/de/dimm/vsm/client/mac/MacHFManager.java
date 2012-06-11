/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.mac;

import de.dimm.vsm.client.HFManager;

/**
 *
 * @author Administrator
 */
public class MacHFManager extends HFManager
{
    public MacHFManager()
    {
        factory = new MacRemoteFSElemFactory();
    }

}
