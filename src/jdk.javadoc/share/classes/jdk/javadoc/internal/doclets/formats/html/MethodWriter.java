/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.Entity;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlId;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Text;
import jdk.javadoc.internal.doclets.toolkit.BaseOptions;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable;

/**
 * Writes method documentation in HTML format.
 */
public class MethodWriter extends AbstractExecutableMemberWriter {

    /**
     * The index of the current field that is being documented at this point
     * in time.
     */
    private ExecutableElement currentMethod;

    /**
     * Construct a new MethodWriterImpl.
     *
     * @param writer the writer for the class that the methods belong to.\
     */
    public MethodWriter(ClassWriter writer) {
        super(writer, writer.typeElement, VisibleMemberTable.Kind.METHODS);
    }

    /**
     * Construct a new MethodWriterImpl.
     *
     * @param writer the writer for the class that the methods belong to.
     * @param typeElement the class
     */
    public MethodWriter(SubWriterHolderWriter writer, TypeElement typeElement) {
        super(writer, typeElement, VisibleMemberTable.Kind.METHODS);
    }

    /**
     * Construct a new MethodWriterImpl.
     *
     * @param writer The writer for the class that the methods belong to.
     */
    // used in ClassUseWriter and SummaryUseWriter
    public MethodWriter(SubWriterHolderWriter writer) {
        super(writer);
    }

    @Override
    public void buildDetails(Content target) {
        buildMethodDoc(target);
    }

    /**
     * Build the method documentation.
     *
     * @param detailsList the content to which the documentation will be added
     */
    protected void buildMethodDoc(Content detailsList) {
        var methods = getVisibleMembers(VisibleMemberTable.Kind.METHODS);
        if (!methods.isEmpty()) {
            Content methodDetailsHeader = getMethodDetailsHeader(detailsList);
            Content memberList = writer.getMemberList();
            writer.tableOfContents.addLink(HtmlIds.METHOD_DETAIL, contents.methodDetailLabel);
            writer.tableOfContents.pushNestedList();

            for (Element method : methods) {
                currentMethod = (ExecutableElement)method;
                Content methodContent = getMethodHeader(currentMethod);
                Content div = HtmlTree.DIV(HtmlStyle.horizontalScroll);
                buildSignature(div);
                buildDeprecationInfo(div);
                buildPreviewInfo(div);
                buildRestrictedInfo(div);
                buildMethodComments(div);
                buildTagInfo(div);
                methodContent.add(div);
                memberList.add(writer.getMemberListItem(methodContent));
                writer.tableOfContents.addLink(htmlIds.forMember(currentMethod),
                        Text.of(utils.getSimpleName(method)
                                + utils.makeSignature(currentMethod, typeElement, false, true)));
            }
            Content methodDetails = getMethodDetails(methodDetailsHeader, memberList);
            detailsList.add(methodDetails);
            writer.tableOfContents.popNestedList();
        }
    }

    @Override
    protected void buildSignature(Content target) {
        target.add(getSignature(currentMethod));
    }

    @Override
    protected void buildDeprecationInfo(Content target) {
        addDeprecated(currentMethod, target);
    }

    @Override
    protected void buildPreviewInfo(Content target) {
        addPreview(currentMethod, target);
    }

    /**
     * Builds the restricted method info.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildRestrictedInfo(Content target) {
        addRestricted(currentMethod, target);
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
            var docFinder = utils.docFinder();
            Optional<ExecutableElement> r = docFinder.search(currentMethod,
                    m -> DocFinder.Result.fromOptional(utils.getFullBody(m).isEmpty() ? Optional.empty() : Optional.of(m))).toOptional();
            ExecutableElement method = r.orElse(currentMethod);
            TypeMirror containingType = method.getEnclosingElement().asType();
            addComments(containingType, method, methodContent);
        }
    }

    /**
     * Build the tag information.
     *
     * @param methodContent the content to which the documentation will be added
     */
    protected void buildTagInfo(Content methodContent) {
        addTags(currentMethod, methodContent);
    }

    @Override
    public Content getMemberSummaryHeader(Content target) {
        target.add(MarkerComments.START_OF_METHOD_SUMMARY);
        Content memberContent = new ContentBuilder();
        writer.addSummaryHeader(this, memberContent);
        return memberContent;
    }

    @Override
    public void buildSummary(Content summariesList, Content content) {
        writer.addSummary(HtmlStyle.methodSummary,
                HtmlIds.METHOD_SUMMARY, summariesList, content);
    }

    protected Content getMethodDetailsHeader(Content content) {
        content.add(MarkerComments.START_OF_METHOD_DETAILS);
        Content methodDetailsContent = new ContentBuilder();
        var heading = HtmlTree.HEADING(Headings.TypeDeclaration.DETAILS_HEADING,
                contents.methodDetailLabel);
        methodDetailsContent.add(heading);
        return methodDetailsContent;
    }

    protected Content getMethodHeader(ExecutableElement method) {
        Content content = new ContentBuilder();
        var heading = HtmlTree.HEADING(Headings.TypeDeclaration.MEMBER_HEADING,
                Text.of(name(method)));
        HtmlId erasureAnchor;
        if ((erasureAnchor = htmlIds.forErasure(method)) != null) {
            heading.setId(erasureAnchor);
        }
        content.add(heading);
        return HtmlTree.SECTION(HtmlStyle.detail, content)
                .setId(htmlIds.forMember(method));
    }

    protected Content getSignature(ExecutableElement method) {
        return new Signatures.MemberSignature(method, this)
                .setTypeParameters(getTypeParameters(method))
                .setReturnType(getReturnType(method))
                .setParameters(getParameters(method, true))
                .setExceptions(getExceptions(method))
                .setAnnotations(writer.getAnnotationInfo(method, true))
                .toContent();
    }

    protected void addDeprecated(ExecutableElement method, Content methodContent) {
        addDeprecatedInfo(method, methodContent);
    }

    protected void addPreview(ExecutableElement method, Content content) {
        addPreviewInfo(method, content);
    }

    protected void addRestricted(ExecutableElement method, Content content) {
        addRestrictedInfo(method, content);
    }

    protected void addComments(TypeMirror holderType, ExecutableElement method, Content methodContent) {
        TypeElement holder = utils.asTypeElement(holderType);
        if (!utils.getFullBody(method).isEmpty()) {
            if (holder.equals(typeElement) ||
                    !(utils.isPublic(holder) ||
                    utils.isLinkable(holder))) {
                writer.addInlineComment(method, methodContent);
            } else {
                if (!utils.hasHiddenTag(holder) && !utils.hasHiddenTag(method)) {
                    Content link =
                            writer.getDocLink(HtmlLinkInfo.Kind.PLAIN,
                                    holder, method,
                                    utils.isIncluded(holder)
                                            ? utils.getSimpleName(holder)
                                            : utils.getFullyQualifiedName(holder));
                    var codeLink = HtmlTree.CODE(link);
                    var descriptionFromTypeLabel = HtmlTree.SPAN(HtmlStyle.descriptionFromTypeLabel,
                            utils.isClass(holder)
                                    ? contents.descriptionFromClassLabel
                                    : contents.descriptionFromInterfaceLabel);
                    descriptionFromTypeLabel.add(Entity.NO_BREAK_SPACE);
                    descriptionFromTypeLabel.add(codeLink);
                    methodContent.add(HtmlTree.DIV(HtmlStyle.block, descriptionFromTypeLabel));
                }
                writer.addInlineComment(method, methodContent);
            }
        }
    }

    protected void addTags(ExecutableElement method, Content methodContent) {
        writer.addTagsInfo(method, methodContent);
    }

    protected Content getMethodDetails(Content methodDetailsHeader, Content methodDetails) {
        Content c = new ContentBuilder(methodDetailsHeader, methodDetails);
        return getMember(HtmlTree.SECTION(HtmlStyle.methodDetails, c)
                .setId(HtmlIds.METHOD_DETAIL));
    }

    @Override
    public void addSummaryLabel(Content content) {
        var label = HtmlTree.HEADING(Headings.TypeDeclaration.SUMMARY_HEADING,
                contents.methodSummary);
        content.add(label);
    }

    @Override
    public TableHeader getSummaryTableHeader(Element member) {
        return new TableHeader(contents.modifierAndTypeLabel, contents.methodLabel,
                contents.descriptionLabel);
    }

    @Override
    protected Table<Element> createSummaryTable() {
        return new Table<Element>(HtmlStyle.summaryTable)
                .setHeader(getSummaryTableHeader(typeElement))
                .setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colSecond, HtmlStyle.colLast)
                .setId(HtmlIds.METHOD_SUMMARY_TABLE)
                .setDefaultTab(contents.getContent("doclet.All_Methods"))
                .addTab(contents.getContent("doclet.Static_Methods"), utils::isStatic)
                .addTab(contents.getContent("doclet.Instance_Methods"), e -> !utils.isStatic(e))
                .addTab(contents.getContent("doclet.Abstract_Methods"), utils::isAbstract)
                .addTab(contents.getContent("doclet.Concrete_Methods"),
                        e -> !utils.isAbstract(e) && !utils.isPlainInterface(e.getEnclosingElement()))
                .addTab(contents.getContent("doclet.Default_Methods"), utils::isDefault)
                .addTab(contents.getContent("doclet.Deprecated_Methods"),
                        e -> utils.isDeprecated(e) || utils.isDeprecated(typeElement));
    }

    @Override
    public void addInheritedSummaryLabel(TypeElement typeElement, Content content) {
        Content classLink = writer.getPreQualifiedClassLink(
                HtmlLinkInfo.Kind.PLAIN, typeElement);
        Content label;
        if (options.summarizeOverriddenMethods()) {
            label = Text.of(utils.isClass(typeElement)
                    ? resources.getText("doclet.Methods_Declared_In_Class")
                    : resources.getText("doclet.Methods_Declared_In_Interface"));
        } else {
            label = Text.of(utils.isClass(typeElement)
                    ? resources.getText("doclet.Methods_Inherited_From_Class")
                    : resources.getText("doclet.Methods_Inherited_From_Interface"));
        }
        var labelHeading = HtmlTree.HEADING(Headings.TypeDeclaration.INHERITED_SUMMARY_HEADING,
                label);
        labelHeading.setId(htmlIds.forInheritedMethods(typeElement));
        labelHeading.add(Entity.NO_BREAK_SPACE);
        labelHeading.add(classLink);
        content.add(labelHeading);
    }

    @Override
    protected void addSummaryType(Element member, Content content) {
        ExecutableElement meth = (ExecutableElement)member;
        addModifiersAndType(meth, utils.getReturnType(typeElement, meth), content);
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
        if (utils.hasHiddenTag(holder) || utils.hasHiddenTag(method)) {
            return;
        }

        Contents contents = writer.contents;
        Content label;
        HtmlLinkInfo.Kind context;
        // Abstract method is implemented from abstract class, not overridden
        label = utils.isAbstract(holder) && utils.isAbstract(method) ? contents.specifiedByLabel : contents.overridesLabel;
        context = HtmlLinkInfo.Kind.LINK_TYPE_PARAMS_AND_BOUNDS;
        dl.add(HtmlTree.DT(label));
        Content overriddenTypeLink =
                writer.getLink(new HtmlLinkInfo(writer.configuration, context, overriddenType));
        var codeOverriddenTypeLink = HtmlTree.CODE(overriddenTypeLink);
        Content methlink = writer.getLink(
                new HtmlLinkInfo(writer.configuration, HtmlLinkInfo.Kind.PLAIN, holder)
                        .fragment(writer.htmlIds.forMember(method).name())
                        .label(method.getSimpleName()));
        var codeMethLink = HtmlTree.CODE(methlink);
        var dd = HtmlTree.DD(codeMethLink);
        dd.add(Entity.NO_BREAK_SPACE);
        dd.add(contents.inClass);
        dd.add(Entity.NO_BREAK_SPACE);
        dd.add(codeOverriddenTypeLink);
        dl.add(dd);
    }

    /**
     * Adds "implements" information for a method (if appropriate)
     * into a definition list.
     *
     * @param writer  the writer for the method
     * @param method  the method
     * @param methods implemented methods
     * @param dl      the definition list
     */
    protected static void addImplementsInfo(HtmlDocletWriter writer,
                                            ExecutableElement method,
                                            Collection<ExecutableElement> methods,
                                            Content dl) {
        Utils utils = writer.utils;
        if (writer.options.noComment()) {
            return;
        }
        Contents contents = writer.contents;
        var enclosing = (TypeElement) method.getEnclosingElement();
        VisibleMemberTable vmt = writer.configuration.getVisibleMemberTable(enclosing);
        SortedSet<ExecutableElement> implementedMethods =
                new TreeSet<>(utils.comparators.overrideUseComparator());
        implementedMethods.addAll(methods);
        for (ExecutableElement implementedMeth : implementedMethods) {
            TypeMirror intfac = vmt.getImplementedMethodHolder(method, implementedMeth);
            intfac = utils.getDeclaredType(enclosing, intfac);
            Content intfaclink = writer.getLink(new HtmlLinkInfo(
                    writer.configuration, HtmlLinkInfo.Kind.LINK_TYPE_PARAMS_AND_BOUNDS, intfac));
            var codeIntfacLink = HtmlTree.CODE(intfaclink);
            dl.add(HtmlTree.DT(contents.specifiedByLabel));
            Content methlink = writer.getDocLink(
                    HtmlLinkInfo.Kind.PLAIN, implementedMeth,
                    implementedMeth.getSimpleName());
            var codeMethLink = HtmlTree.CODE(methlink);
            var dd = HtmlTree.DD(codeMethLink);
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
     * @return the return type
     */
    protected Content getReturnType(ExecutableElement method) {
        TypeMirror type = utils.getReturnType(typeElement, method);
        if (type != null) {
            return writer.getLink(new HtmlLinkInfo(configuration, HtmlLinkInfo.Kind.LINK_TYPE_PARAMS, type));
        }
        return new ContentBuilder();
    }
}
