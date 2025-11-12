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
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyles;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.ContentBuilder;
import jdk.javadoc.internal.html.HtmlId;
import jdk.javadoc.internal.html.HtmlTree;
import jdk.javadoc.internal.html.RawHtml;

import javax.lang.model.element.Element;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A taglet that represents the {@code {@note ...}} tag to create inline notes.
 */
public class NoteTaglet extends SimpleTaglet implements InheritableTaglet {

    final private String defaultHeader;
    final private String defaultKind;

    final static String NOTE_HEADER = "Note:";

    NoteTaglet(HtmlConfiguration config) {
        super(config, DocTree.Kind.NOTE.tagName, DocTree.Kind.NOTE, NOTE_HEADER, true, EnumSet.allOf(Taglet.Location.class), true);
        this.defaultHeader = NOTE_HEADER;
        this.defaultKind = null;
    }

    /**
     * Constructs a {@code NoteTaglet}.
     *
     * @param tagName   the name of this tag
     * @param header    the header to output
     * @param locations the possible locations that this tag can appear in
     *                  The string can contain 'p' for package, 't' for type,
     *                  'm' for method, 'c' for constructor and 'f' for field.
     */
    NoteTaglet(HtmlConfiguration config, String tagName, String header, String locations) {
        super(config, tagName, DocTree.Kind.NOTE, header, true, getLocations(locations), isEnabled(locations));
        this.defaultHeader = header;
        this.defaultKind = tagName;
    }

    @Override
    public boolean isBlockTag() {
        return true;
    }

    @Override
    public Content getAllBlockTagOutput(Element holder, TagletWriter tagletWriter) {
        this.tagletWriter = tagletWriter;
        List<? extends DocTree> tags = getBlockTags(holder);
        if (tags.isEmpty()) {
            return null;
        }

        ContentBuilder body = new ContentBuilder();
        for (DocTree tag : tags) {
            body.add(getTagOutput(holder, (NoteTree) tag));
        }
        return body;
    }

    @Override
    public Content getInlineTagOutput(Element element, DocTree tag, TagletWriter tagletWriter) {
        this.tagletWriter = tagletWriter;
        return HtmlTree.DL(HtmlStyles.notes)
                .add(getTagOutput(element, (NoteTree) tag));
    }

    private Content getTagOutput(Element holder, NoteTree note) {
        var context = tagletWriter.context;
        var htmlWriter = tagletWriter.htmlWriter;

        var attr = getAttributes(note);
        var header = attr.getOrDefault("header", defaultHeader);
        var kind = attr.getOrDefault("kind", defaultKind);
        var id = attr.getOrDefault("id", null);

        var result = HtmlTree.DIV(HtmlStyles.noteTag)
                .add(HtmlTree.DT(RawHtml.of(header)))
                .add(HtmlTree.DD(htmlWriter.commentTagsToContent(holder, note.getBody(), context.within(note))));
        if (id != null) {
            result.setId(HtmlId.of(id));
        }
        if (kind != null) {
            result.addStyle(HtmlStyles.noteTag.cssName() + "-" + kind.trim());
        }
        for (var entry : attr.entrySet()) {
            var name = entry.getKey();
            if (!"header".equalsIgnoreCase(name) && !"kind".equalsIgnoreCase(name) && !"id".equalsIgnoreCase(name)) {
                result.putDataAttr(name, entry.getValue());
            }
        }
        return result;
    }

    private Content wrapOutput(NoteTree note, List<? extends Content> bodies) {
        var attr = getAttributes(note);
        var header = attr.getOrDefault("header", defaultHeader);
        var kind = attr.getOrDefault("kind", defaultKind);

        var result = HtmlTree.DIV(HtmlStyles.noteTag)
                .add(HtmlTree.DT(RawHtml.of(header)));
        result.addAll(bodies, HtmlTree::DD);

        if (kind != null) {
            result.addStyle(HtmlStyles.noteTag.cssName() + "-" + kind.trim());
        }
        return result;
    }

    private Map<String, String> getAttributes(NoteTree note) {
        return note.getAttributes().stream()
                .filter(dt -> dt.getKind() == DocTree.Kind.ATTRIBUTE)
                .map(t -> (AttributeTree) t)
                .collect(Collectors.toMap(at -> at.getName().toString(), NoteTaglet::stringValueOf));
    }

    private static String stringValueOf(AttributeTree at) {
        return at.getValue().stream()
                // value consists of TextTree or ErroneousTree nodes;
                // ErroneousTree is a subtype of TextTree
                .map(t -> ((TextTree) t).getBody())
                .collect(Collectors.joining());
    }
}
