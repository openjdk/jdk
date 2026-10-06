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

/**
 * @test
 * @bug 8393242
 * @summary Test jlink with a non-package resource directory in several modules
 * @library /test/lib
 * @modules java.base/jdk.internal.module
 *          jdk.jlink
 *          jdk.jartool
 * @run junit/othervm NonPackageResourceDirTest
 */

import java.lang.module.ModuleDescriptor;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.spi.ToolProvider;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.ModuleInfoWriter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;

class NonPackageResourceDirTest {

    @BeforeAll
    static void setup() throws Exception {
        Path src = Files.createDirectory(Path.of(".").resolve("src"));
        // Create module a containing
        //     module-info.class
        //     a/C.class
        //     OSGI-INF/MANIFEST.MF
        String moduleName = "a";
        ModuleDescriptor descriptor = ModuleDescriptor.newModule(moduleName)
                .exports("a")
                .build();
        byte[] moduleInfo = ModuleInfoWriter.toBytes(descriptor);
        Path dir = Files.createDirectory(Path.of(".").resolve(moduleName));
        Files.write(dir.resolve("module-info.class"), moduleInfo);
        Path srcA = Files.writeString(Files.createDirectory(src.resolve("a")).resolve("C.java"),
                """
                package a;
                public class C {}
                """);
        ToolProvider javac = ToolProvider.findFirst("javac").orElseThrow();
        javac.run(System.out, System.err, "-d", dir.toString(), srcA.toString());
        Files.createFile(Files.createDirectory(dir.resolve("OSGI-INF")).resolve("MANIFEST.MF"));

        // Create modular jar of a
        ToolProvider jar = ToolProvider.findFirst("jar").orElseThrow();
        jar.run(System.out, System.err, "--create", "--file", moduleName + ".jar", "-C", moduleName, ".");

        // Create module b containing
        //     module-info.class
        //     b/C.class
        //     OSGI-INF/MANIFEST.MF
        moduleName = "b";
        descriptor = ModuleDescriptor.newModule(moduleName)
                .exports("b")
                .build();
        moduleInfo = ModuleInfoWriter.toBytes(descriptor);
        dir = Files.createDirectory(Path.of(".").resolve(moduleName));
        Files.write(dir.resolve("module-info.class"), moduleInfo);
        Path srcB = Files.writeString(Files.createDirectory(src.resolve("b")).resolve("C.java"),
                """
                package b;
                public class C {}
                """);
        javac.run(System.out, System.err, "-d", dir.toString(), srcB.toString());
        Files.createFile(Files.createDirectory(dir.resolve("OSGI-INF")).resolve("MANIFEST.MF"));

        // Create modular jar of b
        jar.run(System.out, System.err, "--create", "--file", moduleName + ".jar", "-C", moduleName, ".");
    }

    @Test
    void testJavaValidate() throws Exception {
        ProcessTools.executeProcess(ProcessTools.createTestJavaProcessBuilder(
                "--module-path", "a.jar" + File.pathSeparator + "b.jar",
                "--validate-modules")).shouldHaveExitValue(0);
    }

    @Test
    void testJlink() throws Exception {
        ToolProvider jlink = ToolProvider.findFirst("jlink").orElseThrow();
        int res = jlink.run(System.out, System.err, "--module-path", "a.jar" + File.pathSeparator + "b.jar",
                "--add-modules", "a,b", "--output", "image");
        assertEquals(0, res);
    }
}
