/*
 * Copyright 1995-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package java.lang;

import java.io.*;

/* This class is for the exclusive use of ProcessBuilder.start() to
 * create new processes.
 *
 * @author Martin Buchholz
 * @since   1.5
 */

final class ProcessImpl extends Process {

    // System-dependent portion of ProcessBuilder.start()
    static Process start(String cmdarray[],
                         java.util.Map<String,String> environment,
                         String dir,
                         boolean redirectErrorStream)
        throws IOException
    {
        String envblock = ProcessEnvironment.toEnvironmentBlock(environment);
        return new ProcessImpl(cmdarray, envblock, dir, redirectErrorStream);
    }

    private long handle = 0;
    private FileDescriptor stdin_fd;
    private FileDescriptor stdout_fd;
    private FileDescriptor stderr_fd;
    private OutputStream stdin_stream;
    private InputStream stdout_stream;
    private InputStream stderr_stream;

    private ProcessImpl(String cmd[],
                        String envblock,
                        String path,
                        boolean redirectErrorStream)
        throws IOException
    {
        // Win32 CreateProcess requires cmd[0] to be normalized
        cmd[0] = new File(cmd[0]).getPath();

        StringBuilder cmdbuf = new StringBuilder(80);
        for (int i = 0; i < cmd.length; i++) {
            if (i > 0) {
                cmdbuf.append(' ');
            }
            String s = cmd[i];
            if (s.indexOf(' ') >= 0 || s.indexOf('\t') >= 0) {
                if (s.charAt(0) != '"') {
                    cmdbuf.append('"');
                    cmdbuf.append(s);
                    if (s.endsWith("\\")) {
                        cmdbuf.append("\\");
                    }
                    cmdbuf.append('"');
                } else if (s.endsWith("\"")) {
                    /* The argument has already been quoted. */
                    cmdbuf.append(s);
                } else {
                    /* Unmatched quote for the argument. */
                    throw new IllegalArgumentException();
                }
            } else {
                cmdbuf.append(s);
            }
        }
        String cmdstr = cmdbuf.toString();

        stdin_fd  = new FileDescriptor();
        stdout_fd = new FileDescriptor();
        stderr_fd = new FileDescriptor();

        handle = create(cmdstr, envblock, path, redirectErrorStream,
                        stdin_fd, stdout_fd, stderr_fd);

        java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction() {
            public Object run() {
                stdin_stream =
                    new BufferedOutputStream(new FileOutputStream(stdin_fd));
                stdout_stream =
                    new BufferedInputStream(new FileInputStream(stdout_fd));
                stderr_stream =
                    new FileInputStream(stderr_fd);
                return null;
            }
        });
    }

    public OutputStream getOutputStream() {
        return stdin_stream;
    }

    public InputStream getInputStream() {
        return stdout_stream;
    }

    public InputStream getErrorStream() {
        return stderr_stream;
    }

    public void finalize() {
        closeHandle(handle);
    }

    private static final int STILL_ACTIVE = getStillActive();
    private static native int getStillActive();

    public int exitValue() {
        int exitCode = getExitCodeProcess(handle);
        if (exitCode == STILL_ACTIVE)
            throw new IllegalThreadStateException("process has not exited");
        return exitCode;
    }
    private static native int getExitCodeProcess(long handle);

    public int waitFor() throws InterruptedException {
        waitForInterruptibly(handle);
        if (Thread.interrupted())
            throw new InterruptedException();
        return exitValue();
    }
    private static native void waitForInterruptibly(long handle);

    public void destroy() { terminateProcess(handle); }
    private static native void terminateProcess(long handle);

    private static native long create(String cmdstr,
                                      String envblock,
                                      String dir,
                                      boolean redirectErrorStream,
                                      FileDescriptor in_fd,
                                      FileDescriptor out_fd,
                                      FileDescriptor err_fd)
        throws IOException;

    private static native boolean closeHandle(long handle);
}
