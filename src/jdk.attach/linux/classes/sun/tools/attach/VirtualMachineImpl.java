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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.Optional;

import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

/*
 * Linux implementation of HotSpotVirtualMachine
 */
@SuppressWarnings("restricted")
public class VirtualMachineImpl extends HotSpotVirtualMachine {
    // "/tmp" is used as a global well-known location for the files
    // .java_pid<pid>. and .attach_pid<pid>. It is important that this
    // location is the same for all processes, otherwise the tools
    // will not be able to find all Hotspot processes.
    // Any changes to this needs to be synchronized with HotSpot.
    private static final Path TMPDIR = Path.of("/tmp");

    private static final Path PROC     = Path.of("/proc");
    private static final Path STATUS   = Path.of("status");
    private static final Path ROOT_TMP = Path.of("root/tmp");

    String socket_path;
    private OperationProperties props = new OperationProperties(VERSION_1); // updated in ctor

    /**
     * Attaches to the target VM
     */
    VirtualMachineImpl(AttachProvider provider, String vmid) throws AttachNotSupportedException, IOException
    {
        super(provider, vmid);

        // This provider only understands pids
        int pid = Integer.parseInt(vmid);
        if (pid < 1) {
            throw new AttachNotSupportedException("Invalid process identifier: " + vmid);
        }

        // Try to resolve to the "inner most" pid namespace
        final long ns_pid = getNamespacePid(pid);

        // Find the socket file. If not found then we attempt to start the
        // attach mechanism in the target VM by sending it a QUIT signal.
        // Then we attempt to find the socket file again.
        final File socket_file = findSocketFile(pid, ns_pid);
        socket_path = socket_file.getPath();
        if (!socket_file.exists()) {
            // Keep canonical version of File, to delete, in case target process ends and /proc link has gone:
            File f = createAttachFile(pid, ns_pid).getCanonicalFile();

            boolean timedout = false;

            try {
                checkCatchesAndSendQuitTo(pid, false);

                // give the target VM time to start the attach mechanism
                final int delay_step = 100;
                final long timeout = attachTimeout();
                long time_spent = 0;
                long delay = 0;
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
                          "or HotSpot VM not loaded", socket_path, pid, time_spent));
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

    // Return the socket file for the given process.
    private File findSocketFile(long pid, long ns_pid) throws AttachNotSupportedException, IOException {
        return new File(findTargetProcessTmpDirectory(pid, ns_pid), ".java_pid" + ns_pid);
    }

    // On Linux a simple handshake is used to start the attach mechanism
    // if not already started. The client creates a .attach_pid<pid> file in the
    // target VM's working directory (or temp directory), and the SIGQUIT handler
    // checks for the file.
    private File createAttachFile(long pid, long ns_pid) throws AttachNotSupportedException, IOException {
        Path fn   = Path.of(".attach_pid" + ns_pid);
        Path path = PROC.resolve(Path.of(Long.toString(pid), "cwd")).resolve(fn);
        File f    = new File(path.toString());
        try {
            // Do not canonicalize the file path, or we will fail to attach to a VM in a container.
            f.createNewFile();
        } catch (IOException _) {
            f = new File(findTargetProcessTmpDirectory(pid, ns_pid), fn.toString());
            f.createNewFile();
        }
        return f;
    }

    private String findTargetProcessTmpDirectory(long pid, long ns_pid) throws AttachNotSupportedException, IOException {
        final var procPidRoot = PROC.resolve(Long.toString(pid)).resolve(ROOT_TMP);

        /* We need to handle at least 4 different cases:
         * 1. Caller and target processes share PID namespace and root filesystem (host to host or container to
         *    container with both /tmp mounted between containers).
         * 2. Caller and target processes share PID namespace and root filesystem but the target process has elevated
         *    privileges (host to host).
         * 3. Caller and target processes share PID namespace but NOT root filesystem (container to container).
         * 4. Caller and target processes share neither PID namespace nor root filesystem (host to container)
         *
         * if target is elevated, we cant use /proc/<pid>/... so we have to fallback to /tmp, but that may not be shared
         * with the target/attachee process, we can try, except in the case where the ns_pid also exists in this pid ns
         * which is ambiguous, if we share /tmp with the intended target, the attach will succeed, if we do not,
         * then we will potentially attempt to attach to some arbitrary process with the same pid (in this pid ns)
         * as that of the intended target (in its * pid ns).
         *
         * so in that case we should prehaps throw - or risk sending SIGQUIT to some arbitrary process... which could kill it
         *
         * however we can also check the target pid's signal masks to see if it catches SIGQUIT and only do so if in
         * fact it does ... this reduces the risk of killing an innocent process in the current ns as opposed to
         * attaching to the actual target JVM ... c.f: checkCatchesAndSendQuitTo() below.
         *
         * note that if pid == ns_pid we are in a shared pid ns with the target and may (potentially) share /tmp
         */

        return (Files.isWritable(procPidRoot) ? procPidRoot : TMPDIR).toString();
    }

    // Return the inner most namespaced PID if there is one,
    // otherwise return the original PID.
    private long getNamespacePid(long pid) throws AttachNotSupportedException, IOException {
        // Assuming a real procfs sits beneath, reading this doesn't block
        // nor will it consume a lot of memory.
        final var statusFile = PROC.resolve(Long.toString(pid)).resolve(STATUS).toString();
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
                    return Long.parseLong(parts[parts.length - 1]);
                }
            }
            // Old kernels may not have NSpid field (i.e. 3.10).
            // Fallback to original pid in the event we cannot deduce.
            return pid;
        } catch (NumberFormatException | IOException x) {
            throw new AttachNotSupportedException("Unable to parse namespace");
        }
    }

    private static final String FIELD = "field";
    private static final String MASK  = "mask";

    private static final Pattern SIGNAL_MASK_PATTERN = Pattern.compile("(?<" + FIELD + ">Sig\\p{Alpha}{3}):\\s+(?<" + MASK + ">\\p{XDigit}{16}).*");

    private static final long SIGQUIT = 0b100; // mask bit for SIGQUIT

    private static boolean checkCatchesAndSendQuitTo(int pid, boolean throwIfNotReady) throws AttachNotSupportedException, IOException {
        var quitIgn = false;
        var quitBlk = false;
        var quitCgt = false;

        final var procPid = PROC.resolve(Integer.toString(pid));

        var readBlk = false;
        var readIgn = false;
        var readCgt = false;


        if (!Files.exists(procPid)) throw new IOException("non existent JVM pid: " + pid);

        for (var line : Files.readAllLines(procPid.resolve("status"))) {

            if (!line.startsWith("Sig")) continue; // to speed things up ... avoids the matcher/RE invocation...

            final var m = SIGNAL_MASK_PATTERN.matcher(line);

            if (!m.matches()) continue;

            var       sigmask = m.group(MASK);
            final var slen    = sigmask.length();

            sigmask = sigmask.substring(slen / 2 , slen); // only really interested in the non r/t signals ...

            final var sigquit = (Long.valueOf(sigmask, 16) & SIGQUIT) != 0L;

            switch (m.group(FIELD)) {
                case "SigBlk": { quitBlk = sigquit; readBlk = true; break; }
                case "SigIgn": { quitIgn = sigquit; readIgn = true; break; }
                case "SigCgt": { quitCgt = sigquit; readCgt = true; break; }
            }

            if (readBlk && readIgn && readCgt) break;
        }

        final boolean  okToSendQuit = (!quitIgn && quitCgt); // ignore blocked as it may be temporary ...

        if (okToSendQuit) {
            sendQuitTo(pid);
        } else if (throwIfNotReady) {
            Optional<String> cmdline = Optional.empty();

            try (final var clf = Files.lines(procPid.resolve("cmdline"))) {
                cmdline = clf.findFirst();
            }

            var cmd = "null"; // default

            if (cmdline.isPresent()) {
                cmd = cmdline.get();
                cmd = cmd.substring(0, cmd.length() - 1); // remove trailing \0
            }

            throw new AttachNotSupportedException("pid: " + pid + " cmd: '" + cmd + "' state is not ready to participate in attach handshake!");
        }

        return okToSendQuit;
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
