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
 * @bug 4853450 4997614
 * @summary ClassDeclaration tests
 * @library ../../lib
 * @compile -source 1.5 ClassDecl.java
 * @run main/othervm ClassDecl
 */


import java.io.Serializable;
import java.util.*;
import com.sun.mirror.declaration.*;
import com.sun.mirror.type.*;
import com.sun.mirror.util.*;


/**
 * Sed Quis custodiet ipsos custodes?
 */
@AT1
@AT2
public class ClassDecl extends Tester {

    public static void main(String[] args) {
        (new ClassDecl()).run();
    }


    private ClassDeclaration nested = null;     // a nested type
    private ClassDeclaration object = null;     // java.lang.object

    // A constructor to be found
    private ClassDecl() {
    }

    // Another constructor to be found
    private ClassDecl(int i) {
        this();
    }

    // An extra field to be found
    static int i;

    // Static initializer isn't among this class's methods.
    static {
        i = 7;
    }

    // A nested class with some accoutrements
    private static strictfp class NestedClass<T> implements Serializable {
        void m1() {}
        void m2() {}
        void m2(int i) {}
    }

    protected void init() {
        nested = (ClassDeclaration)
            thisClassDecl.getNestedTypes().iterator().next();
        object = (ClassDeclaration)
            env.getTypeDeclaration("java.lang.Object");
    }


    // Declaration methods

    @Test(result="class")
    Collection<String> accept() {
        final Collection<String> res = new ArrayList<String>();

        thisClassDecl.accept(new SimpleDeclarationVisitor() {
            public void visitTypeDeclaration(TypeDeclaration t) {
                res.add("type");
            }
            public void visitClassDeclaration(ClassDeclaration c) {
                res.add("class");
            }
            public void visitEnumDeclaration(EnumDeclaration e) {
                res.add("enum");
            }
        });
        return res;
    }

    @Test(result={"@AT1", "@AT2"})
    Collection<AnnotationMirror> getAnnotationMirrors() {
        return thisClassDecl.getAnnotationMirrors();
    }

    @Test(result=" Sed Quis custodiet ipsos custodes?\n")
    String getDocComment() {
        return thisClassDecl.getDocComment();
    }

    @Test(result={"public"})
    Collection<Modifier> getModifiers1() {
        return thisClassDecl.getModifiers();
    }

    // Check that static nested class has "static" modifier, even though
    // the VM doesn't set that bit.
    @Test(result={"private", "static", "strictfp"})
    Collection<Modifier> getModifiers2() {
        return nested.getModifiers();
    }

    @Test(result="ClassDecl.java")
    String getPosition() {
        return thisClassDecl.getPosition().file().getName();
    }

    @Test(result="ClassDecl")
    String getSimpleName1() {
        return thisClassDecl.getSimpleName();
    }

    @Test(result="NestedClass")
    String getSimpleName2() {
        return nested.getSimpleName();
    }


    // MemberDeclaration method

    @Test(result="null")
    TypeDeclaration getDeclaringType1() {
        return thisClassDecl.getDeclaringType();
    }

    @Test(result="ClassDecl")
    TypeDeclaration getDeclaringType2() {
        return nested.getDeclaringType();
    }


    // TypeDeclaration methods

    @Test(result={"nested", "object", "i"})
    Collection<FieldDeclaration> getFields() {
        return thisClassDecl.getFields();
    }

    @Test(result={})
    Collection<TypeParameterDeclaration> getFormalTypeParameters1() {
        return thisClassDecl.getFormalTypeParameters();
    }

    @Test(result="T")
    Collection<TypeParameterDeclaration> getFormalTypeParameters2() {
        return nested.getFormalTypeParameters();
    }

    @Test(result="ClassDecl.NestedClass<T>")
    Collection<TypeDeclaration> getNestedTypes() {
        return thisClassDecl.getNestedTypes();
    }

    @Test(result="")
    PackageDeclaration getPackage1() {
        return thisClassDecl.getPackage();
    }

    @Test(result="java.lang")
    PackageDeclaration getPackage2() {
        return object.getPackage();
    }

    @Test(result="ClassDecl")
    String getQualifiedName1() {
        return thisClassDecl.getQualifiedName();
    }

    @Test(result="ClassDecl.NestedClass")
    String getQualifiedName2() {
        return nested.getQualifiedName();
    }

    @Test(result="java.lang.Object")
    String getQualifiedName3() {
        return object.getQualifiedName();
    }

    @Test(result="java.io.Serializable")
    Collection<InterfaceType> getSuperinterfaces() {
        return nested.getSuperinterfaces();
    }


    // ClassDeclaration methods

    @Test(result={"ClassDecl()", "ClassDecl(int)"})
    Collection<ConstructorDeclaration> getConstructors1() {
        return thisClassDecl.getConstructors();
    }

    // Check for default constructor.
    // 4997614: visitConstructionDeclaration reports info when there is no ctor
    @Test(result={"NestedClass()"})
    Collection<ConstructorDeclaration> getConstructors2() {
        return nested.getConstructors();
    }

    @Test(result={"m1()", "m2()", "m2(int)"})
    Collection<MethodDeclaration> getMethods() {
        return nested.getMethods();
    }

    @Test(result={"Tester"})
    ClassType getSuperclass() {
        return thisClassDecl.getSuperclass();
    }

    @Test(result={"null"})
    ClassType objectHasNoSuperclass() {
        return object.getSuperclass();
    }
}


// Annotations used for testing.

@interface AT1 {
}

@interface AT2 {
}
