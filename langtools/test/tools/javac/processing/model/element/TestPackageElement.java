/*
 * Copyright (c) 2006, 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6449798 6399404
 * @summary Test basic workings of PackageElement
 * @author  Joseph D. Darcy
 * @library ../../../lib
 * @build   JavacTestingAbstractProcessor TestPackageElement
 * @compile -processor TestPackageElement -proc:only TestPackageElement.java
 */

import java.util.Set;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import static javax.lang.model.SourceVersion.*;
import javax.lang.model.element.*;
import javax.lang.model.util.*;
import static javax.lang.model.util.ElementFilter.*;
import static javax.tools.Diagnostic.Kind.*;
import static javax.tools.StandardLocation.*;

/**
 * Test basic workings of PackageElement.
 */
public class TestPackageElement extends JavacTestingAbstractProcessor {
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            PackageElement unnamedPkg = eltUtils.getPackageElement("");

            if (!unnamedPkg.getQualifiedName().contentEquals(""))
                throw new RuntimeException("The unnamed package is named!");

            // The next line tests an implementation detail upon which
            // some diagnostics depend.
            if (!unnamedPkg.toString().equals("unnamed package"))
                throw new RuntimeException(
                                "toString on unnamed package: " + unnamedPkg);

            if (!unnamedPkg.isUnnamed())
                throw new RuntimeException("The isUnnamed method on the unnamed package returned false!");

            PackageElement javaLang = eltUtils.getPackageElement("java.lang");
            if (javaLang.isUnnamed())
                throw new RuntimeException("Package java.lang is unnamed!");
        }
        return true;
    }
}
