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

package jdk.javadoc.internal.doclets.toolkit;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;

/**
 * The interface for writing serialized form output.
 */
public interface SerializedFormWriter {

    /**
     * Get the header.
     *
     * @param header the header to write.
     * @return the header content
     */
    Content getHeader(String header);

    /**
     * Get the serialized form summaries header.
     *
     * @return the serialized form summary header
     */
    Content getSerializedSummariesHeader();

    /**
     * Get the package serialized form header.
     *
     * @return the package serialized form header
     */
    Content getPackageSerializedHeader();

    /**
     * Add the serialized package to the serialized summaries.
     *
     * @param serializedSummaries the serialized content to which the package serialized content will be added
     * @param packageSerialized the serialized content per package that needs to be added
     */
    void addPackageSerialized(Content serializedSummaries, Content packageSerialized);

    /**
     * {@return a header for the given package}
     *
     * @param packageElement the package element to write
     */
    Content getPackageHeader(PackageElement packageElement);

    /**
     * {@return the serialized class header}
     */
    Content getClassSerializedHeader();

    /**
     * {@return the heading for the serializable class}
     *
     * @param typeElement the class being processed
     */
    Content getClassHeader(TypeElement typeElement);

    /**
     * {@return the serial UID info header}
     */
    Content getSerialUIDInfoHeader();

    /**
     * Adds the serial UID info.
     *
     * @param header the header that will show up before the UID.
     * @param serialUID the serial UID to print.
     * @param target the serial UID to which the content will be added.
     */
    void addSerialUIDInfo(String header, String serialUID, Content target);

    /**
     * {@return the serialized class header}
     */
    Content getClassContentHeader();

    /**
     * Return an instance of a SerialFieldWriter for a class.
     *
     * @param typeElement the class
     * @return an instance of a SerialFieldWriter.
     */
    SerialFieldWriter getSerialFieldWriter(TypeElement typeElement);

    /**
     * Return an instance of a SerialMethodWriter for a class.
     *
     * @param typeElement the class
     * @return an instance of a SerialMethodWriter.
     */
    SerialMethodWriter getSerialMethodWriter(TypeElement typeElement);

    /**
     * Add the serialized content to the body content.
     *
     * @param source content for serialized data
     */
    void addSerializedContent(Content source);

    /**
     * Add the footer.
     */
    void addFooter();

    /**
     * Print the serialized form document.
     *
     * @param source the content that will be printed
     * @throws DocFileIOException if there is a problem while writing the document
     */
    void printDocument(Content source) throws DocFileIOException;

    /**
     * Gets the member.
     *
     * @param content the content used to generate the complete member
     * @return the member
     */
    Content getMember(Content content);

    /**
     * A writer for the serialized form for a given field.
     */
    interface SerialFieldWriter {

        /**
         * {@return the serializable field header}
         */
        Content getSerializableFieldsHeader();

        /**
         * {@return the field content header}
         *
         * @param isLastContent true if this is the last content to be documented
         */
        Content getFieldsContentHeader(boolean isLastContent);

        /**
         * {@return the fields}
         *
         * @param heading the heading to write.
         * @param content the content to be added
         * @return serializable fields content
         */
        Content getSerializableFields(String heading, Content content);

        /**
         * Adds the deprecated information for this member.
         *
         * @param field the field to document.
         * @param content the content to which the deprecated information will be added
         */
        void addMemberDeprecatedInfo(VariableElement field, Content content);

        /**
         * Adds the description text for this member.
         *
         * @param field the field to document
         * @param content the content to which the member description will be added
         */
        void addMemberDescription(VariableElement field, Content content);

        /**
         * Adds the description text for this member represented by the tag.
         *
         * @param field the field to document
         * @param serialFieldTag the field to document (represented by tag)
         * @param content the content to which the member description will be added
         */
        void addMemberDescription(VariableElement field, DocTree serialFieldTag, Content content);

        /**
         * Adds the tag information for this member.
         *
         * @param field the field to document
         * @param content the content to which the member tags will be added
         */
        void addMemberTags(VariableElement field, Content content);

        /**
         * Adds the member header.
         *
         * @param fieldType the type of the field
         * @param fieldName the name of the field
         * @param content the content to which the member header will be added
         */
        void addMemberHeader(TypeMirror fieldType, String fieldName, Content content);

        /**
         * Check to see if overview details should be printed. If
         * nocomment option set or if there is no text to be printed
         * for deprecation info, inline comment or tags,
         * do not print overview details.
         *
         * @param field the field to check overview details for
         * @return true if overview details need to be printed
         */
        boolean shouldPrintOverview(VariableElement field);
    }

    /**
     * Write the serialized form for a given field.
     */
    interface SerialMethodWriter {

        /**
         * {@return the header for serializable methods section}
         */
        Content getSerializableMethodsHeader();

        /**
         * {@return the header for serializable methods content section}
         *
         * @param isLastContent true if the content being documented is the last content
         */
        Content getMethodsContentHeader(boolean isLastContent);

        /**
         * Gets the given heading.
         *
         * @param heading the heading to write
         * @param source the content which will be added
         * @return a serializable methods content
         */
        Content getSerializableMethods(String heading, Content source);

        /**
         * Gets a warning that no serializable methods exist.
         *
         * @param msg the warning to print
         * @return a no customization message
         */
        Content getNoCustomizationMsg(String msg);

        /**
         * Adds the header.
         *
         * @param member the member to write the header for
         * @param methodsContent the content to which the header will be added
         */
        void addMemberHeader(ExecutableElement member, Content methodsContent);

        /**
         * Adds the deprecated information for this member.
         *
         * @param member the member to write the deprecated information for
         * @param methodsContent the content to which the deprecated
         * information will be added
         */
        void addDeprecatedMemberInfo(ExecutableElement member, Content methodsContent);

        /**
         * Adds the description for this member.
         *
         * @param member the member to write the information for
         * @param methodsContent the content to which the member
         * information will be added
         */
        void addMemberDescription(ExecutableElement member, Content methodsContent);

        /**
         * Adds the tag information for this member.
         *
         * @param member the member to write the tags information for
         * @param methodsContent the content to which the tags
         * information will be added
         */
        void addMemberTags(ExecutableElement member, Content methodsContent);
    }
}
