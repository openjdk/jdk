/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.formats.html;

import java.io.*;

import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlConstants;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.MemberSummaryWriter;
import jdk.javadoc.internal.doclets.toolkit.MethodWriter;
import jdk.javadoc.internal.doclets.toolkit.util.ImplementedMethods;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

/**
 * Writes method documentation in HTML format.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Robert Field
 * @author Atul M Dambalkar
 * @author Jamie Ho (rewrite)
 * @author Bhavesh Patel (Modified)
 */
public class MethodWriterImpl extends AbstractExecutableMemberWriter
        implements MethodWriter, MemberSummaryWriter {

    /**
     * Construct a new MethodWriterImpl.
     *
     * @param writer the writer for the class that the methods belong to.
     * @param typeElement the class being documented.
     */
    public MethodWriterImpl(SubWriterHolderWriter writer, TypeElement typeElement) {
        super(writer, typeElement);
    }

    /**
     * Construct a new MethodWriterImpl.
     *
     * @param writer The writer for the class that the methods belong to.
     */
    public MethodWriterImpl(SubWriterHolderWriter writer) {
        super(writer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getMemberSummaryHeader(TypeElement typeElement, Content memberSummaryTree) {
        memberSummaryTree.addContent(HtmlConstants.START_OF_METHOD_SUMMARY);
        Content memberTree = writer.getMemberTreeHeader();
        writer.addSummaryHeader(this, typeElement, memberTree);
        return memberTree;
    }

    /**
     * {@inheritDoc}
     */
    public void addMemberTree(Content memberSummaryTree, Content memberTree) {
        writer.addMemberTree(memberSummaryTree, memberTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getMethodDetailsTreeHeader(TypeElement typeElement, Content memberDetailsTree) {
        memberDetailsTree.addContent(HtmlConstants.START_OF_METHOD_DETAILS);
        Content methodDetailsTree = writer.getMemberTreeHeader();
        methodDetailsTree.addContent(writer.getMarkerAnchor(
                SectionName.METHOD_DETAIL));
        Content heading = HtmlTree.HEADING(HtmlConstants.DETAILS_HEADING,
                writer.methodDetailsLabel);
        methodDetailsTree.addContent(heading);
        return methodDetailsTree;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getMethodDocTreeHeader(ExecutableElement method, Content methodDetailsTree) {
        String erasureAnchor;
        if ((erasureAnchor = getErasureAnchor(method)) != null) {
            methodDetailsTree.addContent(writer.getMarkerAnchor((erasureAnchor)));
        }
        methodDetailsTree.addContent(
                writer.getMarkerAnchor(writer.getAnchor(method)));
        Content methodDocTree = writer.getMemberTreeHeader();
        Content heading = new HtmlTree(HtmlConstants.MEMBER_HEADING);
        heading.addContent(name(method));
        methodDocTree.addContent(heading);
        return methodDocTree;
    }

    /**
     * Get the signature for the given method.
     *
     * @param method the method being documented.
     * @return a content object for the signature
     */
    @Override
    public Content getSignature(ExecutableElement method) {
        Content pre = new HtmlTree(HtmlTag.PRE);
        writer.addAnnotationInfo(method, pre);
        int annotationLength = pre.charCount();
        addModifiers(method, pre);
        addTypeParameters(method, pre);
        addReturnType(method, pre);
        if (configuration.linksource) {
            Content methodName = new StringContent(name(method));
            writer.addSrcLink(method, methodName, pre);
        } else {
            addName(name(method), pre);
        }
        int indent = pre.charCount() - annotationLength;
        addParameters(method, pre, indent);
        addExceptions(method, pre, indent);
        return pre;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addDeprecated(ExecutableElement method, Content methodDocTree) {
        addDeprecatedInfo(method, methodDocTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addComments(TypeMirror holderType, ExecutableElement method, Content methodDocTree) {
        TypeElement holder = utils.asTypeElement(holderType);
        if (!utils.getBody(method).isEmpty()) {
            if (holder.equals(typeElement) ||
                    !(utils.isPublic(holder) ||
                    utils.isLinkable(holder))) {
                writer.addInlineComment(method, methodDocTree);
            } else {
                Content link =
                        writer.getDocLink(LinkInfoImpl.Kind.EXECUTABLE_ELEMENT_COPY,
                        holder, method,
                        utils.isIncluded(holder)
                                ? utils.getSimpleName(holder)
                                : utils.getFullyQualifiedName(holder),
                            false);
                Content codelLink = HtmlTree.CODE(link);
                Content descfrmLabel = HtmlTree.SPAN(HtmlStyle.descfrmTypeLabel,
                        utils.isClass(holder)
                                ? writer.descfrmClassLabel
                                : writer.descfrmInterfaceLabel);
                descfrmLabel.addContent(writer.getSpace());
                descfrmLabel.addContent(codelLink);
                methodDocTree.addContent(HtmlTree.DIV(HtmlStyle.block, descfrmLabel));
                writer.addInlineComment(method, methodDocTree);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addTags(ExecutableElement method, Content methodDocTree) {
        writer.addTagsInfo(method, methodDocTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getMethodDetails(Content methodDetailsTree) {
        if (configuration.allowTag(HtmlTag.SECTION)) {
            HtmlTree htmlTree = HtmlTree.SECTION(getMemberTree(methodDetailsTree));
            return htmlTree;
        }
        return getMemberTree(methodDetailsTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getMethodDoc(Content methodDocTree,
            boolean isLastContent) {
        return getMemberTree(methodDocTree, isLastContent);
    }

    /**
     * Close the writer.
     */
    @Override
    public void close() throws IOException {
        writer.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addSummaryLabel(Content memberTree) {
        Content label = HtmlTree.HEADING(HtmlConstants.SUMMARY_HEADING,
                writer.getResource("doclet.Method_Summary"));
        memberTree.addContent(label);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTableSummary() {
        return configuration.getText("doclet.Member_Table_Summary",
                configuration.getText("doclet.Method_Summary"),
                configuration.getText("doclet.methods"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getCaption() {
        return configuration.getResource("doclet.Methods");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getSummaryTableHeader(Element member) {
        List<String> header = Arrays.asList(writer.getModifierTypeHeader(),
        configuration.getText("doclet.0_and_1",
                   configuration.getText("doclet.Method"),
                   configuration.getText("doclet.Description")));
        return header;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addSummaryAnchor(TypeElement typeElement, Content memberTree) {
        memberTree.addContent(writer.getMarkerAnchor(
                SectionName.METHOD_SUMMARY));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addInheritedSummaryAnchor(TypeElement typeElement, Content inheritedTree) {
        inheritedTree.addContent(writer.getMarkerAnchor(
                SectionName.METHODS_INHERITANCE, configuration.getClassName(typeElement)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addInheritedSummaryLabel(TypeElement typeElement, Content inheritedTree) {
        Content classLink = writer.getPreQualifiedClassLink(
                LinkInfoImpl.Kind.MEMBER, typeElement, false);
        Content label = new StringContent(utils.isClass(typeElement)
                ? configuration.getText("doclet.Methods_Inherited_From_Class")
                : configuration.getText("doclet.Methods_Inherited_From_Interface"));
        Content labelHeading = HtmlTree.HEADING(HtmlConstants.INHERITED_SUMMARY_HEADING,
                label);
        labelHeading.addContent(writer.getSpace());
        labelHeading.addContent(classLink);
        inheritedTree.addContent(labelHeading);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addSummaryType(Element member, Content tdSummaryType) {
        ExecutableElement meth = (ExecutableElement)member;
        addModifierAndType(meth, utils.getReturnType(meth), tdSummaryType);
    }

    /**
     * {@inheritDoc}
     */
    protected static void addOverridden(HtmlDocletWriter writer,
            TypeMirror overriddenType, ExecutableElement method, Content dl) {
        if (writer.configuration.nocomment) {
            return;
        }
        Utils utils = writer.configuration().utils;
        TypeElement holder = utils.getEnclosingTypeElement(method);
        if (!(utils.isPublic(holder) ||
            utils.isLinkable(holder))) {
            //This is an implementation detail that should not be documented.
            return;
        }
        if (utils.isIncluded(holder) && ! utils.isIncluded(method)) {
            //The class is included but the method is not.  That means that it
            //is not visible so don't document this.
            return;
        }
        Content label = writer.overridesLabel;
        LinkInfoImpl.Kind context = LinkInfoImpl.Kind.METHOD_OVERRIDES;

        if (method != null) {
            if (utils.isAbstract(holder) && utils.isAbstract(method)){
                //Abstract method is implemented from abstract class,
                //not overridden
                label = writer.specifiedByLabel;
                context = LinkInfoImpl.Kind.METHOD_SPECIFIED_BY;
            }
            Content dt = HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.overrideSpecifyLabel, label));
            dl.addContent(dt);
            Content overriddenTypeLink =
                    writer.getLink(new LinkInfoImpl(writer.configuration, context, overriddenType));
            Content codeOverridenTypeLink = HtmlTree.CODE(overriddenTypeLink);
            Content methlink = writer.getLink(
                    new LinkInfoImpl(writer.configuration, LinkInfoImpl.Kind.MEMBER,
                    holder)
                    .where(writer.getName(writer.getAnchor(method))).label(method.getSimpleName()));
            Content codeMethLink = HtmlTree.CODE(methlink);
            Content dd = HtmlTree.DD(codeMethLink);
            dd.addContent(writer.getSpace());
            dd.addContent(writer.getResource("doclet.in_class"));
            dd.addContent(writer.getSpace());
            dd.addContent(codeOverridenTypeLink);
            dl.addContent(dd);
        }
    }

    /**
     * {@inheritDoc}
     */
    protected static void addImplementsInfo(HtmlDocletWriter writer,
            ExecutableElement method, Content dl) {
        if (writer.configuration.nocomment) {
            return;
        }
        Utils utils = writer.utils;
        ImplementedMethods implementedMethodsFinder =
                new ImplementedMethods(method, writer.configuration);
        SortedSet<ExecutableElement> implementedMethods =
                new TreeSet<>(utils.makeOverrideUseComparator());
        implementedMethods.addAll(implementedMethodsFinder.build());
        for (ExecutableElement implementedMeth : implementedMethods) {
            TypeMirror intfac = implementedMethodsFinder.getMethodHolder(implementedMeth);
            intfac = utils.getDeclaredType(utils.getEnclosingTypeElement(method), intfac);
            Content intfaclink = writer.getLink(new LinkInfoImpl(
                    writer.configuration, LinkInfoImpl.Kind.METHOD_SPECIFIED_BY, intfac));
            Content codeIntfacLink = HtmlTree.CODE(intfaclink);
            Content dt = HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.overrideSpecifyLabel, writer.specifiedByLabel));
            dl.addContent(dt);
            Content methlink = writer.getDocLink(
                    LinkInfoImpl.Kind.MEMBER, implementedMeth,
                    implementedMeth.getSimpleName(), false);
            Content codeMethLink = HtmlTree.CODE(methlink);
            Content dd = HtmlTree.DD(codeMethLink);
            dd.addContent(writer.getSpace());
            dd.addContent(writer.getResource("doclet.in_interface"));
            dd.addContent(writer.getSpace());
            dd.addContent(codeIntfacLink);
            dl.addContent(dd);
        }
    }

    /**
     * Add the return type.
     *
     * @param method the method being documented.
     * @param htmltree the content tree to which the return type will be added
     */
    protected void addReturnType(ExecutableElement method, Content htmltree) {
        TypeMirror type = utils.getReturnType(method);
        if (type != null) {
            Content linkContent = writer.getLink(
                    new LinkInfoImpl(configuration, LinkInfoImpl.Kind.RETURN_TYPE, type));
            htmltree.addContent(linkContent);
            htmltree.addContent(writer.getSpace());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Content getNavSummaryLink(TypeElement typeElement, boolean link) {
        if (link) {
            if (typeElement == null) {
                return writer.getHyperLink(
                        SectionName.METHOD_SUMMARY,
                        writer.getResource("doclet.navMethod"));
            } else {
                return writer.getHyperLink(
                        SectionName.METHODS_INHERITANCE,
                        configuration.getClassName(typeElement), writer.getResource("doclet.navMethod"));
            }
        } else {
            return writer.getResource("doclet.navMethod");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addNavDetailLink(boolean link, Content liNav) {
        if (link) {
            liNav.addContent(writer.getHyperLink(
                    SectionName.METHOD_DETAIL, writer.getResource("doclet.navMethod")));
        } else {
            liNav.addContent(writer.getResource("doclet.navMethod"));
        }
    }
}
