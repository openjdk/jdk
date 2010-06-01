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
 * @bug 4853450 4993303 5004618 5010746
 * @summary InterfaceDeclaration tests
 * @library ../../lib
 * @compile -source 1.5 InterfaceDecl.java
 * @run main/othervm InterfaceDecl
 */


import java.util.*;
import com.sun.mirror.declaration.*;
import com.sun.mirror.type.*;
import com.sun.mirror.util.*;


/**
 * Sed Quis custodiet ipsos custodes?
 */
@AT1
@AT2
public class InterfaceDecl extends Tester {

    public static void main(String[] args) {
        (new InterfaceDecl()).run();
    }


    private InterfaceDeclaration iDecl = null;          // an interface
    private InterfaceDeclaration nested = null;         // a nested interface

    protected void init() {
        iDecl = (InterfaceDeclaration) env.getTypeDeclaration("I");
        nested = (InterfaceDeclaration)
            iDecl.getNestedTypes().iterator().next();
    }


    // Declaration methods

    @Test(result="interface")
    Collection<String> accept() {
        final Collection<String> res = new ArrayList<String>();

        iDecl.accept(new SimpleDeclarationVisitor() {
            public void visitTypeDeclaration(TypeDeclaration t) {
                res.add("type");
            }
            public void visitClassDeclaration(ClassDeclaration c) {
                res.add("class");
            }
            public void visitInterfaceDeclaration(InterfaceDeclaration e) {
                res.add("interface");
            }
            public void visitAnnotationTypeDeclaration(
                                        AnnotationTypeDeclaration e) {
                res.add("annotation type");
            }
        });
        return res;
    }

    @Test(result="true")
    boolean equals1() {
        return iDecl.equals(iDecl);
    }

    @Test(result="false")
    boolean equals2() {
        return iDecl.equals(nested);
    }

    @Test(result="true")
    boolean equals3() {
        return iDecl.equals(env.getTypeDeclaration("I"));
    }


    @Test(result={"@AT1", "@AT2"})
    Collection<AnnotationMirror> getAnnotationMirrors() {
        return iDecl.getAnnotationMirrors();
    }

    @Test(result=" Sed Quis custodiet ipsos custodes?\n")
    String getDocComment() {
        return iDecl.getDocComment();
    }

    // Check that interface has "abstract" modifier, even though it's implict
    // in the source code.
    @Test(result={"abstract"})
    Collection<Modifier> getModifiers1() {
        return iDecl.getModifiers();
    }

    // Check that nested interface has "static" modifier, even though
    // it's implicit in the source code and the VM doesn't set that bit.
    @Test(result={"public", "abstract", "static"})
    Collection<Modifier> getModifiers2() {
        return nested.getModifiers();
    }

    @Test(result="InterfaceDecl.java")
    String getPosition() {
        return iDecl.getPosition().file().getName();
    }

    @Test(result="I")
    String getSimpleName1() {
        return iDecl.getSimpleName();
    }

    @Test(result="Nested")
    String getSimpleName2() {
        return nested.getSimpleName();
    }


    // MemberDeclaration method

    @Test(result="null")
    TypeDeclaration getDeclaringType1() {
        return iDecl.getDeclaringType();
    }

    @Test(result="I<T extends java.lang.Number>")
    TypeDeclaration getDeclaringType2() {
        return nested.getDeclaringType();
    }


    // TypeDeclaration methods

    @Test(result={"i"})
    Collection<FieldDeclaration> getFields() {
        return iDecl.getFields();
    }

    @Test(result={"T extends java.lang.Number"})
    Collection<TypeParameterDeclaration> getFormalTypeParameters1() {
        return iDecl.getFormalTypeParameters();
    }

    @Test(result={})
    Collection<TypeParameterDeclaration> getFormalTypeParameters2() {
        return nested.getFormalTypeParameters();
    }

    // 4993303: verify policy on Object methods being visible
    @Test(result={"m()", "toString()"})
    Collection<? extends MethodDeclaration> getMethods() {
        return nested.getMethods();
    }

    @Test(result="I.Nested")
    Collection<TypeDeclaration> getNestedTypes() {
        return iDecl.getNestedTypes();
    }

    @Test(result="")
    PackageDeclaration getPackage1() {
        return iDecl.getPackage();
    }

    @Test(result="java.util")
    PackageDeclaration getPackage2() {
        InterfaceDeclaration set =
            (InterfaceDeclaration) env.getTypeDeclaration("java.util.Set");
        return set.getPackage();
    }

    @Test(result="I")
    String getQualifiedName1() {
        return iDecl.getQualifiedName();
    }

    @Test(result="I.Nested")
    String getQualifiedName2() {
        return nested.getQualifiedName();
    }

    @Test(result="java.util.Set")
    String getQualifiedName3() {
        InterfaceDeclaration set =
            (InterfaceDeclaration) env.getTypeDeclaration("java.util.Set");
        return set.getQualifiedName();
    }

    @Test(result="java.lang.Runnable")
    Collection<InterfaceType> getSuperinterfaces() {
        return iDecl.getSuperinterfaces();
    }
}


// Interfaces used for testing.

/**
 * Sed Quis custodiet ipsos custodes?
 */
@AT1
@AT2
interface I<T extends Number> extends Runnable {
    int i = 6;
    void m1();
    void m2();
    void m2(int j);

    interface Nested {
        void m();
        String toString();
    }
}

@interface AT1 {
}

@interface AT2 {
}
