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


public class Scanner implements AnnotationProcessorFactory {
    static class ScannerProc implements AnnotationProcessor {
        AnnotationProcessorEnvironment env;
        ScannerProc(AnnotationProcessorEnvironment env) {
            this.env = env;
        }

        static class CountingVisitor extends SimpleDeclarationVisitor {
            int count;
            CountingVisitor() {
                count = 0;
            }

            public void visitDeclaration(Declaration d) {
                count++;

                Collection<AnnotationMirror> ams = d.getAnnotationMirrors();
                if (ams == null)
                    throw new RuntimeException("Declaration " + d +
                                               " not annotated with visit order.");
                else {
                    if (ams.size() != 1)
                        throw new RuntimeException("Declaration " + d +
                                                   " has wrong number of declarations.");
                    else {
                        for(AnnotationMirror am: ams) {
                            Map<AnnotationTypeElementDeclaration,AnnotationValue> elementValues = am.getElementValues();
                            for(AnnotationTypeElementDeclaration atmd: elementValues.keySet()) {
                                if (!atmd.getDeclaringType().toString().equals("VisitOrder"))
                                    throw new RuntimeException("Annotation " + atmd +
                                                               " is the wrong type.");
                                else {
                                    AnnotationValue av =
                                        elementValues.get(atmd);
                                    Integer value = (Integer) av.getValue();
                                    if (value.intValue() != count)
                                        throw new RuntimeException("Expected declaration " + d +
                                                                   " to be in position " + count +
                                                                   " instead of " + value.intValue());

                                    System.out.println("Declaration " + d +
                                                       ": visit order " + value.intValue());
                                }
                            }

                        }
                    }
                }

            }
        }

        public void process() {
            for(TypeDeclaration td: env.getSpecifiedTypeDeclarations() ) {
                td.accept(DeclarationVisitors.getSourceOrderDeclarationScanner(new CountingVisitor(),
                                                                               DeclarationVisitors.NO_OP));
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

    public AnnotationProcessor getProcessorFor(Set<AnnotationTypeDeclaration> atds,
                                        AnnotationProcessorEnvironment env) {
        return new ScannerProc(env);
    }

}
