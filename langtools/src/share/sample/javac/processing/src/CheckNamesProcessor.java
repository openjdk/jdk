/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.util.Set;
import java.util.EnumSet;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;
import static javax.lang.model.SourceVersion.*;
import static javax.lang.model.element.Modifier.*;
import static javax.lang.model.element.ElementKind.*;
import static javax.lang.model.type.TypeKind.*;
import static javax.lang.model.util.ElementFilter.*;
import static javax.tools.Diagnostic.Kind.*;

/**
 * A sample processor to check naming conventions are being followed.
 *
 * <h3>How to run this processor from the command line</h3>
 * <ol>
 * <li> Compile this file; for example<br>
 *      {@code javac -d procdir CheckNamesProcessor.java}
 * <li> Use {@code javac} to run the annotation processor on itself:<br>
 *      {@code javac -processorpath procdir -processor CheckNamesProcessor -proc:only CheckNamesProcessor.java}
 * </ol>
 *
 * <h3>Another way to run this processor from the command line</h3>
 * <ol>
 * <li> Compile the processor as before
 *
 * <li> Create a UTF-8 encoded text file named {@code
 * javax.annotation.processing.Processor} in the {@code
 * META-INF/services} directory.  The contents of the file are a list
 * of the binary names of the concrete processor classes, one per
 * line.  This provider-configuration file is used by {@linkplain
 * java.util.ServiceLoader service-loader} style lookup.
 *
 * <li> Create a {@code jar} file with the processor classes and
 * {@code META-INF} information.
 *
 * <li> Such a {@code jar} file can now be used with the <i>discovery
 * process</i> without explicitly naming the processor to run:<br>
 * {@code javac -processorpath procdir -proc:only CheckNamesProcessor.java}
 *
 * </ol>
 *
 * For some notes on how to run an annotation processor inside
 * NetBeans, see http://wiki.java.net/bin/view/Netbeans/FaqApt.
 *
 * <h3>Possible Enhancements</h3>
 * <ul>
 *
 * <li> Support an annotation processor option to control checking
 * exported API elements ({@code public} and {@code protected} ones)
 * or all elements
 *
 * <li> Print out warnings that are more informative
 *
 * <li> Return a true/false status if any warnings were printed or
 * compute and return name warning count
 *
 * <li> Implement checks of package names
 *
 * <li> Use the Tree API, com.sun.source, to examine names within method bodies
 *
 * <li> Define an annotation type whose presence can indicate a
 * different naming convention is being followed
 *
 * <li> Implement customized checks on elements in chosen packages
 *
 * </ul>
 *
 * @author Joseph D. Darcy
 */
@SupportedAnnotationTypes("*")     // Process (check) everything
public class CheckNamesProcessor extends AbstractProcessor {
    private NameChecker nameChecker;

    /**
     * Check that the names of the root elements (and their enclosed
     * elements) follow the appropriate naming conventions.  This
     * processor examines all files regardless of whether or not
     * annotations are present; no new source or class files are
     * generated.
     *
     * <p>Processors that actually process specific annotations should
     * <em>not</em> report supporting {@code *}; this could cause
     * performance degradations and other undesirable outcomes.
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            for (Element element : roundEnv.getRootElements() )
                nameChecker.checkNames(element);
        }
        return false; // Allow other processors to examine files too.
    }

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        nameChecker = new NameChecker(processingEnv);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        /*
         * Return latest source version instead of a fixed version
         * like RELEASE_7.  To return a fixed version, this class
         * could be annotated with a SupportedSourceVersion
         * annotation.
         *
         * Warnings will be issued if any unknown language constructs
         * are encountered.
         */
        return SourceVersion.latest();
    }

    /**
     * Provide checks that an element and its enclosed elements follow
     * the usual naming conventions.
     *
     * <p> Conventions from JLSv3 section 6.8:
     *
     * <ul>
     * <li> Classes and interfaces: camel case, first letter is uppercase
     * <li> Methods: camel case, first letter is lowercase
     * <li> Type variables: one uppercase letter
     * <li> Fields
     * <ul>
     * <li> non-final: camel case, initial lowercase
     * <li> constant: uppercase separated by underscores
     * </ul>
     * <li> Packages: checks left as exercise for the reader, see JLSv3 section 7.7
     * </ul>
     */
    private static class NameChecker {
        private final Messager messager;
        private final Types typeUtils;

        NameCheckScanner nameCheckScanner = new NameCheckScanner();

        NameChecker(ProcessingEnvironment processsingEnv) {
            this.messager  = processsingEnv.getMessager();
            this.typeUtils = processsingEnv.getTypeUtils();
        }

        /**
         * If the name of the argument or its enclosed elements
         * violates the naming conventions, report a warning.
         */
        public void checkNames(Element element) {
            // Implement name checks with a visitor, but expose that
            // functionality through this method instead.
            nameCheckScanner.scan(element);
        }

        /**
         * Visitor to implement name checks.
         */
        private class NameCheckScanner extends ElementScanner7<Void, Void> {
            // The visitor could be enhanced to return true/false if
            // there were warnings reported or a count of the number
            // of warnings.  This could be facilitated by using
            // Boolean or Integer instead of Void for the actual type
            // arguments.  In more detail, one way to tally the number
            // of warnings would be for each method to return the sum
            // of the warnings it and the methods it called issued, a
            // bottom-up computation.  In that case, the first type
            // argument would be Integer and the second type argument
            // would still be Void.  Alternatively, the current count
            // could be passed along in Integer parameter p and each
            // method could return the Integer sum of p and the
            // warnings the method issued.  Some computations are more
            // naturally expressed in one form instead of the other.
            // If greater control is needed over traversal order, a
            // SimpleElementVisitor can be extended instead of an
            // ElementScanner.

            /**
             * Check the name of a type and its enclosed elements and
             * type parameters.
             */
            @Override
            public Void visitType(TypeElement e, Void p) {
                scan(e.getTypeParameters(), p); // Check the names of any type parameters
                checkCamelCase(e, true);        // Check the name of the class or interface
                super.visitType(e, p);          // Check the names of any enclosed elements
                return null;
            }

            /**
             * Check the name of an executable (method, constructor,
             * etc.) and its type parameters.
             */
            @Override
            public Void visitExecutable(ExecutableElement e, Void p) {
                scan(e.getTypeParameters(), p); // Check the names of any type parameters

                // Check the name of the executable
                if (e.getKind() == METHOD) {
                    // Make sure that a method does not have the same
                    // name as its class or interface.
                    Name name = e.getSimpleName();
                    if (name.contentEquals(e.getEnclosingElement().getSimpleName()))
                        messager.printMessage(WARNING,
                                              "A method should not have the same name as its enclosing type, ``" +
                                              name + "''." , e);
                    checkCamelCase(e, false);
                }
                // else constructors and initializers don't have user-defined names

                // At this point, could use the Tree API,
                // com.sun.source, to examine the names of entities
                // inside a method.
                super.visitExecutable(e, p);
                return null;
            }

            /**
             * Check the name of a field, parameter, etc.
             */
            @Override
            public Void visitVariable(VariableElement e, Void p) {
                if (!checkForSerial(e)) { // serialVersionUID checks
                    // Is the variable a constant?
                    if (e.getKind() == ENUM_CONSTANT ||
                        e.getConstantValue() != null ||
                        heuristicallyConstant(e) )
                        checkAllCaps(e); // includes enum constants
                    else
                        checkCamelCase(e, false);
                }
                // A call to super can be elided with the current language definition.
                // super.visitVariable(e, p);
                return null;
            }

            /**
             * Check the name of a type parameter.
             */
            @Override
            public Void visitTypeParameter(TypeParameterElement e, Void p) {
                checkAllCaps(e);
                // A call to super can be elided with the current language definition.
                // super.visitTypeParameter(e, p);
                return null;
            }

            /**
             * Check the name of a package.
             */
            @Override
            public Void visitPackage(PackageElement e, Void p) {
                /*
                 * Implementing the checks of package names is left
                 * as an exercise for the reader, see JLSv3 section
                 * 7.7 for conventions.
                 */

                // Whether or not this method should call
                // super.visitPackage, to visit the packages enclosed
                // elements, is a design decision based on what a
                // PackageElemement is used to mean in this context.
                // A PackageElement can represent a whole package, so
                // it can provide a concise way to indicate many
                // user-defined types should be visited.  However, a
                // PackageElement can also represent a
                // package-info.java file, as would be in the case if
                // the PackageElement came from
                // RoundEnvironment.getRootElements.  In that case,
                // the package-info file and other files in that
                // package could be passed in.  Therefore, without
                // further checks, types in a package could be visited
                // more than once if a package's elements were visited
                // too.
                return null;
            }

            @Override
            public Void visitUnknown(Element e, Void p) {
                // This method will be called if a kind of element
                // added after JDK 7 is visited.  Since as of this
                // writing the conventions for such constructs aren't
                // known, issue a warning.
                messager.printMessage(WARNING,
                                      "Unknown kind of element, " + e.getKind() +
                                      ", no name checking performed.", e);
                return null;
            }

            // All the name checking methods assume the examined names
            // are syntactically well-formed identifiers.

            /**
             * Return {@code true} if this variable is a field named
             * "serialVersionUID"; false otherwise.  A true
             * serialVersionUID of a class has type {@code long} and
             * is static and final.
             *
             * <p>To check that a Serializable class defines a proper
             * serialVersionUID, run javac with -Xlint:serial.
             *
             * @return true if this variable is a serialVersionUID field and false otherwise
             */
            private boolean checkForSerial(VariableElement e) {
                // If a field is named "serialVersionUID" ...
                if (e.getKind() == FIELD &&
                    e.getSimpleName().contentEquals("serialVersionUID")) {
                    // ... issue a warning if it does not act as a serialVersionUID
                    if (!(e.getModifiers().containsAll(EnumSet.of(STATIC, FINAL)) &&
                            typeUtils.isSameType(e.asType(), typeUtils.getPrimitiveType(LONG)) &&
                            e.getEnclosingElement().getKind() == CLASS )) // could check that class implements Serializable
                        messager.printMessage(WARNING,
                                              "Field named ``serialVersionUID'' is not acting as such.", e);
                    return true;
                }
                return false;
            }

            /**
             * Using heuristics, return {@code true} is the variable
             * should follow the naming conventions for constants and
             * {@code false} otherwise.  For example, the public
             * static final fields ZERO, ONE, and TEN in
             * java.math.BigDecimal are logically constants (and named
             * as constants) even though BigDecimal values are not
             * regarded as constants by the language specification.
             * However, some final fields may not act as constants
             * since the field may be a reference to a mutable object.
             *
             * <p> These heuristics could be tweaked to provide better
             * fidelity.
             *
             * @return true if the current heuristics regard the
             * variable as a constant and false otherwise.
             */
            private boolean heuristicallyConstant(VariableElement e) {
                // Fields declared in interfaces are logically
                // constants, JLSv3 section 9.3.
                if (e.getEnclosingElement().getKind() == INTERFACE)
                    return true;
                else if (e.getKind() == FIELD &&
                         e.getModifiers().containsAll(EnumSet.of(PUBLIC, STATIC, FINAL)))
                    return true;
                else {
                    // A parameter declared final should not be named like
                    // a constant, neither should exception parameters.
                    return false;
                }
            }

            /**
             * Print a warning if an element's simple name is not in
             * camel case.  If there are two adjacent uppercase
             * characters, the name is considered to violate the
             * camel case naming convention.
             *
             * @param e the element whose name will be checked
             * @param initialCaps whether or not the first character should be uppercase
             */
            private void checkCamelCase(Element e, boolean initialCaps) {
                String name = e.getSimpleName().toString();
                boolean previousUpper = false;
                boolean conventional = true;
                int firstCodePoint = name.codePointAt(0);

                if (Character.isUpperCase(firstCodePoint)) {
                    previousUpper = true;
                    if (!initialCaps) {
                        messager.printMessage(WARNING,
                                              "Name, ``" + name + "'', should start in lowercase.", e);
                        return;
                    }
                } else if (Character.isLowerCase(firstCodePoint)) {
                    if (initialCaps) {
                        messager.printMessage(WARNING,
                                              "Name, ``" + name + "'', should start in uppercase.", e);
                        return;
                    }
                } else // underscore, etc.
                    conventional = false;

                if (conventional) {
                    int cp = firstCodePoint;
                    for (int i = Character.charCount(cp);
                         i < name.length();
                         i += Character.charCount(cp)) {
                        cp = name.codePointAt(i);
                        if (Character.isUpperCase(cp)){
                            if (previousUpper) {
                                conventional = false;
                                break;
                            }
                            previousUpper = true;
                        } else
                            previousUpper = false;
                    }
                }

                if (!conventional)
                    messager.printMessage(WARNING,
                                          "Name, ``" + name + "'', should be in camel case.", e);
            }

            /**
             * Print a warning if the element's name is not a sequence
             * of uppercase letters separated by underscores ("_").
             *
             * @param e the element whose name will be checked
             */
            private void checkAllCaps(Element e) {
                String name = e.getSimpleName().toString();
                if (e.getKind() == TYPE_PARAMETER) { // Should be one character
                    if (name.codePointCount(0, name.length()) > 1 ||
                        // Assume names are non-empty
                        !Character.isUpperCase(name.codePointAt(0)))
                        messager.printMessage(WARNING,
                                              "A type variable's name,``" + name +
                                              "'', should be a single uppercace character.",
                                              e);
                } else {
                    boolean conventional = true;
                    int firstCodePoint = name.codePointAt(0);

                    // Starting with an underscore is not conventional
                    if (!Character.isUpperCase(firstCodePoint))
                        conventional = false;
                    else {
                        // Was the previous character an underscore?
                        boolean previousUnderscore = false;
                        int cp = firstCodePoint;
                        for (int i = Character.charCount(cp);
                             i < name.length();
                             i += Character.charCount(cp)) {
                            cp = name.codePointAt(i);
                            if (cp == (int) '_') {
                                if (previousUnderscore) {
                                    conventional = false;
                                    break;
                                }
                                previousUnderscore = true;
                            } else {
                                previousUnderscore = false;
                                if (!Character.isUpperCase(cp) && !Character.isDigit(cp) ) {
                                    conventional = false;
                                    break;
                                }
                            }
                        }
                    }

                    if (!conventional)
                        messager.printMessage(WARNING,
                                              "A constant's name, ``" + name + "'', should be ALL_CAPS.",
                                              e);
                }
            }

        }
    }
}

/**
 * Lots of bad names.  Don't write code like this!
 */
class BADLY_NAMED_CODE {
    enum colors {
        red,
        blue,
        green;
    }

    // Don't start the name of a constant with an underscore
    static final int _FORTY_TWO = 42;

    // Non-constants shouldn't use ALL_CAPS
    public static int NOT_A_CONSTANT = _FORTY_TWO;

    // *Not* a serialVersionUID
    private static final int serialVersionUID = _FORTY_TWO;

    // Not a constructor
    protected void BADLY_NAMED_CODE() {
        return;
    }

    public void NOTcamelCASEmethodNAME() {
        return;
    }
}
