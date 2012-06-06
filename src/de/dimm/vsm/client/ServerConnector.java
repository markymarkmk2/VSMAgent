/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client;

import de.dimm.vsm.net.RemoteCallFactory;
import de.dimm.vsm.net.interfaces.ServerApi;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;


class ServerTicket
{
    InetAddress adress;
    int port;
    ServerApi api;

    public ServerTicket( InetAddress adress, int port )
    {
        this.adress = adress;
        this.port = port;
        this.api = null;
    }


    @Override
    public boolean equals( Object obj )
    {
        if ( obj instanceof ServerTicket)
        {
            ServerTicket t = (ServerTicket) obj;
            if (t.adress.equals(adress) && t.port == port)
                return true;

            return false;
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode()
    {
        int hash = adress.hashCode();
        return hash;
    }


}
/**
 *
 * @author Administrator
 */
public class ServerConnector
{
    public static final String path = "net";
    public static final String keyStore = "vsmkeystore2.jks";
    public static final String keyPwd = "123456";

    List<ServerTicket> serverList;

    public ServerConnector()
    {
        serverList = new ArrayList<ServerTicket>();
    }

    public ServerApi connect( InetAddress adress, int port, boolean ssl, boolean tcp )
    {
        ServerTicket tk = new ServerTicket(adress, port);
        int idx = serverList.indexOf(tk);
        try
        {
            if (idx >= 0)
            {
                tk = serverList.remove(idx);

                tk.api = generate_api(adress, port, ssl, path, keyStore, keyPwd, tcp);
                
                serverList.add(tk);
                return tk.api;
            }
            else
            {
                tk.api = generate_api(adress, port, ssl, path, keyStore, keyPwd, tcp);
                
                serverList.add(tk);
                return tk.api;
            }
        }
        catch (Exception e)
        {
            System.out.println("Connect to Server " + adress.toString() + " failed: " + e.toString());
        }
        return null;
    }
    
    public ServerApi getServerApi( InetAddress adress, int port, boolean ssl, boolean tcp )
    {
        ServerTicket tk = new ServerTicket(adress, port);
        int idx = serverList.indexOf(tk);
        if (idx >= 0)
            return serverList.get(idx).api;

        return connect( adress, port, ssl, tcp );
    }

    public void invalidateConnect(InetAddress adress, int port )
    {
        ServerTicket tk = new ServerTicket(adress, port);
        int idx = serverList.indexOf(tk);
        if (idx >= 0)
        {
            tk = serverList.remove(idx);
            //tk.api.close();
        }
    }


    private static ServerApi generate_api( InetAddress adress, int port, boolean ssl, String path, String keystore, String keypwd, boolean tcp )
    {
        ServerApi api = null;
        System.setProperty("javax.net.ssl.trustStore", keystore);

        try
        {
            RemoteCallFactory factory = new RemoteCallFactory(adress, port, path, ssl, tcp);

            api = (ServerApi) factory.create(ServerApi.class);
        }
        catch (MalformedURLException malformedURLException)
        {
            System.out.println("Err: " + malformedURLException.getMessage());
        }
        return api;
    }

}
