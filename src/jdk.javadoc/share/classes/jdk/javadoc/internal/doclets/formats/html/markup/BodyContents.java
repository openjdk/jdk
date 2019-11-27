/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.formats.html.markup;

import jdk.javadoc.internal.doclets.toolkit.Content;

import java.util.ArrayList;
import java.util.List;

/**
 * A builder for the contents of the BODY element.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class BodyContents {

    private List<Content> mainContents = new ArrayList<>();
    private Content header = HtmlTree.EMPTY;
    private Content footer = HtmlTree.EMPTY;

    public BodyContents addMainContent(Content content) {
        mainContents.add(content);
        return this;
    }

    public BodyContents setHeader(Content header) {
        this.header = header;
        return this;
    }

    public BodyContents setFooter(Content footer) {
        this.footer = footer;
        return this;
    }

    /**
     * Returns the HTML for the contents of the BODY element.
     *
     * @return the HTML
     */
    public Content toContent() {
        HtmlTree mainTree = HtmlTree.MAIN();
        mainContents.forEach(mainTree::add);
        HtmlTree flexHeader = HtmlTree.HEADER().setStyle(HtmlStyle.flexHeader);
        flexHeader.add(header);
        HtmlTree flexBox = HtmlTree.DIV(HtmlStyle.flexBox, flexHeader);
        HtmlTree flexContent = HtmlTree.DIV(HtmlStyle.flexContent, mainTree);
        flexContent.add(footer);
        flexBox.add(flexContent);
        return flexBox;
    }
}
