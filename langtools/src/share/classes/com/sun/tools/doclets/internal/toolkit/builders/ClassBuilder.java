/*
 * Copyright (c) 2003, 2008, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.javadoc.*;
import java.io.*;
import java.util.*;
import java.lang.reflect.*;

/**
 * Builds the summary for a given class.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @author Jamie Ho
 * @since 1.5
 */
public class ClassBuilder extends AbstractBuilder {

    /**
     * The root element of the class XML is {@value}.
     */
    public static final String ROOT = "ClassDoc";

    /**
     * The class being documented.
     */
    private ClassDoc classDoc;

    /**
     * The doclet specific writer.
     */
    private ClassWriter writer;

    /**
     * Keep track of whether or not this classdoc is an interface.
     */
    private boolean isInterface = false;

    /**
     * Keep track of whether or not this classdoc is an enum.
     */
    private boolean isEnum = false;

    /**
     * Construct a new ClassBuilder.
     *
     * @param configuration the current configuration of the
     *                      doclet.
     */
    private ClassBuilder(Configuration configuration) {
        super(configuration);
    }

    /**
     * Construct a new ClassBuilder.
     *
     * @param configuration the current configuration of the doclet.
     * @param classDoc the class being documented.
     * @param writer the doclet specific writer.
     */
    public static ClassBuilder getInstance(Configuration configuration,
        ClassDoc classDoc, ClassWriter writer)
    throws Exception {
        ClassBuilder builder = new ClassBuilder(configuration);
        builder.configuration = configuration;
        builder.classDoc = classDoc;
        builder.writer = writer;
        if (classDoc.isInterface()) {
            builder.isInterface = true;
        } else if (classDoc.isEnum()) {
            builder.isEnum = true;
            Util.setEnumDocumentation(configuration, classDoc);
        }
        if(containingPackagesSeen == null) {
            containingPackagesSeen = new HashSet<String>();
        }
        return builder;
    }

    /**
     * {@inheritDoc}
     */
    public void invokeMethod(String methodName, Class<?>[] paramClasses,
            Object[] params)
    throws Exception {
        if (DEBUG) {
            configuration.root.printError("DEBUG: " + this.getClass().getName()
                + "." + methodName);
        }
        Method method = this.getClass().getMethod(methodName, paramClasses);
        method.invoke(this, params);
    }

    /**
     * {@inheritDoc}
     */
    public void build() throws IOException {
        build(LayoutParser.getInstance(configuration).parseXML(ROOT));
    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return ROOT;
    }

     /**
      * Handles the &lt;ClassDoc> tag.
      *
      * @param elements the XML elements that specify how to document a class.
      */
     public void buildClassDoc(List<?> elements) throws Exception {
        build(elements);
        writer.close();
        copyDocFiles();
     }


     /**
      * Copy the doc files for the current ClassDoc if necessary.
      */
     private void copyDocFiles() {
        PackageDoc containingPackage = classDoc.containingPackage();
        if((configuration.packages == null ||
                Arrays.binarySearch(configuration.packages,
                                    containingPackage) < 0) &&
           ! containingPackagesSeen.contains(containingPackage.name())){
            //Only copy doc files dir if the containing package is not
            //documented AND if we have not documented a class from the same
            //package already. Otherwise, we are making duplicate copies.
            Util.copyDocFiles(configuration,
                Util.getPackageSourcePath(configuration,
                    classDoc.containingPackage()) +
                DirectoryManager.getDirectoryPath(classDoc.containingPackage())
                    + File.separator, DocletConstants.DOC_FILES_DIR_NAME, true);
            containingPackagesSeen.add(containingPackage.name());
        }
     }

    /**
     * Build the header of the page.
     */
    public void buildClassHeader() {
        String key;
        if (isInterface) {
            key =  "doclet.Interface";
        } else if (isEnum) {
            key = "doclet.Enum";
        } else {
            key =  "doclet.Class";
        }

        writer.writeHeader(configuration.getText(key) + " " + classDoc.name());
    }

    /**
     * Build the class tree documentation.
     */
    public void buildClassTree() {
        writer.writeClassTree();
    }

    /**
     * If this is a class, list all interfaces
     * implemented by this class.
     */
    public void buildImplementedInterfacesInfo() {
        writer.writeImplementedInterfacesInfo();
    }

    /**
     * If this is an interface, list all super interfaces.
     */
    public void buildSuperInterfacesInfo() {
        writer.writeSuperInterfacesInfo();
    }

    /**
     * List the parameters of this class.
     */
    public void buildTypeParamInfo() {
        writer.writeTypeParamInfo();
    }

    /**
     * List all the classes extend this one.
     */
    public void buildSubClassInfo() {
        writer.writeSubClassInfo();
    }

    /**
     * List all the interfaces that extend this one.
     */
    public void buildSubInterfacesInfo() {
        writer.writeSubInterfacesInfo();
    }

    /**
     * If this is an interface, list all classes that implement this interface.
     */
    public void buildInterfaceUsageInfo () {
        writer.writeInterfaceUsageInfo();
    }

    /**
     * If this is an inner class or interface, list the enclosing class or
     * interface.
     */
    public void buildNestedClassInfo () {
        writer.writeNestedClassInfo();
    }

    /**
     * If this class is deprecated, print the appropriate information.
     */
    public void buildDeprecationInfo () {
        writer.writeClassDeprecationInfo();
    }

    /**
     * Build the signature of the current class.
     */
    public void buildClassSignature() {
        StringBuffer modifiers = new StringBuffer(classDoc.modifiers() + " ");
        if (isEnum) {
            modifiers.append("enum ");
            int index;
            if ((index = modifiers.indexOf("abstract")) >= 0) {
                modifiers.delete(index, index + (new String("abstract")).length());
                modifiers = new StringBuffer(
                    Util.replaceText(modifiers.toString(), "  ", " "));
            }
            if ((index = modifiers.indexOf("final")) >= 0) {
                modifiers.delete(index, index + (new String("final")).length());
                modifiers = new StringBuffer(
                    Util.replaceText(modifiers.toString(), "  ", " "));
            }
        //} else if (classDoc.isAnnotationType()) {
            //modifiers.append("@interface ");
        } else if (! isInterface) {
            modifiers.append("class ");
        }
        writer.writeClassSignature(modifiers.toString());
    }

    /**
     * Build the class description.
     */
    public void buildClassDescription() {
       writer.writeClassDescription();
    }

    /**
     * Build the tag information for the current class.
     */
    public void buildClassTagInfo() {
       writer.writeClassTagInfo();
    }

    /**
     * Build the contents of the page.
     *
     * @param elements the XML elements that specify how a member summary is
     *                 documented.
     */
    public void buildMemberSummary(List<?> elements) throws Exception {
        configuration.getBuilderFactory().
            getMemberSummaryBuilder(writer).build(elements);
        writer.completeMemberSummaryBuild();
    }

    /**
     * Build the enum constants documentation.
     *
     * @param elements the XML elements that specify how a enum constants are
     *                 documented.
     */
    public void buildEnumConstantsDetails(List<?> elements) throws Exception {
        configuration.getBuilderFactory().
            getEnumConstantsBuilder(writer).build(elements);
    }

    /**
     * Build the field documentation.
     *
     * @param elements the XML elements that specify how a field is documented.
     */
    public void buildFieldDetails(List<?> elements) throws Exception {
        configuration.getBuilderFactory().
            getFieldBuilder(writer).build(elements);
    }

    /**
     * Build the constructor documentation.
     *
     * @param elements the XML elements that specify how to document a
     * constructor.
     */
    public void buildConstructorDetails(List<?> elements) throws Exception {
        configuration.getBuilderFactory().
            getConstructorBuilder(writer).build(elements);
    }

    /**
     * Build the method documentation.
     *
     * @param elements the XML elements that specify how a method is documented.
     */
    public void buildMethodDetails(List<?> elements) throws Exception {
        configuration.getBuilderFactory().
                getMethodBuilder(writer).build(elements);
    }

    /**
     * Build the footer of the page.
     */
    public void buildClassFooter() {
        writer.writeFooter();
    }
}
