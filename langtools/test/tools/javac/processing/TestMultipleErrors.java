/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8066843
 * @summary Annotation processors should be able to print multiple errors at the same location.
 * @library /tools/javac/lib
 * @modules jdk.compiler
 * @build JavacTestingAbstractProcessor TestMultipleErrors
 * @compile/fail/ref=TestMultipleErrors.out -XDrawDiagnostics -processor TestMultipleErrors TestMultipleErrors.java
 */

import java.util.*;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.tools.Diagnostic.Kind;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

public class TestMultipleErrors extends JavacTestingAbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element root : roundEnv.getRootElements()) {
            processingEnv.getMessager().printMessage(Kind.ERROR, "error1", root);
            processingEnv.getMessager().printMessage(Kind.ERROR, "error2", root);

            Trees trees = Trees.instance(processingEnv);
            TreePath path = trees.getPath(root);

            trees.printMessage(Kind.ERROR, "error3", path.getLeaf(), path.getCompilationUnit());
            trees.printMessage(Kind.ERROR, "error4", path.getLeaf(), path.getCompilationUnit());
        }
        return true;
    }
}
