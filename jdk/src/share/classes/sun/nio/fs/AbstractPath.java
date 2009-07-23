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

package sun.nio.fs;

import java.nio.file.*;
import static java.nio.file.StandardOpenOption.*;
import java.nio.file.attribute.*;
import java.nio.channels.*;
import java.nio.ByteBuffer;
import java.io.*;
import java.util.*;

/**
 * Base implementation class for a {@code Path}.
 */

abstract class AbstractPath extends Path {
    protected AbstractPath() { }

    @Override
    public final Path createFile(FileAttribute<?>... attrs)
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
     * Deletes a file. The {@code failIfNotExists} parameters determines if an
     * {@code IOException} is thrown when the file does not exist.
     */
    abstract void implDelete(boolean failIfNotExists) throws IOException;

    @Override
    public final void delete() throws IOException {
        implDelete(true);
    }

    @Override
    public final void deleteIfExists() throws IOException {
        implDelete(false);
    }

    @Override
    public final InputStream newInputStream(OpenOption... options)
        throws IOException
    {
        if (options.length > 0) {
            for (OpenOption opt: options) {
                if (opt != READ)
                    throw new UnsupportedOperationException("'" + opt + "' not allowed");
            }
        }
        return Channels.newInputStream(newByteChannel());
    }

    @Override
    public final OutputStream newOutputStream(OpenOption... options)
        throws IOException
    {
        int len = options.length;
        Set<OpenOption> opts = new HashSet<OpenOption>(len + 3);
        if (len == 0) {
            opts.add(CREATE);
            opts.add(TRUNCATE_EXISTING);
        } else {
            for (OpenOption opt: options) {
                if (opt == READ)
                    throw new IllegalArgumentException("READ not allowed");
                opts.add(opt);
            }
        }
        opts.add(WRITE);
        return Channels.newOutputStream(newByteChannel(opts));
    }

    @Override
    public final SeekableByteChannel newByteChannel(OpenOption... options)
        throws IOException
    {
        Set<OpenOption> set = new HashSet<OpenOption>(options.length);
        Collections.addAll(set, options);
        return newByteChannel(set);
    }

    private static final DirectoryStream.Filter<Path> acceptAllFilter =
        new DirectoryStream.Filter<Path>() {
            @Override public boolean accept(Path entry) { return true; }
        };

    @Override
    public final DirectoryStream<Path> newDirectoryStream() throws IOException {
        return newDirectoryStream(acceptAllFilter);
    }

    @Override
    public final DirectoryStream<Path> newDirectoryStream(String glob)
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

    @Override
    public final boolean exists() {
        try {
            checkAccess();
            return true;
        } catch (IOException x) {
            // unable to determine if file exists
        }
        return false;
    }

    @Override
    public final boolean notExists() {
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

    private static final WatchEvent.Modifier[] NO_MODIFIERS = new WatchEvent.Modifier[0];

    @Override
    public final WatchKey register(WatchService watcher,
                                   WatchEvent.Kind<?>... events)
        throws IOException
    {
        return register(watcher, events, NO_MODIFIERS);
    }

    abstract void implCopyTo(Path target, CopyOption... options)
        throws IOException;

    @Override
    public final Path copyTo(Path target, CopyOption... options)
        throws IOException
    {
        if ((getFileSystem().provider() == target.getFileSystem().provider())) {
            implCopyTo(target, options);
        } else {
            copyToForeignTarget(target, options);
        }
        return target;
    }

    abstract void implMoveTo(Path target, CopyOption... options)
        throws IOException;

    @Override
    public final Path moveTo(Path target, CopyOption... options)
        throws IOException
    {
        if ((getFileSystem().provider() == target.getFileSystem().provider())) {
            implMoveTo(target, options);
        } else {
            // different providers so copy + delete
            copyToForeignTarget(target, convertMoveToCopyOptions(options));
            delete();
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
    private void copyToForeignTarget(Path target, CopyOption... options)
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
            target.deleteIfExists();

        // create directory or file
        if (attrs.isDirectory()) {
            target.createDirectory();
        } else {
            copyRegularFileToForeignTarget(target);
        }

        // copy basic attributes to target
        if (opts.copyAttributes) {
            BasicFileAttributeView view = target
                .getFileAttributeView(BasicFileAttributeView.class, linkOptions);
            try {
                view.setTimes(attrs.lastModifiedTime(),
                              attrs.lastAccessTime(),
                              attrs.creationTime());
            } catch (IOException x) {
                // rollback
                try {
                    target.delete();
                } catch (IOException ignore) { }
                throw x;
            }
        }
    }


   /**
     * Simple copy of regular file to a target file that exists.
     */
    private void copyRegularFileToForeignTarget(Path target)
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
     * Gets a DynamicFileAttributeView by name. Returns {@code null} if the
     * view is not available.
     */
    abstract DynamicFileAttributeView getFileAttributeView(String name,
                                                           LinkOption... options);

    @Override
    public final void setAttribute(String attribute,
                                   Object value,
                                   LinkOption... options)
        throws IOException
    {
        String[] s = split(attribute);
        DynamicFileAttributeView view = getFileAttributeView(s[0], options);
        if (view == null)
            throw new UnsupportedOperationException("View '" + s[0] + "' not available");
        view.setAttribute(s[1], value);
    }

    @Override
    public final Object getAttribute(String attribute, LinkOption... options)
        throws IOException
    {
        String[] s = split(attribute);
        DynamicFileAttributeView view = getFileAttributeView(s[0], options);
        return (view == null) ? null : view.getAttribute(s[1]);
    }

    @Override
    public final Map<String,?> readAttributes(String attributes, LinkOption... options)
        throws IOException
    {
        String[] s = split(attributes);
        DynamicFileAttributeView view = getFileAttributeView(s[0], options);
        if (view == null)
            return Collections.emptyMap();
        return view.readAttributes(s[1].split(","));
    }
}
