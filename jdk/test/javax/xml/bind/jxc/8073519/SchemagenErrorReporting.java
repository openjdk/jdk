/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8073519
 * @summary test that schemagen tool reports errors during
 * xsd generation process
 * @library /lib/testlibrary
 * @run testng/othervm SchemagenErrorReporting
 */
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.stream.Collectors;
import jdk.testlibrary.JDKToolLauncher;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SchemagenErrorReporting {

    @Test
    public void schemagenErrorReporting() throws Exception {
        //schemagen tool output file name
        final String SCHEMA_FILE = "schema1.xsd";
        //Schemagen input java file with not compilable source
        final String CLASS_FILE = "InputWithError.java";
        //Test working, src directories and test output file
        Path testWorkDir, testSrcDir, testOutput;

        //Prepare test environment
        //Create test directory inside scratch
        testWorkDir = Paths.get(System.getProperty("user.dir", "."))
                .resolve("SchemagenErrorReporting");
        //Get test source directory
        testSrcDir = Paths.get(System.getProperty("test.src", "."));
        //Set test output file path
        testOutput = testWorkDir.resolve("stdErrContent");
        //Create test directory inside scratch directory
        Files.createDirectory(testWorkDir);
        //Copy java source from test.src to the test directory
        Files.copy(testSrcDir.resolve(CLASS_FILE), testWorkDir.resolve(CLASS_FILE),
                StandardCopyOption.REPLACE_EXISTING);

        //Prepare process builder to run schemagen tool and save its output
        JDKToolLauncher sgl = JDKToolLauncher.createUsingTestJDK("schemagen");
        sgl.addToolArg(CLASS_FILE);
        System.out.println("Executing: " + Arrays.asList(sgl.getCommand()));
        ProcessBuilder pb = new ProcessBuilder(sgl.getCommand());
        //Set schemagen work directory with the input java file
        pb.directory(testWorkDir.toFile());
        //Redirect schemagen output to file
        pb.redirectError(testOutput.toFile());
        Process p = pb.start();
        int result = p.waitFor();
        p.destroy();

        //Read schemagen output from the file
        String stdErrContent = Files.lines(testOutput)
                .collect(Collectors.joining(System.lineSeparator(), System.lineSeparator(), ""));
        System.out.println("Schemagen return value:" + result);
        System.out.println("Error output:" + stdErrContent);
        //Check test results:
        //Schemagen finished with non-0 return value
        Assert.assertNotEquals(result, 0);
        //Schemagen output contains compile error message
        Assert.assertTrue(stdErrContent.contains("InputWithError.java:28: error"));
    }
}
