/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.EndElementTree;
import com.sun.source.doctree.StartElementTree;
import com.sun.source.util.DocTreeFactory;
import jdk.javadoc.internal.doclets.formats.html.markup.BodyContents;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.toolkit.DocFileElement;
import jdk.javadoc.internal.doclets.toolkit.DocletException;
import jdk.javadoc.internal.doclets.toolkit.util.DocFile;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;
import jdk.javadoc.internal.doclint.HtmlTag;

import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.tools.JavaFileManager.Location;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;

/**
 * A class to handle any files, including HTML files, found in the {@code doc-files}
 * subdirectory for any given package.
 */
public class DocFilesHandler {

    private final Element element;
    private final Location location;
    private final DocPath  source;
    private final HtmlConfiguration configuration;
    private final HtmlOptions options;
    private final Utils utils;
    private final WriterFactory writerFactory;

    /**
     * Constructor to construct the DocFilesHandler object.
     *
     * @param configuration the configuration of this doclet.
     * @param element the containing element of the doc-files.
     *
     * @see WriterFactory#newDocFilesHandler(Element)
     */
    public DocFilesHandler(HtmlConfiguration configuration, Element element) {
        this.configuration = configuration;
        this.options = configuration.getOptions();
        this.utils = configuration.utils;
        this.writerFactory = configuration.getWriterFactory();
        this.element = element;

        switch (element.getKind()) {
            case MODULE -> {
                ModuleElement mdle = (ModuleElement) element;
                location = utils.getLocationForModule(mdle);
                source = DocPaths.DOC_FILES;
            }

            case PACKAGE -> {
                PackageElement pkg = (PackageElement) element;
                location = utils.getLocationForPackage(pkg);
                // Note, given that we have a module-specific location,
                // we want a module-relative path for the source, and not the
                // standard path that may include the module directory
                source = DocPath.create(pkg.getQualifiedName().toString().replace('.', '/'))
                        .resolve(DocPaths.DOC_FILES);
            }

            default -> throw new AssertionError("unsupported element " + element);
        }
    }

    /**
     * Copy doc-files directory and its contents from the source
     * elements directory to the generated documentation directory.
     *
     * @throws DocletException if there is a problem while copying
     *         the documentation files
     */
    public void copyDocFiles() throws DocletException {
        boolean first = true;
        for (DocFile srcdir : DocFile.list(configuration, location, source)) {
            if (!srcdir.isDirectory()) {
                continue;
            }
            DocPath path = switch (this.element.getKind()) {
                case MODULE -> DocPaths.forModule((ModuleElement) this.element);
                case PACKAGE -> configuration.docPaths.forPackage((PackageElement) this.element);
                default -> throw new AssertionError("unknown kind:" + this.element.getKind());
            };
            copyDirectory(srcdir, path.resolve(DocPaths.DOC_FILES), first);
            first = false;
        }
    }

    public List<DocPath> getStylesheets() throws DocFileIOException {
        var stylesheets = new ArrayList<DocPath>();
        for (DocFile srcdir : DocFile.list(configuration, location, source)) {
            for (DocFile srcFile : srcdir.list()) {
                if (srcFile.getName().endsWith(".css"))
                    stylesheets.add(DocPaths.DOC_FILES.resolve(srcFile.getName()));
            }
        }
        return stylesheets;
    }

    private void copyDirectory(DocFile srcdir, final DocPath dstDocPath,
                               boolean first) throws DocletException {
        DocFile dstdir = DocFile.createFileForOutput(configuration, dstDocPath);
        if (srcdir.isSameFile(dstdir)) {
            return;
        }
        for (DocFile srcfile: srcdir.list()) {
            // ensure that the name is a valid component in an eventual full path
            // and so avoid an equivalent check lower down in the file manager
            // that throws IllegalArgumentException
            if (!isValidFilename(srcfile)) {
                configuration.messages.warning("doclet.Copy_Ignored_warning",
                        srcfile.getPath());
                continue;
            }

            DocFile destfile = dstdir.resolve(srcfile.getName());
            if (srcfile.isFile()) {
                if (destfile.exists() && !first) {
                    configuration.messages.warning("doclet.Copy_Overwrite_warning",
                            srcfile.getPath(), dstdir.getPath());
                } else {
                    var path = Utils.toLowerCase(srcfile.getPath());
                    if (path.endsWith(".html") || path.endsWith(".md")) {
                        handleDocFile(srcfile, dstDocPath);
                    } else {
                        configuration.messages.notice("doclet.Copying_File_0_To_Dir_1",
                                srcfile.getPath(), dstdir.getPath());
                        destfile.copyFile(srcfile);
                    }
                }
            } else if (srcfile.isDirectory()) {
                if (options.copyDocfileSubdirs()
                        && !configuration.shouldExcludeDocFileDir(srcfile.getName())) {
                    DocPath dirDocPath = dstDocPath.resolve(srcfile.getName());
                    copyDirectory(srcfile, dirDocPath, first);
                }
            }
        }
    }

    private boolean isValidFilename(DocFile f) {
        try {
            String n = f.getName();
            URI u = new URI(n);
            return u.getPath().equals(n);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private void handleDocFile(DocFile srcFile, DocPath dstPath) throws DocletException {
        var fileObject = srcFile.getFileObject();
        var dfElement = new DocFileElement(utils, element, fileObject);
        var path = dstPath.resolve(srcFile.getName().replaceAll("\\.[a-z]+$", ".html"));

        writerFactory.newDocFileWriter(path, dfElement).buildPage();
    }

    /**
     * A writer to write out the processed form of an HTML file found in the {@code doc-files} subdirectory
     * for a module or package.
     */
    public static class DocFileWriter extends HtmlDocletWriter {
        private final DocFileElement dfElement;

        /**
         * Constructor.
         *
         * @param configuration the configuration of this doclet
         * @param path          the file to be generated
         * @param dfElement     the element representing the doc file
         */
        public DocFileWriter(HtmlConfiguration configuration, DocPath path, DocFileElement dfElement) {
            super(configuration, path);
            this.dfElement = dfElement;
        }

        @Override
        public void buildPage() throws DocFileIOException {

            List<? extends DocTree> localTags = getLocalHeaderTags(utils.getPreamble(dfElement));
            Content localTagsContent = commentTagsToContent(dfElement, localTags, false);

            String title = getWindowTitle(this, dfElement).trim();
            HtmlTree htmlContent = getBody(title);

            List<? extends DocTree> fullBody = utils.getFullBody(dfElement);
            Content pageContent = commentTagsToContent(dfElement, fullBody, false);
            addTagsInfo(dfElement, pageContent);

            htmlContent.add(new BodyContents()
                    .setHeader(getHeader(PageMode.DOC_FILE, dfElement.getElement()))
                    .addMainContent(pageContent)
                    .setFooter(getFooter()));
            printHtmlDocument(List.of(), null, localTagsContent, List.of(), htmlContent);
        }

        private String getWindowTitle(HtmlDocletWriter docletWriter, DocFileElement element) {
            var t = docletWriter.getFileTitle(element);
            return docletWriter.getWindowTitle(t);
        }

        private List<? extends DocTree> getLocalHeaderTags(List<? extends DocTree> dtrees) {
            List<DocTree> localTags = new ArrayList<>();
            DocTreeFactory docTreeFactory = configuration.docEnv.getDocTrees().getDocTreeFactory();
            boolean inHead = false;
            boolean inTitle = false;
            loop:
            for (DocTree dt : dtrees) {
                switch (dt.getKind()) {
                    case START_ELEMENT:
                        StartElementTree startElem = (StartElementTree)dt;
                        switch (HtmlTag.get(startElem.getName())) {
                            case HEAD:
                                inHead = true;
                                break;
                            case META:
                                break;
                            case TITLE:
                                inTitle = true;
                                break;
                            default:
                                if (inHead) {
                                    localTags.add(startElem);
                                    localTags.add(docTreeFactory.newTextTree("\n"));
                                }
                        }
                        break;
                    case END_ELEMENT:
                        EndElementTree endElem = (EndElementTree)dt;
                        switch (HtmlTag.get(endElem.getName())) {
                            case HEAD:
                                inHead = false;
                                break loop;
                            case TITLE:
                                inTitle = false;
                                break;
                            default:
                                if (inHead) {
                                    localTags.add(endElem);
                                    localTags.add(docTreeFactory.newTextTree("\n"));
                                }
                        }
                        break;
                    case ENTITY:
                    case TEXT:
                        if (inHead && !inTitle) {
                            localTags.add(dt);
                        }
                        break;
                }
            }
            return localTags;
        }

        @Override
        public boolean isIndexable() {
            return true;
        }
    }

}
