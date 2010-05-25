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

import static java.util.Collections.*;
import static com.sun.mirror.util.DeclarationVisitors.*;

/*
 * Indirect test of whether source or class files are used to provide
 * declaration information.
 */
public class FreshnessApf implements AnnotationProcessorFactory {
    // Process any set of annotations
    private static final Collection<String> supportedAnnotations
        = unmodifiableCollection(Arrays.asList("*"));

    // No supported options
    private static final Collection<String> supportedOptions = emptySet();

    public Collection<String> supportedAnnotationTypes() {
        return supportedAnnotations;
    }

    public Collection<String> supportedOptions() {
        return supportedOptions;
    }

    public AnnotationProcessor getProcessorFor(
            Set<AnnotationTypeDeclaration> atds,
            AnnotationProcessorEnvironment env) {
        return new FreshnessAp(env);
    }

    private static class FreshnessAp implements AnnotationProcessor {
        private final AnnotationProcessorEnvironment env;
        FreshnessAp(AnnotationProcessorEnvironment env) {
            this.env = env;
        }

        public void process() {
            System.out.println("Testing for freshness.");
            boolean empty = true;
            for (TypeDeclaration typeDecl : env.getSpecifiedTypeDeclarations()) {
                for (FieldDeclaration fieldDecl: typeDecl.getFields() ) {
                    empty = false;
                    System.out.println(typeDecl.getQualifiedName() +
                                       "." + fieldDecl.getSimpleName());

                    // Verify the declaration for the type of the
                    // field is a class with an annotation.
                    System.out.println(((DeclaredType) fieldDecl.getType()).getDeclaration().getAnnotationMirrors());
                    if (((DeclaredType) fieldDecl.getType()).getDeclaration().getAnnotationMirrors().size() == 0)
                        env.getMessager().printError("Expected an annotation.");
                }
            }

            if (empty)
                env.getMessager().printError("No fields encountered.");
        }
    }
}
