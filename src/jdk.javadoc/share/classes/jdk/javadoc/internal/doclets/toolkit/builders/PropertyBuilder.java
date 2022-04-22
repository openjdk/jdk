/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import jdk.javadoc.internal.doclets.toolkit.BaseOptions;
import jdk.javadoc.internal.doclets.toolkit.CommentUtils;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.DocletException;
import jdk.javadoc.internal.doclets.toolkit.PropertyWriter;

import static jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable.Kind.PROPERTIES;

/**
 * Builds documentation for a property.
 */
public class PropertyBuilder extends AbstractMemberBuilder {

    /**
     * The writer to output the property documentation.
     */
    private final PropertyWriter writer;

    /**
     * The list of properties being documented.
     */
    private final List<? extends Element> properties;

    /**
     * The index of the current property that is being documented at this point
     * in time.
     */
    private ExecutableElement currentProperty;

    /**
     * Construct a new PropertyBuilder.
     *
     * @param context  the build context.
     * @param typeElement the class whose members are being documented.
     * @param writer the doclet specific writer.
     */
    private PropertyBuilder(Context context,
            TypeElement typeElement,
            PropertyWriter writer) {
        super(context, typeElement);
        this.writer = Objects.requireNonNull(writer);
        properties = getVisibleMembers(PROPERTIES);
    }

    /**
     * Construct a new PropertyBuilder.
     *
     * @param context  the build context.
     * @param typeElement the class whose members are being documented.
     * @param writer the doclet specific writer.
     * @return the new PropertyBuilder
     */
    public static PropertyBuilder getInstance(Context context,
            TypeElement typeElement,
            PropertyWriter writer) {
        return new PropertyBuilder(context, typeElement, writer);
    }

    /**
     * Returns whether or not there are members to document.
     *
     * @return whether or not there are members to document
     */
    @Override
    public boolean hasMembersToDocument() {
        return !properties.isEmpty();
    }

    @Override
    public void build(Content target) throws DocletException {
        buildPropertyDoc(target);
    }

    /**
     * Build the property documentation.
     *
     * @param detailsList the content to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildPropertyDoc(Content detailsList) throws DocletException {
        if (hasMembersToDocument()) {
            Content propertyDetailsHeader = writer.getPropertyDetailsHeader(detailsList);
            Content memberList = writer.getMemberList();

            for (Element property : properties) {
                currentProperty = (ExecutableElement)property;
                Content propertyContent = writer.getPropertyHeaderContent(currentProperty);

                buildSignature(propertyContent);
                buildPropertyComments(propertyContent);
                buildTagInfo(propertyContent);

                memberList.add(writer.getMemberListItem(propertyContent));
            }
            Content propertyDetails = writer.getPropertyDetails(propertyDetailsHeader, memberList);
            detailsList.add(propertyDetails);
        }
    }

    /**
     * Build the signature.
     *
     * @param propertyContent the content to which the documentation will be added
     */
    protected void buildSignature(Content propertyContent) {
        propertyContent.add(writer.getSignature(currentProperty));
    }

    /**
     * Build the deprecation information.
     *
     * @param propertyContent the content to which the documentation will be added
     */
    protected void buildDeprecationInfo(Content propertyContent) {
        writer.addDeprecated(currentProperty, propertyContent);
    }

    /**
     * Build the preview information.
     *
     * @param propertyContent the content to which the documentation will be added
     */
    protected void buildPreviewInfo(Content propertyContent) {
        writer.addPreview(currentProperty, propertyContent);
    }

    /**
     * Build the comments for the property.  Do nothing if
     * {@link BaseOptions#noComment()} is set to true.
     *
     * @param propertyContent the content to which the documentation will be added
     */
    protected void buildPropertyComments(Content propertyContent) {
        if (!options.noComment()) {
            writer.addComments(currentProperty, propertyContent);
        }
    }

    /**
     * Build the tag information.
     *
     * @param propertyContent the content to which the documentation will be added
     */
    protected void buildTagInfo(Content propertyContent) {
        CommentUtils cmtUtils = configuration.cmtUtils;
        DocCommentTree dct = utils.getDocCommentTree(currentProperty);
        var fullBody = dct.getFullBody();
        ArrayList<DocTree> blockTags = dct.getBlockTags().stream()
                .filter(t -> t.getKind() != DocTree.Kind.RETURN)
                .collect(Collectors.toCollection(ArrayList::new));
        String sig = "#" + currentProperty.getSimpleName() + "()";
        blockTags.add(cmtUtils.makeSeeTree(sig, currentProperty));
        // The property method is used as a proxy for the property
        // (which does not have an explicit element of its own.)
        // Temporarily override the doc comment for the property method
        // by removing the `@return` tag, which should not be displayed for
        // the property.
        CommentUtils.DocCommentInfo prev = cmtUtils.setDocCommentTree(currentProperty, fullBody, blockTags);
        try {
            writer.addTags(currentProperty, propertyContent);
        } finally {
            cmtUtils.setDocCommentInfo(currentProperty, prev);
        }
    }

    /**
     * Return the property writer for this builder.
     *
     * @return the property writer for this builder.
     */
    public PropertyWriter getWriter() {
        return writer;
    }
}
