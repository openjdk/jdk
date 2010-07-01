/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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
import java.io.FileNotFoundException;
import java.util.Properties;

/*
 * Solaris implementation of HotSpotVirtualMachine.
 */
public class SolarisVirtualMachine extends HotSpotVirtualMachine {
    // Use /tmp instead of /var/tmp on Solaris as /tmp is the default used by
    // HotSpot when the property is not set on the command line.
    private static final String tmpdir1 = System.getProperty("java.io.tmpdir");
    private static final String tmpdir =
        (tmpdir1.equals("/var/tmp") || tmpdir1.equals("/var/tmp/")) ? "/tmp" : tmpdir1;

    // door descriptor;
    private int fd = -1;

    /**
     * Attaches to the target VM
     */
    SolarisVirtualMachine(AttachProvider provider, String vmid)
        throws AttachNotSupportedException, IOException
    {
        super(provider, vmid);
        // This provider only understands process-ids (pids).
        int pid;
        try {
            pid = Integer.parseInt(vmid);
        } catch (NumberFormatException x) {
            throw new AttachNotSupportedException("invalid process identifier");
        }

        // Opens the door file to the target VM. If the file is not
        // found it might mean that the attach mechanism isn't started in the
        // target VM so we attempt to start it and retry.
        try {
            fd = openDoor(pid);
        } catch (FileNotFoundException fnf1) {
            File f = createAttachFile(pid);
            try {
                // kill -QUIT will tickle target VM to check for the
                // attach file.
                sigquit(pid);

                // give the target VM time to start the attach mechanism
                int i = 0;
                long delay = 200;
                int retries = (int)(attachTimeout() / delay);
                do {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException x) { }
                    try {
                        fd = openDoor(pid);
                    } catch (FileNotFoundException fnf2) { }
                    i++;
                } while (i <= retries && fd == -1);
                if (fd == -1) {
                    throw new AttachNotSupportedException(
                        "Unable to open door: target process not responding or " +
                        "HotSpot VM not loaded");
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
        assert args.length <= 3;                // includes null

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
            sis.close();
            if (cmd.equals("load")) {
                throw new AgentLoadException("Failed to load agent library");
            } else {
                throw new IOException("Command failed in target VM");
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

            return SolarisVirtualMachine.read(s, bs, off, len);
        }

        public void close() throws IOException {
            SolarisVirtualMachine.close(s);
        }
    }

    // The door is attached to .java_pid<pid> in the target VM's working
    // directory or temporary directory.
    private int openDoor(int pid) throws IOException {
        // First check for a .java_pid<pid> file in the working directory
        // of the target process
        String fn = ".java_pid" + pid;
        String path = "/proc/" + pid + "/cwd/" + fn;
        try {
            fd = open(path);
        } catch (FileNotFoundException fnf) {
            path = tmpdir + "/" + fn;
            fd = open(path);
        }

        // Check that the file owner/permission to avoid attaching to
        // bogus process
        try {
            checkPermissions(path);
        } catch (IOException ioe) {
            close(fd);
            throw ioe;
        }
        return fd;
    }

    // On Solaris/Linux a simple handshake is used to start the attach mechanism
    // if not already started. The client creates a .attach_pid<pid> file in the
    // target VM's working directory (or temporary directory), and the SIGQUIT
    // handler checks for the file.
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
