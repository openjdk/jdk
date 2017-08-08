/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.util.ArrayList;
import jdk.test.lib.compiler.CompilerUtils;
import jdk.testlibrary.ProcessTools;
import jdk.testlibrary.OutputAnalyzer;
import org.testng.annotations.BeforeTest;

/**
 * @test
 * @bug 8151654
 * @library /lib/testlibrary /test/lib
 * @library /java/security/modules
 * @build jdk.test.lib.compiler.CompilerUtils JarUtils
 * @summary Test custom JAAS callback handler with all possible modular option.
 * @run testng JaasModularDefaultHandlerTest
 */
public class JaasModularDefaultHandlerTest extends ModularTest {

    private static final Path S_SRC = SRC.resolve("TestCallbackHandler.java");
    private static final String MODULAR = "M";
    private static final String S_PKG = "handler";
    private static final String S_JAR_NAME = S_PKG + JAR_EXTN;
    private static final String MS_JAR_NAME = MODULAR + S_PKG + JAR_EXTN;
    private static final String HANDLER = S_PKG + ".TestCallbackHandler";

    private static final Path C_SRC
            = SRC.resolve("JaasClientWithDefaultHandler.java");
    private static final Path CL_SRC = SRC.resolve("TestLoginModule.java");
    private static final String C_PKG = "login";
    private static final String C_JAR_NAME = C_PKG + JAR_EXTN;
    private static final String MCN_JAR_NAME
            = MODULAR + C_PKG + "NoMUse" + JAR_EXTN;
    private static final String MC_JAR_NAME = MODULAR + C_PKG + JAR_EXTN;

    private static final Path BUILD_DIR = Paths.get(".").resolve("build");
    private static final Path COMPILE_DIR = BUILD_DIR.resolve("bin");
    private static final Path S_BUILD_DIR = COMPILE_DIR.resolve(S_PKG);
    private static final Path C_BLD_DIR = COMPILE_DIR.resolve(C_PKG);
    private static final Path M_BASE_PATH = BUILD_DIR.resolve("mbase");
    private static final Path ARTIFACTS_DIR = BUILD_DIR.resolve("artifacts");

    private static final Path S_ARTIFACTS_DIR = ARTIFACTS_DIR.resolve(S_PKG);
    private static final Path S_JAR = S_ARTIFACTS_DIR.resolve(S_JAR_NAME);
    private static final Path MS_JAR = S_ARTIFACTS_DIR.resolve(MS_JAR_NAME);

    private static final Path C_ARTIFACTS_DIR = ARTIFACTS_DIR.resolve(C_PKG);
    private static final Path C_JAR = C_ARTIFACTS_DIR.resolve(C_JAR_NAME);
    private static final Path MC_JAR = C_ARTIFACTS_DIR.resolve(MC_JAR_NAME);
    private static final Path MCN_JAR = C_ARTIFACTS_DIR.resolve(MCN_JAR_NAME);

    private static final String MAIN = C_PKG + ".JaasClientWithDefaultHandler";
    private static final List<String> M_REQUIRED = Arrays.asList("java.base",
            "jdk.security.auth");

    private static final String CLASS_NOT_FOUND_MSG
            = "java.lang.ClassNotFoundException: handler.TestCallbackHandler";
    private static final String NO_FAILURE = null;

    /**
     * Generates Test specific input parameters.
     */
    @Override
    public Object[][] getTestInput() {

        List<List<Object>> params = new ArrayList<>();
        String[] args = new String[]{HANDLER};
        // PARAMETER ORDERS -
        // Client Module Type, Service Module Type,
        // Service META Descriptor Required,
        // Expected Failure message, Client arguments
        params.add(Arrays.asList(MODULE_TYPE.EXPLICIT, MODULE_TYPE.EXPLICIT,
                false, NO_FAILURE, args));
        params.add(Arrays.asList(MODULE_TYPE.EXPLICIT, MODULE_TYPE.AUTO,
                false, NO_FAILURE, args));
        params.add(Arrays.asList(MODULE_TYPE.EXPLICIT, MODULE_TYPE.UNNAMED,
                false, NO_FAILURE, args));

        params.add(Arrays.asList(MODULE_TYPE.AUTO, MODULE_TYPE.EXPLICIT,
                false, NO_FAILURE, args));
        params.add(Arrays.asList(MODULE_TYPE.AUTO, MODULE_TYPE.AUTO,
                false, NO_FAILURE, args));
        params.add(Arrays.asList(MODULE_TYPE.AUTO, MODULE_TYPE.UNNAMED,
                false, NO_FAILURE, args));

        params.add(Arrays.asList(MODULE_TYPE.UNNAMED, MODULE_TYPE.EXPLICIT,
                false, NO_FAILURE, args));
        params.add(Arrays.asList(MODULE_TYPE.UNNAMED, MODULE_TYPE.AUTO,
                false, NO_FAILURE, args));
        params.add(Arrays.asList(MODULE_TYPE.UNNAMED, MODULE_TYPE.UNNAMED,
                false, NO_FAILURE, args));
        return params.stream().map(p -> p.toArray()).toArray(Object[][]::new);
    }

    /**
     * Pre-compile and generate the artifacts required to run this test before
     * running each test cases.
     */
    @BeforeTest
    public void buildArtifacts() {

        boolean done = true;
        try {
            done = CompilerUtils.compile(S_SRC, S_BUILD_DIR);
            // Generate modular/regular handler jars.
            generateJar(true, MODULE_TYPE.EXPLICIT, MS_JAR, S_BUILD_DIR, false);
            generateJar(true, MODULE_TYPE.UNNAMED, S_JAR, S_BUILD_DIR, false);
            // Compile client source codes.
            done &= CompilerUtils.compile(C_SRC, C_BLD_DIR);
            done &= CompilerUtils.compile(CL_SRC, C_BLD_DIR);
            // Generate modular client jar with explicit dependency
            generateJar(false, MODULE_TYPE.EXPLICIT, MC_JAR, C_BLD_DIR, true);
            // Generate modular client jar without any dependency
            generateJar(false, MODULE_TYPE.EXPLICIT, MCN_JAR, C_BLD_DIR, false);
            // Generate regular client jar
            generateJar(false, MODULE_TYPE.UNNAMED, C_JAR, C_BLD_DIR, false);
            System.out.format("%nArtifacts generated successfully? %s", done);
            if (!done) {
                throw new RuntimeException("Artifact generation failed");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Generate modular/regular jar based on module type for this test.
     */
    private void generateJar(boolean isService, MODULE_TYPE moduleType,
            Path jar, Path compilePath, boolean depends) throws IOException {

        ModuleDescriptor mDescriptor = null;
        if (isService) {
            mDescriptor = generateModuleDescriptor(isService, moduleType, S_PKG,
                    S_PKG, null, null, null, M_REQUIRED, depends);
        } else {
            mDescriptor = generateModuleDescriptor(isService, moduleType, C_PKG,
                    C_PKG, null, null, S_PKG, M_REQUIRED, depends);
        }
        generateJar(mDescriptor, jar, compilePath);
    }

    /**
     * Holds Logic for the test client. This method will get called with each
     * test parameter.
     */
    @Override
    public OutputAnalyzer executeTestClient(MODULE_TYPE cModuleType,
            Path cJarPath, MODULE_TYPE sModuletype, Path sJarPath,
            String... args) throws Exception {

        OutputAnalyzer output = null;
        try {
            // For automated/explicit module types, copy the corresponding
            // jars to module base folder, which will be considered as
            // module base path during execution.
            if (!(cModuleType == MODULE_TYPE.UNNAMED
                    && sModuletype == MODULE_TYPE.UNNAMED)) {
                copyJarsToModuleBase(cModuleType, cJarPath, M_BASE_PATH);
                copyJarsToModuleBase(sModuletype, sJarPath, M_BASE_PATH);
            }

            System.out.format("%nExecuting java client with required"
                    + " custom service in class/module path.");
            String mName = getModuleName(cModuleType, cJarPath, C_PKG);
            Path cmBasePath = (cModuleType != MODULE_TYPE.UNNAMED
                    || sModuletype != MODULE_TYPE.UNNAMED) ? M_BASE_PATH : null;
            String cPath = buildClassPath(cModuleType, cJarPath, sModuletype,
                    sJarPath);
            Map<String, String> vmArgs = getVMArgs(sModuletype, cModuleType,
                    getModuleName(sModuletype, sJarPath, S_PKG));
            output = ProcessTools.executeTestJava(
                    getJavaCommand(cmBasePath, cPath, mName, MAIN, vmArgs,
                            args)).outputTo(System.out).errorTo(System.out);
        } finally {
            // Clean module path to hold required jars for next run.
            cleanModuleBasePath(M_BASE_PATH);
        }
        return output;
    }

    /**
     * Decide the pre-generated client/service jar path for each test case
     * based on client/service module type.
     */
    @Override
    public Path findJarPath(boolean depends, MODULE_TYPE moduleType,
            boolean addMetaDesc, boolean dependsOnServiceModule) {
        if (depends) {
            if (moduleType == MODULE_TYPE.EXPLICIT) {
                return MS_JAR;
            } else {
                return S_JAR;
            }
        } else {
            // Choose corresponding client jar using dependent module
            if (moduleType == MODULE_TYPE.EXPLICIT) {
                if (dependsOnServiceModule) {
                    return MC_JAR;
                } else {
                    return MCN_JAR;
                }
            } else {
                return C_JAR;
            }
        }
    }

    /**
     * VM argument required for the test.
     */
    private Map<String, String> getVMArgs(MODULE_TYPE sModuletype,
            MODULE_TYPE cModuleType, String addModName) throws IOException {
        final Map<String, String> vmArgs = new LinkedHashMap<>();
        vmArgs.put("-Duser.language=", "en");
        vmArgs.put("-Duser.region=", "US");
        vmArgs.put("-Djava.security.auth.login.config=", SRC.resolve(
                "jaas.conf").toFile().getCanonicalPath());
        if (addModName != null
                && !(cModuleType == MODULE_TYPE.EXPLICIT
                && sModuletype == MODULE_TYPE.EXPLICIT)) {
            vmArgs.put("--add-modules=", addModName);
        }
        return vmArgs;
    }

}
