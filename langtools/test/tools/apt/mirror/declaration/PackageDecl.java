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
 * @bug 4853450 5031168
 * @summary PackageDeclaration tests
 * @library ../../lib
 * @compile -source 1.5 PackageDecl.java
 * @run main/othervm PackageDecl
 */


import java.io.File;
import java.util.*;
import com.sun.mirror.declaration.*;
import com.sun.mirror.type.*;
import com.sun.mirror.util.*;

import pkg1.pkg2.*;


/**
 * Sed Quis custodiet ipsos custodes?
 */
public class PackageDecl extends Tester {

    public PackageDecl() {
        super(System.getProperty("test.src", ".") + File.separator +
              "pkg1" + File.separator + "package-info.java");
    }

    public static void main(String[] args) {
        (new PackageDecl()).run();
    }


    private PackageDeclaration pkg1 = null;             // a package
    private PackageDeclaration pkg2 = null;             // a subpackage

    protected void init() {
        pkg1 = env.getPackage("pkg1");
        pkg2 = env.getPackage("pkg1.pkg2");
    }


    // Declaration methods

    @Test(result="package")
    Collection<String> accept() {
        final Collection<String> res = new ArrayList<String>();

        pkg1.accept(new SimpleDeclarationVisitor() {
            public void visitTypeDeclaration(TypeDeclaration t) {
                res.add("type");
            }
            public void visitPackageDeclaration(PackageDeclaration p) {
                res.add("package");
            }
        });
        return res;
    }

    @Test(result={"@pkg1.AnAnnoType"})
    Collection<AnnotationMirror> getAnnotationMirrors() {
        return pkg1.getAnnotationMirrors();
    }

    @Test(result=" Herein lieth the package comment.\n" +
                 " A doc comment it be, and wonderous to behold.\n")
    String getDocCommentFromPackageInfoFile() {
        return pkg1.getDocComment();
    }

    @Test(result="\nHerein lieth the package comment.\n" +
                 "An HTML file it be, and wonderous to behold.\n\n")
    @Ignore("Not yet supported")
    String getDocCommentFromHtmlFile() {
        return pkg2.getDocComment();
    }

    @Test(result={})
    Collection<Modifier> getModifiers() {
        return pkg1.getModifiers();
    }

    @Test(result="null")
    SourcePosition getPosition() {
        return thisClassDecl.getPackage().getPosition();
    }

    @Test(result="package-info.java")
    String getPositionFromPackageInfoFile() {
        return pkg1.getPosition().file().getName();
    }

    @Test(result="pkg1/pkg2/package.html")
    @Ignore("Not yet supported")
    String getPositionFromHtmlFile() {
        return pkg2.getPosition().file().getName()
                                            .replace(File.separatorChar, '/');
    }

    @Test(result="pkg1")
    String getSimpleName1() {
        return pkg1.getSimpleName();
    }

    @Test(result="pkg2")
    String getSimpleName2() {
        return pkg2.getSimpleName();
    }


    // PackageDeclaration methods

    @Test(result="pkg1.AnAnnoType")
    Collection<AnnotationTypeDeclaration> getAnnotationTypes() {
        return pkg1.getAnnotationTypes();
    }

    @Test(result={"pkg1.AClass", "pkg1.AnEnum"})
    Collection<ClassDeclaration> getClasses() {
        return pkg1.getClasses();
    }

    @Test(result="pkg1.AnEnum")
    Collection<EnumDeclaration> getEnums() {
        return pkg1.getEnums();
    }

    @Test(result={"pkg1.AnInterface", "pkg1.AnAnnoType"})
    Collection<InterfaceDeclaration> getInterfaces() {
        return pkg1.getInterfaces();
    }

    @Test(result="pkg1")
    String getQualifiedName1() {
        return pkg1.getQualifiedName();
    }

    @Test(result="pkg1.pkg2")
    String getQualifiedName2() {
        return pkg2.getQualifiedName();
    }
}
