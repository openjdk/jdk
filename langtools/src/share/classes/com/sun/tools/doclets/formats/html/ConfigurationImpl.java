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

import java.net.*;
import java.util.*;

import javax.tools.JavaFileManager;

import com.sun.javadoc.*;
import com.sun.tools.doclets.formats.html.markup.ContentBuilder;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.tools.doclint.DocLint;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javadoc.RootDocImpl;

/**
 * Configure the output based on the command line options.
 * <p>
 * Also determine the length of the command line option. For example,
 * for a option "-header" there will be a string argument associated, then the
 * the length of option "-header" is two. But for option "-nohelp" no argument
 * is needed so it's length is 1.
 * </p>
 * <p>
 * Also do the error checking on the options used. For example it is illegal to
 * use "-helpfile" option when already "-nohelp" option is used.
 * </p>
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Robert Field.
 * @author Atul Dambalkar.
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 */
public class ConfigurationImpl extends Configuration {

    /**
     * The build date.  Note: For now, we will use
     * a version number instead of a date.
     */
    public static final String BUILD_DATE = System.getProperty("java.version");

    /**
     * Argument for command line option "-header".
     */
    public String header = "";

    /**
     * Argument for command line option "-packagesheader".
     */
    public String packagesheader = "";

    /**
     * Argument for command line option "-footer".
     */
    public String footer = "";

    /**
     * Argument for command line option "-doctitle".
     */
    public String doctitle = "";

    /**
     * Argument for command line option "-windowtitle".
     */
    public String windowtitle = "";

    /**
     * Argument for command line option "-top".
     */
    public String top = "";

    /**
     * Argument for command line option "-bottom".
     */
    public String bottom = "";

    /**
     * Argument for command line option "-helpfile".
     */
    public String helpfile = "";

    /**
     * Argument for command line option "-stylesheetfile".
     */
    public String stylesheetfile = "";

    /**
     * Argument for command line option "-Xdocrootparent".
     */
    public String docrootparent = "";

    /**
     * True if command line option "-nohelp" is used. Default value is false.
     */
    public boolean nohelp = false;

    /**
     * True if command line option "-splitindex" is used. Default value is
     * false.
     */
    public boolean splitindex = false;

    /**
     * False if command line option "-noindex" is used. Default value is true.
     */
    public boolean createindex = true;

    /**
     * True if command line option "-use" is used. Default value is false.
     */
    public boolean classuse = false;

    /**
     * False if command line option "-notree" is used. Default value is true.
     */
    public boolean createtree = true;

    /**
     * True if command line option "-nodeprecated" is used. Default value is
     * false.
     */
    public boolean nodeprecatedlist = false;

    /**
     * True if command line option "-nonavbar" is used. Default value is false.
     */
    public boolean nonavbar = false;

    /**
     * True if command line option "-nooverview" is used. Default value is
     * false
     */
    private boolean nooverview = false;

    /**
     * True if command line option "-overview" is used. Default value is false.
     */
    public boolean overview = false;

    /**
     * This is true if option "-overview" is used or option "-overview" is not
     * used and number of packages is more than one.
     */
    public boolean createoverview = false;

    /**
     * Collected set of doclint options
     */
    public Set<String> doclintOpts = new LinkedHashSet<String>();

    /**
     * Unique Resource Handler for this package.
     */
    public final MessageRetriever standardmessage;

    /**
     * First file to appear in the right-hand frame in the generated
     * documentation.
     */
    public DocPath topFile = DocPath.empty;

    /**
     * The classdoc for the class file getting generated.
     */
    public ClassDoc currentcd = null;  // Set this classdoc in the ClassWriter.

    /**
     * Constructor. Initializes resource for the
     * {@link com.sun.tools.doclets.internal.toolkit.util.MessageRetriever MessageRetriever}.
     */
    public ConfigurationImpl() {
        standardmessage = new MessageRetriever(this,
            "com.sun.tools.doclets.formats.html.resources.standard");
    }

    /**
     * Return the build date for the doclet.
     */
    @Override
    public String getDocletSpecificBuildDate() {
        return BUILD_DATE;
    }

    /**
     * Depending upon the command line options provided by the user, set
     * configure the output generation environment.
     *
     * @param options The array of option names and values.
     */
    @Override
    public void setSpecificDocletOptions(String[][] options) {
        for (int oi = 0; oi < options.length; ++oi) {
            String[] os = options[oi];
            String opt = os[0].toLowerCase();
            if (opt.equals("-footer")) {
                footer = os[1];
            } else if (opt.equals("-header")) {
                header = os[1];
            } else if (opt.equals("-packagesheader")) {
                packagesheader = os[1];
            } else if (opt.equals("-doctitle")) {
                doctitle = os[1];
            } else if (opt.equals("-windowtitle")) {
                windowtitle = os[1];
            } else if (opt.equals("-top")) {
                top = os[1];
            } else if (opt.equals("-bottom")) {
                bottom = os[1];
            } else if (opt.equals("-helpfile")) {
                helpfile = os[1];
            } else if (opt.equals("-stylesheetfile")) {
                stylesheetfile = os[1];
            } else if (opt.equals("-charset")) {
                charset = os[1];
            } else if (opt.equals("-xdocrootparent")) {
                docrootparent = os[1];
            } else if (opt.equals("-nohelp")) {
                nohelp = true;
            } else if (opt.equals("-splitindex")) {
                splitindex = true;
            } else if (opt.equals("-noindex")) {
                createindex = false;
            } else if (opt.equals("-use")) {
                classuse = true;
            } else if (opt.equals("-notree")) {
                createtree = false;
            } else if (opt.equals("-nodeprecatedlist")) {
                nodeprecatedlist = true;
            } else if (opt.equals("-nonavbar")) {
                nonavbar = true;
            } else if (opt.equals("-nooverview")) {
                nooverview = true;
            } else if (opt.equals("-overview")) {
                overview = true;
            } else if (opt.equals("-xdoclint")) {
                doclintOpts.add(null);
            } else if (opt.startsWith("-xdoclint:")) {
                doclintOpts.add(opt.substring(opt.indexOf(":") + 1));
            }
        }
        if (root.specifiedClasses().length > 0) {
            Map<String,PackageDoc> map = new HashMap<String,PackageDoc>();
            PackageDoc pd;
            ClassDoc[] classes = root.classes();
            for (int i = 0; i < classes.length; i++) {
                pd = classes[i].containingPackage();
                if(! map.containsKey(pd.name())) {
                    map.put(pd.name(), pd);
                }
            }
        }
        setCreateOverview();
        setTopFile(root);

        if (root instanceof RootDocImpl) {
            ((RootDocImpl) root).initDocLint(doclintOpts);
        }
    }

    /**
     * Returns the "length" of a given option. If an option takes no
     * arguments, its length is one. If it takes one argument, it's
     * length is two, and so on. This method is called by JavaDoc to
     * parse the options it does not recognize. It then calls
     * {@link #validOptions(String[][], DocErrorReporter)} to
     * validate them.
     * <b>Note:</b><br>
     * The options arrive as case-sensitive strings. For options that
     * are not case-sensitive, use toLowerCase() on the option string
     * before comparing it.
     * </blockquote>
     *
     * @return number of arguments + 1 for a option. Zero return means
     * option not known.  Negative value means error occurred.
     */
    public int optionLength(String option) {
        int result = -1;
        if ((result = super.optionLength(option)) > 0) {
            return result;
        }
        // otherwise look for the options we have added
        option = option.toLowerCase();
        if (option.equals("-nodeprecatedlist") ||
            option.equals("-noindex") ||
            option.equals("-notree") ||
            option.equals("-nohelp") ||
            option.equals("-splitindex") ||
            option.equals("-serialwarn") ||
            option.equals("-use") ||
            option.equals("-nonavbar") ||
            option.equals("-nooverview") ||
            option.equals("-xdoclint") ||
            option.startsWith("-xdoclint:")) {
            return 1;
        } else if (option.equals("-help")) {
            // Uugh: first, this should not be hidden inside optionLength,
            // and second, we should not be writing directly to stdout.
            // But we have no access to a DocErrorReporter, which would
            // allow use of reporter.printNotice
            System.out.println(getText("doclet.usage"));
            return 1;
        } else if (option.equals("-x")) {
            // Uugh: first, this should not be hidden inside optionLength,
            // and second, we should not be writing directly to stdout.
            // But we have no access to a DocErrorReporter, which would
            // allow use of reporter.printNotice
            System.out.println(getText("doclet.X.usage"));
            return 1;
        } else if (option.equals("-footer") ||
                   option.equals("-header") ||
                   option.equals("-packagesheader") ||
                   option.equals("-doctitle") ||
                   option.equals("-windowtitle") ||
                   option.equals("-top") ||
                   option.equals("-bottom") ||
                   option.equals("-helpfile") ||
                   option.equals("-stylesheetfile") ||
                   option.equals("-charset") ||
                   option.equals("-overview") ||
                   option.equals("-xdocrootparent")) {
            return 2;
        } else {
            return 0;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean validOptions(String options[][],
            DocErrorReporter reporter) {
        boolean helpfile = false;
        boolean nohelp = false;
        boolean overview = false;
        boolean nooverview = false;
        boolean splitindex = false;
        boolean noindex = false;
        // check shared options
        if (!generalValidOptions(options, reporter)) {
            return false;
        }
        // otherwise look at our options
        for (int oi = 0; oi < options.length; ++oi) {
            String[] os = options[oi];
            String opt = os[0].toLowerCase();
            if (opt.equals("-helpfile")) {
                if (nohelp == true) {
                    reporter.printError(getText("doclet.Option_conflict",
                        "-helpfile", "-nohelp"));
                    return false;
                }
                if (helpfile == true) {
                    reporter.printError(getText("doclet.Option_reuse",
                        "-helpfile"));
                    return false;
                }
                DocFile help = DocFile.createFileForInput(this, os[1]);
                if (!help.exists()) {
                    reporter.printError(getText("doclet.File_not_found", os[1]));
                    return false;
                }
                helpfile = true;
            } else  if (opt.equals("-nohelp")) {
                if (helpfile == true) {
                    reporter.printError(getText("doclet.Option_conflict",
                        "-nohelp", "-helpfile"));
                    return false;
                }
                nohelp = true;
            } else if (opt.equals("-xdocrootparent")) {
                try {
                    new URL(os[1]);
                } catch (MalformedURLException e) {
                    reporter.printError(getText("doclet.MalformedURL", os[1]));
                    return false;
                }
            } else if (opt.equals("-overview")) {
                if (nooverview == true) {
                    reporter.printError(getText("doclet.Option_conflict",
                        "-overview", "-nooverview"));
                    return false;
                }
                if (overview == true) {
                    reporter.printError(getText("doclet.Option_reuse",
                        "-overview"));
                    return false;
                }
                overview = true;
            } else  if (opt.equals("-nooverview")) {
                if (overview == true) {
                    reporter.printError(getText("doclet.Option_conflict",
                        "-nooverview", "-overview"));
                    return false;
                }
                nooverview = true;
            } else if (opt.equals("-splitindex")) {
                if (noindex == true) {
                    reporter.printError(getText("doclet.Option_conflict",
                        "-splitindex", "-noindex"));
                    return false;
                }
                splitindex = true;
            } else if (opt.equals("-noindex")) {
                if (splitindex == true) {
                    reporter.printError(getText("doclet.Option_conflict",
                        "-noindex", "-splitindex"));
                    return false;
                }
                noindex = true;
            } else if (opt.startsWith("-xdoclint:")) {
                if (opt.contains("/")) {
                    reporter.printError(getText("doclet.Option_doclint_no_qualifiers"));
                    return false;
                }
                if (!DocLint.isValidOption(
                        opt.replace("-xdoclint:", DocLint.XMSGS_CUSTOM_PREFIX))) {
                    reporter.printError(getText("doclet.Option_doclint_invalid_arg"));
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MessageRetriever getDocletSpecificMsg() {
        return standardmessage;
    }

    /**
     * Decide the page which will appear first in the right-hand frame. It will
     * be "overview-summary.html" if "-overview" option is used or no
     * "-overview" but the number of packages is more than one. It will be
     * "package-summary.html" of the respective package if there is only one
     * package to document. It will be a class page(first in the sorted order),
     * if only classes are provided on the command line.
     *
     * @param root Root of the program structure.
     */
    protected void setTopFile(RootDoc root) {
        if (!checkForDeprecation(root)) {
            return;
        }
        if (createoverview) {
            topFile = DocPaths.OVERVIEW_SUMMARY;
        } else {
            if (packages.length == 1 && packages[0].name().equals("")) {
                if (root.classes().length > 0) {
                    ClassDoc[] classarr = root.classes();
                    Arrays.sort(classarr);
                    ClassDoc cd = getValidClass(classarr);
                    topFile = DocPath.forClass(cd);
                }
            } else {
                topFile = DocPath.forPackage(packages[0]).resolve(DocPaths.PACKAGE_SUMMARY);
            }
        }
    }

    protected ClassDoc getValidClass(ClassDoc[] classarr) {
        if (!nodeprecated) {
            return classarr[0];
        }
        for (int i = 0; i < classarr.length; i++) {
            if (classarr[i].tags("deprecated").length == 0) {
                return classarr[i];
            }
        }
        return null;
    }

    protected boolean checkForDeprecation(RootDoc root) {
        ClassDoc[] classarr = root.classes();
        for (int i = 0; i < classarr.length; i++) {
            if (isGeneratedDoc(classarr[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generate "overview.html" page if option "-overview" is used or number of
     * packages is more than one. Sets {@link #createoverview} field to true.
     */
    protected void setCreateOverview() {
        if ((overview || packages.length > 1) && !nooverview) {
            createoverview = true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WriterFactory getWriterFactory() {
        return new WriterFactoryImpl(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Comparator<ProgramElementDoc> getMemberComparator() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Locale getLocale() {
        if (root instanceof RootDocImpl)
            return ((RootDocImpl)root).getLocale();
        else
            return Locale.getDefault();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JavaFileManager getFileManager() {
        if (fileManager == null) {
            if (root instanceof RootDocImpl)
                fileManager = ((RootDocImpl) root).getFileManager();
            else
                fileManager = new JavacFileManager(new Context(), false, null);
        }
        return fileManager;
    }

    private JavaFileManager fileManager;

    @Override
    public boolean showMessage(SourcePosition pos, String key) {
        if (root instanceof RootDocImpl) {
            return pos == null || ((RootDocImpl) root).showTagMessages();
        }
        return true;
    }

    @Override
    public Content newContent() {
        return new ContentBuilder();
    }
}
