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
import java.util.*;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.SerialFieldTree;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.SerializedFormWriter;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocletAbortException;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

/**
 * Builds the serialized form.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 */
public class SerializedFormBuilder extends AbstractBuilder {

    /**
     * The root element of the serialized form XML is {@value}.
     */
    public static final String NAME = "SerializedForm";

    /**
     * The writer for this builder.
     */
    private SerializedFormWriter writer;

    /**
     * The writer for serializable fields.
     */
    private SerializedFormWriter.SerialFieldWriter fieldWriter;

    /**
     * The writer for serializable method documentation.
     */
    private SerializedFormWriter.SerialMethodWriter methodWriter;

    /**
     * The header for the serial version UID.  Save the string
     * here instead of the properties file because we do not want
     * this string to be localized.
     */
    private static final String SERIAL_VERSION_UID = "serialVersionUID";
    private static final String SERIAL_VERSION_UID_HEADER = SERIAL_VERSION_UID + ":";

    /**
     * The current package being documented.
     */
    private PackageElement currentPackage;

    /**
     * The current class being documented.
     */
    private TypeElement currentTypeElement;

    /**
     * The current member being documented.
     */
    protected Element currentMember;

    /**
     * The content that will be added to the serialized form documentation tree.
     */
    private Content contentTree;


    /**
     * Construct a new SerializedFormBuilder.
     * @param context  the build context.
     */
    private SerializedFormBuilder(Context context) {
        super(context);
    }

    /**
     * Construct a new SerializedFormBuilder.
     * @param context  the build context.
     */
    public static SerializedFormBuilder getInstance(Context context) {
        return new SerializedFormBuilder(context);
    }

    /**
     * Build the serialized form.
     */
    public void build() throws IOException {
        SortedSet<TypeElement> rootclasses = new TreeSet<>(utils.makeGeneralPurposeComparator());
        rootclasses.addAll(configuration.root.getIncludedClasses());
        if (!serialClassFoundToDocument(rootclasses)) {
            //Nothing to document.
            return;
        }
        try {
            writer = configuration.getWriterFactory().getSerializedFormWriter();
            if (writer == null) {
                //Doclet does not support this output.
                return;
            }
        } catch (Exception e) {
            throw new DocletAbortException(e);
        }
        build(layoutParser.parseXML(NAME), contentTree);
        writer.close();
    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return NAME;
    }

    /**
     * Build the serialized form.
     *
     * @param node the XML element that specifies which components to document
     * @param serializedTree content tree to which the documentation will be added
     */
    public void buildSerializedForm(XMLNode node, Content serializedTree) throws Exception {
        serializedTree = writer.getHeader(configuration.getText(
                "doclet.Serialized_Form"));
        buildChildren(node, serializedTree);
        writer.addFooter(serializedTree);
        writer.printDocument(serializedTree);
        writer.close();
    }

    /**
     * Build the serialized form summaries.
     *
     * @param node the XML element that specifies which components to document
     * @param serializedTree content tree to which the documentation will be added
     */
    public void buildSerializedFormSummaries(XMLNode node, Content serializedTree) {
        Content serializedSummariesTree = writer.getSerializedSummariesHeader();
        for (PackageElement pkg : configuration.packages) {
            currentPackage = pkg;
            buildChildren(node, serializedSummariesTree);
        }
        serializedTree.addContent(writer.getSerializedContent(
                serializedSummariesTree));
    }

    /**
     * Build the package serialized form for the current package being processed.
     *
     * @param node the XML element that specifies which components to document
     * @param serializedSummariesTree content tree to which the documentation will be added
     */
    public void buildPackageSerializedForm(XMLNode node, Content serializedSummariesTree) {
        Content packageSerializedTree = writer.getPackageSerializedHeader();
        SortedSet<TypeElement> classes = utils.getAllClassesUnfiltered(currentPackage);
        if (classes.isEmpty()) {
            return;
        }
        if (!serialInclude(utils, currentPackage)) {
            return;
        }
        if (!serialClassFoundToDocument(classes)) {
            return;
        }
        buildChildren(node, packageSerializedTree);
        writer.addPackageSerializedTree(serializedSummariesTree, packageSerializedTree);
    }

    /**
     * Build the package header.
     *
     * @param node the XML element that specifies which components to document
     * @param packageSerializedTree content tree to which the documentation will be added
     */
    public void buildPackageHeader(XMLNode node, Content packageSerializedTree) {
        packageSerializedTree.addContent(writer.getPackageHeader(
                utils.getPackageName(currentPackage)));
    }

    /**
     * Build the class serialized form.
     *
     * @param node the XML element that specifies which components to document
     * @param packageSerializedTree content tree to which the documentation will be added
     */
    public void buildClassSerializedForm(XMLNode node, Content packageSerializedTree) {
        Content classSerializedTree = writer.getClassSerializedHeader();
        SortedSet<TypeElement> typeElements = utils.getAllClassesUnfiltered(currentPackage);
        for (TypeElement typeElement : typeElements) {
            currentTypeElement = typeElement;
            fieldWriter = writer.getSerialFieldWriter(currentTypeElement);
            methodWriter = writer.getSerialMethodWriter(currentTypeElement);
            if (utils.isClass(currentTypeElement) && utils.isSerializable(currentTypeElement)) {
                if (!serialClassInclude(utils, currentTypeElement)) {
                    continue;
                }
                Content classTree = writer.getClassHeader(currentTypeElement);
                buildChildren(node, classTree);
                classSerializedTree.addContent(classTree);
            }
        }
        packageSerializedTree.addContent(classSerializedTree);
    }

    /**
     * Build the serial UID information for the given class.
     *
     * @param node the XML element that specifies which components to document
     * @param classTree content tree to which the serial UID information will be added
     */
    public void buildSerialUIDInfo(XMLNode node, Content classTree) {
        Content serialUidTree = writer.getSerialUIDInfoHeader();
        for (Element e : utils.getFieldsUnfiltered(currentTypeElement)) {
            VariableElement field = (VariableElement)e;
            if (field.getSimpleName().toString().compareTo(SERIAL_VERSION_UID) == 0 &&
                field.getConstantValue() != null) {
                writer.addSerialUIDInfo(SERIAL_VERSION_UID_HEADER,
                                        utils.constantValueExpresion(field), serialUidTree);
                break;
            }
        }
        classTree.addContent(serialUidTree);
    }

    /**
     * Build the summaries for the methods and fields.
     *
     * @param node the XML element that specifies which components to document
     * @param classTree content tree to which the documentation will be added
     */
    public void buildClassContent(XMLNode node, Content classTree) {
        Content classContentTree = writer.getClassContentHeader();
        buildChildren(node, classContentTree);
        classTree.addContent(classContentTree);
    }

    /**
     * Build the summaries for the methods that belong to the given
     * class.
     *
     * @param node the XML element that specifies which components to document
     * @param classContentTree content tree to which the documentation will be added
     */
    public void buildSerializableMethods(XMLNode node, Content classContentTree) {
        Content serializableMethodTree = methodWriter.getSerializableMethodsHeader();
        SortedSet<ExecutableElement> members = utils.serializationMethods(currentTypeElement);
        if (!members.isEmpty()) {
            for (ExecutableElement member : members) {
                currentMember = member;
                Content methodsContentTree = methodWriter.getMethodsContentHeader(
                        currentMember == members.last());
                buildChildren(node, methodsContentTree);
                serializableMethodTree.addContent(methodsContentTree);
            }
        }
        if (!utils.serializationMethods(currentTypeElement).isEmpty()) {
            classContentTree.addContent(methodWriter.getSerializableMethods(
                    configuration.getText("doclet.Serialized_Form_methods"),
                    serializableMethodTree));
            if (utils.isSerializable(currentTypeElement) && !utils.isExternalizable(currentTypeElement)) {
                if (utils.serializationMethods(currentTypeElement).isEmpty()) {
                    Content noCustomizationMsg = methodWriter.getNoCustomizationMsg(
                            configuration.getText("doclet.Serializable_no_customization"));
                    classContentTree.addContent(methodWriter.getSerializableMethods(
                    configuration.getText("doclet.Serialized_Form_methods"),
                    noCustomizationMsg));
                }
            }
        }
    }

    /**
     * Build the method sub header.
     *
     * @param node the XML element that specifies which components to document
     * @param methodsContentTree content tree to which the documentation will be added
     */
    public void buildMethodSubHeader(XMLNode node, Content methodsContentTree)  {
        methodWriter.addMemberHeader((ExecutableElement)currentMember, methodsContentTree);
    }

    /**
     * Build the deprecated method description.
     *
     * @param node the XML element that specifies which components to document
     * @param methodsContentTree content tree to which the documentation will be added
     */
    public void buildDeprecatedMethodInfo(XMLNode node, Content methodsContentTree) {
        methodWriter.addDeprecatedMemberInfo((ExecutableElement)currentMember, methodsContentTree);
    }

    /**
     * Build the information for the method.
     *
     * @param node the XML element that specifies which components to document
     * @param methodsContentTree content tree to which the documentation will be added
     */
    public void buildMethodInfo(XMLNode node, Content methodsContentTree)  {
        if(configuration.nocomment){
            return;
        }
        buildChildren(node, methodsContentTree);
    }

    /**
     * Build method description.
     *
     * @param node the XML element that specifies which components to document
     * @param methodsContentTree content tree to which the documentation will be added
     */
    public void buildMethodDescription(XMLNode node, Content methodsContentTree) {
        methodWriter.addMemberDescription((ExecutableElement)currentMember, methodsContentTree);
    }

    /**
     * Build the method tags.
     *
     * @param node the XML element that specifies which components to document
     * @param methodsContentTree content tree to which the documentation will be added
     */
    public void buildMethodTags(XMLNode node, Content methodsContentTree) {
        methodWriter.addMemberTags((ExecutableElement)currentMember, methodsContentTree);
        ExecutableElement method = (ExecutableElement)currentMember;
        if (method.getSimpleName().toString().compareTo("writeExternal") == 0
                && utils.getSerialDataTrees(method).isEmpty()) {
            if (configuration.serialwarn) {
                TypeElement encl  = (TypeElement) method.getEnclosingElement();
                configuration.getDocletSpecificMsg().warning(currentMember,
                        "doclet.MissingSerialDataTag", encl.getQualifiedName().toString(),
                        method.getSimpleName().toString());
            }
        }
    }

    /**
     * Build the field header.
     *
     * @param node the XML element that specifies which components to document
     * @param classContentTree content tree to which the documentation will be added
     */
    public void buildFieldHeader(XMLNode node, Content classContentTree) {
        if (!utils.serializableFields(currentTypeElement).isEmpty()) {
            buildFieldSerializationOverview(currentTypeElement, classContentTree);
        }
    }

    /**
     * Build the serialization overview for the given class.
     *
     * @param typeElement the class to print the overview for.
     * @param classContentTree content tree to which the documentation will be added
     */
    public void buildFieldSerializationOverview(TypeElement typeElement, Content classContentTree) {
        if (utils.definesSerializableFields(typeElement)) {
            VariableElement ve = utils.serializableFields(typeElement).first();
            // Check to see if there are inline comments, tags or deprecation
            // information to be printed.
            if (fieldWriter.shouldPrintOverview(ve)) {
                Content serializableFieldsTree = fieldWriter.getSerializableFieldsHeader();
                Content fieldsOverviewContentTree = fieldWriter.getFieldsContentHeader(true);
                fieldWriter.addMemberDeprecatedInfo(ve, fieldsOverviewContentTree);
                if (!configuration.nocomment) {
                    fieldWriter.addMemberDescription(ve, fieldsOverviewContentTree);
                    fieldWriter.addMemberTags(ve, fieldsOverviewContentTree);
                }
                serializableFieldsTree.addContent(fieldsOverviewContentTree);
                classContentTree.addContent(fieldWriter.getSerializableFields(
                        configuration.getText("doclet.Serialized_Form_class"),
                        serializableFieldsTree));
            }
        }
    }

    /**
     * Build the summaries for the fields that belong to the given class.
     *
     * @param node the XML element that specifies which components to document
     * @param classContentTree content tree to which the documentation will be added
     */
    public void buildSerializableFields(XMLNode node, Content classContentTree) {
        SortedSet<VariableElement> members = utils.serializableFields(currentTypeElement);
        if (!members.isEmpty()) {
            Content serializableFieldsTree = fieldWriter.getSerializableFieldsHeader();
            for (VariableElement ve : members) {
                currentMember = ve;
                if (!utils.definesSerializableFields(currentTypeElement)) {
                    Content fieldsContentTree = fieldWriter.getFieldsContentHeader(
                            currentMember == members.last());
                    buildChildren(node, fieldsContentTree);
                    serializableFieldsTree.addContent(fieldsContentTree);
                } else {
                    buildSerialFieldTagsInfo(serializableFieldsTree);
                }
            }
            classContentTree.addContent(fieldWriter.getSerializableFields(
                    configuration.getText("doclet.Serialized_Form_fields"),
                    serializableFieldsTree));
        }
    }

    /**
     * Build the field sub header.
     *
     * @param node the XML element that specifies which components to document
     * @param fieldsContentTree content tree to which the documentation will be added
     */
    public void buildFieldSubHeader(XMLNode node, Content fieldsContentTree) {
        if (!utils.definesSerializableFields(currentTypeElement)) {
            VariableElement field = (VariableElement) currentMember;
            fieldWriter.addMemberHeader(utils.asTypeElement(field.asType()),
                    utils.getTypeName(field.asType(), false), utils.getDimension(field.asType()),
                    utils.getSimpleName(field),
                    fieldsContentTree);
        }
    }

    /**
     * Build the field deprecation information.
     *
     * @param node the XML element that specifies which components to document
     * @param fieldsContentTree content tree to which the documentation will be added
     */
    public void buildFieldDeprecationInfo(XMLNode node, Content fieldsContentTree) {
        if (!utils.definesSerializableFields(currentTypeElement)) {
            fieldWriter.addMemberDeprecatedInfo((VariableElement)currentMember,
                    fieldsContentTree);
        }
    }

    /**
     * Build the serial field tags information.
     *
     * @param serializableFieldsTree content tree to which the documentation will be added
     */
    public void buildSerialFieldTagsInfo(Content serializableFieldsTree) {
        if(configuration.nocomment){
            return;
        }
        VariableElement field = (VariableElement)currentMember;
        // Process Serializable Fields specified as array of
        // ObjectStreamFields. Print a member for each serialField tag.
        // (There should be one serialField tag per ObjectStreamField
        // element.)
        SortedSet<SerialFieldTree> tags = new TreeSet<>(utils.makeSerialFieldTreeComparator());
        // sort the elements
        for (DocTree dt : utils.getSerialFieldTrees(field)) {
            SerialFieldTree st = (SerialFieldTree) dt;
            tags.add(st);
        }

        CommentHelper ch = utils.getCommentHelper(field);
        for (SerialFieldTree tag : tags) {
            if (tag.getName() == null || tag.getType() == null)  // ignore malformed @serialField tags
                continue;
            Content fieldsContentTree = fieldWriter.getFieldsContentHeader(tag.equals(tags.last()));
            TypeElement te = ch.getReferencedClass(configuration, tag);
            String fieldType = ch.getReferencedMemberName(tag);
            if (te != null && utils.isPrimitive(te.asType())) {
                fieldType = utils.getTypeName(te.asType(), false);
                te = null;
            }
            String refSignature = ch.getReferencedSignature(tag);
            // TODO: Print the signature directly, if it is an array, the
            // current DocTree APIs makes it very hard to distinguish
            // an as these are returned back as "Array" a DeclaredType.
            if (refSignature.endsWith("[]")) {
                te = null;
                fieldType = refSignature;
            }
            fieldWriter.addMemberHeader(te, fieldType, "",
                    tag.getName().getName().toString(), fieldsContentTree);
            fieldWriter.addMemberDescription(field, tag, fieldsContentTree);
            serializableFieldsTree.addContent(fieldsContentTree);
        }
    }

    /**
     * Build the field information.
     *
     * @param node the XML element that specifies which components to document
     * @param fieldsContentTree content tree to which the documentation will be added
     */
    public void buildFieldInfo(XMLNode node, Content fieldsContentTree) {
        if(configuration.nocomment){
            return;
        }
        VariableElement field = (VariableElement)currentMember;
        TypeElement te = utils.getEnclosingTypeElement(currentMember);
        // Process default Serializable field.
        if ((utils.getSerialTrees(field).isEmpty()) /*&& ! field.isSynthetic()*/
                && configuration.serialwarn) {
            configuration.message.warning(field,
                    "doclet.MissingSerialTag", utils.getFullyQualifiedName(te),
                    utils.getSimpleName(field));
        }
        fieldWriter.addMemberDescription(field, fieldsContentTree);
        fieldWriter.addMemberTags(field, fieldsContentTree);
    }

    /**
     * Return true if the given Element should be included
     * in the serialized form.
     *
     * @param element the Element object to check for serializability.
     */
    public static boolean serialInclude(Utils utils, Element element) {
        if (element == null) {
            return false;
        }
        return utils.isClass(element)
                ? serialClassInclude(utils, (TypeElement)element)
                : serialDocInclude(utils, element);
    }

    /**
     * Return true if the given TypeElement should be included
     * in the serialized form.
     *
     * @param te the TypeElement object to check for serializability.
     */
    private static boolean serialClassInclude(Utils utils, TypeElement te) {
        if (utils.isEnum(te)) {
            return false;
        }
        if (utils.isSerializable(te)) {
            if (!utils.getSerialTrees(te).isEmpty()) {
                return serialDocInclude(utils, te);
            } else if (utils.isPublic(te) || utils.isProtected(te)) {
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    /**
     * Return true if the given Element should be included
     * in the serialized form.
     *
     * @param element the Element to check for serializability.
     */
    private static boolean serialDocInclude(Utils utils, Element element) {
        if (utils.isEnum(element)) {
            return false;
        }
        List<? extends DocTree> serial = utils.getSerialTrees(element);
        if (!serial.isEmpty()) {
            CommentHelper ch = utils.getCommentHelper(element);
            String serialtext = Utils.toLowerCase(ch.getText(serial.get(0)));
            if (serialtext.contains("exclude")) {
                return false;
            } else if (serialtext.contains("include")) {
                return true;
            }
        }
        return true;
    }

    /**
     * Return true if any of the given typeElements have a @serialinclude tag.
     *
     * @param classes the typeElements to check.
     * @return true if any of the given typeElements have a @serialinclude tag.
     */
    private boolean serialClassFoundToDocument(SortedSet<TypeElement> classes) {
        for (TypeElement aClass : classes) {
            if (serialClassInclude(utils, aClass)) {
                return true;
            }
        }
        return false;
    }
}
