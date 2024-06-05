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

/* @test
 * @bug 8325203
 * @library ../helpers
 * @library /test/lib
 * @build jdk.test.lib.Utils
 *        jdk.test.lib.util.JarUtils
 * @requires os.family == "windows"
 * @run main/othervm JpackageTest
 * @summary Test that Jpackage windows executable application kills the launched 3rd party application
 *          when System.exit(0) is invoked along with terminating java program. 
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.logging.Logger;

import jdk.jpackage.test.TKit;
import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.JarUtils;

public class JpackageTest {
    private static final Logger logger = Logger
	    .getLogger(JpackageTest.class.getName());
    private static final String APPLICATION_NAME = "ThirdPartyAppLauncher";
    private static final String FS = File.separator;
    private static final String SRC_FILE = TKit.TEST_SRC_ROOT
	    .resolve("apps" + FS + APPLICATION_NAME + ".java").toString();
    private static final String CLASS_FILE = APPLICATION_NAME + ".class";
    private static final String JAR_FILE = APPLICATION_NAME + ".jar";
    private static final String INSTALLER = APPLICATION_NAME + "-1.0.exe";
    private static final String JAVA_HOME = System.getProperty("test.jdk");
    private static final String JPACKAGE = JAVA_HOME + FS + "bin" + FS
	    + "jpackage";
    private static final String APPLICATION_PATH = "C:" + FS + "Program Files"
	    + FS + APPLICATION_NAME + FS;
    private static final String EXECUTABLE_APPLICATION = APPLICATION_PATH
	    + APPLICATION_NAME + ".exe";
    private static final Path CWD = Paths.get(".");
    private static final String processIdFilePath = CWD.toString() + FS
	    + "process.tmp";
    private static final Path srcDir = Paths
	    .get(System.getProperty("test.src", "."));
    private static final Path classesDir = Paths
	    .get(System.getProperty("test.classes", "."));

    public static void main(String[] args) throws Throwable {
	JpackageTest test = new JpackageTest();
	try {
	    test.closeThirdPartyApplication();
	    test.setUp();
	    test.run();
	} finally {
	    test.cleanUp();
	}
    }

    /**
     * Sets up the prerequisites for executing the test - Compile the third
     * party application launcher source - Create a jar of third party
     * application launcher - Create a windows installer package of third party
     * application launcher
     */
    private void setUp() throws Throwable {

	CompilerUtils.compile(srcDir.resolve(SRC_FILE), classesDir);
	logger.info("Compiled Successfully");

	Manifest manifest = new Manifest();
	manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION,
		"1.0");
	manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS,
		APPLICATION_NAME);

	JarUtils.createJarFile(Paths.get(JAR_FILE), manifest, classesDir,
		classesDir.resolve(CLASS_FILE));
	logger.info("Jar Created Successfully");

	ProcessTools
		.executeCommand(JPACKAGE, "--input", CWD.toString(), "--name",
			APPLICATION_NAME, "--main-jar", JAR_FILE,
			"--main-class", APPLICATION_NAME, "--type", "exe")
		.shouldHaveExitValue(0);
	logger.info("Package Successfully");

	ProcessTools.executeCommand(INSTALLER).shouldHaveExitValue(0);
	logger.info("Installed Successfully");
    }

    /**
     * Execute the test - Start the third party application launcher - Check
     * whether the termination of third party application launcher also
     * terminating the launched third party application - If third party
     * application is not terminated the test is successful else failure
     */
    private void run() throws Throwable {
	ProcessTools.executeCommand(EXECUTABLE_APPLICATION)
		.shouldHaveExitValue(0);
	logger.info("Launched Successfully");

	long processId = readProcessId();
	logger.info(
		"Successfully read processid from file " + processIdFilePath);
	logger.info("Process Id is " + processId);

	Optional<ProcessHandle> processHandle = ProcessHandle.of(processId);
	boolean isAlive = processHandle.isPresent()
		&& processHandle.get().isAlive();
	if (isAlive) {
	    logger.info("Test Successful");
	} else {
	    logger.info("Test failed");
	    throw new RuntimeException(
		    "Test failed: Third party software is terminated");
	}
    }

    /**
     * Helper function to read processId from file
     */
    private long readProcessId() throws Throwable {

	Path path = Paths.get(processIdFilePath);
	long processId = Long.parseLong(Files.readAllLines(path).get(0));
	return processId;
    }

    /**
     * Kill if any regedit.exe is running before launching
     */
    private void closeThirdPartyApplication() throws Throwable {
	Runtime.getRuntime().exec("taskkill /F /IM regedit.exe");
    }

    /**
     * Uninstalls the application and terminates the third party application
     */
    private void cleanUp() throws Throwable {
	ProcessTools.executeCommand(INSTALLER, "/q", "REMOVE=ALL")
		.shouldHaveExitValue(0);
	closeThirdPartyApplication();
	logger.info("Cleanup Successful");
    }
}
