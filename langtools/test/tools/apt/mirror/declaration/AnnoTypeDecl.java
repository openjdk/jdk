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
 * @bug 4853450 5014539
 * @summary AnnotationTypeDeclaration tests
 * @library ../../lib
 * @compile -source 1.5 AnnoTypeDecl.java
 * @run main/othervm AnnoTypeDecl
 */


import java.util.*;
import com.sun.mirror.declaration.*;
import com.sun.mirror.type.*;
import com.sun.mirror.util.*;


public class AnnoTypeDecl extends Tester {

    public static void main(String[] args) {
        (new AnnoTypeDecl()).run();
    }


    private AnnotationTypeDeclaration at;

    protected void init() {
        at = (AnnotationTypeDeclaration) env.getTypeDeclaration("AT");
    }


    // Declaration methods

    @Test(result="annotation type")
    Collection<String> accept() {
        final Collection<String> res = new ArrayList<String>();

        at.accept(new SimpleDeclarationVisitor() {
            public void visitTypeDeclaration(TypeDeclaration t) {
                res.add("type");
            }
            public void visitClassDeclaration(ClassDeclaration c) {
                res.add("class");
            }
            public void visitInterfaceDeclaration(InterfaceDeclaration i) {
                res.add("interface");
            }
            public void visitAnnotationTypeDeclaration(
                                                AnnotationTypeDeclaration a) {
                res.add("annotation type");
            }
        });
        return res;
    }


    // AnnotationTypeDeclaration methods

    @Test(result={"s()"})
    Collection<AnnotationTypeElementDeclaration> getMethods() {
        return at.getMethods();
    }
}


// An annotation type to use for testing.

@interface AT {
    String s();
}
