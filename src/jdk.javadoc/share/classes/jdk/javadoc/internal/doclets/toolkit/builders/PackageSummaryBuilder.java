/*
 * Copyright (c) 2003, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    // Maximum number of subpackages and sibling packages to list in related packages table
    private final static int MAX_SUBPACKAGES = 20;
    private final static int MAX_SIBLING_PACKAGES = 5;

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
        buildPackageDoc();
    }

    /**
     * Build the package documentation.
     *
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildPackageDoc() throws DocletException {
        Content contentTree = packageWriter.getPackageHeader();

        buildContent();

        packageWriter.addPackageFooter();
        packageWriter.printDocument(contentTree);
        DocFilesHandler docFilesHandler = configuration
                .getWriterFactory()
                .getDocFilesHandler(packageElement);
        docFilesHandler.copyDocFiles();
    }

    /**
     * Build the content for the package.
     *
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildContent() throws DocletException {
        Content packageContentTree = packageWriter.getContentHeader();

        packageWriter.addPackageSignature(packageContentTree);
        buildPackageDescription(packageContentTree);
        buildPackageTags(packageContentTree);
        buildSummary(packageContentTree);

        packageWriter.addPackageContent(packageContentTree);
    }

    /**
     * Builds the list of summaries for the different kinds of types in this package.
     *
     * @param packageContentTree the package content tree to which the summaries will
     *                           be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildSummary(Content packageContentTree) throws DocletException {
        Content summariesList = packageWriter.getSummariesList();

        buildRelatedPackagesSummary(summariesList);
        buildInterfaceSummary(summariesList);
        buildClassSummary(summariesList);
        buildEnumSummary(summariesList);
        buildRecordSummary(summariesList);
        buildExceptionSummary(summariesList);
        buildErrorSummary(summariesList);
        buildAnnotationTypeSummary(summariesList);

        packageContentTree.add(packageWriter.getPackageSummary(summariesList));
    }

    /**
     * Builds a list of "nearby" packages (subpackages, super and sibling packages).
     *
     * @param summariesList the list of summaries to which the summary will be added
     */
    protected void buildRelatedPackagesSummary(Content summariesList) {
        List<PackageElement> packages = findRelatedPackages();
        if (!packages.isEmpty()) {
            packageWriter.addRelatedPackagesSummary(packages, summariesList);
        }
    }

    /**
     * Builds the summary for any interfaces in this package.
     *
     * @param summariesList the list of summaries to which the summary will be added
     */
    protected void buildInterfaceSummary(Content summariesList) {
        SortedSet<TypeElement> ilist = utils.isSpecified(packageElement)
                        ? utils.getTypeElementsAsSortedSet(utils.getInterfaces(packageElement))
                        : configuration.typeElementCatalog.interfaces(packageElement);
        SortedSet<TypeElement> interfaces = utils.filterOutPrivateClasses(ilist, options.javafx());
        if (!interfaces.isEmpty()) {
            packageWriter.addInterfaceSummary(interfaces, summariesList);
        }
    }

    /**
     * Builds the summary for any classes in this package.
     *
     * @param summariesList the list of summaries to which the summary will be added
     */
    protected void buildClassSummary(Content summariesList) {
        SortedSet<TypeElement> clist = utils.isSpecified(packageElement)
            ? utils.getTypeElementsAsSortedSet(utils.getOrdinaryClasses(packageElement))
            : configuration.typeElementCatalog.ordinaryClasses(packageElement);
        SortedSet<TypeElement> classes = utils.filterOutPrivateClasses(clist, options.javafx());
        if (!classes.isEmpty()) {
            packageWriter.addClassSummary(classes, summariesList);
        }
    }

    /**
     * Builds the summary for the enum types in this package.
     *
     * @param summariesList the list of summaries to which the summary will be added
     */
    protected void buildEnumSummary(Content summariesList) {
        SortedSet<TypeElement> elist = utils.isSpecified(packageElement)
            ? utils.getTypeElementsAsSortedSet(utils.getEnums(packageElement))
            : configuration.typeElementCatalog.enums(packageElement);
        SortedSet<TypeElement> enums = utils.filterOutPrivateClasses(elist, options.javafx());
        if (!enums.isEmpty()) {
            packageWriter.addEnumSummary(enums, summariesList);
        }
    }

    /**
     * Builds the summary for any record types in this package.
     *
     * @param summariesList the list of summaries to which the summary will be added
     */
    protected void buildRecordSummary(Content summariesList) {
        SortedSet<TypeElement> rlist = utils.isSpecified(packageElement)
                ? utils.getTypeElementsAsSortedSet(utils.getRecords(packageElement))
                : configuration.typeElementCatalog.records(packageElement);
        SortedSet<TypeElement> records = utils.filterOutPrivateClasses(rlist, options.javafx());
        if (!records.isEmpty()) {
            packageWriter.addRecordSummary(records, summariesList);
        }
    }

    /**
     * Builds the summary for any exception types in this package.
     *
     * @param summariesList the list of summaries to which the summary will be added
     */
    protected void buildExceptionSummary(Content summariesList) {
        Set<TypeElement> iexceptions =
            utils.isSpecified(packageElement)
                ? utils.getTypeElementsAsSortedSet(utils.getExceptions(packageElement))
                : configuration.typeElementCatalog.exceptions(packageElement);
        SortedSet<TypeElement> exceptions = utils.filterOutPrivateClasses(iexceptions,
                options.javafx());
        if (!exceptions.isEmpty()) {
            packageWriter.addExceptionSummary(exceptions, summariesList);
        }
    }

    /**
     * Builds the summary for any error types in this package.
     *
     * @param summariesList the list of summaries to which the summary will be added
     */
    protected void buildErrorSummary(Content summariesList) {
        Set<TypeElement> ierrors =
            utils.isSpecified(packageElement)
                ? utils.getTypeElementsAsSortedSet(utils.getErrors(packageElement))
                : configuration.typeElementCatalog.errors(packageElement);
        SortedSet<TypeElement> errors = utils.filterOutPrivateClasses(ierrors, options.javafx());
        if (!errors.isEmpty()) {
            packageWriter.addErrorSummary(errors, summariesList);
        }
    }

    /**
     * Builds the summary for any annotation types in this package.
     *
     * @param summariesList the list of summaries to which the summary will be added
     */
    protected void buildAnnotationTypeSummary(Content summariesList) {
        SortedSet<TypeElement> iannotationTypes =
            utils.isSpecified(packageElement)
                ? utils.getTypeElementsAsSortedSet(utils.getAnnotationTypes(packageElement))
                : configuration.typeElementCatalog.annotationTypes(packageElement);
        SortedSet<TypeElement> annotationTypes = utils.filterOutPrivateClasses(iannotationTypes,
                options.javafx());
        if (!annotationTypes.isEmpty()) {
            packageWriter.addAnnotationTypeSummary(annotationTypes, summariesList);
        }
    }

    /**
     * Build the description of the summary.
     *
     * @param packageContentTree the tree to which the package description will
     *                           be added
     */
    protected void buildPackageDescription(Content packageContentTree) {
        if (options.noComment()) {
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
        if (options.noComment()) {
            return;
        }
        packageWriter.addPackageTags(packageContentTree);
    }

    private List<PackageElement> findRelatedPackages() {
        String pkgName = packageElement.getQualifiedName().toString();

        // always add super package
        int lastdot = pkgName.lastIndexOf('.');
        String pkgPrefix = lastdot > 0 ? pkgName.substring(0, lastdot) : null;
        List<PackageElement> packages = new ArrayList<>(
                filterPackages(p -> p.getQualifiedName().toString().equals(pkgPrefix)));
        boolean hasSuperPackage = !packages.isEmpty();

        // add subpackages unless there are very many of them
        Pattern subPattern = Pattern.compile(pkgName.replace(".", "\\.") + "\\.\\w+");
        List<PackageElement> subpackages = filterPackages(
                p -> subPattern.matcher(p.getQualifiedName().toString()).matches());
        if (subpackages.size() <= MAX_SUBPACKAGES) {
            packages.addAll(subpackages);
        }

        // only add sibling packages if there is a non-empty super package, we are beneath threshold,
        // and number of siblings is beneath threshold as well
        if (hasSuperPackage && pkgPrefix != null && packages.size() <= MAX_SIBLING_PACKAGES) {
            Pattern siblingPattern = Pattern.compile(pkgPrefix.replace(".", "\\.") + "\\.\\w+");

            List<PackageElement> siblings = filterPackages(
                    p -> siblingPattern.matcher(p.getQualifiedName().toString()).matches());
            if (siblings.size() <= MAX_SIBLING_PACKAGES) {
                packages.addAll(siblings);
            }
        }
        return packages;
    }

    private List<PackageElement> filterPackages(Predicate<? super PackageElement> filter) {
        return configuration.packages.stream()
                .filter(p -> p != packageElement && filter.test(p))
                .toList();
    }
}
