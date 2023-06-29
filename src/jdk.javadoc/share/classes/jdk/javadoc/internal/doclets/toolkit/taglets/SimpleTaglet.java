/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.taglets;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.doclet.Taglet.Location;

import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder.Result;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

/**
 * A custom single-argument block tag.
 */
public class SimpleTaglet extends BaseTaglet implements InheritableTaglet {

    /**
     * The header to output.
     */
    protected String header;

    /**
     * Whether or not the taglet should generate output.
     * Standard tags like {@code @author}, {@code @since}, {@code @version} can
     * be disabled by command-line options; custom tags created with -tag can be
     * disabled with an X in the defining string.
     */
    protected final boolean enabled;

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
    public SimpleTaglet(String tagName, String header, String locations) {
        this(tagName, header, getLocations(locations), isEnabled(locations));
    }

    /**
     * Constructs a {@code SimpleTaglet}.
     *
     * @param tagKind   the kind of this tag
     * @param header    the header to output
     * @param locations the possible locations that this tag can appear in
     */
    public SimpleTaglet(DocTree.Kind tagKind, String header, Set<Location> locations) {
        this(tagKind, header, locations, true);
    }

    /**
     * Constructs a {@code SimpleTaglet}.
     *
     * @param tagName   the name of this tag
     * @param header    the header to output
     * @param locations the possible locations that this tag can appear in
     */
    public SimpleTaglet(String tagName, String header, Set<Location> locations) {
        this(tagName, header, locations, true);
    }

    /**
     * Constructs a {@code SimpleTaglet}.
     *
     * @param tagName   the name of this tag
     * @param header    the header to output
     * @param locations the possible locations that this tag can appear in
     */
    public SimpleTaglet(String tagName, String header, Set<Location> locations, boolean enabled) {
        super(tagName, false, locations);
        this.header = header;
        this.enabled = enabled;
    }

    /**
     * Constructs a {@code SimpleTaglet}.
     *
     * @param tagKind   the kind of this tag
     * @param header    the header to output
     * @param locations the possible locations that this tag can appear in
     */
    public SimpleTaglet(DocTree.Kind tagKind, String header, Set<Location> locations, boolean enabled) {
        super(tagKind, false, locations);
        this.header = header;
        this.enabled = enabled;
    }

    private static Set<Location> getLocations(String locations) {
        Set<Location> set = EnumSet.noneOf(Location.class);
        for (int i = 0; i < locations.length(); i++) {
            switch (locations.charAt(i)) {
                case 'a':  case 'A':
                    return EnumSet.allOf(Location.class);
                case 'c':  case 'C':
                    set.add(Location.CONSTRUCTOR);
                    break;
                case 'f':  case 'F':
                    set.add(Location.FIELD);
                    break;
                case 'm':  case 'M':
                    set.add(Location.METHOD);
                    break;
                case 'o':  case 'O':
                    set.add(Location.OVERVIEW);
                    break;
                case 'p':  case 'P':
                    set.add(Location.PACKAGE);
                    break;
                case 's':  case 'S':        // super-packages, anyone?
                    set.add(Location.MODULE);
                    break;
                case 't':  case 'T':
                    set.add(Location.TYPE);
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

    @Override
    public Output inherit(Element dst, Element src, DocTree tag, boolean isFirstSentence, BaseConfiguration configuration) {
        assert dst.getKind() == ElementKind.METHOD;
        assert !isFirstSentence;
        try {
            var docFinder = configuration.utils.docFinder();
            Optional<Documentation> r;
            if (src == null) {
                r = docFinder.find((ExecutableElement) dst,
                        m -> Result.fromOptional(extractFirst(m, configuration.utils))).toOptional();
            } else {
                r = docFinder.search((ExecutableElement) src,
                        m -> Result.fromOptional(extractFirst(m, configuration.utils))).toOptional();
            }
            return r.map(result -> new Output(result.tag, result.method, result.description, true))
                    .orElseGet(()->new Output(null, null, List.of(), true));
        } catch (DocFinder.NoOverriddenMethodFound e) {
            return new Output(null, null, List.of(), false);
        }
    }

    record Documentation(DocTree tag, List<? extends DocTree> description, ExecutableElement method) { }

    private Optional<Documentation> extractFirst(ExecutableElement m, Utils utils) {
        List<? extends DocTree> tags = utils.getBlockTags(m, this);
        if (tags.isEmpty()) {
            return Optional.empty();
        }
        DocTree t = tags.get(0);
        return Optional.of(new Documentation(t, utils.getCommentHelper(m).getDescription(t), m));
    }

    @Override
    public Content getAllBlockTagOutput(Element holder, TagletWriter writer) {
        Utils utils = writer.configuration().utils;
        List<? extends DocTree> tags = utils.getBlockTags(holder, this);
        if (header == null || tags.isEmpty()) {
            return null;
        }
        return writer.simpleBlockTagOutput(holder, tags, header);
    }
}
