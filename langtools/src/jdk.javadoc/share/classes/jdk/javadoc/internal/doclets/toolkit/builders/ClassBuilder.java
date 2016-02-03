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

import java.io.*;

import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import jdk.javadoc.internal.doclets.toolkit.ClassWriter;
import jdk.javadoc.internal.doclets.toolkit.Content;
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
     * The root element of the class XML is {@value}.
     */
    public static final String ROOT = "ClassDoc";

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
     * Construct a new ClassBuilder.
     *
     * @param context  the build context
     * @param typeElement the class being documented.
     * @param writer the doclet specific writer.
     */
    public static ClassBuilder getInstance(Context context,
            TypeElement typeElement, ClassWriter writer) {
        return new ClassBuilder(context, typeElement, writer);
    }

    /**
     * {@inheritDoc}
     */
    public void build() throws IOException {
        build(layoutParser.parseXML(ROOT), contentTree);
    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return ROOT;
    }

     /**
      * Handles the {@literal <TypeElement>} tag.
      *
      * @param node the XML element that specifies which components to document
      * @param contentTree the content tree to which the documentation will be added
      */
     public void buildClassDoc(XMLNode node, Content contentTree) throws Exception {
         String key;
         if (isInterface) {
             key =  "doclet.Interface";
         } else if (isEnum) {
             key = "doclet.Enum";
         } else {
             key =  "doclet.Class";
         }
         contentTree = writer.getHeader(configuration.getText(key) + " " +
                 utils.getSimpleName(typeElement));
         Content classContentTree = writer.getClassContentHeader();
         buildChildren(node, classContentTree);
         writer.addClassContentTree(contentTree, classContentTree);
         writer.addFooter(contentTree);
         writer.printDocument(contentTree);
         writer.close();
         copyDocFiles();
     }

     /**
      * Build the class tree documentation.
      *
      * @param node the XML element that specifies which components to document
      * @param classContentTree the content tree to which the documentation will be added
      */
    public void buildClassTree(XMLNode node, Content classContentTree) {
        writer.addClassTree(classContentTree);
    }

    /**
     * Build the class information tree documentation.
     *
     * @param node the XML element that specifies which components to document
     * @param classContentTree the content tree to which the documentation will be added
     */
    public void buildClassInfo(XMLNode node, Content classContentTree) {
        Content classInfoTree = writer.getClassInfoTreeHeader();
        buildChildren(node, classInfoTree);
        classContentTree.addContent(writer.getClassInfo(classInfoTree));
    }

    /**
     * Build the typeparameters of this class.
     *
     * @param node the XML element that specifies which components to document
     * @param classInfoTree the content tree to which the documentation will be added
     */
    public void buildTypeParamInfo(XMLNode node, Content classInfoTree) {
        writer.addTypeParamInfo(classInfoTree);
    }

    /**
     * If this is an interface, list all super interfaces.
     *
     * @param node the XML element that specifies which components to document
     * @param classInfoTree the content tree to which the documentation will be added
     */
    public void buildSuperInterfacesInfo(XMLNode node, Content classInfoTree) {
        writer.addSuperInterfacesInfo(classInfoTree);
    }

    /**
     * If this is a class, list all interfaces implemented by this class.
     *
     * @param node the XML element that specifies which components to document
     * @param classInfoTree the content tree to which the documentation will be added
     */
    public void buildImplementedInterfacesInfo(XMLNode node, Content classInfoTree) {
        writer.addImplementedInterfacesInfo(classInfoTree);
    }

    /**
     * List all the classes extend this one.
     *
     * @param node the XML element that specifies which components to document
     * @param classInfoTree the content tree to which the documentation will be added
     */
    public void buildSubClassInfo(XMLNode node, Content classInfoTree) {
        writer.addSubClassInfo(classInfoTree);
    }

    /**
     * List all the interfaces that extend this one.
     *
     * @param node the XML element that specifies which components to document
     * @param classInfoTree the content tree to which the documentation will be added
     */
    public void buildSubInterfacesInfo(XMLNode node, Content classInfoTree) {
        writer.addSubInterfacesInfo(classInfoTree);
    }

    /**
     * If this is an interface, list all classes that implement this interface.
     *
     * @param node the XML element that specifies which components to document
     * @param classInfoTree the content tree to which the documentation will be added
     */
    public void buildInterfaceUsageInfo(XMLNode node, Content classInfoTree) {
        writer.addInterfaceUsageInfo(classInfoTree);
    }

    /**
     * If this is an functional interface, display appropriate message.
     *
     * @param node the XML element that specifies which components to document
     * @param classInfoTree the content tree to which the documentation will be added
     */
    public void buildFunctionalInterfaceInfo(XMLNode node, Content classInfoTree) {
        writer.addFunctionalInterfaceInfo(classInfoTree);
    }

    /**
     * If this class is deprecated, build the appropriate information.
     *
     * @param node the XML element that specifies which components to document
     * @param classInfoTree the content tree to which the documentation will be added
     */
    public void buildDeprecationInfo (XMLNode node, Content classInfoTree) {
        writer.addClassDeprecationInfo(classInfoTree);
    }

    /**
     * If this is an inner class or interface, list the enclosing class or interface.
     *
     * @param node the XML element that specifies which components to document
     * @param classInfoTree the content tree to which the documentation will be added
     */
    public void buildNestedClassInfo (XMLNode node, Content classInfoTree) {
        writer.addNestedClassInfo(classInfoTree);
    }

    /**
     * Copy the doc files.
     */
     private void copyDocFiles() {
        PackageElement containingPackage = utils.containingPackage(typeElement);
        if((configuration.packages == null ||
            !configuration.packages.contains(containingPackage)) &&
            !containingPackagesSeen.contains(containingPackage)) {
            //Only copy doc files dir if the containing package is not
            //documented AND if we have not documented a class from the same
            //package already. Otherwise, we are making duplicate copies.
            utils.copyDocFiles(containingPackage);
            containingPackagesSeen.add(containingPackage);
        }
     }

    /**
     * Build the signature of the current class.
     *
     * @param node the XML element that specifies which components to document
     * @param classInfoTree the content tree to which the documentation will be added
     */
    public void buildClassSignature(XMLNode node, Content classInfoTree) {
        writer.addClassSignature(utils.modifiersToString(typeElement, true), classInfoTree);
    }

    /**
     * Build the class description.
     *
     * @param node the XML element that specifies which components to document
     * @param classInfoTree the content tree to which the documentation will be added
     */
    public void buildClassDescription(XMLNode node, Content classInfoTree) {
       writer.addClassDescription(classInfoTree);
    }

    /**
     * Build the tag information for the current class.
     *
     * @param node the XML element that specifies which components to document
     * @param classInfoTree the content tree to which the documentation will be added
     */
    public void buildClassTagInfo(XMLNode node, Content classInfoTree) {
       writer.addClassTagInfo(classInfoTree);
    }

    /**
     * Build the member summary contents of the page.
     *
     * @param node the XML element that specifies which components to document
     * @param classContentTree the content tree to which the documentation will be added
     */
    public void buildMemberSummary(XMLNode node, Content classContentTree) throws Exception {
        Content memberSummaryTree = writer.getMemberTreeHeader();
        configuration.getBuilderFactory().
                getMemberSummaryBuilder(writer).buildChildren(node, memberSummaryTree);
        classContentTree.addContent(writer.getMemberSummaryTree(memberSummaryTree));
    }

    /**
     * Build the member details contents of the page.
     *
     * @param node the XML element that specifies which components to document
     * @param classContentTree the content tree to which the documentation will be added
     */
    public void buildMemberDetails(XMLNode node, Content classContentTree) {
        Content memberDetailsTree = writer.getMemberTreeHeader();
        buildChildren(node, memberDetailsTree);
        classContentTree.addContent(writer.getMemberDetailsTree(memberDetailsTree));
    }

    /**
     * Build the enum constants documentation.
     *
     * @param node the XML element that specifies which components to document
     * @param memberDetailsTree the content tree to which the documentation will be added
     */
    public void buildEnumConstantsDetails(XMLNode node,
            Content memberDetailsTree) throws Exception {
        configuration.getBuilderFactory().
                getEnumConstantsBuilder(writer).buildChildren(node, memberDetailsTree);
    }

    /**
     * Build the field documentation.
     *
     * @param node the XML element that specifies which components to document
     * @param memberDetailsTree the content tree to which the documentation will be added
     */
    public void buildFieldDetails(XMLNode node,
            Content memberDetailsTree) throws Exception {
        configuration.getBuilderFactory().
                getFieldBuilder(writer).buildChildren(node, memberDetailsTree);
    }

    /**
     * Build the property documentation.
     *
     * @param elements the XML elements that specify how a field is documented.
     */
    public void buildPropertyDetails(XMLNode node,
            Content memberDetailsTree) throws Exception {
        configuration.getBuilderFactory().
                getPropertyBuilder(writer).buildChildren(node, memberDetailsTree);
    }

    /**
     * Build the constructor documentation.
     *
     * @param node the XML element that specifies which components to document
     * @param memberDetailsTree the content tree to which the documentation will be added
     */
    public void buildConstructorDetails(XMLNode node,
            Content memberDetailsTree) throws Exception {
        configuration.getBuilderFactory().
                getConstructorBuilder(writer).buildChildren(node, memberDetailsTree);
    }

    /**
     * Build the method documentation.
     *
     * @param node the XML element that specifies which components to document
     * @param memberDetailsTree the content tree to which the documentation will be added
     */
    public void buildMethodDetails(XMLNode node,
            Content memberDetailsTree) throws Exception {
        configuration.getBuilderFactory().
                getMethodBuilder(writer).buildChildren(node, memberDetailsTree);
    }
}
