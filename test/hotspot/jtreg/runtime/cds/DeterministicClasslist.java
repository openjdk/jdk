/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8378371
 * @summary The same JDK build should always generate the same classlist file (no randomness).
 * @requires vm.cds & vm.flagless
 * @library /test/lib ./appcds/
 * @run driver DeterministicClasslist
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.compiler.InMemoryJavaCompiler;
import jdk.test.lib.helpers.ClassFileInstaller;


public class DeterministicClasslist {

    public static void compareClasslists(String base, String test) throws Exception {
        File baseClasslistFile = new File(base);
        File testClasslistFile = new File(test);

        BufferedReader baseReader = new BufferedReader(new FileReader(baseClasslistFile));
        BufferedReader testReader = new BufferedReader(new FileReader(testClasslistFile));

        String baseLine, testLine;
        while ((baseLine = baseReader.readLine()) != null) {
            testLine = testReader.readLine();
            // Skip constant pool entries
            if (baseLine.contains("@cp")) {
                continue;
            }
            if (!baseLine.equals(testLine)) {
                System.out.println(baseLine + " vs " + testLine);
                throw new RuntimeException("Classlists differ");
            }
        }
    }

    static Path findFile(String path) {
        Path root = Paths.get(System.getProperty("test.root", "."));
        // Move back to java root directory
        root = root.getParent().getParent().getParent();

        Path file = root.resolve(path);
        if (Files.exists(file)) {
            return file;
        }

        return null;
    }

    public static void main (String[] args) throws Exception {
        String[] classlist = { "build/tools/classlist/HelloClasslist", "build/tools/classlist/HelloClasslist$1A", "build/tools/classlist/HelloClasslist$1B" };
        String appClass = classlist[0];
        String appJar;
        String classDir = System.getProperty("test.classes");
        String baseClasslist = "base.classlist";
        String testClasslist = "test.classlist";

        Path testPath = findFile("make/jdk/src/classes/build/tools/classlist/HelloClasslist.java");
        if (testPath == null) {
            throw new RuntimeException("Could not find HelloClasslist");
        }

        String source = Files.readString(testPath);
        Map<String, byte[]> compiledClasses = InMemoryJavaCompiler.compile(Map.of(appClass, source));

        for (Entry<String, byte[]> e : compiledClasses.entrySet()) {
            ClassFileInstaller.writeClassToDisk(e.getKey(), e.getValue(), classDir);
        }

        JarBuilder.build("classlist", classlist);
        appJar = TestCommon.getTestJar("classlist.jar");

        CDSTestUtils.dumpClassList(baseClasslist, "-cp", appJar, "-Xint", appClass);
        CDSTestUtils.dumpClassList(testClasslist, "-cp", appJar, "-Xint", appClass);

        compareClasslists(baseClasslist, testClasslist);
    }
}
