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
 * @bug 8168423
 * @summary Different types of ClassLoader running with(out) SecurityManager and
 *          (in)valid security policy file.
 * @library /lib/testlibrary
 * @modules java.base/jdk.internal.module
 * @build JarUtils CompilerUtils
 * @run main/timeout=240 ClassLoaderTest
 */
import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.HashMap;
import java.lang.module.ModuleDescriptor;
import jdk.internal.module.ModuleInfoWriter;
import jdk.testlibrary.ProcessTools;

public class ClassLoaderTest {

    private static final String SRC = System.getProperty("test.src");
    private static final Path CL_SRC = Paths.get(SRC, "TestClassLoader.java");
    private static final Path C_SRC = Paths.get(SRC, "TestClient.java");
    private static final Path CL_BIN = Paths.get("classes", "clbin");
    private static final Path C_BIN = Paths.get("classes", "cbin");
    private static final Path ARTIFACT_DIR = Paths.get("jars");
    private static final Path VALID_POLICY = Paths.get(SRC, "valid.policy");
    private static final Path INVALID_POLICY
            = Paths.get(SRC, "malformed.policy");
    private static final Path NO_POLICY = null;
    private static final String LOCALE = "-Duser.language=en -Duser.region=US";
    /*
     * Here is the naming convention followed for each jar.
     * cl.jar   - Regular custom class loader jar.
     * mcl.jar  - Modular custom class loader jar.
     * c.jar    - Regular client jar.
     * mc.jar   - Modular client jar.
     * amc.jar  - Modular client referring automated custom class loader jar.
     */
    private static final Path CL_JAR = ARTIFACT_DIR.resolve("cl.jar");
    private static final Path MCL_JAR = ARTIFACT_DIR.resolve("mcl.jar");
    private static final Path C_JAR = ARTIFACT_DIR.resolve("c.jar");
    private static final Path MC_JAR = ARTIFACT_DIR.resolve("mc.jar");
    private static final Path AMC_JAR = ARTIFACT_DIR.resolve("amc.jar");
    private static final Map<String, String> MSG_MAP = new HashMap<>();

    static {
        // This mapping help process finding expected message based
        // on the key passed as argument while executing java command.
        MSG_MAP.put("MissingModule", "Module cl not found, required by mc");
        MSG_MAP.put("ErrorPolicy", "java.security.policy: error parsing file");
        MSG_MAP.put(
                "SystemCL", "jdk.internal.loader.ClassLoaders$AppClassLoader");
        MSG_MAP.put("CustomCL", "cl.TestClassLoader");
    }

    public static void main(String[] args) throws Exception {

        // Generates regular and modular jars before start processing it.
        setUp();
        processForEachPolicyFile();
    }

    /**
     * Test cases are based on the following logic,
     *  for (policyFile : {"NO_POLICY", "VALID", "MALFORMED"}) {
     *      for (classLoader : {"SystemClassLoader", "CustomClassLoader"}){
     *          for (clientModule : {"NAMED", "AUTOMATIC", "UNNAMED"}) {
     *              for (classLoaderModule : {"NAMED", "AUTOMATIC", "UNNAMED"}) {
     *                  Create and run java command for each possible Test case
     *              }
     *          }
     *      }
     *  }
     */
    private static void processForEachPolicyFile() throws Exception {

        final String regCLloc = CL_JAR.toFile().getAbsolutePath();
        final String modCLloc = MCL_JAR.toFile().getAbsolutePath();
        final String regCloc = C_JAR.toFile().getAbsolutePath();
        final String modCloc = MC_JAR.toFile().getAbsolutePath();
        final String autoModCloc = AMC_JAR.toFile().getAbsolutePath();
        final String separator = File.pathSeparator;

        for (Path policy
                : new Path[]{NO_POLICY, VALID_POLICY, INVALID_POLICY}) {
            final String policyFile = (policy != null)
                    ? policy.toFile().getAbsolutePath() : null;
            final boolean malformedPolicy
                    = (policy == null) ? false : policy.equals(INVALID_POLICY);

            for (boolean useSCL : new boolean[]{true, false}) {
                final String clVmArg = (useSCL) ? ""
                        : "-Djava.system.class.loader=cl.TestClassLoader";
                final String autoAddModArg
                        = (useSCL) ? "" : "--add-modules=cl";
                final String addmodArg = (useSCL) ? "" : "--add-modules=mcl";
                final String sMArg = (policy != null) ? String.format(
                        "-Djava.security.manager -Djava.security.policy=%s",
                        policyFile) : "";
                final String smMsg = (policy != null) ? "With SecurityManager"
                        : "Without SecurityManager";
                final String expectedResult = ((!malformedPolicy)
                        ? ((useSCL) ? "PASS SystemCL" : "PASS CustomCL")
                        : "FAIL ErrorPolicy");

                // NAMED-NAMED, NAMED-AUTOMATIC, NAMED-UNNAMED
                System.out.printf("Case:- Modular Client and %s %s%n",
                        ((useSCL) ? "SystemClassLoader"
                                : "Modular CustomClassLoader"), smMsg);
                execute(new String[]{String.format(
                    "--module-path %s%s%s %s %s %s -m mc/c.TestClient",
                    modCloc, separator, modCLloc, LOCALE, clVmArg, sMArg),
                    expectedResult});
                System.out.printf("Case:- Modular Client and %s %s%n", ((useSCL)
                        ? "SystemClassLoader"
                        : "Automatic modular CustomClassLoader"), smMsg);
                execute(new String[]{String.format(
                    "--module-path %s%s%s %s %s %s -m mc/c.TestClient",
                    autoModCloc, separator, regCLloc, LOCALE, clVmArg, sMArg),
                    expectedResult});
                System.out.printf("Case:- Modular Client and %s %s%n", ((useSCL)
                        ? "SystemClassLoader"
                        : "Unknown modular CustomClassLoader"), smMsg);
                execute(new String[]{String.format(
                    "--module-path %s -cp %s %s %s %s -m mc/c.TestClient",
                    autoModCloc, regCLloc, LOCALE, clVmArg, sMArg),
                    "FAIL MissingModule"});

                // AUTOMATIC-NAMED, AUTOMATIC-AUTOMATIC, AUTOMATIC-UNNAMED
                System.out.printf("Case:- Automated modular Client and %s %s%n",
                        ((useSCL) ? "SystemClassLoader"
                                : "Modular CustomClassLoader"), smMsg);
                execute(new String[]{String.format(
                    "--module-path %s%s%s %s %s %s %s -m c/c.TestClient",
                    regCloc, separator, modCLloc, addmodArg, LOCALE, clVmArg,
                    sMArg), expectedResult});
                System.out.printf("Case:- Automated modular Client and %s %s%n",
                        ((useSCL) ? "SystemClassLoader"
                                : "Automatic modular CustomClassLoader"),
                        smMsg);
                execute(new String[]{String.format(
                    "--module-path %s%s%s %s %s %s %s -m c/c.TestClient",
                    regCloc, separator, regCLloc, autoAddModArg, LOCALE,
                    clVmArg, sMArg), expectedResult});
                System.out.printf("Case:- Automated modular Client and %s %s%n",
                        ((useSCL) ? "SystemClassLoader"
                                : "Unknown modular CustomClassLoader"), smMsg);
                execute(new String[]{String.format(
                    "--module-path %s -cp %s %s %s %s -m c/c.TestClient",
                    regCloc, regCLloc, LOCALE, clVmArg, sMArg),
                    expectedResult});

                // UNNAMED-NAMED, UNNAMED-AUTOMATIC, UNNAMED-UNNAMED
                System.out.printf("Case:- Unknown modular Client and %s %s%n",
                        ((useSCL) ? "SystemClassLoader"
                                : "Modular CustomClassLoader"), smMsg);
                execute(new String[]{String.format(
                    "-cp %s --module-path %s %s %s %s %s c.TestClient",
                    regCloc, modCLloc, addmodArg, LOCALE, clVmArg, sMArg),
                    expectedResult});
                System.out.printf("Case:- Unknown modular Client and %s %s%n",
                        ((useSCL) ? "SystemClassLoader"
                                : "Automatic modular CustomClassLoader"),
                        smMsg);
                execute(new String[]{String.format(
                    "-cp %s --module-path %s %s %s %s %s c.TestClient",
                    regCloc, regCLloc, autoAddModArg, LOCALE, clVmArg, sMArg),
                    expectedResult});
                System.out.printf("Case:- Unknown modular Client and %s %s%n",
                        ((useSCL) ? "SystemClassLoader"
                                : "Unknown modular CustomClassLoader"), smMsg);
                execute(new String[]{String.format(
                    "-cp %s%s%s %s %s %s c.TestClient", regCloc, separator,
                    regCLloc, LOCALE, clVmArg, sMArg), expectedResult});

                // Regular jars in module-path and Modular jars in class-path.
                System.out.printf("Case:- Regular Client and %s "
                        + "inside --module-path %s.%n", ((useSCL)
                                ? "SystemClassLoader"
                                : "Unknown modular CustomClassLoader"), smMsg);
                execute(new String[]{String.format(
                    "--module-path %s%s%s %s %s %s %s -m c/c.TestClient",
                    regCloc, separator, regCLloc, autoAddModArg, LOCALE,
                    clVmArg, sMArg), expectedResult});
                System.out.printf("Case:- Modular Client and %s in -cp %s%n",
                        ((useSCL) ? "SystemClassLoader"
                                : "Modular CustomClassLoader"), smMsg);
                execute(new String[]{String.format(
                    "-cp %s%s%s %s %s %s c.TestClient", modCloc, separator,
                    modCLloc, LOCALE, clVmArg, sMArg), expectedResult});
            }
        }
    }

    /**
     * Execute with command arguments and process the result.
     */
    private static void execute(String[] args) throws Exception {

        String status = null;
        String msgKey = null;
        if ((args != null && args.length > 1)) {
            String[] secArgs = args[1].split("\\s+");
            status = (secArgs.length > 0) ? secArgs[0] : null;
            msgKey = (secArgs.length > 1) ? secArgs[1] : null;
        }
        String out = ProcessTools.executeTestJvm(args[0].split("\\s+"))
                .getOutput();
        // Handle response.
        if ((status != null && "PASS".equals(status) && msgKey != null
                && out.contains(MSG_MAP.get(msgKey)))) {
            System.out.printf("PASS: Expected Result: %s.%n",
                    MSG_MAP.get(msgKey));
        } else if ((status != null && "FAIL".equals(status) && msgKey != null
                && out.contains(MSG_MAP.get(msgKey)))) {
            System.out.printf("PASS: Expected Failure: %s.%n",
                    MSG_MAP.get(msgKey));
        } else if (out.contains("Exception") || out.contains("Error")) {
            System.out.printf("OUTPUT: %s", out);
            throw new RuntimeException("FAIL: Unknown Exception.");
        } else {
            System.out.printf("OUTPUT: %s", out);
            throw new RuntimeException("FAIL: Unknown Test case found");
        }
    }

    /**
     * Creates regular/modular jar files for TestClient and TestClassLoader.
     */
    private static void setUp() throws Exception {

        boolean compiled = CompilerUtils.compile(CL_SRC, CL_BIN);
        compiled &= CompilerUtils.compile(C_SRC, C_BIN);
        if (!compiled) {
            throw new RuntimeException("Test Setup failed.");
        }
        // Generate regular jar files for TestClient and TestClassLoader
        JarUtils.createJarFile(CL_JAR, CL_BIN);
        JarUtils.createJarFile(C_JAR, C_BIN);
        // Generate modular jar files for TestClient and TestClassLoader with
        // their corresponding ModuleDescriptor.
        Files.copy(CL_JAR, MCL_JAR, StandardCopyOption.REPLACE_EXISTING);
        updateModuleDescr(MCL_JAR, ModuleDescriptor.newModule("mcl")
                .exports("cl").requires("java.base").build());
        Files.copy(C_JAR, MC_JAR, StandardCopyOption.REPLACE_EXISTING);
        updateModuleDescr(MC_JAR, ModuleDescriptor.newModule("mc")
                .exports("c").requires("java.base").requires("mcl").build());
        Files.copy(C_JAR, AMC_JAR, StandardCopyOption.REPLACE_EXISTING);
        updateModuleDescr(AMC_JAR, ModuleDescriptor.newModule("mc")
                .exports("c").requires("java.base").requires("cl").build());
    }

    /**
     * Update regular jars and include module-info.class inside it to make
     * modular jars.
     */
    private static void updateModuleDescr(Path jar, ModuleDescriptor mDescr)
            throws Exception {
        if (mDescr != null) {
            Path dir = Files.createTempDirectory("tmp");
            Path mi = dir.resolve("module-info.class");
            try (OutputStream out = Files.newOutputStream(mi)) {
                ModuleInfoWriter.write(mDescr, out);
            }
            System.out.format("Adding 'module-info.class' to jar '%s'%n", jar);
            JarUtils.updateJarFile(jar, dir);
        }
    }
}
