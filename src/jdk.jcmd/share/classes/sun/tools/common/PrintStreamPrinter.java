/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.common;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

/**
 * A helper class which prints the content of input streams to print streams.
 */
public class PrintStreamPrinter {

    /**
     * Reads bytes from the input stream and writes them
     * with the given print stream. Closes the input stream before it returns.
     *
     * @return The number of printed bytes.
     */
    public static long drain(InputStream is, PrintStream ps) throws IOException {
        long result = 0;

        try (BufferedInputStream bis = new BufferedInputStream(is)) {
            byte b[] = new byte[256];
            int n;

            do {
                n = bis.read(b);

                if (n > 0) {
                    result += n;
                    ps.write(b, 0, n);
                }
            } while (n > 0);
        }
        is.close();

        return result;
    }
}
