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

package jdk.javadoc.internal.doclets.formats.html;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.Text;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;
import jdk.javadoc.internal.doclets.toolkit.util.links.LinkInfo;


/**
 * HTML-specific information about a link.
 */
public class HtmlLinkInfo extends LinkInfo {

    /**
     * Enumeration of different kinds of links.
     */
    public enum Kind {
        /**
         * Link with just the element name as label.
         */
        PLAIN,
        /**
         * Link with additional preview flag if appropriate.
         */
        SHOW_PREVIEW,
        /**
         * Link with optional type parameters appended as plain text.
         */
        SHOW_TYPE_PARAMS,

        /**
         * Link with optional type parameters included in the link label.
         */
        SHOW_TYPE_PARAMS_IN_LABEL,

        /**
         * Link with optional type parameters and bounds appended as plain text.
         */
        SHOW_TYPE_PARAMS_AND_BOUNDS,
        /**
         * Link with optional type parameters but no bounds rendered as separate links.
         */
        LINK_TYPE_PARAMS,
        /**
         * Link with optional type parameters and bounds rendered as separate links.
         */
        LINK_TYPE_PARAMS_AND_BOUNDS;
    }

    public final HtmlConfiguration configuration;

    /**
     * The context of the link.
     */
    public Kind context = Kind.PLAIN;

    /**
     * The fragment of the link.
     */
    public String fragment = "";

    /**
     * The member this link points to (if any).
     */
    public Element targetMember;

    /**
     * Optional style for the link.
     */
    public HtmlStyle style = null;

    public final Utils utils;

    /**
     * Construct a LinkInfo object.
     *
     * @param configuration the configuration data for the doclet
     * @param context    the context of the link.
     * @param ee   the member to link to.
     */
    public HtmlLinkInfo(HtmlConfiguration configuration, Kind context, ExecutableElement ee) {
        this.configuration = configuration;
        this.utils = configuration.utils;
        this.executableElement = ee;
        setContext(context);
    }

    @Override
    protected Content newContent() {
        return new ContentBuilder();
    }

    /**
     * Construct a LinkInfo object.
     *
     * @param configuration the configuration data for the doclet
     * @param context    the context of the link.
     * @param typeElement   the class to link to.
     */
    public HtmlLinkInfo(HtmlConfiguration configuration, Kind context, TypeElement typeElement) {
        this.configuration = configuration;
        this.utils = configuration.utils;
        this.typeElement = typeElement;
        setContext(context);
    }

    /**
     * Construct a LinkInfo object.
     *
     * @param configuration the configuration data for the doclet
     * @param context    the context of the link.
     * @param type       the class to link to.
     */
    public HtmlLinkInfo(HtmlConfiguration configuration, Kind context, TypeMirror type) {
        this.configuration = configuration;
        this.utils = configuration.utils;
        this.type = type;
        setContext(context);
    }

    /**
     * Set the label for the link.
     * @param label plain-text label for the link
     */
    public HtmlLinkInfo label(CharSequence label) {
        this.label = Text.of(label);
        return this;
    }

    /**
     * Set the label for the link.
     */
    public HtmlLinkInfo label(Content label) {
        this.label = label;
        return this;
    }

    /**
     * Sets the style to be used for the link.
     */
    public HtmlLinkInfo style(HtmlStyle style) {
        this.style = style;
        return this;
    }

    /**
     * Set whether or not this is a link to a varargs parameter.
     */
    public HtmlLinkInfo varargs(boolean varargs) {
        this.isVarArg = varargs;
        return this;
    }

    /**
     * Set the fragment specifier for the link.
     */
    public HtmlLinkInfo fragment(String fragment) {
        this.fragment = fragment;
        return this;
    }

    /**
     * Set the member this link points to (if any).
     */
    public HtmlLinkInfo targetMember(Element el) {
        this.targetMember = el;
        return this;
    }

    /**
     * Set whether or not the preview flags should be skipped for this link.
     */
    public HtmlLinkInfo skipPreview(boolean skipPreview) {
        this.skipPreview = skipPreview;
        return this;
    }

    public Kind getContext() {
        return context;
    }

    /**
     * This method sets the link attributes to the appropriate values
     * based on the context.
     *
     * @param c the context id to set.
     */
    public final void setContext(Kind c) {
        linkTypeParameters = c == Kind.LINK_TYPE_PARAMS || c == Kind.LINK_TYPE_PARAMS_AND_BOUNDS;
        showTypeBounds = c == Kind.SHOW_TYPE_PARAMS_AND_BOUNDS || c == Kind.LINK_TYPE_PARAMS_AND_BOUNDS;
        context = c;
    }

    /**
     * Return true if this link is linkable and false if we can't link to the
     * desired place.
     *
     * @return true if this link is linkable and false if we can't link to the
     * desired place.
     */
    @Override
    public boolean isLinkable() {
        return configuration.utils.isLinkable(typeElement);
    }

    @Override
    public boolean showTypeParameters() {
        // Type parameters for these kinds of links are either not desired
        // or already included in the link label.
        return context != Kind.PLAIN && context != Kind.SHOW_PREVIEW
                && context != Kind.SHOW_TYPE_PARAMS_IN_LABEL;
    }

    @Override
    public String toString() {
        return "HtmlLinkInfo{" +
                "context=" + context +
                ", fragment=" + fragment +
                ", style=" + style +
                super.toString() + '}';
    }
}
