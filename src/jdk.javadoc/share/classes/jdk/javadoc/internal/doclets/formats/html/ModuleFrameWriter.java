/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;

/**
 * Class to generate file for each module contents in the left-hand bottom
 * frame. This will list all the Class Kinds in the module. A click on any
 * class-kind will update the right-hand frame with the clicked class-kind page.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Bhavesh Patel
 */
public class ModuleFrameWriter extends HtmlDocletWriter {

    /**
     * The module being documented.
     */
    private final ModuleElement mdle;

    /**
     * The classes to be documented.  Use this to filter out classes
     * that will not be documented.
     */
    private SortedSet<TypeElement> documentedClasses;

    /**
     * Constructor to construct ModuleFrameWriter object and to generate
     * "module_name-type-frame.html" file. For example for module "java.base" this will generate file
     * "java.base-type-frame.html" file.
     *
     * @param configuration the configuration of the doclet.
     * @param moduleElement moduleElement under consideration.
     */
    public ModuleFrameWriter(HtmlConfiguration configuration, ModuleElement moduleElement) {
        super(configuration, configuration.docPaths.moduleTypeFrame(moduleElement));
        this.mdle = moduleElement;
        if (configuration.getSpecifiedPackageElements().isEmpty()) {
            documentedClasses = new TreeSet<>(utils.makeGeneralPurposeComparator());
            documentedClasses.addAll(configuration.getIncludedTypeElements());
        }
    }

    /**
     * Generate a module type summary page for the left-hand bottom frame.
     *
     * @param configuration the current configuration of the doclet.
     * @param moduleElement The package for which "module_name-type-frame.html" is to be generated.
     * @throws DocFileIOException if there is a problem generating the module summary file
     */
    public static void generate(HtmlConfiguration configuration, ModuleElement moduleElement)
            throws DocFileIOException {
        ModuleFrameWriter mdlgen = new ModuleFrameWriter(configuration, moduleElement);
        String mdlName = moduleElement.getQualifiedName().toString();
        Content mdlLabel = new StringContent(mdlName);
        HtmlTree body = mdlgen.getBody(false, mdlgen.getWindowTitle(mdlName));
        HtmlTree htmlTree = HtmlTree.MAIN();
        DocPath moduleSummary = configuration.useModuleDirectories
                ? DocPaths.DOT_DOT.resolve(configuration.docPaths.moduleSummary(moduleElement))
                : configuration.docPaths.moduleSummary(moduleElement);
        Content heading = HtmlTree.HEADING(Headings.PAGE_TITLE_HEADING, HtmlStyle.bar,
                mdlgen.links.createLink(moduleSummary, mdlLabel, "", "classFrame"));
        htmlTree.add(heading);
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.setStyle(HtmlStyle.indexContainer);
        mdlgen.addClassListing(div);
        htmlTree.add(div);
        body.add(htmlTree);
        mdlgen.printHtmlDocument(
                configuration.metakeywords.getMetaKeywordsForModule(moduleElement),
                "module summary (frame)",
                body);
    }

    /**
     * Add class listing for all the classes in this module. Divide class
     * listing as per the class kind and generate separate listing for
     * Classes, Interfaces, Exceptions and Errors.
     *
     * @param contentTree the content tree to which the listing will be added
     */
    protected void addClassListing(HtmlTree contentTree) {
        List<PackageElement> packagesIn = ElementFilter.packagesIn(mdle.getEnclosedElements());
        SortedSet<TypeElement> interfaces = new TreeSet<>(utils.makeGeneralPurposeComparator());
        SortedSet<TypeElement> classes = new TreeSet<>(utils.makeGeneralPurposeComparator());
        SortedSet<TypeElement> enums = new TreeSet<>(utils.makeGeneralPurposeComparator());
        SortedSet<TypeElement> exceptions = new TreeSet<>(utils.makeGeneralPurposeComparator());
        SortedSet<TypeElement> errors = new TreeSet<>(utils.makeGeneralPurposeComparator());
        SortedSet<TypeElement> annotationTypes = new TreeSet<>(utils.makeGeneralPurposeComparator());
        for (PackageElement pkg : packagesIn) {
            if (utils.isIncluded(pkg)) {
                interfaces.addAll(utils.getInterfaces(pkg));
                classes.addAll(utils.getOrdinaryClasses(pkg));
                enums.addAll(utils.getEnums(pkg));
                exceptions.addAll(utils.getExceptions(pkg));
                errors.addAll(utils.getErrors(pkg));
                annotationTypes.addAll(utils.getAnnotationTypes(pkg));
            }
        }
        addClassKindListing(interfaces, contents.interfaces, contentTree);
        addClassKindListing(classes, contents.classes, contentTree);
        addClassKindListing(enums, contents.enums, contentTree);
        addClassKindListing(exceptions, contents.exceptions, contentTree);
        addClassKindListing(errors, contents.errors, contentTree);
        addClassKindListing(annotationTypes, contents.annotationTypes, contentTree);
    }

    /**
     * Add specific class kind listing. Also add label to the listing.
     *
     * @param list Iterable list of TypeElements
     * @param labelContent content tree of the label to be added
     * @param contentTree the content tree to which the class kind listing will be added
     */
    protected void addClassKindListing(Iterable<TypeElement> list, Content labelContent,
            HtmlTree contentTree) {
        SortedSet<TypeElement> tset = utils.filterOutPrivateClasses(list, configuration.javafx);
        if (!tset.isEmpty()) {
            boolean printedHeader = false;
            HtmlTree htmlTree = HtmlTree.SECTION();
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
                    Content heading = HtmlTree.HEADING(Headings.CONTENT_HEADING,
                            true, labelContent);
                    htmlTree.add(heading);
                    printedHeader = true;
                }
                Content arr_i_name = new StringContent(utils.getSimpleName(typeElement));
                if (utils.isInterface(typeElement)) {
                    arr_i_name = HtmlTree.SPAN(HtmlStyle.interfaceName, arr_i_name);
                }
                Content link = getLink(new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.ALL_CLASSES_FRAME, typeElement).label(arr_i_name).target("classFrame"));
                Content li = HtmlTree.LI(link);
                ul.add(li);
            }
            htmlTree.add(ul);
            contentTree.add(htmlTree);
        }
    }
}
