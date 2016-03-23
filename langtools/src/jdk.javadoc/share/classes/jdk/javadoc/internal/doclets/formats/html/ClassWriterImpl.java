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

import java.io.IOException;
import java.util.*;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleElementVisitor8;

import com.sun.source.doctree.DocTree;
import com.sun.tools.javac.util.DefinedBy;
import com.sun.tools.javac.util.DefinedBy.Api;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlConstants;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.ClassWriter;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.builders.MemberSummaryBuilder;
import jdk.javadoc.internal.doclets.toolkit.taglets.ParamTaglet;
import jdk.javadoc.internal.doclets.toolkit.util.ClassTree;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.DocletAbortException;
import jdk.javadoc.internal.doclets.toolkit.util.DocletConstants;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberMap;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberMap.Kind;

/**
 * Generate the Class Information Page.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @see javax.lang.model.element.TypeElement
 * @see java.util.Collections
 * @see java.util.List
 * @see java.util.ArrayList
 * @see java.util.HashMap
 *
 * @author Atul M Dambalkar
 * @author Robert Field
 * @author Bhavesh Patel (Modified)
 */
public class ClassWriterImpl extends SubWriterHolderWriter implements ClassWriter {

    protected final TypeElement typeElement;

    protected final ClassTree classtree;

    protected final TypeElement prev;

    protected final TypeElement next;

    /**
     * @param configuration the configuration data for the doclet
     * @param typeElement the class being documented.
     * @param prevClass the previous class that was documented.
     * @param nextClass the next class being documented.
     * @param classTree the class tree for the given class.
     * @throws java.io.IOException
     */
    public ClassWriterImpl(ConfigurationImpl configuration, TypeElement typeElement,
            TypeElement prevClass, TypeElement nextClass, ClassTree classTree)
            throws IOException {
        super(configuration, DocPath.forClass(configuration.utils, typeElement));
        this.typeElement = typeElement;
        configuration.currentTypeElement = typeElement;
        this.classtree = classTree;
        this.prev = prevClass;
        this.next = nextClass;
    }

    /**
     * Get this package link.
     *
     * @return a content tree for the package link
     */
    @Override
    protected Content getNavLinkPackage() {
        Content linkContent = getHyperLink(DocPaths.PACKAGE_SUMMARY,
                packageLabel);
        Content li = HtmlTree.LI(linkContent);
        return li;
    }

    /**
     * Get the class link.
     *
     * @return a content tree for the class link
     */
    @Override
    protected Content getNavLinkClass() {
        Content li = HtmlTree.LI(HtmlStyle.navBarCell1Rev, classLabel);
        return li;
    }

    /**
     * Get the class use link.
     *
     * @return a content tree for the class use link
     */
    @Override
    protected Content getNavLinkClassUse() {
        Content linkContent = getHyperLink(DocPaths.CLASS_USE.resolve(filename), useLabel);
        Content li = HtmlTree.LI(linkContent);
        return li;
    }

    /**
     * Get link to previous class.
     *
     * @return a content tree for the previous class link
     */
    @Override
    public Content getNavLinkPrevious() {
        Content li;
        if (prev != null) {
            Content prevLink = getLink(new LinkInfoImpl(configuration,
                    LinkInfoImpl.Kind.CLASS, prev)
                    .label(prevclassLabel).strong(true));
            li = HtmlTree.LI(prevLink);
        }
        else
            li = HtmlTree.LI(prevclassLabel);
        return li;
    }

    /**
     * Get link to next class.
     *
     * @return a content tree for the next class link
     */
    @Override
    public Content getNavLinkNext() {
        Content li;
        if (next != null) {
            Content nextLink = getLink(new LinkInfoImpl(configuration,
                    LinkInfoImpl.Kind.CLASS, next)
                    .label(nextclassLabel).strong(true));
            li = HtmlTree.LI(nextLink);
        }
        else
            li = HtmlTree.LI(nextclassLabel);
        return li;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getHeader(String header) {
        HtmlTree bodyTree = getBody(true, getWindowTitle(utils.getSimpleName(typeElement)));
        HtmlTree htmlTree = (configuration.allowTag(HtmlTag.HEADER))
                ? HtmlTree.HEADER()
                : bodyTree;
        addTop(htmlTree);
        addNavLinks(true, htmlTree);
        if (configuration.allowTag(HtmlTag.HEADER)) {
            bodyTree.addContent(htmlTree);
        }
        bodyTree.addContent(HtmlConstants.START_OF_CLASS_DATA);
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.addStyle(HtmlStyle.header);
        ModuleElement mdle = configuration.root.getElementUtils().getModuleOf(typeElement);
        if (mdle != null && !mdle.isUnnamed()) {
            Content classModuleLabel = HtmlTree.SPAN(HtmlStyle.moduleLabelInClass, moduleLabel);
            Content moduleNameDiv = HtmlTree.DIV(HtmlStyle.subTitle, classModuleLabel);
            moduleNameDiv.addContent(getSpace());
            moduleNameDiv.addContent(getModuleLink(mdle,
                    new StringContent(mdle.getQualifiedName().toString())));
            div.addContent(moduleNameDiv);
        }
        PackageElement pkg = utils.containingPackage(typeElement);
        if (!pkg.isUnnamed()) {
            Content classPackageLabel = HtmlTree.SPAN(HtmlStyle.packageLabelInClass, packageLabel);
            Content pkgNameDiv = HtmlTree.DIV(HtmlStyle.subTitle, classPackageLabel);
            pkgNameDiv.addContent(getSpace());
            Content pkgNameContent = getPackageLink(pkg,
                    new StringContent(utils.getPackageName(pkg)));
            pkgNameDiv.addContent(pkgNameContent);
            div.addContent(pkgNameDiv);
        }
        LinkInfoImpl linkInfo = new LinkInfoImpl(configuration,
                LinkInfoImpl.Kind.CLASS_HEADER, typeElement);
        //Let's not link to ourselves in the header.
        linkInfo.linkToSelf = false;
        Content headerContent = new StringContent(header);
        Content heading = HtmlTree.HEADING(HtmlConstants.CLASS_PAGE_HEADING, true,
                HtmlStyle.title, headerContent);
        heading.addContent(getTypeParameterLinks(linkInfo));
        div.addContent(heading);
        if (configuration.allowTag(HtmlTag.MAIN)) {
            mainTree.addContent(div);
        } else {
            bodyTree.addContent(div);
        }
        return bodyTree;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getClassContentHeader() {
        return getContentHeader();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addFooter(Content contentTree) {
        contentTree.addContent(HtmlConstants.END_OF_CLASS_DATA);
        Content htmlTree = (configuration.allowTag(HtmlTag.FOOTER))
                ? HtmlTree.FOOTER()
                : contentTree;
        addNavLinks(false, htmlTree);
        addBottom(htmlTree);
        if (configuration.allowTag(HtmlTag.FOOTER)) {
            contentTree.addContent(htmlTree);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void printDocument(Content contentTree) throws IOException {
        printHtmlDocument(configuration.metakeywords.getMetaKeywords(typeElement),
                true, contentTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getClassInfoTreeHeader() {
        return getMemberTreeHeader();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getClassInfo(Content classInfoTree) {
        return getMemberTree(HtmlStyle.description, classInfoTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addClassSignature(String modifiers, Content classInfoTree) {
        classInfoTree.addContent(new HtmlTree(HtmlTag.BR));
        Content pre = new HtmlTree(HtmlTag.PRE);
        addAnnotationInfo(typeElement, pre);
        pre.addContent(modifiers);
        LinkInfoImpl linkInfo = new LinkInfoImpl(configuration,
                LinkInfoImpl.Kind.CLASS_SIGNATURE, typeElement);
        //Let's not link to ourselves in the signature.
        linkInfo.linkToSelf = false;
        Content className = new StringContent(utils.getSimpleName(typeElement));
        Content parameterLinks = getTypeParameterLinks(linkInfo);
        if (configuration.linksource) {
            addSrcLink(typeElement, className, pre);
            pre.addContent(parameterLinks);
        } else {
            Content span = HtmlTree.SPAN(HtmlStyle.typeNameLabel, className);
            span.addContent(parameterLinks);
            pre.addContent(span);
        }
        if (!utils.isInterface(typeElement)) {
            TypeMirror superclass = utils.getFirstVisibleSuperClass(typeElement);
            if (superclass != null) {
                pre.addContent(DocletConstants.NL);
                pre.addContent("extends ");
                Content link = getLink(new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.CLASS_SIGNATURE_PARENT_NAME,
                        superclass));
                pre.addContent(link);
            }
        }
        List<? extends TypeMirror> interfaces = typeElement.getInterfaces();
        if (!interfaces.isEmpty()) {
            boolean isFirst = true;
            for (TypeMirror type : interfaces) {
                TypeElement tDoc = utils.asTypeElement(type);
                if (!(utils.isPublic(tDoc) || utils.isLinkable(tDoc))) {
                    continue;
                }
                if (isFirst) {
                    pre.addContent(DocletConstants.NL);
                    pre.addContent(utils.isInterface(typeElement) ? "extends " : "implements ");
                    isFirst = false;
                } else {
                    pre.addContent(", ");
                }
                Content link = getLink(new LinkInfoImpl(configuration,
                                                        LinkInfoImpl.Kind.CLASS_SIGNATURE_PARENT_NAME,
                                                        type));
                pre.addContent(link);
            }
        }
        classInfoTree.addContent(pre);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addClassDescription(Content classInfoTree) {
        if(!configuration.nocomment) {
            // generate documentation for the class.
            if (!utils.getBody(typeElement).isEmpty()) {
                addInlineComment(typeElement, classInfoTree);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addClassTagInfo(Content classInfoTree) {
        if(!configuration.nocomment) {
            // Print Information about all the tags here
            addTagsInfo(typeElement, classInfoTree);
        }
    }

    /**
     * Get the class hierarchy tree for the given class.
     *
     * @param type the class to print the hierarchy for
     * @return a content tree for class inheritence
     */
    private Content getClassInheritenceTree(TypeMirror type) {
        TypeMirror sup;
        HtmlTree classTreeUl = new HtmlTree(HtmlTag.UL);
        classTreeUl.addStyle(HtmlStyle.inheritance);
        Content liTree = null;
        do {
            sup = utils.getFirstVisibleSuperClass(type);
            if (sup != null) {
                HtmlTree ul = new HtmlTree(HtmlTag.UL);
                ul.addStyle(HtmlStyle.inheritance);
                ul.addContent(getTreeForClassHelper(type));
                if (liTree != null)
                    ul.addContent(liTree);
                Content li = HtmlTree.LI(ul);
                liTree = li;
                type = sup;
            } else
                classTreeUl.addContent(getTreeForClassHelper(type));
        } while (sup != null);
        if (liTree != null)
            classTreeUl.addContent(liTree);
        return classTreeUl;
    }

    /**
     * Get the class helper tree for the given class.
     *
     * @param type the class to print the helper for
     * @return a content tree for class helper
     */
    private Content getTreeForClassHelper(TypeMirror type) {
        Content li = new HtmlTree(HtmlTag.LI);
        if (type.equals(typeElement.asType())) {
            Content typeParameters = getTypeParameterLinks(
                    new LinkInfoImpl(configuration, LinkInfoImpl.Kind.TREE,
                    typeElement));
            if (configuration.shouldExcludeQualifier(utils.containingPackage(typeElement).toString())) {
                li.addContent(utils.asTypeElement(type).getSimpleName());
                li.addContent(typeParameters);
            } else {
                li.addContent(utils.asTypeElement(type).getQualifiedName());
                li.addContent(typeParameters);
            }
        } else {
            Content link = getLink(new LinkInfoImpl(configuration,
                    LinkInfoImpl.Kind.CLASS_TREE_PARENT, type)
                    .label(configuration.getClassName(utils.asTypeElement(type))));
            li.addContent(link);
        }
        return li;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addClassTree(Content classContentTree) {
        if (!utils.isClass(typeElement)) {
            return;
        }
        classContentTree.addContent(getClassInheritenceTree(typeElement.asType()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addTypeParamInfo(Content classInfoTree) {
        if (!utils.getTypeParamTrees(typeElement).isEmpty()) {
            Content typeParam = (new ParamTaglet()).getTagletOutput(typeElement,
                    getTagletWriterInstance(false));
            Content dl = HtmlTree.DL(typeParam);
            classInfoTree.addContent(dl);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addSubClassInfo(Content classInfoTree) {
        if (utils.isClass(typeElement)) {
            if (typeElement.getQualifiedName().toString().equals("java.lang.Object") ||
                    typeElement.getQualifiedName().toString().equals("org.omg.CORBA.Object")) {
                return;    // Don't generate the list, too huge
            }
            Set<TypeElement> subclasses = classtree.directSubClasses(typeElement, false);
            if (!subclasses.isEmpty()) {
                Content label = getResource(
                        "doclet.Subclasses");
                Content dt = HtmlTree.DT(label);
                Content dl = HtmlTree.DL(dt);
                dl.addContent(getClassLinks(LinkInfoImpl.Kind.SUBCLASSES,
                        subclasses));
                classInfoTree.addContent(dl);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addSubInterfacesInfo(Content classInfoTree) {
        if (utils.isInterface(typeElement)) {
            Set<TypeElement> subInterfaces = classtree.allSubClasses(typeElement, false);
            if (!subInterfaces.isEmpty()) {
                Content label = getResource(
                        "doclet.Subinterfaces");
                Content dt = HtmlTree.DT(label);
                Content dl = HtmlTree.DL(dt);
                dl.addContent(getClassLinks(LinkInfoImpl.Kind.SUBINTERFACES,
                        subInterfaces));
                classInfoTree.addContent(dl);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addInterfaceUsageInfo (Content classInfoTree) {
        if (!utils.isInterface(typeElement)) {
            return;
        }
        if (typeElement.getQualifiedName().toString().equals("java.lang.Cloneable") ||
                typeElement.getQualifiedName().toString().equals("java.io.Serializable")) {
            return;   // Don't generate the list, too big
        }
        Set<TypeElement> implcl = classtree.implementingClasses(typeElement);
        if (!implcl.isEmpty()) {
            Content label = getResource(
                    "doclet.Implementing_Classes");
            Content dt = HtmlTree.DT(label);
            Content dl = HtmlTree.DL(dt);
            dl.addContent(getClassLinks(LinkInfoImpl.Kind.IMPLEMENTED_CLASSES,
                    implcl));
            classInfoTree.addContent(dl);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addImplementedInterfacesInfo(Content classInfoTree) {
        SortedSet<TypeMirror> interfaces = new TreeSet<>(utils.makeTypeMirrorClassUseComparator());
        interfaces.addAll(utils.getAllInterfaces(typeElement));
        if (utils.isClass(typeElement) && !interfaces.isEmpty()) {
            Content label = getResource(
                    "doclet.All_Implemented_Interfaces");
            Content dt = HtmlTree.DT(label);
            Content dl = HtmlTree.DL(dt);
            dl.addContent(getClassLinks(LinkInfoImpl.Kind.IMPLEMENTED_INTERFACES, interfaces));
            classInfoTree.addContent(dl);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addSuperInterfacesInfo(Content classInfoTree) {
        SortedSet<TypeMirror> interfaces =
                new TreeSet<>(utils.makeTypeMirrorIndexUseComparator());
        interfaces.addAll(utils.getAllInterfaces(typeElement));

        if (utils.isInterface(typeElement) && !interfaces.isEmpty()) {
            Content label = getResource("doclet.All_Superinterfaces");
            Content dt = HtmlTree.DT(label);
            Content dl = HtmlTree.DL(dt);
            dl.addContent(getClassLinks(LinkInfoImpl.Kind.SUPER_INTERFACES, interfaces));
            classInfoTree.addContent(dl);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addNestedClassInfo(final Content classInfoTree) {
        Element outerClass = typeElement.getEnclosingElement();
        if (outerClass == null)
            return;
        new SimpleElementVisitor8<Void, Void>() {
            @Override @DefinedBy(Api.LANGUAGE_MODEL)
            public Void visitType(TypeElement e, Void p) {
                String label = utils.isInterface(e)
                        ? "doclet.Enclosing_Interface"
                        : "doclet.Enclosing_Class";
                Content dt = HtmlTree.DT(getResource(label));
                Content dl = HtmlTree.DL(dt);
                Content dd = new HtmlTree(HtmlTag.DD);
                dd.addContent(getLink(new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.CLASS, e)));
                dl.addContent(dd);
                classInfoTree.addContent(dl);
                return null;
            }
        }.visit(outerClass);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addFunctionalInterfaceInfo (Content classInfoTree) {
        if (isFunctionalInterface()) {
            Content dt = HtmlTree.DT(getResource("doclet.Functional_Interface"));
            Content dl = HtmlTree.DL(dt);
            Content dd = new HtmlTree(HtmlTag.DD);
            dd.addContent(getResource("doclet.Functional_Interface_Message"));
            dl.addContent(dd);
            classInfoTree.addContent(dl);
        }
    }

    public boolean isFunctionalInterface() {
        List<? extends AnnotationMirror> annotationMirrors = ((Element) typeElement).getAnnotationMirrors();
        for (AnnotationMirror anno : annotationMirrors) {
            if (utils.isFunctionalInterface(anno)) {
                return true;
            }
        }
        return false;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void addClassDeprecationInfo(Content classInfoTree) {
        Content hr = new HtmlTree(HtmlTag.HR);
        classInfoTree.addContent(hr);
        List<? extends DocTree> deprs = utils.getBlockTags(typeElement, DocTree.Kind.DEPRECATED);
        if (utils.isDeprecated(typeElement)) {
            Content deprLabel = HtmlTree.SPAN(HtmlStyle.deprecatedLabel, deprecatedPhrase);
            Content div = HtmlTree.DIV(HtmlStyle.block, deprLabel);
            if (!deprs.isEmpty()) {
                CommentHelper ch = utils.getCommentHelper(typeElement);
                DocTree dt = deprs.get(0);
                List<? extends DocTree> commentTags = ch.getBody(configuration, dt);
                if (!commentTags.isEmpty()) {
                    div.addContent(getSpace());
                    addInlineDeprecatedComment(typeElement, deprs.get(0), div);
                }
            }
            classInfoTree.addContent(div);
        }
    }

    /**
     * Get links to the given classes.
     *
     * @param context the id of the context where the link will be printed
     * @param list the list of classes
     * @return a content tree for the class list
     */
    private Content getClassLinks(LinkInfoImpl.Kind context, Collection<?> list) {
        Content dd = new HtmlTree(HtmlTag.DD);
        boolean isFirst = true;
        for (Object type : list) {
            if (!isFirst) {
                Content separator = new StringContent(", ");
                dd.addContent(separator);
            } else {
                isFirst = false;
            }
            // TODO: should we simply split this method up to avoid instanceof ?
            if (type instanceof TypeElement) {
                Content link = getLink(
                        new LinkInfoImpl(configuration, context, (TypeElement)(type)));
                dd.addContent(link);
            } else {
                Content link = getLink(
                        new LinkInfoImpl(configuration, context, ((TypeMirror)type)));
                dd.addContent(link);
            }
        }
        return dd;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Content getNavLinkTree() {
        Content treeLinkContent = getHyperLink(DocPaths.PACKAGE_TREE,
                treeLabel, "", "");
        Content li = HtmlTree.LI(treeLinkContent);
        return li;
    }

    /**
     * Add summary details to the navigation bar.
     *
     * @param subDiv the content tree to which the summary detail links will be added
     */
    protected void addSummaryDetailLinks(Content subDiv) {
        try {
            Content div = HtmlTree.DIV(getNavSummaryLinks());
            div.addContent(getNavDetailLinks());
            subDiv.addContent(div);
        } catch (Exception e) {
            throw new DocletAbortException(e);
        }
    }

    /**
     * Get summary links for navigation bar.
     *
     * @return the content tree for the navigation summary links
     */
    protected Content getNavSummaryLinks() throws Exception {
        Content li = HtmlTree.LI(summaryLabel);
        li.addContent(getSpace());
        Content ulNav = HtmlTree.UL(HtmlStyle.subNavList, li);
        MemberSummaryBuilder memberSummaryBuilder = (MemberSummaryBuilder)
                configuration.getBuilderFactory().getMemberSummaryBuilder(this);
        for (VisibleMemberMap.Kind kind : VisibleMemberMap.Kind.summarySet) {
            Content liNav = new HtmlTree(HtmlTag.LI);
            if (kind == VisibleMemberMap.Kind.ENUM_CONSTANTS && !utils.isEnum(typeElement)) {
                continue;
            }
            if (kind == VisibleMemberMap.Kind.CONSTRUCTORS && utils.isEnum(typeElement)) {
                continue;
            }
            AbstractMemberWriter writer =
                ((AbstractMemberWriter) memberSummaryBuilder.getMemberSummaryWriter(kind));
            if (writer == null) {
                liNav.addContent(getResource(VisibleMemberMap.Kind.getNavLinkLabels(kind)));
            } else {
                writer.addNavSummaryLink(
                        memberSummaryBuilder.members(kind),
                        memberSummaryBuilder.getVisibleMemberMap(kind), liNav);
            }
            if (kind != Kind.METHODS) {
                addNavGap(liNav);
            }
            ulNav.addContent(liNav);
        }
        return ulNav;
    }

    /**
     * Get detail links for the navigation bar.
     *
     * @return the content tree for the detail links
     * @throws java.lang.Exception
     */
    protected Content getNavDetailLinks() throws Exception {
        Content li = HtmlTree.LI(detailLabel);
        li.addContent(getSpace());
        Content ulNav = HtmlTree.UL(HtmlStyle.subNavList, li);
        MemberSummaryBuilder memberSummaryBuilder = (MemberSummaryBuilder)
                configuration.getBuilderFactory().getMemberSummaryBuilder(this);
        for (VisibleMemberMap.Kind kind : VisibleMemberMap.Kind.detailSet) {
            Content liNav = new HtmlTree(HtmlTag.LI);
            AbstractMemberWriter writer =
                    ((AbstractMemberWriter) memberSummaryBuilder.
                    getMemberSummaryWriter(kind));
            if (kind == VisibleMemberMap.Kind.ENUM_CONSTANTS && !utils.isEnum(typeElement)) {
                continue;
            }
            if (kind == VisibleMemberMap.Kind.CONSTRUCTORS && utils.isEnum(typeElement)) {
                continue;
            }
            if (writer == null) {
                liNav.addContent(getResource(VisibleMemberMap.Kind.getNavLinkLabels(kind)));
            } else {
                writer.addNavDetailLink(memberSummaryBuilder.members(kind), liNav);
            }
            if (kind != Kind.METHODS) {
                addNavGap(liNav);
            }
            ulNav.addContent(liNav);
        }
        return ulNav;
    }

    /**
     * Add gap between navigation bar elements.
     *
     * @param liNav the content tree to which the gap will be added
     */
    protected void addNavGap(Content liNav) {
        liNav.addContent(getSpace());
        liNav.addContent("|");
        liNav.addContent(getSpace());
    }

    /**
     * Return the TypeElement being documented.
     *
     * @return the TypeElement being documented.
     */
    @Override
    public TypeElement getTypeElement() {
        return typeElement;
    }
}
