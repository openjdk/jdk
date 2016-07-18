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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.ModuleElement.DirectiveKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlConstants;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.RawHtml;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.ModuleSummaryWriter;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.DocletAbortException;

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

    private final Map<ModuleElement.DirectiveKind, List<ModuleElement.Directive>> directiveMap
            = new EnumMap<>(ModuleElement.DirectiveKind.class);

    /**
     * The HTML tree for main tag.
     */
    protected HtmlTree mainTree = HtmlTree.MAIN();

    /**
     * The HTML tree for section tag.
     */
    protected HtmlTree sectionTree = HtmlTree.SECTION();

    /**
     * Constructor to construct ModuleWriter object and to generate
     * "moduleName-summary.html" file.
     *
     * @param configuration the configuration of the doclet.
     * @param mdle        Module under consideration.
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
        generateDirectiveMap();
    }

    /**
     * Get the module header.
     *
     * @param heading the heading for the section
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
     * Get the content header.
     */
    public Content getContentHeader() {
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.addStyle(HtmlStyle.contentContainer);
        return div;
    }

    /**
     * Get the summary section header.
     */
    public Content getSummaryHeader() {
        HtmlTree li = new HtmlTree(HtmlTag.LI);
        li.addStyle(HtmlStyle.blockList);
        return li;
    }

    /**
     * Get the summary tree.
     *
     * @param summaryContentTree the content tree to be added to the summary tree.
     */
    public Content getSummaryTree(Content summaryContentTree) {
        HtmlTree ul = HtmlTree.UL(HtmlStyle.blockList, summaryContentTree);
        return ul;
    }

    /**
     * Generate the directive map for the directives on the module.
     */
    public void generateDirectiveMap() {
        for (ModuleElement.Directive d : mdle.getDirectives()) {
            if (directiveMap.containsKey(d.getKind())) {
                List<ModuleElement.Directive> dir = directiveMap.get(d.getKind());
                dir.add(d);
                directiveMap.put(d.getKind(), dir);
            } else {
                List<ModuleElement.Directive> dir = new ArrayList<>();
                dir.add(d);
                directiveMap.put(d.getKind(), dir);
            }
        }
    }

    /**
     * Add the summary header.
     *
     * @param startMarker the marker comment
     * @param markerAnchor the marker anchor for the section
     * @param heading the heading for the section
     * @param htmltree the content tree to which the information is added
     */
    public void addSummaryHeader(Content startMarker, SectionName markerAnchor, Content heading, Content htmltree) {
        htmltree.addContent(startMarker);
        htmltree.addContent(getMarkerAnchor(markerAnchor));
        htmltree.addContent(HtmlTree.HEADING(HtmlTag.H3, heading));
    }

    /**
     * Add the summary for the module.
     *
     * @param text the table caption
     * @param tableSummary the summary for the table
     * @param htmltree the content tree to which the table will be added
     * @param tableStyle the table style
     * @param tableHeader the table header
     * @param dirs the list of module directives
     */
    public void addSummary(String text, String tableSummary, Content htmltree, HtmlStyle tableStyle,
            List<String> tableHeader, List<ModuleElement.Directive> dirs) {
        Content table = (configuration.isOutputHtml5())
                ? HtmlTree.TABLE(tableStyle, getTableCaption(new RawHtml(text)))
                : HtmlTree.TABLE(tableStyle, tableSummary, getTableCaption(new RawHtml(text)));
        table.addContent(getSummaryTableHeader(tableHeader, "col"));
        Content tbody = new HtmlTree(HtmlTag.TBODY);
        addList(dirs, tbody);
        table.addContent(tbody);
        htmltree.addContent(table);
    }

    /**
     * Add the list of directives for the module.
     *
     * @param dirs the list of module directives
     * @params tbody the content tree to which the list is added
     */
    public void addList(List<ModuleElement.Directive> dirs, Content tbody) {
        boolean altColor = true;
        for (ModuleElement.Directive direct : dirs) {
            DirectiveKind kind = direct.getKind();
            switch (kind) {
                case REQUIRES:
                    addRequiresList((ModuleElement.RequiresDirective) direct, tbody, altColor);
                    break;
                case EXPORTS:
                    addExportedPackagesList((ModuleElement.ExportsDirective) direct, tbody, altColor);
                    break;
                case USES:
                    addUsesList((ModuleElement.UsesDirective) direct, tbody, altColor);
                    break;
                case PROVIDES:
                    addProvidesList((ModuleElement.ProvidesDirective) direct, tbody, altColor);
                    break;
                default:
                    throw new AssertionError("unknown directive kind: " + kind);
            }
            altColor = !altColor;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void addModulesSummary(Content summaryContentTree) {
        List<ModuleElement.Directive> dirs = directiveMap.get(DirectiveKind.REQUIRES);
        if (dirs != null && !dirs.isEmpty()) {
            HtmlTree li = new HtmlTree(HtmlTag.LI);
            li.addStyle(HtmlStyle.blockList);
            addSummaryHeader(HtmlConstants.START_OF_MODULES_SUMMARY, SectionName.MODULES,
                    getResource("doclet.navModules"), li);
            String text = configuration.getText("doclet.Requires_Summary");
            String tableSummary = configuration.getText("doclet.Member_Table_Summary",
                    configuration.getText("doclet.Requires_Summary"),
                    configuration.getText("doclet.modules"));
            addRequiresSummary(text, tableSummary, dirs, li);
            HtmlTree ul = HtmlTree.UL(HtmlStyle.blockList, li);
            summaryContentTree.addContent(ul);
        }
    }

    /**
     * Add the requires summary for the module.
     *
     * @param text the table caption
     * @param tableSummary the summary for the table
     * @param dirs the list of module directives
     * @param htmltree the content tree to which the table will be added
     */
    public void addRequiresSummary(String text, String tableSummary, List<ModuleElement.Directive> dirs,
            Content htmltree) {
        addSummary(text, tableSummary, htmltree, HtmlStyle.requiresSummary, requiresTableHeader, dirs);
    }

    /**
     * Add the requires directive list for the module.
     *
     * @param direct the requires directive
     * @param tbody the content tree to which the directive will be added
     * @param altColor true if altColor style should be used or false if rowColor style should be used
     */
    public void addRequiresList(ModuleElement.RequiresDirective direct, Content tbody, boolean altColor) {
        ModuleElement m = direct.getDependency();
        Content moduleLinkContent = getModuleLink(m, new StringContent(m.getQualifiedName().toString()));
        Content tdPackage = HtmlTree.TD(HtmlStyle.colFirst, moduleLinkContent);
        HtmlTree tdSummary = new HtmlTree(HtmlTag.TD);
        tdSummary.addStyle(HtmlStyle.colLast);
        addSummaryComment(m, tdSummary);
        HtmlTree tr = HtmlTree.TR(tdPackage);
        tr.addContent(tdSummary);
        tr.addStyle(altColor ? HtmlStyle.altColor : HtmlStyle.rowColor);
        tbody.addContent(tr);
    }

    /**
     * {@inheritDoc}
     */
    public void addPackagesSummary(Content summaryContentTree) {
        List<ModuleElement.Directive> dirs = directiveMap.get(DirectiveKind.EXPORTS);
        if (dirs != null && !dirs.isEmpty()) {
            HtmlTree li = new HtmlTree(HtmlTag.LI);
            li.addStyle(HtmlStyle.blockList);
            addSummaryHeader(HtmlConstants.START_OF_PACKAGES_SUMMARY, SectionName.PACKAGES,
                    getResource("doclet.navPackages"), li);
            String text = configuration.getText("doclet.Exported_Packages_Summary");
            String tableSummary = configuration.getText("doclet.Member_Table_Summary",
                    configuration.getText("doclet.Exported_Packages_Summary"),
                    configuration.getText("doclet.packages"));
            addExportedPackagesSummary(text, tableSummary, dirs, li);
            HtmlTree ul = HtmlTree.UL(HtmlStyle.blockList, li);
            summaryContentTree.addContent(ul);
        }
    }

    /**
     * Add the exported packages summary for the module.
     *
     * @param text the table caption
     * @param tableSummary the summary for the table
     * @param dirs the list of module directives
     * @param htmltree the content tree to which the table will be added
     */
    public void addExportedPackagesSummary(String text, String tableSummary, List<ModuleElement.Directive> dirs,
            Content htmltree) {
        addSummary(text, tableSummary, htmltree, HtmlStyle.packagesSummary, exportedPackagesTableHeader, dirs);
    }

    /**
     * Add the exported packages list for the module.
     *
     * @param direct the requires directive
     * @param tbody the content tree to which the directive will be added
     * @param altColor true if altColor style should be used or false if rowColor style should be used
     */
    public void addExportedPackagesList(ModuleElement.ExportsDirective direct, Content tbody, boolean altColor) {
        PackageElement pkg = direct.getPackage();
        Content pkgLinkContent = getPackageLink(pkg, new StringContent(utils.getPackageName(pkg)));
        Content tdPackage = HtmlTree.TD(HtmlStyle.colFirst, pkgLinkContent);
        HtmlTree tdModules = new HtmlTree(HtmlTag.TD);
        tdModules.addStyle(HtmlStyle.colSecond);
        List<? extends ModuleElement> targetModules = direct.getTargetModules();
        if (targetModules != null) {
            List<? extends ModuleElement> mElements = direct.getTargetModules();
            for (int i = 0; i < mElements.size(); i++) {
                if (i > 0) {
                    tdModules.addContent(new HtmlTree(HtmlTag.BR));
                }
                ModuleElement m = mElements.get(i);
                tdModules.addContent(new StringContent(m.getQualifiedName().toString()));
            }
        } else {
            tdModules.addContent(configuration.getText("doclet.All_Modules"));
        }
        HtmlTree tdSummary = new HtmlTree(HtmlTag.TD);
        tdSummary.addStyle(HtmlStyle.colLast);
        addSummaryComment(pkg, tdSummary);
        HtmlTree tr = HtmlTree.TR(tdPackage);
        tr.addContent(tdModules);
        tr.addContent(tdSummary);
        tr.addStyle(altColor ? HtmlStyle.altColor : HtmlStyle.rowColor);
        tbody.addContent(tr);
    }

    /**
     * {@inheritDoc}
     */
    public void addServicesSummary(Content summaryContentTree) {
        List<ModuleElement.Directive> usesDirs = directiveMap.get(DirectiveKind.USES);
        List<ModuleElement.Directive> providesDirs = directiveMap.get(DirectiveKind.PROVIDES);
        if ((usesDirs != null && !usesDirs.isEmpty()) || (providesDirs != null && !providesDirs.isEmpty())) {
            HtmlTree li = new HtmlTree(HtmlTag.LI);
            li.addStyle(HtmlStyle.blockList);
            addSummaryHeader(HtmlConstants.START_OF_SERVICES_SUMMARY, SectionName.SERVICES,
                    getResource("doclet.navServices"), li);
            String text;
            String tableSummary;
            if (usesDirs != null && !usesDirs.isEmpty()) {
                text = configuration.getText("doclet.Uses_Summary");
                tableSummary = configuration.getText("doclet.Member_Table_Summary",
                        configuration.getText("doclet.Uses_Summary"),
                        configuration.getText("doclet.types"));
                addUsesSummary(text, tableSummary, usesDirs, li);
            }
            if (providesDirs != null && !providesDirs.isEmpty()) {
                text = configuration.getText("doclet.Provides_Summary");
                tableSummary = configuration.getText("doclet.Member_Table_Summary",
                        configuration.getText("doclet.Provides_Summary"),
                        configuration.getText("doclet.types"));
                addProvidesSummary(text, tableSummary, providesDirs, li);
            }
            HtmlTree ul = HtmlTree.UL(HtmlStyle.blockList, li);
            summaryContentTree.addContent(ul);
        }
    }

    /**
     * Add the uses summary for the module.
     *
     * @param text the table caption
     * @param tableSummary the summary for the table
     * @param dirs the list of module directives
     * @param htmltree the content tree to which the table will be added
     */
    public void addUsesSummary(String text, String tableSummary, List<ModuleElement.Directive> dirs,
            Content htmltree) {
        addSummary(text, tableSummary, htmltree, HtmlStyle.usesSummary, usesTableHeader, dirs);
    }

    /**
     * Add the uses list for the module.
     *
     * @param direct the requires directive
     * @param tbody the content tree to which the directive will be added
     * @param altColor true if altColor style should be used or false if rowColor style should be used
     */
    public void addUsesList(ModuleElement.UsesDirective direct, Content tbody, boolean altColor) {
        TypeElement type = direct.getService();
        Content typeLinkContent = getLink(new LinkInfoImpl(configuration, LinkInfoImpl.Kind.PACKAGE, type));
        Content tdPackage = HtmlTree.TD(HtmlStyle.colFirst, typeLinkContent);
        HtmlTree tdSummary = new HtmlTree(HtmlTag.TD);
        tdSummary.addStyle(HtmlStyle.colLast);
        addSummaryComment(type, tdSummary);
        HtmlTree tr = HtmlTree.TR(tdPackage);
        tr.addContent(tdSummary);
        tr.addStyle(altColor ? HtmlStyle.altColor : HtmlStyle.rowColor);
        tbody.addContent(tr);
    }

    /**
     * Add the provides summary for the module.
     *
     * @param text the table caption
     * @param tableSummary the summary for the table
     * @param dirs the list of module directives
     * @param htmltree the content tree to which the table will be added
     */
    public void addProvidesSummary(String text, String tableSummary, List<ModuleElement.Directive> dirs,
            Content htmltree) {
        addSummary(text, tableSummary, htmltree, HtmlStyle.providesSummary, providesTableHeader, dirs);
    }

    /**
     * Add the exported packages list for the module.
     *
     * @param direct the requires directive
     * @param tbody the content tree to which the directive will be added
     * @param altColor true if altColor style should be used or false if rowColor style should be used
     */
    public void addProvidesList(ModuleElement.ProvidesDirective direct, Content tbody, boolean altColor) {
        TypeElement impl = direct.getImplementation();
        TypeElement srv = direct.getService();
        Content implLinkContent = getLink(new LinkInfoImpl(configuration, LinkInfoImpl.Kind.PACKAGE, impl));
        Content srvLinkContent = getLink(new LinkInfoImpl(configuration, LinkInfoImpl.Kind.PACKAGE, srv));
        HtmlTree tdType = HtmlTree.TD(HtmlStyle.colFirst, srvLinkContent);
        tdType.addContent(new HtmlTree(HtmlTag.BR));
        tdType.addContent("(");
        HtmlTree implSpan = HtmlTree.SPAN(HtmlStyle.implementationLabel, getResource("doclet.Implementation"));
        tdType.addContent(implSpan);
        tdType.addContent(getSpace());
        tdType.addContent(implLinkContent);
        tdType.addContent(")");
        HtmlTree tdDesc = new HtmlTree(HtmlTag.TD);
        tdDesc.addStyle(HtmlStyle.colLast);
        addSummaryComment(srv, tdDesc);
        HtmlTree tr = HtmlTree.TR(tdType);
        tr.addContent(tdDesc);
        tr.addStyle(altColor ? HtmlStyle.altColor : HtmlStyle.rowColor);
        tbody.addContent(tr);
    }

    /**
     * {@inheritDoc}
     */
    public void addModuleDescription(Content moduleContentTree) {
        if (!utils.getBody(mdle).isEmpty()) {
            Content tree = configuration.allowTag(HtmlTag.SECTION) ? HtmlTree.SECTION() : moduleContentTree;
            tree.addContent(HtmlConstants.START_OF_MODULE_DESCRIPTION);
            tree.addContent(getMarkerAnchor(SectionName.MODULE_DESCRIPTION));
            addInlineComment(mdle, tree);
            if (configuration.allowTag(HtmlTag.SECTION)) {
                moduleContentTree.addContent(tree);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void addModuleTags(Content moduleContentTree) {
        Content tree = (configuration.allowTag(HtmlTag.SECTION))
                ? HtmlTree.SECTION()
                : moduleContentTree;
        addTagsInfo(mdle, tree);
        if (configuration.allowTag(HtmlTag.SECTION)) {
            moduleContentTree.addContent(tree);
        }
    }

    /**
     * Add summary details to the navigation bar.
     *
     * @param subDiv the content tree to which the summary detail links will be added
     */
    protected void addSummaryDetailLinks(Content subDiv) {
        try {
            Content div = HtmlTree.DIV(getNavSummaryLinks());
            subDiv.addContent(div);
        } catch (Exception e) {
            throw new DocletAbortException(e);
        }
    }

    /**
     * Get summary links for navigation bar.
     *
     * @return the content tree for the navigation summary links
     */
    protected Content getNavSummaryLinks() throws Exception {
        Content li = HtmlTree.LI(moduleSubNavLabel);
        li.addContent(getSpace());
        Content ulNav = HtmlTree.UL(HtmlStyle.subNavList, li);
        Content liNav = new HtmlTree(HtmlTag.LI);
        liNav.addContent(!utils.getBody(mdle).isEmpty() && !configuration.nocomment
                ? getHyperLink(SectionName.MODULE_DESCRIPTION, getResource("doclet.navModuleDescription"))
                : getResource("doclet.navModuleDescription"));
        addNavGap(liNav);
        liNav.addContent(showDirectives(DirectiveKind.REQUIRES)
                ? getHyperLink(SectionName.MODULES, getResource("doclet.navModules"))
                : getResource("doclet.navModules"));
        addNavGap(liNav);
        liNav.addContent(showDirectives(DirectiveKind.EXPORTS)
                ? getHyperLink(SectionName.PACKAGES, getResource("doclet.navPackages"))
                : getResource("doclet.navPackages"));
        addNavGap(liNav);
        liNav.addContent((showDirectives(DirectiveKind.USES) || showDirectives(DirectiveKind.PROVIDES))
                ? getHyperLink(SectionName.SERVICES, getResource("doclet.navServices"))
                : getResource("doclet.navServices"));
        ulNav.addContent(liNav);
        return ulNav;
    }

    /**
     * Return true if the directive should be displayed.
     *
     * @param dirKind the kind of directive for the module
     * @return true if the directive should be displayed
     */
    private boolean showDirectives(DirectiveKind dirKind) {
        return directiveMap.get(dirKind) != null && !directiveMap.get(dirKind).isEmpty();
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
     * Get this module link.
     *
     * @return a content tree for the module link
     */
    @Override
    protected Content getNavLinkModule() {
        Content li = HtmlTree.LI(HtmlStyle.navBarCell1Rev, moduleLabel);
        return li;
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
