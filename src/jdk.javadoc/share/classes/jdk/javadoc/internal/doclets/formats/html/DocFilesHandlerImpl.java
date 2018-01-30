/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.source.doctree.AttributeTree;
import com.sun.source.doctree.AttributeTree.ValueKind;
import com.sun.source.doctree.DocRootTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.EndElementTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.StartElementTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.util.SimpleDocTreeVisitor;
import com.sun.tools.doclint.HtmlTag;
import com.sun.tools.doclint.HtmlTag.Attr;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.DocFileElement;
import jdk.javadoc.internal.doclets.toolkit.DocFilesHandler;
import jdk.javadoc.internal.doclets.toolkit.util.DocFile;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.tools.FileObject;
import javax.tools.JavaFileManager.Location;
import java.util.Collections;
import java.util.List;

public class DocFilesHandlerImpl implements DocFilesHandler {

    public final Element element;
    public final Location location;
    public final DocPath  source;
    public final HtmlConfiguration configuration;

    /**
     * Constructor to construct the DocFilesWriter object.
     *
     * @param configuration the configuration of this doclet.
     * @param element the containing element of the doc-files.
     *
     */
    public DocFilesHandlerImpl(HtmlConfiguration configuration, Element element) {
        this.configuration = configuration;
        this.element = element;

        switch (element.getKind()) {
            case MODULE:
                location = configuration.utils.getLocationForModule((ModuleElement)element);
                source = DocPaths.DOC_FILES;
                break;
            case PACKAGE:
                location = configuration.utils.getLocationForPackage((PackageElement)element);
                source = DocPath.forPackage((PackageElement)element).resolve(DocPaths.DOC_FILES);
                break;
            default:
                throw new AssertionError("unsupported element " + element);
        }
    }

    /**
     * Copy doc-files directory and its contents from the source
     * elements directory to the generated documentation directory.
     *
     * @throws DocFileIOException if there is a problem while copying
     *         the documentation files
     */

    public void copyDocFiles()  throws DocFileIOException {
        boolean first = true;
        for (DocFile srcdir : DocFile.list(configuration, location, source)) {
            if (!srcdir.isDirectory()) {
                continue;
            }
            DocPath path = null;
            switch (this.element.getKind()) {
                case MODULE:
                    path = DocPath.forModule((ModuleElement)this.element);
                    break;
                case PACKAGE:
                    path = DocPath.forPackage((PackageElement)this.element);
                    break;
                default:
                    throw new AssertionError("unknown kind:" + this.element.getKind());
            }
            copyDirectory(srcdir, path.resolve(DocPaths.DOC_FILES), first);
            first = false;
        }
    }


    private void copyDirectory(DocFile srcdir, final DocPath dstDocPath,
                               boolean first) throws DocFileIOException {
        DocFile dstdir = DocFile.createFileForOutput(configuration, dstDocPath);
        if (srcdir.isSameFile(dstdir)) {
            return;
        }
        for (DocFile srcfile: srcdir.list()) {
            DocFile destfile = dstdir.resolve(srcfile.getName());
            if (srcfile.isFile()) {
                if (destfile.exists() && !first) {
                    configuration.messages.warning("doclet.Copy_Overwrite_warning",
                            srcfile.getPath(), dstdir.getPath());
                } else {
                    if (Utils.toLowerCase(srcfile.getPath()).endsWith(".html")) {
                        if (handleHtmlFile(srcfile, dstDocPath)) {
                            continue;
                        }
                    }
                    configuration.messages.notice("doclet.Copying_File_0_To_Dir_1",
                            srcfile.getPath(), dstdir.getPath());
                    destfile.copyFile(srcfile);
                }
            } else if (srcfile.isDirectory()) {
                if (configuration.copydocfilesubdirs
                        && !configuration.shouldExcludeDocFileDir(srcfile.getName())) {
                    DocPath dirDocPath = dstDocPath.resolve(srcfile.getName());
                    copyDirectory(srcfile, dirDocPath, first);
                }
            }
        }
    }

    private boolean handleHtmlFile(DocFile srcfile, DocPath dstPath) throws DocFileIOException {
        Utils utils = configuration.utils;
        FileObject fileObject = srcfile.getFileObject();
        DocFileElement dfElement = new DocFileElement(element, fileObject);

        if (shouldPassThrough(utils.getPreamble(dfElement))) {
            return false;
        }

        DocPath dfilePath = dstPath.resolve(srcfile.getName());
        HtmlDocletWriter docletWriter = new DocFileWriter(configuration, dfilePath, element);
        configuration.messages.notice("doclet.Generating_0", docletWriter.filename);

        String title = getWindowTitle(docletWriter, dfElement).trim();
        HtmlTree htmlContent = docletWriter.getBody(true, title);
        docletWriter.addTop(htmlContent);
        docletWriter.addNavLinks(true, htmlContent);
        List<? extends DocTree> fullBody = utils.getFullBody(dfElement);
        Content bodyContent = docletWriter.commentTagsToContent(null, dfElement, fullBody, false);

        docletWriter.addTagsInfo(dfElement, bodyContent);
        htmlContent.addContent(bodyContent);

        docletWriter.addNavLinks(false, htmlContent);
        docletWriter.addBottom(htmlContent);
        docletWriter.printHtmlDocument(Collections.emptyList(), false, htmlContent);
        return true;
    }


    private boolean shouldPassThrough(List<? extends DocTree> dtrees) {
        SimpleDocTreeVisitor<Boolean, Boolean> check = new SimpleDocTreeVisitor<Boolean, Boolean>() {
            @Override
            public Boolean visitStartElement(StartElementTree node, Boolean p) {
                if (Utils.toLowerCase(node.getName().toString()).equals((Attr.STYLE.getText()))) {
                    return true;
                }
                if (Utils.toLowerCase(node.getName().toString()).equals(HtmlTag.LINK.getText())) {
                    for (DocTree dt : node.getAttributes()) {
                        if (this.visit(dt, true))
                            return true;
                    }
                }
                return false;
            }

            @Override
            public Boolean visitAttribute(AttributeTree node, Boolean p) {
                if (p == null || p == false) {
                    return false;
                }
                if (Utils.toLowerCase(node.getName().toString()).equals("rel")) {
                    for (DocTree dt :  node.getValue()) {
                        Boolean found = new SimpleDocTreeVisitor<Boolean, ValueKind>() {

                            @Override
                            public Boolean visitText(TextTree node, ValueKind valueKind) {
                                switch (valueKind) {
                                    case EMPTY:
                                        return false;
                                    default:
                                        return Utils.toLowerCase(node.getBody()).equals("stylesheet");
                                }
                            }

                            @Override
                            protected Boolean defaultAction(DocTree node, ValueKind valueKind) {
                                return false;
                            }

                        }.visit(dt, node.getValueKind());

                        if (found)
                            return true;
                    }
                }
                return false;
            }

            @Override
            protected Boolean defaultAction(DocTree node, Boolean p) {
                return false;
            }
        };
        for (DocTree dt : dtrees) {
            if (check.visit(dt, false))
                return true;
        }
        return false;
    }

    private String getWindowTitle(HtmlDocletWriter docletWriter, Element element) {
        List<? extends DocTree> preamble = configuration.utils.getPreamble(element);
        StringBuilder sb = new StringBuilder();
        boolean titleFound = false;
        loop:
        for (DocTree dt : preamble) {
            switch (dt.getKind()) {
                case START_ELEMENT:
                    StartElementTree nodeStart = (StartElementTree)dt;
                    if (Utils.toLowerCase(nodeStart.getName().toString()).equals("title")) {
                        titleFound = true;
                    }
                    break;

                case END_ELEMENT:
                    EndElementTree nodeEnd = (EndElementTree)dt;
                    if (Utils.toLowerCase(nodeEnd.getName().toString()).equals("title")) {
                        break loop;
                    }
                    break;

                case TEXT:
                    TextTree nodeText = (TextTree)dt;
                    if (titleFound)
                        sb.append(nodeText.getBody());
                    break;

                default:
                    // do nothing
            }
        }
        return docletWriter.getWindowTitle(sb.toString().trim());
    }

    private static class DocFileWriter extends HtmlDocletWriter {

        final PackageElement pkg;

        /**
         * Constructor to construct the HtmlDocletWriter object.
         *
         * @param configuration the configuruation of this doclet.
         * @param path          the file to be generated.
         * @param e             the anchoring element.
         */
        public DocFileWriter(HtmlConfiguration configuration, DocPath path, Element e) {
            super(configuration, path);
            switch (e.getKind()) {
                case PACKAGE:
                    pkg = (PackageElement)e;
                    break;
                default:
                    throw new AssertionError("unsupported element: " + e.getKind());
            }
        }

        /**
         * Get the module link.
         *
         * @return a content tree for the module link
         */
        @Override
        protected Content getNavLinkModule() {
            Content linkContent = getModuleLink(utils.elementUtils.getModuleOf(pkg),
                    contents.moduleLabel);
            Content li = HtmlTree.LI(linkContent);
            return li;
        }

        /**
         * Get this package link.
         *
         * @return a content tree for the package link
         */
        @Override
        protected Content getNavLinkPackage() {
            Content linkContent = getPackageLink(pkg,
                    contents.packageLabel);
            Content li = HtmlTree.LI(linkContent);
            return li;
        }
    }
}
