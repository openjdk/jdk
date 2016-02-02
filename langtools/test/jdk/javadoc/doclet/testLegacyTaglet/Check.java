/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.doclet.taglet.Taglet;

public class Check implements Taglet {

    private static final String TAG_NAME = "check";
    private static final String TAG_HEADER = "Check:";

    private final EnumSet<Location> allowedSet = EnumSet.allOf(Location.class);

    @Override
    public Set<Taglet.Location> getAllowedLocations() {
        return allowedSet;
    }

    /**
     * Return false since the tag is not an inline tag.
     *
     * @return false since the tag is not an inline tag.
     */
    public boolean isInlineTag() {
        return false;
    }

    /**
     * Return the name of this custom tag.
     *
     * @return the name of this tag.
     */
    public String getName() {
        return TAG_NAME;
    }

    /**
     * Given the DocTree representation of this custom tag, return its string
     * representation.
     *
     * @param tag the DocTree representing this custom tag.
     */
    public String toString(DocTree tag) {
        return "<dt><span class=\"simpleTagLabel\">" + TAG_HEADER + ":</span></dt><dd>" +
                tag.toString() + "</dd>\n";
    }

    /**
     * Given an array of DocTrees representing this custom tag, return its string
     * representation.
     *
     * @param tags the array of tags representing this custom tag.
     * @return null to test if the javadoc throws an exception or not.
     */
    public String toString(List<? extends DocTree> tags) {
        return null;
    }
}
