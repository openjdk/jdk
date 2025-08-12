/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.ContentBuilder;
import jdk.javadoc.internal.html.HtmlTree;
import jdk.javadoc.internal.html.Text;

/**
 * Content for the {@code <body>} element.
 *
 * The content is a {@code <div>} element that contains a
 * header that is always visible, main content that
 * can be scrolled if necessary, and optional side and footer
 * contents that are only rendered if available.
 */
public class BodyContents extends Content {

    private final List<Content> mainContents = new ArrayList<>();
    private Content side = null;
    private Content header = null;
    private Content footer = null;

    public BodyContents addMainContent(Content content) {
        mainContents.add(content);
        return this;
    }

    public BodyContents setSideContent(Content side) {
        this.side = Objects.requireNonNull(side);
        return this;
    }

    public BodyContents setHeader(Content header) {
        this.header = Objects.requireNonNull(header);
        return this;
    }

    public BodyContents setFooter(Content footer) {
        this.footer = footer;
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation always returns {@code false}.
     *
     * @return {@code false}
     */
    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean write(Writer out, String newline, boolean atNewline) throws IOException {
        return toContent().write(out, newline, atNewline);
    }

    /**
     * Returns the HTML for the contents of the BODY element.
     *
     * @return the HTML
     */
    private Content toContent() {
        if (header == null)
            throw new NullPointerException();

        return new ContentBuilder()
                .add(header)
                .add(HtmlTree.DIV(HtmlStyles.mainGrid)
                        .add(side == null ? Text.EMPTY : side)
                        .add(HtmlTree.MAIN()
                                .add(mainContents)
                                .add(footer == null ? Text.EMPTY : footer)));
    }
}
