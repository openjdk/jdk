/*
 * Copyright (c) 2008, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;

// This class is public so that tools can invoke.
public class TextFileReader implements AutoCloseable {
    private BufferedReader reader;
    private int lineNo;

    public TextFileReader(String filename) throws IOException {
        this(new File(new File(System.getProperty("test.src", "."),
                               "TimeZoneData"),
                      filename));
    }

    public TextFileReader(File file) throws IOException {
        InputStream is = new FileInputStream(file);
        reader = new BufferedReader(new InputStreamReader(is, "utf-8"));
    }

    public String readLine() throws IOException {
        return getLine();
    }

    public String getLine() throws IOException {
        checkReader();

        String line;
        while ((line = reader.readLine()) != null) {
            lineNo++;
            line = line.trim();
            // Skip blank and comment lines.
            if (line.length() == 0) {
                continue;
            }
            int x = line.indexOf('#');
            if (x == 0) {
                    continue;
            }
            if (x > 0) {
                line = line.substring(0, x).trim();
            }
            break;
        }
        return line;
    }

    public String getRawLine() throws IOException {
        checkReader();

        String line = reader.readLine();
        if (line != null) {
            lineNo++;
        }
        return line;
    }

    private void checkReader() throws IOException {
        if (reader == null) {
            throw new IOException("This TextFileReader has been closed.");
        }
    }

    @Override
    public void close() throws IOException {
        reader.close();
        reader = null;
    }

    int getLineNo() {
        return lineNo;
    }
}
