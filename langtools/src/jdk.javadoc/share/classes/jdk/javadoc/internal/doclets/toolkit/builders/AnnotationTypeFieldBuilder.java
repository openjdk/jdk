/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

import jdk.javadoc.internal.doclets.toolkit.AnnotationTypeFieldWriter;
import jdk.javadoc.internal.doclets.toolkit.Configuration;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberMap;


/**
 * Builds documentation for annotation type fields.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Bhavesh Patel
 */
public class AnnotationTypeFieldBuilder extends AbstractMemberBuilder {

    /**
     * The annotation type whose members are being documented.
     */
    protected TypeElement typeElement;

    /**
     * The visible members for the given class.
     */
    protected VisibleMemberMap visibleMemberMap;

    /**
     * The writer to output the member documentation.
     */
    protected AnnotationTypeFieldWriter writer;

    /**
     * The list of members being documented.
     */
    protected SortedSet<Element> members;

    /**
     * The index of the current member that is being documented at this point
     * in time.
     */
    protected Element currentMember;

    /**
     * Construct a new AnnotationTypeFieldsBuilder.
     *
     * @param context  the build context.
     * @param typeElement the class whose members are being documented.
     * @param writer the doclet specific writer.
     * @param memberType the type of member that is being documented.
     */
    protected AnnotationTypeFieldBuilder(Context context,
            TypeElement typeElement,
            AnnotationTypeFieldWriter writer,
            VisibleMemberMap.Kind memberType) {
        super(context);
        this.typeElement = typeElement;
        this.writer = writer;
        this.visibleMemberMap = new VisibleMemberMap(typeElement, memberType, configuration);
        this.members = this.visibleMemberMap.getMembersFor(typeElement);
    }


    /**
     * Construct a new AnnotationTypeFieldBuilder.
     *
     * @param context  the build context.
     * @param typeElement the class whose members are being documented.
     * @param writer the doclet specific writer.
     */
    public static AnnotationTypeFieldBuilder getInstance(
            Context context, TypeElement typeElement,
            AnnotationTypeFieldWriter writer) {
        return new AnnotationTypeFieldBuilder(context, typeElement,
                    writer, VisibleMemberMap.Kind.ANNOTATION_TYPE_FIELDS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "AnnotationTypeFieldDetails";
    }

    /**
     * Returns a list of members that will be documented for the given class.
     * This information can be used for doclet specific documentation
     * generation.
     *
     * @param typeElement the TypeElement we want to check.
     * @return a list of members that will be documented.
     */
    public SortedSet<Element> members(TypeElement typeElement) {
        return visibleMemberMap.getMembersFor(typeElement);
    }

    /**
     * Returns the visible member map for the members of this class.
     *
     * @return the visible member map for the members of this class.
     */
    public VisibleMemberMap getVisibleMemberMap() {
        return visibleMemberMap;
    }

    /**
     * summaryOrder.size()
     */
    @Override
    public boolean hasMembersToDocument() {
        return members.size() > 0;
    }

    /**
     * Build the annotation type field documentation.
     *
     * @param node the XML element that specifies which components to document
     * @param memberDetailsTree the content tree to which the documentation will be added
     */
    public void buildAnnotationTypeField(XMLNode node, Content memberDetailsTree) {
        buildAnnotationTypeMember(node, memberDetailsTree);
    }

    /**
     * Build the member documentation.
     *
     * @param node the XML element that specifies which components to document
     * @param memberDetailsTree the content tree to which the documentation will be added
     */
    public void buildAnnotationTypeMember(XMLNode node, Content memberDetailsTree) {
        if (writer == null) {
            return;
        }
        if (!members.isEmpty()) {
            writer.addAnnotationFieldDetailsMarker(memberDetailsTree);
            for (Element element : members) {
                currentMember = element;
                Content detailsTree = writer.getMemberTreeHeader();
                writer.addAnnotationDetailsTreeHeader(typeElement, detailsTree);
                Content annotationDocTree = writer.getAnnotationDocTreeHeader(element, detailsTree);
                buildChildren(node, annotationDocTree);
                detailsTree.addContent(writer.getAnnotationDoc(
                        annotationDocTree, currentMember == members.last()));
                memberDetailsTree.addContent(writer.getAnnotationDetails(detailsTree));
            }
        }
    }

    /**
     * Build the signature.
     *
     * @param node the XML element that specifies which components to document
     * @param annotationDocTree the content tree to which the documentation will be added
     */
    public void buildSignature(XMLNode node, Content annotationDocTree) {
        annotationDocTree.addContent(
                writer.getSignature(currentMember));
    }

    /**
     * Build the deprecation information.
     *
     * @param node the XML element that specifies which components to document
     * @param annotationDocTree the content tree to which the documentation will be added
     */
    public void buildDeprecationInfo(XMLNode node, Content annotationDocTree) {
        writer.addDeprecated(currentMember, annotationDocTree);
    }

    /**
     * Build the comments for the member.  Do nothing if
     * {@link Configuration#nocomment} is set to true.
     *
     * @param node the XML element that specifies which components to document
     * @param annotationDocTree the content tree to which the documentation will be added
     */
    public void buildMemberComments(XMLNode node, Content annotationDocTree) {
        if(! configuration.nocomment){
            writer.addComments(currentMember, annotationDocTree);
        }
    }

    /**
     * Build the tag information.
     *
     * @param node the XML element that specifies which components to document
     * @param annotationDocTree the content tree to which the documentation will be added
     */
    public void buildTagInfo(XMLNode node, Content annotationDocTree) {
        writer.addTags(currentMember, annotationDocTree);
    }

    /**
     * Return the annotation type field writer for this builder.
     *
     * @return the annotation type field writer for this builder.
     */
    public AnnotationTypeFieldWriter getWriter() {
        return writer;
    }
}
