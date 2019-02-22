/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlConstants;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Navigation;
import jdk.javadoc.internal.doclets.formats.html.markup.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.RawHtml;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;

/**
 * Abstract class to generate the module overview files in
 * Frame and Non-Frame format. This will be sub-classed to
 * generate module-overview-frame.html as well as module-overview-summary.html.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Bhavesh Patel
 */
public abstract class AbstractModuleIndexWriter extends HtmlDocletWriter {

    /**
     * Modules to be documented.
     */
    protected SortedMap<ModuleElement, Set<PackageElement>> modules;

    protected Navigation navBar;

    /**
     * Constructor. Also initializes the modules variable.
     *
     * @param configuration  The current configuration
     * @param filename Name of the module index file to be generated.
     */
    public AbstractModuleIndexWriter(HtmlConfiguration configuration,
                                      DocPath filename) {
        super(configuration, filename);
        modules = configuration.modulePackages;
        this.navBar = new Navigation(null, configuration, fixedNavDiv, PageMode.OVERVIEW, path);
    }

    /**
     * Adds the navigation bar header to the documentation tree.
     *
     * @param header the document tree to which the navigation bar header will be added
     */
    protected abstract void addNavigationBarHeader(Content header);

    /**
     * Adds the navigation bar footer to the documentation tree.
     *
     * @param footer the document tree to which the navigation bar footer will be added
     */
    protected abstract void addNavigationBarFooter(Content footer);

    /**
     * Adds the overview header to the documentation tree.
     *
     * @param main the document tree to which the overview header will be added
     */
    protected abstract void addOverviewHeader(Content main);

    /**
     * Adds the modules list to the documentation tree.
     *
     * @param main the document tree to which the modules list will be added
     */
    protected abstract void addModulesList(Content main);

    /**
     * Adds the module packages list to the documentation tree.
     *
     * @param modules the set of modules
     * @param text caption for the table
     * @param tableSummary summary for the table
     * @param main the document tree to which the modules list will be added
     * @param mdle the module being documented
     */
    protected abstract void addModulePackagesList(Map<ModuleElement, Set<PackageElement>> modules, String text,
            String tableSummary, Content main, ModuleElement mdle);

    /**
     * Generate and prints the contents in the module index file. Call appropriate
     * methods from the sub-class in order to generate Frame or Non
     * Frame format.
     *
     * @param title the title of the window
     * @param description the content for the description META tag
     * @param includeScript boolean set true if windowtitle script is to be included
     * @throws DocFileIOException if there is a problem building the module index file
     */
    protected void buildModuleIndexFile(String title, String description, boolean includeScript)
            throws DocFileIOException {
        String windowOverview = resources.getText(title);
        Content body = getBody(includeScript, getWindowTitle(windowOverview));
        Content header = HtmlTree.HEADER();
        addNavigationBarHeader(header);
        Content main = HtmlTree.MAIN();
        addOverviewHeader(main);
        addIndex(header, main);
        addOverview(main);
        Content footer = HtmlTree.FOOTER();
        addNavigationBarFooter(footer);
        body.addContent(header);
        body.addContent(main);
        body.addContent(footer);
        printHtmlDocument(
                configuration.metakeywords.getOverviewMetaKeywords(title, configuration.doctitle),
                description,
                body);
    }

    /**
     * Generate and prints the contents in the module packages index file. Call appropriate
     * methods from the sub-class in order to generate Frame or Non
     * Frame format.
     *
     * @param title the title of the window.
     * @param description the content for the description META tag
     * @param includeScript boolean set true if windowtitle script is to be included
     * @param mdle the name of the module being documented
     * @throws DocFileIOException if there is an exception building the module packages index file
     */
    protected void buildModulePackagesIndexFile(String title, String description,
            boolean includeScript, ModuleElement mdle) throws DocFileIOException {
        String windowOverview = resources.getText(title);
        Content body = getBody(includeScript, getWindowTitle(windowOverview));
        Content header = HtmlTree.HEADER();
        addNavigationBarHeader(header);
        Content main = HtmlTree.MAIN();
        addOverviewHeader(main);
        addModulePackagesIndex(header, main, mdle);
        addOverview(main);
        Content footer = HtmlTree.FOOTER();
        addNavigationBarFooter(footer);
        body.addContent(header);
        body.addContent(main);
        body.addContent(footer);
        printHtmlDocument(
                configuration.metakeywords.getOverviewMetaKeywords(title, configuration.doctitle),
                description,
                body);
    }

    /**
     * Default to no overview, override to add overview.
     *
     * @param main the document tree to which the overview will be added
     */
    protected void addOverview(Content main) { }

    /**
     * Adds the frame or non-frame module index to the documentation tree.
     *
     * @param header the document tree to which the navigational links will be added
     * @param main the document tree to which the modules list will be added
     */
    protected void addIndex(Content header, Content main) {
        addIndexContents(configuration.modules, "doclet.Module_Summary",
                resources.getText("doclet.Member_Table_Summary",
                resources.getText("doclet.Module_Summary"),
                resources.getText("doclet.modules")), header, main);
    }

    /**
     * Adds the frame or non-frame module packages index to the documentation tree.
     *
     * @param header the document tree to which the navigational links will be added
     * @param main the document tree to which the module packages list will be added
     * @param mdle the module being documented
     */
    protected void addModulePackagesIndex(Content header, Content main, ModuleElement mdle) {
        addModulePackagesIndexContents("doclet.Module_Summary",
                resources.getText("doclet.Member_Table_Summary",
                resources.getText("doclet.Module_Summary"),
                resources.getText("doclet.modules")), header, main, mdle);
    }

    /**
     * Adds module index contents. Call appropriate methods from
     * the sub-classes. Adds it to the body HtmlTree
     *
     * @param modules the modules to be documented
     * @param text string which will be used as the heading
     * @param tableSummary summary for the table
     * @param header the document tree to which the navgational links will be added
     * @param main the document tree to which the modules list will be added
     */
    protected void addIndexContents(Collection<ModuleElement> modules, String text,
            String tableSummary, Content header, Content main) {
        HtmlTree htmlTree = HtmlTree.NAV();
        htmlTree.setStyle(HtmlStyle.indexNav);
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        addAllClassesLink(ul);
        addAllPackagesLink(ul);
        htmlTree.addContent(ul);
        header.addContent(htmlTree);
        addModulesList(main);
    }

    /**
     * Adds module packages index contents. Call appropriate methods from
     * the sub-classes. Adds it to the body HtmlTree
     *
     * @param text string which will be used as the heading
     * @param tableSummary summary for the table
     * @param header the document tree to which the navigational links will be added
     * @param main the document tree to which the module packages list will be added
     * @param mdle the module being documented
     */
    protected void addModulePackagesIndexContents(String text,
            String tableSummary, Content header, Content main, ModuleElement mdle) {
        HtmlTree htmlTree = HtmlTree.NAV();
        htmlTree.setStyle(HtmlStyle.indexNav);
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        addAllClassesLink(ul);
        addAllPackagesLink(ul);
        addAllModulesLink(ul);
        htmlTree.addContent(ul);
        header.addContent(htmlTree);
        addModulePackagesList(modules, text, tableSummary, main, mdle);
    }

    /**
     * Adds the doctitle to the documentation tree, if it is specified on the command line.
     *
     * @param body the document tree to which the title will be added
     */
    protected void addConfigurationTitle(Content body) {
        if (configuration.doctitle.length() > 0) {
            Content title = new RawHtml(configuration.doctitle);
            Content heading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING,
                    HtmlStyle.title, title);
            Content div = HtmlTree.DIV(HtmlStyle.header, heading);
            body.addContent(div);
        }
    }

    /**
     * Do nothing. This will be overridden in ModuleIndexFrameWriter.
     *
     * @param div the document tree to which the all classes link will be added
     */
    protected void addAllClassesLink(Content div) { }

    /**
     * Do nothing. This will be overridden in ModuleIndexFrameWriter.
     *
     * @param div the document tree to which the all packages link will be added
     */
    protected void addAllPackagesLink(Content div) { }

    /**
     * Do nothing. This will be overridden in ModulePackageIndexFrameWriter.
     *
     * @param div the document tree to which the all modules link will be added
     */
    protected void addAllModulesLink(Content div) { }
}
