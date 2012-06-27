/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.mac;

import de.dimm.vsm.client.mac.MacRemoteFSElemFactory.Attrlist;
import de.dimm.vsm.net.AttributeContainer;
import de.dimm.vsm.net.RemoteFSElem;
import de.dimm.vsm.net.VSMAclEntry;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntry.Builder;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Administrator
 */
public class MacAttributeContainerImpl
{

    public static boolean fill(RemoteFSElem elem, AttributeContainer ac )
    {
        File file = new File( elem.getPath() );
        

        return fill( file, ac );
    }


    public static boolean fill(File file, AttributeContainer ac )
    {
        ac.setUserName( null );
        ac.setAcl( null );
        ac.setUserAttributes( null );

        int             err;
         Attrlist      attrList = new Attrlist();
         ByteBuffer  buff = MacRemoteFSElemFactory.allocByteBuffer(4096);



         attrList.bitmapcount = MacRemoteFSElemFactory.ATTR_BIT_MAP_COUNT;
         attrList.commonattr  = MacRemoteFSElemFactory.ATTR_CMN_FNDRINFO | MacRemoteFSElemFactory.ATTR_CMN_EXTENDED_SECURITY;

         err = MacRemoteFSElemFactory.getattrlist(file.getAbsolutePath(), attrList, buff, buff.limit(), 0);

         if (err == 0)
         {

             System.out.println("Finder information for " +  file.getAbsolutePath());

             int length = buff.getInt(0);

             // 4 BYTE LEN, 32 BYTE FndrInfo
             // 8 Byte Attrreference:
             /*
              struct
             {
               int32 offset
               int32 len
             }
              *
              */

             int acl_attr_dataoffset = buff.getInt(36);
             int acl_attr_len = buff.getInt(40);

             int objType = buff.getInt(4);

             byte[] arr = buff.array();
             String type = new String( arr, 8, 4 );
             String creator = new String( arr, 12, 4 );
             System.out.println("Object type " +  objType);
             System.out.println("Type / creator " +  type + " " + creator);

         }


        return true;
    }


    public static boolean set(RemoteFSElem elem, AttributeContainer ac )
    {
        FileSystem fs = FileSystems.getDefault();
        Path file = fs.getPath(elem.getPath());

        return set( file, ac );
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


                String name =ac.getUserName();
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



                    name =aclEntry.principalName();
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

        return true;
    }


}
