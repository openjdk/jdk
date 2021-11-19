/*
 * Copyright (c) 2003, 2021, Oracle and/or its affiliates. All rights reserved.
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

import jdk.javadoc.internal.doclets.toolkit.AnnotationTypeMemberWriter;
import jdk.javadoc.internal.doclets.toolkit.BaseOptions;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.DocletException;

import static jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable.Kind.*;

/**
 * Builds documentation for required annotation type members.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class AnnotationTypeMemberBuilder extends AbstractMemberBuilder {

    /**
     * The writer to output the member documentation.
     */
    protected AnnotationTypeMemberWriter writer;

    /**
     * The list of members being documented.
     */
    protected List<Element> members;

    /**
     * The index of the current member that is being documented at this point
     * in time.
     */
    protected Element currentMember;

    /**
     * Construct a new AnnotationTypeRequiredMemberBuilder.
     *
     * @param context  the build context.
     * @param typeElement the class whose members are being documented.
     * @param writer the doclet specific writer.
     */
    protected AnnotationTypeMemberBuilder(Context context,
                                          TypeElement typeElement,
                                          AnnotationTypeMemberWriter writer) {
        super(context, typeElement);
        this.writer = Objects.requireNonNull(writer);
        // In contrast to the annotation interface member summaries the details generated
        // by this builder share a single list for both required and optional members.
        this.members = getVisibleMembers(ANNOTATION_TYPE_MEMBER);
    }


    /**
     * Construct a new AnnotationTypeMemberBuilder.
     *
     * @param context  the build context.
     * @param typeElement the class whose members are being documented.
     * @param writer the doclet specific writer.
     * @return an instance of this object
     */
    public static AnnotationTypeMemberBuilder getInstance(
            Context context, TypeElement typeElement,
            AnnotationTypeMemberWriter writer) {
        return new AnnotationTypeMemberBuilder(context, typeElement,
                writer);
    }

    /**
     * Returns whether or not there are members to document.
     * @return whether or not there are members to document
     */
    @Override
    public boolean hasMembersToDocument() {
        return !members.isEmpty();
    }

    @Override
    public void build(Content contentTree) throws DocletException {
        buildAnnotationTypeMember(contentTree);
    }

    /**
     * Build the member documentation.
     *
     * @param detailsList the content tree to which the documentation will be added
     * @throws DocletException if an error occurs
     */
    protected void buildAnnotationTypeMember(Content detailsList)
            throws DocletException {
        if (hasMembersToDocument()) {
            writer.addAnnotationDetailsMarker(detailsList);
            Content annotationDetailsTreeHeader = writer.getAnnotationDetailsTreeHeader();
            Content memberList = writer.getMemberList();

            for (Element member : members) {
                currentMember = member;
                Content annotationDocTree = writer.getAnnotationDocTreeHeader(currentMember);

                buildAnnotationTypeMemberChildren(annotationDocTree);

                memberList.add(writer.getMemberListItem(annotationDocTree));
            }
            Content annotationDetails = writer.getAnnotationDetails(annotationDetailsTreeHeader, memberList);
            detailsList.add(annotationDetails);
        }
    }

    protected void buildAnnotationTypeMemberChildren(Content annotationDocTree) {
        buildSignature(annotationDocTree);
        buildDeprecationInfo(annotationDocTree);
        buildPreviewInfo(annotationDocTree);
        buildMemberComments(annotationDocTree);
        buildTagInfo(annotationDocTree);
        buildDefaultValueInfo(annotationDocTree);
    }

    /**
     * Build the signature.
     *
     * @param annotationDocTree the content tree to which the documentation will be added
     */
    protected void buildSignature(Content annotationDocTree) {
        annotationDocTree.add(writer.getSignature(currentMember));
    }

    /**
     * Build the deprecation information.
     *
     * @param annotationDocTree the content tree to which the documentation will be added
     */
    protected void buildDeprecationInfo(Content annotationDocTree) {
        writer.addDeprecated(currentMember, annotationDocTree);
    }

    /**
     * Build the preview information.
     *
     * @param annotationDocTree the content tree to which the documentation will be added
     */
    protected void buildPreviewInfo(Content annotationDocTree) {
        writer.addPreview(currentMember, annotationDocTree);
    }

    /**
     * Build the comments for the member.  Do nothing if
     * {@link BaseOptions#noComment()} is set to true.
     *
     * @param annotationDocTree the content tree to which the documentation will be added
     */
    protected void buildMemberComments(Content annotationDocTree) {
        if (!options.noComment()) {
            writer.addComments(currentMember, annotationDocTree);
        }
    }

    /**
     * Build the tag information.
     *
     * @param annotationDocTree the content tree to which the documentation will be added
     */
    protected void buildTagInfo(Content annotationDocTree) {
        writer.addTags(currentMember, annotationDocTree);
    }

    /**
     * Build the default value for this optional member.
     *
     * @param annotationDocTree the content tree to which the documentation will be added
     */
    protected void buildDefaultValueInfo(Content annotationDocTree) {
        writer.addDefaultValueInfo(currentMember, annotationDocTree);
    }

    /**
     * Return the annotation type required member writer for this builder.
     *
     * @return the annotation type required member constant writer for this
     * builder.
     */
    public AnnotationTypeMemberWriter getWriter() {
        return writer;
    }
}
