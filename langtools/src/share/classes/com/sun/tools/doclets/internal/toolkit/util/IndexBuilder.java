/*
 * Copyright (c) 1998, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.internal.toolkit.util;

import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.javadoc.*;
import java.util.*;

/**
 * Build the mapping of each Unicode character with it's member lists
 * containing members names starting with it. Also build a list for all the
 * Unicode characters which start a member name. Member name is
 * classkind or field or method or constructor name.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @since 1.2
 * @see java.lang.Character
 * @author Atul M Dambalkar
 */
public class IndexBuilder {

    /**
     * Mapping of each Unicode Character with the member list containing
     * members with names starting with it.
     */
    private Map<Character,List<Doc>> indexmap = new HashMap<Character,List<Doc>>();

    /**
     * Don't generate deprecated information if true.
     */
    private boolean noDeprecated;

    /**
     * Build this Index only for classes?
     */
    private boolean classesOnly;

    // make ProgramElementDoc[] when new toArray is available
    protected final Object[] elements;

    /**
     * A comparator used to sort classes and members.
     * Note:  Maybe this compare code belongs in the tool?
     */
    private class DocComparator implements Comparator<Doc> {
        public int compare(Doc d1, Doc d2) {
            String doc1 = d1.name();
            String doc2 = d2.name();
            int compareResult;
            if ((compareResult = doc1.compareToIgnoreCase(doc2)) != 0) {
                return compareResult;
            } else if (d1 instanceof ProgramElementDoc && d2 instanceof ProgramElementDoc) {
                 doc1 = (((ProgramElementDoc) d1).qualifiedName());
                 doc2 = (((ProgramElementDoc) d2).qualifiedName());
                 return doc1.compareToIgnoreCase(doc2);
            } else {
                return 0;
            }
        }
    }

    /**
     * Constructor. Build the index map.
     *
     * @param configuration the current configuration of the doclet.
     * @param noDeprecated  true if -nodeprecated option is used,
     *                      false otherwise.
     */
    public IndexBuilder(Configuration configuration, boolean noDeprecated) {
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
    public IndexBuilder(Configuration configuration, boolean noDeprecated,
                        boolean classesOnly) {
        if (classesOnly) {
            configuration.message.notice("doclet.Building_Index_For_All_Classes");
        } else {
            configuration.message.notice("doclet.Building_Index");
        }
        this.noDeprecated = noDeprecated;
        this.classesOnly = classesOnly;
        buildIndexMap(configuration.root);
        Set<Character> set = indexmap.keySet();
        elements =  set.toArray();
        Arrays.sort(elements);
    }

    /**
     * Sort the index map. Traverse the index map for all it's elements and
     * sort each element which is a list.
     */
    protected void sortIndexMap() {
        for (Iterator<List<Doc>> it = indexmap.values().iterator(); it.hasNext(); ) {
            Collections.sort(it.next(), new DocComparator());
        }
    }

    /**
     * Get all the members in all the Packages and all the Classes
     * given on the command line. Form separate list of those members depending
     * upon their names.
     *
     * @param root Root of the documemt.
     */
    protected void buildIndexMap(RootDoc root)  {
        PackageDoc[] packages = root.specifiedPackages();
        ClassDoc[] classes = root.classes();
        if (!classesOnly) {
            if (packages.length == 0) {
                Set<PackageDoc> set = new HashSet<PackageDoc>();
                PackageDoc pd;
                for (int i = 0; i < classes.length; i++) {
                    pd = classes[i].containingPackage();
                    if (pd != null && pd.name().length() > 0) {
                        set.add(pd);
                    }
                }
                adjustIndexMap(set.toArray(packages));
            } else {
                adjustIndexMap(packages);
            }
        }
        adjustIndexMap(classes);
        if (!classesOnly) {
            for (int i = 0; i < classes.length; i++) {
                if (shouldAddToIndexMap(classes[i])) {
                    putMembersInIndexMap(classes[i]);
                }
            }
        }
        sortIndexMap();
    }

    /**
     * Put all the members(fields, methods and constructors) in the classdoc
     * to the indexmap.
     *
     * @param classdoc ClassDoc whose members will be added to the indexmap.
     */
    protected void putMembersInIndexMap(ClassDoc classdoc) {
        adjustIndexMap(classdoc.fields());
        adjustIndexMap(classdoc.methods());
        adjustIndexMap(classdoc.constructors());
    }


    /**
     * Adjust list of members according to their names. Check the first
     * character in a member name, and then add the member to a list of members
     * for that particular unicode character.
     *
     * @param elements Array of members.
     */
    protected void adjustIndexMap(Doc[] elements) {
        for (int i = 0; i < elements.length; i++) {
            if (shouldAddToIndexMap(elements[i])) {
                String name = elements[i].name();
                char ch = (name.length()==0)?
                    '*' :
                    Character.toUpperCase(name.charAt(0));
                Character unicode = new Character(ch);
                List<Doc> list = indexmap.get(unicode);
                if (list == null) {
                    list = new ArrayList<Doc>();
                    indexmap.put(unicode, list);
                }
                list.add(elements[i]);
            }
        }
    }

    /**
     * Should this doc element be added to the index map?
     */
    protected boolean shouldAddToIndexMap(Doc element) {
        if (element instanceof PackageDoc)
            // Do not add to index map if -nodeprecated option is set and the
            // package is marked as deprecated.
            return !(noDeprecated && Util.isDeprecated(element));
        else
            // Do not add to index map if -nodeprecated option is set and if the
            // Doc is marked as deprecated or the containing package is marked as
            // deprecated.
            return !(noDeprecated &&
                    (Util.isDeprecated(element) ||
                    Util.isDeprecated(((ProgramElementDoc)element).containingPackage())));
    }

    /**
     * Return a map of all the individual member lists with Unicode character.
     *
     * @return Map index map.
     */
    public Map<Character,List<Doc>> getIndexMap() {
        return indexmap;
    }

    /**
     * Return the sorted list of members, for passed Unicode Character.
     *
     * @param index index Unicode character.
     * @return List member list for specific Unicode character.
     */
    public List<Doc> getMemberList(Character index) {
        return indexmap.get(index);
    }

    /**
     * Array of IndexMap keys, Unicode characters.
     */
    public Object[] elements() {
        return elements;
    }
}
