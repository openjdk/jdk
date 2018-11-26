/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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
import jdk.javadoc.internal.doclets.formats.html.markup.DocType;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlAttr;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlDocument;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Script;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocFile;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;


/**
 * Generate the documentation in the Html "frame" format in the browser. The
 * generated documentation will have two or three frames depending upon the
 * number of packages on the command line. In general there will be three frames
 * in the output, a left-hand top frame will have a list of all packages with
 * links to target left-hand bottom frame. The left-hand bottom frame will have
 * the particular package contents or the all-classes list, where as the single
 * right-hand frame will have overview or package summary or class file. Also
 * take care of browsers which do not support Html frames.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Atul M Dambalkar
 */
public class FrameOutputWriter extends HtmlDocletWriter {

    /**
     * Number of packages specified on the command line.
     */
    int noOfPackages;

    /**
     * Constructor to construct FrameOutputWriter object.
     *
     * @param configuration for this run
     * @param filename File to be generated.
     */
    public FrameOutputWriter(HtmlConfiguration configuration, DocPath filename) {
        super(configuration, filename);
        noOfPackages = configuration.packages.size();
    }

    /**
     * Construct FrameOutputWriter object and then use it to generate the Html
     * file which will have the description of all the frames in the
     * documentation. The name of the generated file is "index.html" which is
     * the default first file for Html documents.
     * @param configuration the configuration for this doclet
     * @throws DocFileIOException if there is a problem generating the frame file
     */
    public static void generate(HtmlConfiguration configuration) throws DocFileIOException {
        FrameOutputWriter framegen = new FrameOutputWriter(configuration, DocPaths.INDEX);
        framegen.generateFrameFile();
    }

    /**
     * Generate the constants in the "index.html" file. Print the frame details
     * as well as warning if browser is not supporting the Html frames.
     * @throws DocFileIOException if there is a problem generating the frame file
     */
    protected void generateFrameFile() throws DocFileIOException {
        Content frame = getFrameDetails();
        HtmlTree body = new HtmlTree(HtmlTag.BODY);
        body.addAttr(HtmlAttr.ONLOAD, "loadFrames()");
        String topFilePath = configuration.topFile.getPath();
        Script script = new Script(
                "\nif (targetPage == \"\" || targetPage == \"undefined\")\n" +
                "     window.location.replace(")
                .appendStringLiteral(topFilePath, '\'')
                .append(");\n");
        body.addContent(script.asContent());
        Content noScript = HtmlTree.NOSCRIPT(contents.noScriptMessage);
        body.addContent(noScript);
        if (configuration.allowTag(HtmlTag.MAIN)) {
            HtmlTree main = HtmlTree.MAIN(frame);
            body.addContent(main);
        } else {
            body.addContent(frame);
        }
        if (configuration.windowtitle.length() > 0) {
            printFramesDocument(configuration.windowtitle, body);
        } else {
            printFramesDocument(resources.getText("doclet.Generated_Docs_Untitled"), body);
        }
    }

    /**
     * Print the frames version of the Html file header.
     * Called only when generating an HTML frames file.
     *
     * @param title Title of this HTML document
     * @param body the body content tree to be added to the HTML document
     * @throws DocFileIOException if there is an error writing the frames document
     */
    private void printFramesDocument(String title, HtmlTree body) throws DocFileIOException {
        DocType htmlDocType = DocType.forVersion(configuration.htmlVersion);
        Content htmlComment = contents.newPage;
        Head head = new Head(path, configuration.htmlVersion, configuration.docletVersion)
                .setTimestamp(!configuration.notimestamp)
                .setTitle(title)
                .setCharset(configuration.charset)
                .setStylesheets(configuration.getMainStylesheet(), configuration.getAdditionalStylesheets())
                .addDefaultScript(false)
                .addScript(getFramesScript());

        Content htmlTree = HtmlTree.HTML(configuration.getLocale().getLanguage(), head.toContent(), body);
        HtmlDocument htmlDocument = new HtmlDocument(htmlDocType, htmlComment, htmlTree);
        htmlDocument.write(DocFile.createFileForOutput(configuration, path));
   }

    /**
     * Get the frame sizes and their contents.
     *
     * @return a content tree for the frame details
     */
    protected Content getFrameDetails() {
        HtmlTree leftContainerDiv = new HtmlTree(HtmlTag.DIV);
        HtmlTree rightContainerDiv = new HtmlTree(HtmlTag.DIV);
        leftContainerDiv.setStyle(HtmlStyle.leftContainer);
        rightContainerDiv.setStyle(HtmlStyle.rightContainer);
        if (configuration.showModules && configuration.modules.size() > 1) {
            addAllModulesFrameTag(leftContainerDiv);
        } else if (noOfPackages > 1) {
            addAllPackagesFrameTag(leftContainerDiv);
        }
        addAllClassesFrameTag(leftContainerDiv);
        addClassFrameTag(rightContainerDiv);
        HtmlTree mainContainer = HtmlTree.DIV(HtmlStyle.mainContainer, leftContainerDiv);
        mainContainer.addContent(rightContainerDiv);
        return mainContainer;
    }

    /**
     * Add the IFRAME tag for the frame that lists all modules.
     *
     * @param contentTree to which the information will be added
     */
    private void addAllModulesFrameTag(Content contentTree) {
        HtmlTree frame = HtmlTree.IFRAME(DocPaths.MODULE_OVERVIEW_FRAME.getPath(),
                "packageListFrame", resources.getText("doclet.All_Modules"));
        HtmlTree leftTop = HtmlTree.DIV(HtmlStyle.leftTop, frame);
        contentTree.addContent(leftTop);
    }

    /**
     * Add the IFRAME tag for the frame that lists all packages.
     *
     * @param contentTree the content tree to which the information will be added
     */
    private void addAllPackagesFrameTag(Content contentTree) {
        HtmlTree frame = HtmlTree.IFRAME(DocPaths.OVERVIEW_FRAME.getPath(),
                "packageListFrame", resources.getText("doclet.All_Packages"));
        HtmlTree leftTop = HtmlTree.DIV(HtmlStyle.leftTop, frame);
        contentTree.addContent(leftTop);
    }

    /**
     * Add the IFRAME tag for the frame that lists all classes.
     *
     * @param contentTree the content tree to which the information will be added
     */
    private void addAllClassesFrameTag(Content contentTree) {
        HtmlTree frame = HtmlTree.IFRAME(DocPaths.ALLCLASSES_FRAME.getPath(),
                "packageFrame", resources.getText("doclet.All_classes_and_interfaces"));
        HtmlTree leftBottom = HtmlTree.DIV(HtmlStyle.leftBottom, frame);
        contentTree.addContent(leftBottom);
    }

    /**
     * Add the IFRAME tag for the frame that describes the class in detail.
     *
     * @param contentTree the content tree to which the information will be added
     */
    private void addClassFrameTag(Content contentTree) {
        HtmlTree frame = HtmlTree.IFRAME(configuration.topFile.getPath(), "classFrame",
                resources.getText("doclet.Package_class_and_interface_descriptions"));
        frame.setStyle(HtmlStyle.rightIframe);
        contentTree.addContent(frame);
    }

    /**
     * Returns a content tree for the SCRIPT tag for the main page(index.html).
     *
     * @return a content for the SCRIPT tag
     */
    protected Script getFramesScript() {
        return new Script("\n" +
                "    tmpTargetPage = \"\" + window.location.search;\n" +
                "    if (tmpTargetPage != \"\" && tmpTargetPage != \"undefined\")\n" +
                "        tmpTargetPage = tmpTargetPage.substring(1);\n" +
                "    if (tmpTargetPage.indexOf(\":\") != -1 || (tmpTargetPage != \"\" && !validURL(tmpTargetPage)))\n" +
                "        tmpTargetPage = \"undefined\";\n" +
                "    targetPage = tmpTargetPage;\n" +
                "    function validURL(url) {\n" +
                "        try {\n" +
                "            url = decodeURIComponent(url);\n" +
                "        }\n" +
                "        catch (error) {\n" +
                "            return false;\n" +
                "        }\n" +
                "        var pos = url.indexOf(\".html\");\n" +
                "        if (pos == -1 || pos != url.length - 5)\n" +
                "            return false;\n" +
                "        var allowNumber = false;\n" +
                "        var allowSep = false;\n" +
                "        var seenDot = false;\n" +
                "        for (var i = 0; i < url.length - 5; i++) {\n" +
                "            var ch = url.charAt(i);\n" +
                "            if ('a' <= ch && ch <= 'z' ||\n" +
                "                    'A' <= ch && ch <= 'Z' ||\n" +
                "                    ch == '$' ||\n" +
                "                    ch == '_' ||\n" +
                "                    ch.charCodeAt(0) > 127) {\n" +
                "                allowNumber = true;\n" +
                "                allowSep = true;\n" +
                "            } else if ('0' <= ch && ch <= '9'\n" +
                "                    || ch == '-') {\n" +
                "                if (!allowNumber)\n" +
                "                     return false;\n" +
                "            } else if (ch == '/' || ch == '.') {\n" +
                "                if (!allowSep)\n" +
                "                    return false;\n" +
                "                allowNumber = false;\n" +
                "                allowSep = false;\n" +
                "                if (ch == '.')\n" +
                "                     seenDot = true;\n" +
                "                if (ch == '/' && seenDot)\n" +
                "                     return false;\n" +
                "            } else {\n" +
                "                return false;\n" +
                "            }\n" +
                "        }\n" +
                "        return true;\n" +
                "    }\n" +
                "    function loadFrames() {\n" +
                "        if (targetPage != \"\" && targetPage != \"undefined\")\n" +
                "             top.classFrame.location = top.targetPage;\n" +
                "    }\n");
    }
}
