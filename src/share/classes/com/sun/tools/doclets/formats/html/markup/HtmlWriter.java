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

import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * Class for the Html format code generation.
 * Initilizes PrintWriter with FileWriter, to enable print
 * related methods to generate the code to the named File through FileWriter.
 *
 * @since 1.2
 * @author Atul M Dambalkar
 * @author Bhavesh Patel (Modified)
 */
public class HtmlWriter extends PrintWriter {

    /**
     * Name of the file, to which this writer is writing to.
     */
    protected final String htmlFilename;

    /**
     * The window title of this file
     */
    protected String winTitle;

    /**
     * URL file separator string("/").
     */
    public static final String fileseparator =
         DirectoryManager.URL_FILE_SEPERATOR;

    /**
     * The configuration
     */
    protected Configuration configuration;

    /**
     * The flag to indicate whether a member details list is printed or not.
     */
    protected boolean memberDetailsListPrinted;

    /**
     * Header for tables displaying packages and description..
     */
    protected final String[] packageTableHeader;

    /**
     * Summary for use tables displaying class and package use.
     */
    protected final String useTableSummary;

    /**
     * Column header for class docs displaying Modifier and Type header.
     */
    protected final String modifierTypeHeader;

    /**
     * Constructor.
     *
     * @param path The directory path to be created for this file
     *             or null if none to be created.
     * @param filename File Name to which the PrintWriter will
     *                 do the Output.
     * @param docencoding Encoding to be used for this file.
     * @exception IOException Exception raised by the FileWriter is passed on
     * to next level.
     * @exception UnSupportedEncodingException Exception raised by the
     * OutputStreamWriter is passed on to next level.
     */
    public HtmlWriter(Configuration configuration,
                      String path, String filename, String docencoding)
                      throws IOException, UnsupportedEncodingException {
        super(Util.genWriter(configuration, path, filename, docencoding));
        this.configuration = configuration;
        htmlFilename = filename;
        this.memberDetailsListPrinted = false;
        packageTableHeader = new String[] {
            configuration.getText("doclet.Package"),
            configuration.getText("doclet.Description")
        };
        useTableSummary = configuration.getText("doclet.Use_Table_Summary",
                configuration.getText("doclet.packages"));
        modifierTypeHeader = configuration.getText("doclet.0_and_1",
                configuration.getText("doclet.Modifier"),
                configuration.getText("doclet.Type"));
    }

    /**
     * Print &lt;HTML&gt; tag. Add a newline character at the end.
     */
    public void html() {
        println("<HTML lang=\"" + configuration.getLocale().getLanguage() + "\">");
    }

    /**
     * Print &lt;/HTML&gt; tag. Add a newline character at the end.
     */
    public void htmlEnd() {
        println("</HTML>");
    }

    /**
     * Print the script code to be embeded before the  &lt;/HEAD&gt; tag.
     */
    protected void printWinTitleScript(String winTitle){
        if(winTitle != null && winTitle.length() > 0) {
            script();
            println("function windowTitle()");
            println("{");
            println("    if (location.href.indexOf('is-external=true') == -1) {");
            println("        parent.document.title=\"" + winTitle + "\";");
            println("    }");
            println("}");
            scriptEnd();
            noScript();
            noScriptEnd();
        }
    }

    /**
     * Print the Javascript &lt;SCRIPT&gt; start tag with its type
     * attribute.
     */
    public void script() {
        println("<SCRIPT type=\"text/javascript\">");
    }

    /**
     * Print the Javascript &lt;/SCRIPT&gt; end tag.
     */
    public void scriptEnd() {
        println("</SCRIPT>");
    }

    /**
     * Print the Javascript &lt;NOSCRIPT&gt; start tag.
     */
    public void noScript() {
        println("<NOSCRIPT>");
    }

    /**
     * Print the Javascript &lt;/NOSCRIPT&gt; end tag.
     */
    public void noScriptEnd() {
        println("</NOSCRIPT>");
    }

    /**
     * Return the Javascript call to be embedded in the &lt;BODY&gt; tag.
     * Return nothing if winTitle is empty.
     * @return the Javascript call to be embedded in the &lt;BODY&gt; tag.
     */
    protected String getWindowTitleOnload(){
        if(winTitle != null && winTitle.length() > 0) {
            return " onload=\"windowTitle();\"";
        } else {
            return "";
        }
    }

    /**
     * Print &lt;BODY BGCOLOR="bgcolor"&gt;, including JavaScript
     * "onload" call to load windowtitle script.  This script shows the name
     * of the document in the window title bar when frames are on.
     *
     * @param bgcolor Background color.
     * @param includeScript  boolean set true if printing windowtitle script
     */
    public void body(String bgcolor, boolean includeScript) {
        print("<BODY BGCOLOR=\"" + bgcolor + "\"");
        if (includeScript) {
            print(getWindowTitleOnload());
        }
        println(">");
    }

    /**
     * Print &lt;/BODY&gt; tag. Add a newline character at the end.
     */
    public void bodyEnd() {
        println("</BODY>");
    }

    /**
     * Print &lt;TITLE&gt; tag. Add a newline character at the end.
     */
    public void title() {
        println("<TITLE>");
    }

    /**
     * Print &lt;TITLE&gt; tag. Add a newline character at the end.
     *
     * @param winTitle The title of this document.
     */
    public void title(String winTitle) {
        // Set window title string which is later printed
        this.winTitle = winTitle;
        title();
    }


    /**
     * Print &lt;/TITLE&gt; tag. Add a newline character at the end.
     */
    public void titleEnd() {
        println("</TITLE>");
    }

    /**
     * Print &lt;UL&gt; tag. Add a newline character at the end.
     */
    public void ul() {
        println("<UL>");
    }

    /**
     * Print &lt;/UL&gt; tag. Add a newline character at the end.
     */
    public void ulEnd() {
        println("</UL>");
    }

    /**
     * Print &lt;LI&gt; tag.
     */
    public void li() {
        print("<LI>");
    }

    /**
     * Print &lt;LI TYPE="type"&gt; tag.
     *
     * @param type Type string.
     */
    public void li(String type) {
        print("<LI TYPE=\"" + type + "\">");
    }

    /**
     * Print &lt;H1&gt; tag. Add a newline character at the end.
     */
    public void h1() {
        println("<H1>");
    }

    /**
     * Print &lt;/H1&gt; tag. Add a newline character at the end.
     */
    public void h1End() {
        println("</H1>");
    }

    /**
     * Print text with &lt;H1&gt; tag. Also adds &lt;/H1&gt; tag. Add a newline character
     * at the end of the text.
     *
     * @param text Text to be printed with &lt;H1&gt; format.
     */
    public void h1(String text) {
        h1();
        println(text);
        h1End();
    }

    /**
     * Print &lt;H2&gt; tag. Add a newline character at the end.
     */
    public void h2() {
        println("<H2>");
    }

    /**
     * Print text with &lt;H2&gt; tag. Also adds &lt;/H2&gt; tag. Add a newline character
     *  at the end of the text.
     *
     * @param text Text to be printed with &lt;H2&gt; format.
     */
    public void h2(String text) {
        h2();
        println(text);
        h2End();
    }

    /**
     * Print &lt;/H2&gt; tag. Add a newline character at the end.
     */
    public void h2End() {
        println("</H2>");
    }

    /**
     * Print &lt;H3&gt; tag. Add a newline character at the end.
     */
    public void h3() {
        println("<H3>");
    }

    /**
     * Print text with &lt;H3&gt; tag. Also adds &lt;/H3&gt; tag. Add a newline character
     *  at the end of the text.
     *
     * @param text Text to be printed with &lt;H3&gt; format.
     */
    public void h3(String text) {
        h3();
        println(text);
        h3End();
    }

    /**
     * Print &lt;/H3&gt; tag. Add a newline character at the end.
     */
    public void h3End() {
        println("</H3>");
    }

    /**
     * Print &lt;H4&gt; tag. Add a newline character at the end.
     */
    public void h4() {
        println("<H4>");
    }

    /**
     * Print &lt;/H4&gt; tag. Add a newline character at the end.
     */
    public void h4End() {
        println("</H4>");
    }

    /**
     * Print text with &lt;H4&gt; tag. Also adds &lt;/H4&gt; tag. Add a newline character
     * at the end of the text.
     *
     * @param text Text to be printed with &lt;H4&gt; format.
     */
    public void h4(String text) {
        h4();
        println(text);
        h4End();
    }

    /**
     * Print &lt;H5&gt; tag. Add a newline character at the end.
     */
    public void h5() {
        println("<H5>");
    }

    /**
     * Print &lt;/H5&gt; tag. Add a newline character at the end.
     */
    public void h5End() {
        println("</H5>");
    }

    /**
     * Print HTML &lt;IMG SRC="imggif" WIDTH="width" HEIGHT="height" ALT="imgname&gt;
     * tag. It prepends the "images" directory name to the "imggif". This
     * method is used for oneone format generation. Add a newline character
     * at the end.
     *
     * @param imggif   Image GIF file.
     * @param imgname  Image name.
     * @param width    Width of the image.
     * @param height   Height of the image.
     */
    public void img(String imggif, String imgname, int width, int height) {
        println("<IMG SRC=\"images/" + imggif + ".gif\""
              + " WIDTH=\"" + width + "\" HEIGHT=\"" + height
              + "\" ALT=\"" + imgname + "\">");
    }

    /**
     * Print &lt;MENU&gt; tag. Add a newline character at the end.
     */
    public void menu() {
        println("<MENU>");
    }

    /**
     * Print &lt;/MENU&gt; tag. Add a newline character at the end.
     */
    public void menuEnd() {
        println("</MENU>");
    }

    /**
     * Print &lt;PRE&gt; tag. Add a newline character at the end.
     */
    public void pre() {
        println("<PRE>");
    }

    /**
     * Print &lt;PRE&gt; tag without adding new line character at th eend.
     */
    public void preNoNewLine() {
        print("<PRE>");
    }

    /**
     * Print &lt;/PRE&gt; tag. Add a newline character at the end.
     */
    public void preEnd() {
        println("</PRE>");
    }

    /**
     * Print &lt;HR&gt; tag. Add a newline character at the end.
     */
    public void hr() {
        println("<HR>");
    }

    /**
     * Print &lt;HR SIZE="size" WIDTH="widthpercent%"&gt; tag. Add a newline
     * character at the end.
     *
     * @param size           Size of the ruler.
     * @param widthPercent   Percentage Width of the ruler
     */
    public void hr(int size, int widthPercent) {
        println("<HR SIZE=\"" + size + "\" WIDTH=\"" + widthPercent + "%\">");
    }

    /**
     * Print &lt;HR SIZE="size" NOSHADE&gt; tag. Add a newline character at the end.
     *
     * @param size           Size of the ruler.
     * @param noshade        noshade string.
     */
    public void hr(int size, String noshade) {
        println("<HR SIZE=\"" + size + "\" NOSHADE>");
    }

    /**
     * Get the "&lt;STRONG&gt;" string.
     *
     * @return String Return String "&lt;STRONG&gt;";
     */
    public String getStrong() {
        return "<STRONG>";
    }

    /**
     * Get the "&lt;/STRONG&gt;" string.
     *
     * @return String Return String "&lt;/STRONG&gt;";
     */
    public String getStrongEnd() {
        return "</STRONG>";
    }

    /**
     * Print &lt;STRONG&gt; tag.
     */
    public void strong() {
        print("<STRONG>");
    }

    /**
     * Print &lt;/STRONG&gt; tag.
     */
    public void strongEnd() {
        print("</STRONG>");
    }

    /**
     * Print text passed, in strong format using &lt;STRONG&gt; and &lt;/STRONG&gt; tags.
     *
     * @param text String to be printed in between &lt;STRONG&gt; and &lt;/STRONG&gt; tags.
     */
    public void strong(String text) {
        strong();
        print(text);
        strongEnd();
    }

    /**
     * Print text passed, in Italics using &lt;I&gt; and &lt;/I&gt; tags.
     *
     * @param text String to be printed in between &lt;I&gt; and &lt;/I&gt; tags.
     */
    public void italics(String text) {
        print("<I>");
        print(text);
        println("</I>");
    }

    /**
     * Return, text passed, with Italics &lt;I&gt; and &lt;/I&gt; tags, surrounding it.
     * So if the text passed is "Hi", then string returned will be "&lt;I&gt;Hi&lt;/I&gt;".
     *
     * @param text String to be printed in between &lt;I&gt; and &lt;/I&gt; tags.
     */
    public String italicsText(String text) {
        return "<I>" + text + "</I>";
    }

    public String codeText(String text) {
        return "<CODE>" + text + "</CODE>";
    }

    /**
     * Print "&#38;nbsp;", non-breaking space.
     */
    public void space() {
        print("&nbsp;");
    }

    /**
     * Print &lt;DL&gt; tag. Add a newline character at the end.
     */
    public void dl() {
        println("<DL>");
    }

    /**
     * Print &lt;/DL&gt; tag. Add a newline character at the end.
     */
    public void dlEnd() {
        println("</DL>");
    }

    /**
     * Print &lt;DT&gt; tag.
     */
    public void dt() {
        print("<DT>");
    }

    /**
     * Print &lt;/DT&gt; tag.
     */
    public void dtEnd() {
        print("</DT>");
    }

    /**
     * Print &lt;DD&gt; tag.
     */
    public void dd() {
        print("<DD>");
    }

    /**
     * Print &lt;/DD&gt; tag. Add a newline character at the end.
     */
    public void ddEnd() {
        println("</DD>");
    }

    /**
     * Print &lt;SUP&gt; tag. Add a newline character at the end.
     */
    public void sup() {
        println("<SUP>");
    }

    /**
     * Print &lt;/SUP&gt; tag. Add a newline character at the end.
     */
    public void supEnd() {
        println("</SUP>");
    }

    /**
     * Print &lt;FONT SIZE="size"&gt; tag. Add a newline character at the end.
     *
     * @param size String size.
     */
    public void font(String size) {
        println("<FONT SIZE=\"" + size + "\">");
    }

    /**
     * Print &lt;FONT SIZE="size"&gt; tag.
     *
     * @param size String size.
     */
    public void fontNoNewLine(String size) {
        print("<FONT SIZE=\"" + size + "\">");
    }

    /**
     * Print &lt;FONT CLASS="stylename"&gt; tag. Add a newline character at the end.
     *
     * @param stylename String stylename.
     */
    public void fontStyle(String stylename) {
        print("<FONT CLASS=\"" + stylename + "\">");
    }

    /**
     * Print &lt;FONT SIZE="size" CLASS="stylename"&gt; tag. Add a newline character
     * at the end.
     *
     * @param size String size.
     * @param stylename String stylename.
     */
    public void fontSizeStyle(String size, String stylename) {
        println("<FONT size=\"" + size + "\" CLASS=\"" + stylename + "\">");
    }

    /**
     * Print &lt;/FONT&gt; tag.
     */
    public void fontEnd() {
        print("</FONT>");
    }

    /**
     * Get the "&lt;FONT COLOR="color"&gt;" string.
     *
     * @param color String color.
     * @return String Return String "&lt;FONT COLOR="color"&gt;".
     */
    public String getFontColor(String color) {
        return "<FONT COLOR=\"" + color + "\">";
    }

    /**
     * Get the "&lt;/FONT&gt;" string.
     *
     * @return String Return String "&lt;/FONT&gt;";
     */
    public String getFontEnd() {
        return "</FONT>";
    }

    /**
     * Print &lt;CENTER&gt; tag. Add a newline character at the end.
     */
    public void center() {
        println("<CENTER>");
    }

    /**
     * Print &lt;/CENTER&gt; tag. Add a newline character at the end.
     */
    public void centerEnd() {
        println("</CENTER>");
    }

    /**
     * Print anchor &lt;A NAME="name"&gt; tag.
     *
     * @param name Name String.
     */
    public void aName(String name) {
        print("<A NAME=\"" + name + "\">");
    }

    /**
     * Print &lt;/A&gt; tag.
     */
    public void aEnd() {
        print("</A>");
    }

    /**
     * Print &lt;I&gt; tag.
     */
    public void italic() {
        print("<I>");
    }

    /**
     * Print &lt;/I&gt; tag.
     */
    public void italicEnd() {
        print("</I>");
    }

    /**
     * Print contents within anchor &lt;A NAME="name"&gt; tags.
     *
     * @param name String name.
     * @param content String contents.
     */
    public void anchor(String name, String content) {
        aName(name);
        print(content);
        aEnd();
    }

    /**
     * Print anchor &lt;A NAME="name"&gt; and &lt;/A&gt;tags. Print comment string
     * "&lt;!-- --&gt;" within those tags.
     *
     * @param name String name.
     */
    public void anchor(String name) {
        anchor(name, "<!-- -->");
    }

    /**
     * Print newline and then print &lt;P&gt; tag. Add a newline character at the
     * end.
     */
    public void p() {
        println();
        println("<P>");
    }

    /**
     * Print newline and then print &lt;/P&gt; tag. Add a newline character at the
     * end.
     */
    public void pEnd() {
        println();
        println("</P>");
    }

    /**
     * Print newline and then print &lt;BR&gt; tag. Add a newline character at the
     * end.
     */
    public void br() {
        println();
        println("<BR>");
    }

    /**
     * Print &lt;ADDRESS&gt; tag. Add a newline character at the end.
     */
    public void address() {
        println("<ADDRESS>");
    }

    /**
     * Print &lt;/ADDRESS&gt; tag. Add a newline character at the end.
     */
    public void addressEnd() {
        println("</ADDRESS>");
    }

    /**
     * Print &lt;HEAD&gt; tag. Add a newline character at the end.
     */
    public void head() {
        println("<HEAD>");
    }

    /**
     * Print &lt;/HEAD&gt; tag. Add a newline character at the end.
     */
    public void headEnd() {
        println("</HEAD>");
    }

    /**
     * Print &lt;CODE&gt; tag.
     */
    public void code() {
        print("<CODE>");
    }

    /**
     * Print &lt;/CODE&gt; tag.
     */
    public void codeEnd() {
        print("</CODE>");
    }

    /**
     * Print &lt;EM&gt; tag. Add a newline character at the end.
     */
    public void em() {
        println("<EM>");
    }

    /**
     * Print &lt;/EM&gt; tag. Add a newline character at the end.
     */
    public void emEnd() {
        println("</EM>");
    }

    /**
     * Print HTML &lt;TABLE BORDER="border" WIDTH="width"
     * CELLPADDING="cellpadding" CELLSPACING="cellspacing"&gt; tag.
     *
     * @param border       Border size.
     * @param width        Width of the table.
     * @param cellpadding  Cellpadding for the table cells.
     * @param cellspacing  Cellspacing for the table cells.
     */
    public void table(int border, String width, int cellpadding,
                      int cellspacing) {
        println(DocletConstants.NL +
                "<TABLE BORDER=\"" + border +
                "\" WIDTH=\"" + width +
                "\" CELLPADDING=\"" + cellpadding +
                "\" CELLSPACING=\"" + cellspacing +
                "\" SUMMARY=\"\">");
    }

    /**
     * Print HTML &lt;TABLE BORDER="border" WIDTH="width"
     * CELLPADDING="cellpadding" CELLSPACING="cellspacing" SUMMARY="summary"&gt; tag.
     *
     * @param border       Border size.
     * @param width        Width of the table.
     * @param cellpadding  Cellpadding for the table cells.
     * @param cellspacing  Cellspacing for the table cells.
     * @param summary      Table summary.
     */
    public void table(int border, String width, int cellpadding,
                      int cellspacing, String summary) {
        println(DocletConstants.NL +
                "<TABLE BORDER=\"" + border +
                "\" WIDTH=\"" + width +
                "\" CELLPADDING=\"" + cellpadding +
                "\" CELLSPACING=\"" + cellspacing +
                "\" SUMMARY=\"" + summary + "\">");
    }

    /**
     * Print HTML &lt;TABLE BORDER="border" CELLPADDING="cellpadding"
     * CELLSPACING="cellspacing"&gt; tag.
     *
     * @param border       Border size.
     * @param cellpadding  Cellpadding for the table cells.
     * @param cellspacing  Cellspacing for the table cells.
     */
    public void table(int border, int cellpadding, int cellspacing) {
        println(DocletConstants.NL +
                "<TABLE BORDER=\"" + border +
                "\" CELLPADDING=\"" + cellpadding +
                "\" CELLSPACING=\"" + cellspacing +
                "\" SUMMARY=\"\">");
    }

    /**
     * Print HTML &lt;TABLE BORDER="border" CELLPADDING="cellpadding"
     * CELLSPACING="cellspacing" SUMMARY="summary"&gt; tag.
     *
     * @param border       Border size.
     * @param cellpadding  Cellpadding for the table cells.
     * @param cellspacing  Cellspacing for the table cells.
     * @param summary      Table summary.
     */
    public void table(int border, int cellpadding, int cellspacing, String summary) {
        println(DocletConstants.NL +
                "<TABLE BORDER=\"" + border +
                "\" CELLPADDING=\"" + cellpadding +
                "\" CELLSPACING=\"" + cellspacing +
                "\" SUMMARY=\"" + summary + "\">");
    }

    /**
     * Print HTML &lt;TABLE BORDER="border" WIDTH="width"&gt;
     *
     * @param border       Border size.
     * @param width        Width of the table.
     */
    public void table(int border, String width) {
        println(DocletConstants.NL +
                "<TABLE BORDER=\"" + border +
                "\" WIDTH=\"" + width +
                "\" SUMMARY=\"\">");
    }

    /**
     * Print the HTML table tag with border size 0 and width 100%.
     */
    public void table() {
        table(0, "100%");
    }

    /**
     * Print &lt;/TABLE&gt; tag. Add a newline character at the end.
     */
    public void tableEnd() {
        println("</TABLE>");
    }

    /**
     * Print &lt;TR&gt; tag. Add a newline character at the end.
     */
    public void tr() {
        println("<TR>");
    }

    /**
     * Print &lt;/TR&gt; tag. Add a newline character at the end.
     */
    public void trEnd() {
        println("</TR>");
    }

    /**
     * Print &lt;TD&gt; tag.
     */
    public void td() {
        print("<TD>");
    }

    /**
     * Print &lt;TD NOWRAP&gt; tag.
     */
    public void tdNowrap() {
        print("<TD NOWRAP>");
    }

    /**
     * Print &lt;TD WIDTH="width"&gt; tag.
     *
     * @param width String width.
     */
    public void tdWidth(String width) {
        print("<TD WIDTH=\"" + width + "\">");
    }

    /**
     * Print &lt;/TD&gt; tag. Add a newline character at the end.
     */
    public void tdEnd() {
        println("</TD>");
    }

    /**
     * Print &lt;LINK str&gt; tag.
     *
     * @param str String.
     */
    public void link(String str) {
        println("<LINK " + str + ">");
    }

    /**
     * Print "&lt;!-- " comment start string.
     */
    public void commentStart() {
         print("<!-- ");
    }

    /**
     * Print "--&gt;" comment end string. Add a newline character at the end.
     */
    public void commentEnd() {
         println("-->");
    }

    /**
     * Print &lt;CAPTION CLASS="stylename"&gt; tag. Adds a newline character
     * at the end.
     *
     * @param stylename style to be applied.
     */
    public void captionStyle(String stylename) {
        println("<CAPTION CLASS=\"" + stylename + "\">");
    }

    /**
     * Print &lt;/CAPTION&gt; tag. Add a newline character at the end.
     */
    public void captionEnd() {
        println("</CAPTION>");
    }

    /**
     * Print &lt;TR BGCOLOR="color" CLASS="stylename"&gt; tag. Adds a newline character
     * at the end.
     *
     * @param color String color.
     * @param stylename String stylename.
     */
    public void trBgcolorStyle(String color, String stylename) {
        println("<TR BGCOLOR=\"" + color + "\" CLASS=\"" + stylename + "\">");
    }

    /**
     * Print &lt;TR BGCOLOR="color"&gt; tag. Adds a newline character at the end.
     *
     * @param color String color.
     */
    public void trBgcolor(String color) {
        println("<TR BGCOLOR=\"" + color + "\">");
    }

    /**
     * Print &lt;TR ALIGN="align" VALIGN="valign"&gt; tag. Adds a newline character
     * at the end.
     *
     * @param align String align.
     * @param valign String valign.
     */
    public void trAlignVAlign(String align, String valign) {
        println("<TR ALIGN=\"" + align + "\" VALIGN=\"" + valign + "\">");
    }

    /**
     * Print &lt;TH ALIGN="align"&gt; tag.
     *
     * @param align the align attribute.
     */
    public void thAlign(String align) {
        print("<TH ALIGN=\"" + align + "\">");
    }

    /**
     * Print &lt;TH CLASS="stylename" SCOPE="scope" NOWRAP&gt; tag.
     *
     * @param stylename style to be applied.
     * @param scope the scope attribute.
     */
    public void thScopeNoWrap(String stylename, String scope) {
        print("<TH CLASS=\"" + stylename + "\" SCOPE=\"" + scope + "\" NOWRAP>");
    }

    /*
     * Returns a header for Modifier and Type column of a table.
     */
    public String getModifierTypeHeader() {
        return modifierTypeHeader;
    }

    /**
     * Print &lt;TH align="align" COLSPAN=i&gt; tag.
     *
     * @param align the align attribute.
     * @param i integer.
     */
    public void thAlignColspan(String align, int i) {
        print("<TH ALIGN=\"" + align + "\" COLSPAN=\"" + i + "\">");
    }

    /**
     * Print &lt;TH align="align" NOWRAP&gt; tag.
     *
     * @param align the align attribute.
     */
    public void thAlignNowrap(String align) {
        print("<TH ALIGN=\"" + align + "\" NOWRAP>");
    }

    /**
     * Print &lt;/TH&gt; tag. Add a newline character at the end.
     */
    public void thEnd() {
        println("</TH>");
    }

    /**
     * Print &lt;TD COLSPAN=i&gt; tag.
     *
     * @param i integer.
     */
    public void tdColspan(int i) {
        print("<TD COLSPAN=" + i + ">");
    }

    /**
     * Print &lt;TD BGCOLOR="color" CLASS="stylename"&gt; tag.
     *
     * @param color String color.
     * @param stylename String stylename.
     */
    public void tdBgcolorStyle(String color, String stylename) {
        print("<TD BGCOLOR=\"" + color + "\" CLASS=\"" + stylename + "\">");
    }

    /**
     * Print &lt;TD COLSPAN=i BGCOLOR="color" CLASS="stylename"&gt; tag.
     *
     * @param i integer.
     * @param color String color.
     * @param stylename String stylename.
     */
    public void tdColspanBgcolorStyle(int i, String color, String stylename) {
        print("<TD COLSPAN=" + i + " BGCOLOR=\"" + color + "\" CLASS=\"" +
              stylename + "\">");
    }

    /**
     * Print &lt;TD ALIGN="align"&gt; tag. Adds a newline character
     * at the end.
     *
     * @param align String align.
     */
    public void tdAlign(String align) {
        print("<TD ALIGN=\"" + align + "\">");
    }

    /**
     * Print &lt;TD ALIGN="align" CLASS="stylename"&gt; tag.
     *
     * @param align        String align.
     * @param stylename    String stylename.
     */
    public void tdVAlignClass(String align, String stylename) {
        print("<TD VALIGN=\"" + align + "\" CLASS=\"" + stylename + "\">");
    }

    /**
     * Print &lt;TD VALIGN="valign"&gt; tag.
     *
     * @param valign String valign.
     */
    public void tdVAlign(String valign) {
        print("<TD VALIGN=\"" + valign + "\">");
    }

    /**
     * Print &lt;TD ALIGN="align" VALIGN="valign"&gt; tag.
     *
     * @param align   String align.
     * @param valign  String valign.
     */
    public void tdAlignVAlign(String align, String valign) {
        print("<TD ALIGN=\"" + align + "\" VALIGN=\"" + valign + "\">");
    }

    /**
     * Print &lt;TD ALIGN="align" ROWSPAN=rowspan&gt; tag.
     *
     * @param align    String align.
     * @param rowspan  integer rowspan.
     */
    public void tdAlignRowspan(String align, int rowspan) {
        print("<TD ALIGN=\"" + align + "\" ROWSPAN=" + rowspan + ">");
    }

    /**
     * Print &lt;TD ALIGN="align" VALIGN="valign" ROWSPAN=rowspan&gt; tag.
     *
     * @param align    String align.
     * @param valign  String valign.
     * @param rowspan  integer rowspan.
     */
    public void tdAlignVAlignRowspan(String align, String valign,
                                     int rowspan) {
        print("<TD ALIGN=\"" + align + "\" VALIGN=\"" + valign
                + "\" ROWSPAN=" + rowspan + ">");
    }

    /**
     * Print &lt;BLOCKQUOTE&gt; tag. Add a newline character at the end.
     */
    public void blockquote() {
        println("<BLOCKQUOTE>");
    }

    /**
     * Print &lt;/BLOCKQUOTE&gt; tag. Add a newline character at the end.
     */
    public void blockquoteEnd() {
        println("</BLOCKQUOTE>");
    }

    /**
     * Get the "&lt;CODE&gt;" string.
     *
     * @return String Return String "&lt;CODE>";
     */
    public String getCode() {
        return "<CODE>";
    }

    /**
     * Get the "&lt;/CODE&gt;" string.
     *
     * @return String Return String "&lt;/CODE&gt;";
     */
    public String getCodeEnd() {
        return "</CODE>";
    }

    /**
     * Print &lt;NOFRAMES&gt; tag. Add a newline character at the end.
     */
    public void noFrames() {
        println("<NOFRAMES>");
    }

    /**
     * Print &lt;/NOFRAMES&gt; tag. Add a newline character at the end.
     */
    public void noFramesEnd() {
        println("</NOFRAMES>");
    }
}
