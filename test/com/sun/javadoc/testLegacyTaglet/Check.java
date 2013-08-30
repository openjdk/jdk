/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.doclets.Taglet;
import com.sun.javadoc.*;
import java.util.Map;

public class Check implements Taglet {

    private static final String TAG_NAME = "check";
    private static final String TAG_HEADER = "Check:";

    /**
     * Return true since the tag can be used in package documentation.
     *
     * @return true since the tag can be used in package documentation.
     */
    public boolean inPackage() {
        return true;
    }

    /**
     * Return true since the tag can be used in overview documentation.
     *
     * @return true since the tag can be used in overview documentation.
     */
    public boolean inOverview() {
        return true;
    }

    /**
     * Return true since the tag can be used in type (class/interface)
     * documentation.
     *
     * @return true since the tag can be used in type (class/interface)
     * documentation.
     */
    public boolean inType() {
        return true;
    }

    /**
     * Return true since the tag can be used in constructor documentation.
     *
     * @return true since the tag can be used in constructor documentation.
     */
    public boolean inConstructor() {
        return true;
    }

    /**
     * Return true since the tag can be used in field documentation.
     *
     * @return true since the tag can be used in field documentation.
     */
    public boolean inField() {
        return true;
    }

    /**
     * Return true since the tag can be used in method documentation.
     *
     * @return true since the tag can be used in method documentation.
     */
    public boolean inMethod() {
        return true;
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
     * Register this taglet.
     *
     * @param tagletMap the map to register this tag to.
     */
    @SuppressWarnings("unchecked")
    public static void register(Map tagletMap) {
        Check tag = new Check();
        Taglet t = (Taglet) tagletMap.get(tag.getName());
        if (t != null) {
            tagletMap.remove(tag.getName());
        }
        tagletMap.put(tag.getName(), tag);
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
     * Given the tag representation of this custom tag, return its string
     * representation.
     *
     * @param tag the tag representation of this custom tag.
     */
    public String toString(Tag tag) {
        return "<dt><span class=\"strong\">" + TAG_HEADER + ":</span></dt><dd>" + tag.text() +
                "</dd>\n";
    }

    /**
     * Given an array of tags representing this custom tag, return its string
     * representation.
     *
     * @param tags the array of tags representing of this custom tag.
     * @return null to test if the javadoc throws an exception or not.
     */
    public String toString(Tag[] tags) {
        return null;
    }
}
