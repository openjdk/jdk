/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime.regexp;

/**
 * Match tuple to keep track of ongoing regexp match.
 */
public final class RegExpResult {
    final Object[] groups;
    final int      index;
    final String   input;

    /**
     * Constructor
     *
     * @param input  regexp input
     * @param index  index of match
     * @param groups groups vector
     */
    public RegExpResult(final String input, final int index, final Object[] groups) {
        this.input  = input;
        this.index  = index;
        this.groups = groups;
    }

    /**
     * Get the groups for the match
     * @return group vector
     */
    public Object[] getGroups() {
        return groups;
    }

    /**
     * Get the input for the map
     * @return input
     */
    public String getInput() {
        return input;
    }

    /**
     * Get the index for the match
     * @return index
     */
    public int getIndex() {
        return index;
    }

    /**
     * Get the length of the match
     * @return length
     */
    public int length() {
        return ((String)groups[0]).length();
    }

    /**
     * Get the group with the given index or the empty string if group index is not valid.
     * @param groupIndex the group index
     * @return the group or ""
     */
    public Object getGroup(final int groupIndex) {
        return groupIndex >= 0 && groupIndex < groups.length ? groups[groupIndex] : "";
    }

    /**
     * Get the last parenthesis group, or the empty string if none exists.
     * @return the last group or ""
     */
    public Object getLastParen() {
        return groups.length > 1 ? groups[groups.length - 1] : "";
    }

}
