/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary a jtreg wrapper for gtest tests
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @run main/native GTestWrapper
 */

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import java.nio.file.Paths;
import java.nio.file.Path;

import jdk.test.lib.Platform;
import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class GTestWrapper {
    public static void main(String[] args) throws Throwable {
        // gtestLauncher is located in <test_image>/hotspot/gtest/<vm_variant>/
        // nativePath points either to <test_image>/hotspot/jtreg/native or to <test_image>/hotspot/gtest
        Path nativePath = Paths.get(System.getProperty("test.nativepath"));
        String jvmVariantDir = getJVMVariantSubDir();
        // let's assume it's <test_image>/hotspot/gtest
        Path path = nativePath.resolve(jvmVariantDir);
        if (!path.toFile().exists()) {
            // maybe it is <test_image>/hotspot/jtreg/native
            path = nativePath.getParent()
                             .getParent()
                             .resolve("gtest")
                             .resolve(jvmVariantDir);
        }
        if (!path.toFile().exists()) {
            throw new Error("TESTBUG: the library has not been found in " + nativePath);
        }
        path = path.resolve("gtestLauncher" + (Platform.isWindows() ? ".exe" : ""));
        ProcessTools.executeCommand(new String[] {
                path.toString(),
                "-jdk",
                System.getProperty("test.jdk")
        }).shouldHaveExitValue(0);
    }

    private static String getJVMVariantSubDir() {
        if (Platform.isServer()) {
            return "server";
        } else if (Platform.isClient()) {
            return "client";
        } else if (Platform.isMinimal()) {
            return "minimal";
        } else {
            throw new Error("TESTBUG: unsuppported vm variant");
        }
    }
}
