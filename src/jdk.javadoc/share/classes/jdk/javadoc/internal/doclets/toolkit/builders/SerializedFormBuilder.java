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


import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import com.sun.source.doctree.SerialFieldTree;
import com.sun.source.doctree.SerialTree;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.DocletException;
import jdk.javadoc.internal.doclets.toolkit.SerializedFormWriter;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

/**
 * Builds the serialized form.
 */
public class SerializedFormBuilder extends AbstractBuilder {

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
     * Construct a new SerializedFormBuilder.
     * @param context  the build context.
     */
    private SerializedFormBuilder(Context context) {
        super(context);
    }

    /**
     * Construct a new SerializedFormBuilder.
     *
     * @param context  the build context.
     * @return the new SerializedFormBuilder
     */
    public static SerializedFormBuilder getInstance(Context context) {
        return new SerializedFormBuilder(context);
    }

    /**
     * Build the serialized form.
     *
     * @throws DocletException if there is a problem while building the documentation
     */
    @Override
    public void build() throws DocletException {
        SortedSet<TypeElement> rootclasses = new TreeSet<>(utils.comparators.makeGeneralPurposeComparator());
        rootclasses.addAll(configuration.getIncludedTypeElements());
        if (!serialClassFoundToDocument(rootclasses)) {
            //Nothing to document.
            return;
        }
        writer = configuration.getWriterFactory().getSerializedFormWriter();
        if (writer == null) {
            //Doclet does not support this output.
            return;
        }
        buildSerializedForm();
    }

    /**
     * Build the serialized form.
     *
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildSerializedForm() throws DocletException {
        Content content = writer.getHeader(resources.getText(
                "doclet.Serialized_Form"));

        buildSerializedFormSummaries();

        writer.addFooter();
        writer.printDocument(content);
    }

    /**
     * Build the serialized form summaries.
     *
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildSerializedFormSummaries()
            throws DocletException {
        Content c = writer.getSerializedSummariesHeader();
        for (PackageElement pkg : configuration.packages) {
            currentPackage = pkg;

            buildPackageSerializedForm(c);
        }
        writer.addSerializedContent(c);
    }

    /**
     * Build the package serialized form for the current package being processed.
     *
     * @param target the content to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildPackageSerializedForm(Content target) throws DocletException {
        Content packageSerializedHeader = writer.getPackageSerializedHeader();
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

        buildPackageHeader(packageSerializedHeader);
        buildClassSerializedForm(packageSerializedHeader);

        writer.addPackageSerialized(target, packageSerializedHeader);
    }

    /**
     * Build the package header.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildPackageHeader(Content target) {
        target.add(writer.getPackageHeader(currentPackage));
    }

    /**
     * Build the class serialized form.
     *
     * @param target the content to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildClassSerializedForm(Content target)
            throws DocletException {
        Content classSerializedHeader = writer.getClassSerializedHeader();
        SortedSet<TypeElement> typeElements = utils.getAllClassesUnfiltered(currentPackage);
        for (TypeElement typeElement : typeElements) {
            currentTypeElement = typeElement;
            fieldWriter = writer.getSerialFieldWriter(currentTypeElement);
            methodWriter = writer.getSerialMethodWriter(currentTypeElement);
            if (utils.isClass(currentTypeElement) && utils.isSerializable(currentTypeElement)) {
                if (!serialClassInclude(utils, currentTypeElement)) {
                    continue;
                }
                Content classHeader = writer.getClassHeader(currentTypeElement);

                buildSerialUIDInfo(classHeader);
                buildClassContent(classHeader);

                classSerializedHeader.add(writer.getMember(classHeader));
            }
        }
        target.add(classSerializedHeader);
    }

    /**
     * Build the serial UID information for the given class.
     *
     * @param target the content to which the serial UID information will be added
     */
    protected void buildSerialUIDInfo(Content target) {
        Content serialUIDHeader = writer.getSerialUIDInfoHeader();
        for (VariableElement field : utils.getFieldsUnfiltered(currentTypeElement)) {
            if (field.getSimpleName().toString().compareTo(SERIAL_VERSION_UID) == 0 &&
                field.getConstantValue() != null) {
                writer.addSerialUIDInfo(SERIAL_VERSION_UID_HEADER,
                                        utils.constantValueExpression(field), serialUIDHeader);
                break;
            }
        }
        target.add(serialUIDHeader);
    }

    /**
     * Build the summaries for the methods and fields.
     *
     * @param target the content to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildClassContent(Content target) throws DocletException {
        Content classContent = writer.getClassContentHeader();

        buildSerializableMethods(classContent);
        buildFieldHeader(classContent);
        buildSerializableFields(classContent);

        target.add(classContent);
    }

    /**
     * Build the summaries for the methods that belong to the given class.
     *
     * @param target the content to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildSerializableMethods(Content target) throws DocletException {
        Content serializableMethodsHeader = methodWriter.getSerializableMethodsHeader();
        SortedSet<ExecutableElement> members = utils.serializationMethods(currentTypeElement);
        if (!members.isEmpty()) {
            for (ExecutableElement member : members) {
                currentMember = member;
                Content methodsContent = methodWriter.getMethodsContentHeader(
                        currentMember == members.last());

                buildMethodSubHeader(methodsContent);
                buildDeprecatedMethodInfo(methodsContent);
                buildMethodInfo(methodsContent);

                serializableMethodsHeader.add(methodsContent);
            }
        }
        if (!utils.serializationMethods(currentTypeElement).isEmpty()) {
            target.add(methodWriter.getSerializableMethods(
                    resources.getText("doclet.Serialized_Form_methods"),
                    serializableMethodsHeader));
            if (utils.isSerializable(currentTypeElement) && !utils.isExternalizable(currentTypeElement)) {
                if (utils.serializationMethods(currentTypeElement).isEmpty()) {
                    Content noCustomizationMsg = methodWriter.getNoCustomizationMsg(
                            resources.getText("doclet.Serializable_no_customization"));
                    target.add(methodWriter.getSerializableMethods(
                            resources.getText("doclet.Serialized_Form_methods"),
                            noCustomizationMsg));
                }
            }
        }
    }

    /**
     * Build the method sub header.
     *
     * @param methodsContent the content to which the documentation will be added
     */
    protected void buildMethodSubHeader(Content methodsContent)  {
        methodWriter.addMemberHeader((ExecutableElement)currentMember, methodsContent);
    }

    /**
     * Build the deprecated method description.
     *
     * @param methodsContent the content to which the documentation will be added
     */
    protected void buildDeprecatedMethodInfo(Content methodsContent) {
        methodWriter.addDeprecatedMemberInfo((ExecutableElement)currentMember, methodsContent);
    }

    /**
     * Build the information for the method.
     *
     * @param methodsContent the content to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildMethodInfo(Content methodsContent) throws DocletException  {
        if (options.noComment()) {
            return;
        }

        buildMethodDescription(methodsContent);
        buildMethodTags(methodsContent);
    }

    /**
     * Build method description.
     *
     * @param methodsContent the content to which the documentation will be added
     */
    protected void buildMethodDescription(Content methodsContent) {
        methodWriter.addMemberDescription((ExecutableElement)currentMember, methodsContent);
    }

    /**
     * Build the method tags.
     *
     * @param methodsContent the content to which the documentation will be added
     */
    protected void buildMethodTags(Content methodsContent) {
        methodWriter.addMemberTags((ExecutableElement)currentMember, methodsContent);
        ExecutableElement method = (ExecutableElement)currentMember;
        if (method.getSimpleName().toString().compareTo("writeExternal") == 0
                && utils.getSerialDataTrees(method).isEmpty()) {
            if (options.serialWarn()) {
                TypeElement encl  = (TypeElement) method.getEnclosingElement();
                messages.warning(currentMember,
                        "doclet.MissingSerialDataTag", encl.getQualifiedName().toString(),
                        method.getSimpleName().toString());
            }
        }
    }

    /**
     * Build the field header.
     *
     * @param classContent the content to which the documentation will be added
     */
    protected void buildFieldHeader(Content classContent) {
        if (!utils.serializableFields(currentTypeElement).isEmpty()) {
            buildFieldSerializationOverview(currentTypeElement, classContent);
        }
    }

    /**
     * Build the serialization overview for the given class.
     *
     * @param typeElement the class to print the overview for.
     * @param classContent the content to which the documentation will be added
     */
    public void buildFieldSerializationOverview(TypeElement typeElement, Content classContent) {
        if (utils.definesSerializableFields(typeElement)) {
            VariableElement ve = utils.serializableFields(typeElement).first();
            // Check to see if there are inline comments, tags or deprecation
            // information to be printed.
            if (fieldWriter.shouldPrintOverview(ve)) {
                Content serializableFieldsHeader = fieldWriter.getSerializableFieldsHeader();
                Content fieldsOverviewContent = fieldWriter.getFieldsContentHeader(true);
                fieldWriter.addMemberDeprecatedInfo(ve, fieldsOverviewContent);
                if (!options.noComment()) {
                    fieldWriter.addMemberDescription(ve, fieldsOverviewContent);
                    fieldWriter.addMemberTags(ve, fieldsOverviewContent);
                }
                serializableFieldsHeader.add(fieldsOverviewContent);
                classContent.add(fieldWriter.getSerializableFields(
                        resources.getText("doclet.Serialized_Form_class"),
                        serializableFieldsHeader));
            }
        }
    }

    /**
     * Build the summaries for the fields that belong to the given class.
     *
     * @param target the content to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildSerializableFields(Content target)
            throws DocletException {
        SortedSet<VariableElement> members = utils.serializableFields(currentTypeElement);
        if (!members.isEmpty()) {
            Content serializableFieldsHeader = fieldWriter.getSerializableFieldsHeader();
            for (VariableElement ve : members) {
                currentMember = ve;
                if (!utils.definesSerializableFields(currentTypeElement)) {
                    Content fieldsContent = fieldWriter.getFieldsContentHeader(
                            currentMember == members.last());

                    buildFieldSubHeader(fieldsContent);
                    buildFieldDeprecationInfo(fieldsContent);
                    buildFieldInfo(fieldsContent);

                    serializableFieldsHeader.add(fieldsContent);
                } else {
                    buildSerialFieldTagsInfo(serializableFieldsHeader);
                }
            }
            target.add(fieldWriter.getSerializableFields(
                    resources.getText("doclet.Serialized_Form_fields"),
                    serializableFieldsHeader));
        }
    }

    /**
     * Build the field sub header.
     *
     * @param fieldsContent the content to which the documentation will be added
     */
    protected void buildFieldSubHeader(Content fieldsContent) {
        if (!utils.definesSerializableFields(currentTypeElement)) {
            VariableElement field = (VariableElement) currentMember;
            fieldWriter.addMemberHeader(field.asType(),
                    utils.getSimpleName(field),
                    fieldsContent);
        }
    }

    /**
     * Build the field deprecation information.
     *
     * @param fieldsContent the content to which the documentation will be added
     */
    protected void buildFieldDeprecationInfo(Content fieldsContent) {
        if (!utils.definesSerializableFields(currentTypeElement)) {
            fieldWriter.addMemberDeprecatedInfo((VariableElement)currentMember,
                    fieldsContent);
        }
    }

    /**
     * Build the serial field tags information.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildSerialFieldTagsInfo(Content target) {
        if (options.noComment()) {
            return;
        }
        VariableElement field = (VariableElement)currentMember;
        // Process Serializable Fields specified as array of
        // ObjectStreamFields. Print a member for each serialField tag.
        // (There should be one serialField tag per ObjectStreamField
        // element.)
        SortedSet<SerialFieldTree> tags = new TreeSet<>(utils.comparators.makeSerialFieldTreeComparator());
        // sort the elements
        tags.addAll(utils.getSerialFieldTrees(field));

        CommentHelper ch = utils.getCommentHelper(field);
        for (SerialFieldTree tag : tags) {
            if (tag.getName() == null || tag.getType() == null)  // ignore malformed @serialField tags
                continue;
            Content fieldsContent = fieldWriter.getFieldsContentHeader(tag.equals(tags.last()));
            TypeMirror type = ch.getReferencedType(tag);
            fieldWriter.addMemberHeader(type, tag.getName().getName().toString(), fieldsContent);
            fieldWriter.addMemberDescription(field, tag, fieldsContent);
            target.add(fieldsContent);
        }
    }

    /**
     * Build the field information.
     *
     * @param fieldsContent the content to which the documentation will be added
     */
    protected void buildFieldInfo(Content fieldsContent) {
        if (options.noComment()) {
            return;
        }
        VariableElement field = (VariableElement)currentMember;
        TypeElement te = utils.getEnclosingTypeElement(currentMember);
        // Process default Serializable field.
        if ((utils.getSerialTrees(field).isEmpty()) /*&& !field.isSynthetic()*/
                && options.serialWarn()) {
            messages.warning(field,
                    "doclet.MissingSerialTag", utils.getFullyQualifiedName(te),
                    utils.getSimpleName(field));
        }
        fieldWriter.addMemberDescription(field, fieldsContent);
        fieldWriter.addMemberTags(field, fieldsContent);
    }

    /**
     * Returns true if the given Element should be included
     * in the serialized form.
     *
     * @param utils the utils object
     * @param element the Element object to check for serializability
     * @return true if the element should be included in the serial form
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
     * Returns true if the given TypeElement should be included
     * in the serialized form.
     *
     * @param te the TypeElement object to check for serializability.
     */
    private static boolean serialClassInclude(Utils utils, TypeElement te) {
        if (utils.isEnum(te)) {
            return false;
        }
        if (utils.isSerializable(te)) {
            if (utils.hasDocCommentTree(te) && !utils.getSerialTrees(te).isEmpty()) {
                return serialDocInclude(utils, te);
            } else {
                return utils.isPublic(te) || utils.isProtected(te);
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
        List<? extends SerialTree> serial = utils.getSerialTrees(element);
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
     * Return true if any of the given typeElements have a {@code @serial include} tag.
     *
     * @param classes the typeElements to check.
     * @return true if any of the given typeElements have a {@code @serial include} tag.
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
