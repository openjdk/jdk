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

import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

/**
 * Abstraction for immutable relative paths.
 * Paths always use '/' as a separator, and never begin or end with '/'.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class DocPath {
    private final String path;

    /** The empty path. */
    public static final DocPath empty = new DocPath("");

    /** The empty path. */
    public static final DocPath parent = new DocPath("..");

    /**
     * Create a path from a string.
     */
    public static DocPath create(String p) {
        return (p == null) || p.isEmpty() ? empty : new DocPath(p);
    }

    /**
     * Return the path for a class.
     * For example, if the class is java.lang.Object,
     * the path is java/lang/Object.html.
     */
    public static DocPath forClass(Utils utils, TypeElement typeElement) {
        return (typeElement == null)
                ? empty
                : forPackage(utils.containingPackage(typeElement)).resolve(forName(utils, typeElement));
    }

    /**
     * Return the path for the simple name of the class.
     * For example, if the class is java.lang.Object,
     * the path is Object.html.
     */
    public static DocPath forName(Utils utils, TypeElement typeElement) {
        return (typeElement == null) ? empty : new DocPath(utils.getSimpleName(typeElement) + ".html");
    }

    /**
     * Return the path for the package of a class.
     * For example, if the class is java.lang.Object,
     * the path is java/lang.
     */
    public static DocPath forPackage(Utils utils, TypeElement typeElement) {
        return (typeElement == null) ? empty : forPackage(utils.containingPackage(typeElement));
    }

    /**
     * Return the path for a package.
     * For example, if the package is java.lang,
     * the path is java/lang.
     */
    public static DocPath forPackage(PackageElement pkgElement) {
        return pkgElement == null || pkgElement.isUnnamed()
                ? empty
                : DocPath.create(pkgElement.getQualifiedName().toString().replace('.', '/'));
    }

    /**
     * Return the inverse path for a package.
     * For example, if the package is java.lang,
     * the inverse path is ../...
     */
    public static DocPath forRoot(PackageElement pkgElement) {
        String name = (pkgElement == null || pkgElement.isUnnamed())
                ? ""
                : pkgElement.getQualifiedName().toString();
        return new DocPath(name.replace('.', '/').replaceAll("[^/]+", ".."));
    }

    /**
     * Return the relative path from one package to another.
     */
    public static DocPath relativePath(PackageElement from, PackageElement to) {
        return forRoot(from).resolve(forPackage(to));
    }

    protected DocPath(String p) {
        path = (p.endsWith("/") ? p.substring(0, p.length() - 1) : p);
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object other) {
        return (other instanceof DocPath) && path.equals(((DocPath)other).path);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return path.hashCode();
    }

    public DocPath basename() {
        int sep = path.lastIndexOf("/");
        return (sep == -1) ? this : new DocPath(path.substring(sep + 1));
    }

    public DocPath parent() {
        int sep = path.lastIndexOf("/");
        return (sep == -1) ? empty : new DocPath(path.substring(0, sep));
    }

    /**
     * Return the path formed by appending the specified string to the current path.
     */
    public DocPath resolve(String p) {
        if (p == null || p.isEmpty())
            return this;
        if (path.isEmpty())
            return new DocPath(p);
        return new DocPath(path + "/" + p);
    }

    /**
     * Return the path by appending the specified path to the current path.
     */
    public DocPath resolve(DocPath p) {
        if (p == null || p.isEmpty())
            return this;
        if (path.isEmpty())
            return p;
        return new DocPath(path + "/" + p.getPath());
    }

    /**
     * Return the inverse path for this path.
     * For example, if the path is a/b/c, the inverse path is ../../..
     */
    public DocPath invert() {
        return new DocPath(path.replaceAll("[^/]+", ".."));
    }

    /**
     * Return true if this path is empty.
     */
    public boolean isEmpty() {
        return path.isEmpty();
    }

    public DocLink fragment(String fragment) {
        return new DocLink(path, null, fragment);
    }

    public DocLink query(String query) {
        return new DocLink(path, query, null);
    }

    /**
     * Return this path as a string.
     */
    // This is provided instead of using toString() to help catch
    // unintended use of toString() in string concatenation sequences.
    public String getPath() {
        return path;
    }
}
