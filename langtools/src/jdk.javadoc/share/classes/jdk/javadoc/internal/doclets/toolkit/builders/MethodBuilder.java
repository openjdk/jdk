/*
 * Copyright (c) 2003, 2017, Oracle and/or its affiliates. All rights reserved.
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

import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.DocletException;
import jdk.javadoc.internal.doclets.toolkit.MethodWriter;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberMap;


/**
 * Builds documentation for a method.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 */
public class MethodBuilder extends AbstractMemberBuilder {

    /**
     * The index of the current field that is being documented at this point
     * in time.
     */
    private ExecutableElement currentMethod;

    /**
     * The class whose methods are being documented.
     */
    private final TypeElement typeElement;

    /**
     * The visible methods for the given class.
     */
    private final VisibleMemberMap visibleMemberMap;

    /**
     * The writer to output the method documentation.
     */
    private final MethodWriter writer;

    /**
     * The methods being documented.
     */
    private final List<Element> methods;


    /**
     * Construct a new MethodBuilder.
     *
     * @param context       the build context.
     * @param typeElement the class whoses members are being documented.
     * @param writer the doclet specific writer.
     */
    private MethodBuilder(Context context,
            TypeElement typeElement,
            MethodWriter writer) {
        super(context);
        this.typeElement = typeElement;
        this.writer = writer;
        visibleMemberMap = configuration.getVisibleMemberMap(typeElement,
                VisibleMemberMap.Kind.METHODS);
        methods = visibleMemberMap.getLeafMembers();
    }

    /**
     * Construct a new MethodBuilder.
     *
     * @param context       the build context.
     * @param typeElement the class whoses members are being documented.
     * @param writer the doclet specific writer.
     *
     * @return an instance of a MethodBuilder.
     */
    public static MethodBuilder getInstance(Context context,
            TypeElement typeElement, MethodWriter writer) {
        return new MethodBuilder(context, typeElement, writer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "MethodDetails";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasMembersToDocument() {
        return !methods.isEmpty();
    }

    /**
     * Build the method documentation.
     *
     * @param node the XML element that specifies which components to document
     * @param memberDetailsTree the content tree to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    public void buildMethodDoc(XMLNode node, Content memberDetailsTree) throws DocletException {
        if (writer == null) {
            return;
        }
        if (hasMembersToDocument()) {
            Content methodDetailsTree = writer.getMethodDetailsTreeHeader(typeElement,
                    memberDetailsTree);

            Element lastElement = methods.get(methods.size() - 1);
            for (Element method : methods) {
                currentMethod = (ExecutableElement)method;
                Content methodDocTree = writer.getMethodDocTreeHeader(currentMethod, methodDetailsTree);
                buildChildren(node, methodDocTree);
                methodDetailsTree.addContent(writer.getMethodDoc(
                        methodDocTree, currentMethod == lastElement));
            }
            memberDetailsTree.addContent(writer.getMethodDetails(methodDetailsTree));
        }
    }

    /**
     * Build the signature.
     *
     * @param node the XML element that specifies which components to document
     * @param methodDocTree the content tree to which the documentation will be added
     */
    public void buildSignature(XMLNode node, Content methodDocTree) {
        methodDocTree.addContent(writer.getSignature(currentMethod));
    }

    /**
     * Build the deprecation information.
     *
     * @param node the XML element that specifies which components to document
     * @param methodDocTree the content tree to which the documentation will be added
     */
    public void buildDeprecationInfo(XMLNode node, Content methodDocTree) {
        writer.addDeprecated(currentMethod, methodDocTree);
    }

    /**
     * Build the comments for the method.  Do nothing if
     * {@link BaseConfiguration#nocomment} is set to true.
     *
     * @param node the XML element that specifies which components to document
     * @param methodDocTree the content tree to which the documentation will be added
     */
    public void buildMethodComments(XMLNode node, Content methodDocTree) {
        if (!configuration.nocomment) {
            ExecutableElement method = currentMethod;
            if (utils.getFullBody(currentMethod).isEmpty()) {
                DocFinder.Output docs = DocFinder.search(configuration,
                        new DocFinder.Input(utils, currentMethod));
                if (docs.inlineTags != null && !docs.inlineTags.isEmpty())
                        method = (ExecutableElement)docs.holder;
            }
            TypeMirror containingType = method.getEnclosingElement().asType();
            writer.addComments(containingType, method, methodDocTree);
        }
    }

    /**
     * Build the tag information.
     *
     * @param node the XML element that specifies which components to document
     * @param methodDocTree the content tree to which the documentation will be added
     */
    public void buildTagInfo(XMLNode node, Content methodDocTree) {
        writer.addTags(currentMethod, methodDocTree);
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
