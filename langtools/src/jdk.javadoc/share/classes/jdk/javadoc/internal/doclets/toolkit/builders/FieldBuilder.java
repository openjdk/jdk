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

package jdk.javadoc.internal.doclets.toolkit.builders;

import java.util.*;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import jdk.javadoc.internal.doclets.toolkit.Configuration;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.FieldWriter;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberMap;


/**
 * Builds documentation for a field.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 */
public class FieldBuilder extends AbstractMemberBuilder {

    /**
     * The class whose fields are being documented.
     */
    private final TypeElement typeElement;

    /**
     * The visible fields for the given class.
     */
    private final VisibleMemberMap visibleMemberMap;

    /**
     * The writer to output the field documentation.
     */
    private final FieldWriter writer;

    /**
     * The list of fields being documented.
     */
    private final SortedSet<Element> fields;

    /**
     * The index of the current field that is being documented at this point
     * in time.
     */
    private VariableElement currentElement;

    /**
     * Construct a new FieldBuilder.
     *
     * @param context  the build context.
     * @param typeElement the class whoses members are being documented.
     * @param writer the doclet specific writer.
     */
    private FieldBuilder(Context context,
            TypeElement typeElement,
            FieldWriter writer) {
        super(context);
        this.typeElement = typeElement;
        this.writer = writer;
        visibleMemberMap =
                new VisibleMemberMap(
                typeElement,
                VisibleMemberMap.Kind.FIELDS,
                configuration);
        fields = visibleMemberMap.getLeafClassMembers();
    }

    /**
     * Construct a new FieldBuilder.
     *
     * @param context  the build context.
     * @param typeElement the class whoses members are being documented.
     * @param writer the doclet specific writer.
     */
    public static FieldBuilder getInstance(Context context,
            TypeElement typeElement,
            FieldWriter writer) {
        return new FieldBuilder(context, typeElement, writer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "FieldDetails";
    }

    /**
     * Returns a list of fields that will be documented for the given class.
     * This information can be used for doclet specific documentation
     * generation.
     *
     * @param typeElement the {@link TypeElement} we want to check.
     * @return a list of fields that will be documented.
     */
    public SortedSet<Element> members(TypeElement typeElement) {
        return visibleMemberMap.getMembersFor(typeElement);
    }

    /**
     * Returns the visible member map for the fields of this class.
     *
     * @return the visible member map for the fields of this class.
     */
    public VisibleMemberMap getVisibleMemberMap() {
        return visibleMemberMap;
    }

    /**
     * summaryOrder.size()
     */
    @Override
    public boolean hasMembersToDocument() {
        return !fields.isEmpty();
    }

    /**
     * Build the field documentation.
     *
     * @param node the XML element that specifies which components to document
     * @param memberDetailsTree the content tree to which the documentation will be added
     */
    public void buildFieldDoc(XMLNode node, Content memberDetailsTree) {
        if (writer == null) {
            return;
        }
        if (!fields.isEmpty()) {
            Content fieldDetailsTree = writer.getFieldDetailsTreeHeader(typeElement, memberDetailsTree);
            for (Element element : fields) {
                currentElement = (VariableElement)element;
                Content fieldDocTree = writer.getFieldDocTreeHeader(currentElement, fieldDetailsTree);
                buildChildren(node, fieldDocTree);
                fieldDetailsTree.addContent(writer.getFieldDoc(
                        fieldDocTree, currentElement.equals(fields.last())));
            }
            memberDetailsTree.addContent(
                    writer.getFieldDetails(fieldDetailsTree));
        }
    }

    /**
     * Build the signature.
     *
     * @param node the XML element that specifies which components to document
     * @param fieldDocTree the content tree to which the documentation will be added
     */
    public void buildSignature(XMLNode node, Content fieldDocTree) {
        fieldDocTree.addContent(writer.getSignature(currentElement));
    }

    /**
     * Build the deprecation information.
     *
     * @param node the XML element that specifies which components to document
     * @param fieldDocTree the content tree to which the documentation will be added
     */
    public void buildDeprecationInfo(XMLNode node, Content fieldDocTree) {
        writer.addDeprecated(currentElement, fieldDocTree);
    }

    /**
     * Build the comments for the field.  Do nothing if
     * {@link Configuration#nocomment} is set to true.
     *
     * @param node the XML element that specifies which components to document
     * @param fieldDocTree the content tree to which the documentation will be added
     */
    public void buildFieldComments(XMLNode node, Content fieldDocTree) {
        if (!configuration.nocomment) {
            writer.addComments(currentElement, fieldDocTree);
        }
    }

    /**
     * Build the tag information.
     *
     * @param node the XML element that specifies which components to document
     * @param fieldDocTree the content tree to which the documentation will be added
     */
    public void buildTagInfo(XMLNode node, Content fieldDocTree) {
        writer.addTags(currentElement, fieldDocTree);
    }

    /**
     * Return the field writer for this builder.
     *
     * @return the field writer for this builder.
     */
    public FieldWriter getWriter() {
        return writer;
    }
}
