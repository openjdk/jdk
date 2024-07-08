/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.BodyContents;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyles;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.ContentBuilder;
import jdk.javadoc.internal.html.HtmlAttr;
import jdk.javadoc.internal.html.HtmlId;
import jdk.javadoc.internal.html.HtmlTag;
import jdk.javadoc.internal.html.HtmlTree;
import jdk.javadoc.internal.html.Text;

/**
 * Generates the search landing page for the generated API documentation.
 */
public class SearchWriter extends HtmlDocletWriter {

    /**
     * Constructor to construct SearchWriter object.
     * @param configuration the configuration
     */
    public SearchWriter(HtmlConfiguration configuration) {
        super(configuration, DocPaths.SEARCH_PAGE);
    }

    @Override
    public void buildPage() throws DocFileIOException {
        String title = resources.getText("doclet.Window_Search_title");
        HtmlTree body = getBody(getWindowTitle(title));
        ContentBuilder searchFileContent = new ContentBuilder();
        addSearchFileContents(searchFileContent);
        body.add(new BodyContents()
                .setHeader(getHeader(PageMode.SEARCH))
                .addMainContent(searchFileContent)
                .setFooter(getFooter()));
        printHtmlDocument(null, "search", body);
    }

    /**
     * Adds the search file contents to the content tree.
     */
    protected void addSearchFileContents(Content contentTree) {

        String copyText = resources.getText("doclet.Copy_to_clipboard");
        String copiedText = resources.getText("doclet.Copied_to_clipboard");
        String copyUrlText = resources.getText("doclet.Copy_url_to_clipboard");
        Content helpSection = Text.EMPTY;
        // Suppress link to help page if no help page is generated or a custom help page is used.
        HtmlOptions options = configuration.getOptions();
        if (!options.noHelp() && options.helpFile().isEmpty()) {
            Content helpLink = HtmlTree.A("help-doc.html#search", contents.getContent("doclet.search.help_page_link"));
            helpSection = HtmlTree.P(contents.getContent("doclet.search.help_page_info", helpLink));
        }

        contentTree.add(HtmlTree.HEADING(Headings.PAGE_TITLE_HEADING, HtmlStyles.title,
                        contents.getContent("doclet.search.main_heading")))
                .add(HtmlTree.DIV(HtmlTree.INPUT(HtmlAttr.InputType.TEXT, HtmlId.of("page-search-input"))
                                .put(HtmlAttr.PLACEHOLDER, resources.getText("doclet.search_placeholder"))
                                .put(HtmlAttr.ARIA_LABEL, resources.getText("doclet.search_in_documentation"))
                                .put(HtmlAttr.AUTOCOMPLETE, "off"))
                        .add(HtmlTree.INPUT(HtmlAttr.InputType.RESET, HtmlId.of("page-search-reset"))
                                .put(HtmlAttr.VALUE, resources.getText("doclet.search_reset"))
                                .put(HtmlAttr.STYLE, "margin: 6px;"))
                        .add(HtmlTree.DETAILS(HtmlStyles.pageSearchDetails)
                                .add(HtmlTree.SUMMARY(contents.getContent("doclet.search.show_more"))
                                        .setId(HtmlId.of("page-search-expand")))))
                .add(HtmlTree.DIV(HtmlStyles.pageSearchInfo, helpSection)
                        .add(HtmlTree.P(contents.getContent("doclet.search.keyboard_info")))
                        .add(HtmlTree.P(contents.getContent("doclet.search.browser_info")))
                        .add(HtmlTree.SPAN(Text.of("link"))
                                .setId(HtmlId.of("page-search-link")))
                        .add(new HtmlTree(HtmlTag.BUTTON)
                                .add(new HtmlTree(HtmlTag.IMG)
                                        .put(HtmlAttr.SRC, pathToRoot.resolve(DocPaths.RESOURCE_FILES)
                                                                     .resolve(DocPaths.CLIPBOARD_SVG).getPath())
                                        .put(HtmlAttr.ALT, copyUrlText))
                                .add(HtmlTree.SPAN(Text.of(copyText))
                                        .put(HtmlAttr.DATA_COPIED, copiedText))
                                .addStyle(HtmlStyles.copy)
                                .put(HtmlAttr.ARIA_LABEL, copyUrlText)
                                .setId(HtmlId.of("page-search-copy")))
                        .add(HtmlTree.P(HtmlTree.INPUT(HtmlAttr.InputType.CHECKBOX, HtmlId.of("search-redirect")))
                                .add(HtmlTree.LABEL("search-redirect",
                                        contents.getContent("doclet.search.redirect")))))
                .add(new HtmlTree(HtmlTag.P)
                        .setId(HtmlId.of("page-search-notify"))
                        .add(contents.getContent("doclet.search.loading")))
                .add(HtmlTree.DIV(new HtmlTree(HtmlTag.DIV)
                                .setId(HtmlId.of("result-container"))
                                .addUnchecked(Text.EMPTY))
                        .setId(HtmlId.of("result-section"))
                        .put(HtmlAttr.STYLE, "display: none;")
                        .add(HtmlTree.SCRIPT(pathToRoot.resolve(DocPaths.SCRIPT_FILES)
                                                       .resolve(DocPaths.SEARCH_PAGE_JS).getPath())));
    }
}
