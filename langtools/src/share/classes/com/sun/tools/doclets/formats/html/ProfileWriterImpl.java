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
import java.util.*;

import com.sun.javadoc.*;
import com.sun.tools.javac.jvm.Profile;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * Class to generate file for each profile contents in the right-hand
 * frame. This will list all the packages and Class Kinds in the profile. A click on any
 * class-kind will update the frame with the clicked class-kind page. A click on any
 * package will update the frame with the clicked profile package page.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Bhavesh Patel
 */
public class ProfileWriterImpl extends HtmlDocletWriter
    implements ProfileSummaryWriter {

    /**
     * The prev profile name in the alpha-order list.
     */
    protected Profile prevProfile;

    /**
     * The next profile name in the alpha-order list.
     */
    protected Profile nextProfile;

    /**
     * The profile being documented.
     */
    protected Profile profile;

    /**
     * Constructor to construct ProfileWriter object and to generate
     * "profileName-summary.html" file.
     *
     * @param configuration the configuration of the doclet.
     * @param profile       Profile under consideration.
     * @param prevProfile   Previous profile in the sorted array.
     * @param nextProfile   Next profile in the sorted array.
     */
    public ProfileWriterImpl(ConfigurationImpl configuration,
            Profile profile, Profile prevProfile, Profile nextProfile)
            throws IOException {
        super(configuration, DocPaths.profileSummary(profile.name));
        this.prevProfile = prevProfile;
        this.nextProfile = nextProfile;
        this.profile = profile;
    }

    /**
     * {@inheritDoc}
     */
    public Content getProfileHeader(String heading) {
        String profileName = profile.name;
        Content bodyTree = getBody(true, getWindowTitle(profileName));
        addTop(bodyTree);
        addNavLinks(true, bodyTree);
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.addStyle(HtmlStyle.header);
        Content tHeading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, true,
                HtmlStyle.title, profileLabel);
        tHeading.addContent(getSpace());
        Content profileHead = new RawHtml(heading);
        tHeading.addContent(profileHead);
        div.addContent(tHeading);
        bodyTree.addContent(div);
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
        HtmlTree div = HtmlTree.DIV(HtmlStyle.summary, ul);
        return div;
    }

    /**
     * {@inheritDoc}
     */
    public Content getPackageSummaryHeader(PackageDoc pkg) {
        Content pkgName = getTargetProfilePackageLink(pkg,
                    "classFrame", new StringContent(pkg.name()), profile.name);
        Content heading = HtmlTree.HEADING(HtmlTag.H3, pkgName);
        HtmlTree li = HtmlTree.LI(HtmlStyle.blockList, heading);
        addPackageDeprecationInfo(li, pkg);
        return li;
    }

    /**
     * {@inheritDoc}
     */
    public Content getPackageSummaryTree(Content packageSummaryContentTree) {
        HtmlTree ul = HtmlTree.UL(HtmlStyle.blockList, packageSummaryContentTree);
        return ul;
    }

    /**
     * {@inheritDoc}
     */
    public void addClassesSummary(ClassDoc[] classes, String label,
            String tableSummary, String[] tableHeader, Content packageSummaryContentTree) {
        addClassesSummary(classes, label, tableSummary, tableHeader,
                packageSummaryContentTree, profile.value);
    }

    /**
     * {@inheritDoc}
     */
    public void addProfileFooter(Content contentTree) {
        addNavLinks(false, contentTree);
        addBottom(contentTree);
    }

    /**
     * {@inheritDoc}
     */
    public void printDocument(Content contentTree) throws IOException {
        printHtmlDocument(configuration.metakeywords.getMetaKeywords(profile),
                true, contentTree);
    }

    /**
     * Add the profile package deprecation information to the documentation tree.
     *
     * @param li the content tree to which the deprecation information will be added
     * @param pkg the PackageDoc that is added
     */
    public void addPackageDeprecationInfo(Content li, PackageDoc pkg) {
        Tag[] deprs;
        if (Util.isDeprecated(pkg)) {
            deprs = pkg.tags("deprecated");
            HtmlTree deprDiv = new HtmlTree(HtmlTag.DIV);
            deprDiv.addStyle(HtmlStyle.deprecatedContent);
            Content deprPhrase = HtmlTree.SPAN(HtmlStyle.deprecatedLabel, deprecatedPhrase);
            deprDiv.addContent(deprPhrase);
            if (deprs.length > 0) {
                Tag[] commentTags = deprs[0].inlineTags();
                if (commentTags.length > 0) {
                    addInlineDeprecatedComment(pkg, deprs[0], deprDiv);
                }
            }
            li.addContent(deprDiv);
        }
    }

    /**
     * Get "PREV PROFILE" link in the navigation bar.
     *
     * @return a content tree for the previous link
     */
    public Content getNavLinkPrevious() {
        Content li;
        if (prevProfile == null) {
            li = HtmlTree.LI(prevprofileLabel);
        } else {
            li = HtmlTree.LI(getHyperLink(pathToRoot.resolve(DocPaths.profileSummary(
                    prevProfile.name)), prevprofileLabel, "", ""));
        }
        return li;
    }

    /**
     * Get "NEXT PROFILE" link in the navigation bar.
     *
     * @return a content tree for the next link
     */
    public Content getNavLinkNext() {
        Content li;
        if (nextProfile == null) {
            li = HtmlTree.LI(nextprofileLabel);
        } else {
            li = HtmlTree.LI(getHyperLink(pathToRoot.resolve(DocPaths.profileSummary(
                    nextProfile.name)), nextprofileLabel, "", ""));
        }
        return li;
    }
}
