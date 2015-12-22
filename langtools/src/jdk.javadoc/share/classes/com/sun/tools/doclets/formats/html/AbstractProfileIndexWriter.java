/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.formats.html;

import java.io.*;

import com.sun.tools.javac.sym.Profiles;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.DocPath;

/**
 * Abstract class to generate the profile overview files in
 * Frame and Non-Frame format. This will be sub-classed to
 * generate profile-overview-frame.html as well as profile-overview-summary.html.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Bhavesh Patel
 */
public abstract class AbstractProfileIndexWriter extends HtmlDocletWriter {

    /**
     * Profiles to be documented.
     */
    protected Profiles profiles;

    /**
     * Constructor. Also initializes the profiles variable.
     *
     * @param configuration  The current configuration
     * @param filename Name of the profile index file to be generated.
     */
    public AbstractProfileIndexWriter(ConfigurationImpl configuration,
                                      DocPath filename) throws IOException {
        super(configuration, filename);
        profiles = configuration.profiles;
    }

    /**
     * Adds the navigation bar header to the documentation tree.
     *
     * @param body the document tree to which the navigation bar header will be added
     */
    protected abstract void addNavigationBarHeader(Content body);

    /**
     * Adds the navigation bar footer to the documentation tree.
     *
     * @param body the document tree to which the navigation bar footer will be added
     */
    protected abstract void addNavigationBarFooter(Content body);

    /**
     * Adds the overview header to the documentation tree.
     *
     * @param body the document tree to which the overview header will be added
     */
    protected abstract void addOverviewHeader(Content body);

    /**
     * Adds the profiles list to the documentation tree.
     *
     * @param profiles profiles object
     * @param text caption for the table
     * @param tableSummary summary for the table
     * @param body the document tree to which the profiles list will be added
     */
    protected abstract void addProfilesList(Profiles profiles, String text,
            String tableSummary, Content body);

    /**
     * Adds the profile packages list to the documentation tree.
     *
     * @param profiles profiles object
     * @param text caption for the table
     * @param tableSummary summary for the table
     * @param body the document tree to which the profiles list will be added
     * @param profileName the name for the profile being documented
     */
    protected abstract void addProfilePackagesList(Profiles profiles, String text,
            String tableSummary, Content body, String profileName);

    /**
     * Generate and prints the contents in the profile index file. Call appropriate
     * methods from the sub-class in order to generate Frame or Non
     * Frame format.
     *
     * @param title the title of the window.
     * @param includeScript boolean set true if windowtitle script is to be included
     */
    protected void buildProfileIndexFile(String title, boolean includeScript) throws IOException {
        String windowOverview = configuration.getText(title);
        Content body = getBody(includeScript, getWindowTitle(windowOverview));
        addNavigationBarHeader(body);
        addOverviewHeader(body);
        addIndex(body);
        addOverview(body);
        addNavigationBarFooter(body);
        printHtmlDocument(configuration.metakeywords.getOverviewMetaKeywords(title,
                configuration.doctitle), includeScript, body);
    }

    /**
     * Generate and prints the contents in the profile packages index file. Call appropriate
     * methods from the sub-class in order to generate Frame or Non
     * Frame format.
     *
     * @param title the title of the window.
     * @param includeScript boolean set true if windowtitle script is to be included
     * @param profileName the name of the profile being documented
     */
    protected void buildProfilePackagesIndexFile(String title,
            boolean includeScript, String profileName) throws IOException {
        String windowOverview = configuration.getText(title);
        Content body = getBody(includeScript, getWindowTitle(windowOverview));
        addNavigationBarHeader(body);
        addOverviewHeader(body);
        addProfilePackagesIndex(body, profileName);
        addOverview(body);
        addNavigationBarFooter(body);
        printHtmlDocument(configuration.metakeywords.getOverviewMetaKeywords(title,
                configuration.doctitle), includeScript, body);
    }

    /**
     * Default to no overview, override to add overview.
     *
     * @param body the document tree to which the overview will be added
     */
    protected void addOverview(Content body) throws IOException {
    }

    /**
     * Adds the frame or non-frame profile index to the documentation tree.
     *
     * @param body the document tree to which the index will be added
     */
    protected void addIndex(Content body) {
        addIndexContents(profiles, "doclet.Profile_Summary",
                configuration.getText("doclet.Member_Table_Summary",
                configuration.getText("doclet.Profile_Summary"),
                configuration.getText("doclet.profiles")), body);
    }

    /**
     * Adds the frame or non-frame profile packages index to the documentation tree.
     *
     * @param body the document tree to which the index will be added
     * @param profileName  the name of the profile being documented
     */
    protected void addProfilePackagesIndex(Content body, String profileName) {
        addProfilePackagesIndexContents(profiles, "doclet.Profile_Summary",
                configuration.getText("doclet.Member_Table_Summary",
                configuration.getText("doclet.Profile_Summary"),
                configuration.getText("doclet.profiles")), body, profileName);
    }

    /**
     * Adds profile index contents. Call appropriate methods from
     * the sub-classes. Adds it to the body HtmlTree
     *
     * @param profiles profiles to be documented
     * @param text string which will be used as the heading
     * @param tableSummary summary for the table
     * @param body the document tree to which the index contents will be added
     */
    protected void addIndexContents(Profiles profiles, String text,
            String tableSummary, Content body) {
        if (profiles.getProfileCount() > 0) {
            HtmlTree htmlTree = (configuration.allowTag(HtmlTag.NAV))
                    ? HtmlTree.NAV()
                    : new HtmlTree(HtmlTag.DIV);
            htmlTree.addStyle(HtmlStyle.indexNav);
            HtmlTree ul = new HtmlTree(HtmlTag.UL);
            addAllClassesLink(ul);
            addAllPackagesLink(ul);
            htmlTree.addContent(ul);
            body.addContent(htmlTree);
            addProfilesList(profiles, text, tableSummary, body);
        }
    }

    /**
     * Adds profile packages index contents. Call appropriate methods from
     * the sub-classes. Adds it to the body HtmlTree
     *
     * @param profiles profiles to be documented
     * @param text string which will be used as the heading
     * @param tableSummary summary for the table
     * @param body the document tree to which the index contents will be added
     * @param profileName the name of the profile being documented
     */
    protected void addProfilePackagesIndexContents(Profiles profiles, String text,
            String tableSummary, Content body, String profileName) {
        HtmlTree htmlTree = (configuration.allowTag(HtmlTag.NAV))
                ? HtmlTree.NAV()
                : new HtmlTree(HtmlTag.DIV);
        htmlTree.addStyle(HtmlStyle.indexNav);
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        addAllClassesLink(ul);
        addAllPackagesLink(ul);
        addAllProfilesLink(ul);
        htmlTree.addContent(ul);
        body.addContent(htmlTree);
        addProfilePackagesList(profiles, text, tableSummary, body, profileName);
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
     * Returns highlighted "Overview", in the navigation bar as this is the
     * overview page.
     *
     * @return a Content object to be added to the documentation tree
     */
    protected Content getNavLinkContents() {
        Content li = HtmlTree.LI(HtmlStyle.navBarCell1Rev, overviewLabel);
        return li;
    }

    /**
     * Do nothing. This will be overridden in ProfileIndexFrameWriter.
     *
     * @param div the document tree to which the all classes link will be added
     */
    protected void addAllClassesLink(Content div) {
    }

    /**
     * Do nothing. This will be overridden in ProfileIndexFrameWriter.
     *
     * @param div the document tree to which the all packages link will be added
     */
    protected void addAllPackagesLink(Content div) {
    }

    /**
     * Do nothing. This will be overridden in ProfilePackageIndexFrameWriter.
     *
     * @param div the document tree to which the all profiles link will be added
     */
    protected void addAllProfilesLink(Content div) {
    }
}
