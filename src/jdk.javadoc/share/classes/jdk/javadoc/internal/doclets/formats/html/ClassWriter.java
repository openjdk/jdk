/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleElementVisitor8;

import com.sun.source.doctree.DeprecatedTree;
import com.sun.source.doctree.DocTree;

import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyles;
import jdk.javadoc.internal.doclets.toolkit.CommentUtils;
import jdk.javadoc.internal.doclets.toolkit.DocletException;
import jdk.javadoc.internal.doclets.toolkit.PropertyUtils;
import jdk.javadoc.internal.doclets.toolkit.util.ClassTree;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.ContentBuilder;
import jdk.javadoc.internal.html.HtmlAttr;
import jdk.javadoc.internal.html.HtmlTree;
import jdk.javadoc.internal.html.Text;

/**
 * Generate the Class Information Page.
 *
 * @see javax.lang.model.element.TypeElement
 */
public class ClassWriter extends SubWriterHolderWriter {

    private static final Set<String> suppressSubtypesSet
            = Set.of("java.lang.Object",
                     "org.omg.CORBA.Object");

    private static final Set<String> suppressImplementingSet
            = Set.of("java.lang.Cloneable",
                     "java.lang.constant.Constable",
                     "java.lang.constant.ConstantDesc",
                     "java.io.Serializable");

    /* Length threshold to determine whether to insert whitespace between type parameters */
    protected static final int LONG_TYPE_PARAM = 8;

    protected final TypeElement typeElement;

    protected final ClassTree classTree;
    protected final PropertyUtils.PropertyHelper pHelper;

    /**
     * @param configuration the configuration data for the doclet
     * @param typeElement the class being documented.
     * @param classTree the class tree for the given class.
     */
    public ClassWriter(HtmlConfiguration configuration, TypeElement typeElement,
                       ClassTree classTree) {
        super(configuration, configuration.docPaths.forClass(typeElement));
        this.typeElement = typeElement;
        this.classTree = classTree;

        pHelper = new PropertyUtils.PropertyHelper(configuration, typeElement);

        switch (typeElement.getKind()) {
            case ENUM   -> setEnumDocumentation(typeElement);
            case RECORD -> setRecordDocumentation(typeElement);
        }
    }

    @Override
    public PropertyUtils.PropertyHelper getPropertyHelper() {
        return pHelper;
    }

    @Override
    public void buildPage() throws DocletException {
        buildClassDoc();
    }

    /**
     * Handles the {@literal <TypeElement>} tag.
     *
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildClassDoc() throws DocletException {
        String key = switch (typeElement.getKind()) {
            case INTERFACE       -> "doclet.Interface";
            case ENUM            -> "doclet.Enum";
            case RECORD          -> "doclet.RecordClass";
            case ANNOTATION_TYPE -> "doclet.AnnotationType";
            case CLASS           -> "doclet.Class";
            default -> throw new IllegalStateException(typeElement.getKind() + " " + typeElement);
        };
        Content content = getHeader(resources.getText(key) + " " + utils.getSimpleName(typeElement));
        Content classContent = getClassContentHeader();

        buildClassTree(classContent);
        buildClassInfo(classContent);
        buildMemberSummary(classContent);
        buildMemberDetails(classContent);

        addClassContent(classContent);
        addFooter();
        printDocument(content);
        copyDocFiles();
    }

    /**
     * Build the class inheritance tree documentation.
     *
     * @param classContent the content to which the documentation will be added
     */
    protected void buildClassTree(Content classContent) {
        addClassTree(classContent);
    }

    /**
     * Build the class information documentation.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildClassInfo(Content target) {
        var c = new ContentBuilder();
        buildParamInfo(c);
        buildSuperInterfacesInfo(c);
        buildImplementedInterfacesInfo(c);
        buildSubClassInfo(c);
        buildSubInterfacesInfo(c);
        buildInterfaceUsageInfo(c);
        buildNestedClassInfo(c);
        buildFunctionalInterfaceInfo(c);
        c.add(HtmlTree.HR());
        var div = HtmlTree.DIV(HtmlStyles.horizontalScroll);
        buildClassSignature(div);
        buildDeprecationInfo(div);
        buildClassDescription(div);
        buildClassTagInfo(div);
        c.add(div);
        target.add(getClassInfo(c));
    }

    /**
     * Build the type parameters and state components of this class.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildParamInfo(Content target) {
        addParamInfo(target);
    }

    /**
     * If this is an interface, list all superinterfaces.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildSuperInterfacesInfo(Content target) {
        addSuperInterfacesInfo(target);
    }

    /**
     * If this is a class, list all interfaces implemented by this class.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildImplementedInterfacesInfo(Content target) {
        addImplementedInterfacesInfo(target);
    }

    /**
     * List all the classes that extend this one.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildSubClassInfo(Content target) {
        addSubClassInfo(target);
    }

    /**
     * List all the interfaces that extend this one.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildSubInterfacesInfo(Content target) {
        addSubInterfacesInfo(target);
    }

    /**
     * If this is an interface, list all classes that implement this interface.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildInterfaceUsageInfo(Content target) {
        addInterfaceUsageInfo(target);
    }

    /**
     * If this is a functional interface, display appropriate message.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildFunctionalInterfaceInfo(Content target) {
        addFunctionalInterfaceInfo(target);
    }

    /**
     * If this class is deprecated, build the appropriate information.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildDeprecationInfo(Content target) {
        addClassDeprecationInfo(target);
    }

    /**
     * If this is an inner class or interface, list the enclosing class or interface.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildNestedClassInfo(Content target) {
        addNestedClassInfo(target);
    }

    /**
     * Copy the doc files.
     *
     * @throws DocFileIOException if there is a problem while copying the files
     */
    private void copyDocFiles() throws DocletException {
        PackageElement containingPackage = utils.containingPackage(typeElement);
        var containingPackagesSeen = configuration.getContainingPackagesSeen();
        if ((configuration.packages == null ||
                !configuration.packages.contains(containingPackage)) &&
                !containingPackagesSeen.contains(containingPackage)) {
            //Only copy doc files dir if the containing package is not
            //documented AND if we have not documented a class from the same
            //package already. Otherwise, we are making duplicate copies.
            var docFilesHandler = configuration
                    .getWriterFactory()
                    .newDocFilesHandler(containingPackage);
            docFilesHandler.copyDocFiles();
            containingPackagesSeen.add(containingPackage);
        }
    }

    /**
     * Build the signature of the current class.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildClassSignature(Content target) {
        addClassSignature(target);
    }

    /**
     * Build the class description.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildClassDescription(Content target) {
        addClassDescription(target);
    }

    /**
     * Build the tag information for the current class.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildClassTagInfo(Content target) {
        addClassTagInfo(target);
    }

    /**
     * Build the member summary contents of the page.
     *
     * @param classContent the content to which the documentation will be added
     */
    protected void buildMemberSummary(Content classContent) {
        Content summariesList = getSummariesList();

        var f = configuration.getWriterFactory();
        for (var k : AbstractMemberWriter.summaryKinds) {
            var writer = f.newMemberWriter(this, k);
            writer.buildSummary(summariesList);
        }

        classContent.add(getMemberSummary(summariesList));
    }

    /**
     * Build the member details contents of the page.
     *
     * @param classContent the content to which the documentation will be added
     */
    protected void buildMemberDetails(Content classContent) {
        Content detailsList = getDetailsList();

        var f = configuration.getWriterFactory();
        for (var k : AbstractMemberWriter.detailKinds) {
            var writer = f.newMemberWriter(this, k);
            writer.buildDetails(detailsList);
        }

        classContent.add(getMemberDetails(detailsList));
    }

    /**
     * The documentation for values() and valueOf() in Enums are set by the
     * doclet only iff the user or overridden methods are missing.
     * @param elem the enum element
     */
    private void setEnumDocumentation(TypeElement elem) {
        CommentUtils cmtUtils = configuration.cmtUtils;
        for (ExecutableElement ee : utils.getMethods(elem)) {
            if (!utils.getFullBody(ee).isEmpty()) // ignore if already set
                continue;
            Name name = ee.getSimpleName();
            if (name.contentEquals("values") && ee.getParameters().isEmpty()) {
                utils.removeCommentHelper(ee); // purge previous entry
                cmtUtils.setEnumValuesTree(ee);
            } else if (name.contentEquals("valueOf") && ee.getParameters().size() == 1) {
                // TODO: check parameter type
                utils.removeCommentHelper(ee); // purge previous entry
                cmtUtils.setEnumValueOfTree(ee);
            }
        }
    }

    /**
     * Sets the documentation as needed for the mandated parts of a record type.
     * This includes the canonical constructor, methods like {@code equals},
     * {@code hashCode}, {@code toString}, the accessor methods, and the underlying
     * field.
     * @param elem the record element
     */

    private void setRecordDocumentation(TypeElement elem) {
        CommentUtils cmtUtils = configuration.cmtUtils;
        Set<Name> componentNames = elem.getRecordComponents().stream()
                .map(Element::getSimpleName)
                .collect(Collectors.toSet());

        for (ExecutableElement ee : utils.getConstructors(elem)) {
            if (utils.isCanonicalRecordConstructor(ee)) {
                if (utils.getFullBody(ee).isEmpty()) {
                    utils.removeCommentHelper(ee); // purge previous entry
                    cmtUtils.setRecordConstructorTree(ee);
                }
                // only one canonical constructor; no need to keep looking
                break;
            }
        }

        var fields = utils.isSerializable(elem)
                ? utils.getFieldsUnfiltered(elem)
                : utils.getFields(elem);
        for (VariableElement ve : fields) {
            // The fields for the record component cannot be declared by the
            // user and so cannot have any pre-existing comment.
            Name name = ve.getSimpleName();
            if (componentNames.contains(name)) {
                utils.removeCommentHelper(ve); // purge previous entry
                cmtUtils.setRecordFieldTree(ve);
            }
        }

        TypeMirror objectType = utils.getObjectType();

        for (ExecutableElement ee : utils.getMethods(elem)) {
            if (!utils.getFullBody(ee).isEmpty()) {
                continue;
            }

            Name name = ee.getSimpleName();
            List<? extends VariableElement> params = ee.getParameters();
            if (name.contentEquals("equals")) {
                if (params.size() == 1 && utils.typeUtils.isSameType(params.get(0).asType(), objectType)) {
                    utils.removeCommentHelper(ee); // purge previous entry
                    cmtUtils.setRecordEqualsTree(ee);
                }
            } else if (name.contentEquals("hashCode")) {
                if (params.isEmpty()) {
                    utils.removeCommentHelper(ee); // purge previous entry
                    cmtUtils.setRecordHashCodeTree(ee);
                }
            } else if (name.contentEquals("toString")) {
                if (params.isEmpty()) {
                    utils.removeCommentHelper(ee); // purge previous entry
                    cmtUtils.setRecordToStringTree(ee);
                }
            } else if (componentNames.contains(name)) {
                if (params.isEmpty()) {
                    utils.removeCommentHelper(ee); // purge previous entry
                    cmtUtils.setRecordAccessorTree(ee);
                }
            }
        }
    }

    protected Content getHeader(String header) {
        HtmlTree body = getBody(getWindowTitle(utils.getSimpleName(typeElement)));
        var div = HtmlTree.DIV(HtmlStyles.header);
        var heading = HtmlTree.HEADING_TITLE(Headings.PAGE_TITLE_HEADING,
                HtmlStyles.title, Text.of(header));
        heading.add(getTypeParameters());
        div.add(heading);
        bodyContents.setHeader(getHeader(PageMode.CLASS, typeElement))
                .addMainContent(MarkerComments.START_OF_CLASS_DATA)
                .addMainContent(div);
        return body;
    }

    // Renders type parameters for the class heading, creating id attributes
    // if @param block tags are missing in doc comment.
    private Content getTypeParameters() {
        var content = new ContentBuilder();
        var typeParams = typeElement.getTypeParameters();
        if (!typeParams.isEmpty()) {
            // Generate id attributes if @param tags are missing for type parameters.
            // Note that this does not handle the case where some but not all @param tags are missing.
            var needsId = !utils.hasBlockTag(typeElement, DocTree.Kind.PARAM);
            var linkInfo = new HtmlLinkInfo(configuration,
                    HtmlLinkInfo.Kind.SHOW_TYPE_PARAMS_AND_BOUNDS, typeElement)
                    .linkToSelf(false);  // Let's not link to ourselves in the header
            content.add("<");
            var first = true;
            boolean longTypeParams = typeParams.stream()
                    .map(t -> getLink(linkInfo.forType(t.asType())))
                    .anyMatch(t -> t.charCount() > ClassWriter.LONG_TYPE_PARAM);
            for (TypeParameterElement t : typeParams) {
                if (!first) {
                    if (longTypeParams) {
                        content.add(", ");
                    } else {
                        content.add(",").add(HtmlTree.WBR());
                    }
                }
                var typeParamLink = getLink(linkInfo.forType(t.asType()));
                content.add(needsId
                        ? HtmlTree.SPAN_ID(htmlIds.forTypeParam(t.getSimpleName().toString(), typeElement), typeParamLink)
                        : typeParamLink);
                first = false;
            }
            content.add(">");
        }
        return content;
    }

    protected Content getClassContentHeader() {
        return getContentHeader();
    }

    protected void addFooter() {
        bodyContents.addMainContent(MarkerComments.END_OF_CLASS_DATA);
        bodyContents.setFooter(getFooter());
    }

    protected void printDocument(Content content) throws DocFileIOException {
        String description = getDescription("declaration", typeElement);
        PackageElement pkg = utils.containingPackage(typeElement);
        List<DocPath> localStylesheets = getLocalStylesheets(pkg);
        content.add(bodyContents);
        printHtmlDocument(configuration.metakeywords.getMetaKeywords(typeElement),
                description, localStylesheets, content);
    }

    protected Content getClassInfo(Content classInfo) {
        return getMember(HtmlIds.CLASS_DESCRIPTION, HtmlStyles.classDescription, classInfo);
    }

    @Override
    public TypeElement getCurrentTypeElement() {
        return typeElement;
    }

    protected void addClassSignature(Content classInfo) {
        classInfo.add(new Signatures.TypeSignature(typeElement, this)
                .toContent());
    }

    protected void addClassDescription(Content classInfo) {
        addPreviewInfo(classInfo);
        tableOfContents.addLink(HtmlIds.TOP_OF_PAGE, contents.descriptionLabel,
                TableOfContents.Level.FIRST);
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

    protected void addClassTagInfo(Content classInfo) {
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
            var entry = HtmlTree.DIV(HtmlStyles.inheritance, getClassHelperContent(type));
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
                    new HtmlLinkInfo(configuration, HtmlLinkInfo.Kind.SHOW_TYPE_PARAMS,
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
                    HtmlLinkInfo.Kind.SHOW_TYPE_PARAMS, type)
                    .label(configuration.getClassName(utils.asTypeElement(type))));
            result.add(link);
        }
        return result;
    }

    protected void addClassTree(Content target) {
        if (!utils.isClass(typeElement)) {
            return;
        }
        target.add(getClassInheritanceTreeContent(typeElement.asType()));
    }

    protected void addParamInfo(Content target) {
        if (utils.hasBlockTag(typeElement, DocTree.Kind.PARAM)) {
            var t = configuration.tagletManager.getTaglet(DocTree.Kind.PARAM);
            Content paramInfo = t.getAllBlockTagOutput(typeElement, getTagletWriterInstance(false));
            if (!paramInfo.isEmpty()) {
                target.add(HtmlTree.DL(HtmlStyles.notes, paramInfo));
            }
        }
    }

    protected void addSubClassInfo(Content target) {
        if (utils.isClass(typeElement)) {
            for (String s : suppressSubtypesSet) {
                if (typeElement.getQualifiedName().contentEquals(s)) {
                    return;    // Don't generate the list, too huge
                }
            }
            Set<TypeElement> subclasses = classTree.hierarchy(typeElement).subtypes(typeElement);
            if (!subclasses.isEmpty()) {
                var dl = HtmlTree.DL(HtmlStyles.notes);
                dl.add(HtmlTree.DT(contents.subclassesLabel));
                dl.add(HtmlTree.DD(getClassLinks(HtmlLinkInfo.Kind.PLAIN, subclasses)));
                target.add(dl);
            }
        }
    }

    protected void addSubInterfacesInfo(Content target) {
        if (utils.isPlainInterface(typeElement)) {
            Set<TypeElement> subInterfaces = classTree.hierarchy(typeElement).allSubtypes(typeElement);
            if (!subInterfaces.isEmpty()) {
                var dl = HtmlTree.DL(HtmlStyles.notes);
                dl.add(HtmlTree.DT(contents.subinterfacesLabel));
                dl.add(HtmlTree.DD(getClassLinks(HtmlLinkInfo.Kind.SHOW_TYPE_PARAMS, subInterfaces)));
                target.add(dl);
            }
        }
    }

    protected void addInterfaceUsageInfo(Content target) {
        if (!utils.isPlainInterface(typeElement)) {
            return;
        }
        for (String s : suppressImplementingSet) {
            if (typeElement.getQualifiedName().contentEquals(s)) {
                return;    // Don't generate the list, too huge
            }
        }
        Set<TypeElement> implcl = classTree.implementingClasses(typeElement);
        if (!implcl.isEmpty()) {
            var dl = HtmlTree.DL(HtmlStyles.notes);
            dl.add(HtmlTree.DT(contents.implementingClassesLabel));
            dl.add(HtmlTree.DD(getClassLinks(HtmlLinkInfo.Kind.PLAIN, implcl)));
            target.add(dl);
        }
    }

    protected void addImplementedInterfacesInfo(Content target) {
        SortedSet<TypeMirror> interfaces = new TreeSet<>(comparators.typeMirrorClassUseComparator());
        interfaces.addAll(utils.getAllInterfaces(typeElement));
        if (utils.isClass(typeElement) && !interfaces.isEmpty()) {
            var dl = HtmlTree.DL(HtmlStyles.notes);
            dl.add(HtmlTree.DT(contents.allImplementedInterfacesLabel));
            dl.add(HtmlTree.DD(getClassLinks(HtmlLinkInfo.Kind.SHOW_TYPE_PARAMS, interfaces)));
            target.add(dl);
        }
    }

    protected void addSuperInterfacesInfo(Content target) {
        SortedSet<TypeMirror> interfaces =
                new TreeSet<>(comparators.typeMirrorIndexUseComparator());
        interfaces.addAll(utils.getAllInterfaces(typeElement));

        if (utils.isPlainInterface(typeElement) && !interfaces.isEmpty()) {
            var dl = HtmlTree.DL(HtmlStyles.notes);
            dl.add(HtmlTree.DT(contents.allSuperinterfacesLabel));
            dl.add(HtmlTree.DD(getClassLinks(HtmlLinkInfo.Kind.SHOW_TYPE_PARAMS, interfaces)));
            target.add(dl);
        }
    }

    protected void addNestedClassInfo(final Content target) {
        Element outerClass = typeElement.getEnclosingElement();
        if (outerClass == null)
            return;
        new SimpleElementVisitor8<Void, Void>() {
            @Override
            public Void visitType(TypeElement e, Void p) {
                var dl = HtmlTree.DL(HtmlStyles.notes);
                dl.add(HtmlTree.DT(utils.isPlainInterface(e)
                        ? contents.enclosingInterfaceLabel
                        : contents.enclosingClassLabel));
                dl.add(HtmlTree.DD(getClassLinks(HtmlLinkInfo.Kind.LINK_TYPE_PARAMS_AND_BOUNDS, List.of(e))));
                target.add(dl);
                return null;
            }
        }.visit(outerClass);
    }

    protected void addFunctionalInterfaceInfo (Content target) {
        if (utils.isFunctionalInterface(typeElement)) {
            var dl = HtmlTree.DL(HtmlStyles.notes)
                .add(HtmlTree.DT(contents.functionalInterface))
                .add(HtmlTree.DD(contents.functionalInterfaceMessage));
            target.add(dl);
        }
    }

    protected void addClassDeprecationInfo(Content classInfo) {
        List<? extends DeprecatedTree> deprs = utils.getDeprecatedTrees(typeElement);
        if (utils.isDeprecated(typeElement)) {
            var deprLabel = HtmlTree.SPAN(HtmlStyles.deprecatedLabel, getDeprecatedPhrase(typeElement));
            var div = HtmlTree.DIV(HtmlStyles.deprecationBlock, deprLabel);
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
                content.add(getLink(
                        new HtmlLinkInfo(configuration, context, te)));
            } else {
                content.add(getLink(
                        new HtmlLinkInfo(configuration, context, ((TypeMirror)type))));
            }
        }
        return HtmlTree.CODE(content);
    }

    /**
     * Return the TypeElement being documented.
     *
     * @return the TypeElement being documented.
     */
    public TypeElement getTypeElement() {
        return typeElement;
    }

    protected Content getMemberDetails(Content content) {
        var section = HtmlTree.SECTION(HtmlStyles.details, content);
        // The following id is required by the Navigation bar
        if (utils.isAnnotationInterface(typeElement)) {
            section.setId(HtmlIds.ANNOTATION_TYPE_ELEMENT_DETAIL);
        }
        return section;
    }

    @Override
    public boolean isIndexable() {
        return true;
    }
}
