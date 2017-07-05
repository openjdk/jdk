/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Testing -Xbootclasspath/a support for CDS
 * @library /testlibrary
 * @modules java.base/jdk.internal.misc
 *          java.management
 *          jdk.jvmstat/sun.jvmstat.monitor
 * @ignore 8150683
 * @compile javax/sound/sampled/MyClass.jasm
 * @compile org/omg/CORBA/Context.jasm
 * @compile nonjdk/myPackage/MyClass.java
 * @build jdk.test.lib.* LoadClass ClassFileInstaller
 * @run main/othervm BootAppendTests
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import java.nio.file.Path;
import java.nio.file.Paths;

import jdk.test.lib.ProcessTools;
import jdk.test.lib.OutputAnalyzer;

public class BootAppendTests {
    private static final String APP_CLASS = "LoadClass";
    private static final String BOOT_APPEND_MODULE_CLASS = "javax/sound/sampled/MyClass";
    private static final String BOOT_APPEND_DUPLICATE_MODULE_CLASS = "org/omg/CORBA/Context";
    private static final String BOOT_APPEND_CLASS = "nonjdk/myPackage/MyClass";
    private static final String BOOT_APPEND_MODULE_CLASS_NAME =
        BOOT_APPEND_MODULE_CLASS.replace('/', '.');
    private static final String BOOT_APPEND_DUPLICATE_MODULE_CLASS_NAME =
        BOOT_APPEND_DUPLICATE_MODULE_CLASS.replace('/', '.');
    private static final String BOOT_APPEND_CLASS_NAME =
        BOOT_APPEND_CLASS.replace('/', '.');
    private static final String[] ARCHIVE_CLASSES =
        {BOOT_APPEND_MODULE_CLASS, BOOT_APPEND_DUPLICATE_MODULE_CLASS, BOOT_APPEND_CLASS};

    private static final String modes[] = {"on", "off"};

    private static String appJar;
    private static String bootAppendJar;

    public static void main(String... args) throws Exception {
        dumpArchive();
        testBootAppendModuleClass();
        testBootAppendDuplicateModuleClass();
        testBootAppendExcludedModuleClass();
        testBootAppendDuplicateExcludedModuleClass();
        testBootAppendClass();
    }

    static void dumpArchive() throws Exception {
        // create the classlist
        File classlist = new File(new File(System.getProperty("test.classes", ".")),
                                  "BootAppendTest.classlist");
        FileOutputStream fos = new FileOutputStream(classlist);
        PrintStream ps = new PrintStream(fos);
        for (String s : ARCHIVE_CLASSES) {
            ps.println(s);
        }
        ps.close();
        fos.close();

        // build jar files
        appJar = ClassFileInstaller.writeJar("app.jar", APP_CLASS);
        bootAppendJar = ClassFileInstaller.writeJar("bootAppend.jar",
            BOOT_APPEND_MODULE_CLASS, BOOT_APPEND_DUPLICATE_MODULE_CLASS, BOOT_APPEND_CLASS);

        // dump
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedArchiveFile=./BootAppendTests.jsa",
            "-XX:SharedClassListFile=" + classlist.getPath(),
            "-XX:+PrintSharedSpaces",
            "-Xbootclasspath/a:" + bootAppendJar,
            "-Xshare:dump");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Loading classes to share")
              .shouldHaveExitValue(0);

        // Make sure all the classes were successfully archived.
        for (String archiveClass : ARCHIVE_CLASSES) {
            output.shouldNotContain("Preload Warning: Cannot find " + archiveClass);
        }
    }

    // Test #1: If a class on -Xbootclasspath/a is from a package defined in
    //          bootmodules, the class is not loaded at runtime.
    //          Verify the behavior is the same when the class is archived
    //          with CDS enabled at runtime.
    //
    //          The javax.sound.sampled package is defined in the java.desktop module.
    //          The archived javax.sound.sampled.MyClass from the -Xbootclasspath/a
    //          should not be loaded at runtime.
    public static void testBootAppendModuleClass() throws Exception {
        for (String mode : modes) {
            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:SharedArchiveFile=./BootAppendTests.jsa",
                "-cp", appJar,
                "-Xbootclasspath/a:" + bootAppendJar,
                "-Xshare:" + mode,
                APP_CLASS,
                BOOT_APPEND_MODULE_CLASS_NAME);
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.shouldContain("java.lang.ClassNotFoundException: javax.sound.sampled.MyClass");
        }
    }

    // Test #2: If a class on -Xbootclasspath/a has the same fully qualified
    //          name as a class defined in boot modules, the class is not loaded
    //          from -Xbootclasspath/a. Verify the behavior is the same at runtime
    //          when CDS is enabled.
    //
    //          The org.omg.CORBA.Context is a boot module class. The class on
    //          the -Xbootclasspath/a path that has the same fully-qualified name
    //          should not be loaded at runtime when CDS is enabled.
    //          The one from the boot modules should be loaded instead.
    public static void testBootAppendDuplicateModuleClass() throws Exception {
        for (String mode : modes) {
            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:SharedArchiveFile=./BootAppendTests.jsa",
                "-XX:+TraceClassLoading",
                "-cp", appJar,
                "-Xbootclasspath/a:" + bootAppendJar,
                "-Xshare:" + mode,
                APP_CLASS,
                BOOT_APPEND_DUPLICATE_MODULE_CLASS_NAME);
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.shouldContain("[classload] org.omg.CORBA.Context source: jrt:/java.corba");
        }
    }

    // Test #3: If a class on -Xbootclasspath/a is from a package defined in boot modules,
    //          the class can be loaded from -Xbootclasspath/a when the module is excluded
    //          using -limitmods. Verify the behavior is the same at runtime when CDS is
    //          enabled.
    //
    //          The java.desktop module is excluded using -limitmods at runtime,
    //          javax.sound.sampled.MyClass is archived from -Xbootclasspath/a. It can be
    //          loaded from the archive at runtime.
    public static void testBootAppendExcludedModuleClass() throws Exception {
        for (String mode : modes) {
            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:SharedArchiveFile=./BootAppendTests.jsa",
                "-XX:+TraceClassLoading",
                "-cp", appJar,
                "-Xbootclasspath/a:" + bootAppendJar,
                "-limitmods", "java.base",
                "-Xshare:" + mode,
                APP_CLASS,
                BOOT_APPEND_MODULE_CLASS_NAME);
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.shouldContain("[classload] javax.sound.sampled.MyClass");

            // When CDS is enabled, the shared class should be loaded from the archive.
            if (mode.equals("on")) {
                output.shouldContain("[classload] javax.sound.sampled.MyClass source: shared objects file");
            }
        }
    }

    // Test #4: If a class on -Xbootclasspath/a has the same fully qualified
    //          name as a class defined in boot modules, the class is loaded
    //          from -Xbootclasspath/a when the boot module is excluded using
    //          -limitmods. Verify the behavior is the same at runtime when CDS is
    //          enabled.
    //
    //          The org.omg.CORBA.Context is a boot module class. The class
    //          on -Xbootclasspath/a that has the same fully-qualified name
    //          as org.omg.CORBA.Context can be loaded at runtime when
    //          java.corba is excluded.
    public static void testBootAppendDuplicateExcludedModuleClass() throws Exception {
        for (String mode : modes) {
            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:SharedArchiveFile=./BootAppendTests.jsa",
                "-XX:+TraceClassLoading",
                "-cp", appJar,
                "-Xbootclasspath/a:" + bootAppendJar,
                "-limitmods", "java.base",
                "-Xshare:" + mode,
                APP_CLASS,
                BOOT_APPEND_DUPLICATE_MODULE_CLASS_NAME);
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.shouldContain("[classload] org.omg.CORBA.Context");
            output.shouldMatch(".*\\[classload\\] org.omg.CORBA.Context source:.*bootAppend.jar");
        }
    }

    // Test #5: If a class on -Xbootclasspath/a is not from named modules,
    //          the class can be loaded at runtime. Verify the behavior is
    //          the same at runtime when CDS is enabled.
    //
    //          The nonjdk.myPackage is not defined in named modules. The
    //          archived nonjdk.myPackage.MyClass from -Xbootclasspath/a
    //          can be loaded at runtime when CDS is enabled.
    public static void testBootAppendClass() throws Exception {
        for (String mode : modes) {
            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:SharedArchiveFile=./BootAppendTests.jsa",
                "-XX:+TraceClassLoading",
                "-cp", appJar,
                "-Xbootclasspath/a:" + bootAppendJar,
                "-Xshare:" + mode,
                APP_CLASS,
                BOOT_APPEND_CLASS_NAME);
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.shouldContain("[classload] nonjdk.myPackage.MyClass");

            // If CDS is enabled, the nonjdk.myPackage.MyClass should be loaded
            // from the shared archive.
            if (mode.equals("on")) {
                output.shouldContain(
                    "[classload] nonjdk.myPackage.MyClass source: shared objects file");
            }
        }
    }
}
