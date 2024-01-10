/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8306112 8309568
 * @summary Test basic processing of implicitly declared classes.
 * @library /tools/javac/lib
 * @modules java.compiler
 *          jdk.compiler
 * @build   JavacTestingAbstractProcessor TestImplicitClass
 * @compile         -processor TestImplicitClass            --enable-preview --release ${jdk.version}                            Anonymous.java
 * @clean Nameless.java
 * @compile/process -processor TestImplicitClass -proc:only --enable-preview --release ${jdk.version} -Xprefer:newer -AclassOnly Anonymous Nameless
 */

// The first @compile line processes Anonymous.java and a
// Nameless.java class generated using the Filer. Both of those implicitly
// declared classes are then compiled down to class files.  The second
// @compile line, as directed by -Xprefer:newer, builds and checks the
// language model objects constructed from those class files, ignoring
// any source files for those types.

import java.lang.annotation.*;
import java.io.Writer;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
import static javax.lang.model.util.ElementFilter.*;
import javax.tools.JavaFileObject;

/**
 * Test annotation processing representation of implicitly declared classes
 * constructed from either a source file or a class file.
 */
@SuppressWarnings("preview")
@SupportedOptions("classOnly")
public class TestImplicitClass extends JavacTestingAbstractProcessor {

    private static int round  = 0;
    private static int checkedClassesCount = 0;
    private static boolean classOnly = false;

    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        if (round == 0) {
            classOnly = options.containsKey("classOnly");

            checkRoots(roundEnv); // Check any files from the comamnd line

            // Don't generate any files if testing pre-existing class files
            if (!classOnly) {
                generateImplicitClass();
            }
        } else {
            if (!roundEnv.processingOver()) { // Test generated file(s)
                checkRoots(roundEnv);
            } else { // Should have checked at least one class before processing is over
                if (checkedClassesCount == 0) {
                    messager.printError("No implicitly declared classes checked.");
                }
            }
        }

        round++;
        return true;
    }

    private void checkRoots(RoundEnvironment roundEnv) {
        int checks = 0;
        for (TypeElement type : typesIn(roundEnv.getRootElements())) {
            checks++;
            checkUnnamedClassProperties(type);
        }
        if (checks == 0) {
            messager.printError("No checking done of any candidate implicitly declared classes.");
        }
    }

    private void generateImplicitClass() {
        try {
            String unnamedSource = """
            void main() {
                System.out.println("Nameless, but not voiceless.");
            }
            """;

            JavaFileObject outputFile = processingEnv.getFiler().createSourceFile("Nameless");
            try(Writer w = outputFile.openWriter()) {
                w.append(unnamedSource);
            }
        } catch (java.io.IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    /*
     * From JEP 463 JLS changes:
     *
     * "An implicitly declared class compilation unit implicitly declares a class that
     * satisfies the following properties:
     * It is always a top level class.
     * It is never abstract (8.1.1.1).
     * It is always final (8.1.1.2).
     * It is always a member of an unnamed package (7.4.2) and has package access.
     * Its direct superclass type is always Object (8.1.4).
     * It never has any direct superinterface types (8.1.5).
     *
     * The body of the class contains every ClassMemberDeclaration
     * from the implicitly declared class compilation unit. It is not possible for
     * an implicitly declared class compilation unit to declare an instance
     * initializer, static initializer, or constructor.
     *
     * It has an implicitly declared default constructor (8.8.9).
     * All members of this class, including any implicitly declared
     * members, are subject to the usual rules for member declarations
     * in a class.
     *
     * It is a compile-time error if this class does not declare a candidate main method (12.1.4).
     */
    void checkUnnamedClassProperties(TypeElement implicitClass) {
        checkedClassesCount++;
        Name expectedName = implicitClass.getSimpleName();

        System.out.println("Checking " + expectedName);

        if (implicitClass.getNestingKind() != NestingKind.TOP_LEVEL) {
            messager.printError("Implicitly declared class is not top-level.", implicitClass);
        }

        if (!implicitClass.getQualifiedName().equals(expectedName)) {
            messager.printError("Implicitly declared class qualified name does not match simple name.", implicitClass);
        }

        Name binaryName = elements.getBinaryName(implicitClass);
        if (!expectedName.equals(binaryName)) {
            messager.printError("Implicitly declared class has unexpected binary name" + binaryName + ".", implicitClass);
        }

        if (implicitClass.getModifiers().contains(Modifier.ABSTRACT)) {
            messager.printError("Implicitly declared class is abstract.", implicitClass);
        }

        if (!implicitClass.getModifiers().contains(Modifier.FINAL)) {
            messager.printError("Implicitly declared class is _not_ final.", implicitClass);
        }

        if (!elements.getPackageOf(implicitClass).isUnnamed()) {
            messager.printError("Implicitly declared class is _not_ in an unnamed package.", implicitClass);
        }

        if (implicitClass.getModifiers().contains(Modifier.PUBLIC)  ||
            implicitClass.getModifiers().contains(Modifier.PRIVATE) ||
            implicitClass.getModifiers().contains(Modifier.PROTECTED)) {
            messager.printError("Implicitly declared class does _not_ have package access.", implicitClass);
        }

        if ( !types.isSameType(implicitClass.getSuperclass(),
                               elements.getTypeElement("java.lang.Object").asType())) {
            messager.printError("Implicitly declared class does _not_ have java.lang.Object as a superclass.", implicitClass);
        }

        if (!implicitClass.getInterfaces().isEmpty()) {
            messager.printError("Implicitly declared class has superinterfaces.", implicitClass);
        }

        List<ExecutableElement> ctors = constructorsIn(implicitClass.getEnclosedElements());
        if (ctors.size() != 1 ) {
            messager.printError("Did not find exactly one constructor", implicitClass);
        }

        if (!classOnly) {
            // Mandated-ness of default constructors not preserved in class files
            ExecutableElement ctor = ctors.getFirst();
            if (elements.getOrigin(ctor) != Elements.Origin.MANDATED) {
                messager.printError("Constructor was not marked as mandated", ctor);
            }
        }

        List<ExecutableElement> methods = methodsIn(implicitClass.getEnclosedElements());
        // Just look for a method named "main"; don't check the other details.
        boolean mainFound = false;
        Name mainName = elements.getName("main");
        for (var method : methods) {
            if (method.getSimpleName().equals(mainName)) {
                mainFound = true;
                break;
            }
        }

        if (!mainFound) {
            messager.printError("No main method found", implicitClass);
        }
    }
}
