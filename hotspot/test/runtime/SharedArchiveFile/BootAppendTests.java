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

/**
 * @test
 * @summary Testing -Xbootclasspath/a support for CDS
 * @requires (vm.opt.UseCompressedOops == null) | (vm.opt.UseCompressedOops == true)
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 *          jdk.internal.jvmstat/sun.jvmstat.monitor
 * @compile javax/sound/sampled/MyClass.jasm
 * @compile org/omg/CORBA/Context.jasm
 * @compile nonjdk/myPackage/MyClass.java
 * @build LoadClass
 * @run main/othervm BootAppendTests
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import java.nio.file.Path;
import java.nio.file.Paths;

import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

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

        logTestCase("1");
        testBootAppendModuleClass();

        logTestCase("2");
        testBootAppendDuplicateModuleClass();

        logTestCase("3");
        testBootAppendExcludedModuleClass();

        logTestCase("4");
        testBootAppendDuplicateExcludedModuleClass();

        logTestCase("5");
        testBootAppendClass();
    }

    private static void logTestCase(String msg) {
        System.out.println();
        System.out.printf("TESTCASE: %s", msg);
        System.out.println();
    }

    static void dumpArchive() throws Exception {
        // create the classlist
        File classlist = CDSTestUtils.makeClassList(ARCHIVE_CLASSES);

        // build jar files
        appJar = ClassFileInstaller.writeJar("app.jar", APP_CLASS);
        bootAppendJar = ClassFileInstaller.writeJar("bootAppend.jar",
            BOOT_APPEND_MODULE_CLASS, BOOT_APPEND_DUPLICATE_MODULE_CLASS, BOOT_APPEND_CLASS);


        OutputAnalyzer out = CDSTestUtils.createArchiveAndCheck(
                                 "-Xbootclasspath/a:" + bootAppendJar,
                                 "-XX:SharedClassListFile=" + classlist.getPath());
        // Make sure all the classes were successfully archived.
        for (String archiveClass : ARCHIVE_CLASSES) {
            out.shouldNotContain("Preload Warning: Cannot find " + archiveClass);
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
            CDSOptions opts = (new CDSOptions())
                .setXShareMode(mode).setUseVersion(false)
                .addPrefix("-Xbootclasspath/a:" + bootAppendJar, "-cp", appJar, "-showversion")
                .addSuffix(APP_CLASS, BOOT_APPEND_MODULE_CLASS_NAME);

            OutputAnalyzer out = CDSTestUtils.runWithArchive(opts);
            CDSTestUtils.checkExec(out, opts, "java.lang.ClassNotFoundException: javax.sound.sampled.MyClass");
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
            CDSOptions opts = (new CDSOptions())
                .setXShareMode(mode).setUseVersion(false)
                .addPrefix("--add-modules", "java.corba", "-showversion",
                           "-Xbootclasspath/a:" + bootAppendJar, "-cp", appJar)
                .addSuffix("-Xlog:class+load=info",
                           APP_CLASS, BOOT_APPEND_DUPLICATE_MODULE_CLASS_NAME);

            OutputAnalyzer out = CDSTestUtils.runWithArchive(opts);
            CDSTestUtils.checkExec(out, opts, "[class,load] org.omg.CORBA.Context source: jrt:/java.corba");
        }
    }

    // Test #3: If a class on -Xbootclasspath/a is from a package defined in boot modules,
    //          the class can be loaded from -Xbootclasspath/a when the module is excluded
    //          using --limit-modules. Verify the behavior is the same at runtime when CDS
    //          is enabled.
    //
    //          The java.desktop module is excluded using --limit-modules at runtime,
    //          javax.sound.sampled.MyClass is archived from -Xbootclasspath/a. It can be
    //          loaded from the archive at runtime.
    public static void testBootAppendExcludedModuleClass() throws Exception {
        for (String mode : modes) {
            CDSOptions opts = (new CDSOptions())
                .setXShareMode(mode).setUseVersion(false)
                .addPrefix("-Xbootclasspath/a:" + bootAppendJar, "-showversion",
                           "--limit-modules=java.base", "-cp", appJar)
                .addSuffix("-Xlog:class+load=info",
                           APP_CLASS, BOOT_APPEND_MODULE_CLASS_NAME);

            OutputAnalyzer out = CDSTestUtils.runWithArchive(opts);
            CDSTestUtils.checkExec(out, opts, "[class,load] javax.sound.sampled.MyClass");

            // When CDS is enabled, the shared class should be loaded from the archive.
            if (mode.equals("on")) {
                CDSTestUtils.checkExec(out, opts, "[class,load] javax.sound.sampled.MyClass source: shared objects file");
            }
        }
    }

    // Test #4: If a class on -Xbootclasspath/a has the same fully qualified
    //          name as a class defined in boot modules, the class is loaded
    //          from -Xbootclasspath/a when the boot module is excluded using
    //          --limit-modules. Verify the behavior is the same at runtime
    //          when CDS is enabled.
    //
    //          The org.omg.CORBA.Context is a boot module class. The class
    //          on -Xbootclasspath/a that has the same fully-qualified name
    //          as org.omg.CORBA.Context can be loaded at runtime when
    //          java.corba is excluded.
    public static void testBootAppendDuplicateExcludedModuleClass() throws Exception {
        for (String mode : modes) {
            CDSOptions opts = (new CDSOptions())
                .setXShareMode(mode).setUseVersion(false)
                .addPrefix("-Xbootclasspath/a:" + bootAppendJar, "-showversion",
                           "--limit-modules=java.base", "-cp", appJar)
                .addSuffix("-Xlog:class+load=info",
                           APP_CLASS, BOOT_APPEND_DUPLICATE_MODULE_CLASS_NAME);

            OutputAnalyzer out = CDSTestUtils.runWithArchive(opts);
            CDSTestUtils.checkExec(out, opts, "[class,load] org.omg.CORBA.Context");
            if (!CDSTestUtils.isUnableToMap(out)) {
                out.shouldMatch(".*\\[class,load\\] org.omg.CORBA.Context source:.*bootAppend.jar");
            }
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
            CDSOptions opts = (new CDSOptions())
                .setXShareMode(mode).setUseVersion(false)
                .addPrefix("-Xbootclasspath/a:" + bootAppendJar, "-showversion",
                           "--limit-modules=java.base", "-cp", appJar)
                .addSuffix("-Xlog:class+load=info",
                           APP_CLASS, BOOT_APPEND_CLASS_NAME);

            OutputAnalyzer out = CDSTestUtils.runWithArchive(opts);
            CDSTestUtils.checkExec(out, opts, "[class,load] nonjdk.myPackage.MyClass");

            // If CDS is enabled, the nonjdk.myPackage.MyClass should be loaded
            // from the shared archive.
            if (mode.equals("on")) {
                CDSTestUtils.checkExec(out, opts,
                    "[class,load] nonjdk.myPackage.MyClass source: shared objects file");
            }
        }
    }
}
