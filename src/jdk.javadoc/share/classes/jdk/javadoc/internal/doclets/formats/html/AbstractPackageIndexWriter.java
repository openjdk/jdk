/*
 * Copyright (c) 1998, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import javax.lang.model.element.PackageElement;

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
 * Abstract class to generate the overview files in
 * Frame and Non-Frame format. This will be sub-classed by to
 * generate overview-frame.html as well as overview-summary.html.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Atul M Dambalkar
 * @author Bhavesh Patel (Modified)
 */
public abstract class AbstractPackageIndexWriter extends HtmlDocletWriter {

    /**
     * A Set of Packages to be documented.
     */
    protected SortedSet<PackageElement> packages;

    protected Navigation navBar;

    /**
     * Constructor. Also initializes the packages variable.
     *
     * @param configuration  The current configuration
     * @param filename Name of the package index file to be generated.
     */
    public AbstractPackageIndexWriter(HtmlConfiguration configuration,
                                      DocPath filename) {
        super(configuration, filename);
        packages = configuration.packages;
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
     * @param body the document tree to which the navigation bar footer will be added
     */
    protected abstract void addNavigationBarFooter(Content body);

    /**
     * Adds the overview header to the documentation tree.
     *
     * @param footer the document tree to which the overview header will be added
     */
    protected abstract void addOverviewHeader(Content footer);

    /**
     * Adds the packages list to the documentation tree.
     *
     * @param main the document tree to which the packages list will be added
     */
    protected abstract void addPackagesList(Content main);

    /**
     * Generate and prints the contents in the package index file. Call appropriate
     * methods from the sub-class in order to generate Frame or Non
     * Frame format.
     *
     * @param title the title of the window
     * @param description the content for the description META tag
     * @param includeScript boolean set true if windowtitle script is to be included
     * @throws DocFileIOException if there is a problem building the package index file
     */
    protected void buildPackageIndexFile(String title, String description, boolean includeScript)
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
                description, body);
    }

    /**
     * Default to no overview, override to add overview.
     *
     * @param main the document tree to which the overview will be added
     */
    protected void addOverview(Content main) { }

    /**
     * Adds the frame or non-frame package index to the documentation tree.
     *
     * @param header the document tree to which the navigation links will be added
     * @param main the document tree to which the packages list will be added
     */
    protected void addIndex(Content header, Content main) {
        addIndexContents(header, main);
    }

    /**
     * Adds package index contents. Call appropriate methods from
     * the sub-classes. Adds it to the body HtmlTree
     *
     * @param header the document tree to which navigation links will be added
     * @param main the document tree to which the packages list will be added
     */
    protected void addIndexContents(Content header, Content main) {
        if (!packages.isEmpty()) {
            HtmlTree htmlTree = HtmlTree.NAV();
            htmlTree.setStyle(HtmlStyle.indexNav);
            HtmlTree ul = new HtmlTree(HtmlTag.UL);
            addAllClassesLink(ul);
            if (configuration.showModules  && configuration.modules.size() > 1) {
                addAllModulesLink(ul);
            }
            htmlTree.addContent(ul);
            header.addContent(htmlTree);
            addPackagesList(main);
        }
    }

    /**
     * Adds the doctitle to the documentation tree, if it is specified on the command line.
     *
     * @param body the document tree to which the title will be added
     */
    protected void addConfigurationTitle(Content body) {
        if (configuration.doctitle.length() > 0) {
            Content title = new RawHtml(configuration.doctitle);
            Content heading = HtmlTree.HEADING(Headings.PAGE_TITLE_HEADING,
                    HtmlStyle.title, title);
            Content div = HtmlTree.DIV(HtmlStyle.header, heading);
            body.addContent(div);
        }
    }

    /**
     * Do nothing. This will be overridden.
     *
     * @param div the document tree to which the all classes link will be added
     */
    protected void addAllClassesLink(Content div) {
    }

    /**
     * Do nothing. This will be overridden.
     *
     * @param div the document tree to which the all modules link will be added
     */
    protected void addAllModulesLink(Content div) {
    }
}
