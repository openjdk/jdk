/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

import jdk.javadoc.internal.doclets.formats.html.markup.Head;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.DocType;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlAttr;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlDocument;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Script;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocFile;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;

/**
 * Writes an index.html file that tries to redirect to an alternate page.
 * The redirect uses JavaSCript, if enabled, falling back on
 * {@code <meta http-eqiv=refresh content="0,<uri>">}.
 * If neither are supported/enabled in a browser, the page displays the
 * standard "JavaScipt not enabled" message, and a link to the alternate page.
 */
public class IndexRedirectWriter extends HtmlDocletWriter {

    public static void generate(HtmlConfiguration configuration)
            throws DocFileIOException {
        IndexRedirectWriter indexRedirect;
        DocPath filename = DocPaths.INDEX;
            indexRedirect = new IndexRedirectWriter(configuration, filename);
            indexRedirect.generateIndexFile();
    }

    IndexRedirectWriter(HtmlConfiguration configuration, DocPath filename) {
        super(configuration, filename);
    }

    /**
     * Generate an index file that redirects to an alternate file.
     * @throws DocFileIOException if there is a problem generating the file
     */
    void generateIndexFile() throws DocFileIOException {
        DocType htmlDocType = DocType.forVersion(configuration.htmlVersion);
        Content htmlComment = contents.newPage;
        Head head = new Head(path, configuration.htmlVersion, configuration.docletVersion)
                .setTimestamp(true, false)
                .addDefaultScript(false);

        String title = (configuration.windowtitle.length() > 0)
                ? configuration.windowtitle
                : resources.getText("doclet.Generated_Docs_Untitled");

        head.setTitle(title)
                .setCharset(configuration.charset);

        String topFilePath = configuration.topFile.getPath();
        Script script = new Script("window.location.replace(")
                .appendStringLiteral(topFilePath, '\'')
                .append(")");
        HtmlTree metaRefresh = new HtmlTree(HtmlTag.META)
                .addAttr(HtmlAttr.HTTP_EQUIV, "Refresh")
                .addAttr(HtmlAttr.CONTENT, "0;" + topFilePath);
        head.addContent(
                script.asContent(),
                configuration.isOutputHtml5() ? HtmlTree.NOSCRIPT(metaRefresh) : metaRefresh);

        ContentBuilder bodyContent = new ContentBuilder();
        bodyContent.addContent(HtmlTree.NOSCRIPT(
                HtmlTree.P(contents.getContent("doclet.No_Script_Message"))));

        bodyContent.addContent(HtmlTree.P(HtmlTree.A(topFilePath, new StringContent(topFilePath))));

        Content body = new HtmlTree(HtmlTag.BODY);
        if (configuration.allowTag(HtmlTag.MAIN)) {
            HtmlTree main = HtmlTree.MAIN(bodyContent);
            body.addContent(main);
        } else {
            body.addContent(bodyContent);
        }

        Content htmlTree = HtmlTree.HTML(configuration.getLocale().getLanguage(), head.toContent(), body);
        HtmlDocument htmlDocument = new HtmlDocument(htmlDocType, htmlComment, htmlTree);
        htmlDocument.write(DocFile.createFileForOutput(configuration, path));
    }
}
