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

import java.util.*;
import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.javadoc.*;

/**
 * Builds documentation for a method.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
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
     *
     * @param node the XML element that specifies which components to document
     * @param memberDetailsTree the content tree to which the documentation will be added
     */
    public void buildMethodDoc(XMLNode node, Content memberDetailsTree) {
        if (writer == null) {
            return;
        }
        int size = methods.size();
        if (size > 0) {
            Content methodDetailsTree = writer.getMethodDetailsTreeHeader(
                    classDoc, memberDetailsTree);
            for (currentMethodIndex = 0; currentMethodIndex < size;
                    currentMethodIndex++) {
                Content methodDocTree = writer.getMethodDocTreeHeader(
                        (MethodDoc) methods.get(currentMethodIndex),
                        methodDetailsTree);
                buildChildren(node, methodDocTree);
                methodDetailsTree.addContent(writer.getMethodDoc(
                        methodDocTree, (currentMethodIndex == size - 1)));
            }
            memberDetailsTree.addContent(
                    writer.getMethodDetails(methodDetailsTree));
        }
    }

    /**
     * Build the signature.
     *
     * @param node the XML element that specifies which components to document
     * @param methodDocTree the content tree to which the documentation will be added
     */
    public void buildSignature(XMLNode node, Content methodDocTree) {
        methodDocTree.addContent(
                writer.getSignature((MethodDoc) methods.get(currentMethodIndex)));
    }

    /**
     * Build the deprecation information.
     *
     * @param node the XML element that specifies which components to document
     * @param methodDocTree the content tree to which the documentation will be added
     */
    public void buildDeprecationInfo(XMLNode node, Content methodDocTree) {
        writer.addDeprecated(
                (MethodDoc) methods.get(currentMethodIndex), methodDocTree);
    }

    /**
     * Build the comments for the method.  Do nothing if
     * {@link Configuration#nocomment} is set to true.
     *
     * @param node the XML element that specifies which components to document
     * @param methodDocTree the content tree to which the documentation will be added
     */
    public void buildMethodComments(XMLNode node, Content methodDocTree) {
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
            writer.addComments(method.containingClass(), method, methodDocTree);
        }
    }

    /**
     * Build the tag information.
     *
     * @param node the XML element that specifies which components to document
     * @param methodDocTree the content tree to which the documentation will be added
     */
    public void buildTagInfo(XMLNode node, Content methodDocTree) {
        writer.addTags((MethodDoc) methods.get(currentMethodIndex),
                methodDocTree);
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
