/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;

/*
 * Linux implementation of HotSpotVirtualMachine
 */
public class VirtualMachineImpl extends HotSpotVirtualMachine {
    // "/tmp" is used as a global well-known location for the files
    // .java_pid<pid>. and .attach_pid<pid>. It is important that this
    // location is the same for all processes, otherwise the tools
    // will not be able to find all Hotspot processes.
    // Any changes to this needs to be synchronized with HotSpot.
    private static final String tmpdir = "/tmp";

    private static final Optional<Path> MOUNT_NS;

    static {
        Path mountNs = null;
        try {
            mountNs = Files.readSymbolicLink(Path.of("/proc/self/ns/mnt"));
        } catch (IOException ioe) {
        } finally {
            MOUNT_NS = Optional.ofNullable(mountNs);
        }
    }

    String socket_path;

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

        Path targetProcessTmpDir = findTargetProcessTmpDirectory(pid);

        // Try to resolve to the "inner most" pid namespace
        int ns_pid = getNamespacePid(pid);

        // Find the socket file. If not found then we attempt to start the
        // attach mechanism in the target VM by sending it a QUIT signal.
        // Then we attempt to find the socket file again.
        File socket_file = findSocketFile(targetProcessTmpDir, ns_pid);
        socket_path = socket_file.getPath();
        if (!socket_file.exists()) {
            // Keep canonical version of File, to delete, in case target process ends and /proc link has gone:
            File f = createAttachFile(pid, ns_pid, targetProcessTmpDir).getCanonicalFile();
            try {
                sendQuitTo(pid);

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

                    time_spend += delay;
                    if (time_spend > timeout/2 && !socket_file.exists()) {
                        // Send QUIT again to give target VM the last chance to react
                        sendQuitTo(pid);
                    }
                } while (time_spend <= timeout && !socket_file.exists());
                if (!socket_file.exists()) {
                    throw new AttachNotSupportedException(
                        String.format("Unable to open socket file %s: " +
                          "target process %d doesn't respond within %dms " +
                          "or HotSpot VM not loaded", socket_path, pid,
                                      time_spend));
                }
            } finally {
                f.delete();
            }
        }

        // Check that the file owner/permission to avoid attaching to
        // bogus process
        checkPermissions(socket_path);

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

    // protocol version
    private static final String PROTOCOL_VERSION = "1";

    /**
     * Execute the given command in the target VM.
     */
    InputStream execute(String cmd, Object ... args) throws AgentLoadException, IOException {
        assert args.length <= 3;                // includes null
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
        // <ver> <cmd> <args...>
        try {
            writeString(s, PROTOCOL_VERSION);
            writeString(s, cmd);

            for (int i = 0; i < 3; i++) {
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
        SocketInputStreamImpl sis = new SocketInputStreamImpl(s);

        // Process the command completion status
        processCompletionStatus(ioe, cmd, sis);

        // Return the input stream so that the command output can be read
        return sis;
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

    // Return the socket file for the given process.
    private File findSocketFile(Path targetProcessTmpDirectory, int ns_pid) {
        return targetProcessTmpDirectory.resolve(".java_pid" + ns_pid).toFile();
    }

    // On Linux a simple handshake is used to start the attach mechanism
    // if not already started. The client creates a .attach_pid<pid> file in the
    // target VM's working directory (or temp directory), and the SIGQUIT handler
    // checks for the file.
    private File createAttachFile(int pid, int ns_pid, Path targetProcessTmpDirectory) throws IOException {
        String fn = ".attach_pid" + ns_pid;
        String path = "/proc/" + pid + "/cwd/" + fn;
        File f = new File(path);
        try {
            // Do not canonicalize the file path, or we will fail to attach to a VM in a container.
            f.createNewFile();
        } catch (IOException x) {
            f = targetProcessTmpDirectory.resolve(fn).toFile();
            f.createNewFile();
        }
        return f;
    }

    private Path findTargetProcessTmpDirectory(int pid) {
        // We need to handle at least 4 different cases:
        // 1. Caller and target processes share PID namespace and root filesystem (host to host or container to
        //    container with both /tmp mounted between containers).
        // 2. Caller and target processes share PID namespace and root filesystem but the target process has elevated
        //    privileges (host to host).
        // 3. Caller and target processes share PID namespace but NOT root filesystem (container to container).
        // 4. Caller and target processes share neither PID namespace nor root filesystem (host to container).
        //
        // Hence, we start out by comparing mount namespaces. If they match, we know for sure that reading from and
        // writing to /tmp will work. If not, we check if we can access the target process' /tmp folder through
        // /proc/<pid>/root/tmp. This works in all cases except case 2. Even though both the caller and target process
        // are running as the same user, access to the /proc/<pid>/root symlink is governed by ptrace access mode
        // PTRACE_MODE_READ_FSCREDS, and if the target process has elevated privileges set by setcap, access is denied
        // (see JDK-8226919). If /proc/<pid>/root is not readable, as a last resort we fall back to trying /tmp.

        Optional<Path> targetMountNs = Optional.empty();

        try {
            targetMountNs = Optional.ofNullable(Files.readSymbolicLink(Path.of("/proc", Integer.toString(pid), "ns", "mnt")));
        } catch (IOException _) {
            // do nothing...
        }

        final boolean sameMountNs = MOUNT_NS.isPresent() && targetMountNs.isPresent() && MOUNT_NS.equals(targetMountNs);

        if (sameMountNs) {
            // Cases 1 and 3
            return Path.of(tmpdir);
        }

        Path procRootDirectory = Path.of("/proc", Integer.toString(pid), "root" );
        if (Files.isReadable(procRootDirectory)) {
            // Case 4
            return Path.of(procRootDirectory.toString(), tmpdir);
        }

        // Case 2, and cases where no matching process is running or the attacher process is running as a non-privileged
        // user and the target process is running as another user than the attacher.
        return Path.of(tmpdir);
    }

    /*
     * Write/sends the given to the target VM. String is transmitted in
     * UTF-8 encoding.
     */
    private void writeString(int fd, String s) throws IOException {
        if (s.length() > 0) {
            byte[] b = s.getBytes(UTF_8);
            VirtualMachineImpl.write(fd, b, 0, b.length);
        }
        byte b[] = new byte[1];
        b[0] = 0;
        write(fd, b, 0, 1);
    }


    // Return the inner most namespaced PID if there is one,
    // otherwise return the original PID.
    private int getNamespacePid(int pid) throws AttachNotSupportedException {
        // Assuming a real procfs sits beneath, reading this doesn't block
        // nor will it consume a lot of memory.
        String statusFile = "/proc/" + pid + "/status";
        File f = new File(statusFile);
        if (!f.exists()) {
            return pid; // Likely a bad pid, but this is properly handled later.
        }

        Path statusPath = Paths.get(statusFile);

        try {
            for (String line : Files.readAllLines(statusPath)) {
                String[] parts = line.split(":");
                if (parts.length == 2 && parts[0].trim().equals("NSpid")) {
                    parts = parts[1].trim().split("\\s+");
                    // The last entry represents the PID the JVM "thinks" it is.
                    // Even in non-namespaced pids these entries should be
                    // valid. You could refer to it as the inner most pid.
                    int ns_pid = Integer.parseInt(parts[parts.length - 1]);
                    return ns_pid;
                }
            }
            // Old kernels may not have NSpid field (i.e. 3.10).
            // Fallback to original pid in the event we cannot deduce.
            return pid;
        } catch (NumberFormatException | IOException x) {
            throw new AttachNotSupportedException("Unable to parse namespace");
        }
    }


    //-- native methods

    static native void sendQuitTo(int pid) throws IOException;

    static native void checkPermissions(String path) throws IOException;

    static native int socket() throws IOException;

    static native void connect(int fd, String path) throws IOException;

    static native void close(int fd) throws IOException;

    static native int read(int fd, byte buf[], int off, int bufLen) throws IOException;

    static native void write(int fd, byte buf[], int off, int bufLen) throws IOException;

    static {
        System.loadLibrary("attach");
    }
}
