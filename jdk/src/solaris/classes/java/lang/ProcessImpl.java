/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.lang.ProcessBuilder.Redirect;

/**
 * This class is for the exclusive use of ProcessBuilder.start() to
 * create new processes.
 *
 * @author Martin Buchholz
 * @since   1.5
 */
final class ProcessImpl {
    private static final sun.misc.JavaIOFileDescriptorAccess fdAccess
        = sun.misc.SharedSecrets.getJavaIOFileDescriptorAccess();

    private ProcessImpl() {}    // Not instantiable

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

    // Only for use by ProcessBuilder.start()
    static Process start(String[] cmdarray,
                         java.util.Map<String,String> environment,
                         String dir,
                         ProcessBuilder.Redirect[] redirects,
                         boolean redirectErrorStream)
        throws IOException
    {
        assert cmdarray != null && cmdarray.length > 0;

        // Convert arguments to a contiguous block; it's easier to do
        // memory management in Java than in C.
        byte[][] args = new byte[cmdarray.length-1][];
        int size = args.length; // For added NUL bytes
        for (int i = 0; i < args.length; i++) {
            args[i] = cmdarray[i+1].getBytes();
            size += args[i].length;
        }
        byte[] argBlock = new byte[size];
        int i = 0;
        for (byte[] arg : args) {
            System.arraycopy(arg, 0, argBlock, i, arg.length);
            i += arg.length + 1;
            // No need to write NUL bytes explicitly
        }

        int[] envc = new int[1];
        byte[] envBlock = ProcessEnvironment.toEnvironmentBlock(environment, envc);

        int[] std_fds;

        FileInputStream  f0 = null;
        FileOutputStream f1 = null;
        FileOutputStream f2 = null;

        try {
            if (redirects == null) {
                std_fds = new int[] { -1, -1, -1 };
            } else {
                std_fds = new int[3];

                if (redirects[0] == Redirect.PIPE)
                    std_fds[0] = -1;
                else if (redirects[0] == Redirect.INHERIT)
                    std_fds[0] = 0;
                else {
                    f0 = new FileInputStream(redirects[0].file());
                    std_fds[0] = fdAccess.get(f0.getFD());
                }

                if (redirects[1] == Redirect.PIPE)
                    std_fds[1] = -1;
                else if (redirects[1] == Redirect.INHERIT)
                    std_fds[1] = 1;
                else {
                    f1 = new FileOutputStream(redirects[1].file(),
                                              redirects[1].append());
                    std_fds[1] = fdAccess.get(f1.getFD());
                }

                if (redirects[2] == Redirect.PIPE)
                    std_fds[2] = -1;
                else if (redirects[2] == Redirect.INHERIT)
                    std_fds[2] = 2;
                else {
                    f2 = new FileOutputStream(redirects[2].file(),
                                              redirects[2].append());
                    std_fds[2] = fdAccess.get(f2.getFD());
                }
            }

        return new UNIXProcess
            (toCString(cmdarray[0]),
             argBlock, args.length,
             envBlock, envc[0],
             toCString(dir),
                 std_fds,
             redirectErrorStream);
        } finally {
            // In theory, close() can throw IOException
            // (although it is rather unlikely to happen here)
            try { if (f0 != null) f0.close(); }
            finally {
                try { if (f1 != null) f1.close(); }
                finally { if (f2 != null) f2.close(); }
            }
        }
    }
}
