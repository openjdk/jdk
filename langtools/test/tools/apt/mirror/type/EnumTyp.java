/*
 * Copyright 2004-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */


/*
 * @test
 * @bug 4853450
 * @summary EnumType tests
 * @library ../../lib
 * @compile -source 1.5 EnumTyp.java
 * @run main/othervm EnumTyp
 */


import java.util.*;
import com.sun.mirror.declaration.*;
import com.sun.mirror.type.*;
import com.sun.mirror.util.*;


public class EnumTyp extends Tester {

    public static void main(String[] args) {
        (new EnumTyp()).run();
    }


    // Declarations used by tests

    enum Suit {
        CIVIL,
        CRIMINAL
    }

    private Suit s;


    private EnumType e;         // an enum type

    protected void init() {
        e = (EnumType) getField("s").getType();
    }


    // TypeMirror methods

    @Test(result="enum")
    Collection<String> accept() {
        final Collection<String> res = new ArrayList<String>();

        e.accept(new SimpleTypeVisitor() {
            public void visitTypeMirror(TypeMirror t) {
                res.add("type");
            }
            public void visitReferenceType(ReferenceType t) {
                res.add("ref type");
            }
            public void visitClassType(ClassType t) {
                res.add("class");
            }
            public void visitEnumType(EnumType t) {
                res.add("enum");
            }
            public void visitInterfaceType(InterfaceType t) {
                res.add("interface");
            }
        });
        return res;
    }


    // EnumType method

    @Test(result="EnumTyp.Suit")
    EnumDeclaration getDeclaration() {
        return e.getDeclaration();
    }
}
