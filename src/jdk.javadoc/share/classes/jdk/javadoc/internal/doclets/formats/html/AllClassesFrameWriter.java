/*
 * Copyright (c) 1998, 2018, Oracle and/or its affiliates. All rights reserved.
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


import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlConstants;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.IndexBuilder;


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
     */
    public AllClassesFrameWriter(HtmlConfiguration configuration,
                                 DocPath filename, IndexBuilder indexbuilder) {
        super(configuration, filename);
        this.indexbuilder = indexbuilder;
    }

    /**
     * Create AllClassesFrameWriter object. Then use it to generate the
     * "allclasses-frame.html" file. Generate the file in the current or the
     * destination directory.
     *
     * @param configuration the configuration for this javadoc run
     * @throws DocFileIOException
     */
    public static void generate(HtmlConfiguration configuration,
            IndexBuilder indexBuilder) throws DocFileIOException {
        if (configuration.frames) {
            generate(configuration, indexBuilder, DocPaths.ALLCLASSES_FRAME, true);
        }
    }

    private static void generate(HtmlConfiguration configuration, IndexBuilder indexBuilder,
                                 DocPath fileName, boolean wantFrames) throws DocFileIOException {
        AllClassesFrameWriter allclassgen = new AllClassesFrameWriter(configuration,
                fileName, indexBuilder);
        allclassgen.buildAllClassesFile(wantFrames);
        allclassgen = new AllClassesFrameWriter(configuration,
                                                fileName, indexBuilder);
    }

    /**
     * Print all the classes in the file.
     * @param wantFrames True if we want frames.
     */
    protected void buildAllClassesFile(boolean wantFrames) throws DocFileIOException {
        String label = resources.getText("doclet.All_Classes");
        Content body = getBody(false, getWindowTitle(label));
        Content htmlTree = HtmlTree.MAIN();
        Content heading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING,
                HtmlStyle.bar, contents.allClassesLabel);
        htmlTree.addContent(heading);
        Content ul = new HtmlTree(HtmlTag.UL);
        // Generate the class links and add it to the tdFont tree.
        addAllClasses(ul, wantFrames);
        HtmlTree div = HtmlTree.DIV(HtmlStyle.indexContainer, ul);
        htmlTree.addContent(div);
        body.addContent(htmlTree);
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
        for (Character unicode : indexbuilder.index()) {
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
    protected void addContents(Iterable<? extends Element> classlist, boolean wantFrames,
                               Content content) {
        for (Element element : classlist) {
            TypeElement typeElement = (TypeElement)element;
            if (!utils.isCoreClass(typeElement)) {
                continue;
            }
            Content label = interfaceName(typeElement, false);
            Content linkContent;
            if (wantFrames) {
                linkContent = getLink(new LinkInfoImpl(configuration,
                                                       LinkInfoImpl.Kind.ALL_CLASSES_FRAME, typeElement).label(label).target("classFrame"));
            } else {
                linkContent = getLink(new LinkInfoImpl(configuration, LinkInfoImpl.Kind.DEFAULT, typeElement).label(label));
            }
            Content li = HtmlTree.LI(linkContent);
            content.addContent(li);
        }
    }
}
