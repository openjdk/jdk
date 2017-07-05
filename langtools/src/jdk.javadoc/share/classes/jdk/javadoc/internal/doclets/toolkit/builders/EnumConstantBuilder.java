/*
 * Copyright (c) 2003, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.builders;

import java.util.*;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import jdk.javadoc.internal.doclets.toolkit.Configuration;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.DocletException;
import jdk.javadoc.internal.doclets.toolkit.EnumConstantWriter;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberMap;


/**
 * Builds documentation for a enum constants.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 */
public class EnumConstantBuilder extends AbstractMemberBuilder {

    /**
     * The class whose enum constants are being documented.
     */
    private final TypeElement typeElement;

    /**
     * The visible enum constantss for the given class.
     */
    private final VisibleMemberMap visibleMemberMap;

    /**
     * The writer to output the enum constants documentation.
     */
    private final EnumConstantWriter writer;

    /**
     * The set of enum constants being documented.
     */
    private final List<Element> enumConstants;

    /**
     * The current enum constant that is being documented at this point
     * in time.
     */
    private VariableElement currentElement;

    /**
     * Construct a new EnumConstantsBuilder.
     *
     * @param context  the build context.
     * @param typeElement the class whose members are being documented.
     * @param writer the doclet specific writer.
     */
    private EnumConstantBuilder(Context context,
            TypeElement typeElement, EnumConstantWriter writer) {
        super(context);
        this.typeElement = typeElement;
        this.writer = writer;
        visibleMemberMap = configuration.getVisibleMemberMap(typeElement,
                VisibleMemberMap.Kind.ENUM_CONSTANTS);
        enumConstants = visibleMemberMap.getMembers(typeElement);
    }

    /**
     * Construct a new EnumConstantsBuilder.
     *
     * @param context  the build context.
     * @param typeElement the class whoses members are being documented.
     * @param writer the doclet specific writer.
     * @return the new EnumConstantsBuilder
     */
    public static EnumConstantBuilder getInstance(Context context,
            TypeElement typeElement, EnumConstantWriter writer) {
        return new EnumConstantBuilder(context, typeElement, writer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "EnumConstantDetails";
    }

    /**
     * Returns whether or not there are members to document.
     *
     * @return whether or not there are members to document
     */
    @Override
    public boolean hasMembersToDocument() {
        return !enumConstants.isEmpty();
    }

    /**
     * Build the enum constant documentation.
     *
     * @param node the XML element that specifies which components to document
     * @param memberDetailsTree the content tree to which the documentation will be added
     * @throws DocletException is there is a problem while building the documentation
     */
    public void buildEnumConstant(XMLNode node, Content memberDetailsTree) throws DocletException {
        if (writer == null) {
            return;
        }
        if (hasMembersToDocument()) {
            Content enumConstantsDetailsTree = writer.getEnumConstantsDetailsTreeHeader(typeElement,
                    memberDetailsTree);
            Element lastElement = enumConstants.get(enumConstants.size() - 1);
            for (Element enumConstant : enumConstants) {
                currentElement = (VariableElement)enumConstant;
                Content enumConstantsTree = writer.getEnumConstantsTreeHeader(currentElement,
                        enumConstantsDetailsTree);
                buildChildren(node, enumConstantsTree);
                enumConstantsDetailsTree.addContent(writer.getEnumConstants(
                        enumConstantsTree, currentElement == lastElement));
            }
            memberDetailsTree.addContent(
                    writer.getEnumConstantsDetails(enumConstantsDetailsTree));
        }
    }

    /**
     * Build the signature.
     *
     * @param node the XML element that specifies which components to document
     * @param enumConstantsTree the content tree to which the documentation will be added
     */
    public void buildSignature(XMLNode node, Content enumConstantsTree) {
        enumConstantsTree.addContent(writer.getSignature(currentElement));
    }

    /**
     * Build the deprecation information.
     *
     * @param node the XML element that specifies which components to document
     * @param enumConstantsTree the content tree to which the documentation will be added
     */
    public void buildDeprecationInfo(XMLNode node, Content enumConstantsTree) {
        writer.addDeprecated(currentElement, enumConstantsTree);
    }

    /**
     * Build the comments for the enum constant.  Do nothing if
     * {@link Configuration#nocomment} is set to true.
     *
     * @param node the XML element that specifies which components to document
     * @param enumConstantsTree the content tree to which the documentation will be added
     */
    public void buildEnumConstantComments(XMLNode node, Content enumConstantsTree) {
        if (!configuration.nocomment) {
            writer.addComments(currentElement, enumConstantsTree);
        }
    }

    /**
     * Build the tag information.
     *
     * @param node the XML element that specifies which components to document
     * @param enumConstantsTree the content tree to which the documentation will be added
     */
    public void buildTagInfo(XMLNode node, Content enumConstantsTree) {
        writer.addTags(currentElement, enumConstantsTree);
    }

    /**
     * Return the enum constant writer for this builder.
     *
     * @return the enum constant writer for this builder.
     */
    public EnumConstantWriter getWriter() {
        return writer;
    }
}
