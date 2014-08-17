/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Standard DocPath objects.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @since 8
 */
public class DocPaths {

    /** The name of the file for all classes, using frames. */
    public static final DocPath ALLCLASSES_FRAME = DocPath.create("allclasses-frame.html");

    /** The name of the file for all classes, without using frames. */
    public static final DocPath ALLCLASSES_NOFRAME = DocPath.create("allclasses-noframe.html");

    /** The name of the sub-directory for storing class usage info. */
    public static final DocPath CLASS_USE = DocPath.create("class-use");

    /** The name of the file for constant values. */
    public static final DocPath CONSTANT_VALUES = DocPath.create("constant-values.html");

    /** The name of the fie for deprecated elements. */
    public static final DocPath DEPRECATED_LIST = DocPath.create("deprecated-list.html");

    /** The name of the subdirectory for user-provided additional documentation files. */
    public static final DocPath DOC_FILES = DocPath.create("doc-files");

    /** The name of the file for help info. */
    public static final DocPath HELP_DOC = DocPath.create("help-doc.html");

    /** The name of the main index file. */
    public static final DocPath INDEX = DocPath.create("index.html");

    /** The name of the single index file for all classes. */
    public static final DocPath INDEX_ALL = DocPath.create("index-all.html");

    /** The name of the directory for the split index files. */
    public static final DocPath INDEX_FILES = DocPath.create("index-files");

    /** Generate the name of one of the files in the split index. */
    public static DocPath indexN(int n) {
        return DocPath.create("index-" + n + ".html");
    }

    /** The name of the default javascript file. */
    public static final DocPath JAVASCRIPT = DocPath.create("script.js");

    /** The name of the file for the overview frame. */
    public static final DocPath OVERVIEW_FRAME = DocPath.create("overview-frame.html");

    /** The name of the file for the overview summary. */
    public static final DocPath OVERVIEW_SUMMARY = DocPath.create("overview-summary.html");

    /** The name of the file for the overview tree. */
    public static final DocPath OVERVIEW_TREE = DocPath.create("overview-tree.html");

    /** The name of the file for the package frame. */
    public static final DocPath PACKAGE_FRAME = DocPath.create("package-frame.html");

    /** The name of the file for the profile frame. */
     public static DocPath profileFrame(String profileName) {
        return DocPath.create(profileName + "-frame.html");
    }

    /** The name of the file for the profile package frame. */
     public static DocPath profilePackageFrame(String profileName) {
        return DocPath.create(profileName + "-package-frame.html");
    }

    /** The name of the file for the profile package summary. */
     public static DocPath profilePackageSummary(String profileName) {
        return DocPath.create(profileName + "-package-summary.html");
    }

    /** The name of the file for the profile summary. */
     public static DocPath profileSummary(String profileName) {
        return DocPath.create(profileName + "-summary.html");
    }

    /** The name of the file for the package list. */
    public static final DocPath PACKAGE_LIST = DocPath.create("package-list");

    /** The name of the file for the package summary. */
    public static final DocPath PACKAGE_SUMMARY = DocPath.create("package-summary.html");

    /** The name of the file for the package tree. */
    public static final DocPath PACKAGE_TREE = DocPath.create("package-tree.html");

    /** The name of the file for the package usage info. */
    public static final DocPath PACKAGE_USE = DocPath.create("package-use.html");

    /** The name of the file for the overview frame. */
    public static final DocPath PROFILE_OVERVIEW_FRAME = DocPath.create("profile-overview-frame.html");

    /** The name of the sub-package from which resources are read. */
    public static final DocPath RESOURCES = DocPath.create("resources");

    /** The name of the file for the serialized form info. */
    public static final DocPath SERIALIZED_FORM = DocPath.create("serialized-form.html");

    /** The name of the directory in which HTML versions of the source code
     *  are generated.
     */
    public static final DocPath SOURCE_OUTPUT = DocPath.create("src-html");

    /** The name of the default stylesheet. */
    public static final DocPath STYLESHEET = DocPath.create("stylesheet.css");

}
