/*
 * Copyright (c) 2004, 2020 Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 5040740
 * @summary annotations cause memory leak
 * @author gafter
 * @library /test/lib
 * @build jdk.test.lib.process.*
 *        Main
 *        A
 *        B
 *        C
 * @run testng LoaderLeakTest
*/

import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import java.nio.file.*;
import java.util.List;

public class LoaderLeakTest {

    @BeforeClass
    public void initialize() throws Exception {
        final Path CLASSES_PATH = Paths.get(Utils.TEST_CLASSES).toAbsolutePath();
        final Path REPOSITORY_PATH = CLASSES_PATH.resolve("classes").toAbsolutePath();
        Files.createDirectories(REPOSITORY_PATH);
        List<String> classes = List.of("A.class", "B.class", "C.class");
        for (String fileName : classes) {
            Files.move(
                CLASSES_PATH.resolve(fileName),
                REPOSITORY_PATH.resolve(fileName),
                StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    @Test
    public void testWithoutReadingAnnotations() throws Throwable {
        runJavaProcessExpectSuccesExitCode("Main");
    }

    @Test
    public void testWithReadingAnnotations() throws Throwable {
        runJavaProcessExpectSuccesExitCode("Main",  "foo");
    }

    private void runJavaProcessExpectSuccesExitCode(String ... command) throws Throwable {
        ProcessTools
                .executeCommand(
                        ProcessTools
                                .createJavaProcessBuilder(command)
                                .directory(Paths.get(Utils.TEST_CLASSES).toFile()
                        )
                ).shouldHaveExitValue(0);
    }

}