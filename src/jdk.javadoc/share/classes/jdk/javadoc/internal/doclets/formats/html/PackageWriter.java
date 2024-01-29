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

import java.util.ArrayList;
import java.util.Arrays;
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
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.TagName;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.Text;
import jdk.javadoc.internal.doclets.toolkit.DocletException;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;

/**
 * Class to generate file for each package contents in the right-hand
 * frame. This will list all the Class Kinds in the package. A click on any
 * class-kind will update the frame with the clicked class-kind page.
 */
public class PackageWriter extends HtmlDocletWriter {

    /**
     * The package being documented.
     */
    protected PackageElement packageElement;

    private List<PackageElement> relatedPackages;
    private SortedSet<TypeElement> allClasses;

    /**
     * The HTML element for the section tag being written.
     */
    private final HtmlTree section = HtmlTree.SECTION(HtmlStyle.packageDescription, new ContentBuilder());

    private final BodyContents bodyContents = new BodyContents();

    // Maximum number of subpackages and sibling packages to list in related packages table
    private static final int MAX_SUBPACKAGES = 20;
    private static final int MAX_SIBLING_PACKAGES = 5;


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
    public PackageWriter(HtmlConfiguration configuration, PackageElement packageElement) {
        super(configuration,
                configuration.docPaths.forPackage(packageElement)
                .resolve(DocPaths.PACKAGE_SUMMARY));
        this.packageElement = packageElement;
        computePackageData();
    }

    @Override
    public void buildPage() throws DocletException {
        buildPackageDoc();
    }

    /**
     * Build the package documentation.
     *
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildPackageDoc() throws DocletException {
        Content content = getPackageHeader();

        buildContent();

        addPackageFooter();
        printDocument(content);
        var docFilesHandler = configuration
                .getWriterFactory()
                .newDocFilesHandler(packageElement);
        docFilesHandler.copyDocFiles();
    }

    /**
     * Build the content for the package.
     */
    protected void buildContent() {
        Content packageContent = getContentHeader();
        packageContent.add(new HtmlTree(TagName.HR));
        Content div = HtmlTree.DIV(HtmlStyle.horizontalScroll);
        addPackageSignature(div);
        buildPackageDescription(div);
        buildPackageTags(div);
        packageContent.add(div);
        buildSummary(packageContent);

        addPackageContent(packageContent);
    }

    /**
     * Builds the list of summaries for the different kinds of types in this package.
     *
     * @param packageContent the package content to which the summaries will
     *                       be added
     */
    protected void buildSummary(Content packageContent) {
        Content summariesList = getSummariesList();

        buildRelatedPackagesSummary(summariesList);
        buildAllClassesAndInterfacesSummary(summariesList);

        packageContent.add(getPackageSummary(summariesList));
    }

    /**
     * Builds a list of "nearby" packages (subpackages, superpackages, and sibling packages).
     *
     * @param summariesList the list of summaries to which the summary will be added
     */
    protected void buildRelatedPackagesSummary(Content summariesList) {
        addRelatedPackagesSummary(summariesList);
    }

    /**
     * Builds the summary for all classes and interfaces in this package.
     *
     * @param summariesList the list of summaries to which the summary will be added
     */
    protected void buildAllClassesAndInterfacesSummary(Content summariesList) {
        addAllClassesAndInterfacesSummary(summariesList);
    }

    /**
     * Build the description of the summary.
     *
     * @param packageContent the content to which the package description will
     *                       be added
     */
    protected void buildPackageDescription(Content packageContent) {
        tableOfContents.addLink(HtmlIds.TOP_OF_PAGE, contents.navDescription);
        if (options.noComment()) {
            return;
        }
        tableOfContents.pushNestedList();
        addPackageDescription(packageContent);
        tableOfContents.popNestedList();
    }

    /**
     * Build the tags of the summary.
     *
     * @param packageContent the content to which the package tags will be added
     */
    protected void buildPackageTags(Content packageContent) {
        if (options.noComment()) {
            return;
        }
        addPackageTags(packageContent);
    }

    protected Content getPackageHeader() {
        String packageName = getLocalizedPackageName(packageElement).toString();
        HtmlTree body = getBody(getWindowTitle(packageName));
        var div = HtmlTree.DIV(HtmlStyle.header);
        Content packageHead = new ContentBuilder();
        if (!packageElement.isUnnamed()) {
            packageHead.add(contents.packageLabel).add(" ");
        }
        packageHead.add(packageName);
        var tHeading = HtmlTree.HEADING_TITLE(Headings.PAGE_TITLE_HEADING,
                HtmlStyle.title, packageHead);
        div.add(tHeading);
        bodyContents.setHeader(getHeader(PageMode.PACKAGE, packageElement))
                .addMainContent(div);
        return body;
    }

    protected Content getContentHeader() {
        return new ContentBuilder();
    }

    private void computePackageData() {
        relatedPackages = findRelatedPackages();
        boolean isSpecified = utils.isSpecified(packageElement);
        allClasses = filterClasses(isSpecified
                ? utils.getAllClasses(packageElement)
                : configuration.typeElementCatalog.allClasses(packageElement));
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

        // always add superpackage
        int lastdot = pkgName.lastIndexOf('.');
        String pkgPrefix = lastdot > 0 ? pkgName.substring(0, lastdot) : null;
        List<PackageElement> packages = new ArrayList<>(
                filterPackages(p -> p.getQualifiedName().toString().equals(pkgPrefix)));
        boolean hasSuperPackage = !packages.isEmpty();

        // add subpackages unless there are very many of them
        Pattern subPattern = Pattern.compile(pkgName.replace(".", "\\.") + "\\.\\w+");
        List<PackageElement> subpackages = filterPackages(
                p -> subPattern.matcher(p.getQualifiedName().toString()).matches());
        if (subpackages.size() <= MAX_SUBPACKAGES) {
            packages.addAll(subpackages);
        }

        // only add sibling packages if there is a non-empty superpackage, we are beneath threshold,
        // and number of siblings is beneath threshold as well
        if (hasSuperPackage && pkgPrefix != null && packages.size() <= MAX_SIBLING_PACKAGES) {
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
        List<Content> subnavLinks = new ArrayList<>();
        if (configuration.showModules) {
            ModuleElement mdle = configuration.docEnv.getElementUtils().getModuleOf(packageElement);
            subnavLinks.add(links.createLink(pathToRoot.resolve(docPaths.moduleSummary(mdle)),
                    Text.of(mdle.getQualifiedName())));
        }
        subnavLinks.add(links.createLink(pathString(packageElement, DocPaths.PACKAGE_SUMMARY),
                getLocalizedPackageName(packageElement), HtmlStyle.currentSelection, ""));
        return super.getNavBar(pageMode, element).setSubNavLinks(subnavLinks);
    }

    /**
     * Add the package deprecation information to the documentation tree.
     *
     * @param div the content to which the deprecation information will be added
     */
    public void addDeprecationInfo(Content div) {
        List<? extends DeprecatedTree> deprs = utils.getDeprecatedTrees(packageElement);
        if (utils.isDeprecated(packageElement)) {
            CommentHelper ch = utils.getCommentHelper(packageElement);
            var deprDiv = HtmlTree.DIV(HtmlStyle.deprecationBlock);
            var deprPhrase = HtmlTree.SPAN(HtmlStyle.deprecatedLabel, getDeprecatedPhrase(packageElement));
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

    protected Content getSummariesList() {
        return HtmlTree.UL(HtmlStyle.summaryList);
    }

    protected void addRelatedPackagesSummary(Content summaryContent) {
        boolean showModules = configuration.showModules && hasRelatedPackagesInOtherModules(relatedPackages);
        TableHeader tableHeader= showModules
                ? new TableHeader(contents.moduleLabel, contents.packageLabel, contents.descriptionLabel)
                : new TableHeader(contents.packageLabel, contents.descriptionLabel);
        addRelatedPackageSummary(tableHeader, summaryContent, showModules);
    }

    /**
     * Add all types to the content.
     *
     * @param target the content to which the links will be added
     */
    public void addAllClassesAndInterfacesSummary(Content target) {
        var table = new Table<TypeElement>(HtmlStyle.summaryTable)
                .setHeader(new TableHeader(contents.classLabel, contents.descriptionLabel))
                .setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colLast)
                .setId(HtmlIds.CLASS_SUMMARY)
                .setDefaultTab(contents.allClassesAndInterfacesLabel)
                .addTab(contents.interfaces, utils::isPlainInterface)
                .addTab(contents.classes, utils::isNonThrowableClass)
                .addTab(contents.enums, utils::isEnum)
                .addTab(contents.records, utils::isRecord)
                .addTab(contents.exceptionClasses, utils::isThrowable)
                .addTab(contents.annotationTypes, utils::isAnnotationInterface);
        for (TypeElement typeElement : allClasses) {
            if (typeElement != null && utils.isCoreClass(typeElement)) {
                Content classLink = getLink(new HtmlLinkInfo(
                        configuration, HtmlLinkInfo.Kind.SHOW_TYPE_PARAMS_AND_BOUNDS, typeElement));
                ContentBuilder description = new ContentBuilder();
                addPreviewSummary(typeElement, description);
                if (utils.isDeprecated(typeElement)) {
                    description.add(getDeprecatedPhrase(typeElement));
                    List<? extends DeprecatedTree> tags = utils.getDeprecatedTrees(typeElement);
                    if (!tags.isEmpty()) {
                        addSummaryDeprecatedComment(typeElement, tags.get(0), description);
                    }
                } else {
                    addSummaryComment(typeElement, description);
                }
                table.addRow(typeElement, Arrays.asList(classLink, description));
            }
        }
        if (!table.isEmpty()) {
            tableOfContents.addLink(HtmlIds.CLASS_SUMMARY, contents.navClassesAndInterfaces);
            target.add(HtmlTree.LI(table));
        }
    }

    protected void addRelatedPackageSummary(TableHeader tableHeader, Content summaryContent,
                                     boolean showModules) {
        if (!relatedPackages.isEmpty()) {
            tableOfContents.addLink(HtmlIds.RELATED_PACKAGE_SUMMARY, contents.relatedPackages);
            var table = new Table<Void>(HtmlStyle.summaryTable)
                    .setId(HtmlIds.RELATED_PACKAGE_SUMMARY)
                    .setCaption(contents.relatedPackages)
                    .setHeader(tableHeader);
            if (showModules) {
                table.setColumnStyles(HtmlStyle.colPlain, HtmlStyle.colFirst, HtmlStyle.colLast);
            } else {
                table.setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colLast);
            }

            for (PackageElement pkg : relatedPackages) {
                Content packageLink = getPackageLink(pkg, Text.of(pkg.getQualifiedName()));
                Content moduleLink = Text.EMPTY;
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
            summaryContent.add(HtmlTree.LI(table));
        }
    }

    protected void addPackageDescription(Content packageContent) {
        addPreviewInfo(packageElement, packageContent);
        if (!utils.getBody(packageElement).isEmpty()) {
            section.setId(HtmlIds.PACKAGE_DESCRIPTION);
            addDeprecationInfo(section);
            addInlineComment(packageElement, section);
        }
    }

    protected void addPackageTags(Content packageContent) {
        addTagsInfo(packageElement, section);
        packageContent.add(section);
    }

    protected void addPackageSignature(Content packageContent) {
        packageContent.add(Signatures.getPackageSignature(packageElement, this));
    }

    protected void addPackageContent(Content packageContent) {
        bodyContents.addMainContent(packageContent);
        bodyContents.setSideContent(tableOfContents.toContent(false));
    }

    protected void addPackageFooter() {
        bodyContents.setFooter(getFooter());
    }

    protected void printDocument(Content content) throws DocFileIOException {
        String description = getDescription("declaration", packageElement);
        List<DocPath> localStylesheets = getLocalStylesheets(packageElement);
        content.add(bodyContents);
        printHtmlDocument(configuration.metakeywords.getMetaKeywords(packageElement),
                description, localStylesheets, content);
    }

    protected Content getPackageSummary(Content summaryContent) {
        return HtmlTree.SECTION(HtmlStyle.summary, summaryContent);
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

    @Override
    public boolean isIndexable() {
        return true;
    }
}
