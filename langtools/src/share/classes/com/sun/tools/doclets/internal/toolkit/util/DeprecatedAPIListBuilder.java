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

import com.sun.javadoc.*;
import java.util.*;
import com.sun.tools.doclets.internal.toolkit.Configuration;

/**
 * Build list of all the deprecated packages, classes, constructors, fields and methods.
 *
 * @author Atul M Dambalkar
 */
public class DeprecatedAPIListBuilder {

    public static final int NUM_TYPES = 12;

    public static final int PACKAGE = 0;
    public static final int INTERFACE = 1;
    public static final int CLASS = 2;
    public static final int ENUM = 3;
    public static final int EXCEPTION = 4;
    public static final int ERROR = 5;
    public static final int ANNOTATION_TYPE = 6;
    public static final int FIELD = 7;
    public static final int METHOD = 8;
    public static final int CONSTRUCTOR = 9;
    public static final int ENUM_CONSTANT = 10;
    public static final int ANNOTATION_TYPE_MEMBER = 11;

    /**
     * List of deprecated type Lists.
     */
    private List<List<Doc>> deprecatedLists;


    /**
     * Constructor.
     *
     * @param configuration the current configuration of the doclet
     */
    public DeprecatedAPIListBuilder(Configuration configuration) {
        deprecatedLists = new ArrayList<List<Doc>>();
        for (int i = 0; i < NUM_TYPES; i++) {
            deprecatedLists.add(i, new ArrayList<Doc>());
        }
        buildDeprecatedAPIInfo(configuration);
    }

    /**
     * Build the sorted list of all the deprecated APIs in this run.
     * Build separate lists for deprecated packages, classes, constructors,
     * methods and fields.
     *
     * @param configuration the current configuration of the doclet.
     */
    private void buildDeprecatedAPIInfo(Configuration configuration) {
        PackageDoc[] packages = configuration.packages;
        PackageDoc pkg;
        for (int c = 0; c < packages.length; c++) {
            pkg = packages[c];
            if (Util.isDeprecated(pkg)) {
                getList(PACKAGE).add(pkg);
            }
        }
        ClassDoc[] classes = configuration.root.classes();
        for (int i = 0; i < classes.length; i++) {
            ClassDoc cd = classes[i];
            if (Util.isDeprecated(cd)) {
                if (cd.isOrdinaryClass()) {
                    getList(CLASS).add(cd);
                } else if (cd.isInterface()) {
                    getList(INTERFACE).add(cd);
                } else if (cd.isException()) {
                    getList(EXCEPTION).add(cd);
                } else if (cd.isEnum()) {
                    getList(ENUM).add(cd);
                } else if (cd.isError()) {
                    getList(ERROR).add(cd);
                } else if (cd.isAnnotationType()) {
                    getList(ANNOTATION_TYPE).add(cd);
                }
            }
            composeDeprecatedList(getList(FIELD), cd.fields());
            composeDeprecatedList(getList(METHOD), cd.methods());
            composeDeprecatedList(getList(CONSTRUCTOR), cd.constructors());
            if (cd.isEnum()) {
                composeDeprecatedList(getList(ENUM_CONSTANT), cd.enumConstants());
            }
            if (cd.isAnnotationType()) {
                composeDeprecatedList(getList(ANNOTATION_TYPE_MEMBER),
                        ((AnnotationTypeDoc) cd).elements());
            }
        }
        sortDeprecatedLists();
    }

    /**
     * Add the members into a single list of deprecated members.
     *
     * @param list List of all the particular deprecated members, e.g. methods.
     * @param members members to be added in the list.
     */
    private void composeDeprecatedList(List<Doc> list, MemberDoc[] members) {
        for (int i = 0; i < members.length; i++) {
            if (Util.isDeprecated(members[i])) {
                list.add(members[i]);
            }
        }
    }

    /**
     * Sort the deprecated lists for class kinds, fields, methods and
     * constructors.
     */
    private void sortDeprecatedLists() {
        for (int i = 0; i < NUM_TYPES; i++) {
            Collections.sort(getList(i));
        }
    }

    /**
     * Return the list of deprecated Doc objects of a given type.
     *
     * @param the constant representing the type of list being returned.
     */
    public List<Doc> getList(int type) {
        return deprecatedLists.get(type);
    }

    /**
     * Return true if the list of a given type has size greater than 0.
     *
     * @param type the type of list being checked.
     */
    public boolean hasDocumentation(int type) {
        return (deprecatedLists.get(type)).size() > 0;
    }
}
