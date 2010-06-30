/*
 * Copyright (c) 2003, 2008, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.javadoc.*;
import java.io.*;
import java.util.*;

/**
 * Builds the Constants Summary Page.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @author Jamie Ho
 * @since 1.5
 */
public class ConstantsSummaryBuilder extends AbstractBuilder {

    /**
     * The root element of the constant summary XML is {@value}.
     */
    public static final String ROOT = "ConstantSummary";

    /**
     * The maximum number of package directories shown in the constant
     * value index.
     */
    public static final int MAX_CONSTANT_VALUE_INDEX_LENGTH = 2;

    /**
     * The writer used to write the results.
     */
    protected ConstantsSummaryWriter writer;

    /**
     * The set of ClassDocs that have constant fields.
     */
    protected Set<ClassDoc> classDocsWithConstFields;

    /**
     * The set of printed package headers.
     */
    protected Set<String> printedPackageHeaders;

    /**
     * The current package being documented.
     */
    private PackageDoc currentPackage;

    /**
     * The current class being documented.
     */
    private ClassDoc currentClass;

    /**
     * Construct a new ConstantsSummaryBuilder.
     *
     * @param configuration the current configuration of the
     *                      doclet.
     */
    private ConstantsSummaryBuilder(Configuration configuration) {
        super(configuration);
    }

    /**
     * Construct a ConstantsSummaryBuilder.
     *
     * @param configuration the configuration used in this run
     *                      of the doclet.
     * @param writer        the writer for the summary.
     */
    public static ConstantsSummaryBuilder getInstance(
        Configuration configuration, ConstantsSummaryWriter writer) {
        ConstantsSummaryBuilder builder = new ConstantsSummaryBuilder(
            configuration);
        builder.writer = writer;
        builder.classDocsWithConstFields = new HashSet<ClassDoc>();
        return builder;
    }

    /**
     * {@inheritDoc}
     */
    public void build() throws IOException {
        if (writer == null) {
            //Doclet does not support this output.
            return;
        }
        build(LayoutParser.getInstance(configuration).parseXML(ROOT));
    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return ROOT;
    }

    /**
     * Build the constant summary.
     *
     * @param elements the list of elements describing constant summary
     *                 documentation.
     */
    public void buildConstantSummary(XMLNode node) throws Exception {
        buildChildren(node);
        writer.close();
    }

    /**
     * Build the header.
     */
    public void buildHeader(XMLNode node) {
        writer.writeHeader();
    }

    /**
     * Build the footer.
     */
    public void buildFooter(XMLNode node) {
        writer.writeFooter();
    }

    /**
     * Build the table of contents.
     */
    public void buildContents(XMLNode node) {
        writer.writeContentsHeader();
        PackageDoc[] packages = configuration.packages;
        printedPackageHeaders = new HashSet<String>();
        for (int i = 0; i < packages.length; i++) {
            if (hasConstantField(packages[i]) && ! hasPrintedPackageIndex(packages[i].name())) {
                writer.writeLinkToPackageContent(packages[i],
                    parsePackageName(packages[i].name()),
                    printedPackageHeaders);
            }
        }
        writer.writeContentsFooter();
    }

    /**
     * Build the summary for each documented package.
     *
     * @param elements the XML elements that represent the components
     *                 of documentation for each package.
     */
    public void buildConstantSummaries(XMLNode node) {
        PackageDoc[] packages = configuration.packages;
        printedPackageHeaders = new HashSet<String>();
        for (int i = 0; i < packages.length; i++) {
            if (hasConstantField(packages[i])) {
                currentPackage = packages[i];
                //Build the documentation for the current package.
                buildChildren(node);
            }
        }
    }

    /**
     * Build the summary for the current package.
     *
     * @param elements the list of XML elements that make up package
     *                 documentation.
     */
    public void buildPackageConstantSummary(XMLNode node) {
        buildChildren(node);
    }

    /**
     * Build the summary for the current class.
     *
     * @param elements the list of XML elements that make up the class
     *                 constant summary.
     */
    public void buildClassConstantSummary(XMLNode node) {
        ClassDoc[] classes = currentPackage.name().length() > 0 ?
            currentPackage.allClasses() :
            configuration.classDocCatalog.allClasses(
                DocletConstants.DEFAULT_PACKAGE_NAME);
        Arrays.sort(classes);
        for (int i = 0; i < classes.length; i++) {
            if (! classDocsWithConstFields.contains(classes[i]) ||
                ! classes[i].isIncluded()) {
                continue;
            }
            currentClass = classes[i];
            //Build the documentation for the current class.
            buildChildren(node);
        }
    }

    /**
     * Build the header for the given class.
     */
    public void buildPackageHeader(XMLNode node) {
        String parsedPackageName = parsePackageName(currentPackage.name());
        if (! printedPackageHeaders.contains(parsedPackageName)) {
            writer.writePackageName(currentPackage,
                parsePackageName(currentPackage.name()));
            printedPackageHeaders.add(parsedPackageName);
        }
    }

    /**
     * Build the header for the given class.
     */
    public void buildClassHeader(XMLNode node) {
        writer.writeConstantMembersHeader(currentClass);
    }

    /**
     * Print summary of constant members in the
     * class.
     */
    public void buildConstantMembers(XMLNode node) {
        new ConstantFieldBuilder(currentClass).buildMembersSummary(node);
    }

    /**
     * Build the footer for the given class.
     */
    public void buildClassFooter(XMLNode node) {
        writer.writeConstantMembersFooter(currentClass);
    }

    /**
     * Return true if the given package has constant fields to document.
     *
     * @param pkg   the package being checked.
     * @return true if the given package has constant fields to document.
     */
    private boolean hasConstantField(PackageDoc pkg) {
        ClassDoc[] classes;
        if (pkg.name().length() > 0) {
            classes = pkg.allClasses();
        } else {
            classes = configuration.classDocCatalog.allClasses(
                DocletConstants.DEFAULT_PACKAGE_NAME);
        }
        boolean found = false;
        for (int j = 0; j < classes.length; j++){
            if (classes[j].isIncluded() && hasConstantField(classes[j])) {
                found = true;
            }
        }
        return found;
    }

    /**
     * Return true if the given class has constant fields to document.
     *
     * @param classDoc the class being checked.
     * @return true if the given package has constant fields to document.
     */
    private boolean hasConstantField (ClassDoc classDoc) {
        VisibleMemberMap visibleMemberMapFields = new VisibleMemberMap(classDoc,
            VisibleMemberMap.FIELDS, configuration.nodeprecated);
        List<?> fields = visibleMemberMapFields.getLeafClassMembers(configuration);
        for (Iterator<?> iter = fields.iterator(); iter.hasNext(); ) {
            FieldDoc field = (FieldDoc) iter.next();
            if (field.constantValueExpression() != null) {
                classDocsWithConstFields.add(classDoc);
                return true;
            }
        }
        return false;
    }

    /**
     * Return true if the given package name has been printed.  Also
     * return true if the root of this package has been printed.
     *
     * @param pkgname the name of the package to check.
     */
    private boolean hasPrintedPackageIndex(String pkgname) {
        String[] list = printedPackageHeaders.toArray(new String[] {});
        for (int i = 0; i < list.length; i++) {
            if (pkgname.startsWith(list[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Print the table of constants.
     *
     * @author Jamie Ho
     * @since 1.4
     */
    private class ConstantFieldBuilder {

        /**
         * The map used to get the visible variables.
         */
        protected VisibleMemberMap visibleMemberMapFields = null;

        /**
         * The map used to get the visible variables.
         */
        protected VisibleMemberMap visibleMemberMapEnumConst = null;

        /**
         * The classdoc that we are examining constants for.
         */
        protected ClassDoc classdoc;

        /**
         * Construct a ConstantFieldSubWriter.
         * @param classdoc the classdoc that we are examining constants for.
         */
        public ConstantFieldBuilder(ClassDoc classdoc) {
            this.classdoc = classdoc;
            visibleMemberMapFields = new VisibleMemberMap(classdoc,
                VisibleMemberMap.FIELDS, configuration.nodeprecated);
            visibleMemberMapEnumConst = new VisibleMemberMap(classdoc,
                VisibleMemberMap.ENUM_CONSTANTS, configuration.nodeprecated);
        }

        /**
         * Builds the table of constants for a given class.
         */
        protected void buildMembersSummary(XMLNode node) {
            List<FieldDoc> members = new ArrayList<FieldDoc>(members());
            if (members.size() > 0) {
                Collections.sort(members);
                writer.writeConstantMembers(classdoc, members);
            }
        }

        /**
         * Return the list of visible constant fields for the given classdoc.
         * @param cd the classdoc to examine.
         * @return the list of visible constant fields for the given classdoc.
         */
        protected List<FieldDoc> members() {
            List<ProgramElementDoc> l = visibleMemberMapFields.getLeafClassMembers(configuration);
            l.addAll(visibleMemberMapEnumConst.getLeafClassMembers(configuration));
            Iterator<ProgramElementDoc> iter;

            if(l != null){
                iter = l.iterator();
            } else {
                return null;
            }
            List<FieldDoc> inclList = new LinkedList<FieldDoc>();
            FieldDoc member;
            while(iter.hasNext()){
                member = (FieldDoc)iter.next();
                if(member.constantValue() != null){
                    inclList.add(member);
                }
            }
            return inclList;
        }
    }

    /**
     * Parse the package name.  We only want to display package name up to
     * 2 levels.
     */
    private String parsePackageName(String pkgname) {
        int index = -1;
        for (int j = 0; j < MAX_CONSTANT_VALUE_INDEX_LENGTH; j++) {
            index = pkgname.indexOf(".", index + 1);
        }
        if (index != -1) {
            pkgname = pkgname.substring(0, index);
        }
        return pkgname;
    }
}
