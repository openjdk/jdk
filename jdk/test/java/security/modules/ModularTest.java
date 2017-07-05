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

import java.io.IOException;
import java.io.OutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.lang.module.ModuleDescriptor;
import jdk.testlibrary.OutputAnalyzer;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import jdk.internal.module.ModuleInfoWriter;
import static java.lang.module.ModuleDescriptor.Builder;

/**
 * Base class need to be extended by modular test for security.
 */
public abstract class ModularTest {

    /**
     * Enum represents all supported module types supported in JDK9. i.e.
     * EXPLICIT - Modules have module descriptor(module-info.java)
     * defining the module.
     * AUTO - Are regular jar files but provided in MODULE_PATH instead
     * of CLASS_PATH.
     * UNNAMED - Are regular jar but provided through CLASS_PATH.
     */
    public enum MODULE_TYPE {

        EXPLICIT, AUTO, UNNAMED;
    }

    public static final String SPACE = " ";
    public static final Path SRC = Paths.get(System.getProperty("test.src"));
    public static final String DESCRIPTOR = "MetaService";
    public static final String MODULAR = "Modular";
    public static final String AUTO = "AutoServiceType";
    public static final String JAR_EXTN = ".jar";

    /**
     * Setup test data for the test.
     */
    @DataProvider(name = "TestParams")
    public Object[][] setUpTestData() {
        return getTestInput();
    }

    /**
     * Test method for TestNG.
     */
    @Test(dataProvider = "TestParams")
    public void runTest(MODULE_TYPE cModuleType, MODULE_TYPE sModuletype,
            boolean addMetaDesc, String failureMsgExpected, String[] args)
            throws Exception {

        String testName = new StringJoiner("_").add(cModuleType.toString())
                .add(sModuletype.toString()).add(
                        (addMetaDesc) ? "DESCRIPTOR" : "NO_DESCRIPTOR")
                .toString();

        System.out.format("%nStarting Test case: '%s'", testName);
        Path cJarPath = findJarPath(false, cModuleType, false,
                (sModuletype == MODULE_TYPE.EXPLICIT));
        Path sJarPath = findJarPath(true, sModuletype, addMetaDesc, false);
        System.out.format("%nClient jar path : %s ", cJarPath);
        System.out.format("%nService jar path : %s ", sJarPath);
        OutputAnalyzer output = executeTestClient(cModuleType, cJarPath,
                sModuletype, sJarPath, args);

        if (output.getExitValue() != 0) {
            if (failureMsgExpected != null
                    && output.getOutput().contains(failureMsgExpected)) {
                System.out.println("PASS: Test is expected to fail here.");
            } else {
                System.out.format("%nUnexpected failure occured with exit code"
                        + " '%s'.", output.getExitValue());
                throw new RuntimeException("Unexpected failure occured.");
            }
        }
    }

    /**
     * Abstract method need to be implemented by each Test type to provide
     * test parameters.
     */
    public abstract Object[][] getTestInput();

    /**
     * Execute the test client to access required service.
     */
    public abstract OutputAnalyzer executeTestClient(MODULE_TYPE cModuleType,
            Path cJarPath, MODULE_TYPE sModuletype, Path sJarPath,
            String... args) throws Exception;

    /**
     * Find the Jar for service/client based on module type and if service
     * descriptor required.
     */
    public abstract Path findJarPath(boolean service, MODULE_TYPE moduleType,
            boolean addMetaDesc, boolean dependsOnServiceModule);

    /**
     * Constructs a Java Command line string based on modular structure followed
     * by modular client and service.
     */
    public String[] getJavaCommand(Path modulePath, String classPath,
            String clientModuleName, String mainClass,
            Map<String, String> vmArgs, String... options) throws IOException {

        final StringJoiner command = new StringJoiner(SPACE, SPACE, SPACE);
        vmArgs.forEach((key, value) -> command.add(key + value));
        if (modulePath != null) {
            command.add("-mp").add(modulePath.toFile().getCanonicalPath());
        }
        if (classPath != null && classPath.length() > 0) {
            command.add("-cp").add(classPath);
        }
        if (clientModuleName != null && clientModuleName.length() > 0) {
            command.add("-m").add(clientModuleName + "/" + mainClass);
        } else {
            command.add(mainClass);
        }
        command.add(Arrays.stream(options).collect(Collectors.joining(SPACE)));
        return command.toString().trim().split("[\\s]+");
    }

    /**
     * Generate ModuleDescriptor object for explicit/auto based client/Service
     * modules type.
     */
    public ModuleDescriptor generateModuleDescriptor(boolean isService,
            MODULE_TYPE moduleType, String moduleName, String pkg,
            String serviceInterface, String serviceImpl,
            String serviceModuleName, List<String> requiredModules,
            boolean depends) {

        final Builder builder;
        if (moduleType == MODULE_TYPE.EXPLICIT) {
            System.out.format(" %nGenerating ModuleDescriptor object");
            builder = new Builder(moduleName).exports(pkg);
            if (isService) {
                builder.provides(serviceInterface, serviceImpl);
            } else {
                builder.uses(serviceInterface);
                if (depends) {
                    builder.requires(serviceModuleName);
                }
            }
        } else {
            System.out.format(" %nModuleDescriptor object not required.");
            return null;
        }
        requiredModules.stream().forEach(reqMod -> builder.requires(reqMod));
        return builder.build();
    }

    /**
     * Generates service descriptor inside META-INF folder.
     */
    public boolean createMetaInfServiceDescriptor(
            Path serviceDescriptorFile, String serviceImpl) {
        boolean created = true;
        System.out.format("%nCreating META-INF service descriptor for '%s' "
                + "at path '%s'", serviceImpl, serviceDescriptorFile);
        try {
            Path parent = serviceDescriptorFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(serviceDescriptorFile, serviceImpl.getBytes("UTF-8"));
            System.out.println(
                    "META-INF service descriptor generated successfully");
        } catch (IOException e) {
            e.printStackTrace(System.out);
            created = false;
        }
        return created;
    }

    /**
     * Generate modular/regular jar file.
     */
    public void generateJar(ModuleDescriptor mDescriptor, Path jar,
            Path compilePath) throws IOException {
        System.out.format("%nCreating jar file '%s'", jar);
        JarUtils.createJarFile(jar, compilePath);
        if (mDescriptor != null) {
            Path dir = Files.createTempDirectory("tmp");
            Path mi = dir.resolve("module-info.class");
            try (OutputStream out = Files.newOutputStream(mi)) {
                ModuleInfoWriter.write(mDescriptor, out);
            }
            System.out.format("%nAdding 'module-info.class' to jar '%s'", jar);
            JarUtils.updateJarFile(jar, dir);
        }
    }

    /**
     * Copy pre-generated jar files to the module base path.
     */
    public void copyJarsToModuleBase(MODULE_TYPE moduleType, Path jar,
            Path mBasePath) throws IOException {
        if (mBasePath != null) {
            Files.createDirectories(mBasePath);
        }
        if (moduleType != MODULE_TYPE.UNNAMED) {
            Path artifactName = mBasePath.resolve(jar.getFileName());
            System.out.format("%nCopy jar path: '%s' to module base path: %s",
                    jar, artifactName);
            Files.copy(jar, artifactName);
        }
    }

    /**
     * Construct class path string.
     */
    public String buildClassPath(MODULE_TYPE cModuleType,
            Path cJarPath, MODULE_TYPE sModuletype,
            Path sJarPath) throws IOException {
        StringJoiner classPath = new StringJoiner(File.pathSeparator);
        classPath.add((cModuleType == MODULE_TYPE.UNNAMED)
                ? cJarPath.toFile().getCanonicalPath() : "");
        classPath.add((sModuletype == MODULE_TYPE.UNNAMED)
                ? sJarPath.toFile().getCanonicalPath() : "");
        return classPath.toString();
    }

    /**
     * Construct executable module name for java. It is fixed for explicit
     * module type while it is same as jar file name for automated module type.
     */
    public String getModuleName(MODULE_TYPE moduleType,
            Path jarPath, String mName) {
        String jarName = jarPath.toFile().getName();
        return (moduleType == MODULE_TYPE.EXPLICIT) ? mName
                : ((moduleType == MODULE_TYPE.AUTO) ? jarName.substring(0,
                                jarName.indexOf(JAR_EXTN)) : "");
    }

    /**
     * Delete all the files inside the base module path.
     */
    public void cleanModuleBasePath(Path mBasePath) {
        Arrays.asList(mBasePath.toFile().listFiles()).forEach(f -> {
            System.out.println("delete: " + f);
            f.delete();
        });
    }

}
