/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
import jdk.javadoc.internal.html.Text;

import javax.lang.model.element.Element;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A taglet that represents the bimodal {@code @note} tag to create notes.
 */
public class NoteTaglet extends SimpleTaglet implements InheritableTaglet {

    private final static boolean OLD_SCHOOL_BLOCK_TAGS = false;

    private final String defaultHeader;
    private final String defaultKind;
    private final boolean isBlockTag;

    private static final String CSS_CLASS_PREFIX = "note-tag";

    NoteTaglet(HtmlConfiguration config) {
        super(config, DocTree.Kind.NOTE.tagName, DocTree.Kind.NOTE,
                config.docResources.getText("doclet.Note_Tag_Default_Header"),
                true, EnumSet.allOf(Taglet.Location.class), true);
        this.isBlockTag = true;
        this.defaultHeader = this.header;
        this.defaultKind = null;
    }

    /**
     * Constructs a {@code NoteTaglet}.
     *
     * @param tagName   the name of this tag
     * @param header    the header to output
     * @param locations the possible locations that this tag can appear in.
     *                  The string can contain 'p' for package, 't' for type,
     *                  'm' for method, 'c' for constructor and 'f' for field.
     *                  See {@link #getLocations(String) getLocations} for the
     *                  complete list.
     */
    NoteTaglet(HtmlConfiguration config, String tagName, String header, String locations) {
        super(config, tagName, DocTree.Kind.NOTE, header,
                allowInlineUse(locations), getLocations(locations), isEnabled(locations));
        this.isBlockTag = allowBlockUse(locations);
        this.defaultHeader = header;
        this.defaultKind = tagName;
        if (!isInlineTag() && !isBlockTag()) {
            messages.error("doclet.note.block_and_inline_flags_together");
        }
    }

    @Override
    public boolean isBlockTag() {
        return isBlockTag;
    }

    @Override
    public Content getAllBlockTagOutput(Element holder, TagletWriter tagletWriter) {
        // Useful when comparing API docs with older releases.
        if (OLD_SCHOOL_BLOCK_TAGS) {
            return super.getAllBlockTagOutput(holder, tagletWriter);
        }
        this.tagletWriter = tagletWriter;
        List<? extends DocTree> tags = getBlockTags(holder);
        if (tags.isEmpty()) {
            return null;
        }

        var map = new LinkedHashMap<String, HtmlTree>();
        var context = tagletWriter.context;
        var htmlWriter = tagletWriter.htmlWriter;
        for (DocTree tag : tags) {
            if (tag instanceof NoteTree note) {
                var attr = getAttributes(note);
                var header = attr.getOrDefault("header", defaultHeader);
                var kind = attr.getOrDefault("kind", defaultKind);
                var body = HtmlTree.DD(htmlWriter.commentTagsToContent(holder, note.getBody(), context.within(note)));
                var id = attr.getOrDefault("id", null);

                // Block notes with the same header are grouped under a single <dt> element, followed by
                // a <dd> for each note body. Because the style is applied to the enclosing <div> element,
                // mixing multiple styles in such a grouped note would not lead to a desired outcome.
                // The first note in a group therefore determines the style of the group.
                map.compute(header, (hdr, cnt) -> {
                    if (cnt == null) {
                        return HtmlTree.DIV(HtmlStyles.blockNote)
                                .setId(getId(id, holder, false))
                                .addStyle(getCSSClass(kind))
                                .add(HtmlTree.DT(RawHtml.of(hdr)))
                                .add(body);
                    } else {
                        if (id != null) {
                            body.setId(config.htmlIds.makeUnique(id, tagletWriter.htmlWriter.getExistingIds()));
                        }
                        if (kind != defaultKind) {
                            messages.warning("doclet.note.kind_attribute_ignored");
                        }
                        cnt.add(body);
                        return cnt;
                    }
                });
            }
        }

        Content content = new ContentBuilder();
        for (var cnt : map.values()) {
            content.add(cnt);
        }
        return content;
    }

    @Override
    public Content getInlineTagOutput(Element element, DocTree tag, TagletWriter tagletWriter) {
        this.tagletWriter = tagletWriter;
        var context = tagletWriter.context;
        var htmlWriter = tagletWriter.htmlWriter;
        var note = (NoteTree) tag;

        var attr = getAttributes(note);
        var header = attr.getOrDefault("header", defaultHeader);
        var id = attr.getOrDefault("id", null);

        HtmlTree result = HtmlTree.DIV(HtmlStyles.inlineNote)
                .setId(getId(id, element, true))
                .add(HtmlTree.SPAN(HtmlStyles.noteHeader, RawHtml.of(header)))
                .add(Text.NL)
                .add(htmlWriter.commentTagsToContent(element, note.getBody(), context.within(note)));

        var kind = attr.getOrDefault("kind", defaultKind);
        result.addStyle(getCSSClass(kind));

        for (var entry : attr.entrySet()) {
            var name = entry.getKey();
            if (!"header".equalsIgnoreCase(name) && !"kind".equalsIgnoreCase(name) && !"id".equalsIgnoreCase(name)) {
                result.putDataAttr(name, entry.getValue());
            }
        }

        return result;
    }

    private Map<String, String> getAttributes(NoteTree note) {
        // Missing values and duplicate keys are checked by Doclint.
        return note.getAttributes().stream()
                .filter(dt -> dt.getKind() == DocTree.Kind.ATTRIBUTE)
                .map(t -> (AttributeTree) t)
                .filter(at -> at.getValue() != null)
                .collect(Collectors.toMap(at -> at.getName().toString(), NoteTaglet::stringValueOf,
                                          (oldValue, _) -> oldValue));
    }

    private HtmlId getId(String id, Element e, boolean inline) {
        var existingIds = tagletWriter.htmlWriter.getExistingIds();
        return id != null
            ? config.htmlIds.makeUnique(id, existingIds)
            : config.htmlIds.forNote(e, defaultKind, inline, existingIds);
    }

    private static String stringValueOf(AttributeTree at) {
        return at.getValue().stream()
                // value consists of TextTree or ErroneousTree nodes;
                // ErroneousTree is a subtype of TextTree
                .map(t -> ((TextTree) t).getBody())
                .collect(Collectors.joining());
    }

    private String getCSSClass(String kind) {
        return kind == null
                ? CSS_CLASS_PREFIX
                : CSS_CLASS_PREFIX + "-" + kind.trim();
    }

    private static Set<Taglet.Location> getLocations(String locations) {
        Set<Taglet.Location> set = EnumSet.noneOf(Taglet.Location.class);
        for (int i = 0; i < locations.length(); i++) {
            switch (locations.charAt(i)) {
                case 'a':  case 'A':
                    return EnumSet.allOf(Taglet.Location.class);
                case 'c':  case 'C':
                    set.add(Taglet.Location.CONSTRUCTOR);
                    break;
                case 'f':  case 'F':
                    set.add(Taglet.Location.FIELD);
                    break;
                case 'm':  case 'M':
                    set.add(Taglet.Location.METHOD);
                    break;
                case 'o':  case 'O':
                    set.add(Taglet.Location.OVERVIEW);
                    break;
                case 'p':  case 'P':
                    set.add(Taglet.Location.PACKAGE);
                    break;
                case 's':  case 'S':        // super-packages, anyone?
                    set.add(Taglet.Location.MODULE);
                    break;
                case 't':  case 'T':
                    set.add(Taglet.Location.TYPE);
                    break;
                case 'x':  case 'X':
                    break;
            }
        }
        return set;
    }

    private static boolean isEnabled(String locations) {
        return locations.matches("[^Xx]*");
    }

    private static boolean allowInlineUse(String locations) {
        return locations.matches("[^Bb]*");
    }

    private static boolean allowBlockUse(String locations) {
        return locations.matches("[^Ii]*");
    }
}
