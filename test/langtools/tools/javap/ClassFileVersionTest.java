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

/*
 * @test
 * @bug 8358078
 * @summary javap should not crash due to class file versions
 * @library /tools/lib
 * @modules jdk.jdeps/com.sun.tools.javap
 * @run junit ClassFileVersionTest
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import toolbox.JavapTask;
import toolbox.Task;
import toolbox.ToolBox;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.ClassFileFormatVersion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.of;

public class ClassFileVersionTest {

    final ToolBox toolBox = new ToolBox();

    public static Stream<Arguments> classFiles() {
        var classFile = ClassFile.of();
        int major17 = ClassFileFormatVersion.RELEASE_17.major();
        int preview = Character.MAX_VALUE;
        int majorLatest = ClassFileFormatVersion.latest().major();
        return Stream.of(
            of(createClassFile(classFile, major17, 0), false),
            of(createClassFile(classFile, major17, preview), false),
            of(createClassFile(classFile, 0, 0), false),
            of(createClassFile(classFile, major17, 0, AccessFlag.PUBLIC), false),
            of(createClassFile(classFile, major17, preview, AccessFlag.PUBLIC), false),
            of(createClassFile(classFile, majorLatest, preview, AccessFlag.PUBLIC), false),
            of(createClassFile(classFile, majorLatest, 0, AccessFlag.BRIDGE), true), // misplaced access flag
            of(createClassFile(classFile, majorLatest, preview, AccessFlag.BRIDGE), true) // misplaced access flag
        );
    }

    private static Object createClassFile(ClassFile classFile, int major, int minor, AccessFlag... classFlags) {
        return classFile.build(ClassDesc.of("Test"), (builder) -> {
            // manually assemble flag bits to avoid exception in ClassFile api
            int flags = 0;
            for (AccessFlag classFlag : classFlags) {
                flags |= classFlag.mask();
            }
            builder.withVersion(major, minor).withFlags(flags);
        });
    }

    @ParameterizedTest
    @MethodSource("classFiles")
    void test(byte[] classFile, boolean shouldError) throws Throwable {

        Files.write(Path.of("cf.class"), classFile);

        var lines = new JavapTask(toolBox)
            .classes("cf.class")
            .options("-c", "-p", "-v")
            .run(shouldError ? Task.Expect.FAIL : Task.Expect.SUCCESS)
            .writeAll()
            .getOutputLines(Task.OutputKind.DIRECT);

        assertEquals(shouldError, lines.stream().anyMatch(l -> l.startsWith("Error: Access Flags:")), "printed error");
    }
}
