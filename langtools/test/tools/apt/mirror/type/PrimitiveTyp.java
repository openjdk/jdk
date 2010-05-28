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
 * @summary PrimitiveType tests
 * @library ../../lib
 * @compile -source 1.5 PrimitiveTyp.java
 * @run main/othervm PrimitiveTyp
 */


import java.util.*;
import com.sun.mirror.declaration.*;
import com.sun.mirror.type.*;
import com.sun.mirror.util.*;


public class PrimitiveTyp extends Tester {

    public static void main(String[] args) {
        (new PrimitiveTyp()).run();
    }


    // Declaration used by tests

    private boolean b;


    private PrimitiveType prim;         // a primitive type

    protected void init() {
        prim = (PrimitiveType) getField("b").getType();
    }


    // TypeMirror methods

    @Test(result="primitive")
    Collection<String> accept() {
        final Collection<String> res = new ArrayList<String>();

        prim.accept(new SimpleTypeVisitor() {
            public void visitTypeMirror(TypeMirror t) {
                res.add("type");
            }
            public void visitPrimitiveType(PrimitiveType t) {
                res.add("primitive");
            }
            public void visitReferenceType(ReferenceType t) {
                res.add("ref type");
            }
        });
        return res;
    }

    @Test(result="boolean")
    String toStringTest() {
        return prim.toString();
    }


    // PrimitiveType method

    @Test(result="BOOLEAN")
    PrimitiveType.Kind getKind() {
        return prim.getKind();
    }
}
