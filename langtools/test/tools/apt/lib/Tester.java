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


/*
 * A utility used to invoke and test the apt tool.
 * Tests should subclass Tester, and invoke run().
 *
 * @author Scott Seligman
 */

import java.io.*;
import java.util.*;
import com.sun.mirror.apt.*;
import com.sun.mirror.declaration.*;


public abstract class Tester {

    /**
     * The declaration corresponding to this tester's class.  Set by
     * TestProcessorFactory after the constructor completes, and
     * before init() is invoked.
     */
    ClassDeclaration thisClassDecl;

    /**
     * The environment for this apt run.  Set by TestProcessorFactory
     * after the constructor completes, and before init() is invoked.
     */
    AnnotationProcessorEnvironment env;


    // TestProcessorFactory looks here to find the tester that's running
    // when it's invoked.
    static Tester activeTester;

    private static final String[] DEFAULT_ARGS = {
        "-nocompile",
        "-XPrintAptRounds",
        "-XListDeclarations",
    };
    private static final String[] NO_STRINGS = {};

    // Force processor and factory to be compiled
    private static Class dummy = TestProcessorFactory.class;

    private final String testSrc =     System.getProperty("test.src",     ".");
    private final String testClasses = System.getProperty("test.classes", ".");

    // apt command-line args
    private String[] args;


    static {
        // Enable assertions in the unnamed package.
        ClassLoader loader = Tester.class.getClassLoader();
        if (loader != null) {
            loader.setPackageAssertionStatus(null, true);
        }
    }


    protected Tester(String... additionalArgs) {
        String sourceFile = testSrc + File.separator +
                            getClass().getName() + ".java";

        ArrayList<String> as = new ArrayList<String>();
        Collections.addAll(as, DEFAULT_ARGS);
        as.add("-sourcepath");  as.add(testSrc);
        as.add("-factory");     as.add(TestProcessorFactory.class.getName());
        Collections.addAll(as, additionalArgs);
        as.add(sourceFile);
        args = as.toArray(NO_STRINGS);
    }

    /**
     * Run apt.
     */
    protected void run() {
        activeTester = this;
        if (com.sun.tools.apt.Main.process(args) != 0) {
            throw new Error("apt errors encountered.");
        }
    }

    /**
     * Called after thisClassDecl and env have been set, but before any
     * tests are run, to allow the tester subclass to perform any
     * needed initialization.
     */
    protected void init() {
    }

    /**
     * Returns the declaration of a named method in this class.  If this
     * method name is overloaded, one method is chosen arbitrarily.
     * Returns null if no method is found.
     */
    protected MethodDeclaration getMethod(String methodName) {
        for (MethodDeclaration m : thisClassDecl.getMethods()) {
            if (methodName.equals(m.getSimpleName())) {
                return m;
            }
        }
        return null;
    }

    /**
     * Returns the declaration of a named field in this class.
     * Returns null if no field is found.
     */
    protected FieldDeclaration getField(String fieldName) {
        for (FieldDeclaration f : thisClassDecl.getFields()) {
            if (fieldName.equals(f.getSimpleName())) {
                return f;
            }
        }
        return null;
    }

    /**
     * Returns the annotation mirror of a given type on a named method
     * in this class.  If this method name is overloaded, one method is
     * chosen arbitrarily.  Returns null if no appropriate annotation
     * is found.
     */
    protected AnnotationMirror getAnno(String methodName, String annoType) {
        MethodDeclaration m = getMethod(methodName);
        if (m != null) {
            TypeDeclaration at = env.getTypeDeclaration(annoType);
            for (AnnotationMirror a : m.getAnnotationMirrors()) {
                if (at == a.getAnnotationType().getDeclaration()) {
                    return a;
                }
            }
        }
        return null;
    }
}
