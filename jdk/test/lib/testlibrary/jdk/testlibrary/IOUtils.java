/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package jdk.testlibrary;

/**
 * Defines useful I/O methods.
 */
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public final class IOUtils {

    /*
     * Prevent instantiation.
     */
    private IOUtils() {}

    /**
     * Read all bytes from <code>in</code>
     * until EOF is detected.
     * @param in input stream, must not be null
     * @return bytes read
     * @throws IOException Any IO error.
     */
    public static byte[] readFully(InputStream is) throws IOException {
        byte[] output = {};
        int pos = 0;
        while (true) {
            int bytesToRead;
            if (pos >= output.length) { // Only expand when there's no room
                bytesToRead = output.length + 1024;
                if (output.length < pos + bytesToRead) {
                    output = Arrays.copyOf(output, pos + bytesToRead);
                }
            } else {
                bytesToRead = output.length - pos;
            }
            int cc = is.read(output, pos, bytesToRead);
            if (cc < 0) {
                if (output.length != pos) {
                    output = Arrays.copyOf(output, pos);
                }
                break;
            }
            pos += cc;
        }
        return output;
    }
}
