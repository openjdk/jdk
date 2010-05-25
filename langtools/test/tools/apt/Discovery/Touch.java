/*
 * Copyright (c) 2004, 2007, Oracle and/or its affiliates. All rights reserved.
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


import com.sun.mirror.apt.*;
import com.sun.mirror.declaration.*;
import com.sun.mirror.type.*;
import com.sun.mirror.util.*;

import java.util.Collection;
import java.util.Set;
import java.util.Arrays;
import java.util.Collections;
import java.io.*;

public class Touch implements AnnotationProcessorFactory {
    static class TouchProc implements AnnotationProcessor {
        AnnotationProcessorEnvironment ape;
        TouchProc(AnnotationProcessorEnvironment ape) {
            this.ape = ape;
        }

        public void process() {
            boolean result;
            // Only run the processor on the initial apt invocation
            Collection<TypeDeclaration> tdecls = ape.getSpecifiedTypeDeclarations();

            if (tdecls.size() == 1) {
                for(TypeDeclaration decl: tdecls) {
                    if (! decl.getSimpleName().equals("Touch") )
                        return;
                }

                try {
                    // Create temporary file
                    java.io.File f = new java.io.File("touched");
                    result = f.createNewFile();


                    Filer filer = ape.getFiler();

                    // Create new source file
                    PrintWriter pw = filer.createSourceFile("HelloWorld");
                    pw.println("public class HelloWorld {");
                    pw.println("  public static void main(String argv[]) {");
                    pw.println("    System.out.println(\"Hello World\");");
                    pw.println("  }");
                    pw.println("}");

                    // Create new class file and copy Empty.class
                    OutputStream os = filer.createClassFile("Empty");
                    FileInputStream fis = new FileInputStream("Empty.clazz");
                    int datum;
                    while((datum = fis.read()) != -1)
                        os.write(datum);

                } catch (java.io.IOException e) {
                    result = false;
                }
                if (!result)
                    throw new RuntimeException("touched file already exists or other error");
            }

        }

    }

    static Collection<String> supportedTypes;
    static {
        String types[] = {"*"};
        supportedTypes = Collections.unmodifiableCollection(Arrays.asList(types));
    }

    static Collection<String> supportedOptions;
    static {
        String options[] = {""};
        supportedOptions = Collections.unmodifiableCollection(Arrays.asList(options));
    }

    public Collection<String> supportedOptions() {
        return supportedOptions;
    }

    public Collection<String> supportedAnnotationTypes() {
        return supportedTypes;
    }

    /*
     * Return the same processor independent of what annotations are
     * present, if any.
     */
    public AnnotationProcessor getProcessorFor(Set<AnnotationTypeDeclaration> atds,
                                        AnnotationProcessorEnvironment env) {
        return new TouchProc(env);
    }
}
