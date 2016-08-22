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

import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import jdk.javadoc.internal.doclets.toolkit.AnnotationTypeWriter;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.DocletException;


/**
 * Builds the summary for a given annotation type.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 */
public class AnnotationTypeBuilder extends AbstractBuilder {

    /**
     * The root element of the annotation type XML is {@value}.
     */
    public static final String ROOT = "AnnotationTypeDoc";

    /**
     * The annotation type being documented.
     */
    private final TypeElement annotationType;

    /**
     * The doclet specific writer.
     */
    private final AnnotationTypeWriter writer;

    /**
     * The content tree for the annotation documentation.
     */
    private Content contentTree;

    /**
     * Construct a new ClassBuilder.
     *
     * @param context           the build context.
     * @param annotationTypeElement the class being documented.
     * @param writer            the doclet specific writer.
     */
    private AnnotationTypeBuilder(Context context,
            TypeElement annotationTypeElement,
            AnnotationTypeWriter writer) {
        super(context);
        this.annotationType = annotationTypeElement;
        this.writer = writer;
    }

    /**
     * Construct a new AnnotationTypeBuilder.
     *
     * @param context           the build context.
     * @param annotationTypeDoc the class being documented.
     * @param writer            the doclet specific writer.
     * @return                  an AnnotationTypeBuilder
     */
    public static AnnotationTypeBuilder getInstance(Context context,
            TypeElement annotationTypeDoc,
            AnnotationTypeWriter writer) {
        return new AnnotationTypeBuilder(context, annotationTypeDoc, writer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void build() throws DocletException {
        build(layoutParser.parseXML(ROOT), contentTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return ROOT;
    }

    /**
     * Build the annotation type documentation.
     *
     * @param node the XML element that specifies which components to document
     * @param contentTree the content tree to which the documentation will be added
     * @throws DocletException if there is a problem building the documentation
     */
    public void buildAnnotationTypeDoc(XMLNode node, Content contentTree) throws DocletException {
        contentTree = writer.getHeader(configuration.getText("doclet.AnnotationType") +
               " " + utils.getSimpleName(annotationType));
        Content annotationContentTree = writer.getAnnotationContentHeader();
        buildChildren(node, annotationContentTree);
        writer.addAnnotationContentTree(contentTree, annotationContentTree);
        writer.addFooter(contentTree);
        writer.printDocument(contentTree);
        copyDocFiles();
    }

    /**
     * Copy the doc files for the current TypeElement if necessary.
     *
     * @throws DocletException if there is a problem building the documentation
     */
    private void copyDocFiles() throws DocletException {
        PackageElement containingPackage = utils.containingPackage(annotationType);
        if((configuration.packages == null ||
            !configuration.packages.contains(containingPackage) &&
            !containingPackagesSeen.contains(containingPackage))){
            //Only copy doc files dir if the containing package is not
            //documented AND if we have not documented a class from the same
            //package already. Otherwise, we are making duplicate copies.
            utils.copyDocFiles(containingPackage);
            containingPackagesSeen.add(containingPackage);
        }
    }

    /**
     * Build the annotation information tree documentation.
     *
     * @param node the XML element that specifies which components to document
     * @param annotationContentTree the content tree to which the documentation will be added
     * @throws DocletException if there is a problem building the documentation
     */
    public void buildAnnotationTypeInfo(XMLNode node, Content annotationContentTree)
            throws DocletException {
        Content annotationInfoTree = writer.getAnnotationInfoTreeHeader();
        buildChildren(node, annotationInfoTree);
        annotationContentTree.addContent(writer.getAnnotationInfo(annotationInfoTree));
    }

    /**
     * If this annotation is deprecated, build the appropriate information.
     *
     * @param node the XML element that specifies which components to document
     * @param annotationInfoTree the content tree to which the documentation will be added
     */
    public void buildDeprecationInfo (XMLNode node, Content annotationInfoTree) {
        writer.addAnnotationTypeDeprecationInfo(annotationInfoTree);
    }

    /**
     * Build the signature of the current annotation type.
     *
     * @param node the XML element that specifies which components to document
     * @param annotationInfoTree the content tree to which the documentation will be added
     */
    public void buildAnnotationTypeSignature(XMLNode node, Content annotationInfoTree) {
        writer.addAnnotationTypeSignature(utils.modifiersToString(annotationType, true),
                annotationInfoTree);
    }

    /**
     * Build the annotation type description.
     *
     * @param node the XML element that specifies which components to document
     * @param annotationInfoTree the content tree to which the documentation will be added
     */
    public void buildAnnotationTypeDescription(XMLNode node, Content annotationInfoTree) {
        writer.addAnnotationTypeDescription(annotationInfoTree);
    }

    /**
     * Build the tag information for the current annotation type.
     *
     * @param node the XML element that specifies which components to document
     * @param annotationInfoTree the content tree to which the documentation will be added
     */
    public void buildAnnotationTypeTagInfo(XMLNode node, Content annotationInfoTree) {
        writer.addAnnotationTypeTagInfo(annotationInfoTree);
    }

    /**
     * Build the member summary contents of the page.
     *
     * @param node the XML element that specifies which components to document
     * @param annotationContentTree the content tree to which the documentation will be added
     * @throws DocletException if there is a problem building the documentation
     */
    public void buildMemberSummary(XMLNode node, Content annotationContentTree) throws DocletException {
        Content memberSummaryTree = writer.getMemberTreeHeader();
        configuration.getBuilderFactory().
                getMemberSummaryBuilder(writer).buildChildren(node, memberSummaryTree);
        annotationContentTree.addContent(writer.getMemberSummaryTree(memberSummaryTree));
    }

    /**
     * Build the member details contents of the page.
     *
     * @param node the XML element that specifies which components to document
     * @param annotationContentTree the content tree to which the documentation will be added
     * @throws DocletException if there is a problem building the documentation
     */
    public void buildAnnotationTypeMemberDetails(XMLNode node, Content annotationContentTree)
            throws DocletException {
        Content memberDetailsTree = writer.getMemberTreeHeader();
        buildChildren(node, memberDetailsTree);
        if (memberDetailsTree.isValid()) {
            annotationContentTree.addContent(writer.getMemberDetailsTree(memberDetailsTree));
        }
    }

    /**
     * Build the annotation type field documentation.
     *
     * @param node the XML element that specifies which components to document
     * @param memberDetailsTree the content tree to which the documentation will be added
     * @throws DocletException if there is a problem building the documentation
     */
    public void buildAnnotationTypeFieldDetails(XMLNode node, Content memberDetailsTree)
            throws DocletException {
        configuration.getBuilderFactory().
                getAnnotationTypeFieldsBuilder(writer).buildChildren(node, memberDetailsTree);
    }

    /**
     * Build the annotation type optional member documentation.
     *
     * @param node the XML element that specifies which components to document
     * @param memberDetailsTree the content tree to which the documentation will be added
     * @throws DocletException if there is a problem building the documentation
     */
    public void buildAnnotationTypeOptionalMemberDetails(XMLNode node, Content memberDetailsTree)
            throws DocletException {
        configuration.getBuilderFactory().
                getAnnotationTypeOptionalMemberBuilder(writer).buildChildren(node, memberDetailsTree);
    }

    /**
     * Build the annotation type required member documentation.
     *
     * @param node the XML element that specifies which components to document
     * @param memberDetailsTree the content tree to which the documentation will be added
     * @throws DocletException if there is a problem building the documentation
     */
    public void buildAnnotationTypeRequiredMemberDetails(XMLNode node, Content memberDetailsTree)
            throws DocletException {
        configuration.getBuilderFactory().
                getAnnotationTypeRequiredMemberBuilder(writer).buildChildren(node, memberDetailsTree);
    }
}
