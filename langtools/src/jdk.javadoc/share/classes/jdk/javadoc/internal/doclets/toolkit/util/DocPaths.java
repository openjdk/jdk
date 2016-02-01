/*
 * Copyright (c) 1998, 2016, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Standard DocPath objects.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
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

    /** The name of the image file showing a magnifying glass on the search box. */
    public static final DocPath GLASS_IMG = DocPath.create("glass.png");

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

    /** The name of the directory for the jQuery. */
    public static final DocPath JQUERY_FILES = DocPath.create("jquery");

    /** The name of the default jQuery stylesheet file. */
    public static final DocPath JQUERY_STYLESHEET_FILE = DocPath.create("jquery-ui.css");

    /** The name of the default jQuery javascript file. */
    public static final DocPath JQUERY_JS_1_10 = DocPath.create("jquery-1.10.2.js");

    /** The name of the default jQuery javascript file. */
    public static final DocPath JQUERY_JS = DocPath.create("jquery-ui.js");

    /** The name of the default jszip javascript file. */
    public static final DocPath JSZIP = DocPath.create("jszip/dist/jszip.js");

    /** The name of the default jszip javascript file. */
    public static final DocPath JSZIP_MIN = DocPath.create("jszip/dist/jszip.min.js");

    /** The name of the default jszip-utils javascript file. */
    public static final DocPath JSZIPUTILS = DocPath.create("jszip-utils/dist/jszip-utils.js");

    /** The name of the default jszip-utils javascript file. */
    public static final DocPath JSZIPUTILS_MIN = DocPath.create("jszip-utils/dist/jszip-utils.min.js");

    /** The name of the default jszip-utils javascript file. */
    public static final DocPath JSZIPUTILS_IE = DocPath.create("jszip-utils/dist/jszip-utils-ie.js");

    /** The name of the default jszip-utils javascript file. */
    public static final DocPath JSZIPUTILS_IE_MIN = DocPath.create("jszip-utils/dist/jszip-utils-ie.min.js");

    /** The name of the member search index file. */
    public static final DocPath MEMBER_SEARCH_INDEX_JSON = DocPath.create("member-search-index.json");

    /** The name of the member search index zip file. */
    public static final DocPath MEMBER_SEARCH_INDEX_ZIP = DocPath.create("member-search-index.zip");

    /** The name of the file for the overview frame. */
    public static final DocPath OVERVIEW_FRAME = DocPath.create("overview-frame.html");

    /** The name of the file for the overview summary. */
    public static final DocPath OVERVIEW_SUMMARY = DocPath.create("overview-summary.html");

    /** The name of the file for the overview tree. */
    public static final DocPath OVERVIEW_TREE = DocPath.create("overview-tree.html");

    /** The name of the file for the package frame. */
    public static final DocPath PACKAGE_FRAME = DocPath.create("package-frame.html");

    /** The name of the file for the package list. */
    public static final DocPath PACKAGE_LIST = DocPath.create("package-list");

    /** The name of the package search index file. */
    public static final DocPath PACKAGE_SEARCH_INDEX_JSON = DocPath.create("package-search-index.json");

    /** The name of the package search index zipfile. */
    public static final DocPath PACKAGE_SEARCH_INDEX_ZIP = DocPath.create("package-search-index.zip");

    /** The name of the file for the package summary. */
    public static final DocPath PACKAGE_SUMMARY = DocPath.create("package-summary.html");

    /** The name of the file for the package tree. */
    public static final DocPath PACKAGE_TREE = DocPath.create("package-tree.html");

    /** The name of the file for the package usage info. */
    public static final DocPath PACKAGE_USE = DocPath.create("package-use.html");

    /** The name of the sub-package from which resources are read. */
    public static final DocPath RESOURCES = DocPath.create("resources");

    /** The name of the search javascript file. */
    public static final DocPath SEARCH_JS = DocPath.create("search.js");

    /** The name of the file for the serialized form info. */
    public static final DocPath SERIALIZED_FORM = DocPath.create("serialized-form.html");

    /** The name of the directory in which HTML versions of the source code
     *  are generated.
     */
    public static final DocPath SOURCE_OUTPUT = DocPath.create("src-html");

    /** The name of the default stylesheet. */
    public static final DocPath STYLESHEET = DocPath.create("stylesheet.css");

    /** The name of the tag search index file. */
    public static final DocPath TAG_SEARCH_INDEX_JSON = DocPath.create("tag-search-index.json");

    /** The name of the tag search index zip file. */
    public static final DocPath TAG_SEARCH_INDEX_ZIP = DocPath.create("tag-search-index.zip");

    /** The name of the type search index file. */
    public static final DocPath TYPE_SEARCH_INDEX_JSON = DocPath.create("type-search-index.json");

    /** The name of the type search index zip file. */
    public static final DocPath TYPE_SEARCH_INDEX_ZIP = DocPath.create("type-search-index.zip");

    /** The name of the image file for undo button on the search box. */
    public static final DocPath X_IMG = DocPath.create("x.png");

}
