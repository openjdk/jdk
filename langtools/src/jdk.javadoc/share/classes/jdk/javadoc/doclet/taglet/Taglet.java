/*
 * Copyright (c) 2001, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * The interface for a custom taglet supported by doclets such as
 * the {@link jdk.javadoc.doclets.StandardDoclet standard doclet}.
 * Custom taglets are used to handle custom tags in documentation
 * comments.
 *
 * <p>A custom taglet must implement this interface, and must have
 * a public default constructor (i.e. a public constructor with no
 * parameters), by which, the doclet will instantiate and
 * register the custom taglet.
 *
 * @since 9
 */

public interface Taglet {

    /**
     * Returns the set of locations in which a tag may be used.
     * @return the set of locations in which a tag may be used
     */
    Set<Location> getAllowedLocations();

    /**
     * Indicates whether this taglet is for inline tags or not.
     * @return true if this taglet is for an inline tag, and false otherwise
     */
    boolean isInlineTag();

    /**
     * Returns the name of the tag.
     * @return the name of this custom tag.
     */
    String getName();

    /**
     * Returns the string representation of a series of instances of
     * this tag to be included in the generated output.
     * If this taglet is for an {@link #isInlineTag inline} tag} it will
     * be called once per instance of the tag, each time with a singleton list.
     * Otherwise, if this tag is a block tag, it will be called once per
     * comment, with a list of all the instances of the tag in the comment.
     * @param tags the list of {@code DocTree} containing one or more
     *  instances of this tag
     * @return the string representation of the tags to be included in
     *  the generated output
     */
    String toString(List<? extends DocTree> tags);

    /**
     * The kind of location in which a tag may be used.
     */
    public static enum Location {
        /** In an Overview document. */
        OVERVIEW,
        /** In the documentation for a module. */
        MODULE,
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
