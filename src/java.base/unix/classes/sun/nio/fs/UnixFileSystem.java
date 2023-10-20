/*
 * Copyright (c) 2008, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.LinkOption;
import java.nio.file.LinkPermission;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import jdk.internal.misc.Blocker;
import sun.nio.ch.DirectBuffer;
import sun.nio.ch.IOStatus;
import sun.security.action.GetPropertyAction;
import static sun.nio.fs.UnixConstants.*;
import static sun.nio.fs.UnixNativeDispatcher.*;

/**
 * Base implementation of FileSystem for Unix-like implementations.
 */

abstract class UnixFileSystem
    extends FileSystem
{
    // minimum size of a temporary direct buffer
    private static final int MIN_BUFFER_SIZE = 16384;

    // whether direct copying is supported on this platform
    private static volatile boolean directCopyNotSupported;

    private final UnixFileSystemProvider provider;
    private final byte[] defaultDirectory;
    private final boolean needToResolveAgainstDefaultDirectory;
    private final UnixPath rootDirectory;

    // package-private
    UnixFileSystem(UnixFileSystemProvider provider, String dir) {
        this.provider = provider;
        this.defaultDirectory = Util.toBytes(UnixPath.normalizeAndCheck(dir));
        if (this.defaultDirectory[0] != '/') {
            throw new RuntimeException("default directory must be absolute");
        }

        // if process-wide chdir is allowed or default directory is not the
        // process working directory then paths must be resolved against the
        // default directory.
        String propValue = GetPropertyAction
                .privilegedGetProperty("sun.nio.fs.chdirAllowed", "false");
        boolean chdirAllowed = propValue.isEmpty() ? true : Boolean.parseBoolean(propValue);
        if (chdirAllowed) {
            this.needToResolveAgainstDefaultDirectory = true;
        } else {
            byte[] cwd = UnixNativeDispatcher.getcwd();
            boolean defaultIsCwd = (cwd.length == defaultDirectory.length);
            if (defaultIsCwd) {
                for (int i=0; i<cwd.length; i++) {
                    if (cwd[i] != defaultDirectory[i]) {
                        defaultIsCwd = false;
                        break;
                    }
                }
            }
            this.needToResolveAgainstDefaultDirectory = !defaultIsCwd;
        }

        // the root directory
        this.rootDirectory = new UnixPath(this, "/");
    }

    // package-private
    byte[] defaultDirectory() {
        return defaultDirectory;
    }

    boolean needToResolveAgainstDefaultDirectory() {
        return needToResolveAgainstDefaultDirectory;
    }

    boolean isCaseInsensitiveAndPreserving() {
        return false;
    }

    UnixPath rootDirectory() {
        return rootDirectory;
    }

    static List<String> standardFileAttributeViews() {
        return Arrays.asList("basic", "posix", "unix", "owner");
    }

    @Override
    public final FileSystemProvider provider() {
        return provider;
    }

    @Override
    public final String getSeparator() {
        return "/";
    }

    @Override
    public final boolean isOpen() {
        return true;
    }

    @Override
    public final boolean isReadOnly() {
        return false;
    }

    @Override
    public final void close() throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * Copies non-POSIX attributes from the source to target file.
     *
     * Copying a file preserving attributes, or moving a file, will preserve
     * the file owner/group/permissions/timestamps but it does not preserve
     * other non-POSIX attributes. This method is invoked by the
     * copy or move operation to preserve these attributes. It should copy
     * extended attributes, ACLs, or other attributes.
     *
     * @param   sfd
     *          Open file descriptor to source file
     * @param   tfd
     *          Open file descriptor to target file
     */
    void copyNonPosixAttributes(int sfd, int tfd) {
        // no-op by default
    }

    /**
     * Unix systems only have a single root directory (/)
     */
    @Override
    public final Iterable<Path> getRootDirectories() {
        final List<Path> allowedList = List.of(rootDirectory);
        return new Iterable<>() {
            public Iterator<Path> iterator() {
                try {
                    @SuppressWarnings("removal")
                    SecurityManager sm = System.getSecurityManager();
                    if (sm != null)
                        sm.checkRead(rootDirectory.toString());
                    return allowedList.iterator();
                } catch (SecurityException x) {
                    return Collections.emptyIterator(); //disallowed
                }
            }
        };
    }

    /**
     * Returns object to iterate over entries in mounttab or equivalent
     */
    abstract Iterable<UnixMountEntry> getMountEntries();

    /**
     * Returns a FileStore to represent the file system for the given mount
     * mount.
     */
    abstract FileStore getFileStore(UnixMountEntry entry) throws IOException;

    /**
     * Iterator returned by getFileStores method.
     */
    private class FileStoreIterator implements Iterator<FileStore> {
        private final Iterator<UnixMountEntry> entries;
        private FileStore next;

        FileStoreIterator() {
            this.entries = getMountEntries().iterator();
        }

        private FileStore readNext() {
            assert Thread.holdsLock(this);
            for (;;) {
                if (!entries.hasNext())
                    return null;
                UnixMountEntry entry = entries.next();

                // skip entries with the "ignore" option
                if (entry.isIgnored())
                    continue;

                // check permission to read mount point
                @SuppressWarnings("removal")
                SecurityManager sm = System.getSecurityManager();
                if (sm != null) {
                    try {
                        sm.checkRead(Util.toString(entry.dir()));
                    } catch (SecurityException x) {
                        continue;
                    }
                }
                try {
                    return getFileStore(entry);
                } catch (IOException ignore) {
                    // ignore as per spec
                }
            }
        }

        @Override
        public synchronized boolean hasNext() {
            if (next != null)
                return true;
            next = readNext();
            return next != null;
        }

        @Override
        public synchronized FileStore next() {
            if (next == null)
                next = readNext();
            if (next == null) {
                throw new NoSuchElementException();
            } else {
                FileStore result = next;
                next = null;
                return result;
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public final Iterable<FileStore> getFileStores() {
        @SuppressWarnings("removal")
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            try {
                sm.checkPermission(new RuntimePermission("getFileStoreAttributes"));
            } catch (SecurityException se) {
                return Collections.emptyList();
            }
        }
        return new Iterable<>() {
            public Iterator<FileStore> iterator() {
                return new FileStoreIterator();
            }
        };
    }

    @Override
    public final Path getPath(String first, String... more) {
        Objects.requireNonNull(first);
        String path;
        if (more.length == 0) {
            path = first;
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(first);
            for (String segment: more) {
                if (!segment.isEmpty()) {
                    if (sb.length() > 0)
                        sb.append('/');
                    sb.append(segment);
                }
            }
            path = sb.toString();
        }
        return new UnixPath(this, path);
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndInput) {
        int pos = syntaxAndInput.indexOf(':');
        if (pos <= 0)
            throw new IllegalArgumentException();
        String syntax = syntaxAndInput.substring(0, pos);
        String input = syntaxAndInput.substring(pos+1);

        String expr;
        if (syntax.equalsIgnoreCase(GLOB_SYNTAX)) {
            expr = Globs.toUnixRegexPattern(input);
        } else {
            if (syntax.equalsIgnoreCase(REGEX_SYNTAX)) {
                expr = input;
            } else {
                throw new UnsupportedOperationException("Syntax '" + syntax +
                    "' not recognized");
            }
        }

        // return matcher
        final Pattern pattern = compilePathMatchPattern(expr);

        return new PathMatcher() {
            @Override
            public boolean matches(Path path) {
                return pattern.matcher(path.toString()).matches();
            }
        };
    }

    private static final String GLOB_SYNTAX = "glob";
    private static final String REGEX_SYNTAX = "regex";

    @Override
    public final UserPrincipalLookupService getUserPrincipalLookupService() {
        return LookupService.instance;
    }

    private static class LookupService {
        static final UserPrincipalLookupService instance =
            new UserPrincipalLookupService() {
                @Override
                public UserPrincipal lookupPrincipalByName(String name)
                    throws IOException
                {
                    return UnixUserPrincipals.lookupUser(name);
                }

                @Override
                public GroupPrincipal lookupPrincipalByGroupName(String group)
                    throws IOException
                {
                    return UnixUserPrincipals.lookupGroup(group);
                }
            };
    }

    // Override if the platform has different path match requirement, such as
    // case insensitive or Unicode canonical equal on MacOSX
    Pattern compilePathMatchPattern(String expr) {
        return Pattern.compile(expr);
    }

    // Override if the platform uses different Unicode normalization form
    // for native file path. For example on MacOSX, the native path is stored
    // in Unicode NFD form.
    String normalizeNativePath(String path) {
        return path;
    }

    // Override if the native file path use non-NFC form. For example on MacOSX,
    // the native path is stored in Unicode NFD form, the path need to be
    // normalized back to NFC before passed back to Java level.
    String normalizeJavaPath(String path) {
        return path;
    }

    //  Unix implementation of Files#copy and Files#move methods.

    // calculate the least common multiple of two values;
    // the parameters in general will be powers of two likely in the
    // range [4096, 65536] so this algorithm is expected to converge
    // when it is rarely called
    private static long lcm(long x, long y) {
        assert x > 0 && y > 0 : "Non-positive parameter";

        long u = x;
        long v = y;

        while (u != v) {
            if (u < v)
                u += x;
            else // u > v
                v += y;
        }

        return u;
    }

    // calculate temporary direct buffer size
    private static int temporaryBufferSize(UnixPath source, UnixPath target) {
        int bufferSize = MIN_BUFFER_SIZE;
        try {
            long bss = UnixFileStoreAttributes.get(source).blockSize();
            long bst = UnixFileStoreAttributes.get(target).blockSize();
            if (bss > 0 && bst > 0) {
                bufferSize = (int)(bss == bst ? bss : lcm(bss, bst));
            }
            if (bufferSize < MIN_BUFFER_SIZE) {
                int factor = (MIN_BUFFER_SIZE + bufferSize - 1)/bufferSize;
                bufferSize *= factor;
            }
        } catch (UnixException ignored) {
        }
        return bufferSize;
    }

    // The flags that control how a file is copied or moved
    protected static class Flags {
        boolean replaceExisting;
        boolean atomicMove;
        boolean followLinks;
        boolean interruptible;

        // the attributes to copy
        boolean copyBasicAttributes;
        boolean copyPosixAttributes;
        boolean copyNonPosixAttributes;

        // flags that indicate if we should fail if attributes cannot be copied
        boolean failIfUnableToCopyBasic;
        boolean failIfUnableToCopyPosix;
        boolean failIfUnableToCopyNonPosix;

        static Flags fromCopyOptions(CopyOption... options) {
            Flags flags = new Flags();
            flags.followLinks = true;
            for (CopyOption option: options) {
                if (option == StandardCopyOption.REPLACE_EXISTING) {
                    flags.replaceExisting = true;
                    continue;
                }
                if (option == LinkOption.NOFOLLOW_LINKS) {
                    flags.followLinks = false;
                    continue;
                }
                if (option == StandardCopyOption.COPY_ATTRIBUTES) {
                    // copy all attributes but only fail if basic attributes
                    // cannot be copied
                    flags.copyBasicAttributes = true;
                    flags.copyPosixAttributes = true;
                    flags.copyNonPosixAttributes = true;
                    flags.failIfUnableToCopyBasic = true;
                    continue;
                }
                if (ExtendedOptions.INTERRUPTIBLE.matches(option)) {
                    flags.interruptible = true;
                    continue;
                }
                if (option == null)
                    throw new NullPointerException();
                throw new UnsupportedOperationException("Unsupported copy option: " + option);
            }
            return flags;
        }

        static Flags fromMoveOptions(CopyOption... options) {
            Flags flags = new Flags();
            for (CopyOption option: options) {
                if (option == StandardCopyOption.ATOMIC_MOVE) {
                    flags.atomicMove = true;
                    continue;
                }
                if (option == StandardCopyOption.REPLACE_EXISTING) {
                    flags.replaceExisting = true;
                    continue;
                }
                if (option == LinkOption.NOFOLLOW_LINKS) {
                    // ignore
                    continue;
                }
                if (option == null)
                    throw new NullPointerException();
                throw new UnsupportedOperationException("Unsupported option: " + option);
            }

            // a move requires that all attributes be copied but only fail if
            // the basic attributes cannot be copied
            flags.copyBasicAttributes = true;
            flags.copyPosixAttributes = true;
            flags.copyNonPosixAttributes = true;
            flags.failIfUnableToCopyBasic = true;
            return flags;
        }
    }

    // copy directory from source to target
    private void copyDirectory(UnixPath source,
                               UnixFileAttributes attrs,
                               UnixPath target,
                               Flags flags)
        throws IOException
    {
        try {
            mkdir(target, attrs.mode());
        } catch (UnixException x) {
            if (x.errno() == EEXIST && flags.replaceExisting)
                throw new FileSystemException(target.toString());
            x.rethrowAsIOException(target);
        }

        // no attributes to copy
        if (!flags.copyBasicAttributes &&
            !flags.copyPosixAttributes &&
            !flags.copyNonPosixAttributes) return;

        // open target directory if possible (this can fail when copying a
        // directory for which we don't have read access).
        int dfd = -1;
        try {
            dfd = open(target, O_RDONLY, 0);
        } catch (UnixException x) {
            // access to target directory required to copy named attributes
            if (flags.copyNonPosixAttributes && flags.failIfUnableToCopyNonPosix) {
                try { rmdir(target); } catch (UnixException ignore) { }
                x.rethrowAsIOException(target);
            }
        }

        boolean done = false;
        try {
            // copy owner/group/permissions
            if (flags.copyPosixAttributes){
                try {
                    if (dfd >= 0) {
                        fchown(dfd, attrs.uid(), attrs.gid());
                        fchmod(dfd, attrs.mode());
                    } else {
                        chown(target, attrs.uid(), attrs.gid());
                        chmod(target, attrs.mode());
                    }
                } catch (UnixException x) {
                    // unable to set owner/group
                    if (flags.failIfUnableToCopyPosix)
                        x.rethrowAsIOException(target);
                }
            }
            // copy other attributes
            if (flags.copyNonPosixAttributes && (dfd >= 0)) {
                int sfd = -1;
                try {
                    sfd = open(source, O_RDONLY, 0);
                } catch (UnixException x) {
                    if (flags.failIfUnableToCopyNonPosix)
                        x.rethrowAsIOException(source);
                }
                if (sfd >= 0) {
                    source.getFileSystem().copyNonPosixAttributes(sfd, dfd);
                    UnixNativeDispatcher.close(sfd, e -> null);
                }
            }
            // copy time stamps last
            if (flags.copyBasicAttributes) {
                try {
                    if (dfd >= 0 && futimesSupported()) {
                        futimes(dfd,
                                attrs.lastAccessTime().to(TimeUnit.MICROSECONDS),
                                attrs.lastModifiedTime().to(TimeUnit.MICROSECONDS));
                    } else {
                        utimes(target,
                               attrs.lastAccessTime().to(TimeUnit.MICROSECONDS),
                               attrs.lastModifiedTime().to(TimeUnit.MICROSECONDS));
                    }
                } catch (UnixException x) {
                    // unable to set times
                    if (flags.failIfUnableToCopyBasic)
                        x.rethrowAsIOException(target);
                }
            }
            done = true;
        } finally {
            if (dfd >= 0)
                UnixNativeDispatcher.close(dfd, e -> null);
            if (!done) {
                // rollback
                try { rmdir(target); } catch (UnixException ignore) { }
            }
        }
    }

    /**
     * Copies data between file descriptors {@code src} and {@code dst} using
     * a platform-specific function or system call possibly having kernel
     * support.
     *
     * @param dst destination file descriptor
     * @param src source file descriptor
     * @param addressToPollForCancel address to check for cancellation
     *        (a non-zero value written to this address indicates cancel)
     *
     * @return 0 on success, IOStatus.UNAVAILABLE if the platform function
     *         would block, IOStatus.UNSUPPORTED_CASE if the call does not
     *         work with the given parameters, or IOStatus.UNSUPPORTED if
     *         direct copying is not supported on this platform
     */
    int directCopy(int dst, int src, long addressToPollForCancel)
        throws UnixException
    {
        return IOStatus.UNSUPPORTED;
    }

    /**
     * Copies data between file descriptors {@code src} and {@code dst} using
     * an intermediate temporary direct buffer.
     *
     * @param dst destination file descriptor
     * @param src source file descriptor
     * @param address the address of the temporary direct buffer's array
     * @param size the size of the temporary direct buffer's array
     * @param addressToPollForCancel address to check for cancellation
     *        (a non-zero value written to this address indicates cancel)
     */
    void bufferedCopy(int dst, int src, long address,
                      int size, long addressToPollForCancel)
        throws UnixException
    {
        bufferedCopy0(dst, src, address, size, addressToPollForCancel);
    }

    // copy regular file from source to target
    void copyFile(UnixPath source,
                  UnixFileAttributes attrs,
                  UnixPath  target,
                  Flags flags,
                  long addressToPollForCancel)
        throws IOException
    {
        int fi = -1;
        try {
            fi = open(source, O_RDONLY, 0);
        } catch (UnixException x) {
            x.rethrowAsIOException(source);
        }

        try {
            // open new file
            int fo = -1;
            try {
                fo = open(target,
                           (O_WRONLY |
                            O_CREAT |
                            O_EXCL),
                           attrs.mode());
            } catch (UnixException x) {
                if (x.errno() == EEXIST && flags.replaceExisting)
                    throw new FileSystemException(target.toString());
                x.rethrowAsIOException(target);
            }

            // set to true when file and attributes copied
            boolean complete = false;
            try {
                // set to true when data copied
                boolean copied = false;

                // Some forms of direct copy do not work on zero size files
                if (!directCopyNotSupported && attrs.size() > 0) {
                    // copy bytes to target using platform function
                    long comp = Blocker.begin();
                    try {
                        int res = directCopy(fo, fi, addressToPollForCancel);
                        if (res == 0) {
                            copied = true;
                        } else if (res == IOStatus.UNSUPPORTED) {
                            directCopyNotSupported = true;
                        }
                    } catch (UnixException x) {
                        x.rethrowAsIOException(source, target);
                    } finally {
                        Blocker.end(comp);
                    }
                }

                if (!copied) {
                    // copy bytes to target via a temporary direct buffer
                    int bufferSize = temporaryBufferSize(source, target);
                    ByteBuffer buf =
                        sun.nio.ch.Util.getTemporaryDirectBuffer(bufferSize);
                    try {
                        long comp = Blocker.begin();
                        try {
                            bufferedCopy(fo, fi, ((DirectBuffer)buf).address(),
                                          bufferSize, addressToPollForCancel);
                        } catch (UnixException x) {
                            x.rethrowAsIOException(source, target);
                        } finally {
                            Blocker.end(comp);
                        }
                    } finally {
                        sun.nio.ch.Util.releaseTemporaryDirectBuffer(buf);
                    }
                }

                // copy owner/permissions
                if (flags.copyPosixAttributes) {
                    try {
                        fchown(fo, attrs.uid(), attrs.gid());
                        fchmod(fo, attrs.mode());
                    } catch (UnixException x) {
                        if (flags.failIfUnableToCopyPosix)
                            x.rethrowAsIOException(target);
                    }
                }
                // copy non POSIX attributes (depends on file system)
                if (flags.copyNonPosixAttributes) {
                    source.getFileSystem().copyNonPosixAttributes(fi, fo);
                }
                // copy time attributes
                if (flags.copyBasicAttributes) {
                    try {
                        if (futimesSupported()) {
                            futimes(fo,
                                    attrs.lastAccessTime().to(TimeUnit.MICROSECONDS),
                                    attrs.lastModifiedTime().to(TimeUnit.MICROSECONDS));
                        } else {
                            utimes(target,
                                   attrs.lastAccessTime().to(TimeUnit.MICROSECONDS),
                                   attrs.lastModifiedTime().to(TimeUnit.MICROSECONDS));
                        }
                    } catch (UnixException x) {
                        if (flags.failIfUnableToCopyBasic)
                            x.rethrowAsIOException(target);
                    }
                }
                complete = true;
            } finally {
                UnixNativeDispatcher.close(fo, e -> null);

                // copy of file or attributes failed so rollback
                if (!complete) {
                    try {
                        unlink(target);
                    } catch (UnixException ignore) { }
                }
            }
        } finally {
            UnixNativeDispatcher.close(fi, e -> null);
        }
    }

    // copy symbolic link from source to target
    private void copyLink(UnixPath source,
                          UnixFileAttributes attrs,
                          UnixPath  target,
                          Flags flags)
        throws IOException
    {
        byte[] linktarget = null;
        try {
            linktarget = readlink(source);
        } catch (UnixException x) {
            x.rethrowAsIOException(source);
        }
        try {
            symlink(linktarget, target);

            if (flags.copyPosixAttributes) {
                try {
                    lchown(target, attrs.uid(), attrs.gid());
                } catch (UnixException x) {
                    // ignore since link attributes not required to be copied
                }
            }
        } catch (UnixException x) {
            if (x.errno() == EEXIST && flags.replaceExisting)
                throw new FileSystemException(target.toString());
            x.rethrowAsIOException(target);
        }
    }

    // copy special file from source to target
    private void copySpecial(UnixPath source,
                             UnixFileAttributes attrs,
                             UnixPath  target,
                             Flags flags)
        throws IOException
    {
        try {
            mknod(target, attrs.mode(), attrs.rdev());
        } catch (UnixException x) {
            if (x.errno() == EEXIST && flags.replaceExisting)
                throw new FileSystemException(target.toString());
            x.rethrowAsIOException(target);
        }
        boolean done = false;
        try {
            if (flags.copyPosixAttributes) {
                try {
                    chown(target, attrs.uid(), attrs.gid());
                    chmod(target, attrs.mode());
                } catch (UnixException x) {
                    if (flags.failIfUnableToCopyPosix)
                        x.rethrowAsIOException(target);
                }
            }
            if (flags.copyBasicAttributes) {
                try {
                    utimes(target,
                           attrs.lastAccessTime().to(TimeUnit.MICROSECONDS),
                           attrs.lastModifiedTime().to(TimeUnit.MICROSECONDS));
                } catch (UnixException x) {
                    if (flags.failIfUnableToCopyBasic)
                        x.rethrowAsIOException(target);
                }
            }
            done = true;
        } finally {
            if (!done) {
                try { unlink(target); } catch (UnixException ignore) { }
            }
        }
    }

    // throw a DirectoryNotEmpty exception if appropriate
    static void ensureEmptyDir(UnixPath dir) throws IOException {
        try {
            long ptr = opendir(dir);
            try (UnixDirectoryStream stream =
                new UnixDirectoryStream(dir, ptr, e -> true)) {
                if (stream.iterator().hasNext()) {
                    throw new DirectoryNotEmptyException(
                        dir.getPathForExceptionMessage());
                }
            }
        } catch (UnixException e) {
            e.rethrowAsIOException(dir);
        }
    }

    // move file from source to target
    void move(UnixPath source, UnixPath target, CopyOption... options)
        throws IOException
    {
        // permission check
        @SuppressWarnings("removal")
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            source.checkWrite();
            target.checkWrite();
        }

        // translate options into flags
        Flags flags = Flags.fromMoveOptions(options);

        // handle atomic rename case
        if (flags.atomicMove) {
            try {
                rename(source, target);
            } catch (UnixException x) {
                if (x.errno() == EXDEV) {
                    throw new AtomicMoveNotSupportedException(
                        source.getPathForExceptionMessage(),
                        target.getPathForExceptionMessage(),
                        x.errorString());
                }
                x.rethrowAsIOException(source, target);
            }
            return;
        }

        // move using rename or copy+delete
        UnixFileAttributes sourceAttrs = null;
        UnixFileAttributes targetAttrs = null;

        // get attributes of source file (don't follow links)
        try {
            sourceAttrs = UnixFileAttributes.get(source, false);
            if (sourceAttrs.isDirectory()) {
                // ensure source can be moved
                access(source, W_OK);
            }
        } catch (UnixException x) {
            x.rethrowAsIOException(source);
        }

        // get attributes of target file (don't follow links)
        try {
            targetAttrs = UnixFileAttributes.get(target, false);
        } catch (UnixException x) {
            // ignore
        }
        boolean targetExists = (targetAttrs != null);

        // if the target exists:
        // 1. check if source and target are the same file
        // 2. throw exception if REPLACE_EXISTING option is not set
        // 3. delete target if REPLACE_EXISTING option set
        if (targetExists) {
            if (sourceAttrs.isSameFile(targetAttrs))
                return;  // nothing to do as files are identical
            if (!flags.replaceExisting)
                throw new FileAlreadyExistsException(
                    target.getPathForExceptionMessage());

            // attempt to delete target
            try {
                if (targetAttrs.isDirectory()) {
                    rmdir(target);
                } else {
                    unlink(target);
                }
            } catch (UnixException x) {
                // target is non-empty directory that can't be replaced.
                if (targetAttrs.isDirectory() &&
                   (x.errno() == EEXIST || x.errno() == ENOTEMPTY))
                {
                    throw new DirectoryNotEmptyException(
                        target.getPathForExceptionMessage());
                }
                x.rethrowAsIOException(target);
            }
        }

        // first try rename
        try {
            rename(source, target);
            return;
        } catch (UnixException x) {
            if (x.errno() != EXDEV && x.errno() != EISDIR) {
                x.rethrowAsIOException(source, target);
            }
        }

        // copy source to target
        if (sourceAttrs.isDirectory()) {
            ensureEmptyDir(source);
            copyDirectory(source, sourceAttrs, target, flags);
        } else {
            if (sourceAttrs.isSymbolicLink()) {
                copyLink(source, sourceAttrs, target, flags);
            } else {
                if (sourceAttrs.isDevice()) {
                    copySpecial(source, sourceAttrs, target, flags);
                } else {
                    copyFile(source, sourceAttrs, target, flags, 0L);
                }
            }
        }

        // delete source
        try {
            if (sourceAttrs.isDirectory()) {
                rmdir(source);
            } else {
                unlink(source);
            }
        } catch (UnixException x) {
            // file was copied but unable to unlink the source file so attempt
            // to remove the target and throw a reasonable exception
            try {
                if (sourceAttrs.isDirectory()) {
                    rmdir(target);
                } else {
                    unlink(target);
                }
            } catch (UnixException ignore) { }

            if (sourceAttrs.isDirectory() &&
                (x.errno() == EEXIST || x.errno() == ENOTEMPTY))
            {
                throw new DirectoryNotEmptyException(
                    source.getPathForExceptionMessage());
            }
            x.rethrowAsIOException(source);
        }
    }

    // copy file from source to target
    void copy(final UnixPath source,
              final UnixPath target,
              CopyOption... options) throws IOException
    {
        // permission checks
        @SuppressWarnings("removal")
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            source.checkRead();
            target.checkWrite();
        }

        // translate options into flags
        final Flags flags = Flags.fromCopyOptions(options);

        UnixFileAttributes sourceAttrs = null;
        UnixFileAttributes targetAttrs = null;

        // get attributes of source file
        try {
            sourceAttrs = UnixFileAttributes.get(source, flags.followLinks);
        } catch (UnixException x) {
            x.rethrowAsIOException(source);
        }

        // if source file is symbolic link then we must check LinkPermission
        if (sm != null && sourceAttrs.isSymbolicLink()) {
            sm.checkPermission(new LinkPermission("symbolic"));
        }

        // ensure source can be copied
        if (!sourceAttrs.isSymbolicLink() || flags.followLinks) {
            try {
                // the access(2) system call always follows links so it
                // is suppressed if the source is an unfollowed link
                access(source, R_OK);
            } catch (UnixException exc) {
                exc.rethrowAsIOException(source);
            }
        }

        // get attributes of target file (don't follow links)
        try {
            targetAttrs = UnixFileAttributes.get(target, false);
        } catch (UnixException x) {
            // ignore
        }
        boolean targetExists = (targetAttrs != null);

        // if the target exists:
        // 1. check if source and target are the same file
        // 2. throw exception if REPLACE_EXISTING option is not set
        // 3. try to unlink the target
        if (targetExists) {
            if (sourceAttrs.isSameFile(targetAttrs))
                return;  // nothing to do as files are identical
            if (!flags.replaceExisting)
                throw new FileAlreadyExistsException(
                    target.getPathForExceptionMessage());

            try {
                if (targetAttrs.isDirectory()) {
                    rmdir(target);
                } else {
                    unlink(target);
                }
            } catch (UnixException x) {
                // target is non-empty directory that can't be replaced.
                if (targetAttrs.isDirectory() &&
                   (x.errno() == EEXIST || x.errno() == ENOTEMPTY))
                {
                    throw new DirectoryNotEmptyException(
                        target.getPathForExceptionMessage());
                }
                x.rethrowAsIOException(target);
            }
        }

        // do the copy
        if (sourceAttrs.isDirectory()) {
            copyDirectory(source, sourceAttrs, target, flags);
            return;
        }
        if (sourceAttrs.isSymbolicLink()) {
            copyLink(source, sourceAttrs, target, flags);
            return;
        }
        if (!flags.interruptible) {
            // non-interruptible file copy
            copyFile(source, sourceAttrs, target, flags, 0L);
            return;
        }

        // interruptible file copy
        final UnixFileAttributes attrsToCopy = sourceAttrs;
        Cancellable copyTask = new Cancellable() {
            @Override public void implRun() throws IOException {
                copyFile(source, attrsToCopy, target,
                         flags, addressToPollForCancel());
            }
        };
        try {
            Cancellable.runInterruptibly(copyTask);
        } catch (ExecutionException e) {
            Throwable t = e.getCause();
            if (t instanceof IOException)
                throw (IOException)t;
            throw new IOException(t);
        }
    }


    // -- native methods --

    private static native void bufferedCopy0(int dst, int src, long address,
                                             int size, long addressToPollForCancel)
        throws UnixException;
}
