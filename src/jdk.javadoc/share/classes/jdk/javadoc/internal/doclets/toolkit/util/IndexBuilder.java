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

import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.Messages;

import static jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable.Kind.*;

/**
 * Build the mapping of each Unicode character with it's member lists
 * containing members names starting with it. Also build a list for all the
 * Unicode characters which start a member name. Member name is
 * classkind or field or method or constructor name.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @see java.lang.Character
 */
public class IndexBuilder {

    /**
     * Mapping of each Unicode Character with the member list containing
     * members with names starting with it.
     */
    private final Map<Character, SortedSet<Element>> indexmap;

    /**
     * Don't generate deprecated information if true.
     */
    private boolean noDeprecated;

    /**
     * Build this Index only for classes?
     */
    private boolean classesOnly;

    /**
     * Indicates javafx mode.
     */
    private boolean javafx;

    private final BaseConfiguration configuration;
    private final Utils utils;
    private final Comparator<Element> comparator;

    /**
     * Constructor. Build the index map.
     *
     * @param configuration the current configuration of the doclet.
     * @param noDeprecated  true if -nodeprecated option is used,
     *                      false otherwise.
     */
    public IndexBuilder(BaseConfiguration configuration, boolean noDeprecated) {
        this(configuration, noDeprecated, false);
    }

    /**
     * Constructor. Build the index map.
     *
     * @param configuration the current configuration of the doclet.
     * @param noDeprecated  true if -nodeprecated option is used,
     *                      false otherwise.
     * @param classesOnly   Include only classes in index.
     */
    public IndexBuilder(BaseConfiguration configuration, boolean noDeprecated,
                        boolean classesOnly) {
        this.configuration  = configuration;
        this.utils = configuration.utils;

        Messages messages = configuration.getMessages();
        if (classesOnly) {
            messages.notice("doclet.Building_Index_For_All_Classes");
        } else {
            messages.notice("doclet.Building_Index");
        }

        this.noDeprecated = noDeprecated;
        this.classesOnly = classesOnly;
        this.javafx = configuration.getOptions().javafx();
        this.indexmap = new TreeMap<>();
        comparator = classesOnly
                ? utils.makeAllClassesComparator()
                : utils.makeIndexUseComparator();
        buildIndexMap(configuration.docEnv);
    }

    /**
     * Get all the members in all the Packages and all the Classes
     * given on the command line. Form separate list of those members depending
     * upon their names.
     *
     * @param docEnv the doclet environment
     */
    protected void buildIndexMap(DocletEnvironment docEnv)  {
        Set<PackageElement> packages = configuration.getSpecifiedPackageElements();
        Set<TypeElement> classes = configuration.getIncludedTypeElements();
        if (!classesOnly) {
            if (packages.isEmpty()) {
                Set<PackageElement> set = new HashSet<>();
                for (TypeElement aClass : classes) {
                    PackageElement pkg = utils.containingPackage(aClass);
                    if (pkg != null && !pkg.isUnnamed()) {
                        set.add(pkg);
                    }
                }
                adjustIndexMap(set);
            } else {
                adjustIndexMap(packages);
            }
        }
        adjustIndexMap(classes);
        if (!classesOnly) {
            for (TypeElement aClass : classes) {
                if (shouldAddToIndexMap(aClass)) {
                    putMembersInIndexMap(aClass);
                }
            }
            if (configuration.showModules) {
                addModulesToIndexMap();
            }
        }
    }

    /**
     * Put all the members(fields, methods and constructors) in the te
     * to the indexmap.
     *
     * @param te TypeElement whose members will be added to the indexmap.
     */
    protected void putMembersInIndexMap(TypeElement te) {
        VisibleMemberTable vmt = configuration.getVisibleMemberTable(te);
        adjustIndexMap(vmt.getMembers(ANNOTATION_TYPE_FIELDS));
        adjustIndexMap(vmt.getMembers(FIELDS));
        adjustIndexMap(vmt.getMembers(METHODS));
        adjustIndexMap(vmt.getMembers(CONSTRUCTORS));
        adjustIndexMap(vmt.getMembers(ENUM_CONSTANTS));
    }


    /**
     * Adjust list of members according to their names. Check the first
     * character in a member name, and then add the member to a list of members
     * for that particular unicode character.
     *
     * @param elements Array of members.
     */
    protected void adjustIndexMap(Iterable<? extends Element> elements) {
        for (Element element : elements) {
            if (shouldAddToIndexMap(element)) {
                String name = utils.isPackage(element)
                        ? utils.getPackageName((PackageElement)element)
                        : utils.getSimpleName(element);
                char ch = (name.length() == 0) ?
                          '*' :
                          Character.toUpperCase(name.charAt(0));
                Character unicode = ch;
                SortedSet<Element> list = indexmap.computeIfAbsent(unicode,
                        c -> new TreeSet<>(comparator));
                list.add(element);
            }
        }
    }

    /**
     * Add all the modules to index map.
     */
    protected void addModulesToIndexMap() {
        for (ModuleElement mdle : configuration.modules) {
            String mdleName = mdle.getQualifiedName().toString();
            char ch = (mdleName.length() == 0)
                    ? '*'
                    : Character.toUpperCase(mdleName.charAt(0));
            Character unicode = ch;
            SortedSet<Element> list = indexmap.computeIfAbsent(unicode,
                    c -> new TreeSet<>(comparator));
            list.add(mdle);
        }
    }

    /**
     * Should this element be added to the index map?
     */
    protected boolean shouldAddToIndexMap(Element element) {
        if (utils.hasHiddenTag(element)) {
            return false;
        }

        if (utils.isPackage(element))
            // Do not add to index map if -nodeprecated option is set and the
            // package is marked as deprecated.
            return !(noDeprecated && configuration.utils.isDeprecated(element));
        else
            // Do not add to index map if -nodeprecated option is set and if the
            // element is marked as deprecated or the containing package is marked as
            // deprecated.
            return !(noDeprecated &&
                    (configuration.utils.isDeprecated(element) ||
                    configuration.utils.isDeprecated(utils.containingPackage(element))));
    }

    /**
     * Return a map of all the individual member lists with Unicode character.
     *
     * @return Map index map.
     */
    public Map<Character, SortedSet<Element>> getIndexMap() {
        return indexmap;
    }

    /**
     * Return the sorted list of members, for passed Unicode Character.
     *
     * @param index index Unicode character.
     * @return List member list for specific Unicode character.
     */
    public List<? extends Element> getMemberList(Character index) {
        SortedSet<Element> set = indexmap.get(index);
        if (set == null)
            return null;
        List<Element> out = new ArrayList<>();
        out.addAll(set);
        return out;
    }

    /**
     * Array of IndexMap keys, Unicode characters.
     */
    public List<Character> index() {
        return new ArrayList<>(indexmap.keySet());
    }
}
