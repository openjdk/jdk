/*
 * Copyright (c) 1994, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.channels.FileChannel;
import jdk.internal.access.SharedSecrets;
import jdk.internal.access.JavaIOFileDescriptorAccess;
import jdk.internal.misc.Blocker;
import sun.nio.ch.FileChannelImpl;


/**
 * A file output stream is an output stream for writing data to a
 * {@code File} or to a {@code FileDescriptor}. Whether or not
 * a file is available or may be created depends upon the underlying
 * platform.  Some platforms, in particular, allow a file to be opened
 * for writing by only one {@code FileOutputStream} (or other
 * file-writing object) at a time.  In such situations the constructors in
 * this class will fail if the file involved is already open.
 *
 * <p>{@code FileOutputStream} is meant for writing streams of raw bytes
 * such as image data. For writing streams of characters, consider using
 * {@code FileWriter}.
 *
 * @apiNote
 * The {@link #close} method should be called to release resources used by this
 * stream, either directly, or with the {@code try}-with-resources statement.
 *
 * @implSpec
 * Subclasses are responsible for the cleanup of resources acquired by the subclass.
 * Subclasses requiring that resource cleanup take place after a stream becomes
 * unreachable should use {@link java.lang.ref.Cleaner} or some other mechanism.
 *
 * @author  Arthur van Hoff
 * @see     java.io.File
 * @see     java.io.FileDescriptor
 * @see     java.io.FileInputStream
 * @see     java.nio.file.Files#newOutputStream
 * @since   1.0
 */
public class FileOutputStream extends OutputStream
{
    /**
     * Access to FileDescriptor internals.
     */
    private static final JavaIOFileDescriptorAccess FD_ACCESS =
        SharedSecrets.getJavaIOFileDescriptorAccess();

    /**
     * The system dependent file descriptor.
     */
    private final FileDescriptor fd;

    /**
     * The associated channel, initialized lazily.
     */
    private volatile FileChannel channel;

    /**
     * The path of the referenced file
     * (null if the stream is created with a file descriptor)
     */
    private final String path;

    private final Object closeLock = new Object();

    private volatile boolean closed;

    /**
     * Creates a file output stream to write to the file with the
     * specified name. A new {@code FileDescriptor} object is
     * created to represent this file connection.
     * <p>
     * First, if there is a security manager, its {@code checkWrite}
     * method is called with {@code name} as its argument.
     * <p>
     * If the file exists but is a directory rather than a regular file, does
     * not exist but cannot be created, or cannot be opened for any other
     * reason then a {@code FileNotFoundException} is thrown.
     *
     * @implSpec Invoking this constructor with the parameter {@code name} is
     * equivalent to invoking {@link #FileOutputStream(String,boolean)
     * new FileOutputStream(name, false)}.
     *
     * @param      name   the system-dependent filename
     * @throws     FileNotFoundException  if the file exists but is a directory
     *                   rather than a regular file, does not exist but cannot
     *                   be created, or cannot be opened for any other reason
     * @throws     SecurityException  if a security manager exists and its
     *               {@code checkWrite} method denies write access
     *               to the file.
     * @see        java.lang.SecurityManager#checkWrite(java.lang.String)
     */
    public FileOutputStream(String name) throws FileNotFoundException {
        this(name != null ? new File(name) : null, false);
    }

    /**
     * Creates a file output stream to write to the file with the specified
     * name.  If the second argument is {@code true}, then
     * bytes will be written to the end of the file rather than the beginning.
     * A new {@code FileDescriptor} object is created to represent this
     * file connection.
     * <p>
     * First, if there is a security manager, its {@code checkWrite}
     * method is called with {@code name} as its argument.
     * <p>
     * If the file exists but is a directory rather than a regular file, does
     * not exist but cannot be created, or cannot be opened for any other
     * reason then a {@code FileNotFoundException} is thrown.
     *
     * @param     name        the system-dependent file name
     * @param     append      if {@code true}, then bytes will be written
     *                   to the end of the file rather than the beginning
     * @throws     FileNotFoundException  if the file exists but is a directory
     *                   rather than a regular file, does not exist but cannot
     *                   be created, or cannot be opened for any other reason.
     * @throws     SecurityException  if a security manager exists and its
     *               {@code checkWrite} method denies write access
     *               to the file.
     * @see        java.lang.SecurityManager#checkWrite(java.lang.String)
     * @since     1.1
     */
    public FileOutputStream(String name, boolean append)
        throws FileNotFoundException
    {
        this(name != null ? new File(name) : null, append);
    }

    /**
     * Creates a file output stream to write to the file represented by
     * the specified {@code File} object. A new
     * {@code FileDescriptor} object is created to represent this
     * file connection.
     * <p>
     * First, if there is a security manager, its {@code checkWrite}
     * method is called with the path represented by the {@code file}
     * argument as its argument.
     * <p>
     * If the file exists but is a directory rather than a regular file, does
     * not exist but cannot be created, or cannot be opened for any other
     * reason then a {@code FileNotFoundException} is thrown.
     *
     * @param      file               the file to be opened for writing.
     * @throws     FileNotFoundException  if the file exists but is a directory
     *                   rather than a regular file, does not exist but cannot
     *                   be created, or cannot be opened for any other reason
     * @throws     SecurityException  if a security manager exists and its
     *               {@code checkWrite} method denies write access
     *               to the file.
     * @see        java.io.File#getPath()
     * @see        java.lang.SecurityException
     * @see        java.lang.SecurityManager#checkWrite(java.lang.String)
     */
    public FileOutputStream(File file) throws FileNotFoundException {
        this(file, false);
    }

    /**
     * Creates a file output stream to write to the file represented by
     * the specified {@code File} object. If the second argument is
     * {@code true}, then bytes will be written to the end of the file
     * rather than the beginning. A new {@code FileDescriptor} object is
     * created to represent this file connection.
     * <p>
     * First, if there is a security manager, its {@code checkWrite}
     * method is called with the path represented by the {@code file}
     * argument as its argument.
     * <p>
     * If the file exists but is a directory rather than a regular file, does
     * not exist but cannot be created, or cannot be opened for any other
     * reason then a {@code FileNotFoundException} is thrown.
     *
     * @param      file               the file to be opened for writing.
     * @param     append      if {@code true}, then bytes will be written
     *                   to the end of the file rather than the beginning
     * @throws     FileNotFoundException  if the file exists but is a directory
     *                   rather than a regular file, does not exist but cannot
     *                   be created, or cannot be opened for any other reason
     * @throws     SecurityException  if a security manager exists and its
     *               {@code checkWrite} method denies write access
     *               to the file.
     * @see        java.io.File#getPath()
     * @see        java.lang.SecurityException
     * @see        java.lang.SecurityManager#checkWrite(java.lang.String)
     * @since 1.4
     */
    public FileOutputStream(File file, boolean append)
        throws FileNotFoundException
    {
        String name = (file != null ? file.getPath() : null);
        @SuppressWarnings("removal")
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkWrite(name);
        }
        if (name == null) {
            throw new NullPointerException();
        }
        if (file.isInvalid()) {
            throw new FileNotFoundException("Invalid file path");
        }
        this.fd = new FileDescriptor();
        fd.attach(this);
        this.path = name;

        open(name, append);
        FileCleanable.register(fd);   // open sets the fd, register the cleanup
    }

    /**
     * Creates a file output stream to write to the specified file
     * descriptor, which represents an existing connection to an actual
     * file in the file system.
     * <p>
     * First, if there is a security manager, its {@code checkWrite}
     * method is called with the file descriptor {@code fdObj}
     * argument as its argument.
     * <p>
     * If {@code fdObj} is null then a {@code NullPointerException}
     * is thrown.
     * <p>
     * This constructor does not throw an exception if {@code fdObj}
     * is {@link java.io.FileDescriptor#valid() invalid}.
     * However, if the methods are invoked on the resulting stream to attempt
     * I/O on the stream, an {@code IOException} is thrown.
     *
     * @param      fdObj   the file descriptor to be opened for writing
     * @throws     SecurityException  if a security manager exists and its
     *               {@code checkWrite} method denies
     *               write access to the file descriptor
     * @see        java.lang.SecurityManager#checkWrite(java.io.FileDescriptor)
     */
    public FileOutputStream(FileDescriptor fdObj) {
        @SuppressWarnings("removal")
        SecurityManager security = System.getSecurityManager();
        if (fdObj == null) {
            throw new NullPointerException();
        }
        if (security != null) {
            security.checkWrite(fdObj);
        }
        this.fd = fdObj;
        this.path = null;

        fd.attach(this);
    }

    /**
     * Opens a file, with the specified name, for overwriting or appending.
     * @param name name of file to be opened
     * @param append whether the file is to be opened in append mode
     */
    private native void open0(String name, boolean append)
        throws FileNotFoundException;

    // wrap native call to allow instrumentation
    /**
     * Opens a file, with the specified name, for overwriting or appending.
     * @param name name of file to be opened
     * @param append whether the file is to be opened in append mode
     */
    private void open(String name, boolean append) throws FileNotFoundException {
        long comp = Blocker.begin();
        try {
            open0(name, append);
        } finally {
            Blocker.end(comp);
        }
    }

    /**
     * Writes the specified byte to this file output stream.
     *
     * @param   b   the byte to be written.
     * @param   append   {@code true} if the write operation first
     *     advances the position to the end of file
     */
    private native void write(int b, boolean append) throws IOException;

    /**
     * Writes the specified byte to this file output stream. Implements
     * the {@code write} method of {@code OutputStream}.
     *
     * @param      b   the byte to be written.
     * @throws     IOException  if an I/O error occurs.
     */
    @Override
    public void write(int b) throws IOException {
        boolean append = FD_ACCESS.getAppend(fd);
        long comp = Blocker.begin();
        try {
            write(b, append);
        } finally {
            Blocker.end(comp);
        }
    }

    /**
     * Writes a sub array as a sequence of bytes.
     * @param b the data to be written
     * @param off the start offset in the data
     * @param len the number of bytes that are written
     * @param append {@code true} to first advance the position to the
     *     end of file
     * @throws    IOException If an I/O error has occurred.
     */
    private native void writeBytes(byte[] b, int off, int len, boolean append)
        throws IOException;

    /**
     * Writes {@code b.length} bytes from the specified byte array
     * to this file output stream.
     *
     * @param      b   {@inheritDoc}
     * @throws     IOException  {@inheritDoc}
     */
    @Override
    public void write(byte[] b) throws IOException {
        boolean append = FD_ACCESS.getAppend(fd);
        long comp = Blocker.begin();
        try {
            writeBytes(b, 0, b.length, append);
        } finally {
            Blocker.end(comp);
        }
    }

    /**
     * Writes {@code len} bytes from the specified byte array
     * starting at offset {@code off} to this file output stream.
     *
     * @param      b     {@inheritDoc}
     * @param      off   {@inheritDoc}
     * @param      len   {@inheritDoc}
     * @throws     IOException  if an I/O error occurs.
     * @throws     IndexOutOfBoundsException {@inheritDoc}
     */
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        boolean append = FD_ACCESS.getAppend(fd);
        long comp = Blocker.begin();
        try {
            writeBytes(b, off, len, append);
        } finally {
            Blocker.end(comp);
        }
    }

    /**
     * Closes this file output stream and releases any system resources
     * associated with this stream. This file output stream may no longer
     * be used for writing bytes.
     *
     * <p> If this stream has an associated channel then the channel is closed
     * as well.
     *
     * @apiNote
     * Overriding {@link #close} to perform cleanup actions is reliable
     * only when called directly or when called by try-with-resources.
     *
     * @implSpec
     * Subclasses requiring that resource cleanup take place after a stream becomes
     * unreachable should use the {@link java.lang.ref.Cleaner} mechanism.
     *
     * <p>
     * If this stream has an associated channel then this method will close the
     * channel, which in turn will close this stream. Subclasses that override
     * this method should be prepared to handle possible reentrant invocation.
     *
     * @throws     IOException  if an I/O error occurs.
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        synchronized (closeLock) {
            if (closed) {
                return;
            }
            closed = true;
        }

        FileChannel fc = channel;
        if (fc != null) {
            // possible race with getChannel(), benign since
            // FileChannel.close is final and idempotent
            fc.close();
        }

        fd.closeAll(new Closeable() {
            public void close() throws IOException {
               fd.close();
           }
        });
    }

    /**
     * Returns the file descriptor associated with this stream.
     *
     * @return  the {@code FileDescriptor} object that represents
     *          the connection to the file in the file system being used
     *          by this {@code FileOutputStream} object.
     *
     * @throws     IOException  if an I/O error occurs.
     * @see        java.io.FileDescriptor
     */
     public final FileDescriptor getFD()  throws IOException {
        if (fd != null) {
            return fd;
        }
        throw new IOException();
     }

    /**
     * Returns the unique {@link java.nio.channels.FileChannel FileChannel}
     * object associated with this file output stream.
     *
     * <p> The initial {@link java.nio.channels.FileChannel#position()
     * position} of the returned channel will be equal to the
     * number of bytes written to the file so far unless this stream is in
     * append mode, in which case it will be equal to the size of the file.
     * Writing bytes to this stream will increment the channel's position
     * accordingly.  Changing the channel's position, either explicitly or by
     * writing, will change this stream's file position.
     *
     * @return  the file channel associated with this file output stream
     *
     * @since 1.4
     */
    public FileChannel getChannel() {
        FileChannel fc = this.channel;
        if (fc == null) {
            synchronized (this) {
                fc = this.channel;
                if (fc == null) {
                    this.channel = fc = FileChannelImpl.open(fd, path, false,
                        true, false, this);
                    if (closed) {
                        try {
                            // possible race with close(), benign since
                            // FileChannel.close is final and idempotent
                            fc.close();
                        } catch (IOException ioe) {
                            throw new InternalError(ioe); // should not happen
                        }
                    }
                }
            }
        }
        return fc;
    }

    private static native void initIDs();

    static {
        initIDs();
    }
}
