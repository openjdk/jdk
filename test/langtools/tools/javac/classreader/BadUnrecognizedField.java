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

/*
 * @test
 * @bug 8388341
 * @summary Verify that javac ignores reserved bit flags when asked for the field's modifiers
 * @modules jdk.compiler/com.sun.tools.javac.code
 * @run junit BadUnrecognizedField
 */

import com.sun.source.util.JavacTask;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.lang.model.element.Modifier;
import javax.lang.model.util.Elements;
import javax.tools.ToolProvider;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.CD_int;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BadUnrecognizedField {

    @ParameterizedTest
    @ValueSource(ints = {0x0020, 0x0100, 0x0200, 0x0400, 0x0800, 0x2000, 0x8000})
    void testUnrecognizedFields(int bits) throws Throwable {
        for (int major : new int[]{
                ClassFile.JAVA_8_VERSION,
                ClassFile.JAVA_17_VERSION,
                ClassFile.JAVA_25_VERSION,
                ClassFile.latestMajorVersion()
        }) {
            var dir = Files.createTempDirectory("test");
            ClassFile.of().buildTo(dir.resolve("C.class"), ClassDesc.of("C"), cb -> {
                cb.withVersion(major, 0);
                cb.withField("x", CD_int, ACC_PUBLIC | ACC_FINAL | bits);
            });

            var javac = ToolProvider.getSystemJavaCompiler();
            try (var fm = javac.getStandardFileManager(
                    null,
                    null,
                    null)) {
                var task = (JavacTask) javac.getTask(null,
                        fm,
                        null,
                        List.of("-classpath", dir.toString()),
                        null,
                        null);
                var elements = task.getElements();
                var field = elements.getTypeElement("C").getEnclosedElements().getFirst();
                assertEquals(Set.of(Modifier.PUBLIC, Modifier.FINAL), field.getModifiers());
                assertEquals(Elements.Origin.EXPLICIT, elements.getOrigin(field));
            }
        }
    }

    // 0x800 must survive as Flags.STRICT
    @Test
    void testPreviewFields() throws Throwable {
        var dir = Files.createTempDirectory("test");
        ClassFile.of().buildTo(dir.resolve("C.class"), ClassDesc.of("C"), cb -> {
            cb.withVersion(ClassFile.latestMajorVersion(), PREVIEW_MINOR_VERSION);
            cb.withField("x", CD_int, ACC_PUBLIC | ACC_FINAL | ACC_STRICT_INIT);
        });

        var javac = ToolProvider.getSystemJavaCompiler();
        try (var fm = javac.getStandardFileManager(null,
                null,
                null)) {
            var task = (JavacTask) javac.getTask(
                    null,
                    fm,
                    null,
                    List.of("--enable-preview", "--source", Integer.toString(Runtime.version().feature()), "-classpath", dir.toString()),
                    null,
                    null);
            var symbol = (Symbol.VarSymbol) task.getElements().getTypeElement("C").getEnclosedElements().getFirst();
            var actual = (symbol.flags() & Flags.STRICT) != 0;
            assertTrue(actual);
        }
    }
}
