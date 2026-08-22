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

import static jdk.jpackage.test.JPackageCommand.RuntimeImageType.RUNTIME_TYPE_FAKE;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.test.Annotations.Test;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.test.Executor;
import jdk.jpackage.test.HelloApp;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JavaAppDesc;
import jdk.jpackage.test.JavaTool;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.TKit;

/*
 * @test
 * @summary Probes bundling environment
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror BundlingEnvironmentProbeTest.java
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=BundlingEnvironmentProbeTest
 */

/**
 * Tries all supported packagings in the system.
 * <p>
 * The goal is to make jpackage log actions it
 * takes to detect available packing tools. This is primarily for debugging
 * issues related to unavailability of specific packaging in the system.
 */
public class BundlingEnvironmentProbeTest {

    @Test
    public static void test() {

        var env = System.getenv().entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey)).map(e -> {
            return String.format("%s=%s", e.getKey(), e.getValue());
        }).collect(Collectors.joining("\n"));

        TKit.trace("\n--- Environment ---\n" + env + "\n--- Environment ---");

        var jar = HelloApp.createBundle(JavaAppDesc.parse("Hello!"), TKit.createTempDirectory("input"));

        var outputDir = TKit.createTempDirectory("output").toString();

        var runtimeImage = JPackageCommand.createInputRuntimeImage(RUNTIME_TYPE_FAKE).toString();

        for (var type : Stream.of(PackageType.values()).filter(packageType -> {
            return packageType.os() == OperatingSystem.current();
        }).filter(PackageType::isNative).distinct().map(PackageType::getType).toList()) {
            Executor.of(
                    JavaTool.JPACKAGE.getPath().toString(),
                    "--type", type,
                    "--input", jar.getParent().toString(),
                    "--main-jar", jar.getFileName().toString(),
                    "--dest", outputDir,
                    "--runtime-image", runtimeImage,
                    "--verbose", "console").dumpOutput().executeWithoutExitCodeCheck();
        }
    }
}
