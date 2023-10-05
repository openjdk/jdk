/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8307944
 * @library /test/lib
 * @build DumpMethodHandleInternals
 * @run main DumpMethodHandleInternals

 * @summary Test startup with -Djdk.invoke.MethodHandle.dumpMethodHandleInternals
 *          to work properly
 */

import java.nio.file.Files;
import java.nio.file.Path;

import jdk.test.lib.process.ProcessTools;

public class DumpMethodHandleInternals {

    private static final Path DUMP_DIR = Path.of("DUMP_METHOD_HANDLE_INTERNALS");

    public static void main(String[] args) throws Exception {
        if (ProcessTools.executeTestJava("-Djdk.invoke.MethodHandle.dumpMethodHandleInternals",
                                         "-version")
                .outputTo(System.out)
                .errorTo(System.out)
                .getExitValue() != 0)
            throw new RuntimeException("Test failed - see output");

        if (Files.notExists(DUMP_DIR))
            throw new RuntimeException(DUMP_DIR + " not created");
    }

}
