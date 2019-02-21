/*
 * Copyright (c) 1998, 2019, Oracle and/or its affiliates. All rights reserved.
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

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlConstants;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Navigation;
import jdk.javadoc.internal.doclets.formats.html.markup.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
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
 *
 * @author Atul M Dambalkar
 */
public class HelpWriter extends HtmlDocletWriter {

    HtmlTree mainTree = HtmlTree.MAIN();

    private final Navigation navBar;

    /**
     * Constructor to construct HelpWriter object.
     * @param configuration the configuration
     * @param filename File to be generated.
     */
    public HelpWriter(HtmlConfiguration configuration,
                      DocPath filename) {
        super(configuration, filename);
        this.navBar = new Navigation(null, configuration, fixedNavDiv, PageMode.HELP, path);
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
        HtmlTree body = getBody(true, getWindowTitle(title));
        HtmlTree htmlTree = HtmlTree.HEADER();
        addTop(htmlTree);
        navBar.setUserHeader(getUserHeaderFooter(true));
        htmlTree.addContent(navBar.getContent(true));
        body.addContent(htmlTree);
        addHelpFileContents(body);
        htmlTree = HtmlTree.FOOTER();
        navBar.setUserFooter(getUserHeaderFooter(false));
        htmlTree.addContent(navBar.getContent(false));
        addBottom(htmlTree);
        body.addContent(htmlTree);
        printHtmlDocument(null, "help", true, body);
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
        Content heading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, false, HtmlStyle.title,
                contents.getContent("doclet.help.main_heading"));
        Content div = HtmlTree.DIV(HtmlStyle.header, heading);
        Content intro = HtmlTree.DIV(HtmlStyle.subTitle,
                contents.getContent("doclet.help.intro"));
        div.addContent(intro);
        mainTree.addContent(div);
        HtmlTree htmlTree;
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.setStyle(HtmlStyle.blockList);

        // Overview
        if (configuration.createoverview) {
            Content overviewHeading = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                contents.overviewLabel);
            htmlTree = HtmlTree.SECTION(overviewHeading);
            String overviewKey = configuration.showModules
                    ? "doclet.help.overview.modules.body"
                    : "doclet.help.overview.packages.body";
            Content overviewLink = links.createLink(
                    DocPaths.overviewSummary(configuration.frames),
                    resources.getText("doclet.Overview"));
            Content overviewBody = contents.getContent(overviewKey, overviewLink);
            Content overviewPara = HtmlTree.P(overviewBody);
            htmlTree.addContent(overviewPara);
            ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));
        }

        // Module
        if (configuration.showModules) {
            Content moduleHead = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                    contents.moduleLabel);
            htmlTree = HtmlTree.SECTION(moduleHead);
            Content moduleIntro = contents.getContent("doclet.help.module.intro");
            Content modulePara = HtmlTree.P(moduleIntro);
            htmlTree.addContent(modulePara);
            HtmlTree ulModule = new HtmlTree(HtmlTag.UL);
            ulModule.addContent(HtmlTree.LI(contents.packagesLabel));
            ulModule.addContent(HtmlTree.LI(contents.modulesLabel));
            ulModule.addContent(HtmlTree.LI(contents.servicesLabel));
            htmlTree.addContent(ulModule);
            ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));
        }

        // Package
        Content packageHead = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                contents.packageLabel);
        htmlTree = HtmlTree.SECTION(packageHead);
        Content packageIntro = contents.getContent("doclet.help.package.intro");
        Content packagePara = HtmlTree.P(packageIntro);
        htmlTree.addContent(packagePara);
        HtmlTree ulPackage = new HtmlTree(HtmlTag.UL);
        ulPackage.addContent(HtmlTree.LI(contents.interfaces));
        ulPackage.addContent(HtmlTree.LI(contents.classes));
        ulPackage.addContent(HtmlTree.LI(contents.enums));
        ulPackage.addContent(HtmlTree.LI(contents.exceptions));
        ulPackage.addContent(HtmlTree.LI(contents.errors));
        ulPackage.addContent(HtmlTree.LI(contents.annotationTypes));
        htmlTree.addContent(ulPackage);
        ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));

        // Class/interface
        Content classHead = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                contents.getContent("doclet.help.class_interface.head"));
        htmlTree = HtmlTree.SECTION(classHead);
        Content classIntro = contents.getContent("doclet.help.class_interface.intro");
        Content classPara = HtmlTree.P(classIntro);
        htmlTree.addContent(classPara);
        HtmlTree ul1 = new HtmlTree(HtmlTag.UL);
        ul1.addContent(HtmlTree.LI(contents.getContent("doclet.help.class_interface.inheritance_diagram")));
        ul1.addContent(HtmlTree.LI(contents.getContent("doclet.help.class_interface.subclasses")));
        ul1.addContent(HtmlTree.LI(contents.getContent("doclet.help.class_interface.subinterfaces")));
        ul1.addContent(HtmlTree.LI(contents.getContent("doclet.help.class_interface.implementations")));
        ul1.addContent(HtmlTree.LI(contents.getContent("doclet.help.class_interface.declaration")));
        ul1.addContent(HtmlTree.LI(contents.getContent("doclet.help.class_interface.description")));
        htmlTree.addContent(ul1);
        htmlTree.addContent(new HtmlTree(HtmlTag.BR));
        HtmlTree ul2 = new HtmlTree(HtmlTag.UL);
        ul2.addContent(HtmlTree.LI(contents.nestedClassSummary));
        ul2.addContent(HtmlTree.LI(contents.fieldSummaryLabel));
        ul2.addContent(HtmlTree.LI(contents.propertySummaryLabel));
        ul2.addContent(HtmlTree.LI(contents.constructorSummaryLabel));
        ul2.addContent(HtmlTree.LI(contents.methodSummary));
        htmlTree.addContent(ul2);
        htmlTree.addContent(new HtmlTree(HtmlTag.BR));
        HtmlTree ul3 = new HtmlTree(HtmlTag.UL);
        ul3.addContent(HtmlTree.LI(contents.fieldDetailsLabel));
        ul3.addContent(HtmlTree.LI(contents.propertyDetailsLabel));
        ul3.addContent(HtmlTree.LI(contents.constructorDetailsLabel));
        ul3.addContent(HtmlTree.LI(contents.methodDetailLabel));
        htmlTree.addContent(ul3);
        Content classSummary = contents.getContent("doclet.help.class_interface.summary");
        Content para = HtmlTree.P(classSummary);
        htmlTree.addContent(para);
        ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));

        // Annotation Types
        Content aHead = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                contents.annotationType);
        htmlTree = HtmlTree.SECTION(aHead);
        Content aIntro = contents.getContent("doclet.help.annotation_type.intro");
        Content aPara = HtmlTree.P(aIntro);
        htmlTree.addContent(aPara);
        HtmlTree aul = new HtmlTree(HtmlTag.UL);
        aul.addContent(HtmlTree.LI(contents.getContent("doclet.help.annotation_type.declaration")));
        aul.addContent(HtmlTree.LI(contents.getContent("doclet.help.annotation_type.description")));
        aul.addContent(HtmlTree.LI(contents.annotateTypeRequiredMemberSummaryLabel));
        aul.addContent(HtmlTree.LI(contents.annotateTypeOptionalMemberSummaryLabel));
        aul.addContent(HtmlTree.LI(contents.annotationTypeMemberDetail));
        htmlTree.addContent(aul);
        ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));

        // Enums
        Content enumHead = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING, contents.enum_);
        htmlTree = HtmlTree.SECTION(enumHead);
        Content eIntro = contents.getContent("doclet.help.enum.intro");
        Content enumPara = HtmlTree.P(eIntro);
        htmlTree.addContent(enumPara);
        HtmlTree eul = new HtmlTree(HtmlTag.UL);
        eul.addContent(HtmlTree.LI(contents.getContent("doclet.help.enum.declaration")));
        eul.addContent(HtmlTree.LI(contents.getContent("doclet.help.enum.definition")));
        eul.addContent(HtmlTree.LI(contents.enumConstantSummary));
        eul.addContent(HtmlTree.LI(contents.enumConstantDetailLabel));
        htmlTree.addContent(eul);
        ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));

        // Class Use
        if (configuration.classuse) {
            Content useHead = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                    contents.getContent("doclet.help.use.head"));
            htmlTree = HtmlTree.SECTION(useHead);
            Content useBody = contents.getContent("doclet.help.use.body");
            Content usePara = HtmlTree.P(useBody);
            htmlTree.addContent(usePara);
            ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));
        }

        // Tree
        if (configuration.createtree) {
            Content treeHead = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                    contents.getContent("doclet.help.tree.head"));
            htmlTree = HtmlTree.SECTION(treeHead);
            Content treeIntro = contents.getContent("doclet.help.tree.intro",
                    links.createLink(DocPaths.OVERVIEW_TREE,
                    resources.getText("doclet.Class_Hierarchy")),
                    HtmlTree.CODE(new StringContent("java.lang.Object")));
            Content treePara = HtmlTree.P(treeIntro);
            htmlTree.addContent(treePara);
            HtmlTree tul = new HtmlTree(HtmlTag.UL);
            tul.addContent(HtmlTree.LI(contents.getContent("doclet.help.tree.overview")));
            tul.addContent(HtmlTree.LI(contents.getContent("doclet.help.tree.package")));
            htmlTree.addContent(tul);
            ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));
        }

        // Deprecated
        if (!(configuration.nodeprecatedlist || configuration.nodeprecated)) {
            Content dHead = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                    contents.deprecatedAPI);
            htmlTree = HtmlTree.SECTION(dHead);
            Content deprBody = contents.getContent("doclet.help.deprecated.body",
                    links.createLink(DocPaths.DEPRECATED_LIST,
                    resources.getText("doclet.Deprecated_API")));
            Content dPara = HtmlTree.P(deprBody);
            htmlTree.addContent(dPara);
            ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));
        }

        // Index
        if (configuration.createindex) {
            Content indexlink;
            if (configuration.splitindex) {
                indexlink = links.createLink(DocPaths.INDEX_FILES.resolve(DocPaths.indexN(1)),
                        resources.getText("doclet.Index"));
            } else {
                indexlink = links.createLink(DocPaths.INDEX_ALL,
                        resources.getText("doclet.Index"));
            }
            Content indexHead = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                    contents.getContent("doclet.help.index.head"));
            htmlTree = HtmlTree.SECTION(indexHead);
            Content indexBody = contents.getContent("doclet.help.index.body", indexlink);
            Content indexPara = HtmlTree.P(indexBody);
            htmlTree.addContent(indexPara);
            ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));
        }

        // Frames
        if (configuration.frames) {
            Content frameHead = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                    contents.getContent("doclet.help.frames.head"));
            htmlTree = HtmlTree.SECTION(frameHead);
            Content framesBody = contents.getContent("doclet.help.frames.body");
            Content framePara = HtmlTree.P(framesBody);
            htmlTree.addContent(framePara);

            ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));
        }

        // Serialized Form
        Content sHead = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                contents.serializedForm);
        htmlTree = HtmlTree.SECTION(sHead);
        Content serialBody = contents.getContent("doclet.help.serial_form.body");
        Content serialPara = HtmlTree.P(serialBody);
        htmlTree.addContent(serialPara);
        ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));

        // Constant Field Values
        Content constHead = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                contents.constantsSummaryTitle);
        htmlTree = HtmlTree.SECTION(constHead);
        Content constantsBody = contents.getContent("doclet.help.constants.body",
                links.createLink(DocPaths.CONSTANT_VALUES,
                resources.getText("doclet.Constants_Summary")));
        Content constPara = HtmlTree.P(constantsBody);
        htmlTree.addContent(constPara);
        ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));

        // Search
        Content searchHead = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                contents.getContent("doclet.help.search.head"));
        htmlTree = HtmlTree.SECTION(searchHead);
        Content searchBody = contents.getContent("doclet.help.search.body");
        Content searchPara = HtmlTree.P(searchBody);
        htmlTree.addContent(searchPara);
        ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));

        Content divContent = HtmlTree.DIV(HtmlStyle.contentContainer, ul);
        divContent.addContent(new HtmlTree(HtmlTag.HR));
        Content footnote = HtmlTree.SPAN(HtmlStyle.emphasizedPhrase,
                contents.getContent("doclet.help.footnote"));
        divContent.addContent(footnote);
        mainTree.addContent(divContent);
        contentTree.addContent(mainTree);
    }
}
