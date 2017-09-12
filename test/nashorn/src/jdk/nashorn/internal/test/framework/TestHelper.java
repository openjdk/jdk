/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.test.framework;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;

/**
 * Simple utilities to deal with build-dir, read/dump files etc.
 */
@SuppressWarnings("javadoc")
public abstract class TestHelper {

    public static final String TEST_ROOT   = "test" + File.separator + "nashorn";
    public static final String BUILD_ROOT =
        System.getProperty("build.dir", "build") + File.separator + "test";
    public static final String TEST_PREFIX = TEST_ROOT + File.separator;

    private TestHelper() {
        // empty
    }

    protected static File makeBuildDir(final File testFile) {
        final File buildDir = getBuildDir(testFile);
        if (!new File(BUILD_ROOT).exists()) {
            throw new IllegalArgumentException("no " + BUILD_ROOT + " directory in " + new File(".").getAbsolutePath());
        }
        buildDir.mkdirs();
        return buildDir;
    }

    protected static File getBuildDir(final File testFile) {
        if (!testFile.getPath().startsWith(TEST_PREFIX)) {
            throw new IllegalArgumentException("test file path not a relative pathname");
        }
        final File buildDir = new File(BUILD_ROOT + File.separator + testFile.getParent().substring(TEST_PREFIX.length()));
        return buildDir;
    }

    // return the first line of the given file
    protected static String firstLine(final File file) throws IOException {
        return content(file, true);
    }

    // return the full content of the file as a String
    protected static String fullContent(final File file) throws IOException {
        return content(file, false);
    }

    private static String content(final File file, final boolean firstLine) throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final PrintStream ps = new PrintStream(bos);
        dumpFile(ps, file);
        final String contents = bos.toString();

        if (! firstLine) {
            return contents;
        }

        // else return only the first line, stripping the trailing cr, lf or cr+lf,
        // if present
        final int cr = contents.indexOf('\r');
        if (cr > 0) {
            return contents.substring(0, cr);
        }
        final int lf = contents.indexOf('\n');
        if (lf > 0) {
            return contents.substring(0, lf);
        }
        return contents;
    }

    // dump the content of given reader on standard output
    protected static void dumpFile(final Reader rdr) throws IOException {
        dumpFile(System.out, rdr);
    }

    // dump the content of given reader on given output stream
    protected static void dumpFile(final PrintStream output, final Reader rdr) throws IOException {
        try (BufferedReader reader = new BufferedReader(rdr)) {
            while (true) {
                final String line = reader.readLine();
                if (line == null) {
                    break;
                }
                output.println(line);
            }
        }
    }

    // dump the content of given file on standard output
    protected static void dumpFile(final File file) throws IOException {
        dumpFile(System.out, file);
    }

    // dump the content of given file on given output stream
    protected static void dumpFile(final PrintStream output, final File file) throws IOException {
        try (Reader rdr = new FileReader(file); BufferedReader reader = new BufferedReader(rdr)) {
            while (true) {
                final String line = reader.readLine();
                if (line == null) {
                    break;
                }
                output.println(line);
            }
        }
    }
}
