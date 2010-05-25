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
 * @bug 4853450
 * @summary TypeVariable tests
 * @library ../../lib
 * @compile -source 1.5 TypeVar.java
 * @run main/othervm TypeVar
 */


import java.util.*;
import com.sun.mirror.declaration.*;
import com.sun.mirror.type.*;
import com.sun.mirror.util.*;


public class TypeVar<T, S extends Number & Runnable> extends Tester {

    public static void main(String[] args) {
        (new TypeVar()).run();
    }


    // Declarations used by tests

    private T t;
    private S s;


    private TypeVariable tvT;   // type variable T
    private TypeVariable tvS;   // type variable S

    protected void init() {
        tvT = (TypeVariable) getField("t").getType();
        tvS = (TypeVariable) getField("s").getType();
    }


    // TypeMirror methods

    @Test(result="type var")
    Collection<String> accept() {
        final Collection<String> res = new ArrayList<String>();

        tvT.accept(new SimpleTypeVisitor() {
            public void visitTypeMirror(TypeMirror t) {
                res.add("type");
            }
            public void visitReferenceType(ReferenceType t) {
                res.add("ref type");
            }
            public void visitTypeVariable(TypeVariable t) {
                res.add("type var");
            }
        });
        return res;
    }

    @Test(result="T")
    String toStringTest1() {
        return tvT.toString();
    }

    @Test(result="S")
    String toStringTest2() {
        return tvS.toString();
    }


    // TypeVariable method

    @Test(result="S extends java.lang.Number & java.lang.Runnable")
    TypeParameterDeclaration getDeclaration() {
        return tvS.getDeclaration();
    }
}
