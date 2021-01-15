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

import java.util.List;
import java.util.SortedSet;

import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import com.sun.source.doctree.DeprecatedTree;
import com.sun.source.doctree.DocTree;
import jdk.javadoc.internal.doclets.formats.html.markup.BodyContents;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.Entity;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.TagName;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
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

    /**
     * The HTML tree for section tag.
     */
    protected HtmlTree sectionTree = HtmlTree.SECTION(HtmlStyle.packageDescription, new ContentBuilder());

    private final BodyContents bodyContents = new BodyContents();

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
    }

    @Override
    public Content getPackageHeader(String heading) {
        HtmlTree bodyTree = getBody(getWindowTitle(utils.getPackageName(packageElement)));
        HtmlTree div = new HtmlTree(TagName.DIV);
        div.setStyle(HtmlStyle.header);
        if (configuration.showModules) {
            ModuleElement mdle = configuration.docEnv.getElementUtils().getModuleOf(packageElement);
            Content classModuleLabel = HtmlTree.SPAN(HtmlStyle.moduleLabelInPackage, contents.moduleLabel);
            Content moduleNameDiv = HtmlTree.DIV(HtmlStyle.subTitle, classModuleLabel);
            moduleNameDiv.add(Entity.NO_BREAK_SPACE);
            moduleNameDiv.add(getModuleLink(mdle,
                    new StringContent(mdle.getQualifiedName().toString())));
            div.add(moduleNameDiv);
        }
        Content tHeading = HtmlTree.HEADING_TITLE(Headings.PAGE_TITLE_HEADING,
                HtmlStyle.title, contents.packageLabel);
        tHeading.add(Entity.NO_BREAK_SPACE);
        Content packageHead = new StringContent(heading);
        tHeading.add(packageHead);
        div.add(tHeading);
        bodyContents.setHeader(getHeader(PageMode.PACKAGE, packageElement))
                .addMainContent(div);
        return bodyTree;
    }

    @Override
    public Content getContentHeader() {
        return new ContentBuilder();
    }

    @Override
    protected Navigation getNavBar(PageMode pageMode, Element element) {
        Content linkContent = getModuleLink(utils.elementUtils.getModuleOf(packageElement),
                contents.moduleLabel);
        return super.getNavBar(pageMode, element)
                .setNavLinkModule(linkContent);
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
    public void addInterfaceSummary(SortedSet<TypeElement> interfaces, Content summaryContentTree) {
        TableHeader tableHeader= new TableHeader(contents.interfaceLabel, contents.descriptionLabel);
        addClassesSummary(interfaces, contents.interfaceSummary, tableHeader, summaryContentTree);
    }

    @Override
    public void addClassSummary(SortedSet<TypeElement> classes, Content summaryContentTree) {
        TableHeader tableHeader= new TableHeader(contents.classLabel, contents.descriptionLabel);
        addClassesSummary(classes, contents.classSummary, tableHeader, summaryContentTree);
    }

    @Override
    public void addEnumSummary(SortedSet<TypeElement> enums, Content summaryContentTree) {
        TableHeader tableHeader= new TableHeader(contents.enum_, contents.descriptionLabel);
        addClassesSummary(enums, contents.enumSummary, tableHeader, summaryContentTree);
    }

    @Override
    public void addRecordSummary(SortedSet<TypeElement> records, Content summaryContentTree) {
        TableHeader tableHeader= new TableHeader(contents.record, contents.descriptionLabel);
        addClassesSummary(records, contents.recordSummary, tableHeader, summaryContentTree);
    }

    @Override
    public void addExceptionSummary(SortedSet<TypeElement> exceptions, Content summaryContentTree) {
        TableHeader tableHeader= new TableHeader(contents.exception, contents.descriptionLabel);
        addClassesSummary(exceptions, contents.exceptionSummary, tableHeader, summaryContentTree);
    }

    @Override
    public void addErrorSummary(SortedSet<TypeElement> errors, Content summaryContentTree) {
        TableHeader tableHeader= new TableHeader(contents.error, contents.descriptionLabel);
        addClassesSummary(errors, contents.errorSummary, tableHeader, summaryContentTree);
    }

    @Override
    public void addAnnotationTypeSummary(SortedSet<TypeElement> annoTypes, Content summaryContentTree) {
        TableHeader tableHeader= new TableHeader(contents.annotationType, contents.descriptionLabel);
        addClassesSummary(annoTypes, contents.annotationTypeSummary, tableHeader, summaryContentTree);
    }

    public void addClassesSummary(SortedSet<TypeElement> classes, String label,
            TableHeader tableHeader, Content summaryContentTree) {
        if(!classes.isEmpty()) {
            Table table = new Table(HtmlStyle.summaryTable)
                    .setCaption(new StringContent(label))
                    .setHeader(tableHeader)
                    .setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colLast);

            for (TypeElement klass : classes) {
                if (!utils.isCoreClass(klass) || !configuration.isGeneratedDoc(klass)) {
                    continue;
                }
                Content classLink = getLink(new LinkInfoImpl(
                        configuration, LinkInfoImpl.Kind.PACKAGE, klass));
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
            summaryContentTree.add(HtmlTree.LI(table));
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
}
