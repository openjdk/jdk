/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.tools.javac.jvm.Profile;

/**
 * Generate the profile index for the left-hand frame in the generated output.
 * A click on the profile name in this frame will update the page in the top
 * left hand frame with the listing of packages of the clicked profile.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Bhavesh Patel
 */
public class ProfileIndexFrameWriter extends AbstractProfileIndexWriter {

    /**
     * Construct the ProfileIndexFrameWriter object.
     *
     * @param configuration the configuration object
     * @param filename Name of the profile index file to be generated.
     */
    public ProfileIndexFrameWriter(ConfigurationImpl configuration,
                                   DocPath filename) throws IOException {
        super(configuration, filename);
    }

    /**
     * Generate the profile index file named "profile-overview-frame.html".
     * @throws DocletAbortException
     * @param configuration the configuration object
     */
    public static void generate(ConfigurationImpl configuration) {
        ProfileIndexFrameWriter profilegen;
        DocPath filename = DocPaths.PROFILE_OVERVIEW_FRAME;
        try {
            profilegen = new ProfileIndexFrameWriter(configuration, filename);
            profilegen.buildProfileIndexFile("doclet.Window_Overview", false);
            profilegen.close();
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
    protected void addProfilesList(Profiles profiles, String text,
            String tableSummary, Content body) {
        Content heading = HtmlTree.HEADING(HtmlConstants.PROFILE_HEADING, true,
                profilesLabel);
        Content div = HtmlTree.DIV(HtmlStyle.indexContainer, heading);
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.addAttr(HtmlAttr.TITLE, profilesLabel.toString());
        for (int i = 1; i < profiles.getProfileCount(); i++) {
            ul.addContent(getProfile(i));
        }
        div.addContent(ul);
        body.addContent(div);
    }

    /**
     * Gets each profile name as a separate link.
     *
     * @param profile the profile being documented
     * @return content for the profile link
     */
    protected Content getProfile(int profile) {
        Content profileLinkContent;
        Content profileLabel;
        String profileName = (Profile.lookup(profile)).name;
        profileLabel = new StringContent(profileName);
        profileLinkContent = getHyperLink(DocPaths.profileFrame(profileName), profileLabel, "",
                    "packageListFrame");
        Content li = HtmlTree.LI(profileLinkContent);
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
     * Adds "All Packages" link for the top of the left-hand frame page to the
     * documentation tree.
     *
     * @param div the Content object to which the all packages link should be added
     */
    protected void addAllPackagesLink(Content div) {
        Content linkContent = getHyperLink(DocPaths.OVERVIEW_FRAME,
                allpackagesLabel, "", "packageListFrame");
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

    protected void addProfilePackagesList(Profiles profiles, String text,
            String tableSummary, Content body, String profileName) {
    }
}
