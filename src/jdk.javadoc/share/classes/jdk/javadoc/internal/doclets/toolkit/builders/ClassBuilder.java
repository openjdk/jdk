/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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

import jdk.javadoc.internal.doclets.toolkit.ClassWriter;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.DocFilesHandler;
import jdk.javadoc.internal.doclets.toolkit.DocletException;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

/**
 * Builds the summary for a given class.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 */
public class ClassBuilder extends AbstractBuilder {

    /**
     * The class being documented.
     */
    private final TypeElement typeElement;

    /**
     * The doclet specific writer.
     */
    private final ClassWriter writer;

    /**
     * Keep track of whether or not this typeElement is an interface.
     */
    private final boolean isInterface;

    /**
     * Keep track of whether or not this typeElement is an enum.
     */
    private final boolean isEnum;

    /**
     * The content tree for the class documentation.
     */
    private Content contentTree;

    private final Utils utils;

    /**
     * Construct a new ClassBuilder.
     *
     * @param context  the build context
     * @param typeElement the class being documented.
     * @param writer the doclet specific writer.
     */
    private ClassBuilder(Context context, TypeElement typeElement, ClassWriter writer) {
        super(context);
        this.typeElement = typeElement;
        this.writer = writer;
        this.utils = configuration.utils;
        if (utils.isInterface(typeElement)) {
            isInterface = true;
            isEnum = false;
        } else if (utils.isEnum(typeElement)) {
            isInterface = false;
            isEnum = true;
            utils.setEnumDocumentation(typeElement);
        } else {
            isInterface = false;
            isEnum = false;
        }
    }

    /**
     * Constructs a new ClassBuilder.
     *
     * @param context  the build context
     * @param typeElement the class being documented.
     * @param writer the doclet specific writer.
     * @return the new ClassBuilder
     */
    public static ClassBuilder getInstance(Context context, TypeElement typeElement, ClassWriter writer) {
        return new ClassBuilder(context, typeElement, writer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void build() throws DocletException {
        buildClassDoc(contentTree);
    }

     /**
      * Handles the {@literal <TypeElement>} tag.
      *
      * @param contentTree the content tree to which the documentation will be added
      * @throws DocletException if there is a problem while building the documentation
      */
     protected void buildClassDoc(Content contentTree) throws DocletException {
        String key;
        if (isInterface) {
            key = "doclet.Interface";
        } else if (isEnum) {
            key = "doclet.Enum";
        } else {
            key = "doclet.Class";
        }
        contentTree = writer.getHeader(resources.getText(key) + " "
                + utils.getSimpleName(typeElement));
        Content classContentTree = writer.getClassContentHeader();

        buildClassTree(classContentTree);
        buildClassInfo(classContentTree);
        buildMemberSummary(classContentTree);
        buildMemberDetails(classContentTree);

        writer.addClassContentTree(contentTree, classContentTree);
        writer.addFooter(contentTree);
        writer.printDocument(contentTree);
        copyDocFiles();
    }

     /**
      * Build the class tree documentation.
      *
      * @param classContentTree the content tree to which the documentation will be added
      */
    protected void buildClassTree(Content classContentTree) {
        writer.addClassTree(classContentTree);
    }

    /**
     * Build the class information tree documentation.
     *
     * @param classContentTree the content tree to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildClassInfo(Content classContentTree) throws DocletException {
        Content classInfoTree = writer.getClassInfoTreeHeader();

        buildTypeParamInfo(classInfoTree);
        buildSuperInterfacesInfo(classInfoTree);
        buildImplementedInterfacesInfo(classInfoTree);
        buildSubClassInfo(classInfoTree);
        buildSubInterfacesInfo(classInfoTree);
        buildInterfaceUsageInfo(classInfoTree);
        buildNestedClassInfo(classInfoTree);
        buildFunctionalInterfaceInfo(classInfoTree);
        buildClassSignature(classInfoTree);
        buildDeprecationInfo(classInfoTree);
        buildClassDescription(classInfoTree);
        buildClassTagInfo(classInfoTree);

        classContentTree.addContent(writer.getClassInfo(classInfoTree));
    }

    /**
     * Build the type parameters of this class.
     *
     * @param classInfoTree the content tree to which the documentation will be added
     */
    protected void buildTypeParamInfo(Content classInfoTree) {
        writer.addTypeParamInfo(classInfoTree);
    }

    /**
     * If this is an interface, list all super interfaces.
     *
     * @param classInfoTree the content tree to which the documentation will be added
     */
    protected void buildSuperInterfacesInfo(Content classInfoTree) {
        writer.addSuperInterfacesInfo(classInfoTree);
    }

    /**
     * If this is a class, list all interfaces implemented by this class.
     *
     * @param classInfoTree the content tree to which the documentation will be added
     */
    protected void buildImplementedInterfacesInfo(Content classInfoTree) {
        writer.addImplementedInterfacesInfo(classInfoTree);
    }

    /**
     * List all the classes extend this one.
     *
     * @param classInfoTree the content tree to which the documentation will be added
     */
    protected void buildSubClassInfo(Content classInfoTree) {
        writer.addSubClassInfo(classInfoTree);
    }

    /**
     * List all the interfaces that extend this one.
     *
     * @param classInfoTree the content tree to which the documentation will be added
     */
    protected void buildSubInterfacesInfo(Content classInfoTree) {
        writer.addSubInterfacesInfo(classInfoTree);
    }

    /**
     * If this is an interface, list all classes that implement this interface.
     *
     * @param classInfoTree the content tree to which the documentation will be added
     */
    protected void buildInterfaceUsageInfo(Content classInfoTree) {
        writer.addInterfaceUsageInfo(classInfoTree);
    }

    /**
     * If this is an functional interface, display appropriate message.
     *
     * @param classInfoTree the content tree to which the documentation will be added
     */
    protected void buildFunctionalInterfaceInfo(Content classInfoTree) {
        writer.addFunctionalInterfaceInfo(classInfoTree);
    }

    /**
     * If this class is deprecated, build the appropriate information.
     *
     * @param classInfoTree the content tree to which the documentation will be added
     */
    protected void buildDeprecationInfo(Content classInfoTree) {
        writer.addClassDeprecationInfo(classInfoTree);
    }

    /**
     * If this is an inner class or interface, list the enclosing class or interface.
     *
     * @param classInfoTree the content tree to which the documentation will be added
     */
    protected void buildNestedClassInfo(Content classInfoTree) {
        writer.addNestedClassInfo(classInfoTree);
    }

    /**
     * Copy the doc files.
     *
     * @throws DocFileIOException if there is a problem while copying the files
     */
     private void copyDocFiles() throws DocletException {
        PackageElement containingPackage = utils.containingPackage(typeElement);
        if ((configuration.packages == null ||
            !configuration.packages.contains(containingPackage)) &&
            !containingPackagesSeen.contains(containingPackage)) {
            //Only copy doc files dir if the containing package is not
            //documented AND if we have not documented a class from the same
            //package already. Otherwise, we are making duplicate copies.
            DocFilesHandler docFilesHandler = configuration
                    .getWriterFactory()
                    .getDocFilesHandler(containingPackage);
            docFilesHandler.copyDocFiles();
            containingPackagesSeen.add(containingPackage);
        }
     }

    /**
     * Build the signature of the current class.
     *
     * @param classInfoTree the content tree to which the documentation will be added
     */
    protected void buildClassSignature(Content classInfoTree) {
        writer.addClassSignature(utils.modifiersToString(typeElement, true), classInfoTree);
    }

    /**
     * Build the class description.
     *
     * @param classInfoTree the content tree to which the documentation will be added
     */
    protected void buildClassDescription(Content classInfoTree) {
       writer.addClassDescription(classInfoTree);
    }

    /**
     * Build the tag information for the current class.
     *
     * @param classInfoTree the content tree to which the documentation will be added
     */
    protected void buildClassTagInfo(Content classInfoTree) {
       writer.addClassTagInfo(classInfoTree);
    }

    /**
     * Build the member summary contents of the page.
     *
     * @param classContentTree the content tree to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildMemberSummary(Content classContentTree) throws DocletException {
        Content memberSummaryTree = writer.getMemberTreeHeader();
        builderFactory.getMemberSummaryBuilder(writer).build(memberSummaryTree);
        classContentTree.addContent(writer.getMemberSummaryTree(memberSummaryTree));
    }

    /**
     * Build the member details contents of the page.
     *
     * @param classContentTree the content tree to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildMemberDetails(Content classContentTree) throws DocletException {
        Content memberDetailsTree = writer.getMemberTreeHeader();

        buildEnumConstantsDetails(memberDetailsTree);
        buildPropertyDetails(memberDetailsTree);
        buildFieldDetails(memberDetailsTree);
        buildConstructorDetails(memberDetailsTree);
        buildMethodDetails(memberDetailsTree);

        classContentTree.addContent(writer.getMemberDetailsTree(memberDetailsTree));
    }

    /**
     * Build the enum constants documentation.
     *
     * @param memberDetailsTree the content tree to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildEnumConstantsDetails(Content memberDetailsTree) throws DocletException {
        builderFactory.getEnumConstantsBuilder(writer).build(memberDetailsTree);
    }

    /**
     * Build the field documentation.
     *
     * @param memberDetailsTree the content tree to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildFieldDetails(Content memberDetailsTree) throws DocletException {
        builderFactory.getFieldBuilder(writer).build(memberDetailsTree);
    }

    /**
     * Build the property documentation.
     *
     * @param memberDetailsTree the content tree to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    public void buildPropertyDetails( Content memberDetailsTree) throws DocletException {
        builderFactory.getPropertyBuilder(writer).build(memberDetailsTree);
    }

    /**
     * Build the constructor documentation.
     *
     * @param memberDetailsTree the content tree to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildConstructorDetails(Content memberDetailsTree) throws DocletException {
        builderFactory.getConstructorBuilder(writer).build(memberDetailsTree);
    }

    /**
     * Build the method documentation.
     *
     * @param memberDetailsTree the content tree to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildMethodDetails(Content memberDetailsTree) throws DocletException {
        builderFactory.getMethodBuilder(writer).build(memberDetailsTree);
    }
}
