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

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleElementVisitor8;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.Entity;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlAttr;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.ClassWriter;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.taglets.ParamTaglet;
import jdk.javadoc.internal.doclets.toolkit.util.ClassTree;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocletConstants;

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
 */
public class ClassWriterImpl extends SubWriterHolderWriter implements ClassWriter {

    private static final Set<String> suppressSubtypesSet
            = Set.of("java.lang.Object",
                     "org.omg.CORBA.Object");

    private static final Set<String> suppressImplementingSet
            = Set.of("java.lang.Cloneable",
                     "java.lang.constant.Constable",
                     "java.lang.constant.ConstantDesc",
                     "java.io.Serializable");

    protected final TypeElement typeElement;

    protected final ClassTree classtree;

    private final Navigation navBar;

    /**
     * @param configuration the configuration data for the doclet
     * @param typeElement the class being documented.
     * @param classTree the class tree for the given class.
     */
    public ClassWriterImpl(HtmlConfiguration configuration, TypeElement typeElement,
                           ClassTree classTree) {
        super(configuration, configuration.docPaths.forClass(typeElement));
        this.typeElement = typeElement;
        configuration.currentTypeElement = typeElement;
        this.classtree = classTree;
        this.navBar = new Navigation(typeElement, configuration, PageMode.CLASS, path);
    }

    @Override
    public Content getHeader(String header) {
        HtmlTree bodyTree = getBody(getWindowTitle(utils.getSimpleName(typeElement)));
        Content headerContent = new ContentBuilder();
        addTop(headerContent);
        Content linkContent = getModuleLink(utils.elementUtils.getModuleOf(typeElement),
                contents.moduleLabel);
        navBar.setNavLinkModule(linkContent);
        navBar.setMemberSummaryBuilder(configuration.getBuilderFactory().getMemberSummaryBuilder(this));
        navBar.setUserHeader(getUserHeaderFooter(true));
        headerContent.add(navBar.getContent(Navigation.Position.TOP));
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.setStyle(HtmlStyle.header);
        if (configuration.showModules) {
            ModuleElement mdle = configuration.docEnv.getElementUtils().getModuleOf(typeElement);
            Content classModuleLabel = HtmlTree.SPAN(HtmlStyle.moduleLabelInType, contents.moduleLabel);
            Content moduleNameDiv = HtmlTree.DIV(HtmlStyle.subTitle, classModuleLabel);
            moduleNameDiv.add(Entity.NO_BREAK_SPACE);
            moduleNameDiv.add(getModuleLink(mdle,
                    new StringContent(mdle.getQualifiedName())));
            div.add(moduleNameDiv);
        }
        PackageElement pkg = utils.containingPackage(typeElement);
        if (!pkg.isUnnamed()) {
            Content classPackageLabel = HtmlTree.SPAN(HtmlStyle.packageLabelInType, contents.packageLabel);
            Content pkgNameDiv = HtmlTree.DIV(HtmlStyle.subTitle, classPackageLabel);
            pkgNameDiv.add(Entity.NO_BREAK_SPACE);
            Content pkgNameContent = getPackageLink(pkg,
                    new StringContent(utils.getPackageName(pkg)));
            pkgNameDiv.add(pkgNameContent);
            div.add(pkgNameDiv);
        }
        LinkInfoImpl linkInfo = new LinkInfoImpl(configuration,
                LinkInfoImpl.Kind.CLASS_HEADER, typeElement);
        //Let's not link to ourselves in the header.
        linkInfo.linkToSelf = false;
        Content heading = HtmlTree.HEADING(Headings.PAGE_TITLE_HEADING, true,
                HtmlStyle.title, new StringContent(header));
        heading.add(getTypeParameterLinks(linkInfo));
        div.add(heading);
        bodyContents.setHeader(headerContent)
                .addMainContent(MarkerComments.START_OF_CLASS_DATA)
                .addMainContent(div);
        return bodyTree;
    }

    @Override
    public Content getClassContentHeader() {
        return getContentHeader();
    }

    @Override
    public void addFooter() {
        bodyContents.addMainContent(MarkerComments.END_OF_CLASS_DATA);
        Content htmlTree = HtmlTree.FOOTER();
        navBar.setUserFooter(getUserHeaderFooter(false));
        htmlTree.add(navBar.getContent(Navigation.Position.BOTTOM));
        addBottom(htmlTree);
        bodyContents.setFooter(htmlTree);
    }

    @Override
    public void printDocument(Content contentTree) throws DocFileIOException {
        String description = getDescription("declaration", typeElement);
        PackageElement pkg = utils.containingPackage(typeElement);
        List<DocPath> localStylesheets = getLocalStylesheets(pkg);
        contentTree.add(bodyContents);
        printHtmlDocument(configuration.metakeywords.getMetaKeywords(typeElement),
                description, localStylesheets, contentTree);
    }

    @Override
    public Content getClassInfoTreeHeader() {
        return getMemberTreeHeader();
    }

    @Override
    public Content getClassInfo(Content classInfoTree) {
        return getMemberTree(HtmlStyle.description, classInfoTree);
    }

    @Override
    protected TypeElement getCurrentPageElement() {
        return typeElement;
    }

    @Override @SuppressWarnings("preview")
    public void addClassSignature(String modifiers, Content classInfoTree) {
        Content hr = new HtmlTree(HtmlTag.HR);
        classInfoTree.add(hr);
        Content pre = new HtmlTree(HtmlTag.PRE);
        addAnnotationInfo(typeElement, pre);
        pre.add(modifiers);
        LinkInfoImpl linkInfo = new LinkInfoImpl(configuration,
                LinkInfoImpl.Kind.CLASS_SIGNATURE, typeElement);
        //Let's not link to ourselves in the signature.
        linkInfo.linkToSelf = false;
        Content className = new StringContent(utils.getSimpleName(typeElement));
        Content parameterLinks = getTypeParameterLinks(linkInfo);
        if (options.linkSource()) {
            addSrcLink(typeElement, className, pre);
            pre.add(parameterLinks);
        } else {
            Content span = HtmlTree.SPAN(HtmlStyle.typeNameLabel, className);
            span.add(parameterLinks);
            pre.add(span);
        }
        if (utils.isRecord(typeElement)) {
            pre.add(getRecordComponents(typeElement));
        }
        if (!utils.isInterface(typeElement)) {
            TypeMirror superclass = utils.getFirstVisibleSuperClass(typeElement);
            if (superclass != null) {
                pre.add(DocletConstants.NL);
                pre.add("extends ");
                Content link = getLink(new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.CLASS_SIGNATURE_PARENT_NAME,
                        superclass));
                pre.add(link);
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
                    pre.add(DocletConstants.NL);
                    pre.add(utils.isInterface(typeElement) ? "extends " : "implements ");
                    isFirst = false;
                } else {
                    pre.add(", ");
                }
                Content link = getLink(new LinkInfoImpl(configuration,
                                                        LinkInfoImpl.Kind.CLASS_SIGNATURE_PARENT_NAME,
                                                        type));
                pre.add(link);
            }
        }
        classInfoTree.add(pre);
    }

    @SuppressWarnings("preview")
    private Content getRecordComponents(TypeElement typeElem) {
        Content content = new ContentBuilder();
        content.add("(");
        String sep = "";
        for (RecordComponentElement e : typeElement.getRecordComponents()) {
            content.add(sep);
            getAnnotations(e.getAnnotationMirrors(), false)
                    .forEach(a -> { content.add(a); content.add(" "); });
            Content link = getLink(new LinkInfoImpl(configuration, LinkInfoImpl.Kind.RECORD_COMPONENT,
                    e.asType()));
            content.add(link);
            content.add(Entity.NO_BREAK_SPACE);
            content.add(e.getSimpleName());
            sep = ", ";
        }
        content.add(")");
        return content;
    }

    @Override
    public void addClassDescription(Content classInfoTree) {
        if (!options.noComment()) {
            // generate documentation for the class.
            if (!utils.getFullBody(typeElement).isEmpty()) {
                addInlineComment(typeElement, classInfoTree);
            }
        }
    }

    @Override
    public void addClassTagInfo(Content classInfoTree) {
        if (!options.noComment()) {
            // Print Information about all the tags here
            addTagsInfo(typeElement, classInfoTree);
        }
    }

    /**
     * Get the class hierarchy tree for the given class.
     *
     * @param type the class to print the hierarchy for
     * @return a content tree for class inheritance
     */
    private Content getClassInheritanceTree(TypeMirror type) {
        TypeMirror sup;
        HtmlTree classTree = null;
        do {
            sup = utils.getFirstVisibleSuperClass(type);
            HtmlTree htmlElement = HtmlTree.DIV(HtmlStyle.inheritance, getTreeForClassHelper(type));
            if (classTree != null)
                htmlElement.add(classTree);
            classTree = htmlElement;
            type = sup;
        } while (sup != null);
        classTree.put(HtmlAttr.TITLE, contents.getContent("doclet.Inheritance_Tree").toString());
        return classTree;
    }

    /**
     * Get the class helper tree for the given class.
     *
     * @param type the class to print the helper for
     * @return a content tree for class helper
     */
    private Content getTreeForClassHelper(TypeMirror type) {
        Content content = new ContentBuilder();
        if (type.equals(typeElement.asType())) {
            Content typeParameters = getTypeParameterLinks(
                    new LinkInfoImpl(configuration, LinkInfoImpl.Kind.TREE,
                    typeElement));
            if (configuration.shouldExcludeQualifier(utils.containingPackage(typeElement).toString())) {
                content.add(utils.asTypeElement(type).getSimpleName());
                content.add(typeParameters);
            } else {
                content.add(utils.asTypeElement(type).getQualifiedName());
                content.add(typeParameters);
            }
        } else {
            Content link = getLink(new LinkInfoImpl(configuration,
                    LinkInfoImpl.Kind.CLASS_TREE_PARENT, type)
                    .label(configuration.getClassName(utils.asTypeElement(type))));
            content.add(link);
        }
        return content;
    }

    @Override
    public void addClassTree(Content classContentTree) {
        if (!utils.isClass(typeElement)) {
            return;
        }
        classContentTree.add(getClassInheritanceTree(typeElement.asType()));
    }

    @Override
    public void addParamInfo(Content classInfoTree) {
        if (utils.hasBlockTag(typeElement, DocTree.Kind.PARAM)) {
            Content paramInfo = (new ParamTaglet()).getTagletOutput(typeElement,
                    getTagletWriterInstance(false));
            if (!paramInfo.isEmpty()) {
                classInfoTree.add(HtmlTree.DL(HtmlStyle.notes, paramInfo));
            }
        }
    }

    @Override
    public void addSubClassInfo(Content classInfoTree) {
        if (utils.isClass(typeElement)) {
            for (String s : suppressSubtypesSet) {
                if (typeElement.getQualifiedName().contentEquals(s)) {
                    return;    // Don't generate the list, too huge
                }
            }
            Set<TypeElement> subclasses = classtree.directSubClasses(typeElement, false);
            if (!subclasses.isEmpty()) {
                HtmlTree dl = HtmlTree.DL(HtmlStyle.notes);
                dl.add(HtmlTree.DT(contents.subclassesLabel));
                dl.add(HtmlTree.DD(getClassLinks(LinkInfoImpl.Kind.SUBCLASSES, subclasses)));
                classInfoTree.add(dl);
            }
        }
    }

    @Override
    public void addSubInterfacesInfo(Content classInfoTree) {
        if (utils.isInterface(typeElement)) {
            Set<TypeElement> subInterfaces = classtree.allSubClasses(typeElement, false);
            if (!subInterfaces.isEmpty()) {
                Content dl = HtmlTree.DL(HtmlStyle.notes);
                dl.add(HtmlTree.DT(contents.subinterfacesLabel));
                dl.add(HtmlTree.DD(getClassLinks(LinkInfoImpl.Kind.SUBINTERFACES, subInterfaces)));
                classInfoTree.add(dl);
            }
        }
    }

    @Override
    public void addInterfaceUsageInfo (Content classInfoTree) {
        if (!utils.isInterface(typeElement)) {
            return;
        }
        for (String s : suppressImplementingSet) {
            if (typeElement.getQualifiedName().contentEquals(s)) {
                return;    // Don't generate the list, too huge
            }
        }
        Set<TypeElement> implcl = classtree.implementingClasses(typeElement);
        if (!implcl.isEmpty()) {
            HtmlTree dl = HtmlTree.DL(HtmlStyle.notes);
            dl.add(HtmlTree.DT(contents.implementingClassesLabel));
            dl.add(HtmlTree.DD(getClassLinks(LinkInfoImpl.Kind.IMPLEMENTED_CLASSES, implcl)));
            classInfoTree.add(dl);
        }
    }

    @Override
    public void addImplementedInterfacesInfo(Content classInfoTree) {
        SortedSet<TypeMirror> interfaces = new TreeSet<>(utils.makeTypeMirrorClassUseComparator());
        interfaces.addAll(utils.getAllInterfaces(typeElement));
        if (utils.isClass(typeElement) && !interfaces.isEmpty()) {
            HtmlTree dl = HtmlTree.DL(HtmlStyle.notes);
            dl.add(HtmlTree.DT(contents.allImplementedInterfacesLabel));
            dl.add(HtmlTree.DD(getClassLinks(LinkInfoImpl.Kind.IMPLEMENTED_INTERFACES, interfaces)));
            classInfoTree.add(dl);
        }
    }

    @Override
    public void addSuperInterfacesInfo(Content classInfoTree) {
        SortedSet<TypeMirror> interfaces =
                new TreeSet<>(utils.makeTypeMirrorIndexUseComparator());
        interfaces.addAll(utils.getAllInterfaces(typeElement));

        if (utils.isInterface(typeElement) && !interfaces.isEmpty()) {
            HtmlTree dl = HtmlTree.DL(HtmlStyle.notes);
            dl.add(HtmlTree.DT(contents.allSuperinterfacesLabel));
            dl.add(HtmlTree.DD(getClassLinks(LinkInfoImpl.Kind.SUPER_INTERFACES, interfaces)));
            classInfoTree.add(dl);
        }
    }

    @Override
    public void addNestedClassInfo(final Content classInfoTree) {
        Element outerClass = typeElement.getEnclosingElement();
        if (outerClass == null)
            return;
        new SimpleElementVisitor8<Void, Void>() {
            @Override
            public Void visitType(TypeElement e, Void p) {
                HtmlTree dl = HtmlTree.DL(HtmlStyle.notes);
                dl.add(HtmlTree.DT(utils.isInterface(e)
                        ? contents.enclosingInterfaceLabel
                        : contents.enclosingClassLabel));
                Content dd = new HtmlTree(HtmlTag.DD);
                dd.add(getLink(new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.CLASS, e)));
                dl.add(dd);
                classInfoTree.add(dl);
                return null;
            }
        }.visit(outerClass);
    }

    @Override
    public void addFunctionalInterfaceInfo (Content classInfoTree) {
        if (isFunctionalInterface()) {
            HtmlTree dl = HtmlTree.DL(HtmlStyle.notes);
            dl.add(HtmlTree.DT(contents.functionalInterface));
            Content dd = new HtmlTree(HtmlTag.DD);
            dd.add(contents.functionalInterfaceMessage);
            dl.add(dd);
            classInfoTree.add(dl);
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


    @Override
    public void addClassDeprecationInfo(Content classInfoTree) {
        List<? extends DocTree> deprs = utils.getBlockTags(typeElement, DocTree.Kind.DEPRECATED);
        if (utils.isDeprecated(typeElement)) {
            Content deprLabel = HtmlTree.SPAN(HtmlStyle.deprecatedLabel, getDeprecatedPhrase(typeElement));
            Content div = HtmlTree.DIV(HtmlStyle.deprecationBlock, deprLabel);
            if (!deprs.isEmpty()) {
                CommentHelper ch = utils.getCommentHelper(typeElement);
                DocTree dt = deprs.get(0);
                List<? extends DocTree> commentTags = ch.getBody(dt);
                if (!commentTags.isEmpty()) {
                    addInlineDeprecatedComment(typeElement, deprs.get(0), div);
                }
            }
            classInfoTree.add(div);
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
        Content content = new ContentBuilder();
        boolean isFirst = true;
        for (Object type : list) {
            if (!isFirst) {
                Content separator = new StringContent(", ");
                content.add(separator);
            } else {
                isFirst = false;
            }
            // TODO: should we simply split this method up to avoid instanceof ?
            if (type instanceof TypeElement) {
                Content link = getLink(
                        new LinkInfoImpl(configuration, context, (TypeElement)(type)));
                content.add(HtmlTree.CODE(link));
            } else {
                Content link = getLink(
                        new LinkInfoImpl(configuration, context, ((TypeMirror)type)));
                content.add(HtmlTree.CODE(link));
            }
        }
        return content;
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
