/*
 * Copyright (c) 2002, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.*;

/**
 * Provides methods for creating an array of class, method and
 * field names to be included as meta keywords in the HTML header
 * of class pages.  These keywords improve search results
 * on browsers that look for keywords.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Doug Kramer
 */
public class MetaKeywords {

    /**
     * The global configuration information for this run.
     */
    private final Configuration configuration;
    private final Utils utils;

    /**
     * Constructor
     */
    public MetaKeywords(Configuration configuration) {
        this.configuration = configuration;
        this.utils = configuration.utils;
    }

    /**
     * Returns an array of strings where each element
     * is a class, method or field name.  This array is
     * used to create one meta keyword tag for each element.
     * Method parameter lists are converted to "()" and
     * overloads are combined.
     *
     * Constructors are not included because they have the same
     * name as the class, which is already included.
     * Nested class members are not included because their
     * definitions are on separate pages.
     */
    public String[] getMetaKeywords(ClassDoc classdoc) {
        ArrayList<String> results = new ArrayList<>();

        // Add field and method keywords only if -keywords option is used
        if( configuration.keywords ) {
            results.addAll(getClassKeyword(classdoc));
            results.addAll(getMemberKeywords(classdoc.fields()));
            results.addAll(getMemberKeywords(classdoc.methods()));
        }
        return results.toArray(new String[]{});
    }

    /**
     * Get the current class for a meta tag keyword, as the first
     * and only element of an array list.
     */
    protected ArrayList<String> getClassKeyword(ClassDoc classdoc) {
        String cltypelower = classdoc.isInterface() ? "interface" : "class";
        ArrayList<String> metakeywords = new ArrayList<>(1);
        metakeywords.add(classdoc.qualifiedName() + " " + cltypelower);
        return metakeywords;
    }

    /**
     * Get the package keywords.
     */
    public String[] getMetaKeywords(PackageDoc packageDoc) {
        if( configuration.keywords ) {
            String pkgName = utils.getPackageName(packageDoc);
            return new String[] { pkgName + " " + "package" };
        } else {
            return new String[] {};
        }
    }

    /**
     * Get the overview keywords.
     */
    public String[] getOverviewMetaKeywords(String title, String docTitle) {
        if( configuration.keywords ) {
            String windowOverview = configuration.getText(title);
            String[] metakeywords = { windowOverview };
            if (docTitle.length() > 0 ) {
                metakeywords[0] += ", " + docTitle;
            }
            return metakeywords;
        } else {
            return new String[] {};
        }
    }

    /**
     * Get members for meta tag keywords as an array,
     * where each member name is a string element of the array.
     * The parameter lists are not included in the keywords;
     * therefore all overloaded methods are combined.<br>
     * Example: getValue(Object) is returned in array as getValue()
     *
     * @param memberdocs  array of MemberDoc objects to be added to keywords
     */
    protected ArrayList<String> getMemberKeywords(MemberDoc[] memberdocs) {
        ArrayList<String> results = new ArrayList<>();
        String membername;
        for (MemberDoc memberdoc : memberdocs) {
            membername = memberdoc.name() + (memberdoc.isMethod() ? "()" : "");
            if (!results.contains(membername)) {
                results.add(membername);
            }
        }
        return results;
    }
}
