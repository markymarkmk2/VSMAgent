/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.win;

import de.dimm.vsm.client.HFManager;
import de.dimm.vsm.records.HotFolder;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 *
 * @author Administrator
 */
public class WinHFManager extends HFManager
{

    public WinHFManager()
    {
        factory = new WinRemoteFSElemFactory();
    }

}
