/*
 * Copyright (c) 2017, Google Inc. All rights reserved.
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
 * @bug 8144185
 * @summary javac produces incorrect RuntimeInvisibleTypeAnnotations length attribute
 * @enablePreview
 * @modules java.base/jdk.internal.classfile.impl
 */

import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.classfile.*;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class TypeAnnotationPropagationTest extends ClassfileTestHelper {
    public static void main(String[] args) throws Exception {
        new TypeAnnotationPropagationTest().run();
    }

    public void run() throws Exception {
        ClassModel cm = getClassFile("TypeAnnotationPropagationTest$Test.class");

        MethodModel f = null;
        for (MethodModel mm : cm.methods()) {
            if (mm.methodName().stringValue().contains("f")) {
                f = mm;
                break;
            }
        }

        assert f != null;
        CodeAttribute cattr = f.findAttribute(Attributes.CODE).orElse(null);
        assert cattr != null;
        RuntimeVisibleTypeAnnotationsAttribute attr = cattr.findAttribute(Attributes.RUNTIME_VISIBLE_TYPE_ANNOTATIONS).orElse(null);

        assert attr != null;
        List<TypeAnnotation.LocalVarTargetInfo> annosPosition = ((TypeAnnotation.LocalVarTarget) attr.annotations().get(0).targetInfo()).table();
        int[] lvarOffset = annosPosition.stream()
                .map(e -> cattr.labelToBci(e.startLabel()))
                .mapToInt(t -> t).toArray();
        int[] lvarLength = annosPosition.stream()
                .map(e -> cattr.labelToBci(e.endLabel()) - cattr.labelToBci(e.startLabel()))
                .mapToInt(t -> t).toArray();
        int[] lvarIndex = annosPosition.stream()
                .map(TypeAnnotation.LocalVarTargetInfo::index)
                .mapToInt(t -> t).toArray();

        assertEquals(lvarOffset, new int[] {3}, "start_pc");
        assertEquals(lvarLength, new int[] {8}, "length");
        assertEquals(lvarIndex, new int[] {1}, "index");
    }

    void assertEquals(int[] actual, int[] expected, String message) {
        if (!Arrays.equals(actual, expected)) {
            throw new AssertionError(
                    String.format(
                            "actual: %s, expected: %s, %s",
                            Arrays.toString(actual), Arrays.toString(expected), message));
        }
    }

    /** ********************* Test class ************************ */
    static class Test {
        void f() {
            @A String s = "";
            Runnable r =
                    () -> {
                        Objects.requireNonNull(s);
                        Objects.requireNonNull(s);
                        Objects.requireNonNull(s);
                        Objects.requireNonNull(s);
                        Objects.requireNonNull(s);
                        Objects.requireNonNull(s);
                    };
        }

        @Retention(RUNTIME)
        @Target(TYPE_USE)
        @interface A {}
    }
}

