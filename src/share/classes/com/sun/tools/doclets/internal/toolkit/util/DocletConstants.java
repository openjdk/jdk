/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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
 * Stores all constants for a Doclet.  Extend this class if you have doclet
 * specific constants to add.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @author Jamie Ho
 * @since 1.5
 */
public class DocletConstants {

    /**
     * The default package name.
     */
    public static final String DEFAULT_PACKAGE_NAME = "&lt;Unnamed&gt;";

    /**
     * The default package file name.
     */
    public static final String DEFAULT_PACKAGE_FILE_NAME = "default";

    /**
     * The anchor for the default package.
     */
    public static final String UNNAMED_PACKAGE_ANCHOR = "unnamed_package";

    /**
     * The name of the doc files directory.
     */
    public static final String DOC_FILES_DIR_NAME = "doc-files";

    /**
     * The default amount of space between tab stops.
     */
    public static final int DEFAULT_TAB_STOP_LENGTH = 8;

    /**
     * The name of the directory where we will copy resource files to.
     */
    public static final String RESOURE_DIR_NAME = "resources";

    /**
     * The source output directory name
     */
    public static final String SOURCE_OUTPUT_DIR_NAME = "src-html/";

    /**
     * The name of the package list file.
     */
    public static final String PACKAGE_LIST_FILE_NAME = "package-list";

    /**
     * The line seperator for the current operating system.
     */
    public static final String NL = System.getProperty("line.separator");
}
