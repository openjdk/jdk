/*
 * Copyright (c) 2004, 2021 Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/*
 * @test
 * @bug 4952558
 * @library /test/lib
 * @build NonJavaNames
 * @run testng/othervm NonJavaNameTest
 * @summary Verify names that aren't legal Java names are accepted by forName.
 * @author Joseph D. Darcy
 */

public class NonJavaNameTest {
    private static final String SRC_DIR = System.getProperty("test.src");
    private static final String NONJAVA_NAMES_SRC = SRC_DIR + "/classes/";
    private static final String NONJAVA_NAMES_CLASSES = System.getProperty("test.classes", ".");
    Path dhyphenPath, dcommaPath, dperiodPath, dleftsquarePath, drightsquarePath, dplusPath, dsemicolonPath, dzeroPath, dthreePath, dzadePath;

    @BeforeClass
    public void createInvalidNameClasses() throws IOException {
        Path hyphenPath = Paths.get(NONJAVA_NAMES_SRC + "hyphen.class");
        Path commaPath = Paths.get(NONJAVA_NAMES_SRC + "comma.class");
        Path periodPath = Paths.get(NONJAVA_NAMES_SRC + "period.class");
        Path leftsquarePath = Paths.get(NONJAVA_NAMES_SRC + "left-square.class");
        Path rightsquarePath = Paths.get(NONJAVA_NAMES_SRC + "right-square.class");
        Path plusPath = Paths.get(NONJAVA_NAMES_SRC + "plus.class");
        Path semicolonPath = Paths.get(NONJAVA_NAMES_SRC + "semicolon.class");
        Path zeroPath = Paths.get(NONJAVA_NAMES_SRC + "0.class");
        Path threePath = Paths.get(NONJAVA_NAMES_SRC + "3.class");
        Path zadePath = Paths.get(NONJAVA_NAMES_SRC + "Z.class");

        dhyphenPath = Paths.get(NONJAVA_NAMES_CLASSES + "/-.class");
        dcommaPath = Paths.get(NONJAVA_NAMES_CLASSES + "/,.class");
        dperiodPath = Paths.get(NONJAVA_NAMES_CLASSES + "/..class");
        dleftsquarePath = Paths.get(NONJAVA_NAMES_CLASSES + "/[.class");
        drightsquarePath = Paths.get(NONJAVA_NAMES_CLASSES + "/].class");
        dplusPath = Paths.get(NONJAVA_NAMES_CLASSES + "/+.class");
        dsemicolonPath = Paths.get(NONJAVA_NAMES_CLASSES + "/;.class");
        dzeroPath = Paths.get(NONJAVA_NAMES_CLASSES + "/0.class");
        dthreePath = Paths.get(NONJAVA_NAMES_CLASSES + "/3.class");
        dzadePath = Paths.get(NONJAVA_NAMES_CLASSES + "/Z.class");

        Files.copy(hyphenPath, dhyphenPath, REPLACE_EXISTING);
        Files.copy(commaPath, dcommaPath, REPLACE_EXISTING);
        Files.copy(periodPath, dperiodPath, REPLACE_EXISTING);
        Files.copy(leftsquarePath, dleftsquarePath, REPLACE_EXISTING);
        Files.copy(rightsquarePath, drightsquarePath, REPLACE_EXISTING);
        Files.copy(plusPath, dplusPath, REPLACE_EXISTING);
        Files.copy(semicolonPath, dsemicolonPath, REPLACE_EXISTING);
        Files.copy(zeroPath, dzeroPath, REPLACE_EXISTING);
        Files.copy(threePath, dthreePath, REPLACE_EXISTING);
        Files.copy(zadePath, dzadePath, REPLACE_EXISTING);
    }

    @Test
    public void NonJavaNamestest() throws Throwable {
        ProcessBuilder processBuilder = ProcessTools.createJavaProcessBuilder( "NonJavaNames");
        OutputAnalyzer outputAnalyzer = ProcessTools.executeCommand(processBuilder);
        outputAnalyzer.shouldHaveExitValue(0);
    }

    @AfterClass
    public void deleteInvalidNameClasses() throws IOException {
        Files.deleteIfExists(dhyphenPath);
        Files.deleteIfExists(dcommaPath);
        Files.deleteIfExists(dperiodPath);
        Files.deleteIfExists(dleftsquarePath);
        Files.deleteIfExists(drightsquarePath);
        Files.deleteIfExists(dplusPath);
        Files.deleteIfExists(dzeroPath);
        Files.deleteIfExists(dthreePath);
        Files.deleteIfExists(dzadePath);
    }
}
