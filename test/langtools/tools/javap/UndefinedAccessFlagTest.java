/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @test 8333748
 * @summary javap should not fail if reserved access flag bits are set to 1
 * @library /tools/lib
 * @modules jdk.jdeps/com.sun.tools.javap
 * @enablePreview
 * @run junit UndefinedAccessFlagTest
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import toolbox.JavapTask;
import toolbox.Task;
import toolbox.ToolBox;

import java.lang.classfile.AccessFlags;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.lang.classfile.ClassFile.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UndefinedAccessFlagTest {

    final ToolBox toolBox = new ToolBox();

    enum TestLocation {
        NONE(false), CLASS, FIELD, METHOD, INNER_CLASS(false);

        final boolean fails;
        TestLocation() { this(true); }
        TestLocation(boolean fails) { this.fails = fails; }
    }

    @ParameterizedTest
    @EnumSource(TestLocation.class)
    void test(TestLocation location) throws Throwable {
        var cf = of();
        ClassModel cm;
        try (var is = UndefinedAccessFlagTest.class.getResourceAsStream(
            "/UndefinedAccessFlagTest$SampleInnerClass.class"
        )) {
            cm = cf.parse(is.readAllBytes());
        }
        var bytes = cf.transformClass(cm, (cb, ce) -> {
            switch (ce) {
                case AccessFlags flags when location == TestLocation.CLASS -> cb
                    .withFlags(flags.flagsMask() | ACC_PRIVATE);
                case FieldModel f when location == TestLocation.FIELD -> cb
                    .transformField(f, (fb, fe) -> {
                        if (fe instanceof AccessFlags flags) {
                            fb.withFlags(flags.flagsMask() | ACC_SYNCHRONIZED);
                        } else {
                            fb.with(fe);
                        }
                    });
                case MethodModel m when location == TestLocation.METHOD -> cb
                    .transformMethod(m, (mb, me) -> {
                        if (me instanceof AccessFlags flags) {
                            mb.withFlags(flags.flagsMask() | ACC_INTERFACE);
                        } else {
                            mb.with(me);
                        }
                    });
                case InnerClassesAttribute attr when location == TestLocation.INNER_CLASS -> cb
                    .with(InnerClassesAttribute.of(attr.classes().stream()
                        .map(ic -> InnerClassInfo.of(ic.innerClass(), ic.outerClass(), ic.innerName(), ic.flagsMask() | 0x0020))
                        .toList()));
                default -> cb.with(ce);
            }
        });

        Files.write(Path.of("transformed.class"), bytes);

        var lines = new JavapTask(toolBox)
            .classes("transformed.class")
            .options("-c", "-p", "-v")
            .run(location.fails ? Task.Expect.FAIL : Task.Expect.SUCCESS)
            .writeAll()
            .getOutputLines(Task.OutputKind.DIRECT);

        // No termination when access flag error happens
        assertTrue(lines.stream().anyMatch(l -> l.contains("java.lang.String field;")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("UndefinedAccessFlagTest$SampleInnerClass();")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("void method();")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("SampleInnerClass=class UndefinedAccessFlagTest$SampleInnerClass of class UndefinedAccessFlagTest")));

        // Remove non-error lines
        assertTrue(lines.removeIf(st -> !st.startsWith("Error:")));
        // Desired locations has errors
        assertTrue(location == TestLocation.NONE || !lines.isEmpty());
        // Access Flag errors only
        assertTrue(lines.stream().allMatch(l -> l.contains("Access Flags:")), () -> String.join("\n", lines));
    }

    static class SampleInnerClass {
        String field;
        void method() {}
    }
}
