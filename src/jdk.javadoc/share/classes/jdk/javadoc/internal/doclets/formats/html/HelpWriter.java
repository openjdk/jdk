/*
 * Copyright (c) 1998, 2017, Oracle and/or its affiliates. All rights reserved.
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
import jdk.javadoc.internal.doclets.formats.html.markup.Links;
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

    /**
     * Constructor to construct HelpWriter object.
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
        String title = configuration.getText("doclet.Window_Help_title");
        HtmlTree body = getBody(true, getWindowTitle(title));
        HtmlTree htmlTree = (configuration.allowTag(HtmlTag.HEADER))
                ? HtmlTree.HEADER()
                : body;
        addTop(htmlTree);
        addNavLinks(true, htmlTree);
        if (configuration.allowTag(HtmlTag.HEADER)) {
            body.addContent(htmlTree);
        }
        addHelpFileContents(body);
        if (configuration.allowTag(HtmlTag.FOOTER)) {
            htmlTree = HtmlTree.FOOTER();
        }
        addNavLinks(false, htmlTree);
        addBottom(htmlTree);
        if (configuration.allowTag(HtmlTag.FOOTER)) {
            body.addContent(htmlTree);
        }
        printHtmlDocument(null, true, body);
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
        Content heading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, false, HtmlStyle.title,
                contents.getContent("doclet.Help_line_1"));
        Content div = HtmlTree.DIV(HtmlStyle.header, heading);
        Content line2 = HtmlTree.DIV(HtmlStyle.subTitle,
                contents.getContent("doclet.Help_line_2"));
        div.addContent(line2);
        if (configuration.allowTag(HtmlTag.MAIN)) {
            mainTree.addContent(div);
        } else {
            contentTree.addContent(div);
        }
        HtmlTree htmlTree;
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.setStyle(HtmlStyle.blockList);
        if (configuration.createoverview) {
            Content overviewHeading = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                contents.overviewLabel);
            htmlTree = (configuration.allowTag(HtmlTag.SECTION))
                    ? HtmlTree.SECTION(overviewHeading)
                    : HtmlTree.LI(HtmlStyle.blockList, overviewHeading);
            Content line3 = contents.getContent("doclet.Help_line_3",
                    Links.createLink(DocPaths.overviewSummary(configuration.frames),
                    configuration.getText("doclet.Overview")));
            Content overviewPara = HtmlTree.P(line3);
            htmlTree.addContent(overviewPara);
            if (configuration.allowTag(HtmlTag.SECTION)) {
                ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));
            } else {
                ul.addContent(htmlTree);
            }
        }
        Content packageHead = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                contents.packageLabel);
        htmlTree = (configuration.allowTag(HtmlTag.SECTION))
                ? HtmlTree.SECTION(packageHead)
                : HtmlTree.LI(HtmlStyle.blockList, packageHead);
        Content line4 = contents.getContent("doclet.Help_line_4");
        Content packagePara = HtmlTree.P(line4);
        htmlTree.addContent(packagePara);
        HtmlTree ulPackage = new HtmlTree(HtmlTag.UL);
        ulPackage.addContent(HtmlTree.LI(
                contents.interfacesItalic));
        ulPackage.addContent(HtmlTree.LI(
                contents.classes));
        ulPackage.addContent(HtmlTree.LI(
                contents.enums));
        ulPackage.addContent(HtmlTree.LI(
                contents.exceptions));
        ulPackage.addContent(HtmlTree.LI(
                contents.errors));
        ulPackage.addContent(HtmlTree.LI(
                contents.annotationTypes));
        htmlTree.addContent(ulPackage);
        if (configuration.allowTag(HtmlTag.SECTION)) {
            ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));
        } else {
            ul.addContent(htmlTree);
        }
        Content classHead = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                contents.getContent("doclet.Help_line_5"));
        htmlTree = (configuration.allowTag(HtmlTag.SECTION))
                ? HtmlTree.SECTION(classHead)
                : HtmlTree.LI(HtmlStyle.blockList, classHead);
        Content line6 = contents.getContent("doclet.Help_line_6");
        Content classPara = HtmlTree.P(line6);
        htmlTree.addContent(classPara);
        HtmlTree ul1 = new HtmlTree(HtmlTag.UL);
        ul1.addContent(HtmlTree.LI(
                contents.getContent("doclet.Help_line_7")));
        ul1.addContent(HtmlTree.LI(
                contents.getContent("doclet.Help_line_8")));
        ul1.addContent(HtmlTree.LI(
                contents.getContent("doclet.Help_line_9")));
        ul1.addContent(HtmlTree.LI(
                contents.getContent("doclet.Help_line_10")));
        ul1.addContent(HtmlTree.LI(
                contents.getContent("doclet.Help_line_11")));
        ul1.addContent(HtmlTree.LI(
                contents.getContent("doclet.Help_line_12")));
        htmlTree.addContent(ul1);
        HtmlTree ul2 = new HtmlTree(HtmlTag.UL);
        ul2.addContent(HtmlTree.LI(contents.nestedClassSummary));
        ul2.addContent(HtmlTree.LI(contents.fieldSummaryLabel));
        ul2.addContent(HtmlTree.LI(contents.constructorSummaryLabel));
        ul2.addContent(HtmlTree.LI(contents.methodSummary));
        htmlTree.addContent(ul2);
        HtmlTree ul3 = new HtmlTree(HtmlTag.UL);
        ul3.addContent(HtmlTree.LI(contents.fieldDetailsLabel));
        ul3.addContent(HtmlTree.LI(contents.constructorDetailsLabel));
        ul3.addContent(HtmlTree.LI(contents.methodDetailLabel));
        htmlTree.addContent(ul3);
        Content line13 = contents.getContent("doclet.Help_line_13");
        Content para = HtmlTree.P(line13);
        htmlTree.addContent(para);
        if (configuration.allowTag(HtmlTag.SECTION)) {
            ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));
        } else {
            ul.addContent(htmlTree);
        }
        //Annotation Types
        Content aHead = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                contents.annotationType);
        htmlTree = (configuration.allowTag(HtmlTag.SECTION))
                ? HtmlTree.SECTION(aHead)
                : HtmlTree.LI(HtmlStyle.blockList, aHead);
        Content aline1 = contents.getContent("doclet.Help_annotation_type_line_1");
        Content aPara = HtmlTree.P(aline1);
        htmlTree.addContent(aPara);
        HtmlTree aul = new HtmlTree(HtmlTag.UL);
        aul.addContent(HtmlTree.LI(
                contents.getContent("doclet.Help_annotation_type_line_2")));
        aul.addContent(HtmlTree.LI(
                contents.getContent("doclet.Help_annotation_type_line_3")));
        aul.addContent(HtmlTree.LI(
                contents.annotateTypeRequiredMemberSummaryLabel));
        aul.addContent(HtmlTree.LI(
                contents.annotateTypeOptionalMemberSummaryLabel));
        aul.addContent(HtmlTree.LI(
                contents.annotationTypeMemberDetail));
        htmlTree.addContent(aul);
        if (configuration.allowTag(HtmlTag.SECTION)) {
            ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));
        } else {
            ul.addContent(htmlTree);
        }
        //Enums
        Content enumHead = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                contents.enum_);
        htmlTree = (configuration.allowTag(HtmlTag.SECTION))
                ? HtmlTree.SECTION(enumHead)
                : HtmlTree.LI(HtmlStyle.blockList, enumHead);
        Content eline1 = contents.getContent("doclet.Help_enum_line_1");
        Content enumPara = HtmlTree.P(eline1);
        htmlTree.addContent(enumPara);
        HtmlTree eul = new HtmlTree(HtmlTag.UL);
        eul.addContent(HtmlTree.LI(
                contents.getContent("doclet.Help_enum_line_2")));
        eul.addContent(HtmlTree.LI(
                contents.getContent("doclet.Help_enum_line_3")));
        eul.addContent(HtmlTree.LI(
                contents.enumConstantSummary));
        eul.addContent(HtmlTree.LI(
                contents.enumConstantDetailLabel));
        htmlTree.addContent(eul);
        if (configuration.allowTag(HtmlTag.SECTION)) {
            ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));
        } else {
            ul.addContent(htmlTree);
        }
        if (configuration.classuse) {
            Content useHead = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                    contents.getContent("doclet.Help_line_14"));
            htmlTree = (configuration.allowTag(HtmlTag.SECTION))
                    ? HtmlTree.SECTION(useHead)
                    : HtmlTree.LI(HtmlStyle.blockList, useHead);
            Content line15 = contents.getContent("doclet.Help_line_15");
            Content usePara = HtmlTree.P(line15);
            htmlTree.addContent(usePara);
            if (configuration.allowTag(HtmlTag.SECTION)) {
                ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));
            } else {
                ul.addContent(htmlTree);
            }
        }
        if (configuration.createtree) {
            Content treeHead = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                    contents.getContent("doclet.Help_line_16"));
            htmlTree = (configuration.allowTag(HtmlTag.SECTION))
                    ? HtmlTree.SECTION(treeHead)
                    : HtmlTree.LI(HtmlStyle.blockList, treeHead);
            Content line17 = contents.getContent("doclet.Help_line_17_with_tree_link",
                    Links.createLink(DocPaths.OVERVIEW_TREE,
                    configuration.getText("doclet.Class_Hierarchy")),
                    HtmlTree.CODE(new StringContent("java.lang.Object")));
            Content treePara = HtmlTree.P(line17);
            htmlTree.addContent(treePara);
            HtmlTree tul = new HtmlTree(HtmlTag.UL);
            tul.addContent(HtmlTree.LI(
                    contents.getContent("doclet.Help_line_18")));
            tul.addContent(HtmlTree.LI(
                    contents.getContent("doclet.Help_line_19")));
            htmlTree.addContent(tul);
            if (configuration.allowTag(HtmlTag.SECTION)) {
                ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));
            } else {
                ul.addContent(htmlTree);
            }
        }
        if (!(configuration.nodeprecatedlist ||
                  configuration.nodeprecated)) {
            Content dHead = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                    contents.deprecatedAPI);
            htmlTree = (configuration.allowTag(HtmlTag.SECTION))
                    ? HtmlTree.SECTION(dHead)
                    : HtmlTree.LI(HtmlStyle.blockList, dHead);
            Content line20 = contents.getContent("doclet.Help_line_20_with_deprecated_api_link",
                    Links.createLink(DocPaths.DEPRECATED_LIST,
                    configuration.getText("doclet.Deprecated_API")));
            Content dPara = HtmlTree.P(line20);
            htmlTree.addContent(dPara);
            if (configuration.allowTag(HtmlTag.SECTION)) {
                ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));
            } else {
                ul.addContent(htmlTree);
            }
        }
        if (configuration.createindex) {
            Content indexlink;
            if (configuration.splitindex) {
                indexlink = Links.createLink(DocPaths.INDEX_FILES.resolve(DocPaths.indexN(1)),
                        configuration.getText("doclet.Index"));
            } else {
                indexlink = Links.createLink(DocPaths.INDEX_ALL,
                        configuration.getText("doclet.Index"));
            }
            Content indexHead = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                    contents.getContent("doclet.Help_line_21"));
            htmlTree = (configuration.allowTag(HtmlTag.SECTION))
                    ? HtmlTree.SECTION(indexHead)
                    : HtmlTree.LI(HtmlStyle.blockList, indexHead);
            Content line22 = contents.getContent("doclet.Help_line_22", indexlink);
            Content indexPara = HtmlTree.P(line22);
            htmlTree.addContent(indexPara);
            if (configuration.allowTag(HtmlTag.SECTION)) {
                ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));
            } else {
                ul.addContent(htmlTree);
            }
        }
        Content prevHead = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                contents.getContent("doclet.Help_line_23"));
        htmlTree = (configuration.allowTag(HtmlTag.SECTION))
                ? HtmlTree.SECTION(prevHead)
                : HtmlTree.LI(HtmlStyle.blockList, prevHead);
        Content line24 = contents.getContent("doclet.Help_line_24");
        Content prevPara = HtmlTree.P(line24);
        htmlTree.addContent(prevPara);
        if (configuration.allowTag(HtmlTag.SECTION)) {
            ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));
        } else {
            ul.addContent(htmlTree);
        }

        if (configuration.frames) {
            Content frameHead = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                    contents.getContent("doclet.Help_line_25"));
            htmlTree = (configuration.allowTag(HtmlTag.SECTION))
                    ? HtmlTree.SECTION(frameHead)
                    : HtmlTree.LI(HtmlStyle.blockList, frameHead);
            Content line26 = contents.getContent("doclet.Help_line_26");
            Content framePara = HtmlTree.P(line26);
            htmlTree.addContent(framePara);
        }

        if (configuration.allowTag(HtmlTag.SECTION)) {
            ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));
        } else {
            ul.addContent(htmlTree);
        }

        Content allclassesHead = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                contents.allClassesLabel);
        htmlTree = (configuration.allowTag(HtmlTag.SECTION))
                ? HtmlTree.SECTION(allclassesHead)
                : HtmlTree.LI(HtmlStyle.blockList, allclassesHead);
        Content line27 = contents.getContent("doclet.Help_line_27",
                Links.createLink(DocPaths.AllClasses(configuration.frames),
                resources.getText("doclet.All_Classes")));
        Content allclassesPara = HtmlTree.P(line27);
        htmlTree.addContent(allclassesPara);
        if (configuration.allowTag(HtmlTag.SECTION)) {
            ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));
        } else {
            ul.addContent(htmlTree);
        }
        Content sHead = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                contents.serializedForm);
        htmlTree = (configuration.allowTag(HtmlTag.SECTION))
                ? HtmlTree.SECTION(sHead)
                : HtmlTree.LI(HtmlStyle.blockList, sHead);
        Content line28 = contents.getContent("doclet.Help_line_28");
        Content serialPara = HtmlTree.P(line28);
        htmlTree.addContent(serialPara);
        if (configuration.allowTag(HtmlTag.SECTION)) {
            ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));
        } else {
            ul.addContent(htmlTree);
        }
        Content constHead = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                contents.constantsSummaryTitle);
        htmlTree = (configuration.allowTag(HtmlTag.SECTION))
                ? HtmlTree.SECTION(constHead)
                : HtmlTree.LI(HtmlStyle.blockList, constHead);
        Content line29 = contents.getContent("doclet.Help_line_29",
                Links.createLink(DocPaths.CONSTANT_VALUES,
                resources.getText("doclet.Constants_Summary")));
        Content constPara = HtmlTree.P(line29);
        htmlTree.addContent(constPara);
        if (configuration.allowTag(HtmlTag.SECTION)) {
            ul.addContent(HtmlTree.LI(HtmlStyle.blockList, htmlTree));
        } else {
            ul.addContent(htmlTree);
        }
        Content divContent = HtmlTree.DIV(HtmlStyle.contentContainer, ul);
        Content line30 = HtmlTree.SPAN(HtmlStyle.emphasizedPhrase, contents.getContent("doclet.Help_line_30"));
        divContent.addContent(line30);
        if (configuration.allowTag(HtmlTag.MAIN)) {
            mainTree.addContent(divContent);
            contentTree.addContent(mainTree);
        } else {
            contentTree.addContent(divContent);
        }
    }

    /**
     * Get the help label.
     *
     * @return a content tree for the help label
     */
    @Override
    protected Content getNavLinkHelp() {
        Content li = HtmlTree.LI(HtmlStyle.navBarCell1Rev, contents.helpLabel);
        return li;
    }
}
