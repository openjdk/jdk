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
 * @summary Test Multi-Release jar support in schemagen tool
 * @library /test/lib
 * @library /lib/testlibrary
 * @modules jdk.compiler java.xml.ws
 * @build jdk.test.lib.JDKToolFinder jdk.test.lib.JDKToolLauncher
 *        jdk.test.lib.process.OutputAnalyzer
 *        jdk.test.lib.process.ProcessTools
 *        jdk.test.lib.Utils
 *        CompilerUtils MultiReleaseJarTest
 * @run testng MultiReleaseJarTest
 */

import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.testng.Assert.assertTrue;

public class MultiReleaseJarTest {
    private static final int SUCCESS = 0;

    @DataProvider(name = "jarFiles")
    public Object[][] jarFiles() {
        return new Object[][]{{"MV_BOTH.jar", 9},
                {"MV_ONLY_9.jar", 9},
                {"NON_MV.jar", 8}};
    }

    @BeforeClass
    public void setUpTest() throws Throwable {
        compile();
        Path classes = Paths.get("classes");
        jar("cf", "MV_BOTH.jar",
                "-C", classes.resolve("base").toString(), ".",
                "--release", "9", "-C", classes.resolve("v9").toString(), ".",
                "--release", "10", "-C", classes.resolve("v10").toString(), ".")
                .shouldHaveExitValue(SUCCESS);

        jar("cf", "MV_ONLY_9.jar",
                "-C", classes.resolve("base").toString(), ".",
                "--release", "9", "-C", classes.resolve("v9").toString(), ".")
                .shouldHaveExitValue(SUCCESS);
        jar("cf", "NON_MV.jar",
                "-C", classes.resolve("base").toString(), ".")
                .shouldHaveExitValue(SUCCESS);
    }

    @Test(dataProvider = "jarFiles")
    public void testSchemagen(String jar, int mainVer) throws Throwable {
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("schemagen");
        Utils.getForwardVmOptions().forEach(launcher::addToolArg);
        launcher.addToolArg("-cp").addToolArg(jar)
                .addToolArg("schemagen.Person");
        ProcessTools.executeCommand(launcher.getCommand())
                .shouldHaveExitValue(SUCCESS);

        String pattern = "version" + mainVer;
        assertTrue(grep(pattern, Paths.get("schema1.xsd")),
                "Pattern " + pattern + " not found in the generated file.");
    }

    private boolean grep(String pattern, Path file) throws IOException {
        return new String(Files.readAllBytes(file)).contains(pattern);
    }

    private OutputAnalyzer jar(String... args) throws Throwable {
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jar");
        Stream.of(args).forEach(launcher::addToolArg);
        return ProcessTools.executeCommand(launcher.getCommand());
    }

    private void compile() throws Throwable {
        String[] vers = {"base", "v9", "v10"};
        for (String ver : vers) {
            Path classes = Paths.get("classes", ver);
            Files.createDirectories(classes);
            Path source = Paths.get(Utils.TEST_SRC,"data", "mr", ver);
            assertTrue(CompilerUtils.compile(source, classes,
                    "--add-modules", "java.xml.ws"));
        }
    }
}