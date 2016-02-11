/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.doclet.taglet;

import java.util.List;
import java.util.Set;

import com.sun.source.doctree.DocTree;

/**
 * The interface for a custom tag used by Doclets. A custom
 * tag must implement this interface, and must have a public
 * default constructor (i.e. a public constructor with no
 * parameters), by which, the doclet will instantiate and
 * register the custom tag.
 *
 * @since 9
 */

public interface Taglet {

    /**
     * Returns the set of locations in which a taglet may be used.
     * @return the set of locations in which a taglet may be used
     * allowed in or an empty set.
     */
    Set<Location> getAllowedLocations();

    /**
     * Indicates the tag is an inline or a body tag.
     * @return true if this <code>Taglet</code>
     * is an inline tag, false otherwise.
     */
    public abstract boolean isInlineTag();

    /**
     * Returns the name of the tag.
     * @return the name of this custom tag.
     */
    public abstract String getName();

    /**
     * Given the {@link DocTree DocTree} representation of this custom
     * tag, return its string representation, which is output
     * to the generated page.
     * @param tag the <code>Tag</code> representation of this custom tag.
     * @return the string representation of this <code>Tag</code>.
     */
    public abstract String toString(DocTree tag);

    /**
     * Given a List of {@link DocTree DocTrees} representing this custom
     * tag, return its string representation, which is output
     * to the generated page.  This method should
     * return null if this taglet represents an inline or body tag.
     * @param tags the list of <code>DocTree</code>s representing this custom tag.
     * @return the string representation of this <code>Tag</code>.
     */
    public abstract String toString(List<? extends DocTree> tags);

    /**
     * The kind of location.
     */
    public static enum Location {
        /** In an Overview document. */
        OVERVIEW,
        /** In the documentation for a package. */
        PACKAGE,
        /** In the documentation for a class, interface or enum. */
        TYPE,
        /** In the documentation for a constructor. */
        CONSTRUCTOR,
        /** In the documentation for a method. */
        METHOD,
        /** In the documentation for a field. */
        FIELD
    }
}
