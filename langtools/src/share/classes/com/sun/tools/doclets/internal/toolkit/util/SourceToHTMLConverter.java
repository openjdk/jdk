/*
 * Copyright 2001-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.doclets.internal.toolkit.util;

import java.io.*;
import java.util.*;
import javax.tools.FileObject;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.*;

/**
 * Converts Java Source Code to HTML.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @author Jamie Ho
 * @since 1.4
 */
public class SourceToHTMLConverter {

    /**
     * The background color.
     */
    protected static final String BGCOLOR = "white";

    /**
     * The line number color.
     */
    protected static final String LINE_NO_COLOR = "green";

    /**
     * The number of trailing blank lines at the end of the page.
     * This is inserted so that anchors at the bottom of small pages
     * can be reached.
     */
    protected static final int NUM_BLANK_LINES = 60;


    /**
     * Source is converted to HTML using static methods below.
     */
    private SourceToHTMLConverter() {}

    /**
     * Convert the Classes in the given RootDoc to an HTML.
     * @param configuration the configuration.
     * @param rd the RootDoc to convert.
     * @param outputdir the name of the directory to output to.
     */
    public static void convertRoot(Configuration configuration, RootDoc rd, String outputdir) {
        if (rd == null || outputdir == null) {
            return;
        }
        PackageDoc[] pds = rd.specifiedPackages();
        for (int i = 0; i < pds.length; i++) {
            convertPackage(configuration, pds[i], outputdir);
        }
        ClassDoc[] cds = rd.specifiedClasses();
        for (int i = 0; i < cds.length; i++) {
            convertClass(configuration, cds[i],
                getPackageOutputDir(outputdir, cds[i].containingPackage()));
        }
    }

    /**
     * Convert the Classes in the given Package to an HTML.
     * @param configuration the configuration.
     * @param pd the Package to convert.
     * @param outputdir the name of the directory to output to.
     */
    public static void convertPackage(Configuration configuration, PackageDoc pd, String outputdir) {
        if (pd == null || outputdir == null) {
            return;
        }
        String classOutputdir = getPackageOutputDir(outputdir, pd);
        ClassDoc[] cds = pd.allClasses();
        for (int i = 0; i < cds.length; i++) {
            convertClass(configuration, cds[i], classOutputdir);
        }
    }

    /**
     * Return the directory write output to for the given package.
     * @param outputDir the directory to output to.
     * @param pd the Package to generate output for.
     */
    private static String getPackageOutputDir(String outputDir, PackageDoc pd) {
        return outputDir + File.separator +
            DirectoryManager.getDirectoryPath(pd) + File.separator;
    }

    /**
     * Convert the given Class to an HTML.
     * @param configuration the configuration.
     * @param cd the class to convert.
     * @param outputdir the name of the directory to output to.
     */
    public static void convertClass(Configuration configuration, ClassDoc cd, String outputdir) {
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
            StringBuffer output = new StringBuffer();
            try {
                while ((line = reader.readLine()) != null) {
                    output.append(formatLine(line, configuration.sourcetab, lineno));
                    lineno++;
                }
            } finally {
                reader.close();
            }
            output = addLineNumbers(output.toString());
            output.insert(0, getHeader(configuration));
            output.append(getFooter());
            writeToFile(output.toString(), outputdir, cd.name(), configuration);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Write the output to the file.
     * @param output the string to output.
     * @param outputDir the directory to output to.
     * @param className the name of the class that I am converting to HTML.
     * @param configuration the Doclet configuration to pass notices to.
     */
    private static void writeToFile(String output, String outputDir, String className, Configuration configuration) throws IOException {
        File dir = new File(outputDir);
        dir.mkdirs();
        File newFile = new File(dir, className + ".html");
        configuration.message.notice("doclet.Generating_0", newFile.getPath());
        FileOutputStream fout = new FileOutputStream(newFile);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fout));
        bw.write(output);
        bw.close();
        fout.close();
    }

    /**
     * Given a <code>String</code>, add line numbers.
     * @param s the text to add line numbers to.
     *
     * @return the string buffer with the line numbering for each line.
     */
    private static StringBuffer addLineNumbers(String s) {
        StringBuffer sb = new StringBuffer();
        StringTokenizer st = new StringTokenizer(s, "\n", true);
        int lineno = 1;
        String current;
        while(st.hasMoreTokens()){
            current = st.nextToken();
            sb.append(current.equals("\n") ?
                    getHTMLLineNo(lineno) + current :
                    getHTMLLineNo(lineno) + current + st.nextToken());
            lineno++;
        }
        return sb;
    }

    /**
     * Get the header.
     * @param configuration the Doclet configuration
     * @return the header to the output file
     */
    protected static String getHeader(Configuration configuration) {
        StringBuffer result = new StringBuffer("<HTML lang=\"" + configuration.getLocale().getLanguage() + "\">" + DocletConstants.NL);
        result.append("<BODY BGCOLOR=\""+ BGCOLOR + "\">" + DocletConstants.NL);
        result.append("<PRE>" + DocletConstants.NL);
        return result.toString();
    }

    /**
     * Get the footer
     * @return the footer to the output file
     */
    protected static String getFooter() {
        StringBuffer footer = new StringBuffer();
        for (int i = 0; i < NUM_BLANK_LINES; i++) {
            footer.append(DocletConstants.NL);
        }
        footer.append("</PRE>" + DocletConstants.NL + "</BODY>" +
            DocletConstants.NL + "</HTML>" + DocletConstants.NL);
        return footer.toString();
    }

    /**
     * Get the HTML for the lines.
     * @param lineno The line number
     * @return the HTML code for the line
     */
    protected static String getHTMLLineNo(int lineno) {
        StringBuffer result = new StringBuffer("<FONT color=\"" + LINE_NO_COLOR
            + "\">");
        if (lineno < 10) {
            result.append("00" + ((new Integer(lineno)).toString()));
        } else if (lineno < 100) {
            result.append("0" + ((new Integer(lineno)).toString()));
        } else {
            result.append((new Integer(lineno)).toString());
        }
        result.append("</FONT>    ");
        return result.toString();
    }

    /**
     * Format a given line of source. <br>
     * Note:  In the future, we will add special colors for constructs in the
     * language.
     * @param line the string to format.
     * @param tabLength the number of spaces for each tab.
     * @param currentLineNo the current number.
     */
    protected static String formatLine(String line, int tabLength, int currentLineNo) {
        if (line == null) {
            return null;
        }
        StringBuffer lineBuffer = new StringBuffer(Util.escapeHtmlChars(line));
        //Insert an anchor for the line
        lineBuffer.append("<a name=\"line." + Integer.toString(currentLineNo) + "\"></a>");
        lineBuffer.append(DocletConstants.NL);
        Util.replaceTabs(tabLength, lineBuffer);
        return lineBuffer.toString();
    }

    /**
     * Given an array of <code>Doc</code>s, add to the given <code>HashMap</code> the
     * line numbers and anchors that should be inserted in the output at those lines.
     * @param docs the array of <code>Doc</code>s to add anchors for.
     * @param hash the <code>HashMap</code> to add to.
     */
    protected static void addToHash(Doc[] docs, HashMap<Integer,String> hash) {
        if(docs == null) {
            return;
        }
        for(int i = 0; i < docs.length; i++) {
            hash.put(docs[i].position().line(), getAnchor(docs[i]));
        }
    }

    /**
     * Given a <code>Doc</code>, return an anchor for it.
     * @param d the <code>Doc</code> to check.
     * @return an anchor of the form &lt;a name="my_name">&lt;/a>
     */
    protected static String getAnchor(Doc d) {
        return "    <a name=\"" + getAnchorName(d) + "\"></a>";
    }

    /**
     * Given a <code>Doc</code>, return an anchor name for it.
     * @param d the <code>Doc</code> to check.
     * @return the name of the anchor.
     */
    public static String getAnchorName(Doc d) {
        return "line." + d.position().line();
    }
}
