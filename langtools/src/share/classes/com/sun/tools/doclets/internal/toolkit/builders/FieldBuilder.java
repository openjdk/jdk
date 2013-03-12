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

package com.sun.tools.doclets.internal.toolkit.builders;

import java.util.*;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

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
 * @since 1.5
 */
public class FieldBuilder extends AbstractMemberBuilder {

    /**
     * The class whose fields are being documented.
     */
    private final ClassDoc classDoc;

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
    private final List<ProgramElementDoc> fields;

    /**
     * The index of the current field that is being documented at this point
     * in time.
     */
    private int currentFieldIndex;

    /**
     * Construct a new FieldBuilder.
     *
     * @param context  the build context.
     * @param classDoc the class whoses members are being documented.
     * @param writer the doclet specific writer.
     */
    private FieldBuilder(Context context,
            ClassDoc classDoc,
            FieldWriter writer) {
        super(context);
        this.classDoc = classDoc;
        this.writer = writer;
        visibleMemberMap =
                new VisibleMemberMap(
                classDoc,
                VisibleMemberMap.FIELDS,
                configuration);
        fields =
                new ArrayList<ProgramElementDoc>(visibleMemberMap.getLeafClassMembers(
                configuration));
        if (configuration.getMemberComparator() != null) {
            Collections.sort(fields, configuration.getMemberComparator());
        }
    }

    /**
     * Construct a new FieldBuilder.
     *
     * @param context  the build context.
     * @param classDoc the class whoses members are being documented.
     * @param writer the doclet specific writer.
     */
    public static FieldBuilder getInstance(Context context,
            ClassDoc classDoc,
            FieldWriter writer) {
        return new FieldBuilder(context, classDoc, writer);
    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return "FieldDetails";
    }

    /**
     * Returns a list of fields that will be documented for the given class.
     * This information can be used for doclet specific documentation
     * generation.
     *
     * @param classDoc the {@link ClassDoc} we want to check.
     * @return a list of fields that will be documented.
     */
    public List<ProgramElementDoc> members(ClassDoc classDoc) {
        return visibleMemberMap.getMembersFor(classDoc);
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
    public boolean hasMembersToDocument() {
        return fields.size() > 0;
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
        int size = fields.size();
        if (size > 0) {
            Content fieldDetailsTree = writer.getFieldDetailsTreeHeader(
                    classDoc, memberDetailsTree);
            for (currentFieldIndex = 0; currentFieldIndex < size;
                    currentFieldIndex++) {
                Content fieldDocTree = writer.getFieldDocTreeHeader(
                        (FieldDoc) fields.get(currentFieldIndex),
                        fieldDetailsTree);
                buildChildren(node, fieldDocTree);
                fieldDetailsTree.addContent(writer.getFieldDoc(
                        fieldDocTree, (currentFieldIndex == size - 1)));
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
        fieldDocTree.addContent(
                writer.getSignature((FieldDoc) fields.get(currentFieldIndex)));
    }

    /**
     * Build the deprecation information.
     *
     * @param node the XML element that specifies which components to document
     * @param fieldDocTree the content tree to which the documentation will be added
     */
    public void buildDeprecationInfo(XMLNode node, Content fieldDocTree) {
        writer.addDeprecated(
                (FieldDoc) fields.get(currentFieldIndex), fieldDocTree);
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
            writer.addComments((FieldDoc) fields.get(currentFieldIndex), fieldDocTree);
        }
    }

    /**
     * Build the tag information.
     *
     * @param node the XML element that specifies which components to document
     * @param fieldDocTree the content tree to which the documentation will be added
     */
    public void buildTagInfo(XMLNode node, Content fieldDocTree) {
        writer.addTags((FieldDoc) fields.get(currentFieldIndex), fieldDocTree);
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
