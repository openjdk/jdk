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
 *
 */

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Platform;

import org.junit.Test;

// This class is for running the ../../../../../../jdk/java/lang/invoke/MethodHandles*java tests
// using CDSAppTester
public class JDKMethodHandlesTestRunner {
    private static final String classDir = System.getProperty("test.classes");
    private static final String mainClass = "TestMHApp";
    private static final String javaClassPath = System.getProperty("java.class.path");
    private static final String ps = System.getProperty("path.separator");
    private static final String testPackageName = "test.java.lang.invoke";

    public static void test(String testClassName) throws Exception {
        String appJar = JarBuilder.build("MH", new File(classDir), null);
        String classList = testClassName + ".list";
        String archiveName = testClassName + ".jsa";
        // Disable VerifyDpendencies when running with debug build because
        // the test requires a lot more time to execute with the option enabled.
        String verifyOpt =
            Platform.isDebugBuild() ? "-XX:-VerifyDependencies" : "-showversion";

        String junitJar = Path.of(Test.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();

        String jars = appJar + ps + junitJar;

        CDSAppTester tester = new CDSAppTester(testClassName) {
                @Override
                public String classpath(RunMode runMode) {
                    return jars;
                }

                @Override
                public String[] vmArgs(RunMode runMode) {
                    if (runMode.isProductionRun()) {
                        return new String[] {
                            "-Xlog:class+load,cds=debug",
                            verifyOpt,
                        };
                    } else {
                        return new String[] {
                            verifyOpt,
                        };
                    }
                }

                @Override
                public String[] appCommandLine(RunMode runMode) {
                    return new String[] {
                        mainClass,
                        testPackageName + "." + testClassName,
                    };
                }

                @Override
                public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
                    out.shouldHaveExitValue(0);
                    if (runMode.isProductionRun()) {
                        out.shouldMatch(".class.load. test.java.lang.invoke." + testClassName +
                                        "[$][$]Lambda.*/0x.*source:.*shared.*objects.*file");
                    }
                }
            };

        String workflow = System.getProperty("cds.app.tester.workflow");
        tester.run(workflow);
    }
}

class TestMHApp {
    public static void main(String args[]) throws Exception {
        try {
            Class<?> testClass = Class.forName(args[0]);
            System.out.println(testClass);
            Object obj = testClass.newInstance();
            final List<Method> allMethods = new ArrayList<Method>(Arrays.asList(testClass.getDeclaredMethods()));
            for (final Method method : allMethods) {
                method.setAccessible(true);
                Annotation[] annotations = null;
                try {
                    annotations = method.getDeclaredAnnotations();
                } catch (Throwable th) {
                    System.out.println("skipping method");
                    continue;
                }
                boolean isTest = false;
                for (Annotation annotation : annotations) {
                    String annotationString = annotation.toString();
                    System.out.println("     annotation: " + annotationString);
                    if (annotationString.startsWith("@org.junit.Test")) {
                        isTest = true;
                    }
                }
                if (isTest) {
                    System.out.println("    invoking method: " + method.getName());
                    try {
                        method.invoke(obj);
                    } catch (IllegalAccessException iae) {
                        System.out.println("Got IllegalAccessException!!!");
                        System.out.println(iae.getCause());
                    } catch (InvocationTargetException ite) {
                        System.out.println("Got InvocationTargetException!!!");
                        throw ite;
                    }
               }
            }
        } catch (ClassNotFoundException cnfe) {
            System.out.println("Class not found: " + args[0]);
        } catch (java.lang.IllegalAccessError iae) {
            System.out.println("Skipping test: " + args[0]);
        }
    }
}
