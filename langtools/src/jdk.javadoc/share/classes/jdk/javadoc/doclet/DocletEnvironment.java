/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.doclet;

import java.util.List;
import java.util.Set;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileManager;

import com.sun.source.util.DocTrees;

/**
 * Represents the operating environment of a single invocation
 * of the doclet. This object can be used to access the program
 * structures, various utilities and the user specified elements
 * on the command line.
 *
 * @since 9
 */
public interface DocletEnvironment {
    /**
     * Returns the <a href="package-summary.html#included">included</a>
     * classes, interfaces and enums in all packages.
     *
     * @return a Set containing {@link javax.lang.model.element.TypeElement TypeElements}.
     */
    Set<TypeElement> getIncludedClasses();

    /**
     * Returns an instance of the {@code DocTrees} utility class.
     * This class provides methods to access {@code TreePath}s, {@code DocCommentTree}s
     * and so on.
     * @return a utility class to operate on doc trees.
     */
    DocTrees getDocTrees();

    /**
     * Returns an instance of the {@code Elements} utility class.
     * This class provides methods for operating on
     * {@link javax.lang.model.element.Element elements}.
     * @return a utility class to operate on elements
     */
    Elements getElementUtils();

    /**
     * Returns the selected elements that can be documented.
     *
     * @param elements those that need to be checked
     * @return elements selected, an empty list if none.
     */
    List<Element> getSelectedElements(List<? extends Element> elements);

    /**
     * Returns the elements <a href="package-summary.html#included">specified</a>
     * on the command line, usually PackageElements and TypeElements.
     * If {@code -subpackages} and {@code -exclude} options
     * are used, return all the non-excluded packages.
     *
     * @return elements specified on the command line.
     */
    Set<Element> getSpecifiedElements();

    /**
     * Returns an instance of the {@code Types} utility class.
     * This class provides methods for operating on
     * {@link javax.lang.model.type.TypeMirror type mirrors}.
     * @return a utility class to operate on type mirrors
     */
    Types getTypeUtils();

    /**
     * Indicates if an element is <a href="package-summary.html#included">included</a>.
     *
     * @param e the Element in question
     * @return true if included, false otherwise
     */
    boolean isIncluded(Element e);

    /**
     * Returns the file manager used to read and write files.
     *
     * @return the file manager used to read and write files
     */
    JavaFileManager getJavaFileManager();

    /**
     * Returns the source version of the source files that were read.
     *
     * @return the source version
     */
    SourceVersion getSourceVersion();
}
