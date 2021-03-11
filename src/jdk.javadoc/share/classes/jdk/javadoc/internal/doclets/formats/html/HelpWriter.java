/*
 * Copyright (c) 1998, 2021, Oracle and/or its affiliates. All rights reserved.
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

import jdk.javadoc.internal.doclets.formats.html.markup.BodyContents;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.TagName;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.Text;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;


/**
 * Generate the Help File for the generated API documentation. The help file
 * contents are helpful for browsing the generated documentation.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class HelpWriter extends HtmlDocletWriter {

    private final String[][] SEARCH_EXAMPLES = {
            {"j.l.obj", "\"java.lang.Object\""},
            {"InpStr", "\"java.io.InputStream\""},
            {"HM.cK", "\"java.util.HashMap.containsKey(Object)\""}
    };

    /**
     * Constructor to construct HelpWriter object.
     * @param configuration the configuration
     * @param filename File to be generated.
     */
    public HelpWriter(HtmlConfiguration configuration,
                      DocPath filename) {
        super(configuration, filename);
    }

    /**
     * Construct the HelpWriter object and then use it to generate the help
     * file. The name of the generated file is "help-doc.html". The help file
     * will get generated if and only if "-helpfile" and "-nohelp" is not used
     * on the command line.
     *
     * @param configuration the configuration
     * @throws DocFileIOException if there is a problem while generating the documentation
     */
    public static void generate(HtmlConfiguration configuration) throws DocFileIOException {
        DocPath filename = DocPaths.HELP_DOC;
        HelpWriter helpgen = new HelpWriter(configuration, filename);
        helpgen.generateHelpFile();
    }

    /**
     * Generate the help file contents.
     *
     * @throws DocFileIOException if there is a problem while generating the documentation
     */
    protected void generateHelpFile() throws DocFileIOException {
        String title = resources.getText("doclet.Window_Help_title");
        HtmlTree body = getBody(getWindowTitle(title));
        ContentBuilder helpFileContent = new ContentBuilder();
        addHelpFileContents(helpFileContent);
        body.add(new BodyContents()
                .setHeader(getHeader(PageMode.HELP))
                .addMainContent(helpFileContent)
                .setFooter(getFooter()));
        printHtmlDocument(null, "help", body);
    }

    /**
     * Add the help file contents from the resource file to the content tree. While adding the
     * help file contents it also keeps track of user options. If "-notree"
     * is used, then the "overview-tree.html" will not get added and hence
     * help information also will not get added.
     *
     * @param contentTree the content tree to which the help file contents will be added
     */
    protected void addHelpFileContents(Content contentTree) {
        // Heading
        Content heading = HtmlTree.HEADING(Headings.PAGE_TITLE_HEADING, HtmlStyle.title,
                getContent("doclet.help.main_heading"));
        Content div = HtmlTree.DIV(HtmlStyle.header, heading);
        Content intro = HtmlTree.DIV(HtmlStyle.subTitle,
                getContent("doclet.help.intro"));
        div.add(intro);
        contentTree.add(div);

        HtmlTree section;

        // Overview
        if (options.createOverview()) {
            section = newHelpSection(contents.overviewLabel);
            String overviewKey = configuration.showModules
                    ? "doclet.help.overview.modules.body"
                    : "doclet.help.overview.packages.body";
            Content overviewLink = links.createLink(
                    DocPaths.INDEX, resources.getText("doclet.Overview"));
            section.add(HtmlTree.P(getContent(overviewKey, overviewLink)));
            contentTree.add(section);
        }

        // Module
        if (configuration.showModules) {
            section = newHelpSection(contents.moduleLabel);
            Content moduleIntro = getContent("doclet.help.module.intro");
            Content modulePara = HtmlTree.P(moduleIntro);
            section.add(modulePara)
                    .add(newHelpSectionList(
                            contents.packagesLabel,
                            contents.modulesLabel,
                            contents.servicesLabel));
            contentTree.add(section);
        }

        // Package
        section = newHelpSection(contents.packageLabel)
                .add(HtmlTree.P(getContent("doclet.help.package.intro")))
                .add(newHelpSectionList(
                        contents.interfaces,
                        contents.classes,
                        contents.enums,
                        contents.exceptions,
                        contents.errors,
                        contents.annotationTypes));
        contentTree.add(section);

        // Class/interface
        section = newHelpSection(getContent("doclet.help.class_interface.head"))
                .add(HtmlTree.P(getContent("doclet.help.class_interface.intro")))
                .add(newHelpSectionList(
                        getContent("doclet.help.class_interface.inheritance_diagram"),
                        getContent("doclet.help.class_interface.subclasses"),
                        getContent("doclet.help.class_interface.subinterfaces"),
                        getContent("doclet.help.class_interface.implementations"),
                        getContent("doclet.help.class_interface.declaration"),
                        getContent("doclet.help.class_interface.description")))
                .add(new HtmlTree(TagName.BR))
                .add(newHelpSectionList(
                        contents.nestedClassSummary,
                        contents.fieldSummaryLabel,
                        contents.propertySummaryLabel,
                        contents.constructorSummaryLabel,
                        contents.methodSummary))
                .add(new HtmlTree(TagName.BR))
                .add(newHelpSectionList(
                        contents.fieldDetailsLabel,
                        contents.propertyDetailsLabel,
                        contents.constructorDetailsLabel,
                        contents.methodDetailLabel))
                .add(HtmlTree.P(getContent("doclet.help.class_interface.summary")));
        contentTree.add(section);

        // Annotation Types
        section = newHelpSection(contents.annotationType)
                .add(HtmlTree.P(getContent("doclet.help.annotation_type.intro")))
                .add(newHelpSectionList(
                        getContent("doclet.help.annotation_type.declaration"),
                        getContent("doclet.help.annotation_type.description"),
                        contents.annotateTypeRequiredMemberSummaryLabel,
                        contents.annotateTypeOptionalMemberSummaryLabel,
                        contents.annotationTypeMemberDetail));
        contentTree.add(section);

        // Enums
        section = newHelpSection(contents.enum_)
                .add(HtmlTree.P(getContent("doclet.help.enum.intro")))
                .add(newHelpSectionList(
                        getContent("doclet.help.enum.declaration"),
                        getContent("doclet.help.enum.definition"),
                        contents.enumConstantSummary,
                        contents.enumConstantDetailLabel));
        contentTree.add(section);

        // Class Use
        if (options.classUse()) {
            section = newHelpSection(getContent("doclet.help.use.head"))
                    .add(HtmlTree.P(getContent("doclet.help.use.body")));
            contentTree.add(section);
        }

        // Tree
        if (options.createTree()) {
            section = newHelpSection(getContent("doclet.help.tree.head"));
            Content treeIntro = getContent("doclet.help.tree.intro",
                    links.createLink(DocPaths.OVERVIEW_TREE, resources.getText("doclet.Class_Hierarchy")),
                    HtmlTree.CODE(Text.of("java.lang.Object")));
            section.add(HtmlTree.P(treeIntro))
                    .add(newHelpSectionList(
                            getContent("doclet.help.tree.overview"),
                            getContent("doclet.help.tree.package")));
            contentTree.add(section);
        }

        // Deprecated
        if (configuration.conditionalPages.contains(HtmlConfiguration.ConditionalPage.DEPRECATED)) {
            section = newHelpSection(contents.deprecatedAPI);
            Content deprBody = getContent("doclet.help.deprecated.body",
                    links.createLink(DocPaths.DEPRECATED_LIST, resources.getText("doclet.Deprecated_API")));
            section.add(HtmlTree.P(deprBody));
            contentTree.add(section);
        }

        // Preview
        if (configuration.conditionalPages.contains(HtmlConfiguration.ConditionalPage.PREVIEW)) {
            section = newHelpSection(contents.previewAPI);
            Content previewBody = getContent("doclet.help.preview.body",
                    links.createLink(DocPaths.PREVIEW_LIST, contents.previewAPI));
            section.add(HtmlTree.P(previewBody));
            contentTree.add(section);
        }

        // Index
        if (options.createIndex()) {
            DocPath dp = options.splitIndex()
                    ? DocPaths.INDEX_FILES.resolve(DocPaths.indexN(1))
                    : DocPaths.INDEX_ALL;
            Content indexLink = links.createLink(dp, resources.getText("doclet.Index"));
            section = newHelpSection(getContent("doclet.help.index.head"))
                    .add(HtmlTree.P(getContent("doclet.help.index.body", indexLink)));
            contentTree.add(section);
        }

        // Serialized Form
        if (configuration.conditionalPages.contains(HtmlConfiguration.ConditionalPage.SERIALIZED_FORM)) {
            section = newHelpSection(contents.serializedForm)
                    .add(HtmlTree.P(getContent("doclet.help.serial_form.body")));
            contentTree.add(section);
        }

        // Constant Field Values
        if (configuration.conditionalPages.contains(HtmlConfiguration.ConditionalPage.CONSTANT_VALUES)) {
            section = newHelpSection(contents.constantsSummaryTitle);
            Content constantsBody = getContent("doclet.help.constants.body",
                    links.createLink(DocPaths.CONSTANT_VALUES, resources.getText("doclet.Constants_Summary")));
            section.add(HtmlTree.P(constantsBody));
            contentTree.add(section);
        }

        // System Properties
        if (configuration.conditionalPages.contains(HtmlConfiguration.ConditionalPage.SYSTEM_PROPERTIES)) {
            section = newHelpSection(contents.systemPropertiesLabel);
            Content sysPropsBody = getContent("doclet.help.systemProperties.body",
                    links.createLink(DocPaths.SYSTEM_PROPERTIES, resources.getText("doclet.systemProperties")));
            section.add(HtmlTree.P(sysPropsBody));
            contentTree.add(section);
        }

        // Search
        if (options.createIndex()) {
            section = newHelpSection(getContent("doclet.help.search.head"));
            Content searchIntro = HtmlTree.P(getContent("doclet.help.search.intro"));
            Content searchExamples = new HtmlTree(TagName.UL).setStyle(HtmlStyle.helpSectionList);
            for (String[] example : SEARCH_EXAMPLES) {
                searchExamples.add(HtmlTree.LI(
                        getContent("doclet.help.search.example",
                                HtmlTree.CODE(Text.of(example[0])), example[1])));
            }
            Content searchSpecLink = HtmlTree.A(
                    resources.getText("doclet.help.search.spec.url", configuration.getDocletVersion().feature()),
                    getContent("doclet.help.search.spec.title"));
            Content searchRefer = HtmlTree.P(getContent("doclet.help.search.refer", searchSpecLink));
            section.add(searchIntro)
                    .add(searchExamples)
                    .add(searchRefer);
            contentTree.add(section);
        }

        contentTree.add(new HtmlTree(TagName.HR))
                .add(HtmlTree.SPAN(HtmlStyle.helpFootnote,
                            getContent("doclet.help.footnote")));
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

    private HtmlTree newHelpSection(Content headingContent) {
        return HtmlTree.SECTION(HtmlStyle.helpSection,
                HtmlTree.HEADING(Headings.CONTENT_HEADING, headingContent));
    }

    private HtmlTree newHelpSectionList(Content first, Content... rest) {
        HtmlTree list = HtmlTree.UL(HtmlStyle.helpSectionList, HtmlTree.LI(first));
        List.of(rest).forEach(i -> list.add(HtmlTree.LI(i)));
        return list;
    }
}
