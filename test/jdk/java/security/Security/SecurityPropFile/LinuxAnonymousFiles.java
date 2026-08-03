/*
 * Copyright (c) 2026, Red Hat, Inc.
 *
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

import jdk.test.lib.process.ProcessTools;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

/*
 * @test
 * @summary Ensures the java executable is able to load extra security
 * properties files from anonymous files and pipes.
 * @bug 8352728
 * @requires os.family == "linux"
 * @modules java.base/java.io:+open
 * @library /test/lib
 * @run main LinuxAnonymousFiles
 */

public class LinuxAnonymousFiles {
    private static final String TEST_PROP = "property.name=PROPERTY_VALUE";

    private static final class AnonymousFile implements AutoCloseable {
        public final Path fdPath;
        private final FileInputStream fis;

        private AnonymousFile(CharSequence content) throws Exception {
            Path tmp = Files.createTempFile("anonymous-file-", "");
            Files.writeString(tmp, content + System.lineSeparator());
            fis = new FileInputStream(tmp.toFile());
            Files.delete(tmp);
            // Now the file is regular but anonymous, and will be unlinked
            // when we close the last file descriptor referring to it. The
            // fis instance ensures we keep it alive until close() is invoked.
            Field field = FileDescriptor.class.getDeclaredField("fd");
            field.setAccessible(true);
            int fd = field.getInt(fis.getFD());
            fdPath = Path.of("/proc/self").toRealPath().resolve("fd/" + fd);
        }

        @Override
        public void close() throws IOException {
            fis.close();
        }
    }

    public static void main(String[] args) throws Exception {
        Path java = Path.of(System.getProperty("test.jdk"), "bin", "java");
        try (AnonymousFile af = new AnonymousFile("include /dev/stdin")) {
            ProcessTools.executeProcess(new ProcessBuilder(java.toString(),
                            "-Djava.security.debug=properties",
                            "-Djava.security.properties=" + af.fdPath,
                            "-XshowSettings:security:properties", "-version"),
                    TEST_PROP).shouldHaveExitValue(0).shouldContain(TEST_PROP);
        }
        System.out.println("TEST PASS - OK");
    }
}
