/*
 * Copyright 2007-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package java.nio.file.attribute;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * This class consists exclusively of static methods that operate on or return
 * the attributes of files or file stores. These methods provide for convenient
 * use of the {@link AttributeView attribute-views} defined in this package.
 *
 * @since 1.7
 */

public final class Attributes {
    private Attributes() {
    }

    /**
     * Splits the given attribute name into the name of an attribute view and
     * the attribute. If the attribute view is not identified then it assumed
     * to be "basic".
     */
    private static String[] split(String attribute) {
        String[] s = new String[2];
        int pos = attribute.indexOf(':');
        if (pos == -1) {
            s[0] = "basic";
            s[1] = attribute;
        } else {
            s[0] = attribute.substring(0, pos++);
            s[1] = (pos == attribute.length()) ? "" : attribute.substring(pos);
        }
        return s;
    }

    /**
     * Sets the value of a file attribute.
     *
     * <p> The {@code attribute} parameter identifies the attribute to be set
     * and takes the form:
     * <blockquote>
     * [<i>view-name</i><b>:</b>]<i>attribute-name</i>
     * </blockquote>
     * where square brackets [...] delineate an optional component and the
     * character {@code ':'} stands for itself.
     *
     * <p> <i>view-name</i> is the {@link FileAttributeView#name name} of a {@link
     * FileAttributeView} that identifies a set of file attributes. If not
     * specified then it defaults to {@code "basic"}, the name of the file
     * attribute view that identifies the basic set of file attributes common to
     * many file systems. <i>attribute-name</i> is the name of the attribute
     * within the set.
     *
     * <p> <b>Usage Example:</b>
     * Suppose we want to set the DOS "hidden" attribute:
     * <pre>
     *    Attributes.setAttribute(file, "dos:hidden", true);
     * </pre>
     *
     * @param   file
     *          A file reference that locates the file
     * @param   attribute
     *          The attribute to set
     * @param   value
     *          The attribute value
     *
     * @throws  UnsupportedOperationException
     *          If the attribute view is not available or it does not
     *          support updating the attribute
     * @throws  IllegalArgumentException
     *          If the attribute value is of the correct type but has an
     *          inappropriate value
     * @throws  ClassCastException
     *          If the attribute value is not of the expected type or is a
     *          collection containing elements that are not of the expected
     *          type
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, its {@link SecurityManager#checkWrite(String) checkWrite}
     *          method denies write access to the file. If this method is invoked
     *          to set security sensitive attributes then the security manager
     *          may be invoked to check for additional permissions.
     */
    public static void setAttribute(FileRef file, String attribute, Object value)
        throws IOException
    {
        String[] s = split(attribute);
        FileAttributeView view = file.getFileAttributeView(s[0]);
        if (view == null)
            throw new UnsupportedOperationException("View '" + s[0] + "' not available");
        view.setAttribute(s[1], value);
    }

    /**
     * Reads the value of a file attribute.
     *
     * <p> The {@code attribute} parameter identifies the attribute to be read
     * and takes the form:
     * <blockquote>
     * [<i>view-name</i><b>:</b>]<i>attribute-name</i>
     * </blockquote>
     * where square brackets [...] delineate an optional component and the
     * character {@code ':'} stands for itself.
     *
     * <p> <i>view-name</i> is the {@link FileAttributeView#name name} of a {@link
     * FileAttributeView} that identifies a set of file attributes. If not
     * specified then it defaults to {@code "basic"}, the name of the file
     * attribute view that identifies the basic set of file attributes common to
     * many file systems. <i>attribute-name</i> is the name of the attribute.
     *
     * <p> The {@code options} array may be used to indicate how symbolic links
     * are handled for the case that the file is a symbolic link. By default,
     * symbolic links are followed and the file attribute of the final target
     * of the link is read. If the option {@link LinkOption#NOFOLLOW_LINKS
     * NOFOLLOW_LINKS} is present then symbolic links are not followed and so
     * the method returns the file attribute of the symbolic link.
     *
     * <p> <b>Usage Example:</b>
     * Suppose we require the user ID of the file owner on a system that
     * supports a "{@code unix}" view:
     * <pre>
     *    int uid = (Integer)Attributes.getAttribute(file, "unix:uid");
     * </pre>
     *
     * @param   file
     *          A file reference that locates the file
     * @param   attribute
     *          The attribute to read
     * @param   options
     *          Options indicating how symbolic links are handled
     *
     * @return  The attribute value, or {@code null} if the attribute view
     *          is not available or it does not support reading the attribute
     *
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, its {@link SecurityManager#checkRead(String) checkRead}
     *          method denies read access to the file. If this method is invoked
     *          to read security sensitive attributes then the security manager
     *          may be invoked to check for additional permissions.
     */
    public static Object getAttribute(FileRef file,
                                      String attribute,
                                      LinkOption... options)
        throws IOException
    {
        String[] s = split(attribute);
        FileAttributeView view = file.getFileAttributeView(s[0], options);
        if (view != null)
            return view.getAttribute(s[1]);
        // view not available
        return null;
    }

    /**
     * Reads a set of file attributes as a bulk operation.
     *
     * <p> The {@code attributes} parameter identifies the attributes to be read
     * and takes the form:
     * <blockquote>
     * [<i>view-name</i><b>:</b>]<i>attribute-list</i>
     * </blockquote>
     * where square brackets [...] delineate an optional component and the
     * character {@code ':'} stands for itself.
     *
     * <p> <i>view-name</i> is the {@link FileAttributeView#name name} of a {@link
     * FileAttributeView} that identifies a set of file attributes. If not
     * specified then it defaults to {@code "basic"}, the name of the file
     * attribute view that identifies the basic set of file attributes common to
     * many file systems.
     *
     * <p> The <i>attribute-list</i> component is a comma separated list of
     * zero or more names of attributes to read. If the list contains the value
     * {@code "*"} then all attributes are read. Attributes that are not supported
     * are ignored and will not be present in the returned map. It is
     * implementation specific if all attributes are read as an atomic operation
     * with respect to other file system operations.
     *
     * <p> The following examples demonstrate possible values for the {@code
     * attributes} parameter:
     *
     * <blockquote>
     * <table border="0">
     * <tr>
     *   <td> {@code "*"} </td>
     *   <td> Read all {@link BasicFileAttributes basic-file-attributes}. </td>
     * </tr>
     * <tr>
     *   <td> {@code "size,lastModifiedTime,lastAccessTime"} </td>
     *   <td> Reads the file size, last modified time, and last access time
     *     attributes. </td>
     * </tr>
     * <tr>
     *   <td> {@code "posix:*"} </td>
     *   <td> Read all {@link PosixFileAttributes POSIX-file-attributes}.. </td>
     * </tr>
     * <tr>
     *   <td> {@code "posix:permissions,owner,size"} </td>
     *   <td> Reads the POSX file permissions, owner, and file size. </td>
     * </tr>
     * </table>
     * </blockquote>
     *
     * <p> The {@code options} array may be used to indicate how symbolic links
     * are handled for the case that the file is a symbolic link. By default,
     * symbolic links are followed and the file attributes of the final target
     * of the link are read. If the option {@link LinkOption#NOFOLLOW_LINKS
     * NOFOLLOW_LINKS} is present then symbolic links are not followed and so
     * the method returns the file attributes of the symbolic link.
     *
     * @param   file
     *          A file reference that locates the file
     * @param   attributes
     *          The attributes to read
     * @param   options
     *          Options indicating how symbolic links are handled
     *
     * @return  A map of the attributes returned; may be empty. The map's keys
     *          are the attribute names, its values are the attribute values
     *
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, its {@link SecurityManager#checkRead(String) checkRead}
     *          method denies read access to the file. If this method is invoked
     *          to read security sensitive attributes then the security manager
     *          may be invoke to check for additional permissions.
     */
    public static Map<String,?> readAttributes(FileRef file,
                                               String attributes,
                                               LinkOption... options)
        throws IOException
    {
        String[] s = split(attributes);
        FileAttributeView view = file.getFileAttributeView(s[0], options);
        if (view != null) {
            // further split attributes into the first and rest.
            String[] names = s[1].split(",");
            int rem = names.length-1;
            String first = names[0];
            String[] rest = new String[rem];
            if (rem > 0) System.arraycopy(names, 1, rest, 0, rem);

            return view.readAttributes(first, rest);
        }
        // view not available
        return Collections.emptyMap();
    }

    /**
     * Reads the basic file attributes of a file.
     *
     * <p> The {@code options} array may be used to indicate how symbolic links
     * are handled for the case that the file is a symbolic link. By default,
     * symbolic links are followed and the file attributes of the final target
     * of the link are read. If the option {@link LinkOption#NOFOLLOW_LINKS
     * NOFOLLOW_LINKS} is present then symbolic links are not followed and so
     * the method returns the file attributes of the symbolic link. This option
     * should be used where there is a need to determine if a file is a
     * symbolic link:
     * <pre>
     *    boolean isSymbolicLink = Attributes.readBasicFileAttributes(file, NOFOLLOW_LINKS).isSymbolicLink();
     * </pre>
     *
     * <p> It is implementation specific if all file attributes are read as an
     * atomic operation with respect to other file system operations.
     *
     * @param   file
     *          A file reference that locates the file
     * @param   options
     *          Options indicating how symbolic links are handled
     *
     * @return  The basic file attributes
     *
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, the security manager's {@link
     *          SecurityManager#checkRead(String) checkRead} method is invoked
     *          to check read access to file
     *
     * @see BasicFileAttributeView#readAttributes
     */
    public static BasicFileAttributes readBasicFileAttributes(FileRef file,
                                                              LinkOption... options)
        throws IOException
    {
        return file.getFileAttributeView(BasicFileAttributeView.class, options)
            .readAttributes();
    }

    /**
     * Reads the POSIX file attributes of a file.
     *
     * <p> The {@code file} parameter locates a file that supports the {@link
     * PosixFileAttributeView}. This file attribute view provides access to a
     * subset of the file attributes commonly associated with files on file
     * systems used by operating systems that implement the Portable Operating
     * System Interface (POSIX) family of standards. It is implementation
     * specific if all file attributes are read as an atomic operation with
     * respect to other file system operations.
     *
     * <p> The {@code options} array may be used to indicate how symbolic links
     * are handled for the case that the file is a symbolic link. By default,
     * symbolic links are followed and the file attributes of the final target
     * of the link are read. If the option {@link LinkOption#NOFOLLOW_LINKS
     * NOFOLLOW_LINKS} is present then symbolic links are not followed and so
     * the method returns the file attributes of the symbolic link.
     *
     * @param   file
     *          A file reference that locates the file
     * @param   options
     *          Options indicating how symbolic links are handled
     *
     * @return  The POSIX file attributes
     *
     * @throws  UnsupportedOperationException
     *          If the {@code PosixFileAttributeView} is not available
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, it denies {@link RuntimePermission}<tt>("accessUserInformation")</tt>
     *          or its {@link SecurityManager#checkRead(String) checkRead} method
     *          denies read access to the file.
     *
     * @see PosixFileAttributeView#readAttributes
     */
    public static PosixFileAttributes readPosixFileAttributes(FileRef file,
                                                              LinkOption... options)
        throws IOException
    {
        PosixFileAttributeView view =
            file.getFileAttributeView(PosixFileAttributeView.class, options);
        if (view == null)
            throw new UnsupportedOperationException();
        return view.readAttributes();
    }

    /**
     * Reads the DOS file attributes of a file.
     *
     * <p> The {@code file} parameter locates a file that supports the {@link
     * DosFileAttributeView}. This file attribute view provides access to
     * legacy "DOS" attributes supported by the file systems such as File
     * Allocation Table (FAT), commonly used in <em>consumer devices</em>. It is
     * implementation specific if all file attributes are read as an atomic
     * operation with respect to other file system operations.
     *
     * <p> The {@code options} array may be used to indicate how symbolic links
     * are handled for the case that the file is a symbolic link. By default,
     * symbolic links are followed and the file attributes of the final target
     * of the link are read. If the option {@link LinkOption#NOFOLLOW_LINKS
     * NOFOLLOW_LINKS} is present then symbolic links are not followed and so
     * the method returns the file attributes of the symbolic link.
     *
     * @param   file
     *          A file reference that locates the file
     * @param   options
     *          Options indicating how symbolic links are handled
     *
     * @return  The DOS file attributes
     *
     * @throws  UnsupportedOperationException
     *          If the {@code DosFileAttributeView} is not available
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, the security manager's {@link
     *          SecurityManager#checkRead(String) checkRead} method is invoked
     *          to check read access to file
     *
     * @see DosFileAttributeView#readAttributes
     */
    public static DosFileAttributes readDosFileAttributes(FileRef file,
                                                          LinkOption... options)
        throws IOException
    {
        DosFileAttributeView view =
            file.getFileAttributeView(DosFileAttributeView.class, options);
        if (view == null)
            throw new UnsupportedOperationException();
        return view.readAttributes();
    }

    /**
     * Returns the owner of a file.
     *
     * <p> The {@code file} parameter locates a file that supports the {@link
     * FileOwnerAttributeView}. This file attribute view provides access to
     * a file attribute that is the owner of the file.
     *
     * @param   file
     *          A file reference that locates the file
     *
     * @return  A user principal representing the owner of the file
     *
     * @throws  UnsupportedOperationException
     *          If the {@code FileOwnerAttributeView} is not available
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, it denies {@link RuntimePermission}<tt>("accessUserInformation")</tt>
     *          or its {@link SecurityManager#checkRead(String) checkRead} method
     *          denies read access to the file.
     *
     * @see FileOwnerAttributeView#getOwner
     */
    public static UserPrincipal getOwner(FileRef file) throws IOException {
        FileOwnerAttributeView view =
            file.getFileAttributeView(FileOwnerAttributeView.class);
        if (view == null)
            throw new UnsupportedOperationException();
        return view.getOwner();
    }

    /**
     * Updates the file owner.
     *
     * <p> The {@code file} parameter locates a file that supports the {@link
     * FileOwnerAttributeView}. This file attribute view provides access to
     * a file attribute that is the owner of the file.
     *
     * @param   file
     *          A file reference that locates the file
     * @param   owner
     *          The new file owner
     *
     * @throws  UnsupportedOperationException
     *          If the {@code FileOwnerAttributeView} is not available
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, it denies {@link RuntimePermission}<tt>("accessUserInformation")</tt>
     *          or its {@link SecurityManager#checkWrite(String) checkWrite}
     *          method denies write access to the file.
     *
     * @see FileOwnerAttributeView#setOwner
     */
    public static void setOwner(FileRef file, UserPrincipal owner)
            throws IOException
    {
        FileOwnerAttributeView view =
            file.getFileAttributeView(FileOwnerAttributeView.class);
        if (view == null)
            throw new UnsupportedOperationException();
        view.setOwner(owner);
    }

    /**
     * Reads a file's Access Control List (ACL).
     *
     * <p> The {@code file} parameter locates a file that supports the {@link
     * AclFileAttributeView}. This file attribute view provides access to ACLs
     * based on the ACL model specified in
     *  <a href="http://www.ietf.org/rfc/rfc3530.txt"><i>RFC&nbsp;3530</i></a>.
     *
     * @param   file
     *          A file reference that locates the file
     *
     * @return  An ordered list of {@link AclEntry entries} representing the
     *          ACL. The returned list is modifiable.
     *
     * @throws  UnsupportedOperationException
     *          If the {@code AclAttributeView} is not available
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, it denies {@link RuntimePermission}<tt>("accessUserInformation")</tt>
     *          or its {@link SecurityManager#checkRead(String) checkRead} method
     *          denies read access to the file.
     *
     * @see AclFileAttributeView#getAcl
     */
    public static List<AclEntry> getAcl(FileRef file) throws IOException {
        AclFileAttributeView view =
            file.getFileAttributeView(AclFileAttributeView.class);
        if (view == null)
            throw new UnsupportedOperationException();
        return view.getAcl();
    }

    /**
     * Updates a file's Access Control List (ACL).
     *
     * <p> The {@code file} parameter locates a file that supports the {@link
     * AclFileAttributeView}. This file attribute view provides access to ACLs
     * based on the ACL model specified in
     *  <a href="http://www.ietf.org/rfc/rfc3530.txt"><i>RFC&nbsp;3530</i></a>.
     *
     * @param   file
     *          A file reference that locates the file
     * @param   acl
     *          The new file ACL
     *
     * @throws  UnsupportedOperationException
     *          If the {@code AclFileAttributeView} is not available
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, it denies {@link RuntimePermission}<tt>("accessUserInformation")</tt>
     *          or its {@link SecurityManager#checkWrite(String) checkWrite}
     *          method denies write access to the file.
     *
     * @see AclFileAttributeView#setAcl
     */
    public static void setAcl(FileRef file, List<AclEntry> acl)
        throws IOException
    {
        AclFileAttributeView view =
            file.getFileAttributeView(AclFileAttributeView.class);
        if (view == null)
            throw new UnsupportedOperationException();
        view.setAcl(acl);
    }

    /**
     * Updates the value of a file's last modified time attribute.
     *
     * <p> The time value is measured since the epoch
     * (00:00:00 GMT, January 1, 1970) and is converted to the precision supported
     * by the file system. Converting from finer to coarser granularities result
     * in precision loss.
     *
     * <p> If the file system does not support a last modified time attribute then
     * this method has no effect.
     *
     * @param   file
     *          A file reference that locates the file
     *
     * @param   lastModifiedTime
     *          The new last modified time, or {@code -1L} to update it to
     *          the current time
     * @param   unit
     *          A {@code TimeUnit} determining how to interpret the
     *          {@code lastModifiedTime} parameter
     *
     * @throws  IllegalArgumentException
     *          If the {@code lastModifiedime} parameter is a negative value other
     *          than {@code -1L}
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, the security manager's {@link
     *          SecurityManager#checkWrite(String) checkWrite} method is invoked
     *          to check write access to file
     *
     * @see BasicFileAttributeView#setTimes
     */
    public static void setLastModifiedTime(FileRef file,
                                           long lastModifiedTime,
                                           TimeUnit unit)
        throws IOException
    {
        file.getFileAttributeView(BasicFileAttributeView.class)
            .setTimes(lastModifiedTime, null, null, unit);
    }

    /**
     * Updates the value of a file's last access time attribute.
     *
     * <p> The time value is measured since the epoch
     * (00:00:00 GMT, January 1, 1970) and is converted to the precision supported
     * by the file system. Converting from finer to coarser granularities result
     * in precision loss.
     *
     * <p> If the file system does not support a last access time attribute then
     * this method has no effect.
     *
     * @param   lastAccessTime
     *          The new last access time, or {@code -1L} to update it to
     *          the current time
     * @param   unit
     *          A {@code TimeUnit} determining how to interpret the
     *          {@code lastModifiedTime} parameter
     *
     * @throws  IllegalArgumentException
     *          If the {@code lastAccessTime} parameter is a negative value other
     *          than {@code -1L}
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, the security manager's {@link
     *          SecurityManager#checkWrite(String) checkWrite} method is invoked
     *          to check write access to file
     *
     * @see BasicFileAttributeView#setTimes
     */
    public static void setLastAccessTime(FileRef file,
                                         long lastAccessTime,
                                         TimeUnit unit)
        throws IOException
    {
        file.getFileAttributeView(BasicFileAttributeView.class)
            .setTimes(null, lastAccessTime, null, unit);
    }

    /**
     * Sets a file's POSIX permissions.
     *
     * <p> The {@code file} parameter is a reference to an existing file. It
     * supports the {@link PosixFileAttributeView} that provides access to file
     * attributes commonly associated with files on file systems used by
     * operating systems that implement the Portable Operating System Interface
     * (POSIX) family of standards.
     *
     * @param   file
     *          A file reference that locates the file
     * @param   perms
     *          The new set of permissions
     *
     * @throws  UnsupportedOperationException
     *          If {@code PosixFileAttributeView} is not available
     * @throws  ClassCastException
     *          If the sets contains elements that are not of type {@code
     *          PosixFilePermission}
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, it denies {@link RuntimePermission}<tt>("accessUserInformation")</tt>
     *          or its {@link SecurityManager#checkWrite(String) checkWrite}
     *          method denies write access to the file.
     *
     * @see PosixFileAttributeView#setPermissions
     */
    public static void setPosixFilePermissions(FileRef file,
                                               Set<PosixFilePermission> perms)
        throws IOException
    {
        PosixFileAttributeView view =
            file.getFileAttributeView(PosixFileAttributeView.class);
        if (view == null)
            throw new UnsupportedOperationException();
        view.setPermissions(perms);
    }

    /**
     * Reads the space attributes of a file store.
     *
     * <p> The {@code store} parameter is a file store that supports the
     * {@link FileStoreSpaceAttributeView} providing access to the space related
     * attributes of the file store. It is implementation specific if all attributes
     * are read as an atomic operation with respect to other file system operations.
     *
     * @param   store
     *          The file store
     *
     * @return  The file store space attributes
     *
     * @throws  UnsupportedOperationException
     *          If the file store space attribute view is not supported
     * @throws  IOException
     *          If an I/O error occurs
     *
     * @see FileStoreSpaceAttributeView#readAttributes()
     */
    public static FileStoreSpaceAttributes readFileStoreSpaceAttributes(FileStore store)
        throws IOException
    {
        FileStoreSpaceAttributeView view =
            store.getFileStoreAttributeView(FileStoreSpaceAttributeView.class);
        if (view == null)
            throw new UnsupportedOperationException();
        return view.readAttributes();
    }
}
