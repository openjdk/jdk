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
 * @bug 4853450 5031171
 * @summary ParameterDeclaration tests
 * @library ../../lib
 * @run main/othervm ParameterDecl
 */


import java.util.*;
import com.sun.mirror.declaration.*;
import com.sun.mirror.type.*;
import com.sun.mirror.util.*;


public class ParameterDecl extends Tester {

    public static void main(String[] args) {
        (new ParameterDecl()).run();
    }


    // Declarations used by tests

    @interface AT1 {
    }

    @interface AT2 {
        boolean value();
    }

    private void m1(@AT1 @AT2(true) final int p1) {
    }

    private void m2(int p1) {
    }


    private ParameterDeclaration p1 = null;     // a parameter

    protected void init() {
        p1 = getMethod("m1").getParameters().iterator().next();
    }


    // Declaration methods

    @Test(result="param")
    Collection<String> accept() {
        final Collection<String> res = new ArrayList<String>();

        p1.accept(new SimpleDeclarationVisitor() {
            public void visitFieldDeclaration(FieldDeclaration f) {
                res.add("field");
            }
            public void visitParameterDeclaration(ParameterDeclaration p) {
                res.add("param");
            }
        });
        return res;
    }

    @Test(result={"@ParameterDecl.AT1", "@ParameterDecl.AT2(true)"})
    Collection<AnnotationMirror> getAnnotationMirrors() {
        return p1.getAnnotationMirrors();
    }

    @Test(result={"final"})
    Collection<Modifier> getModifiers() {
        return p1.getModifiers();
    }

    @Test(result="ParameterDecl.java")
    String getPosition() {
        return p1.getPosition().file().getName();
    }

    @Test(result="p1")
    String getSimpleName() {
        return p1.getSimpleName();
    }


    // ParameterDeclaration methods

    @Test(result="int")
    TypeMirror getType() {
        return p1.getType();
    }


    // toString, equals

    @Test(result="int p1")
    String toStringTest() {
        return p1.toString();
    }

    @Test(result="true")
    boolean equalsTest1() {
        ParameterDeclaration p =
            getMethod("m1").getParameters().iterator().next();
        return p1.equals(p);
    }

    // Not all p1's are equal.
    @Test(result="false")
    boolean equalsTest2() {
        ParameterDeclaration p2 =
            getMethod("m2").getParameters().iterator().next();
        return p1.equals(p2);
    }
}
