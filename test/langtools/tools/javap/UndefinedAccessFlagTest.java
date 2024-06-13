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

import toolbox.JavapTask;
import toolbox.ToolBox;

import org.junit.jupiter.api.Test;

import java.lang.classfile.AccessFlags;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.lang.classfile.ClassFile.*;

public class UndefinedAccessFlagTest {

    final ToolBox toolBox = new ToolBox();

    @Test
    void test() throws Throwable {
        var cf = of();
        ClassModel cm;
        try (var is = UndefinedAccessFlagTest.class.getResourceAsStream(
            "/UndefinedAccessFlagTest$SampleInnerClass.class"
        )) {
            cm = cf.parse(is.readAllBytes());
        }
        var bytes = cf.transform(cm, (cb, ce) -> {
            switch (ce) {
                case AccessFlags flags -> cb.withFlags(flags.flagsMask() | ACC_PRIVATE);
                case FieldModel f -> cb.transformField(f, (fb, fe) -> {
                    if (fe instanceof AccessFlags flags) {
                        fb.withFlags(flags.flagsMask() | ACC_SYNCHRONIZED);
                    } else {
                        fb.with(fe);
                    }
                });
                case MethodModel m -> cb.transformMethod(m, (mb, me) -> {
                    if (me instanceof AccessFlags flags) {
                        mb.withFlags(flags.flagsMask() | ACC_INTERFACE);
                    } else {
                        mb.with(me);
                    }
                });
                case InnerClassesAttribute attr -> {
                    cb.with(InnerClassesAttribute.of(attr.classes().stream()
                        .map(ic -> InnerClassInfo.of(ic.innerClass(), ic.outerClass(), ic.innerName(), ic.flagsMask() | 0x0020))
                        .toList()));
                }
                default -> cb.with(ce);
            }
        });

        Files.write(Path.of("transformed.class"), bytes);

        new JavapTask(toolBox)
            .classes("transformed.class")
            .options("-c", "-p", "-v")
            .run();
    }

    static class SampleInnerClass {
        String field;
        void method() {}
    }
}
