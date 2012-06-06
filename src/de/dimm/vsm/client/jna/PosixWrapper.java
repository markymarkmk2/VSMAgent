/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.jna;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import org.jruby.ext.posix.POSIX;
import org.jruby.ext.posix.POSIX.ERRORS;
import org.jruby.ext.posix.POSIXFactory;
import org.jruby.ext.posix.POSIXHandler;

/**
 *
 * @author Administrator
 */
public class PosixWrapper implements POSIXHandler
{

    private static POSIX posix;
    static private void init()
    {
        PosixWrapper wrapper = new PosixWrapper();

        posix = POSIXFactory.getPOSIX(wrapper, /*useNativePOSIX*/ true);

      

    }


    static public POSIX getPosix()
    {
        if (posix == null)
        {
            init();
        }
        return posix;
    }

    public void error( ERRORS error, String extraData )
    {
        System.out.println("Err:  " + error.name() + " " + extraData);
    }

    public void unimplementedError( String methodName )
    {
        System.out.println("Not implemented yet:" + methodName);
    }

    public void warn( WARNING_ID id, String message, Object... data )
    {
        System.out.println("Warn: " + id.name() + " " + message );
    }

    public boolean isVerbose()
    {
        return true;
    }

    public File getCurrentWorkingDirectory()
    {
        return new File(".").getParentFile();
    }

    public String[] getEnv()
    {
        return null;
    }

    public InputStream getInputStream()
    {
        return System.in;
    }

    public PrintStream getOutputStream()
    {
        return System.out;
    }

    public int getPID()
    {
        try
        {
            String n = ManagementFactory.getRuntimeMXBean().getName();
            String[] args = n.split("@");
            if (args.length == 2)
            {
                return Integer.parseInt(args[0]);
            }
        }
        catch (NumberFormatException numberFormatException)
        {
        }
        return -1;
    }

    public PrintStream getErrorStream()
    {
        return System.err;
    }

}
