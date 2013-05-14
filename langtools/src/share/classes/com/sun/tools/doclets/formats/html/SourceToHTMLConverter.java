/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.formats.html;

import java.io.*;

import javax.tools.FileObject;

import com.sun.javadoc.*;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

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
 * @since 1.4
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

    private final ConfigurationImpl configuration;

    private final RootDoc rootDoc;

    private DocPath outputdir;

    /**
     * Relative path from the documentation root to the file that is being
     * generated.
     */
    private DocPath relativePath = DocPath.empty;

    private SourceToHTMLConverter(ConfigurationImpl configuration, RootDoc rd,
            DocPath outputdir) {
        this.configuration  = configuration;
        this.rootDoc = rd;
        this.outputdir = outputdir;
    }

    /**
     * Convert the Classes in the given RootDoc to an HTML.
     *
     * @param configuration the configuration.
     * @param rd the RootDoc to convert.
     * @param outputdir the name of the directory to output to.
     */
    public static void convertRoot(ConfigurationImpl configuration, RootDoc rd,
            DocPath outputdir) {
        new SourceToHTMLConverter(configuration, rd, outputdir).generate();
    }

    void generate() {
        if (rootDoc == null || outputdir == null) {
            return;
        }
        PackageDoc[] pds = rootDoc.specifiedPackages();
        for (int i = 0; i < pds.length; i++) {
            // If -nodeprecated option is set and the package is marked as deprecated,
            // do not convert the package files to HTML.
            if (!(configuration.nodeprecated && Util.isDeprecated(pds[i])))
                convertPackage(pds[i], outputdir);
        }
        ClassDoc[] cds = rootDoc.specifiedClasses();
        for (int i = 0; i < cds.length; i++) {
            // If -nodeprecated option is set and the class is marked as deprecated
            // or the containing package is deprecated, do not convert the
            // package files to HTML.
            if (!(configuration.nodeprecated &&
                    (Util.isDeprecated(cds[i]) || Util.isDeprecated(cds[i].containingPackage()))))
                convertClass(cds[i], outputdir);
        }
    }

    /**
     * Convert the Classes in the given Package to an HTML.
     *
     * @param pd the Package to convert.
     * @param outputdir the name of the directory to output to.
     */
    public void convertPackage(PackageDoc pd, DocPath outputdir) {
        if (pd == null) {
            return;
        }
        ClassDoc[] cds = pd.allClasses();
        for (int i = 0; i < cds.length; i++) {
            // If -nodeprecated option is set and the class is marked as deprecated,
            // do not convert the package files to HTML. We do not check for
            // containing package deprecation since it is already check in
            // the calling method above.
            if (!(configuration.nodeprecated && Util.isDeprecated(cds[i])))
                convertClass(cds[i], outputdir);
        }
    }

    /**
     * Convert the given Class to an HTML.
     *
     * @param cd the class to convert.
     * @param outputdir the name of the directory to output to.
     */
    public void convertClass(ClassDoc cd, DocPath outputdir) {
        if (cd == null) {
            return;
        }
        try {
            SourcePosition sp = cd.position();
            if (sp == null)
                return;
            Reader r;
            // temp hack until we can update SourcePosition API.
            if (sp instanceof com.sun.tools.javadoc.SourcePositionImpl) {
                FileObject fo = ((com.sun.tools.javadoc.SourcePositionImpl) sp).fileObject();
                if (fo == null)
                    return;
                r = fo.openReader(true);
            } else {
                File file = sp.file();
                if (file == null)
                    return;
                r = new FileReader(file);
            }
            LineNumberReader reader = new LineNumberReader(r);
            int lineno = 1;
            String line;
            relativePath = DocPaths.SOURCE_OUTPUT
                    .resolve(DocPath.forPackage(cd))
                    .invert();
            Content body = getHeader();
            Content pre = new HtmlTree(HtmlTag.PRE);
            try {
                while ((line = reader.readLine()) != null) {
                    addLineNo(pre, lineno);
                    addLine(pre, line, lineno);
                    lineno++;
                }
            } finally {
                reader.close();
            }
            addBlankLines(pre);
            Content div = HtmlTree.DIV(HtmlStyle.sourceContainer, pre);
            body.addContent(div);
            writeToFile(body, outputdir.resolve(DocPath.forClass(cd)));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Write the output to the file.
     *
     * @param body the documentation content to be written to the file.
     * @param path the path for the file.
     */
    private void writeToFile(Content body, DocPath path) throws IOException {
        Content htmlDocType = DocType.TRANSITIONAL;
        Content head = new HtmlTree(HtmlTag.HEAD);
        head.addContent(HtmlTree.TITLE(new StringContent(
                configuration.getText("doclet.Window_Source_title"))));
        head.addContent(getStyleSheetProperties());
        Content htmlTree = HtmlTree.HTML(configuration.getLocale().getLanguage(),
                head, body);
        Content htmlDocument = new HtmlDocument(htmlDocType, htmlTree);
        configuration.message.notice("doclet.Generating_0", path.getPath());
        DocFile df = DocFile.createFileForOutput(configuration, path);
        Writer w = df.openWriter();
        try {
            htmlDocument.write(w, true);
        } finally {
            w.close();
        }

    }

    /**
     * Returns a link to the stylesheet file.
     *
     * @return an HtmlTree for the lINK tag which provides the stylesheet location
     */
    public HtmlTree getStyleSheetProperties() {
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
        return link;
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
        span.addStyle(HtmlStyle.sourceLineNo);
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
            pre.addContent(Util.replaceTabs(configuration, line));
            Content anchor = HtmlTree.A_NAME("line." + Integer.toString(currentLineNo));
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
     * Given a <code>Doc</code>, return an anchor name for it.
     *
     * @param d the <code>Doc</code> to check.
     * @return the name of the anchor.
     */
    public static String getAnchorName(Doc d) {
        return "line." + d.position().line();
    }
}
