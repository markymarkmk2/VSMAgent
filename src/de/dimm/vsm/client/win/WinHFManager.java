/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.win;

import de.dimm.vsm.client.HFManager;
import de.dimm.vsm.client.RemoteFSElemFactory;

/**
 *
 * @author Administrator
 */
public class WinHFManager extends HFManager
{

    WinRemoteFSElemFactory factory;

    public WinHFManager()
    {
        factory = new WinRemoteFSElemFactory();
    }

    @Override
    public RemoteFSElemFactory getFactory()
    {
        return factory;
    }



}
