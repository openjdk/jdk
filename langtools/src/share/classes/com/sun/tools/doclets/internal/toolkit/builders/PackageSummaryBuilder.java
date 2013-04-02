/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.internal.toolkit.builders;

import java.io.*;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * Builds the summary for a given package.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 * @since 1.5
 */
public class PackageSummaryBuilder extends AbstractBuilder {
    /**
     * The root element of the package summary XML is {@value}.
     */
    public static final String ROOT = "PackageDoc";

    /**
     * The package being documented.
     */
    private final PackageDoc packageDoc;

    /**
     * The doclet specific writer that will output the result.
     */
    private final PackageSummaryWriter packageWriter;

    /**
     * The content that will be added to the package summary documentation tree.
     */
    private Content contentTree;

    /**
     * Construct a new PackageSummaryBuilder.
     *
     * @param context  the build context.
     * @param pkg the package being documented.
     * @param packageWriter the doclet specific writer that will output the
     *        result.
     */
    private PackageSummaryBuilder(Context context,
            PackageDoc pkg,
            PackageSummaryWriter packageWriter) {
        super(context);
        this.packageDoc = pkg;
        this.packageWriter = packageWriter;
    }

    /**
     * Construct a new PackageSummaryBuilder.
     *
     * @param context  the build context.
     * @param pkg the package being documented.
     * @param packageWriter the doclet specific writer that will output the
     *        result.
     *
     * @return an instance of a PackageSummaryBuilder.
     */
    public static PackageSummaryBuilder getInstance(Context context,
            PackageDoc pkg, PackageSummaryWriter packageWriter) {
        return new PackageSummaryBuilder(context, pkg, packageWriter);
    }

    /**
     * Build the package summary.
     */
    public void build() throws IOException {
        if (packageWriter == null) {
            //Doclet does not support this output.
            return;
        }
        build(layoutParser.parseXML(ROOT), contentTree);
    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return ROOT;
    }

    /**
     * Build the package documentation.
     *
     * @param node the XML element that specifies which components to document
     * @param contentTree the content tree to which the documentation will be added
     */
    public void buildPackageDoc(XMLNode node, Content contentTree) throws Exception {
        contentTree = packageWriter.getPackageHeader(
                Util.getPackageName(packageDoc));
        buildChildren(node, contentTree);
        packageWriter.addPackageFooter(contentTree);
        packageWriter.printDocument(contentTree);
        packageWriter.close();
        Util.copyDocFiles(configuration, packageDoc);
    }

    /**
     * Build the content for the package doc.
     *
     * @param node the XML element that specifies which components to document
     * @param contentTree the content tree to which the package contents
     *                    will be added
     */
    public void buildContent(XMLNode node, Content contentTree) {
        Content packageContentTree = packageWriter.getContentHeader();
        buildChildren(node, packageContentTree);
        contentTree.addContent(packageContentTree);
    }

    /**
     * Build the package summary.
     *
     * @param node the XML element that specifies which components to document
     * @param packageContentTree the package content tree to which the summaries will
     *                           be added
     */
    public void buildSummary(XMLNode node, Content packageContentTree) {
        Content summaryContentTree = packageWriter.getSummaryHeader();
        buildChildren(node, summaryContentTree);
        packageContentTree.addContent(summaryContentTree);
    }

    /**
     * Build the summary for the interfaces in this package.
     *
     * @param node the XML element that specifies which components to document
     * @param summaryContentTree the summary tree to which the interface summary
     *                           will be added
     */
    public void buildInterfaceSummary(XMLNode node, Content summaryContentTree) {
        String interfaceTableSummary =
                configuration.getText("doclet.Member_Table_Summary",
                configuration.getText("doclet.Interface_Summary"),
                configuration.getText("doclet.interfaces"));
        String[] interfaceTableHeader = new String[] {
            configuration.getText("doclet.Interface"),
            configuration.getText("doclet.Description")
        };
        ClassDoc[] interfaces =
                packageDoc.isIncluded()
                        ? packageDoc.interfaces()
                        : configuration.classDocCatalog.interfaces(
                                Util.getPackageName(packageDoc));
        interfaces = Util.filterOutPrivateClasses(interfaces, configuration.javafx);
        if (interfaces.length > 0) {
            packageWriter.addClassesSummary(
                    interfaces,
                    configuration.getText("doclet.Interface_Summary"),
                    interfaceTableSummary, interfaceTableHeader, summaryContentTree);
        }
    }

    /**
     * Build the summary for the classes in this package.
     *
     * @param node the XML element that specifies which components to document
     * @param summaryContentTree the summary tree to which the class summary will
     *                           be added
     */
    public void buildClassSummary(XMLNode node, Content summaryContentTree) {
        String classTableSummary =
                configuration.getText("doclet.Member_Table_Summary",
                configuration.getText("doclet.Class_Summary"),
                configuration.getText("doclet.classes"));
        String[] classTableHeader = new String[] {
            configuration.getText("doclet.Class"),
            configuration.getText("doclet.Description")
        };
        ClassDoc[] classes =
                packageDoc.isIncluded()
                        ? packageDoc.ordinaryClasses()
                        : configuration.classDocCatalog.ordinaryClasses(
                                Util.getPackageName(packageDoc));
        classes = Util.filterOutPrivateClasses(classes, configuration.javafx);
        if (classes.length > 0) {
            packageWriter.addClassesSummary(
                    classes,
                    configuration.getText("doclet.Class_Summary"),
                    classTableSummary, classTableHeader, summaryContentTree);
        }
    }

    /**
     * Build the summary for the enums in this package.
     *
     * @param node the XML element that specifies which components to document
     * @param summaryContentTree the summary tree to which the enum summary will
     *                           be added
     */
    public void buildEnumSummary(XMLNode node, Content summaryContentTree) {
        String enumTableSummary =
                configuration.getText("doclet.Member_Table_Summary",
                configuration.getText("doclet.Enum_Summary"),
                configuration.getText("doclet.enums"));
        String[] enumTableHeader = new String[] {
            configuration.getText("doclet.Enum"),
            configuration.getText("doclet.Description")
        };
        ClassDoc[] enums =
                packageDoc.isIncluded()
                        ? packageDoc.enums()
                        : configuration.classDocCatalog.enums(
                                Util.getPackageName(packageDoc));
        enums = Util.filterOutPrivateClasses(enums, configuration.javafx);
        if (enums.length > 0) {
            packageWriter.addClassesSummary(
                    enums,
                    configuration.getText("doclet.Enum_Summary"),
                    enumTableSummary, enumTableHeader, summaryContentTree);
        }
    }

    /**
     * Build the summary for the exceptions in this package.
     *
     * @param node the XML element that specifies which components to document
     * @param summaryContentTree the summary tree to which the exception summary will
     *                           be added
     */
    public void buildExceptionSummary(XMLNode node, Content summaryContentTree) {
        String exceptionTableSummary =
                configuration.getText("doclet.Member_Table_Summary",
                configuration.getText("doclet.Exception_Summary"),
                configuration.getText("doclet.exceptions"));
        String[] exceptionTableHeader = new String[] {
            configuration.getText("doclet.Exception"),
            configuration.getText("doclet.Description")
        };
        ClassDoc[] exceptions =
                packageDoc.isIncluded()
                        ? packageDoc.exceptions()
                        : configuration.classDocCatalog.exceptions(
                                Util.getPackageName(packageDoc));
        exceptions = Util.filterOutPrivateClasses(exceptions, configuration.javafx);
        if (exceptions.length > 0) {
            packageWriter.addClassesSummary(
                    exceptions,
                    configuration.getText("doclet.Exception_Summary"),
                    exceptionTableSummary, exceptionTableHeader, summaryContentTree);
        }
    }

    /**
     * Build the summary for the errors in this package.
     *
     * @param node the XML element that specifies which components to document
     * @param summaryContentTree the summary tree to which the error summary will
     *                           be added
     */
    public void buildErrorSummary(XMLNode node, Content summaryContentTree) {
        String errorTableSummary =
                configuration.getText("doclet.Member_Table_Summary",
                configuration.getText("doclet.Error_Summary"),
                configuration.getText("doclet.errors"));
        String[] errorTableHeader = new String[] {
            configuration.getText("doclet.Error"),
            configuration.getText("doclet.Description")
        };
        ClassDoc[] errors =
                packageDoc.isIncluded()
                        ? packageDoc.errors()
                        : configuration.classDocCatalog.errors(
                                Util.getPackageName(packageDoc));
        errors = Util.filterOutPrivateClasses(errors, configuration.javafx);
        if (errors.length > 0) {
            packageWriter.addClassesSummary(
                    errors,
                    configuration.getText("doclet.Error_Summary"),
                    errorTableSummary, errorTableHeader, summaryContentTree);
        }
    }

    /**
     * Build the summary for the annotation type in this package.
     *
     * @param node the XML element that specifies which components to document
     * @param summaryContentTree the summary tree to which the annotation type
     *                           summary will be added
     */
    public void buildAnnotationTypeSummary(XMLNode node, Content summaryContentTree) {
        String annotationtypeTableSummary =
                configuration.getText("doclet.Member_Table_Summary",
                configuration.getText("doclet.Annotation_Types_Summary"),
                configuration.getText("doclet.annotationtypes"));
        String[] annotationtypeTableHeader = new String[] {
            configuration.getText("doclet.AnnotationType"),
            configuration.getText("doclet.Description")
        };
        ClassDoc[] annotationTypes =
                packageDoc.isIncluded()
                        ? packageDoc.annotationTypes()
                        : configuration.classDocCatalog.annotationTypes(
                                Util.getPackageName(packageDoc));
        annotationTypes = Util.filterOutPrivateClasses(annotationTypes, configuration.javafx);
        if (annotationTypes.length > 0) {
            packageWriter.addClassesSummary(
                    annotationTypes,
                    configuration.getText("doclet.Annotation_Types_Summary"),
                    annotationtypeTableSummary, annotationtypeTableHeader,
                    summaryContentTree);
        }
    }

    /**
     * Build the description of the summary.
     *
     * @param node the XML element that specifies which components to document
     * @param packageContentTree the tree to which the package description will
     *                           be added
     */
    public void buildPackageDescription(XMLNode node, Content packageContentTree) {
        if (configuration.nocomment) {
            return;
        }
        packageWriter.addPackageDescription(packageContentTree);
    }

    /**
     * Build the tags of the summary.
     *
     * @param node the XML element that specifies which components to document
     * @param packageContentTree the tree to which the package tags will be added
     */
    public void buildPackageTags(XMLNode node, Content packageContentTree) {
        if (configuration.nocomment) {
            return;
        }
        packageWriter.addPackageTags(packageContentTree);
    }
}
