/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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
import javax.lang.model.type.TypeMirror;

/**
 * Represents a package program element.  Provides access to information
 * about the package and its members.
 *
 * @apiNote
 * The represented package may have an explicit {@linkplain
 * javax.lang.model.util.Elements#getFileObjectOf(Element) reference
 * representation} (either source code or executable output) or may be
 * created from implicit information. The explicit and standalone
 * source code construct for a package is typically a {@code
 * package-info.java} file (JLS {@jls 7.4.1}).  A named package
 * without a standalone {@code package-info.java} file can be declared
 * in the package declaration of a {@linkplain NestingKind#TOP_LEVEL
 * top-level} class or interface.  Implicit information is used to
 * model {@linkplain #isUnnamed unnamed packages} (JLS {@jls 7.4.2}).
 * <p>In the context of annotation processing, a package element can
 * be:
 * <ul>
 * <li>created from the initial inputs to a run of the tool
 * <li>created from {@linkplain
 * javax.annotation.processing.Filer#createSourceFile(CharSequence,
 * Element...) source code} or {@linkplain
 * javax.annotation.processing.Filer#createClassFile(CharSequence,
 * Element...) class files} written by a processor
 * <li>{@linkplain
 * javax.lang.model.util.Elements#getAllPackageElements(CharSequence)
 * queried for} in the configured environment
 * </ul>
 *
 * @see javax.lang.model.util.Elements#getPackageOf
 * @since 1.6
 */
public interface PackageElement extends Element, QualifiedNameable {
    /**
     * {@return a {@linkplain javax.lang.model.type.NoType pseudo-type}
     * for this package}
     *
     * @see javax.lang.model.type.NoType
     * @see javax.lang.model.type.TypeKind#PACKAGE
     */
    @Override
    TypeMirror asType();

    /**
     * Returns the fully qualified name of this package.  This is also
     * known as the package's <i>canonical</i> name.  For an
     * {@linkplain #isUnnamed() unnamed package}, an {@linkplain
     * Name##empty_name empty name} is returned.
     *
     * @apiNote The fully qualified name of a named package that is
     * not a subpackage of a named package is its simple name. The
     * fully qualified name of a named package that is a subpackage of
     * another named package consists of the fully qualified name of
     * the containing package, followed by "{@code .}", followed by the simple
     * (member) name of the subpackage.
     *
     * @return the fully qualified name of this package, or an
     * empty name if this is an unnamed package
     * @jls 6.7 Fully Qualified Names and Canonical Names
     */
    Name getQualifiedName();

    /**
     * Returns the simple name of this package.  For an {@linkplain
     * #isUnnamed() unnamed package}, an {@linkplain
     * Name##empty_name empty name} is returned.
     *
     * @return the simple name of this package or an empty name if
     * this is an unnamed package
     */
    @Override
    Name getSimpleName();

    /**
     * {@return the {@linkplain NestingKind#TOP_LEVEL top-level}
     * classes and interfaces within this package}  Note that
     * subpackages are <em>not</em> considered to be enclosed by a
     * package.
     */
    @Override
    List<? extends Element> getEnclosedElements();

    /**
     * {@return {@code true} if this is an unnamed package and {@code
     * false} otherwise}
     *
     * @jls 7.4.2 Unnamed Packages
     */
    boolean isUnnamed();

    /**
     * {@return the enclosing module if such a module exists; otherwise
     * {@code null}}
     *
     * One situation where a module does not exist for a package is if
     * the environment does not include modules, such as an annotation
     * processing environment configured for a {@linkplain
     * javax.annotation.processing.ProcessingEnvironment#getSourceVersion
     * source version} without modules.
     */
    @Override
    Element getEnclosingElement();
}
