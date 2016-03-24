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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import jdk.testlibrary.ProcessTools;
import jdk.testlibrary.OutputAnalyzer;
import org.testng.annotations.BeforeTest;

/**
 * @test
 * @bug 8130360
 * @library /lib/testlibrary
 * @library /java/security/modules
 * @modules java.base/jdk.internal.module
 * @build CompilerUtils JarUtils
 * @summary Test custom security provider module with all possible modular
 *          condition. The test includes different combination of security
 *          client/provider modules interaction with or without service
 *          description.
 * @run testng SecurityProviderModularTest
 */
public class SecurityProviderModularTest extends ModularTest {

    private static final Path S_SRC = SRC.resolve("TestSecurityProvider.java");
    private static final String S_PKG = "provider";
    private static final String S_JAR_NAME = S_PKG + JAR_EXTN;
    private static final String S_WITH_DESCR_JAR_NAME = S_PKG + DESCRIPTOR
            + JAR_EXTN;
    private static final String MS_JAR_NAME = MODULAR + S_PKG + JAR_EXTN;
    private static final String MS_WITH_DESCR_JAR_NAME = MODULAR + S_PKG
            + DESCRIPTOR + JAR_EXTN;

    private static final Path C_SRC = SRC.resolve(
            "TestSecurityProviderClient.java");
    private static final String C_PKG = "client";
    private static final String C_JAR_NAME = C_PKG + JAR_EXTN;
    private static final String MC_DEPENDS_ON_AUTO_SERVICE_JAR_NAME = MODULAR
            + C_PKG + AUTO + JAR_EXTN;
    private static final String MC_JAR_NAME = MODULAR + C_PKG + JAR_EXTN;

    private static final Path BUILD_DIR = Paths.get(".").resolve("build");
    private static final Path COMPILE_DIR = BUILD_DIR.resolve("bin");
    private static final Path S_BUILD_DIR = COMPILE_DIR.resolve(S_PKG);
    private static final Path S_WITH_META_DESCR_BUILD_DIR = COMPILE_DIR.resolve(
            S_PKG + DESCRIPTOR);
    private static final Path C_BUILD_DIR = COMPILE_DIR.resolve(C_PKG);
    private static final Path M_BASE_PATH = BUILD_DIR.resolve("mbase");
    private static final Path ARTIFACTS_DIR = BUILD_DIR.resolve("artifacts");

    private static final Path S_ARTIFACTS_DIR = ARTIFACTS_DIR.resolve(S_PKG);
    private static final Path S_JAR = S_ARTIFACTS_DIR.resolve(S_JAR_NAME);
    private static final Path S_WITH_DESCRIPTOR_JAR = S_ARTIFACTS_DIR.resolve(
            S_WITH_DESCR_JAR_NAME);
    private static final Path MS_JAR = S_ARTIFACTS_DIR.resolve(
            MS_JAR_NAME);
    private static final Path MS_WITH_DESCR_JAR = S_ARTIFACTS_DIR.resolve(
            MS_WITH_DESCR_JAR_NAME);

    private static final Path C_ARTIFACTS_DIR = ARTIFACTS_DIR.resolve(C_PKG);
    private static final Path C_JAR = C_ARTIFACTS_DIR.resolve(C_JAR_NAME);
    private static final Path MC_JAR = C_ARTIFACTS_DIR.resolve(MC_JAR_NAME);
    private static final Path MC_DEPENDS_ON_AUTO_SERVICE_JAR = C_ARTIFACTS_DIR
            .resolve(MC_DEPENDS_ON_AUTO_SERVICE_JAR_NAME);

    private static final String MAIN = C_PKG + ".TestSecurityProviderClient";
    private static final String S_INTERFACE = "java.security.Provider";
    private static final String S_IMPL = S_PKG + ".TestSecurityProvider";
    private static final List<String> M_REQUIRED = Arrays.asList("java.base");
    private static final Path META_DESCR_PATH = Paths.get("META-INF")
            .resolve("services").resolve(S_INTERFACE);
    private static final Path S_META_DESCR_FPATH = S_WITH_META_DESCR_BUILD_DIR
            .resolve(META_DESCR_PATH);

    private static final boolean WITH_S_DESCR = true;
    private static final boolean WITHOUT_S_DESCR = false;
    private static final String CLASS_NOT_FOUND_MSG = "NoClassDefFoundError:"
            + " provider/TestSecurityProvider";
    private static final String PROVIDER_NOT_FOUND_MSG = "Unable to find Test"
            + " Security Provider";
    private static final String CAN_NOT_ACCESS_MSG = "cannot access class";
    private static final String NO_FAILURE = null;
    private static final String SERVICE_LOADER = "SERVICE_LOADER";
    private static final String CLASS_LOADER = "CLASS_LOADER";
    private static final String SECURITY_PROP = "SECURITY_PROP";
    private static final List<String> MECHANISMS = Arrays.asList(SERVICE_LOADER,
            CLASS_LOADER, SECURITY_PROP);
    private static final Path SECURE_PROP_EXTN = Paths.get("./java.secure.ext");

    /**
     * Generates Test specific input parameters.
     */
    @Override
    public Object[][] getTestInput() {

        List<List<Object>> params = new ArrayList<>();
        MECHANISMS.stream().forEach((mechanism) -> {
            boolean useCLoader = CLASS_LOADER.equals(mechanism);
            boolean useSLoader = SERVICE_LOADER.equals(mechanism);
            String[] args = new String[]{mechanism};
            //PARAMETER ORDERS -
            //client Module Type, Service Module Type,
            //Service META Descriptor Required,
            //Expected Failure message, mech used to find the provider
            params.add(Arrays.asList(MODULE_TYPE.EXPLICIT, MODULE_TYPE.EXPLICIT,
                    WITH_S_DESCR, NO_FAILURE, args));
            params.add(Arrays.asList(MODULE_TYPE.EXPLICIT, MODULE_TYPE.EXPLICIT,
                    WITHOUT_S_DESCR, NO_FAILURE, args));
            params.add(Arrays.asList(MODULE_TYPE.EXPLICIT, MODULE_TYPE.AUTO,
                    WITH_S_DESCR, ((useCLoader) ? CAN_NOT_ACCESS_MSG
                            : NO_FAILURE), args));
            params.add(Arrays.asList(MODULE_TYPE.EXPLICIT, MODULE_TYPE.AUTO,
                    WITHOUT_S_DESCR, CLASS_NOT_FOUND_MSG, args));
            params.add(Arrays.asList(MODULE_TYPE.EXPLICIT, MODULE_TYPE.UNNAMED,
                    WITH_S_DESCR, ((useCLoader) ? CAN_NOT_ACCESS_MSG
                            : NO_FAILURE), args));
            params.add(Arrays.asList(MODULE_TYPE.EXPLICIT, MODULE_TYPE.UNNAMED,
                    WITHOUT_S_DESCR, ((useCLoader) ? CAN_NOT_ACCESS_MSG
                            : ((useSLoader) ? PROVIDER_NOT_FOUND_MSG
                                    : NO_FAILURE)), args));

            params.add(Arrays.asList(MODULE_TYPE.AUTO, MODULE_TYPE.EXPLICIT,
                    WITH_S_DESCR, NO_FAILURE, args));
            params.add(Arrays.asList(MODULE_TYPE.AUTO, MODULE_TYPE.EXPLICIT,
                    WITH_S_DESCR, NO_FAILURE, args));
            params.add(Arrays.asList(MODULE_TYPE.AUTO, MODULE_TYPE.AUTO,
                    WITH_S_DESCR, NO_FAILURE, args));
            params.add(Arrays.asList(MODULE_TYPE.AUTO, MODULE_TYPE.AUTO,
                    WITHOUT_S_DESCR, CLASS_NOT_FOUND_MSG, args));
            params.add(Arrays.asList(MODULE_TYPE.AUTO, MODULE_TYPE.UNNAMED,
                    WITH_S_DESCR, NO_FAILURE, args));
            params.add(Arrays.asList(MODULE_TYPE.AUTO, MODULE_TYPE.UNNAMED,
                    WITHOUT_S_DESCR, ((useSLoader) ? PROVIDER_NOT_FOUND_MSG
                            : NO_FAILURE), args));

            params.add(Arrays.asList(MODULE_TYPE.UNNAMED, MODULE_TYPE.EXPLICIT,
                    WITH_S_DESCR, NO_FAILURE, args));
            params.add(Arrays.asList(MODULE_TYPE.UNNAMED, MODULE_TYPE.EXPLICIT,
                    WITHOUT_S_DESCR, NO_FAILURE, args));
            params.add(Arrays.asList(MODULE_TYPE.UNNAMED, MODULE_TYPE.AUTO,
                    WITH_S_DESCR, NO_FAILURE, args));
            params.add(Arrays.asList(MODULE_TYPE.UNNAMED, MODULE_TYPE.AUTO,
                    WITHOUT_S_DESCR, CLASS_NOT_FOUND_MSG, args));
            params.add(Arrays.asList(MODULE_TYPE.UNNAMED, MODULE_TYPE.UNNAMED,
                    WITH_S_DESCR, NO_FAILURE, args));
            params.add(Arrays.asList(MODULE_TYPE.UNNAMED, MODULE_TYPE.UNNAMED,
                    WITHOUT_S_DESCR, ((useSLoader) ? PROVIDER_NOT_FOUND_MSG
                            : NO_FAILURE), args));
        });
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

            done &= CompilerUtils.compile(S_SRC, S_BUILD_DIR);
            done &= CompilerUtils.compile(S_SRC, S_WITH_META_DESCR_BUILD_DIR);
            done &= createMetaInfServiceDescriptor(S_META_DESCR_FPATH, S_IMPL);
            //Generate regular/modular jars with(out) META-INF
            //Service descriptor
            generateJar(true, MODULE_TYPE.EXPLICIT, MS_JAR, S_BUILD_DIR, false);
            generateJar(true, MODULE_TYPE.EXPLICIT, MS_WITH_DESCR_JAR,
                    S_WITH_META_DESCR_BUILD_DIR, false);
            generateJar(true, MODULE_TYPE.UNNAMED, S_JAR, S_BUILD_DIR, false);
            generateJar(true, MODULE_TYPE.UNNAMED, S_WITH_DESCRIPTOR_JAR,
                    S_WITH_META_DESCR_BUILD_DIR, false);
            //Generate regular/modular(depends on explicit/auto Service)
            //jars for client
            done &= CompilerUtils.compile(C_SRC, C_BUILD_DIR, "-cp",
                    S_JAR.toFile().getCanonicalPath());
            generateJar(false, MODULE_TYPE.EXPLICIT, MC_JAR, C_BUILD_DIR, true);
            generateJar(false, MODULE_TYPE.EXPLICIT,
                    MC_DEPENDS_ON_AUTO_SERVICE_JAR, C_BUILD_DIR, false);
            generateJar(false, MODULE_TYPE.UNNAMED, C_JAR, C_BUILD_DIR, false);
            System.out.format("%nArtifacts generated successfully? %s", done);
            if (!done) {
                throw new RuntimeException("Artifacts generation failed");
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
                    S_PKG, S_INTERFACE, S_IMPL, null, M_REQUIRED, depends);
        } else {
            mDescriptor = generateModuleDescriptor(isService, moduleType, C_PKG,
                    C_PKG, S_INTERFACE, null, S_PKG, M_REQUIRED, depends);
        }
        generateJar(mDescriptor, jar, compilePath);
    }

    /**
     * Holds Logic for the test. This method will get called with each test
     * parameter.
     */
    @Override
    public OutputAnalyzer executeTestClient(MODULE_TYPE cModuleType,
            Path cJarPath, MODULE_TYPE sModuletype, Path sJarPath,
            String... args) throws Exception {

        OutputAnalyzer output = null;
        try {

            //For automated/explicit module type copy the corresponding
            //jars to module base folder, which will be considered as
            //module base path during execution.
            if (!(cModuleType == MODULE_TYPE.UNNAMED
                    && sModuletype == MODULE_TYPE.UNNAMED)) {
                copyJarsToModuleBase(cModuleType, cJarPath, M_BASE_PATH);
                copyJarsToModuleBase(sModuletype, sJarPath, M_BASE_PATH);
            }

            System.out.format("%nExecuting java client with required"
                    + " custom security provider in class/module path.");
            String mName = getModuleName(cModuleType, cJarPath, C_PKG);
            Path cmBasePath = (cModuleType != MODULE_TYPE.UNNAMED
                    || sModuletype != MODULE_TYPE.UNNAMED) ? M_BASE_PATH : null;
            String cPath = buildClassPath(cModuleType, cJarPath, sModuletype,
                    sJarPath);

            Map<String, String> VM_ARGS = getVMArgs(sModuletype, args);
            output = ProcessTools.executeTestJava(
                    getJavaCommand(cmBasePath, cPath, mName, MAIN, VM_ARGS,
                            args)).outputTo(System.out).errorTo(System.out);
        } finally {
            //clean module path so that the modulepath can hold only
            //the required jars for next run.
            cleanModuleBasePath(M_BASE_PATH);
            System.out.println("--------------------------------------------");
        }
        return output;
    }

    /**
     * Decide the pre-generated client/service jar path for each test case
     * based on client/service module type.
     */
    @Override
    public Path findJarPath(boolean isService, MODULE_TYPE moduleType,
            boolean addMetaDesc, boolean dependsOnServiceModule) {
        if (isService) {
            if (moduleType == MODULE_TYPE.EXPLICIT) {
                if (addMetaDesc) {
                    return MS_WITH_DESCR_JAR;
                } else {
                    return MS_JAR;
                }
            } else {
                if (addMetaDesc) {
                    return S_WITH_DESCRIPTOR_JAR;
                } else {
                    return S_JAR;
                }
            }
        } else {
            if (moduleType == MODULE_TYPE.EXPLICIT) {
                if (dependsOnServiceModule) {
                    return MC_JAR;
                } else {
                    return MC_DEPENDS_ON_AUTO_SERVICE_JAR;
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
            String... args) throws IOException {
        final Map<String, String> VM_ARGS = new LinkedHashMap<>();
        VM_ARGS.put("-Duser.language=", "en");
        VM_ARGS.put("-Duser.region=", "US");
        //If mechanism selected to find the provider through
        //Security.getProvider() then use providerName/ProviderClassName based
        //on modular/regular provider jar in security configuration file.
        if (args != null && args.length > 0 && SECURITY_PROP.equals(args[0])) {
            if (sModuletype == MODULE_TYPE.UNNAMED) {
                Files.write(SECURE_PROP_EXTN, ("security.provider.10=" + S_IMPL)
                        .getBytes());
            } else {
                Files.write(SECURE_PROP_EXTN, "security.provider.10=TEST"
                        .getBytes());
            }
            VM_ARGS.put("-Djava.security.properties=", SECURE_PROP_EXTN.toFile()
                    .getCanonicalPath());
        }
        return VM_ARGS;
    }

}
