/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 6449781
 * @summary Test that reported names of anonymous classes are non-null.
 * @author  Joseph D. Darcy
 * @library ../../../lib
 * @build   JavacTestingAbstractProcessor TestAnonSourceNames
 * @compile -processor TestAnonSourceNames TestAnonClassNames.java
 * @run main TestAnonClassNames
 */

/*
 * This test operates in phases to test retrieving the qualified name
 * of anonymous classes from type elements modeling the anonymous
 * class.  The type elements are generated using both source files and
 * class files as the basis of constructing the elements.
 *
 * Source files will be tested by the @compile line which runs
 * TestAnonSourceNames as an annotation processor over this file.
 *
 * Class files are tested by the @run command on this type.  This
 * class gets the names of classes with different nesting kinds,
 * including anonymous classes, and then invokes the compiler with an
 * annotation processor having the class files names as inputs.  The
 * compiler is invoked via the javax.tools mechanism.
 */

import java.lang.annotation.*;
import javax.lang.model.element.*;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.*;
import javax.tools.*;
import java.util.*;

import static java.lang.annotation.RetentionPolicy.*;
import static javax.lang.model.element.NestingKind.*;
import static javax.lang.model.util.ElementFilter.*;
import static javax.tools.Diagnostic.Kind.*;
import static javax.tools.StandardLocation.*;

@Nesting(TOP_LEVEL)
public class TestAnonClassNames {
    @Nesting(MEMBER)
    static class MemberClass1{}

    @Nesting(MEMBER)
    class MemberClass2{}

    @Nesting(MEMBER)
    class Win$$AtVegas { } // Class with funny name.

    public static void main(String... argv) {
        @Nesting(LOCAL)
        class LocalClass{};

        Object o =  new @Nesting(ANONYMOUS) Object() { // An anonymous annotated class
                public String toString() {
                    return "I have no name!";
                }
            };

        Class<?>[] classes = {
            MemberClass1.class,
            MemberClass2.class,
            LocalClass.class,
            Win$$AtVegas.class,
            o.getClass(),
            TestAnonClassNames.class,
        };

        for(Class<?> clazz : classes) {
            String name = clazz.getName();
            System.out.format("%s is %s%n",
                              clazz.getName(),
                              clazz.getAnnotation(Nesting.class).value());
            testClassName(name);
        }
    }

    /**
     * Perform annotation processing on the class file name and verify
     * the existence of different flavors of class names when the
     * input classes are modeled as elements.
     */
    static void testClassName(String className) {
        JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        List<String> classNames = new ArrayList<String>();
        classNames.add(className);

        List<String> options = new ArrayList<String>();
        options.add("-proc:only");
        options.add("-classpath");
        options.add(System.getProperty("test.classes"));

        JavaCompiler.CompilationTask compileTask =
            javaCompiler.getTask(null, // Output
                                 null, // File manager
                                 null, // Diagnostics
                                 options,
                                 classNames,
                                 null); // Sources
        List<Processor> processors = new ArrayList<Processor>();
        processors.add(new ClassNameProber());
        compileTask.setProcessors(processors);
        Boolean goodResult = compileTask.call();
        if (!goodResult) {
            throw new RuntimeException("Errors found during compile.");
        }
    }
}

@Retention(RUNTIME)
@interface Nesting {
    NestingKind value();
}

/**
 * Probe at the various kinds of names of a type element.
 */
class ClassNameProber extends JavacTestingAbstractProcessor {
    public ClassNameProber(){super();}

    private boolean classesFound=false;

    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            for(TypeElement typeElt : typesIn(roundEnv.getRootElements())) {
                classesFound = true;

                // Verify different names are non-null; an NPE will
                // result in failed compile status being reported.
                NestingKind nestingKind = typeElt.getNestingKind();
                System.out.printf("\tSimple name: ''%s''\tQualified Name: ''%s''\tKind ''%s''\tNesting ''%s''%n",
                                  typeElt.getSimpleName().toString(),
                                  typeElt.getQualifiedName().toString(),
                                  typeElt.getKind().toString(),
                                  nestingKind.toString());

                if (typeElt.getAnnotation(Nesting.class).value() != nestingKind) {
                    throw new RuntimeException("Mismatch of expected and reported nesting kind.");
                }
            }

        }

        if (!classesFound) {
            throw new RuntimeException("Error: no classes processed.");
        }
        return true;
    }
}
