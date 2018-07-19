/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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

import jdk.javadoc.internal.doclets.formats.html.markup.Table;
import jdk.javadoc.internal.doclets.formats.html.markup.TableHeader;

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
import jdk.javadoc.internal.doclets.toolkit.util.Utils;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable;

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
    @Override
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
        methodDetailsTree.addContent(links.createAnchor(SectionName.METHOD_DETAIL));
        Content heading = HtmlTree.HEADING(HtmlConstants.DETAILS_HEADING,
                contents.methodDetailLabel);
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
            methodDetailsTree.addContent(links.createAnchor((erasureAnchor)));
        }
        methodDetailsTree.addContent(links.createAnchor(writer.getAnchor(method)));
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
        HtmlTree pre = new HtmlTree(HtmlTag.PRE);
        pre.setStyle(HtmlStyle.methodSignature);
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
        if (!utils.getFullBody(method).isEmpty()) {
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
                                ? contents.descfrmClassLabel
                                : contents.descfrmInterfaceLabel);
                descfrmLabel.addContent(Contents.SPACE);
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
     * {@inheritDoc}
     */
    @Override
    public void addSummaryLabel(Content memberTree) {
        Content label = HtmlTree.HEADING(HtmlConstants.SUMMARY_HEADING,
                contents.methodSummary);
        memberTree.addContent(label);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TableHeader getSummaryTableHeader(Element member) {
        return new TableHeader(contents.modifierAndTypeLabel, contents.methodLabel,
                contents.descriptionLabel);
    }

    @Override
    protected Table createSummaryTable() {
        String summary =  resources.getText("doclet.Member_Table_Summary",
                resources.getText("doclet.Method_Summary"),
                resources.getText("doclet.methods"));

        return new Table(configuration.htmlVersion, HtmlStyle.memberSummary)
                .setSummary(summary)
                .setHeader(getSummaryTableHeader(typeElement))
                .setRowScopeColumn(1)
                .setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colSecond, HtmlStyle.colLast)
                .setDefaultTab(resources.getText("doclet.All_Methods"))
                .addTab(resources.getText("doclet.Static_Methods"), utils::isStatic)
                .addTab(resources.getText("doclet.Instance_Methods"), e -> !utils.isStatic(e))
                .addTab(resources.getText("doclet.Abstract_Methods"), utils::isAbstract)
                .addTab(resources.getText("doclet.Concrete_Methods"),
                        e -> !utils.isAbstract(e) && !utils.isInterface(e.getEnclosingElement()))
                .addTab(resources.getText("doclet.Default_Methods"), utils::isDefault)
                .addTab(resources.getText("doclet.Deprecated_Methods"),
                        e -> utils.isDeprecated(e) || utils.isDeprecated(typeElement))
                .setTabScript(i -> "show(" + i + ");")
                .setUseTBody(false)
                .setPutIdFirst(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addSummaryAnchor(TypeElement typeElement, Content memberTree) {
        memberTree.addContent(links.createAnchor(SectionName.METHOD_SUMMARY));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addInheritedSummaryAnchor(TypeElement typeElement, Content inheritedTree) {
        inheritedTree.addContent(links.createAnchor(
                SectionName.METHODS_INHERITANCE, configuration.getClassName(typeElement)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addInheritedSummaryLabel(TypeElement typeElement, Content inheritedTree) {
        Content classLink = writer.getPreQualifiedClassLink(
                LinkInfoImpl.Kind.MEMBER, typeElement, false);
        Content label;
        if (configuration.summarizeOverriddenMethods) {
            label = new StringContent(utils.isClass(typeElement)
                    ? configuration.getText("doclet.Methods_Declared_In_Class")
                    : configuration.getText("doclet.Methods_Declared_In_Interface"));
        } else {
            label = new StringContent(utils.isClass(typeElement)
                    ? configuration.getText("doclet.Methods_Inherited_From_Class")
                    : configuration.getText("doclet.Methods_Inherited_From_Interface"));
        }
        Content labelHeading = HtmlTree.HEADING(HtmlConstants.INHERITED_SUMMARY_HEADING,
                label);
        labelHeading.addContent(Contents.SPACE);
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
        Utils utils = writer.utils;
        Contents contents = writer.contents;
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
        Content label = contents.overridesLabel;
        LinkInfoImpl.Kind context = LinkInfoImpl.Kind.METHOD_OVERRIDES;

        if (method != null) {
            if (utils.isAbstract(holder) && utils.isAbstract(method)){
                //Abstract method is implemented from abstract class,
                //not overridden
                label = contents.specifiedByLabel;
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
                    .where(writer.links.getName(writer.getAnchor(method))).label(method.getSimpleName()));
            Content codeMethLink = HtmlTree.CODE(methlink);
            Content dd = HtmlTree.DD(codeMethLink);
            dd.addContent(Contents.SPACE);
            dd.addContent(writer.contents.inClass);
            dd.addContent(Contents.SPACE);
            dd.addContent(codeOverridenTypeLink);
            dl.addContent(dd);
        }
    }

    /**
     * {@inheritDoc}
     */
    protected static void addImplementsInfo(HtmlDocletWriter writer,
            ExecutableElement method, Content dl) {
        Utils utils = writer.utils;
        if (utils.isStatic(method) || writer.configuration.nocomment) {
            return;
        }
        Contents contents = writer.contents;
        VisibleMemberTable vmt = writer.configuration
                .getVisibleMemberTable(utils.getEnclosingTypeElement(method));
        SortedSet<ExecutableElement> implementedMethods =
                new TreeSet<>(utils.makeOverrideUseComparator());
        implementedMethods.addAll(vmt.getImplementedMethods(method));
        for (ExecutableElement implementedMeth : implementedMethods) {
            TypeMirror intfac = vmt.getImplementedMethodHolder(method, implementedMeth);
            intfac = utils.getDeclaredType(utils.getEnclosingTypeElement(method), intfac);
            Content intfaclink = writer.getLink(new LinkInfoImpl(
                    writer.configuration, LinkInfoImpl.Kind.METHOD_SPECIFIED_BY, intfac));
            Content codeIntfacLink = HtmlTree.CODE(intfaclink);
            Content dt = HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.overrideSpecifyLabel, contents.specifiedByLabel));
            dl.addContent(dt);
            Content methlink = writer.getDocLink(
                    LinkInfoImpl.Kind.MEMBER, implementedMeth,
                    implementedMeth.getSimpleName(), false);
            Content codeMethLink = HtmlTree.CODE(methlink);
            Content dd = HtmlTree.DD(codeMethLink);
            dd.addContent(Contents.SPACE);
            dd.addContent(contents.inInterface);
            dd.addContent(Contents.SPACE);
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
            htmltree.addContent(Contents.SPACE);
        }
    }
}
