/*
 * Copyright (c) 2008, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package sun.nio.fs;

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.io.*;
import java.util.*;
import jdk.internal.util.StaticProperty;
import sun.nio.ch.ThreadPool;
import sun.security.util.SecurityConstants;

import static sun.nio.fs.WindowsNativeDispatcher.*;
import static sun.nio.fs.WindowsSecurity.*;
import static sun.nio.fs.WindowsConstants.*;

class WindowsFileSystemProvider
    extends AbstractFileSystemProvider
{
    private static final byte[] EMPTY_PATH = new byte[0];

    private final WindowsFileSystem theFileSystem;

    public WindowsFileSystemProvider() {
        theFileSystem = new WindowsFileSystem(this, StaticProperty.userDir());
    }

    WindowsFileSystem theFileSystem() {
        return theFileSystem;
    }

    @Override
    public String getScheme() {
        return "file";
    }

    private void checkUri(URI uri) {
        if (!uri.getScheme().equalsIgnoreCase(getScheme()))
            throw new IllegalArgumentException("URI does not match this provider");
        if (uri.getRawAuthority() != null)
            throw new IllegalArgumentException("Authority component present");
        String path = uri.getPath();
        if (path == null)
            throw new IllegalArgumentException("Path component is undefined");
        if (!path.equals("/"))
            throw new IllegalArgumentException("Path component should be '/'");
        if (uri.getRawQuery() != null)
            throw new IllegalArgumentException("Query component present");
        if (uri.getRawFragment() != null)
            throw new IllegalArgumentException("Fragment component present");
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String,?> env)
        throws IOException
    {
        checkUri(uri);
        throw new FileSystemAlreadyExistsException();
    }

    @Override
    public final FileSystem getFileSystem(URI uri) {
        checkUri(uri);
        return theFileSystem;
    }

    @Override
    public Path getPath(URI uri) {
        return WindowsUriSupport.fromUri(theFileSystem, uri);
    }

    @Override
    public FileChannel newFileChannel(Path path,
                                      Set<? extends OpenOption> options,
                                      FileAttribute<?>... attrs)
        throws IOException
    {
        if (path == null)
            throw new NullPointerException();
        if (!(path instanceof WindowsPath))
            throw new ProviderMismatchException();
        WindowsPath file = (WindowsPath)path;

        WindowsSecurityDescriptor sd = WindowsSecurityDescriptor.fromAttribute(attrs);
        try {
            return WindowsChannelFactory
                .newFileChannel(file.getPathForWin32Calls(),
                                options,
                                sd.address());
        } catch (WindowsException x) {
            x.rethrowAsIOException(file);
            return null;
        } finally {
            if (sd != null)
                sd.release();
        }
    }

    @Override
    public AsynchronousFileChannel newAsynchronousFileChannel(Path path,
                                                              Set<? extends OpenOption> options,
                                                              ExecutorService executor,
                                                              FileAttribute<?>... attrs)
        throws IOException
    {
        if (path == null)
            throw new NullPointerException();
        if (!(path instanceof WindowsPath))
            throw new ProviderMismatchException();
        WindowsPath file = (WindowsPath)path;
        ThreadPool pool = (executor == null) ? null : ThreadPool.wrap(executor, 0);
        WindowsSecurityDescriptor sd =
            WindowsSecurityDescriptor.fromAttribute(attrs);
        try {
            return WindowsChannelFactory
                .newAsynchronousFileChannel(file.getPathForWin32Calls(),
                                            options,
                                            sd.address(),
                                            pool);
        } catch (WindowsException x) {
            x.rethrowAsIOException(file);
            return null;
        } finally {
            if (sd != null)
                sd.release();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V extends FileAttributeView> V
        getFileAttributeView(Path obj, Class<V> view, LinkOption... options)
    {
        WindowsPath file = WindowsPath.toWindowsPath(obj);
        if (view == null)
            throw new NullPointerException();
        boolean followLinks = Util.followLinks(options);
        if (view == BasicFileAttributeView.class)
            return (V) WindowsFileAttributeViews.createBasicView(file, followLinks);
        if (view == DosFileAttributeView.class)
            return (V) WindowsFileAttributeViews.createDosView(file, followLinks);
        if (view == AclFileAttributeView.class)
            return (V) new WindowsAclFileAttributeView(file, followLinks);
        if (view == FileOwnerAttributeView.class)
            return (V) new FileOwnerAttributeViewImpl(
                new WindowsAclFileAttributeView(file, followLinks));
        if (view == UserDefinedFileAttributeView.class)
            return (V) new WindowsUserDefinedFileAttributeView(file, followLinks);
        return (V) null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A extends BasicFileAttributes> A readAttributes(Path file,
                                                            Class<A> type,
                                                            LinkOption... options)
        throws IOException
    {
        Class<? extends BasicFileAttributeView> view;
        if (type == BasicFileAttributes.class)
            view = BasicFileAttributeView.class;
        else if (type == DosFileAttributes.class)
            view = DosFileAttributeView.class;
        else if (type == null)
            throw new NullPointerException();
        else
            throw new UnsupportedOperationException();
        return (A) getFileAttributeView(file, view, options).readAttributes();
    }

    @Override
    public DynamicFileAttributeView getFileAttributeView(Path obj, String name, LinkOption... options) {
        WindowsPath file = WindowsPath.toWindowsPath(obj);
        boolean followLinks = Util.followLinks(options);
        if (name.equals("basic"))
            return WindowsFileAttributeViews.createBasicView(file, followLinks);
        if (name.equals("dos"))
            return WindowsFileAttributeViews.createDosView(file, followLinks);
        if (name.equals("acl"))
            return new WindowsAclFileAttributeView(file, followLinks);
        if (name.equals("owner"))
            return new FileOwnerAttributeViewImpl(
                new WindowsAclFileAttributeView(file, followLinks));
        if (name.equals("user"))
            return new WindowsUserDefinedFileAttributeView(file, followLinks);
        return null;
    }

    @Override
    public SeekableByteChannel newByteChannel(Path obj,
                                              Set<? extends OpenOption> options,
                                              FileAttribute<?>... attrs)
         throws IOException
    {
        WindowsPath file = WindowsPath.toWindowsPath(obj);
        WindowsSecurityDescriptor sd =
            WindowsSecurityDescriptor.fromAttribute(attrs);
        try {
            return WindowsChannelFactory
                .newFileChannel(file.getPathForWin32Calls(),
                                options,
                                sd.address());
        } catch (WindowsException x) {
            x.rethrowAsIOException(file);
            return null;  // keep compiler happy
        } finally {
            sd.release();
        }
    }

    @Override
    boolean implDelete(Path obj, boolean failIfNotExists) throws IOException {
        WindowsPath file = WindowsPath.toWindowsPath(obj);

        WindowsFileAttributes attrs = null;
        try {
             // need to know if file is a directory or junction
             attrs = WindowsFileAttributes.get(file, false);
             if (attrs.isDirectory() || attrs.isDirectoryLink() ||
                 attrs.isDirectoryJunction()) {
                RemoveDirectory(file.getPathForWin32Calls());
             } else {
                DeleteFile(file.getPathForWin32Calls());
             }
             return true;
        } catch (WindowsException x) {

            // no-op if file does not exist
            if (!failIfNotExists &&
                (x.lastError() == ERROR_FILE_NOT_FOUND ||
                 x.lastError() == ERROR_PATH_NOT_FOUND)) return false;

            if (attrs != null && attrs.isDirectory()) {
                // ERROR_ALREADY_EXISTS is returned when attempting to delete
                // non-empty directory on SAMBA servers.
                if (x.lastError() == ERROR_DIR_NOT_EMPTY ||
                    x.lastError() == ERROR_ALREADY_EXISTS)
                {
                    throw new DirectoryNotEmptyException(
                        file.getPathForExceptionMessage());
                }
            }
            x.rethrowAsIOException(file);
            return false;
        }
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options)
        throws IOException
    {
        WindowsFileCopy.copy(WindowsPath.toWindowsPath(source),
                             WindowsPath.toWindowsPath(target),
                             options);
    }

    @Override
    public void move(Path source, Path target, CopyOption... options)
        throws IOException
    {
        WindowsFileCopy.move(WindowsPath.toWindowsPath(source),
                             WindowsPath.toWindowsPath(target),
                             options);
    }

    /**
     * Checks the file security against desired access.
     */
    private static boolean hasDesiredAccess(WindowsPath file, int rights) throws IOException {
        // read security descriptor containing ACL (symlinks are followed)
        boolean hasRights = false;
        String target = WindowsLinkSupport.getFinalPath(file, true);
        try (NativeBuffer aclBuffer = WindowsAclFileAttributeView
            .getFileSecurity(target,
                DACL_SECURITY_INFORMATION
                | OWNER_SECURITY_INFORMATION
                | GROUP_SECURITY_INFORMATION)) {
            hasRights = checkAccessMask(aclBuffer.address(), rights,
                FILE_GENERIC_READ,
                FILE_GENERIC_WRITE,
                FILE_GENERIC_EXECUTE,
                FILE_ALL_ACCESS);
        } catch (WindowsException exc) {
            exc.rethrowAsIOException(file);
        }
        return hasRights;
    }

    /**
     * Checks if the given file(or directory) exists and is readable.
     */
    private void checkReadAccess(WindowsPath file) throws IOException {
        try {
            Set<OpenOption> opts = Collections.emptySet();
            FileChannel fc = WindowsChannelFactory
                .newFileChannel(file.getPathForWin32Calls(),
                                opts,
                                0L);
            fc.close();
        } catch (WindowsException exc) {
            try {
                if (exc.lastError() == ERROR_CANT_ACCESS_FILE && isUnixDomainSocket(file)) {
                    // socket file is accessible
                    return;
                }
            } catch (WindowsException ignore) {}

            // Windows errors are very inconsistent when the file is a directory
            // (ERROR_PATH_NOT_FOUND returned for root directories for example)
            // so we retry by attempting to open it as a directory.
            try {
                new WindowsDirectoryStream(file, null).close();
            } catch (IOException ioe) {
                // translate and throw original exception
                exc.rethrowAsIOException(file);
            }
        }
    }

    private static boolean isUnixDomainSocket(WindowsPath path) throws WindowsException {
        WindowsFileAttributes attrs = WindowsFileAttributes.get(path, false);
        return attrs.isUnixDomainSocket();
    }

    @Override
    public void checkAccess(Path obj, AccessMode... modes) throws IOException {
        WindowsPath file = WindowsPath.toWindowsPath(obj);

        boolean r = false;
        boolean w = false;
        boolean x = false;
        for (AccessMode mode: modes) {
            switch (mode) {
                case READ : r = true; break;
                case WRITE : w = true; break;
                case EXECUTE : x = true; break;
                default: throw new AssertionError("Should not get here");
            }
        }

        // check file exists only
        if (!(r || w || x)) {
            try {
                WindowsFileAttributes.get(file, true);
                return;
            } catch (WindowsException exc) {
                exc.rethrowAsIOException(file);
            }
        }

        // special-case read access to avoid needing to determine effective
        // access to file
        if (!w && !x) {
            checkReadAccess(file);
            return;
        }

        int mask = 0;
        if (r) {
            mask |= FILE_READ_DATA;
        }
        if (w) {
            mask |= FILE_WRITE_DATA;
        }
        if (x) {
            mask |= FILE_EXECUTE;
        }

        if (!hasDesiredAccess(file, mask))
            throw new AccessDeniedException(
                file.getPathForExceptionMessage(), null,
                "Permissions does not allow requested access");

        // for write access we need to check if the DOS readonly attribute
        // and if the volume is read-only
        if (w) {
            try {
                WindowsFileAttributes attrs = WindowsFileAttributes.get(file, true);
                if (!attrs.isDirectory() && attrs.isReadOnly())
                    throw new AccessDeniedException(
                        file.getPathForExceptionMessage(), null,
                        "DOS readonly attribute is set");
            } catch (WindowsException exc) {
                exc.rethrowAsIOException(file);
            }

            if (WindowsFileStore.create(file).isReadOnly()) {
                throw new AccessDeniedException(
                    file.getPathForExceptionMessage(), null, "Read-only file system");
            }
        }
    }

    /**
     * Contains the attributes of a given file system entry and the open
     * handle from which they were obtained. The handle must remain open
     * until the volume serial number and file index of the attributes
     * are no longer needed for comparison with other attributes.
     *
     * @param attrs  the file system entry attributes
     * @param handle the open Windows file handle
     */
    private record EntryAttributes(WindowsFileAttributes attrs, long handle) {
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (obj instanceof EntryAttributes other) {
                WindowsFileAttributes oattrs = other.attrs();
                return oattrs.volSerialNumber() == attrs.volSerialNumber() &&
                       oattrs.fileIndexHigh()   == attrs.fileIndexHigh() &&
                       oattrs.fileIndexLow()    == attrs.fileIndexLow();
            }
            return false;
        }

        public int hashCode() {
            return attrs.volSerialNumber() +
                   attrs.fileIndexHigh() + attrs.fileIndexLow();
        }
    }

    /**
     * Returns the attributes of the file located by the given path if it is a
     * symbolic link. The handle contained in the returned value must be closed
     * once the attributes are no longer needed.
     *
     * @param path the file system path to examine
     * @return the attributes and handle or null if no link is found
     */
    private EntryAttributes linkAttributes(WindowsPath path)
        throws WindowsException
    {
        long h = INVALID_HANDLE_VALUE;
        try {
            h = path.openForReadAttributeAccess(false);
        } catch (WindowsException x) {
            if (x.lastError() != ERROR_FILE_NOT_FOUND &&
                x.lastError() != ERROR_PATH_NOT_FOUND)
                throw x;
            return null;
        }

        WindowsFileAttributes attrs = null;
        try {
            attrs = WindowsFileAttributes.readAttributes(h);
        } finally {
            if (attrs == null || !attrs.isSymbolicLink()) {
                CloseHandle(h);
                return null;
            }
        }

        return new EntryAttributes(attrs, h);
    }

    /**
     * Returns the attributes of the last symbolic link encountered in the
     * specified path. Links are not resolved in the path taken as a whole,
     * but rather the first link is followed, then its target, and so on,
     * until no more links are encountered.  The handle contained in the
     * returned value must be closed once the attributes are no longer needed.
     *
     * @param path the file system path to examine
     * @return the attributes and handle or null if no links are found
     * @throws FileSystemLoopException if a symbolic link cycle is encountered
     */
    private EntryAttributes lastLinkAttributes(WindowsPath path)
        throws IOException, WindowsException
    {
        var linkAttrs = new LinkedHashSet<EntryAttributes>();
        try {
            while (path != null) {
                EntryAttributes linkAttr = linkAttributes(path);
                if (linkAttr == null)
                    break;

                if (!linkAttrs.add(linkAttr)) {
                    // the element was not added to the set so close its handle
                    // here as it would not be closed in the finally block
                    CloseHandle(linkAttr.handle());
                    throw new FileSystemLoopException(path.toString());
                }

                String target = WindowsLinkSupport.readLink(path, linkAttr.handle());
                path = WindowsPath.parse(path.getFileSystem(), target);
            }

            if (!linkAttrs.isEmpty())
                return linkAttrs.removeLast();
        } finally {
            linkAttrs.stream().forEach(la -> CloseHandle(la.handle()));
        }

        return null;
    }

    /**
     * Returns the attributes of the file located by the supplied parameter
     * with all symbolic links in its path resolved. If the file located by
     * the resolved path does not exist, then null is returned. The handle
     * contained in the returned value must be closed once the attributes
     * are no longer needed.
     *
     * @param path the file system path to examine
     * @return the attributes and handle or null if the real path does not exist
     */
    private EntryAttributes realPathAttributes(WindowsPath path)
        throws WindowsException
    {
        long h;
        try {
            h = path.openForReadAttributeAccess(true);
        } catch (WindowsException x) {
            if (x.lastError() == ERROR_FILE_NOT_FOUND ||
                x.lastError() == ERROR_PATH_NOT_FOUND ||
                x.lastError() == ERROR_CANT_RESOLVE_FILENAME)
                return null;

            throw x;
        }

        WindowsFileAttributes attrs = null;
        try {
            attrs = WindowsFileAttributes.readAttributes(h);
        } catch (WindowsException x) {
            CloseHandle(h);
            throw x;
        }

        return new EntryAttributes(attrs, h);
    }

    @Override
    public boolean isSameFile(Path obj1, Path obj2) throws IOException {
        // toWindowsPath verifies its argument is a non-null WindowsPath
        WindowsPath file1 = WindowsPath.toWindowsPath(obj1);
        if (file1.equals(obj2))
            return true;
        if (obj2 == null)
            throw new NullPointerException();
        if (!(obj2 instanceof WindowsPath file2))
            return false;

        EntryAttributes attrs1 = null;
        EntryAttributes attrs2 = null;
        WindowsPath pathForException = file1;
        try {
            if ((attrs1 = realPathAttributes(file1)) != null ||
                (attrs1 = lastLinkAttributes(file1)) != null) {
                pathForException = file2;
                if ((attrs2 = realPathAttributes(file2)) != null ||
                    (attrs2 = lastLinkAttributes(file2)) != null)
                    return attrs1.equals(attrs2);
            }
        } catch (WindowsException x) {
            x.rethrowAsIOException(pathForException);
        } finally {
            if (attrs1 != null) {
                CloseHandle(attrs1.handle());
                if (attrs2 != null)
                    CloseHandle(attrs2.handle());
            }
        }

        return false;
    }

    @Override
    public boolean isHidden(Path obj) throws IOException {
        WindowsPath file = WindowsPath.toWindowsPath(obj);
        WindowsFileAttributes attrs = null;
        try {
            attrs = WindowsFileAttributes.get(file, true);
        } catch (WindowsException x) {
            x.rethrowAsIOException(file);
        }
        return attrs.isHidden();
    }

    @Override
    public FileStore getFileStore(Path obj) throws IOException {
        WindowsPath file = WindowsPath.toWindowsPath(obj);
        return WindowsFileStore.create(file);
    }


    @Override
    public void createDirectory(Path obj, FileAttribute<?>... attrs)
        throws IOException
    {
        WindowsPath dir = WindowsPath.toWindowsPath(obj);
        WindowsSecurityDescriptor sd = WindowsSecurityDescriptor.fromAttribute(attrs);
        try {
            CreateDirectory(dir.getPathForWin32Calls(), sd.address());
        } catch (WindowsException x) {
            // convert ERROR_ACCESS_DENIED to FileAlreadyExistsException if we can
            // verify that the directory exists
            if (x.lastError() == ERROR_ACCESS_DENIED) {
                try {
                    if (WindowsFileAttributes.get(dir, false).isDirectory())
                        throw new FileAlreadyExistsException(dir.toString());
                } catch (WindowsException ignore) { }
            }
            x.rethrowAsIOException(dir);
        } finally {
            sd.release();
        }
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path obj, DirectoryStream.Filter<? super Path> filter)
        throws IOException
    {
        WindowsPath dir = WindowsPath.toWindowsPath(obj);
        if (filter == null)
            throw new NullPointerException();
        return new WindowsDirectoryStream(dir, filter);
    }

    @Override
    public void createSymbolicLink(Path obj1, Path obj2, FileAttribute<?>... attrs)
        throws IOException
    {
        WindowsPath link = WindowsPath.toWindowsPath(obj1);
        WindowsPath target = WindowsPath.toWindowsPath(obj2);

        // no attributes allowed
        if (attrs.length > 0) {
            WindowsSecurityDescriptor.fromAttribute(attrs);  // may throw NPE or UOE
            throw new UnsupportedOperationException("Initial file attributes" +
                "not supported when creating symbolic link");
        }

        /**
         * Throw I/O exception for the drive-relative case because Windows
         * creates a link with the resolved target for this case.
         */
        if (target.type() == WindowsPathType.DRIVE_RELATIVE) {
            throw new IOException("Cannot create symbolic link to working directory relative target");
        }

        /*
         * Windows treats symbolic links to directories differently than it
         * does to other file types. For that reason we need to check if the
         * target is a directory (or a directory junction).
         */
        WindowsPath resolvedTarget;
        if (target.type() == WindowsPathType.RELATIVE) {
            WindowsPath parent = link.getParent();
            resolvedTarget = (parent == null) ? target : parent.resolve(target);
        } else {
            resolvedTarget = link.resolve(target);
        }
        int flags = 0;
        try {
            WindowsFileAttributes wattrs = WindowsFileAttributes.get(resolvedTarget, false);
            if (wattrs.isDirectory() || wattrs.isDirectoryLink())
                flags |= SYMBOLIC_LINK_FLAG_DIRECTORY;
        } catch (WindowsException x) {
            // unable to access target so assume target is not a directory
        }

        // create the link
        try {
            WindowsLinkSupport.createSymbolicLink(link.getPathForWin32Calls(),
                                                  WindowsPath.addPrefixIfNeeded(target.toString()),
                                                  flags);
        } catch (WindowsException x) {
            if (x.lastError() == ERROR_INVALID_REPARSE_DATA) {
                x.rethrowAsIOException(link, target);
            } else {
                x.rethrowAsIOException(link);
            }
        }
    }

    @Override
    public void createLink(Path obj1, Path obj2) throws IOException {
        WindowsPath link = WindowsPath.toWindowsPath(obj1);
        WindowsPath existing = WindowsPath.toWindowsPath(obj2);

        // create hard link
        try {
            CreateHardLink(link.getPathForWin32Calls(),
                           existing.getPathForWin32Calls());
        } catch (WindowsException x) {
            x.rethrowAsIOException(link, existing);
        }
    }

    @Override
    public Path readSymbolicLink(Path obj1) throws IOException {
        WindowsPath link = WindowsPath.toWindowsPath(obj1);
        WindowsFileSystem fs = link.getFileSystem();

        String target = WindowsLinkSupport.readLink(link);
        return WindowsPath.createFromNormalizedPath(fs, target);
    }

    @Override
    public byte[] getSunPathForSocketFile(Path obj) {
        WindowsPath file = WindowsPath.toWindowsPath(obj);
        String s = file.toString();
        return s.isEmpty() ? EMPTY_PATH : s.getBytes(StandardCharsets.UTF_8);
    }

}
