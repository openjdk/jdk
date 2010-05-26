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
import java.util.Map;
import java.util.Arrays;
import java.util.Collections;

public class OptionChecker implements AnnotationProcessorFactory {
    static class OptionCheck implements AnnotationProcessor {
        AnnotationProcessorEnvironment ape;
        OptionCheck(AnnotationProcessorEnvironment ape) {
            this.ape = ape;
        }

        public void process() {
            Map<String, String> options = ape.getOptions();
            if (options.containsKey("-Afoo") &&
                options.containsKey("-Abar") &&
                options.containsKey("-classpath") ) {
                System.out.println("Expected options found.");
                return;  // All is well
            } else {
                System.err.println("Unexpected options values: " + options);
                throw new RuntimeException();
            }
        }
    }

    static class HelloWorld implements AnnotationProcessor {
        AnnotationProcessorEnvironment ape;
        HelloWorld(AnnotationProcessorEnvironment ape) {
            this.ape = ape;
        }

        public void process() {
            java.io.PrintWriter pw;
            try {
                pw = ape.getFiler().createSourceFile("HelloWorld");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            pw.println("public class HelloWorld {");
            pw.println("  public static void main (String argv[]) {");
            pw.println("    System.out.println(\"Hello apt world.\");");
            pw.println("  }");
            pw.println("}");
        }
    }


    static Collection<String> supportedTypes;
    static {
        String types[] = {"*"};
        supportedTypes = Collections.unmodifiableCollection(Arrays.asList(types));
    }

    static Collection<String> supportedOptions;
    static {
        String options[] = {"-Afoo", "-Abar"};
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
                                               AnnotationProcessorEnvironment ape) {

        if (atds.contains(ape.getTypeDeclaration("Marker"))) {
            System.out.println("Returning composite processor.");
            return AnnotationProcessors.getCompositeAnnotationProcessor(new OptionCheck(ape),
                                                                        new HelloWorld(ape));
        }
        else {
            System.out.println("Returning single processor.");
            return new OptionCheck(ape);
        }
    }
}
