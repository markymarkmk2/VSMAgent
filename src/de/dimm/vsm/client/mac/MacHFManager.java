/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.mac;

import de.dimm.vsm.client.HFManager;
import de.dimm.vsm.client.RemoteFSElemFactory;

/**
 *
 * @author Administrator
 */
public class MacHFManager extends HFManager
{
    MacRemoteFSElemFactory factory;
    public MacHFManager()
    {
        factory = new MacRemoteFSElemFactory();
    }

    @Override
    public RemoteFSElemFactory getFactory()
    {
        return factory;
    }



}
