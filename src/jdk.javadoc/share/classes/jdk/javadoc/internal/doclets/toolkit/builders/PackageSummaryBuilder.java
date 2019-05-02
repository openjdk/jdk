/*
 * Copyright (c) 2003, 2019, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.builders;

import java.util.Set;
import java.util.SortedSet;

import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.DocFilesHandler;
import jdk.javadoc.internal.doclets.toolkit.DocletException;
import jdk.javadoc.internal.doclets.toolkit.PackageSummaryWriter;


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
 */
public class PackageSummaryBuilder extends AbstractBuilder {

    /**
     * The package being documented.
     */
    private final PackageElement packageElement;

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
            PackageElement pkg,
            PackageSummaryWriter packageWriter) {
        super(context);
        this.packageElement = pkg;
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
            PackageElement pkg, PackageSummaryWriter packageWriter) {
        return new PackageSummaryBuilder(context, pkg, packageWriter);
    }

    /**
     * Build the package summary.
     *
     * @throws DocletException if there is a problem while building the documentation
     */
    @Override
    public void build() throws DocletException {
        if (packageWriter == null) {
            //Doclet does not support this output.
            return;
        }
        buildPackageDoc(contentTree);
    }

    /**
     * Build the package documentation.
     *
     * @param contentTree the content tree to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildPackageDoc(Content contentTree) throws DocletException {
        contentTree = packageWriter.getPackageHeader(utils.getPackageName(packageElement));

        buildContent(contentTree);

        packageWriter.addPackageFooter(contentTree);
        packageWriter.printDocument(contentTree);
        DocFilesHandler docFilesHandler = configuration
                .getWriterFactory()
                .getDocFilesHandler(packageElement);
        docFilesHandler.copyDocFiles();
    }

    /**
     * Build the content for the package.
     *
     * @param contentTree the content tree to which the package contents
     *                    will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildContent(Content contentTree) throws DocletException {
        Content packageContentTree = packageWriter.getContentHeader();

        buildPackageDescription(packageContentTree);
        buildPackageTags(packageContentTree);
        buildSummary(packageContentTree);

        packageWriter.addPackageContent(contentTree, packageContentTree);
    }

    /**
     * Build the package summary.
     *
     * @param packageContentTree the package content tree to which the summaries will
     *                           be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildSummary(Content packageContentTree) throws DocletException {
        Content summaryContentTree = packageWriter.getSummaryHeader();

        buildInterfaceSummary(summaryContentTree);
        buildClassSummary(summaryContentTree);
        buildEnumSummary(summaryContentTree);
        buildExceptionSummary(summaryContentTree);
        buildErrorSummary(summaryContentTree);
        buildAnnotationTypeSummary(summaryContentTree);

        packageContentTree.add(packageWriter.getPackageSummary(summaryContentTree));
    }

    /**
     * Build the summary for the interfaces in this package.
     *
     * @param summaryContentTree the summary tree to which the interface summary
     *                           will be added
     */
    protected void buildInterfaceSummary(Content summaryContentTree) {
        SortedSet<TypeElement> ilist = utils.isSpecified(packageElement)
                        ? utils.getTypeElementsAsSortedSet(utils.getInterfaces(packageElement))
                        : configuration.typeElementCatalog.interfaces(packageElement);
        SortedSet<TypeElement> interfaces = utils.filterOutPrivateClasses(ilist, configuration.javafx);
        if (!interfaces.isEmpty()) {
            packageWriter.addInterfaceSummary(interfaces, summaryContentTree);
        }
    }

    /**
     * Build the summary for the classes in this package.
     *
     * @param summaryContentTree the summary tree to which the class summary will
     *                           be added
     */
    protected void buildClassSummary(Content summaryContentTree) {
        SortedSet<TypeElement> clist = utils.isSpecified(packageElement)
            ? utils.getTypeElementsAsSortedSet(utils.getOrdinaryClasses(packageElement))
            : configuration.typeElementCatalog.ordinaryClasses(packageElement);
        SortedSet<TypeElement> classes = utils.filterOutPrivateClasses(clist, configuration.javafx);
        if (!classes.isEmpty()) {
            packageWriter.addClassSummary(classes, summaryContentTree);
        }
    }

    /**
     * Build the summary for the enums in this package.
     *
     * @param summaryContentTree the summary tree to which the enum summary will
     *                           be added
     */
    protected void buildEnumSummary(Content summaryContentTree) {
        SortedSet<TypeElement> elist = utils.isSpecified(packageElement)
            ? utils.getTypeElementsAsSortedSet(utils.getEnums(packageElement))
            : configuration.typeElementCatalog.enums(packageElement);
        SortedSet<TypeElement> enums = utils.filterOutPrivateClasses(elist, configuration.javafx);
        if (!enums.isEmpty()) {
            packageWriter.addEnumSummary(enums, summaryContentTree);
        }
    }

    /**
     * Build the summary for the exceptions in this package.
     *
     * @param summaryContentTree the summary tree to which the exception summary will
     *                           be added
     */
    protected void buildExceptionSummary(Content summaryContentTree) {
        Set<TypeElement> iexceptions =
            utils.isSpecified(packageElement)
                ? utils.getTypeElementsAsSortedSet(utils.getExceptions(packageElement))
                : configuration.typeElementCatalog.exceptions(packageElement);
        SortedSet<TypeElement> exceptions = utils.filterOutPrivateClasses(iexceptions,
                configuration.javafx);
        if (!exceptions.isEmpty()) {
            packageWriter.addExceptionSummary(exceptions, summaryContentTree);
        }
    }

    /**
     * Build the summary for the errors in this package.
     *
     * @param summaryContentTree the summary tree to which the error summary will
     *                           be added
     */
    protected void buildErrorSummary(Content summaryContentTree) {
        Set<TypeElement> ierrors =
            utils.isSpecified(packageElement)
                ? utils.getTypeElementsAsSortedSet(utils.getErrors(packageElement))
                : configuration.typeElementCatalog.errors(packageElement);
        SortedSet<TypeElement> errors = utils.filterOutPrivateClasses(ierrors, configuration.javafx);
        if (!errors.isEmpty()) {
            packageWriter.addErrorSummary(errors, summaryContentTree);
        }
    }

    /**
     * Build the summary for the annotation type in this package.
     *
     * @param summaryContentTree the summary tree to which the annotation type
     *                           summary will be added
     */
    protected void buildAnnotationTypeSummary(Content summaryContentTree) {
        SortedSet<TypeElement> iannotationTypes =
            utils.isSpecified(packageElement)
                ? utils.getTypeElementsAsSortedSet(utils.getAnnotationTypes(packageElement))
                : configuration.typeElementCatalog.annotationTypes(packageElement);
        SortedSet<TypeElement> annotationTypes = utils.filterOutPrivateClasses(iannotationTypes,
                configuration.javafx);
        if (!annotationTypes.isEmpty()) {
            packageWriter.addAnnotationTypeSummary(annotationTypes, summaryContentTree);
        }
    }

    /**
     * Build the description of the summary.
     *
     * @param packageContentTree the tree to which the package description will
     *                           be added
     */
    protected void buildPackageDescription(Content packageContentTree) {
        if (configuration.nocomment) {
            return;
        }
        packageWriter.addPackageDescription(packageContentTree);
    }

    /**
     * Build the tags of the summary.
     *
     * @param packageContentTree the tree to which the package tags will be added
     */
    protected void buildPackageTags(Content packageContentTree) {
        if (configuration.nocomment) {
            return;
        }
        packageWriter.addPackageTags(packageContentTree);
    }
}
