/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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


package com.sun.tools.doclets.formats.html;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.tools.doclets.internal.toolkit.util.links.*;

/**
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class LinkInfoImpl extends LinkInfo {

    /**
     * Indicate that the link appears in a class list.
     */
    public static final int ALL_CLASSES_FRAME = 1;

    /**
     * Indicate that the link appears in a class documentation.
     */
    public static final int CONTEXT_CLASS = 2;

    /**
     * Indicate that the link appears in member documentation.
     */
    public static final int CONTEXT_MEMBER = 3;

    /**
     * Indicate that the link appears in class use documentation.
     */
    public static final int CONTEXT_CLASS_USE = 4;

    /**
     * Indicate that the link appears in index documentation.
     */
    public static final int CONTEXT_INDEX = 5;

    /**
     * Indicate that the link appears in constant value summary.
     */
    public static final int CONTEXT_CONSTANT_SUMMARY = 6;

    /**
     * Indicate that the link appears in serialized form documentation.
     */
    public static final int CONTEXT_SERIALIZED_FORM = 7;

    /**
     * Indicate that the link appears in serial member documentation.
     */
    public static final int CONTEXT_SERIAL_MEMBER = 8;

    /**
     * Indicate that the link appears in package documentation.
     */
    public static final int CONTEXT_PACKAGE = 9;

    /**
     * Indicate that the link appears in see tag documentation.
     */
    public static final int CONTEXT_SEE_TAG = 10;

    /**
     * Indicate that the link appears in value tag documentation.
     */
    public static final int CONTEXT_VALUE_TAG = 11;

    /**
     * Indicate that the link appears in tree documentation.
     */
    public static final int CONTEXT_TREE = 12;

    /**
     * Indicate that the link appears in a class list.
     */
    public static final int PACKAGE_FRAME = 13;

    /**
     * The header in the class documentation.
     */
    public static final int CONTEXT_CLASS_HEADER = 14;

    /**
     * The signature in the class documentation.
     */
    public static final int CONTEXT_CLASS_SIGNATURE = 15;

    /**
     * The return type of a method.
     */
    public static final int CONTEXT_RETURN_TYPE = 16;

    /**
     * The return type of a method in a member summary.
     */
    public static final int CONTEXT_SUMMARY_RETURN_TYPE = 17;

    /**
     * The type of a method/constructor parameter.
     */
    public static final int CONTEXT_EXECUTABLE_MEMBER_PARAM = 18;

    /**
     * Super interface links.
     */
    public static final int CONTEXT_SUPER_INTERFACES = 19;

    /**
     * Implemented interface links.
     */
    public static final int CONTEXT_IMPLEMENTED_INTERFACES = 20;

    /**
     * Implemented class links.
     */
    public static final int CONTEXT_IMPLEMENTED_CLASSES = 21;

    /**
     * Subinterface links.
     */
    public static final int CONTEXT_SUBINTERFACES = 22;

    /**
     * Subclasses links.
     */
    public static final int CONTEXT_SUBCLASSES = 23;

    /**
     * The signature in the class documentation (implements/extends portion).
     */
    public static final int CONTEXT_CLASS_SIGNATURE_PARENT_NAME = 24;

    /**
     * The header for method documentation copied from parent.
     */
    public static final int CONTEXT_METHOD_DOC_COPY = 26;

    /**
     * Method "specified by" link.
     */
    public static final int CONTEXT_METHOD_SPECIFIED_BY = 27;

    /**
     * Method "overrides" link.
     */
    public static final int CONTEXT_METHOD_OVERRIDES = 28;

    /**
     * Annotation link.
     */
    public static final int CONTEXT_ANNOTATION = 29;

    /**
     * The header for field documentation copied from parent.
     */
    public static final int CONTEXT_FIELD_DOC_COPY = 30;

    /**
     * The parent nodes int the class tree.
     */
    public static final int CONTEXT_CLASS_TREE_PARENT = 31;

    /**
     * The type parameters of a method or constructor.
     */
    public static final int CONTEXT_MEMBER_TYPE_PARAMS = 32;

    /**
     * Indicate that the link appears in class use documentation.
     */
    public static final int CONTEXT_CLASS_USE_HEADER = 33;

    /**
     * The header for property documentation copied from parent.
     */
    public static final int CONTEXT_PROPERTY_DOC_COPY = 34;

    public final ConfigurationImpl configuration;

    /**
     * The integer indicating the location of the link.
     */
    public int context;

    /**
     * The value of the marker #.
     */
    public String where = "";

    /**
     * String style of text defined in style sheet.
     */
    public String styleName ="";

    /**
     * The value of the target.
     */
    public String target = "";

    /**
     * Construct a LinkInfo object.
     *
     * @param configuration the configuration data for the doclet
     * @param context    the context of the link.
     * @param classDoc   the class to link to.
     * @param label      the label for the link.
     * @param target     the value of the target attribute.
     */
    public LinkInfoImpl(ConfigurationImpl configuration,
            int context, ClassDoc classDoc, String label, String target) {
        this.configuration = configuration;
        this.classDoc = classDoc;
        this.label = label;
        this.target = target;
        setContext(context);
    }

    /**
     * Construct a LinkInfo object.
     *
     * @param configuration the configuration data for the doclet
     * @param context    the context of the link.
     * @param classDoc   the class to link to.
     * @param where      the value of the marker #.
     * @param label      the label for the link.
     * @param isStrong       true if the link should be strong.
     * @param styleName  String style of text defined in style sheet.
     */
    public LinkInfoImpl(ConfigurationImpl configuration,
            int context, ClassDoc classDoc, String where, String label,
            boolean isStrong, String styleName) {
        this.configuration = configuration;
        this.classDoc = classDoc;
        this.where = where;
        this.label = label;
        this.isStrong = isStrong;
        this.styleName = styleName;
        setContext(context);
    }

    /**
     * Construct a LinkInfo object.
     *
     * @param configuration the configuration data for the doclet
     * @param context    the context of the link.
     * @param classDoc   the class to link to.
     * @param where      the value of the marker #.
     * @param label      the label for the link.
     * @param isStrong       true if the link should be strong.
     */
    public LinkInfoImpl(ConfigurationImpl configuration,
            int context, ClassDoc classDoc, String where, String label,
            boolean isStrong) {
        this.configuration = configuration;
        this.classDoc = classDoc;
        this.where = where;
        this.label = label;
        this.isStrong = isStrong;
        setContext(context);
    }

    /**
     * Construct a LinkInfo object.
     *
     * @param configuration the configuration data for the doclet
     * @param classDoc   the class to link to.
     * @param label      the label for the link.
     */
    public LinkInfoImpl(ConfigurationImpl configuration,
            ClassDoc classDoc, String label) {
        this.configuration = configuration;
        this.classDoc = classDoc;
        this.label = label;
        setContext(context);
    }

    /**
     * Construct a LinkInfo object.
     *
     * @param configuration the configuration data for the doclet
     * @param context               the context of the link.
     * @param executableMemberDoc   the member to link to.
     * @param isStrong                true if the link should be strong.
     */
    public LinkInfoImpl(ConfigurationImpl configuration,
            int context, ExecutableMemberDoc executableMemberDoc,
            boolean isStrong) {
        this.configuration = configuration;
        this.executableMemberDoc = executableMemberDoc;
        this.isStrong = isStrong;
        setContext(context);
    }

    /**
     * Construct a LinkInfo object.
     *
     * @param configuration the configuration data for the doclet
     * @param context    the context of the link.
     * @param classDoc   the class to link to.
     * @param isStrong       true if the link should be strong.
     */
    public LinkInfoImpl(ConfigurationImpl configuration,
            int context, ClassDoc classDoc,  boolean isStrong) {
        this.configuration = configuration;
        this.classDoc = classDoc;
        this.isStrong = isStrong;
        setContext(context);
    }

    /**
     * Construct a LinkInfo object.
     *
     * @param configuration the configuration data for the doclet
     * @param context    the context of the link.
     * @param type       the class to link to.
     */
    public LinkInfoImpl(ConfigurationImpl configuration,
            int context, Type type) {
        this.configuration = configuration;
        this.type = type;
        setContext(context);
    }

    /**
     * Construct a LinkInfo object.
     *
     * @param configuration the configuration data for the doclet
     * @param context    the context of the link.
     * @param type       the class to link to.
     * @param isVarArg   true if this is a link to a var arg.
     */
    public LinkInfoImpl(ConfigurationImpl configuration,
            int context, Type type, boolean isVarArg) {
        this.configuration = configuration;
        this.type = type;
        this.isVarArg = isVarArg;
        setContext(context);
    }

    /**
     * Construct a LinkInfo object.
     *
     * @param configuration the configuration data for the doclet
     * @param context    the context of the link.
     * @param type       the class to link to.
     * @param label      the label for the link.
     * @param isStrong     true if the link should be strong.
     */
    public LinkInfoImpl(ConfigurationImpl configuration,
            int context, Type type, String label,
            boolean isStrong) {
        this.configuration = configuration;
        this.type = type;
        this.label = label;
        this.isStrong = isStrong;
        setContext(context);
    }

    /**
     * Construct a LinkInfo object.
     *
     * @param configuration the configuration data for the doclet
     * @param context    the context of the link.
     * @param classDoc   the class to link to.
     * @param label      the label for the link.
     * @param isStrong       true if the link should be strong.
     */
    public LinkInfoImpl(ConfigurationImpl configuration,
            int context, ClassDoc classDoc, String label,
            boolean isStrong) {
        this.configuration = configuration;
        this.classDoc = classDoc;
        this.label = label;
        this.isStrong = isStrong;
        setContext(context);
    }

    /**
     * {@inheritDoc}
     */
    public int getContext() {
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
    public void setContext(int c) {
        //NOTE:  Put context specific link code here.
        switch (c) {
            case ALL_CLASSES_FRAME:
            case PACKAGE_FRAME:
            case CONTEXT_IMPLEMENTED_CLASSES:
            case CONTEXT_SUBCLASSES:
            case CONTEXT_METHOD_DOC_COPY:
            case CONTEXT_FIELD_DOC_COPY:
            case CONTEXT_PROPERTY_DOC_COPY:
            case CONTEXT_CLASS_USE_HEADER:
                includeTypeInClassLinkLabel = false;
                break;

            case CONTEXT_ANNOTATION:
                excludeTypeParameterLinks = true;
                excludeTypeBounds = true;
                break;

            case CONTEXT_IMPLEMENTED_INTERFACES:
            case CONTEXT_SUPER_INTERFACES:
            case CONTEXT_SUBINTERFACES:
            case CONTEXT_CLASS_TREE_PARENT:
            case CONTEXT_TREE:
            case CONTEXT_CLASS_SIGNATURE_PARENT_NAME:
                excludeTypeParameterLinks = true;
                excludeTypeBounds = true;
                includeTypeInClassLinkLabel = false;
                includeTypeAsSepLink = true;
                break;

            case CONTEXT_PACKAGE:
            case CONTEXT_CLASS_USE:
            case CONTEXT_CLASS_HEADER:
            case CONTEXT_CLASS_SIGNATURE:
                excludeTypeParameterLinks = true;
                includeTypeAsSepLink = true;
                includeTypeInClassLinkLabel = false;
                break;

            case CONTEXT_MEMBER_TYPE_PARAMS:
                includeTypeAsSepLink = true;
                includeTypeInClassLinkLabel = false;
                break;

            case CONTEXT_RETURN_TYPE:
            case CONTEXT_SUMMARY_RETURN_TYPE:
                excludeTypeBounds = true;
                break;
            case CONTEXT_EXECUTABLE_MEMBER_PARAM:
                excludeTypeBounds = true;
                break;
        }
        context = c;
        if (type != null &&
            type.asTypeVariable()!= null &&
            type.asTypeVariable().owner() instanceof ExecutableMemberDoc){
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
    public boolean isLinkable() {
        return Util.isLinkable(classDoc, configuration);
    }
}
