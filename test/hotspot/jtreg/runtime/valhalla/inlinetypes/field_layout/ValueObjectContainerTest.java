/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @key randomness
 * @library /test/lib
 * @requires vm.flagless
 * @modules java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.misc
 * @enablePreview
 * @compile FieldLayoutAnalyzer.java ValueClassGenerator.java ValueObjectContainerTest.java
 * @run main/othervm/timeout=2000 runtime.valhalla.inlinetypes.field_layout.ValueObjectContainerTest
 */

package runtime.valhalla.inlinetypes.field_layout;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Utils;

public class ValueObjectContainerTest {

    static String containerClassTemplate;

    static List<String> generateObjectContainerClasses(Path tempWorkDir, List<String> valueClassNames) {
        ArrayList<String> containerFilesNames = new ArrayList<>();
        if (containerClassTemplate == null) {
            try {
                String path = System.getProperty("test.src");
                containerClassTemplate = Files.readString(Path.of(path, "ValueObjectContainerTemplate.java.template"));
            } catch (Throwable t) {
                t.printStackTrace();
                throw new RuntimeException("Failed to read ValueObjectContainerTemplate.java.template template file", t);
            }
        }
        for (String valName : valueClassNames) {
            String className = valName + "Container";

            String src = containerClassTemplate.replace("<class_name>", className)
                                                        .replace("<value_class_name>", valName);
            String fileName = className + ".java";
            File file = new File(tempWorkDir.toFile(), className + ".java" );
            try (PrintWriter out = new PrintWriter(file)) {
                out.println(src);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            containerFilesNames.add(fileName);
        }
        return containerFilesNames;
    }

    static void compileContainerClasses(List<String> containerFilesNames, Path tempWorkDir) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        File[] files = containerFilesNames.stream().map(name -> new File(tempWorkDir.toFile(), name)).toArray(File[]::new);
        Asserts.assertEquals(files.length, containerFilesNames.size(), "Not all container source files were created");
        ArrayList<String> optionList = new ArrayList<>();
        optionList.addAll(Arrays.asList("-source", Integer.toString(Runtime.version().feature())));
        optionList.addAll(Arrays.asList("--enable-preview"));
        optionList.addAll(Arrays.asList("-d", tempWorkDir.toString()));
        String classpath = System.getProperty("java.class.path") + System.getProperty("path.separator") + tempWorkDir.toString();
        optionList.addAll(Arrays.asList("-cp", classpath));
        optionList.addAll(Arrays.asList("--add-exports", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED"));
        optionList.addAll(Arrays.asList("--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED"));
        StandardJavaFileManager sjfm = compiler.getStandardFileManager(null, null, null);
        Iterable<? extends JavaFileObject> fileObjects = sjfm.getJavaFileObjects(files);
        JavaCompiler.CompilationTask task = compiler.getTask(null, null, null, optionList, null, fileObjects);
        if (!task.call()) {
            throw new AssertionError("test failed due to a compilation error");
        }
        try {
            sjfm.close();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    static class TestRunner {
        public static void main(String[] args) throws Exception {
            for (String s : args) {
                Class<?> c = Class.forName(s);
                try {
                    Method m = c.getDeclaredMethod("runFieldsTests");
                    m.invoke(null);
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    static ProcessBuilder exec(boolean useNullFreeAtomicFlat, boolean useNullableAtomicFlat,
                               boolean useNullableNonAtomicFlat, Path tempWorkDir, String... args) throws Exception {
        List<String> argsList = new ArrayList<>();
        String classpath = System.getProperty("java.class.path") + System.getProperty("path.separator") +
                           tempWorkDir.toString();
        Collections.addAll(argsList, "-cp", classpath);
        Collections.addAll(argsList, "--enable-preview");
        Collections.addAll(argsList, "-XX:+UnlockDiagnosticVMOptions");
        Collections.addAll(argsList, "-XX:+UnlockExperimentalVMOptions");
        Collections.addAll(argsList, "-XX:+PrintFieldLayout");
        Collections.addAll(argsList, "-Xshare:off");
        Collections.addAll(argsList, "-Xmx1g");
        Collections.addAll(argsList, "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED");
        Collections.addAll(argsList, useNullFreeAtomicFlat ? "-XX:+UseNullFreeAtomicValueFlattening" : "-XX:-UseNullFreeAtomicValueFlattening");
        Collections.addAll(argsList, useNullableAtomicFlat ?  "-XX:+UseNullableAtomicValueFlattening" : "-XX:-UseNullableAtomicValueFlattening");
        Collections.addAll(argsList, useNullableNonAtomicFlat ? "-XX:+UseNullableNonAtomicValueFlattening" : "-XX:-UseNullableNonAtomicValueFlattening");
        Collections.addAll(argsList, args);
        return ProcessTools.createTestJavaProcessBuilder(argsList);
  }

    static void runContainerTestWithConfig(List<String> containerFilesNames,
                                                 Path tempWorkDir,int scenario) throws Exception{
        boolean useNullFreeAtomicFlat;
        boolean useNullableAtomicFlat;
        boolean useNullableNonAtomicFlat;

        switch(scenario) {
            case 0: useNullFreeAtomicFlat = false;
                    useNullableAtomicFlat = false;
                    useNullableNonAtomicFlat = false;
                    break;
            case 1: useNullFreeAtomicFlat = true;
                    useNullableAtomicFlat = true;
                    useNullableNonAtomicFlat = true;
                    break;
            case 2: useNullFreeAtomicFlat = false;
                    useNullableAtomicFlat = true;
                    useNullableNonAtomicFlat = false;
                    break;
            case 3: useNullFreeAtomicFlat = true;
                    useNullableAtomicFlat = false;
                    useNullableNonAtomicFlat = false;
                    break;
            case 4: useNullFreeAtomicFlat = true;
                    useNullableAtomicFlat = true;
                    useNullableNonAtomicFlat = false;
                    break;
            case 5: useNullFreeAtomicFlat = false;
                    useNullableAtomicFlat = true;
                    useNullableNonAtomicFlat = true;
                    break;
            default: throw new RuntimeException("Unrecognized configuration");
        }
        String[] testArgs = new String[containerFilesNames.size()+1];
        testArgs[0] = "runtime.valhalla.inlinetypes.field_layout.ValueObjectContainerTest$TestRunner";
        System.arraycopy(containerFilesNames.toArray(), 0, testArgs, 1, containerFilesNames.size());

        // Execute the test runner in charge of loading all test classes
        ProcessBuilder pb = exec(useNullFreeAtomicFlat, useNullableAtomicFlat, useNullableNonAtomicFlat, tempWorkDir,testArgs);
        OutputAnalyzer out = new OutputAnalyzer(pb.start());

        // Checking the status of the process execution before trying to parse the output
        out.shouldHaveExitValue(0);

        // Get and parse the test output
        FieldLayoutAnalyzer.LogOutput lo = new FieldLayoutAnalyzer.LogOutput(out.asLines());
        FieldLayoutAnalyzer fla =  FieldLayoutAnalyzer.createFieldLayoutAnalyzer(lo);

        // Verify that all layouts are correct
        try {
            fla.check();
        } catch (Throwable t) {
            System.out.print(out.getOutput());
            throw t;
        }
    }

    static void runContainerTests(List<String> containerClassNames, Path tempWorkDir) throws Exception {
        for (int i = 0; i <= 5; i++) {
            System.out.println("Running container layout tests with configuration " + i);
            runContainerTestWithConfig(containerClassNames, tempWorkDir, i);
      }
    }

    public static void main(String[] args) throws Exception {
        Path tempWorkDir;
        try {
            tempWorkDir = Utils.createTempDirectory("generatedClasses_");
        } catch (Exception e) {
            System.err.println("Failed to create temporary directory: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        var gen = new ValueClassGenerator(Utils.getRandomInstance(), 10);
        List<String> valueClassNames = gen.generateAll(128,  tempWorkDir);
        Asserts.assertTrue(valueClassNames.size() > 0, "No value classes were generated");
        List<String> containerFilesNames = generateObjectContainerClasses(tempWorkDir, valueClassNames);
        compileContainerClasses(containerFilesNames, tempWorkDir);
        List<String> containerClassNames = containerFilesNames.stream().map(name -> name.substring(0, name.indexOf(".java"))).toList();
        runContainerTests(containerClassNames, tempWorkDir);
    }
}
