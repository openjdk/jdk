/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.BodyContents;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyles;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.ContentBuilder;
import jdk.javadoc.internal.html.HtmlId;
import jdk.javadoc.internal.html.HtmlTag;
import jdk.javadoc.internal.html.HtmlTree;
import jdk.javadoc.internal.html.Text;


/**
 * Generate the Help File for the generated API documentation. The help file
 * contents are helpful for browsing the generated documentation.
 */
public class HelpWriter extends HtmlDocletWriter {

    private final String[][] SEARCH_EXAMPLES = {
            {"\"j.l.obj\"", "\"java.lang.Object\""},
            {"\"InpStr\"", "\"java.io.InputStream\""},
            {"\"math exact long\"", "\"java.lang.Math.absExact(long)\""}
    };

    Content overviewLink;
    Content indexLink;
    Content allClassesLink;
    Content allPackagesLink;

    /**
     * Constructor to construct HelpWriter object.
     * @param configuration the configuration
     */
    public HelpWriter(HtmlConfiguration configuration) {
        super(configuration, DocPaths.HELP_DOC);

        // yes, INDEX is correct in the following line
        overviewLink = links.createLink(DocPaths.INDEX, resources.getText("doclet.Overview"));
        allPackagesLink = links.createLink(DocPaths.ALLPACKAGES_INDEX, resources.getText("doclet.All_Packages"));
        allClassesLink = links.createLink(DocPaths.ALLCLASSES_INDEX, resources.getText("doclet.All_Classes_And_Interfaces"));
        DocPath dp = options.splitIndex()
                ? DocPaths.INDEX_FILES.resolve(DocPaths.indexN(1))
                : DocPaths.INDEX_ALL;
        indexLink = links.createLink(dp, resources.getText("doclet.Index"));
    }

    @Override
    public void buildPage() throws DocFileIOException {
        String title = resources.getText("doclet.Window_Help_title");
        HtmlTree body = getBody(getWindowTitle(title));
        ContentBuilder helpFileContent = new ContentBuilder();
        addHelpFileContents(helpFileContent);
        body.add(new BodyContents()
                .setHeader(getHeader(PageMode.HELP))
                .addMainContent(helpFileContent)
                .setSideContent(tableOfContents.toContent(false))
                .setFooter(getFooter()));
        printHtmlDocument(null, "help", body);
    }

    /**
     * Adds the help file contents from the resource file to the content.
     * While adding the help file contents it also keeps track of user options.
     *
     * The general organization is:
     * <ul>
     * <li>Heading, and TOC
     * <li>Navigation help
     * <li>Page-specific help
     * </ul>
     */
    protected void addHelpFileContents(Content content) {
        var mainHeading = getContent("doclet.help.main_heading");
        tableOfContents.addLink(HtmlIds.TOP_OF_PAGE, mainHeading);
        tableOfContents.pushNestedList();
        content.add(HtmlTree.HEADING(Headings.PAGE_TITLE_HEADING, HtmlStyles.title, mainHeading))
                .add(new HtmlTree(HtmlTag.HR))
                .add(getNavigationSection())
                .add(new HtmlTree(HtmlTag.HR))
                .add(getPageKindSection())
                .add(new HtmlTree(HtmlTag.HR))
                .add(HtmlTree.SPAN(HtmlStyles.helpFootnote,
                        getContent("doclet.help.footnote")));
        tableOfContents.popNestedList();
    }

    /**
     * Creates the navigation help, adding an entry into the main table-of-contents.
     *
     * The general organization is:
     * <ul>
     * <li>General notes
     * <li>Search
     * </ul>
     *
     * @return the content containing the help
     */
    private Content getNavigationSection() {
        Content content = new ContentBuilder();

        Content navHeading = contents.getContent("doclet.help.navigation.head");
        var navSection = HtmlTree.DIV(HtmlStyles.subTitle)
                .add(HtmlTree.HEADING(Headings.CONTENT_HEADING, navHeading).setId(HtmlIds.HELP_NAVIGATION))
                .add(contents.getContent("doclet.help.navigation.intro", overviewLink));
        if (options.createIndex()) {
            Content links = new ContentBuilder();
            if (!configuration.packages.isEmpty()) {
                links.add(allPackagesLink);
                links.add(", ");
            }
            links.add(allClassesLink);
            navSection.add(" ")
                    .add(contents.getContent("doclet.help.navigation.index", indexLink, links));
        }
        content.add(navSection);

        tableOfContents.addLink(HtmlIds.HELP_NAVIGATION, navHeading);
        tableOfContents.pushNestedList();

        HtmlTree section;

        // Search
        if (options.createIndex()) {
            section = newHelpSection(getContent("doclet.help.search.head"), PageMode.SEARCH);
            var searchIntro = HtmlTree.P(getContent("doclet.help.search.intro"));
            var searchExamples = HtmlTree.OL(HtmlStyles.tocList);
            for (String[] example : SEARCH_EXAMPLES) {
                searchExamples.add(HtmlTree.LI(
                        getContent("doclet.help.search.example",
                                HtmlTree.CODE(Text.of(example[0])), example[1])));
            }
            var searchSpecLink = HtmlTree.A(
                    resources.getText("doclet.help.search.spec.url", configuration.getDocletVersion().feature()),
                    getContent("doclet.help.search.spec.title"));
            var searchRefer = HtmlTree.P(getContent("doclet.help.search.refer", searchSpecLink));
            section.add(searchIntro)
                    .add(searchExamples)
                    .add(searchRefer);
            navSection.add(section);
        }
        tableOfContents.popNestedList();

        return content;
    }

    /**
     * Creates the page-specific help, adding an entry into the main table-of-contents.
     *
     * The general organization is:
     * <ul>
     * <li>Overview
     * <li>Declaration pages: module, package, classes
     * <li>Derived info for declarations: use and tree
     * <li>General summary info: deprecated, preview
     * <li>Detailed summary info: constant values, serialized form, system properties
     * <li>Index info: all packages, all classes, full index
     * </ul>
     *
     * @return the content containing the help
     */
    private Content getPageKindSection() {
        Content pageKindsHeading = contents.getContent("doclet.help.page_kinds.head");
        var pageKindsSection = HtmlTree.DIV(HtmlStyles.subTitle)
                .add(HtmlTree.HEADING(Headings.CONTENT_HEADING, pageKindsHeading).setId(HtmlIds.HELP_PAGES))
                .add(contents.getContent("doclet.help.page_kinds.intro"));

        tableOfContents.addLink(HtmlIds.HELP_PAGES, pageKindsHeading);
        tableOfContents.pushNestedList();

        HtmlTree section;

        // Overview
        if (options.createOverview()) {
            section = newHelpSection(contents.overviewLabel, PageMode.OVERVIEW);
            String overviewKey = configuration.showModules
                    ? "doclet.help.overview.modules.body"
                    : "doclet.help.overview.packages.body";
            section.add(HtmlTree.P(getContent(overviewKey, overviewLink)));
            pageKindsSection.add(section);
        }

        // Module
        if (configuration.showModules) {
            section = newHelpSection(contents.moduleLabel, PageMode.MODULE);
            Content moduleIntro = getContent("doclet.help.module.intro");
            var modulePara = HtmlTree.P(moduleIntro);
            section.add(modulePara)
                    .add(newHelpSectionList(
                            contents.packagesLabel,
                            contents.modulesLabel,
                            contents.servicesLabel));
            pageKindsSection.add(section);
        }

        // Package
        section = newHelpSection(contents.packageLabel, PageMode.PACKAGE)
                .add(HtmlTree.P(getContent("doclet.help.package.intro")))
                .add(newHelpSectionList(
                        contents.interfaces,
                        contents.classes,
                        contents.enums,
                        contents.exceptionClasses,
                        contents.annotationTypes));
        pageKindsSection.add(section);

        // Class/interface
        Content notes = new ContentBuilder(
                HtmlTree.SPAN(HtmlStyles.helpNote, getContent("doclet.help.class_interface.note")),
                Text.of(" "),
                getContent("doclet.help.class_interface.anno"),
                Text.of(" "),
                getContent("doclet.help.class_interface.enum"),
                Text.of(" "),
                getContent("doclet.help.class_interface.record"),
                Text.of(" "),
                getContent("doclet.help.class_interface.property"));

        section = newHelpSection(getContent("doclet.help.class_interface.head"), PageMode.CLASS)
                .add(HtmlTree.P(getContent("doclet.help.class_interface.intro")))
                .add(newHelpSectionList(
                        getContent("doclet.help.class_interface.inheritance_diagram"),
                        getContent("doclet.help.class_interface.subclasses"),
                        getContent("doclet.help.class_interface.subinterfaces"),
                        getContent("doclet.help.class_interface.implementations"),
                        getContent("doclet.help.class_interface.declaration"),
                        getContent("doclet.help.class_interface.description")))
                .add(new HtmlTree(HtmlTag.BR))
                .add(newHelpSectionList(
                        contents.nestedClassSummary,
                        contents.enumConstantSummary,
                        contents.fieldSummaryLabel,
                        contents.propertySummaryLabel,
                        contents.constructorSummaryLabel,
                        contents.methodSummary,
                        contents.annotateTypeRequiredMemberSummaryLabel,
                        contents.annotateTypeOptionalMemberSummaryLabel))
                .add(new HtmlTree(HtmlTag.BR))
                .add(newHelpSectionList(
                        contents.enumConstantDetailLabel,
                        contents.fieldDetailsLabel,
                        contents.propertyDetailsLabel,
                        contents.constructorDetailsLabel,
                        contents.methodDetailLabel,
                        contents.annotationTypeMemberDetail))
                .add(HtmlTree.P(notes))
                .add(HtmlTree.P(getContent("doclet.help.class_interface.member_order")));
        pageKindsSection.add(section);

        section = newHelpSection(getContent("doclet.help.other_files.head"), PageMode.DOC_FILE)
                .add(HtmlTree.P(getContent("doclet.help.other_files.body")));
        pageKindsSection.add(section);

        // Class Use
        if (options.classUse()) {
            section = newHelpSection(getContent("doclet.help.use.head"), PageMode.USE)
                    .add(HtmlTree.P(getContent("doclet.help.use.body")));
            pageKindsSection.add(section);
        }

        // Tree
        if (options.createTree()) {
            section = newHelpSection(getContent("doclet.help.tree.head"), PageMode.TREE);
            Content treeIntro = getContent("doclet.help.tree.intro",
                    links.createLink(DocPaths.OVERVIEW_TREE, resources.getText("doclet.Class_Hierarchy")),
                    HtmlTree.CODE(Text.of("java.lang.Object")));
            section.add(HtmlTree.P(treeIntro))
                    .add(newHelpSectionList(
                            getContent("doclet.help.tree.overview"),
                            getContent("doclet.help.tree.package")));
            pageKindsSection.add(section);
        }

        // Preview
        if (configuration.conditionalPages.contains(HtmlConfiguration.ConditionalPage.PREVIEW)) {
            section = newHelpSection(contents.previewAPI, PageMode.PREVIEW);
            Content previewBody = getContent("doclet.help.preview.body",
                    links.createLink(DocPaths.PREVIEW_LIST, contents.previewAPI));
            section.add(HtmlTree.P(previewBody));
            pageKindsSection.add(section);
        }

        // New
        if (configuration.conditionalPages.contains(HtmlConfiguration.ConditionalPage.NEW)) {
            section = newHelpSection(contents.newAPI, PageMode.NEW);
            Content newBody = getContent("doclet.help.new.body",
                    links.createLink(DocPaths.NEW_LIST, contents.newAPI));
            section.add(HtmlTree.P(newBody));
            pageKindsSection.add(section);
        }

        // Deprecated
        if (configuration.conditionalPages.contains(HtmlConfiguration.ConditionalPage.DEPRECATED)) {
            section = newHelpSection(contents.deprecatedAPI, PageMode.DEPRECATED);
            Content deprBody = getContent("doclet.help.deprecated.body",
                    links.createLink(DocPaths.DEPRECATED_LIST, resources.getText("doclet.Deprecated_API")));
            section.add(HtmlTree.P(deprBody));
            pageKindsSection.add(section);
        }

        // Restricted
        if (configuration.conditionalPages.contains(HtmlConfiguration.ConditionalPage.RESTRICTED)) {
            section = newHelpSection(contents.restrictedMethods, PageMode.RESTRICTED);
            Content restrictedBody = getContent("doclet.help.restricted.body",
                    links.createLink(DocPaths.RESTRICTED_LIST, resources.getText("doclet.Restricted_Methods")));
            section.add(HtmlTree.P(restrictedBody));
            pageKindsSection.add(section);
        }

        // Constant Field Values
        if (configuration.conditionalPages.contains(HtmlConfiguration.ConditionalPage.CONSTANT_VALUES)) {
            section = newHelpSection(contents.constantsSummaryTitle, PageMode.CONSTANT_VALUES);
            Content constantsBody = getContent("doclet.help.constants.body",
                    links.createLink(DocPaths.CONSTANT_VALUES, resources.getText("doclet.Constants_Summary")));
            section.add(HtmlTree.P(constantsBody));
            pageKindsSection.add(section);
        }

        // Serialized Form
        if (configuration.conditionalPages.contains(HtmlConfiguration.ConditionalPage.SERIALIZED_FORM)) {
            section = newHelpSection(contents.serializedForm, PageMode.SERIALIZED_FORM)
                    .add(HtmlTree.P(getContent("doclet.help.serial_form.body")));
            pageKindsSection.add(section);
        }

        // System Properties
        if (configuration.conditionalPages.contains(HtmlConfiguration.ConditionalPage.SYSTEM_PROPERTIES)) {
            section = newHelpSection(contents.systemPropertiesLabel, PageMode.SYSTEM_PROPERTIES);
            Content sysPropsBody = getContent("doclet.help.systemProperties.body",
                    links.createLink(DocPaths.SYSTEM_PROPERTIES, resources.getText("doclet.systemProperties")));
            section.add(HtmlTree.P(sysPropsBody));
            pageKindsSection.add(section);
        }

        // External Specification
        if (configuration.conditionalPages.contains(HtmlConfiguration.ConditionalPage.EXTERNAL_SPECS)) {
            section = newHelpSection(contents.externalSpecifications, PageMode.EXTERNAL_SPECS);
            Content extSpecsBody = getContent("doclet.help.externalSpecifications.body",
                    links.createLink(DocPaths.EXTERNAL_SPECS, resources.getText("doclet.External_Specifications")));
            section.add(HtmlTree.P(extSpecsBody));
            pageKindsSection.add(section);
        }

        // Index
        if (options.createIndex()) {
            if (!configuration.packages.isEmpty()) {
                section = newHelpSection(getContent("doclet.help.all_packages.head"), PageMode.ALL_PACKAGES)
                        .add(HtmlTree.P(getContent("doclet.help.all_packages.body", allPackagesLink)));
                pageKindsSection.add(section);
            }

            section = newHelpSection(getContent("doclet.help.all_classes.head"), PageMode.ALL_CLASSES)
                    .add(HtmlTree.P(getContent("doclet.help.all_classes.body", allClassesLink)));
            pageKindsSection.add(section);

            Content links = new ContentBuilder();
            if (!configuration.packages.isEmpty()) {
                links.add(allPackagesLink);
                links.add(", ");
            }
            links.add(allClassesLink);
            section = newHelpSection(getContent("doclet.help.index.head"), PageMode.INDEX)
                    .add(HtmlTree.P(getContent("doclet.help.index.body", indexLink, links)));
            pageKindsSection.add(section);
        }
        tableOfContents.popNestedList();

        return pageKindsSection;
    }

    private Content getContent(String key) {
        return contents.getContent(key);
    }

    private Content getContent(String key, Object arg) {
        return contents.getContent(key, arg);
    }

    private Content getContent(String key, Object arg1, Object arg2) {
        return contents.getContent(key, arg1, arg2);
    }

    private HtmlTree newHelpSection(Content headingContent, HtmlId id) {
        tableOfContents.addLink(id, headingContent);

        return HtmlTree.SECTION(HtmlStyles.helpSection,
                HtmlTree.HEADING(Headings.SUB_HEADING, headingContent))
                .setId(id);
    }

    private HtmlTree newHelpSection(Content headingContent, Navigation.PageMode pm) {
        return newHelpSection(headingContent, htmlIds.forPage(pm));
    }

    private HtmlTree newHelpSectionList(Content first, Content... rest) {
        var list = HtmlTree.UL(HtmlStyles.helpSectionList, HtmlTree.LI(first));
        List.of(rest).forEach(i -> list.add(HtmlTree.LI(i)));
        return list;
    }
}
