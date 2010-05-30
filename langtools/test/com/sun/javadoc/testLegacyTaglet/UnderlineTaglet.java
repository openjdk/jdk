/*
 * Copyright (c) 2003, 2007, Oracle and/or its affiliates. All rights reserved.
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

/**
 * A sample Inline Taglet representing {@underline ...}. This tag can be used in any kind of
 * {@link com.sun.javadoc.Doc}.  The text is simple underlined.  For
 * example, "@underline UNDERLINE ME" would be shown as: <u>UNDERLINE ME</u>.
 *
 * @author Jamie Ho
 * @since 1.4
 */

public class UnderlineTaglet implements Taglet {

    private String NAME = "underline";

    /**
     * Return the name of this custom tag.
     */
    public String getName() {
        return NAME;
    }

    /**
     * Will return false since this is an inline tag.
     * @return false since this is an inline tag.
     */
    public boolean inField() {
        return false;
    }

    /**
     * Will return false since this is an inline tag.
     * @return false since this is an inline tag.
     */
    public boolean inConstructor() {
        return true;
    }

    /**
     * Will return false since this is an inline tag.
     * @return false since this is an inline tag.
     */
    public boolean inMethod() {
        return false;
    }

    /**
     * Will return false since this is an inline tag.
     * @return false since this is an inline tag.
     */
    public boolean inOverview() {
        return false;
    }

    /**
     * Will return false since this is an inline tag.
     * @return false since this is an inline tag.
     */
    public boolean inPackage() {
        return false;
    }

    /**
     * Will return false since this is an inline tag.
     * @return false since this is an inline tag.
     */
    public boolean inType() {
        return false;
    }

    /**
     * Will return true since this is an inline tag.
     * @return true since this is an inline tag.
     */

    public boolean isInlineTag() {
        return true;
    }

    /**
     * Register this Taglet.
     * @param tagletMap  the map to register this tag to.
     */
    public static void register(Map tagletMap) {
       UnderlineTaglet tag = new UnderlineTaglet();
       Taglet t = (Taglet) tagletMap.get(tag.getName());
       if (t != null) {
           tagletMap.remove(tag.getName());
       }
       tagletMap.put(tag.getName(), tag);
    }

    /**
     * Given the <code>Tag</code> representation of this custom
     * tag, return its string representation.
     * @param tag he <code>Tag</code> representation of this custom tag.
     */
    public String toString(Tag tag) {
        return "<u>" + tag.text() + "</u>";
    }

    /**
     * This method should not be called since arrays of inline tags do not
     * exist.  Method {@link #tostring(Tag)} should be used to convert this
     * inline tag to a string.
     * @param tags the array of <code>Tag</code>s representing of this custom tag.
     */
    public String toString(Tag[] tags) {
        return null;
    }
}
