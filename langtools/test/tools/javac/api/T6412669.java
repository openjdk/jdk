/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6412669
 * @summary Should be able to get SourcePositions from 269 world
 */

import java.io.*;
import java.util.*;
import javax.annotation.*;
import javax.annotation.processing.*;
import javax.lang.model.*;
import javax.lang.model.element.*;
import javax.tools.*;
import com.sun.source.util.*;
import com.sun.tools.javac.api.*;

@SupportedAnnotationTypes("*")
public class T6412669 extends AbstractProcessor {
    public static void main(String... args) throws IOException {
        String testSrc = System.getProperty("test.src", ".");
        String testClasses = System.getProperty("test.classes", ".");

        JavacTool tool = JavacTool.create();
        StandardJavaFileManager fm = tool.getStandardFileManager(null, null, null);
        fm.setLocation(StandardLocation.CLASS_PATH, Arrays.asList(new File(testClasses)));
        Iterable<? extends JavaFileObject> files =
            fm.getJavaFileObjectsFromFiles(Arrays.asList(new File(testSrc, T6412669.class.getName()+".java")));
        String[] opts = { "-proc:only", "-processor", T6412669.class.getName(),
                          "-classpath", new File(testClasses).getPath() };
        JavacTask task = tool.getTask(null, fm, null, Arrays.asList(opts), null, files);
        if (!task.call())
            throw new AssertionError("test failed");
    }

    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Trees trees = Trees.instance(processingEnv);
        SourcePositions sp = trees.getSourcePositions();
        Messager m = processingEnv.getMessager();
        for (TypeElement anno: annotations) {
            for (Element e: roundEnv.getElementsAnnotatedWith(anno)) {
                TreePath p = trees.getPath(e);
                long start = sp.getStartPosition(p.getCompilationUnit(), p.getLeaf());
                long end = sp.getEndPosition(p.getCompilationUnit(), p.getLeaf());
                Diagnostic.Kind k = (start > 0 && end > 0 && start < end
                                     ? Diagnostic.Kind.NOTE : Diagnostic.Kind.ERROR);
                m.printMessage(k, "test [" + start + "," + end + "]", e);
            }
        }
        return true;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }
}
