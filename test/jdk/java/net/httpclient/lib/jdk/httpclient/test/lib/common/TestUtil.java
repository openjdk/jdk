/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.httpclient.test.lib.common;

import java.io.*;
import java.nio.file.*;

public final class TestUtil {

    private TestUtil() {}

    public static void assertFilesEqual(Path f1, Path f2) {
        try (InputStream s1 = new BufferedInputStream(Files.newInputStream(f1));
             InputStream s2 = new BufferedInputStream(Files.newInputStream(f2))) {
            for (long i = 0; ; i++) {
                int c1 = s1.read();
                int c2 = s2.read();
                String message = null;
                if (c1 == -1 && c2 == -1) {
                    break;
                } else if (c1 == -1) {
                    message = String.format("At index %d, `%s` reached EOF, while `%s` did not", i, f1, f2);
                } else if (c2 == -1) {
                    message = String.format("At index %d, `%s` reached EOF, while `%s` did not", i, f2, f1);
                } else if (c1 != c2) {
                    message = String.format(
                            "At index %d, `%s` has `%s`, while `%s` has `%s`",
                            i, f1, Character.toString(c1), f2, Character.toString(c2));
                }
                if (message != null) {
                    throw new AssertionError(message);
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

}
