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
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @author Administrator
 */
public class AttributeContainerImpl 
{
    public static boolean allowUserAttributes = false;

    private static final int SYSLANG_UNDEFINED = -1;
    private static final int SYSLANG_ENGLISH = 0;
    private static final int SYSLANG_GERMAN = 1;
    static int systemLang = SYSLANG_UNDEFINED;

    private static boolean systemIsGerman()
    {
        if (systemLang == SYSLANG_UNDEFINED)
        {
            systemLang = SYSLANG_ENGLISH;

            try
            {
                UserPrincipalLookupService usv = FileSystems.getDefault().getUserPrincipalLookupService();
                UserPrincipal test = usv.lookupPrincipalByName("");
                if (test != null && test.getName().startsWith("VORDEFINIERT"))
                {
                    systemLang = SYSLANG_GERMAN;
                }
            }
            catch (IOException iOException)
            {
            }
        }

        return systemLang == SYSLANG_GERMAN;
    }

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


    public static boolean fill(String npath, AttributeContainer ac )
    {
        FileSystem fs = FileSystems.getDefault();

        Path file = fs.getPath(npath);

        return fill( file, ac );
    }

    
    public static boolean fill(Path file, AttributeContainer ac )
    {
        ac.setUserName( null );
        ac.setUserAttributes( null );
        // acl muss immer != null sein
        List<VSMAclEntry> acl = new ArrayList<>();
        ac.setAcl( acl );

        AclFileAttributeView view = Files.getFileAttributeView(file, AclFileAttributeView.class);
        if (view != null)
        {
            try
            {
                ac.setUserName( view.getOwner().getName() );
                List<AclEntry> _acl = view.getAcl();

                

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


    public static boolean set(String path, AttributeContainer ac )
    {
        FileSystem fs = FileSystems.getDefault();
        Path file = fs.getPath(path);

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

        if (Main.getWinacl() == Main.WINACL.WINACL_SKIP)
        {
            return true;
        }


        AclFileAttributeView view = Files.getFileAttributeView(file, AclFileAttributeView.class);
        if (view != null)
        {
            // IN CASE OF ERROR WE SKIP SETTING ACL, WE COULD END UP IN A NON-ACCESSIBLE FILE/DIR
            boolean skipWrite = false;
            try
            {
                UserPrincipalLookupService usv = FileSystems.getDefault().getUserPrincipalLookupService();
                
                String name = mapPrincipal( ac.getUserName(), systemIsGerman() );
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

                HashMap<String,AclEntry> aclMap = new HashMap<String, AclEntry>();
                if (Main.getWinacl() == Main.WINACL.WINACL_HASH)
                {
                    for (int i = 0; i < realAcls.size(); i++)
                    {
                        AclEntry aclEntry = realAcls.get(i);
                        name = aclEntry.principal().getName();
                        aclMap.put(name, aclEntry);
                    }
                    realAcls.clear();
                }

                if (Main.getWinacl() == Main.WINACL.WINACL_EVERYBODY)
                {

                    String name1 = mapPrincipal( "VORDEFINIERT\\Gäste", systemIsGerman() );
                    String name2 = mapPrincipal( "NT-AUTORITÄT\\Authentifizierte Benutzer", systemIsGerman() );
                    UserPrincipal owner1 = usv.lookupPrincipalByName(name1);
                    UserPrincipal owner2 = usv.lookupPrincipalByName(name2);
                    List<UserPrincipal> userList = new ArrayList<UserPrincipal>();
                    userList.add(owner1);
                    userList.add(owner2);


                    for (int i = 0; i < userList.size(); i++)
                    {
                        UserPrincipal userPrincipal = userList.get(i);


                        AclEntry entry = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(userPrincipal)
                                 .setPermissions(AclEntryPermission.DELETE, AclEntryPermission.READ_DATA,
                                 AclEntryPermission.READ_ATTRIBUTES, AclEntryPermission.WRITE_DATA,
                                 AclEntryPermission.WRITE_ATTRIBUTES, AclEntryPermission.EXECUTE, AclEntryPermission.APPEND_DATA,
                                 AclEntryPermission.READ_NAMED_ATTRS, AclEntryPermission.DELETE_CHILD, AclEntryPermission.SYNCHRONIZE,
                                 AclEntryPermission.READ_NAMED_ATTRS, AclEntryPermission.WRITE_NAMED_ATTRS,
                                 AclEntryPermission.READ_ACL, AclEntryPermission.WRITE_ACL)
                                 .build();

                        if (file.toFile().isDirectory())
                        {
                            entry.permissions().add(AclEntryPermission.LIST_DIRECTORY);
                            entry.flags().add(AclEntryFlag.FILE_INHERIT);
                            entry.flags().add(AclEntryFlag.DIRECTORY_INHERIT);
                        }

                        realAcls.add(entry);
                    }

                }
                else if (acl != null)
                {
                    for (int i = 0; i < acl.size(); i++)
                    {
                        VSMAclEntry aclEntry = acl.get(i);


                        Builder bacl = AclEntry.newBuilder();
                        bacl.setType(aclEntry.type());

                        if (!aclEntry.permissions().isEmpty())
                        {
                            bacl.setPermissions(aclEntry.permissions());
                        }
                        if (!aclEntry.flags().isEmpty())
                        {
                            bacl.setFlags(aclEntry.flags());
                        }


                        name = mapPrincipal( aclEntry.principalName(), systemIsGerman() );
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
                }
                
                // IN HASH MODE WE GUARANTEE SETTING ONLY ONE (THE LAST) ACLENTRY PER PRINCIPALNAME
                if (Main.getWinacl() == Main.WINACL.WINACL_HASH)
                {
                    for (int i = 0; i < realAcls.size(); i++)
                    {
                        AclEntry aclEntry = realAcls.get(i);
                        name = aclEntry.principal().getName();
                        aclMap.put(name, aclEntry);
                    }
                    realAcls = new ArrayList<>( aclMap.values() );
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