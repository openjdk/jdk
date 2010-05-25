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

public class PhantomTouch implements AnnotationProcessorFactory {
    static class PhantomTouchProc implements AnnotationProcessor {
        AnnotationProcessorEnvironment ape;
        PhantomTouchProc(AnnotationProcessorEnvironment ape) {
            this.ape = ape;
        }

        public void process() {
            // Only run the processor on the initial apt invocation
            if (ape.getSpecifiedTypeDeclarations().size() == 0) {
                boolean result;
                try {
                    // Create temporary file
                    java.io.File f = new java.io.File("touched");
                    result = f.createNewFile();

                    if (result) {
                        // Create new source file
                        PrintWriter pw = ape.getFiler().createSourceFile("HelloWorld");
                        pw.println("public class HelloWorld {");
                        pw.println("  // Phantom hello world");
                        pw.println("  public static void main(String argv[]) {");
                        pw.println("    System.out.println(\"Hello World\");");
                        pw.println("  }");
                        pw.println("}");
                    } else
                        throw new RuntimeException("touched file already exists!");
                } catch (java.io.IOException e) {
                    result = false;
                }
            }
        }
    }

    static final Collection<String> supportedOptions;
    static final Collection<String> supportedTypes;

    static {
        String options[] = {""};
        supportedOptions = Collections.unmodifiableCollection(Arrays.asList(options));

        String types[] = {"*"};
        supportedTypes = Collections.unmodifiableCollection(Arrays.asList(types));
    }

    public Collection<String> supportedAnnotationTypes() {return supportedTypes;}
    public Collection<String> supportedOptions() {return supportedOptions;}


    /*
     * Return the same processor independent of what annotations are
     * present, if any.
     */
    public AnnotationProcessor getProcessorFor(Set<AnnotationTypeDeclaration> atds,
                                        AnnotationProcessorEnvironment env) {
        return new PhantomTouchProc(env);
    }
}
