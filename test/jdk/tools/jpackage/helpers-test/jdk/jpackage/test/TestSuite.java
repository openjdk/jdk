/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/*
 * @test
 * @summary Unit tests for jpackage test library
 * @library /test/jdk/tools/jpackage/helpers
 * @library /test/jdk/tools/jpackage/helpers-test
 * @build jdk.jpackage.test.*
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.TestSuite
 */

public final class TestSuite {
    public static void main(String args[]) throws Throwable {
        final var pkgName = TestSuite.class.getPackageName();
        final var javaSuffix = ".java";
        final var testSrcNameSuffix = "Test" + javaSuffix;

        final var unitTestDir = TKit.TEST_SRC_ROOT.resolve(Path.of("helpers-test", pkgName.split("\\.")));

        final List<String> runTestArgs = new ArrayList<>();
        runTestArgs.addAll(List.of(args));

        try (var javaSources = Files.list(unitTestDir)) {
            runTestArgs.addAll(javaSources.filter(path -> {
                return path.getFileName().toString().endsWith(testSrcNameSuffix);
            }).map(path -> {
                var filename = path.getFileName().toString();
                return String.join(".", pkgName, filename.substring(0, filename.length() - javaSuffix.length()));
            }).map(testClassName -> {
                return "--jpt-run=" + testClassName;
            }).toList());
        }

        Main.main(runTestArgs.toArray(String[]::new));
    }
}
