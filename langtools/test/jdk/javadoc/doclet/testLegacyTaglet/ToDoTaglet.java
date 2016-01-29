/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Map;

import java.util.Set;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import com.sun.source.doctree.UnknownInlineTagTree;
import com.sun.source.util.SimpleDocTreeVisitor;
import jdk.javadoc.doclet.taglet.Taglet;
import jdk.javadoc.doclet.taglet.Taglet.Location;
import static jdk.javadoc.doclet.taglet.Taglet.Location.*;


/**
 * A sample Taglet representing @todo. This tag can be used in any kind of
 * {@link javax.lang.model.Element}.  It is not an inline tag. The text is displayed
 * in yellow to remind the developer to perform a task.  For
 * example, "@todo Fix this!" would be shown as:
 * <DL>
 * <DT>
 * <B>To Do:</B>
 * <DD><table summary="Summary" cellpadding=2 cellspacing=0><tr><td bgcolor="yellow">Fix this!
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

    private final EnumSet<Location> allowedSet = EnumSet.allOf(Location.class);

    @Override
    public Set<Taglet.Location> getAllowedLocations() {
        return allowedSet;
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
     * Given the <code>DocTree</code> representation of this custom
     * tag, return its string representation.
     * @param tag   the <code>DocTree</code> representing this custom tag.
     */
    public String toString(DocTree tag) {

        return "<DT><B>" + HEADER + "</B><DD>"
               + "<table summary=\"Summary\" cellpadding=2 cellspacing=0><tr><td bgcolor=\"yellow\">"
               + getText(tag)
               + "</td></tr></table></DD>\n";
    }

    /**
     * Given an array of <code>Tag</code>s representing this custom
     * tag, return its string representation.
     * @param tags  the array of <code>DocTree</code>s representing this custom tag.
     */
    @Override
    public String toString(List<? extends DocTree> tags) {
        if (tags.isEmpty()) {
            return null;
        }
        String result = "\n<DT><B>" + HEADER + "</B><DD>";
        result += "<table summary=\"Summary\" cellpadding=2 cellspacing=0><tr><td bgcolor=\"yellow\">";
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) {
                result += ", ";
            }
            result += getText(tags.get(i));
        }
        return result + "</td></tr></table></DD>\n";
    }

    static String getText(DocTree dt) {
        return new SimpleDocTreeVisitor<String, Void>() {
            @Override
            public String visitUnknownBlockTag(UnknownBlockTagTree node, Void p) {
                for (DocTree dt : node.getContent()) {
                    return dt.accept(this, null);
                }
                return "";
            }

            @Override
            public String visitUnknownInlineTag(UnknownInlineTagTree node, Void p) {
                for (DocTree dt : node.getContent()) {
                    return dt.accept(this, null);
                }
                return "";
            }

            @Override
            public String visitText(TextTree node, Void p) {
                return node.getBody();
            }

            @Override
            protected String defaultAction(DocTree node, Void p) {
                return "";
            }

        }.visit(dt, null);
    }
}
