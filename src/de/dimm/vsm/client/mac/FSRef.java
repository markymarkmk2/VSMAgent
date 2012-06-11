/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.dimm.vsm.client.mac;

import com.sun.jna.Memory;
import com.sun.jna.PointerType;

/**
 *
 * @author mw
 */
public class FSRef extends PointerType
    {
        
        public FSRef()
        {
            super(new Memory(256));
            
        }
       
    }
