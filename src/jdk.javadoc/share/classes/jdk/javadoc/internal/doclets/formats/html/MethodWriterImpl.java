/*
 * Copyright (c) 1997, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.SortedSet;
import java.util.TreeSet;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.Entity;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.formats.html.markup.Table;
import jdk.javadoc.internal.doclets.formats.html.markup.TableHeader;
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

    @Override
    public Content getMemberSummaryHeader(TypeElement typeElement, Content memberSummaryTree) {
        memberSummaryTree.add(MarkerComments.START_OF_METHOD_SUMMARY);
        Content memberTree = new ContentBuilder();
        writer.addSummaryHeader(this, memberTree);
        return memberTree;
    }

    @Override
    public void addSummary(Content summariesList, Content content) {
        writer.addSummary(HtmlStyle.methodSummary,
                SectionName.METHOD_SUMMARY, summariesList, content);
    }

    @Override
    public Content getMethodDetailsTreeHeader(Content memberDetailsTree) {
        memberDetailsTree.add(MarkerComments.START_OF_METHOD_DETAILS);
        Content methodDetailsTree = new ContentBuilder();
        Content heading = HtmlTree.HEADING(Headings.TypeDeclaration.DETAILS_HEADING,
                contents.methodDetailLabel);
        methodDetailsTree.add(heading);
        return methodDetailsTree;
    }

    @Override
    public Content getMethodDocTreeHeader(ExecutableElement method) {
        String erasureAnchor;
        Content methodDocTree = new ContentBuilder();
        HtmlTree heading = HtmlTree.HEADING(Headings.TypeDeclaration.MEMBER_HEADING,
                new StringContent(name(method)));
        if ((erasureAnchor = getErasureAnchor(method)) != null) {
            heading.setId(erasureAnchor);
        }
        methodDocTree.add(heading);
        return HtmlTree.SECTION(HtmlStyle.detail, methodDocTree)
                .setId(links.getAnchor(method));
    }

    /**
     * Get the signature for the given method.
     *
     * @param method the method being documented.
     * @return a content object for the signature
     */
    @Override
    public Content getSignature(ExecutableElement method) {
        return new Signatures.MemberSignature(method, this)
                .setTypeParameters(getTypeParameters(method))
                .setReturnType(getReturnType(method))
                .setParameters(getParameters(method, true))
                .setExceptions(getExceptions(method))
                .setAnnotations(writer.getAnnotationInfo(method, true))
                .toContent();
    }

    @Override
    public void addDeprecated(ExecutableElement method, Content methodDocTree) {
        addDeprecatedInfo(method, methodDocTree);
    }

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
                Content codeLink = HtmlTree.CODE(link);
                Content descfrmLabel = HtmlTree.SPAN(HtmlStyle.descfrmTypeLabel,
                        utils.isClass(holder)
                                ? contents.descfrmClassLabel
                                : contents.descfrmInterfaceLabel);
                descfrmLabel.add(Entity.NO_BREAK_SPACE);
                descfrmLabel.add(codeLink);
                methodDocTree.add(HtmlTree.DIV(HtmlStyle.block, descfrmLabel));
                writer.addInlineComment(method, methodDocTree);
            }
        }
    }

    @Override
    public void addTags(ExecutableElement method, Content methodDocTree) {
        writer.addTagsInfo(method, methodDocTree);
    }

    @Override
    public Content getMethodDetails(Content methodDetailsTreeHeader, Content methodDetailsTree) {
        Content methodDetails = new ContentBuilder(methodDetailsTreeHeader, methodDetailsTree);
        return getMemberTree(HtmlTree.SECTION(HtmlStyle.methodDetails, methodDetails)
                .setId(SectionName.METHOD_DETAIL.getName()));
    }

    @Override
    public void addSummaryLabel(Content memberTree) {
        Content label = HtmlTree.HEADING(Headings.TypeDeclaration.SUMMARY_HEADING,
                contents.methodSummary);
        memberTree.add(label);
    }

    @Override
    public TableHeader getSummaryTableHeader(Element member) {
        return new TableHeader(contents.modifierAndTypeLabel, contents.methodLabel,
                contents.descriptionLabel);
    }

    @Override
    protected Table createSummaryTable() {
        return new Table(HtmlStyle.summaryTable)
                .setHeader(getSummaryTableHeader(typeElement))
                .setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colSecond, HtmlStyle.colLast)
                .setId("method-summary-table")
                .setDefaultTab(resources.getText("doclet.All_Methods"))
                .addTab(resources.getText("doclet.Static_Methods"), utils::isStatic)
                .addTab(resources.getText("doclet.Instance_Methods"), e -> !utils.isStatic(e))
                .addTab(resources.getText("doclet.Abstract_Methods"), utils::isAbstract)
                .addTab(resources.getText("doclet.Concrete_Methods"),
                        e -> !utils.isAbstract(e) && !utils.isInterface(e.getEnclosingElement()))
                .addTab(resources.getText("doclet.Default_Methods"), utils::isDefault)
                .addTab(resources.getText("doclet.Deprecated_Methods"),
                        e -> utils.isDeprecated(e) || utils.isDeprecated(typeElement));
    }

    @Override
    public void addInheritedSummaryLabel(TypeElement typeElement, Content inheritedTree) {
        Content classLink = writer.getPreQualifiedClassLink(
                LinkInfoImpl.Kind.MEMBER, typeElement, false);
        Content label;
        if (options.summarizeOverriddenMethods()) {
            label = new StringContent(utils.isClass(typeElement)
                    ? resources.getText("doclet.Methods_Declared_In_Class")
                    : resources.getText("doclet.Methods_Declared_In_Interface"));
        } else {
            label = new StringContent(utils.isClass(typeElement)
                    ? resources.getText("doclet.Methods_Inherited_From_Class")
                    : resources.getText("doclet.Methods_Inherited_From_Interface"));
        }
        HtmlTree labelHeading = HtmlTree.HEADING(Headings.TypeDeclaration.INHERITED_SUMMARY_HEADING,
                label);
        labelHeading.setId(SectionName.METHODS_INHERITANCE.getName()
                + links.getName(configuration.getClassName(typeElement)));
        labelHeading.add(Entity.NO_BREAK_SPACE);
        labelHeading.add(classLink);
        inheritedTree.add(labelHeading);
    }

    @Override
    protected void addSummaryType(Element member, Content tdSummaryType) {
        ExecutableElement meth = (ExecutableElement)member;
        addModifierAndType(meth, utils.getReturnType(typeElement, meth), tdSummaryType);
    }

    /**
     * Adds "overrides" or "specified by" information about a method (if appropriate)
     * into a definition list.
     *
     * @param writer         the writer for the element
     * @param overriddenType the superclass
     * @param method         the method
     * @param dl             the list in which to add the information.
     */
    protected static void addOverridden(HtmlDocletWriter writer,
                                        TypeMirror overriddenType,
                                        ExecutableElement method,
                                        Content dl) {
        if (writer.options.noComment()) {
            return;
        }
        Utils utils = writer.utils;
        TypeElement holder = utils.getEnclosingTypeElement(method);
        if (!(utils.isPublic(holder) || utils.isLinkable(holder))) {
            //This is an implementation detail that should not be documented.
            return;
        }
        if (utils.isIncluded(holder) && !utils.isIncluded(method)) {
            //The class is included but the method is not.  That means that it
            //is not visible so don't document this.
            return;
        }

        if (method != null) {
            Contents contents = writer.contents;
            Content label;
            LinkInfoImpl.Kind context;
            if (utils.isAbstract(holder) && utils.isAbstract(method)){
                //Abstract method is implemented from abstract class,
                //not overridden
                label = contents.specifiedByLabel;
                context = LinkInfoImpl.Kind.METHOD_SPECIFIED_BY;
            } else {
                label = contents.overridesLabel;
                context = LinkInfoImpl.Kind.METHOD_OVERRIDES;
            }
            dl.add(HtmlTree.DT(label));
            Content overriddenTypeLink =
                    writer.getLink(new LinkInfoImpl(writer.configuration, context, overriddenType));
            Content codeOverriddenTypeLink = HtmlTree.CODE(overriddenTypeLink);
            Content methlink = writer.getLink(
                    new LinkInfoImpl(writer.configuration, LinkInfoImpl.Kind.MEMBER, holder)
                            .where(writer.links.getAnchor(method))
                            .label(method.getSimpleName()));
            Content codeMethLink = HtmlTree.CODE(methlink);
            Content dd = HtmlTree.DD(codeMethLink);
            dd.add(Entity.NO_BREAK_SPACE);
            dd.add(contents.inClass);
            dd.add(Entity.NO_BREAK_SPACE);
            dd.add(codeOverriddenTypeLink);
            dl.add(dd);
        }
    }

    /**
     * Adds "implements" information for a method (if appropriate)
     * into a definition list.
     *
     * @param writer the writer for the method
     * @param method the method
     * @param dl     the definition list
     */
    protected static void addImplementsInfo(HtmlDocletWriter writer,
                                            ExecutableElement method,
                                            Content dl) {
        Utils utils = writer.utils;
        if (utils.isStatic(method) || writer.options.noComment()) {
            return;
        }
        Contents contents = writer.contents;
        VisibleMemberTable vmt = writer.configuration
                .getVisibleMemberTable(utils.getEnclosingTypeElement(method));
        SortedSet<ExecutableElement> implementedMethods =
                new TreeSet<>(utils.comparators.makeOverrideUseComparator());
        implementedMethods.addAll(vmt.getImplementedMethods(method));
        for (ExecutableElement implementedMeth : implementedMethods) {
            TypeMirror intfac = vmt.getImplementedMethodHolder(method, implementedMeth);
            intfac = utils.getDeclaredType(utils.getEnclosingTypeElement(method), intfac);
            Content intfaclink = writer.getLink(new LinkInfoImpl(
                    writer.configuration, LinkInfoImpl.Kind.METHOD_SPECIFIED_BY, intfac));
            Content codeIntfacLink = HtmlTree.CODE(intfaclink);
            dl.add(HtmlTree.DT(contents.specifiedByLabel));
            Content methlink = writer.getDocLink(
                    LinkInfoImpl.Kind.MEMBER, implementedMeth,
                    implementedMeth.getSimpleName(), false);
            Content codeMethLink = HtmlTree.CODE(methlink);
            Content dd = HtmlTree.DD(codeMethLink);
            dd.add(Entity.NO_BREAK_SPACE);
            dd.add(contents.inInterface);
            dd.add(Entity.NO_BREAK_SPACE);
            dd.add(codeIntfacLink);
            dl.add(dd);
        }
    }

    /**
     * Get the return type for the given method.
     *
     * @param method the method being documented.
     * @return content containing the return type
     */
    protected Content getReturnType(ExecutableElement method) {
        TypeMirror type = utils.getReturnType(typeElement, method);
        if (type != null) {
            return writer.getLink(new LinkInfoImpl(configuration, LinkInfoImpl.Kind.RETURN_TYPE, type));
        }
        return new ContentBuilder();
    }

    @Override
    public Content getMemberTreeHeader(){
        return writer.getMemberTreeHeader();
    }
}
