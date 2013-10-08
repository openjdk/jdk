/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.tools.javac.sym.Profiles;
import com.sun.tools.javac.jvm.Profile;
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
        return doclet.start(doclet, root);
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
        copyResourceFile("background.gif");
        copyResourceFile("tab.gif");
        copyResourceFile("titlebar.gif");
        copyResourceFile("titlebar_end.gif");
        copyResourceFile("activetitlebar.gif");
        copyResourceFile("activetitlebar_end.gif");
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
        DocFile f;
        if (configuration.stylesheetfile.length() == 0) {
            f = DocFile.createFileForOutput(configuration, DocPaths.STYLESHEET);
            f.copyResource(DocPaths.RESOURCES.resolve(DocPaths.STYLESHEET), false, true);
        }
        f = DocFile.createFileForOutput(configuration, DocPaths.JAVASCRIPT);
        f.copyResource(DocPaths.RESOURCES.resolve(DocPaths.JAVASCRIPT), true, true);
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
                throw de;
            } catch (Exception e) {
                e.printStackTrace();
                throw new DocletAbortException(e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    protected void generateProfileFiles() throws Exception {
        if (configuration.showProfiles && configuration.profilePackages.size() > 0) {
            ProfileIndexFrameWriter.generate(configuration);
            Profile prevProfile = null, nextProfile;
            String profileName;
            for (int i = 1; i < configuration.profiles.getProfileCount(); i++) {
                profileName = Profile.lookup(i).name;
                // Generate profile package pages only if there are any packages
                // in a profile to be documented. The profilePackages map will not
                // contain an entry for the profile if there are no packages to be documented.
                if (!configuration.shouldDocumentProfile(profileName))
                    continue;
                ProfilePackageIndexFrameWriter.generate(configuration, profileName);
                PackageDoc[] packages = configuration.profilePackages.get(
                        profileName);
                PackageDoc prev = null, next;
                for (int j = 0; j < packages.length; j++) {
                    // if -nodeprecated option is set and the package is marked as
                    // deprecated, do not generate the profilename-package-summary.html
                    // and profilename-package-frame.html pages for that package.
                    if (!(configuration.nodeprecated && Util.isDeprecated(packages[j]))) {
                        ProfilePackageFrameWriter.generate(configuration, packages[j], i);
                        next = (j + 1 < packages.length
                                && packages[j + 1].name().length() > 0) ? packages[j + 1] : null;
                        AbstractBuilder profilePackageSummaryBuilder =
                                configuration.getBuilderFactory().getProfilePackageSummaryBuilder(
                                packages[j], prev, next, Profile.lookup(i));
                        profilePackageSummaryBuilder.build();
                        prev = packages[j];
                    }
                }
                nextProfile = (i + 1 < configuration.profiles.getProfileCount()) ?
                        Profile.lookup(i + 1) : null;
                AbstractBuilder profileSummaryBuilder =
                        configuration.getBuilderFactory().getProfileSummaryBuilder(
                        Profile.lookup(i), prevProfile, nextProfile);
                profileSummaryBuilder.build();
                prevProfile = Profile.lookup(i);
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

    /**
     * Copy a file in the resources directory to the destination directory.
     * @param resource   The name of the resource file to copy
     */
    private void copyResourceFile(String resource) {
        DocPath p = DocPaths.RESOURCES.resolve(resource);
        DocFile f = DocFile.createFileForOutput(configuration, p);
        f.copyResource(p, false, false);
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
