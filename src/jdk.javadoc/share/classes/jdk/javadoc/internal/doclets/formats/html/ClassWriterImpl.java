/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleElementVisitor8;

import com.sun.source.doctree.DeprecatedTree;
import com.sun.source.doctree.DocTree;
import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.Entity;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlAttr;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.TagName;
import jdk.javadoc.internal.doclets.formats.html.markup.Text;
import jdk.javadoc.internal.doclets.toolkit.ClassWriter;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.taglets.ParamTaglet;
import jdk.javadoc.internal.doclets.toolkit.util.ClassTree;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable;

/**
 * Generate the Class Information Page.
 *
 * @see javax.lang.model.element.TypeElement
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
    }

    @Override
    public Content getHeader(String header) {
        HtmlTree body = getBody(getWindowTitle(utils.getSimpleName(typeElement)));
        var div = HtmlTree.DIV(HtmlStyle.header);
        if (configuration.showModules) {
            ModuleElement mdle = configuration.docEnv.getElementUtils().getModuleOf(typeElement);
            var classModuleLabel = HtmlTree.SPAN(HtmlStyle.moduleLabelInType, contents.moduleLabel);
            var moduleNameDiv = HtmlTree.DIV(HtmlStyle.subTitle, classModuleLabel);
            moduleNameDiv.add(Entity.NO_BREAK_SPACE);
            moduleNameDiv.add(getModuleLink(mdle,
                    Text.of(mdle.getQualifiedName())));
            div.add(moduleNameDiv);
        }
        PackageElement pkg = utils.containingPackage(typeElement);
        if (!pkg.isUnnamed()) {
            var classPackageLabel = HtmlTree.SPAN(HtmlStyle.packageLabelInType, contents.packageLabel);
            var pkgNameDiv = HtmlTree.DIV(HtmlStyle.subTitle, classPackageLabel);
            pkgNameDiv.add(Entity.NO_BREAK_SPACE);
            Content pkgNameContent = getPackageLink(pkg, getLocalizedPackageName(pkg));
            pkgNameDiv.add(pkgNameContent);
            div.add(pkgNameDiv);
        }
        HtmlLinkInfo linkInfo = new HtmlLinkInfo(configuration,
                HtmlLinkInfo.Kind.CLASS_HEADER, typeElement);
        //Let's not link to ourselves in the header.
        linkInfo.linkToSelf = false;
        var heading = HtmlTree.HEADING_TITLE(Headings.PAGE_TITLE_HEADING,
                HtmlStyle.title, Text.of(header));
        heading.add(getTypeParameterLinks(linkInfo));
        div.add(heading);
        bodyContents.setHeader(getHeader(PageMode.CLASS, typeElement))
                .addMainContent(MarkerComments.START_OF_CLASS_DATA)
                .addMainContent(div);
        return body;
    }

    @Override
    public Content getClassContentHeader() {
        return getContentHeader();
    }

    @Override
    protected Navigation getNavBar(PageMode pageMode, Element element) {
        Content linkContent = getModuleLink(utils.elementUtils.getModuleOf(element),
                contents.moduleLabel);
        return super.getNavBar(pageMode, element)
                .setNavLinkModule(linkContent)
                .setSubNavLinks(() -> {
                    List<Content> list = new ArrayList<>();
                    VisibleMemberTable vmt = configuration.getVisibleMemberTable(typeElement);
                    Set<VisibleMemberTable.Kind> summarySet =
                            VisibleMemberTable.Kind.forSummariesOf(element.getKind());
                    for (VisibleMemberTable.Kind kind : summarySet) {
                        list.add(links.createLink(HtmlIds.forMemberSummary(kind),
                                contents.getNavLinkLabelContent(kind), vmt.hasVisibleMembers(kind)));
                    }
                    return list;
                });
    }

    @Override
    public void addFooter() {
        bodyContents.addMainContent(MarkerComments.END_OF_CLASS_DATA);
        bodyContents.setFooter(getFooter());
    }

    @Override
    public void printDocument(Content content) throws DocFileIOException {
        String description = getDescription("declaration", typeElement);
        PackageElement pkg = utils.containingPackage(typeElement);
        List<DocPath> localStylesheets = getLocalStylesheets(pkg);
        content.add(bodyContents);
        printHtmlDocument(configuration.metakeywords.getMetaKeywords(typeElement),
                description, localStylesheets, content);
    }

    @Override
    public Content getClassInfo(Content classInfo) {
        return getMember(HtmlIds.CLASS_DESCRIPTION, HtmlStyle.classDescription, classInfo);
    }

    @Override
    protected TypeElement getCurrentPageElement() {
        return typeElement;
    }

    @Override
    public void addClassSignature(Content classInfo) {
        classInfo.add(new HtmlTree(TagName.HR));
        classInfo.add(new Signatures.TypeSignature(typeElement, this)
                .toContent());
    }


    @Override
    public void addClassDescription(Content classInfo) {
        addPreviewInfo(classInfo);
        if (!options.noComment()) {
            // generate documentation for the class.
            if (!utils.getFullBody(typeElement).isEmpty()) {
                addInlineComment(typeElement, classInfo);
            }
        }
    }

    private void addPreviewInfo(Content content) {
        addPreviewInfo(typeElement, content);
    }

    @Override
    public void addClassTagInfo(Content classInfo) {
        if (!options.noComment()) {
            // Print Information about all the tags here
            addTagsInfo(typeElement, classInfo);
        }
    }

    /**
     * Get the class inheritance tree for the given class.
     *
     * @param type the class to get the inheritance tree for
     * @return the class inheritance tree
     */
    private Content getClassInheritanceTreeContent(TypeMirror type) {
        TypeMirror sup;
        HtmlTree classTree = null;
        do {
            sup = utils.getFirstVisibleSuperClass(type);
            var entry = HtmlTree.DIV(HtmlStyle.inheritance, getClassHelperContent(type));
            if (classTree != null)
                entry.add(classTree);
            classTree = entry;
            type = sup;
        } while (sup != null);
        classTree.put(HtmlAttr.TITLE, contents.getContent("doclet.Inheritance_Tree").toString());
        return classTree;
    }

    /**
     * Get the class helper for the given class.
     *
     * @param type the class to get the helper for
     * @return the class helper
     */
    private Content getClassHelperContent(TypeMirror type) {
        Content result = new ContentBuilder();
        if (utils.typeUtils.isSameType(type, typeElement.asType())) {
            Content typeParameters = getTypeParameterLinks(
                    new HtmlLinkInfo(configuration, HtmlLinkInfo.Kind.TREE,
                    typeElement));
            if (configuration.shouldExcludeQualifier(utils.containingPackage(typeElement).toString())) {
                result.add(utils.asTypeElement(type).getSimpleName());
                result.add(typeParameters);
            } else {
                result.add(utils.asTypeElement(type).getQualifiedName());
                result.add(typeParameters);
            }
        } else {
            Content link = getLink(new HtmlLinkInfo(configuration,
                    HtmlLinkInfo.Kind.CLASS_TREE_PARENT, type)
                    .label(configuration.getClassName(utils.asTypeElement(type))));
            result.add(link);
        }
        return result;
    }

    @Override
    public void addClassTree(Content target) {
        if (!utils.isClass(typeElement)) {
            return;
        }
        target.add(getClassInheritanceTreeContent(typeElement.asType()));
    }

    @Override
    public void addParamInfo(Content target) {
        if (utils.hasBlockTag(typeElement, DocTree.Kind.PARAM)) {
            Content paramInfo = (new ParamTaglet()).getAllBlockTagOutput(typeElement,
                    getTagletWriterInstance(false));
            if (!paramInfo.isEmpty()) {
                target.add(HtmlTree.DL(HtmlStyle.notes, paramInfo));
            }
        }
    }

    @Override
    public void addSubClassInfo(Content target) {
        if (utils.isClass(typeElement)) {
            for (String s : suppressSubtypesSet) {
                if (typeElement.getQualifiedName().contentEquals(s)) {
                    return;    // Don't generate the list, too huge
                }
            }
            Set<TypeElement> subclasses = classtree.directSubClasses(typeElement, false);
            if (!subclasses.isEmpty()) {
                var dl = HtmlTree.DL(HtmlStyle.notes);
                dl.add(HtmlTree.DT(contents.subclassesLabel));
                dl.add(HtmlTree.DD(getClassLinks(HtmlLinkInfo.Kind.SUBCLASSES, subclasses)));
                target.add(dl);
            }
        }
    }

    @Override
    public void addSubInterfacesInfo(Content target) {
        if (utils.isPlainInterface(typeElement)) {
            Set<TypeElement> subInterfaces = classtree.allSubClasses(typeElement, false);
            if (!subInterfaces.isEmpty()) {
                var dl = HtmlTree.DL(HtmlStyle.notes);
                dl.add(HtmlTree.DT(contents.subinterfacesLabel));
                dl.add(HtmlTree.DD(getClassLinks(HtmlLinkInfo.Kind.SUBINTERFACES, subInterfaces)));
                target.add(dl);
            }
        }
    }

    @Override
    public void addInterfaceUsageInfo(Content target) {
        if (!utils.isPlainInterface(typeElement)) {
            return;
        }
        for (String s : suppressImplementingSet) {
            if (typeElement.getQualifiedName().contentEquals(s)) {
                return;    // Don't generate the list, too huge
            }
        }
        Set<TypeElement> implcl = classtree.implementingClasses(typeElement);
        if (!implcl.isEmpty()) {
            var dl = HtmlTree.DL(HtmlStyle.notes);
            dl.add(HtmlTree.DT(contents.implementingClassesLabel));
            dl.add(HtmlTree.DD(getClassLinks(HtmlLinkInfo.Kind.IMPLEMENTED_CLASSES, implcl)));
            target.add(dl);
        }
    }

    @Override
    public void addImplementedInterfacesInfo(Content target) {
        SortedSet<TypeMirror> interfaces = new TreeSet<>(comparators.makeTypeMirrorClassUseComparator());
        interfaces.addAll(utils.getAllInterfaces(typeElement));
        if (utils.isClass(typeElement) && !interfaces.isEmpty()) {
            var dl = HtmlTree.DL(HtmlStyle.notes);
            dl.add(HtmlTree.DT(contents.allImplementedInterfacesLabel));
            dl.add(HtmlTree.DD(getClassLinks(HtmlLinkInfo.Kind.IMPLEMENTED_INTERFACES, interfaces)));
            target.add(dl);
        }
    }

    @Override
    public void addSuperInterfacesInfo(Content target) {
        SortedSet<TypeMirror> interfaces =
                new TreeSet<>(comparators.makeTypeMirrorIndexUseComparator());
        interfaces.addAll(utils.getAllInterfaces(typeElement));

        if (utils.isPlainInterface(typeElement) && !interfaces.isEmpty()) {
            var dl = HtmlTree.DL(HtmlStyle.notes);
            dl.add(HtmlTree.DT(contents.allSuperinterfacesLabel));
            dl.add(HtmlTree.DD(getClassLinks(HtmlLinkInfo.Kind.SUPER_INTERFACES, interfaces)));
            target.add(dl);
        }
    }

    @Override
    public void addNestedClassInfo(final Content target) {
        Element outerClass = typeElement.getEnclosingElement();
        if (outerClass == null)
            return;
        new SimpleElementVisitor8<Void, Void>() {
            @Override
            public Void visitType(TypeElement e, Void p) {
                var dl = HtmlTree.DL(HtmlStyle.notes);
                dl.add(HtmlTree.DT(utils.isPlainInterface(e)
                        ? contents.enclosingInterfaceLabel
                        : contents.enclosingClassLabel));
                dl.add(HtmlTree.DD(getClassLinks(HtmlLinkInfo.Kind.CLASS, List.of(e))));
                target.add(dl);
                return null;
            }
        }.visit(outerClass);
    }

    @Override
    public void addFunctionalInterfaceInfo (Content target) {
        if (isFunctionalInterface()) {
            var dl = HtmlTree.DL(HtmlStyle.notes);
            dl.add(HtmlTree.DT(contents.functionalInterface));
            var dd = new HtmlTree(TagName.DD);
            dd.add(contents.functionalInterfaceMessage);
            dl.add(dd);
            target.add(dl);
        }
    }

    public boolean isFunctionalInterface() {
        List<? extends AnnotationMirror> annotationMirrors = typeElement.getAnnotationMirrors();
        for (AnnotationMirror anno : annotationMirrors) {
            if (utils.isFunctionalInterface(anno)) {
                return true;
            }
        }
        return false;
    }


    @Override
    public void addClassDeprecationInfo(Content classInfo) {
        List<? extends DeprecatedTree> deprs = utils.getDeprecatedTrees(typeElement);
        if (utils.isDeprecated(typeElement)) {
            var deprLabel = HtmlTree.SPAN(HtmlStyle.deprecatedLabel, getDeprecatedPhrase(typeElement));
            var div = HtmlTree.DIV(HtmlStyle.deprecationBlock, deprLabel);
            if (!deprs.isEmpty()) {
                CommentHelper ch = utils.getCommentHelper(typeElement);
                DocTree dt = deprs.get(0);
                List<? extends DocTree> commentTags = ch.getBody(dt);
                if (!commentTags.isEmpty()) {
                    addInlineDeprecatedComment(typeElement, deprs.get(0), div);
                }
            }
            classInfo.add(div);
        }
    }

    /**
     * Get the links to the given classes.
     *
     * @param context the id of the context where the links will be added
     * @param list the classes
     * @return the links
     */
    private Content getClassLinks(HtmlLinkInfo.Kind context, Collection<?> list) {
        Content content = new ContentBuilder();
        boolean isFirst = true;
        for (Object type : list) {
            if (!isFirst) {
                content.add(Text.of(", "));
            } else {
                isFirst = false;
            }
            // TODO: should we simply split this method up to avoid instanceof ?
            if (type instanceof TypeElement te) {
                Content link = getLink(
                        new HtmlLinkInfo(configuration, context, te));
                content.add(HtmlTree.CODE(link));
            } else {
                Content link = getLink(
                        new HtmlLinkInfo(configuration, context, ((TypeMirror)type)));
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

    @Override
    public Content getMemberDetails(Content content) {
        var section = HtmlTree.SECTION(HtmlStyle.details, content);
        // The following id is required by the Navigation bar
        if (utils.isAnnotationInterface(typeElement)) {
            section.setId(HtmlIds.ANNOTATION_TYPE_ELEMENT_DETAIL);
        }
        return section;
    }
}
