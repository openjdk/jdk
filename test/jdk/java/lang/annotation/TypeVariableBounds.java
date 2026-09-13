/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8038994
 * @summary Test that getAnnotatedBounds().getType() match getBounds()
 * @run junit TypeVariableBounds
 */

import java.io.Serializable;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.concurrent.Callable;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class TypeVariableBounds {
    @ParameterizedTest
    @ValueSource(classes = {
            Case1.class,
            Case2.class,
            Case5.class,
            Case6.class,
    })
    public void testClass(Class<?> c) throws Exception {
        assertNotEquals(0, c.getTypeParameters().length);

        TypeVariable[] tv = c.getTypeParameters();

        for(TypeVariable t : tv)
            testTv(t);

    }

    @ParameterizedTest
    @ValueSource(classes = {
            Case3.class,
            Case4.class,
            Case5.class,
            Case6.class,
    })
    public void testMethod(Class<?>c) throws Exception {
        Method m = c.getMethod("aMethod");
        TypeVariable[] tv = m.getTypeParameters();

        for(TypeVariable t : tv)
            testTv(t);

    }

    public void testTv(TypeVariable<?> tv) {
        Type[] t = tv.getBounds();
        AnnotatedType[] at = tv.getAnnotatedBounds();

        assertEquals(t.length, at.length, () -> "Bounds count t = %s; at = %s".formatted(Arrays.asList(t), Arrays.asList(at)));

        for (int i = 0; i < t.length; i++) {
            int p = i;
            assertSame(t[i], at[i].getType(), () -> "Underlying type for at[%d]: %s; t = %s; at = %s".formatted(p, at[p], Arrays.asList(t), Arrays.asList(at)));
        }
    }

    // Class type var
    public static class Case1<C1T1, C1T2 extends AnnotatedElement, C1T3 extends AnnotatedElement & Type & Serializable> {}
    public static class Case2<C2T0, @TA C2T1 extends Type, C2T2 extends @TB AnnotatedElement, C2T3 extends AnnotatedElement & @TB Type & Serializable> {}

    // Method type var
    public static class Case3 { public <C3T1, C3T2 extends AnnotatedElement, C3T3 extends AnnotatedElement & Type & Serializable> void aMethod() {}}
    public static class Case4 { public <C4T0, @TA C4T1 extends List, C4T2 extends @TB Set, C4T3 extends Set & @TB Callable & Serializable> void aMethod() {}}

    // Both
    public static class Case5 <C5CT1, C5CT2 extends Runnable> {
        public <C5MT1,
               C5MT2 extends AnnotatedElement,
               C5MT3 extends AnnotatedElement & Type & Serializable,
               C5MT4 extends C5CT2>
                   void aMethod() {}}

    public static class Case6 <@TA C6CT1, C6CT2 extends @TB Runnable> {
        public <@TA C6MT1,
               C6MT2 extends @TB AnnotatedElement,
               C6MT3 extends @TB AnnotatedElement & @TB2 Type & Serializable,
               C6MT4 extends @TB2 C6CT2>
                   void aMethod() {}}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_PARAMETER)
    public @interface TA {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    public @interface TB {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    public @interface TB2 {}
}
