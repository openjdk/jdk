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

package jdk.javadoc.internal.doclets.formats.html;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;
import jdk.javadoc.internal.doclets.toolkit.util.links.LinkInfo;


/**
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class LinkInfoImpl extends LinkInfo {

    public enum Kind {
        DEFAULT,

        /**
         * Indicate that the link appears in a class list.
         */
        ALL_CLASSES_FRAME,

        /**
         * Indicate that the link appears in a class documentation.
         */
        CLASS,

        /**
         * Indicate that the link appears in member documentation.
         */
        MEMBER,

        /**
         * Indicate that the link appears in class use documentation.
         */
        CLASS_USE,

        /**
         * Indicate that the link appears in index documentation.
         */
        INDEX,

        /**
         * Indicate that the link appears in constant value summary.
         */
        CONSTANT_SUMMARY,

        /**
         * Indicate that the link appears in serialized form documentation.
         */
        SERIALIZED_FORM,

        /**
         * Indicate that the link appears in serial member documentation.
         */
        SERIAL_MEMBER,

        /**
         * Indicate that the link appears in package documentation.
         */
        PACKAGE,

        /**
         * Indicate that the link appears in see tag documentation.
         */
        SEE_TAG,

        /**
         * Indicate that the link appears in value tag documentation.
         */
        VALUE_TAG,

        /**
         * Indicate that the link appears in tree documentation.
         */
        TREE,

        /**
         * Indicate that the link appears in a class list.
         */
        PACKAGE_FRAME,

        /**
         * The header in the class documentation.
         */
        CLASS_HEADER,

        /**
         * The signature in the class documentation.
         */
        CLASS_SIGNATURE,

        /**
         * The return type of a method.
         */
        RETURN_TYPE,

        /**
         * The return type of a method in a member summary.
         */
        SUMMARY_RETURN_TYPE,

        /**
         * The type of a method/constructor parameter.
         */
        EXECUTABLE_MEMBER_PARAM,

        /**
         * Super interface links.
         */
        SUPER_INTERFACES,

        /**
         * Implemented interface links.
         */
        IMPLEMENTED_INTERFACES,

        /**
         * Implemented class links.
         */
        IMPLEMENTED_CLASSES,

        /**
         * Subinterface links.
         */
        SUBINTERFACES,

        /**
         * Subclasses links.
         */
        SUBCLASSES,

        /**
         * The signature in the class documentation (implements/extends portion).
         */
        CLASS_SIGNATURE_PARENT_NAME,

        /**
         * The header for method documentation copied from parent.
         */
        EXECUTABLE_ELEMENT_COPY,

        /**
         * Method "specified by" link.
         */
        METHOD_SPECIFIED_BY,

        /**
         * Method "overrides" link.
         */
        METHOD_OVERRIDES,

        /**
         * Annotation link.
         */
        ANNOTATION,

        /**
         * The header for field documentation copied from parent.
         */
        VARIABLE_ELEMENT_COPY,

        /**
         * The parent nodes in the class tree.
         */
        CLASS_TREE_PARENT,

        /**
         * The type parameters of a method or constructor.
         */
        MEMBER_TYPE_PARAMS,

        /**
         * Indicate that the link appears in class use documentation.
         */
        CLASS_USE_HEADER,

        /**
         * The header for property documentation copied from parent.
         */
        PROPERTY_COPY,

        /**
         * A receiver type
         */
        RECEIVER_TYPE
    }

    public final ConfigurationImpl configuration;

    /**
     * The location of the link.
     */
    public Kind context = Kind.DEFAULT;

    /**
     * The value of the marker #.
     */
    public String where = "";

    /**
     * String style of text defined in style sheet.
     */
    public String styleName = "";

    /**
     * The value of the target.
     */
    public String target = "";
    public  final Utils utils;
    /**
     * Construct a LinkInfo object.
     *
     * @param configuration the configuration data for the doclet
     * @param context    the context of the link.
     * @param ee   the member to link to.
     */
    public LinkInfoImpl(ConfigurationImpl configuration, Kind context, ExecutableElement ee) {
        this.configuration = configuration;
        this.utils = configuration.utils;
        this.executableElement = ee;
        setContext(context);
    }

    /**
     * {@inheritDoc}
     */
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
    public LinkInfoImpl(ConfigurationImpl configuration, Kind context, TypeElement typeElement) {
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
    public LinkInfoImpl(ConfigurationImpl configuration, Kind context, TypeMirror type) {
        this.configuration = configuration;
        this.utils = configuration.utils;
        this.type = type;
        setContext(context);
    }

    /**
     * Set the label for the link.
     * @param label plain-text label for the link
     */
    public LinkInfoImpl label(CharSequence label) {
        this.label = new StringContent(label);
        return this;
    }

    /**
     * Set the label for the link.
     */
    public LinkInfoImpl label(Content label) {
        this.label = label;
        return this;
    }

    /**
     * Set whether or not the link should be strong.
     */
    public LinkInfoImpl strong(boolean strong) {
        this.isStrong = strong;
        return this;
    }

    /**
     * Set the style to be used for the link.
     * @param styleName  String style of text defined in style sheet.
     */
    public LinkInfoImpl styleName(String styleName) {
        this.styleName = styleName;
        return this;
    }

    /**
     * Set the target to be used for the link.
     * @param styleName  String style of text defined in style sheet.
     */
    public LinkInfoImpl target(String target) {
        this.target = target;
        return this;
    }

    /**
     * Set whether or not this is a link to a varargs parameter.
     */
    public LinkInfoImpl varargs(boolean varargs) {
        this.isVarArg = varargs;
        return this;
    }

    /**
     * Set the fragment specifier for the link.
     */
    public LinkInfoImpl where(String where) {
        this.where = where;
        return this;
     }

    /**
     * {@inheritDoc}
     */
    public Kind getContext() {
        return context;
    }

    /**
     * {@inheritDoc}
     *
     * This method sets the link attributes to the appropriate values
     * based on the context.
     *
     * @param c the context id to set.
     */
    public final void setContext(Kind c) {
        //NOTE:  Put context specific link code here.
        switch (c) {
            case ALL_CLASSES_FRAME:
            case PACKAGE_FRAME:
            case IMPLEMENTED_CLASSES:
            case SUBCLASSES:
            case EXECUTABLE_ELEMENT_COPY:
            case VARIABLE_ELEMENT_COPY:
            case PROPERTY_COPY:
            case CLASS_USE_HEADER:
                includeTypeInClassLinkLabel = false;
                break;

            case ANNOTATION:
                excludeTypeParameterLinks = true;
                excludeTypeBounds = true;
                break;

            case IMPLEMENTED_INTERFACES:
            case SUPER_INTERFACES:
            case SUBINTERFACES:
            case CLASS_TREE_PARENT:
            case TREE:
            case CLASS_SIGNATURE_PARENT_NAME:
                excludeTypeParameterLinks = true;
                excludeTypeBounds = true;
                includeTypeInClassLinkLabel = false;
                includeTypeAsSepLink = true;
                break;

            case PACKAGE:
            case CLASS_USE:
            case CLASS_HEADER:
            case CLASS_SIGNATURE:
            case RECEIVER_TYPE:
                excludeTypeParameterLinks = true;
                includeTypeAsSepLink = true;
                includeTypeInClassLinkLabel = false;
                break;

            case MEMBER_TYPE_PARAMS:
                includeTypeAsSepLink = true;
                includeTypeInClassLinkLabel = false;
                break;

            case RETURN_TYPE:
            case SUMMARY_RETURN_TYPE:
                excludeTypeBounds = true;
                break;
            case EXECUTABLE_MEMBER_PARAM:
                excludeTypeBounds = true;
                break;
        }
        context = c;
        if (type != null &&
            utils.isTypeVariable(type) &&
            utils.isExecutableElement(utils.asTypeElement(type).getEnclosingElement())) {
                excludeTypeParameterLinks = true;
        }
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
    public String toString() {
        return "LinkInfoImpl{" +
                "context=" + context +
                ", where=" + where +
                ", styleName=" + styleName +
                ", target=" + target +
                super.toString() + '}';
    }
}
