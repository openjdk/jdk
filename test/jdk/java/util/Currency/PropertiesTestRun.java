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

/*
 * @test
 * @bug 6332666 6863624 7180362 8003846 8074350 8074351 8130246 8149735 7102969
 *      8157138 8190904 8210410
 * @summary Tests the capability of replacing the currency data with a user
 *          specified currency properties file in lib directory (old way) or
 *          via the system property in the cmdline (new way).
 * @library /test/lib
 * @build PropertiesTest
 * @run junit PropertiesTestRun
 */

import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import java.util.stream.Stream;

public class PropertiesTestRun {

    // Collection of String paths used for the cmdline processes
    private static final String TEST_JAVA = Utils.TEST_JDK;

    private static final String TEST_PROPS =
            Utils.TEST_SRC+Utils.FILE_SEPARATOR+"currency.properties";

    private static final String WRITABLE_JDK =
            "."+Utils.FILE_SEPARATOR+"WRITABLE_JDK";

    private static final String WRITABLE_JDK_LIB =
            WRITABLE_JDK+Utils.FILE_SEPARATOR+"lib";

    private static final String WRITABLE_JDK_BIN =
            WRITABLE_JDK+Utils.FILE_SEPARATOR+"bin";

    // Create a writable JDK and set up dumps 1-3
    @BeforeAll
    static void setUp() throws Throwable {
        // Create separate JDK to supersede currencies via lib directory
        createWritableJDK();
        // Create dump without user defined prop file
        invokeTestJDKMethod("PropertiesTest", "-d", "dump1");
        // Create dump with user defined prop file (via system property)
        invokeTestJDKMethod("-Djava.util.currency.data="+TEST_PROPS, "PropertiesTest", "-d", "dump2");
        // Create dump with user defined prop file (via lib)
        invokeWritableJDKMethod("PropertiesTest", "-d", "dump3");
    }

    // Need to create a separate JDK to insert the user defined properties file
    // into the lib folder. Create separate JDK to not disturb current TEST JDK.
    private static void createWritableJDK() throws Throwable {
        // Copy Test JDK into a separate JDK folder
        invokeProcess(new String[]{"cp", "-H", "-R", TEST_JAVA, WRITABLE_JDK});
        // Make the separate JDK writable
        invokeProcess(new String[]{"chmod", "-R", "u+w", WRITABLE_JDK_LIB});
        // Copy the properties file into the writable JDK lib folder
        invokeProcess(new String[]{"cp", TEST_PROPS, WRITABLE_JDK_LIB});
    }

    // Compares the dumped output is expected between the default currencies
    // and the user-defined custom currencies
    @Test
    void compareDumps() throws Throwable {
        // Compare dump (from sys prop)
        invokeTestJDKMethod("PropertiesTest", "-c", "dump1", "dump2", TEST_PROPS);
        // Compare dump (from lib)
        invokeTestJDKMethod("PropertiesTest", "-c", "dump1", "dump3", TEST_PROPS);
    }

    // Launch a test from PropertiesTest. See PropertiesTest.java for more
    // detail regarding a specific test that was launched.
    @ParameterizedTest
    @MethodSource("PropertiesTestMethods")
    void launchPropertiesTests(String methodName) throws Throwable {
        // Test via both the lib and system property
        invokeWritableJDKMethod("PropertiesTest", methodName);
        invokeTestJDKMethod("-Djava.util.currency.data="+TEST_PROPS,
                "PropertiesTest", methodName);
    }

    private static Stream<String> PropertiesTestMethods() {
        return Stream.of("bug7102969", "bug8157138", "bug8190904");
    }

    // Launch a PropertiesTest method using the TEST JDK
    private static void invokeTestJDKMethod(String... params) throws Throwable {
        int exitStatus = ProcessTools.executeTestJvm(params).getExitValue();
        if (exitStatus != 0) {
            fail("Process started with: " + Arrays.toString(params) + " did not pass");
        }
    }

    // Launch a PropertiesTest method using the WRITABLE JDK
    private static void invokeWritableJDKMethod(String... params) throws Throwable {
        // Need to include WritableJDK javapath, TEST JDK classpath
        String[] allParams = new String[3+params.length+Utils.getTestJavaOpts().length];
        allParams[0] = WRITABLE_JDK_BIN + Utils.FILE_SEPARATOR + "java";
        allParams[1] = "-cp";
        allParams[2] = System.getProperty("java.class.path");
        // Add test.vm.opts and test.java.opts
        System.arraycopy(Utils.getTestJavaOpts(), 0, allParams, 3,
                Utils.getTestJavaOpts().length);
        // Add the rest of the actual arguments
        System.arraycopy(params, 0, allParams, Utils.getTestJavaOpts().length+3,
                params.length);
        // Launch the actual test method with all parameters set
        invokeProcess(allParams);
    }

    // Invoke a process and fail if the command is not successful
    private static void invokeProcess(String[] params) throws Throwable {
        System.out.println("Command line: " + Arrays.toString(params));
        int exitStatus = ProcessTools.executeProcess(params).getExitValue();
        if (exitStatus != 0) {
            fail("Process started with: " + Arrays.toString(params) + " did not pass");
        }
    }

    @AfterAll
    static void tearDown() throws Throwable {
        tearDownWritableJDK();
    }

    // Remove the copied writable JDK image from scratch folder
    private static void tearDownWritableJDK() throws Throwable {
        invokeProcess(new String[]{"rm", "-rf", WRITABLE_JDK});
    }
}
