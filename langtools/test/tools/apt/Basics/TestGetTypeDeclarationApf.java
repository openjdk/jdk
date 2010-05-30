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
 * This class is used to test getTypeDeclaration on classes that are
 * not already loaded.
 */
public class TestGetTypeDeclarationApf implements AnnotationProcessorFactory {
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
        return new TestGetTypeDeclarationAp(env);
    }

    private static class TestGetTypeDeclarationAp implements AnnotationProcessor {
        private final AnnotationProcessorEnvironment env;
        TestGetTypeDeclarationAp(AnnotationProcessorEnvironment env) {
            this.env = env;
        }

        public void process() {
            String classNames[] = {
                "java.lang.String",             // should be already available
                "java.lang.Thread.State",       // should be already available
                "java.util.Collection",
                "java.util.Map.Entry",
                "foo.bar.Baz.Xxyzzy.Wombat",
                "foo.bar.Baz.Xxyzzy",
                "foo.bar.Baz",
                "foo.bar.Quux",
                "foo.bar.Quux.Xxyzzy",
                "foo.bar.Quux.Xxyzzy.Wombat",
                "NestedClassAnnotations",
                "NestedClassAnnotations.NestedClass",
            };

            for(String className: classNames) {
                TypeDeclaration t = env.getTypeDeclaration(className);
                if (t == null)
                    throw new RuntimeException("No declaration found for " + className);
                if (! t.getQualifiedName().equals(className))
                    throw new RuntimeException("Class with wrong name found for " + className);
            }

            // Test obscuring behavior; i.e. nested class C1 in class
            // p1 where p1 is member of package p2 should be favored
            // over class C1 in package p1.p2.
            String nonuniqueCanonicalNames[] = {
                "p1.p2.C1",
            };
            for(String className: nonuniqueCanonicalNames) {
                ClassDeclaration c1 = (ClassDeclaration) env.getTypeDeclaration(className);
                ClassDeclaration c2 = (ClassDeclaration) c1.getDeclaringType();
                PackageDeclaration p     = env.getPackage("p1");

                if (!p.equals(c1.getPackage())  ||
                    c2 == null ||
                    !"C1".equals(c1.getSimpleName())) {
                    throw new RuntimeException("Bad class declaration");
                }
            }

            String notClassNames[] = {
                "",
                "XXYZZY",
                "java",
                "java.lang",
                "java.lang.Bogogogous",
                "1",
                "1.2",
                "3.14159",
                "To be or not to be is a tautology",
                "1+2=3",
                "foo+.x",
                "foo+x",
                "+",
                "?",
                "***",
                "java.*",
            };

            for(String notClassName: notClassNames) {
                Declaration t = env.getTypeDeclaration(notClassName);
                if (t != null) {
                    System.err.println("Unexpected declaration:" + t);
                    throw new RuntimeException("Declaration found for ``" + notClassName + "''.");
                }
            }

        }
    }
}
