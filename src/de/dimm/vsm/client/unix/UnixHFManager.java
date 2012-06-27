/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.unix;

import de.dimm.vsm.client.HFManager;
import de.dimm.vsm.client.RemoteFSElemFactory;

/**
 *
 * @author Administrator
 */
public class UnixHFManager extends HFManager
{
    UnixAgentApi api;
    public UnixHFManager(UnixAgentApi api)
    {
        this.api = api;
    }
    @Override
    public RemoteFSElemFactory getFactory()
    {
        return api.getFsFactory();
    }

}
