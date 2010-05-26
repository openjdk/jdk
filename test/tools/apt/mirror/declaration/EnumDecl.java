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
 * @bug 4853450 4989987 5010050
 * @summary EnumDeclaration tests
 * @library ../../lib
 * @compile -source 1.5 EnumDecl.java
 * @run main/othervm EnumDecl
 */


import java.util.*;
import com.sun.mirror.declaration.*;
import com.sun.mirror.type.*;
import com.sun.mirror.util.*;


public class EnumDecl extends Tester {

    public static void main(String[] args) {
        (new EnumDecl()).run();
    }


    private EnumDeclaration eDecl;

    protected void init() {
        eDecl = (EnumDeclaration) env.getTypeDeclaration("E");
    }


    // Declaration methods

    @Test(result="enum")
    Collection<String> accept() {
        final Collection<String> res = new ArrayList<String>();

        eDecl.accept(new SimpleDeclarationVisitor() {
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


    // ClassDeclaration methods

    // 4989987: Verify synthetic enum constructor parameters are not visible
    @Test(result={"E(java.lang.String)"})
    Collection<ConstructorDeclaration> getConstructors() {
        return eDecl.getConstructors();
    }

    // 4989987: Verify synthetic enum constructor parameters are not visible
    @Test(result={"java.lang.String color"})
    Collection<ParameterDeclaration> getConstructorParams() {
        return eDecl.getConstructors().iterator().next().getParameters();
    }

    @Test(result={"values()", "valueOf(java.lang.String)"})
    Collection<MethodDeclaration> getMethods() {
        return eDecl.getMethods();
    }

    // 5010050: Cannot find parameter names for valueOf(String name) method...
    @Test(result={"java.lang.String name"})
    Collection<ParameterDeclaration> getMethodParams() {
        for (MethodDeclaration m : eDecl.getMethods()) {
            if (m.getSimpleName().equals("valueOf")) {
                return m.getParameters();
            }
        }
        throw new AssertionError();
    }


    // EnumDeclaration methods

    @Test(result={"stop", "slow", "go"})
    Collection<EnumConstantDeclaration> getEnumConstants() {
        return eDecl.getEnumConstants();
    }
}


// An enum to use for testing.

enum E {
    stop("red"),
    slow("amber"),
    go("green");

    private String color;
    E(String color) {
        this.color = color;
    }
}
