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
 * @bug 4853450 4993299
 * @summary ConstructorDeclaration tests
 * @library ../../lib
 * @compile -source 1.5 ConstructorDecl.java
 * @run main/othervm ConstructorDecl
 */


import java.util.*;
import com.sun.mirror.declaration.*;
import com.sun.mirror.type.*;
import com.sun.mirror.util.*;


public class ConstructorDecl extends Tester {

    /**
     * Sed Quis custodiet ipsos custodes?
     */
    @AT1
    public ConstructorDecl() {
    }


    public static void main(String[] args) {
        (new ConstructorDecl()).run();
    }


    private ConstructorDeclaration ctor = null;         // a constructor
    private ConstructorDeclaration ctorDef = null;      // a default c'tor
    private ConstructorDeclaration ctorInner = null;    // an inner class c'tor

    protected void init() {
        ctor = getAConstructor(thisClassDecl);
        ctorDef = getAConstructor((ClassDeclaration)
                                  env.getTypeDeclaration("C1"));
        ctorInner = getAConstructor((ClassDeclaration)
                                    env.getTypeDeclaration("C1.C2"));
    }

    // Return a constructor of a class.
    private ConstructorDeclaration getAConstructor(ClassDeclaration c) {
        return c.getConstructors().iterator().next();
    }


    // Declaration methods

    @Test(result="constructor")
    Collection<String> accept() {
        final Collection<String> res = new ArrayList<String>();

        ctor.accept(new SimpleDeclarationVisitor() {
            public void visitTypeDeclaration(TypeDeclaration t) {
                res.add("type");
            }
            public void visitExecutableDeclaration(ExecutableDeclaration e) {
                res.add("executable");
            }
            public void visitConstructorDeclaration(ConstructorDeclaration c) {
                res.add("constructor");
            }
        });
        return res;
    }

    @Test(result={"@AT1"})
    Collection<AnnotationMirror> getAnnotationMirrors() {
        return ctor.getAnnotationMirrors();
    }

    @Test(result=" Sed Quis custodiet ipsos custodes?\n")
    String getDocComment() {
        return ctor.getDocComment();
    }

    @Test(result={"public"})
    Collection<Modifier> getModifiers() {
        return ctor.getModifiers();
    }

    @Test(result="ConstructorDecl.java")
    String getPosition() {
        return ctor.getPosition().file().getName();
    }

    @Test(result="ConstructorDecl.java")
    String getPositionDefault() {
        return ctorDef.getPosition().file().getName();
    }

    @Test(result="ConstructorDecl")
    String getSimpleName() {
        return ctor.getSimpleName();
    }

    @Test(result="C2")
    String getSimpleNameInner() {
        return ctorInner.getSimpleName();
    }


    // MemberDeclaration method

    @Test(result="ConstructorDecl")
    TypeDeclaration getDeclaringType() {
        return ctor.getDeclaringType();
    }


    // ExecutableDeclaration methods

    @Test(result={})
    Collection<TypeParameterDeclaration> getFormalTypeParameters1() {
        return ctor.getFormalTypeParameters();
    }

    @Test(result={"N extends java.lang.Number"})
    Collection<TypeParameterDeclaration> getFormalTypeParameters2() {
        return ctorInner.getFormalTypeParameters();
    }

    @Test(result={})
    Collection<ParameterDeclaration> getParameters1() {
        return ctor.getParameters();
    }

    // 4993299: verify synthetic parameters to inner class constructors
    //          aren't visible
    @Test(result={"N n1", "N n2", "java.lang.String[] ss"},
          ordered=true)
    Collection<ParameterDeclaration> getParameters2() {
        return ctorInner.getParameters();
    }

    @Test(result={"java.lang.Throwable"})
    Collection<ReferenceType> getThrownTypes() {
        return ctorInner.getThrownTypes();
    }

    @Test(result="false")
    Boolean isVarArgs1() {
        return ctor.isVarArgs();
    }

    @Test(result="true")
    Boolean isVarArgs2() {
        return ctorInner.isVarArgs();
    }


    // toString

    @Test(result="<N extends java.lang.Number> C2(N, N, String...)")
    @Ignore("This is what it would be nice to see.")
    String toStringTest() {
        return ctorInner.toString();
    }
}


// Classes and interfaces used for testing.

class C1 {
    class C2 {
        <N extends Number> C2(N n1, N n2, String... ss) throws Throwable {
        }
    }
}

@interface AT1 {
}
