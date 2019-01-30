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

import java.net.*;
import java.util.*;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;

import com.sun.source.util.DocTreePath;
import com.sun.tools.doclint.DocLint;

import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlConstants;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlVersion;
import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.DocletException;
import jdk.javadoc.internal.doclets.toolkit.Messages;
import jdk.javadoc.internal.doclets.toolkit.Resources;
import jdk.javadoc.internal.doclets.toolkit.WriterFactory;
import jdk.javadoc.internal.doclets.toolkit.util.DocFile;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

import static javax.tools.Diagnostic.Kind.*;

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
public class HtmlConfiguration extends BaseConfiguration {

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
     * Argument for command line option "--add-stylesheet".
     */
    public List<String> additionalStylesheets = new ArrayList<>();

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
     * The META charset tag used for cross-platform viewing.
     */
    public String charset = null;

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
     * The overview path specified with "-overview" flag.
     */
    public String overviewpath = null;

    /**
     * This is true if option "-overview" is used or option "-overview" is not
     * used and number of packages is more than one.
     */
    public boolean createoverview = false;

    /**
     * Specifies whether or not frames should be generated.
     * Defaults to false; can be set to true by --frames; can be set to false by --no-frames; last one wins.
     */
    public boolean frames = false;

    /**
     * Collected set of doclint options
     */
    public Map<Doclet.Option, String> doclintOpts = new LinkedHashMap<>();

    public final Resources resources;

    /**
     * First file to appear in the right-hand frame in the generated
     * documentation.
     */
    public DocPath topFile = DocPath.empty;

    /**
     * The TypeElement for the class file getting generated.
     */
    public TypeElement currentTypeElement = null;  // Set this TypeElement in the ClassWriter.

    protected SortedSet<SearchIndexItem> memberSearchIndex;

    protected SortedSet<SearchIndexItem> moduleSearchIndex;

    protected SortedSet<SearchIndexItem> packageSearchIndex;

    protected SortedSet<SearchIndexItem> tagSearchIndex;

    protected SortedSet<SearchIndexItem> typeSearchIndex;

    protected Map<Character,List<SearchIndexItem>> tagSearchIndexMap = new HashMap<>();

    protected Set<Character> tagSearchIndexKeys;

    public final Contents contents;

    protected final Messages messages;

    public DocPaths docPaths;

    /**
     * Creates an object to hold the configuration for a doclet.
     *
     * @param doclet the doclet
     */
    public HtmlConfiguration(Doclet doclet) {
        super(doclet);
        resources = new Resources(this,
                BaseConfiguration.sharedResourceBundleName,
                "jdk.javadoc.internal.doclets.formats.html.resources.standard");

        messages = new Messages(this);
        contents = new Contents(this);

        String v;
        try {
            ResourceBundle rb = ResourceBundle.getBundle(versionBundleName, getLocale());
            try {
                v = rb.getString("release");
            } catch (MissingResourceException e) {
                v = defaultDocletVersion;
            }
        } catch (MissingResourceException e) {
            v = defaultDocletVersion;
        }
        docletVersion = v;
    }

    private static final String versionBundleName = "jdk.javadoc.internal.tool.resources.version";
    private static final String defaultDocletVersion = System.getProperty("java.version");
    public final String docletVersion;

    @Override
    public String getDocletVersion() {
        return docletVersion;
    }

    @Override
    public Resources getResources() {
        return resources;
    }

    public Contents getContents() {
        return contents;
    }

    @Override
    public Messages getMessages() {
        return messages;
    }

    protected boolean validateOptions() {
        // check shared options
        if (!generalValidOptions()) {
            return false;
        }

        // check if helpfile exists
        if (!helpfile.isEmpty()) {
            DocFile help = DocFile.createFileForInput(this, helpfile);
            if (!help.exists()) {
                reporter.print(ERROR, resources.getText("doclet.File_not_found", helpfile));
                return false;
            }
        }
        // check if stylesheetfile exists
        if (!stylesheetfile.isEmpty()) {
            DocFile stylesheet = DocFile.createFileForInput(this, stylesheetfile);
            if (!stylesheet.exists()) {
                reporter.print(ERROR, resources.getText("doclet.File_not_found", stylesheetfile));
                return false;
            }
        }
        // check if additional stylesheets exists
        for (String ssheet : additionalStylesheets) {
            DocFile ssfile = DocFile.createFileForInput(this, ssheet);
            if (!ssfile.exists()) {
                reporter.print(ERROR, resources.getText("doclet.File_not_found", ssheet));
                return false;
            }
        }

        // In a more object-oriented world, this would be done by methods on the Option objects.
        // Note that -windowtitle silently removes any and all HTML elements, and so does not need
        // to be handled here.
        utils.checkJavaScriptInOption("-header", header);
        utils.checkJavaScriptInOption("-footer", footer);
        utils.checkJavaScriptInOption("-top", top);
        utils.checkJavaScriptInOption("-bottom", bottom);
        utils.checkJavaScriptInOption("-doctitle", doctitle);
        utils.checkJavaScriptInOption("-packagesheader", packagesheader);

        return true;
    }


    @Override
    public boolean finishOptionSettings() {
        if (!validateOptions()) {
            return false;
        }
        if (!getSpecifiedTypeElements().isEmpty()) {
            Map<String, PackageElement> map = new HashMap<>();
            PackageElement pkg;
            for (TypeElement aClass : getIncludedTypeElements()) {
                pkg = utils.containingPackage(aClass);
                if (!map.containsKey(utils.getPackageName(pkg))) {
                    map.put(utils.getPackageName(pkg), pkg);
                }
            }
        }
        docPaths = new DocPaths(utils, useModuleDirectories);
        setCreateOverview();
        setTopFile(docEnv);
        workArounds.initDocLint(doclintOpts.values(), tagletManager.getAllTagletNames(),
                Utils.toLowerCase(HtmlVersion.HTML5.name()));
        return true;
    }

    /**
     * Decide the page which will appear first in the right-hand frame. It will
     * be "overview-summary.html" if "-overview" option is used or no
     * "-overview" but the number of packages is more than one. It will be
     * "package-summary.html" of the respective package if there is only one
     * package to document. It will be a class page(first in the sorted order),
     * if only classes are provided on the command line.
     *
     * @param docEnv the doclet environment
     */
    protected void setTopFile(DocletEnvironment docEnv) {
        if (!checkForDeprecation(docEnv)) {
            return;
        }
        if (createoverview) {
            topFile = DocPaths.overviewSummary(frames);
        } else {
            if (showModules) {
                topFile = DocPath.empty.resolve(docPaths.moduleSummary(modules.first()));
            } else if (packages.size() == 1 && packages.first().isUnnamed()) {
                List<TypeElement> classes = new ArrayList<>(getIncludedTypeElements());
                if (!classes.isEmpty()) {
                    TypeElement te = getValidClass(classes);
                    topFile = docPaths.forClass(te);
                }
            } else if (!packages.isEmpty()) {
                topFile = docPaths.forPackage(packages.first()).resolve(DocPaths.PACKAGE_SUMMARY);
            }
        }
    }

    protected TypeElement getValidClass(List<TypeElement> classes) {
        if (!nodeprecated) {
            return classes.get(0);
        }
        for (TypeElement te : classes) {
            if (!utils.isDeprecated(te)) {
                return te;
            }
        }
        return null;
    }

    protected boolean checkForDeprecation(DocletEnvironment docEnv) {
        for (TypeElement te : getIncludedTypeElements()) {
            if (isGeneratedDoc(te)) {
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
        if (!nooverview) {
            if (overviewpath != null
                    || modules.size() > 1
                    || (modules.isEmpty() && packages.size() > 1)) {
                createoverview = true;
            }
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
    public Locale getLocale() {
        if (locale == null)
            return Locale.getDefault();
        return locale;
    }

    /**
     * Return the path of the overview file or null if it does not exist.
     *
     * @return the path of the overview file or null if it does not exist.
     */
    @Override
    public JavaFileObject getOverviewPath() {
        if (overviewpath != null && getFileManager() instanceof StandardJavaFileManager) {
            StandardJavaFileManager fm = (StandardJavaFileManager) getFileManager();
            return fm.getJavaFileObjects(overviewpath).iterator().next();
        }
        return null;
    }

    public DocFile getMainStylesheet() {
        return stylesheetfile.isEmpty() ? null : DocFile.createFileForInput(this, stylesheetfile);
    }

    public List<DocFile> getAdditionalStylesheets() {
        return additionalStylesheets.stream()
                .map(ssf -> DocFile.createFileForInput(this, ssf))
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JavaFileManager getFileManager() {
        return docEnv.getJavaFileManager();
    }

    @Override
    public boolean showMessage(DocTreePath path, String key) {
        return (path == null || workArounds.haveDocLint());
    }

    @Override
    public boolean showMessage(Element e, String key) {
        return (e == null || workArounds.haveDocLint());
    }

    protected void buildSearchTagIndex() {
        for (SearchIndexItem sii : tagSearchIndex) {
            String tagLabel = sii.getLabel();
            Character unicode = (tagLabel.length() == 0)
                    ? '*'
                    : Character.toUpperCase(tagLabel.charAt(0));
            List<SearchIndexItem> list = tagSearchIndexMap.get(unicode);
            if (list == null) {
                list = new ArrayList<>();
                tagSearchIndexMap.put(unicode, list);
            }
            list.add(sii);
        }
        tagSearchIndexKeys = tagSearchIndexMap.keySet();
    }

    @Override
    public Set<Doclet.Option> getSupportedOptions() {
        Resources resources = getResources();
        Doclet.Option[] options = {
            new Option(resources, "--add-stylesheet", 1) {
                @Override
                public boolean process(String opt, List<String> args) {
                    additionalStylesheets.add(args.get(0));
                    return true;
                }
            },
            new Option(resources, "-bottom", 1) {
                @Override
                public boolean process(String opt,  List<String> args) {
                    bottom = args.get(0);
                    return true;
                }
            },
            new Option(resources, "-charset", 1) {
                @Override
                public boolean process(String opt,  List<String> args) {
                    charset = args.get(0);
                    return true;
                }
            },
            new Option(resources, "-doctitle", 1) {
                @Override
                public boolean process(String opt,  List<String> args) {
                    doctitle = args.get(0);
                    return true;
                }
            },
            new Option(resources, "-footer", 1) {
                @Override
                public boolean process(String opt, List<String> args) {
                    footer = args.get(0);
                    return true;
                }
            },
            new Option(resources, "-header", 1) {
                @Override
                public boolean process(String opt,  List<String> args) {
                    header = args.get(0);
                    return true;
                }
            },
            new Option(resources, "-helpfile", 1) {
                @Override
                public boolean process(String opt,  List<String> args) {
                    if (nohelp == true) {
                        reporter.print(ERROR, resources.getText("doclet.Option_conflict",
                                "-helpfile", "-nohelp"));
                        return false;
                    }
                    if (!helpfile.isEmpty()) {
                        reporter.print(ERROR, resources.getText("doclet.Option_reuse",
                                "-helpfile"));
                        return false;
                    }
                    helpfile = args.get(0);
                    return true;
                }
            },
            new Option(resources, "-html5") {
                @Override
                public boolean process(String opt,  List<String> args) {
                    return true;
                }
            },
            new Option(resources, "-nohelp") {
                @Override
                public boolean process(String opt, List<String> args) {
                    nohelp = true;
                    if (!helpfile.isEmpty()) {
                        reporter.print(ERROR, resources.getText("doclet.Option_conflict",
                                "-nohelp", "-helpfile"));
                        return false;
                    }
                    return true;
                }
            },
            new Option(resources, "-nodeprecatedlist") {
                @Override
                public boolean process(String opt,  List<String> args) {
                    nodeprecatedlist = true;
                    return true;
                }
            },
            new Option(resources, "-noindex") {
                @Override
                public boolean process(String opt,  List<String> args) {
                    createindex = false;
                    if (splitindex == true) {
                        reporter.print(ERROR, resources.getText("doclet.Option_conflict",
                                "-noindex", "-splitindex"));
                        return false;
                    }
                    return true;
                }
            },
            new Option(resources, "-nonavbar") {
                @Override
                public boolean process(String opt,  List<String> args) {
                    nonavbar = true;
                    return true;
                }
            },
            new Hidden(resources, "-nooverview") {
                @Override
                public boolean process(String opt,  List<String> args) {
                    nooverview = true;
                    if (overviewpath != null) {
                        reporter.print(ERROR, resources.getText("doclet.Option_conflict",
                                "-nooverview", "-overview"));
                        return false;
                    }
                    return true;
                }
            },
            new Option(resources, "-notree") {
                @Override
                public boolean process(String opt,  List<String> args) {
                    createtree = false;
                    return true;
                }
            },
            new Option(resources, "-overview", 1) {
                @Override
                public boolean process(String opt,  List<String> args) {
                    overviewpath = args.get(0);
                    if (nooverview == true) {
                        reporter.print(ERROR, resources.getText("doclet.Option_conflict",
                                "-overview", "-nooverview"));
                        return false;
                    }
                    return true;
                }
            },
            new Option(resources, "--frames") {
                @Override
                public boolean process(String opt,  List<String> args) {
                    reporter.print(WARNING, resources.getText("doclet.Frames_specified", helpfile));
                    frames = true;
                    return true;
                }
            },
            new Option(resources, "--no-frames") {
                @Override
                public boolean process(String opt,  List<String> args) {
                    frames = false;
                    return true;
                }
            },
            new Hidden(resources, "-packagesheader", 1) {
                @Override
                public boolean process(String opt,  List<String> args) {
                    packagesheader = args.get(0);
                    return true;
                }
            },
            new Option(resources, "-splitindex") {
                @Override
                public boolean process(String opt, List<String> args) {
                    splitindex = true;
                    if (createindex == false) {
                        reporter.print(ERROR, resources.getText("doclet.Option_conflict",
                                "-splitindex", "-noindex"));
                        return false;
                    }
                    return true;
                }
            },
            new Option(resources, "--main-stylesheet -stylesheetfile", 1) {
                @Override
                public boolean process(String opt,  List<String> args) {
                    stylesheetfile = args.get(0);
                    return true;
                }
            },
            new Option(resources, "-top", 1) {
                @Override
                public boolean process(String opt,  List<String> args) {
                    top = args.get(0);
                    return true;
                }
            },
            new Option(resources, "-use") {
                @Override
                public boolean process(String opt,  List<String> args) {
                    classuse = true;
                    return true;
                }
            },
            new Option(resources, "-windowtitle", 1) {
                @Override
                public boolean process(String opt,  List<String> args) {
                    windowtitle = args.get(0).replaceAll("\\<.*?>", "");
                    return true;
                }
            },
            new XOption(resources, "-Xdoclint") {
                @Override
                public boolean process(String opt,  List<String> args) {
                    doclintOpts.put(this, DocLint.XMSGS_OPTION);
                    return true;
                }
            },
            new XOption(resources, "-Xdocrootparent", 1) {
                @Override
                public boolean process(String opt, List<String> args) {
                    docrootparent = args.get(0);
                    try {
                        URL ignored = new URL(docrootparent);
                    } catch (MalformedURLException e) {
                        reporter.print(ERROR, resources.getText("doclet.MalformedURL", docrootparent));
                        return false;
                    }
                    return true;
                }
            },
            new XOption(resources, "doclet.usage.xdoclint-extended", "-Xdoclint:", 0) {
                @Override
                public boolean process(String opt,  List<String> args) {
                    String dopt = opt.replace("-Xdoclint:", DocLint.XMSGS_CUSTOM_PREFIX);
                    doclintOpts.put(this, dopt);
                    if (dopt.contains("/")) {
                        reporter.print(ERROR, resources.getText("doclet.Option_doclint_no_qualifiers"));
                        return false;
                    }
                    if (!DocLint.isValidOption(dopt)) {
                        reporter.print(ERROR, resources.getText("doclet.Option_doclint_invalid_arg"));
                        return false;
                    }
                    return true;
                }
            },
            new XOption(resources, "doclet.usage.xdoclint-package", "-Xdoclint/package:", 0) {
                @Override
                public boolean process(String opt,  List<String> args) {
                    String dopt = opt.replace("-Xdoclint/package:", DocLint.XCHECK_PACKAGE);
                    doclintOpts.put(this, dopt);
                    if (!DocLint.isValidOption(dopt)) {
                        reporter.print(ERROR, resources.getText("doclet.Option_doclint_package_invalid_arg"));
                        return false;
                    }
                    return true;
                }
            }
        };
        Set<Doclet.Option> oset = new TreeSet<>();
        oset.addAll(Arrays.asList(options));
        oset.addAll(super.getSupportedOptions());
        return oset;
    }

    @Override
    protected boolean finishOptionSettings0() throws DocletException {
        if (docencoding == null) {
            if (charset == null) {
                docencoding = charset = (encoding == null) ? HtmlConstants.HTML_DEFAULT_CHARSET : encoding;
            } else {
                docencoding = charset;
            }
        } else {
            if (charset == null) {
                charset = docencoding;
            } else if (!charset.equals(docencoding)) {
                reporter.print(ERROR, resources.getText("doclet.Option_conflict", "-charset", "-docencoding"));
                return false;
            }
        }
        return super.finishOptionSettings0();
    }

    @Override
    protected void initConfiguration(DocletEnvironment docEnv) {
        super.initConfiguration(docEnv);
        memberSearchIndex = new TreeSet<>(utils.makeGenericSearchIndexComparator());
        moduleSearchIndex = new TreeSet<>(utils.makeGenericSearchIndexComparator());
        packageSearchIndex = new TreeSet<>(utils.makeGenericSearchIndexComparator());
        tagSearchIndex = new TreeSet<>(utils.makeGenericSearchIndexComparator());
        typeSearchIndex = new TreeSet<>(utils.makeTypeSearchIndexComparator());
    }
}
