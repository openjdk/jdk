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
 * @bug 4853450 5010746
 * @summary MethodDeclaration tests
 * @library ../../lib
 * @compile -source 1.5 MethodDecl.java
 * @run main/othervm MethodDecl
 */


import java.util.*;
import com.sun.mirror.declaration.*;
import com.sun.mirror.type.*;
import com.sun.mirror.util.*;


public class MethodDecl extends Tester {

    public static void main(String[] args) {
        (new MethodDecl()).run();
    }


    private MethodDeclaration meth1 = null;             // a method
    private MethodDeclaration meth2 = null;             // another method

    protected void init() {
        meth1 = getMethod("m1");
        meth2 = getMethod("m2");
    }


    // Declaration methods

    @Test(result="method")
    Collection<String> accept() {
        final Collection<String> res = new ArrayList<String>();

        meth1.accept(new SimpleDeclarationVisitor() {
            public void visitTypeDeclaration(TypeDeclaration t) {
                res.add("type");
            }
            public void visitExecutableDeclaration(ExecutableDeclaration e) {
                res.add("executable");
            }
            public void visitMethodDeclaration(MethodDeclaration m) {
                res.add("method");
            }
            public void visitAnnotationTypeElementDeclaration(
                                        AnnotationTypeElementDeclaration a) {
                res.add("anno type element");
            }
        });
        return res;
    }

    @Test(result={"@AT1"})
    Collection<AnnotationMirror> getAnnotationMirrors() {
        return meth1.getAnnotationMirrors();
    }

    @Test(result=" Sed Quis custodiet ipsos custodes?\n")
    String getDocComment() {
        return meth1.getDocComment();
    }

    @Test(result={"private", "static", "strictfp"})
    Collection<Modifier> getModifiers() {
        return meth1.getModifiers();
    }

    // Interface methods are implicitly public and abstract.
    @Test(result={"public", "abstract"})
    Collection<Modifier> getModifiersInterface() {
        for (TypeDeclaration t : thisClassDecl.getNestedTypes()) {
            for (MethodDeclaration m : t.getMethods()) {
                return m.getModifiers();
            }
        }
        throw new AssertionError();
    }

    @Test(result="MethodDecl.java")
    String getPosition() {
        return meth1.getPosition().file().getName();
    }

    @Test(result="m2")
    String getSimpleName() {
        return meth2.getSimpleName();
    }


    // MemberDeclaration method

    @Test(result="MethodDecl")
    TypeDeclaration getDeclaringType() {
        return meth1.getDeclaringType();
    }


    // ExecutableDeclaration methods

    @Test(result={})
    Collection<TypeParameterDeclaration> getFormalTypeParameters1() {
        return meth1.getFormalTypeParameters();
    }

    @Test(result={"T", "N extends java.lang.Number"},
          ordered=true)
    Collection<TypeParameterDeclaration> getFormalTypeParameters2() {
        return meth2.getFormalTypeParameters();
    }

    @Test(result={})
    Collection<ParameterDeclaration> getParameters1() {
        return meth1.getParameters();
    }

    @Test(result={"N n", "java.lang.String[] ss"},
          ordered=true)
    Collection<ParameterDeclaration> getParameters2() {
        return meth2.getParameters();
    }

    @Test(result="true")
    boolean parameterEquals1() {
        ParameterDeclaration p1 =
            getMethod("m3").getParameters().iterator().next();
        ParameterDeclaration p2 =
            getMethod("m3").getParameters().iterator().next();
        return p1.equals(p2);
    }

    @Test(result="false")
    boolean parameterEquals2() {
        ParameterDeclaration p1 =
            getMethod("m3").getParameters().iterator().next();
        ParameterDeclaration p2 =
            getMethod("m4").getParameters().iterator().next();
        return p1.equals(p2);
    }

    @Test(result="true")
    boolean parameterHashCode() {
        ParameterDeclaration p1 =
            getMethod("m3").getParameters().iterator().next();
        ParameterDeclaration p2 =
            getMethod("m3").getParameters().iterator().next();
        return p1.hashCode() == p2.hashCode();
    }

    @Test(result={"java.lang.Throwable"})
    Collection<ReferenceType> getThrownTypes() {
        return meth2.getThrownTypes();
    }

    @Test(result="false")
    Boolean isVarArgs1() {
        return meth1.isVarArgs();
    }

    @Test(result="true")
    Boolean isVarArgs2() {
        return meth2.isVarArgs();
    }


    // MethodDeclaration methods

    @Test(result="void")
    TypeMirror getReturnType1() {
        return meth1.getReturnType();
    }

    @Test(result="N")
    TypeMirror getReturnType2() {
        return meth2.getReturnType();
    }


    // toString

    @Test(result="<T, N extends java.lang.Number> m2(N, java.lang.String...)")
    @Ignore("This is what it would be nice to see.")
    String toStringTest() {
        return meth2.toString();
    }


    // Declarations used by tests.

    /**
     * Sed Quis custodiet ipsos custodes?
     */
    @AT1
    private static strictfp void m1() {
    }

    private <T, N extends Number> N m2(N n, String... ss) throws Throwable {
        return null;
    }

    private void m3(String s) {
    }

    private void m4(String s) {
    }

    // A nested interface
    interface I {
        void m();
    }
}


// Annotation type used by tests.

@interface AT1 {
}
