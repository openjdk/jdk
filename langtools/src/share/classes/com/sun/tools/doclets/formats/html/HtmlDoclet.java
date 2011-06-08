/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.builders.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

import com.sun.javadoc.*;
import java.util.*;
import java.io.*;

/**
 * The class with "start" method, calls individual Writers.
 *
 * @author Atul M Dambalkar
 * @author Robert Field
 * @author Jamie Ho
 *
 */
public class HtmlDoclet extends AbstractDoclet {
    public HtmlDoclet() {
        configuration = (ConfigurationImpl) configuration();
    }

    /**
     * The global configuration information for this run.
     */
    public ConfigurationImpl configuration;

    /**
     * The "start" method as required by Javadoc.
     *
     * @param root the root of the documentation tree.
     * @see com.sun.javadoc.RootDoc
     * @return true if the doclet ran without encountering any errors.
     */
    public static boolean start(RootDoc root) {
        try {
            HtmlDoclet doclet = new HtmlDoclet();
            return doclet.start(doclet, root);
        } finally {
            ConfigurationImpl.reset();
        }
    }

    /**
     * Create the configuration instance.
     * Override this method to use a different
     * configuration.
     */
    public Configuration configuration() {
        return ConfigurationImpl.getInstance();
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
            if (configuration.destDirName.length() > 0) {
                SourceToHTMLConverter.convertRoot(configuration,
                    root, configuration.destDirName + File.separator
                    + DocletConstants.SOURCE_OUTPUT_DIR_NAME);
            } else {
                SourceToHTMLConverter.convertRoot(configuration,
                    root, DocletConstants.SOURCE_OUTPUT_DIR_NAME);
            }
        }

        if (configuration.topFile.length() == 0) {
            configuration.standardmessage.
                error("doclet.No_Non_Deprecated_Classes_To_Document");
            return;
        }
        boolean nodeprecated = configuration.nodeprecated;
        String configdestdir = configuration.destDirName;
        String confighelpfile = configuration.helpfile;
        String configstylefile = configuration.stylesheetfile;
        performCopy(configdestdir, confighelpfile);
        performCopy(configdestdir, configstylefile);
        Util.copyResourceFile(configuration, "background.gif", false);
        Util.copyResourceFile(configuration, "tab.gif", false);
        Util.copyResourceFile(configuration, "titlebar.gif", false);
        Util.copyResourceFile(configuration, "titlebar_end.gif", false);
        // do early to reduce memory footprint
        if (configuration.classuse) {
            ClassUseWriter.generate(configuration, classtree);
        }
        IndexBuilder indexbuilder = new IndexBuilder(configuration, nodeprecated);

        if (configuration.createtree) {
            TreeWriter.generate(configuration, classtree);
        }
        if (configuration.createindex) {
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
        if (configuration.stylesheetfile.length() == 0) {
            Util.copyFile(configuration, "stylesheet.css", Util.RESOURCESDIR,
                    (configdestdir.isEmpty()) ?
                        System.getProperty("user.dir") : configdestdir, false, true);
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
            } catch (Exception e) {
                e.printStackTrace();
                throw new DocletAbortException();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    protected void generatePackageFiles(ClassTree classtree) throws Exception {
        PackageDoc[] packages = configuration.packages;
        if (packages.length > 1) {
            PackageIndexFrameWriter.generate(configuration);
        }
        PackageDoc prev = null, next;
        for (int i = 0; i < packages.length; i++) {
            // if -nodeprecated option is set and the package is marked as
            // deprecated, do not generate the package-summary.html, package-frame.html
            // and package-tree.html pages for that package.
            if (!(configuration.nodeprecated && Util.isDeprecated(packages[i]))) {
                PackageFrameWriter.generate(configuration, packages[i]);
                next = (i + 1 < packages.length &&
                        packages[i + 1].name().length() > 0) ? packages[i + 1] : null;
                //If the next package is unnamed package, skip 2 ahead if possible
                next = (i + 2 < packages.length && next == null) ? packages[i + 2] : next;
                AbstractBuilder packageSummaryBuilder =
                        configuration.getBuilderFactory().getPackageSummaryBuilder(
                        packages[i], prev, next);
                packageSummaryBuilder.build();
                if (configuration.createtree) {
                    PackageTreeWriter.generate(configuration,
                            packages[i], prev, next,
                            configuration.nodeprecated);
                }
                prev = packages[i];
            }
        }
    }

    /**
     * Check for doclet added options here.
     *
     * @return number of arguments to option. Zero return means
     * option not known.  Negative value means error occurred.
     */
    public static int optionLength(String option) {
        // Construct temporary configuration for check
        return (ConfigurationImpl.getInstance()).optionLength(option);
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
        // Construct temporary configuration for check
        return (ConfigurationImpl.getInstance()).validOptions(options, reporter);
    }

    private void performCopy(String configdestdir, String filename) {
        try {
            String destdir = (configdestdir.length() > 0) ?
                configdestdir + File.separatorChar: "";
            if (filename.length() > 0) {
                File helpstylefile = new File(filename);
                String parent = helpstylefile.getParent();
                String helpstylefilename = (parent == null)?
                    filename:
                    filename.substring(parent.length() + 1);
                File desthelpfile = new File(destdir + helpstylefilename);
                if (!desthelpfile.getCanonicalPath().equals(
                        helpstylefile.getCanonicalPath())) {
                    configuration.message.
                        notice((SourcePosition) null,
                            "doclet.Copying_File_0_To_File_1",
                            helpstylefile.toString(), desthelpfile.toString());
                    Util.copyFile(desthelpfile, helpstylefile);
                }
            }
        } catch (IOException exc) {
            configuration.message.
                error((SourcePosition) null,
                    "doclet.perform_copy_exception_encountered",
                    exc.toString());
            throw new DocletAbortException();
        }
    }
}
