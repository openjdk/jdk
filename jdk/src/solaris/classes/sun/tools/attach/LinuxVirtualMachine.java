/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.spi.AttachProvider;
import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.util.Properties;

/*
 * Linux implementation of HotSpotVirtualMachine
 */
public class LinuxVirtualMachine extends HotSpotVirtualMachine {
    // "/tmp" is used as a global well-known location for the files
    // .java_pid<pid>. and .attach_pid<pid>. It is important that this
    // location is the same for all processes, otherwise the tools
    // will not be able to find all Hotspot processes.
    // Any changes to this needs to be synchronized with HotSpot.
    private static final String tmpdir = "/tmp";

    // Indicates if this machine uses the old LinuxThreads
    static boolean isLinuxThreads;

    // The patch to the socket file created by the target VM
    String path;

    /**
     * Attaches to the target VM
     */
    LinuxVirtualMachine(AttachProvider provider, String vmid)
        throws AttachNotSupportedException, IOException
    {
        super(provider, vmid);

        // This provider only understands pids
        int pid;
        try {
            pid = Integer.parseInt(vmid);
        } catch (NumberFormatException x) {
            throw new AttachNotSupportedException("Invalid process identifier");
        }

        // Find the socket file. If not found then we attempt to start the
        // attach mechanism in the target VM by sending it a QUIT signal.
        // Then we attempt to find the socket file again.
        path = findSocketFile(pid);
        if (path == null) {
            File f = createAttachFile(pid);
            try {
                // On LinuxThreads each thread is a process and we don't have the
                // pid of the VMThread which has SIGQUIT unblocked. To workaround
                // this we get the pid of the "manager thread" that is created
                // by the first call to pthread_create. This is parent of all
                // threads (except the initial thread).
                if (isLinuxThreads) {
                    int mpid;
                    try {
                        mpid = getLinuxThreadsManager(pid);
                    } catch (IOException x) {
                        throw new AttachNotSupportedException(x.getMessage());
                    }
                    assert(mpid >= 1);
                    sendQuitToChildrenOf(mpid);
                } else {
                    sendQuitTo(pid);
                }

                // give the target VM time to start the attach mechanism
                int i = 0;
                long delay = 200;
                int retries = (int)(attachTimeout() / delay);
                do {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException x) { }
                    path = findSocketFile(pid);
                    i++;
                } while (i <= retries && path == null);
                if (path == null) {
                    throw new AttachNotSupportedException(
                        "Unable to open socket file: target process not responding " +
                        "or HotSpot VM not loaded");
                }
            } finally {
                f.delete();
            }
        }

        // Check that the file owner/permission to avoid attaching to
        // bogus process
        checkPermissions(path);

        // Check that we can connect to the process
        // - this ensures we throw the permission denied error now rather than
        // later when we attempt to enqueue a command.
        int s = socket();
        try {
            connect(s, path);
        } finally {
            close(s);
        }
    }

    /**
     * Detach from the target VM
     */
    public void detach() throws IOException {
        synchronized (this) {
            if (this.path != null) {
                this.path = null;
            }
        }
    }

    // protocol version
    private final static String PROTOCOL_VERSION = "1";

    // known errors
    private final static int ATTACH_ERROR_BADVERSION = 101;

    /**
     * Execute the given command in the target VM.
     */
    InputStream execute(String cmd, Object ... args) throws AgentLoadException, IOException {
        assert args.length <= 3;                // includes null

        // did we detach?
        String p;
        synchronized (this) {
            if (this.path == null) {
                throw new IOException("Detached from target VM");
            }
            p = this.path;
        }

        // create UNIX socket
        int s = socket();

        // connect to target VM
        try {
            connect(s, p);
        } catch (IOException x) {
            close(s);
            throw x;
        }

        IOException ioe = null;

        // connected - write request
        // <ver> <cmd> <args...>
        try {
            writeString(s, PROTOCOL_VERSION);
            writeString(s, cmd);

            for (int i=0; i<3; i++) {
                if (i < args.length && args[i] != null) {
                    writeString(s, (String)args[i]);
                } else {
                    writeString(s, "");
                }
            }
        } catch (IOException x) {
            ioe = x;
        }


        // Create an input stream to read reply
        SocketInputStream sis = new SocketInputStream(s);

        // Read the command completion status
        int completionStatus;
        try {
            completionStatus = readInt(sis);
        } catch (IOException x) {
            sis.close();
            if (ioe != null) {
                throw ioe;
            } else {
                throw x;
            }
        }

        if (completionStatus != 0) {
            sis.close();

            // In the event of a protocol mismatch then the target VM
            // returns a known error so that we can throw a reasonable
            // error.
            if (completionStatus == ATTACH_ERROR_BADVERSION) {
                throw new IOException("Protocol mismatch with target VM");
            }

            // Special-case the "load" command so that the right exception is
            // thrown.
            if (cmd.equals("load")) {
                throw new AgentLoadException("Failed to load agent library");
            } else {
                throw new IOException("Command failed in target VM");
            }
        }

        // Return the input stream so that the command output can be read
        return sis;
    }

    /*
     * InputStream for the socket connection to get target VM
     */
    private class SocketInputStream extends InputStream {
        int s;

        public SocketInputStream(int s) {
            this.s = s;
        }

        public synchronized int read() throws IOException {
            byte b[] = new byte[1];
            int n = this.read(b, 0, 1);
            if (n == 1) {
                return b[0] & 0xff;
            } else {
                return -1;
            }
        }

        public synchronized int read(byte[] bs, int off, int len) throws IOException {
            if ((off < 0) || (off > bs.length) || (len < 0) ||
                ((off + len) > bs.length) || ((off + len) < 0)) {
                throw new IndexOutOfBoundsException();
            } else if (len == 0)
                return 0;

            return LinuxVirtualMachine.read(s, bs, off, len);
        }

        public void close() throws IOException {
            LinuxVirtualMachine.close(s);
        }
    }

    // Return the socket file for the given process.
    private String findSocketFile(int pid) {
        File f = new File(tmpdir, ".java_pid" + pid);
        if (!f.exists()) {
            return null;
        }
        return f.getPath();
    }

    // On Solaris/Linux a simple handshake is used to start the attach mechanism
    // if not already started. The client creates a .attach_pid<pid> file in the
    // target VM's working directory (or temp directory), and the SIGQUIT handler
    // checks for the file.
    private File createAttachFile(int pid) throws IOException {
        String fn = ".attach_pid" + pid;
        String path = "/proc/" + pid + "/cwd/" + fn;
        File f = new File(path);
        try {
            f.createNewFile();
        } catch (IOException x) {
            f = new File(tmpdir, fn);
            f.createNewFile();
        }
        return f;
    }

    /*
     * Write/sends the given to the target VM. String is transmitted in
     * UTF-8 encoding.
     */
    private void writeString(int fd, String s) throws IOException {
        if (s.length() > 0) {
            byte b[];
            try {
                b = s.getBytes("UTF-8");
            } catch (java.io.UnsupportedEncodingException x) {
                throw new InternalError(x);
            }
            LinuxVirtualMachine.write(fd, b, 0, b.length);
        }
        byte b[] = new byte[1];
        b[0] = 0;
        write(fd, b, 0, 1);
    }


    //-- native methods

    static native boolean isLinuxThreads();

    static native int getLinuxThreadsManager(int pid) throws IOException;

    static native void sendQuitToChildrenOf(int pid) throws IOException;

    static native void sendQuitTo(int pid) throws IOException;

    static native void checkPermissions(String path) throws IOException;

    static native int socket() throws IOException;

    static native void connect(int fd, String path) throws IOException;

    static native void close(int fd) throws IOException;

    static native int read(int fd, byte buf[], int off, int bufLen) throws IOException;

    static native void write(int fd, byte buf[], int off, int bufLen) throws IOException;

    static {
        System.loadLibrary("attach");
        isLinuxThreads = isLinuxThreads();
    }
}
