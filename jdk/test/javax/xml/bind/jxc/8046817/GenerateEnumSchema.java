/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8046817 8073357 8076139
 * @summary schemagen fails to generate xsd for enum types.
 * Check that order of Enum values is preserved.
 * @library /lib/testlibrary
 * @run testng/othervm GenerateEnumSchema
 */
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jdk.testlibrary.JDKToolLauncher;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class GenerateEnumSchema {

    @DataProvider
    //test case name, input file for schemagen, regexp for checking schema content
    public static Object[][] schemagenGenerationData() {
        return new Object[][] {
            {"Class", "TestClassType.java",
                ".+?name=\"testClassType\".+"},
            {"Enum", "TestEnumType.java",
                ".+?FIRST.+?ONE.+?TWO.+?THREE.+?FOUR.+?FIVE.+?SIX.+?LAST.+"},
        };
    }

    @BeforeTest
    public void setUp() throws IOException {
        //Create test directory inside scratch
        testWorkDir = Paths.get(System.getProperty("user.dir", "."))
                .resolve("GenerateEnumSchema");
        testSrcDir = Paths.get(System.getProperty("test.src", "."));
        //Create test work directory inside scratch directory
        Files.createDirectory(testWorkDir);
    }

    @Test(dataProvider="schemagenGenerationData")
    public void schemangenGenerationTestCase(String shortTestName,
            String inputFileName, String regexp) throws IOException {
        //Create test case directory
        Path testCaseDir = testWorkDir.resolve(shortTestName);
        Files.createDirectories(testCaseDir);
        //Copy java source from test.src to the test case dir
        Files.copy(testSrcDir.resolve(inputFileName), testCaseDir.resolve(inputFileName), REPLACE_EXISTING);
        //Run schemagen
        runSchemaGen(inputFileName, testCaseDir);
        //Check if schema file generated
        Assert.assertTrue(Files.exists(testCaseDir.resolve(SCHEMA_FILE)));
        //Read schema content from file
        String content = Files.lines(testCaseDir.resolve(SCHEMA_FILE)).collect(Collectors.joining(""));
        System.out.println("Generated schema: " + content);
        //Check if schema contains expected content
        Assert.assertTrue(Pattern.matches(regexp, content));
    }

    private static void runSchemaGen(String classFile, Path runDir) {
        try {
            JDKToolLauncher sgl = JDKToolLauncher.createUsingTestJDK("schemagen");
            sgl.addToolArg(classFile);
            System.out.println("Executing: " + Arrays.asList(sgl.getCommand()));
            ProcessBuilder pb = new ProcessBuilder(sgl.getCommand());
            pb.directory(runDir.toFile());
            pb.redirectErrorStream(true);
            pb.inheritIO();
            Process p = pb.start();
            int result = p.waitFor();
            p.destroy();
            if (result != 0) {
                throw new RuntimeException("schemagen failed");
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Can't run schemagen tool. Exception:");
            e.printStackTrace(System.err);
            throw new RuntimeException("Error launching schemagen tool");
        }
    }

    //schemagen tool output file name
    private static final String SCHEMA_FILE = "schema1.xsd";
    //Test working directory
    Path testWorkDir;
    //Directory with test src
    Path testSrcDir;
}
