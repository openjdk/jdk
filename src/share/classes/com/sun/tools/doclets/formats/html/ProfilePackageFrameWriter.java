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
 * Class to generate file for each package contents of a profile in the left-hand bottom
 * frame. This will list all the Class Kinds in the package for a profile. A click on any
 * class-kind will update the right-hand frame with the clicked class-kind page.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Bhavesh Patel
 */
public class ProfilePackageFrameWriter extends HtmlDocletWriter {

    /**
     * The package being documented.
     */
    private PackageDoc packageDoc;

    /**
     * Constructor to construct ProfilePackageFrameWriter object and to generate
     * "profilename-package-frame.html" file in the respective package directory.
     * For example for profile compact1 and package "java.lang" this will generate file
     * "compact1-package-frame.html" file in the "java/lang" directory. It will also
     * create "java/lang" directory in the current or the destination directory
     * if it doesn't exist.
     *
     * @param configuration the configuration of the doclet.
     * @param packageDoc PackageDoc under consideration.
     * @param profileName the name of the profile being documented
     */
    public ProfilePackageFrameWriter(ConfigurationImpl configuration,
            PackageDoc packageDoc, String profileName)
            throws IOException {
        super(configuration, DocPath.forPackage(packageDoc).resolve(
                DocPaths.profilePackageFrame(profileName)));
        this.packageDoc = packageDoc;
    }

    /**
     * Generate a profile package summary page for the left-hand bottom frame. Construct
     * the ProfilePackageFrameWriter object and then uses it generate the file.
     *
     * @param configuration the current configuration of the doclet.
     * @param packageDoc The package for which "profilename-package-frame.html" is to be generated.
     * @param profileValue the value of the profile being documented
     */
    public static void generate(ConfigurationImpl configuration,
            PackageDoc packageDoc, int profileValue) {
        ProfilePackageFrameWriter profpackgen;
        try {
            String profileName = Profile.lookup(profileValue).name;
            profpackgen = new ProfilePackageFrameWriter(configuration, packageDoc,
                    profileName);
            StringBuilder winTitle = new StringBuilder(profileName);
            String sep = " - ";
            winTitle.append(sep);
            String pkgName = Util.getPackageName(packageDoc);
            winTitle.append(pkgName);
            Content body = profpackgen.getBody(false,
                    profpackgen.getWindowTitle(winTitle.toString()));
            Content profName = new StringContent(profileName);
            Content sepContent = new StringContent(sep);
            Content pkgNameContent = new RawHtml(pkgName);
            Content heading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, HtmlStyle.bar,
                    profpackgen.getTargetProfileLink("classFrame", profName, profileName));
            heading.addContent(sepContent);
            heading.addContent(profpackgen.getTargetProfilePackageLink(packageDoc,
                    "classFrame", pkgNameContent, profileName));
            body.addContent(heading);
            HtmlTree div = new HtmlTree(HtmlTag.DIV);
            div.addStyle(HtmlStyle.indexContainer);
            profpackgen.addClassListing(div, profileValue);
            body.addContent(div);
            profpackgen.printHtmlDocument(
                    configuration.metakeywords.getMetaKeywords(packageDoc), false, body);
            profpackgen.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                    "doclet.exception_encountered",
                    exc.toString(), DocPaths.PACKAGE_FRAME.getPath());
            throw new DocletAbortException();
        }
    }

    /**
     * Add class listing for all the classes in this package. Divide class
     * listing as per the class kind and generate separate listing for
     * Classes, Interfaces, Exceptions and Errors.
     *
     * @param contentTree the content tree to which the listing will be added
     * @param profileValue the value of the profile being documented
     */
    protected void addClassListing(Content contentTree, int profileValue) {
        if (packageDoc.isIncluded()) {
            addClassKindListing(packageDoc.interfaces(),
                getResource("doclet.Interfaces"), contentTree, profileValue);
            addClassKindListing(packageDoc.ordinaryClasses(),
                getResource("doclet.Classes"), contentTree, profileValue);
            addClassKindListing(packageDoc.enums(),
                getResource("doclet.Enums"), contentTree, profileValue);
            addClassKindListing(packageDoc.exceptions(),
                getResource("doclet.Exceptions"), contentTree, profileValue);
            addClassKindListing(packageDoc.errors(),
                getResource("doclet.Errors"), contentTree, profileValue);
            addClassKindListing(packageDoc.annotationTypes(),
                getResource("doclet.AnnotationTypes"), contentTree, profileValue);
        }
    }

    /**
     * Add specific class kind listing. Also add label to the listing.
     *
     * @param arr Array of specific class kinds, namely Class or Interface or Exception or Error
     * @param labelContent content tree of the label to be added
     * @param contentTree the content tree to which the class kind listing will be added
     * @param profileValue the value of the profile being documented
     */
    protected void addClassKindListing(ClassDoc[] arr, Content labelContent,
            Content contentTree, int profileValue) {
        if(arr.length > 0) {
            Arrays.sort(arr);
            boolean printedHeader = false;
            HtmlTree ul = new HtmlTree(HtmlTag.UL);
            ul.setTitle(labelContent);
            for (int i = 0; i < arr.length; i++) {
                if (!isTypeInProfile(arr[i], profileValue)) {
                    continue;
                }
                if (!Util.isCoreClass(arr[i]) || !
                        configuration.isGeneratedDoc(arr[i])) {
                    continue;
                }
                if (!printedHeader) {
                    Content heading = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                            true, labelContent);
                    contentTree.addContent(heading);
                    printedHeader = true;
                }
                Content arr_i_name = new StringContent(arr[i].name());
                if (arr[i].isInterface()) arr_i_name = HtmlTree.I(arr_i_name);
                Content link = getLink(new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.PACKAGE_FRAME, arr[i]).label(arr_i_name).target("classFrame"));
                Content li = HtmlTree.LI(link);
                ul.addContent(li);
            }
            contentTree.addContent(ul);
        }
    }
}
