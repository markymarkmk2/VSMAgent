/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.unix;

import de.dimm.vsm.client.HFManager;

/**
 *
 * @author Administrator
 */
public class UnixHFManager extends HFManager
{
    public UnixHFManager()
    {
        factory = new NetatalkRemoteFSElemFactory();
    }

}
