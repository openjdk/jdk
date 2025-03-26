/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8352621
 * @summary Verify javac does not use primitive types in SwitchBootstraps.typeSwitch
 *          when compiling with target older than JDK 23
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main NoPrimitivesAsCaseLabelsFor21
*/

import java.io.IOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.constantpool.MethodHandleEntry;
import java.lang.constant.DirectMethodHandleDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

import toolbox.JavacTask;
import toolbox.TestRunner;
import toolbox.ToolBox;

public class NoPrimitivesAsCaseLabelsFor21 extends TestRunner {

    ToolBox tb;

    public static void main(String... args) throws Exception {
        new NoPrimitivesAsCaseLabelsFor21().runTests();
    }

    NoPrimitivesAsCaseLabelsFor21() {
        super(System.err);
        tb = new ToolBox();
    }

    public void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testExhaustiveSealedClasses(Path base) throws Exception {
        Path current = base.resolve(".");
        Path src = current.resolve("src");

        tb.writeJavaFiles(src,
                          """
                          package test;
                          public class Test {
                                private int test(Object obj) {
                                  return switch (obj) {
                                      case R1(String s1, String s2) when s1.isEmpty() -> 0;
                                      case R1(String s1, String s2) -> 1;
                                      case R2(int i1, int i2) when i1 == 0 -> 2;
                                      case R2(int i1, int i2) -> 3;
                                      default -> 4;
                                  };
                              }
                              record R1(String s1, String s2) {}
                              record R2(int i1, int i2) {}
                          }
                          """);

        Path classes = current.resolve("classes");

        Files.createDirectories(classes);

        for (String version : new String[] {"23", System.getProperty("java.specification.version")}) {
            new JavacTask(tb)
                .options("--release", version)
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run()
                .writeAll();

            Path testClassFile = classes.resolve("test").resolve("Test.class");
            String primitivesInBoostrapArgsForNewer =
                    findPrimitiveBootstrapArguments(testClassFile);

            if (!primitivesInBoostrapArgsForNewer.contains("I-Ljava/lang/Class")) {
                throw new AssertionError("Expected primitive types in switch bootstrap arguments: " + primitivesInBoostrapArgsForNewer);
            }
        }

        for (String version : new String[] {"21", "22"}) {
            new JavacTask(tb)
                .options("--release", version)
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run()
                .writeAll();

            Path testClassFile = classes.resolve("test").resolve("Test.class");
            String primitivesInBoostrapArgsForOlder =
                    findPrimitiveBootstrapArguments(testClassFile);


            if (!primitivesInBoostrapArgsForOlder.isEmpty()) {
                throw new AssertionError("Unexpected primitive types in switch bootstrap arguments: " + primitivesInBoostrapArgsForOlder);
            }
        }
    }

    private String findPrimitiveBootstrapArguments(Path forFile) throws IOException {
        AtomicBoolean hasTypeSwitchBootStrap = new AtomicBoolean();
        StringBuilder nonClassInTypeSwitchBootStrap = new StringBuilder();
        ClassModel testClassFileModel = ClassFile.of().parse(forFile);

        testClassFileModel.findAttribute(Attributes.bootstrapMethods())
                          .orElseThrow()
                          .bootstrapMethods()
                          .stream()
                          .filter(bme -> isTypeSwitchBoostrap(bme.bootstrapMethod()))
                          .forEach(bme -> {
            hasTypeSwitchBootStrap.set(true);
            for (LoadableConstantEntry e : bme.arguments()) {
                if (!(e instanceof ClassEntry)) {
                    nonClassInTypeSwitchBootStrap.append(String.valueOf(e));
                }
            }
        });

        if (!hasTypeSwitchBootStrap.get()) {
            throw new AssertionError("Didn't find any typeSwitch bootstraps!");
        }

        return nonClassInTypeSwitchBootStrap.toString();
    }

    private static boolean isTypeSwitchBoostrap(MethodHandleEntry entry) {
        DirectMethodHandleDesc desc = entry.asSymbol();
        return "Ljava/lang/runtime/SwitchBootstraps;".equals(desc.owner().descriptorString()) &&
               "typeSwitch".equals(desc.methodName()) &&
               "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;".equals(desc.lookupDescriptor());
    }
}
