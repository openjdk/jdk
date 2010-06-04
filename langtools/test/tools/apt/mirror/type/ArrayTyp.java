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
 * @bug 4853450 5009357
 * @summary ArrayType tests
 * @library ../../lib
 * @compile -source 1.5 ArrayTyp.java
 * @run main/othervm ArrayTyp
 */


import java.util.*;
import com.sun.mirror.declaration.*;
import com.sun.mirror.type.*;
import com.sun.mirror.util.*;


public class ArrayTyp extends Tester {

    public static void main(String[] args) {
        (new ArrayTyp()).run();
    }


    // Declaration used by tests

    private boolean[] bs;
    private String[][] bss;


    private ArrayType arr;              // an array type
    private ArrayType arrarr;           // a multi-dimensional array type

    protected void init() {
        arr = (ArrayType) getField("bs").getType();
        arrarr = (ArrayType) getField("bss").getType();
    }


    // TypeMirror methods

    @Test(result="array")
    Collection<String> accept() {
        final Collection<String> res = new ArrayList<String>();

        arr.accept(new SimpleTypeVisitor() {
            public void visitTypeMirror(TypeMirror t) {
                res.add("type");
            }
            public void visitArrayType(ArrayType t) {
                res.add("array");
            }
            public void visitReferenceType(ReferenceType t) {
                res.add("ref type");
            }
        });
        return res;
    }

    @Test(result="boolean[]")
    String toStringTest() {
        return arr.toString();
    }

    @Test(result="java.lang.String[][]")
    String toStringTestMulti() {
        return arrarr.toString();
    }


    // ArrayType method

    @Test(result="boolean")
    TypeMirror getComponentType() {
        return (PrimitiveType) arr.getComponentType();
    }

    @Test(result="java.lang.String[]")
    TypeMirror getComponentTypeMulti() {
        return (ArrayType) arrarr.getComponentType();
    }
}
