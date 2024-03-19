/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import jdk.javadoc.internal.doclets.formats.html.markup.BodyContents;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.Entity;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlId;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.TagName;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.Text;
import jdk.javadoc.internal.doclets.toolkit.DocletException;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.IndexItem;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable;


/**
 * Write the Constants Summary Page in HTML format.
 */
public class ConstantsSummaryWriter extends HtmlDocletWriter {

    /**
     * The maximum number of package directories shown in the headings of
     * the constant values contents list and headings.
     */
    private static final int MAX_CONSTANT_VALUE_INDEX_LENGTH = 2;

    /**
     * The current class being documented.
     */
    private TypeElement currentTypeElement;

    private final TableHeader constantsTableHeader;

    /**
     * The HTML tree for constant values summary currently being written.
     */
    private HtmlTree summarySection;

    private final BodyContents bodyContents = new BodyContents();

    private boolean hasConstants = false;


    /**
     * The set of type elements that have constant fields.
     */
    protected final Set<TypeElement> typeElementsWithConstFields;

    /**
     * The set of package-group headings.
     */
    protected final Set<String> packageGroupHeadings;

    private PackageElement currentPackage;
    private TypeElement currentClass; // FIXME: dup of currentTypeElement


    /**
     * Construct a ConstantsSummaryWriter.
     * @param configuration the configuration used in this run
     *        of the standard doclet.
     */
    public ConstantsSummaryWriter(HtmlConfiguration configuration) {
        super(configuration, DocPaths.CONSTANT_VALUES, false);
        constantsTableHeader = new TableHeader(
                contents.modifierAndTypeLabel, contents.constantFieldLabel, contents.valueLabel);

        this.typeElementsWithConstFields = new HashSet<>();
        this.packageGroupHeadings = new TreeSet<>(utils::compareStrings);
    }

    @Override
    public void buildPage() throws DocletException {
        boolean anyConstants = configuration.packages.stream().anyMatch(this::hasConstantField);
        if (!anyConstants) {
            return;
        }

        configuration.conditionalPages.add(HtmlConfiguration.ConditionalPage.CONSTANT_VALUES);
        writeGenerating();

        buildConstantSummary();
    }

    /**
     * Builds the constant summary page.
     *
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildConstantSummary() throws DocletException {
        Content content = getHeader();

        buildContents();
        buildConstantSummaries();

        addFooter();
        printDocument(content);
    }

    /**
     * Builds the list of contents for the groups of packages appearing in the constants summary page.
     */
    protected void buildContents() {
        tableOfContents.addLink(HtmlIds.TOP_OF_PAGE, Text.of(resources.getText("doclet.Constants_Summary")))
                .pushNestedList();
        packageGroupHeadings.clear();
        for (PackageElement pkg : configuration.packages) {
            String abbrevPackageName = getAbbrevPackageName(pkg);
            if (hasConstantField(pkg) && !packageGroupHeadings.contains(abbrevPackageName)) {
                addLinkToTableOfContents(abbrevPackageName);
                packageGroupHeadings.add(abbrevPackageName);
            }
        }
        tableOfContents.popNestedList();
        bodyContents.setSideContent(tableOfContents.toContent(true));
    }

    /**
     * Builds the summary for each documented package.
     */
    protected void buildConstantSummaries() {
        packageGroupHeadings.clear();
        Content summaries = new ContentBuilder();
        for (PackageElement aPackage : configuration.packages) {
            if (hasConstantField(aPackage)) {
                currentPackage = aPackage;
                //Build the documentation for the current package.
                buildPackageHeader(summaries);
                buildClassConstantSummary();
            }
        }
        addConstantSummaries(summaries);
    }

    /**
     * Builds the header for the given package.
     *
     * @param target the content to which the package header will be added
     */
    protected void buildPackageHeader(Content target) {
        String abbrevPkgName = getAbbrevPackageName(currentPackage);
        if (!packageGroupHeadings.contains(abbrevPkgName)) {
            addPackageGroup(abbrevPkgName, target);
            packageGroupHeadings.add(abbrevPkgName);
        }
    }

    /**
     * Builds the summary for the current class.
     */
    protected void buildClassConstantSummary() {
        SortedSet<TypeElement> classes = !currentPackage.isUnnamed()
                ? utils.getAllClasses(currentPackage)
                : configuration.typeElementCatalog.allUnnamedClasses();
        Content classConstantHeader = getClassConstantHeader();
        for (TypeElement te : classes) {
            if (!typeElementsWithConstFields.contains(te) ||
                    !utils.isIncluded(te)) {
                continue;
            }
            currentClass = te;
            //Build the documentation for the current class.

            buildConstantMembers(classConstantHeader);

        }
        addClassConstant(classConstantHeader);
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
    private boolean hasConstantField(TypeElement typeElement) {
        VisibleMemberTable vmt = configuration.getVisibleMemberTable(typeElement);
        List<? extends Element> fields = vmt.getVisibleMembers(VisibleMemberTable.Kind.FIELDS);
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
                addConstantMembers(typeElement, members, target);
            }
        }

        /**
         * {@return a set of visible constant fields for the given type}
         */
        protected SortedSet<VariableElement> members() {
            VisibleMemberTable vmt = configuration.getVisibleMemberTable(typeElement);
            List<Element> members = new ArrayList<>();
            members.addAll(vmt.getVisibleMembers(VisibleMemberTable.Kind.FIELDS));
            members.addAll(vmt.getVisibleMembers(VisibleMemberTable.Kind.ENUM_CONSTANTS));
            SortedSet<VariableElement> includes =
                    new TreeSet<>(utils.comparators.generalPurposeComparator());
            for (Element element : members) {
                VariableElement member = (VariableElement)element;
                if (member.getConstantValue() != null) {
                    includes.add(member);
                }
            }
            return includes;
        }
    }

     Content getHeader() {
         String label = resources.getText("doclet.Constants_Summary");
         HtmlTree body = getBody(getWindowTitle(label));
         bodyContents.setHeader(getHeader(PageMode.CONSTANT_VALUES));
         Content titleContent = contents.constantsSummaryTitle;
         var pHeading = HtmlTree.HEADING_TITLE(Headings.PAGE_TITLE_HEADING,
                 HtmlStyle.title, titleContent);
         var div = HtmlTree.DIV(HtmlStyle.header, pHeading);
         bodyContents.addMainContent(div);
         return body;
    }

    Content getContentsHeader() {
        return HtmlTree.UL(HtmlStyle.contentsList);
    }

    void addLinkToTableOfContents(String abbrevPackageName) {
        if (abbrevPackageName.isEmpty()) {
            tableOfContents.addLink(HtmlIds.UNNAMED_PACKAGE_ANCHOR, contents.defaultPackageLabel);
        } else {
            tableOfContents.addLink(HtmlId.of(abbrevPackageName), Text.of(abbrevPackageName + ".*"));
        }
    }

     void addPackageGroup(String abbrevPackageName, Content toContent) {
        Content headingContent;
        HtmlId anchorName;
        if (abbrevPackageName.isEmpty()) {
            anchorName = HtmlIds.UNNAMED_PACKAGE_ANCHOR;
            headingContent = contents.defaultPackageLabel;
        } else {
            anchorName = htmlIds.forPackageName(abbrevPackageName);
            headingContent = new ContentBuilder(
                    getPackageLabel(abbrevPackageName),
                    Text.of(".*"));
        }
        var heading = HtmlTree.HEADING_TITLE(
                Headings.ConstantsSummary.PACKAGE_HEADING,
                headingContent);
        summarySection = HtmlTree.SECTION(HtmlStyle.constantsSummary, heading)
                .setId(anchorName);

        toContent.add(summarySection);
    }

     Content getClassConstantHeader() {
        return HtmlTree.UL(HtmlStyle.blockList);
    }

     void addClassConstant(Content fromClassConstant) {
        summarySection.add(fromClassConstant);
        hasConstants = true;
    }

     void addConstantMembers(TypeElement typeElement, Collection<VariableElement> fields,
            Content target) {
        currentTypeElement = typeElement;

        //generate links backward only to public classes.
        Content classLink = (utils.isPublic(typeElement) || utils.isProtected(typeElement)) ?
            getLink(new HtmlLinkInfo(configuration,
                    HtmlLinkInfo.Kind.SHOW_TYPE_PARAMS_IN_LABEL, typeElement)) :
            Text.of(utils.getFullyQualifiedName(typeElement));

        PackageElement enclosingPackage  = utils.containingPackage(typeElement);
        Content caption = new ContentBuilder();
        if (!enclosingPackage.isUnnamed()) {
            caption.add(enclosingPackage.getQualifiedName());
            caption.add(".");
        }
        caption.add(classLink);

        var table = new Table<Void>(HtmlStyle.summaryTable)
                .setCaption(caption)
                .setHeader(constantsTableHeader)
                .setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colSecond, HtmlStyle.colLast);

        for (VariableElement field : fields) {
            table.addRow(getTypeColumn(field), getNameColumn(field), getValue(field));
        }
        target.add(HtmlTree.LI(table));
    }

    /**
     * Get the type column for the constant summary table row.
     *
     * @param member the field to be documented.
     * @return the type column of the constant table row
     */
    private Content getTypeColumn(VariableElement member) {
        Content typeContent = new ContentBuilder();
        var code = new HtmlTree(TagName.CODE)
                .setId(htmlIds.forMember(currentTypeElement, member));
        for (Modifier mod : member.getModifiers()) {
            code.add(Text.of(mod.toString()))
                    .add(Entity.NO_BREAK_SPACE);
        }
        Content type = getLink(new HtmlLinkInfo(configuration,
                HtmlLinkInfo.Kind.LINK_TYPE_PARAMS_AND_BOUNDS, member.asType()));
        code.add(type);
        typeContent.add(code);
        return typeContent;
    }

    /**
     * Get the name column for the constant summary table row.
     *
     * @param member the field to be documented.
     * @return the name column of the constant table row
     */
    private Content getNameColumn(VariableElement member) {
        Content nameContent = getDocLink(HtmlLinkInfo.Kind.PLAIN,
                member, member.getSimpleName());
        return HtmlTree.CODE(nameContent);
    }

    /**
     * Get the value column for the constant summary table row.
     *
     * @param member the field to be documented.
     * @return the value column of the constant table row
     */
    private Content getValue(VariableElement member) {
        String value = utils.constantValueExpression(member);
        return HtmlTree.CODE(Text.of(value));
    }

     void addConstantSummaries(Content content) {
        bodyContents.addMainContent(content);
    }

     void addFooter() {
        bodyContents.setFooter(getFooter());
    }

     void printDocument(Content content) throws DocFileIOException {
        content.add(bodyContents);
        printHtmlDocument(null, "summary of constants", content);

        if (hasConstants && configuration.indexBuilder != null) {
            configuration.indexBuilder.add(IndexItem.of(IndexItem.Category.TAGS,
                    resources.getText("doclet.Constants_Summary"), path));
        }
    }
}
