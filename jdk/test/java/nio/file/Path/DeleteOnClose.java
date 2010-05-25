/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.*;
import static java.nio.file.StandardOpenOption.*;
import java.io.*;
import java.util.*;

public class DeleteOnClose {

    public static void main(String[] args) throws IOException {
        // open file but do not close it. Its existance will be checked by
        // the calling script.
        Paths.get(args[0]).newByteChannel(READ, WRITE, DELETE_ON_CLOSE);

        // check temporary file has been deleted after closing it
        Path file = File.createTempFile("blah", "tmp").toPath();
        file.newByteChannel(READ, WRITE, DELETE_ON_CLOSE).close();
        if (file.exists())
            throw new RuntimeException("Temporary file was not deleted");

        Path dir = TestUtil.createTemporaryDirectory();
        try {
            // check that DELETE_ON_CLOSE fails when file is a sym link
            if (TestUtil.supportsLinks(dir)) {
                file = dir.resolve("foo").createFile();
                Path link = dir.resolve("link").createSymbolicLink(file);
                try {
                    link.newByteChannel(READ, WRITE, DELETE_ON_CLOSE);
                    throw new RuntimeException("IOException expected");
                } catch (IOException ignore) { }
            }

            // check that DELETE_ON_CLOSE works with files created via open
            // directories
            DirectoryStream stream = dir.newDirectoryStream();
            try {
                if (stream instanceof SecureDirectoryStream) {
                    SecureDirectoryStream secure = (SecureDirectoryStream)stream;
                    file = Paths.get("foo");

                    Set<OpenOption> opts = new HashSet<OpenOption>();
                    opts.add(WRITE);
                    opts.add(DELETE_ON_CLOSE);
                    secure.newByteChannel(file, opts).close();

                    if (dir.resolve(file).exists())
                        throw new RuntimeException("File not deleted");
                }
            } finally {
                stream.close();
            }
        } finally {
            TestUtil.removeAll(dir);
        }
    }
}
