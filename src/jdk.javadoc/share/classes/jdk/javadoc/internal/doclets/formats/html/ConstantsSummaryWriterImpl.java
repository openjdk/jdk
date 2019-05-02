/*
 * Copyright (c) 2001, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Set;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.Entity;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Navigation;
import jdk.javadoc.internal.doclets.formats.html.markup.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.formats.html.markup.Table;
import jdk.javadoc.internal.doclets.formats.html.markup.TableHeader;
import jdk.javadoc.internal.doclets.toolkit.ConstantsSummaryWriter;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocLink;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;


/**
 * Write the Constants Summary Page in HTML format.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 */
public class ConstantsSummaryWriterImpl extends HtmlDocletWriter implements ConstantsSummaryWriter {

    /**
     * The configuration used in this run of the standard doclet.
     */
    HtmlConfiguration configuration;

    /**
     * The current class being documented.
     */
    private TypeElement currentTypeElement;

    private final TableHeader constantsTableHeader;

    /**
     * The HTML tree for main tag.
     */
    private final HtmlTree mainTree = HtmlTree.MAIN();

    /**
     * The HTML tree for constant values summary.
     */
    private HtmlTree summaryTree;

    private final Navigation navBar;

    /**
     * Construct a ConstantsSummaryWriter.
     * @param configuration the configuration used in this run
     *        of the standard doclet.
     */
    public ConstantsSummaryWriterImpl(HtmlConfiguration configuration) {
        super(configuration, DocPaths.CONSTANT_VALUES);
        this.configuration = configuration;
        constantsTableHeader = new TableHeader(
                contents.modifierAndTypeLabel, contents.constantFieldLabel, contents.valueLabel);
        this.navBar = new Navigation(null, configuration, fixedNavDiv, PageMode.CONSTANTVALUES, path);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getHeader() {
        String label = resources.getText("doclet.Constants_Summary");
        HtmlTree bodyTree = getBody(getWindowTitle(label));
        HtmlTree htmlTree = HtmlTree.HEADER();
        addTop(htmlTree);
        navBar.setUserHeader(getUserHeaderFooter(true));
        htmlTree.add(navBar.getContent(true));
        bodyTree.add(htmlTree);
        return bodyTree;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getContentsHeader() {
        return new HtmlTree(HtmlTag.UL);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addLinkToPackageContent(PackageElement pkg,
            Set<PackageElement> printedPackageHeaders, Content contentListTree) {
        //add link to summary
        Content link;
        if (pkg.isUnnamed()) {
            link = links.createLink(SectionName.UNNAMED_PACKAGE_ANCHOR,
                    contents.defaultPackageLabel, "", "");
        } else {
            String parsedPackageName = utils.parsePackageName(pkg);
            Content packageNameContent = getPackageLabel(parsedPackageName);
            packageNameContent.add(".*");
            link = links.createLink(DocLink.fragment(parsedPackageName),
                    packageNameContent, "", "");
            PackageElement abbrevPkg = configuration.workArounds.getAbbreviatedPackageElement(pkg);
            printedPackageHeaders.add(abbrevPkg);
        }
        contentListTree.add(HtmlTree.LI(link));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addContentsList(Content contentTree, Content contentListTree) {
        Content titleContent = contents.constantsSummaryTitle;
        Content pHeading = HtmlTree.HEADING(Headings.PAGE_TITLE_HEADING, true,
                HtmlStyle.title, titleContent);
        Content div = HtmlTree.DIV(HtmlStyle.header, pHeading);
        Content headingContent = contents.contentsHeading;
        Content heading = HtmlTree.HEADING(Headings.CONTENT_HEADING, true,
                headingContent);
        HtmlTree section = HtmlTree.SECTION(HtmlStyle.packages, heading);
        section.add(contentListTree);
        div.add(section);
        mainTree.add(div);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getConstantSummaries() {
        HtmlTree summariesDiv = new HtmlTree(HtmlTag.DIV);
        summariesDiv.setStyle(HtmlStyle.constantValuesContainer);
        return summariesDiv;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addPackageName(PackageElement pkg, Content summariesTree, boolean first) {
        Content pkgNameContent;
        if (!first) {
            summariesTree.add(summaryTree);
        }
        if (pkg.isUnnamed()) {
            summariesTree.add(links.createAnchor(SectionName.UNNAMED_PACKAGE_ANCHOR));
            pkgNameContent = contents.defaultPackageLabel;
        } else {
            String parsedPackageName = utils.parsePackageName(pkg);
            summariesTree.add(links.createAnchor(parsedPackageName));
            pkgNameContent = getPackageLabel(parsedPackageName);
        }
        Content headingContent = new StringContent(".*");
        Content heading = HtmlTree.HEADING(Headings.ConstantsSummary.PACKAGE_HEADING, true,
                pkgNameContent);
        heading.add(headingContent);
        summaryTree = HtmlTree.SECTION(HtmlStyle.constantsSummary, heading);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getClassConstantHeader() {
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.setStyle(HtmlStyle.blockList);
        return ul;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addClassConstant(Content summariesTree, Content classConstantTree) {
        summaryTree.add(classConstantTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addConstantMembers(TypeElement typeElement, Collection<VariableElement> fields,
            Content classConstantTree) {
        currentTypeElement = typeElement;

        //generate links backward only to public classes.
        Content classlink = (utils.isPublic(typeElement) || utils.isProtected(typeElement)) ?
            getLink(new LinkInfoImpl(configuration,
                    LinkInfoImpl.Kind.CONSTANT_SUMMARY, typeElement)) :
            new StringContent(utils.getFullyQualifiedName(typeElement));

        PackageElement enclosingPackage  = utils.containingPackage(typeElement);
        Content caption = new ContentBuilder();
        if (!enclosingPackage.isUnnamed()) {
            caption.add(enclosingPackage.getQualifiedName());
            caption.add(".");
        }
        caption.add(classlink);

        Table table = new Table(HtmlStyle.constantsSummary)
                .setCaption(caption)
                .setHeader(constantsTableHeader)
                .setRowScopeColumn(1)
                .setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colSecond, HtmlStyle.colLast);

        for (VariableElement field : fields) {
            table.addRow(getTypeColumn(field), getNameColumn(field), getValue(field));
        }
        Content li = HtmlTree.LI(HtmlStyle.blockList, table.toContent());
        classConstantTree.add(li);
    }

    /**
     * Get the type column for the constant summary table row.
     *
     * @param member the field to be documented.
     * @return the type column of the constant table row
     */
    private Content getTypeColumn(VariableElement member) {
        Content anchor = links.createAnchor(
                currentTypeElement.getQualifiedName() + "." + member.getSimpleName());
        Content typeContent = new ContentBuilder();
        typeContent.add(anchor);
        Content code = new HtmlTree(HtmlTag.CODE);
        for (Modifier mod : member.getModifiers()) {
            Content modifier = new StringContent(mod.toString());
            code.add(modifier);
            code.add(Entity.NO_BREAK_SPACE);
        }
        Content type = getLink(new LinkInfoImpl(configuration,
                LinkInfoImpl.Kind.CONSTANT_SUMMARY, member.asType()));
        code.add(type);
        typeContent.add(code);
        return typeContent;
    }

    /**
     * Get the name column for the constant summary table row.
     *
     * @param member the field to be documented.
     * @return the name column of the constant table row
     */
    private Content getNameColumn(VariableElement member) {
        Content nameContent = getDocLink(LinkInfoImpl.Kind.CONSTANT_SUMMARY,
                member, member.getSimpleName(), false);
        return HtmlTree.CODE(nameContent);
    }

    /**
     * Get the value column for the constant summary table row.
     *
     * @param member the field to be documented.
     * @return the value column of the constant table row
     */
    private Content getValue(VariableElement member) {
        String value = utils.constantValueExpresion(member);
        Content valueContent = new StringContent(value);
        return HtmlTree.CODE(valueContent);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addConstantSummaries(Content contentTree, Content summariesTree) {
        if (summaryTree != null) {
            summariesTree.add(summaryTree);
        }
        mainTree.add(summariesTree);
        contentTree.add(mainTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addFooter(Content contentTree) {
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
        printHtmlDocument(null, "summary of constants", contentTree);
    }
}
