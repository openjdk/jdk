/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.builders.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * The class with "start" method, calls individual Writers.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Atul M Dambalkar
 * @author Robert Field
 * @author Jamie Ho
 *
 */
public class HtmlDoclet extends AbstractDoclet {
    // An instance will be created by validOptions, and used by start.
    private static HtmlDoclet docletToStart = null;

    public HtmlDoclet() {
        configuration = new ConfigurationImpl();
    }

    /**
     * The global configuration information for this run.
     */
    public final ConfigurationImpl configuration;

    /**
     * The "start" method as required by Javadoc.
     *
     * @param root the root of the documentation tree.
     * @see com.sun.javadoc.RootDoc
     * @return true if the doclet ran without encountering any errors.
     */
    public static boolean start(RootDoc root) {
        // In typical use, options will have been set up by calling validOptions,
        // which will create an HtmlDoclet for use here.
        HtmlDoclet doclet;
        if (docletToStart != null) {
            doclet = docletToStart;
            docletToStart = null;
        } else {
            doclet = new HtmlDoclet();
        }
        return doclet.startDoclet(root);
    }

    /**
     * Create the configuration instance.
     * Override this method to use a different
     * configuration.
     */
    public Configuration configuration() {
        return configuration;
    }

    /**
     * Start the generation of files. Call generate methods in the individual
     * writers, which will in turn genrate the documentation files. Call the
     * TreeWriter generation first to ensure the Class Hierarchy is built
     * first and then can be used in the later generation.
     *
     * For new format.
     *
     * @see com.sun.javadoc.RootDoc
     */
    protected void generateOtherFiles(RootDoc root, ClassTree classtree)
            throws Exception {
        super.generateOtherFiles(root, classtree);
        if (configuration.linksource) {
            SourceToHTMLConverter.convertRoot(configuration,
                root, DocPaths.SOURCE_OUTPUT);
        }

        if (configuration.topFile.isEmpty()) {
            configuration.standardmessage.
                error("doclet.No_Non_Deprecated_Classes_To_Document");
            return;
        }
        boolean nodeprecated = configuration.nodeprecated;
        performCopy(configuration.helpfile);
        performCopy(configuration.stylesheetfile);
        // do early to reduce memory footprint
        if (configuration.classuse) {
            ClassUseWriter.generate(configuration, classtree);
        }
        IndexBuilder indexbuilder = new IndexBuilder(configuration, nodeprecated);

        if (configuration.createtree) {
            TreeWriter.generate(configuration, classtree);
        }
        if (configuration.createindex) {
            configuration.buildSearchTagIndex();
            if (configuration.splitindex) {
                SplitIndexWriter.generate(configuration, indexbuilder);
            } else {
                SingleIndexWriter.generate(configuration, indexbuilder);
            }
        }

        if (!(configuration.nodeprecatedlist || nodeprecated)) {
            DeprecatedListWriter.generate(configuration);
        }

        AllClassesFrameWriter.generate(configuration,
            new IndexBuilder(configuration, nodeprecated, true));

        FrameOutputWriter.generate(configuration);

        if (configuration.createoverview) {
            PackageIndexWriter.generate(configuration);
        }
        if (configuration.helpfile.length() == 0 &&
            !configuration.nohelp) {
            HelpWriter.generate(configuration);
        }
        // If a stylesheet file is not specified, copy the default stylesheet
        // and replace newline with platform-specific newline.
        DocFile f;
        if (configuration.stylesheetfile.length() == 0) {
            f = DocFile.createFileForOutput(configuration, DocPaths.STYLESHEET);
            f.copyResource(DocPaths.RESOURCES.resolve(DocPaths.STYLESHEET), false, true);
        }
        f = DocFile.createFileForOutput(configuration, DocPaths.JAVASCRIPT);
        f.copyResource(DocPaths.RESOURCES.resolve(DocPaths.JAVASCRIPT), true, true);
        if (configuration.createindex) {
            f = DocFile.createFileForOutput(configuration, DocPaths.SEARCH_JS);
            f.copyResource(DocPaths.RESOURCES.resolve(DocPaths.SEARCH_JS), true, true);
            f = DocFile.createFileForOutput(configuration, DocPaths.RESOURCES.resolve(DocPaths.GLASS_IMG));
            f.copyResource(DocPaths.RESOURCES.resolve(DocPaths.GLASS_IMG), true, false);
            f = DocFile.createFileForOutput(configuration, DocPaths.RESOURCES.resolve(DocPaths.X_IMG));
            f.copyResource(DocPaths.RESOURCES.resolve(DocPaths.X_IMG), true, false);
            copyJqueryFiles();
        }
    }

    protected void copyJqueryFiles() {
        List<String> files = Arrays.asList(
                "jquery-1.10.2.js",
                "jquery-ui.js",
                "jquery-ui.css",
                "jquery-ui.min.js",
                "jquery-ui.min.css",
                "jquery-ui.structure.min.css",
                "jquery-ui.structure.css",
                "external/jquery/jquery.js",
                "jszip/dist/jszip.js",
                "jszip/dist/jszip.min.js",
                "jszip-utils/dist/jszip-utils.js",
                "jszip-utils/dist/jszip-utils.min.js",
                "jszip-utils/dist/jszip-utils-ie.js",
                "jszip-utils/dist/jszip-utils-ie.min.js",
                "images/ui-bg_flat_0_aaaaaa_40x100.png",
                "images/ui-icons_454545_256x240.png",
                "images/ui-bg_glass_95_fef1ec_1x400.png",
                "images/ui-bg_glass_75_dadada_1x400.png",
                "images/ui-bg_highlight-soft_75_cccccc_1x100.png",
                "images/ui-icons_888888_256x240.png",
                "images/ui-icons_2e83ff_256x240.png",
                "images/ui-bg_glass_65_ffffff_1x400.png",
                "images/ui-icons_cd0a0a_256x240.png",
                "images/ui-bg_glass_55_fbf9ee_1x400.png",
                "images/ui-icons_222222_256x240.png",
                "images/ui-bg_glass_75_e6e6e6_1x400.png",
                "images/ui-bg_flat_75_ffffff_40x100.png");
        DocFile f;
        for (String file : files) {
            DocPath filePath = DocPaths.JQUERY_FILES.resolve(file);
            f = DocFile.createFileForOutput(configuration, filePath);
            f.copyResource(DocPaths.RESOURCES.resolve(filePath), true, false);
        }
    }

    /**
     * {@inheritDoc}
     */
    protected void generateClassFiles(ClassDoc[] arr, ClassTree classtree) {
        Arrays.sort(arr);
        for(int i = 0; i < arr.length; i++) {
            if (!(configuration.isGeneratedDoc(arr[i]) && arr[i].isIncluded())) {
                continue;
            }
            ClassDoc prev = (i == 0)?
                null:
                arr[i-1];
            ClassDoc curr = arr[i];
            ClassDoc next = (i+1 == arr.length)?
                null:
                arr[i+1];
            try {
                if (curr.isAnnotationType()) {
                    AbstractBuilder annotationTypeBuilder =
                        configuration.getBuilderFactory()
                            .getAnnotationTypeBuilder((AnnotationTypeDoc) curr,
                                prev, next);
                    annotationTypeBuilder.build();
                } else {
                    AbstractBuilder classBuilder =
                        configuration.getBuilderFactory()
                            .getClassBuilder(curr, prev, next, classtree);
                    classBuilder.build();
                }
            } catch (IOException e) {
                throw new DocletAbortException(e);
            } catch (DocletAbortException de) {
                de.printStackTrace();
                throw de;
            } catch (Exception e) {
                e.printStackTrace();
                throw new DocletAbortException(e);
            }
        }
    }

    PackageDoc getNamedPackage(List<PackageDoc> list, int idx) {
        if (idx < list.size()) {
            PackageDoc pkg = list.get(idx);
            if (!pkg.name().isEmpty()) {
                return pkg;
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    protected void generatePackageFiles(ClassTree classtree) throws Exception {
        Set<PackageDoc> packages = configuration.packages;
        if (packages.size() > 1) {
            PackageIndexFrameWriter.generate(configuration);
        }
        List<PackageDoc> pList = new ArrayList<>(configuration.packages);
        PackageDoc prev = null, next;
        for (int i = 0; i < pList.size(); i++) {
            // if -nodeprecated option is set and the package is marked as
            // deprecated, do not generate the package-summary.html, package-frame.html
            // and package-tree.html pages for that package.
            PackageDoc pkg = pList.get(i);
            if (!(configuration.nodeprecated && utils.isDeprecated(pkg))) {
                PackageFrameWriter.generate(configuration, pkg);
                next = getNamedPackage(pList, i + 1);
                //If the next package is unnamed package, skip 2 ahead if possible
                if (next == null)
                    next = getNamedPackage(pList, i + 2);
                AbstractBuilder packageSummaryBuilder =
                        configuration.getBuilderFactory().getPackageSummaryBuilder(
                        pkg, prev, next);
                packageSummaryBuilder.build();
                if (configuration.createtree) {
                    PackageTreeWriter.generate(configuration,
                            pkg, prev, next,
                            configuration.nodeprecated);
                }
                prev = pkg;
            }
        }
    }

    public static final ConfigurationImpl sharedInstanceForOptions =
            new ConfigurationImpl();

    /**
     * Check for doclet added options here.
     *
     * @return number of arguments to option. Zero return means
     * option not known.  Negative value means error occurred.
     */
    public static int optionLength(String option) {
        // Construct temporary configuration for check
        return sharedInstanceForOptions.optionLength(option);
    }

    /**
     * Check that options have the correct arguments here.
     * <P>
     * This method is not required and will default gracefully
     * (to true) if absent.
     * <P>
     * Printing option related error messages (using the provided
     * DocErrorReporter) is the responsibility of this method.
     *
     * @return true if the options are valid.
     */
    public static boolean validOptions(String options[][],
            DocErrorReporter reporter) {
        docletToStart = new HtmlDoclet();
        return docletToStart.configuration.validOptions(options, reporter);
    }

    private void performCopy(String filename) {
        if (filename.isEmpty())
            return;

        try {
            DocFile fromfile = DocFile.createFileForInput(configuration, filename);
            DocPath path = DocPath.create(fromfile.getName());
            DocFile toFile = DocFile.createFileForOutput(configuration, path);
            if (toFile.isSameFile(fromfile))
                return;

            configuration.message.notice((SourcePosition) null,
                    "doclet.Copying_File_0_To_File_1",
                    fromfile.toString(), path.getPath());
            toFile.copyFile(fromfile);
        } catch (IOException exc) {
            configuration.message.error((SourcePosition) null,
                    "doclet.perform_copy_exception_encountered",
                    exc.toString());
            throw new DocletAbortException(exc);
        }
    }
}
