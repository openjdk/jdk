/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;

import com.sun.source.doctree.BlockTagTree;
import com.sun.source.doctree.DocTree;

import jdk.javadoc.doclet.Taglet;
import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.RawHtml;
import jdk.javadoc.internal.doclets.formats.html.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder;

/**
 * A custom single-argument block tag.
 */
public class SimpleTaglet extends BaseTaglet implements InheritableTaglet {

    /**
     * The header to output.
     */
    private final String header;

    private final boolean enabled;

    /**
     * Constructs a {@code SimpleTaglet}.
     *
     * @param tagName   the name of this tag
     * @param header    the header to output
     * @param locations the possible locations that this tag can appear in
     *                  The string can contain 'p' for package, 't' for type,
     *                  'm' for method, 'c' for constructor and 'f' for field.
     *                  See {@link #getLocations(String) getLocations} for the
     *                  complete list.
     */
    SimpleTaglet(HtmlConfiguration config, String tagName, String header, String locations) {
        this(config, tagName, header, getLocations(locations), isEnabled(locations));
    }

    /**
     * Constructs a {@code SimpleTaglet}.
     *
     * @param tagKind   the kind of this tag
     * @param header    the header to output
     * @param locations the possible locations that this tag can appear in
     */
    SimpleTaglet(HtmlConfiguration config, DocTree.Kind tagKind, String header, Set<Taglet.Location> locations) {
        this(config, tagKind, header, locations, true);
    }

    /**
     * Constructs a {@code SimpleTaglet}.
     *
     * @param tagName   the name of this tag
     * @param header    the header to output
     * @param locations the possible locations that this tag can appear in
     */
    SimpleTaglet(HtmlConfiguration config, String tagName, String header, Set<Taglet.Location> locations) {
        this(config, tagName, header, locations, true);
    }

    /**
     * Constructs a {@code SimpleTaglet}.
     *
     * @param tagName   the name of this tag
     * @param header    the header to output
     * @param locations the possible locations that this tag can appear in
     * @param enabled   whether this tag is enabled
     */
    private SimpleTaglet(HtmlConfiguration config, String tagName, String header, Set<Taglet.Location> locations, boolean enabled) {
        super(config, tagName, false, locations);
        this.header = header;
        this.enabled = enabled;
    }

    /**
     * Constructs a {@code SimpleTaglet}.
     *
     * @param tagKind   the kind of this tag
     * @param header    the header to output
     * @param locations the possible locations that this tag can appear in
     * @param enabled   whether this tag is enabled
     */
    protected SimpleTaglet(HtmlConfiguration config, DocTree.Kind tagKind, String header, Set<Taglet.Location> locations, boolean enabled) {
        super(config, tagKind, false, locations);
        this.header = header;
        this.enabled = enabled;
    }

    /**
     * Constructs a {@code SimpleTaglet} that will look for tags on enclosing type elements
     * if there are no relevant tags on a nested (member) type element.
     *
     * @param tagKind   the kind of this tag
     * @param header    the header to output
     * @param locations the possible locations that this tag can appear in
     * @param enabled   whether this tag is enabled
     */
    static SimpleTaglet createWithDefaultForNested(HtmlConfiguration config, DocTree.Kind tagKind, String header, Set<Taglet.Location> locations, boolean enabled) {
        return new SimpleTaglet(config, tagKind, header, locations, enabled) {
            @Override
            protected List<? extends BlockTagTree> getDefaultBlockTags(Element e, Predicate<? super BlockTagTree> accepts) {
                while (isNestedType(e)) {
                    e = e.getEnclosingElement();
                    var tags = utils.getBlockTags(e, accepts);
                    if (!tags.isEmpty()) {
                        return tags;
                    }
                }

                return List.of();
            }

            private boolean isNestedType(Element e) {
                return e.getKind().isDeclaredType()
                        && ((TypeElement) e).getNestingKind() == NestingKind.MEMBER;
            }
        };
    }

    @Override
    public Output inherit(Element dst, Element src, DocTree tag, boolean isFirstSentence) {
        assert dst.getKind() == ElementKind.METHOD;
        assert !isFirstSentence;
        try {
            var docFinder = utils.docFinder();
            Optional<Documentation> r;
            if (src == null) {
                r = docFinder.find((ExecutableElement) dst,
                        m -> DocFinder.Result.fromOptional(extractFirst(m))).toOptional();
            } else {
                r = docFinder.search((ExecutableElement) src,
                        m -> DocFinder.Result.fromOptional(extractFirst(m))).toOptional();
            }
            return r.map(result -> new Output(result.tag, result.method, result.description, true))
                    .orElseGet(()->new Output(null, null, List.of(), true));
        } catch (DocFinder.NoOverriddenMethodFound e) {
            return new Output(null, null, List.of(), false);
        }
    }

    /**
     * Whether the taglet should generate output.
     * Standard tags like {@code @author}, {@code @since}, {@code @version} can
     * be disabled by command-line options; custom tags created with -tag can be
     * disabled with an X in the defining string.
     */
    boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns whether this taglet accepts a {@code BlockTagTree} node.
     * The taglet accepts a tree node if it has the same kind, or
     * if the kind is {@code UNKNOWN_BLOCK_TAG} with the same tag name.
     *
     * @param tree the tree node
     * @return {@code true} if this taglet accepts this tree node
     */
    private boolean accepts(BlockTagTree tree) {
        return (tree.getKind() == DocTree.Kind.UNKNOWN_BLOCK_TAG && tagKind == DocTree.Kind.UNKNOWN_BLOCK_TAG)
                ? tree.getTagName().equals(name)
                : tree.getKind() == tagKind;
    }

    record Documentation(DocTree tag, List<? extends DocTree> description, ExecutableElement method) { }

    private Optional<Documentation> extractFirst(ExecutableElement m) {
        List<? extends DocTree> tags = getBlockTags(m);
        if (tags.isEmpty()) {
            return Optional.empty();
        }
        DocTree t = tags.get(0);
        return Optional.of(new Documentation(t, utils.getCommentHelper(m).getDescription(t), m));
    }

    @Override
    public Content getAllBlockTagOutput(Element holder, TagletWriter tagletWriter) {
        this.tagletWriter = tagletWriter;
        List<? extends DocTree> tags = getBlockTags(holder);
        if (header == null || tags.isEmpty()) {
            return null;
        }
        return simpleBlockTagOutput(holder, tags, header);
    }

    private List<? extends BlockTagTree> getBlockTags(Element e) {
        var tags = utils.getBlockTags(e, this::accepts);
        if (tags.isEmpty()) {
            tags = getDefaultBlockTags(e, this::accepts);
        }
        return tags;
    }

    protected List<? extends BlockTagTree> getDefaultBlockTags(Element e, Predicate<? super BlockTagTree> accepts) {
        return List.of();
    }

    /**
     * Returns the output for a series of simple tags.
     *
     * @param element    The element that owns the doc comment
     * @param simpleTags the list of simple tags
     * @param header     the header for the series of tags
     *
     * @return the output
     */
    private Content simpleBlockTagOutput(Element element,
                                        List<? extends DocTree> simpleTags,
                                        String header) {
        var ch = utils.getCommentHelper(element);
        var context = tagletWriter.context;
        var htmlWriter = tagletWriter.htmlWriter;

        ContentBuilder body = new ContentBuilder();
        boolean many = false;
        for (DocTree simpleTag : simpleTags) {
            if (many) {
                body.add(", ");
            }
            List<? extends DocTree> bodyTags = ch.getBody(simpleTag);
            body.add(htmlWriter.commentTagsToContent(element, bodyTags, context.within(simpleTag)));
            many = true;
        }
        return new ContentBuilder(
                HtmlTree.DT(RawHtml.of(header)),
                HtmlTree.DD(body));
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
}
