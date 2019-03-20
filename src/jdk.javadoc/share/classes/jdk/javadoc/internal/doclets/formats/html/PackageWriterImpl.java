/*
 * Copyright (c) 1997, 2019, Oracle and/or its affiliates. All rights reserved.
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

import jdk.javadoc.internal.doclets.formats.html.markup.Table;
import jdk.javadoc.internal.doclets.formats.html.markup.TableHeader;

import java.util.*;

import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Navigation;
import jdk.javadoc.internal.doclets.formats.html.markup.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.PackageSummaryWriter;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
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
 *
 * @author Atul M Dambalkar
 * @author Bhavesh Patel (Modified)
 */
public class PackageWriterImpl extends HtmlDocletWriter
    implements PackageSummaryWriter {

    /**
     * The package being documented.
     */
    protected PackageElement packageElement;

    /**
     * The HTML tree for main tag.
     */
    protected HtmlTree mainTree = HtmlTree.MAIN();

    /**
     * The HTML tree for section tag.
     */
    protected HtmlTree sectionTree = HtmlTree.SECTION();

    private final Navigation navBar;

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
        this.navBar = new Navigation(packageElement, configuration, fixedNavDiv, PageMode.PACKAGE, path);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getPackageHeader(String heading) {
        HtmlTree bodyTree = getBody(true, getWindowTitle(utils.getPackageName(packageElement)));
        HtmlTree htmlTree = HtmlTree.HEADER();
        addTop(htmlTree);
        Content linkContent = getModuleLink(utils.elementUtils.getModuleOf(packageElement),
                contents.moduleLabel);
        navBar.setNavLinkModule(linkContent);
        navBar.setUserHeader(getUserHeaderFooter(true));
        htmlTree.add(navBar.getContent(true));
        bodyTree.add(htmlTree);
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.setStyle(HtmlStyle.header);
        if (configuration.showModules) {
            ModuleElement mdle = configuration.docEnv.getElementUtils().getModuleOf(packageElement);
            Content classModuleLabel = HtmlTree.SPAN(HtmlStyle.moduleLabelInPackage, contents.moduleLabel);
            Content moduleNameDiv = HtmlTree.DIV(HtmlStyle.subTitle, classModuleLabel);
            moduleNameDiv.add(Contents.SPACE);
            moduleNameDiv.add(getModuleLink(mdle,
                    new StringContent(mdle.getQualifiedName().toString())));
            div.add(moduleNameDiv);
        }
        Content annotationContent = new HtmlTree(HtmlTag.P);
        addAnnotationInfo(packageElement, annotationContent);
        div.add(annotationContent);
        Content tHeading = HtmlTree.HEADING(Headings.PAGE_TITLE_HEADING, true,
                HtmlStyle.title, contents.packageLabel);
        tHeading.add(Contents.SPACE);
        Content packageHead = new StringContent(heading);
        tHeading.add(packageHead);
        div.add(tHeading);
        mainTree.add(div);
        return bodyTree;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getContentHeader() {
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.setStyle(HtmlStyle.contentContainer);
        return div;
    }

    /**
     * Add the package deprecation information to the documentation tree.
     *
     * @param div the content tree to which the deprecation information will be added
     */
    public void addDeprecationInfo(Content div) {
        List<? extends DocTree> deprs = utils.getBlockTags(packageElement, DocTree.Kind.DEPRECATED);
        if (utils.isDeprecated(packageElement)) {
            CommentHelper ch = utils.getCommentHelper(packageElement);
            HtmlTree deprDiv = new HtmlTree(HtmlTag.DIV);
            deprDiv.setStyle(HtmlStyle.deprecationBlock);
            Content deprPhrase = HtmlTree.SPAN(HtmlStyle.deprecatedLabel, getDeprecatedPhrase(packageElement));
            deprDiv.add(deprPhrase);
            if (!deprs.isEmpty()) {
                List<? extends DocTree> commentTags = ch.getDescription(configuration, deprs.get(0));
                if (!commentTags.isEmpty()) {
                    addInlineDeprecatedComment(packageElement, deprs.get(0), deprDiv);
                }
            }
            div.add(deprDiv);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getSummaryHeader() {
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.setStyle(HtmlStyle.blockList);
        return ul;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addInterfaceSummary(SortedSet<TypeElement> interfaces, Content summaryContentTree) {
        TableHeader tableHeader= new TableHeader(contents.interfaceLabel, contents.descriptionLabel);
        addClassesSummary(interfaces, resources.interfaceSummary, tableHeader, summaryContentTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addClassSummary(SortedSet<TypeElement> classes, Content summaryContentTree) {
        TableHeader tableHeader= new TableHeader(contents.classLabel, contents.descriptionLabel);
        addClassesSummary(classes, resources.classSummary, tableHeader, summaryContentTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addEnumSummary(SortedSet<TypeElement> enums, Content summaryContentTree) {
        TableHeader tableHeader= new TableHeader(contents.enum_, contents.descriptionLabel);
        addClassesSummary(enums, resources.enumSummary, tableHeader, summaryContentTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addExceptionSummary(SortedSet<TypeElement> exceptions, Content summaryContentTree) {
        TableHeader tableHeader= new TableHeader(contents.exception, contents.descriptionLabel);
        addClassesSummary(exceptions, resources.exceptionSummary, tableHeader, summaryContentTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addErrorSummary(SortedSet<TypeElement> errors, Content summaryContentTree) {
        TableHeader tableHeader= new TableHeader(contents.error, contents.descriptionLabel);
        addClassesSummary(errors, resources.errorSummary, tableHeader, summaryContentTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAnnotationTypeSummary(SortedSet<TypeElement> annoTypes, Content summaryContentTree) {
        TableHeader tableHeader= new TableHeader(contents.annotationType, contents.descriptionLabel);
        addClassesSummary(annoTypes, resources.annotationTypeSummary, tableHeader, summaryContentTree);
    }

    public void addClassesSummary(SortedSet<TypeElement> classes, String label,
            TableHeader tableHeader, Content summaryContentTree) {
        if(!classes.isEmpty()) {
            Table table = new Table(HtmlStyle.typeSummary)
                    .setCaption(getTableCaption(new StringContent(label)))
                    .setHeader(tableHeader)
                    .setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colLast);

            for (TypeElement klass : classes) {
                if (!utils.isCoreClass(klass) || !configuration.isGeneratedDoc(klass)) {
                    continue;
                }
                Content classLink = getLink(new LinkInfoImpl(
                        configuration, LinkInfoImpl.Kind.PACKAGE, klass));
                ContentBuilder description = new ContentBuilder();
                if (utils.isDeprecated(klass)) {
                    description.add(getDeprecatedPhrase(klass));
                    List<? extends DocTree> tags = utils.getDeprecatedTrees(klass);
                    if (!tags.isEmpty()) {
                        addSummaryDeprecatedComment(klass, tags.get(0), description);
                    }
                } else {
                    addSummaryComment(klass, description);
                }
                table.addRow(classLink, description);
            }
            Content li = HtmlTree.LI(HtmlStyle.blockList, table.toContent());
            summaryContentTree.add(li);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addPackageDescription(Content packageContentTree) {
        if (!utils.getBody(packageElement).isEmpty()) {
            Content tree = sectionTree;
            tree.add(links.createAnchor(SectionName.PACKAGE_DESCRIPTION));
            addDeprecationInfo(tree);
            addInlineComment(packageElement, tree);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addPackageTags(Content packageContentTree) {
        Content htmlTree = sectionTree;
        addTagsInfo(packageElement, htmlTree);
        packageContentTree.add(sectionTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addPackageContent(Content contentTree, Content packageContentTree) {
        mainTree.add(packageContentTree);
        contentTree.add(mainTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addPackageFooter(Content contentTree) {
        Content htmlTree = HtmlTree.FOOTER();
        navBar.setUserFooter(getUserHeaderFooter(false));
        htmlTree.add(navBar.getContent(false));
        addBottom(htmlTree);
        contentTree.add(htmlTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void printDocument(Content contentTree) throws DocFileIOException {
        String description = getDescription("declaration", packageElement);
        Content stylesheetContent = getLocalStylesheetContent(packageElement);
        printHtmlDocument(configuration.metakeywords.getMetaKeywords(packageElement),
                description, stylesheetContent, contentTree);
    }
}
