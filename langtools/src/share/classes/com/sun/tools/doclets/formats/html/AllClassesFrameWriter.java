/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.util.*;

import com.sun.javadoc.*;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * Generate the file with list of all the classes in this run. This page will be
 * used in the left-hand bottom frame, when "All Classes" link is clicked in
 * the left-hand top frame. The name of the generated file is
 * "allclasses-frame.html".
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Atul M Dambalkar
 * @author Doug Kramer
 * @author Bhavesh Patel (Modified)
 */
public class AllClassesFrameWriter extends HtmlDocletWriter {

    /**
     * Index of all the classes.
     */
    protected IndexBuilder indexbuilder;

    /**
     * BR tag to be used within a document tree.
     */
    final HtmlTree BR = new HtmlTree(HtmlTag.BR);

    /**
     * Construct AllClassesFrameWriter object. Also initializes the indexbuilder
     * variable in this class.
     * @param configuration  The current configuration
     * @param filename       Path to the file which is getting generated.
     * @param indexbuilder   Unicode based Index from {@link IndexBuilder}
     * @throws IOException
     * @throws DocletAbortException
     */
    public AllClassesFrameWriter(ConfigurationImpl configuration,
                                 DocPath filename, IndexBuilder indexbuilder)
                              throws IOException {
        super(configuration, filename);
        this.indexbuilder = indexbuilder;
    }

    /**
     * Create AllClassesFrameWriter object. Then use it to generate the
     * "allclasses-frame.html" file. Generate the file in the current or the
     * destination directory.
     *
     * @param indexbuilder IndexBuilder object for all classes index.
     * @throws DocletAbortException
     */
    public static void generate(ConfigurationImpl configuration,
                                IndexBuilder indexbuilder) {
        AllClassesFrameWriter allclassgen;
        DocPath filename = DocPaths.ALLCLASSES_FRAME;
        try {
            allclassgen = new AllClassesFrameWriter(configuration,
                                                    filename, indexbuilder);
            allclassgen.buildAllClassesFile(true);
            allclassgen.close();
            filename = DocPaths.ALLCLASSES_NOFRAME;
            allclassgen = new AllClassesFrameWriter(configuration,
                                                    filename, indexbuilder);
            allclassgen.buildAllClassesFile(false);
            allclassgen.close();
        } catch (IOException exc) {
            configuration.standardmessage.
                     error("doclet.exception_encountered",
                           exc.toString(), filename);
            throw new DocletAbortException(exc);
        }
    }

    /**
     * Print all the classes in the file.
     * @param wantFrames True if we want frames.
     */
    protected void buildAllClassesFile(boolean wantFrames) throws IOException {
        String label = configuration.getText("doclet.All_Classes");
        Content body = getBody(false, getWindowTitle(label));
        Content heading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING,
                HtmlStyle.bar, allclassesLabel);
        body.addContent(heading);
        Content ul = new HtmlTree(HtmlTag.UL);
        // Generate the class links and add it to the tdFont tree.
        addAllClasses(ul, wantFrames);
        Content div = HtmlTree.DIV(HtmlStyle.indexContainer, ul);
        body.addContent(div);
        printHtmlDocument(null, false, body);
    }

    /**
     * Use the sorted index of all the classes and add all the classes to the
     * content list.
     *
     * @param content HtmlTree content to which all classes information will be added
     * @param wantFrames True if we want frames.
     */
    protected void addAllClasses(Content content, boolean wantFrames) {
        for (int i = 0; i < indexbuilder.elements().length; i++) {
            Character unicode = (Character)((indexbuilder.elements())[i]);
            addContents(indexbuilder.getMemberList(unicode), wantFrames, content);
        }
    }

    /**
     * Given a list of classes, generate links for each class or interface.
     * If the class kind is interface, print it in the italics font. Also all
     * links should target the right-hand frame. If clicked on any class name
     * in this page, appropriate class page should get opened in the right-hand
     * frame.
     *
     * @param classlist Sorted list of classes.
     * @param wantFrames True if we want frames.
     * @param content HtmlTree content to which the links will be added
     */
    protected void addContents(List<Doc> classlist, boolean wantFrames,
            Content content) {
        for (int i = 0; i < classlist.size(); i++) {
            ClassDoc cd = (ClassDoc)classlist.get(i);
            if (!Util.isCoreClass(cd)) {
                continue;
            }
            Content label = italicsClassName(cd, false);
            Content linkContent;
            if (wantFrames) {
                linkContent = getLink(new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.ALL_CLASSES_FRAME, cd).label(label).target("classFrame"));
            } else {
                linkContent = getLink(new LinkInfoImpl(configuration, LinkInfoImpl.Kind.DEFAULT, cd).label(label));
            }
            Content li = HtmlTree.LI(linkContent);
            content.addContent(li);
        }
    }
}
