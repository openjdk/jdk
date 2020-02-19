/*
 * Copyright (c) 1998, 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.util;

import java.util.*;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.Messages;

import static jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable.Kind.*;

/**
 *  An alphabetical index of {@link Element elements}.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class IndexBuilder {

    /**
     * Sets of elements keyed by the first character of the names of the
     * elements in those sets.
     */
    private final Map<Character, SortedSet<Element>> indexMap;

    /**
     * Don't generate deprecated information if true.
     */
    private final boolean noDeprecated;

    /**
     * Build this index only for classes?
     */
    private final boolean classesOnly;

    private final BaseConfiguration configuration;
    private final Utils utils;
    private final Comparator<Element> comparator;

    /**
     * Creates a new {@code IndexBuilder}.
     *
     * @param configuration the current configuration of the doclet
     * @param noDeprecated  true if -nodeprecated option is used,
     *                      false otherwise
     */
    public IndexBuilder(BaseConfiguration configuration,
                        boolean noDeprecated)
    {
        this(configuration, noDeprecated, false);
    }

    /**
     * Creates a new {@code IndexBuilder}.
     *
     * @param configuration the current configuration of the doclet
     * @param noDeprecated  true if -nodeprecated option is used,
     *                      false otherwise
     * @param classesOnly   include only classes in index
     */
    public IndexBuilder(BaseConfiguration configuration,
                        boolean noDeprecated,
                        boolean classesOnly)
    {
        this.configuration = configuration;
        this.utils = configuration.utils;

        Messages messages = configuration.getMessages();
        if (classesOnly) {
            messages.notice("doclet.Building_Index_For_All_Classes");
        } else {
            messages.notice("doclet.Building_Index");
        }

        this.noDeprecated = noDeprecated;
        this.classesOnly = classesOnly;
        this.indexMap = new TreeMap<>();
        comparator = classesOnly
                ? utils.makeAllClassesComparator()
                : utils.makeIndexUseComparator();
        buildIndex();
    }

    /**
     * Indexes all the members in all the packages and all the classes.
     */
    private void buildIndex()  {
        Set<TypeElement> classes = configuration.getIncludedTypeElements();
        indexElements(classes);
        if (classesOnly) {
            return;
        }
        Set<PackageElement> packages = configuration.getSpecifiedPackageElements();
        if (packages.isEmpty()) {
            packages = classes
                    .stream()
                    .map(utils::containingPackage)
                    .filter(_package -> _package != null && !_package.isUnnamed())
                    .collect(Collectors.toSet());
        }
        indexElements(packages);
        classes.stream()
               .filter(this::shouldIndex)
               .forEach(this::indexMembers);

        if (configuration.showModules) {
            indexModules();
        }
    }

    /**
     * Indexes all the members (fields, methods, constructors, etc.) of the
     * provided type element.
     *
     * @param te TypeElement whose members are to be indexed
     */
    private void indexMembers(TypeElement te) {
        VisibleMemberTable vmt = configuration.getVisibleMemberTable(te);
        indexElements(vmt.getMembers(ANNOTATION_TYPE_FIELDS));
        indexElements(vmt.getMembers(FIELDS));
        indexElements(vmt.getMembers(METHODS));
        indexElements(vmt.getMembers(CONSTRUCTORS));
        indexElements(vmt.getMembers(ENUM_CONSTANTS));
    }

    /**
     * Indexes the provided elements.
     *
     * @param elements a collection of elements
     */
    private void indexElements(Iterable<? extends Element> elements) {
        for (Element element : elements) {
            if (shouldIndex(element)) {
                String name = utils.isPackage(element)
                        ? utils.getPackageName((PackageElement) element)
                        : utils.getSimpleName(element);
                Character ch = keyCharacter(name);
                SortedSet<Element> set = indexMap.computeIfAbsent(ch, c -> new TreeSet<>(comparator));
                set.add(element);
            }
        }
    }

    private static Character keyCharacter(String s) {
        return s.isEmpty() ? '*' : Character.toUpperCase(s.charAt(0));
    }

    /**
     * Indexes all the modules.
     */
    private void indexModules() {
        for (ModuleElement m : configuration.modules) {
            Character ch = keyCharacter(m.getQualifiedName().toString());
            SortedSet<Element> set = indexMap.computeIfAbsent(ch, c -> new TreeSet<>(comparator));
            set.add(m);
        }
    }

    /**
     * Should this element be added to the index?
     */
    private boolean shouldIndex(Element element) {
        if (utils.hasHiddenTag(element)) {
            return false;
        }

        if (utils.isPackage(element)) {
            // Do not add to index map if -nodeprecated option is set and the
            // package is marked as deprecated.
            return !(noDeprecated && configuration.utils.isDeprecated(element));
        } else {
            // Do not add to index map if -nodeprecated option is set and if the
            // element is marked as deprecated or the containing package is marked as
            // deprecated.
            return !(noDeprecated &&
                    (configuration.utils.isDeprecated(element) ||
                    configuration.utils.isDeprecated(utils.containingPackage(element))));
        }
    }

    /**
     * Returns a map representation of this index.
     *
     * @return map
     */
    public Map<Character, SortedSet<Element>> asMap() {
        return indexMap;
    }

    /**
     * Returns a sorted list of elements whose names start with the
     * provided character.
     *
     * @param key index key
     * @return list of elements keyed by the provided character
     */
    public List<? extends Element> getMemberList(Character key) {
        SortedSet<Element> set = indexMap.get(key);
        if (set == null) {
            return null;
        }
        return new ArrayList<>(set);
    }

    /**
     * Returns a list of index keys.
     */
    public List<Character> keys() {
        return new ArrayList<>(indexMap.keySet());
    }
}
