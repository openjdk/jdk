/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.formats.html.taglets;

import com.sun.source.doctree.AttributeTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.NoteTree;
import com.sun.source.doctree.TextTree;
import jdk.javadoc.doclet.Taglet;
import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.HtmlTag;
import jdk.javadoc.internal.html.HtmlTree;
import jdk.javadoc.internal.html.Text;

import javax.lang.model.element.Element;
import java.util.EnumSet;
import java.util.stream.Collectors;

/**
 * A taglet that represents the {@code {@note ...}} tag to create inline notes.
 */
public class NoteTaglet extends BaseTaglet {

    NoteTaglet(HtmlConfiguration config, DocTree.Kind tagKind) {
        super(config, tagKind, true, EnumSet.allOf(Taglet.Location.class));
    }

    @Override
    public Content getInlineTagOutput(Element element, DocTree tag, TagletWriter tagletWriter) {
        this.tagletWriter = tagletWriter;
        var note = (NoteTree) tag;
        var context = tagletWriter.context;
        var htmlWriter = tagletWriter.htmlWriter;

        var attr = note.getAttributes().stream()
                .filter(dt -> dt.getKind() == DocTree.Kind.ATTRIBUTE)
                .map(t -> (AttributeTree) t)
                .collect(Collectors.toMap(at -> at.getName().toString(), NoteTaglet::stringValueOf));
        var label = attr.getOrDefault("label", "Note");

        return HtmlTree.of(HtmlTag.BLOCKQUOTE)
                .add(HtmlTree.of(HtmlTag.STRONG).add(Text.of(label + ": ")))
                .add(htmlWriter.commentTagsToContent(element, note.getBody(), context.within(note)));
    }

    private static String stringValueOf(AttributeTree at) {
        return at.getValue().stream()
                // value consists of TextTree or ErroneousTree nodes;
                // ErroneousTree is a subtype of TextTree
                .map(t -> ((TextTree) t).getBody())
                .collect(Collectors.joining());
    }
}
