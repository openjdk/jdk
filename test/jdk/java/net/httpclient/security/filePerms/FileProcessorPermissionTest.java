/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Basic checks for File Processors
 * @run testng/othervm FileProcessorPermissionTest
 */

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import org.testng.annotations.Test;
import static java.nio.file.StandardOpenOption.*;
import static org.testng.Assert.*;

public class FileProcessorPermissionTest {

    static final String testSrc = System.getProperty("test.src", ".");
    static final Path fromFilePath = Paths.get(testSrc, "FileProcessorPermissionTest.java");
    static final Path asFilePath = Paths.get(testSrc, "asFile.txt");
    static final Path CWD = Paths.get(".");

    interface ExceptionAction<T> {
        T run() throws Exception;
    }

    @Test
    public void test() throws Exception {
        List<ExceptionAction<?>> list = List.of(
                () -> HttpRequest.BodyPublishers.ofFile(fromFilePath),

                () -> BodyHandlers.ofFile(asFilePath),
                () -> BodyHandlers.ofFile(asFilePath, CREATE),
                () -> BodyHandlers.ofFile(asFilePath, CREATE, WRITE),

                () -> BodyHandlers.ofFileDownload(CWD),
                () -> BodyHandlers.ofFileDownload(CWD, CREATE),
                () -> BodyHandlers.ofFileDownload(CWD, CREATE, WRITE)
        );

        for (ExceptionAction<?> pa : list) {
            pa.run();
        }

    }
}
