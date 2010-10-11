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
import java.util.*;

/**
 * Builds documentation for a field.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @author Jamie Ho
 * @since 1.5
 */
public class FieldBuilder extends AbstractMemberBuilder {

        /**
         * The class whose fields are being documented.
         */
        private ClassDoc classDoc;

        /**
         * The visible fields for the given class.
         */
        private VisibleMemberMap visibleMemberMap;

        /**
         * The writer to output the field documentation.
         */
        private FieldWriter writer;

        /**
         * The list of fields being documented.
         */
        private List<ProgramElementDoc> fields;

        /**
         * The index of the current field that is being documented at this point
         * in time.
         */
        private int currentFieldIndex;

        /**
         * Construct a new FieldBuilder.
         *
         * @param configuration the current configuration of the
         *                      doclet.
         */
        private FieldBuilder(Configuration configuration) {
                super(configuration);
        }

        /**
         * Construct a new FieldBuilder.
         *
         * @param configuration the current configuration of the doclet.
         * @param classDoc the class whoses members are being documented.
         * @param writer the doclet specific writer.
         */
        public static FieldBuilder getInstance(
                Configuration configuration,
                ClassDoc classDoc,
                FieldWriter writer) {
                FieldBuilder builder = new FieldBuilder(configuration);
                builder.classDoc = classDoc;
                builder.writer = writer;
                builder.visibleMemberMap =
                        new VisibleMemberMap(
                                classDoc,
                                VisibleMemberMap.FIELDS,
                                configuration.nodeprecated);
                builder.fields =
                        new ArrayList<ProgramElementDoc>(builder.visibleMemberMap.getLeafClassMembers(
                            configuration));
                if (configuration.getMemberComparator() != null) {
                        Collections.sort(
                                builder.fields,
                                configuration.getMemberComparator());
                }
                return builder;
        }

        /**
         * {@inheritDoc}
         */
        public String getName() {
                return "FieldDetails";
        }

        /**
         * Returns a list of fields that will be documented for the given class.
         * This information can be used for doclet specific documentation
         * generation.
         *
         * @param classDoc the {@link ClassDoc} we want to check.
         * @return a list of fields that will be documented.
         */
        public List<ProgramElementDoc> members(ClassDoc classDoc) {
                return visibleMemberMap.getMembersFor(classDoc);
        }

        /**
         * Returns the visible member map for the fields of this class.
         *
         * @return the visible member map for the fields of this class.
         */
        public VisibleMemberMap getVisibleMemberMap() {
                return visibleMemberMap;
        }

        /**
         * summaryOrder.size()
         */
        public boolean hasMembersToDocument() {
                return fields.size() > 0;
        }

        /**
         * Build the field documentation.
         *
         * @param elements the XML elements that specify how to construct this
         *                documentation.
         */
        public void buildFieldDoc(XMLNode node) {
                if (writer == null) {
                        return;
                }
                for (currentFieldIndex = 0;
                        currentFieldIndex < fields.size();
                        currentFieldIndex++) {
                        buildChildren(node);
                }
        }

        /**
         * Build the overall header.
         */
        public void buildHeader(XMLNode node) {
                writer.writeHeader(
                        classDoc,
                        configuration.getText("doclet.Field_Detail"));
        }

        /**
         * Build the header for the individual field.
         */
        public void buildFieldHeader(XMLNode node) {
                writer.writeFieldHeader(
                        (FieldDoc) fields.get(currentFieldIndex),
                        currentFieldIndex == 0);
        }

        /**
         * Build the signature.
         */
        public void buildSignature(XMLNode node) {
                writer.writeSignature((FieldDoc) fields.get(currentFieldIndex));
        }

        /**
         * Build the deprecation information.
         */
        public void buildDeprecationInfo(XMLNode node) {
                writer.writeDeprecated((FieldDoc) fields.get(currentFieldIndex));
        }

        /**
         * Build the comments for the field.  Do nothing if
         * {@link Configuration#nocomment} is set to true.
         */
        public void buildFieldComments(XMLNode node) {
                if (!configuration.nocomment) {
                        writer.writeComments((FieldDoc) fields.get(currentFieldIndex));
                }
        }

        /**
         * Build the tag information.
         */
        public void buildTagInfo(XMLNode node) {
                writer.writeTags((FieldDoc) fields.get(currentFieldIndex));
        }

        /**
         * Build the footer for the individual field.
         */
        public void buildFieldFooter(XMLNode node) {
                writer.writeFieldFooter();
        }

        /**
         * Build the overall footer.
         */
        public void buildFooter(XMLNode node) {
                writer.writeFooter(classDoc);
        }

        /**
         * Return the field writer for this builder.
         *
         * @return the field writer for this builder.
         */
        public FieldWriter getWriter() {
                return writer;
        }
}
