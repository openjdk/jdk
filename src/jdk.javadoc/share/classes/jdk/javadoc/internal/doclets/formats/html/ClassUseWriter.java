/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.TagName;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.toolkit.DocletException;
import jdk.javadoc.internal.doclets.toolkit.util.ClassTree;
import jdk.javadoc.internal.doclets.toolkit.util.ClassUseMapper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;


/**
 * Generate class usage information.
 */
public class ClassUseWriter extends SubWriterHolderWriter {

    final TypeElement typeElement;
    Set<PackageElement> pkgToPackageAnnotations = null;
    final Map<PackageElement, List<Element>> pkgToClassTypeParameter;
    final Map<PackageElement, List<Element>> pkgToSubclassTypeParameter;
    final Map<PackageElement, List<Element>> pkgToSubinterfaceTypeParameter;
    final Map<PackageElement, List<Element>> pkgToImplementsTypeParameter;
    final Map<PackageElement, List<Element>> pkgToClassAnnotations;
    final Map<PackageElement, List<Element>> pkgToMethodTypeParameter;
    final Map<PackageElement, List<Element>> pkgToMethodArgTypeParameter;
    final Map<PackageElement, List<Element>> pkgToMethodReturnTypeParameter;
    final Map<PackageElement, List<Element>> pkgToMethodAnnotations;
    final Map<PackageElement, List<Element>> pkgToMethodParameterAnnotations;
    final Map<PackageElement, List<Element>> pkgToFieldTypeParameter;
    final Map<PackageElement, List<Element>> pkgToFieldAnnotations;
    final Map<PackageElement, List<Element>> pkgToSubclass;
    final Map<PackageElement, List<Element>> pkgToSubinterface;
    final Map<PackageElement, List<Element>> pkgToImplementingClass;
    final Map<PackageElement, List<Element>> pkgToField;
    final Map<PackageElement, List<Element>> pkgToMethodReturn;
    final Map<PackageElement, List<Element>> pkgToMethodArgs;
    final Map<PackageElement, List<Element>> pkgToMethodThrows;
    final Map<PackageElement, List<Element>> pkgToConstructorAnnotations;
    final Map<PackageElement, List<Element>> pkgToConstructorParameterAnnotations;
    final Map<PackageElement, List<Element>> pkgToConstructorArgs;
    final Map<PackageElement, List<Element>> pkgToConstructorArgTypeParameter;
    final Map<PackageElement, List<Element>> pkgToConstructorThrows;
    final SortedSet<PackageElement> pkgSet;
    final MethodWriter methodSubWriter;
    final ConstructorWriter constrSubWriter;
    final FieldWriter fieldSubWriter;
    final NestedClassWriter classSubWriter;

    /**
     * Creates a writer for a page listing the uses of a type element.
     *
     * @param configuration the configuration
     * @param mapper a "mapper" containing the usage information
     * @param typeElement the type element
     */
    public ClassUseWriter(HtmlConfiguration configuration,
                          ClassUseMapper mapper,
                          TypeElement typeElement) {
        super(configuration, pathFor(configuration, typeElement));
        this.typeElement = typeElement;
        if (mapper.classToPackageAnnotations.containsKey(typeElement)) {
            pkgToPackageAnnotations = new TreeSet<>(comparators.classUseComparator());
            pkgToPackageAnnotations.addAll(mapper.classToPackageAnnotations.get(typeElement));
        }
        configuration.currentTypeElement = typeElement;
        this.pkgSet = new TreeSet<>(comparators.packageComparator());
        this.pkgToClassTypeParameter = pkgDivide(mapper.classToClassTypeParam);
        this.pkgToSubclassTypeParameter = pkgDivide(mapper.classToSubclassTypeParam);
        this.pkgToSubinterfaceTypeParameter = pkgDivide(mapper.classToSubinterfaceTypeParam);
        this.pkgToImplementsTypeParameter = pkgDivide(mapper.classToImplementsTypeParam);
        this.pkgToClassAnnotations = pkgDivide(mapper.classToClassAnnotations);
        this.pkgToMethodTypeParameter = pkgDivide(mapper.classToMethodTypeParam);
        this.pkgToMethodArgTypeParameter = pkgDivide(mapper.classToMethodArgTypeParam);
        this.pkgToFieldTypeParameter = pkgDivide(mapper.classToFieldTypeParam);
        this.pkgToFieldAnnotations = pkgDivide(mapper.annotationToField);
        this.pkgToMethodReturnTypeParameter = pkgDivide(mapper.classToMethodReturnTypeParam);
        this.pkgToMethodAnnotations = pkgDivide(mapper.classToMethodAnnotations);
        this.pkgToMethodParameterAnnotations = pkgDivide(mapper.classToMethodParamAnnotation);
        this.pkgToSubclass = pkgDivide(mapper.classToSubclass);
        this.pkgToSubinterface = pkgDivide(mapper.classToSubinterface);
        this.pkgToImplementingClass = pkgDivide(mapper.classToImplementingClass);
        this.pkgToField = pkgDivide(mapper.classToField);
        this.pkgToMethodReturn = pkgDivide(mapper.classToMethodReturn);
        this.pkgToMethodArgs = pkgDivide(mapper.classToMethodArgs);
        this.pkgToMethodThrows = pkgDivide(mapper.classToMethodThrows);
        this.pkgToConstructorAnnotations = pkgDivide(mapper.classToConstructorAnnotations);
        this.pkgToConstructorParameterAnnotations = pkgDivide(mapper.classToConstructorParamAnnotation);
        this.pkgToConstructorArgs = pkgDivide(mapper.classToConstructorArgs);
        this.pkgToConstructorArgTypeParameter = pkgDivide(mapper.classToConstructorArgTypeParam);
        this.pkgToConstructorThrows = pkgDivide(mapper.classToConstructorThrows);
        //tmp test
        if (pkgSet.size() > 0 &&
            mapper.classToPackage.containsKey(this.typeElement) &&
            !pkgSet.equals(mapper.classToPackage.get(this.typeElement))) {
            configuration.reporter.print(Diagnostic.Kind.WARNING,
                    "Internal error: package sets don't match: "
                    + pkgSet + " with: " + mapper.classToPackage.get(this.typeElement));
        }

        methodSubWriter = new MethodWriter(this);
        constrSubWriter = new ConstructorWriter(this);
        constrSubWriter.setFoundNonPubConstructor(true);
        fieldSubWriter = new FieldWriter(this);
        classSubWriter = new NestedClassWriter(this);
    }

    private static DocPath pathFor(HtmlConfiguration configuration, TypeElement typeElement) {
        return configuration.docPaths.forPackage(typeElement)
                .resolve(DocPaths.CLASS_USE)
                .resolve(configuration.docPaths.forName( typeElement));
    }

    /**
     * Write out class use and package use pages.
     *
     * @param configuration the configuration for this doclet
     * @param classTree the class tree hierarchy
     * @throws DocletException if there is an error while generating the documentation
     */
    public static void generate(HtmlConfiguration configuration, ClassTree classTree) throws DocletException {
        var writerFactory = configuration.getWriterFactory();
        var mapper = new ClassUseMapper(configuration, classTree);
        boolean nodeprecated = configuration.getOptions().noDeprecated();
        Utils utils = configuration.utils;
        for (TypeElement aClass : configuration.getIncludedTypeElements()) {
            // If -nodeprecated option is set and the containing package is marked
            // as deprecated, do not generate the class-use page. We will still generate
            // the class-use page if the class is marked as deprecated but the containing
            // package is not since it could still be linked from that package-use page.
            if (!(nodeprecated && utils.isDeprecated(utils.containingPackage(aClass)))) {
                writerFactory.newClassUseWriter(aClass, mapper).buildPage();
            }
        }
        for (PackageElement pkg : configuration.packages) {
            // If -nodeprecated option is set and the package is marked
            // as deprecated, do not generate the package-use page.
            if (!(nodeprecated && utils.isDeprecated(pkg))) {
                writerFactory.newPackageUseWriter(pkg, mapper).buildPage();
            }
        }
    }

    private Map<PackageElement, List<Element>> pkgDivide(Map<TypeElement, ? extends List<? extends Element>> classMap) {
        Map<PackageElement, List<Element>> map = new HashMap<>();
        List<? extends Element> elements = classMap.get(typeElement);
        if (elements != null) {
            elements.sort(comparators.classUseComparator());
            for (Element e : elements) {
                PackageElement pkg = utils.containingPackage(e);
                pkgSet.add(pkg);
                map.computeIfAbsent(pkg, k -> new ArrayList<>()).add(e);
            }
        }
        return map;
    }

    /**
     * Generate the class use elements.
     *
     * @throws DocFileIOException if there is a problem while generating the documentation
     */
    @Override
    public void buildPage() throws DocFileIOException {
        HtmlTree body = getClassUseHeader();
        Content mainContent = new ContentBuilder();
        if (pkgSet.size() > 0) {
            addClassUse(mainContent);
        } else {
            mainContent.add(contents.getContent("doclet.ClassUse_No.usage.of.0",
                    utils.getFullyQualifiedName(typeElement)));
        }
        bodyContents.addMainContent(mainContent);
        bodyContents.setFooter(getFooter());
        body.add(bodyContents);
        String description = getDescription("use", typeElement);
        printHtmlDocument(null, description, body);
    }

    /**
     * Add the class use documentation.
     *
     * @param content the content to which the class use information will be added
     */
    protected void addClassUse(Content content) {
        Content c = new ContentBuilder();
        if (configuration.packages.size() > 1) {
            addPackageList(c);
            addPackageAnnotationList(c);
        }
        addClassList(c);
        content.add(c);
    }

    /**
     * Add the packages elements that use the given class.
     *
     * @param content the content to which the packages elements will be added
     */
    protected void addPackageList(Content content) {
        Content caption = contents.getContent(
                "doclet.ClassUse_Packages.that.use.0",
                getLink(new HtmlLinkInfo(configuration,
                        HtmlLinkInfo.Kind.PLAIN, typeElement)));
        var table = new Table<Void>(HtmlStyle.summaryTable)
                .setCaption(caption)
                .setHeader(getPackageTableHeader())
                .setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colLast);
        for (PackageElement pkg : pkgSet) {
            addPackageUse(pkg, table);
        }
        content.add(table);
    }

    /**
     * Add the package annotation elements.
     *
     * @param content the content to which the package annotation elements will be added
     */
    protected void addPackageAnnotationList(Content content) {
        if (!utils.isAnnotationInterface(typeElement) ||
                pkgToPackageAnnotations == null ||
                pkgToPackageAnnotations.isEmpty()) {
            return;
        }
        Content caption = contents.getContent(
                "doclet.ClassUse_PackageAnnotation",
                getLink(new HtmlLinkInfo(configuration,
                        HtmlLinkInfo.Kind.PLAIN, typeElement)));

        var table = new Table<Void>(HtmlStyle.summaryTable)
                .setCaption(caption)
                .setHeader(getPackageTableHeader())
                .setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colLast);
        for (PackageElement pkg : pkgToPackageAnnotations) {
            Content summary = new ContentBuilder();
            addSummaryComment(pkg, summary);
            table.addRow(getPackageLink(pkg, getLocalizedPackageName(pkg)), summary);
        }
        content.add(table);
    }

    /**
     * Add the class elements that use the given class.
     *
     * @param content the content to which the class elements will be added
     */
    protected void addClassList(Content content) {
        var ul = HtmlTree.UL(HtmlStyle.blockList);
        for (PackageElement pkg : pkgSet) {
            var section = HtmlTree.SECTION(HtmlStyle.detail)
                    .setId(htmlIds.forPackage(pkg));
            Content link = contents.getContent("doclet.ClassUse_Uses.of.0.in.1",
                    getLink(new HtmlLinkInfo(configuration, HtmlLinkInfo.Kind.PLAIN,
                            typeElement)),
                    getPackageLink(pkg, getLocalizedPackageName(pkg)));
            var heading = HtmlTree.HEADING(Headings.TypeUse.SUMMARY_HEADING, link);
            section.add(heading);
            addClassUse(pkg, section);
            ul.add(HtmlTree.LI(section));
        }
        var li = HtmlTree.SECTION(HtmlStyle.classUses, ul);
        content.add(li);
    }

    /**
     * Add the package use information.
     *
     * @param pkg the package that uses the given class
     * @param table the table to which the package use information will be added
     */
    protected void addPackageUse(PackageElement pkg, Table<?> table) {
        Content pkgLink =
                links.createLink(htmlIds.forPackage(pkg), getLocalizedPackageName(pkg));
        Content summary = new ContentBuilder();
        addSummaryComment(pkg, summary);
        table.addRow(pkgLink, summary);
    }

    /**
     * Add the class use information.
     *
     * @param pkg the package that uses the given class
     * @param content the content to which the class use information will be added
     */
    protected void addClassUse(PackageElement pkg, Content content) {
        Content classLink = getLink(new HtmlLinkInfo(configuration,
            HtmlLinkInfo.Kind.PLAIN, typeElement));
        Content pkgLink = getPackageLink(pkg, getLocalizedPackageName(pkg));
        classSubWriter.addUseInfo(pkgToClassAnnotations.get(pkg),
                contents.getContent("doclet.ClassUse_Annotation", classLink,
                pkgLink), content);
        classSubWriter.addUseInfo(pkgToClassTypeParameter.get(pkg),
                contents.getContent("doclet.ClassUse_TypeParameter", classLink,
                pkgLink), content);
        classSubWriter.addUseInfo(pkgToSubclass.get(pkg),
                contents.getContent("doclet.ClassUse_Subclass", classLink,
                pkgLink), content);
        classSubWriter.addUseInfo(pkgToSubinterface.get(pkg),
                contents.getContent("doclet.ClassUse_Subinterface", classLink,
                pkgLink), content);
        classSubWriter.addUseInfo(pkgToImplementingClass.get(pkg),
                contents.getContent("doclet.ClassUse_ImplementingClass", classLink,
                pkgLink), content);
        classSubWriter.addUseInfo(pkgToSubclassTypeParameter.get(pkg),
                contents.getContent("doclet.ClassUse_SubclassTypeParameter", classLink,
                pkgLink), content);
        classSubWriter.addUseInfo(pkgToSubinterfaceTypeParameter.get(pkg),
                contents.getContent("doclet.ClassUse_SubinterfaceTypeParameter", classLink,
                pkgLink), content);
        classSubWriter.addUseInfo(pkgToImplementsTypeParameter.get(pkg),
                contents.getContent("doclet.ClassUse_ImplementsTypeParameter", classLink,
                pkgLink), content);
        fieldSubWriter.addUseInfo(pkgToField.get(pkg),
                contents.getContent("doclet.ClassUse_Field", classLink,
                pkgLink), content);
        fieldSubWriter.addUseInfo(pkgToFieldAnnotations.get(pkg),
                contents.getContent("doclet.ClassUse_FieldAnnotations", classLink,
                pkgLink), content);
        fieldSubWriter.addUseInfo(pkgToFieldTypeParameter.get(pkg),
                contents.getContent("doclet.ClassUse_FieldTypeParameter", classLink,
                pkgLink), content);
        methodSubWriter.addUseInfo(pkgToMethodAnnotations.get(pkg),
                contents.getContent("doclet.ClassUse_MethodAnnotations", classLink,
                pkgLink), content);
        methodSubWriter.addUseInfo(pkgToMethodParameterAnnotations.get(pkg),
                contents.getContent("doclet.ClassUse_MethodParameterAnnotations", classLink,
                pkgLink), content);
        methodSubWriter.addUseInfo(pkgToMethodTypeParameter.get(pkg),
                contents.getContent("doclet.ClassUse_MethodTypeParameter", classLink,
                pkgLink), content);
        methodSubWriter.addUseInfo(pkgToMethodReturn.get(pkg),
                contents.getContent("doclet.ClassUse_MethodReturn", classLink,
                pkgLink), content);
        methodSubWriter.addUseInfo(pkgToMethodReturnTypeParameter.get(pkg),
                contents.getContent("doclet.ClassUse_MethodReturnTypeParameter", classLink,
                pkgLink), content);
        methodSubWriter.addUseInfo(pkgToMethodArgs.get(pkg),
                contents.getContent("doclet.ClassUse_MethodArgs", classLink,
                pkgLink), content);
        methodSubWriter.addUseInfo(pkgToMethodArgTypeParameter.get(pkg),
                contents.getContent("doclet.ClassUse_MethodArgsTypeParameters", classLink,
                pkgLink), content);
        methodSubWriter.addUseInfo(pkgToMethodThrows.get(pkg),
                contents.getContent("doclet.ClassUse_MethodThrows", classLink,
                pkgLink), content);
        constrSubWriter.addUseInfo(pkgToConstructorAnnotations.get(pkg),
                contents.getContent("doclet.ClassUse_ConstructorAnnotations", classLink,
                pkgLink), content);
        constrSubWriter.addUseInfo(pkgToConstructorParameterAnnotations.get(pkg),
                contents.getContent("doclet.ClassUse_ConstructorParameterAnnotations", classLink,
                pkgLink), content);
        constrSubWriter.addUseInfo(pkgToConstructorArgs.get(pkg),
                contents.getContent("doclet.ClassUse_ConstructorArgs", classLink,
                pkgLink), content);
        constrSubWriter.addUseInfo(pkgToConstructorArgTypeParameter.get(pkg),
                contents.getContent("doclet.ClassUse_ConstructorArgsTypeParameters", classLink,
                pkgLink), content);
        constrSubWriter.addUseInfo(pkgToConstructorThrows.get(pkg),
                contents.getContent("doclet.ClassUse_ConstructorThrows", classLink,
                pkgLink), content);
    }

    /**
     * Get the header for the class use listing.
     *
     * @return the class use header
     */
    protected HtmlTree getClassUseHeader() {
        String cltype = resources.getText(switch (typeElement.getKind()) {
            case ANNOTATION_TYPE -> "doclet.AnnotationType";
            case INTERFACE -> "doclet.Interface";
            case RECORD -> "doclet.RecordClass";
            case ENUM -> "doclet.Enum";
            default -> "doclet.Class";
        });
        String clname = utils.getFullyQualifiedName(typeElement);
        String title = resources.getText("doclet.Window_ClassUse_Header",
                cltype, clname);
        HtmlTree body = getBody(getWindowTitle(title));
        ContentBuilder headingContent = new ContentBuilder();
        headingContent.add(contents.getContent("doclet.ClassUse_Title", cltype));
        headingContent.add(new HtmlTree(TagName.BR));
        headingContent.add(clname);
        var heading = HtmlTree.HEADING_TITLE(Headings.PAGE_TITLE_HEADING,
                HtmlStyle.title, headingContent);
        var div = HtmlTree.DIV(HtmlStyle.header, heading);
        bodyContents.setHeader(getHeader(PageMode.USE, typeElement)).addMainContent(div);
        return body;
    }
}

