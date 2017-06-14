/*
 * Copyright (c) 2005, 2017, Oracle and/or its affiliates. All rights reserved.
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

package javax.lang.model.element;

import java.util.List;

/**
 * Represents a module program element.  Provides access to
 * information about the module, its directives, and its members.
 *
 * @see javax.lang.model.util.Elements#getModuleOf
 * @since 9
 * @jls 7.7 Module Declarations
 * @spec JPMS
 */
public interface ModuleElement extends Element, QualifiedNameable {

    /**
     * Returns the fully qualified name of this module.  For an
     * {@linkplain #isUnnamed() unnamed module}, an empty name is returned.
     *
     * @return the fully qualified name of this module, or an
     * empty name if this is an unnamed module
     */
    @Override
    Name getQualifiedName();

    /**
     * Returns the simple name of this module.  For an {@linkplain
     * #isUnnamed() unnamed module}, an empty name is returned.
     *
     * @return the simple name of this module or an empty name if
     * this is an unnamed module
     */
    @Override
    Name getSimpleName();

    /**
     * Returns the packages within this module.
     * @return the packages within this module
     */
    @Override
    List<? extends Element> getEnclosedElements();

    /**
     * Returns {@code true} if this is an open module and {@code
     * false} otherwise.
     *
     * @return {@code true} if this is an open module and {@code
     * false} otherwise
     */ // TODO: add @jls to unnamed module section
    boolean isOpen();

    /**
     * Returns {@code true} if this is an unnamed module and {@code
     * false} otherwise.
     *
     * @return {@code true} if this is an unnamed module and {@code
     * false} otherwise
     */ // TODO: add @jls to unnamed module section
    boolean isUnnamed();

    /**
     * Returns {@code null} since a module is not enclosed by another
     * element.
     *
     * @return {@code null}
     */
    @Override
    Element getEnclosingElement();

    /**
     * Returns the directives contained in the declaration of this module.
     * @return  the directives in the declaration of this module
     */
    List<? extends Directive> getDirectives();

    /**
     * The {@code kind} of a directive.
     *
     * <p>Note that it is possible additional directive kinds will be added
     * to accommodate new, currently unknown, language structures added to
     * future versions of the Java&trade; programming language.
     *
     * @since 9
     * @spec JPMS
     */
    enum DirectiveKind {
        /** A "requires (static|transitive)* module-name" directive. */
        REQUIRES,
        /** An "exports package-name [to module-name-list]" directive. */
        EXPORTS,
        /** An "opens package-name [to module-name-list]" directive. */
        OPENS,
        /** A "uses service-name" directive. */
        USES,
        /** A "provides service-name with implementation-name" directive. */
        PROVIDES
    };

    /**
     * Represents a directive within the declaration of this
     * module. The directives of a module declaration configure the
     * module in the Java Platform Module System.
     *
     * @since 9
     * @spec JPMS
     */
    interface Directive {
        /**
         * Returns the {@code kind} of this directive.
         *
         * @return the kind of this directive
         */
        DirectiveKind getKind();

        /**
         * Applies a visitor to this directive.
         *
         * @param <R> the return type of the visitor's methods
         * @param <P> the type of the additional parameter to the visitor's methods
         * @param v   the visitor operating on this directive
         * @param p   additional parameter to the visitor
         * @return a visitor-specified result
         */
        <R, P> R accept(DirectiveVisitor<R, P> v, P p);
    }

    /**
     * A visitor of module directives, in the style of the visitor design
     * pattern.  Classes implementing this interface are used to operate
     * on a directive when the kind of directive is unknown at compile time.
     * When a visitor is passed to a directive's {@link Directive#accept
     * accept} method, the <code>visit<i>Xyz</i></code> method applicable
     * to that directive is invoked.
     *
     * <p> Classes implementing this interface may or may not throw a
     * {@code NullPointerException} if the additional parameter {@code p}
     * is {@code null}; see documentation of the implementing class for
     * details.
     *
     * <p> <b>WARNING:</b> It is possible that methods will be added to
     * this interface to accommodate new, currently unknown, language
     * structures added to future versions of the Java&trade; programming
     * language. Methods to accommodate new language constructs will
     * be added in a source <em>compatible</em> way using
     * <em>default methods</em>.
     *
     * @param <R> the return type of this visitor's methods.  Use {@link
     *            Void} for visitors that do not need to return results.
     * @param <P> the type of the additional parameter to this visitor's
     *            methods.  Use {@code Void} for visitors that do not need an
     *            additional parameter.
     *
     * @since 9
     * @spec JPMS
     */
    interface DirectiveVisitor<R, P> {
        /**
         * Visits any directive as if by passing itself to that
         * directive's {@link Directive#accept accept} method and passing
         * {@code null} for the additional parameter.
         * The invocation {@code v.visit(d)} is equivalent to
         * {@code d.accept(v, null)}.
         * @param d  the directive to visit
         * @return a visitor-specified result
         * @implSpec This implementation is {@code visit(d, null)}
         */
        default R visit(Directive d) {
            return d.accept(this, null);
        }

        /**
         * Visits any directive as if by passing itself to that
         * directive's {@link Directive#accept accept} method.
         * The invocation {@code v.visit(d, p)} is equivalent to
         * {@code d.accept(v, p)}.
         * @param d  the directive to visit
         * @param p  a visitor-specified parameter
         * @return a visitor-specified result
         */
        default R visit(Directive d, P p) {
            return d.accept(this, p);
        }

        /**
         * Visits a {@code requires} directive.
         * @param d  the directive to visit
         * @param p  a visitor-specified parameter
         * @return a visitor-specified result
         */
        R visitRequires(RequiresDirective d, P p);

        /**
         * Visits an {@code exports} directive.
         * @param d  the directive to visit
         * @param p  a visitor-specified parameter
         * @return a visitor-specified result
         */
        R visitExports(ExportsDirective d, P p);

        /**
         * Visits an {@code opens} directive.
         * @param d  the directive to visit
         * @param p  a visitor-specified parameter
         * @return a visitor-specified result
         */
        R visitOpens(OpensDirective d, P p);

        /**
         * Visits a {@code uses} directive.
         * @param d  the directive to visit
         * @param p  a visitor-specified parameter
         * @return a visitor-specified result
         */
        R visitUses(UsesDirective d, P p);

        /**
         * Visits a {@code provides} directive.
         * @param d  the directive to visit
         * @param p  a visitor-specified parameter
         * @return a visitor-specified result
         */
        R visitProvides(ProvidesDirective d, P p);

        /**
         * Visits an unknown directive.
         * This can occur if the language evolves and new kinds of directive are added.
         * @param d  the directive to visit
         * @param p  a visitor-specified parameter
         * @return a visitor-specified result
         * @throws UnknownDirectiveException a visitor implementation may optionally throw this exception
         * @implSpec This implementation throws {@code new UnknownDirectiveException(d, p)}.
         */
        default R visitUnknown(Directive d, P p) {
            throw new UnknownDirectiveException(d, p);
        }
    }

    /**
     * A dependency of a module.
     * @since 9
     * @spec JPMS
     */
    interface RequiresDirective extends Directive {
        /**
         * Returns whether or not this is a static dependency.
         * @return whether or not this is a static dependency
         */
        boolean isStatic();

        /**
         * Returns whether or not this is a transitive dependency.
         * @return whether or not this is a transitive dependency
         */
        boolean isTransitive();

        /**
         * Returns the module that is required
         * @return the module that is required
         */
        ModuleElement getDependency();
    }

    /**
     * An exported package of a module.
     * @since 9
     * @spec JPMS
     */
    interface ExportsDirective extends Directive {

        /**
         * Returns the package being exported.
         * @return the package being exported
         */
        PackageElement getPackage();

        /**
         * Returns the specific modules to which the package is being exported,
         * or null, if the package is exported to all modules which
         * have readability to this module.
         * @return the specific modules to which the package is being exported
         */
        List<? extends ModuleElement> getTargetModules();
    }

    /**
     * An opened package of a module.
     * @since 9
     * @spec JPMS
     */
    interface OpensDirective extends Directive {

        /**
         * Returns the package being opened.
         * @return the package being opened
         */
        PackageElement getPackage();

        /**
         * Returns the specific modules to which the package is being open
         * or null, if the package is open all modules which
         * have readability to this module.
         * @return the specific modules to which the package is being opened
         */
        List<? extends ModuleElement> getTargetModules();
    }

    /**
     * An implementation of a service provided by a module.
     * @since 9
     * @spec JPMS
     */
    interface ProvidesDirective extends Directive {
        /**
         * Returns the service being provided.
         * @return the service being provided
         */
        TypeElement getService();

        /**
         * Returns the implementations of the service being provided.
         * @return the implementations of the service being provided
         */
        List<? extends TypeElement> getImplementations();
    }

    /**
     * A reference to a service used by a module.
     * @since 9
     * @spec JPMS
     */
    interface UsesDirective extends Directive {
        /**
         * Returns the service that is used.
         * @return the service that is used
         */
        TypeElement getService();
    }
}
