/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.javadoc.*;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * Generate the package index for the left-hand frame in the generated output.
 * A click on the package name in this frame will update the page in the bottom
 * left hand frame with the listing of contents of the clicked package.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Atul M Dambalkar
 */
public class PackageIndexFrameWriter extends AbstractPackageIndexWriter {

    /**
     * Construct the PackageIndexFrameWriter object.
     *
     * @param filename Name of the package index file to be generated.
     */
    public PackageIndexFrameWriter(ConfigurationImpl configuration,
                                   DocPath filename) throws IOException {
        super(configuration, filename);
    }

    /**
     * Generate the package index file named "overview-frame.html".
     * @throws DocletAbortException
     */
    public static void generate(ConfigurationImpl configuration) {
        PackageIndexFrameWriter packgen;
        DocPath filename = DocPaths.OVERVIEW_FRAME;
        try {
            packgen = new PackageIndexFrameWriter(configuration, filename);
            packgen.buildPackageIndexFile("doclet.Window_Overview", false);
            packgen.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                        "doclet.exception_encountered",
                        exc.toString(), filename);
            throw new DocletAbortException();
        }
    }

    /**
     * {@inheritDoc}
     */
    protected void addPackagesList(PackageDoc[] packages, String text,
            String tableSummary, Content body) {
        Content heading = HtmlTree.HEADING(HtmlConstants.PACKAGE_HEADING, true,
                packagesLabel);
        Content div = HtmlTree.DIV(HtmlStyle.indexContainer, heading);
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.addAttr(HtmlAttr.TITLE, packagesLabel.toString());
        for(int i = 0; i < packages.length; i++) {
            // Do not list the package if -nodeprecated option is set and the
            // package is marked as deprecated.
            if (packages[i] != null &&
                    (!(configuration.nodeprecated && Util.isDeprecated(packages[i])))) {
                ul.addContent(getPackage(packages[i]));
            }
        }
        div.addContent(ul);
        body.addContent(div);
    }

    /**
     * Gets each package name as a separate link.
     *
     * @param pd PackageDoc
     * @return content for the package link
     */
    protected Content getPackage(PackageDoc pd) {
        Content packageLinkContent;
        Content packageLabel;
        if (pd.name().length() > 0) {
            packageLabel = getPackageLabel(pd.name());
            packageLinkContent = getHyperLink(pathString(pd,
                     DocPaths.PACKAGE_FRAME), packageLabel, "",
                    "packageFrame");
        } else {
            packageLabel = new RawHtml("&lt;unnamed package&gt;");
            packageLinkContent = getHyperLink(DocPaths.PACKAGE_FRAME,
                    packageLabel, "", "packageFrame");
        }
        Content li = HtmlTree.LI(packageLinkContent);
        return li;
    }

    /**
     * {@inheritDoc}
     */
    protected void addNavigationBarHeader(Content body) {
        Content headerContent;
        if (configuration.packagesheader.length() > 0) {
            headerContent = new RawHtml(replaceDocRootDir(configuration.packagesheader));
        } else {
            headerContent = new RawHtml(replaceDocRootDir(configuration.header));
        }
        Content heading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, true,
                HtmlStyle.bar, headerContent);
        body.addContent(heading);
    }

    /**
     * Do nothing as there is no overview information in this page.
     */
    protected void addOverviewHeader(Content body) {
    }

    /**
     * Adds "All Classes" link for the top of the left-hand frame page to the
     * documentation tree.
     *
     * @param div the Content object to which the all classes link should be added
     */
    protected void addAllClassesLink(Content div) {
        Content linkContent = getHyperLink(DocPaths.ALLCLASSES_FRAME,
                allclassesLabel, "", "packageFrame");
        Content span = HtmlTree.SPAN(linkContent);
        div.addContent(span);
    }

    /**
     * Adds "All Profiles" link for the top of the left-hand frame page to the
     * documentation tree.
     *
     * @param div the Content object to which the all profiles link should be added
     */
    protected void addAllProfilesLink(Content div) {
        Content linkContent = getHyperLink(DocPaths.PROFILE_OVERVIEW_FRAME,
                allprofilesLabel, "", "profileListFrame");
        Content span = HtmlTree.SPAN(linkContent);
        div.addContent(span);
    }

    /**
     * {@inheritDoc}
     */
    protected void addNavigationBarFooter(Content body) {
        Content p = HtmlTree.P(getSpace());
        body.addContent(p);
    }
}
