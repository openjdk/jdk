/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8304937
 * @compile -parameters ClassBuildingTest.java
 * @summary Ensure that class transform chaining works.
 * @run junit ClassBuildingTest
 */

import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.ClassFile;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.attribute.MethodParametersAttribute;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.classfile.components.ClassRemapper;
import org.junit.jupiter.api.Test;

import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandles;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

public class ClassBuildingTest {
    @Test
    public void test() throws Throwable {
        var cc = ClassFile.of();
        ClassModel cm;
        try (var in = ClassBuildingTest.class.getResourceAsStream("/Outer$1Local.class")) {
            cm = cc.parse(Objects.requireNonNull(in).readAllBytes());
        }

        ClassTransform transform = ClassRemapper.of(Map.of(ClassDesc.of("Outer"), ClassDesc.of("Router")));
        transform = transform.andThen(ClassTransform.transformingMethods(MethodTransform.dropping(me
                -> me instanceof MethodParametersAttribute)));
        transform = transform.andThen(ClassTransform.transformingMethods(MethodTransform.dropping(me
                -> me instanceof SignatureAttribute)));

        MethodHandles.lookup().defineClass(cc.transform(cm, transform));
    }
}

class Outer {
    void method(int p) {
        class Local<V> {
            Local(V value, int q, Comparator<Integer> p2) {
                System.out.println(p + q);
            }
        }
    }
}
