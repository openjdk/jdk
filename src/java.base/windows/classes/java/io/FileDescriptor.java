/*
 * Copyright (c) 2003, 2017, Oracle and/or its affiliates. All rights reserved.
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

package java.io;

import java.lang.ref.Cleaner;
import java.util.ArrayList;
import java.util.List;

import jdk.internal.misc.JavaIOFileDescriptorAccess;
import jdk.internal.misc.SharedSecrets;
import jdk.internal.ref.CleanerFactory;
import jdk.internal.ref.PhantomCleanable;

/**
 * Instances of the file descriptor class serve as an opaque handle
 * to the underlying machine-specific structure representing an open
 * file, an open socket, or another source or sink of bytes.
 * The main practical use for a file descriptor is to create a
 * {@link FileInputStream} or {@link FileOutputStream} to contain it.
 * <p>
 * Applications should not create their own file descriptors.
 *
 * @author  Pavani Diwanji
 * @since   1.0
 */
public final class FileDescriptor {

    private int fd;

    private long handle;

    private Closeable parent;
    private List<Closeable> otherParents;
    private boolean closed;

    /**
     * true, if file is opened for appending.
     */
    private boolean append;

    static {
        initIDs();
    }

    // Set up JavaIOFileDescriptorAccess in SharedSecrets
    static {
        SharedSecrets.setJavaIOFileDescriptorAccess(
                new JavaIOFileDescriptorAccess() {
                    public void set(FileDescriptor fdo, int fd) {
                        fdo.fd = fd;
                    }

                    public int get(FileDescriptor fdo) {
                        return fdo.fd;
                    }

                    public void setAppend(FileDescriptor fdo, boolean append) {
                        fdo.append = append;
                    }

                    public boolean getAppend(FileDescriptor fdo) {
                        return fdo.append;
                    }

                    public void close(FileDescriptor fdo) throws IOException {
                        fdo.close();
                    }

                    public void registerCleanup(FileDescriptor fdo) {
                        fdo.registerCleanup();
                    }

                    public void setHandle(FileDescriptor fdo, long handle) {
                        fdo.setHandle(handle);
                    }

                    public long getHandle(FileDescriptor fdo) {
                        return fdo.handle;
                    }
                }
        );
    }

    /**
     * Cleanup in case FileDescriptor is not explicitly closed.
     */
    private FDCleanup cleanup;

    /**
     * Constructs an (invalid) FileDescriptor
     * object.
     */
    public FileDescriptor() {
        fd = -1;
        handle = -1;
    }

    /**
     * A handle to the standard input stream. Usually, this file
     * descriptor is not used directly, but rather via the input stream
     * known as {@code System.in}.
     *
     * @see     java.lang.System#in
     */
    public static final FileDescriptor in = standardStream(0);

    /**
     * A handle to the standard output stream. Usually, this file
     * descriptor is not used directly, but rather via the output stream
     * known as {@code System.out}.
     * @see     java.lang.System#out
     */
    public static final FileDescriptor out = standardStream(1);

    /**
     * A handle to the standard error stream. Usually, this file
     * descriptor is not used directly, but rather via the output stream
     * known as {@code System.err}.
     *
     * @see     java.lang.System#err
     */
    public static final FileDescriptor err = standardStream(2);

    /**
     * Tests if this file descriptor object is valid.
     *
     * @return  {@code true} if the file descriptor object represents a
     *          valid, open file, socket, or other active I/O connection;
     *          {@code false} otherwise.
     */
    public boolean valid() {
        return (handle != -1) || (fd != -1);
    }

    /**
     * Force all system buffers to synchronize with the underlying
     * device.  This method returns after all modified data and
     * attributes of this FileDescriptor have been written to the
     * relevant device(s).  In particular, if this FileDescriptor
     * refers to a physical storage medium, such as a file in a file
     * system, sync will not return until all in-memory modified copies
     * of buffers associated with this FileDescriptor have been
     * written to the physical medium.
     *
     * sync is meant to be used by code that requires physical
     * storage (such as a file) to be in a known state  For
     * example, a class that provided a simple transaction facility
     * might use sync to ensure that all changes to a file caused
     * by a given transaction were recorded on a storage medium.
     *
     * sync only affects buffers downstream of this FileDescriptor.  If
     * any in-memory buffering is being done by the application (for
     * example, by a BufferedOutputStream object), those buffers must
     * be flushed into the FileDescriptor (for example, by invoking
     * OutputStream.flush) before that data will be affected by sync.
     *
     * @exception SyncFailedException
     *        Thrown when the buffers cannot be flushed,
     *        or because the system cannot guarantee that all the
     *        buffers have been synchronized with physical media.
     * @since     1.1
     */
    public native void sync() throws SyncFailedException;

    /* This routine initializes JNI field offsets for the class */
    private static native void initIDs();

    private static FileDescriptor standardStream(int fd) {
        FileDescriptor desc = new FileDescriptor();
        desc.handle = getHandle(fd);
        return desc;
    }

    private static native long getHandle(int d);

    /**
     * Set the handle.
     * If setting to -1, clear the cleaner.
     * The {@link #registerCleanup()} method should be called for new handles.
     * @param handle the handle or -1 to indicate closed
     */
    @SuppressWarnings("unchecked")
    void setHandle(long handle) {
        if (handle == -1 && cleanup != null) {
            cleanup.clear();
            cleanup = null;
        }
        this.handle = handle;
    }

    /**
     * Register a cleanup for the current handle.
     * Used directly in java.io and indirectly via fdAccess.
     * The cleanup should be registered after the handle is set in the FileDescriptor.
     */
    @SuppressWarnings("unchecked")
    synchronized void registerCleanup() {
        if (cleanup != null) {
            cleanup.clear();
        }
        cleanup = FDCleanup.create(this);
    }

    /**
     * Close the raw file descriptor or handle, if it has not already been closed
     * and set the fd and handle to -1.
     * Clear the cleaner so the close does not happen twice.
     * Package private to allow it to be used in java.io.
     * @throws IOException if close fails
     */
    @SuppressWarnings("unchecked")
    synchronized void close() throws IOException {
        if (cleanup != null) {
            cleanup.clear();
            cleanup = null;
        }
        close0();
    }

    /*
     * Close the raw file descriptor or handle, if it has not already been closed
     * and set the fd and handle to -1.
     */
    private native void close0() throws IOException;

    /*
    * Raw close of the file handle.
    * Used only for last chance cleanup.
    */
    private static native void cleanupClose0(long handle) throws IOException;

    /*
     * Package private methods to track referents.
     * If multiple streams point to the same FileDescriptor, we cycle
     * through the list of all referents and call close()
     */

    /**
     * Attach a Closeable to this FD for tracking.
     * parent reference is added to otherParents when
     * needed to make closeAll simpler.
     */
    synchronized void attach(Closeable c) {
        if (parent == null) {
            // first caller gets to do this
            parent = c;
        } else if (otherParents == null) {
            otherParents = new ArrayList<>();
            otherParents.add(parent);
            otherParents.add(c);
        } else {
            otherParents.add(c);
        }
    }

    /**
     * Cycle through all Closeables sharing this FD and call
     * close() on each one.
     *
     * The caller closeable gets to call close0().
     */
    @SuppressWarnings("try")
    synchronized void closeAll(Closeable releaser) throws IOException {
        if (!closed) {
            closed = true;
            IOException ioe = null;
            try (releaser) {
                if (otherParents != null) {
                    for (Closeable referent : otherParents) {
                        try {
                            referent.close();
                        } catch(IOException x) {
                            if (ioe == null) {
                                ioe = x;
                            } else {
                                ioe.addSuppressed(x);
                            }
                        }
                    }
                }
            } catch(IOException ex) {
                /*
                 * If releaser close() throws IOException
                 * add other exceptions as suppressed.
                 */
                if (ioe != null)
                    ex.addSuppressed(ioe);
                ioe = ex;
            } finally {
                if (ioe != null)
                    throw ioe;
            }
        }
    }

    /**
     * Cleanup for a FileDescriptor when it becomes phantom reachable.
     * Create a cleanup if handle != -1.
     * Subclassed from {@code PhantomCleanable} so that {@code clear} can be
     * called to disable the cleanup when the fd is closed by any means other
     * than calling {@link FileDescriptor#close}.
     * Otherwise, it may close the handle after it has been reused.
     */
    static final class FDCleanup extends PhantomCleanable<Object> {
        private final long handle;

        static FDCleanup create(FileDescriptor fdo) {
            return fdo.handle == -1
                    ? null
                    : new FDCleanup(fdo, CleanerFactory.cleaner(), fdo.handle);
        }

        /**
         * Constructor for a phantom cleanable reference.
         * @param obj the object to monitor
         * @param cleaner the cleaner
         * @param handle file handle to close
         */
        private FDCleanup(Object obj, Cleaner cleaner, long handle) {
            super(obj, cleaner);
            this.handle = handle;
        }

        /**
         * Close the native handle.
         */
        @Override
        protected void performCleanup() {
            try {
                cleanupClose0(handle);
            } catch (IOException ioe) {
                throw new UncheckedIOException("close", ioe);
            }
        }
    }
}
