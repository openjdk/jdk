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

package com.sun.tools.doclets.formats.html;

import java.util.*;

import com.sun.javadoc.*;
import com.sun.tools.javadoc.RootDocImpl;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.builders.*;
import com.sun.tools.doclets.internal.toolkit.taglets.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

import java.io.IOException;

/**
 * Generate the Class Information Page.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @see com.sun.javadoc.ClassDoc
 * @see java.util.Collections
 * @see java.util.List
 * @see java.util.ArrayList
 * @see java.util.HashMap
 *
 * @author Atul M Dambalkar
 * @author Robert Field
 * @author Bhavesh Patel (Modified)
 */
public class ClassWriterImpl extends SubWriterHolderWriter
        implements ClassWriter {

    protected final ClassDoc classDoc;

    protected final ClassTree classtree;

    protected final ClassDoc prev;

    protected final ClassDoc next;

    /**
     * @param configuration the configuration data for the doclet
     * @param classDoc the class being documented.
     * @param prevClass the previous class that was documented.
     * @param nextClass the next class being documented.
     * @param classTree the class tree for the given class.
     */
    public ClassWriterImpl (ConfigurationImpl configuration, ClassDoc classDoc,
            ClassDoc prevClass, ClassDoc nextClass, ClassTree classTree)
            throws IOException {
        super(configuration, DocPath.forClass(classDoc));
        this.classDoc = classDoc;
        configuration.currentcd = classDoc;
        this.classtree = classTree;
        this.prev = prevClass;
        this.next = nextClass;
    }

    /**
     * Get this package link.
     *
     * @return a content tree for the package link
     */
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
    protected Content getNavLinkClass() {
        Content li = HtmlTree.LI(HtmlStyle.navBarCell1Rev, classLabel);
        return li;
    }

    /**
     * Get the class use link.
     *
     * @return a content tree for the class use link
     */
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
    public Content getHeader(String header) {
        String pkgname = (classDoc.containingPackage() != null)?
            classDoc.containingPackage().name(): "";
        String clname = classDoc.name();
        HtmlTree bodyTree = getBody(true, getWindowTitle(clname));
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
        if (pkgname.length() > 0) {
            Content classPackageLabel = HtmlTree.SPAN(HtmlStyle.packageLabelInClass, packageLabel);
            Content pkgNameDiv = HtmlTree.DIV(HtmlStyle.subTitle, classPackageLabel);
            pkgNameDiv.addContent(getSpace());
            Content pkgNameContent = getTargetPackageLink(classDoc.containingPackage(),
                    "classFrame", new StringContent(pkgname));
            pkgNameDiv.addContent(pkgNameContent);
            div.addContent(pkgNameDiv);
        }
        LinkInfoImpl linkInfo = new LinkInfoImpl(configuration,
                LinkInfoImpl.Kind.CLASS_HEADER, classDoc);
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
    public Content getClassContentHeader() {
        return getContentHeader();
    }

    /**
     * {@inheritDoc}
     */
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
    public void printDocument(Content contentTree) throws IOException {
        printHtmlDocument(configuration.metakeywords.getMetaKeywords(classDoc),
                true, contentTree);
    }

    /**
     * {@inheritDoc}
     */
    public Content getClassInfoTreeHeader() {
        return getMemberTreeHeader();
    }

    /**
     * {@inheritDoc}
     */
    public Content getClassInfo(Content classInfoTree) {
        return getMemberTree(HtmlStyle.description, classInfoTree);
    }

    /**
     * {@inheritDoc}
     */
    public void addClassSignature(String modifiers, Content classInfoTree) {
        boolean isInterface = classDoc.isInterface();
        classInfoTree.addContent(new HtmlTree(HtmlTag.BR));
        Content pre = new HtmlTree(HtmlTag.PRE);
        addAnnotationInfo(classDoc, pre);
        pre.addContent(modifiers);
        LinkInfoImpl linkInfo = new LinkInfoImpl(configuration,
                LinkInfoImpl.Kind.CLASS_SIGNATURE, classDoc);
        //Let's not link to ourselves in the signature.
        linkInfo.linkToSelf = false;
        Content className = new StringContent(classDoc.name());
        Content parameterLinks = getTypeParameterLinks(linkInfo);
        if (configuration.linksource) {
            addSrcLink(classDoc, className, pre);
            pre.addContent(parameterLinks);
        } else {
            Content span = HtmlTree.SPAN(HtmlStyle.typeNameLabel, className);
            span.addContent(parameterLinks);
            pre.addContent(span);
        }
        if (!isInterface) {
            Type superclass = utils.getFirstVisibleSuperClass(classDoc,
                    configuration);
            if (superclass != null) {
                pre.addContent(DocletConstants.NL);
                pre.addContent("extends ");
                Content link = getLink(new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.CLASS_SIGNATURE_PARENT_NAME,
                        superclass));
                pre.addContent(link);
            }
        }
        Type[] implIntfacs = classDoc.interfaceTypes();
        if (implIntfacs != null && implIntfacs.length > 0) {
            int counter = 0;
            for (Type implType : implIntfacs) {
                ClassDoc classDoc = implType.asClassDoc();
                if (!(classDoc.isPublic() || utils.isLinkable(classDoc, configuration))) {
                    continue;
                }
                if (counter == 0) {
                    pre.addContent(DocletConstants.NL);
                    pre.addContent(isInterface ? "extends " : "implements ");
                } else {
                    pre.addContent(", ");
                }
                Content link = getLink(new LinkInfoImpl(configuration,
                                                        LinkInfoImpl.Kind.CLASS_SIGNATURE_PARENT_NAME,
                                                        implType));
                pre.addContent(link);
                counter++;
            }
        }
        classInfoTree.addContent(pre);
    }

    /**
     * {@inheritDoc}
     */
    public void addClassDescription(Content classInfoTree) {
        if(!configuration.nocomment) {
            // generate documentation for the class.
            if (classDoc.inlineTags().length > 0) {
                addInlineComment(classDoc, classInfoTree);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void addClassTagInfo(Content classInfoTree) {
        if(!configuration.nocomment) {
            // Print Information about all the tags here
            addTagsInfo(classDoc, classInfoTree);
        }
    }

    /**
     * Get the class hierarchy tree for the given class.
     *
     * @param type the class to print the hierarchy for
     * @return a content tree for class inheritence
     */
    private Content getClassInheritenceTree(Type type) {
        Type sup;
        HtmlTree classTreeUl = new HtmlTree(HtmlTag.UL);
        classTreeUl.addStyle(HtmlStyle.inheritance);
        Content liTree = null;
        do {
            sup = utils.getFirstVisibleSuperClass(
                    type instanceof ClassDoc ? (ClassDoc) type : type.asClassDoc(),
                    configuration);
            if (sup != null) {
                HtmlTree ul = new HtmlTree(HtmlTag.UL);
                ul.addStyle(HtmlStyle.inheritance);
                ul.addContent(getTreeForClassHelper(type));
                if (liTree != null)
                    ul.addContent(liTree);
                Content li = HtmlTree.LI(ul);
                liTree = li;
                type = sup;
            }
            else
                classTreeUl.addContent(getTreeForClassHelper(type));
        }
        while (sup != null);
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
    private Content getTreeForClassHelper(Type type) {
        Content li = new HtmlTree(HtmlTag.LI);
        if (type.equals(classDoc)) {
            Content typeParameters = getTypeParameterLinks(
                    new LinkInfoImpl(configuration, LinkInfoImpl.Kind.TREE,
                    classDoc));
            if (configuration.shouldExcludeQualifier(
                    classDoc.containingPackage().name())) {
                li.addContent(type.asClassDoc().name());
                li.addContent(typeParameters);
            } else {
                li.addContent(type.asClassDoc().qualifiedName());
                li.addContent(typeParameters);
            }
        } else {
            Content link = getLink(new LinkInfoImpl(configuration,
                    LinkInfoImpl.Kind.CLASS_TREE_PARENT, type)
                    .label(configuration.getClassName(type.asClassDoc())));
            li.addContent(link);
        }
        return li;
    }

    /**
     * {@inheritDoc}
     */
    public void addClassTree(Content classContentTree) {
        if (!classDoc.isClass()) {
            return;
        }
        classContentTree.addContent(getClassInheritenceTree(classDoc));
    }

    /**
     * {@inheritDoc}
     */
    public void addTypeParamInfo(Content classInfoTree) {
        if (classDoc.typeParamTags().length > 0) {
            Content typeParam = (new ParamTaglet()).getTagletOutput(classDoc,
                    getTagletWriterInstance(false));
            Content dl = HtmlTree.DL(typeParam);
            classInfoTree.addContent(dl);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void addSubClassInfo(Content classInfoTree) {
        if (classDoc.isClass()) {
            if (classDoc.qualifiedName().equals("java.lang.Object") ||
                    classDoc.qualifiedName().equals("org.omg.CORBA.Object")) {
                return;    // Don't generate the list, too huge
            }
            SortedSet<ClassDoc> subclasses = classtree.subs(classDoc, false);
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
    public void addSubInterfacesInfo(Content classInfoTree) {
        if (classDoc.isInterface()) {
            SortedSet<ClassDoc> subInterfaces = classtree.allSubs(classDoc, false);
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
    public void addInterfaceUsageInfo (Content classInfoTree) {
        if (! classDoc.isInterface()) {
            return;
        }
        if (classDoc.qualifiedName().equals("java.lang.Cloneable") ||
                classDoc.qualifiedName().equals("java.io.Serializable")) {
            return;   // Don't generate the list, too big
        }
        SortedSet<ClassDoc> implcl = classtree.implementingclasses(classDoc);
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
    public void addImplementedInterfacesInfo(Content classInfoTree) {
        //NOTE:  we really should be using ClassDoc.interfaceTypes() here, but
        //       it doesn't walk up the tree like we want it to.
        List<Type> interfaceArray = utils.getAllInterfaces(classDoc, configuration);
        if (classDoc.isClass() && interfaceArray.size() > 0) {
            Content label = getResource(
                    "doclet.All_Implemented_Interfaces");
            Content dt = HtmlTree.DT(label);
            Content dl = HtmlTree.DL(dt);
            dl.addContent(getClassLinks(LinkInfoImpl.Kind.IMPLEMENTED_INTERFACES,
                    interfaceArray));
            classInfoTree.addContent(dl);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void addSuperInterfacesInfo(Content classInfoTree) {
        //NOTE:  we really should be using ClassDoc.interfaceTypes() here, but
        //       it doesn't walk up the tree like we want it to.
        List<Type> interfaceArray = utils.getAllInterfaces(classDoc, configuration);
        if (classDoc.isInterface() && interfaceArray.size() > 0) {
            Content label = getResource(
                    "doclet.All_Superinterfaces");
            Content dt = HtmlTree.DT(label);
            Content dl = HtmlTree.DL(dt);
            dl.addContent(getClassLinks(LinkInfoImpl.Kind.SUPER_INTERFACES,
                    interfaceArray));
            classInfoTree.addContent(dl);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void addNestedClassInfo(Content classInfoTree) {
        ClassDoc outerClass = classDoc.containingClass();
        if (outerClass != null) {
            Content label;
            if (outerClass.isInterface()) {
                label = getResource(
                        "doclet.Enclosing_Interface");
            } else {
                label = getResource(
                        "doclet.Enclosing_Class");
            }
            Content dt = HtmlTree.DT(label);
            Content dl = HtmlTree.DL(dt);
            Content dd = new HtmlTree(HtmlTag.DD);
            dd.addContent(getLink(new LinkInfoImpl(configuration,
                    LinkInfoImpl.Kind.CLASS, outerClass)));
            dl.addContent(dd);
            classInfoTree.addContent(dl);
        }
    }

    /**
     * {@inheritDoc}
     */
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
        if (configuration.root instanceof RootDocImpl) {
            RootDocImpl root = (RootDocImpl) configuration.root;
            AnnotationDesc[] annotationDescList = classDoc.annotations();
            for (AnnotationDesc annoDesc : annotationDescList) {
                if (root.isFunctionalInterface(annoDesc)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public void addClassDeprecationInfo(Content classInfoTree) {
        Content hr = new HtmlTree(HtmlTag.HR);
        classInfoTree.addContent(hr);
        Tag[] deprs = classDoc.tags("deprecated");
        if (utils.isDeprecated(classDoc)) {
            Content deprLabel = HtmlTree.SPAN(HtmlStyle.deprecatedLabel, deprecatedPhrase);
            Content div = HtmlTree.DIV(HtmlStyle.block, deprLabel);
            if (deprs.length > 0) {
                Tag[] commentTags = deprs[0].inlineTags();
                if (commentTags.length > 0) {
                    div.addContent(getSpace());
                    addInlineDeprecatedComment(classDoc, deprs[0], div);
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
        Object[] typeList = list.toArray();
        Content dd = new HtmlTree(HtmlTag.DD);
        boolean isFirst = true;
        for (Object item : typeList) {
            if (!isFirst) {
                Content separator = new StringContent(", ");
                dd.addContent(separator);
            } else {
                isFirst = false;
            }

            if (item instanceof ClassDoc) {
                Content link = getLink(new LinkInfoImpl(configuration, context, (ClassDoc)item));
                dd.addContent(link);
            } else {
                Content link = getLink(new LinkInfoImpl(configuration, context, (Type)item));
                dd.addContent(link);
            }
        }
        return dd;
    }

    /**
     * {@inheritDoc}
     */
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
            e.printStackTrace();
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
        String[] navLinkLabels =  new String[] {
            "doclet.navNested", "doclet.navEnum", "doclet.navField", "doclet.navConstructor",
            "doclet.navMethod"
        };
        for (int i = 0; i < navLinkLabels.length; i++ ) {
            Content liNav = new HtmlTree(HtmlTag.LI);
            if (i == VisibleMemberMap.ENUM_CONSTANTS && ! classDoc.isEnum()) {
                continue;
            }
            if (i == VisibleMemberMap.CONSTRUCTORS && classDoc.isEnum()) {
                continue;
            }
            AbstractMemberWriter writer =
                ((AbstractMemberWriter) memberSummaryBuilder.
                getMemberSummaryWriter(i));
            if (writer == null) {
                liNav.addContent(getResource(navLinkLabels[i]));
            } else {
                writer.addNavSummaryLink(
                        memberSummaryBuilder.members(i),
                        memberSummaryBuilder.getVisibleMemberMap(i), liNav);
            }
            if (i < navLinkLabels.length-1) {
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
     */
    protected Content getNavDetailLinks() throws Exception {
        Content li = HtmlTree.LI(detailLabel);
        li.addContent(getSpace());
        Content ulNav = HtmlTree.UL(HtmlStyle.subNavList, li);
        MemberSummaryBuilder memberSummaryBuilder = (MemberSummaryBuilder)
                configuration.getBuilderFactory().getMemberSummaryBuilder(this);
        String[] navLinkLabels =  new String[] {
            "doclet.navNested", "doclet.navEnum", "doclet.navField", "doclet.navConstructor",
            "doclet.navMethod"
        };
        for (int i = 1; i < navLinkLabels.length; i++ ) {
            Content liNav = new HtmlTree(HtmlTag.LI);
            AbstractMemberWriter writer =
                    ((AbstractMemberWriter) memberSummaryBuilder.
                    getMemberSummaryWriter(i));
            if (i == VisibleMemberMap.ENUM_CONSTANTS && ! classDoc.isEnum()) {
                continue;
            }
            if (i == VisibleMemberMap.CONSTRUCTORS && classDoc.isEnum()) {
                continue;
            }
            if (writer == null) {
                liNav.addContent(getResource(navLinkLabels[i]));
            } else {
                writer.addNavDetailLink(memberSummaryBuilder.members(i), liNav);
            }
            if (i < navLinkLabels.length - 1) {
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
     * Return the classDoc being documented.
     *
     * @return the classDoc being documented.
     */
    public ClassDoc getClassDoc() {
        return classDoc;
    }
}
