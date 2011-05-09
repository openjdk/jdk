/*
 * Copyright (c) 2001, 2011, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.tools.doclets.formats.html.markup.*;

/**
 * Converts Java Source Code to HTML.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
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
    private static final Content NEW_LINE = new RawHtml(DocletConstants.NL);

    /**
     * Relative path from the documentation root to the file that is being
     * generated.
     */
    private static String relativePath = "";

    /**
     * Source is converted to HTML using static methods below.
     */
    private SourceToHTMLConverter() {}

    /**
     * Convert the Classes in the given RootDoc to an HTML.
     *
     * @param configuration the configuration.
     * @param rd the RootDoc to convert.
     * @param outputdir the name of the directory to output to.
     */
    public static void convertRoot(ConfigurationImpl configuration, RootDoc rd,
            String outputdir) {
        if (rd == null || outputdir == null) {
            return;
        }
        PackageDoc[] pds = rd.specifiedPackages();
        for (int i = 0; i < pds.length; i++) {
            // If -nodeprecated option is set and the package is marked as deprecated,
            // do not convert the package files to HTML.
            if (!(configuration.nodeprecated && Util.isDeprecated(pds[i])))
                convertPackage(configuration, pds[i], outputdir);
        }
        ClassDoc[] cds = rd.specifiedClasses();
        for (int i = 0; i < cds.length; i++) {
            // If -nodeprecated option is set and the class is marked as deprecated
            // or the containing package is deprecated, do not convert the
            // package files to HTML.
            if (!(configuration.nodeprecated &&
                    (Util.isDeprecated(cds[i]) || Util.isDeprecated(cds[i].containingPackage()))))
                convertClass(configuration, cds[i],
                        getPackageOutputDir(outputdir, cds[i].containingPackage()));
        }
    }

    /**
     * Convert the Classes in the given Package to an HTML.
     *
     * @param configuration the configuration.
     * @param pd the Package to convert.
     * @param outputdir the name of the directory to output to.
     */
    public static void convertPackage(ConfigurationImpl configuration, PackageDoc pd,
            String outputdir) {
        if (pd == null || outputdir == null) {
            return;
        }
        String classOutputdir = getPackageOutputDir(outputdir, pd);
        ClassDoc[] cds = pd.allClasses();
        for (int i = 0; i < cds.length; i++) {
            // If -nodeprecated option is set and the class is marked as deprecated,
            // do not convert the package files to HTML. We do not check for
            // containing package deprecation since it is already check in
            // the calling method above.
            if (!(configuration.nodeprecated && Util.isDeprecated(cds[i])))
                convertClass(configuration, cds[i], classOutputdir);
        }
    }

    /**
     * Return the directory write output to for the given package.
     *
     * @param outputDir the directory to output to.
     * @param pd the Package to generate output for.
     * @return the package output directory as a String.
     */
    private static String getPackageOutputDir(String outputDir, PackageDoc pd) {
        return outputDir + File.separator +
            DirectoryManager.getDirectoryPath(pd) + File.separator;
    }

    /**
     * Convert the given Class to an HTML.
     *
     * @param configuration the configuration.
     * @param cd the class to convert.
     * @param outputdir the name of the directory to output to.
     */
    public static void convertClass(ConfigurationImpl configuration, ClassDoc cd,
            String outputdir) {
        if (cd == null || outputdir == null) {
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
            relativePath = DirectoryManager.getRelativePath(DocletConstants.SOURCE_OUTPUT_DIR_NAME) +
                    DirectoryManager.getRelativePath(cd.containingPackage());
            Content body = getHeader();
            Content pre = new HtmlTree(HtmlTag.PRE);
            try {
                while ((line = reader.readLine()) != null) {
                    addLineNo(pre, lineno);
                    addLine(pre, line, configuration.sourcetab, lineno);
                    lineno++;
                }
            } finally {
                reader.close();
            }
            addBlankLines(pre);
            Content div = HtmlTree.DIV(HtmlStyle.sourceContainer, pre);
            body.addContent(div);
            writeToFile(body, outputdir, cd.name(), configuration);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Write the output to the file.
     *
     * @param body the documentation content to be written to the file.
     * @param outputDir the directory to output to.
     * @param className the name of the class that I am converting to HTML.
     * @param configuration the Doclet configuration to pass notices to.
     */
    private static void writeToFile(Content body, String outputDir,
            String className, ConfigurationImpl configuration) throws IOException {
        Content htmlDocType = DocType.Transitional();
        Content head = new HtmlTree(HtmlTag.HEAD);
        head.addContent(HtmlTree.TITLE(new StringContent(
                configuration.getText("doclet.Window_Source_title"))));
        head.addContent(getStyleSheetProperties(configuration));
        Content htmlTree = HtmlTree.HTML(configuration.getLocale().getLanguage(),
                head, body);
        Content htmlDocument = new HtmlDocument(htmlDocType, htmlTree);
        File dir = new File(outputDir);
        dir.mkdirs();
        File newFile = new File(dir, className + ".html");
        configuration.message.notice("doclet.Generating_0", newFile.getPath());
        FileOutputStream fout = new FileOutputStream(newFile);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fout));
        bw.write(htmlDocument.toString());
        bw.close();
        fout.close();
    }

    /**
     * Returns a link to the stylesheet file.
     *
     * @param configuration the doclet configuration for the current run of javadoc
     * @return an HtmlTree for the lINK tag which provides the stylesheet location
     */
    public static HtmlTree getStyleSheetProperties(ConfigurationImpl configuration) {
        String filename = configuration.stylesheetfile;
        if (filename.length() > 0) {
            File stylefile = new File(filename);
            String parent = stylefile.getParent();
            filename = (parent == null)?
                filename:
                filename.substring(parent.length() + 1);
        } else {
            filename = "stylesheet.css";
        }
        filename = relativePath + filename;
        HtmlTree link = HtmlTree.LINK("stylesheet", "text/css", filename, "Style");
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
     * @param tabLength the number of spaces for each tab.
     * @param currentLineNo the current number.
     */
    private static void addLine(Content pre, String line, int tabLength,
            int currentLineNo) {
        if (line != null) {
            StringBuilder lineBuffer = new StringBuilder(Util.escapeHtmlChars(line));
            Util.replaceTabs(tabLength, lineBuffer);
            pre.addContent(new RawHtml(lineBuffer.toString()));
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
