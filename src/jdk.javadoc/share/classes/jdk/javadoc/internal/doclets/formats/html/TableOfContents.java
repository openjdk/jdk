/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.javadoc.internal.doclets.formats.html;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyles;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.Entity;
import jdk.javadoc.internal.html.HtmlAttr;
import jdk.javadoc.internal.html.HtmlId;
import jdk.javadoc.internal.html.HtmlTag;
import jdk.javadoc.internal.html.HtmlTree;
import jdk.javadoc.internal.html.ListBuilder;
import jdk.javadoc.internal.html.Text;

/**
 * A class used by various {@link HtmlDocletWriter} subclasses to build tables of contents.
 */
public class TableOfContents {
    private final ListBuilder listBuilder;
    private final HtmlDocletWriter writer;

    /**
     * Constructor
     * @param writer the writer
     */
    public TableOfContents(HtmlDocletWriter writer) {
        this.writer = writer;
        listBuilder = new ListBuilder(HtmlTree.OL(HtmlStyles.tocList));
    }

    /**
     * Adds a link to the table of contents.
     * @param id the link fragment
     * @param label the link label
     * @return this object
     */
    public TableOfContents addLink(HtmlId id, Content label) {
        listBuilder.add(writer.links.createLink(id, label).put(HtmlAttr.TABINDEX, "0"));
        return this;
    }

    /**
     * Adds a new nested list to add new items to.
     */
    public void pushNestedList() {
        listBuilder.pushNestedList(HtmlTree.OL(HtmlStyles.tocList));
    }

    /**
     * Closes the current nested list and go back to the parent list.
     */
    public void popNestedList() {
        listBuilder.popNestedList();
    }

    /**
     * Returns a content object containing the table of contents, consisting
     * of a header and the contents list itself. If the contents list is empty,
     * an empty content object is returned.
     *
     * @param hasFilterInput whether to add a filter text input
     * @return a content object
     */
    protected Content toContent(boolean hasFilterInput) {
        if (listBuilder.isEmpty()) {
            return Text.EMPTY;
        }
        var content = HtmlTree.NAV()
                .setStyle(HtmlStyles.toc)
                .put(HtmlAttr.ARIA_LABEL, writer.resources.getText("doclet.table_of_contents"));
        var header = HtmlTree.DIV(HtmlStyles.tocHeader, writer.contents.contentsHeading);
        if (hasFilterInput) {
            header.add(Entity.NO_BREAK_SPACE)
                    .add(HtmlTree.INPUT(HtmlAttr.InputType.TEXT, HtmlStyles.filterInput)
                            .put(HtmlAttr.PLACEHOLDER, writer.resources.getText("doclet.filter_label"))
                            .put(HtmlAttr.ARIA_LABEL, writer.resources.getText("doclet.filter_table_of_contents"))
                            .put(HtmlAttr.AUTOCOMPLETE, "off"))
                    .add(HtmlTree.INPUT(HtmlAttr.InputType.RESET, HtmlStyles.resetFilter)
                            .put(HtmlAttr.VALUE, writer.resources.getText("doclet.filter_reset")));
        }
        content.add(header);
        content.add(new HtmlTree(HtmlTag.BUTTON).addStyle(HtmlStyles.hideSidebar)
                .add(HtmlTree.SPAN(writer.contents.hideSidebar).add(Entity.NO_BREAK_SPACE))
                .add(Entity.LEFT_POINTING_ANGLE));
        content.add(new HtmlTree(HtmlTag.BUTTON).addStyle(HtmlStyles.showSidebar)
                .add(Entity.RIGHT_POINTING_ANGLE)
                .add(HtmlTree.SPAN(Entity.NO_BREAK_SPACE).add(writer.contents.showSidebar)));
        return content.add(listBuilder);
    }

}
