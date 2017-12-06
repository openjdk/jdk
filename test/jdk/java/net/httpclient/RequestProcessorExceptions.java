/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng RequestProcessorExceptions
 */

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static jdk.incubator.http.HttpRequest.BodyPublisher.fromByteArray;
import static jdk.incubator.http.HttpRequest.BodyPublisher.fromFile;

public class RequestProcessorExceptions {

    @DataProvider(name = "byteArrayOOBs")
    public Object[][] byteArrayOOBs() {
        return new Object[][] {
                { new byte[100],    1,  100 },
                { new byte[100],   -1,   10 },
                { new byte[100],   99,    2 },
                { new byte[1],   -100,    1 } };
    }

    @Test(dataProvider = "byteArrayOOBs", expectedExceptions = IndexOutOfBoundsException.class)
    public void fromByteArrayCheck(byte[] buf, int offset, int length) {
        fromByteArray(buf, offset, length);
    }

    @DataProvider(name = "nonExistentFiles")
    public Object[][] nonExistentFiles() {
        List<Path> paths = List.of(Paths.get("doesNotExist"),
                                   Paths.get("tsixEtoNseod"),
                                   Paths.get("doesNotExist2"));
        paths.forEach(p -> {
            if (Files.exists(p))
                throw new AssertionError("Unexpected " + p);
        });

        return paths.stream().map(p -> new Object[] { p }).toArray(Object[][]::new);
    }

    @Test(dataProvider = "nonExistentFiles", expectedExceptions = FileNotFoundException.class)
    public void fromFileCheck(Path path) throws Exception {
        fromFile(path);
    }

    // ---

    /* Main entry point for standalone testing of the main functional test. */
    public static void main(String... args) throws Exception {
        RequestProcessorExceptions t = new RequestProcessorExceptions();
        for (Object[] objs : t.byteArrayOOBs()) {
            try {
                t.fromByteArrayCheck((byte[]) objs[0], (int) objs[1], (int) objs[2]);
                throw new RuntimeException("fromByteArrayCheck failed");
            } catch (IndexOutOfBoundsException expected) { /* Ok */ }
        }
        for (Object[] objs : t.nonExistentFiles()) {
            try {
                t.fromFileCheck((Path) objs[0]);
                throw new RuntimeException("fromFileCheck failed");
            } catch (FileNotFoundException expected) { /* Ok */ }
        }
    }
}
