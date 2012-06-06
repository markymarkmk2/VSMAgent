/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package de.dimm.vsm.client.unix;

import de.dimm.vsm.client.Main;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

// JVM-ACL

/*
 *
 *


 *
 * File:
 Owner: zfs.admin
a-15000-bi:WRITE_ATTRIBUTES/DELETE/READ_NAMED_ATTRS/READ_ATTRIBUTES/READ_DATA/APPEND_DATA/DELETE_CHILD/WRITE_NAMED_ATTRS/READ_ACL/EXECUTE/WRITE_DATA/WRITE_OWNER:FILE_INHERIT/DIRECTORY_INHERIT:ALLOW
u-15000-bi:WRITE_ATTRIBUTES/DELETE/READ_NAMED_ATTRS/READ_ATTRIBUTES/READ_DATA/APPEND_DATA/DELETE_CHILD/WRITE_NAMED_ATTRS/READ_ACL/EXECUTE/WRITE_DATA/WRITE_OWNER:FILE_INHERIT/DIRECTORY_INHERIT:ALLOW
a-15042-Abu_Dhabi_Mar:WRITE_ATTRIBUTES/DELETE/READ_NAMED_ATTRS/READ_ATTRIBUTES/READ_DATA/APPEND_DATA/DELETE_CHILD/WRITE_NAMED_ATTRS/READ_ACL/EXECUTE/WRITE_DATA/WRITE_OWNER:FILE_INHERIT/DIRECTORY_INHERIT:ALLOW
u-15042-Abu_Dhabi_Mar:WRITE_ATTRIBUTES/DELETE/READ_NAMED_ATTRS/READ_ATTRIBUTES/READ_DATA/APPEND_DATA/DELETE_CHILD/WRITE_NAMED_ATTRS/READ_ACL/EXECUTE/WRITE_DATA/WRITE_OWNER:FILE_INHERIT/DIRECTORY_INHERIT:ALLOW
zfs.admin:WRITE_ATTRIBUTES/DELETE/READ_ATTRIBUTES/WRITE_ACL/READ_ACL/READ_NAMED_ATTRS/READ_DATA/DELETE_CHILD/APPEND_DATA/WRITE_NAMED_ATTRS/WRITE_DATA/EXECUTE/SYNCHRONIZE/WRITE_OWNER:FILE_INHERIT/DIRECTORY_INHERIT:ALLOW
OWNER@:WRITE_ATTRIBUTES/READ_NAMED_ATTRS/READ_ATTRIBUTES/READ_DATA/APPEND_DATA/WRITE_NAMED_ATTRS/WRITE_ACL/READ_ACL/EXECUTE/WRITE_DATA/WRITE_OWNER/SYNCHRONIZE:ALLOW
GROUP@:READ_NAMED_ATTRS/READ_ATTRIBUTES/READ_DATA/READ_ACL/EXECUTE/SYNCHRONIZE:ALLOW
EVERYONE@:READ_NAMED_ATTRS/READ_ATTRIBUTES/READ_ACL/SYNCHRONIZE:ALLOW

*
 * Dir:
 Owner: zfs.admin
a-15000-bi:READ_NAMED_ATTRS/READ_ATTRIBUTES/READ_DATA/READ_ACL/EXECUTE:NO_PROPAGATE_INHERIT/DIRECTORY_INHERIT:ALLOW
u-15000-bi:READ_NAMED_ATTRS/READ_ATTRIBUTES/READ_DATA/READ_ACL/EXECUTE:NO_PROPAGATE_INHERIT/DIRECTORY_INHERIT:ALLOW
a-15042-Abu_Dhabi_Mar:READ_NAMED_ATTRS/READ_ATTRIBUTES/READ_DATA/READ_ACL/EXECUTE:NO_PROPAGATE_INHERIT/DIRECTORY_INHERIT:ALLOW
u-15042-Abu_Dhabi_Mar:READ_NAMED_ATTRS/READ_ATTRIBUTES/READ_DATA/READ_ACL/EXECUTE:NO_PROPAGATE_INHERIT/DIRECTORY_INHERIT:ALLOW
OWNER@:WRITE_ATTRIBUTES/READ_NAMED_ATTRS/READ_ATTRIBUTES/READ_DATA/APPEND_DATA/WRITE_NAMED_ATTRS/WRITE_ACL/READ_ACL/EXECUTE/WRITE_DATA/WRITE_OWNER/SYNCHRONIZE:ALLOW
GROUP@:READ_NAMED_ATTRS/READ_ATTRIBUTES/READ_DATA/READ_ACL/EXECUTE/SYNCHRONIZE:ALLOW
EVERYONE@:READ_NAMED_ATTRS/READ_ATTRIBUTES/READ_ACL/SYNCHRONIZE:ALLOW
*
 *
 */

/**
 *
 * @author Administrator
 */
public class ACLTest
{

    public static void test() throws IOException
    {
        FileSystem fs = FileSystems.getDefault();
        Path file = fs.getPath("Z:\\unittestdata\\a\\AudioBurnerTool.exe");
        if (Main.is_solaris())
            file = fs.getPath("/home/mw/VSM/InstallAgent/start_agent.sh");
        if (Main.is_osx())
            file = fs.getPath("/Users/mw/Desktop/VSM/InstallAgent/start_agent.sh");
        UserPrincipal joe = fs.getUserPrincipalLookupService().lookupPrincipalByName("mw");

     
        BasicFileAttributes ba = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
     
     PosixFileAttributes pa = Files.readAttributes(file, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
     
     // get view
     AclFileAttributeView view = Files.getFileAttributeView(file, AclFileAttributeView.class);

     if (view != null)
     {
     UserPrincipal test = view.getOwner();
     String name = view.name();
     String val = view.toString();


     // create ACE to give "joe" read access
     AclEntry entry = AclEntry.newBuilder()
         .setType(AclEntryType.ALLOW)
         .setPrincipal(joe)
         .setPermissions(AclEntryPermission.READ_DATA, AclEntryPermission.READ_ATTRIBUTES)
         .build();

     // read ACL, insert ACE, re-write ACL
     List<AclEntry> acl = view.getAcl();
    /* acl.add(0, entry);   // insert before any DENY entries
     view.setAcl(acl);
*/
        }

        try
        {
            printMap(Files.readAttributes(file, "acl:acl", LinkOption.NOFOLLOW_LINKS));
            printMap(Files.readAttributes(file, "acl:owner", LinkOption.NOFOLLOW_LINKS));
        }
        catch (Exception iOException)
        {
            System.out.println(iOException.getMessage());
        }
     //Set<PosixFilePermission>set = Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS);
     printMap( Files.readAttributes(file, "*", LinkOption.NOFOLLOW_LINKS) );
     
     
     UserDefinedFileAttributeView uv =  Files.getFileAttributeView(file, UserDefinedFileAttributeView.class);
     if (uv != null)
     {
         List<String>names = uv.list();
         for (int i = 0; i < names.size(); i++)
         {
             String string = names.get(i);
             ByteBuffer bb = ByteBuffer.allocate( uv.size(string));
             uv.read(string, bb);
             System.out.println("UV: " + string + " Size " + bb.capacity());

         }
     }

    }

    static void printMap( Map<String,Object> map )
    {
        Set<Entry<String,Object>> set = map.entrySet();
        for (Entry<String,Object> e : set)
        {
            System.out.print("Key: " + e.getKey() );

            if (e.getValue() != null)
            {
                System.out.println( " Class: " + e.getValue().getClass().getName() + " Value: " + e.getValue().toString());
            }
            else
            {
                System.out.println("");
            }
        }
    }
}
