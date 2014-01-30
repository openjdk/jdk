/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * Builds documentation for a constructor.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 * @since 1.5
 */
public class ConstructorBuilder extends AbstractMemberBuilder {

    /**
     * The name of this builder.
     */
    public static final String NAME = "ConstructorDetails";

    /**
     * The index of the current field that is being documented at this point
     * in time.
     */
    private int currentConstructorIndex;

    /**
     * The class whose constructors are being documented.
     */
    private final ClassDoc classDoc;

    /**
     * The visible constructors for the given class.
     */
    private final VisibleMemberMap visibleMemberMap;

    /**
     * The writer to output the constructor documentation.
     */
    private final ConstructorWriter writer;

    /**
     * The constructors being documented.
     */
    private final List<ProgramElementDoc> constructors;

    /**
     * Construct a new ConstructorBuilder.
     *
     * @param context  the build context.
     * @param classDoc the class whoses members are being documented.
     * @param writer the doclet specific writer.
     */
    private ConstructorBuilder(Context context,
            ClassDoc classDoc,
            ConstructorWriter writer) {
        super(context);
        this.classDoc = classDoc;
        this.writer = writer;
        visibleMemberMap =
                new VisibleMemberMap(
                classDoc,
                VisibleMemberMap.CONSTRUCTORS,
                configuration);
        constructors = new ArrayList<>(visibleMemberMap.getMembersFor(classDoc));
        for (ProgramElementDoc constructor : constructors) {
            if (constructor.isProtected() || constructor.isPrivate()) {
                writer.setFoundNonPubConstructor(true);
            }
        }
        if (configuration.getMemberComparator() != null) {
            Collections.sort(constructors,configuration.getMemberComparator());
        }
    }

    /**
     * Construct a new ConstructorBuilder.
     *
     * @param context  the build context.
     * @param classDoc the class whoses members are being documented.
     * @param writer the doclet specific writer.
     */
    public static ConstructorBuilder getInstance(Context context,
            ClassDoc classDoc, ConstructorWriter writer) {
        return new ConstructorBuilder(context, classDoc, writer);
    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return NAME;
    }

    /**
     * {@inheritDoc}
     */
    public boolean hasMembersToDocument() {
        return constructors.size() > 0;
    }

    /**
     * Returns a list of constructors that will be documented for the given class.
     * This information can be used for doclet specific documentation
     * generation.
     *
     * @return a list of constructors that will be documented.
     */
    public List<ProgramElementDoc> members(ClassDoc classDoc) {
        return visibleMemberMap.getMembersFor(classDoc);
    }

    /**
     * Return the constructor writer for this builder.
     *
     * @return the constructor writer for this builder.
     */
    public ConstructorWriter getWriter() {
        return writer;
    }

    /**
     * Build the constructor documentation.
     *
     * @param node the XML element that specifies which components to document
     * @param memberDetailsTree the content tree to which the documentation will be added
     */
    public void buildConstructorDoc(XMLNode node, Content memberDetailsTree) {
        if (writer == null) {
            return;
        }
        int size = constructors.size();
        if (size > 0) {
            Content constructorDetailsTree = writer.getConstructorDetailsTreeHeader(
                    classDoc, memberDetailsTree);
            for (currentConstructorIndex = 0; currentConstructorIndex < size;
                    currentConstructorIndex++) {
                Content constructorDocTree = writer.getConstructorDocTreeHeader(
                        (ConstructorDoc) constructors.get(currentConstructorIndex),
                        constructorDetailsTree);
                buildChildren(node, constructorDocTree);
                constructorDetailsTree.addContent(writer.getConstructorDoc(
                        constructorDocTree, (currentConstructorIndex == size - 1)));
            }
            memberDetailsTree.addContent(
                    writer.getConstructorDetails(constructorDetailsTree));
        }
    }

    /**
     * Build the signature.
     *
     * @param node the XML element that specifies which components to document
     * @param constructorDocTree the content tree to which the documentation will be added
     */
    public void buildSignature(XMLNode node, Content constructorDocTree) {
        constructorDocTree.addContent(
                writer.getSignature(
                (ConstructorDoc) constructors.get(currentConstructorIndex)));
    }

    /**
     * Build the deprecation information.
     *
     * @param node the XML element that specifies which components to document
     * @param constructorDocTree the content tree to which the documentation will be added
     */
    public void buildDeprecationInfo(XMLNode node, Content constructorDocTree) {
        writer.addDeprecated(
                (ConstructorDoc) constructors.get(currentConstructorIndex), constructorDocTree);
    }

    /**
     * Build the comments for the constructor.  Do nothing if
     * {@link Configuration#nocomment} is set to true.
     *
     * @param node the XML element that specifies which components to document
     * @param constructorDocTree the content tree to which the documentation will be added
     */
    public void buildConstructorComments(XMLNode node, Content constructorDocTree) {
        if (!configuration.nocomment) {
            writer.addComments(
                    (ConstructorDoc) constructors.get(currentConstructorIndex),
                    constructorDocTree);
        }
    }

    /**
     * Build the tag information.
     *
     * @param node the XML element that specifies which components to document
     * @param constructorDocTree the content tree to which the documentation will be added
     */
    public void buildTagInfo(XMLNode node, Content constructorDocTree) {
        writer.addTags((ConstructorDoc) constructors.get(currentConstructorIndex),
                constructorDocTree);
    }
}
