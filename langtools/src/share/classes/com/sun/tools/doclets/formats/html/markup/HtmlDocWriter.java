/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.doclets.formats.html.markup;

import java.io.*;
import java.util.*;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.*;


/**
 * Class for the Html Format Code Generation specific to JavaDoc.
 * This Class contains methods related to the Html Code Generation which
 * are used by the Sub-Classes in the package com.sun.tools.doclets.standard
 * and com.sun.tools.doclets.oneone.
 *
 * @since 1.2
 * @author Atul M Dambalkar
 * @author Robert Field
 */
public abstract class HtmlDocWriter extends HtmlWriter {

    /**
     * Constructor. Initializes the destination file name through the super
     * class HtmlWriter.
     *
     * @param filename String file name.
     */
    public HtmlDocWriter(Configuration configuration,
                         String filename) throws IOException {
        super(configuration,
              null, configuration.destDirName + filename,
              configuration.docencoding);
        // use File to normalize file separators
        configuration.message.notice("doclet.Generating_0",
            new File(configuration.destDirName, filename));
    }

    public HtmlDocWriter(Configuration configuration,
                         String path, String filename) throws IOException {
        super(configuration,
              configuration.destDirName + path, filename,
              configuration.docencoding);
        // use File to normalize file separators
        configuration.message.notice("doclet.Generating_0",
            new File(configuration.destDirName,
                    ((path.length() > 0)? path + File.separator: "") + filename));
    }

    /**
     * Accessor for configuration.
     */
    public abstract Configuration configuration();

    /**
     * Print Html Hyper Link.
     *
     * @param link String name of the file.
     * @param where Position of the link in the file. Character '#' is not
     * needed.
     * @param label Tag for the link.
     * @param strong  Boolean that sets label to strong.
     */
    public void printHyperLink(String link, String where,
                               String label, boolean strong) {
        print(getHyperLink(link, where, label, strong, "", "", ""));
    }

    /**
     * Print Html Hyper Link.
     *
     * @param link String name of the file.
     * @param where Position of the link in the file. Character '#' is not
     * needed.
     * @param label Tag for the link.
     */
    public void printHyperLink(String link, String where, String label) {
        printHyperLink(link, where, label, false);
    }

    /**
     * Print Html Hyper Link.
     *
     * @param link       String name of the file.
     * @param where      Position of the link in the file. Character '#' is not
     * needed.
     * @param label      Tag for the link.
     * @param strong       Boolean that sets label to strong.
     * @param stylename  String style of text defined in style sheet.
     */
    public void printHyperLink(String link, String where,
                               String label, boolean strong,
                               String stylename) {
        print(getHyperLink(link, where, label, strong, stylename, "", ""));
    }

    /**
     * Return Html Hyper Link string.
     *
     * @param link       String name of the file.
     * @param where      Position of the link in the file. Character '#' is not
     * needed.
     * @param label      Tag for the link.
     * @param strong       Boolean that sets label to strong.
     * @return String    Hyper Link.
     */
    public String getHyperLink(String link, String where,
                               String label, boolean strong) {
        return getHyperLink(link, where, label, strong, "", "", "");
    }

    /**
     * Get Html Hyper Link string.
     *
     * @param link       String name of the file.
     * @param where      Position of the link in the file. Character '#' is not
     *                   needed.
     * @param label      Tag for the link.
     * @param strong       Boolean that sets label to strong.
     * @param stylename  String style of text defined in style sheet.
     * @return String    Hyper Link.
     */
    public String getHyperLink(String link, String where,
                               String label, boolean strong,
                               String stylename) {
        return getHyperLink(link, where, label, strong, stylename, "", "");
    }

    /**
     * Get Html Hyper Link string.
     *
     * @param link       String name of the file.
     * @param where      Position of the link in the file. Character '#' is not
     *                   needed.
     * @param label      Tag for the link.
     * @param strong       Boolean that sets label to strong.
     * @param stylename  String style of text defined in style sheet.
     * @param title      String that describes the link's content for accessibility.
     * @param target     Target frame.
     * @return String    Hyper Link.
     */
    public String getHyperLink(String link, String where,
                               String label, boolean strong,
                               String stylename, String title, String target) {
        StringBuffer retlink = new StringBuffer();
        retlink.append("<A HREF=\"");
        retlink.append(link);
        if (where != null && where.length() != 0) {
            retlink.append("#");
            retlink.append(where);
        }
        retlink.append("\"");
        if (title != null && title.length() != 0) {
            retlink.append(" title=\"" + title + "\"");
        }
        if (target != null && target.length() != 0) {
            retlink.append(" target=\"" + target + "\"");
        }
        retlink.append(">");
        if (stylename != null && stylename.length() != 0) {
            retlink.append("<FONT CLASS=\"");
            retlink.append(stylename);
            retlink.append("\">");
        }
        if (strong) {
            retlink.append("<STRONG>");
        }
        retlink.append(label);
        if (strong) {
            retlink.append("</STRONG>");
        }
        if (stylename != null && stylename.length() != 0) {
            retlink.append("</FONT>");
        }
        retlink.append("</A>");
        return retlink.toString();
    }

    /**
     * Print link without positioning in the file.
     *
     * @param link       String name of the file.
     * @param label      Tag for the link.
     */
    public void printHyperLink(String link, String label) {
        print(getHyperLink(link, "", label, false));
    }

    /**
     * Get link string without positioning in the file.
     *
     * @param link       String name of the file.
     * @param label      Tag for the link.
     * @return Strign    Hyper link.
     */
    public String getHyperLink(String link, String label) {
        return getHyperLink(link, "", label, false);
    }

    /**
     * Print the name of the package, this class is in.
     *
     * @param cd    ClassDoc.
     */
    public void printPkgName(ClassDoc cd) {
        print(getPkgName(cd));
    }

    /**
     * Get the name of the package, this class is in.
     *
     * @param cd    ClassDoc.
     */
    public String getPkgName(ClassDoc cd) {
        String pkgName = cd.containingPackage().name();
        if (pkgName.length() > 0) {
            pkgName += ".";
            return pkgName;
        }
        return "";
    }

    /**
     * Keep track of member details list. Print the definition list start tag
     * if it is not printed yet.
     */
    public void printMemberDetailsListStartTag () {
        if (!getMemberDetailsListPrinted()) {
            dl();
            memberDetailsListPrinted = true;
        }
    }

    /**
     * Print the definition list end tag if the list start tag was printed.
     */
    public void printMemberDetailsListEndTag () {
        if (getMemberDetailsListPrinted()) {
            dlEnd();
            memberDetailsListPrinted = false;
        }
    }

    public boolean getMemberDetailsListPrinted() {
        return memberDetailsListPrinted;
    }

    /**
     * Print the frameset version of the Html file header.
     * Called only when generating an HTML frameset file.
     *
     * @param title    Title of this HTML document.
     */
    public void printFramesetHeader(String title) {
        printFramesetHeader(title, false);
    }

    /**
     * Print the frameset version of the Html file header.
     * Called only when generating an HTML frameset file.
     *
     * @param title        Title of this HTML document.
     * @param noTimeStamp  If true, don't print time stamp in header.
     */
    public void printFramesetHeader(String title, boolean noTimeStamp) {
        println("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 " +
                    "Frameset//EN\" " +
                    "\"http://www.w3.org/TR/html4/frameset.dtd\">");
        println("<!--NewPage-->");
        html();
        head();
        if (! noTimeStamp) {
            print("<!-- Generated by javadoc on ");
            print(today());
            println("-->");
        }
        if (configuration.charset.length() > 0) {
            println("<META http-equiv=\"Content-Type\" content=\"text/html; "
                        + "charset=" + configuration.charset + "\">");
        }
        title();
        println(title);
        titleEnd();
        //Script to set the classFrame if necessary.
        script();
        println("    targetPage = \"\" + window.location.search;");
        println("    if (targetPage != \"\" && targetPage != \"undefined\")");
        println("        targetPage = targetPage.substring(1);");
        println("    if (targetPage.indexOf(\":\") != -1)");
        println("        targetPage = \"undefined\";");

        println("    function loadFrames() {");
        println("        if (targetPage != \"\" && targetPage != \"undefined\")");
        println("             top.classFrame.location = top.targetPage;");
        println("    }");
        scriptEnd();
        noScript();
        noScriptEnd();
        headEnd();
    }

    /**
     * Print the appropriate spaces to format the class tree in the class page.
     *
     * @param len   Number of spaces.
     */
    public String spaces(int len) {
        String space = "";

        for (int i = 0; i < len; i++) {
            space += " ";
        }
        return space;
    }

    /**
     * Print the closing &lt;/body&gt; and &lt;/html&gt; tags.
     */
    public void printBodyHtmlEnd() {
        println();
        bodyEnd();
        htmlEnd();
    }

    /**
     * Calls {@link #printBodyHtmlEnd()} method.
     */
    public void printFooter() {
        printBodyHtmlEnd();
    }

    /**
     * Print closing &lt;/html&gt; tag.
     */
    public void printFrameFooter() {
        htmlEnd();
    }

    /**
     * Print ten non-breaking spaces("&#38;nbsp;").
     */
    public void printNbsps() {
        print("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
    }

    /**
     * Get the day and date information for today, depending upon user option.
     *
     * @return String Today.
     * @see java.util.Calendar
     * @see java.util.GregorianCalendar
     * @see java.util.TimeZone
     */
    public String today() {
        Calendar calendar = new GregorianCalendar(TimeZone.getDefault());
        return calendar.getTime().toString();
    }
}
