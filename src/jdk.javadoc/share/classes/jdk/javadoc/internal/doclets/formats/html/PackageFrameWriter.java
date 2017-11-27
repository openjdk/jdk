/*
 * Copyright (c) 1998, 2017, Oracle and/or its affiliates. All rights reserved.
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
import javax.lang.model.element.TypeElement;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlConstants;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;

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
    private final PackageElement packageElement;

    /**
     * The classes to be documented.  Use this to filter out classes
     * that will not be documented.
     */
    private SortedSet<TypeElement> documentedClasses;

    /**
     * Constructor to construct PackageFrameWriter object and to generate
     * "package-frame.html" file in the respective package directory.
     * For example for package "java.lang" this will generate file
     * "package-frame.html" file in the "java/lang" directory. It will also
     * create "java/lang" directory in the current or the destination directory
     * if it doesn't exist.
     *
     * @param configuration the configuration of the doclet.
     * @param packageElement PackageElement under consideration.
     */
    public PackageFrameWriter(HtmlConfiguration configuration, PackageElement packageElement) {
        super(configuration, DocPath.forPackage(packageElement).resolve(DocPaths.PACKAGE_FRAME));
        this.packageElement = packageElement;
        if (configuration.getSpecifiedPackageElements().isEmpty()) {
            documentedClasses = new TreeSet<>(utils.makeGeneralPurposeComparator());
            documentedClasses.addAll(configuration.getIncludedTypeElements());
        }
    }

    /**
     * Generate a package summary page for the left-hand bottom frame. Construct
     * the PackageFrameWriter object and then uses it generate the file.
     *
     * @param configuration the current configuration of the doclet.
     * @param packageElement The package for which "pacakge-frame.html" is to be generated.
     * @throws DocFileIOException if there is a problem generating the package summary page
     */
    public static void generate(HtmlConfiguration configuration, PackageElement packageElement)
            throws DocFileIOException {
        PackageFrameWriter packgen = new PackageFrameWriter(configuration, packageElement);
        String pkgName = configuration.utils.getPackageName(packageElement);
        HtmlTree body = packgen.getBody(false, packgen.getWindowTitle(pkgName));
        Content pkgNameContent = new StringContent(pkgName);
        HtmlTree htmlTree = (configuration.allowTag(HtmlTag.MAIN))
                ? HtmlTree.MAIN()
                : body;
        Content heading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, HtmlStyle.bar,
                packgen.getTargetPackageLink(packageElement, "classFrame", pkgNameContent));
        htmlTree.addContent(heading);
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.setStyle(HtmlStyle.indexContainer);
        packgen.addClassListing(div);
        htmlTree.addContent(div);
        if (configuration.allowTag(HtmlTag.MAIN)) {
            body.addContent(htmlTree);
        }
        packgen.printHtmlDocument(
                configuration.metakeywords.getMetaKeywords(packageElement), false, body);
    }

    /**
     * Add class listing for all the classes in this package. Divide class
     * listing as per the class kind and generate separate listing for
     * Classes, Interfaces, Exceptions and Errors.
     *
     * @param contentTree the content tree to which the listing will be added
     */
    protected void addClassListing(HtmlTree contentTree) {
        BaseConfiguration config = configuration;
        if (utils.isSpecified(packageElement)) {
            addClassKindListing(utils.getInterfaces(packageElement),
                contents.interfaces, contentTree);
            addClassKindListing(utils.getOrdinaryClasses(packageElement),
                contents.classes, contentTree);
            addClassKindListing(utils.getEnums(packageElement),
                contents.enums, contentTree);
            addClassKindListing(utils.getExceptions(packageElement),
                contents.exceptions, contentTree);
            addClassKindListing(utils.getErrors(packageElement),
                contents.errors, contentTree);
            addClassKindListing(utils.getAnnotationTypes(packageElement),
                contents.annotationTypes, contentTree);
        } else {
            addClassKindListing(config.typeElementCatalog.interfaces(packageElement),
                contents.interfaces, contentTree);
            addClassKindListing(config.typeElementCatalog.ordinaryClasses(packageElement),
                contents.classes, contentTree);
            addClassKindListing(config.typeElementCatalog.enums(packageElement),
                contents.enums, contentTree);
            addClassKindListing(config.typeElementCatalog.exceptions(packageElement),
                contents.exceptions, contentTree);
            addClassKindListing(config.typeElementCatalog.errors(packageElement),
                contents.errors, contentTree);
            addClassKindListing(config.typeElementCatalog.annotationTypes(packageElement),
                contents.annotationTypes, contentTree);
        }
    }

    /**
     * Add specific class kind listing. Also add label to the listing.
     *
     * @param list list of specific class kinds, namely Class or Interface or Exception or Error
     * @param labelContent content tree of the label to be added
     * @param contentTree the content tree to which the class kind listing will be added
     */
    protected void addClassKindListing(Iterable<TypeElement> list, Content labelContent,
            HtmlTree contentTree) {
        SortedSet<TypeElement> tset = utils.filterOutPrivateClasses(list, configuration.javafx);
        if(!tset.isEmpty()) {
            boolean printedHeader = false;
            HtmlTree htmlTree = (configuration.allowTag(HtmlTag.SECTION))
                    ? HtmlTree.SECTION()
                    : contentTree;
            HtmlTree ul = new HtmlTree(HtmlTag.UL);
            ul.setTitle(labelContent);
            for (TypeElement typeElement : tset) {
                if (documentedClasses != null && !documentedClasses.contains(typeElement)) {
                    continue;
                }
                if (!utils.isCoreClass(typeElement) || !configuration.isGeneratedDoc(typeElement)) {
                    continue;
                }
                if (!printedHeader) {
                    Content heading = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                                                       true, labelContent);
                    htmlTree.addContent(heading);
                    printedHeader = true;
                }
                Content arr_i_name = new StringContent(utils.getSimpleName(typeElement));
                if (utils.isInterface(typeElement))
                    arr_i_name = HtmlTree.SPAN(HtmlStyle.interfaceName, arr_i_name);
                Content link = getLink(new LinkInfoImpl(configuration,
                                                        LinkInfoImpl.Kind.PACKAGE_FRAME, typeElement).label(arr_i_name).target("classFrame"));
                Content li = HtmlTree.LI(link);
                ul.addContent(li);
            }
            htmlTree.addContent(ul);
            if (configuration.allowTag(HtmlTag.SECTION)) {
                contentTree.addContent(htmlTree);
            }
        }
    }
}
