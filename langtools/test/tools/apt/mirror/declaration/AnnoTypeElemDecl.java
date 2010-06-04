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
 * @bug 4853450 4993299 5010385 5014539
 * @summary AnnotationTypeElementDeclaration tests
 * @library ../../lib
 * @compile -source 1.5 AnnoTypeElemDecl.java
 * @run main/othervm AnnoTypeElemDecl
 */


import java.util.*;
import com.sun.mirror.declaration.*;
import com.sun.mirror.type.*;
import com.sun.mirror.util.*;


public class AnnoTypeElemDecl extends Tester {

    public static void main(String[] args) {
        (new AnnoTypeElemDecl()).run();
    }


    // Some annotation type elements to use.
    private AnnotationTypeElementDeclaration elem1 = null;      // s()
    private AnnotationTypeElementDeclaration elem2 = null;      // i()
    private AnnotationTypeElementDeclaration elem3 = null;      // b()

    protected void init() {
        for (TypeDeclaration at : thisClassDecl.getNestedTypes()) {
            for (MethodDeclaration meth : at.getMethods()) {
                AnnotationTypeElementDeclaration elem =
                    (AnnotationTypeElementDeclaration) meth;
                if (elem.getSimpleName().equals("s")) {
                    elem1 = elem;       // s()
                } else if (elem.getSimpleName().equals("i")) {
                    elem2 = elem;       // i()
                } else {
                    elem3 = elem;       // b()
                }
            }
        }
    }


    // Declaration methods

    @Test(result="anno type element")
    Collection<String> accept() {
        final Collection<String> res = new ArrayList<String>();

        elem1.accept(new SimpleDeclarationVisitor() {
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

    @Test(result={"@AnnoTypeElemDecl.AT2"})
    Collection<AnnotationMirror> getAnnotationMirrors() {
        return elem1.getAnnotationMirrors();
    }

    @Test(result=" Sed Quis custodiet ipsos custodes?\n")
    String getDocComment() {
        return elem1.getDocComment();
    }

    @Test(result={"public", "abstract"})
    Collection<Modifier> getModifiers() {
        return elem1.getModifiers();
    }

    @Test(result="AnnoTypeElemDecl.java")
    String getPosition() {
        return elem1.getPosition().file().getName();
    }

    @Test(result="s")
    String getSimpleName() {
        return elem1.getSimpleName();
    }


    // MemberDeclaration method

    @Test(result="AnnoTypeElemDecl.AT1")
    TypeDeclaration getDeclaringType() {
        return elem1.getDeclaringType();
    }


    // ExecutableDeclaration methods

    @Test(result={})
    Collection<TypeParameterDeclaration> getFormalTypeParameters() {
        return elem1.getFormalTypeParameters();
    }

    @Test(result={})
    Collection<ParameterDeclaration> getParameters() {
        return elem1.getParameters();
    }

    @Test(result={})
    Collection<ReferenceType> getThrownTypes() {
        return elem1.getThrownTypes();
    }

    @Test(result="false")
    Boolean isVarArgs() {
        return elem1.isVarArgs();
    }


    // AnnotationTypeElementDeclaration method

    @Test(result="\"default\"")
    AnnotationValue getDefaultValue1() {
        return elem1.getDefaultValue();
    }

    @Test(result="null")
    AnnotationValue getDefaultValue2() {
        return elem2.getDefaultValue();
    }

    // 5010385: getValue() returns null for boolean type elements
    @Test(result="false")
    Boolean getDefaultValue3() {
        return (Boolean) elem3.getDefaultValue().getValue();
    }


    // toString

    @Test(result="s()")
    String toStringTest() {
        return elem1.toString();
    }


    // Declarations used by tests.

    @interface AT1 {
        /**
         * Sed Quis custodiet ipsos custodes?
         */
        @AT2
        String s() default "default";

        int i();

        boolean b() default false;
    }

    @interface AT2 {
    }
}
