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
 * @bug 8364095
 * @summary InnerClasses generation against an old class with and without preview;
 *          no SUPER pollution and no missing IDENTITY
 * @library /tools/lib /test/lib
 * @run junit InnerClassesIdentityFlagTest
 */

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.nio.file.Path;

import jdk.test.lib.compiler.CompilerUtils;
import org.junit.jupiter.api.Test;
import toolbox.ToolBox;

import static java.lang.classfile.ClassFile.ACC_IDENTITY;
import static java.lang.classfile.ClassFile.ACC_SUPER;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class InnerClassesIdentityFlagTest {
    /// Last release feature number with no value classes (preview exempt)
    private static final String LAST_NON_VALUE_FEATURE = "25";

    @Test
    void test() throws Exception {
        ToolBox toolBox = new ToolBox();
        var libSourceDir = Path.of("libsrc");
        var libDestinationDir = Path.of("libdest");
        toolBox.writeJavaFiles(libSourceDir, """
                public class One {
                    public class Inner {}
                }
                """);
        CompilerUtils.compile(libSourceDir, libDestinationDir, "--release", LAST_NON_VALUE_FEATURE);

        var userSourceDir = Path.of("usersrc");
        var userRegularDestinationDir = Path.of("userdest");
        var userPreviewDestinationDir = Path.of("userdestPreview");
        toolBox.writeJavaFiles(userSourceDir, """
                class Observer {
                    Observer() {
                        new One().new Inner();
                        super(); // triggers -XDforcePreview
                    }
                }
                """);
        CompilerUtils.compile(userSourceDir, userRegularDestinationDir, "--release", LAST_NON_VALUE_FEATURE,
                "-cp", libDestinationDir.toString());
        CompilerUtils.compile(userSourceDir, userPreviewDestinationDir, "--release",
                Integer.toString(Runtime.version().feature()), "--enable-preview", "-XDforcePreview",
                "-cp", libDestinationDir.toString());

        var regularClass = ClassFile.of().parse(userRegularDestinationDir.resolve("Observer.class"));
        var previewClass = ClassFile.of().parse(userPreviewDestinationDir.resolve("Observer.class"));
        assertEquals(0, regularClass.minorVersion());
        assertEquals(ClassFile.PREVIEW_MINOR_VERSION, previewClass.minorVersion());
        var regularRelation = regularClass.findAttribute(Attributes.innerClasses()).orElseThrow().classes().getFirst();
        var previewRelation = previewClass.findAttribute(Attributes.innerClasses()).orElseThrow().classes().getFirst();
        assertEquals("One$Inner", regularRelation.innerClass().asInternalName());
        assertEquals("One$Inner", previewRelation.innerClass().asInternalName());
        int regularFlags = regularRelation.flagsMask();
        int previewFlags = previewRelation.flagsMask();
        assertEquals(0, regularFlags & ACC_SUPER, () -> "ACC_SUPER pollution: " + Integer.toHexString(regularFlags));
        assertEquals(ACC_IDENTITY, previewFlags & ACC_IDENTITY, () -> "Missing ACC_IDENTITY: " + Integer.toHexString(previewFlags));
    }
}
