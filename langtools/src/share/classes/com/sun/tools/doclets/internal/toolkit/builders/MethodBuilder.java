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
import java.lang.reflect.*;
/**
 * Builds documentation for a method.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @author Jamie Ho
 * @since 1.5
 */
public class MethodBuilder extends AbstractMemberBuilder {

        /**
         * The index of the current field that is being documented at this point
         * in time.
         */
        private int currentMethodIndex;

        /**
         * The class whose methods are being documented.
         */
        private ClassDoc classDoc;

        /**
         * The visible methods for the given class.
         */
        private VisibleMemberMap visibleMemberMap;

        /**
         * The writer to output the method documentation.
         */
        private MethodWriter writer;

        /**
         * The methods being documented.
         */
        private List<ProgramElementDoc> methods;

        private MethodBuilder(Configuration configuration) {
                super(configuration);
        }

        /**
         * Construct a new MethodBuilder.
         *
         * @param configuration the current configuration of the doclet.
         * @param classDoc the class whoses members are being documented.
         * @param writer the doclet specific writer.
         *
         * @return an instance of a MethodBuilder.
         */
        public static MethodBuilder getInstance(
                Configuration configuration,
                ClassDoc classDoc,
                MethodWriter writer) {
                MethodBuilder builder = new MethodBuilder(configuration);
                builder.classDoc = classDoc;
                builder.writer = writer;
                builder.visibleMemberMap =
                        new VisibleMemberMap(
                                classDoc,
                                VisibleMemberMap.METHODS,
                                configuration.nodeprecated);
                builder.methods =
                        new ArrayList<ProgramElementDoc>(builder.visibleMemberMap.getLeafClassMembers(
                configuration));
                if (configuration.getMemberComparator() != null) {
                        Collections.sort(
                                builder.methods,
                                configuration.getMemberComparator());
                }
                return builder;
        }

        /**
         * {@inheritDoc}
         */
        public String getName() {
                return "MethodDetails";
        }

        /**
         * {@inheritDoc}
         */
        public void invokeMethod(
                String methodName,
                Class<?>[] paramClasses,
                Object[] params)
                throws Exception {
                if (DEBUG) {
                        configuration.root.printError(
                                "DEBUG: " + this.getClass().getName() + "." + methodName);
                }
                Method method = this.getClass().getMethod(methodName, paramClasses);
                method.invoke(this, params);
        }

        /**
         * Returns a list of methods that will be documented for the given class.
         * This information can be used for doclet specific documentation
         * generation.
         *
         * @param classDoc the {@link ClassDoc} we want to check.
         * @return a list of methods that will be documented.
         */
        public List<ProgramElementDoc> members(ClassDoc classDoc) {
                return visibleMemberMap.getMembersFor(classDoc);
        }

        /**
         * Returns the visible member map for the methods of this class.
         *
         * @return the visible member map for the methods of this class.
         */
        public VisibleMemberMap getVisibleMemberMap() {
                return visibleMemberMap;
        }

        /**
         * {@inheritDoc}
         */
        public boolean hasMembersToDocument() {
                return methods.size() > 0;
        }

        /**
         * Build the method documentation.
         */
        public void buildMethodDoc(List<?> elements) {
                if (writer == null) {
                        return;
                }
                for (currentMethodIndex = 0;
                        currentMethodIndex < methods.size();
                        currentMethodIndex++) {
                        build(elements);
                }
        }

        /**
         * Build the overall header.
         */
        public void buildHeader() {
                writer.writeHeader(
                        classDoc,
                        configuration.getText("doclet.Method_Detail"));
        }

        /**
         * Build the header for the individual method.
         */
        public void buildMethodHeader() {
                writer.writeMethodHeader(
                        (MethodDoc) methods.get(currentMethodIndex),
                        currentMethodIndex == 0);
        }

        /**
         * Build the signature.
         */
        public void buildSignature() {
                writer.writeSignature((MethodDoc) methods.get(currentMethodIndex));
        }

        /**
         * Build the deprecation information.
         */
        public void buildDeprecationInfo() {
                writer.writeDeprecated((MethodDoc) methods.get(currentMethodIndex));
        }

        /**
         * Build the comments for the method.  Do nothing if
         * {@link Configuration#nocomment} is set to true.  If this method
         */
        public void buildMethodComments() {
                if (!configuration.nocomment) {
            MethodDoc method = (MethodDoc) methods.get(currentMethodIndex);

            if (method.inlineTags().length == 0) {
                DocFinder.Output docs = DocFinder.search(
                    new DocFinder.Input(method));
                method = docs.inlineTags != null && docs.inlineTags.length > 0 ?
                    (MethodDoc) docs.holder : method;

            }
            //NOTE:  When we fix the bug where ClassDoc.interfaceTypes() does
            //       not pass all implemented interfaces, holder will be the
            //       interface type.  For now, it is really the erasure.
            writer.writeComments(method.containingClass(), method);
                }
        }



        /**
         * Build the tag information.
         */
        public void buildTagInfo() {
                writer.writeTags((MethodDoc) methods.get(currentMethodIndex));
        }

        /**
         * Build the footer of the method.
         */
        public void buildMethodFooter() {
                writer.writeMethodFooter();
        }

        /**
         * Build the overall footer.
         */
        public void buildFooter() {
                writer.writeFooter(classDoc);
        }

        /**
         * Return the method writer for this builder.
         *
         * @return the method writer for this builder.
         */
        public MethodWriter getWriter() {
                return writer;
        }
}
