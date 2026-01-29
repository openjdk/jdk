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
 * @summary Stress test for creating a large (>2GB) static CDS archive.
 * @requires vm.cds
 * @requires vm.bits == "64"
 * @requires os.maxMemory > 16G
 * @library /test/lib
 * @compile LargeCDSUtil.java LargeCDSApp.java LargeStaticCDS.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar LargeCDSApp LargeCDSUtil
 * @run driver/timeout=20000 LargeStaticCDS
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import jdk.test.lib.cds.SimpleCDSAppTester;

public class LargeStaticCDS {
    private static final String GENERATED_JAR = "generated-classes.jar";

    private static final int SIMPLE_CLASSES = Integer.getInteger("test.cds.large.simple", 250_000);
    private static final int MEGA_CLASSES = Integer.getInteger("test.cds.large.mega", 4_000);
    private static final int MEGA_FIELDS = Integer.getInteger("test.cds.large.mega.fields", 30_000);

    public static void main(String[] args) throws Exception {
        boolean keepFiles = Boolean.getBoolean("test.cds.large.keep.files");
        boolean success = false;
        try {
            Path jar = Paths.get(GENERATED_JAR);
            Files.deleteIfExists(jar);
            LargeCDSUtil.createGeneratedJar(jar, SIMPLE_CLASSES, MEGA_CLASSES, MEGA_FIELDS);

            SimpleCDSAppTester.of("LargeStaticCDS")
                    .addVmArgs("-Xmx4G",
                               "-XX:CompressedClassSpaceSize=4g",
                               "-XX:MaxMetaspaceSize=12G")
                    .classpath("app.jar", jar.toString())
                    .appCommandLine("LargeCDSApp",
                                    Integer.toString(SIMPLE_CLASSES),
                                    Integer.toString(MEGA_CLASSES))
                    .runStaticWorkflow();

            long size = Files.size(Paths.get("LargeStaticCDS.static.jsa"));
            if (size <= LargeCDSUtil.TWO_G) {
                throw new RuntimeException("Expected LargeStaticCDS.static.jsa to be > 2GB, actual=" + size);
            }
            success = true;
        } finally {
            if (success && !keepFiles) {
                LargeCDSUtil.deleteIfExists(
                        GENERATED_JAR,
                        "LargeStaticCDS.classlist",
                        "LargeStaticCDS.classlist.log",
                        "LargeStaticCDS.static.jsa",
                        "LargeStaticCDS.static.jsa.log",
                        "LargeStaticCDS.production.log",
                        "app.jar");
            }
        }
    }

}
