/*
 * Copyright (c) 1998, 2011, Oracle and/or its affiliates. All rights reserved.
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
import java.text.SimpleDateFormat;
import java.util.*;

import com.sun.javadoc.*;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.tools.doclets.internal.toolkit.taglets.*;

/**
 * Class for the Html Format Code Generation specific to JavaDoc.
 * This Class contains methods related to the Html Code Generation which
 * are used extensively while generating the entire documentation.
 *
 * @since 1.2
 * @author Atul M Dambalkar
 * @author Robert Field
 * @author Bhavesh Patel (Modified)
 */
public class HtmlDocletWriter extends HtmlDocWriter {

    /**
     * Relative path from the file getting generated to the destination
     * directory. For example, if the file getting generated is
     * "java/lang/Object.html", then the relative path string is "../../".
     * This string can be empty if the file getting generated is in
     * the destination directory.
     */
    public String relativePath = "";

    /**
     * Same as relativepath, but normalized to never be empty or
     * end with a slash.
     */
    public String relativepathNoSlash = "";

    /**
     * Platform-dependent directory path from the current or the
     * destination directory to the file getting generated.
     * Used when creating the file.
     * For example, if the file getting generated is
     * "java/lang/Object.html", then the path string is "java/lang".
     */
    public String path = "";

    /**
     * Name of the file getting generated. If the file getting generated is
     * "java/lang/Object.html", then the filename is "Object.html".
     */
    public String filename = "";

    /**
     * The display length used for indentation while generating the class page.
     */
    public int displayLength = 0;

    /**
     * The global configuration information for this run.
     */
    public ConfigurationImpl configuration;

    /**
     * To check whether annotation heading is printed or not.
     */
    protected boolean printedAnnotationHeading = false;

    /**
     * Constructor to construct the HtmlStandardWriter object.
     *
     * @param filename File to be generated.
     */
    public HtmlDocletWriter(ConfigurationImpl configuration,
                              String filename) throws IOException {
        super(configuration, filename);
        this.configuration = configuration;
        this.filename = filename;
    }

    /**
     * Constructor to construct the HtmlStandardWriter object.
     *
     * @param path         Platform-dependent {@link #path} used when
     *                     creating file.
     * @param filename     Name of file to be generated.
     * @param relativePath Value for the variable {@link #relativePath}.
     */
    public HtmlDocletWriter(ConfigurationImpl configuration,
                              String path, String filename,
                              String relativePath) throws IOException {
        super(configuration, path, filename);
        this.configuration = configuration;
        this.path = path;
        this.relativePath = relativePath;
        this.relativepathNoSlash =
            DirectoryManager.getPathNoTrailingSlash(this.relativePath);
        this.filename = filename;
    }

    /**
     * Replace {&#064;docRoot} tag used in options that accept HTML text, such
     * as -header, -footer, -top and -bottom, and when converting a relative
     * HREF where commentTagsToString inserts a {&#064;docRoot} where one was
     * missing.  (Also see DocRootTaglet for {&#064;docRoot} tags in doc
     * comments.)
     * <p>
     * Replace {&#064;docRoot} tag in htmlstr with the relative path to the
     * destination directory from the directory where the file is being
     * written, looping to handle all such tags in htmlstr.
     * <p>
     * For example, for "-d docs" and -header containing {&#064;docRoot}, when
     * the HTML page for source file p/C1.java is being generated, the
     * {&#064;docRoot} tag would be inserted into the header as "../",
     * the relative path from docs/p/ to docs/ (the document root).
     * <p>
     * Note: This doc comment was written with '&amp;#064;' representing '@'
     * to prevent the inline tag from being interpreted.
     */
    public String replaceDocRootDir(String htmlstr) {
        // Return if no inline tags exist
        int index = htmlstr.indexOf("{@");
        if (index < 0) {
            return htmlstr;
        }
        String lowerHtml = htmlstr.toLowerCase();
        // Return index of first occurrence of {@docroot}
        // Note: {@docRoot} is not case sensitive when passed in w/command line option
        index = lowerHtml.indexOf("{@docroot}", index);
        if (index < 0) {
            return htmlstr;
        }
        StringBuilder buf = new StringBuilder();
        int previndex = 0;
        while (true) {
            if (configuration.docrootparent.length() > 0) {
                // Search for lowercase version of {@docRoot}/..
                index = lowerHtml.indexOf("{@docroot}/..", previndex);
                // If next {@docRoot}/.. pattern not found, append rest of htmlstr and exit loop
                if (index < 0) {
                    buf.append(htmlstr.substring(previndex));
                    break;
                }
                // If next {@docroot}/.. pattern found, append htmlstr up to start of tag
                buf.append(htmlstr.substring(previndex, index));
                previndex = index + 13;  // length for {@docroot}/.. string
                // Insert docrootparent absolute path where {@docRoot}/.. was located

                buf.append(configuration.docrootparent);
                // Append slash if next character is not a slash
                if (previndex < htmlstr.length() && htmlstr.charAt(previndex) != '/') {
                    buf.append(DirectoryManager.URL_FILE_SEPARATOR);
                }
            } else {
                // Search for lowercase version of {@docRoot}
                index = lowerHtml.indexOf("{@docroot}", previndex);
                // If next {@docRoot} tag not found, append rest of htmlstr and exit loop
                if (index < 0) {
                    buf.append(htmlstr.substring(previndex));
                    break;
                }
                // If next {@docroot} tag found, append htmlstr up to start of tag
                buf.append(htmlstr.substring(previndex, index));
                previndex = index + 10;  // length for {@docroot} string
                // Insert relative path where {@docRoot} was located
                buf.append(relativepathNoSlash);
                // Append slash if next character is not a slash
                if (relativepathNoSlash.length() > 0 && previndex < htmlstr.length() &&
                        htmlstr.charAt(previndex) != '/') {
                    buf.append(DirectoryManager.URL_FILE_SEPARATOR);
                }
            }
        }
        return buf.toString();
    }

    /**
     * Print Html Hyper Link, with target frame.  This
     * link will only appear if page is not in a frame.
     *
     * @param link String name of the file.
     * @param where Position in the file
     * @param target Name of the target frame.
     * @param label Tag for the link.
     * @param strong Whether the label should be strong or not?
     */
    public void printNoFramesTargetHyperLink(String link, String where,
                                               String target, String label,
                                               boolean strong) {
        script();
        println("  <!--");
        println("  if(window==top) {");
        println("    document.writeln('"
            + getHyperLinkString(link, where, label, strong, "", "", target) + "');");
        println("  }");
        println("  //-->");
        scriptEnd();
        noScript();
        println("  " + getHyperLinkString(link, where, label, strong, "", "", target));
        noScriptEnd();
        println(DocletConstants.NL);
    }

    /**
     * Get the script to show or hide the All classes link.
     *
     * @param id id of the element to show or hide
     * @return a content tree for the script
     */
    public Content getAllClassesLinkScript(String id) {
        HtmlTree script = new HtmlTree(HtmlTag.SCRIPT);
        script.addAttr(HtmlAttr.TYPE, "text/javascript");
        String scriptCode = "<!--" + DocletConstants.NL +
                "  allClassesLink = document.getElementById(\"" + id + "\");" + DocletConstants.NL +
                "  if(window==top) {" + DocletConstants.NL +
                "    allClassesLink.style.display = \"block\";" + DocletConstants.NL +
                "  }" + DocletConstants.NL +
                "  else {" + DocletConstants.NL +
                "    allClassesLink.style.display = \"none\";" + DocletConstants.NL +
                "  }" + DocletConstants.NL +
                "  //-->" + DocletConstants.NL;
        Content scriptContent = new RawHtml(scriptCode);
        script.addContent(scriptContent);
        Content div = HtmlTree.DIV(script);
        return div;
    }

    /**
     * Add method information.
     *
     * @param method the method to be documented
     * @param dl the content tree to which the method information will be added
     */
    private void addMethodInfo(MethodDoc method, Content dl) {
        ClassDoc[] intfacs = method.containingClass().interfaces();
        MethodDoc overriddenMethod = method.overriddenMethod();
        // Check whether there is any implementation or overridden info to be
        // printed. If no overridden or implementation info needs to be
        // printed, do not print this section.
        if ((intfacs.length > 0 &&
                new ImplementedMethods(method, this.configuration).build().length > 0) ||
                overriddenMethod != null) {
            MethodWriterImpl.addImplementsInfo(this, method, dl);
            if (overriddenMethod != null) {
                MethodWriterImpl.addOverridden(this,
                        method.overriddenType(), overriddenMethod, dl);
            }
        }
    }

    /**
     * Adds the tags information.
     *
     * @param doc the doc for which the tags will be generated
     * @param htmltree the documentation tree to which the tags will be added
     */
    protected void addTagsInfo(Doc doc, Content htmltree) {
        if (configuration.nocomment) {
            return;
        }
        Content dl = new HtmlTree(HtmlTag.DL);
        if (doc instanceof MethodDoc) {
            addMethodInfo((MethodDoc) doc, dl);
        }
        TagletOutputImpl output = new TagletOutputImpl("");
        TagletWriter.genTagOuput(configuration.tagletManager, doc,
            configuration.tagletManager.getCustomTags(doc),
                getTagletWriterInstance(false), output);
        String outputString = output.toString().trim();
        if (!outputString.isEmpty()) {
            Content resultString = new RawHtml(outputString);
            dl.addContent(resultString);
        }
        htmltree.addContent(dl);
    }

    /**
     * Check whether there are any tags for Serialization Overview
     * section to be printed.
     *
     * @param field the FieldDoc object to check for tags.
     * @return true if there are tags to be printed else return false.
     */
    protected boolean hasSerializationOverviewTags(FieldDoc field) {
        TagletOutputImpl output = new TagletOutputImpl("");
        TagletWriter.genTagOuput(configuration.tagletManager, field,
            configuration.tagletManager.getCustomTags(field),
                getTagletWriterInstance(false), output);
        return (!output.toString().trim().isEmpty());
    }

    /**
     * Returns a TagletWriter that knows how to write HTML.
     *
     * @return a TagletWriter that knows how to write HTML.
     */
    public TagletWriter getTagletWriterInstance(boolean isFirstSentence) {
        return new TagletWriterImpl(this, isFirstSentence);
    }

    protected void printTagsInfoHeader() {
        dl();
    }

    protected void printTagsInfoFooter() {
        dlEnd();
    }

    /**
     * Get Package link, with target frame.
     *
     * @param pd The link will be to the "package-summary.html" page for this package
     * @param target name of the target frame
     * @param label tag for the link
     * @return a content for the target package link
     */
    public Content getTargetPackageLink(PackageDoc pd, String target,
            Content label) {
        return getHyperLink(pathString(pd, "package-summary.html"), "", label, "", target);
    }

    /**
     * Print the html file header. Also print Html page title and stylesheet
     * default properties.
     *
     * @param title         String window title to go in the &lt;TITLE&gt; tag
     * @param metakeywords  Array of String keywords for META tag.  Each element
     *                      of the array is assigned to a separate META tag.
     *                      Pass in null for no array.
     * @param includeScript boolean true if printing windowtitle script.
     *             False for files that appear in the left-hand frames.
     */
    public void printHtmlHeader(String title, String[] metakeywords,
            boolean includeScript) {
        println("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 " +
                    "Transitional//EN\" " +
                    "\"http://www.w3.org/TR/html4/loose.dtd\">");
        println("<!--NewPage-->");
        html();
        head();
        if (! configuration.notimestamp) {
            print("<!-- Generated by javadoc (build " + ConfigurationImpl.BUILD_DATE + ") on ");
            print(today());
            println(" -->");
        }
        if (configuration.charset.length() > 0) {
            println("<META http-equiv=\"Content-Type\" content=\"text/html; "
                        + "charset=" + configuration.charset + "\">");
        }
        if ( configuration.windowtitle.length() > 0 ) {
            title += " (" + configuration.windowtitle  + ")";
        }
        title(title);
        println(title);
        titleEnd();
        println("");
        if (! configuration.notimestamp) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                println("<META NAME=\"date\" "
                                    + "CONTENT=\"" + dateFormat.format(new Date()) + "\">");
        }
        if ( metakeywords != null ) {
            for ( int i=0; i < metakeywords.length; i++ ) {
                println("<META NAME=\"keywords\" "
                            + "CONTENT=\"" + metakeywords[i] + "\">");
            }
        }
        println("");
        printStyleSheetProperties();
        println("");
        // Don't print windowtitle script for overview-frame, allclasses-frame
        // and package-frame
        if (includeScript) {
            printWinTitleScript(title);
        }
        println("");
        headEnd();
        println("");
        body("white", includeScript);
    }

    /**
     * Generates the HTML document tree and prints it out.
     *
     * @param metakeywords Array of String keywords for META tag. Each element
     *                     of the array is assigned to a separate META tag.
     *                     Pass in null for no array
     * @param includeScript true if printing windowtitle script
     *                      false for files that appear in the left-hand frames
     * @param body the body htmltree to be included in the document
     */
    public void printHtmlDocument(String[] metakeywords, boolean includeScript,
            Content body) {
        Content htmlDocType = DocType.Transitional();
        Content htmlComment = new Comment(configuration.getText("doclet.New_Page"));
        Content head = new HtmlTree(HtmlTag.HEAD);
        if (!configuration.notimestamp) {
            Content headComment = new Comment("Generated by javadoc (version " +
                    ConfigurationImpl.BUILD_DATE + ") on " + today());
            head.addContent(headComment);
        }
        if (configuration.charset.length() > 0) {
            Content meta = HtmlTree.META("Content-Type", "text/html",
                    configuration.charset);
            head.addContent(meta);
        }
        head.addContent(getTitle());
        if (!configuration.notimestamp) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            Content meta = HtmlTree.META("date", dateFormat.format(new Date()));
            head.addContent(meta);
        }
        if (metakeywords != null) {
            for (int i=0; i < metakeywords.length; i++) {
                Content meta = HtmlTree.META("keywords", metakeywords[i]);
                head.addContent(meta);
            }
        }
        head.addContent(getStyleSheetProperties());
        Content htmlTree = HtmlTree.HTML(configuration.getLocale().getLanguage(),
                head, body);
        Content htmlDocument = new HtmlDocument(htmlDocType,
                htmlComment, htmlTree);
        print(htmlDocument.toString());
    }

    /**
     * Get the window title.
     *
     * @param title the title string to construct the complete window title
     * @return the window title string
     */
    public String getWindowTitle(String title) {
        if (configuration.windowtitle.length() > 0) {
            title += " (" + configuration.windowtitle  + ")";
        }
        return title;
    }

    /**
     * Print user specified header and the footer.
     *
     * @param header if true print the user provided header else print the
     * user provided footer.
     */
    public void printUserHeaderFooter(boolean header) {
        em();
        if (header) {
            print(replaceDocRootDir(configuration.header));
        } else {
            if (configuration.footer.length() != 0) {
                print(replaceDocRootDir(configuration.footer));
            } else {
                print(replaceDocRootDir(configuration.header));
            }
        }
        emEnd();
    }

    /**
     * Get user specified header and the footer.
     *
     * @param header if true print the user provided header else print the
     * user provided footer.
     */
    public Content getUserHeaderFooter(boolean header) {
        String content;
        if (header) {
            content = replaceDocRootDir(configuration.header);
        } else {
            if (configuration.footer.length() != 0) {
                content = replaceDocRootDir(configuration.footer);
            } else {
                content = replaceDocRootDir(configuration.header);
            }
        }
        Content rawContent = new RawHtml(content);
        Content em = HtmlTree.EM(rawContent);
        return em;
    }

    /**
     * Print the user specified top.
     */
    public void printTop() {
        print(replaceDocRootDir(configuration.top));
        hr();
    }

    /**
     * Adds the user specified top.
     *
     * @param body the content tree to which user specified top will be added
     */
    public void addTop(Content body) {
        Content top = new RawHtml(replaceDocRootDir(configuration.top));
        body.addContent(top);
    }

    /**
     * Print the user specified bottom.
     */
    public void printBottom() {
        hr();
        print(replaceDocRootDir(configuration.bottom));
    }

    /**
     * Adds the user specified bottom.
     *
     * @param body the content tree to which user specified bottom will be added
     */
    public void addBottom(Content body) {
        Content bottom = new RawHtml(replaceDocRootDir(configuration.bottom));
        Content small = HtmlTree.SMALL(bottom);
        Content p = HtmlTree.P(HtmlStyle.legalCopy, small);
        body.addContent(p);
    }

    /**
     * Print the navigation bar for the Html page at the top and and the bottom.
     *
     * @param header If true print navigation bar at the top of the page else
     * print the nevigation bar at the bottom.
     */
    protected void navLinks(boolean header) {
        println("");
        if (!configuration.nonavbar) {
            if (header) {
                println(DocletConstants.NL + "<!-- ========= START OF TOP NAVBAR ======= -->");
                anchor("navbar_top");
                println();
                print(getHyperLinkString("", "skip-navbar_top", "", false, "",
                    configuration.getText("doclet.Skip_navigation_links"), ""));
            } else {
                println(DocletConstants.NL + "<!-- ======= START OF BOTTOM NAVBAR ====== -->");
                anchor("navbar_bottom");
                println();
                print(getHyperLinkString("", "skip-navbar_bottom", "", false, "",
                    configuration.getText("doclet.Skip_navigation_links"), ""));
            }
            table(0, "100%", 1, 0);
            tr();
            tdColspanBgcolorStyle(2, "#EEEEFF", "NavBarCell1");
            println("");
            if (header) {
                anchor("navbar_top_firstrow");
            } else {
                anchor("navbar_bottom_firstrow");
            }
            table(0, 0, 3);
            print("  ");
            trAlignVAlign("center", "top");

            if (configuration.createoverview) {
                navLinkContents();
            }

            if (configuration.packages.length == 1) {
                navLinkPackage(configuration.packages[0]);
            } else if (configuration.packages.length > 1) {
                navLinkPackage();
            }

            navLinkClass();

            if(configuration.classuse) {
                navLinkClassUse();
            }
            if(configuration.createtree) {
                navLinkTree();
            }
            if(!(configuration.nodeprecated ||
                     configuration.nodeprecatedlist)) {
                navLinkDeprecated();
            }
            if(configuration.createindex) {
                navLinkIndex();
            }
            if (!configuration.nohelp) {
                navLinkHelp();
            }
            print("  ");
            trEnd();
            tableEnd();
            tdEnd();

            tdAlignVAlignRowspan("right", "top", 3);

            printUserHeaderFooter(header);
            tdEnd();
            trEnd();
            println("");

            tr();
            tdBgcolorStyle("white", "NavBarCell2");
            font("-2");
            space();
            navLinkPrevious();
            space();
            println("");
            space();
            navLinkNext();
            fontEnd();
            tdEnd();

            tdBgcolorStyle("white", "NavBarCell2");
            font("-2");
            print("  ");
            navShowLists();
            print("  ");
            space();
            println("");
            space();
            navHideLists(filename);
            print("  ");
            space();
            println("");
            space();
            navLinkClassIndex();
            fontEnd();
            tdEnd();

            trEnd();

            printSummaryDetailLinks();

            tableEnd();
            if (header) {
                aName("skip-navbar_top");
                aEnd();
                println(DocletConstants.NL + "<!-- ========= END OF TOP NAVBAR ========= -->");
            } else {
                aName("skip-navbar_bottom");
                aEnd();
                println(DocletConstants.NL + "<!-- ======== END OF BOTTOM NAVBAR ======= -->");
            }
            println("");
        }
    }

    /**
     * Adds the navigation bar for the Html page at the top and and the bottom.
     *
     * @param header If true print navigation bar at the top of the page else
     * @param body the HtmlTree to which the nav links will be added
     */
    protected void addNavLinks(boolean header, Content body) {
        if (!configuration.nonavbar) {
            String allClassesId = "allclasses_";
            HtmlTree navDiv = new HtmlTree(HtmlTag.DIV);
            if (header) {
                body.addContent(HtmlConstants.START_OF_TOP_NAVBAR);
                navDiv.addStyle(HtmlStyle.topNav);
                allClassesId += "navbar_top";
                Content a = getMarkerAnchor("navbar_top");
                navDiv.addContent(a);
                Content skipLinkContent = getHyperLink("",
                        "skip-navbar_top", HtmlTree.EMPTY, configuration.getText(
                        "doclet.Skip_navigation_links"), "");
                navDiv.addContent(skipLinkContent);
            } else {
                body.addContent(HtmlConstants.START_OF_BOTTOM_NAVBAR);
                navDiv.addStyle(HtmlStyle.bottomNav);
                allClassesId += "navbar_bottom";
                Content a = getMarkerAnchor("navbar_bottom");
                navDiv.addContent(a);
                Content skipLinkContent = getHyperLink("",
                        "skip-navbar_bottom", HtmlTree.EMPTY, configuration.getText(
                        "doclet.Skip_navigation_links"), "");
                navDiv.addContent(skipLinkContent);
            }
            if (header) {
                navDiv.addContent(getMarkerAnchor("navbar_top_firstrow"));
            } else {
                navDiv.addContent(getMarkerAnchor("navbar_bottom_firstrow"));
            }
            HtmlTree navList = new HtmlTree(HtmlTag.UL);
            navList.addStyle(HtmlStyle.navList);
            navList.addAttr(HtmlAttr.TITLE, "Navigation");
            if (configuration.createoverview) {
                navList.addContent(getNavLinkContents());
            }
            if (configuration.packages.length == 1) {
                navList.addContent(getNavLinkPackage(configuration.packages[0]));
            } else if (configuration.packages.length > 1) {
                navList.addContent(getNavLinkPackage());
            }
            navList.addContent(getNavLinkClass());
            if(configuration.classuse) {
                navList.addContent(getNavLinkClassUse());
            }
            if(configuration.createtree) {
                navList.addContent(getNavLinkTree());
            }
            if(!(configuration.nodeprecated ||
                     configuration.nodeprecatedlist)) {
                navList.addContent(getNavLinkDeprecated());
            }
            if(configuration.createindex) {
                navList.addContent(getNavLinkIndex());
            }
            if (!configuration.nohelp) {
                navList.addContent(getNavLinkHelp());
            }
            navDiv.addContent(navList);
            Content aboutDiv = HtmlTree.DIV(HtmlStyle.aboutLanguage, getUserHeaderFooter(header));
            navDiv.addContent(aboutDiv);
            body.addContent(navDiv);
            Content ulNav = HtmlTree.UL(HtmlStyle.navList, getNavLinkPrevious());
            ulNav.addContent(getNavLinkNext());
            Content subDiv = HtmlTree.DIV(HtmlStyle.subNav, ulNav);
            Content ulFrames = HtmlTree.UL(HtmlStyle.navList, getNavShowLists());
            ulFrames.addContent(getNavHideLists(filename));
            subDiv.addContent(ulFrames);
            HtmlTree ulAllClasses = HtmlTree.UL(HtmlStyle.navList, getNavLinkClassIndex());
            ulAllClasses.addAttr(HtmlAttr.ID, allClassesId.toString());
            subDiv.addContent(ulAllClasses);
            subDiv.addContent(getAllClassesLinkScript(allClassesId.toString()));
            addSummaryDetailLinks(subDiv);
            if (header) {
                subDiv.addContent(getMarkerAnchor("skip-navbar_top"));
                body.addContent(subDiv);
                body.addContent(HtmlConstants.END_OF_TOP_NAVBAR);
            } else {
                subDiv.addContent(getMarkerAnchor("skip-navbar_bottom"));
                body.addContent(subDiv);
                body.addContent(HtmlConstants.END_OF_BOTTOM_NAVBAR);
            }
        }
    }

    /**
     * Print the word "NEXT" to indicate that no link is available.  Override
     * this method to customize next link.
     */
    protected void navLinkNext() {
        navLinkNext(null);
    }

    /**
     * Get the word "NEXT" to indicate that no link is available.  Override
     * this method to customize next link.
     *
     * @return a content tree for the link
     */
    protected Content getNavLinkNext() {
        return getNavLinkNext(null);
    }

    /**
     * Print the word "PREV" to indicate that no link is available.  Override
     * this method to customize prev link.
     */
    protected void navLinkPrevious() {
        navLinkPrevious(null);
    }

    /**
     * Get the word "PREV" to indicate that no link is available.  Override
     * this method to customize prev link.
     *
     * @return a content tree for the link
     */
    protected Content getNavLinkPrevious() {
        return getNavLinkPrevious(null);
    }

    /**
     * Do nothing. This is the default method.
     */
    protected void printSummaryDetailLinks() {
    }

    /**
     * Do nothing. This is the default method.
     */
    protected void addSummaryDetailLinks(Content navDiv) {
    }

    /**
     * Print link to the "overview-summary.html" page.
     */
    protected void navLinkContents() {
        navCellStart();
        printHyperLink(relativePath + "overview-summary.html", "",
                       configuration.getText("doclet.Overview"), true, "NavBarFont1");
        navCellEnd();
    }

    /**
     * Get link to the "overview-summary.html" page.
     *
     * @return a content tree for the link
     */
    protected Content getNavLinkContents() {
        Content linkContent = getHyperLink(relativePath +
                "overview-summary.html", "", overviewLabel, "", "");
        Content li = HtmlTree.LI(linkContent);
        return li;
    }

    /**
     * Description for a cell in the navigation bar.
     */
    protected void navCellStart() {
        print("  ");
        tdBgcolorStyle("#EEEEFF", "NavBarCell1");
        print("    ");
    }

    /**
     * Description for a cell in the navigation bar, but with reverse
     * high-light effect.
     */
    protected void navCellRevStart() {
        print("  ");
        tdBgcolorStyle("#FFFFFF", "NavBarCell1Rev");
        print(" ");
        space();
    }

    /**
     * Closing tag for navigation bar cell.
     */
    protected void navCellEnd() {
        space();
        tdEnd();
    }

    /**
     * Print link to the "package-summary.html" page for the package passed.
     *
     * @param pkg Package to which link will be generated.
     */
    protected void navLinkPackage(PackageDoc pkg) {
        navCellStart();
        printPackageLink(pkg, configuration.getText("doclet.Package"), true,
            "NavBarFont1");
        navCellEnd();
    }

    /**
     * Get link to the "package-summary.html" page for the package passed.
     *
     * @param pkg Package to which link will be generated
     * @return a content tree for the link
     */
    protected Content getNavLinkPackage(PackageDoc pkg) {
        Content linkContent = getPackageLink(pkg,
                packageLabel);
        Content li = HtmlTree.LI(linkContent);
        return li;
    }

    /**
     * Print the word "Package" in the navigation bar cell, to indicate that
     * link is not available here.
     */
    protected void navLinkPackage() {
        navCellStart();
        fontStyle("NavBarFont1");
        printText("doclet.Package");
        fontEnd();
        navCellEnd();
    }

    /**
     * Get the word "Package" , to indicate that link is not available here.
     *
     * @return a content tree for the link
     */
    protected Content getNavLinkPackage() {
        Content li = HtmlTree.LI(packageLabel);
        return li;
    }

    /**
     * Print the word "Use" in the navigation bar cell, to indicate that link
     * is not available.
     */
    protected void navLinkClassUse() {
        navCellStart();
        fontStyle("NavBarFont1");
        printText("doclet.navClassUse");
        fontEnd();
        navCellEnd();
    }

    /**
     * Get the word "Use", to indicate that link is not available.
     *
     * @return a content tree for the link
     */
    protected Content getNavLinkClassUse() {
        Content li = HtmlTree.LI(useLabel);
        return li;
    }

    /**
     * Print link for previous file.
     *
     * @param prev File name for the prev link.
     */
    public void navLinkPrevious(String prev) {
        String tag = configuration.getText("doclet.Prev");
        if (prev != null) {
            printHyperLink(prev, "", tag, true) ;
        } else {
            print(tag);
        }
    }

    /**
     * Get link for previous file.
     *
     * @param prev File name for the prev link
     * @return a content tree for the link
     */
    public Content getNavLinkPrevious(String prev) {
        Content li;
        if (prev != null) {
            li = HtmlTree.LI(getHyperLink(prev, "", prevLabel, "", ""));
        }
        else
            li = HtmlTree.LI(prevLabel);
        return li;
    }

    /**
     * Print link for next file.  If next is null, just print the label
     * without linking it anywhere.
     *
     * @param next File name for the next link.
     */
    public void navLinkNext(String next) {
        String tag = configuration.getText("doclet.Next");
        if (next != null) {
            printHyperLink(next, "", tag, true);
        } else {
            print(tag);
        }
    }

    /**
     * Get link for next file.  If next is null, just print the label
     * without linking it anywhere.
     *
     * @param next File name for the next link
     * @return a content tree for the link
     */
    public Content getNavLinkNext(String next) {
        Content li;
        if (next != null) {
            li = HtmlTree.LI(getHyperLink(next, "", nextLabel, "", ""));
        }
        else
            li = HtmlTree.LI(nextLabel);
        return li;
    }

    /**
     * Print "FRAMES" link, to switch to the frame version of the output.
     *
     * @param link File to be linked, "index.html".
     */
    protected void navShowLists(String link) {
        print(getHyperLinkString(link + "?" + path + filename, "",
            configuration.getText("doclet.FRAMES"), true, "", "", "_top"));
    }

    /**
     * Get "FRAMES" link, to switch to the frame version of the output.
     *
     * @param link File to be linked, "index.html"
     * @return a content tree for the link
     */
    protected Content getNavShowLists(String link) {
        Content framesContent = getHyperLink(link + "?" + path +
                filename, "", framesLabel, "", "_top");
        Content li = HtmlTree.LI(framesContent);
        return li;
    }

    /**
     * Print "FRAMES" link, to switch to the frame version of the output.
     */
    protected void navShowLists() {
        navShowLists(relativePath + "index.html");
    }

    /**
     * Get "FRAMES" link, to switch to the frame version of the output.
     *
     * @return a content tree for the link
     */
    protected Content getNavShowLists() {
        return getNavShowLists(relativePath + "index.html");
    }

    /**
     * Print "NO FRAMES" link, to switch to the non-frame version of the output.
     *
     * @param link File to be linked.
     */
    protected void navHideLists(String link) {
        print(getHyperLinkString(link, "", configuration.getText("doclet.NO_FRAMES"),
            true, "", "", "_top"));
    }

    /**
     * Get "NO FRAMES" link, to switch to the non-frame version of the output.
     *
     * @param link File to be linked
     * @return a content tree for the link
     */
    protected Content getNavHideLists(String link) {
        Content noFramesContent = getHyperLink(link, "", noframesLabel, "", "_top");
        Content li = HtmlTree.LI(noFramesContent);
        return li;
    }

    /**
     * Print "Tree" link in the navigation bar. If there is only one package
     * specified on the command line, then the "Tree" link will be to the
     * only "package-tree.html" file otherwise it will be to the
     * "overview-tree.html" file.
     */
    protected void navLinkTree() {
        navCellStart();
        PackageDoc[] packages = configuration.root.specifiedPackages();
        if (packages.length == 1 && configuration.root.specifiedClasses().length == 0) {
            printHyperLink(pathString(packages[0], "package-tree.html"), "",
                           configuration.getText("doclet.Tree"), true, "NavBarFont1");
        } else {
            printHyperLink(relativePath + "overview-tree.html", "",
                           configuration.getText("doclet.Tree"), true, "NavBarFont1");
        }
        navCellEnd();
    }

    /**
     * Get "Tree" link in the navigation bar. If there is only one package
     * specified on the command line, then the "Tree" link will be to the
     * only "package-tree.html" file otherwise it will be to the
     * "overview-tree.html" file.
     *
     * @return a content tree for the link
     */
    protected Content getNavLinkTree() {
        Content treeLinkContent;
        PackageDoc[] packages = configuration.root.specifiedPackages();
        if (packages.length == 1 && configuration.root.specifiedClasses().length == 0) {
            treeLinkContent = getHyperLink(pathString(packages[0],
                    "package-tree.html"), "", treeLabel,
                    "", "");
        } else {
            treeLinkContent = getHyperLink(relativePath + "overview-tree.html",
                    "", treeLabel, "", "");
        }
        Content li = HtmlTree.LI(treeLinkContent);
        return li;
    }

    /**
     * Get the overview tree link for the main tree.
     *
     * @param label the label for the link
     * @return a content tree for the link
     */
    protected Content getNavLinkMainTree(String label) {
        Content mainTreeContent = getHyperLink(relativePath + "overview-tree.html",
                new StringContent(label));
        Content li = HtmlTree.LI(mainTreeContent);
        return li;
    }

    /**
     * Print the word "Class" in the navigation bar cell, to indicate that
     * class link is not available.
     */
    protected void navLinkClass() {
        navCellStart();
        fontStyle("NavBarFont1");
        printText("doclet.Class");
        fontEnd();
        navCellEnd();
    }

    /**
     * Get the word "Class", to indicate that class link is not available.
     *
     * @return a content tree for the link
     */
    protected Content getNavLinkClass() {
        Content li = HtmlTree.LI(classLabel);
        return li;
    }

    /**
     * Print "Deprecated" API link in the navigation bar.
     */
    protected void navLinkDeprecated() {
        navCellStart();
        printHyperLink(relativePath + "deprecated-list.html", "",
                       configuration.getText("doclet.navDeprecated"), true, "NavBarFont1");
        navCellEnd();
    }

    /**
     * Get "Deprecated" API link in the navigation bar.
     *
     * @return a content tree for the link
     */
    protected Content getNavLinkDeprecated() {
        Content linkContent = getHyperLink(relativePath +
                "deprecated-list.html", "", deprecatedLabel, "", "");
        Content li = HtmlTree.LI(linkContent);
        return li;
    }

    /**
     * Print link for generated index. If the user has used "-splitindex"
     * command line option, then link to file "index-files/index-1.html" is
     * generated otherwise link to file "index-all.html" is generated.
     */
    protected void navLinkClassIndex() {
        printNoFramesTargetHyperLink(relativePath +
                AllClassesFrameWriter.OUTPUT_FILE_NAME_NOFRAMES,
            "", "", configuration.getText("doclet.All_Classes"), true);
    }

    /**
     * Get link for generated index. If the user has used "-splitindex"
     * command line option, then link to file "index-files/index-1.html" is
     * generated otherwise link to file "index-all.html" is generated.
     *
     * @return a content tree for the link
     */
    protected Content getNavLinkClassIndex() {
        Content allClassesContent = getHyperLink(relativePath +
                AllClassesFrameWriter.OUTPUT_FILE_NAME_NOFRAMES, "",
                allclassesLabel, "", "");
        Content li = HtmlTree.LI(allClassesContent);
        return li;
    }
    /**
     * Print link for generated class index.
     */
    protected void navLinkIndex() {
        navCellStart();
        printHyperLink(relativePath +
                           (configuration.splitindex?
                                DirectoryManager.getPath("index-files") +
                                fileseparator: "") +
                           (configuration.splitindex?
                                "index-1.html" : "index-all.html"), "",
                       configuration.getText("doclet.Index"), true, "NavBarFont1");
        navCellEnd();
    }

    /**
     * Get link for generated class index.
     *
     * @return a content tree for the link
     */
    protected Content getNavLinkIndex() {
        Content linkContent = getHyperLink(relativePath +(configuration.splitindex?
            DirectoryManager.getPath("index-files") + fileseparator: "") +
            (configuration.splitindex?"index-1.html" : "index-all.html"), "",
            indexLabel, "", "");
        Content li = HtmlTree.LI(linkContent);
        return li;
    }

    /**
     * Print help file link. If user has provided a help file, then generate a
     * link to the user given file, which is already copied to current or
     * destination directory.
     */
    protected void navLinkHelp() {
        String helpfilenm = configuration.helpfile;
        if (helpfilenm.equals("")) {
            helpfilenm = "help-doc.html";
        } else {
            int lastsep;
            if ((lastsep = helpfilenm.lastIndexOf(File.separatorChar)) != -1) {
                helpfilenm = helpfilenm.substring(lastsep + 1);
            }
        }
        navCellStart();
        printHyperLink(relativePath + helpfilenm, "",
                       configuration.getText("doclet.Help"), true, "NavBarFont1");
        navCellEnd();
    }

    /**
     * Get help file link. If user has provided a help file, then generate a
     * link to the user given file, which is already copied to current or
     * destination directory.
     *
     * @return a content tree for the link
     */
    protected Content getNavLinkHelp() {
        String helpfilenm = configuration.helpfile;
        if (helpfilenm.equals("")) {
            helpfilenm = "help-doc.html";
        } else {
            int lastsep;
            if ((lastsep = helpfilenm.lastIndexOf(File.separatorChar)) != -1) {
                helpfilenm = helpfilenm.substring(lastsep + 1);
            }
        }
        Content linkContent = getHyperLink(relativePath + helpfilenm, "",
                helpLabel, "", "");
        Content li = HtmlTree.LI(linkContent);
        return li;
    }

    /**
     * Print the word "Detail" in the navigation bar. No link is available.
     */
    protected void navDetail() {
        printText("doclet.Detail");
    }

    /**
     * Print the word "Summary" in the navigation bar. No link is available.
     */
    protected void navSummary() {
        printText("doclet.Summary");
    }

    /**
     * Print the Html table tag for the index summary tables. The table tag
     * printed is
     * &lt;TABLE BORDER="1" CELLPADDING="3" CELLSPACING="0" WIDTH="100%">
     */
    public void tableIndexSummary() {
        table(1, "100%", 3, 0);
    }

    /**
     * Print the Html table tag for the index summary tables.
     *
     * @param summary the summary for the table tag summary attribute.
     */
    public void tableIndexSummary(String summary) {
        table(1, "100%", 3, 0, summary);
    }

    /**
     * Same as {@link #tableIndexSummary()}.
     */
    public void tableIndexDetail() {
        table(1, "100%", 3, 0);
    }

    /**
     * Print Html tag for table elements. The tag printed is
     * &lt;TD ALIGN="right" VALIGN="top" WIDTH="1%"&gt;.
     */
    public void tdIndex() {
        print("<TD ALIGN=\"right\" VALIGN=\"top\" WIDTH=\"1%\">");
    }

    /**
     * Print table caption.
     */
    public void tableCaptionStart() {
        captionStyle("TableCaption");
    }

    /**
     * Print table sub-caption.
     */
    public void tableSubCaptionStart() {
        captionStyle("TableSubCaption");
    }

    /**
     * Print table caption end tags.
     */
    public void tableCaptionEnd() {
        captionEnd();
    }

    /**
     * Print summary table header.
     */
    public void summaryTableHeader(String[] header, String scope) {
        tr();
        for ( int i=0; i < header.length; i++ ) {
            thScopeNoWrap("TableHeader", scope);
            print(header[i]);
            thEnd();
        }
        trEnd();
    }

    /**
     * Get summary table header.
     *
     * @param header the header for the table
     * @param scope the scope of the headers
     * @return a content tree for the header
     */
    public Content getSummaryTableHeader(String[] header, String scope) {
        Content tr = new HtmlTree(HtmlTag.TR);
        int size = header.length;
        Content tableHeader;
        if (size == 1) {
            tableHeader = new StringContent(header[0]);
            tr.addContent(HtmlTree.TH(HtmlStyle.colOne, scope, tableHeader));
            return tr;
        }
        for (int i = 0; i < size; i++) {
            tableHeader = new StringContent(header[i]);
            if(i == 0)
                tr.addContent(HtmlTree.TH(HtmlStyle.colFirst, scope, tableHeader));
            else if(i == (size - 1))
                tr.addContent(HtmlTree.TH(HtmlStyle.colLast, scope, tableHeader));
            else
                tr.addContent(HtmlTree.TH(scope, tableHeader));
        }
        return tr;
    }

    /**
     * Get table caption.
     *
     * @param rawText the caption for the table which could be raw Html
     * @return a content tree for the caption
     */
    public Content getTableCaption(String rawText) {
        Content title = new RawHtml(rawText);
        Content captionSpan = HtmlTree.SPAN(title);
        Content space = getSpace();
        Content tabSpan = HtmlTree.SPAN(HtmlStyle.tabEnd, space);
        Content caption = HtmlTree.CAPTION(captionSpan);
        caption.addContent(tabSpan);
        return caption;
    }

    /**
     * Get the marker anchor which will be added to the documentation tree.
     *
     * @param anchorName the anchor name attribute
     * @return a content tree for the marker anchor
     */
    public Content getMarkerAnchor(String anchorName) {
        return getMarkerAnchor(anchorName, null);
    }

    /**
     * Get the marker anchor which will be added to the documentation tree.
     *
     * @param anchorName the anchor name attribute
     * @param anchorContent the content that should be added to the anchor
     * @return a content tree for the marker anchor
     */
    public Content getMarkerAnchor(String anchorName, Content anchorContent) {
        if (anchorContent == null)
            anchorContent = new Comment(" ");
        Content markerAnchor = HtmlTree.A_NAME(anchorName, anchorContent);
        return markerAnchor;
    }

    /**
     * Returns a packagename content.
     *
     * @param packageDoc the package to check
     * @return package name content
     */
    public Content getPackageName(PackageDoc packageDoc) {
        return packageDoc == null || packageDoc.name().length() == 0 ?
            defaultPackageLabel :
            getPackageLabel(packageDoc.name());
    }

    /**
     * Returns a package name label.
     *
     * @param parsedName the package name
     * @return the package name content
     */
    public Content getPackageLabel(String packageName) {
        return new StringContent(packageName);
    }

    /**
     * Add package deprecation information to the documentation tree
     *
     * @param deprPkgs list of deprecated packages
     * @param headingKey the caption for the deprecated package table
     * @param tableSummary the summary for the deprecated package table
     * @param tableHeader table headers for the deprecated package table
     * @param contentTree the content tree to which the deprecated package table will be added
     */
    protected void addPackageDeprecatedAPI(List<Doc> deprPkgs, String headingKey,
            String tableSummary, String[] tableHeader, Content contentTree) {
        if (deprPkgs.size() > 0) {
            Content table = HtmlTree.TABLE(0, 3, 0, tableSummary,
                    getTableCaption(configuration().getText(headingKey)));
            table.addContent(getSummaryTableHeader(tableHeader, "col"));
            Content tbody = new HtmlTree(HtmlTag.TBODY);
            for (int i = 0; i < deprPkgs.size(); i++) {
                PackageDoc pkg = (PackageDoc) deprPkgs.get(i);
                HtmlTree td = HtmlTree.TD(HtmlStyle.colOne,
                        getPackageLink(pkg, getPackageName(pkg)));
                if (pkg.tags("deprecated").length > 0) {
                    addInlineDeprecatedComment(pkg, pkg.tags("deprecated")[0], td);
                }
                HtmlTree tr = HtmlTree.TR(td);
                if (i % 2 == 0) {
                    tr.addStyle(HtmlStyle.altColor);
                } else {
                    tr.addStyle(HtmlStyle.rowColor);
                }
                tbody.addContent(tr);
            }
            table.addContent(tbody);
            Content li = HtmlTree.LI(HtmlStyle.blockList, table);
            Content ul = HtmlTree.UL(HtmlStyle.blockList, li);
            contentTree.addContent(ul);
        }
    }

    /**
     * Prine table header information about color, column span and the font.
     *
     * @param color Background color.
     * @param span  Column span.
     */
    public void tableHeaderStart(String color, int span) {
        trBgcolorStyle(color, "TableHeadingColor");
        thAlignColspan("left", span);
        font("+2");
    }

    /**
     * Print table header for the inherited members summary tables. Print the
     * background color information.
     *
     * @param color Background color.
     */
    public void tableInheritedHeaderStart(String color) {
        trBgcolorStyle(color, "TableSubHeadingColor");
        thAlign("left");
    }

    /**
     * Print "Use" table header. Print the background color and the column span.
     *
     * @param color Background color.
     */
    public void tableUseInfoHeaderStart(String color) {
        trBgcolorStyle(color, "TableSubHeadingColor");
        thAlignColspan("left", 2);
    }

    /**
     * Print table header with the background color with default column span 2.
     *
     * @param color Background color.
     */
    public void tableHeaderStart(String color) {
        tableHeaderStart(color, 2);
    }

    /**
     * Print table header with the column span, with the default color #CCCCFF.
     *
     * @param span Column span.
     */
    public void tableHeaderStart(int span) {
        tableHeaderStart("#CCCCFF", span);
    }

    /**
     * Print table header with default column span 2 and default color #CCCCFF.
     */
    public void tableHeaderStart() {
        tableHeaderStart(2);
    }

    /**
     * Print table header end tags for font, column and row.
     */
    public void tableHeaderEnd() {
        fontEnd();
        thEnd();
        trEnd();
    }

    /**
     * Print table header end tags in inherited tables for column and row.
     */
    public void tableInheritedHeaderEnd() {
        thEnd();
        trEnd();
    }

    /**
     * Print the summary table row cell attribute width.
     *
     * @param width Width of the table cell.
     */
    public void summaryRow(int width) {
        if (width != 0) {
            tdWidth(width + "%");
        } else {
            td();
        }
    }

    /**
     * Print the summary table row cell end tag.
     */
    public void summaryRowEnd() {
        tdEnd();
    }

    /**
     * Print the heading in Html &lt;H2> format.
     *
     * @param str The Header string.
     */
    public void printIndexHeading(String str) {
        h2();
        print(str);
        h2End();
    }

    /**
     * Print Html tag &lt;FRAMESET=arg&gt;.
     *
     * @param arg Argument for the tag.
     */
    public void frameSet(String arg) {
        println("<FRAMESET " + arg + ">");
    }

    /**
     * Print Html closing tag &lt;/FRAMESET&gt;.
     */
    public void frameSetEnd() {
        println("</FRAMESET>");
    }

    /**
     * Print Html tag &lt;FRAME=arg&gt;.
     *
     * @param arg Argument for the tag.
     */
    public void frame(String arg) {
        println("<FRAME " + arg + ">");
    }

    /**
     * Print Html closing tag &lt;/FRAME&gt;.
     */
    public void frameEnd() {
        println("</FRAME>");
    }

    /**
     * Return path to the class page for a classdoc. For example, the class
     * name is "java.lang.Object" and if the current file getting generated is
     * "java/io/File.html", then the path string to the class, returned is
     * "../../java/lang.Object.html".
     *
     * @param cd Class to which the path is requested.
     */
    protected String pathToClass(ClassDoc cd) {
        return pathString(cd.containingPackage(), cd.name() + ".html");
    }

    /**
     * Return the path to the class page for a classdoc. Works same as
     * {@link #pathToClass(ClassDoc)}.
     *
     * @param cd   Class to which the path is requested.
     * @param name Name of the file(doesn't include path).
     */
    protected String pathString(ClassDoc cd, String name) {
        return pathString(cd.containingPackage(), name);
    }

    /**
     * Return path to the given file name in the given package. So if the name
     * passed is "Object.html" and the name of the package is "java.lang", and
     * if the relative path is "../.." then returned string will be
     * "../../java/lang/Object.html"
     *
     * @param pd Package in which the file name is assumed to be.
     * @param name File name, to which path string is.
     */
    protected String pathString(PackageDoc pd, String name) {
        StringBuffer buf = new StringBuffer(relativePath);
        buf.append(DirectoryManager.getPathToPackage(pd, name));
        return buf.toString();
    }

    /**
     * Print the link to the given package.
     *
     * @param pkg the package to link to.
     * @param label the label for the link.
     * @param isStrong true if the label should be strong.
     */
    public void printPackageLink(PackageDoc pkg, String label, boolean isStrong) {
        print(getPackageLinkString(pkg, label, isStrong));
    }

    /**
     * Print the link to the given package.
     *
     * @param pkg the package to link to.
     * @param label the label for the link.
     * @param isStrong true if the label should be strong.
     * @param style  the font of the package link label.
     */
    public void printPackageLink(PackageDoc pkg, String label, boolean isStrong,
            String style) {
        print(getPackageLinkString(pkg, label, isStrong, style));
    }

    /**
     * Return the link to the given package.
     *
     * @param pkg the package to link to.
     * @param label the label for the link.
     * @param isStrong true if the label should be strong.
     * @return the link to the given package.
     */
    public String getPackageLinkString(PackageDoc pkg, String label,
                                 boolean isStrong) {
        return getPackageLinkString(pkg, label, isStrong, "");
    }

    /**
     * Return the link to the given package.
     *
     * @param pkg the package to link to.
     * @param label the label for the link.
     * @param isStrong true if the label should be strong.
     * @param style  the font of the package link label.
     * @return the link to the given package.
     */
    public String getPackageLinkString(PackageDoc pkg, String label, boolean isStrong,
            String style) {
        boolean included = pkg != null && pkg.isIncluded();
        if (! included) {
            PackageDoc[] packages = configuration.packages;
            for (int i = 0; i < packages.length; i++) {
                if (packages[i].equals(pkg)) {
                    included = true;
                    break;
                }
            }
        }
        if (included || pkg == null) {
            return getHyperLinkString(pathString(pkg, "package-summary.html"),
                                "", label, isStrong, style);
        } else {
            String crossPkgLink = getCrossPackageLink(Util.getPackageName(pkg));
            if (crossPkgLink != null) {
                return getHyperLinkString(crossPkgLink, "", label, isStrong, style);
            } else {
                return label;
            }
        }
    }

    /**
     * Return the link to the given package.
     *
     * @param pkg the package to link to.
     * @param label the label for the link.
     * @return a content tree for the package link.
     */
    public Content getPackageLink(PackageDoc pkg, Content label) {
        boolean included = pkg != null && pkg.isIncluded();
        if (! included) {
            PackageDoc[] packages = configuration.packages;
            for (int i = 0; i < packages.length; i++) {
                if (packages[i].equals(pkg)) {
                    included = true;
                    break;
                }
            }
        }
        if (included || pkg == null) {
            return getHyperLink(pathString(pkg, "package-summary.html"),
                                "", label);
        } else {
            String crossPkgLink = getCrossPackageLink(Util.getPackageName(pkg));
            if (crossPkgLink != null) {
                return getHyperLink(crossPkgLink, "", label);
            } else {
                return label;
            }
        }
    }

    public String italicsClassName(ClassDoc cd, boolean qual) {
        String name = (qual)? cd.qualifiedName(): cd.name();
        return (cd.isInterface())?  italicsText(name): name;
    }

    public void printSrcLink(ProgramElementDoc d, String label) {
        if (d == null) {
            return;
        }
        ClassDoc cd = d.containingClass();
        if (cd == null) {
            //d must be a class doc since in has no containing class.
            cd = (ClassDoc) d;
        }
        String href = relativePath + DocletConstants.SOURCE_OUTPUT_DIR_NAME
            + DirectoryManager.getDirectoryPath(cd.containingPackage())
            + cd.name() + ".html#" + SourceToHTMLConverter.getAnchorName(d);
        printHyperLink(href, "", label, true);
    }

    /**
     * Add the link to the content tree.
     *
     * @param doc program element doc for which the link will be added
     * @param label label for the link
     * @param htmltree the content tree to which the link will be added
     */
    public void addSrcLink(ProgramElementDoc doc, Content label, Content htmltree) {
        if (doc == null) {
            return;
        }
        ClassDoc cd = doc.containingClass();
        if (cd == null) {
            //d must be a class doc since in has no containing class.
            cd = (ClassDoc) doc;
        }
        String href = relativePath + DocletConstants.SOURCE_OUTPUT_DIR_NAME
                + DirectoryManager.getDirectoryPath(cd.containingPackage())
                + cd.name() + ".html#" + SourceToHTMLConverter.getAnchorName(doc);
        Content linkContent = getHyperLink(href, "", label, "", "");
        htmltree.addContent(linkContent);
    }

    /**
     * Return the link to the given class.
     *
     * @param linkInfo the information about the link.
     *
     * @return the link for the given class.
     */
    public String getLink(LinkInfoImpl linkInfo) {
        LinkFactoryImpl factory = new LinkFactoryImpl(this);
        String link = ((LinkOutputImpl) factory.getLinkOutput(linkInfo)).toString();
        displayLength += linkInfo.displayLength;
        return link;
    }

    /**
     * Return the type parameters for the given class.
     *
     * @param linkInfo the information about the link.
     * @return the type for the given class.
     */
    public String getTypeParameterLinks(LinkInfoImpl linkInfo) {
        LinkFactoryImpl factory = new LinkFactoryImpl(this);
        return ((LinkOutputImpl)
            factory.getTypeParameterLinks(linkInfo, false)).toString();
    }

    /**
     * Print the link to the given class.
     */
    public void printLink(LinkInfoImpl linkInfo) {
        print(getLink(linkInfo));
    }

    /*************************************************************
     * Return a class cross link to external class documentation.
     * The name must be fully qualified to determine which package
     * the class is in.  The -link option does not allow users to
     * link to external classes in the "default" package.
     *
     * @param qualifiedClassName the qualified name of the external class.
     * @param refMemName the name of the member being referenced.  This should
     * be null or empty string if no member is being referenced.
     * @param label the label for the external link.
     * @param strong true if the link should be strong.
     * @param style the style of the link.
     * @param code true if the label should be code font.
     */
    public String getCrossClassLink(String qualifiedClassName, String refMemName,
                                    String label, boolean strong, String style,
                                    boolean code) {
        String className = "",
            packageName = qualifiedClassName == null ? "" : qualifiedClassName;
        int periodIndex;
        while((periodIndex = packageName.lastIndexOf('.')) != -1) {
            className = packageName.substring(periodIndex + 1, packageName.length()) +
                (className.length() > 0 ? "." + className : "");
            String defaultLabel = code ? getCode() + className + getCodeEnd() : className;
            packageName = packageName.substring(0, periodIndex);
            if (getCrossPackageLink(packageName) != null) {
                //The package exists in external documentation, so link to the external
                //class (assuming that it exists).  This is definitely a limitation of
                //the -link option.  There are ways to determine if an external package
                //exists, but no way to determine if the external class exists.  We just
                //have to assume that it does.
                return getHyperLinkString(
                    configuration.extern.getExternalLink(packageName, relativePath,
                                className + ".html?is-external=true"),
                    refMemName == null ? "" : refMemName,
                    label == null || label.length() == 0 ? defaultLabel : label,
                    strong, style,
                    configuration.getText("doclet.Href_Class_Or_Interface_Title", packageName),
                    "");
            }
        }
        return null;
    }

    public boolean isClassLinkable(ClassDoc cd) {
        if (cd.isIncluded()) {
            return configuration.isGeneratedDoc(cd);
        }
        return configuration.extern.isExternal(cd);
    }

    public String getCrossPackageLink(String pkgName) {
        return configuration.extern.getExternalLink(pkgName, relativePath,
            "package-summary.html?is-external=true");
    }

    /**
     * Get the class link.
     *
     * @param context the id of the context where the link will be added
     * @param cd the class doc to link to
     * @return a content tree for the link
     */
    public Content getQualifiedClassLink(int context, ClassDoc cd) {
        return new RawHtml(getLink(new LinkInfoImpl(context, cd,
                configuration.getClassName(cd), "")));
    }

    /**
     * Add the class link.
     *
     * @param context the id of the context where the link will be added
     * @param cd the class doc to link to
     * @param contentTree the content tree to which the link will be added
     */
    public void addPreQualifiedClassLink(int context, ClassDoc cd, Content contentTree) {
        addPreQualifiedClassLink(context, cd, false, contentTree);
    }

    /**
     * Retrieve the class link with the package portion of the label in
     * plain text.  If the qualifier is excluded, it willnot be included in the
     * link label.
     *
     * @param cd the class to link to.
     * @param isStrong true if the link should be strong.
     * @return the link with the package portion of the label in plain text.
     */
    public String getPreQualifiedClassLink(int context,
            ClassDoc cd, boolean isStrong) {
        String classlink = "";
        PackageDoc pd = cd.containingPackage();
        if(pd != null && ! configuration.shouldExcludeQualifier(pd.name())) {
            classlink = getPkgName(cd);
        }
        classlink += getLink(new LinkInfoImpl(context, cd, cd.name(), isStrong));
        return classlink;
    }

    /**
     * Add the class link with the package portion of the label in
     * plain text. If the qualifier is excluded, it will not be included in the
     * link label.
     *
     * @param context the id of the context where the link will be added
     * @param cd the class to link to
     * @param isStrong true if the link should be strong
     * @param contentTree the content tree to which the link with be added
     */
    public void addPreQualifiedClassLink(int context,
            ClassDoc cd, boolean isStrong, Content contentTree) {
        PackageDoc pd = cd.containingPackage();
        if(pd != null && ! configuration.shouldExcludeQualifier(pd.name())) {
            contentTree.addContent(getPkgName(cd));
        }
        contentTree.addContent(new RawHtml(getLink(new LinkInfoImpl(
                context, cd, cd.name(), isStrong))));
    }

    /**
     * Add the class link, with only class name as the strong link and prefixing
     * plain package name.
     *
     * @param context the id of the context where the link will be added
     * @param cd the class to link to
     * @param contentTree the content tree to which the link with be added
     */
    public void addPreQualifiedStrongClassLink(int context, ClassDoc cd, Content contentTree) {
        addPreQualifiedClassLink(context, cd, true, contentTree);
    }

    public void printText(String key) {
        print(configuration.getText(key));
    }

    public void printText(String key, String a1) {
        print(configuration.getText(key, a1));
    }

    public void printText(String key, String a1, String a2) {
        print(configuration.getText(key, a1, a2));
    }

    public void strongText(String key) {
        strong(configuration.getText(key));
    }

    public void strongText(String key, String a1) {
        strong(configuration.getText(key, a1));
    }

    public void strongText(String key, String a1, String a2) {
        strong(configuration.getText(key, a1, a2));
    }

    /**
     * Get the link for the given member.
     *
     * @param context the id of the context where the link will be added
     * @param doc the member being linked to
     * @param label the label for the link
     * @return a content tree for the doc link
     */
    public Content getDocLink(int context, MemberDoc doc, String label) {
        return getDocLink(context, doc.containingClass(), doc, label);
    }

    /**
     * Print the link for the given member.
     *
     * @param context the id of the context where the link will be printed.
     * @param classDoc the classDoc that we should link to.  This is not
     *                 necessarily equal to doc.containingClass().  We may be
     *                 inheriting comments.
     * @param doc the member being linked to.
     * @param label the label for the link.
     * @param strong true if the link should be strong.
     */
    public void printDocLink(int context, ClassDoc classDoc, MemberDoc doc,
            String label, boolean strong) {
        print(getDocLink(context, classDoc, doc, label, strong));
    }

    /**
     * Return the link for the given member.
     *
     * @param context the id of the context where the link will be printed.
     * @param doc the member being linked to.
     * @param label the label for the link.
     * @param strong true if the link should be strong.
     * @return the link for the given member.
     */
    public String getDocLink(int context, MemberDoc doc, String label,
                boolean strong) {
        return getDocLink(context, doc.containingClass(), doc, label, strong);
    }

    /**
     * Return the link for the given member.
     *
     * @param context the id of the context where the link will be printed.
     * @param classDoc the classDoc that we should link to.  This is not
     *                 necessarily equal to doc.containingClass().  We may be
     *                 inheriting comments.
     * @param doc the member being linked to.
     * @param label the label for the link.
     * @param strong true if the link should be strong.
     * @return the link for the given member.
     */
    public String getDocLink(int context, ClassDoc classDoc, MemberDoc doc,
        String label, boolean strong) {
        if (! (doc.isIncluded() ||
            Util.isLinkable(classDoc, configuration()))) {
            return label;
        } else if (doc instanceof ExecutableMemberDoc) {
            ExecutableMemberDoc emd = (ExecutableMemberDoc)doc;
            return getLink(new LinkInfoImpl(context, classDoc,
                getAnchor(emd), label, strong));
        } else if (doc instanceof MemberDoc) {
            return getLink(new LinkInfoImpl(context, classDoc,
                doc.name(), label, strong));
        } else {
            return label;
        }
    }

    /**
     * Return the link for the given member.
     *
     * @param context the id of the context where the link will be added
     * @param classDoc the classDoc that we should link to.  This is not
     *                 necessarily equal to doc.containingClass().  We may be
     *                 inheriting comments
     * @param doc the member being linked to
     * @param label the label for the link
     * @return the link for the given member
     */
    public Content getDocLink(int context, ClassDoc classDoc, MemberDoc doc,
        String label) {
        if (! (doc.isIncluded() ||
            Util.isLinkable(classDoc, configuration()))) {
            return new StringContent(label);
        } else if (doc instanceof ExecutableMemberDoc) {
            ExecutableMemberDoc emd = (ExecutableMemberDoc)doc;
            return new RawHtml(getLink(new LinkInfoImpl(context, classDoc,
                getAnchor(emd), label, false)));
        } else if (doc instanceof MemberDoc) {
            return new RawHtml(getLink(new LinkInfoImpl(context, classDoc,
                doc.name(), label, false)));
        } else {
            return new StringContent(label);
        }
    }

    public void anchor(ExecutableMemberDoc emd) {
        anchor(getAnchor(emd));
    }

    public String getAnchor(ExecutableMemberDoc emd) {
        StringBuilder signature = new StringBuilder(emd.signature());
        StringBuilder signatureParsed = new StringBuilder();
        int counter = 0;
        for (int i = 0; i < signature.length(); i++) {
            char c = signature.charAt(i);
            if (c == '<') {
                counter++;
            } else if (c == '>') {
                counter--;
            } else if (counter == 0) {
                signatureParsed.append(c);
            }
        }
        return emd.name() + signatureParsed.toString();
    }

    public String seeTagToString(SeeTag see) {
        String tagName = see.name();
        if (! (tagName.startsWith("@link") || tagName.equals("@see"))) {
            return "";
        }
        StringBuffer result = new StringBuffer();
        boolean isplaintext = tagName.toLowerCase().equals("@linkplain");
        String label = see.label();
        label = (label.length() > 0)?
            ((isplaintext) ? label :
                 getCode() + label + getCodeEnd()):"";
        String seetext = replaceDocRootDir(see.text());

        //Check if @see is an href or "string"
        if (seetext.startsWith("<") || seetext.startsWith("\"")) {
            result.append(seetext);
            return result.toString();
        }

        //The text from the @see tag.  We will output this text when a label is not specified.
        String text = (isplaintext) ? seetext : getCode() + seetext + getCodeEnd();

        ClassDoc refClass = see.referencedClass();
        String refClassName = see.referencedClassName();
        MemberDoc refMem = see.referencedMember();
        String refMemName = see.referencedMemberName();
        if (refClass == null) {
            //@see is not referencing an included class
            PackageDoc refPackage = see.referencedPackage();
            if (refPackage != null && refPackage.isIncluded()) {
                //@see is referencing an included package
                String packageName = isplaintext ? refPackage.name() :
                    getCode() + refPackage.name() + getCodeEnd();
                result.append(getPackageLinkString(refPackage,
                    label.length() == 0 ? packageName : label, false));
            } else {
                //@see is not referencing an included class or package.  Check for cross links.
                String classCrossLink, packageCrossLink = getCrossPackageLink(refClassName);
                if (packageCrossLink != null) {
                    //Package cross link found
                    result.append(getHyperLinkString(packageCrossLink, "",
                        (label.length() == 0)? text : label, false));
                } else if ((classCrossLink = getCrossClassLink(refClassName,
                        refMemName, label, false, "", ! isplaintext)) != null) {
                    //Class cross link found (possiblly to a member in the class)
                    result.append(classCrossLink);
                } else {
                    //No cross link found so print warning
                    configuration.getDocletSpecificMsg().warning(see.position(), "doclet.see.class_or_package_not_found",
                            tagName, seetext);
                    result.append((label.length() == 0)? text: label);
                }
            }
        } else if (refMemName == null) {
            // Must be a class reference since refClass is not null and refMemName is null.
            if (label.length() == 0) {
                label = (isplaintext) ? refClass.name() : getCode() + refClass.name() + getCodeEnd();
                result.append(getLink(new LinkInfoImpl(refClass, label)));
            } else {
                result.append(getLink(new LinkInfoImpl(refClass, label)));
            }
        } else if (refMem == null) {
            // Must be a member reference since refClass is not null and refMemName is not null.
            // However, refMem is null, so this referenced member does not exist.
            result.append((label.length() == 0)? text: label);
        } else {
            // Must be a member reference since refClass is not null and refMemName is not null.
            // refMem is not null, so this @see tag must be referencing a valid member.
            ClassDoc containing = refMem.containingClass();
            if (see.text().trim().startsWith("#") &&
                ! (containing.isPublic() ||
                Util.isLinkable(containing, configuration()))) {
                // Since the link is relative and the holder is not even being
                // documented, this must be an inherited link.  Redirect it.
                // The current class either overrides the referenced member or
                // inherits it automatically.
                if (this instanceof ClassWriterImpl) {
                    containing = ((ClassWriterImpl) this).getClassDoc();
                } else if (!containing.isPublic()){
                    configuration.getDocletSpecificMsg().warning(
                        see.position(), "doclet.see.class_or_package_not_accessible",
                        tagName, containing.qualifiedName());
                } else {
                    configuration.getDocletSpecificMsg().warning(
                        see.position(), "doclet.see.class_or_package_not_found",
                        tagName, seetext);
                }
            }
            if (configuration.currentcd != containing) {
                refMemName = containing.name() + "." + refMemName;
            }
            if (refMem instanceof ExecutableMemberDoc) {
                if (refMemName.indexOf('(') < 0) {
                    refMemName += ((ExecutableMemberDoc)refMem).signature();
                }
            }
            text = (isplaintext) ?
                refMemName : getCode() + Util.escapeHtmlChars(refMemName) + getCodeEnd();

            result.append(getDocLink(LinkInfoImpl.CONTEXT_SEE_TAG, containing,
                refMem, (label.length() == 0)? text: label, false));
        }
        return result.toString();
    }

    public void printInlineComment(Doc doc, Tag tag) {
        printCommentTags(doc, tag.inlineTags(), false, false);
    }

    /**
     * Add the inline comment.
     *
     * @param doc the doc for which the inline comment will be added
     * @param tag the inline tag to be added
     * @param htmltree the content tree to which the comment will be added
     */
    public void addInlineComment(Doc doc, Tag tag, Content htmltree) {
        addCommentTags(doc, tag.inlineTags(), false, false, htmltree);
    }

    public void printInlineDeprecatedComment(Doc doc, Tag tag) {
        printCommentTags(doc, tag.inlineTags(), true, false);
    }

    /**
     * Add the inline deprecated comment.
     *
     * @param doc the doc for which the inline deprecated comment will be added
     * @param tag the inline tag to be added
     * @param htmltree the content tree to which the comment will be added
     */
    public void addInlineDeprecatedComment(Doc doc, Tag tag, Content htmltree) {
        addCommentTags(doc, tag.inlineTags(), true, false, htmltree);
    }

    public void printSummaryComment(Doc doc) {
        printSummaryComment(doc, doc.firstSentenceTags());
    }

    /**
     * Adds the summary content.
     *
     * @param doc the doc for which the summary will be generated
     * @param htmltree the documentation tree to which the summary will be added
     */
    public void addSummaryComment(Doc doc, Content htmltree) {
        addSummaryComment(doc, doc.firstSentenceTags(), htmltree);
    }

    public void printSummaryComment(Doc doc, Tag[] firstSentenceTags) {
        printCommentTags(doc, firstSentenceTags, false, true);
    }

    /**
     * Adds the summary content.
     *
     * @param doc the doc for which the summary will be generated
     * @param firstSentenceTags the first sentence tags for the doc
     * @param htmltree the documentation tree to which the summary will be added
     */
    public void addSummaryComment(Doc doc, Tag[] firstSentenceTags, Content htmltree) {
        addCommentTags(doc, firstSentenceTags, false, true, htmltree);
    }

    public void printSummaryDeprecatedComment(Doc doc) {
        printCommentTags(doc, doc.firstSentenceTags(), true, true);
    }

    public void printSummaryDeprecatedComment(Doc doc, Tag tag) {
        printCommentTags(doc, tag.firstSentenceTags(), true, true);
    }

    public void addSummaryDeprecatedComment(Doc doc, Tag tag, Content htmltree) {
        addCommentTags(doc, tag.firstSentenceTags(), true, true, htmltree);
    }

    public void printInlineComment(Doc doc) {
        printCommentTags(doc, doc.inlineTags(), false, false);
        p();
    }

    /**
     * Adds the inline comment.
     *
     * @param doc the doc for which the inline comments will be generated
     * @param htmltree the documentation tree to which the inline comments will be added
     */
    public void addInlineComment(Doc doc, Content htmltree) {
        addCommentTags(doc, doc.inlineTags(), false, false, htmltree);
    }

    public void printInlineDeprecatedComment(Doc doc) {
        printCommentTags(doc, doc.inlineTags(), true, false);
    }

    private void printCommentTags(Doc doc, Tag[] tags, boolean depr, boolean first) {
        if(configuration.nocomment){
            return;
        }
        if (depr) {
            italic();
        }
        String result = commentTagsToString(null, doc, tags, first);
        print(result);
        if (depr) {
            italicEnd();
        }
        if (tags.length == 0) {
            space();
        }
    }

    /**
     * Adds the comment tags.
     *
     * @param doc the doc for which the comment tags will be generated
     * @param tags the first sentence tags for the doc
     * @param depr true if it is deprecated
     * @param first true if the first sentenge tags should be added
     * @param htmltree the documentation tree to which the comment tags will be added
     */
    private void addCommentTags(Doc doc, Tag[] tags, boolean depr,
            boolean first, Content htmltree) {
        if(configuration.nocomment){
            return;
        }
        Content div;
        Content result = new RawHtml(commentTagsToString(null, doc, tags, first));
        if (depr) {
            Content italic = HtmlTree.I(result);
            div = HtmlTree.DIV(HtmlStyle.block, italic);
            htmltree.addContent(div);
        }
        else {
            div = HtmlTree.DIV(HtmlStyle.block, result);
            htmltree.addContent(div);
        }
        if (tags.length == 0) {
            htmltree.addContent(getSpace());
        }
    }

    /**
     * Converts inline tags and text to text strings, expanding the
     * inline tags along the way.  Called wherever text can contain
     * an inline tag, such as in comments or in free-form text arguments
     * to non-inline tags.
     *
     * @param holderTag    specific tag where comment resides
     * @param doc    specific doc where comment resides
     * @param tags   array of text tags and inline tags (often alternating)
     *               present in the text of interest for this doc
     * @param isFirstSentence  true if text is first sentence
     */
    public String commentTagsToString(Tag holderTag, Doc doc, Tag[] tags,
            boolean isFirstSentence) {
        StringBuilder result = new StringBuilder();
        boolean textTagChange = false;
        // Array of all possible inline tags for this javadoc run
        configuration.tagletManager.checkTags(doc, tags, true);
        for (int i = 0; i < tags.length; i++) {
            Tag tagelem = tags[i];
            String tagName = tagelem.name();
            if (tagelem instanceof SeeTag) {
                result.append(seeTagToString((SeeTag)tagelem));
            } else if (! tagName.equals("Text")) {
                int originalLength = result.length();
                TagletOutput output = TagletWriter.getInlineTagOuput(
                    configuration.tagletManager, holderTag,
                    tagelem, getTagletWriterInstance(isFirstSentence));
                result.append(output == null ? "" : output.toString());
                if (originalLength == 0 && isFirstSentence && tagelem.name().equals("@inheritDoc") && result.length() > 0) {
                    break;
                } else if (configuration.docrootparent.length() > 0 &&
                        tagelem.name().equals("@docRoot") &&
                        ((tags[i + 1]).text()).startsWith("/..")) {
                    //If Xdocrootparent switch ON, set the flag to remove the /.. occurance after
                    //{@docRoot} tag in the very next Text tag.
                    textTagChange = true;
                    continue;
                } else {
                    continue;
                }
            } else {
                String text = tagelem.text();
                //If Xdocrootparent switch ON, remove the /.. occurance after {@docRoot} tag.
                if (textTagChange) {
                    text = text.replaceFirst("/..", "");
                    textTagChange = false;
                }
                //This is just a regular text tag.  The text may contain html links (<a>)
                //or inline tag {@docRoot}, which will be handled as special cases.
                text = redirectRelativeLinks(tagelem.holder(), text);

                // Replace @docRoot only if not represented by an instance of DocRootTaglet,
                // that is, only if it was not present in a source file doc comment.
                // This happens when inserted by the doclet (a few lines
                // above in this method).  [It might also happen when passed in on the command
                // line as a text argument to an option (like -header).]
                text = replaceDocRootDir(text);
                if (isFirstSentence) {
                    text = removeNonInlineHtmlTags(text);
                }
                StringTokenizer lines = new StringTokenizer(text, "\r\n", true);
                StringBuffer textBuff = new StringBuffer();
                while (lines.hasMoreTokens()) {
                    StringBuilder line = new StringBuilder(lines.nextToken());
                    Util.replaceTabs(configuration.sourcetab, line);
                    textBuff.append(line.toString());
                }
                result.append(textBuff);
            }
        }
        return result.toString();
    }

    /**
     * Return true if relative links should not be redirected.
     *
     * @return Return true if a relative link should not be redirected.
     */
    private boolean shouldNotRedirectRelativeLinks() {
        return  this instanceof AnnotationTypeWriter ||
                this instanceof ClassWriter ||
                this instanceof PackageSummaryWriter;
    }

    /**
     * Suppose a piece of documentation has a relative link.  When you copy
     * that documetation to another place such as the index or class-use page,
     * that relative link will no longer work.  We should redirect those links
     * so that they will work again.
     * <p>
     * Here is the algorithm used to fix the link:
     * <p>
     * &lt;relative link&gt; => docRoot + &lt;relative path to file&gt; + &lt;relative link&gt;
     * <p>
     * For example, suppose com.sun.javadoc.RootDoc has this link:
     * &lt;a href="package-summary.html"&gt;The package Page&lt;/a&gt;
     * <p>
     * If this link appeared in the index, we would redirect
     * the link like this:
     *
     * &lt;a href="./com/sun/javadoc/package-summary.html"&gt;The package Page&lt;/a&gt;
     *
     * @param doc the Doc object whose documentation is being written.
     * @param text the text being written.
     *
     * @return the text, with all the relative links redirected to work.
     */
    private String redirectRelativeLinks(Doc doc, String text) {
        if (doc == null || shouldNotRedirectRelativeLinks()) {
            return text;
        }

        String redirectPathFromRoot;
        if (doc instanceof ClassDoc) {
            redirectPathFromRoot = DirectoryManager.getDirectoryPath(((ClassDoc) doc).containingPackage());
        } else if (doc instanceof MemberDoc) {
            redirectPathFromRoot = DirectoryManager.getDirectoryPath(((MemberDoc) doc).containingPackage());
        } else if (doc instanceof PackageDoc) {
            redirectPathFromRoot = DirectoryManager.getDirectoryPath((PackageDoc) doc);
        } else {
            return text;
        }

        if (! redirectPathFromRoot.endsWith(DirectoryManager.URL_FILE_SEPARATOR)) {
            redirectPathFromRoot += DirectoryManager.URL_FILE_SEPARATOR;
        }

        //Redirect all relative links.
        int end, begin = text.toLowerCase().indexOf("<a");
        if(begin >= 0){
            StringBuffer textBuff = new StringBuffer(text);

            while(begin >=0){
                if (textBuff.length() > begin + 2 && ! Character.isWhitespace(textBuff.charAt(begin+2))) {
                    begin = textBuff.toString().toLowerCase().indexOf("<a", begin + 1);
                    continue;
                }

                begin = textBuff.indexOf("=", begin) + 1;
                end = textBuff.indexOf(">", begin +1);
                if(begin == 0){
                    //Link has no equal symbol.
                    configuration.root.printWarning(
                        doc.position(),
                        configuration.getText("doclet.malformed_html_link_tag", text));
                    break;
                }
                if (end == -1) {
                    //Break without warning.  This <a> tag is not necessarily malformed.  The text
                    //might be missing '>' character because the href has an inline tag.
                    break;
                }
                if(textBuff.substring(begin, end).indexOf("\"") != -1){
                    begin = textBuff.indexOf("\"", begin) + 1;
                    end = textBuff.indexOf("\"", begin +1);
                    if(begin == 0 || end == -1){
                        //Link is missing a quote.
                        break;
                    }
                }
                String relativeLink = textBuff.substring(begin, end);
                if(!(relativeLink.toLowerCase().startsWith("mailto:") ||
                     relativeLink.toLowerCase().startsWith("http:") ||
                     relativeLink.toLowerCase().startsWith("https:") ||
                     relativeLink.toLowerCase().startsWith("file:"))){
                     relativeLink = "{@"+(new DocRootTaglet()).getName() + "}"
                        + redirectPathFromRoot
                        + relativeLink;
                    textBuff.replace(begin, end, relativeLink);
                }
                begin = textBuff.toString().toLowerCase().indexOf("<a", begin + 1);
            }
            return textBuff.toString();
        }
        return text;
    }

    public String removeNonInlineHtmlTags(String text) {
        if (text.indexOf('<') < 0) {
            return text;
        }
        String noninlinetags[] = { "<ul>", "</ul>", "<ol>", "</ol>",
                "<dl>", "</dl>", "<table>", "</table>",
                "<tr>", "</tr>", "<td>", "</td>",
                "<th>", "</th>", "<p>", "</p>",
                "<li>", "</li>", "<dd>", "</dd>",
                "<dir>", "</dir>", "<dt>", "</dt>",
                "<h1>", "</h1>", "<h2>", "</h2>",
                "<h3>", "</h3>", "<h4>", "</h4>",
                "<h5>", "</h5>", "<h6>", "</h6>",
                "<pre>", "</pre>", "<menu>", "</menu>",
                "<listing>", "</listing>", "<hr>",
                "<blockquote>", "</blockquote>",
                "<center>", "</center>",
                "<UL>", "</UL>", "<OL>", "</OL>",
                "<DL>", "</DL>", "<TABLE>", "</TABLE>",
                "<TR>", "</TR>", "<TD>", "</TD>",
                "<TH>", "</TH>", "<P>", "</P>",
                "<LI>", "</LI>", "<DD>", "</DD>",
                "<DIR>", "</DIR>", "<DT>", "</DT>",
                "<H1>", "</H1>", "<H2>", "</H2>",
                "<H3>", "</H3>", "<H4>", "</H4>",
                "<H5>", "</H5>", "<H6>", "</H6>",
                "<PRE>", "</PRE>", "<MENU>", "</MENU>",
                "<LISTING>", "</LISTING>", "<HR>",
                "<BLOCKQUOTE>", "</BLOCKQUOTE>",
                "<CENTER>", "</CENTER>"
        };
        for (int i = 0; i < noninlinetags.length; i++) {
            text = replace(text, noninlinetags[i], "");
        }
        return text;
    }

    public String replace(String text, String tobe, String by) {
        while (true) {
            int startindex = text.indexOf(tobe);
            if (startindex < 0) {
                return text;
            }
            int endindex = startindex + tobe.length();
            StringBuilder replaced = new StringBuilder();
            if (startindex > 0) {
                replaced.append(text.substring(0, startindex));
            }
            replaced.append(by);
            if (text.length() > endindex) {
                replaced.append(text.substring(endindex));
            }
            text = replaced.toString();
        }
    }

    public void printStyleSheetProperties() {
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
        link("REL =\"stylesheet\" TYPE=\"text/css\" HREF=\"" +
                 filename + "\" " + "TITLE=\"Style\"");
    }

    /**
     * Returns a link to the stylesheet file.
     *
     * @return an HtmlTree for the lINK tag which provides the stylesheet location
     */
    public HtmlTree getStyleSheetProperties() {
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
     * According to
     * <cite>The Java&trade; Language Specification</cite>,
     * all the outer classes and static nested classes are core classes.
     */
    public boolean isCoreClass(ClassDoc cd) {
        return cd.containingClass() == null || cd.isStatic();
    }

    /**
     * Write the annotatation types for the given packageDoc.
     *
     * @param packageDoc the package to write annotations for.
     */
    public void writeAnnotationInfo(PackageDoc packageDoc) {
        writeAnnotationInfo(packageDoc, packageDoc.annotations());
    }

    /**
     * Adds the annotatation types for the given packageDoc.
     *
     * @param packageDoc the package to write annotations for.
     * @param htmltree the documentation tree to which the annotation info will be
     *        added
     */
    public void addAnnotationInfo(PackageDoc packageDoc, Content htmltree) {
        addAnnotationInfo(packageDoc, packageDoc.annotations(), htmltree);
    }

    /**
     * Write the annotatation types for the given doc.
     *
     * @param doc the doc to write annotations for.
     */
    public void writeAnnotationInfo(ProgramElementDoc doc) {
        writeAnnotationInfo(doc, doc.annotations());
    }

    /**
     * Adds the annotatation types for the given doc.
     *
     * @param packageDoc the package to write annotations for
     * @param htmltree the content tree to which the annotation types will be added
     */
    public void addAnnotationInfo(ProgramElementDoc doc, Content htmltree) {
        addAnnotationInfo(doc, doc.annotations(), htmltree);
    }

    /**
     * Write the annotatation types for the given doc and parameter.
     *
     * @param indent the number of spaced to indent the parameters.
     * @param doc the doc to write annotations for.
     * @param param the parameter to write annotations for.
     */
    public boolean writeAnnotationInfo(int indent, Doc doc, Parameter param) {
        return writeAnnotationInfo(indent, doc, param.annotations(), false);
    }

    /**
     * Add the annotatation types for the given doc and parameter.
     *
     * @param indent the number of spaces to indent the parameters.
     * @param doc the doc to write annotations for.
     * @param param the parameter to write annotations for.
     * @param tree the content tree to which the annotation types will be added
     */
    public boolean addAnnotationInfo(int indent, Doc doc, Parameter param,
            Content tree) {
        return addAnnotationInfo(indent, doc, param.annotations(), false, tree);
    }

    /**
     * Write the annotatation types for the given doc.
     *
     * @param doc the doc to write annotations for.
     * @param descList the array of {@link AnnotationDesc}.
     */
    private void writeAnnotationInfo(Doc doc, AnnotationDesc[] descList) {
        writeAnnotationInfo(0, doc, descList, true);
    }

    /**
     * Adds the annotatation types for the given doc.
     *
     * @param doc the doc to write annotations for.
     * @param descList the array of {@link AnnotationDesc}.
     * @param htmltree the documentation tree to which the annotation info will be
     *        added
     */
    private void addAnnotationInfo(Doc doc, AnnotationDesc[] descList,
            Content htmltree) {
        addAnnotationInfo(0, doc, descList, true, htmltree);
    }

    /**
     * Write the annotatation types for the given doc.
     *
     * @param indent the number of extra spaces to indent the annotations.
     * @param doc the doc to write annotations for.
     * @param descList the array of {@link AnnotationDesc}.
     */
    private boolean writeAnnotationInfo(int indent, Doc doc, AnnotationDesc[] descList, boolean lineBreak) {
        List<String> annotations = getAnnotations(indent, descList, lineBreak);
        if (annotations.size() == 0) {
            return false;
        }
        fontNoNewLine("-1");
        for (Iterator<String> iter = annotations.iterator(); iter.hasNext();) {
            print(iter.next());
        }
        fontEnd();
        return true;
    }

    /**
     * Adds the annotatation types for the given doc.
     *
     * @param indent the number of extra spaces to indent the annotations.
     * @param doc the doc to write annotations for.
     * @param descList the array of {@link AnnotationDesc}.
     * @param htmltree the documentation tree to which the annotation info will be
     *        added
     */
    private boolean addAnnotationInfo(int indent, Doc doc,
            AnnotationDesc[] descList, boolean lineBreak, Content htmltree) {
        List<String> annotations = getAnnotations(indent, descList, lineBreak);
        if (annotations.size() == 0) {
            return false;
        }
        Content annotationContent;
        for (Iterator<String> iter = annotations.iterator(); iter.hasNext();) {
            annotationContent = new RawHtml(iter.next());
            htmltree.addContent(annotationContent);
        }
        return true;
    }

   /**
     * Return the string representations of the annotation types for
     * the given doc.
     *
     * @param indent the number of extra spaces to indent the annotations.
     * @param descList the array of {@link AnnotationDesc}.
     * @param linkBreak if true, add new line between each member value.
     * @return an array of strings representing the annotations being
     *         documented.
     */
    private List<String> getAnnotations(int indent, AnnotationDesc[] descList, boolean linkBreak) {
        List<String> results = new ArrayList<String>();
        StringBuffer annotation;
        for (int i = 0; i < descList.length; i++) {
            AnnotationTypeDoc annotationDoc = descList[i].annotationType();
            if (! Util.isDocumentedAnnotation(annotationDoc)){
                continue;
            }
            annotation = new StringBuffer();
            LinkInfoImpl linkInfo = new LinkInfoImpl(
                LinkInfoImpl.CONTEXT_ANNOTATION, annotationDoc);
            linkInfo.label = "@" + annotationDoc.name();
            annotation.append(getLink(linkInfo));
            AnnotationDesc.ElementValuePair[] pairs = descList[i].elementValues();
            if (pairs.length > 0) {
                annotation.append('(');
                for (int j = 0; j < pairs.length; j++) {
                    if (j > 0) {
                        annotation.append(",");
                        if (linkBreak) {
                            annotation.append(DocletConstants.NL);
                            int spaces = annotationDoc.name().length() + 2;
                            for (int k = 0; k < (spaces + indent); k++) {
                                annotation.append(' ');
                            }
                        }
                    }
                    annotation.append(getDocLink(LinkInfoImpl.CONTEXT_ANNOTATION,
                        pairs[j].element(), pairs[j].element().name(), false));
                    annotation.append('=');
                    AnnotationValue annotationValue = pairs[j].value();
                    List<AnnotationValue> annotationTypeValues = new ArrayList<AnnotationValue>();
                    if (annotationValue.value() instanceof AnnotationValue[]) {
                        AnnotationValue[] annotationArray =
                            (AnnotationValue[]) annotationValue.value();
                        for (int k = 0; k < annotationArray.length; k++) {
                            annotationTypeValues.add(annotationArray[k]);
                        }
                    } else {
                        annotationTypeValues.add(annotationValue);
                    }
                    annotation.append(annotationTypeValues.size() == 1 ? "" : "{");
                    for (Iterator<AnnotationValue> iter = annotationTypeValues.iterator(); iter.hasNext(); ) {
                        annotation.append(annotationValueToString(iter.next()));
                        annotation.append(iter.hasNext() ? "," : "");
                    }
                    annotation.append(annotationTypeValues.size() == 1 ? "" : "}");
                }
                annotation.append(")");
            }
            annotation.append(linkBreak ? DocletConstants.NL : "");
            results.add(annotation.toString());
        }
        return results;
    }

    private String annotationValueToString(AnnotationValue annotationValue) {
        if (annotationValue.value() instanceof Type) {
            Type type = (Type) annotationValue.value();
            if (type.asClassDoc() != null) {
                LinkInfoImpl linkInfo = new LinkInfoImpl(
                    LinkInfoImpl.CONTEXT_ANNOTATION, type);
                    linkInfo.label = (type.asClassDoc().isIncluded() ?
                        type.typeName() :
                        type.qualifiedTypeName()) + type.dimension() + ".class";
                return getLink(linkInfo);
            } else {
                return type.typeName() + type.dimension() + ".class";
            }
        } else if (annotationValue.value() instanceof AnnotationDesc) {
            List<String> list = getAnnotations(0,
                new AnnotationDesc[]{(AnnotationDesc) annotationValue.value()},
                    false);
            StringBuffer buf = new StringBuffer();
            for (Iterator<String> iter = list.iterator(); iter.hasNext(); ) {
                buf.append(iter.next());
            }
            return buf.toString();
        } else if (annotationValue.value() instanceof MemberDoc) {
            return getDocLink(LinkInfoImpl.CONTEXT_ANNOTATION,
                (MemberDoc) annotationValue.value(),
                ((MemberDoc) annotationValue.value()).name(), false);
         } else {
            return annotationValue.toString();
         }
    }

    /**
     * Return the configuation for this doclet.
     *
     * @return the configuration for this doclet.
     */
    public Configuration configuration() {
        return configuration;
    }
}
