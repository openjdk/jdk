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
 * @bug 4853450 5008309
 * @summary FieldDeclaration tests
 * @library ../../lib
 * @compile -source 1.5 FieldDecl.java
 * @run main/othervm FieldDecl
 */


import java.util.*;
import com.sun.mirror.declaration.*;
import com.sun.mirror.type.*;
import com.sun.mirror.util.*;


public class FieldDecl extends Tester {

    public static void main(String[] args) {
        (new FieldDecl()).run();
    }


    private FieldDeclaration f1 = null;         // a field
    private FieldDeclaration f2 = null;         // a static field
    private FieldDeclaration f3 = null;         // a constant field

    protected void init() {
        f1 = getField("aField");
        f2 = getField("aStaticField");
        f3 = getField("aConstantField");
    }


    // Declaration methods

    @Test(result="field")
    Collection<String> accept() {
        final Collection<String> res = new ArrayList<String>();

        f1.accept(new SimpleDeclarationVisitor() {
            public void visitTypeDeclaration(TypeDeclaration t) {
                res.add("type");
            }
            public void visitFieldDeclaration(FieldDeclaration f) {
                res.add("field");
            }
            public void visitEnumConstantDeclaration(
                                                EnumConstantDeclaration e) {
                res.add("enum const");
            }
        });
        return res;
    }

    @Test(result={"@FieldDecl.AT1"})
    Collection<AnnotationMirror> getAnnotationMirrors() {
        return f1.getAnnotationMirrors();
    }

    @Test(result=" Sed Quis custodiet ipsos custodes?\n")
    String getDocComment() {
        return f1.getDocComment();
    }

    @Test(result={"public"})
    Collection<Modifier> getModifiers() {
        return f1.getModifiers();
    }

    @Test(result="FieldDecl.java")
    String getPosition() {
        return f1.getPosition().file().getName();
    }

    @Test(result="aField")
    String getSimpleName() {
        return f1.getSimpleName();
    }


    // MemberDeclaration method

    @Test(result="FieldDecl")
    TypeDeclaration getDeclaringType() {
        return f1.getDeclaringType();
    }


    // FieldDeclaration methods

    @Test(result="java.util.List<java.lang.String>")
    TypeMirror getType1() {
        return f1.getType();
    }

    @Test(result="int")
    TypeMirror getType2() {
        return f2.getType();
    }

    @Test(result="null")
    Object getConstantValue1() {
        return f1.getConstantValue();
    }

    // 5008309: FieldDeclaration.getConstantValue() doesn't return anything
    @Test(result="true")
    Object getConstantValue2() {
        return f3.getConstantValue();
    }


    // toString

    @Test(result="aField")
    String toStringTest() {
        return f1.toString();
    }


    // Declarations used by tests.

    /**
     * Sed Quis custodiet ipsos custodes?
     */
    @AT1
    public List<String> aField = new ArrayList<String>();

    static int aStaticField;

    public static final boolean aConstantField = true;


    @interface AT1 {
    }
}
