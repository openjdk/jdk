/*
 * Copyright (c) 1998, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javadoc;

/**
 * Represents the root of the program structure information
 * for one run of javadoc.  From this root all other program
 * structure information can be extracted.
 * Also represents the command line information -- the
 * packages, classes and options specified by the user.
 *
 * @since 1.2
 * @author Robert Field
 *
 * @deprecated
 *   The declarations in this package have been superseded by those
 *   in the package {@code jdk.javadoc.doclet}.
 *   For more information, see the <i>Migration Guide</i> in the documentation for that package.
 */
@Deprecated
public interface RootDoc extends Doc, DocErrorReporter {

    /**
     * Command line options.
     * <p>
     * For example, given:
     * <pre>
     *     javadoc -foo this that -bar other ...</pre>
     *
     * this method will return:
     * <pre>
     *      options()[0][0] = "-foo"
     *      options()[0][1] = "this"
     *      options()[0][2] = "that"
     *      options()[1][0] = "-bar"
     *      options()[1][1] = "other"</pre>
     *
     * @return an array of arrays of String.
     */
    String[][] options();

    /**
     * Return the packages
     * <a href="package-summary.html#included">specified</a>
     * on the command line.
     * If {@code -subpackages} and {@code -exclude} options
     * are used, return all the non-excluded packages.
     *
     * @return packages specified on the command line.
     */
    PackageDoc[] specifiedPackages();

    /**
     * Return the classes and interfaces
     * <a href="package-summary.html#included">specified</a>
     * as source file names on the command line.
     *
     * @return classes and interfaces specified on the command line.
     */
    ClassDoc[] specifiedClasses();

    /**
     * Return the
     * <a href="package-summary.html#included">included</a>
      classes and interfaces in all packages.
     *
     * @return included classes and interfaces in all packages.
     */
    ClassDoc[] classes();

    /**
     * Return a PackageDoc for the specified package name.
     *
     * @param name package name
     *
     * @return a PackageDoc holding the specified package, null if
     * this package is not referenced.
     */
    PackageDoc packageNamed(String name);

    /**
     * Return a ClassDoc for the specified class or interface name.
     *
     * @param qualifiedName
     * <a href="package-summary.html#qualified">qualified</a>
     * class or package name
     *
     * @return a ClassDoc holding the specified class, null if
     * this class is not referenced.
     */
    ClassDoc classNamed(String qualifiedName);
}
