/*
 * Copyright (c) 2003, 2009, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.tools.doclets.internal.toolkit.*;

/**
 * Builds the serialized form.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 * @since 1.5
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
    private static final String SERIAL_VERSION_UID_HEADER = "serialVersionUID:";

    /**
     * The current package being documented.
     */
    private PackageDoc currentPackage;

    /**
     * The current class being documented.
     */
    private ClassDoc currentClass;

    /**
     * The current member being documented.
     */
    protected MemberDoc currentMember;

    private SerializedFormBuilder(Configuration configuration) {
        super(configuration);
    }

    /**
     * Construct a new SerializedFormBuilder.
     * @param configuration the current configuration of the doclet.
     */
    public static SerializedFormBuilder getInstance(Configuration configuration) {
        SerializedFormBuilder builder = new SerializedFormBuilder(configuration);
        return builder;
    }

    /**
     * Build the serialized form.
     */
    public void build() throws IOException {
        if (! serialClassFoundToDocument(configuration.root.classes())) {
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
            throw new DocletAbortException();
        }
        build(LayoutParser.getInstance(configuration).parseXML(NAME));
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
     */
    public void buildSerializedForm(List<?> elements) throws Exception {
        build(elements);
        writer.close();
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
     * Build the header.
     */
    public void buildHeader() {
        writer.writeHeader(configuration.getText("doclet.Serialized_Form"));
    }

    /**
     * Build the contents.
     */
    public void buildSerializedFormSummaries(List<?> elements) {
        PackageDoc[] packages = configuration.packages;
        for (int i = 0; i < packages.length; i++) {
            currentPackage = packages[i];
            build(elements);
        }
    }

    /**
     * Build the package serialized for for the current package being processed.
     */
    public void buildPackageSerializedForm(List<?> elements) {
        String foo = currentPackage.name();
        ClassDoc[] classes = currentPackage.allClasses(false);
        if (classes == null || classes.length == 0) {
            return;
        }
        if (!serialInclude(currentPackage)) {
            return;
        }
        if (!serialClassFoundToDocument(classes)) {
            return;
        }
        build(elements);
    }

    public void buildPackageHeader() {
        writer.writePackageHeader(Util.getPackageName(currentPackage));
    }

    public void buildClassSerializedForm(List<?> elements) {
        ClassDoc[] classes = currentPackage.allClasses(false);
        Arrays.sort(classes);
        for (int j = 0; j < classes.length; j++) {
            currentClass = classes[j];
            fieldWriter = writer.getSerialFieldWriter(currentClass);
            methodWriter = writer.getSerialMethodWriter(currentClass);
            if(currentClass.isClass() && currentClass.isSerializable()) {
                if(!serialClassInclude(currentClass)) {
                    continue;
                }
                build(elements);
            }
        }
    }

    public void buildClassHeader() {
        writer.writeClassHeader(currentClass);
    }

    /**
     * Build the serial UID information for the given class.
     */
    public void buildSerialUIDInfo() {
        FieldDoc[] fields = currentClass.fields(false);
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].name().equals("serialVersionUID") &&
                fields[i].constantValueExpression() != null) {
                writer.writeSerialUIDInfo(SERIAL_VERSION_UID_HEADER,
                    fields[i].constantValueExpression());
                return;
            }
        }
    }

    /**
     * Build the footer.
     */
    public void buildFooter() {
        writer.writeFooter();
    }

    /**
     * Return true if the given Doc should be included
     * in the serialized form.
     *
     * @param doc the Doc object to check for serializability.
     */
    public static boolean serialInclude(Doc doc) {
        if (doc == null) {
            return false;
        }
        return doc.isClass() ?
            serialClassInclude((ClassDoc)doc) :
            serialDocInclude(doc);
    }

    /**
     * Return true if the given ClassDoc should be included
     * in the serialized form.
     *
     * @param cd the ClassDoc object to check for serializability.
     */
    private static boolean serialClassInclude(ClassDoc cd) {
        if (cd.isEnum()) {
            return false;
        }
        try {
            cd.superclassType();
        } catch (NullPointerException e) {
            //Workaround for null pointer bug in ClassDoc.superclassType().
            return false;
        }
        if (cd.isSerializable()) {
            if (cd.tags("serial").length > 0) {
                return serialDocInclude(cd);
            } else if (cd.isPublic() || cd.isProtected()) {
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    /**
     * Return true if the given Doc should be included
     * in the serialized form.
     *
     * @param doc the Doc object to check for serializability.
     */
    private static boolean serialDocInclude(Doc doc) {
        if (doc.isEnum()) {
            return false;
        }
        Tag[] serial = doc.tags("serial");
        if (serial.length > 0) {
            String serialtext = serial[0].text().toLowerCase();
            if (serialtext.indexOf("exclude") >= 0) {
                return false;
            } else if (serialtext.indexOf("include") >= 0) {
                return true;
            }
        }
        return true;
    }

    /**
     * Return true if any of the given classes have a @serialinclude tag.
     *
     * @param classes the classes to check.
     * @return true if any of the given classes have a @serialinclude tag.
     */
    private boolean serialClassFoundToDocument(ClassDoc[] classes) {
        for (int i = 0; i < classes.length; i++) {
            if (serialClassInclude(classes[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Build the method header.
     */
    public void buildMethodHeader() {
        if (currentClass.serializationMethods().length > 0) {
            methodWriter.writeHeader(
                configuration.getText("doclet.Serialized_Form_methods"));
            if (currentClass.isSerializable() && !currentClass.isExternalizable()) {
                if (currentClass.serializationMethods().length == 0) {
                    methodWriter.writeNoCustomizationMsg(
                        configuration.getText(
                            "doclet.Serializable_no_customization"));
                }
            }
        }
    }

    /**
     * Build the method sub header.
     */
    public void buildMethodSubHeader()  {
        methodWriter.writeMemberHeader((MethodDoc) currentMember);
    }

    /**
     * Build the deprecated method description.
     */
    public void buildDeprecatedMethodInfo() {
        methodWriter.writeDeprecatedMemberInfo((MethodDoc) currentMember);
    }

    /**
     * Build method tags.
     */
    public void buildMethodDescription() {
        methodWriter.writeMemberDescription((MethodDoc) currentMember);
    }

    /**
     * Build the method tags.
     */
    public void buildMethodTags() {
        methodWriter.writeMemberTags((MethodDoc) currentMember);
        MethodDoc method = (MethodDoc)currentMember;
        if (method.name().compareTo("writeExternal") == 0
            && method.tags("serialData").length == 0) {
            if (configuration.serialwarn) {
                configuration.getDocletSpecificMsg().warning(
                    currentMember.position(), "doclet.MissingSerialDataTag",
                    method.containingClass().qualifiedName(), method.name());
            }
        }
    }

    /**
     * build the information for the method.
     */
    public void buildMethodInfo(List<?> elements)  {
        if(configuration.nocomment){
            return;
        }
        build(elements);
    }

    /**
     * Build the method footer.
     */
    public void buildMethodFooter() {
        methodWriter.writeMemberFooter();
    }

    /**
     * Build the field header.
     */
    public void buildFieldHeader() {
        if (currentClass.serializableFields().length > 0) {
            buildFieldSerializationOverview(currentClass);
            fieldWriter.writeHeader(configuration.getText(
                "doclet.Serialized_Form_fields"));
        }
    }

    /**
     * If possible, build the serialization overview for the given
     * class.
     *
     * @param classDoc the class to print the overview for.
     */
    public void buildFieldSerializationOverview(ClassDoc classDoc) {
        if (classDoc.definesSerializableFields()) {
            FieldDoc serialPersistentField =
                Util.asList(classDoc.serializableFields()).get(0);
            // Check to see if there are inline comments, tags or deprecation
            // information to be printed.
            if (fieldWriter.shouldPrintOverview(serialPersistentField)) {
                fieldWriter.writeHeader(
                        configuration.getText("doclet.Serialized_Form_class"));
                fieldWriter.writeMemberDeprecatedInfo(serialPersistentField);
                if (!configuration.nocomment) {
                    fieldWriter.writeMemberDescription(serialPersistentField);
                    fieldWriter.writeMemberTags(serialPersistentField);
                }
                // Footer required to close the definition list tag
                // for serialization overview.
                fieldWriter.writeFooter(
                        configuration.getText("doclet.Serialized_Form_class"));
            }
        }
    }

    /**
     * Build the field sub header.
     */
    public void buildFieldSubHeader() {
        if (! currentClass.definesSerializableFields() ){
            FieldDoc field = (FieldDoc) currentMember;
            fieldWriter.writeMemberHeader(field.type().asClassDoc(),
                field.type().typeName(), field.type().dimension(), field.name());
        }
    }

    /**
     * Build the field deprecation information.
     */
    public void buildFieldDeprecationInfo() {
        if (!currentClass.definesSerializableFields()) {
            FieldDoc field = (FieldDoc)currentMember;
            fieldWriter.writeMemberDeprecatedInfo(field);
        }
    }

    /**
     * Build the field information.
     */
    public void buildFieldInfo() {
        if(configuration.nocomment){
            return;
        }
        FieldDoc field = (FieldDoc)currentMember;
        ClassDoc cd = field.containingClass();
        if (cd.definesSerializableFields()) {
            // Process Serializable Fields specified as array of
            // ObjectStreamFields. Print a member for each serialField tag.
            // (There should be one serialField tag per ObjectStreamField
            // element.)
            SerialFieldTag[] tags = field.serialFieldTags();
            Arrays.sort(tags);
            for (int i = 0; i < tags.length; i++) {
                fieldWriter.writeMemberHeader(tags[i].fieldTypeDoc(),
                        tags[i].fieldType(), "", tags[i].fieldName());
                fieldWriter.writeMemberDescription(tags[i]);

            }
        } else {

            // Process default Serializable field.
            if ((field.tags("serial").length == 0) && ! field.isSynthetic()
                && configuration.serialwarn) {
                configuration.message.warning(field.position(),
                        "doclet.MissingSerialTag", cd.qualifiedName(),
                        field.name());
            }
            fieldWriter.writeMemberDescription(field);
            fieldWriter.writeMemberTags(field);
        }
    }

    /**
     * Build the field sub footer.
     */
    public void buildFieldSubFooter() {
        if (! currentClass.definesSerializableFields()) {
            fieldWriter.writeMemberFooter();
        }
    }

    /**
     * Build the summaries for the methods that belong to the given
     * class.
     */
    public void buildSerializableMethods(List<?> elements) {
        MemberDoc[] members = currentClass.serializationMethods();
        if (members.length > 0) {
            for (int i = 0; i < members.length; i++) {
                currentMember = members[i];
                build(elements);
            }
        }
    }

    /**
     * Build the summaries for the fields that belong to the given
     * class.
     */
    public void buildSerializableFields(List<?> elements) {
        MemberDoc[] members = currentClass.serializableFields();
        if (members.length > 0) {
            for (int i = 0; i < members.length; i++) {
                currentMember = members[i];
                build(elements);
            }
        }
    }
}
