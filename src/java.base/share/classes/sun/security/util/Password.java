/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.util;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.Arrays;

import jdk.internal.access.SharedSecrets;
import jdk.internal.io.JdkConsoleImpl;
import jdk.internal.misc.VM;

/**
 * A utility class for reading passwords
 */
public class Password {
    /** Reads user password from given input stream. */
    public static char[] readPassword(InputStream in) throws IOException {
        return readPassword(in, false);
    }

    /** Reads user password from given input stream.
     * @param isEchoOn true if the password should be echoed on the screen
     */
    @SuppressWarnings("fallthrough")
    public static char[] readPassword(InputStream in, boolean isEchoOn)
            throws IOException {

        char[] consoleEntered = null;
        byte[] consoleBytes = null;
        char[] buf = null;

        try {
            // Only use Console if `in` is the initial System.in
            if (!isEchoOn) {
                if (in == SharedSecrets.getJavaLangAccess().initialSystemIn()
                        && ConsoleHolder.consoleIsAvailable()) {
                    consoleEntered = ConsoleHolder.readPassword();
                    // readPassword might return null. Stop now.
                    if (consoleEntered == null) {
                        return null;
                    }
                    consoleBytes = ConsoleHolder.convertToBytes(consoleEntered);
                    in = new ByteArrayInputStream(consoleBytes);
                } else if (in == System.in && VM.isBooted()
                            && System.in.available() == 0) {
                    // Warn if reading password from System.in but it's empty.
                    // This may be running in an IDE Run Window or in JShell,
                    // which acts like an interactive console and echoes the
                    // entered password. In this case, print a warning that
                    // the password might be echoed. If available() is not zero,
                    // it's more likely the input comes from a pipe, such as
                    // "echo password |" or "cat password_file |" where input
                    // will be silently consumed without echoing to the screen.
                    // Warn only if VM is booted and ResourcesMgr is available.
                    System.err.print(ResourcesMgr.getString
                            ("warning.input.may.be.visible.on.screen"));
                }
            }

            // Rest of the lines still necessary for KeyStoreLoginModule
            // and when there is no console.
            buf = new char[128];

            int room = buf.length;
            int offset = 0;
            int c;

            boolean done = false;
            while (!done) {
                switch (c = in.read()) {
                  case -1:
                  case '\n':
                      done = true;
                      break;

                  case '\r':
                    int c2 = in.read();
                    if ((c2 != '\n') && (c2 != -1)) {
                        if (!(in instanceof PushbackInputStream)) {
                            in = new PushbackInputStream(in);
                        }
                        ((PushbackInputStream)in).unread(c2);
                    } else {
                        done = true;
                        break;
                    }
                    /* fall through */
                  default:
                    if (--room < 0) {
                        char[] oldBuf = buf;
                        buf = new char[offset + 128];
                        room = buf.length - offset - 1;
                        System.arraycopy(oldBuf, 0, buf, 0, offset);
                        Arrays.fill(oldBuf, ' ');
                    }
                    buf[offset++] = (char) c;
                    break;
                }
            }

            if (offset == 0) {
                return null;
            }

            char[] ret = new char[offset];
            System.arraycopy(buf, 0, ret, 0, offset);
            return ret;
        } finally {
            if (consoleEntered != null) {
                Arrays.fill(consoleEntered, ' ');
            }
            if (consoleBytes != null) {
                Arrays.fill(consoleBytes, (byte)0);
            }
            if (buf != null) {
                Arrays.fill(buf, ' ');
            }
        }
    }

    // Everything on Console or JdkConsoleImpl is inside this class.
    private static class ConsoleHolder {

        // primary console; may be null
        private static final Console c1;
        // secondary console (when stdout is redirected); may be null
        private static final JdkConsoleImpl c2;
        // encoder for c1 or c2
        private static final CharsetEncoder enc;

        static {
            c1 = System.console();
            Charset charset;
            if (c1 != null) {
                c2 = null;
                charset = c1.charset();
            } else {
                c2 = JdkConsoleImpl.passwordConsole().orElse(null);
                charset = (c2 != null) ? c2.charset() : null;
            }
            enc = charset == null ? null : charset.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE);
        }

        public static boolean consoleIsAvailable() {
            return c1 != null || c2 != null;
        }

        public static char[] readPassword() {
            assert consoleIsAvailable();
            if (c1 != null) {
                return c1.readPassword();
            } else {
                try {
                    return c2.readPasswordNoNewLine();
                } finally {
                    System.err.println();
                }
            }
        }

        /**
         * Convert a password read from console into its original bytes.
         *
         * @param pass a char[]
         * @return its byte[] format, equivalent to new String(pass).getBytes()
         *      but String is immutable and cannot be cleaned up.
         */
        public static byte[] convertToBytes(char[] pass) {
            assert consoleIsAvailable();
            byte[] ba = new byte[(int) (enc.maxBytesPerChar() * pass.length)];
            ByteBuffer bb = ByteBuffer.wrap(ba);
            synchronized (enc) {
                enc.reset().encode(CharBuffer.wrap(pass), bb, true);
            }
            if (bb.remaining() > 0) {
                bb.put((byte)'\n'); // will be recognized as a stop sign
            }
            return ba;
        }
    }
}
