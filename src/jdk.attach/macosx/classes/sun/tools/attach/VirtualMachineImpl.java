/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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
package sun.tools.attach;

import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.spi.AttachProvider;

import java.io.InputStream;
import java.io.IOException;
import java.io.File;

import static java.nio.charset.StandardCharsets.UTF_8;

/*
 * Bsd implementation of HotSpotVirtualMachine
 */
@SuppressWarnings("restricted")
public class VirtualMachineImpl extends HotSpotVirtualMachine {
    // "tmpdir" is used as a global well-known location for the files
    // .java_pid<pid>. and .attach_pid<pid>. It is important that this
    // location is the same for all processes, otherwise the tools
    // will not be able to find all Hotspot processes.
    // This is intentionally not the same as java.io.tmpdir, since
    // the latter can be changed by the user.
    // Any changes to this needs to be synchronized with HotSpot.
    private static final String tmpdir;
    String socket_path;
    private OperationProperties props = new OperationProperties(VERSION_1); // updated in ctor

    /**
     * Attaches to the target VM
     */
    VirtualMachineImpl(AttachProvider provider, String vmid)
        throws AttachNotSupportedException, IOException
    {
        super(provider, vmid);

        // This provider only understands pids
        int pid = Integer.parseInt(vmid);
        if (pid < 1) {
            throw new AttachNotSupportedException("Invalid process identifier: " + vmid);
        }

        // Find the socket file. If not found then we attempt to start the
        // attach mechanism in the target VM by sending it a QUIT signal.
        // Then we attempt to find the socket file again.
        File socket_file = new File(tmpdir, ".java_pid" + pid);
        socket_path = socket_file.getPath();
        if (!socket_file.exists()) {
            File f = createAttachFile(pid);
            try {
                checkCatchesAndSendQuitTo(pid, false);

                // give the target VM time to start the attach mechanism
                final int delay_step = 100;
                final long timeout = attachTimeout();
                long time_spent = 0;
                long delay = 0;

                boolean timedout = false;
                do {
                    // Increase timeout on each attempt to reduce polling
                    delay += delay_step;
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException x) { }

                    timedout = (time_spent += delay) > timeout;

                    if (time_spent > timeout/2 && !socket_file.exists()) {
                        // Send QUIT again to give target VM the last chance to react
                        checkCatchesAndSendQuitTo(pid, !timedout);
                    }
                } while (!timedout && !socket_file.exists());
                if (!socket_file.exists()) {
                    throw new AttachNotSupportedException(
                        String.format("Unable to open socket file %s: " +
                                      "target process %d doesn't respond within %dms " +
                                      "or HotSpot VM not loaded", socket_path,
                                      pid, time_spent));
                }
            } finally {
                f.delete();
            }
        }

        // Check that the file owner/permission to avoid attaching to
        // bogus process
        checkPermissions(socket_path);

        if (isAPIv2Enabled()) {
            props = getDefaultProps();
        } else {
            // Check that we can connect to the process
            // - this ensures we throw the permission denied error now rather than
            // later when we attempt to enqueue a command.
            int s = socket();
            try {
                connect(s, socket_path);
            } finally {
                close(s);
            }
        }
    }

    /**
     * Detach from the target VM
     */
    public void detach() throws IOException {
        synchronized (this) {
            if (socket_path != null) {
                socket_path = null;
            }
        }
    }

    /**
     * Execute the given command in the target VM.
     */
    InputStream execute(String cmd, Object ... args) throws AgentLoadException, IOException {
        checkNulls(args);

        // did we detach?
        synchronized (this) {
            if (socket_path == null) {
                throw new IOException("Detached from target VM");
            }
        }

        // create UNIX socket
        int s = socket();

        // connect to target VM
        try {
            connect(s, socket_path);
        } catch (IOException x) {
            close(s);
            throw x;
        }

        IOException ioe = null;

        // connected - write request
        try {
            SocketOutputStream writer = new SocketOutputStream(s);
            writeCommand(writer, props, cmd, args);
        } catch (IOException x) {
            ioe = x;
        }


        // Create an input stream to read reply
        SocketInputStreamImpl sis = new SocketInputStreamImpl(s);

        // Process the command completion status
        processCompletionStatus(ioe, cmd, sis);

        // Return the input stream so that the command output can be read
        return sis;
    }

    private static class SocketOutputStream implements AttachOutputStream {
        private int fd;
        public SocketOutputStream(int fd) {
            this.fd = fd;
        }
        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            VirtualMachineImpl.write(fd, buffer, offset, length);
        }
    }
    /*
     * InputStream for the socket connection to get target VM
     */
    private static class SocketInputStreamImpl extends SocketInputStream {
        public SocketInputStreamImpl(long fd) {
            super(fd);
        }

        @Override
        protected int read(long fd, byte[] bs, int off, int len) throws IOException {
            return VirtualMachineImpl.read((int)fd, bs, off, len);
        }

        @Override
        protected void close(long fd) throws IOException {
            VirtualMachineImpl.close((int)fd);
        }
    }

    private File createAttachFile(int pid) throws IOException {
        File f = new File(tmpdir, ".attach_pid" + pid);
        createAttachFile0(f.getPath());
        return f;
    }

    //-- native methods

    static native boolean checkCatchesAndSendQuitTo(int pid, boolean throwIfNotReady) throws IOException, AttachNotSupportedException;

    static native void checkPermissions(String path) throws IOException;

    static native int socket() throws IOException;

    static native void connect(int fd, String path) throws IOException;

    static native void close(int fd) throws IOException;

    static native int read(int fd, byte buf[], int off, int bufLen) throws IOException;

    static native void write(int fd, byte buf[], int off, int bufLen) throws IOException;

    static native void createAttachFile0(String path);

    static native String getTempDir();

    static {
        System.loadLibrary("attach");
        tmpdir = getTempDir();
    }
}
