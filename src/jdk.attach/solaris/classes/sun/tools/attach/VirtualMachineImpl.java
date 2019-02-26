/*
 * Copyright (c) 2005, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.attach.AttachOperationFailedException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.spi.AttachProvider;

import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileNotFoundException;

/*
 * Solaris implementation of HotSpotVirtualMachine.
 */
public class VirtualMachineImpl extends HotSpotVirtualMachine {
    // "/tmp" is used as a global well-known location for the files
    // .java_pid<pid>. and .attach_pid<pid>. It is important that this
    // location is the same for all processes, otherwise the tools
    // will not be able to find all Hotspot processes.
    // Any changes to this needs to be synchronized with HotSpot.
    private static final String tmpdir = "/tmp";

    // door descriptor;
    private int fd = -1;
    String socket_path;

    /**
     * Attaches to the target VM
     */
    VirtualMachineImpl(AttachProvider provider, String vmid)
        throws AttachNotSupportedException, IOException
    {
        super(provider, vmid);
        // This provider only understands process-ids (pids).
        int pid;
        try {
            pid = Integer.parseInt(vmid);
        } catch (NumberFormatException x) {
            throw new AttachNotSupportedException("Invalid process identifier");
        }

        // Opens the door file to the target VM. If the file is not
        // found it might mean that the attach mechanism isn't started in the
        // target VM so we attempt to start it and retry.
        try {
            fd = openDoor(pid);
        } catch (FileNotFoundException fnf1) {
            File f = createAttachFile(pid);
            try {
                sigquit(pid);

                // give the target VM time to start the attach mechanism
                final int delay_step = 100;
                final long timeout = attachTimeout();
                long time_spend = 0;
                long delay = 0;
                do {
                    // Increase timeout on each attempt to reduce polling
                    delay += delay_step;
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException x) { }
                    try {
                        fd = openDoor(pid);
                    } catch (FileNotFoundException fnf2) {
                        // pass
                    }

                    time_spend += delay;
                    if (time_spend > timeout/2 && fd == -1) {
                        // Send QUIT again to give target VM the last chance to react
                        sigquit(pid);
                    }
                } while (time_spend <= timeout && fd == -1);
                if (fd  == -1) {
                    throw new AttachNotSupportedException(
                        String.format("Unable to open door %s: " +
                          "target process %d doesn't respond within %dms " +
                          "or HotSpot VM not loaded", socket_path, pid, time_spend));
                }
            } finally {
                f.delete();
            }
        }
        assert fd >= 0;
    }

    /**
     * Detach from the target VM
     */
    public void detach() throws IOException {
        synchronized (this) {
            if (fd != -1) {
                close(fd);
                fd = -1;
            }
        }
    }

    /**
     * Execute the given command in the target VM.
     */
    InputStream execute(String cmd, Object ... args) throws AgentLoadException, IOException {
        assert args.length <= 4;                // includes null

        // first check that we are still attached
        int door;
        synchronized (this) {
            if (fd == -1) {
                throw new IOException("Detached from target VM");
            }
            door = fd;
        }

        // enqueue the command via a door call
        int s = enqueue(door, cmd, args);
        assert s >= 0;                          // valid file descriptor

        // The door call returns a file descriptor (one end of a socket pair).
        // Create an input stream around it.
        SocketInputStream sis = new SocketInputStream(s);

        // Read the command completion status
        int completionStatus;
        try {
            completionStatus = readInt(sis);
        } catch (IOException ioe) {
            sis.close();
            throw ioe;
        }

        // If non-0 it means an error but we need to special-case the
        // "load" command to ensure that the right exception is thrown.
        if (completionStatus != 0) {
            // read from the stream and use that as the error message
            String message = readErrorMessage(sis);
            sis.close();
            if (cmd.equals("load")) {
                String msg = "Failed to load agent library";
                if (!message.isEmpty())
                    msg += ": " + message;
                throw new AgentLoadException(msg);
            } else {
                if (message.isEmpty())
                    message = "Command failed in target VM";
                throw new AttachOperationFailedException(message);
            }
        }

        // Return the input stream so that the command output can be read
        return sis;
    }

    // InputStream over a socket
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

            return VirtualMachineImpl.read(s, bs, off, len);
        }

        public void close() throws IOException {
            VirtualMachineImpl.close(s);
        }
    }

    // The door is attached to .java_pid<pid> in the temporary directory.
    private int openDoor(int pid) throws IOException {
        socket_path = tmpdir + "/.java_pid" + pid;
        fd = open(socket_path);

        // Check that the file owner/permission to avoid attaching to
        // bogus process
        try {
            checkPermissions(socket_path);
        } catch (IOException ioe) {
            close(fd);
            throw ioe;
        }
        return fd;
    }

    // On Solaris a simple handshake is used to start the attach mechanism
    // if not already started. The client creates a .attach_pid<pid> file in the
    // target VM's working directory (or temporary directory), and the SIGQUIT
    // handler checks for the file.
    private File createAttachFile(int pid) throws IOException {
        String fn = ".attach_pid" + pid;
        String path = "/proc/" + pid + "/cwd/" + fn;
        File f = new File(path);
        try {
            f = f.getCanonicalFile();
            f.createNewFile();
        } catch (IOException x) {
            f = new File(tmpdir, fn);
            f.createNewFile();
        }
        return f;
    }

    //-- native methods

    static native int open(String path) throws IOException;

    static native void close(int fd) throws IOException;

    static native int read(int fd, byte buf[], int off, int buflen) throws IOException;

    static native void checkPermissions(String path) throws IOException;

    static native void sigquit(int pid) throws IOException;

    // enqueue a command (and arguments) to the given door
    static native int enqueue(int fd, String cmd, Object ... args)
        throws IOException;

    static {
        System.loadLibrary("attach");
    }
}
