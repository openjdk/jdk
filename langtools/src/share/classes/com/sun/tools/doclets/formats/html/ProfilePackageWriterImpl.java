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
 * Class to generate file for each profile package contents in the right-hand
 * frame. This will list all the Class Kinds in the package. A click on any
 * class-kind will update the frame with the clicked class-kind page.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Bhavesh Patel
 */
public class ProfilePackageWriterImpl extends HtmlDocletWriter
    implements ProfilePackageSummaryWriter {

    /**
     * The prev package name in the alpha-order list.
     */
    protected PackageDoc prev;

    /**
     * The next package name in the alpha-order list.
     */
    protected PackageDoc next;

    /**
     * The profile package being documented.
     */
    protected PackageDoc packageDoc;

    /**
     * The name of the profile being documented.
     */
    protected String profileName;

    /**
     * The value of the profile being documented.
     */
    protected int profileValue;

    /**
     * Constructor to construct ProfilePackageWriter object and to generate
     * "profilename-package-summary.html" file in the respective package directory.
     * For example for profile compact1 and package "java.lang" this will generate file
     * "compact1-package-summary.html" file in the "java/lang" directory. It will also
     * create "java/lang" directory in the current or the destination directory
     * if it doesn't exist.
     *
     * @param configuration the configuration of the doclet.
     * @param packageDoc    PackageDoc under consideration.
     * @param prev          Previous package in the sorted array.
     * @param next          Next package in the sorted array.
     * @param profile       The profile being documented.
     */
    public ProfilePackageWriterImpl(ConfigurationImpl configuration,
            PackageDoc packageDoc, PackageDoc prev, PackageDoc next,
            Profile profile) throws IOException {
        super(configuration, DocPath.forPackage(packageDoc).resolve(
                DocPaths.profilePackageSummary(profile.name)));
        this.prev = prev;
        this.next = next;
        this.packageDoc = packageDoc;
        this.profileName = profile.name;
        this.profileValue = profile.value;
    }

    /**
     * {@inheritDoc}
     */
    public Content getPackageHeader(String heading) {
        String pkgName = packageDoc.name();
        Content bodyTree = getBody(true, getWindowTitle(pkgName));
        addTop(bodyTree);
        addNavLinks(true, bodyTree);
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.addStyle(HtmlStyle.header);
        Content profileContent = new StringContent(profileName);
        Content profileNameDiv = HtmlTree.DIV(HtmlStyle.subTitle, profileContent);
        div.addContent(profileNameDiv);
        Content annotationContent = new HtmlTree(HtmlTag.P);
        addAnnotationInfo(packageDoc, annotationContent);
        div.addContent(annotationContent);
        Content tHeading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, true,
                HtmlStyle.title, packageLabel);
        tHeading.addContent(getSpace());
        Content packageHead = new RawHtml(heading);
        tHeading.addContent(packageHead);
        div.addContent(tHeading);
        addDeprecationInfo(div);
        if (packageDoc.inlineTags().length > 0 && ! configuration.nocomment) {
            HtmlTree docSummaryDiv = new HtmlTree(HtmlTag.DIV);
            docSummaryDiv.addStyle(HtmlStyle.docSummary);
            addSummaryComment(packageDoc, docSummaryDiv);
            div.addContent(docSummaryDiv);
            Content space = getSpace();
            Content descLink = getHyperLink(getDocLink(
                    SectionName.PACKAGE_DESCRIPTION),
                    descriptionLabel, "", "");
            Content descPara = new HtmlTree(HtmlTag.P, seeLabel, space, descLink);
            div.addContent(descPara);
        }
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
     * Add the package deprecation information to the documentation tree.
     *
     * @param div the content tree to which the deprecation information will be added
     */
    public void addDeprecationInfo(Content div) {
        Tag[] deprs = packageDoc.tags("deprecated");
        if (Util.isDeprecated(packageDoc)) {
            HtmlTree deprDiv = new HtmlTree(HtmlTag.DIV);
            deprDiv.addStyle(HtmlStyle.deprecatedContent);
            Content deprPhrase = HtmlTree.SPAN(HtmlStyle.deprecatedLabel, deprecatedPhrase);
            deprDiv.addContent(deprPhrase);
            if (deprs.length > 0) {
                Tag[] commentTags = deprs[0].inlineTags();
                if (commentTags.length > 0) {
                    addInlineDeprecatedComment(packageDoc, deprs[0], deprDiv);
                }
            }
            div.addContent(deprDiv);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void addClassesSummary(ClassDoc[] classes, String label,
            String tableSummary, String[] tableHeader, Content packageSummaryContentTree) {
        HtmlTree li = new HtmlTree(HtmlTag.LI);
        li.addStyle(HtmlStyle.blockList);
        addClassesSummary(classes, label, tableSummary, tableHeader,
                li, profileValue);
        packageSummaryContentTree.addContent(li);
    }

    /**
     * {@inheritDoc}
     */
    public Content getSummaryHeader() {
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.addStyle(HtmlStyle.blockList);
        return ul;
    }

    /**
     * {@inheritDoc}
     */
    public void addPackageDescription(Content packageContentTree) {
        if (packageDoc.inlineTags().length > 0) {
            packageContentTree.addContent(
                    getMarkerAnchor(SectionName.PACKAGE_DESCRIPTION));
            Content h2Content = new StringContent(
                    configuration.getText("doclet.Package_Description",
                    packageDoc.name()));
            packageContentTree.addContent(HtmlTree.HEADING(HtmlConstants.PACKAGE_HEADING,
                    true, h2Content));
            addInlineComment(packageDoc, packageContentTree);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void addPackageTags(Content packageContentTree) {
        addTagsInfo(packageDoc, packageContentTree);
    }

    /**
     * {@inheritDoc}
     */
    public void addPackageFooter(Content contentTree) {
        addNavLinks(false, contentTree);
        addBottom(contentTree);
    }

    /**
     * {@inheritDoc}
     */
    public void printDocument(Content contentTree) throws IOException {
        printHtmlDocument(configuration.metakeywords.getMetaKeywords(packageDoc),
                true, contentTree);
    }

    /**
     * Get "Use" link for this package in the navigation bar.
     *
     * @return a content tree for the class use link
     */
    protected Content getNavLinkClassUse() {
        Content useLink = getHyperLink(DocPaths.PACKAGE_USE,
                useLabel, "", "");
        Content li = HtmlTree.LI(useLink);
        return li;
    }

    /**
     * Get "PREV PACKAGE" link in the navigation bar.
     *
     * @return a content tree for the previous link
     */
    public Content getNavLinkPrevious() {
        Content li;
        if (prev == null) {
            li = HtmlTree.LI(prevpackageLabel);
        } else {
            DocPath path = DocPath.relativePath(packageDoc, prev);
            li = HtmlTree.LI(getHyperLink(path.resolve(DocPaths.profilePackageSummary(profileName)),
                prevpackageLabel, "", ""));
        }
        return li;
    }

    /**
     * Get "NEXT PACKAGE" link in the navigation bar.
     *
     * @return a content tree for the next link
     */
    public Content getNavLinkNext() {
        Content li;
        if (next == null) {
            li = HtmlTree.LI(nextpackageLabel);
        } else {
            DocPath path = DocPath.relativePath(packageDoc, next);
            li = HtmlTree.LI(getHyperLink(path.resolve(DocPaths.profilePackageSummary(profileName)),
                nextpackageLabel, "", ""));
        }
        return li;
    }

    /**
     * Get "Tree" link in the navigation bar. This will be link to the package
     * tree file.
     *
     * @return a content tree for the tree link
     */
    protected Content getNavLinkTree() {
        Content useLink = getHyperLink(DocPaths.PACKAGE_TREE,
                treeLabel, "", "");
        Content li = HtmlTree.LI(useLink);
        return li;
    }

    /**
     * Highlight "Package" in the navigation bar, as this is the package page.
     *
     * @return a content tree for the package link
     */
    protected Content getNavLinkPackage() {
        Content li = HtmlTree.LI(HtmlStyle.navBarCell1Rev, packageLabel);
        return li;
    }
}
