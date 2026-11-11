/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8301720
 * @summary Test expected value of SUPER AccessFlag for pre-ValueClass .class file
 * @modules java.base/jdk.internal.misc
 * @compile -source 25 -target 25 SuperAccessFlagTest.java
 * @run main SuperAccessFlagTest
 */

import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.reflect.AccessFlag;
import java.util.Set;

import jdk.internal.misc.PreviewFeatures;

/*
 * Test expected value of ACC_SUPER access flag on an earlier release.
 * We test against class files because core reflection automatically upgrades
 * outdated access flags to the latest flags.
 */
@ExpectedClassFlags("[PUBLIC, SUPER]")
public class SuperAccessFlagTest {
    public static void main(String... args) throws Exception {
        checkClass(SuperAccessFlagTest.class);
        checkClass(ExpectedClassFlags.class);
    }

    private static void checkClass(Class<?> clazz) throws Exception {
        ExpectedClassFlags expected =
                clazz.getAnnotation(ExpectedClassFlags.class);
        if (expected != null) {
            // Examine stable representation in class files
            ClassModel cm;
            try (InputStream is = clazz.getResourceAsStream("/" + clazz.getName().replace('.', '/') + ".class")) {
                cm = ClassFile.of().parse(is.readAllBytes());
            }
            check(clazz, expected, cm.flags().flags());

            if (!PreviewFeatures.isEnabled()) {
                // Hotspot performs automatic flag translations, so no preview
                check(clazz, expected, clazz.accessFlags());
            }
        }
    }

    private static void check(Class<?> clazz, ExpectedClassFlags expected, Set<AccessFlag> flags) {
        String actual = flags.toString();
        if (!expected.value().equals(actual)) {
            throw new RuntimeException("On " + clazz +
                    " expected " + expected.value() +
                    " got " + actual);
        }
    }
}

@Retention(RetentionPolicy.RUNTIME)
@ExpectedClassFlags("[INTERFACE, ABSTRACT, ANNOTATION]")
@interface ExpectedClassFlags {
    String value();
}
