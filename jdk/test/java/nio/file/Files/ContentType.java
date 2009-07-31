/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/* @test
 * @bug 4313887
 * @summary Unit test for probeContentType method
 * @library ..
 * @build ContentType SimpleFileTypeDetector
 * @run main ContentType
 */

import java.nio.file.*;
import java.io.*;

/**
 * Uses Files.probeContentType to probe html file and custom file type.
 */

public class ContentType {

    static Path createHtmlFile() throws IOException {
        Path file = File.createTempFile("foo", ".html").toPath();
        OutputStream out = file.newOutputStream();
        try {
            out.write("<html><body>foo</body></html>".getBytes());
        } finally {
            out.close();
        }

        return file;
    }

    static Path createGrapeFile() throws IOException {
        return File.createTempFile("red", ".grape").toPath();
    }

    public static void main(String[] args) throws IOException {

        // exercise default file type detector
        Path file = createHtmlFile();
        try {
            String type = Files.probeContentType(file);
            if (type == null) {
                System.err.println("Content type cannot be determined - test skipped");
            } else {
                if (!type.equals("text/html"))
                    throw new RuntimeException("Unexpected type: " + type);
            }
        } finally {
            file.delete();
        }

        // exercise custom file type detector
        file = createGrapeFile();
        try {
            String type = Files.probeContentType(file);
            if (type == null)
                throw new RuntimeException("Custom file type detector not installed?");
            if (!type.equals("grape/unknown"))
                throw new RuntimeException("Unexpected type: " + type);
        } finally {
            file.delete();
        }

    }
}
