/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;

import com.sun.source.doctree.DocTree;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlConstants;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.RawHtml;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.ModuleSummaryWriter;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;

/**
 * Class to generate file for each module contents in the right-hand
 * frame. This will list all the packages and Class Kinds in the module. A click on any
 * class-kind will update the frame with the clicked class-kind page. A click on any
 * package will update the frame with the clicked module package page.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Bhavesh Patel
 */
public class ModuleWriterImpl extends HtmlDocletWriter implements ModuleSummaryWriter {

    /**
     * The prev module name in the alpha-order list.
     */
    protected ModuleElement prevModule;

    /**
     * The next module name in the alpha-order list.
     */
    protected ModuleElement nextModule;

    /**
     * The module being documented.
     */
    protected ModuleElement mdle;

    /**
     * The HTML tree for main tag.
     */
    protected HtmlTree mainTree = HtmlTree.MAIN();

    /**
     * Constructor to construct ModuleWriter object and to generate
     * "moduleName-summary.html" file.
     *
     * @param configuration the configuration of the doclet.
     * @param module        Module under consideration.
     * @param prevModule   Previous module in the sorted array.
     * @param nextModule   Next module in the sorted array.
     */
    public ModuleWriterImpl(ConfigurationImpl configuration,
            ModuleElement mdle, ModuleElement prevModule, ModuleElement nextModule)
            throws IOException {
        super(configuration, DocPaths.moduleSummary(mdle));
        this.prevModule = prevModule;
        this.nextModule = nextModule;
        this.mdle = mdle;
    }

    /**
     * {@inheritDoc}
     */
    public Content getModuleHeader(String heading) {
        HtmlTree bodyTree = getBody(true, getWindowTitle(mdle.getQualifiedName().toString()));
        HtmlTree htmlTree = (configuration.allowTag(HtmlTag.HEADER))
                ? HtmlTree.HEADER()
                : bodyTree;
        addTop(htmlTree);
        addNavLinks(true, htmlTree);
        if (configuration.allowTag(HtmlTag.HEADER)) {
            bodyTree.addContent(htmlTree);
        }
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.addStyle(HtmlStyle.header);
        Content tHeading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, true,
                HtmlStyle.title, moduleLabel);
        tHeading.addContent(getSpace());
        Content moduleHead = new RawHtml(heading);
        tHeading.addContent(moduleHead);
        div.addContent(tHeading);
        if (configuration.allowTag(HtmlTag.MAIN)) {
            mainTree.addContent(div);
        } else {
            bodyTree.addContent(div);
        }
        return bodyTree;
    }

    /**
     * {@inheritDoc}
     */
    public Content getContentHeader() {
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.addStyle(HtmlStyle.contentContainer);
        return div;
    }

    /**
     * {@inheritDoc}
     */
    public Content getSummaryHeader() {
        HtmlTree li = new HtmlTree(HtmlTag.LI);
        li.addStyle(HtmlStyle.blockList);
        return li;
    }

    /**
     * {@inheritDoc}
     */
    public Content getSummaryTree(Content summaryContentTree) {
        HtmlTree ul = HtmlTree.UL(HtmlStyle.blockList, summaryContentTree);
        return ul;
    }

    /**
     * {@inheritDoc}
     */
    public void addPackagesSummary(Set<PackageElement> packages, String text,
            String tableSummary, Content summaryContentTree) {
        Content table = (configuration.isOutputHtml5())
                ? HtmlTree.TABLE(HtmlStyle.overviewSummary, getTableCaption(new RawHtml(text)))
                : HtmlTree.TABLE(HtmlStyle.overviewSummary, tableSummary, getTableCaption(new RawHtml(text)));
        table.addContent(getSummaryTableHeader(packageTableHeader, "col"));
        Content tbody = new HtmlTree(HtmlTag.TBODY);
        addPackagesList(packages, tbody);
        table.addContent(tbody);
        summaryContentTree.addContent(table);
    }

    /**
     * Adds list of packages in the package summary table. Generate link to each package.
     *
     * @param packages Packages to which link is to be generated
     * @param tbody the documentation tree to which the list will be added
     */
    protected void addPackagesList(Set<PackageElement> packages, Content tbody) {
        boolean altColor = true;
        for (PackageElement pkg : packages) {
            if (pkg != null && !pkg.isUnnamed()) {
                if (!(configuration.nodeprecated && utils.isDeprecated(pkg))) {
                    Content packageLinkContent = getPackageLink(pkg, getPackageName(pkg));
                    Content tdPackage = HtmlTree.TD(HtmlStyle.colFirst, packageLinkContent);
                    HtmlTree tdSummary = new HtmlTree(HtmlTag.TD);
                    tdSummary.addStyle(HtmlStyle.colLast);
                    addSummaryComment(pkg, tdSummary);
                    HtmlTree tr = HtmlTree.TR(tdPackage);
                    tr.addContent(tdSummary);
                    tr.addStyle(altColor ? HtmlStyle.altColor : HtmlStyle.rowColor);
                    tbody.addContent(tr);
                }
            }
            altColor = !altColor;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void addModuleContent(Content contentTree, Content moduleContentTree) {
        if (configuration.allowTag(HtmlTag.MAIN)) {
            mainTree.addContent(moduleContentTree);
            contentTree.addContent(mainTree);
        } else {
            contentTree.addContent(moduleContentTree);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void addModuleFooter(Content contentTree) {
        Content htmlTree = (configuration.allowTag(HtmlTag.FOOTER))
                ? HtmlTree.FOOTER()
                : contentTree;
        addNavLinks(false, htmlTree);
        addBottom(htmlTree);
        if (configuration.allowTag(HtmlTag.FOOTER)) {
            contentTree.addContent(htmlTree);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void printDocument(Content contentTree) throws IOException {
        printHtmlDocument(configuration.metakeywords.getMetaKeywordsForModule(mdle),
                true, contentTree);
    }

    /**
     * Add the module package deprecation information to the documentation tree.
     *
     * @param li the content tree to which the deprecation information will be added
     * @param pkg the PackageDoc that is added
     */
    public void addPackageDeprecationInfo(Content li, PackageElement pkg) {
        List<? extends DocTree> deprs;
        if (utils.isDeprecated(pkg)) {
            deprs = utils.getDeprecatedTrees(pkg);
            HtmlTree deprDiv = new HtmlTree(HtmlTag.DIV);
            deprDiv.addStyle(HtmlStyle.deprecatedContent);
            Content deprPhrase = HtmlTree.SPAN(HtmlStyle.deprecatedLabel, deprecatedPhrase);
            deprDiv.addContent(deprPhrase);
            if (!deprs.isEmpty()) {
                CommentHelper ch = utils.getCommentHelper(pkg);
                List<? extends DocTree> commentTags = ch.getDescription(configuration, deprs.get(0));
                if (!commentTags.isEmpty()) {
                    addInlineDeprecatedComment(pkg, deprs.get(0), deprDiv);
                }
            }
            li.addContent(deprDiv);
        }
    }

    /**
     * Get "PREV MODULE" link in the navigation bar.
     *
     * @return a content tree for the previous link
     */
    public Content getNavLinkPrevious() {
        Content li;
        if (prevModule == null) {
            li = HtmlTree.LI(prevmoduleLabel);
        } else {
            li = HtmlTree.LI(getHyperLink(pathToRoot.resolve(DocPaths.moduleSummary(
                    prevModule)), prevmoduleLabel, "", ""));
        }
        return li;
    }

    /**
     * Get "NEXT MODULE" link in the navigation bar.
     *
     * @return a content tree for the next link
     */
    public Content getNavLinkNext() {
        Content li;
        if (nextModule == null) {
            li = HtmlTree.LI(nextmoduleLabel);
        } else {
            li = HtmlTree.LI(getHyperLink(pathToRoot.resolve(DocPaths.moduleSummary(
                    nextModule)), nextmoduleLabel, "", ""));
        }
        return li;
    }
}
