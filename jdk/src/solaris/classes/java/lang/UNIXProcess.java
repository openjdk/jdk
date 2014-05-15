/*
 * Copyright (c) 1995, 2014, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.security.AccessController;
import static java.security.AccessController.doPrivileged;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

/**
 * java.lang.Process subclass in the UNIX environment.
 *
 * @author Mario Wolczko and Ross Knippel.
 * @author Konstantin Kladko (ported to Linux and Bsd)
 * @author Martin Buchholz
 * @author Volker Simonis (ported to AIX)
 */
final class UNIXProcess extends Process {
    private static final sun.misc.JavaIOFileDescriptorAccess fdAccess
        = sun.misc.SharedSecrets.getJavaIOFileDescriptorAccess();

    private final int pid;
    private int exitcode;
    private boolean hasExited;

    private /* final */ OutputStream stdin;
    private /* final */ InputStream  stdout;
    private /* final */ InputStream  stderr;

    // only used on Solaris
    private /* final */ DeferredCloseInputStream stdout_inner_stream;

    private static enum LaunchMechanism {
        // order IS important!
        FORK,
        POSIX_SPAWN,
        VFORK
    }

    private static enum Platform {

        LINUX(LaunchMechanism.VFORK, LaunchMechanism.FORK),

        BSD(LaunchMechanism.POSIX_SPAWN, LaunchMechanism.FORK),

        SOLARIS(LaunchMechanism.POSIX_SPAWN, LaunchMechanism.FORK),

        AIX(LaunchMechanism.POSIX_SPAWN, LaunchMechanism.FORK);

        final LaunchMechanism defaultLaunchMechanism;
        final Set<LaunchMechanism> validLaunchMechanisms;

        Platform(LaunchMechanism ... launchMechanisms) {
            this.defaultLaunchMechanism = launchMechanisms[0];
            this.validLaunchMechanisms =
                EnumSet.copyOf(Arrays.asList(launchMechanisms));
        }

        private String helperPath(String javahome, String osArch) {
            switch (this) {
                case SOLARIS:
                    if (osArch.equals("x86")) { osArch = "i386"; }
                    else if (osArch.equals("x86_64")) { osArch = "amd64"; }
                    // fall through...
                case LINUX:
                case AIX:
                    return javahome + "/lib/" + osArch + "/jspawnhelper";

                case BSD:
                    return javahome + "/lib/jspawnhelper";

                default:
                    throw new AssertionError("Unsupported platform: " + this);
            }
        }

        String helperPath() {
            return AccessController.doPrivileged(
                (PrivilegedAction<String>) () ->
                    helperPath(System.getProperty("java.home"),
                               System.getProperty("os.arch"))
            );
        }

        LaunchMechanism launchMechanism() {
            return AccessController.doPrivileged(
                (PrivilegedAction<LaunchMechanism>) () -> {
                    String s = System.getProperty(
                        "jdk.lang.Process.launchMechanism");
                    LaunchMechanism lm;
                    if (s == null) {
                        lm = defaultLaunchMechanism;
                        s = lm.name().toLowerCase(Locale.ENGLISH);
                    } else {
                        try {
                            lm = LaunchMechanism.valueOf(
                                s.toUpperCase(Locale.ENGLISH));
                        } catch (IllegalArgumentException e) {
                            lm = null;
                        }
                    }
                    if (lm == null || !validLaunchMechanisms.contains(lm)) {
                        throw new Error(
                            s + " is not a supported " +
                            "process launch mechanism on this platform."
                        );
                    }
                    return lm;
                }
            );
        }

        static Platform get() {
            String osName = AccessController.doPrivileged(
                (PrivilegedAction<String>) () -> System.getProperty("os.name")
            );

            if (osName.equals("Linux")) { return LINUX; }
            if (osName.contains("OS X")) { return BSD; }
            if (osName.equals("SunOS")) { return SOLARIS; }
            if (osName.equals("AIX")) { return AIX; }

            throw new Error(osName + " is not a supported OS platform.");
        }
    }

    private static final Platform platform = Platform.get();
    private static final LaunchMechanism launchMechanism = platform.launchMechanism();
    private static final byte[] helperpath = toCString(platform.helperPath());

    private static byte[] toCString(String s) {
        if (s == null)
            return null;
        byte[] bytes = s.getBytes();
        byte[] result = new byte[bytes.length + 1];
        System.arraycopy(bytes, 0,
                         result, 0,
                         bytes.length);
        result[result.length-1] = (byte)0;
        return result;
    }

    /* this is for the reaping thread */
    private native int waitForProcessExit(int pid);

    /**
     * Creates a process. Depending on the {@code mode} flag, this is done by
     * one of the following mechanisms:
     * <pre>
     *   1 - fork(2) and exec(2)
     *   2 - posix_spawn(3P)
     *   3 - vfork(2) and exec(2)
     *
     *  (4 - clone(2) and exec(2) - obsolete and currently disabled in native code)
     * </pre>
     * @param fds an array of three file descriptors.
     *        Indexes 0, 1, and 2 correspond to standard input,
     *        standard output and standard error, respectively.  On
     *        input, a value of -1 means to create a pipe to connect
     *        child and parent processes.  On output, a value which
     *        is not -1 is the parent pipe fd corresponding to the
     *        pipe which has been created.  An element of this array
     *        is -1 on input if and only if it is <em>not</em> -1 on
     *        output.
     * @return the pid of the subprocess
     */
    private native int forkAndExec(int mode, byte[] helperpath,
                                   byte[] prog,
                                   byte[] argBlock, int argc,
                                   byte[] envBlock, int envc,
                                   byte[] dir,
                                   int[] fds,
                                   boolean redirectErrorStream)
        throws IOException;

    /**
     * The thread pool of "process reaper" daemon threads.
     */
    private static final Executor processReaperExecutor =
        doPrivileged((PrivilegedAction<Executor>) () -> {

            ThreadGroup tg = Thread.currentThread().getThreadGroup();
            while (tg.getParent() != null) tg = tg.getParent();
            ThreadGroup systemThreadGroup = tg;

            ThreadFactory threadFactory = grimReaper -> {
                // Our thread stack requirement is quite modest.
                Thread t = new Thread(systemThreadGroup, grimReaper,
                                      "process reaper", 32768);
                t.setDaemon(true);
                // A small attempt (probably futile) to avoid priority inversion
                t.setPriority(Thread.MAX_PRIORITY);
                return t;
            };

            return Executors.newCachedThreadPool(threadFactory);
        });

    UNIXProcess(final byte[] prog,
                final byte[] argBlock, final int argc,
                final byte[] envBlock, final int envc,
                final byte[] dir,
                final int[] fds,
                final boolean redirectErrorStream)
            throws IOException {

        pid = forkAndExec(launchMechanism.ordinal() + 1,
                          helperpath,
                          prog,
                          argBlock, argc,
                          envBlock, envc,
                          dir,
                          fds,
                          redirectErrorStream);

        try {
            doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                initStreams(fds);
                return null;
            });
        } catch (PrivilegedActionException ex) {
            throw (IOException) ex.getException();
        }
    }

    static FileDescriptor newFileDescriptor(int fd) {
        FileDescriptor fileDescriptor = new FileDescriptor();
        fdAccess.set(fileDescriptor, fd);
        return fileDescriptor;
    }

    void initStreams(int[] fds) throws IOException {
        switch (platform) {
            case LINUX:
            case BSD:
                stdin = (fds[0] == -1) ?
                        ProcessBuilder.NullOutputStream.INSTANCE :
                        new ProcessPipeOutputStream(fds[0]);

                stdout = (fds[1] == -1) ?
                         ProcessBuilder.NullInputStream.INSTANCE :
                         new ProcessPipeInputStream(fds[1]);

                stderr = (fds[2] == -1) ?
                         ProcessBuilder.NullInputStream.INSTANCE :
                         new ProcessPipeInputStream(fds[2]);

                processReaperExecutor.execute(() -> {
                    int exitcode = waitForProcessExit(pid);

                    synchronized (this) {
                        this.exitcode = exitcode;
                        this.hasExited = true;
                        this.notifyAll();
                    }

                    if (stdout instanceof ProcessPipeInputStream)
                        ((ProcessPipeInputStream) stdout).processExited();

                    if (stderr instanceof ProcessPipeInputStream)
                        ((ProcessPipeInputStream) stderr).processExited();

                    if (stdin instanceof ProcessPipeOutputStream)
                        ((ProcessPipeOutputStream) stdin).processExited();
                });
                break;

            case SOLARIS:
                stdin = (fds[0] == -1) ?
                        ProcessBuilder.NullOutputStream.INSTANCE :
                        new BufferedOutputStream(
                            new FileOutputStream(newFileDescriptor(fds[0])));

                stdout = (fds[1] == -1) ?
                         ProcessBuilder.NullInputStream.INSTANCE :
                         new BufferedInputStream(
                             stdout_inner_stream =
                                 new DeferredCloseInputStream(
                                     newFileDescriptor(fds[1])));

                stderr = (fds[2] == -1) ?
                         ProcessBuilder.NullInputStream.INSTANCE :
                         new DeferredCloseInputStream(newFileDescriptor(fds[2]));

                /*
                 * For each subprocess forked a corresponding reaper task
                 * is submitted.  That task is the only thread which waits
                 * for the subprocess to terminate and it doesn't hold any
                 * locks while doing so.  This design allows waitFor() and
                 * exitStatus() to be safely executed in parallel (and they
                 * need no native code).
                 */
                processReaperExecutor.execute(() -> {
                    int exitcode = waitForProcessExit(pid);

                    synchronized (this) {
                        this.exitcode = exitcode;
                        this.hasExited = true;
                        this.notifyAll();
                    }
                });
                break;

            case AIX:
                stdin = (fds[0] == -1) ?
                        ProcessBuilder.NullOutputStream.INSTANCE :
                        new ProcessPipeOutputStream(fds[0]);

                stdout = (fds[1] == -1) ?
                         ProcessBuilder.NullInputStream.INSTANCE :
                         new DeferredCloseProcessPipeInputStream(fds[1]);

                stderr = (fds[2] == -1) ?
                         ProcessBuilder.NullInputStream.INSTANCE :
                         new DeferredCloseProcessPipeInputStream(fds[2]);

                processReaperExecutor.execute(() -> {
                    int exitcode = waitForProcessExit(pid);

                    synchronized (this) {
                        this.exitcode = exitcode;
                        this.hasExited = true;
                        this.notifyAll();
                    }

                    if (stdout instanceof DeferredCloseProcessPipeInputStream)
                        ((DeferredCloseProcessPipeInputStream) stdout).processExited();

                    if (stderr instanceof DeferredCloseProcessPipeInputStream)
                        ((DeferredCloseProcessPipeInputStream) stderr).processExited();

                    if (stdin instanceof ProcessPipeOutputStream)
                        ((ProcessPipeOutputStream) stdin).processExited();
                });
                break;

            default: throw new AssertionError("Unsupported platform: " + platform);
        }
    }

    public OutputStream getOutputStream() {
        return stdin;
    }

    public InputStream getInputStream() {
        return stdout;
    }

    public InputStream getErrorStream() {
        return stderr;
    }

    public synchronized int waitFor() throws InterruptedException {
        while (!hasExited) {
            wait();
        }
        return exitcode;
    }

    @Override
    public synchronized boolean waitFor(long timeout, TimeUnit unit)
        throws InterruptedException
    {
        if (hasExited) return true;
        if (timeout <= 0) return false;

        long timeoutAsNanos = unit.toNanos(timeout);
        long startTime = System.nanoTime();
        long rem = timeoutAsNanos;

        while (!hasExited && (rem > 0)) {
            wait(Math.max(TimeUnit.NANOSECONDS.toMillis(rem), 1));
            rem = timeoutAsNanos - (System.nanoTime() - startTime);
        }
        return hasExited;
    }

    public synchronized int exitValue() {
        if (!hasExited) {
            throw new IllegalThreadStateException("process hasn't exited");
        }
        return exitcode;
    }

    private static native void destroyProcess(int pid, boolean force);

    private void destroy(boolean force) {
        switch (platform) {
            case LINUX:
            case BSD:
            case AIX:
                // There is a risk that pid will be recycled, causing us to
                // kill the wrong process!  So we only terminate processes
                // that appear to still be running.  Even with this check,
                // there is an unavoidable race condition here, but the window
                // is very small, and OSes try hard to not recycle pids too
                // soon, so this is quite safe.
                synchronized (this) {
                    if (!hasExited)
                        destroyProcess(pid, force);
                }
                try { stdin.close();  } catch (IOException ignored) {}
                try { stdout.close(); } catch (IOException ignored) {}
                try { stderr.close(); } catch (IOException ignored) {}
                break;

            case SOLARIS:
                // There is a risk that pid will be recycled, causing us to
                // kill the wrong process!  So we only terminate processes
                // that appear to still be running.  Even with this check,
                // there is an unavoidable race condition here, but the window
                // is very small, and OSes try hard to not recycle pids too
                // soon, so this is quite safe.
                synchronized (this) {
                    if (!hasExited)
                        destroyProcess(pid, force);
                    try {
                        stdin.close();
                        if (stdout_inner_stream != null)
                            stdout_inner_stream.closeDeferred(stdout);
                        if (stderr instanceof DeferredCloseInputStream)
                            ((DeferredCloseInputStream) stderr)
                                .closeDeferred(stderr);
                    } catch (IOException e) {
                        // ignore
                    }
                }
                break;

            default: throw new AssertionError("Unsupported platform: " + platform);
        }
    }

    public void destroy() {
        destroy(false);
    }

    @Override
    public Process destroyForcibly() {
        destroy(true);
        return this;
    }

    @Override
    public synchronized boolean isAlive() {
        return !hasExited;
    }

    private static native void init();

    static {
        init();
    }

    /**
     * A buffered input stream for a subprocess pipe file descriptor
     * that allows the underlying file descriptor to be reclaimed when
     * the process exits, via the processExited hook.
     *
     * This is tricky because we do not want the user-level InputStream to be
     * closed until the user invokes close(), and we need to continue to be
     * able to read any buffered data lingering in the OS pipe buffer.
     */
    private static class ProcessPipeInputStream extends BufferedInputStream {
        private final Object closeLock = new Object();

        ProcessPipeInputStream(int fd) {
            super(new FileInputStream(newFileDescriptor(fd)));
        }
        private static byte[] drainInputStream(InputStream in)
                throws IOException {
            int n = 0;
            int j;
            byte[] a = null;
            while ((j = in.available()) > 0) {
                a = (a == null) ? new byte[j] : Arrays.copyOf(a, n + j);
                n += in.read(a, n, j);
            }
            return (a == null || n == a.length) ? a : Arrays.copyOf(a, n);
        }

        /** Called by the process reaper thread when the process exits. */
        synchronized void processExited() {
            synchronized (closeLock) {
                try {
                    InputStream in = this.in;
                    // this stream is closed if and only if: in == null
                    if (in != null) {
                        byte[] stragglers = drainInputStream(in);
                        in.close();
                        this.in = (stragglers == null) ?
                            ProcessBuilder.NullInputStream.INSTANCE :
                            new ByteArrayInputStream(stragglers);
                    }
                } catch (IOException ignored) {}
            }
        }

        @Override
        public void close() throws IOException {
            // BufferedInputStream#close() is not synchronized unlike most other
            // methods. Synchronizing helps avoid race with processExited().
            synchronized (closeLock) {
                super.close();
            }
        }
    }

    /**
     * A buffered output stream for a subprocess pipe file descriptor
     * that allows the underlying file descriptor to be reclaimed when
     * the process exits, via the processExited hook.
     */
    private static class ProcessPipeOutputStream extends BufferedOutputStream {
        ProcessPipeOutputStream(int fd) {
            super(new FileOutputStream(newFileDescriptor(fd)));
        }

        /** Called by the process reaper thread when the process exits. */
        synchronized void processExited() {
            OutputStream out = this.out;
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                    // We know of no reason to get an IOException, but if
                    // we do, there's nothing else to do but carry on.
                }
                this.out = ProcessBuilder.NullOutputStream.INSTANCE;
            }
        }
    }

    // A FileInputStream that supports the deferment of the actual close
    // operation until the last pending I/O operation on the stream has
    // finished.  This is required on Solaris because we must close the stdin
    // and stdout streams in the destroy method in order to reclaim the
    // underlying file descriptors.  Doing so, however, causes any thread
    // currently blocked in a read on one of those streams to receive an
    // IOException("Bad file number"), which is incompatible with historical
    // behavior.  By deferring the close we allow any pending reads to see -1
    // (EOF) as they did before.
    //
    private static class DeferredCloseInputStream extends FileInputStream
    {
        DeferredCloseInputStream(FileDescriptor fd) {
            super(fd);
        }

        private Object lock = new Object();     // For the following fields
        private boolean closePending = false;
        private int useCount = 0;
        private InputStream streamToClose;

        private void raise() {
            synchronized (lock) {
                useCount++;
            }
        }

        private void lower() throws IOException {
            synchronized (lock) {
                useCount--;
                if (useCount == 0 && closePending) {
                    streamToClose.close();
                }
            }
        }

        // stc is the actual stream to be closed; it might be this object, or
        // it might be an upstream object for which this object is downstream.
        //
        private void closeDeferred(InputStream stc) throws IOException {
            synchronized (lock) {
                if (useCount == 0) {
                    stc.close();
                } else {
                    closePending = true;
                    streamToClose = stc;
                }
            }
        }

        public void close() throws IOException {
            synchronized (lock) {
                useCount = 0;
                closePending = false;
            }
            super.close();
        }

        public int read() throws IOException {
            raise();
            try {
                return super.read();
            } finally {
                lower();
            }
        }

        public int read(byte[] b) throws IOException {
            raise();
            try {
                return super.read(b);
            } finally {
                lower();
            }
        }

        public int read(byte[] b, int off, int len) throws IOException {
            raise();
            try {
                return super.read(b, off, len);
            } finally {
                lower();
            }
        }

        public long skip(long n) throws IOException {
            raise();
            try {
                return super.skip(n);
            } finally {
                lower();
            }
        }

        public int available() throws IOException {
            raise();
            try {
                return super.available();
            } finally {
                lower();
            }
        }
    }

    /**
     * A buffered input stream for a subprocess pipe file descriptor
     * that allows the underlying file descriptor to be reclaimed when
     * the process exits, via the processExited hook.
     *
     * This is tricky because we do not want the user-level InputStream to be
     * closed until the user invokes close(), and we need to continue to be
     * able to read any buffered data lingering in the OS pipe buffer.
     *
     * On AIX this is especially tricky, because the 'close()' system call
     * will block if another thread is at the same time blocked in a file
     * operation (e.g. 'read()') on the same file descriptor. We therefore
     * combine 'ProcessPipeInputStream' approach used on Linux and Bsd
     * with the DeferredCloseInputStream approach used on Solaris. This means
     * that every potentially blocking operation on the file descriptor
     * increments a counter before it is executed and decrements it once it
     * finishes. The 'close()' operation will only be executed if there are
     * no pending operations. Otherwise it is deferred after the last pending
     * operation has finished.
     *
     */
    private static class DeferredCloseProcessPipeInputStream
        extends BufferedInputStream {

        private final Object closeLock = new Object();
        private int useCount = 0;
        private boolean closePending = false;

        DeferredCloseProcessPipeInputStream(int fd) {
            super(new FileInputStream(newFileDescriptor(fd)));
        }

        private InputStream drainInputStream(InputStream in)
                throws IOException {
            int n = 0;
            int j;
            byte[] a = null;
            synchronized (closeLock) {
                if (buf == null) // asynchronous close()?
                    return null; // discard
                j = in.available();
            }
            while (j > 0) {
                a = (a == null) ? new byte[j] : Arrays.copyOf(a, n + j);
                synchronized (closeLock) {
                    if (buf == null) // asynchronous close()?
                        return null; // discard
                    n += in.read(a, n, j);
                    j = in.available();
                }
            }
            return (a == null) ?
                    ProcessBuilder.NullInputStream.INSTANCE :
                    new ByteArrayInputStream(n == a.length ? a : Arrays.copyOf(a, n));
        }

        /** Called by the process reaper thread when the process exits. */
        synchronized void processExited() {
            try {
                InputStream in = this.in;
                if (in != null) {
                    InputStream stragglers = drainInputStream(in);
                    in.close();
                    this.in = stragglers;
                }
            } catch (IOException ignored) { }
        }

        private void raise() {
            synchronized (closeLock) {
                useCount++;
            }
        }

        private void lower() throws IOException {
            synchronized (closeLock) {
                useCount--;
                if (useCount == 0 && closePending) {
                    closePending = false;
                    super.close();
                }
            }
        }

        @Override
        public int read() throws IOException {
            raise();
            try {
                return super.read();
            } finally {
                lower();
            }
        }

        @Override
        public int read(byte[] b) throws IOException {
            raise();
            try {
                return super.read(b);
            } finally {
                lower();
            }
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            raise();
            try {
                return super.read(b, off, len);
            } finally {
                lower();
            }
        }

        @Override
        public long skip(long n) throws IOException {
            raise();
            try {
                return super.skip(n);
            } finally {
                lower();
            }
        }

        @Override
        public int available() throws IOException {
            raise();
            try {
                return super.available();
            } finally {
                lower();
            }
        }

        @Override
        public void close() throws IOException {
            // BufferedInputStream#close() is not synchronized unlike most other
            // methods. Synchronizing helps avoid racing with drainInputStream().
            synchronized (closeLock) {
                if (useCount == 0) {
                    super.close();
                }
                else {
                    closePending = true;
                }
            }
        }
    }
}
