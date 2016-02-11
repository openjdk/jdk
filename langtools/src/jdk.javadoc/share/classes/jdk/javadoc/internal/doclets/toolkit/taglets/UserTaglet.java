/*
 * Copyright (c) 2003, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import javax.lang.model.element.Element;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.internal.doclets.formats.html.markup.RawHtml;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

import static jdk.javadoc.doclet.taglet.Taglet.Location.*;

/**
 * A taglet wrapper, allows the public taglet {@link jdk.javadoc.doclet.taglet.Taglet}
 * wrapped into an internal Taglet representation.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 */
public class UserTaglet implements Taglet {

    final private jdk.javadoc.doclet.taglet.Taglet userTaglet;

    public UserTaglet(jdk.javadoc.doclet.taglet.Taglet t) {
        userTaglet = t;
    }

    /**
     * {@inheritDoc}
     */
    public boolean inField() {
        return userTaglet.isInlineTag()
                || userTaglet.getAllowedLocations().contains(FIELD);
    }

    /**
     * {@inheritDoc}
     */
    public boolean inConstructor() {
        return userTaglet.isInlineTag()
                || userTaglet.getAllowedLocations().contains(CONSTRUCTOR);
    }

    /**
     * {@inheritDoc}
     */
    public boolean inMethod() {
        return userTaglet.isInlineTag()
                || userTaglet.getAllowedLocations().contains(METHOD);
    }

    /**
     * {@inheritDoc}
     */
    public boolean inOverview() {
        return userTaglet.isInlineTag()
                || userTaglet.getAllowedLocations().contains(OVERVIEW);
    }

    /**
     * {@inheritDoc}
     */
    public boolean inPackage() {
        return userTaglet.isInlineTag()
                || userTaglet.getAllowedLocations().contains(PACKAGE);
    }

    /**
     * {@inheritDoc}
     */
    public boolean inType() {
        return userTaglet.isInlineTag()
                || userTaglet.getAllowedLocations().contains(TYPE);
    }

    /**
     * Return true if this <code>Taglet</code> is an inline tag.
     *
     * @return true if this <code>Taglet</code> is an inline tag and false otherwise.
     */
    public boolean isInlineTag() {
        return userTaglet.isInlineTag();
    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return userTaglet.getName();
    }

    /**
     * {@inheritDoc}
     */
    public Content getTagletOutput(Element element, DocTree tag, TagletWriter writer){
        Content output = writer.getOutputInstance();
        output.addContent(new RawHtml(userTaglet.toString(tag)));
        return output;
    }

    /**
     * {@inheritDoc}
     */
    public Content getTagletOutput(Element holder, TagletWriter writer) {
        Content output = writer.getOutputInstance();
        Utils utils = writer.configuration().utils;
        List<? extends DocTree> tags = utils.getBlockTags(holder, getName());
        if (!tags.isEmpty()) {
            String tagString = userTaglet.toString(tags);
            if (tagString != null) {
                output.addContent(new RawHtml(tagString));
            }
        }
        return output;
    }
}
