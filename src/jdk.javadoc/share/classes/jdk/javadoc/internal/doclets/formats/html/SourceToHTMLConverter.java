/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;

import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.internal.doclets.formats.html.markup.DocType;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlDocument;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.Messages;
import jdk.javadoc.internal.doclets.toolkit.Resources;
import jdk.javadoc.internal.doclets.toolkit.util.DocFile;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.DocletConstants;
import jdk.javadoc.internal.doclets.toolkit.util.SimpleDocletException;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

/**
 * Converts Java Source Code to HTML.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 */
public class SourceToHTMLConverter {

    /**
     * The number of trailing blank lines at the end of the page.
     * This is inserted so that anchors at the bottom of small pages
     * can be reached.
     */
    private static final int NUM_BLANK_LINES = 60;

    /**
     * New line to be added to the documentation.
     */
    private static final String NEW_LINE = DocletConstants.NL;

    private final HtmlConfiguration configuration;
    private final Messages messages;
    private final Resources resources;
    private final Utils utils;

    private final DocletEnvironment docEnv;

    private final DocPath outputdir;

    /**
     * Relative path from the documentation root to the file that is being
     * generated.
     */
    private DocPath relativePath = DocPath.empty;

    private SourceToHTMLConverter(HtmlConfiguration configuration, DocletEnvironment rd,
                                  DocPath outputdir) {
        this.configuration  = configuration;
        this.messages = configuration.getMessages();
        this.resources = configuration.resources;
        this.utils = configuration.utils;
        this.docEnv = rd;
        this.outputdir = outputdir;
    }

    /**
     * Translate the TypeElements in the given DocletEnvironment to HTML representation.
     *
     * @param configuration the configuration.
     * @param docEnv the DocletEnvironment to convert.
     * @param outputdir the name of the directory to output to.
     * @throws DocFileIOException if there is a problem generating an output file
     * @throws SimpleDocletException if there is a problem reading a source file
     */
    public static void convertRoot(HtmlConfiguration configuration, DocletEnvironment docEnv,
                                   DocPath outputdir) throws DocFileIOException, SimpleDocletException {
        new SourceToHTMLConverter(configuration, docEnv, outputdir).generate();
    }

    void generate() throws DocFileIOException, SimpleDocletException {
        if (docEnv == null || outputdir == null) {
            return;
        }
        for (PackageElement pkg : configuration.getSpecifiedPackageElements()) {
            // If -nodeprecated option is set and the package is marked as deprecated,
            // do not convert the package files to HTML.
            if (!(configuration.nodeprecated && utils.isDeprecated(pkg)))
                convertPackage(pkg, outputdir);
        }
        for (TypeElement te : configuration.getSpecifiedTypeElements()) {
            // If -nodeprecated option is set and the class is marked as deprecated
            // or the containing package is deprecated, do not convert the
            // package files to HTML.
            if (!(configuration.nodeprecated &&
                  (utils.isDeprecated(te) || utils.isDeprecated(utils.containingPackage(te)))))
                convertClass(te, outputdir);
        }
    }

    /**
     * Convert the Classes in the given Package to an HTML file.
     *
     * @param pkg the Package to convert.
     * @param outputdir the name of the directory to output to.
     * @throws DocFileIOException if there is a problem generating an output file
     * @throws SimpleDocletException if there is a problem reading a source file
     */
    public void convertPackage(PackageElement pkg, DocPath outputdir)
            throws DocFileIOException, SimpleDocletException {
        if (pkg == null) {
            return;
        }
        for (Element te : utils.getAllClasses(pkg)) {
            // If -nodeprecated option is set and the class is marked as deprecated,
            // do not convert the package files to HTML. We do not check for
            // containing package deprecation since it is already check in
            // the calling method above.
            if (!(configuration.nodeprecated && utils.isDeprecated(te)))
                convertClass((TypeElement)te, outputdir);
        }
    }

    /**
     * Convert the given Class to an HTML.
     *
     * @param te the class to convert.
     * @param outputdir the name of the directory to output to
     * @throws DocFileIOException if there is a problem generating the output file
     * @throws SimpleDocletException if there is a problem reading the source file
     */
    public void convertClass(TypeElement te, DocPath outputdir)
            throws DocFileIOException, SimpleDocletException {
        if (te == null) {
            return;
        }
        FileObject fo = utils.getFileObject(te);
        if (fo == null)
            return;

        try {
            Reader r = fo.openReader(true);
            int lineno = 1;
            String line;
            relativePath = DocPaths.SOURCE_OUTPUT
                    .resolve(configuration.docPaths.forPackage(te))
                    .invert();
            Content body = getHeader();
            Content pre = new HtmlTree(HtmlTag.PRE);
            try (LineNumberReader reader = new LineNumberReader(r)) {
                while ((line = reader.readLine()) != null) {
                    addLineNo(pre, lineno);
                    addLine(pre, line, lineno);
                    lineno++;
                }
            }
            addBlankLines(pre);
            Content div = HtmlTree.DIV(HtmlStyle.sourceContainer, pre);
            body.addContent(HtmlTree.MAIN(div));
            writeToFile(body, outputdir.resolve(configuration.docPaths.forClass(te)));
        } catch (IOException e) {
            String message = resources.getText("doclet.exception.read.file", fo.getName());
            throw new SimpleDocletException(message, e);
        }
    }

    /**
     * Write the output to the file.
     *
     * @param body the documentation content to be written to the file.
     * @param path the path for the file.
     */
    private void writeToFile(Content body, DocPath path) throws DocFileIOException {
        Head head = new Head(path, configuration.docletVersion)
//                .setTimestamp(!configuration.notimestamp) // temporary: compatibility!
                .setTitle(resources.getText("doclet.Window_Source_title"))
//                .setCharset(configuration.charset) // temporary: compatibility!
                .addDefaultScript(false)
                .setStylesheets(configuration.getMainStylesheet(), configuration.getAdditionalStylesheets());
        Content htmlTree = HtmlTree.HTML(configuration.getLocale().getLanguage(),
                head.toContent(), body);
        HtmlDocument htmlDocument = new HtmlDocument(htmlTree);
        messages.notice("doclet.Generating_0", path.getPath());
        htmlDocument.write(DocFile.createFileForOutput(configuration, path));
    }

    /**
     * Returns a link to the stylesheet file.
     *
     * @param head an HtmlTree to which the stylesheet links will be added
     */
    public void addStyleSheetProperties(Content head) {
        String filename = configuration.stylesheetfile;
        DocPath stylesheet;
        if (filename.length() > 0) {
            DocFile file = DocFile.createFileForInput(configuration, filename);
            stylesheet = DocPath.create(file.getName());
        } else {
            stylesheet = DocPaths.STYLESHEET;
        }
        DocPath p = relativePath.resolve(stylesheet);
        HtmlTree link = HtmlTree.LINK("stylesheet", "text/css", p.getPath(), "Style");
        head.addContent(link);
        addStylesheets(head);
    }

    protected void addStylesheets(Content tree) {
        List<String> stylesheets = configuration.additionalStylesheets;
        if (!stylesheets.isEmpty()) {
            stylesheets.forEach((ssheet) -> {
                DocFile file = DocFile.createFileForInput(configuration, ssheet);
                DocPath ssheetPath = DocPath.create(file.getName());
                HtmlTree slink = HtmlTree.LINK("stylesheet", "text/css", relativePath.resolve(ssheetPath).getPath(),
                        "Style");
                tree.addContent(slink);
            });
        }
    }

    /**
     * Get the header.
     *
     * @return the header content for the HTML file
     */
    private static Content getHeader() {
        return new HtmlTree(HtmlTag.BODY);
    }

    /**
     * Add the line numbers for the source code.
     *
     * @param pre the content tree to which the line number will be added
     * @param lineno The line number
     */
    private static void addLineNo(Content pre, int lineno) {
        HtmlTree span = new HtmlTree(HtmlTag.SPAN);
        span.setStyle(HtmlStyle.sourceLineNo);
        if (lineno < 10) {
            span.addContent("00" + Integer.toString(lineno));
        } else if (lineno < 100) {
            span.addContent("0" + Integer.toString(lineno));
        } else {
            span.addContent(Integer.toString(lineno));
        }
        pre.addContent(span);
    }

    /**
     * Add a line from source to the HTML file that is generated.
     *
     * @param pre the content tree to which the line will be added.
     * @param line the string to format.
     * @param currentLineNo the current number.
     */
    private void addLine(Content pre, String line, int currentLineNo) {
        if (line != null) {
            Content anchor = HtmlTree.A_ID(
                    "line." + Integer.toString(currentLineNo),
                    new StringContent(utils.replaceTabs(line)));
            pre.addContent(anchor);
            pre.addContent(NEW_LINE);
        }
    }

    /**
     * Add trailing blank lines at the end of the page.
     *
     * @param pre the content tree to which the blank lines will be added.
     */
    private static void addBlankLines(Content pre) {
        for (int i = 0; i < NUM_BLANK_LINES; i++) {
            pre.addContent(NEW_LINE);
        }
    }

    /**
     * Given an element, return an anchor name for it.
     *
     * @param utils the utility class, used to get the line number of the element
     * @param e the element to check.
     * @return the name of the anchor.
     */
    public static String getAnchorName(Utils utils, Element e) {
        return "line." + utils.getLineNumber(e);
    }
}
