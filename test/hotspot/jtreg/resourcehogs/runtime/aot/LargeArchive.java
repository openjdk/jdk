/*
 * Copyright (c) 2026 salesforce.com, inc. All Rights Reserved
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
 * @summary Stress test for creating large (>2GB) AOT cache.
 *          Use -Dtest.archive.large.all.workflows=true to also test static and dynamic CDS.
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.bits == "64"
 * @requires os.maxMemory > 16G
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @compile LargeArchiveUtil.java LargeArchiveApp.java LargeArchive.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar LargeArchiveApp LargeArchiveUtil
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar whitebox.jar jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=2400 -Xbootclasspath/a:./whitebox.jar -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI LargeArchive
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import jdk.test.lib.cds.SimpleCDSAppTester;

public class LargeArchive {
    private static final String GENERATED_JAR = "generated-classes.jar";

    // Mega classes with many fields contribute most to archive size (~1MB+ each).
    // Simple classes are much smaller (~1-2KB each) but take time to generate/load.
    //
    // Archive size constraints with UseCompactObjectHeaders:
    // - The narrow klass encoding range is 4GB (22-bit narrowKlass + 10-bit shift)
    // - Both the archive (Klass region) and Class Space must fit within this 4GB range
    // - With CompressedClassSpaceSize=256m, archive can be up to ~3.7GB
    // - For larger archives, split mapping would be needed (not yet implemented)
    //
    // Note: Java class file format limits fields to 65,535 per class.
    private static final int SIMPLE_CLASSES = Integer.getInteger("test.archive.large.simple", 1_000);
    private static final int MEGA_CLASSES = Integer.getInteger("test.archive.large.mega", 2_500);
    private static final int MEGA_FIELDS = Integer.getInteger("test.archive.large.mega.fields", 60_000);

    public static void main(String[] args) throws Exception {
        boolean runAll = Boolean.getBoolean("test.archive.large.all.workflows");
        boolean keepFiles = Boolean.getBoolean("test.archive.large.keep.files");
        boolean success = false;
        try {
            Path jar = Paths.get(GENERATED_JAR);
            Files.deleteIfExists(jar);
            LargeArchiveUtil.createGeneratedJar(jar, SIMPLE_CLASSES, MEGA_CLASSES, MEGA_FIELDS);

            SimpleCDSAppTester tester = SimpleCDSAppTester.of("LargeArchive")
                    .addVmArgs("-Xmx8G",
                               "-XX:+UnlockExperimentalVMOptions",
                               "-XX:+UseCompactObjectHeaders",
                               // With UseCompactObjectHeaders, archive + class space must fit in 4GB encoding range.
                               // Reduce class space size to fit within limit with ~3.5GB archive.
                               "-XX:CompressedClassSpaceSize=256m",
                               "-XX:MaxMetaspaceSize=20G",
                               "-Xlog:aot=debug")
                    .classpath("app.jar", jar.toString())
                    .appCommandLine("LargeArchiveApp",
                                    Integer.toString(SIMPLE_CLASSES),
                                    Integer.toString(MEGA_CLASSES));

            if (runAll) {
                // Run all three workflows (static CDS, dynamic CDS, and AOT)
                tester.runStaticWorkflow()
                      .runDynamicWorkflow()
                      .runAOTWorkflow();
                checkArchiveSize("LargeArchive.static.jsa");
                checkArchiveSize("LargeArchive.dynamic.jsa");
                checkArchiveSize("LargeArchive.aot");
            } else {
                // Default: run AOT workflow only (fastest, covers key code paths)
                tester.runAOTWorkflow();
                checkArchiveSize("LargeArchive.aot");
            }

            success = true;
        } finally {
            if (success && !keepFiles) {
                LargeArchiveUtil.deleteIfExists(
                        GENERATED_JAR,
                        "LargeArchive.classlist",
                        "LargeArchive.classlist.log",
                        "LargeArchive.static.jsa",
                        "LargeArchive.static.jsa.log",
                        "LargeArchive.dynamic.jsa",
                        "LargeArchive.dynamic.jsa.log",
                        "LargeArchive.temp-base.jsa",
                        "LargeArchive.aot",
                        "LargeArchive.aot.log",
                        "LargeArchive.aot.log.0",
                        "LargeArchive.production.log",
                        "app.jar",
                        "whitebox.jar");
            }
        }
    }

    private static void checkArchiveSize(String file) throws Exception {
        long size = Files.size(Paths.get(file));
        System.out.println(file + " size: " + size + " bytes (" + (size / (1024*1024)) + " MB)");
        if (size <= LargeArchiveUtil.TWO_G) {
            throw new RuntimeException("Expected " + file + " to be > 2GB, actual=" + size);
        }
    }
}
