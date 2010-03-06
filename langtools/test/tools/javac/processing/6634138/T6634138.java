/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 6634138
 * @author  Joseph D. Darcy
 * @summary Verify source files output after processing is over are compiled
 * @compile T6634138.java
 * @compile -processor T6634138 Dummy.java
 * @run main ExerciseDependency
 */

import java.lang.annotation.Annotation;
import java.io.*;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.*;

@SupportedAnnotationTypes("*")
public class T6634138 extends AbstractProcessor {
    private Filer filer;

    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnvironment) {
        // Write out files *after* processing is over.
        if (roundEnvironment.processingOver()) {
            System.out.println("Writing out source files.");
            try {
                PrintWriter pw = new PrintWriter(filer.createSourceFile("foo.WrittenAfterProcessing").openWriter());
                try {
                     pw.println("package foo;");
                     pw.println("public class WrittenAfterProcessing {");
                     pw.println("  public WrittenAfterProcessing() {super();}");
                     pw.println("}");
                 } finally {
                     pw.close();
                 }

                pw = new PrintWriter(filer.createSourceFile("foo.package-info").openWriter());
                try {
                     pw.println("@Deprecated");
                     pw.println("package foo;");
                 } finally {
                     pw.close();
                 }
            } catch(IOException io) {
                throw new RuntimeException(io);
            }
        }
        return true;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    public void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        filer    = processingEnv.getFiler();
    }
}



