/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/*
* @test
* @bug 8260621
* @summary Bug 8260621: ThreadLocal memory leak in ImageBufferCache
* @run main/othervm -Xmx32m -XX:MaxMetaspaceSize=32m Bug8260621Test
* @author Bo Zhang
*/

public class Bug8260621Test {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        Map<String, String> map = new HashMap<>();
        map.put("java.home", System.getProperty("java.home"));
        for (int i = 0; i < 1000; ++i) {
            try (FileSystem fs = FileSystems.newFileSystem(URI.create("jrt:/"), map)) {
                Path path = fs.getPath("modules");
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                    stream.forEach(it -> {
                    });
                }
            }
        }
    }
}
