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

package java.nio.file.spi;

import java.nio.file.*;
import static java.nio.file.StandardOpenOption.*;
import java.nio.file.attribute.*;
import java.nio.channels.*;
import java.nio.ByteBuffer;
import java.io.*;
import java.util.*;

/**
 * Base implementation class for a {@code Path}.
 *
 * <p> This class is intended to be extended by provider implementors. It
 * implements, or provides default implementations for several of the methods
 * defined by the {@code Path} class. It implements the {@link #copyTo copyTo}
 * and {@link #moveTo moveTo} methods for the case that the source and target
 * are not associated with the same provider.
 *
 * @since 1.7
 */

public abstract class AbstractPath extends Path {

    /**
     * Initializes a new instance of this class.
     */
    protected AbstractPath() { }

    /**
     * Deletes the file referenced by this object.
     *
     * <p> This method invokes the {@link #delete(boolean) delete(boolean)}
     * method with a parameter of {@code true}. It may be overridden where
     * required.
     *
     * @throws  NoSuchFileException             {@inheritDoc}
     * @throws  DirectoryNotEmptyException      {@inheritDoc}
     * @throws  IOException                     {@inheritDoc}
     * @throws  SecurityException               {@inheritDoc}
     */
    @Override
    public void delete() throws IOException {
        delete(true);
    }

    /**
     * Creates a new and empty file, failing if the file already exists.
     *
     * <p> This method invokes the {@link #newByteChannel(Set,FileAttribute[])
     * newByteChannel(Set,FileAttribute...)} method to create the file. It may be
     * overridden where required.
     *
     * @throws  IllegalArgumentException        {@inheritDoc}
     * @throws  FileAlreadyExistsException      {@inheritDoc}
     * @throws  IOException                     {@inheritDoc}
     * @throws  SecurityException               {@inheritDoc}
     */
    @Override
    public Path createFile(FileAttribute<?>... attrs)
        throws IOException
    {
        EnumSet<StandardOpenOption> options = EnumSet.of(CREATE_NEW, WRITE);
        SeekableByteChannel sbc = newByteChannel(options, attrs);
        try {
            sbc.close();
        } catch (IOException x) {
            // ignore
        }
        return this;
    }

    /**
     * Opens or creates a file, returning a seekable byte channel to access the
     * file.
     *
     * <p> This method invokes the {@link #newByteChannel(Set,FileAttribute[])
     * newByteChannel(Set,FileAttribute...)} method to open or create the file.
     * It may be overridden where required.
     *
     * @throws  IllegalArgumentException        {@inheritDoc}
     * @throws  FileAlreadyExistsException      {@inheritDoc}
     * @throws  IOException                     {@inheritDoc}
     * @throws  SecurityException               {@inheritDoc}
     */
    @Override
    public SeekableByteChannel newByteChannel(OpenOption... options)
        throws IOException
    {
        Set<OpenOption> set = new HashSet<OpenOption>(options.length);
        Collections.addAll(set, options);
        return newByteChannel(set);
    }

    /**
     * Opens the file located by this path for reading, returning an input
     * stream to read bytes from the file.
     *
     * <p> This method returns an {@code InputStream} that is constructed by
     * invoking the {@link java.nio.channels.Channels#newInputStream
     * Channels.newInputStream} method. It may be overridden where a more
     * efficient implementation is available.
     *
     * @throws  IOException                     {@inheritDoc}
     * @throws  SecurityException               {@inheritDoc}
     */
    @Override
    public InputStream newInputStream() throws IOException {
        return Channels.newInputStream(newByteChannel());
    }

    // opts must be modifiable
    private OutputStream implNewOutputStream(Set<OpenOption> opts,
                                             FileAttribute<?>... attrs)
        throws IOException
    {
        if (opts.isEmpty()) {
            opts.add(CREATE);
            opts.add(TRUNCATE_EXISTING);
        } else {
            if (opts.contains(READ))
                throw new IllegalArgumentException("READ not allowed");
        }
        opts.add(WRITE);
        return Channels.newOutputStream(newByteChannel(opts, attrs));
    }

    /**
     * Opens or creates the file located by this path for writing, returning an
     * output stream to write bytes to the file.
     *
     * <p> This method returns an {@code OutputStream} that is constructed by
     * invoking the {@link java.nio.channels.Channels#newOutputStream
     * Channels.newOutputStream} method. It may be overridden where a more
     * efficient implementation is available.
     *
     * @throws  IllegalArgumentException        {@inheritDoc}
     * @throws  IOException                     {@inheritDoc}
     * @throws  SecurityException               {@inheritDoc}
     */
    @Override
    public OutputStream newOutputStream(OpenOption... options) throws IOException {
        int len = options.length;
        Set<OpenOption> opts = new HashSet<OpenOption>(len + 3);
        if (len > 0) {
            for (OpenOption opt: options) {
                opts.add(opt);
            }
        }
        return implNewOutputStream(opts);
    }

    /**
     * Opens or creates the file located by this path for writing, returning an
     * output stream to write bytes to the file.
     *
     * <p> This method returns an {@code OutputStream} that is constructed by
     * invoking the {@link java.nio.channels.Channels#newOutputStream
     * Channels.newOutputStream} method. It may be overridden where a more
     * efficient implementation is available.
     *
     * @throws  IllegalArgumentException        {@inheritDoc}
     * @throws  IOException                     {@inheritDoc}
     * @throws  SecurityException               {@inheritDoc}
     */
    @Override
    public OutputStream newOutputStream(Set<? extends OpenOption> options,
                                        FileAttribute<?>... attrs)
        throws IOException
    {
        Set<OpenOption> opts = new HashSet<OpenOption>(options);
        return implNewOutputStream(opts, attrs);
    }

    /**
     * Opens the directory referenced by this object, returning a {@code
     * DirectoryStream} to iterate over all entries in the directory.
     *
     * <p> This method invokes the {@link
     * #newDirectoryStream(java.nio.file.DirectoryStream.Filter)
     * newDirectoryStream(Filter)} method with a filter that accept all entries.
     * It may be overridden where required.
     *
     * @throws  NotDirectoryException           {@inheritDoc}
     * @throws  IOException                     {@inheritDoc}
     * @throws  SecurityException               {@inheritDoc}
     */
    @Override
    public DirectoryStream<Path> newDirectoryStream() throws IOException {
        return newDirectoryStream(acceptAllFilter);
    }
    private static final DirectoryStream.Filter<Path> acceptAllFilter =
        new DirectoryStream.Filter<Path>() {
            @Override public boolean accept(Path entry) { return true; }
        };

    /**
     * Opens the directory referenced by this object, returning a {@code
     * DirectoryStream} to iterate over the entries in the directory. The
     * entries are filtered by matching the {@code String} representation of
     * their file names against a given pattern.
     *
     * <p> This method constructs a {@link PathMatcher} by invoking the
     * file system's {@link java.nio.file.FileSystem#getPathMatcher
     * getPathMatcher} method. This method may be overridden where a more
     * efficient implementation is available.
     *
     * @throws  java.util.regex.PatternSyntaxException  {@inheritDoc}
     * @throws  UnsupportedOperationException   {@inheritDoc}
     * @throws  NotDirectoryException           {@inheritDoc}
     * @throws  IOException                     {@inheritDoc}
     * @throws  SecurityException               {@inheritDoc}
     */
    @Override
    public DirectoryStream<Path> newDirectoryStream(String glob)
        throws IOException
    {
        // avoid creating a matcher if all entries are required.
        if (glob.equals("*"))
            return newDirectoryStream();

        // create a matcher and return a filter that uses it.
        final PathMatcher matcher = getFileSystem().getPathMatcher("glob:" + glob);
        DirectoryStream.Filter<Path> filter = new DirectoryStream.Filter<Path>() {
            @Override
            public boolean accept(Path entry)  {
                return matcher.matches(entry.getName());
            }
        };
        return newDirectoryStream(filter);
    }

    /**
     * Tests whether the file located by this path exists.
     *
     * <p> This method invokes the {@link #checkAccess checkAccess} method to
     * check if the file exists. It may be  overridden where a more efficient
     * implementation is available.
     */
    @Override
    public boolean exists() {
        try {
            checkAccess();
            return true;
        } catch (IOException x) {
            // unable to determine if file exists
        }
        return false;
    }

    /**
     * Tests whether the file located by this path does not exist.
     *
     * <p> This method invokes the {@link #checkAccess checkAccess} method to
     * check if the file exists. It may be  overridden where a more efficient
     * implementation is available.
     */
    @Override
    public boolean notExists() {
        try {
            checkAccess();
            return false;
        } catch (NoSuchFileException x) {
            // file confirmed not to exist
            return true;
        } catch (IOException x) {
            return false;
        }
    }

    /**
     * Registers the file located by this path with a watch service.
     *
     * <p> This method invokes the {@link #register(WatchService,WatchEvent.Kind[],WatchEvent.Modifier[])
     * register(WatchService,WatchEvent.Kind[],WatchEvent.Modifier...)}
     * method to register the file. It may be  overridden where required.
     */
    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events)
        throws IOException
    {
        return register(watcher, events, NO_MODIFIERS);
    }
    private static final WatchEvent.Modifier[] NO_MODIFIERS = new WatchEvent.Modifier[0];

    /**
     * Copy the file located by this path to a target location.
     *
     * <p> This method is invoked by the {@link #copyTo copyTo} method for
     * the case that this {@code Path} and the target {@code Path} are
     * associated with the same provider.
     *
     * @param   target
     *          The target location
     * @param   options
     *          Options specifying how the copy should be done
     *
     * @throws  IllegalArgumentException
     *          If an invalid option is specified
     * @throws  FileAlreadyExistsException
     *          The target file exists and cannot be replaced because the
     *          {@code REPLACE_EXISTING} option is not specified, or the target
     *          file is a non-empty directory <i>(optional specific exception)</i>
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, the {@link SecurityManager#checkRead(String) checkRead}
     *          method is invoked to check read access to the source file, the
     *          {@link SecurityManager#checkWrite(String) checkWrite} is invoked
     *          to check write access to the target file. If a symbolic link is
     *          copied the security manager is invoked to check {@link
     *          LinkPermission}{@code ("symbolic")}.
     */
    protected abstract void implCopyTo(Path target, CopyOption... options)
        throws IOException;

    /**
     * Move the file located by this path to a target location.
     *
     * <p> This method is invoked by the {@link #moveTo moveTo} method for
     * the case that this {@code Path} and the target {@code Path} are
     * associated with the same provider.
     *
     * @param   target
     *          The target location
     * @param   options
     *          Options specifying how the move should be done
     *
     * @throws  IllegalArgumentException
     *          If an invalid option is specified
     * @throws  FileAlreadyExistsException
     *          The target file exists and cannot be replaced because the
     *          {@code REPLACE_EXISTING} option is not specified, or the target
     *          file is a non-empty directory
     * @throws  AtomicMoveNotSupportedException
     *          The options array contains the {@code ATOMIC_MOVE} option but
     *          the file cannot be moved as an atomic file system operation.
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, the {@link SecurityManager#checkWrite(String) checkWrite}
     *          method is invoked to check write access to both the source and
     *          target file.
     */
    protected abstract void implMoveTo(Path target, CopyOption... options)
        throws IOException;

    /**
     * Copy the file located by this path to a target location.
     *
     * <p> If this path is associated with the same {@link FileSystemProvider
     * provider} as the {@code target} then the {@link #implCopyTo implCopyTo}
     * method is invoked to copy the file. Otherwise, this method attempts to
     * copy the file to the target location in a manner that may be less
     * efficient than would be the case that target is associated with the same
     * provider as this path.
     *
     * @throws  IllegalArgumentException            {@inheritDoc}
     * @throws  FileAlreadyExistsException          {@inheritDoc}
     * @throws  IOException                         {@inheritDoc}
     * @throws  SecurityException                   {@inheritDoc}
     */
    @Override
    public final Path copyTo(Path target, CopyOption... options)
        throws IOException
    {
        if ((getFileSystem().provider() == target.getFileSystem().provider())) {
            implCopyTo(target, options);
        } else {
            xProviderCopyTo(target, options);
        }
        return target;
    }

    /**
     * Move or rename the file located by this path to a target location.
     *
     * <p> If this path is associated with the same {@link FileSystemProvider
     * provider} as the {@code target} then the {@link #implCopyTo implMoveTo}
     * method is invoked to move the file. Otherwise, this method attempts to
     * copy the file to the target location and delete the source file. This
     * implementation may be less efficient than would be the case that
     * target is associated with the same provider as this path.
     *
     * @throws  IllegalArgumentException            {@inheritDoc}
     * @throws  FileAlreadyExistsException          {@inheritDoc}
     * @throws  IOException                         {@inheritDoc}
     * @throws  SecurityException                   {@inheritDoc}
     */
    @Override
    public final Path moveTo(Path target, CopyOption... options)
        throws IOException
    {
        if ((getFileSystem().provider() == target.getFileSystem().provider())) {
            implMoveTo(target, options);
        } else {
            // different providers so copy + delete
            xProviderCopyTo(target, convertMoveToCopyOptions(options));
            delete(false);
        }
        return target;
    }

    /**
     * Converts the given array of options for moving a file to options suitable
     * for copying the file when a move is implemented as copy + delete.
     */
    private static CopyOption[] convertMoveToCopyOptions(CopyOption... options)
        throws AtomicMoveNotSupportedException
    {
        int len = options.length;
        CopyOption[] newOptions = new CopyOption[len+2];
        for (int i=0; i<len; i++) {
            CopyOption option = options[i];
            if (option == StandardCopyOption.ATOMIC_MOVE) {
                throw new AtomicMoveNotSupportedException(null, null,
                    "Atomic move between providers is not supported");
            }
            newOptions[i] = option;
        }
        newOptions[len] = LinkOption.NOFOLLOW_LINKS;
        newOptions[len+1] = StandardCopyOption.COPY_ATTRIBUTES;
        return newOptions;
    }

    /**
     * Parses the arguments for a file copy operation.
     */
    private static class CopyOptions {
        boolean replaceExisting = false;
        boolean copyAttributes = false;
        boolean followLinks = true;

        private CopyOptions() { }

        static CopyOptions parse(CopyOption... options) {
            CopyOptions result = new CopyOptions();
            for (CopyOption option: options) {
                if (option == StandardCopyOption.REPLACE_EXISTING) {
                    result.replaceExisting = true;
                    continue;
                }
                if (option == LinkOption.NOFOLLOW_LINKS) {
                    result.followLinks = false;
                    continue;
                }
                if (option == StandardCopyOption.COPY_ATTRIBUTES) {
                    result.copyAttributes = true;
                    continue;
                }
                if (option == null)
                    throw new NullPointerException();
                throw new IllegalArgumentException("'" + option +
                    "' is not a valid copy option");
            }
            return result;
        }
    }

    /**
     * Simple cross-provider copy where the target is a Path.
     */
    private void xProviderCopyTo(Path target, CopyOption... options)
        throws IOException
    {
        CopyOptions opts = CopyOptions.parse(options);
        LinkOption[] linkOptions = (opts.followLinks) ? new LinkOption[0] :
            new LinkOption[] { LinkOption.NOFOLLOW_LINKS };

        // attributes of source file
        BasicFileAttributes attrs = Attributes
            .readBasicFileAttributes(this, linkOptions);
        if (attrs.isSymbolicLink())
            throw new IOException("Copying of symbolic links not supported");

        // delete target file
        if (opts.replaceExisting)
            target.delete(false);

        // create directory or file
        if (attrs.isDirectory()) {
            target.createDirectory();
        } else {
            xProviderCopyRegularFileTo(target);
        }

        // copy basic attributes to target
        if (opts.copyAttributes) {
            BasicFileAttributeView view = target
                .getFileAttributeView(BasicFileAttributeView.class, linkOptions);
            try {
                view.setTimes(attrs.lastModifiedTime(),
                              attrs.lastAccessTime(),
                              attrs.creationTime(),
                              attrs.resolution());
            } catch (IOException x) {
                // rollback
                try {
                    target.delete(false);
                } catch (IOException ignore) { }
                throw x;
            }
        }
    }


   /**
     * Simple copy of regular file to a target file that exists.
     */
    private void xProviderCopyRegularFileTo(FileRef target)
        throws IOException
    {
        ReadableByteChannel rbc = newByteChannel();
        try {
            // open target file for writing
            SeekableByteChannel sbc = target.newByteChannel(CREATE, WRITE);

            // simple copy loop
            try {
                ByteBuffer buf = ByteBuffer.wrap(new byte[8192]);
                int n = 0;
                for (;;) {
                    n = rbc.read(buf);
                    if (n < 0)
                        break;
                    assert n > 0;
                    buf.flip();
                    while (buf.hasRemaining()) {
                        sbc.write(buf);
                    }
                    buf.rewind();
                }

            } finally {
                sbc.close();
            }
        } finally {
            rbc.close();
        }
    }
}
