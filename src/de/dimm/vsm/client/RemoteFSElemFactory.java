/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client;

import de.dimm.vsm.net.RemoteFSElem;
import java.io.File;

/**
 *
 * @author Administrator
 */
public interface RemoteFSElemFactory {

    RemoteFSElem create_elem( File fh, boolean lazyAclInfo );
    public String readAclInfo( RemoteFSElem elem );
    String getFsName( String path );


}
