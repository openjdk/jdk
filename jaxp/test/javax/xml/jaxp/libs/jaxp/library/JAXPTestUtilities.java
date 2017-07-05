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
package jaxp.library;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.testng.Assert.fail;

/**
 * This is an interface provide basic support for JAXP functional test.
 */
public class JAXPTestUtilities {
    /**
     * Prefix for error message.
     */
    public static final String ERROR_MSG_HEADER = "Unexcepted exception thrown:";

    /**
     * Prefix for error message on clean up block.
     */
    public static final String ERROR_MSG_CLEANUP = "Clean up failed on %s";

    /**
     * Force using slash as File separator as we always use cygwin to test in
     * Windows platform.
     */
    public static final String FILE_SEP = "/";

    /**
     * User home.
     */
    public static final String USER_DIR = System.getProperty("user.dir", ".");

    /**
     * TEMP file directory.
     */
    public static final String TEMP_DIR = System.getProperty("java.io.tmpdir", ".");

    /**
     * Compare contents of golden file with test output file line by line.
     * return true if they're identical.
     * @param goldfile Golden output file name
     * @param outputfile Test output file name
     * @return true if two files are identical.
     *         false if two files are not identical.
     * @throws IOException if an I/O error occurs reading from the file or a
     *         malformed or unmappable byte sequence is read
     */
    public static boolean compareWithGold(String goldfile, String outputfile)
            throws IOException {
        return Files.readAllLines(Paths.get(goldfile)).
                equals(Files.readAllLines(Paths.get(outputfile)));
    }

    /**
     * Prints error message if an exception is thrown
     * @param ex The exception is thrown by test.
     */
    public static void failUnexpected(Throwable ex) {
        fail(ERROR_MSG_HEADER, ex);
    }

    /**
     * Prints error message if an exception is thrown when clean up a file.
     * @param ex The exception is thrown in cleaning up a file.
     * @param name Cleaning up file name.
     */
    public static void failCleanup(IOException ex, String name) {
        fail(String.format(ERROR_MSG_CLEANUP, name), ex);
    }
}
