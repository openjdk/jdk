/*
 * Copyright (c) 1997, 2004, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.doclets.internal.toolkit.util.*;
import java.io.*;

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
     * @param filename File to be generated.
     */
    public FrameOutputWriter(ConfigurationImpl configuration,
                             String filename) throws IOException {
        super(configuration, filename);
    noOfPackages = configuration.packages.length;
    }

    /**
     * Construct FrameOutputWriter object and then use it to generate the Html
     * file which will have the description of all the frames in the
     * documentation. The name of the generated file is "index.html" which is
     * the default first file for Html documents.
     * @throws DocletAbortException
     */
    public static void generate(ConfigurationImpl configuration) {
        FrameOutputWriter framegen;
        String filename = "";
        try {
            filename = "index.html";
            framegen = new FrameOutputWriter(configuration, filename);
            framegen.generateFrameFile();
            framegen.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                        "doclet.exception_encountered",
                        exc.toString(), filename);
            throw new DocletAbortException();
        }
    }

    /**
     * Generate the contants in the "index.html" file. Print the frame details
     * as well as warning if browser is not supporting the Html frames.
     */
    protected void generateFrameFile() {
        if (configuration.windowtitle.length() > 0) {
            printFramesetHeader(configuration.windowtitle, configuration.notimestamp);
        } else {
            printFramesetHeader(configuration.getText("doclet.Generated_Docs_Untitled"),
                                configuration.notimestamp);
        }
        printFrameDetails();
        printFrameFooter();
    }

    /**
     * Generate the code for issueing the warning for a non-frame capable web
     * client. Also provide links to the non-frame version documentation.
     */
    protected void printFrameWarning() {
        noFrames();
        h2();
        printText("doclet.Frame_Alert");
        h2End();
        p();
        printText("doclet.Frame_Warning_Message");
        br();
        printText("doclet.Link_To");
        printHyperLink(configuration.topFile,
            configuration.getText("doclet.Non_Frame_Version"));
        println("");
        noFramesEnd();
    }

    /**
     * Print the frame sizes and their contents.
     */
    protected void printFrameDetails() {
        // title attribute intentionally made empty so
        // 508 tests will not flag it as missing
        frameSet("cols=\"20%,80%\" title=\"\" onLoad=\"top.loadFrames()\"");
        if (noOfPackages <= 1) {
            printAllClassesFrameTag();
        } else if (noOfPackages > 1) {
            frameSet("rows=\"30%,70%\" title=\"\" onLoad=\"top.loadFrames()\"");
            printAllPackagesFrameTag();
            printAllClassesFrameTag();
            frameSetEnd();
        }
        printClassFrameTag();
        printFrameWarning();
        frameSetEnd();
    }

    /**
     * Print the FRAME tag for the frame that lists all packages
     */
    private void printAllPackagesFrameTag() {
        frame("src=\"overview-frame.html\" name=\"packageListFrame\""
            + " title=\"" + configuration.getText("doclet.All_Packages") + "\"");
    }

    /**
     * Print the FRAME tag for the frame that lists all classes
     */
    private void printAllClassesFrameTag() {
        frame("src=\"" + "allclasses-frame.html" + "\""
            + " name=\"packageFrame\""
            + " title=\"" + configuration.getText("doclet.All_classes_and_interfaces")
            + "\"");
    }

    /**
     * Print the FRAME tag for the frame that describes the class in detail
     */
    private void printClassFrameTag() {
        frame("src=\"" + configuration.topFile + "\""
            + " name=\"classFrame\""
            + " title=\""
            + configuration.getText("doclet.Package_class_and_interface_descriptions")
            + "\" scrolling=\"yes\"");
    }

}
