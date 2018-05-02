/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import jdk.javadoc.internal.doclets.toolkit.ConstantsSummaryWriter;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.DocletException;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable;

import static jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable.Kind.*;

/**
 * Builds the Constants Summary Page.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 */
public class ConstantsSummaryBuilder extends AbstractBuilder {

    /**
     * The maximum number of package directories shown in the constant
     * value index.
     */
    public static final int MAX_CONSTANT_VALUE_INDEX_LENGTH = 2;

    /**
     * The writer used to write the results.
     */
    protected final ConstantsSummaryWriter writer;

    /**
     * The set of TypeElements that have constant fields.
     */
    protected final Set<TypeElement> typeElementsWithConstFields;

    /**
     * The set of printed package headers.
     */
    protected final Set<PackageElement> printedPackageHeaders;

    /**
     * The current package being documented.
     */
    private PackageElement currentPackage;

    /**
     * The current class being documented.
     */
    private TypeElement currentClass;

    /**
     * The content tree for the constant summary documentation.
     */
    private Content contentTree;

    /**
     * True if first package is listed.
     */
    private boolean first = true;

    /**
     * Construct a new ConstantsSummaryBuilder.
     *
     * @param context       the build context.
     * @param writer        the writer for the summary.
     */
    private ConstantsSummaryBuilder(Context context,
            ConstantsSummaryWriter writer) {
        super(context);
        this.writer = writer;
        this.typeElementsWithConstFields = new HashSet<>();
        this.printedPackageHeaders = new TreeSet<>(utils.makePackageComparator());
    }

    /**
     * Construct a ConstantsSummaryBuilder.
     *
     * @param context       the build context.
     * @param writer        the writer for the summary.
     * @return the new ConstantsSummaryBuilder
     */
    public static ConstantsSummaryBuilder getInstance(Context context,
            ConstantsSummaryWriter writer) {
        return new ConstantsSummaryBuilder(context, writer);
    }

    /**
     * {@inheritDoc}
     * @throws DocletException if there is a problem while building the documentation
     */
    @Override
    public void build() throws DocletException {
        if (writer == null) {
            //Doclet does not support this output.
            return;
        }
        buildConstantSummary(contentTree);
    }

    /**
     * Build the constant summary.
     *
     * @param contentTree the content tree to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildConstantSummary(Content contentTree) throws DocletException {
        contentTree = writer.getHeader();

        buildContents(contentTree);
        buildConstantSummaries(contentTree);

        writer.addFooter(contentTree);
        writer.printDocument(contentTree);
    }

    /**
     * Build the list of packages.
     *
     * @param contentTree the content tree to which the content list will be added
     */
    protected void buildContents(Content contentTree) {
        Content contentListTree = writer.getContentsHeader();
        printedPackageHeaders.clear();
        for (PackageElement pkg : configuration.packages) {
            if (hasConstantField(pkg) && !hasPrintedPackageIndex(pkg)) {
                writer.addLinkToPackageContent(pkg, printedPackageHeaders, contentListTree);
            }
        }
        writer.addContentsList(contentTree, contentListTree);
    }

    /**
     * Build the summary for each documented package.
     *
     * @param contentTree the tree to which the summaries will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildConstantSummaries(Content contentTree) throws DocletException {
        printedPackageHeaders.clear();
        Content summariesTree = writer.getConstantSummaries();
        for (PackageElement aPackage : configuration.packages) {
            if (hasConstantField(aPackage)) {
                currentPackage = aPackage;
                //Build the documentation for the current package.

                buildPackageHeader(summariesTree);
                buildClassConstantSummary(summariesTree);

                first = false;
            }
        }
        writer.addConstantSummaries(contentTree, summariesTree);
    }

    /**
     * Build the header for the given package.
     *
     * @param summariesTree the tree to which the package header will be added
     */
    protected void buildPackageHeader(Content summariesTree) {
        PackageElement abbrevPkg = configuration.workArounds.getAbbreviatedPackageElement(currentPackage);
        if (!printedPackageHeaders.contains(abbrevPkg)) {
            writer.addPackageName(currentPackage, summariesTree, first);
            printedPackageHeaders.add(abbrevPkg);
        }
    }

    /**
     * Build the summary for the current class.
     *
     * @param summariesTree the tree to which the class constant summary will be added
     * @throws DocletException if there is a problem while building the documentation
     *
     */
    protected void buildClassConstantSummary(Content summariesTree)
            throws DocletException {
        SortedSet<TypeElement> classes = !currentPackage.isUnnamed()
                ? utils.getAllClasses(currentPackage)
                : configuration.typeElementCatalog.allUnnamedClasses();
        Content classConstantTree = writer.getClassConstantHeader();
        for (TypeElement te : classes) {
            if (!typeElementsWithConstFields.contains(te) ||
                !utils.isIncluded(te)) {
                continue;
            }
            currentClass = te;
            //Build the documentation for the current class.

            buildConstantMembers(classConstantTree);

        }
        writer.addClassConstant(summariesTree, classConstantTree);
    }

    /**
     * Build the summary of constant members in the class.
     *
     * @param classConstantTree the tree to which the constant members table
     *                          will be added
     */
    protected void buildConstantMembers(Content classConstantTree) {
        new ConstantFieldBuilder(currentClass).buildMembersSummary(classConstantTree);
    }

    /**
     * Return true if the given package has constant fields to document.
     *
     * @param pkg   the package being checked.
     * @return true if the given package has constant fields to document.
     */
    private boolean hasConstantField(PackageElement pkg) {
        SortedSet<TypeElement> classes = !pkg.isUnnamed()
                  ? utils.getAllClasses(pkg)
                  : configuration.typeElementCatalog.allUnnamedClasses();
        boolean found = false;
        for (TypeElement te : classes) {
            if (utils.isIncluded(te) && hasConstantField(te)) {
                found = true;
            }
        }
        return found;
    }

    /**
     * Return true if the given class has constant fields to document.
     *
     * @param typeElement the class being checked.
     * @return true if the given package has constant fields to document.
     */
    private boolean hasConstantField (TypeElement typeElement) {
        VisibleMemberTable vmt = configuration.getVisibleMemberTable(typeElement);
        List<? extends Element> fields = vmt.getVisibleMembers(FIELDS);
        for (Element f : fields) {
            VariableElement field = (VariableElement)f;
            if (field.getConstantValue() != null) {
                typeElementsWithConstFields.add(typeElement);
                return true;
            }
        }
        return false;
    }

    /**
     * Return true if the given package name has been printed.  Also
     * return true if the root of this package has been printed.
     *
     * @param pkg the name of the package to check.
     */
    private boolean hasPrintedPackageIndex(PackageElement pkg) {
        for (PackageElement printedPkg : printedPackageHeaders) {
            if (utils.getPackageName(pkg).startsWith(utils.parsePackageName(printedPkg))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Print the table of constants.
     *
     * @author Jamie Ho
     */
    private class ConstantFieldBuilder {

        /**
         * The typeElement that we are examining constants for.
         */
        protected TypeElement typeElement;

        /**
         * Construct a ConstantFieldSubWriter.
         * @param typeElement the typeElement that we are examining constants for.
         */
        public ConstantFieldBuilder(TypeElement typeElement) {
            this.typeElement = typeElement;
        }

        /**
         * Builds the table of constants for a given class.
         *
         * @param classConstantTree the tree to which the class constants table
         *                          will be added
         */
        protected void buildMembersSummary(Content classConstantTree) {
            SortedSet<VariableElement> members = members();
            if (!members.isEmpty()) {
                writer.addConstantMembers(typeElement, members, classConstantTree);
            }
        }

        /**
         * Returns a set of visible constant fields for the given type.
         * @return the set of visible constant fields for the given type.
         */
        protected SortedSet<VariableElement> members() {
            VisibleMemberTable vmt = configuration.getVisibleMemberTable(typeElement);
            List<Element> members = new ArrayList<>();
            members.addAll(vmt.getVisibleMembers(FIELDS));
            members.addAll(vmt.getVisibleMembers(ENUM_CONSTANTS));
            SortedSet<VariableElement> includes =
                    new TreeSet<>(utils.makeGeneralPurposeComparator());
            for (Element element : members) {
                VariableElement member = (VariableElement)element;
                if (member.getConstantValue() != null) {
                    includes.add(member);
                }
            }
            return includes;
        }
    }
}
