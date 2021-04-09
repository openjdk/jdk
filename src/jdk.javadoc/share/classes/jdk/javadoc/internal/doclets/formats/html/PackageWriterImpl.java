/*
 * Copyright (c) 1997, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.util.SortedSet;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import com.sun.source.doctree.DeprecatedTree;
import com.sun.source.doctree.DocTree;
import jdk.javadoc.internal.doclets.formats.html.markup.BodyContents;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.Entity;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlId;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.TagName;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.Text;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.PackageSummaryWriter;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;

/**
 * Class to generate file for each package contents in the right-hand
 * frame. This will list all the Class Kinds in the package. A click on any
 * class-kind will update the frame with the clicked class-kind page.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class PackageWriterImpl extends HtmlDocletWriter
    implements PackageSummaryWriter {

    /**
     * The package being documented.
     */
    protected PackageElement packageElement;

    private List<PackageElement> relatedPackages;
    private SortedSet<TypeElement> interfaces;
    private SortedSet<TypeElement> classes;
    private SortedSet<TypeElement> enums;
    private SortedSet<TypeElement> exceptions;
    private SortedSet<TypeElement> errors;
    private SortedSet<TypeElement> records;
    private SortedSet<TypeElement> annotationTypes;

    /**
     * The HTML tree for section tag.
     */
    protected HtmlTree sectionTree = HtmlTree.SECTION(HtmlStyle.packageDescription, new ContentBuilder());

    private final BodyContents bodyContents = new BodyContents();

    // Maximum number of subpackages and sibling packages to list in related packages table
    private final static int MAX_SUBPACKAGES = 20;
    private final static int MAX_SIBLING_PACKAGES = 5;


    /**
     * Constructor to construct PackageWriter object and to generate
     * "package-summary.html" file in the respective package directory.
     * For example for package "java.lang" this will generate file
     * "package-summary.html" file in the "java/lang" directory. It will also
     * create "java/lang" directory in the current or the destination directory
     * if it doesn't exist.
     *
     * @param configuration the configuration of the doclet.
     * @param packageElement    PackageElement under consideration.
     */
    public PackageWriterImpl(HtmlConfiguration configuration, PackageElement packageElement) {
        super(configuration,
                configuration.docPaths.forPackage(packageElement)
                .resolve(DocPaths.PACKAGE_SUMMARY));
        this.packageElement = packageElement;
        computePackageData();
    }

    @Override
    public Content getPackageHeader() {
        String packageName = getLocalizedPackageName(packageElement).toString();
        HtmlTree bodyTree = getBody(getWindowTitle(packageName));
        HtmlTree div = new HtmlTree(TagName.DIV);
        div.setStyle(HtmlStyle.header);
        if (configuration.showModules) {
            ModuleElement mdle = configuration.docEnv.getElementUtils().getModuleOf(packageElement);
            Content classModuleLabel = HtmlTree.SPAN(HtmlStyle.moduleLabelInPackage, contents.moduleLabel);
            Content moduleNameDiv = HtmlTree.DIV(HtmlStyle.subTitle, classModuleLabel);
            moduleNameDiv.add(Entity.NO_BREAK_SPACE);
            moduleNameDiv.add(getModuleLink(mdle,
                    Text.of(mdle.getQualifiedName().toString())));
            div.add(moduleNameDiv);
        }
        Content packageHead = new ContentBuilder();
        if (!packageElement.isUnnamed()) {
            packageHead.add(contents.packageLabel).add(" ");
        }
        packageHead.add(packageName);
        Content tHeading = HtmlTree.HEADING_TITLE(Headings.PAGE_TITLE_HEADING,
                HtmlStyle.title, packageHead);
        div.add(tHeading);
        bodyContents.setHeader(getHeader(PageMode.PACKAGE, packageElement))
                .addMainContent(div);
        return bodyTree;
    }

    @Override
    public Content getContentHeader() {
        return new ContentBuilder();
    }

    private void computePackageData() {
        relatedPackages = findRelatedPackages();
        boolean isSpecified = utils.isSpecified(packageElement);
        interfaces = filterClasses(isSpecified
                ? utils.getTypeElementsAsSortedSet(utils.getInterfaces(packageElement))
                : configuration.typeElementCatalog.interfaces(packageElement));
        classes = filterClasses(isSpecified
                ? utils.getTypeElementsAsSortedSet(utils.getOrdinaryClasses(packageElement))
                : configuration.typeElementCatalog.ordinaryClasses(packageElement));
        enums = filterClasses(isSpecified
                ? utils.getTypeElementsAsSortedSet(utils.getEnums(packageElement))
                : configuration.typeElementCatalog.enums(packageElement));
        records = filterClasses(isSpecified
                ? utils.getTypeElementsAsSortedSet(utils.getRecords(packageElement))
                : configuration.typeElementCatalog.records(packageElement));
        exceptions = filterClasses(isSpecified
                ? utils.getTypeElementsAsSortedSet(utils.getExceptions(packageElement))
                : configuration.typeElementCatalog.exceptions(packageElement));
        errors = filterClasses(isSpecified
                ? utils.getTypeElementsAsSortedSet(utils.getErrors(packageElement))
                : configuration.typeElementCatalog.errors(packageElement));
        annotationTypes = filterClasses(isSpecified
                ? utils.getTypeElementsAsSortedSet(utils.getAnnotationTypes(packageElement))
                : configuration.typeElementCatalog.annotationTypes(packageElement));
    }

    private SortedSet<TypeElement> filterClasses(SortedSet<TypeElement> types) {
        List<TypeElement> typeList = types
                .stream()
                .filter(te -> utils.isCoreClass(te) && configuration.isGeneratedDoc(te))
                .collect(Collectors.toList());
        return utils.filterOutPrivateClasses(typeList, options.javafx());
    }

    private List<PackageElement> findRelatedPackages() {
        String pkgName = packageElement.getQualifiedName().toString();

        // always add super package
        int lastdot = pkgName.lastIndexOf('.');
        String pkgPrefix = lastdot > 0 ? pkgName.substring(0, lastdot) : null;
        List<PackageElement> packages = new ArrayList<>(
                filterPackages(p -> p.getQualifiedName().toString().equals(pkgPrefix)));

        // add subpackages unless there are very many of them
        Pattern subPattern = Pattern.compile(pkgName.replace(".", "\\.") + "\\.\\w+");
        List<PackageElement> subpackages = filterPackages(
                p -> subPattern.matcher(p.getQualifiedName().toString()).matches());
        if (subpackages.size() <= MAX_SUBPACKAGES) {
            packages.addAll(subpackages);
        }

        // only add sibling packages if we are beneath threshold, and number of siblings is beneath threshold as well
        if (pkgPrefix != null && packages.size() <= MAX_SIBLING_PACKAGES) {
            Pattern siblingPattern = Pattern.compile(pkgPrefix.replace(".", "\\.") + "\\.\\w+");

            List<PackageElement> siblings = filterPackages(
                    p -> siblingPattern.matcher(p.getQualifiedName().toString()).matches());
            if (siblings.size() <= MAX_SIBLING_PACKAGES) {
                packages.addAll(siblings);
            }
        }
        return packages;
    }
    @Override
    protected Navigation getNavBar(PageMode pageMode, Element element) {
        Content linkContent = getModuleLink(utils.elementUtils.getModuleOf(packageElement),
                contents.moduleLabel);
        return super.getNavBar(pageMode, element)
                .setNavLinkModule(linkContent)
                .setSubNavLinks(() -> {
                    List<Content> list = new ArrayList<>();
                    if (!utils.getFullBody(packageElement).isEmpty() && !options.noComment()) {
                        list.add(HtmlTree.LI(links.createLink(HtmlIds.PACKAGE_DESCRIPTION, contents.navDescription)));
                    }
                    subNavLink(list, relatedPackages, HtmlIds.RELATED_PACKAGE_SUMMARY, contents.navRelated);
                    subNavLink(list, interfaces, HtmlIds.INTERFACE_SUMMARY, contents.interfaces);
                    subNavLink(list, classes, HtmlIds.CLASS_SUMMARY, contents.classes);
                    subNavLink(list, enums, HtmlIds.ENUM_SUMMARY, contents.navEnums);
                    subNavLink(list, records, HtmlIds.RECORD_SUMMARY, contents.navRecords);
                    subNavLink(list, exceptions, HtmlIds.EXCEPTION_SUMMARY, contents.exceptions);
                    subNavLink(list, errors, HtmlIds.ERROR_SUMMARY, contents.errors);
                    subNavLink(list, annotationTypes, HtmlIds.ANNOTATION_TYPE_SUMMARY, contents.navAnnotations);
                    return list;
                });
    }

    private void subNavLink(List<Content> list, Collection<? extends Element> elements, HtmlId id, Content label) {
        if (elements != null && !elements.isEmpty()) {
            list.add(HtmlTree.LI(links.createLink(id, label)));
        }
    }

    /**
     * Add the package deprecation information to the documentation tree.
     *
     * @param div the content tree to which the deprecation information will be added
     */
    public void addDeprecationInfo(Content div) {
        List<? extends DeprecatedTree> deprs = utils.getDeprecatedTrees(packageElement);
        if (utils.isDeprecated(packageElement)) {
            CommentHelper ch = utils.getCommentHelper(packageElement);
            HtmlTree deprDiv = new HtmlTree(TagName.DIV);
            deprDiv.setStyle(HtmlStyle.deprecationBlock);
            Content deprPhrase = HtmlTree.SPAN(HtmlStyle.deprecatedLabel, getDeprecatedPhrase(packageElement));
            deprDiv.add(deprPhrase);
            if (!deprs.isEmpty()) {
                List<? extends DocTree> commentTags = ch.getDescription(deprs.get(0));
                if (!commentTags.isEmpty()) {
                    addInlineDeprecatedComment(packageElement, deprs.get(0), deprDiv);
                }
            }
            div.add(deprDiv);
        }
    }

    @Override
    public Content getSummariesList() {
        return new HtmlTree(TagName.UL).setStyle(HtmlStyle.summaryList);
    }

    @Override
    public void addRelatedPackagesSummary(Content summaryContentTree) {
        boolean showModules = configuration.showModules && hasRelatedPackagesInOtherModules(relatedPackages);
        TableHeader tableHeader= showModules
                ? new TableHeader(contents.moduleLabel, contents.packageLabel, contents.descriptionLabel)
                : new TableHeader(contents.packageLabel, contents.descriptionLabel);
        addPackageSummary(relatedPackages, contents.relatedPackages, tableHeader,
                summaryContentTree, showModules);
    }

    @Override
    public void addInterfaceSummary(Content summaryContentTree) {
        TableHeader tableHeader= new TableHeader(contents.interfaceLabel, contents.descriptionLabel);
        addClassesSummary(interfaces, contents.interfaceSummary, tableHeader, summaryContentTree,
                HtmlIds.INTERFACE_SUMMARY);
    }

    @Override
    public void addClassSummary(Content summaryContentTree) {
        TableHeader tableHeader= new TableHeader(contents.classLabel, contents.descriptionLabel);
        addClassesSummary(classes, contents.classSummary, tableHeader, summaryContentTree,
                HtmlIds.CLASS_SUMMARY);
    }

    @Override
    public void addEnumSummary(Content summaryContentTree) {
        TableHeader tableHeader= new TableHeader(contents.enum_, contents.descriptionLabel);
        addClassesSummary(enums, contents.enumSummary, tableHeader, summaryContentTree,
                HtmlIds.ENUM_SUMMARY);
    }

    @Override
    public void addRecordSummary(Content summaryContentTree) {
        TableHeader tableHeader= new TableHeader(contents.record, contents.descriptionLabel);
        addClassesSummary(records, contents.recordSummary, tableHeader, summaryContentTree,
                HtmlIds.RECORD_SUMMARY);
    }

    @Override
    public void addExceptionSummary(Content summaryContentTree) {
        TableHeader tableHeader= new TableHeader(contents.exception, contents.descriptionLabel);
        addClassesSummary(exceptions, contents.exceptionSummary, tableHeader, summaryContentTree,
                HtmlIds.EXCEPTION_SUMMARY);
    }

    @Override
    public void addErrorSummary(Content summaryContentTree) {
        TableHeader tableHeader= new TableHeader(contents.error, contents.descriptionLabel);
        addClassesSummary(errors, contents.errorSummary, tableHeader, summaryContentTree,
                HtmlIds.ERROR_SUMMARY);
    }

    @Override
    public void addAnnotationTypeSummary(Content summaryContentTree) {
        TableHeader tableHeader= new TableHeader(contents.annotationType, contents.descriptionLabel);
        addClassesSummary(annotationTypes, contents.annotationTypeSummary, tableHeader, summaryContentTree,
                 HtmlIds.ANNOTATION_TYPE_SUMMARY);
    }

    public void addClassesSummary(SortedSet<TypeElement> classes, String label,
            TableHeader tableHeader, Content summaryContentTree, HtmlId id) {
        if(!classes.isEmpty()) {
            Table table = new Table(HtmlStyle.summaryTable)
                    .setCaption(Text.of(label))
                    .setHeader(tableHeader)
                    .setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colLast);

            for (TypeElement klass : classes) {
                Content classLink = getLink(new HtmlLinkInfo(
                        configuration, HtmlLinkInfo.Kind.PACKAGE, klass));
                ContentBuilder description = new ContentBuilder();
                addPreviewSummary(klass, description);
                if (utils.isDeprecated(klass)) {
                    description.add(getDeprecatedPhrase(klass));
                    List<? extends DeprecatedTree> tags = utils.getDeprecatedTrees(klass);
                    if (!tags.isEmpty()) {
                        addSummaryDeprecatedComment(klass, tags.get(0), description);
                    }
                } else {
                    addSummaryComment(klass, description);
                }
                table.addRow(classLink, description);
            }
            summaryContentTree.add(HtmlTree.LI(table).setId(id));
        }
    }

    public void addPackageSummary(List<PackageElement> packages, Content label,
                                  TableHeader tableHeader, Content summaryContentTree,
                                  boolean showModules) {
        if (!packages.isEmpty()) {
            Table table = new Table(HtmlStyle.summaryTable)
                    .setCaption(label)
                    .setHeader(tableHeader);
            if (showModules) {
                table.setColumnStyles(HtmlStyle.colPlain, HtmlStyle.colFirst, HtmlStyle.colLast);
            } else {
                table.setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colLast);
            }

            for (PackageElement pkg : packages) {
                Content packageLink = getPackageLink(pkg, Text.of(pkg.getQualifiedName()));
                Content moduleLink = HtmlTree.EMPTY;
                if (showModules) {
                    ModuleElement module = (ModuleElement) pkg.getEnclosingElement();
                    if (module != null && !module.isUnnamed()) {
                        moduleLink = getModuleLink(module, Text.of(module.getQualifiedName()));
                    }
                }
                ContentBuilder description = new ContentBuilder();
                addPreviewSummary(pkg, description);
                if (utils.isDeprecated(pkg)) {
                    description.add(getDeprecatedPhrase(pkg));
                    List<? extends DeprecatedTree> tags = utils.getDeprecatedTrees(pkg);
                    if (!tags.isEmpty()) {
                        addSummaryDeprecatedComment(pkg, tags.get(0), description);
                    }
                } else {
                    addSummaryComment(pkg, description);
                }
                if (showModules) {
                    table.addRow(moduleLink, packageLink, description);
                } else {
                    table.addRow(packageLink, description);
                }
            }
            summaryContentTree.add(HtmlTree.LI(table)
                    .setId(HtmlIds.RELATED_PACKAGE_SUMMARY));
        }
    }

    @Override
    public void addPackageDescription(Content packageContentTree) {
        addPreviewInfo(packageElement, packageContentTree);
        if (!utils.getBody(packageElement).isEmpty()) {
            HtmlTree tree = sectionTree;
            tree.setId(HtmlIds.PACKAGE_DESCRIPTION);
            addDeprecationInfo(tree);
            addInlineComment(packageElement, tree);
        }
    }

    @Override
    public void addPackageTags(Content packageContentTree) {
        Content htmlTree = sectionTree;
        addTagsInfo(packageElement, htmlTree);
        packageContentTree.add(sectionTree);
    }

    @Override
    public void addPackageSignature(Content packageContentTree) {
        packageContentTree.add(new HtmlTree(TagName.HR));
        packageContentTree.add(Signatures.getPackageSignature(packageElement, this));
    }

    @Override
    public void addPackageContent(Content packageContentTree) {
        bodyContents.addMainContent(packageContentTree);
    }

    @Override
    public void addPackageFooter() {
        bodyContents.setFooter(getFooter());
    }

    @Override
    public void printDocument(Content contentTree) throws DocFileIOException {
        String description = getDescription("declaration", packageElement);
        List<DocPath> localStylesheets = getLocalStylesheets(packageElement);
        contentTree.add(bodyContents);
        printHtmlDocument(configuration.metakeywords.getMetaKeywords(packageElement),
                description, localStylesheets, contentTree);
    }

    @Override
    public Content getPackageSummary(Content summaryContentTree) {
        return HtmlTree.SECTION(HtmlStyle.summary, summaryContentTree);
    }

    private boolean hasRelatedPackagesInOtherModules(List<PackageElement> relatedPackages) {
        final ModuleElement module = (ModuleElement) packageElement.getEnclosingElement();
        return relatedPackages.stream().anyMatch(pkg -> module != pkg.getEnclosingElement());
    }

    private List<PackageElement> filterPackages(Predicate<? super PackageElement> filter) {
        return configuration.packages.stream()
                .filter(p -> p != packageElement && filter.test(p))
                .collect(Collectors.toList());
    }
}
