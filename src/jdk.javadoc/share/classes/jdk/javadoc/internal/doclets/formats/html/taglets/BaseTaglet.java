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

import java.util.Set;

import javax.lang.model.element.Element;

import com.sun.source.doctree.DocTree;

import jdk.javadoc.doclet.Taglet.Location;
import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.doclets.toolkit.Messages;
import jdk.javadoc.internal.doclets.toolkit.Resources;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;
import jdk.javadoc.internal.html.Content;

/**
 * A base class that implements the {@link Taglet} interface.
 */
public class BaseTaglet implements Taglet {
    // The following members are global to the lifetime of the doclet
    protected final HtmlConfiguration config;
    protected final Messages messages;
    protected final Resources resources;
    protected final Utils utils;

    // The following members are specific to the instance of the taglet
    protected final DocTree.Kind tagKind;
    protected final String name;
    private final boolean inline;
    private final Set<Location> sites;

    // The following is dynamically set for the duration of the methods
    //      getInlineTagOutput and getAllBlockTagOutput
    // by those taglets that need to refer to it
    protected TagletWriter tagletWriter;

    public BaseTaglet(HtmlConfiguration config, DocTree.Kind tagKind, boolean inline, Set<Location> sites) {
        this(config, tagKind.tagName, tagKind, inline, sites);
    }

    protected BaseTaglet(HtmlConfiguration config, String name, boolean inline, Set<Location> sites) {
        this(config, name, inline ? DocTree.Kind.UNKNOWN_INLINE_TAG : DocTree.Kind.UNKNOWN_BLOCK_TAG, inline, sites);
    }

    private BaseTaglet(HtmlConfiguration config, String name, DocTree.Kind tagKind, boolean inline, Set<Location> sites) {
        this.config = config;
        this.messages = config.getMessages();
        this.resources = config.getDocResources();
        this.utils = config.utils;

        this.name = name;
        this.tagKind = tagKind;
        this.inline = inline;
        this.sites = sites;
    }

    @Override
    public Set<Location> getAllowedLocations() {
        return sites;
    }

    @Override
    public final boolean isInlineTag() {
        return inline;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Returns the kind of trees recognized by this taglet.
     *
     * @return the kind of trees recognized by this taglet
     */
    public DocTree.Kind getTagKind() {
        return tagKind;
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation throws {@link UnsupportedTagletOperationException}.
     */
    @Override
    public Content getInlineTagOutput(Element element, DocTree tag, TagletWriter tagletWriter) {
        throw new UnsupportedTagletOperationException("Method not supported in taglet " + getName() + ".");
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation throws {@link UnsupportedTagletOperationException}
     */
    @Override
    public Content getAllBlockTagOutput(Element holder, TagletWriter tagletWriter) {
        throw new UnsupportedTagletOperationException("Method not supported in taglet " + getName() + ".");
    }
}
