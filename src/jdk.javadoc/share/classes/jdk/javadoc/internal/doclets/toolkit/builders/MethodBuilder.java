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

import java.util.*;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import jdk.javadoc.internal.doclets.toolkit.BaseOptions;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.DocletException;
import jdk.javadoc.internal.doclets.toolkit.MethodWriter;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder;

import static jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable.Kind.*;

/**
 * Builds documentation for a method.
 */
public class MethodBuilder extends AbstractMemberBuilder {

    /**
     * The index of the current field that is being documented at this point
     * in time.
     */
    private ExecutableElement currentMethod;

    /**
     * The writer to output the method documentation.
     */
    private final MethodWriter writer;

    /**
     * The methods being documented.
     */
    private final List<? extends Element> methods;


    /**
     * Construct a new MethodBuilder.
     *
     * @param context       the build context.
     * @param typeElement the class whose members are being documented.
     * @param writer the doclet specific writer.
     */
    private MethodBuilder(Context context,
            TypeElement typeElement,
            MethodWriter writer) {
        super(context, typeElement);
        this.writer = Objects.requireNonNull(writer);
        methods = getVisibleMembers(METHODS);
    }

    /**
     * Construct a new MethodBuilder.
     *
     * @param context       the build context.
     * @param typeElement the class whose members are being documented.
     * @param writer the doclet specific writer.
     *
     * @return an instance of a MethodBuilder.
     */
    public static MethodBuilder getInstance(Context context,
            TypeElement typeElement, MethodWriter writer) {
        return new MethodBuilder(context, typeElement, writer);
    }

    @Override
    public boolean hasMembersToDocument() {
        return !methods.isEmpty();
    }

    @Override
    public void build(Content target) throws DocletException {
        buildMethodDoc(target);
    }

    /**
     * Build the method documentation.
     *
     * @param detailsList the content to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildMethodDoc(Content detailsList) throws DocletException {
        if (hasMembersToDocument()) {
            Content methodDetailsHeader = writer.getMethodDetailsHeader(detailsList);
            Content memberList = writer.getMemberList();

            for (Element method : methods) {
                currentMethod = (ExecutableElement)method;
                Content methodContent = writer.getMethodHeader(currentMethod);

                buildSignature(methodContent);
                buildDeprecationInfo(methodContent);
                buildPreviewInfo(methodContent);
                buildMethodComments(methodContent);
                buildTagInfo(methodContent);

                memberList.add(writer.getMemberListItem(methodContent));
            }
            Content methodDetails = writer.getMethodDetails(methodDetailsHeader, memberList);
            detailsList.add(methodDetails);
        }
    }

    /**
     * Build the signature.
     *
     * @param methodContent the content to which the documentation will be added
     */
    protected void buildSignature(Content methodContent) {
        methodContent.add(writer.getSignature(currentMethod));
    }

    /**
     * Build the deprecation information.
     *
     * @param methodContent the content to which the documentation will be added
     */
    protected void buildDeprecationInfo(Content methodContent) {
        writer.addDeprecated(currentMethod, methodContent);
    }

    /**
     * Build the preview information.
     *
     * @param methodContent the content to which the documentation will be added
     */
    protected void buildPreviewInfo(Content methodContent) {
        writer.addPreview(currentMethod, methodContent);
    }

    /**
     * Build the comments for the method.  Do nothing if
     * {@link BaseOptions#noComment()} is set to true.
     *
     * @param methodContent the content to which the documentation will be added
     */
    protected void buildMethodComments(Content methodContent) {
        if (!options.noComment()) {
            assert utils.isMethod(currentMethod); // not all executables are methods
            ExecutableElement method = currentMethod;
            if (utils.getFullBody(currentMethod).isEmpty()) {
                DocFinder.Output docs = DocFinder.search(configuration,
                        new DocFinder.Input(utils, currentMethod));
                if (!docs.inlineTags.isEmpty())
                    method = (ExecutableElement) docs.holder;
            }
            TypeMirror containingType = method.getEnclosingElement().asType();
            writer.addComments(containingType, method, methodContent);
        }
    }

    /**
     * Build the tag information.
     *
     * @param methodContent the content to which the documentation will be added
     */
    protected void buildTagInfo(Content methodContent) {
        writer.addTags(currentMethod, methodContent);
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
