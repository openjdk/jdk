/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package javax.lang.model.util;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Objects;

import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.element.*;


/**
 * Utility methods for operating on program elements.
 *
 * <p><b>Compatibility Note:</b> Methods may be added to this interface
 * in future releases of the platform.
 *
 * @see javax.annotation.processing.ProcessingEnvironment#getElementUtils
 * @since 1.6
 */
public interface Elements {

    /**
     * Returns a package given its fully qualified name if the package is uniquely
     * determinable in the environment.
     *
     * If running with modules, packages of the given name are searched in a
     * two-stage process:
     * <ul>
     *     <li>find non-empty packages with the given name returned by
     *         {@link #getPackageElement(ModuleElement, CharSequence)},
     *         where the provided ModuleElement is any
     *         {@linkplain java.lang.module##root-modules root module},
     *     </li>
     *     <li>if the above yields an empty list, search
     *         {@linkplain #getAllModuleElements() all modules} for observable
     *         packages with the given name
     *     </li>
     * </ul>
     *
     * If this process leads to a list with a single element, the
     * single element is returned, otherwise {@code null} is returned.
     *
     * @param name fully qualified package name,
     *             or an empty string for an unnamed package
     * @return the specified package,
     *         or {@code null} if no package can be uniquely determined.
     */
    PackageElement getPackageElement(CharSequence name);

    /**
     * Returns a package given its fully qualified name, as seen from the given module.
     *
     * @implSpec The default implementation of this method returns
     * {@code null}.
     *
     * @param module module relative to which the lookup should happen
     * @param name  fully qualified package name, or an empty string for an unnamed package
     * @return the specified package, or {@code null} if it cannot be found
     * @see #getAllPackageElements
     * @since 9
     */
    default PackageElement getPackageElement(ModuleElement module, CharSequence name) {
        return null;
    }

    /**
     * Returns all package elements with the given canonical name.
     *
     * There may be more than one package element with the same canonical
     * name if the package elements are in different modules.
     *
     * @implSpec The default implementation of this method calls
     * {@link #getAllModuleElements() getAllModuleElements} and stores
     * the result. If the set of modules is empty, {@link
     * #getPackageElement(CharSequence) getPackageElement(name)} is
     * called passing through the name argument. If {@code
     * getPackageElement(name)} is {@code null}, an empty set of
     * package elements is returned; otherwise, a single-element set
     * with the found package element is returned. If the set of
     * modules is nonempty, the modules are iterated over and any
     * non-{@code null} results of {@link
     * #getPackageElement(ModuleElement, CharSequence)
     * getPackageElement(module, name)} are accumulated into a
     * set. The set is then returned.
     *
     * @param name  the canonical name
     * @return the package elements, or an empty set if no package with the name can be found
     * @see #getPackageElement(ModuleElement, CharSequence)
     * @since 9
     */
    default Set<? extends PackageElement> getAllPackageElements(CharSequence name) {
        Set<? extends ModuleElement> modules = getAllModuleElements();
        if (modules.isEmpty()) {
            PackageElement packageElt = getPackageElement(name);
            return (packageElt != null) ?
                Collections.singleton(packageElt):
                Collections.emptySet();
        } else {
            Set<PackageElement> result = new LinkedHashSet<>(1); // Usually expect at most 1 result
            for (ModuleElement module: modules) {
                PackageElement packageElt = getPackageElement(module, name);
                if (packageElt != null)
                    result.add(packageElt);
            }
            return Collections.unmodifiableSet(result);
        }
    }

    /**
     * Returns a type element given its canonical name if the type element is uniquely
     * determinable in the environment.
     *
     * If running with modules, type elements of the given name are
     * searched in a two-stage process:
     * <ul>
     *     <li>find type elements with the given name returned by
     *         {@link #getTypeElement(ModuleElement, CharSequence)},
     *         where the provided ModuleElement is any
     *         {@linkplain java.lang.module##root-modules root module},
     *     </li>
     *     <li>if the above yields an empty list, search
     *         {@linkplain #getAllModuleElements() all modules} for observable
     *         type elements with the given name
     *     </li>
     * </ul>
     *
     * If this process leads to a list with a single element, the
     * single element is returned, otherwise {@code null} is returned.
     *
     * @param name the canonical name
     * @return the named type element,
     *         or {@code null} if no type element can be uniquely determined.
     */
    TypeElement getTypeElement(CharSequence name);

    /**
     * Returns a type element given its canonical name, as seen from the given module.
     *
     * @implSpec The default implementation of this method returns
     * {@code null}.
     *
     * @param module module relative to which the lookup should happen
     * @param name  the canonical name
     * @return the named type element, or {@code null} if it cannot be found
     * @see #getAllTypeElements
     * @since 9
     */
    default TypeElement getTypeElement(ModuleElement module, CharSequence name) {
        return null;
    }

    /**
     * Returns all type elements with the given canonical name.
     *
     * There may be more than one type element with the same canonical
     * name if the type elements are in different modules.
     *
     * @implSpec The default implementation of this method calls
     * {@link #getAllModuleElements() getAllModuleElements} and stores
     * the result. If the set of modules is empty, {@link
     * #getTypeElement(CharSequence) getTypeElement(name)} is called
     * passing through the name argument. If {@code
     * getTypeElement(name)} is {@code null}, an empty set of type
     * elements is returned; otherwise, a single-element set with the
     * found type element is returned. If the set of modules is
     * nonempty, the modules are iterated over and any non-{@code null}
     * results of {@link #getTypeElement(ModuleElement,
     * CharSequence) getTypeElement(module, name)} are accumulated
     * into a set. The set is then returned.
     *
     * @param name  the canonical name
     * @return the type elements, or an empty set if no type with the name can be found
     * @see #getTypeElement(ModuleElement, CharSequence)
     * @since 9
     */
    default Set<? extends TypeElement> getAllTypeElements(CharSequence name) {
        Set<? extends ModuleElement> modules = getAllModuleElements();
        if (modules.isEmpty()) {
            TypeElement typeElt = getTypeElement(name);
            return (typeElt != null) ?
                Collections.singleton(typeElt):
                Collections.emptySet();
        } else {
            Set<TypeElement> result = new LinkedHashSet<>(1); // Usually expect at most 1 result
            for (ModuleElement module: modules) {
                TypeElement typeElt = getTypeElement(module, name);
                if (typeElt != null)
                    result.add(typeElt);
            }
            return Collections.unmodifiableSet(result);
        }
    }

    /**
     * Returns a module element given its fully qualified name.
     *
     * If the requested module cannot be found, {@code null} is
     * returned. One situation where a module cannot be found is if
     * the environment does not include modules, such as an annotation
     * processing environment configured for a {@linkplain
     * javax.annotation.processing.ProcessingEnvironment#getSourceVersion
     * source version} without modules.
     *
     * @implSpec The default implementation of this method returns
     * {@code null}.
     *
     * @param name  the name, or an empty string for an unnamed module
     * @return the named module element, or {@code null} if it cannot be found
     * @see #getAllModuleElements
     * @since 9
     */
    default ModuleElement getModuleElement(CharSequence name) {
        return null;
    }

    /**
     * Returns all module elements in the current environment.
     *
     * If no modules are present, an empty set is returned. One
     * situation where no modules are present occurs when the
     * environment does not include modules, such as an annotation
     * processing environment configured for a {@linkplain
     * javax.annotation.processing.ProcessingEnvironment#getSourceVersion
     * source version} without modules.
     *
     * @implSpec The default implementation of this method returns
     * an empty set.
     *
     * @apiNote
     * When an environment includes modules, both named modules and
     * {@linkplain ModuleElement#isUnnamed() unnamed modules} may be
     * returned.
     *
     * @return the known module elements, or an empty set if there are no modules
     * @see #getModuleElement(CharSequence)
     * @since 9
     */
    default Set<? extends ModuleElement> getAllModuleElements() {
        return Collections.emptySet();
    }

    /**
     * {@return the values of an annotation's elements, including defaults}
     *
     * @see AnnotationMirror#getElementValues()
     * @param a  annotation to examine
     */
    Map<? extends ExecutableElement, ? extends AnnotationValue>
            getElementValuesWithDefaults(AnnotationMirror a);

    /**
     * Returns the text of the documentation (&quot;JavaDoc&quot;)
     * comment of an element.
     *
     * <p>A documentation comment of an element is a particular kind
     * of comment that immediately precedes the element, ignoring
     * white space, annotations and any other comments that are
     * not themselves documentation comments.
     *
     * <p>There are two kinds of documentation comments, either based on
     * <em>traditional comments</em> or based on a series of
     * <em>end-of-line comments</em>. For both kinds, the text
     * returned for the documentation comment is a processed form of
     * the comment as it appears in source code, as described below.
     *
     * <p>A {@linkplain DocCommentKind#TRADITIONAL traditional
     * documentation comment} is a traditional comment that begins
     * with "{@code /**}", and ends with a separate "<code>*&#47;</code>".
     * (Therefore, such a comment contains at least three "{@code *}"
     * characters.)
     * The lines of such a comment are processed as follows:
     * <ul>
     * <li>The leading "{@code /**}" is removed, as are any
     * immediately following space characters on that line. If all the
     * characters of the line are removed, it makes no contribution to
     * the returned comment.
     * <li>For subsequent lines
     * of the doc comment starting after the initial "{@code /**}",
     * if the lines start with <em>zero</em> or more whitespace characters
     * followed by <em>one</em> or more "{@code *}" characters,
     * those leading whitespace characters are discarded as are any
     * consecutive "{@code *}" characters appearing after the white
     * space or starting the line.
     * Otherwise, if a line does not have a prefix of the described
     * form, the entire line is retained.
     * <li> The trailing "<code>*&#47;</code>" is removed. The line
     * with the trailing" <code>*&#47;</code>" also undergoes leading
     * space and "{@code *}" character removal as described above.
     * <li>The processed lines are then concatenated together,
     * separated by newline ("{@code \n}") characters, and returned.
     * </ul>
     *
     * <p>An {@linkplain DocCommentKind#END_OF_LINE end-of-line
     * documentation comment} is a series of adjacent end-of-line
     * comments, each on a line by itself, ignoring any whitespace
     * characters at the beginning of the line, and each beginning
     * with "{@code ///}".
     * The lines of such a comment are processed as follows:
     * <ul>
     * <li>Any leading whitespace and the three initial "{@code /}"
     * characters are removed from each line.
     * <li>The lines are shifted left, by removing leading whitespace
     * characters, until the non-blank line with the least leading
     * whitespace characters has no remaining leading whitespace
     * characters.
     * <li>Additional leading whitespace characters and any trailing
     * whitespace characters in each line are preserved.
     * <li>
     * The processed lines are then concatenated together,
     * separated by newline ("{@code \n}") characters, and returned.
     * If the last line is not blank, the returned value will not be
     * terminated by a newline character.
     * </ul>
     *
     * @param e  the element being examined
     * @return the documentation comment of the element, or {@code null}
     *          if there is none
     * @jls 3.6 White Space
     * @jls 3.7 Comments
     *
     * @apiNote
     * Documentation comments are processed by the standard doclet
     * used by the {@code javadoc} tool to generate API documentation.
     */
    String getDocComment(Element e);

    /**
     * {@return the kind of the documentation comment for the given element,
     * or {@code null} if there is no comment or the kind is not known}
     *
     * @implSpec The default implementation of this method returns
     * {@code null}.
     *
     * @param e the element being examined
     * @since 23
     */
    default DocCommentKind getDocCommentKind(Element e) {
        return null;
    }

    /**
     * The kind of documentation comment.
     *
     * @since 23
     */
    enum DocCommentKind {
        /**
         * The kind of comments whose lines are prefixed by {@code ///}.
         *
         * @apiNote
         * The standard doclet used by the {@code javadoc} tool treats these comments
         * as containing Markdown and documentation comment tags.
         *
         *
         * @see <a href="https://openjdk.org/jeps/467">
         * JEP 467: Markdown Documentation Comments</a>
         */
        END_OF_LINE,

        /**
         * The kind of comments that begin with {@code /**}.
         *
         * @apiNote
         * The standard doclet used by the {@code javadoc} tool treats these comments
         * as containing HTML and documentation comment tags.
         */
        TRADITIONAL
    }

    /**
     * {@return {@code true} if the element is deprecated, {@code false} otherwise}
     *
     * @param e  the element being examined
     */
    boolean isDeprecated(Element e);

    /**
     * {@return the <em>origin</em> of the given element}
     *
     * <p>Note that if this method returns {@link Origin#EXPLICIT
     * EXPLICIT} and the element was created from a class file, then
     * the element may not, in fact, correspond to an explicitly
     * declared construct in source code. This is due to limitations
     * of the fidelity of the class file format in preserving
     * information from source code. For example, at least some
     * versions of the class file format do not preserve whether a
     * constructor was explicitly declared by the programmer or was
     * implicitly declared as the <em>default constructor</em>.
     *
     * @implSpec The default implementation of this method returns
     * {@link Origin#EXPLICIT EXPLICIT}.
     *
     * @param e  the element being examined
     * @since 9
     */
    default Origin getOrigin(Element e) {
        return Origin.EXPLICIT;
    }

    /**
     * {@return the <em>origin</em> of the given annotation mirror}
     *
     * An annotation mirror is {@linkplain Origin#MANDATED mandated}
     * if it is an implicitly declared <em>container annotation</em>
     * used to hold repeated annotations of a repeatable annotation
     * interface.
     *
     * <p>Note that if this method returns {@link Origin#EXPLICIT
     * EXPLICIT} and the annotation mirror was created from a class
     * file, then the element may not, in fact, correspond to an
     * explicitly declared construct in source code. This is due to
     * limitations of the fidelity of the class file format in
     * preserving information from source code. For example, at least
     * some versions of the class file format do not preserve whether
     * an annotation was explicitly declared by the programmer or was
     * implicitly declared as a <em>container annotation</em>.
     *
     * @implSpec The default implementation of this method returns
     * {@link Origin#EXPLICIT EXPLICIT}.
     *
     * @param c the construct the annotation mirror modifies
     * @param a the annotation mirror being examined
     * @jls 9.6.3 Repeatable Annotation Interfaces
     * @jls 9.7.5 Multiple Annotations of the Same Interface
     * @since 9
     */
    default Origin getOrigin(AnnotatedConstruct c,
                             AnnotationMirror a) {
        return Origin.EXPLICIT;
    }

    /**
     * {@return the <em>origin</em> of the given module directive}
     *
     * <p>Note that if this method returns {@link Origin#EXPLICIT
     * EXPLICIT} and the module directive was created from a class
     * file, then the module directive may not, in fact, correspond to
     * an explicitly declared construct in source code. This is due to
     * limitations of the fidelity of the class file format in
     * preserving information from source code. For example, at least
     * some versions of the class file format do not preserve whether
     * a {@code uses} directive was explicitly declared by the
     * programmer or was added as a synthetic construct.
     *
     * <p>Note that an implementation may not be able to reliably
     * determine the origin status of the directive if the directive
     * is created from a class file due to limitations of the fidelity
     * of the class file format in preserving information from source
     * code.
     *
     * @implSpec The default implementation of this method returns
     * {@link Origin#EXPLICIT EXPLICIT}.
     *
     * @param m the module of the directive
     * @param directive  the module directive being examined
     * @since 9
     */
    default Origin getOrigin(ModuleElement m,
                             ModuleElement.Directive directive) {
        return Origin.EXPLICIT;
    }

    /**
     * The <em>origin</em> of an element or other language model
     * item. The origin of an element or item models how a construct
     * in a program is declared in the source code, explicitly,
     * implicitly, etc.
     *
     * <p>Note that it is possible additional kinds of origin values
     * will be added in future versions of the platform.
     *
     * @jls 13.1 The Form of a Binary
     * @since 9
     */
    public enum Origin {
        /**
         * Describes a construct explicitly declared in source code.
         */
        EXPLICIT,

        /**
         * A mandated construct is one that is not explicitly declared
         * in the source code, but whose presence is mandated by the
         * specification. Such a construct is said to be implicitly
         * declared.
         *
         * One example of a mandated element is a <em>default
         * constructor</em> in a class that contains no explicit
         * constructor declarations.
         *
         * Another example of a mandated construct is an implicitly
         * declared <em>container annotation</em> used to hold
         * multiple annotations of a repeatable annotation interface.
         *
         * @jls 8.8.9 Default Constructor
         * @jls 8.9.3 Enum Members
         * @jls 8.10.3 Record Members
         * @jls 9.6.3 Repeatable Annotation Interfaces
         * @jls 9.7.5 Multiple Annotations of the Same Interface
         */
        MANDATED,

        /**
         * A synthetic construct is one that is neither implicitly nor
         * explicitly declared in the source code. Such a construct is
         * typically a translation artifact created by a compiler.
         */
        SYNTHETIC;

        /**
         * Returns {@code true} for values corresponding to constructs
         * that are implicitly or explicitly declared, {@code false}
         * otherwise.
         * @return {@code true} for {@link #EXPLICIT} and {@link #MANDATED},
         *         {@code false} otherwise.
         */
        public boolean isDeclared() {
            return this != SYNTHETIC;
        }
    }

    /**
     * {@return {@code true} if the executable element is a bridge
     * method, {@code false} otherwise}
     *
     * @implSpec The default implementation of this method returns {@code false}.
     *
     * @param e  the executable being examined
     * @since 9
     */
    default boolean isBridge(ExecutableElement e) {
        return false;
    }

    /**
     * {@return the <i>binary name</i> of a type element}
     *
     * @param type  the type element being examined
     *
     * @see TypeElement#getQualifiedName
     * @jls 13.1 The Form of a Binary
     */
    Name getBinaryName(TypeElement type);


    /**
     * {@return the package of an element}  The package of a package is
     * itself.
     * The package of a module is {@code null}.
     *
     * The package of a top-level class or interface is its {@linkplain
     * TypeElement#getEnclosingElement enclosing package}. Otherwise,
     * the package of an element is equal to the package of the
     * {@linkplain Element#getEnclosingElement enclosing element}.
     *
     * @param e the element being examined
     */
    PackageElement getPackageOf(Element e);

    /**
     * {@return the module of an element}  The module of a module is
     * itself.
     *
     * If a package has a module as its {@linkplain
     * PackageElement#getEnclosingElement enclosing element}, that
     * module is the module of the package. If the enclosing element
     * of a package is {@code null}, {@code null} is returned for the
     * package's module.
     *
     * (One situation where a package may have a {@code null} module
     * is if the environment does not include modules, such as an
     * annotation processing environment configured for a {@linkplain
     * javax.annotation.processing.ProcessingEnvironment#getSourceVersion
     * source version} without modules.)
     *
     * Otherwise, the module of an element is equal to the module
     * {@linkplain #getPackageOf(Element) of the package} of the
     * element.
     *
     * @implSpec The default implementation of this method returns
     * {@code null}.
     *
     * @param e the element being examined
     * @since 9
     */
    default ModuleElement getModuleOf(Element e) {
        return null;
    }

    /**
     * Returns all members of a type element, whether inherited or
     * declared directly.  For a class, the result also includes its
     * constructors, but not local or anonymous classes.
     *
     * @apiNote Elements of certain kinds can be isolated using
     * methods in {@link ElementFilter}.
     *
     * @param type  the type being examined
     * @return all members of the type
     * @see Element#getEnclosedElements
     */
    List<? extends Element> getAllMembers(TypeElement type);

    /**
     * {@return the outermost type element an element is contained in
     * if such a containing element exists; otherwise returns {@code
     * null}}
     *
     * {@linkplain ModuleElement Modules} and {@linkplain
     * PackageElement packages} do <em>not</em> have a containing type
     * element and therefore {@code null} is returned for those kinds
     * of elements.
     *
     * A {@linkplain NestingKind#TOP_LEVEL top-level} class or
     * interface is its own outermost type element.
     *
     * @implSpec
     * The default implementation of this method first checks the kind
     * of the argument. For elements of kind {@code PACKAGE}, {@code
     * MODULE}, and {@code OTHER}, {@code null} is returned. For
     * elements of other kinds, the element is examined to see if it
     * is a top-level class or interface. If so, that element is
     * returned; otherwise, the {@linkplain
     * Element#getEnclosingElement enclosing element} chain is
     * followed until a top-level class or interface is found. The
     * element for the eventual top-level class or interface is
     * returned.
     *
     * @param e the element being examined
     * @see Element#getEnclosingElement
     * @since 18
     */
    default TypeElement getOutermostTypeElement(Element e) {
        return switch (e.getKind()) {
        case PACKAGE,
             MODULE  -> null; // Per the general spec above.
        case OTHER   -> null; // Outside of base model of the javax.lang.model API

        // Elements of all remaining kinds should be enclosed in some
        // sort of class or interface. Check to see if the element is
        // a top-level type; if so, return it. Otherwise, keep going
        // up the enclosing element chain until a top-level type is
        // found.
        default -> {
            Element enclosing = e;
            // This implementation is susceptible to infinite loops
            // for misbehaving element implementations.
            while (true) {
                // Conceptual instanceof TypeElement check. If the
                // argument is a type element, put it into a
                // one-element list, otherwise an empty list.
                List<TypeElement> possibleTypeElement = ElementFilter.typesIn(List.of(enclosing));
                if (!possibleTypeElement.isEmpty()) {
                    TypeElement typeElement = possibleTypeElement.get(0);
                    if (typeElement.getNestingKind() == NestingKind.TOP_LEVEL) {
                        yield typeElement;
                    }
                }
                enclosing = enclosing.getEnclosingElement();
            }
        }
        };
    }

    /**
     * Returns all annotations <i>present</i> on an element, whether
     * directly present or present via inheritance.
     *
     * <p>Note that any annotations returned by this method are
     * declaration annotations.
     *
     * @param e  the element being examined
     * @return all annotations of the element
     * @see Element#getAnnotationMirrors
     * @see javax.lang.model.AnnotatedConstruct
     */
    List<? extends AnnotationMirror> getAllAnnotationMirrors(Element e);

    /**
     * Tests whether one type, method, or field hides another.
     *
     * @param hider   the first element
     * @param hidden  the second element
     * @return {@code true} if and only if the first element hides
     *          the second
     * @jls 8.4.8 Inheritance, Overriding, and Hiding
     */
    boolean hides(Element hider, Element hidden);

    /**
     * Tests whether one method, as a member of a given class or interface,
     * overrides another method.
     * When a non-abstract method overrides an abstract one, the
     * former is also said to <i>implement</i> the latter.
     * As implied by JLS {@jls 8.4.8.1}, a method does <em>not</em>
     * override itself. The overrides relation is <i>irreflexive</i>.
     *
     * <p> In the simplest and most typical usage, the value of the
     * {@code type} parameter will simply be the class or interface
     * directly enclosing {@code overrider} (the possibly-overriding
     * method).  For example, suppose {@code m1} represents the method
     * {@code String.hashCode} and {@code m2} represents {@code
     * Object.hashCode}.  We can then ask whether {@code m1} overrides
     * {@code m2} within the class {@code String} (it does):
     *
     * <blockquote>
     * {@code assert elements.overrides(m1, m2,
     *          elements.getTypeElement("java.lang.String")); }
     * </blockquote>
     *
     * A more interesting case can be illustrated by the following example
     * in which a method in class {@code A} does not override a
     * like-named method in interface {@code B}:
     *
     * <blockquote>
     * {@code class A { public void m() {} } }<br>
     * {@code interface B { void m(); } }<br>
     * ...<br>
     * {@code m1 = ...;  // A.m }<br>
     * {@code m2 = ...;  // B.m }<br>
     * {@code assert ! elements.overrides(m1, m2,
     *          elements.getTypeElement("A")); }
     * </blockquote>
     *
     * When viewed as a member of a third class {@code C}, however,
     * the method in {@code A} does override the one in {@code B}:
     *
     * <blockquote>
     * {@code class C extends A implements B {} }<br>
     * ...<br>
     * {@code assert elements.overrides(m1, m2,
     *          elements.getTypeElement("C")); }
     * </blockquote>
     *
     * Consistent with the usage of the {@link Override @Override}
     * annotation, if an interface declares a method
     * override-equivalent to a {@code public} method of {@link Object
     * java.lang.Object}, such a method of the interface is regarded
     * as overriding the corresponding {@code Object} method; for
     * example:
     *
     * {@snippet lang=java :
     * interface I {
     *   @Override
     *   String toString();
     * }
     * ...
     * assert elements.overrides(elementForItoString,
     *                           elementForObjecttoString,
     *                           elements.getTypeElement("I"));
     * }
     *
     * @apiNote This method examines the method's name, signature, subclass relationship, and accessibility
     * in determining whether one method overrides another, as specified in JLS {@jls 8.4.8.1}.
     * In addition, an implementation may have stricter checks including method modifiers, return types and
     * exception types as described in JLS {@jls 8.4.8.1} and {@jls 8.4.8.3}.
     * Note that such additional compile-time checks are not guaranteed and may vary between implementations.
     *
     * @param overrider  the first method, possible overrider
     * @param overridden  the second method, possibly being overridden
     * @param type   the class or interface of which the first method is a member
     * @return {@code true} if and only if the first method overrides
     *          the second
     * @jls 8.4.8 Inheritance, Overriding, and Hiding
     * @jls 9.4.1 Inheritance and Overriding
     */
    boolean overrides(ExecutableElement overrider, ExecutableElement overridden,
                      TypeElement type);

    /**
     * Returns the text of a <i>constant expression</i> representing a
     * primitive value or a string.
     * The text returned is in a form suitable for representing the value
     * in source code.
     *
     * @param value  a primitive value or string
     * @return the text of a constant expression
     * @throws IllegalArgumentException if the argument is not a primitive
     *          value or string
     *
     * @see VariableElement#getConstantValue()
     */
    String getConstantExpression(Object value);

    /**
     * Prints a representation of the elements to the given writer in
     * the specified order.  The main purpose of this method is for
     * diagnostics.  The exact format of the output is <em>not</em>
     * specified and is subject to change.
     *
     * @param w the writer to print the output to
     * @param elements the elements to print
     */
    void printElements(java.io.Writer w, Element... elements);

    /**
     * {@return a name with the same sequence of characters as the
     * argument}
     *
     * @param cs the character sequence to return as a name
     */
    Name getName(CharSequence cs);

    /**
     * {@return {@code true} if the type element is a functional
     * interface, {@code false} otherwise}
     *
     * @param type the type element being examined
     * @jls 9.8 Functional Interfaces
     * @since 1.8
     */
    boolean isFunctionalInterface(TypeElement type);

    /**
     * {@return {@code true} if the module element is an automatic
     * module, {@code false} otherwise}
     *
     * @implSpec
     * The default implementation of this method returns {@code
     * false}.
     *
     * @param module the module element being examined
     * @jls 7.7.1 Dependences
     * @since 17
     */
    default boolean isAutomaticModule(ModuleElement module) {
        return false;
    }

    /**
     * {@return the class body of an {@code enum} constant if the
     * argument is an {@code enum} constant declared with an optional
     * class body, {@code null} otherwise}
     *
     * @implSpec
     * The default implementation of this method throws {@code
     * UnsupportedOperationException} if the argument is an {@code
     * enum} constant and throws an {@code IllegalArgumentException}
     * if it is not.
     *
     * @param enumConstant an enum constant
     * @throws IllegalArgumentException if the argument is not an {@code enum} constant
     * @jls 8.9.1 Enum Constants
     * @since 22
     */
    default TypeElement getEnumConstantBody(VariableElement enumConstant) {
        switch(enumConstant.getKind()) {
        case ENUM_CONSTANT -> throw new UnsupportedOperationException();
        default            -> throw new IllegalArgumentException("Argument not an enum constant");
        }
    }

    /**
     * Returns the record component for the given accessor. Returns
     * {@code null} if the given method is not a record component
     * accessor.
     *
     * @implSpec The default implementation of this method checks if the element
     * enclosing the accessor has kind {@link ElementKind#RECORD RECORD}, if that is
     * the case, then all the record components of the accessor's enclosing element
     * are isolated by invoking {@link ElementFilter#recordComponentsIn(Iterable)}.
     * If the accessor of at least one of the record components retrieved happens to
     * be equal to the accessor passed as a parameter to this method, then that
     * record component is returned, in any other case {@code null} is returned.
     *
     * @param accessor the method for which the record component should be found.
     * @return the record component, or {@code null} if the given
     * method is not a record component accessor
     * @since 16
     */
    default RecordComponentElement recordComponentFor(ExecutableElement accessor) {
        if (accessor.getEnclosingElement().getKind() == ElementKind.RECORD) {
            for (RecordComponentElement rec : ElementFilter.recordComponentsIn(accessor.getEnclosingElement().getEnclosedElements())) {
                if (Objects.equals(rec.getAccessor(), accessor)) {
                    return rec;
                }
            }
        }
        return null;
    }

    /**
     * {@return {@code true} if the executable element can be
     * determined to be a canonical constructor of a record, {@code
     * false} otherwise}
     * Note that in some cases there may be insufficient information
     * to determine if a constructor is a canonical constructor, such
     * as if the executable element is built backed by a class
     * file. In such cases, {@code false} is returned.
     *
     * @implSpec
     * The default implementation of this method unconditionally
     * returns {@code false}.
     *
     * @param e  the executable being examined
     * @jls 8.10.4.1 Normal Canonical Constructors
     * @since 20
     */
    default boolean isCanonicalConstructor(ExecutableElement e) {
        return false;
    }

    /**
     * {@return {@code true} if the executable element can be
     * determined to be a compact constructor of a record, {@code
     * false} otherwise}
     * By definition, a compact constructor is also a {@linkplain
     * #isCanonicalConstructor(ExecutableElement) canonical
     * constructor}.
     * Note that in some cases there may be insufficient information
     * to determine if a constructor is a compact constructor, such as
     * if the executable element is built backed by a class file. In
     * such cases, {@code false} is returned.
     *
     * @implSpec
     * The default implementation of this method unconditionally
     * returns {@code false}.
     *
     * @param e  the executable being examined
     * @jls 8.10.4.2 Compact Canonical Constructors
     * @since 20
     */
    default boolean isCompactConstructor(ExecutableElement e) {
        return false;
    }

    /**
     * {@return the file object for this element or {@code null} if
     * there is no such file object}
     *
     * <p>The returned file object is for the {@linkplain
     * javax.lang.model.element##accurate_model reference
     * representation} of the information used to construct the
     * element. For example, if during compilation or annotation
     * processing, a source file for class {@code Foo} is compiled
     * into a class file, the file object returned for the element
     * representing {@code Foo} would be for the source file and
     * <em>not</em> for the class file.
     *
     * <p>An implementation may choose to not support the
     * functionality of this method, in which case {@link
     * UnsupportedOperationException} is thrown.
     *
     * <p>In the context of annotation processing, a non-{@code null}
     * value is returned if the element was included as part of the
     * initial inputs or the containing file was created during the
     * run of the annotation processing tool. Otherwise, a {@code
     * null} may be returned. In annotation processing, if a
     * {@linkplain javax.annotation.processing.Filer#createClassFile
     * class file is created}, that class file can serve as the
     * reference representation for elements.
     *
     * <p>If it has a file object, the file object for a package will
     * be a {@code package-info} file. A package may exist and not
     * have any {@code package-info} file even if the package is
     * (implicitly) created during an annotation processing run from
     * the creation of source or class files in that package.  An
     * {@linkplain PackageElement#isUnnamed unnamed package} will have
     * a {@code null} file since it cannot be declared in a
     * compilation unit.
     *
     * <p>If it has a file object, the file object for a module will
     * be a {@code module-info} file.  An {@linkplain
     * ModuleElement#isUnnamed unnamed module} will have a {@code
     * null} file since it cannot be declared in a compilation unit.
     * An {@linkplain #isAutomaticModule automatic module} will have a
     * {@code null} file since it is implicitly declared.
     *
     * <p>If it has a file object, the file object for a top-level
     * {@code public} class or interface will be a source or class
     * file corresponding to that class or interface. In this case,
     * typically the leading portion of the name of the file will
     * match the name of the class or interface. A single compilation
     * unit can define multiple top-level classes and interfaces, such
     * as a primary {@code public} class or interfaces whose name
     * corresponds to the file name and one or more <em>auxiliary</em>
     * classes or interfaces whose names do not correspond to the file
     * name. If a source file is providing the reference
     * representation of an auxiliary class or interface, the file for
     * the primary class is returned. (An auxiliary class or interface
     * can also be defined in a {@code package-info} source file, in
     * which case the file for the {@code package-info} file is
     * returned.)  If a class file is providing the reference
     * representation of an auxiliary class or interface, the separate
     * class file for the auxiliary class is returned.
     *
     * <p>For a nested class or interface, if it has a file object:
     *
     * <ul>
     *
     * <li>if a source file is providing the reference representation,
     * the file object will be that of the {@linkplain
     * #getOutermostTypeElement(Element) outermost enclosing} class or
     * interface
     *
     * <li>if a class file is providing the reference representation,
     * the file object will be that of the nested class or interface
     * itself
     *
     * </ul>
     *
     * <p>For other lexically enclosed elements, such as {@linkplain
     * VariableElement#getEnclosingElement() variables}, {@linkplain
     * ExecutableElement#getEnclosingElement() methods, and
     * constructors}, if they have a file object, the file object will
     * be the object associated with the {@linkplain
     * Element#getEnclosingElement() enclosing element} of the
     * lexically enclosed element.
     *
     * @implSpec The default implementation unconditionally throws
     * {@link UnsupportedOperationException}.
     *
     * @throws UnsupportedOperationException if this functionality is
     * not supported
     *
     * @param e the element to find a file object for
     * @since 18
     */
    default javax.tools.JavaFileObject getFileObjectOf(Element e) {
        throw new UnsupportedOperationException();
    }
}
