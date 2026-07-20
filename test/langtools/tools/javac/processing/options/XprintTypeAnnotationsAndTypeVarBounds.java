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
 * @bug 8356057
 * @summary Verify annotated supertypes and type variable bounds are printed properly
 * @compile/ref=XprintTypeAnnotationsAndTypeVarBounds.out -Xprint  XprintTypeAnnotationsAndTypeVarBounds.java
 */

import java.lang.annotation.*;

class AnnotatedObjectSuperType extends @TA Object {
}

class UnannotatedObjectSuperType extends Object {
}

class TypeVariableWithAnnotation1<@TA T> {
}

class TypeVariableWithAnnotation2<@TA T extends Object> {
}

class TypeVariableWithBound1<T extends @TA Object> {
}

class TypeVariableWithBound2<T extends @TA CharSequence> {
}

class TypeVariableWithBound3<T extends @TA Object & CharSequence> {
}

class TypeVariableWithBound4<T extends Object & @TA CharSequence> {
}

class TypeVariableWithBound5<T extends CharSequence> {
}

class TypeVariableWithBound6<T extends Object & CharSequence> {
}

class TypeVariableWithBoundRecursive<T extends TypeVariableWithBoundRecursive<T>> {
}

class TypeVariableBoundsOnMethods {
    public <@TA T> void test1() {}
    public <@TA T extends Object> void test2() {}
    public <T extends @TA Object> void test3() {}
    public <T extends @TA CharSequence> void test4() {}
    public <T extends @TA Object & CharSequence> void test5() {}
    public <T extends Object & @TA CharSequence> void test6() {}
    public <T extends CharSequence> void test7() {}
    public <T extends Object & CharSequence> void test8() {}
}

class TypeVariableBoundsOnConstructors {
    public <@TA T> TypeVariableBoundsOnConstructors(boolean b) {}
    public <@TA T extends Object> TypeVariableBoundsOnConstructors(byte b) {}
    public <T extends @TA Object> TypeVariableBoundsOnConstructors(char c) {}
    public <T extends @TA CharSequence> TypeVariableBoundsOnConstructors(short s) {}
    public <T extends @TA Object & CharSequence> TypeVariableBoundsOnConstructors(int i) {}
    public <T extends Object & @TA CharSequence> TypeVariableBoundsOnConstructors(long l) {}
    public <T extends CharSequence> TypeVariableBoundsOnConstructors(float f) {}
    public <T extends Object & CharSequence> TypeVariableBoundsOnConstructors(double d) {}
}

@Target(ElementType.TYPE_USE)
@interface TA {
}
