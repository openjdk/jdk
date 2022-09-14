/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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
 */
public class ConstantsSummaryBuilder extends AbstractBuilder {

    /**
     * The maximum number of package directories shown in the headings of
     * the constant values contents list and headings.
     */
    private static final int MAX_CONSTANT_VALUE_INDEX_LENGTH = 2;

    /**
     * The writer used to write the results.
     */
    protected ConstantsSummaryWriter writer;

    /**
     * The set of type elements that have constant fields.
     */
    protected final Set<TypeElement> typeElementsWithConstFields;

    /**
     * The set of package-group headings.
     */
    protected final Set<String> packageGroupHeadings;

    /**
     * The current package being documented.
     */
    private PackageElement currentPackage;

    /**
     * The current class being documented.
     */
    private TypeElement currentClass;

    /**
     * Constructs a new {@code ConstantsSummaryBuilder}.
     *
     * @param context       the build context
     */
    private ConstantsSummaryBuilder(Context context) {
        super(context);
        this.typeElementsWithConstFields = new HashSet<>();
        this.packageGroupHeadings = new TreeSet<>(utils::compareStrings);
    }

    /**
     * Constructs a {@code ConstantsSummaryBuilder}.
     *
     * @param context       the build context
     * @return the new ConstantsSummaryBuilder
     */
    public static ConstantsSummaryBuilder getInstance(Context context) {
        return new ConstantsSummaryBuilder(context);
    }

    @Override
    public void build() throws DocletException {
        boolean anyConstants = configuration.packages.stream().anyMatch(this::hasConstantField);
        if (!anyConstants) {
            return;
        }

        writer = configuration.getWriterFactory().getConstantsSummaryWriter();
        if (writer == null) {
            //Doclet does not support this output.
            return;
        }
        buildConstantSummary();
    }

    /**
     * Builds the constant summary page.
     *
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildConstantSummary() throws DocletException {
        Content content = writer.getHeader();

        buildContents();
        buildConstantSummaries();

        writer.addFooter();
        writer.printDocument(content);
    }

    /**
     * Builds the list of contents for the groups of packages appearing in the constants summary page.
     */
    protected void buildContents() {
        Content contentList = writer.getContentsHeader();
        packageGroupHeadings.clear();
        for (PackageElement pkg : configuration.packages) {
            String abbrevPackageName = getAbbrevPackageName(pkg);
            if (hasConstantField(pkg) && !packageGroupHeadings.contains(abbrevPackageName)) {
                writer.addLinkToPackageContent(abbrevPackageName, contentList);
                packageGroupHeadings.add(abbrevPackageName);
            }
        }
        writer.addContentsList(contentList);
    }

    /**
     * Builds the summary for each documented package.
     *
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildConstantSummaries() throws DocletException {
        packageGroupHeadings.clear();
        Content summaries = writer.getConstantSummaries();
        for (PackageElement aPackage : configuration.packages) {
            if (hasConstantField(aPackage)) {
                currentPackage = aPackage;
                //Build the documentation for the current package.
                buildPackageHeader(summaries);
                buildClassConstantSummary();
            }
        }
        writer.addConstantSummaries(summaries);
    }

    /**
     * Builds the header for the given package.
     *
     * @param target the content to which the package header will be added
     */
    protected void buildPackageHeader(Content target) {
        String abbrevPkgName = getAbbrevPackageName(currentPackage);
        if (!packageGroupHeadings.contains(abbrevPkgName)) {
            writer.addPackageGroup(abbrevPkgName, target);
            packageGroupHeadings.add(abbrevPkgName);
        }
    }

    /**
     * Builds the summary for the current class.
     *
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildClassConstantSummary()
            throws DocletException {
        SortedSet<TypeElement> classes = !currentPackage.isUnnamed()
                ? utils.getAllClasses(currentPackage)
                : configuration.typeElementCatalog.allUnnamedClasses();
        Content classConstantHeader = writer.getClassConstantHeader();
        for (TypeElement te : classes) {
            if (!typeElementsWithConstFields.contains(te) ||
                !utils.isIncluded(te)) {
                continue;
            }
            currentClass = te;
            //Build the documentation for the current class.

            buildConstantMembers(classConstantHeader);

        }
        writer.addClassConstant(classConstantHeader);
    }

    /**
     * Builds the summary of constant members in the class.
     *
     * @param target the content to which the table of constant members will be added
     */
    protected void buildConstantMembers(Content target) {
        new ConstantFieldBuilder(currentClass).buildMembersSummary(target);
    }

    /**
     * {@return true if the given package has constant fields to document}
     *
     * @param pkg   the package to be checked
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
     * {@return true if the given class has constant fields to document}
     *
     * @param typeElement the class to be checked
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
     * {@return the abbreviated name for a package, containing the leading segments of the name}
     *
     * @param pkg the package
     */
    public String getAbbrevPackageName(PackageElement pkg) {
        if (pkg.isUnnamed()) {
            return "";
        }

        String packageName = utils.getPackageName(pkg);
        int index = -1;
        for (int j = 0; j < MAX_CONSTANT_VALUE_INDEX_LENGTH; j++) {
            index = packageName.indexOf(".", index + 1);
        }
        return index == -1 ? packageName : packageName.substring(0, index);
    }

    /**
     * Builder for the table of fields with constant values.
     */
    private class ConstantFieldBuilder {

        /**
         * The type element that we are examining constants for.
         */
        protected TypeElement typeElement;

        /**
         * Constructs a {@code ConstantFieldBuilder}.
         * @param typeElement the type element that we are examining constants for
         */
        public ConstantFieldBuilder(TypeElement typeElement) {
            this.typeElement = typeElement;
        }

        /**
         * Builds the table of constants for a given class.
         *
         * @param target the content to which the table of class constants will be added
         */
        protected void buildMembersSummary(Content target) {
            SortedSet<VariableElement> members = members();
            if (!members.isEmpty()) {
                writer.addConstantMembers(typeElement, members, target);
            }
        }

        /**
         * {@return a set of visible constant fields for the given type}
         */
        protected SortedSet<VariableElement> members() {
            VisibleMemberTable vmt = configuration.getVisibleMemberTable(typeElement);
            List<Element> members = new ArrayList<>();
            members.addAll(vmt.getVisibleMembers(FIELDS));
            members.addAll(vmt.getVisibleMembers(ENUM_CONSTANTS));
            SortedSet<VariableElement> includes =
                    new TreeSet<>(utils.comparators.makeGeneralPurposeComparator());
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
