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
import java.util.*;

import com.sun.javadoc.*;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * Class to generate file for each package contents in the left-hand bottom
 * frame. This will list all the Class Kinds in the package. A click on any
 * class-kind will update the right-hand frame with the clicked class-kind page.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Atul M Dambalkar
 * @author Bhavesh Patel (Modified)
 */
public class PackageFrameWriter extends HtmlDocletWriter {

    /**
     * The package being documented.
     */
    private PackageDoc packageDoc;

    /**
     * The classes to be documented.  Use this to filter out classes
     * that will not be documented.
     */
    private Set<ClassDoc> documentedClasses;

    /**
     * Constructor to construct PackageFrameWriter object and to generate
     * "package-frame.html" file in the respective package directory.
     * For example for package "java.lang" this will generate file
     * "package-frame.html" file in the "java/lang" directory. It will also
     * create "java/lang" directory in the current or the destination directory
     * if it doesn't exist.
     *
     * @param configuration the configuration of the doclet.
     * @param packageDoc PackageDoc under consideration.
     */
    public PackageFrameWriter(ConfigurationImpl configuration,
                              PackageDoc packageDoc)
                              throws IOException {
        super(configuration, DocPath.forPackage(packageDoc).resolve(DocPaths.PACKAGE_FRAME));
        this.packageDoc = packageDoc;
        if (configuration.root.specifiedPackages().length == 0) {
            documentedClasses = new HashSet<ClassDoc>(Arrays.asList(configuration.root.classes()));
        }
    }

    /**
     * Generate a package summary page for the left-hand bottom frame. Construct
     * the PackageFrameWriter object and then uses it generate the file.
     *
     * @param configuration the current configuration of the doclet.
     * @param packageDoc The package for which "pacakge-frame.html" is to be generated.
     */
    public static void generate(ConfigurationImpl configuration,
            PackageDoc packageDoc) {
        PackageFrameWriter packgen;
        try {
            packgen = new PackageFrameWriter(configuration, packageDoc);
            String pkgName = Util.getPackageName(packageDoc);
            Content body = packgen.getBody(false, packgen.getWindowTitle(pkgName));
            Content pkgNameContent = new StringContent(pkgName);
            Content heading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, HtmlStyle.bar,
                    packgen.getTargetPackageLink(packageDoc, "classFrame", pkgNameContent));
            body.addContent(heading);
            HtmlTree div = new HtmlTree(HtmlTag.DIV);
            div.addStyle(HtmlStyle.indexContainer);
            packgen.addClassListing(div);
            body.addContent(div);
            packgen.printHtmlDocument(
                    configuration.metakeywords.getMetaKeywords(packageDoc), false, body);
            packgen.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                    "doclet.exception_encountered",
                    exc.toString(), DocPaths.PACKAGE_FRAME.getPath());
            throw new DocletAbortException(exc);
        }
    }

    /**
     * Add class listing for all the classes in this package. Divide class
     * listing as per the class kind and generate separate listing for
     * Classes, Interfaces, Exceptions and Errors.
     *
     * @param contentTree the content tree to which the listing will be added
     */
    protected void addClassListing(Content contentTree) {
        Configuration config = configuration;
        if (packageDoc.isIncluded()) {
            addClassKindListing(packageDoc.interfaces(),
                getResource("doclet.Interfaces"), contentTree);
            addClassKindListing(packageDoc.ordinaryClasses(),
                getResource("doclet.Classes"), contentTree);
            addClassKindListing(packageDoc.enums(),
                getResource("doclet.Enums"), contentTree);
            addClassKindListing(packageDoc.exceptions(),
                getResource("doclet.Exceptions"), contentTree);
            addClassKindListing(packageDoc.errors(),
                getResource("doclet.Errors"), contentTree);
            addClassKindListing(packageDoc.annotationTypes(),
                getResource("doclet.AnnotationTypes"), contentTree);
        } else {
            String name = Util.getPackageName(packageDoc);
            addClassKindListing(config.classDocCatalog.interfaces(name),
                getResource("doclet.Interfaces"), contentTree);
            addClassKindListing(config.classDocCatalog.ordinaryClasses(name),
                getResource("doclet.Classes"), contentTree);
            addClassKindListing(config.classDocCatalog.enums(name),
                getResource("doclet.Enums"), contentTree);
            addClassKindListing(config.classDocCatalog.exceptions(name),
                getResource("doclet.Exceptions"), contentTree);
            addClassKindListing(config.classDocCatalog.errors(name),
                getResource("doclet.Errors"), contentTree);
            addClassKindListing(config.classDocCatalog.annotationTypes(name),
                getResource("doclet.AnnotationTypes"), contentTree);
        }
    }

    /**
     * Add specific class kind listing. Also add label to the listing.
     *
     * @param arr Array of specific class kinds, namely Class or Interface or Exception or Error
     * @param labelContent content tree of the label to be added
     * @param contentTree the content tree to which the class kind listing will be added
     */
    protected void addClassKindListing(ClassDoc[] arr, Content labelContent,
            Content contentTree) {
        arr = Util.filterOutPrivateClasses(arr, configuration.javafx);
        if(arr.length > 0) {
            Arrays.sort(arr);
            boolean printedHeader = false;
            HtmlTree ul = new HtmlTree(HtmlTag.UL);
            ul.setTitle(labelContent);
            for (int i = 0; i < arr.length; i++) {
                if (documentedClasses != null &&
                        !documentedClasses.contains(arr[i])) {
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
                if (arr[i].isInterface()) arr_i_name = HtmlTree.SPAN(HtmlStyle.italic, arr_i_name);
                Content link = getLink(new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.PACKAGE_FRAME, arr[i]).label(arr_i_name).target("classFrame"));
                Content li = HtmlTree.LI(link);
                ul.addContent(li);
            }
            contentTree.addContent(ul);
        }
    }
}
