/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Set;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.LinkedHashSet;

import javax.lang.model.element.*;


/**
 * Filters for selecting just the elements of interest from a
 * collection of elements.  The returned sets and lists are new
 * collections and do use the argument as a backing store.  The
 * methods in this class do not make any attempts to guard against
 * concurrent modifications of the arguments.  The returned sets and
 * lists are mutable but unsafe for concurrent access.  A returned set
 * has the same iteration order as the argument set to a method.
 *
 * <p>If iterables and sets containing {@code null} are passed as
 * arguments to methods in this class, a {@code NullPointerException}
 * will be thrown.
 *
 * <p>Note that a <i>static import</i> statement can make the text of
 * calls to the methods in this class more concise; for example:
 *
 * <blockquote><pre>
 *     import static javax.lang.model.util.ElementFilter.*;
 *     ...
 *         {@code List<VariableElement>} fs = fieldsIn(someClass.getEnclosedElements());
 * </pre></blockquote>
 *
 * @author Joseph D. Darcy
 * @author Scott Seligman
 * @author Peter von der Ah&eacute;
 * @author Martin Buchholz
 * @since 1.6
 */
public class ElementFilter {
    private ElementFilter() {} // Do not instantiate.

    private static final Set<ElementKind> CONSTRUCTOR_KIND =
        Collections.unmodifiableSet(EnumSet.of(ElementKind.CONSTRUCTOR));

    private static final Set<ElementKind> FIELD_KINDS =
        Collections.unmodifiableSet(EnumSet.of(ElementKind.FIELD,
                                               ElementKind.ENUM_CONSTANT));
    private static final Set<ElementKind> METHOD_KIND =
        Collections.unmodifiableSet(EnumSet.of(ElementKind.METHOD));

    private static final Set<ElementKind> PACKAGE_KIND =
        Collections.unmodifiableSet(EnumSet.of(ElementKind.PACKAGE));

    private static final Set<ElementKind> TYPE_KINDS =
        Collections.unmodifiableSet(EnumSet.of(ElementKind.CLASS,
                                               ElementKind.ENUM,
                                               ElementKind.INTERFACE,
                                               ElementKind.ANNOTATION_TYPE));
    /**
     * Returns a list of fields in {@code elements}.
     * @return a list of fields in {@code elements}
     * @param elements the elements to filter
     */
    public static List<VariableElement>
            fieldsIn(Iterable<? extends Element> elements) {
        return listFilter(elements, FIELD_KINDS, VariableElement.class);
    }

    /**
     * Returns a set of fields in {@code elements}.
     * @return a set of fields in {@code elements}
     * @param elements the elements to filter
     */
    public static Set<VariableElement>
            fieldsIn(Set<? extends Element> elements) {
        return setFilter(elements, FIELD_KINDS, VariableElement.class);
    }

    /**
     * Returns a list of constructors in {@code elements}.
     * @return a list of constructors in {@code elements}
     * @param elements the elements to filter
     */
    public static List<ExecutableElement>
            constructorsIn(Iterable<? extends Element> elements) {
        return listFilter(elements, CONSTRUCTOR_KIND, ExecutableElement.class);
    }

    /**
     * Returns a set of constructors in {@code elements}.
     * @return a set of constructors in {@code elements}
     * @param elements the elements to filter
     */
    public static Set<ExecutableElement>
            constructorsIn(Set<? extends Element> elements) {
        return setFilter(elements, CONSTRUCTOR_KIND, ExecutableElement.class);
    }

    /**
     * Returns a list of methods in {@code elements}.
     * @return a list of methods in {@code elements}
     * @param elements the elements to filter
     */
    public static List<ExecutableElement>
            methodsIn(Iterable<? extends Element> elements) {
        return listFilter(elements, METHOD_KIND, ExecutableElement.class);
    }

    /**
     * Returns a set of methods in {@code elements}.
     * @return a set of methods in {@code elements}
     * @param elements the elements to filter
     */
    public static Set<ExecutableElement>
            methodsIn(Set<? extends Element> elements) {
        return setFilter(elements, METHOD_KIND, ExecutableElement.class);
    }

    /**
     * Returns a list of types in {@code elements}.
     * @return a list of types in {@code elements}
     * @param elements the elements to filter
     */
    public static List<TypeElement>
            typesIn(Iterable<? extends Element> elements) {
        return listFilter(elements, TYPE_KINDS, TypeElement.class);
    }

    /**
     * Returns a set of types in {@code elements}.
     * @return a set of types in {@code elements}
     * @param elements the elements to filter
     */
    public static Set<TypeElement>
            typesIn(Set<? extends Element> elements) {
        return setFilter(elements, TYPE_KINDS, TypeElement.class);
    }

    /**
     * Returns a list of packages in {@code elements}.
     * @return a list of packages in {@code elements}
     * @param elements the elements to filter
     */
    public static List<PackageElement>
            packagesIn(Iterable<? extends Element> elements) {
        return listFilter(elements, PACKAGE_KIND, PackageElement.class);
    }

    /**
     * Returns a set of packages in {@code elements}.
     * @return a set of packages in {@code elements}
     * @param elements the elements to filter
     */
    public static Set<PackageElement>
            packagesIn(Set<? extends Element> elements) {
        return setFilter(elements, PACKAGE_KIND, PackageElement.class);
    }

    // Assumes targetKinds and E are sensible.
    private static <E extends Element> List<E> listFilter(Iterable<? extends Element> elements,
                                                          Set<ElementKind> targetKinds,
                                                          Class<E> clazz) {
        List<E> list = new ArrayList<E>();
        for (Element e : elements) {
            if (targetKinds.contains(e.getKind()))
                list.add(clazz.cast(e));
        }
        return list;
    }

    // Assumes targetKinds and E are sensible.
    private static <E extends Element> Set<E> setFilter(Set<? extends Element> elements,
                                                        Set<ElementKind> targetKinds,
                                                        Class<E> clazz) {
        // Return set preserving iteration order of input set.
        Set<E> set = new LinkedHashSet<E>();
        for (Element e : elements) {
            if (targetKinds.contains(e.getKind()))
                set.add(clazz.cast(e));
        }
        return set;
    }
}
