/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.Text;
import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;


/**
 * HTML-specific information about a link.
 */
public class HtmlLinkInfo {

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
        LINK_TYPE_PARAMS_AND_BOUNDS
    }

    private final HtmlConfiguration configuration;

    // The context of the link.
    private Kind context = Kind.PLAIN;

    // The fragment of the link.
    private String fragment = null;

    // The member this link points to (if any).
    private Element targetMember;

    // Optional style for the link.
    private HtmlStyle style = null;

    // The class we want to link to. Null if we are not linking to a class.
    private TypeElement typeElement;

    // The executable element we want to link to. Null if we are not linking to an executable element.
    private ExecutableElement executableElement;

    // The Type we want to link to. Null if we are not linking to a type.
    private TypeMirror type;

    // True if this is a link to a VarArg.
    private boolean isVarArg = false;

    // The label for the link.
    private Content label;

    // True if we should print the type bounds for the type parameter.
    private boolean showTypeBounds = true;

    // True if type parameters should be rendered as links.
    private boolean linkTypeParameters = true;

    // By default, the link can be to the page it's already on.  However,
    // there are cases where we don't want this (e.g. heading of class page).
    private boolean linkToSelf = true;

    // True iff the preview flags should be skipped for this link.
    private boolean skipPreview;

    // True if type parameters should be separated by hard line breaks.
    private boolean addLineBreaksInTypeParameters = false;

    // True if additional <wbr> tags should be added to type parameters
    private boolean addLineBreakOpportunitiesInTypeParameters = false;

    // True if annotations on type parameters should be shown.
    private boolean showTypeParameterAnnotations = false;

    /**
     * Construct a LinkInfo object.
     *
     * @param configuration the configuration data for the doclet
     * @param context    the context of the link.
     * @param ee   the member to link to.
     */
    public HtmlLinkInfo(HtmlConfiguration configuration, Kind context, ExecutableElement ee) {
        this.configuration = configuration;
        this.executableElement = ee;
        setContext(context);
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
        this.type = type;
        setContext(context);
    }

    /**
     * Creates a copy of this HtmlLinkInfo instance with a different TypeMirror.
     * This is used for contained types such as type parameters or array components.
     *
     * @param type the type mirror
     * @return the new link info
     */
    public HtmlLinkInfo forType(TypeMirror type) {
        HtmlLinkInfo linkInfo = new HtmlLinkInfo(configuration, context, type);
        linkInfo.showTypeBounds = showTypeBounds;
        linkInfo.linkTypeParameters = linkTypeParameters;
        linkInfo.linkToSelf = linkToSelf;
        linkInfo.addLineBreaksInTypeParameters = addLineBreaksInTypeParameters;
        linkInfo.showTypeParameterAnnotations = showTypeParameterAnnotations;
        linkInfo.skipPreview = skipPreview;
        return linkInfo;
    }

    /**
     * Sets the typeElement
     * @param typeElement the new typeElement object
     */
    public void setTypeElement(TypeElement typeElement) {
        this.typeElement = typeElement;
    }

    /**
     * The class we want to link to.  Null if we are not linking
     * to a class.
     */
    public TypeElement getTypeElement() {
        return typeElement;
    }

    /**
     * The executable element we want to link to.  Null if we are not linking
     * to an executable element.
     */
    public ExecutableElement getExecutableElement() {
        return executableElement;
    }

    /**
     * The Type we want to link to.  Null if we are not linking to a type.
     */
    public TypeMirror getType() {
        return type;
    }

    /**
     * Set the label for the link.
     * @param label plain-text label for the link
     * @return this object
     */
    public HtmlLinkInfo label(CharSequence label) {
        this.label = Text.of(label);
        return this;
    }

    /**
     * Set the label for the link.
     * @param label the new value
     * @return this object
     */
    public HtmlLinkInfo label(Content label) {
        this.label = label;
        return this;
    }

    /**
     * {@return the label for the link}
     */
    public Content getLabel() {
        return label;
    }

    /**
     * Sets the style to be used for the link.
     * @param style the new style value
     * @return this object
     */
    public HtmlLinkInfo style(HtmlStyle style) {
        this.style = style;
        return this;
    }

    /**
     * {@return the optional style for the link}
     */
    public HtmlStyle getStyle() {
        return style;
    }

    /**
     * Set whether or not this is a link to a varargs parameter.
     * @param varargs the new value
     * @return this object
     */
    public HtmlLinkInfo varargs(boolean varargs) {
        this.isVarArg = varargs;
        return this;
    }

    /**
     * {@return true if this is a link to a vararg member}
     */
    public boolean isVarArg() {
        return isVarArg;
    }

    /**
     * Set the fragment specifier for the link.
     * @param fragment the new fragment value
     * @return this object
     */
    public HtmlLinkInfo fragment(String fragment) {
        this.fragment = fragment;
        return this;
    }

    /**
     * {@return the fragment of the link}
     */
    public String getFragment() {
        return fragment;
    }

    /**
     * Sets the addLineBreaksInTypeParameters flag for this link.
     * @param addLineBreaksInTypeParameters the new value
     * @return this object
     */
    public HtmlLinkInfo addLineBreaksInTypeParameters(boolean addLineBreaksInTypeParameters) {
        this.addLineBreaksInTypeParameters = addLineBreaksInTypeParameters;
        return this;
    }

    /**
     * {@return true if type parameters should be separated by line breaks}
     */
    public boolean addLineBreaksInTypeParameters() {
        return addLineBreaksInTypeParameters;
    }

    /**
     * Sets the addLineBreakOpportunitiesInTypeParameters flag for this link.
     * @param addLineBreakOpportunities the new value
     * @return this object
     */
    public HtmlLinkInfo addLineBreakOpportunitiesInTypeParameters(boolean addLineBreakOpportunities) {
        this.addLineBreakOpportunitiesInTypeParameters = addLineBreakOpportunities;
        return this;
    }

    /**
     * {@return true if line break opportunities should be added to type parameters}
     */
    public boolean addLineBreakOpportunitiesInTypeParameters() {
        return addLineBreakOpportunitiesInTypeParameters;
    }

    /**
     * Set the linkToSelf flag for this link.
     * @param linkToSelf the new value
     * @return this object
     */
    public HtmlLinkInfo linkToSelf(boolean linkToSelf) {
        this.linkToSelf = linkToSelf;
        return this;
    }

    /**
     * {@return true if we should generate links to the current page}
     */
    public boolean linkToSelf() {
        return linkToSelf;
    }

    /**
     * {@return true if type parameters should be rendered as links}
     */
    public boolean linkTypeParameters() {
        return linkTypeParameters;
    }

    /**
     * Set the showTypeBounds flag for this link
     * @param showTypeBounds the new value
     */
    public void showTypeBounds(boolean showTypeBounds) {
        this.showTypeBounds = showTypeBounds;
    }

    /**
     * {@return true if we should print the type bounds for the type parameter}
     */
    public boolean showTypeBounds() {
        return showTypeBounds;
    }

    /**
     * Set the showTypeParameterAnnotations flag for this link.
     * @param showTypeParameterAnnotations the new value
     * @return this object
     */
    public HtmlLinkInfo showTypeParameterAnnotations(boolean showTypeParameterAnnotations) {
        this.showTypeParameterAnnotations = showTypeParameterAnnotations;
        return this;
    }

    /**
     * {@return true if annotations on type parameters should be shown}
     */
    public boolean showTypeParameterAnnotations() {
        return showTypeParameterAnnotations;
    }

    /**
     * Set the member this link points to (if any).
     * @param el the new member value
     * @return this object
     */
    public HtmlLinkInfo targetMember(Element el) {
        this.targetMember = el;
        return this;
    }

    /**
     * {@return the member this link points to (if any)}
     */
    public Element getTargetMember() {
        return targetMember;
    }

    /**
     * Set whether or not the preview flags should be skipped for this link.
     * @param skipPreview the new value
     * @return this object
     */
    public HtmlLinkInfo skipPreview(boolean skipPreview) {
        this.skipPreview = skipPreview;
        return this;
    }

    /**
     * {@return true iff the preview flags should be skipped for this link}
     */
    public boolean isSkipPreview() {
        return skipPreview;
    }

    /**
     * {@return the link context}
     */
    public Kind getContext() {
        return context;
    }

    /**
     * This method sets the link attributes to the appropriate values
     * based on the context.
     *
     * @param c the context id to set.
     */
    private void setContext(Kind c) {
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
    public boolean isLinkable() {
        return configuration.utils.isLinkable(typeElement);
    }

    /**
     * Returns true if links to declared types should include type parameters.
     *
     * @return true if type parameter links should be included
     */
    public boolean showTypeParameters() {
        // Type parameters for these kinds of links are either not desired
        // or already included in the link label.
        return context != Kind.PLAIN && context != Kind.SHOW_PREVIEW
                && context != Kind.SHOW_TYPE_PARAMS_IN_LABEL;
    }

    /**
     * Return the label for this class link.
     *
     * @param configuration the current configuration of the doclet.
     * @return the label for this class link.
     */
    public Content getClassLinkLabel(BaseConfiguration configuration) {
        if (label != null && !label.isEmpty()) {
            return label;
        } else if (isLinkable()) {
            Content tlabel = newContent();
            Utils utils = configuration.utils;
            tlabel.add(type instanceof DeclaredType dt && utils.isGenericType(dt.getEnclosingType())
                    // If enclosing type is rendered as separate links only use own class name
                    ? typeElement.getSimpleName().toString()
                    : configuration.utils.getSimpleName(typeElement));
            return tlabel;
        } else {
            Content tlabel = newContent();
            tlabel.add(configuration.getClassName(typeElement));
            return tlabel;
        }
    }

    /**
     * {@return a new instance of a content object}
     */
    protected Content newContent() {
        return new ContentBuilder();
    }

    @Override
    public String toString() {
        return "HtmlLinkInfo{" +
                "typeElement=" + typeElement +
                ", executableElement=" + executableElement +
                ", type=" + type +
                ", isVarArg=" + isVarArg +
                ", label=" + label +
                ", showTypeBounds=" + showTypeBounds +
                ", linkTypeParameters=" + linkTypeParameters +
                ", linkToSelf=" + linkToSelf +
                ", addLineBreaksInTypeParameters=" + addLineBreaksInTypeParameters +
                ", showTypeParameterAnnotations=" + showTypeParameterAnnotations +
                ", context=" + context +
                ", fragment=" + fragment +
                ", style=" + style + '}';
    }
}
