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
 * A sample Taglet representing @todo. This tag can be used in any kind of
 * {@link com.sun.javadoc.Doc}.  It is not an inline tag. The text is displayed
 * in yellow to remind the developer to perform a task.  For
 * example, "@todo Fix this!" would be shown as:
 * <DL>
 * <DT>
 * <B>To Do:</B>
 * <DD><table cellpadding=2 cellspacing=0><tr><td bgcolor="yellow">Fix this!
 * </td></tr></table></DD>
 * </DL>
 *
 * @author Jamie Ho
 * @since 1.4
 */

public class ToDoTaglet implements Taglet {

    private static final String NAME = "todo";
    private static final String HEADER = "To Do:";

    /**
     * Return the name of this custom tag.
     */
    public String getName() {
        return NAME;
    }

    /**
     * Will return true since <code>@todo</code>
     * can be used in field documentation.
     * @return true since <code>@todo</code>
     * can be used in field documentation and false
     * otherwise.
     */
    public boolean inField() {
        return true;
    }

    /**
     * Will return true since <code>@todo</code>
     * can be used in constructor documentation.
     * @return true since <code>@todo</code>
     * can be used in constructor documentation and false
     * otherwise.
     */
    public boolean inConstructor() {
        return true;
    }

    /**
     * Will return true since <code>@todo</code>
     * can be used in method documentation.
     * @return true since <code>@todo</code>
     * can be used in method documentation and false
     * otherwise.
     */
    public boolean inMethod() {
        return true;
    }

    /**
     * Will return true since <code>@todo</code>
     * can be used in method documentation.
     * @return true since <code>@todo</code>
     * can be used in overview documentation and false
     * otherwise.
     */
    public boolean inOverview() {
        return true;
    }

    /**
     * Will return true since <code>@todo</code>
     * can be used in package documentation.
     * @return true since <code>@todo</code>
     * can be used in package documentation and false
     * otherwise.
     */
    public boolean inPackage() {
        return true;
    }

    /**
     * Will return true since <code>@todo</code>
     * can be used in type documentation (classes or interfaces).
     * @return true since <code>@todo</code>
     * can be used in type documentation and false
     * otherwise.
     */
    public boolean inType() {
        return true;
    }

    /**
     * Will return false since <code>@todo</code>
     * is not an inline tag.
     * @return false since <code>@todo</code>
     * is not an inline tag.
     */

    public boolean isInlineTag() {
        return false;
    }

    /**
     * Register this Taglet.
     * @param tagletMap  the map to register this tag to.
     */
    public static void register(Map tagletMap) {
       ToDoTaglet tag = new ToDoTaglet();
       Taglet t = (Taglet) tagletMap.get(tag.getName());
       if (t != null) {
           tagletMap.remove(tag.getName());
       }
       tagletMap.put(tag.getName(), tag);
    }

    /**
     * Given the <code>Tag</code> representation of this custom
     * tag, return its string representation.
     * @param tag   the <code>Tag</code> representation of this custom tag.
     */
    public String toString(Tag tag) {
        return "<DT><B>" + HEADER + "</B><DD>"
               + "<table cellpadding=2 cellspacing=0><tr><td bgcolor=\"yellow\">"
               + tag.text()
               + "</td></tr></table></DD>\n";
    }

    /**
     * Given an array of <code>Tag</code>s representing this custom
     * tag, return its string representation.
     * @param tags  the array of <code>Tag</code>s representing of this custom tag.
     */
    public String toString(Tag[] tags) {
        if (tags.length == 0) {
            return null;
        }
        String result = "\n<DT><B>" + HEADER + "</B><DD>";
        result += "<table cellpadding=2 cellspacing=0><tr><td bgcolor=\"yellow\">";
        for (int i = 0; i < tags.length; i++) {
            if (i > 0) {
                result += ", ";
            }
            result += tags[i].text();
        }
        return result + "</td></tr></table></DD>\n";
    }
}
