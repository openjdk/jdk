/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.ch;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.Arena;
import java.lang.ref.Cleaner.Cleanable;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.Channel;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.FileLockInterruptionException;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Objects;

import jdk.internal.access.JavaIOFileDescriptorAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.foreign.SegmentFactories;
import jdk.internal.misc.Blocker;
import jdk.internal.misc.ExtendedMapMode;
import jdk.internal.misc.Unsafe;
import jdk.internal.misc.VM;
import jdk.internal.misc.VM.BufferPool;
import jdk.internal.ref.Cleaner;
import jdk.internal.ref.CleanerFactory;

import jdk.internal.access.foreign.UnmapperProxy;

public class FileChannelImpl
    extends FileChannel
{
    // Access to FileDescriptor internals
    private static final JavaIOFileDescriptorAccess fdAccess =
        SharedSecrets.getJavaIOFileDescriptorAccess();

    // Used to make native read and write calls
    private static final FileDispatcher nd = new FileDispatcherImpl();

    // File descriptor
    private final FileDescriptor fd;

    // File access mode (immutable)
    private final boolean writable;
    private final boolean readable;

    // Required to prevent finalization of creating stream (immutable)
    private final Closeable parent;

    // The path of the referenced file
    // (null if the parent stream is created with a file descriptor)
    private final String path;

    // Thread-safe set of IDs of native threads, for signalling
    private final NativeThreadSet threads = new NativeThreadSet(2);

    // Lock for operations involving position and size
    private final Object positionLock = new Object();

    // blocking operations are not interruptible
    private volatile boolean uninterruptible;

    // DirectIO flag
    private final boolean direct;

    // IO alignment value for DirectIO
    private final int alignment;

    // Cleanable with an action which closes this channel's file descriptor
    private final Cleanable closer;

    private static class Closer implements Runnable {
        private final FileDescriptor fd;

        Closer(FileDescriptor fd) {
            this.fd = fd;
        }

        public void run() {
            try {
                fdAccess.close(fd);
            } catch (IOException ioe) {
                // Rethrow as unchecked so the exception can be propagated as needed
                throw new UncheckedIOException("close", ioe);
            }
        }
    }

    private FileChannelImpl(FileDescriptor fd, String path, boolean readable,
                            boolean writable, boolean direct, Closeable parent)
    {
        this.fd = fd;
        this.path = path;
        this.readable = readable;
        this.writable = writable;
        this.direct = direct;
        this.parent = parent;
        if (direct) {
            assert path != null;
            this.alignment = nd.setDirectIO(fd, path);
        } else {
            this.alignment = -1;
        }

        // Register a cleaning action if and only if there is no parent
        // as the parent will take care of closing the file descriptor.
        // FileChannel is used by the LambdaMetaFactory so a lambda cannot
        // be used here hence we use a nested class instead.
        this.closer = parent != null ? null :
            CleanerFactory.cleaner().register(this, new Closer(fd));
    }


    // Used by FileInputStream::getChannel, FileOutputStream::getChannel,
    // and RandomAccessFile::getChannel
    public static FileChannel open(FileDescriptor fd, String path,
                                   boolean readable, boolean writable,
                                   boolean direct, Closeable parent)
    {
        return new FileChannelImpl(fd, path, readable, writable, direct, parent);
    }

    private void ensureOpen() throws IOException {
        if (!isOpen())
            throw new ClosedChannelException();
    }

    public void setUninterruptible() {
        uninterruptible = true;
    }

    private void beginBlocking() {
        if (!uninterruptible) begin();
    }

    private void endBlocking(boolean completed) throws AsynchronousCloseException {
        if (!uninterruptible) end(completed);
    }

    // -- Standard channel operations --

    protected void implCloseChannel() throws IOException {
        if (!fd.valid())
            return; // nothing to do

        // Release and invalidate any locks that we still hold
        if (fileLockTable != null) {
            for (FileLock fl: fileLockTable.removeAll()) {
                synchronized (fl) {
                    if (fl.isValid()) {
                        nd.release(fd, fl.position(), fl.size());
                        ((FileLockImpl)fl).invalidate();
                    }
                }
            }
        }

        // signal any threads blocked on this channel
        threads.signalAndWait();

        if (parent != null) {
            //
            // Close the fd via the parent stream's close method.  The parent
            // will reinvoke our close method, which is defined in the
            // superclass AbstractInterruptibleChannel, but the isOpen logic in
            // that method will prevent this method from being reinvoked.
            //
            parent.close();
        } else { // parent == null hence closer != null
            //
            // Perform the cleaning action so it is not redone when
            // this channel becomes phantom reachable.
            //
            try {
                closer.clean();
            } catch (UncheckedIOException uioe) {
                throw uioe.getCause();
            }
        }
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        ensureOpen();
        if (!readable)
            throw new NonReadableChannelException();
        synchronized (positionLock) {
            if (direct)
                Util.checkChannelPositionAligned(position(), alignment);
            int n = 0;
            int ti = -1;
            try {
                beginBlocking();
                ti = threads.add();
                if (!isOpen())
                    return 0;
                do {
                    long comp = Blocker.begin();
                    try {
                        n = IOUtil.read(fd, dst, -1, direct, alignment, nd);
                    } finally {
                        Blocker.end(comp);
                    }
                } while ((n == IOStatus.INTERRUPTED) && isOpen());
                return IOStatus.normalize(n);
            } finally {
                threads.remove(ti);
                endBlocking(n > 0);
                assert IOStatus.check(n);
            }
        }
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length)
        throws IOException
    {
        Objects.checkFromIndexSize(offset, length, dsts.length);
        ensureOpen();
        if (!readable)
            throw new NonReadableChannelException();
        synchronized (positionLock) {
            if (direct)
                Util.checkChannelPositionAligned(position(), alignment);
            long n = 0;
            int ti = -1;
            try {
                beginBlocking();
                ti = threads.add();
                if (!isOpen())
                    return 0;
                do {
                    long comp = Blocker.begin();
                    try {
                        n = IOUtil.read(fd, dsts, offset, length, direct, alignment, nd);
                    } finally {
                        Blocker.end(comp);
                    }

                } while ((n == IOStatus.INTERRUPTED) && isOpen());
                return IOStatus.normalize(n);
            } finally {
                threads.remove(ti);
                endBlocking(n > 0);
                assert IOStatus.check(n);
            }
        }
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        ensureOpen();
        if (!writable)
            throw new NonWritableChannelException();
        synchronized (positionLock) {
            if (direct)
                Util.checkChannelPositionAligned(position(), alignment);
            int n = 0;
            int ti = -1;
            try {
                beginBlocking();
                ti = threads.add();
                if (!isOpen())
                    return 0;
                do {
                    long comp = Blocker.begin();
                    try {
                        n = IOUtil.write(fd, src, -1, direct, alignment, nd);
                    } finally {
                        Blocker.end(comp);
                    }

                } while ((n == IOStatus.INTERRUPTED) && isOpen());
                return IOStatus.normalize(n);
            } finally {
                threads.remove(ti);
                endBlocking(n > 0);
                assert IOStatus.check(n);
            }
        }
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length)
        throws IOException
    {
        Objects.checkFromIndexSize(offset, length, srcs.length);
        ensureOpen();
        if (!writable)
            throw new NonWritableChannelException();
        synchronized (positionLock) {
            if (direct)
                Util.checkChannelPositionAligned(position(), alignment);
            long n = 0;
            int ti = -1;
            try {
                beginBlocking();
                ti = threads.add();
                if (!isOpen())
                    return 0;
                do {
                    long comp = Blocker.begin();
                    try {
                        n = IOUtil.write(fd, srcs, offset, length, direct, alignment, nd);
                    } finally {
                        Blocker.end(comp);
                    }
                } while ((n == IOStatus.INTERRUPTED) && isOpen());
                return IOStatus.normalize(n);
            } finally {
                threads.remove(ti);
                endBlocking(n > 0);
                assert IOStatus.check(n);
            }
        }
    }

    // -- Other operations --

    @Override
    public long position() throws IOException {
        ensureOpen();
        synchronized (positionLock) {
            long p = -1;
            int ti = -1;
            try {
                beginBlocking();
                ti = threads.add();
                if (!isOpen())
                    return 0;
                boolean append = fdAccess.getAppend(fd);
                do {
                    long comp = Blocker.begin();
                    try {
                        // in append-mode then position is advanced to end before writing
                        p = (append) ? nd.size(fd) : nd.seek(fd, -1);
                    } finally {
                        Blocker.end(comp);
                    }
                } while ((p == IOStatus.INTERRUPTED) && isOpen());
                return IOStatus.normalize(p);
            } finally {
                threads.remove(ti);
                endBlocking(p > -1);
                assert IOStatus.check(p);
            }
        }
    }

    @Override
    public FileChannel position(long newPosition) throws IOException {
        ensureOpen();
        if (newPosition < 0)
            throw new IllegalArgumentException();
        synchronized (positionLock) {
            long p = -1;
            int ti = -1;
            try {
                beginBlocking();
                ti = threads.add();
                if (!isOpen())
                    return null;
                do {
                    long comp = Blocker.begin();
                    try {
                        p = nd.seek(fd, newPosition);
                    } finally {
                        Blocker.end(comp);
                    }
                } while ((p == IOStatus.INTERRUPTED) && isOpen());
                return this;
            } finally {
                threads.remove(ti);
                endBlocking(p > -1);
                assert IOStatus.check(p);
            }
        }
    }

    @Override
    public long size() throws IOException {
        ensureOpen();
        synchronized (positionLock) {
            long s = -1;
            int ti = -1;
            try {
                beginBlocking();
                ti = threads.add();
                if (!isOpen())
                    return -1;
                do {
                    long comp = Blocker.begin();
                    try {
                        s = nd.size(fd);
                    } finally {
                        Blocker.end(comp);
                    }
                } while ((s == IOStatus.INTERRUPTED) && isOpen());
                return IOStatus.normalize(s);
            } finally {
                threads.remove(ti);
                endBlocking(s > -1);
                assert IOStatus.check(s);
            }
        }
    }

    @Override
    public FileChannel truncate(long newSize) throws IOException {
        ensureOpen();
        if (newSize < 0)
            throw new IllegalArgumentException("Negative size");
        if (!writable)
            throw new NonWritableChannelException();
        synchronized (positionLock) {
            int rv = -1;
            long p = -1;
            int ti = -1;
            long rp = -1;
            try {
                beginBlocking();
                ti = threads.add();
                if (!isOpen())
                    return null;

                // get current size
                long size;
                do {
                    long comp = Blocker.begin();
                    try {
                        size = nd.size(fd);
                    } finally {
                        Blocker.end(comp);
                    }
                } while ((size == IOStatus.INTERRUPTED) && isOpen());
                if (!isOpen())
                    return null;

                // get current position
                do {
                    long comp = Blocker.begin();
                    try {
                        p = nd.seek(fd, -1);
                    } finally {
                        Blocker.end(comp);
                    }
                } while ((p == IOStatus.INTERRUPTED) && isOpen());
                if (!isOpen())
                    return null;
                assert p >= 0;

                // truncate file if given size is less than the current size
                if (newSize < size) {
                    do {
                        long comp = Blocker.begin();
                        try {
                            rv = nd.truncate(fd, newSize);
                        } finally {
                            Blocker.end(comp);
                        }
                    } while ((rv == IOStatus.INTERRUPTED) && isOpen());
                    if (!isOpen())
                        return null;
                }

                // if position is beyond new size then adjust it
                if (p > newSize)
                    p = newSize;
                do {
                    long comp = Blocker.begin();
                    try {
                        rp = nd.seek(fd, p);
                    } finally {
                        Blocker.end(comp);
                    }
                } while ((rp == IOStatus.INTERRUPTED) && isOpen());
                return this;
            } finally {
                threads.remove(ti);
                endBlocking(rv > -1);
                assert IOStatus.check(rv);
            }
        }
    }

    @Override
    public void force(boolean metaData) throws IOException {
        ensureOpen();
        int rv = -1;
        int ti = -1;
        try {
            beginBlocking();
            ti = threads.add();
            if (!isOpen())
                return;
            do {
                long comp = Blocker.begin();
                try {
                    rv = nd.force(fd, metaData);
                } finally {
                    Blocker.end(comp);
                }
            } while ((rv == IOStatus.INTERRUPTED) && isOpen());
        } finally {
            threads.remove(ti);
            endBlocking(rv > -1);
            assert IOStatus.check(rv);
        }
    }

    // Assume at first that the underlying kernel supports sendfile/equivalent;
    // set this to true if we find out later that it doesn't
    //
    private static volatile boolean transferToDirectNotSupported;

    // Assume at first that the underlying kernel supports copy_file_range/equivalent;
    // set this to true if we find out later that it doesn't
    //
    private static volatile boolean transferFromDirectNotSupported;

    /**
     * Marks the beginning of a transfer to or from this channel.
     * @throws ClosedChannelException if channel is closed
     */
    private int beforeTransfer() throws ClosedChannelException {
        int ti = threads.add();
        if (isOpen()) {
            return ti;
        } else {
            threads.remove(ti);
            throw new ClosedChannelException();
        }
    }

    /**
     * Marks the end of a transfer to or from this channel.
     * @throws AsynchronousCloseException if not completed and the channel is closed
     */
    private void afterTransfer(boolean completed, int ti) throws AsynchronousCloseException {
        threads.remove(ti);
        if (!completed && !isOpen()) {
            throw new AsynchronousCloseException();
        }
    }

    /**
     * Invoked when ClosedChannelException is thrown during a transfer. This method
     * translates it to an AsynchronousCloseException or ClosedByInterruptException. In
     * the case of ClosedByInterruptException, it ensures that this channel and the
     * source/target channel are closed.
     */
    private AsynchronousCloseException transferFailed(ClosedChannelException cce, Channel other) {
        ClosedByInterruptException cbie = null;
        if (cce instanceof ClosedByInterruptException e) {
            assert Thread.currentThread().isInterrupted();
            cbie = e;
        } else if (!uninterruptible && Thread.currentThread().isInterrupted()) {
            cbie = new ClosedByInterruptException();
        }
        if (cbie != null) {
            try {
                this.close();
            } catch (IOException ioe) {
                cbie.addSuppressed(ioe);
            }
            try {
                other.close();
            } catch (IOException ioe) {
                cbie.addSuppressed(ioe);
            }
            return cbie;

        }
        // one of the channels was closed during the transfer
        if (cce instanceof AsynchronousCloseException ace) {
            return ace;
        } else {
            var ace = new AsynchronousCloseException();
            ace.addSuppressed(cce);
            return ace;
        }
    }

    /**
     * Transfers bytes from this channel's file to the resource that the given file
     * descriptor is connected to.
     */
    private long transferToFileDescriptor(long position, int count, FileDescriptor targetFD) {
        long n;
        boolean append = fdAccess.getAppend(targetFD);
        do {
            long comp = Blocker.begin();
            try {
                n = nd.transferTo(fd, position, count, targetFD, append);
            } finally {
                Blocker.end(comp);
            }
        } while ((n == IOStatus.INTERRUPTED) && isOpen());
        return n;
    }

    /**
     * Transfers bytes from this channel's file to the given channel's file.
     */
    private long transferToFileChannel(long position, int count, FileChannelImpl target)
        throws IOException
    {
        final FileChannelImpl source = this;
        boolean completed = false;
        try {
            beginBlocking();
            int sourceIndex = source.beforeTransfer();
            try {
                int targetIndex = target.beforeTransfer();
                try {
                    long n = transferToFileDescriptor(position, count, target.fd);
                    completed = (n >= 0);
                    return IOStatus.normalize(n);
                } finally {
                    target.afterTransfer(completed, targetIndex);
                }
            } finally {
                source.afterTransfer(completed, sourceIndex);
            }
        } finally {
            endBlocking(completed);
        }
    }

    /**
     * Transfers bytes from this channel's file to the given channel's socket.
     */
    private long transferToSocketChannel(long position, int count, SocketChannelImpl target)
        throws IOException
    {
        final FileChannelImpl source = this;
        boolean completed = false;
        try {
            beginBlocking();
            int sourceIndex = source.beforeTransfer();
            try {
                target.beforeTransferTo();
                try {
                    long n = transferToFileDescriptor(position, count, target.getFD());
                    completed = (n >= 0);
                    return IOStatus.normalize(n);
                } finally {
                    target.afterTransferTo(completed);
                }
            } finally {
                source.afterTransfer(completed, sourceIndex);
            }
        } finally {
            endBlocking(completed);
        }
    }

    /**
     * Transfers bytes from this channel's file from the given channel's file. This
     * implementation uses sendfile, copy_file_range or equivalent.
     */
    private long transferToDirectInternal(long position, int count, WritableByteChannel target)
        throws IOException
    {
        assert !nd.transferToDirectlyNeedsPositionLock() || Thread.holdsLock(positionLock);

        return switch (target) {
            case FileChannelImpl fci  -> transferToFileChannel(position, count, fci);
            case SocketChannelImpl sci -> transferToSocketChannel(position, count, sci);
            default -> IOStatus.UNSUPPORTED_CASE;
        };
    }

    /**
     * Transfers bytes from this channel's file to the given channel's file or socket.
     * @return the number of bytes transferred, UNSUPPORTED_CASE if this transfer cannot
     * be done directly, or UNSUPPORTED if there is no direct support
     */
    private long transferToDirect(long position, int count, WritableByteChannel target)
        throws IOException
    {
        if (transferToDirectNotSupported)
            return IOStatus.UNSUPPORTED;
        if (target instanceof SelectableChannel sc && !nd.canTransferToDirectly(sc))
            return IOStatus.UNSUPPORTED_CASE;

        long n;
        if (nd.transferToDirectlyNeedsPositionLock()) {
            synchronized (positionLock) {
                long pos = position();
                try {
                    n = transferToDirectInternal(position, count, target);
                } finally {
                    try {
                        position(pos);
                    } catch (ClosedChannelException ignore) {
                        // can't reset position if channel is closed
                    }
                }
            }
        } else {
            n = transferToDirectInternal(position, count, target);
        }
        if (n == IOStatus.UNSUPPORTED) {
            transferToDirectNotSupported = true;
        }
        return n;
    }

    // Size threshold above which to use a mapped buffer;
    // transferToArbitraryChannel() and transferFromArbitraryChannel()
    // are faster for smaller transfers
    private static final long MAPPED_TRANSFER_THRESHOLD = 16L*1024L;

    // Maximum size to map when using a mapped buffer
    private static final long MAPPED_TRANSFER_SIZE = 8L*1024L*1024L;

    /**
     * Transfers bytes from channel's file to the given channel. This implementation
     * memory maps this channel's file.
     */
    private long transferToTrustedChannel(long position, long count,
                                          WritableByteChannel target)
        throws IOException
    {
        if (count < MAPPED_TRANSFER_THRESHOLD)
            return IOStatus.UNSUPPORTED_CASE;

        boolean isSelChImpl = (target instanceof SelChImpl);
        if (!((target instanceof FileChannelImpl) || isSelChImpl))
            return IOStatus.UNSUPPORTED_CASE;

        if (target == this) {
            long posThis = position();
            if ((posThis - count + 1 <= position)
                    && (position - count + 1 <= posThis)
                    && !nd.canTransferToFromOverlappedMap()) {
                return IOStatus.UNSUPPORTED_CASE;
            }
        }

        long remaining = count;
        while (remaining > 0L) {
            long size = Math.min(remaining, MAPPED_TRANSFER_SIZE);
            try {
                MappedByteBuffer dbb = map(MapMode.READ_ONLY, position, size);
                try {
                    // write may block, closing this channel will not wake it up
                    int n = target.write(dbb);
                    assert n >= 0;
                    remaining -= n;
                    if (isSelChImpl) {
                        // one attempt to write to selectable channel
                        break;
                    }
                    assert n > 0;
                    position += n;
                } finally {
                    unmap(dbb);
                }
            } catch (IOException ioe) {
                // Only throw exception if no bytes have been written
                if (remaining == count)
                    throw ioe;
                break;
            }
        }
        return count - remaining;
    }

    /**
     * Transfers bytes from channel's file to the given channel.
     */
    private long transferToArbitraryChannel(long position, long count,
                                            WritableByteChannel target)
        throws IOException
    {
        // Untrusted target: Use a newly-erased buffer
        int c = (int) Math.min(count, TRANSFER_SIZE);
        ByteBuffer bb = ByteBuffer.allocate(c);
        long tw = 0;                    // Total bytes written
        long pos = position;
        try {
            while (tw < count) {
                bb.limit((int) Math.min(count - tw, TRANSFER_SIZE));
                int nr = read(bb, pos);
                if (nr <= 0)
                    break;
                bb.flip();
                // write may block, closing this channel will not wake it up
                int nw = target.write(bb);
                tw += nw;
                if (nw != nr)
                    break;
                pos += nw;
                bb.clear();
            }
            return tw;
        } catch (IOException x) {
            if (tw > 0)
                return tw;
            throw x;
        }
    }

    @Override
    public long transferTo(long position, long count, WritableByteChannel target)
        throws IOException
    {
        ensureOpen();
        if (!target.isOpen())
            throw new ClosedChannelException();
        if (!readable)
            throw new NonReadableChannelException();
        if (target instanceof FileChannelImpl && !((FileChannelImpl) target).writable)
            throw new NonWritableChannelException();
        if ((position < 0) || (count < 0))
            throw new IllegalArgumentException();

        try {
            final long sz = size();
            if (position > sz)
                return 0;

            // System calls supporting fast transfers might not work on files
            // which advertise zero size such as those in Linux /proc
            if (sz > 0) {
                // Now sz > 0 and position <= sz so remaining >= 0 and
                // remaining == 0 if and only if sz == position
                long remaining = sz - position;

                if (remaining >= 0 && remaining < count)
                    count = remaining;

                // Attempt a direct transfer, if the kernel supports it,
                // limiting the number of bytes according to which platform
                int icount = (int) Math.min(count, nd.maxDirectTransferSize());
                long n;
                if ((n = transferToDirect(position, icount, target)) >= 0)
                    return n;

                // Attempt a mapped transfer, but only to trusted channel types
                if ((n = transferToTrustedChannel(position, count, target)) >= 0)
                    return n;
            }

            // fallback to read/write loop
            return transferToArbitraryChannel(position, count, target);
        } catch (ClosedChannelException e) {
            // throw AsynchronousCloseException or ClosedByInterruptException
            throw transferFailed(e, target);
        }
    }

    /**
     * Transfers bytes into this channel's file from the resource that the given file
     * descriptor is connected to.
     */
    private long transferFromFileDescriptor(FileDescriptor srcFD, long position, long count) {
        long n;
        boolean append = fdAccess.getAppend(fd);
        do {
            long comp = Blocker.begin();
            try {
                n = nd.transferFrom(srcFD, fd, position, count, append);
            } finally {
                Blocker.end(comp);
            }
        } while ((n == IOStatus.INTERRUPTED) && isOpen());
        return n;
    }

    /**
     * Transfers bytes into this channel's file from the given channel's file. This
     * implementation uses copy_file_range or equivalent.
     */
    private long transferFromDirect(FileChannelImpl src, long position, long count)
        throws IOException
    {
        if (transferFromDirectNotSupported)
            return IOStatus.UNSUPPORTED;

        final FileChannelImpl target = this;
        boolean completed = false;
        try {
            beginBlocking();
            int srcIndex = src.beforeTransfer();
            try {
                int targetIndex = target.beforeTransfer();
                try {
                    long n = transferFromFileDescriptor(src.fd, position, count);
                    if (n == IOStatus.UNSUPPORTED) {
                        transferFromDirectNotSupported = true;
                        return IOStatus.UNSUPPORTED;
                    }
                    completed = (n >= 0);
                    return IOStatus.normalize(n);
                } finally {
                    target.afterTransfer(completed, targetIndex);
                }
            } finally {
                src.afterTransfer(completed, srcIndex);
            }
        } finally {
            endBlocking(completed);
        }
    }

    /**
     * Transfers bytes into this channel's file from the given channel's file. This
     * implementation memory maps the given channel's file.
     */
    private long transferFromFileChannel(FileChannelImpl src, long position, long count)
        throws IOException
    {
        if (count < MAPPED_TRANSFER_THRESHOLD)
            return IOStatus.UNSUPPORTED_CASE;

        synchronized (src.positionLock) {
            long pos = src.position();
            long max = Math.min(count, src.size() - pos);

            if (src == this
                    && (position() - max + 1 <= pos)
                    && (pos - max + 1 <= position())
                    && !nd.canTransferToFromOverlappedMap()) {
                return IOStatus.UNSUPPORTED_CASE;
            }

            long remaining = max;
            long p = pos;
            while (remaining > 0L) {
                long size = Math.min(remaining, MAPPED_TRANSFER_SIZE);
                MappedByteBuffer bb = src.map(MapMode.READ_ONLY, p, size);
                try {
                    long n = write(bb, position);
                    assert n > 0;
                    p += n;
                    position += n;
                    remaining -= n;
                } catch (IOException ioe) {
                    // Only throw exception if no bytes have been written
                    if (remaining == max)
                        throw ioe;
                    break;
                } finally {
                    unmap(bb);
                }
            }
            long nwritten = max - remaining;
            src.position(pos + nwritten);
            return nwritten;
        }
    }

    private static final int TRANSFER_SIZE = 8192;

    /**
     * Transfers bytes into this channel's file from the given channel.
     */
    private long transferFromArbitraryChannel(ReadableByteChannel src,
                                              long position, long count)
        throws IOException
    {
        int c = (int) Math.min(count, TRANSFER_SIZE);
        ByteBuffer bb = ByteBuffer.allocate(c);
        long tw = 0;                    // Total bytes written
        long pos = position;
        try {
            while (tw < count) {
                bb.limit((int) Math.min((count - tw), TRANSFER_SIZE));
                // read may block, closing this channel will not wake it up
                int nr = src.read(bb);
                if (nr <= 0)
                    break;
                bb.flip();
                int nw = write(bb, pos);
                tw += nw;
                if (nw != nr)
                    break;
                pos += nw;
                bb.clear();
            }
            return tw;
        } catch (IOException x) {
            if (tw > 0)
                return tw;
            throw x;
        }
    }

    @Override
    public long transferFrom(ReadableByteChannel src, long position, long count)
        throws IOException
    {
        ensureOpen();
        if (!src.isOpen())
            throw new ClosedChannelException();
        if (src instanceof FileChannelImpl fci && !fci.readable)
            throw new NonReadableChannelException();
        if (!writable)
            throw new NonWritableChannelException();
        if ((position < 0) || (count < 0))
            throw new IllegalArgumentException();

        try {
            // System calls supporting fast transfers might not work on files
            // which advertise zero size such as those in Linux /proc
            if (src instanceof FileChannelImpl fci && fci.size() > 0) {
                long n;
                if ((n = transferFromDirect(fci, position, count)) >= 0)
                    return n;
                if ((n = transferFromFileChannel(fci, position, count)) >= 0)
                    return n;
            }

            // fallback to read/write loop
            return transferFromArbitraryChannel(src, position, count);
        } catch (ClosedChannelException e) {
            // throw AsynchronousCloseException or ClosedByInterruptException
            throw transferFailed(e, src);
        }
    }

    @Override
    public int read(ByteBuffer dst, long position) throws IOException {
        if (dst == null)
            throw new NullPointerException();
        if (position < 0)
            throw new IllegalArgumentException("Negative position");
        ensureOpen();
        if (!readable)
            throw new NonReadableChannelException();
        if (direct)
            Util.checkChannelPositionAligned(position, alignment);
        if (nd.needsPositionLock()) {
            synchronized (positionLock) {
                return readInternal(dst, position);
            }
        } else {
            return readInternal(dst, position);
        }
    }

    private int readInternal(ByteBuffer dst, long position) throws IOException {
        assert !nd.needsPositionLock() || Thread.holdsLock(positionLock);
        int n = 0;
        int ti = -1;

        try {
            beginBlocking();
            ti = threads.add();
            if (!isOpen())
                return -1;
            do {
                long comp = Blocker.begin();
                try {
                    n = IOUtil.read(fd, dst, position, direct, alignment, nd);
                } finally {
                    Blocker.end(comp);
                }
            } while ((n == IOStatus.INTERRUPTED) && isOpen());
            return IOStatus.normalize(n);
        } finally {
            threads.remove(ti);
            endBlocking(n > 0);
            assert IOStatus.check(n);
        }
    }

    @Override
    public int write(ByteBuffer src, long position) throws IOException {
        if (src == null)
            throw new NullPointerException();
        if (position < 0)
            throw new IllegalArgumentException("Negative position");
        ensureOpen();
        if (!writable)
            throw new NonWritableChannelException();
        if (direct)
            Util.checkChannelPositionAligned(position, alignment);
        if (nd.needsPositionLock()) {
            synchronized (positionLock) {
                return writeInternal(src, position);
            }
        } else {
            return writeInternal(src, position);
        }
    }

    private int writeInternal(ByteBuffer src, long position) throws IOException {
        assert !nd.needsPositionLock() || Thread.holdsLock(positionLock);
        int n = 0;
        int ti = -1;
        try {
            beginBlocking();
            ti = threads.add();
            if (!isOpen())
                return -1;
            do {
                long comp = Blocker.begin();
                try {
                    n = IOUtil.write(fd, src, position, direct, alignment, nd);
                } finally {
                    Blocker.end(comp);
                }
            } while ((n == IOStatus.INTERRUPTED) && isOpen());
            return IOStatus.normalize(n);
        } finally {
            threads.remove(ti);
            endBlocking(n > 0);
            assert IOStatus.check(n);
        }
    }


    // -- Memory-mapped buffers --

    private abstract static sealed class Unmapper
        implements Runnable, UnmapperProxy
    {
        private final long address;
        protected final long size;
        protected final long cap;
        private final FileDescriptor fd;
        private final int pagePosition;

        private Unmapper(long address, long size, long cap,
                         FileDescriptor fd, int pagePosition)
        {
            assert (address != 0);
            this.address = address;
            this.size = size;
            this.cap = cap;
            this.fd = fd;
            this.pagePosition = pagePosition;
        }

        @Override
        public long address() {
            return address + pagePosition;
        }

        @Override
        public FileDescriptor fileDescriptor() {
            return fd;
        }

        @Override
        public void run() {
            unmap();
        }

        public long capacity() {
            return cap;
        }

        public void unmap() {
            nd.unmap(address, size);

            // if this mapping has a valid file descriptor then we close it
            if (fd.valid()) {
                try {
                    nd.close(fd);
                } catch (IOException ignore) {
                    // nothing we can do
                }
            }

            decrementStats();
        }
        protected abstract void incrementStats();
        protected abstract void decrementStats();
    }

    private static final class DefaultUnmapper extends Unmapper {

        // keep track of non-sync mapped buffer usage
        static volatile int count;
        static volatile long totalSize;
        static volatile long totalCapacity;

        public DefaultUnmapper(long address, long size, long cap,
                               FileDescriptor fd, int pagePosition) {
            super(address, size, cap, fd, pagePosition);
            incrementStats();
        }

        protected void incrementStats() {
            synchronized (DefaultUnmapper.class) {
                count++;
                totalSize += size;
                totalCapacity += cap;
            }
        }
        protected void decrementStats() {
            synchronized (DefaultUnmapper.class) {
                count--;
                totalSize -= size;
                totalCapacity -= cap;
            }
        }

        public boolean isSync() {
            return false;
        }
    }

    private static final class SyncUnmapper extends Unmapper {

        // keep track of mapped buffer usage
        static volatile int count;
        static volatile long totalSize;
        static volatile long totalCapacity;

        public SyncUnmapper(long address, long size, long cap,
                            FileDescriptor fd, int pagePosition) {
            super(address, size, cap, fd, pagePosition);
            incrementStats();
        }

        protected void incrementStats() {
            synchronized (SyncUnmapper.class) {
                count++;
                totalSize += size;
                totalCapacity += cap;
            }
        }
        protected void decrementStats() {
            synchronized (SyncUnmapper.class) {
                count--;
                totalSize -= size;
                totalCapacity -= cap;
            }
        }

        public boolean isSync() {
            return true;
        }
    }

    private static void unmap(MappedByteBuffer bb) {
        Cleaner cl = ((DirectBuffer)bb).cleaner();
        if (cl != null)
            cl.clean();
    }

    private static final int MAP_INVALID = -1;
    private static final int MAP_RO = 0;
    private static final int MAP_RW = 1;
    private static final int MAP_PV = 2;

    @Override
    public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
        if (size > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Size exceeds Integer.MAX_VALUE");
        boolean isSync = isSync(Objects.requireNonNull(mode, "Mode is null"));
        int prot = toProt(mode);
        Unmapper unmapper = mapInternal(mode, position, size, prot, isSync);
        if (unmapper == null) {
            // a valid file descriptor is not required
            FileDescriptor dummy = new FileDescriptor();
            if ((!writable) || (prot == MAP_RO))
                return Util.newMappedByteBufferR(0, 0, dummy, null, isSync);
            else
                return Util.newMappedByteBuffer(0, 0, dummy, null, isSync);
        } else if ((!writable) || (prot == MAP_RO)) {
            return Util.newMappedByteBufferR((int)unmapper.capacity(),
                    unmapper.address(),
                    unmapper.fileDescriptor(),
                    unmapper, unmapper.isSync());
        } else {
            return Util.newMappedByteBuffer((int)unmapper.capacity(),
                    unmapper.address(),
                    unmapper.fileDescriptor(),
                    unmapper, unmapper.isSync());
        }
    }

    @Override
    public MemorySegment map(MapMode mode, long offset, long size, Arena arena)
        throws IOException
    {
        Objects.requireNonNull(mode,"Mode is null");
        Objects.requireNonNull(arena, "Arena is null");
        MemorySessionImpl sessionImpl = MemorySessionImpl.toMemorySession(arena);
        sessionImpl.checkValidState();
        if (offset < 0)
            throw new IllegalArgumentException("Requested bytes offset must be >= 0.");
        if (size < 0)
            throw new IllegalArgumentException("Requested bytes size must be >= 0.");

        boolean isSync = isSync(mode);
        int prot = toProt(mode);
        Unmapper unmapper = mapInternal(mode, offset, size, prot, isSync);
        boolean readOnly = false;
        if (mode == MapMode.READ_ONLY) {
            readOnly = true;
        }
        return SegmentFactories.mapSegment(size, unmapper, readOnly, sessionImpl);
    }

    private Unmapper mapInternal(MapMode mode, long position, long size, int prot, boolean isSync)
        throws IOException
    {
        ensureOpen();
        if (mode == null)
            throw new NullPointerException("Mode is null");
        if (position < 0L)
            throw new IllegalArgumentException("Negative position");
        if (size < 0L)
            throw new IllegalArgumentException("Negative size");
        if (position + size < 0)
            throw new IllegalArgumentException("Position + size overflow");

        checkMode(mode, prot, isSync);
        long addr = -1;
        int ti = -1;
        try {
            beginBlocking();
            ti = threads.add();
            if (!isOpen())
                return null;

            long mapSize;
            int pagePosition;
            synchronized (positionLock) {
                long filesize;
                do {
                    long comp = Blocker.begin();
                    try {
                        filesize = nd.size(fd);
                    } finally {
                        Blocker.end(comp);
                    }
                } while ((filesize == IOStatus.INTERRUPTED) && isOpen());
                if (!isOpen())
                    return null;

                if (filesize < position + size) { // Extend file size
                    if (!writable) {
                        throw new IOException("Channel not open for writing " +
                            "- cannot extend file to required size");
                    }
                    int rv;
                    do {
                        long comp = Blocker.begin();
                        try {
                            rv = nd.truncate(fd, position + size);
                        } finally {
                            Blocker.end(comp);
                        }
                    } while ((rv == IOStatus.INTERRUPTED) && isOpen());
                    if (!isOpen())
                        return null;
                }

                if (size == 0) {
                    return null;
                }

                pagePosition = (int)(position % nd.allocationGranularity());
                long mapPosition = position - pagePosition;
                mapSize = size + pagePosition;
                try {
                    // If map did not throw an exception, the address is valid
                    addr = nd.map(fd, prot, mapPosition, mapSize, isSync);
                } catch (OutOfMemoryError x) {
                    // An OutOfMemoryError may indicate that we've exhausted
                    // memory so force gc and re-attempt map
                    System.gc();
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException y) {
                        Thread.currentThread().interrupt();
                    }
                    try {
                        addr = nd.map(fd, prot, mapPosition, mapSize, isSync);
                    } catch (OutOfMemoryError y) {
                        // After a second OOME, fail
                        throw new IOException("Map failed", y);
                    }
                }
            } // synchronized

            // On Windows, and potentially other platforms, we need an open
            // file descriptor for some mapping operations.
            FileDescriptor mfd;
            try {
                mfd = nd.duplicateForMapping(fd);
            } catch (IOException ioe) {
                nd.unmap(addr, mapSize);
                throw ioe;
            }

            assert (IOStatus.checkAll(addr));
            assert (addr % nd.allocationGranularity() == 0);
            Unmapper um = (isSync
                ? new SyncUnmapper(addr, mapSize, size, mfd, pagePosition)
                : new DefaultUnmapper(addr, mapSize, size, mfd, pagePosition));
            return um;
        } finally {
            threads.remove(ti);
            endBlocking(IOStatus.checkAll(addr));
        }
    }

    private boolean isSync(MapMode mode) {
        // Do not want to initialize ExtendedMapMode until
        // after the module system has been initialized
        return !VM.isModuleSystemInited() ? false :
            (mode == ExtendedMapMode.READ_ONLY_SYNC ||
                mode == ExtendedMapMode.READ_WRITE_SYNC);
    }

    private int toProt(MapMode mode) {
        int prot;
        if (mode == MapMode.READ_ONLY) {
            prot = MAP_RO;
        } else if (mode == MapMode.READ_WRITE) {
            prot = MAP_RW;
        } else if (mode == MapMode.PRIVATE) {
            prot = MAP_PV;
        } else if (mode == ExtendedMapMode.READ_ONLY_SYNC) {
            prot = MAP_RO;
        } else if (mode == ExtendedMapMode.READ_WRITE_SYNC) {
            prot = MAP_RW;
        } else {
            prot = MAP_INVALID;
        }
        return prot;
    }

    private void checkMode(MapMode mode, int prot, boolean isSync) {
        if (prot == MAP_INVALID) {
            throw new UnsupportedOperationException();
        }
        if ((mode != MapMode.READ_ONLY) && mode != ExtendedMapMode.READ_ONLY_SYNC && !writable)
            throw new NonWritableChannelException();
        if (!readable)
            throw new NonReadableChannelException();
        // reject SYNC request if writeback is not enabled for this platform
        if (isSync && !Unsafe.isWritebackEnabled()) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Invoked by sun.management.ManagementFactoryHelper to create the management
     * interface for mapped buffers.
     */
    public static BufferPool getMappedBufferPool() {
        return new BufferPool() {
            @Override
            public String getName() {
                return "mapped";
            }
            @Override
            public long getCount() {
                return DefaultUnmapper.count;
            }
            @Override
            public long getTotalCapacity() {
                return DefaultUnmapper.totalCapacity;
            }
            @Override
            public long getMemoryUsed() {
                return DefaultUnmapper.totalSize;
            }
        };
    }

    /**
     * Invoked by sun.management.ManagementFactoryHelper to create the management
     * interface for sync mapped buffers.
     */
    public static BufferPool getSyncMappedBufferPool() {
        return new BufferPool() {
            @Override
            public String getName() {
                return "mapped - 'non-volatile memory'";
            }
            @Override
            public long getCount() {
                return SyncUnmapper.count;
            }
            @Override
            public long getTotalCapacity() {
                return SyncUnmapper.totalCapacity;
            }
            @Override
            public long getMemoryUsed() {
                return SyncUnmapper.totalSize;
            }
        };
    }

    // -- Locks --

    // keeps track of locks on this file
    private volatile FileLockTable fileLockTable;

    private FileLockTable fileLockTable() throws IOException {
        if (fileLockTable == null) {
            synchronized (this) {
                if (fileLockTable == null) {
                    int ti = threads.add();
                    try {
                        ensureOpen();
                        fileLockTable = new FileLockTable(this, fd);
                    } finally {
                        threads.remove(ti);
                    }
                }
            }
        }
        return fileLockTable;
    }

    @Override
    public FileLock lock(long position, long size, boolean shared)
        throws IOException
    {
        ensureOpen();
        if (shared && !readable)
            throw new NonReadableChannelException();
        if (!shared && !writable)
            throw new NonWritableChannelException();
        if (size == 0)
            size = Long.MAX_VALUE - Math.max(0, position);
        FileLockImpl fli = new FileLockImpl(this, position, size, shared);
        FileLockTable flt = fileLockTable();
        flt.add(fli);
        boolean completed = false;
        int ti = -1;
        try {
            beginBlocking();
            ti = threads.add();
            if (!isOpen())
                return null;
            int n;
            do {
                long comp = Blocker.begin();
                try {
                    n = nd.lock(fd, true, position, size, shared);
                } finally {
                    Blocker.end(comp);
                }
            } while ((n == FileDispatcher.INTERRUPTED) && isOpen());
            if (isOpen()) {
                if (n == FileDispatcher.RET_EX_LOCK) {
                    assert shared;
                    FileLockImpl fli2 = new FileLockImpl(this, position, size,
                                                         false);
                    flt.replace(fli, fli2);
                    fli = fli2;
                }
                completed = true;
            }
        } finally {
            if (!completed)
                flt.remove(fli);
            threads.remove(ti);
            try {
                endBlocking(completed);
            } catch (ClosedByInterruptException e) {
                throw new FileLockInterruptionException();
            }
        }
        return fli;
    }

    @Override
    public FileLock tryLock(long position, long size, boolean shared)
        throws IOException
    {
        ensureOpen();
        if (shared && !readable)
            throw new NonReadableChannelException();
        if (!shared && !writable)
            throw new NonWritableChannelException();
        if (size == 0)
            size = Long.MAX_VALUE - Math.max(0, position);
        FileLockImpl fli = new FileLockImpl(this, position, size, shared);
        FileLockTable flt = fileLockTable();
        flt.add(fli);
        int result;

        int ti = threads.add();
        try {
            try {
                ensureOpen();
                result = nd.lock(fd, false, position, size, shared);
            } catch (IOException e) {
                flt.remove(fli);
                throw e;
            }
            if (result == FileDispatcher.NO_LOCK) {
                flt.remove(fli);
                return null;
            }
            if (result == FileDispatcher.RET_EX_LOCK) {
                assert shared;
                FileLockImpl fli2 = new FileLockImpl(this, position, size,
                                                     false);
                flt.replace(fli, fli2);
                return fli2;
            }
            return fli;
        } finally {
            threads.remove(ti);
        }
    }

    void release(FileLockImpl fli) throws IOException {
        int ti = threads.add();
        try {
            ensureOpen();
            nd.release(fd, fli.position(), fli.size());
        } finally {
            threads.remove(ti);
        }
        assert fileLockTable != null;
        fileLockTable.remove(fli);
    }
}
