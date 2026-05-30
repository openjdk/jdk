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
import jdk.javadoc.internal.html.Entity;
import jdk.javadoc.internal.html.HtmlId;
import jdk.javadoc.internal.html.HtmlTag;
import jdk.javadoc.internal.html.HtmlTree;
import jdk.javadoc.internal.html.RawHtml;
import jdk.javadoc.internal.html.Text;

import javax.lang.model.element.Element;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A taglet that represents the bimodal {@code @note} tag to create notes.
 */
public class NoteTaglet extends SimpleTaglet implements InheritableTaglet {

    // Flag to re-enable pre-note tag output for block tags.
    private static final boolean OLD_SCHOOL_BLOCK_TAGS = false;

    private final String defaultHeader;
    private final String defaultKind;
    private final boolean isBlockTag;

    private static final String CSS_CLASS_PREFIX = "note-tag";

    private static final int LONG_TEXT_THRESHOLD = 1500;
    private static final int MEDIUM_TEXT_THRESHOLD = 200;

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
                var id = attr.getOrDefault("id", null);
                var body = HtmlTree.DD(htmlWriter.commentTagsToContent(holder, note.getBody(), context.within(note)));

                // Block notes with the same header are grouped under a single <dt> element, followed by
                // a <dd> for each note body. Because the style is applied to the enclosing <div> element,
                // mixing multiple styles in such a grouped note would not lead to a desired outcome.
                // The first note in a group therefore determines the style of the group.
                var content = map.get(header);
                if (content == null) {
                    content = HtmlTree.DIV(HtmlTree.DT(sanitizeHeader(header)))
                            .setId(getId(id, holder, false))
                            .addStyle(getCSSClass(kind))
                            .add(body);
                    map.put(header, content);
                } else {
                    if (id != null) {
                        body.setId(getId(id, holder, false));
                    }
                    if (!Objects.equals(kind, defaultKind)) {
                        messages.warning("doclet.note.kind_attribute_ignored");
                    }
                    content.add(body);
                }
            }
        }

        Content content = new ContentBuilder();
        for (var cnt : map.values()) {
            content.add(setContentLengthStyle(cnt));
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
        var kind = attr.getOrDefault("kind", defaultKind);

        var div = HtmlTree.DIV(HtmlStyles.inlineNote)
                .addStyle(getCSSClass(kind))
                .setId(getId(id, element, true))
                .add(HtmlTree.SPAN(HtmlStyles.noteHeader, sanitizeHeader(header)))
                .add(Text.NL)
                .add(htmlWriter.commentTagsToContent(element, note.getBody(), context.within(note)));

        return setContentLengthStyle(div);
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
            ? config.htmlIds.getUniqueId(id, existingIds)
            : config.htmlIds.forNote(e, name, inline, existingIds);
    }

    private static HtmlTree setContentLengthStyle(HtmlTree html) {
        var contentLength = countTextLength(html.getContents());
        if (contentLength > LONG_TEXT_THRESHOLD) {
            html.addStyle(HtmlStyles.longNote);
        } else if (contentLength > MEDIUM_TEXT_THRESHOLD) {
            html.addStyle(HtmlStyles.mediumLengthNote);
        }
        return html;
    }

    // Similar to Content::charCount but discount <pre> and raw content.
    private static int countTextLength(List<Content> contents) {
        var count = 0;
        for (var c : contents) {
            count += switch (c) {
                case HtmlTree html -> html.tag == HtmlTag.PRE
                        ? html.charCount() / 2
                        : countTextLength(html.getContents());
                case Text text -> text.charCount();
                case ContentBuilder b -> countTextLength(b.getContents());
                default -> 0;
            };
        }
        return count;
    }

    private static String stringValueOf(AttributeTree at) {
        return at.getValue().stream()
                // value consists of TextTree or ErroneousTree nodes;
                // ErroneousTree is a subtype of TextTree
                .map(t -> ((TextTree) t).getBody())
                .collect(Collectors.joining());
    }

    // Returns the main CSS class for a note depending on its kind.
    private static String getCSSClass(String kind) {
        return kind == null
                ? CSS_CLASS_PREFIX
                : CSS_CLASS_PREFIX + "-" + kind.trim();
    }

    // Regex to enable HTML tags allowed in note headers
    private static final Pattern allowedHeaderTags = Pattern.compile(
            "&lt;(?<tag>b|strong|i|em|code|sub|sup)&gt;" +
                    "(?<body>.*?)" +
                    "&lt;/\\k<tag>&gt;",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // Only allow a few select safe HTML elements in header, and only
    // when they are closed properly and don't contain any attributes.
    private static Content sanitizeHeader(String header) {
        var escaped = Entity.escapeHtmlChars(header);

        if (!escaped.equals(header)) {
            var matcher = allowedHeaderTags.matcher(escaped);
            while (matcher.find()) {
                escaped = matcher.replaceFirst("<${tag}>${body}</${tag}>");
                matcher = allowedHeaderTags.matcher(escaped);
            }
        }
        return RawHtml.of(escaped);
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
