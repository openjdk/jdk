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
 *
 */

/*
 * @test
 * @summary Ensure archived nullable flat field and static null-restricted field metadata are validated when the field class is substituted.
 * @bug 8382584
 * @requires vm.cds
 * @library /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.vm.annotation
 * @compile ArchivedFieldMetadataMismatchTest.java
 * @run driver jdk.test.lib.helpers.StrictProcessor ArchivedFieldMetadataInstanceHolder
 * @run main/othervm ArchivedFieldMetadataMismatchTest
 */

import java.io.File;
import java.lang.invoke.MethodHandles;
import jdk.test.lib.helpers.ClassFileInstaller;
import java.nio.file.Files;
import java.nio.file.Path;

import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;
import jdk.test.lib.helpers.StrictInit;
import jdk.test.lib.process.OutputAnalyzer;

public class ArchivedFieldMetadataMismatchTest {
    static final String[] appClasses = {
        "ArchivedFieldMetadataInstanceApp",
        "ArchivedFieldMetadataInstancePoint",
        "ArchivedFieldMetadataInstanceHolder",
        "ArchivedFieldMetadataStaticApp",
        "ArchivedFieldMetadataStaticPoint",
        "ArchivedFieldMetadataStaticHolder",
    };
    static String appJar;

    public static void main(String[] args) throws Exception {
       appJar = ClassFileInstaller.writeJar("app.jar", appClasses);
       testInstanceField();
       testStaticField();
    }

    private static void testInstanceField() throws Exception {
        String pointClassFile = new File(System.getProperty("test.classes", "."),
                                         "ArchivedFieldMetadataInstancePoint.class").getPath();

        TestCommon.checkDump(TestCommon.dump(appJar,
                        appClasses,
                        "--enable-preview",
                        "-XX:+UnlockDiagnosticVMOptions",
                        "-XX:+UseNullableNonAtomicValueFlattening"));

        OutputAnalyzer output = TestCommon.exec(appJar,
                        "--enable-preview",
                        "-Xlog:class+load,class+preload",
                        "-XX:+UnlockDiagnosticVMOptions",
                        "-XX:+UseNullableNonAtomicValueFlattening",
                        "ArchivedFieldMetadataInstanceApp", pointClassFile);
        TestCommon.checkExec(output);

        output.shouldContain("Preloading of class ArchivedFieldMetadataInstancePoint during loading of shared class " +
                             "ArchivedFieldMetadataInstanceHolder (cause: archived flat/null-restricted field metadata) " +
                             "failed : app substituted a different version");
    }

    private static void testStaticField() throws Exception {
        String pointClassFile = new File(System.getProperty("test.classes", "."),
                                         "ArchivedFieldMetadataStaticPoint.class").getPath();

        TestCommon.checkDump(TestCommon.dump(appJar,
                        appClasses,
                        "--enable-preview"));

        OutputAnalyzer output = TestCommon.exec(appJar,
                        "--enable-preview",
                        "-Xlog:class+load,class+preload",
                        "ArchivedFieldMetadataStaticApp", pointClassFile);
        TestCommon.checkExec(output);

        output.shouldContain("Preloading of class ArchivedFieldMetadataStaticPoint during loading of shared class " +
                             "ArchivedFieldMetadataStaticHolder (cause: archived flat/null-restricted field metadata) " +
                             "failed : app substituted a different version");
    }
}

class ArchivedFieldMetadataInstanceApp {
    public static void main(String[] args) throws Throwable {
        Path classFile = Path.of(args[0]);
        byte[] classBytes = Files.readAllBytes(classFile);
        Class<?> fieldClass = MethodHandles.lookup().defineClass(classBytes);

        ArchivedFieldMetadataInstanceHolder holder = new ArchivedFieldMetadataInstanceHolder();

        if (holder.p.getClass() != fieldClass) {
            throw new RuntimeException("Mismatched field class");
        }
    }
}

class ArchivedFieldMetadataInstanceHolder {
    @StrictInit
    final ArchivedFieldMetadataInstancePoint p;

    ArchivedFieldMetadataInstanceHolder() {
        p = new ArchivedFieldMetadataInstancePoint();
        super();
    }
}

@LooselyConsistentValue
value class ArchivedFieldMetadataInstancePoint {
    int x = 0;
    int y = 0;
}

class ArchivedFieldMetadataStaticApp {
    public static void main(String[] args) throws Throwable {
        Path classFile = Path.of(args[0]);
        byte[] classBytes = Files.readAllBytes(classFile);
        Class<?> fieldClass = MethodHandles.lookup().defineClass(classBytes);

        if (ArchivedFieldMetadataStaticHolder.p.getClass() != fieldClass) {
            throw new RuntimeException("Mismatched static field class");
        }
    }
}

class ArchivedFieldMetadataStaticHolder {
    @NullRestricted
    static ArchivedFieldMetadataStaticPoint p = new ArchivedFieldMetadataStaticPoint();
}

value class ArchivedFieldMetadataStaticPoint {
    int x = 0;
    int y = 0;
}
