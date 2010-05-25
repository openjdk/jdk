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
 * @bug 4853450 5014539
 * @summary Tests AnnotationMirror and AnnotationValue methods.
 * @library ../../lib
 * @compile -source 1.5 AnnoMirror.java
 * @run main/othervm AnnoMirror
 */


import java.util.*;
import com.sun.mirror.declaration.*;
import com.sun.mirror.type.*;


public class AnnoMirror extends Tester {

    public static void main(String[] args) {
        (new AnnoMirror()).run();
    }


    @Test(result={"AT1"})
    @AT1
    AnnotationType getAnnotationType() {
        AnnotationMirror anno = getAnno("getAnnotationType", "AT1");
        return anno.getAnnotationType();
    }

    @Test(result={})
    @AT1
    Set getElementValuesNone() {
        AnnotationMirror anno = getAnno("getElementValuesNone", "AT1");
        return anno.getElementValues().entrySet();
    }


    // The seemingly out-of-place parens in the following "result"
    // entry are needed due to the shortcut of having the test return
    // the entry set directly.
    @Test(result={"i()=2",
                  "b()=true",
                  "k()=java.lang.Boolean.class",
                  "a()=@AT1"})
    @AT2(i = 1+1,
         b = true,
         k = Boolean.class,
         a = @AT1)
    Set getElementValues() {
        AnnotationMirror anno = getAnno("getElementValues", "AT2");
        return anno.getElementValues().entrySet();
    }

    @Test(result={"@AT1(\"zax\")",
                  "@AT2(i=2, b=true, k=java.lang.Boolean.class, a=@AT1)",
                  "@AT3(arr={1})",
                  "@AT4({2, 3, 4})"})
    Collection<AnnotationMirror> toStringTests() {
        for (MethodDeclaration m : thisClassDecl.getMethods()) {
            if (m.getSimpleName().equals("toStringTestsHelper")) {
                return m.getAnnotationMirrors();
            }
        }
        throw new AssertionError();
    }

    @AT1("zax")
    @AT2(i = 1+1,
         b = true,
         k = Boolean.class,
         a = @AT1)
    @AT3(arr={1})
    @AT4({2,3,4})
    private void toStringTestsHelper() {
    }
}


/*
 * Annotations used for testing.
 */

@interface AT1 {
    String value() default "";
}

@interface AT2 {
    int i();
    boolean b();
    Class k();
    AT1 a();
}

@interface AT3 {
    int[] arr();
}

@interface AT4 {
    int[] value();
}
