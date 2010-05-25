/*
 * Copyright (c) 2004, 2008, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4989091 5050782 5051962
 * @summary Tests Declaration.getAnnotation method
 * @library ../../lib
 * @compile -source 1.5 GetAnno.java
 * @run main/othervm GetAnno
 */


import java.lang.annotation.*;
import java.util.*;

import com.sun.mirror.declaration.*;
import com.sun.mirror.type.*;

import static java.lang.annotation.RetentionPolicy.*;


public class GetAnno extends Tester {

    public static void main(String[] args) {
        (new GetAnno()).run();
    }


    // Annotations used by tests

    @Retention(RUNTIME)
    @interface AT1 {
        long l();
        String s();
        RetentionPolicy e();
        String[] sa();
        AT2 a();
    }

    @Inherited
    @interface AT2 {
    }

    @interface AT3 {
        Class value() default String.class;
    }

    // Array-valued elements of various kinds.
    @interface AT4 {
        boolean[] bs();
        long[] ls();
        String[] ss();
        RetentionPolicy[] es();
        AT2[] as();
    }


    @Test(result="@GetAnno$AT1(l=7, s=sigh, e=CLASS, sa=[in, out], " +
                              "a=@GetAnno$AT2())")
    @AT1(l=7, s="sigh", e=CLASS, sa={"in", "out"}, a=@AT2)
    public Annotation getAnnotation() {
        MethodDeclaration m = getMethod("getAnnotation");
        AT1 a = m.getAnnotation(AT1.class);
        if (a.l() != 7 || !a.s().equals("sigh") || a.e() != CLASS)
            throw new AssertionError();
        return a;
    }

    @Test(result="null")
    public Annotation getAnnotationNotThere() {
        return thisClassDecl.getAnnotation(Deprecated.class);
    }

    @Test(result="@GetAnno$AT4(bs=[true, false], " +
                              "ls=[9, 8], " +
                              "ss=[black, white], " +
                              "es=[CLASS, SOURCE], " +
                              "as=[@GetAnno$AT2(), @GetAnno$AT2()])")
    @AT4(bs={true, false},
         ls={9, 8},
         ss={"black", "white"},
         es={CLASS, SOURCE},
         as={@AT2, @AT2})
    public AT4 getAnnotationArrayValues() {
        MethodDeclaration m = getMethod("getAnnotationArrayValues");
        return m.getAnnotation(AT4.class);
    }

    @Test(result="@GetAnno$AT3(value=java.lang.String)")
    @AT3(String.class)
    public AT3 getAnnotationWithClass1() {
        MethodDeclaration m = getMethod("getAnnotationWithClass1");
        return m.getAnnotation(AT3.class);
    }

    @Test(result="java.lang.String")
    public TypeMirror getAnnotationWithClass2() {
        AT3 a = getAnnotationWithClass1();
        try {
            Class c = a.value();
            throw new AssertionError();
        } catch (MirroredTypeException e) {
            return e.getTypeMirror();
        }
    }

    @Test(result="boolean")
    @AT3(boolean.class)
    public TypeMirror getAnnotationWithPrim() {
        MethodDeclaration m = getMethod("getAnnotationWithPrim");
        AT3 a = m.getAnnotation(AT3.class);
        try {
            Class c = a.value();
            throw new AssertionError();
        } catch (MirroredTypeException e) {
            return e.getTypeMirror();
        }
    }

    // 5050782
    @Test(result="null")
    public AT2 getInheritedAnnotation() {
        return thisClassDecl.getAnnotation(AT2.class);
    }

    /**
     * Verify that an annotation created by Declaration.getAnnotation()
     * has the same hash code as a like annotation created by core
     * reflection.
     */
    @Test(result="true")
    @AT1(l=7, s="sigh", e=CLASS, sa={"in", "out"}, a=@AT2)
    public boolean getAnnotationHashCode() {
        MethodDeclaration m1 = getMethod("getAnnotationHashCode");
        AT1 a1 = m1.getAnnotation(AT1.class);
        java.lang.reflect.Method m2 = null;
        try {
            m2 = this.getClass().getMethod("getAnnotationHashCode");
        } catch (NoSuchMethodException e) {
            assert false;
        }
        AT1 a2 = m2.getAnnotation(AT1.class);
        return a1.hashCode() == a2.hashCode();
    }
}
