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

package com.sun.tools.doclets.internal.toolkit.util.links;

import com.sun.javadoc.*;

/**
 * A factory that constructs links from given link information.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 * @since 1.5
 */
public abstract class LinkFactory {

    /**
     * Return an empty instance of the link output object.
     *
     * @return an empty instance of the link output object.
     */
    protected abstract LinkOutput getOutputInstance();

    /**
     * Constructs a link from the given link information.
     *
     * @param linkInfo the information about the link.
     * @return the output of the link.
     */
    public LinkOutput getLinkOutput(LinkInfo linkInfo) {
        if (linkInfo.type != null) {
            Type type = linkInfo.type;
            LinkOutput linkOutput = getOutputInstance();
            if (type.isPrimitive()) {
                //Just a primitive.
                linkInfo.displayLength += type.typeName().length();
                linkOutput.append(type.typeName());
            } else if (type.asAnnotatedType() != null) {
                linkOutput.append(getTypeAnnotationLinks(linkInfo));
                linkInfo.type = type.asAnnotatedType().underlyingType();
                linkOutput.append(getLinkOutput(linkInfo));
                return linkOutput;
            } else if (type.asWildcardType() != null) {
                //Wildcard type.
                linkInfo.isTypeBound = true;
                linkInfo.displayLength += 1;
                linkOutput.append("?");
                WildcardType wildcardType = type.asWildcardType();
                Type[] extendsBounds = wildcardType.extendsBounds();
                for (int i = 0; i < extendsBounds.length; i++) {
                    linkInfo.displayLength += i > 0 ? 2 : 9;
                    linkOutput.append(i > 0 ? ", " : " extends ");
                    setBoundsLinkInfo(linkInfo, extendsBounds[i]);
                    linkOutput.append(getLinkOutput(linkInfo));
                }
                Type[] superBounds = wildcardType.superBounds();
                for (int i = 0; i < superBounds.length; i++) {
                    linkInfo.displayLength += i > 0 ? 2 : 7;
                    linkOutput.append(i > 0 ? ", " : " super ");
                    setBoundsLinkInfo(linkInfo, superBounds[i]);
                    linkOutput.append(getLinkOutput(linkInfo));
                }
            } else if (type.asTypeVariable()!= null) {
                linkOutput.append(getTypeAnnotationLinks(linkInfo));
                linkInfo.isTypeBound = true;
                //A type variable.
                Doc owner = type.asTypeVariable().owner();
                if ((! linkInfo.excludeTypeParameterLinks) &&
                        owner instanceof ClassDoc) {
                    linkInfo.classDoc = (ClassDoc) owner;
                    linkInfo.label = type.typeName();
                    linkOutput.append(getClassLink(linkInfo));
                } else {
                    //No need to link method type parameters.
                    linkInfo.displayLength += type.typeName().length();
                    linkOutput.append(type.typeName());
                }

                Type[] bounds = type.asTypeVariable().bounds();
                if (! linkInfo.excludeTypeBounds) {
                    linkInfo.excludeTypeBounds = true;
                    for (int i = 0; i < bounds.length; i++) {
                        linkInfo.displayLength += i > 0 ? 2 : 9;
                        linkOutput.append(i > 0 ? " & " : " extends ");
                        setBoundsLinkInfo(linkInfo, bounds[i]);
                        linkOutput.append(getLinkOutput(linkInfo));
                    }
                }
            } else if (type.asClassDoc() != null) {
                //A class type.
                if (linkInfo.isTypeBound &&
                        linkInfo.excludeTypeBoundsLinks) {
                    //Since we are excluding type parameter links, we should not
                    //be linking to the type bound.
                    linkInfo.displayLength += type.typeName().length();
                    linkOutput.append(type.typeName());
                    linkOutput.append(getTypeParameterLinks(linkInfo));
                    return linkOutput;
                } else {
                    linkInfo.classDoc = type.asClassDoc();
                    linkOutput = getClassLink(linkInfo);
                    if (linkInfo.includeTypeAsSepLink) {
                        linkOutput.append(getTypeParameterLinks(linkInfo, false));
                    }
                }
            }

            if (linkInfo.isVarArg) {
                if (type.dimension().length() > 2) {
                    //Javadoc returns var args as array.
                    //Strip out the first [] from the var arg.
                    linkInfo.displayLength += type.dimension().length()-2;
                    linkOutput.append(type.dimension().substring(2));
                }
                linkInfo.displayLength += 3;
                linkOutput.append("...");
            } else {
                linkInfo.displayLength += type.dimension().length();
                linkOutput.append(type.dimension());
            }
            return linkOutput;
        } else if (linkInfo.classDoc != null) {
            //Just a class link
            LinkOutput linkOutput = getClassLink(linkInfo);
            if (linkInfo.includeTypeAsSepLink) {
                linkOutput.append(getTypeParameterLinks(linkInfo, false));
            }
            return linkOutput;
        } else {
            return null;
        }
    }

    private void setBoundsLinkInfo(LinkInfo linkInfo, Type bound) {
        linkInfo.classDoc = null;
        linkInfo.label = null;
        linkInfo.type = bound;
    }

    /**
     * Return the link to the given class.
     *
     * @param linkInfo the information about the link to construct.
     *
     * @return the link for the given class.
     */
    protected abstract LinkOutput getClassLink(LinkInfo linkInfo);

    /**
     * Return the link to the given type parameter.
     *
     * @param linkInfo     the information about the link to construct.
     * @param typeParam the type parameter to link to.
     */
    protected abstract LinkOutput getTypeParameterLink(LinkInfo linkInfo,
        Type typeParam);

    protected abstract LinkOutput getTypeAnnotationLink(LinkInfo linkInfo,
            AnnotationDesc annotation);

    /**
     * Return the links to the type parameters.
     *
     * @param linkInfo     the information about the link to construct.
     * @return the links to the type parameters.
     */
    public LinkOutput getTypeParameterLinks(LinkInfo linkInfo) {
        return getTypeParameterLinks(linkInfo, true);
    }

    /**
     * Return the links to the type parameters.
     *
     * @param linkInfo     the information about the link to construct.
     * @param isClassLabel true if this is a class label.  False if it is
     *                     the type parameters portion of the link.
     * @return the links to the type parameters.
     */
    public LinkOutput getTypeParameterLinks(LinkInfo linkInfo, boolean isClassLabel) {
        LinkOutput output = getOutputInstance();
        Type[] vars;
        if (linkInfo.executableMemberDoc != null) {
            vars = linkInfo.executableMemberDoc.typeParameters();
        } else if (linkInfo.type != null &&
                linkInfo.type.asParameterizedType() != null){
            vars =  linkInfo.type.asParameterizedType().typeArguments();
        } else if (linkInfo.classDoc != null){
            vars = linkInfo.classDoc.typeParameters();
        } else {
            //Nothing to document.
            return output;
        }
        if (((linkInfo.includeTypeInClassLinkLabel && isClassLabel) ||
             (linkInfo.includeTypeAsSepLink && ! isClassLabel)
              )
            && vars.length > 0) {
            linkInfo.displayLength += 1;
            output.append(getLessThanString());
            for (int i = 0; i < vars.length; i++) {
                if (i > 0) {
                    linkInfo.displayLength += 1;
                    output.append(",");
                }
                output.append(getTypeParameterLink(linkInfo, vars[i]));
            }
            linkInfo.displayLength += 1;
            output.append(getGreaterThanString());
        }
        return output;
    }

    public LinkOutput getTypeAnnotationLinks(LinkInfo linkInfo) {
        LinkOutput output = getOutputInstance();
        if (linkInfo.type.asAnnotatedType() == null)
            return output;
        AnnotationDesc[] annotations = linkInfo.type.asAnnotatedType().annotations();
        for (int i = 0; i < annotations.length; i++) {
            if (i > 0) {
                linkInfo.displayLength += 1;
                output.append(" ");
            }
            output.append(getTypeAnnotationLink(linkInfo, annotations[i]));
        }

        linkInfo.displayLength += 1;
        output.append(" ");
        return output;
    }

    /**
     * Return &amp;lt;, which is used in type parameters.  Override this
     * if your doclet uses something different.
     *
     * @return return &amp;lt;, which is used in type parameters.
     */
    protected String getLessThanString() {
        return "&lt;";
    }

    /**
     * Return &amp;gt;, which is used in type parameters.  Override this
     * if your doclet uses something different.
     *
     * @return return &amp;gt;, which is used in type parameters.
     */
    protected String getGreaterThanString() {
        return "&gt;";
    }
}
