/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client;

import de.dimm.vsm.net.AttributeContainer;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.net.VSMAclEntry;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntry.Builder;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 *
 * @author Administrator
 */
public class AttributeContainerImpl 
{
    public static boolean allowUserAttributes = false;

    // SHOULD THIS BE CONFIGURED SOMEWHERE ELSE?
    static String[] skipUserDefinedAttributes = null;

        static
        {
            if (Main.is_solaris())
            {
                skipUserDefinedAttributes = new String[] {"SUNWattr_ro","SUNWattr_rw"};
            }
            else if(Main.is_win())
            {
                skipUserDefinedAttributes = new String[] {"AFP_AfpInfo","AFP_DeskTop","AFP_IdIndex"};
            }
            // TODO, DETECT OTHER
            else
                skipUserDefinedAttributes = new String[0];
        }


    public static boolean fill(RemoteFSElem elem, AttributeContainer ac )
    {
        FileSystem fs = FileSystems.getDefault();
        Path file = fs.getPath(elem.getPath());

        return fill( file, ac );
    }

    
    public static boolean fill(Path file, AttributeContainer ac )
    {
        ac.setUserName( null );
        ac.setAcl( null );
        ac.setUserAttributes( null );

        AclFileAttributeView view = Files.getFileAttributeView(file, AclFileAttributeView.class);
        if (view != null)
        {
            try
            {
                ac.setUserName( view.getOwner().getName() );
                List<AclEntry> _acl = view.getAcl();

                List<VSMAclEntry> acl = new ArrayList<VSMAclEntry>();

                for (int i = 0; i < _acl.size(); i++)
                {
                    AclEntry aclEntry = _acl.get(i);
                    String name = aclEntry.principal().getName();
                    boolean isGroup = aclEntry.principal() instanceof GroupPrincipal;

                    acl.add( new VSMAclEntry(aclEntry.type(), name , isGroup, aclEntry.permissions(), aclEntry.flags()));
                }
                ac.setAcl(acl);
            }
            catch (Exception iOException)
            {
                System.out.println("Error getting AclFileAttributeView for " + file.toString() + ": " + iOException.getMessage());
                return false;
            }
        }
//
//        UserDefinedFileAttributeView uview = Files.getFileAttributeView(file, UserDefinedFileAttributeView.class);
//        if (allowUserAttributes && uview != null)
//        {
//            try
//            {
//                 List<String>names = uview.list();
//                 HashMap<String,byte[]> m = new HashMap<String,byte[]>();
//                 for (int i = 0; i < names.size(); i++)
//                 {
//                     String name = names.get(i);
//
//                     boolean skip = false;
//                     for (int j = 0; j < skipUserDefinedAttributes.length; j++)
//                     {
//                         String string = skipUserDefinedAttributes[j];
//                         if (string.equals(name))
//                         {
//                             skip = true;
//                             break;
//                         }
//                     }
//                     if (skip)
//                         continue;
//
//                     int len = uview.size(name);
//                     if (len > 0)
//                     {
//                         ByteBuffer buf = ByteBuffer.allocate(len);
//                         uview.read(name, buf);
//                         byte[] b = buf.array();
//                         m.put(name, b);
//                     }
//                 }
//
//                 if (!m.isEmpty())
//                 {
//                    ac.setUserAttributes(m);
//                 }
//            }
//            catch (Exception iOException)
//            {
//                System.out.println("Error getting UserDefinedFileAttributeView for " + file.toString() + ": " + iOException.getMessage());
//                return false;
//            }
//        }
        return true;
    }


    public static boolean set(RemoteFSElem elem, AttributeContainer ac )
    {
        FileSystem fs = FileSystems.getDefault();
        Path file = fs.getPath(elem.getPath());

        return set( file, ac );
    }
    static HashMap<String, String> builtinTranslastionMapg2e;
    static HashMap<String, String> builtinTranslastionMape2g;

    // THIS IS JUST A HACK TO TRY TO RESOLVE DIFFERENT BUILTIN NAMES ON DIFFERENT LOCALIZATIONS
    // THIS IS ABSOLUTE FUCKING STUPID OF MS... NO WAY TO RESOLVE THIS ISSUE FROM INSIDE JAVA
    static private void addToMaps( String e, String g)
    {
        builtinTranslastionMapg2e.put(g.toLowerCase(), e);
        builtinTranslastionMape2g.put(e.toLowerCase(), g);
    }
    static
    {
        builtinTranslastionMapg2e = new HashMap<String, String>();
        builtinTranslastionMape2g = new HashMap<String, String>();

        addToMaps( "BUILTIN\\ADMINISTRATORS", "VORDEFINIERT\\Administratoren");
        addToMaps( "BUILTIN\\USERS", "VORDEFINIERT\\Benutzer");
        addToMaps( "BUILTIN\\GUESTS", "VORDEFINIERT\\Gäste");
        addToMaps( "NT AUTHORITY\\SYSTEM", "NT-AUTORITÄT\\SYSTEM");
        addToMaps( "NT AUTHORITY\\authenticated users", "NT-AUTORITÄT\\Authentifizierte Benutzer");
        addToMaps( "NT AUTHORITY\\LOCAL SERVICE", "NT-AUTORITÄT\\LOKALER DIENST");
    }

    static private String mapPrincipal( String p, boolean systemIsGerman )
    {
        String ret = p;
        if (systemIsGerman)
        {
            String r = builtinTranslastionMape2g.get( p.toLowerCase() );
            if (r != null)
                ret = r;
        }
        else
        {
            String r = builtinTranslastionMapg2e.get( p.toLowerCase() );
            if (r != null)
                ret = r;
        }

        return ret;
    }


    public static boolean set(Path file, AttributeContainer ac )
    {
        // TODO: HANDLE CROSS PLATFORM RESTORE



        AclFileAttributeView view = Files.getFileAttributeView(file, AclFileAttributeView.class);
        if (view != null)
        {
            // IN CASE OF ERROR WE SKIP SETTING ACL, WE COULD END UP IN A NON-ACCESSIBLE FILE/DIR
            boolean skipWrite = false;
            try
            {
                UserPrincipalLookupService usv = FileSystems.getDefault().getUserPrincipalLookupService();

                boolean systemIsGerman = false;

                try
                {
                    UserPrincipal test = usv.lookupPrincipalByName("");
                    if (test != null && test.getName().startsWith("VORDEFINIERT"))
                    {
                        systemIsGerman = true;
                    }
                }
                catch (IOException iOException)
                {
                }

                
                String name = mapPrincipal( ac.getUserName(), systemIsGerman );
                try
                {
                    UserPrincipal owner = usv.lookupPrincipalByName(name);
                    view.setOwner(owner);
                }
                catch(UserPrincipalNotFoundException exc )
                {
                    System.out.println("Unknown owner " + ac.getUserName() + " for " + file.toString());
                    skipWrite = true;
                }
                catch (IOException iOException)
                {
                    System.out.println("Error setting User " + ac.getUserName() + " for " + file.toString() + ": " + iOException.getMessage());
                    skipWrite = true;
                }
                List<VSMAclEntry> acl =ac.getAcl();
                List<AclEntry> realAcls = new ArrayList<AclEntry>(view.getAcl());


                for (int i = 0; i < acl.size(); i++)
                {
                    VSMAclEntry aclEntry = acl.get(i);


                    Builder bacl = AclEntry.newBuilder();
                    bacl.setType(aclEntry.type());
                    
                    bacl.setPermissions(aclEntry.permissions());
                    if (!aclEntry.flags().isEmpty())
                    {
                        bacl.setFlags(aclEntry.flags());
                    }



                    name = mapPrincipal( aclEntry.principalName(), systemIsGerman );
                    try
                    {
                        UserPrincipal aclOwner = null;
                        
                        // TODO: HASH OR EHCACHE
                        if (aclEntry.isGroup())
                        {
                            aclOwner = usv.lookupPrincipalByGroupName(name);
                        }
                        else
                        {
                            aclOwner = usv.lookupPrincipalByName(name);
                        }
                        bacl.setPrincipal(aclOwner);
                    }
                    catch(UserPrincipalNotFoundException exc )
                    {
                        System.out.println("Skipping unknown user " + ac.getUserName() + " for " + file.toString());
                        skipWrite = true;

                        // WITHOUT OWNER WE CANNOT SET ACL
                        continue;
                    }
                    catch (IOException iOException)
                    {
                        System.out.println("Error setting AclOwner " + ac.getUserName() + " for " + file.toString() + ": " + iOException.getMessage());
                        skipWrite = true;

                        // WITHOUT OWNER WE CANNOT SET ACL
                        continue;
                    }


                    AclEntry entry = bacl.build();

                    realAcls.add(entry);
                }

                if (!skipWrite)
                {
                    view.setAcl(realAcls);
                }
            }
            catch (Exception iOException)
            {
                System.out.println("Error setting AclFileAttributeView for " + file.toString() + ": " + iOException.getMessage());
                return false;
            }
        }
//        UserDefinedFileAttributeView uview = Files.getFileAttributeView(file, UserDefinedFileAttributeView.class);
//
//        // TODO: HANDLE CROSS PLATFORM RESTORE
//        if (ac.getUserAttributes() != null && uview != null)
//        {
//            try
//            {
//                //List<String>names = uview.list();
//                Map<String,byte[]> m = ac.getUserAttributes();
//
//                Set<String> keys = m.keySet();
//                for (String key : keys)
//                {
//                     ByteBuffer buf = ByteBuffer.wrap(m.get(key));
//                     uview.write(key, buf);
//                }
//            }
//            catch (Exception iOException)
//            {
//                System.out.println("Error setting UserDefinedFileAttributeView for " + file.toString() + ": " + iOException.getMessage());
//                return false;
//            }
//        }
        return true;
    }


  


}