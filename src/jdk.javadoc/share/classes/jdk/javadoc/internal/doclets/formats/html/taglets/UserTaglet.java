/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.Set;

import javax.lang.model.element.Element;

import com.sun.source.doctree.DocTree;

import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.RawHtml;

/**
 * A taglet wrapper, allows the public taglet {@link jdk.javadoc.doclet.Taglet}
 * wrapped into an internal {@code Taglet} representation.
 */
public final class UserTaglet implements Taglet {

    private final jdk.javadoc.doclet.Taglet userTaglet;

    UserTaglet(jdk.javadoc.doclet.Taglet t) {
        userTaglet = t;
    }

    @Override
    public Set<jdk.javadoc.doclet.Taglet.Location> getAllowedLocations() {
        return userTaglet.getAllowedLocations();
    }

    @Override
    public boolean isInlineTag() {
        return userTaglet.isInlineTag();
    }

    @Override
    public boolean isBlockTag() {
        return userTaglet.isBlockTag();
    }

    @Override
    public String getName() {
        return userTaglet.getName();
    }

    @Override
    public Content getInlineTagOutput(Element element, DocTree tag, TagletWriter tagletWriter) {
        Content output = tagletWriter.getOutputInstance();
        output.add(RawHtml.of(userTaglet.toString(List.of(tag), element)));
        return output;
    }

    @Override
    public Content getAllBlockTagOutput(Element holder, TagletWriter tagletWriter) {
        Content output = tagletWriter.getOutputInstance();
        var utils = tagletWriter.utils;
        List<? extends DocTree> tags = utils.getBlockTags(holder, getName());
        if (!tags.isEmpty()) {
            String tagString = userTaglet.toString(tags, holder);
            if (tagString != null) {
                output.add(RawHtml.of(tagString));
            }
        }
        return output;
    }
}
