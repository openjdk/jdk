/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Set;

import javax.lang.model.element.Element;

import com.sun.source.doctree.DocTree;

import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

/**
 * A simple single argument custom tag.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 */

public class SimpleTaglet extends BaseTaglet implements InheritableTaglet {

    /**
     * The header to output.
     */
    protected String header;

    /**
     * Whether or not the taglet should generate output.
     * Standard tags like at-author, at-since, at-version can be disabled
     * by command-line options; custom tags created with -tag can be
     * disabled with an X in the defining string.
     */
    protected final boolean enabled;

    /**
     * Construct a <code>SimpleTaglet</code>.
     * @param tagName the name of this tag
     * @param header the header to output.
     * @param locations the possible locations that this tag
     * can appear in.  The <code>String</code> can contain 'p'
     * for package, 't' for type, 'm' for method, 'c' for constructor
     * and 'f' for field.
     */
    public SimpleTaglet(String tagName, String header, String locations) {
        this(tagName, header, getSites(locations), isEnabled(locations));
    }

    /**
     * Construct a <code>SimpleTaglet</code>.
     * @param tagName the name of this tag
     * @param header the header to output.
     * @param sites the possible sites (locations) that this tag
     * can appear in.  The <code>String</code> can contain 'p'
     * for package, 't' for type, 'm' for method, 'c' for constructor
     * and 'f' for field.
     */
    public SimpleTaglet(String tagName, String header, Set<Site> sites) {
        this(tagName, header, sites, true);
    }

    /**
     * Construct a <code>SimpleTaglet</code>.
     * @param tagName the name of this tag
     * @param header the header to output.
     * @param sites the possible sites (locations) that this tag
     * can appear in.  The <code>String</code> can contain 'p'
     * for package, 't' for type, 'm' for method, 'c' for constructor
     * and 'f' for field.
     */
    public SimpleTaglet(String tagName, String header, Set<Site> sites, boolean enabled) {
        super(tagName, false, sites);
        this.header = header;
        this.enabled = enabled;
    }

    private static Set<Site> getSites(String locations) {
        Set<Site> set = EnumSet.noneOf(Site.class);
        for (int i = 0; i < locations.length(); i++) {
            switch (locations.charAt(i)) {
                case 'a':  case 'A':
                    return EnumSet.allOf(Site.class);
                case 'c':  case 'C':
                    set.add(Site.CONSTRUCTOR);
                    break;
                case 'f':  case 'F':
                    set.add(Site.FIELD);
                    break;
                case 'm':  case 'M':
                    set.add(Site.METHOD);
                    break;
                case 'o':  case 'O':
                    set.add(Site.OVERVIEW);
                    break;
                case 'p':  case 'P':
                    set.add(Site.PACKAGE);
                    break;
                case 's':  case 'S':        // super-packages, anyone?
                    set.add(Site.MODULE);
                    break;
                case 't':  case 'T':
                    set.add(Site.TYPE);
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
    public void inherit(DocFinder.Input input, DocFinder.Output output) {
        List<? extends DocTree> tags = input.utils.getBlockTags(input.element, name);
        if (!tags.isEmpty()) {
            output.holder = input.element;
            output.holderTag = tags.get(0);
            CommentHelper ch = input.utils.getCommentHelper(output.holder);
            output.inlineTags = input.isFirstSentence
                    ? ch.getFirstSentenceTrees(input.utils.configuration, output.holderTag)
                    : ch.getTags(input.utils.configuration, output.holderTag);
        }
    }

    @Override
    public Content getTagletOutput(Element element, DocTree tag, TagletWriter writer) {
        return header == null || tag == null ? null : writer.simpleTagOutput(element, tag, header);
    }

    @Override
    public Content getTagletOutput(Element holder, TagletWriter writer) {
        Utils utils = writer.configuration().utils;
        List<? extends DocTree> tags = utils.getBlockTags(holder, getName());
        if (header == null || tags.isEmpty()) {
            return null;
        }
        return writer.simpleTagOutput(holder, tags, header);
    }
}
