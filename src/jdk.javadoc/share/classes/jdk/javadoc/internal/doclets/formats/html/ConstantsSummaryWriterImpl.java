/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlConstants;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Navigation;
import jdk.javadoc.internal.doclets.formats.html.markup.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
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

    private final String constantsTableSummary;

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
        constantsTableSummary = configuration.getText("doclet.Constants_Table_Summary",
                configuration.getText("doclet.Constants_Summary"));
        constantsTableHeader = new TableHeader(
                contents.modifierAndTypeLabel, contents.constantFieldLabel, contents.valueLabel);
        this.navBar = new Navigation(null, configuration, fixedNavDiv, PageMode.CONSTANTVALUES, path);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getHeader() {
        String label = configuration.getText("doclet.Constants_Summary");
        HtmlTree bodyTree = getBody(true, getWindowTitle(label));
        HtmlTree htmlTree = (configuration.allowTag(HtmlTag.HEADER))
                ? HtmlTree.HEADER()
                : bodyTree;
        addTop(htmlTree);
        navBar.setUserHeader(getUserHeaderFooter(true));
        htmlTree.addContent(navBar.getContent(true));
        if (configuration.allowTag(HtmlTag.HEADER)) {
            bodyTree.addContent(htmlTree);
        }
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
            packageNameContent.addContent(".*");
            link = links.createLink(DocLink.fragment(parsedPackageName),
                    packageNameContent, "", "");
            PackageElement abbrevPkg = configuration.workArounds.getAbbreviatedPackageElement(pkg);
            printedPackageHeaders.add(abbrevPkg);
        }
        contentListTree.addContent(HtmlTree.LI(link));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addContentsList(Content contentTree, Content contentListTree) {
        Content titleContent = contents.constantsSummaryTitle;
        Content pHeading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, true,
                HtmlStyle.title, titleContent);
        Content div = HtmlTree.DIV(HtmlStyle.header, pHeading);
        Content headingContent = contents.contentsHeading;
        Content heading = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING, true,
                headingContent);
        if (configuration.allowTag(HtmlTag.SECTION)) {
            HtmlTree section = HtmlTree.SECTION(heading);
            section.addContent(contentListTree);
            div.addContent(section);
            mainTree.addContent(div);
        } else {
            div.addContent(heading);
            div.addContent(contentListTree);
            contentTree.addContent(div);
        }
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
        if (!first && configuration.allowTag(HtmlTag.SECTION)) {
            summariesTree.addContent(summaryTree);
        }
        if (pkg.isUnnamed()) {
            summariesTree.addContent(links.createAnchor(SectionName.UNNAMED_PACKAGE_ANCHOR));
            pkgNameContent = contents.defaultPackageLabel;
        } else {
            String parsedPackageName = utils.parsePackageName(pkg);
            summariesTree.addContent(links.createAnchor(parsedPackageName));
            pkgNameContent = getPackageLabel(parsedPackageName);
        }
        Content headingContent = new StringContent(".*");
        Content heading = HtmlTree.HEADING(HtmlConstants.PACKAGE_HEADING, true,
                pkgNameContent);
        heading.addContent(headingContent);
        if (configuration.allowTag(HtmlTag.SECTION)) {
            summaryTree = HtmlTree.SECTION(heading);
        } else {
            summariesTree.addContent(heading);
        }
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
        if (configuration.allowTag(HtmlTag.SECTION)) {
            summaryTree.addContent(classConstantTree);
        } else {
            summariesTree.addContent(classConstantTree);
        }
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
            caption.addContent(enclosingPackage.getQualifiedName());
            caption.addContent(".");
        }
        caption.addContent(classlink);

        Table table = new Table(configuration.htmlVersion, HtmlStyle.constantsSummary)
                .setSummary(constantsTableSummary)
                .setCaption(caption)
                .setHeader(constantsTableHeader)
                .setRowScopeColumn(1)
                .setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colSecond, HtmlStyle.colLast);

        for (VariableElement field : fields) {
            table.addRow(getTypeColumn(field), getNameColumn(field), getValue(field));
        }
        Content li = HtmlTree.LI(HtmlStyle.blockList, table.toContent());
        classConstantTree.addContent(li);
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
        typeContent.addContent(anchor);
        Content code = new HtmlTree(HtmlTag.CODE);
        for (Modifier mod : member.getModifiers()) {
            Content modifier = new StringContent(mod.toString());
            code.addContent(modifier);
            code.addContent(Contents.SPACE);
        }
        Content type = getLink(new LinkInfoImpl(configuration,
                LinkInfoImpl.Kind.CONSTANT_SUMMARY, member.asType()));
        code.addContent(type);
        typeContent.addContent(code);
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
        if (configuration.allowTag(HtmlTag.SECTION) && summaryTree != null) {
            summariesTree.addContent(summaryTree);
        }
        if (configuration.allowTag(HtmlTag.MAIN)) {
            mainTree.addContent(summariesTree);
            contentTree.addContent(mainTree);
        } else {
            contentTree.addContent(summariesTree);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addFooter(Content contentTree) {
        Content htmlTree = (configuration.allowTag(HtmlTag.FOOTER))
                ? HtmlTree.FOOTER()
                : contentTree;
        navBar.setUserFooter(getUserHeaderFooter(false));
        htmlTree.addContent(navBar.getContent(false));
        addBottom(htmlTree);
        if (configuration.allowTag(HtmlTag.FOOTER)) {
            contentTree.addContent(htmlTree);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void printDocument(Content contentTree) throws DocFileIOException {
        printHtmlDocument(null, true, contentTree);
    }
}
