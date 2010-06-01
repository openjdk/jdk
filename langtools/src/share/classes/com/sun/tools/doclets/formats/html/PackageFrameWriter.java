/*
 * Copyright (c) 1998, 2008, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.tools.doclets.internal.toolkit.*;

import com.sun.javadoc.*;
import java.io.*;
import java.util.*;
/**
 * Class to generate file for each package contents in the left-hand bottom
 * frame. This will list all the Class Kinds in the package. A click on any
 * class-kind will update the right-hand frame with the clicked class-kind page.
 *
 * @author Atul M Dambalkar
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
     * The name of the output file.
     */
    public static final String OUTPUT_FILE_NAME = "package-frame.html";

    /**
     * Constructor to construct PackageFrameWriter object and to generate
     * "package-frame.html" file in the respective package directory.
     * For example for package "java.lang" this will generate file
     * "package-frame.html" file in the "java/lang" directory. It will also
     * create "java/lang" directory in the current or the destination directory
     * if it doesen't exist.
     *
     * @param configuration the configuration of the doclet.
     * @param packageDoc PackageDoc under consideration.
     */
    public PackageFrameWriter(ConfigurationImpl configuration,
                              PackageDoc packageDoc)
                              throws IOException {
        super(configuration, DirectoryManager.getDirectoryPath(packageDoc), OUTPUT_FILE_NAME, DirectoryManager.getRelativePath(packageDoc));
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
            packgen.printHtmlHeader(pkgName, configuration.metakeywords.getMetaKeywords(packageDoc), false);
            packgen.printPackageHeader(pkgName);
            packgen.generateClassListing();
            packgen.printBodyHtmlEnd();
            packgen.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                        "doclet.exception_encountered",
                        exc.toString(), OUTPUT_FILE_NAME);
            throw new DocletAbortException();
        }
    }

    /**
     * Generate class listing for all the classes in this package. Divide class
     * listing as per the class kind and generate separate listing for
     * Classes, Interfaces, Exceptions and Errors.
     */
    protected void generateClassListing() {
        Configuration config = configuration();
        if (packageDoc.isIncluded()) {
            generateClassKindListing(packageDoc.interfaces(),
                configuration.getText("doclet.Interfaces"));
            generateClassKindListing(packageDoc.ordinaryClasses(),
                configuration.getText("doclet.Classes"));
            generateClassKindListing(packageDoc.enums(),
                configuration.getText("doclet.Enums"));
            generateClassKindListing(packageDoc.exceptions(),
                configuration.getText("doclet.Exceptions"));
            generateClassKindListing(packageDoc.errors(),
                configuration.getText("doclet.Errors"));
            generateClassKindListing(packageDoc.annotationTypes(),
                configuration.getText("doclet.AnnotationTypes"));
        } else {
            String name = Util.getPackageName(packageDoc);
            generateClassKindListing(config.classDocCatalog.interfaces(name),
                configuration.getText("doclet.Interfaces"));
            generateClassKindListing(config.classDocCatalog.ordinaryClasses(name),
                configuration.getText("doclet.Classes"));
            generateClassKindListing(config.classDocCatalog.enums(name),
                configuration.getText("doclet.Enums"));
            generateClassKindListing(config.classDocCatalog.exceptions(name),
                configuration.getText("doclet.Exceptions"));
            generateClassKindListing(config.classDocCatalog.errors(name),
                configuration.getText("doclet.Errors"));
            generateClassKindListing(config.classDocCatalog.annotationTypes(name),
                configuration.getText("doclet.AnnotationTypes"));
        }
    }

    /**
     * Generate specific class kind listing. Also add label to the listing.
     *
     * @param arr Array of specific class kinds, namely Class or Interface or
     * Exception or Error.
     * @param label Label for the listing
     */
    protected void generateClassKindListing(ClassDoc[] arr, String label) {
        if(arr.length > 0) {
            Arrays.sort(arr);
            printPackageTableHeader();
            fontSizeStyle("+1", "FrameHeadingFont");
            boolean printedHeader = false;
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
                    print(label);
                    fontEnd();
                    println("&nbsp;");
                    fontStyle("FrameItemFont");
                    printedHeader = true;
                }
                br();
                printLink(new LinkInfoImpl(
                    LinkInfoImpl.PACKAGE_FRAME,
                    arr[i],
                    (arr[i].isInterface() ?
                        italicsText(arr[i].name()) :
                        arr[i].name()),"classFrame")
                );
            }
            fontEnd();
            printPackageTableFooter();
            println();
        }
    }

    /**
     * Print the package link at the top of the class kind listing. Clicking
     * this link, package-summary page will appear in the right hand frame.
     *
     * @param heading Top Heading to be used for the class kind listing.
     */
    protected void printPackageHeader(String heading) {
        fontSizeStyle("+1", "FrameTitleFont");
        printTargetPackageLink(packageDoc, "classFrame", heading);
        fontEnd();
    }

    /**
     * The table for the class kind listing.
     */
    protected void printPackageTableHeader() {
        table();
        tr();
        tdNowrap();
    }

    /**
     * Closing Html tags for table of class kind listing.
     */
    protected void printPackageTableFooter() {
        tdEnd();
        trEnd();
        tableEnd();
    }
}
