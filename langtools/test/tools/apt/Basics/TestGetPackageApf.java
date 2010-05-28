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
 * This class is used to test getPackage on classes that are
 * not already loaded.
 */
public class TestGetPackageApf implements AnnotationProcessorFactory {
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
        return new TestGetPackageAp(env);
    }

    private static class TestGetPackageAp implements AnnotationProcessor {
        private final AnnotationProcessorEnvironment env;
        TestGetPackageAp(AnnotationProcessorEnvironment env) {
            this.env = env;
        }

        public void process() {
            boolean failed = false;
            String packageNames[] = {
                "", // unnamed package
                "java.lang.annotation",
                "java.lang",
                "java.util",
                "java.awt.image.renderable",
                "foo.bar",
                "foo",
                "p1",
                // "p1.p2", // class p1.p2 obscures package p1.p2
            };

            for(String packageName: packageNames) {
                PackageDeclaration p = env.getPackage(packageName);
                if (p == null) {
                    failed = true;
                    System.err.println("ERROR: No declaration found for ``" + packageName + "''.");
                }
                else if (!packageName.equals(p.getQualifiedName())) {
                    failed = true;
                    System.err.println("ERROR: Unexpected package name; expected " + packageName +
                                       "got " + p.getQualifiedName());
                }
            }

            String notPackageNames[] = {
                "XXYZZY",
                "java.lang.String",
                "1",
                "1.2",
                "3.14159",
                "To be or not to be is a tautology",
                "1+2=3",
            };

            for(String notPackageName: notPackageNames) {
                PackageDeclaration p = env.getPackage(notPackageName);
                if (p != null) {
                    failed = true;
                    System.err.println("ERROR: Unexpected declaration: ``" + p + "''.");
                }
            }

            if (failed)
                throw new RuntimeException("Errors found testing getPackage.");
        }
    }
}
