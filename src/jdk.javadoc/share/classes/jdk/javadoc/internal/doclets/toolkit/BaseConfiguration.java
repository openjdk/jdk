/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit;

import java.io.*;
import java.lang.ref.*;
import java.util.*;

import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.SimpleElementVisitor9;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

import com.sun.source.util.DocTreePath;
import com.sun.tools.javac.util.DefinedBy;
import com.sun.tools.javac.util.DefinedBy.Api;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;
import jdk.javadoc.internal.doclets.toolkit.builders.BuilderFactory;
import jdk.javadoc.internal.doclets.toolkit.taglets.TagletManager;
import jdk.javadoc.internal.doclets.toolkit.util.DocFile;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileFactory;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocletConstants;
import jdk.javadoc.internal.doclets.toolkit.util.Extern;
import jdk.javadoc.internal.doclets.toolkit.util.Group;
import jdk.javadoc.internal.doclets.toolkit.util.MetaKeywords;
import jdk.javadoc.internal.doclets.toolkit.util.SimpleDocletException;
import jdk.javadoc.internal.doclets.toolkit.util.TypeElementCatalog;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;
import jdk.javadoc.internal.doclets.toolkit.util.Utils.Pair;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberMap;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberMap.GetterSetter;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberMap.Kind;

import static javax.tools.Diagnostic.Kind.*;

/**
 * Configure the output based on the options. Doclets should sub-class
 * BaseConfiguration, to configure and add their own options. This class contains
 * all user options which are supported by the 1.1 doclet and the standard
 * doclet.
 * <p>
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 *
 * @author Robert Field.
 * @author Atul Dambalkar.
 * @author Jamie Ho
 */
public abstract class BaseConfiguration {
    /**
     * The doclet that created this configuration.
     */
    public final Doclet doclet;

    /**
     * The factory for builders.
     */
    protected BuilderFactory builderFactory;

    /**
     * The taglet manager.
     */
    public TagletManager tagletManager;

    /**
     * The path to the builder XML input file.
     */
    public String builderXMLPath;

    /**
     * The default path to the builder XML.
     */
    public static final String DEFAULT_BUILDER_XML = "resources/doclet.xml";

    /**
     * The path to Taglets
     */
    public String tagletpath = null;

    /**
     * This is true if option "-serialwarn" is used. Defualt value is false to
     * suppress excessive warnings about serial tag.
     */
    public boolean serialwarn = false;

    /**
     * The specified amount of space between tab stops.
     */
    public int sourcetab;

    public String tabSpaces;

    /**
     * True if we should generate browsable sources.
     */
    public boolean linksource = false;

    /**
     * True if command line option "-nosince" is used. Default value is
     * false.
     */
    public boolean nosince = false;

    /**
     * True if we should recursively copy the doc-file subdirectories
     */
    public boolean copydocfilesubdirs = false;

    /**
     * Maintain backward compatibility with previous javadoc version
     */
    public boolean backwardCompatibility = true;

    /**
     * True if user wants to add member names as meta keywords.
     * Set to false because meta keywords are ignored in general
     * by most Internet search engines.
     */
    public boolean keywords = false;

    /**
     * The meta tag keywords instance.
     */
    public final MetaKeywords metakeywords;

    /**
     * The set of doc-file subdirectories to exclude
     */
    protected Set<String> excludedDocFileDirs;

    /**
     * The set of qualifiers to exclude
     */
    protected Set<String> excludedQualifiers;

    /**
     * The doclet environment.
     */
    public DocletEnvironment docEnv;

    /**
     * An utility class for commonly used helpers
     */
    public Utils utils;

    /**
     * All the temporary accessors to javac internals.
     */
    public WorkArounds workArounds;

    /**
     * Destination directory name, in which doclet will generate the entire
     * documentation. Default is current directory.
     */
    public String destDirName = "";

    /**
     * Destination directory name, in which doclet will copy the doc-files to.
     */
    public String docFileDestDirName = "";

    /**
     * Encoding for this document. Default is default encoding for this
     * platform.
     */
    public String docencoding = null;

    /**
     * True if user wants to suppress descriptions and tags.
     */
    public boolean nocomment = false;

    /**
     * Encoding for this document. Default is default encoding for this
     * platform.
     */
    public String encoding = null;

    /**
     * Generate author specific information for all the classes if @author
     * tag is used in the doc comment and if -author option is used.
     * <code>showauthor</code> is set to true if -author option is used.
     * Default is don't show author information.
     */
    public boolean showauthor = false;

    /**
     * Generate documentation for JavaFX getters and setters automatically
     * by copying it from the appropriate property definition.
     */
    public boolean javafx = false;

    /**
     * Generate version specific information for the all the classes
     * if @version tag is used in the doc comment and if -version option is
     * used. <code>showversion</code> is set to true if -version option is
     * used.Default is don't show version information.
     */
    public boolean showversion = false;

    /**
     * Allow JavaScript in doc comments.
     */
    private boolean allowScriptInComments = false;

    /**
     * Sourcepath from where to read the source files. Default is classpath.
     */
    public String sourcepath = "";

    /**
     * Generate modules documentation if more than one module is present.
     */
    public boolean showModules = false;

    /**
     * Don't generate deprecated API information at all, if -nodeprecated
     * option is used. <code>nodepracted</code> is set to true if
     * -nodeprecated option is used. Default is generate deprected API
     * information.
     */
    public boolean nodeprecated = false;

    /**
     * The catalog of classes specified on the command-line
     */
    public TypeElementCatalog typeElementCatalog;

    /**
     * True if user wants to suppress time stamp in output.
     * Default is false.
     */
    public boolean notimestamp = false;

    /**
     * The package grouping instance.
     */
    public final Group group = new Group(this);

    /**
     * The tracker of external package links.
     */
    public final Extern extern = new Extern(this);

    public Reporter reporter;

    public Locale locale;

    /**
     * Suppress all messages
     */
    public boolean quiet = false;

    /**
     * Specifies whether those methods that override a super-type's method
     * with no changes to the API contract should be summarized in the
     * footnote section.
     */
    public boolean summarizeOverriddenMethods = false;

    // A list containing urls
    private final List<String> linkList = new ArrayList<>();

     // A list of pairs containing urls and package list
    private final List<Pair<String, String>> linkOfflineList = new ArrayList<>();


    public boolean dumpOnError = false;

    private List<Pair<String, String>> groupPairs;

    private final Map<TypeElement, EnumMap<Kind, Reference<VisibleMemberMap>>> typeElementMemberCache;

    public abstract Messages getMessages();

    public abstract Resources getResources();

    /**
     * Returns a string identifying the version of the doclet.
     *
     * @return a version string
     */
    public abstract String getDocletVersion();

    /**
     * This method should be defined in all those doclets (configurations),
     * which want to derive themselves from this BaseConfiguration. This method
     * can be used to finish up the options setup.
     *
     * @return true if successful and false otherwise
     */

    public abstract boolean finishOptionSettings();

    public CommentUtils cmtUtils;

    /**
     * A sorted set of included packages.
     */
    public SortedSet<PackageElement> packages = null;

    public OverviewElement overviewElement;

    // The following three fields provide caches for use by all instances of VisibleMemberMap.
    public final Map<TypeElement, List<Element>> propertiesCache = new HashMap<>();
    public final Map<Element, Element> classPropertiesMap = new HashMap<>();
    public final Map<Element, GetterSetter> getterSetterMap = new HashMap<>();

    public DocFileFactory docFileFactory;

    /**
     * A sorted map, giving the (specified|included|other) packages for each module.
     */
    public SortedMap<ModuleElement, Set<PackageElement>> modulePackages;

    /**
     * The list of known modules, that should be documented.
     */
    public SortedSet<ModuleElement> modules;

    protected static final String sharedResourceBundleName =
            "jdk.javadoc.internal.doclets.toolkit.resources.doclets";

    /**
     * Constructs the configurations needed by the doclet.
     *
     * @param doclet the doclet that created this configuration
     */
    public BaseConfiguration(Doclet doclet) {
        this.doclet = doclet;
        excludedDocFileDirs = new HashSet<>();
        excludedQualifiers = new HashSet<>();
        setTabWidth(DocletConstants.DEFAULT_TAB_STOP_LENGTH);
        metakeywords = new MetaKeywords(this);
        groupPairs = new ArrayList<>(0);
        typeElementMemberCache = new HashMap<>();
    }

    private boolean initialized = false;

    protected void initConfiguration(DocletEnvironment docEnv) {
        if (initialized) {
            throw new IllegalStateException("configuration previously initialized");
        }
        initialized = true;
        this.docEnv = docEnv;
        Splitter specifiedSplitter = new Splitter(docEnv, false);
        specifiedModuleElements = Collections.unmodifiableSet(specifiedSplitter.mset);
        specifiedPackageElements = Collections.unmodifiableSet(specifiedSplitter.pset);
        specifiedTypeElements = Collections.unmodifiableSet(specifiedSplitter.tset);

        Splitter includedSplitter = new Splitter(docEnv, true);
        includedModuleElements = Collections.unmodifiableSet(includedSplitter.mset);
        includedPackageElements = Collections.unmodifiableSet(includedSplitter.pset);
        includedTypeElements = Collections.unmodifiableSet(includedSplitter.tset);
    }

    /**
     * Return the builder factory for this doclet.
     *
     * @return the builder factory for this doclet.
     */
    public BuilderFactory getBuilderFactory() {
        if (builderFactory == null) {
            builderFactory = new BuilderFactory(this);
        }
        return builderFactory;
    }

    public Reporter getReporter() {
        return this.reporter;
    }

    private Set<ModuleElement> specifiedModuleElements;

    public Set<ModuleElement> getSpecifiedModuleElements() {
        return specifiedModuleElements;
    }

    private Set<PackageElement> specifiedPackageElements;

    public Set<PackageElement> getSpecifiedPackageElements() {
        return specifiedPackageElements;
    }

    private Set<TypeElement> specifiedTypeElements;

    public Set<TypeElement> getSpecifiedTypeElements() {
        return specifiedTypeElements;
    }

    private Set<ModuleElement> includedModuleElements;

    public Set<ModuleElement> getIncludedModuleElements() {
        return includedModuleElements;
    }

    private Set<PackageElement> includedPackageElements;

    public Set<PackageElement> getIncludedPackageElements() {
        return includedPackageElements;
    }

    private Set<TypeElement> includedTypeElements;

    public Set<TypeElement> getIncludedTypeElements() {
        return includedTypeElements;
    }

    private void initModules() {
        // Build the modules structure used by the doclet
        modules = new TreeSet<>(utils.makeModuleComparator());
        modules.addAll(getSpecifiedModuleElements());

        modulePackages = new TreeMap<>(utils.makeModuleComparator());
        for (PackageElement p : packages) {
            ModuleElement mdle = docEnv.getElementUtils().getModuleOf(p);
            if (mdle != null && !mdle.isUnnamed()) {
                Set<PackageElement> s = modulePackages
                        .computeIfAbsent(mdle, m -> new TreeSet<>(utils.makePackageComparator()));
                s.add(p);
            }
        }

        for (PackageElement p : getIncludedPackageElements()) {
            ModuleElement mdle = docEnv.getElementUtils().getModuleOf(p);
            if (mdle != null && !mdle.isUnnamed()) {
                Set<PackageElement> s = modulePackages
                        .computeIfAbsent(mdle, m -> new TreeSet<>(utils.makePackageComparator()));
                s.add(p);
            }
        }

        // add entries for modules which may not have exported packages
        modules.forEach((ModuleElement mdle) -> {
            modulePackages.computeIfAbsent(mdle, m -> Collections.emptySet());
        });

        modules.addAll(modulePackages.keySet());
        showModules = !modules.isEmpty();
        for (Set<PackageElement> pkgs : modulePackages.values()) {
            packages.addAll(pkgs);
        }
    }

    private void initPackages() {
        packages = new TreeSet<>(utils.makePackageComparator());
        // add all the included packages
        packages.addAll(includedPackageElements);
    }

    public Set<Doclet.Option> getSupportedOptions() {
        Resources resources = getResources();
        Doclet.Option[] options = {
                new Option(resources, "-author") {
                    @Override
                    public boolean process(String opt, List<String> args) {
                        showauthor = true;
                        return true;
                    }
                },
                new Option(resources, "-d", 1) {
                    @Override
                    public boolean process(String opt, List<String> args) {
                        destDirName = addTrailingFileSep(args.get(0));
                        return true;
                    }
                },
                new Option(resources, "-docencoding", 1) {
                    @Override
                    public boolean process(String opt, List<String> args) {
                        docencoding = args.get(0);
                        return true;
                    }
                },
                new Option(resources, "-docfilessubdirs") {
                    @Override
                    public boolean process(String opt, List<String> args) {
                        copydocfilesubdirs = true;
                        return true;
                    }
                },
                new Hidden(resources, "-encoding", 1) {
                    @Override
                    public boolean process(String opt, List<String> args) {
                        encoding = args.get(0);
                        return true;
                    }
                },
                new Option(resources, "-excludedocfilessubdir", 1) {
                    @Override
                    public boolean process(String opt, List<String> args) {
                        addToSet(excludedDocFileDirs, args.get(0));
                        return true;
                    }
                },
                new Option(resources, "-group", 2) {
                    @Override
                    public boolean process(String opt, List<String> args) {
                        groupPairs.add(new Pair<>(args.get(0), args.get(1)));
                        return true;
                    }
                },
                new Option(resources, "--javafx -javafx") {
                    @Override
                    public boolean process(String opt, List<String> args) {
                        javafx = true;
                        return true;
                    }
                },
                new Option(resources, "-keywords") {
                    @Override
                    public boolean process(String opt, List<String> args) {
                        keywords = true;
                        return true;
                    }
                },
                new Option(resources, "-link", 1) {
                    @Override
                    public boolean process(String opt, List<String> args) {
                        linkList.add(args.get(0));
                        return true;
                    }
                },
                new Option(resources, "-linksource") {
                    @Override
                    public boolean process(String opt, List<String> args) {
                        linksource = true;
                        return true;
                    }
                },
                new Option(resources, "-linkoffline", 2) {
                    @Override
                    public boolean process(String opt, List<String> args) {
                        linkOfflineList.add(new Pair<>(args.get(0), args.get(1)));
                        return true;
                    }
                },
                new Option(resources, "-nocomment") {
                    @Override
                    public boolean process(String opt, List<String> args) {
                        nocomment = true;
                        return true;
                    }
                },
                new Option(resources, "-nodeprecated") {
                    @Override
                    public boolean process(String opt, List<String> args) {
                        nodeprecated = true;
                        return true;
                    }
                },
                new Option(resources, "-nosince") {
                    @Override
                    public boolean process(String opt, List<String> args) {
                        nosince = true;
                        return true;
                    }
                },
                new Option(resources, "-notimestamp") {
                    @Override
                    public boolean process(String opt, List<String> args) {
                        notimestamp = true;
                        return true;
                    }
                },
                new Option(resources, "-noqualifier", 1) {
                    @Override
                    public boolean process(String opt, List<String> args) {
                        addToSet(excludedQualifiers, args.get(0));
                        return true;
                    }
                },
                new Option(resources, "--override-methods", 1) {
                    @Override
                    public boolean process(String opt,  List<String> args) {
                        String o = args.get(0);
                        switch (o) {
                            case "summary":
                                summarizeOverriddenMethods = true;
                                break;
                            case "detail":
                                summarizeOverriddenMethods = false;
                                break;
                            default:
                                reporter.print(ERROR, getText("doclet.Option_invalid",
                                        o, "--override-methods"));
                                return false;
                        }
                        return true;
                    }
                },
                new Hidden(resources, "-quiet") {
                    @Override
                    public boolean process(String opt, List<String> args) {
                        quiet = true;
                        return true;
                    }
                },
                new Option(resources, "-serialwarn") {
                    @Override
                    public boolean process(String opt, List<String> args) {
                        serialwarn = true;
                        return true;
                    }
                },
                new Option(resources, "-sourcetab", 1) {
                    @Override
                    public boolean process(String opt, List<String> args) {
                        linksource = true;
                        try {
                            setTabWidth(Integer.parseInt(args.get(0)));
                        } catch (NumberFormatException e) {
                            //Set to -1 so that warning will be printed
                            //to indicate what is valid argument.
                            sourcetab = -1;
                        }
                        if (sourcetab <= 0) {
                            getMessages().warning("doclet.sourcetab_warning");
                            setTabWidth(DocletConstants.DEFAULT_TAB_STOP_LENGTH);
                        }
                        return true;
                    }
                },
                new Option(resources, "-tag", 1) {
                    @Override
                    public boolean process(String opt, List<String> args) {
                        ArrayList<String> list = new ArrayList<>();
                        list.add(opt);
                        list.add(args.get(0));
                        customTagStrs.add(list);
                        return true;
                    }
                },
                new Option(resources, "-taglet", 1) {
                    @Override
                    public boolean process(String opt, List<String> args) {
                        ArrayList<String> list = new ArrayList<>();
                        list.add(opt);
                        list.add(args.get(0));
                        customTagStrs.add(list);
                        return true;
                    }
                },
                new Option(resources, "-tagletpath", 1) {
                    @Override
                    public boolean process(String opt, List<String> args) {
                        tagletpath = args.get(0);
                        return true;
                    }
                },
                new Option(resources, "-version") {
                    @Override
                    public boolean process(String opt, List<String> args) {
                        showversion = true;
                        return true;
                    }
                },
                new Hidden(resources, "--dump-on-error") {
                    @Override
                    public boolean process(String opt, List<String> args) {
                        dumpOnError = true;
                        return true;
                    }
                },
                new Option(resources, "--allow-script-in-comments") {
                    @Override
                    public boolean process(String opt, List<String> args) {
                        allowScriptInComments = true;
                        return true;
                    }
                }
        };
        Set<Doclet.Option> set = new TreeSet<>();
        set.addAll(Arrays.asList(options));
        return set;
    }

    final LinkedHashSet<List<String>> customTagStrs = new LinkedHashSet<>();

    /*
     * when this is called all the option have been set, this method,
     * initializes certain components before anything else is started.
     */
    protected boolean finishOptionSettings0() throws DocletException {

        initDestDirectory();
        for (String link : linkList) {
            extern.link(link, reporter);
        }
        for (Pair<String, String> linkOfflinePair : linkOfflineList) {
            extern.link(linkOfflinePair.first, linkOfflinePair.second, reporter);
        }
        typeElementCatalog = new TypeElementCatalog(includedTypeElements, this);
        initTagletManager(customTagStrs);
        groupPairs.stream().forEach((grp) -> {
            if (showModules) {
                group.checkModuleGroups(grp.first, grp.second);
            } else {
                group.checkPackageGroups(grp.first, grp.second);
            }
        });
        overviewElement = new OverviewElement(workArounds.getUnnamedPackage(), getOverviewPath());
        return true;
    }

    /**
     * Set the command line options supported by this configuration.
     *
     * @return true if the options are set successfully
     * @throws DocletException if there is a problem while setting the options
     */
    public boolean setOptions() throws DocletException {
        initPackages();
        initModules();
        if (!finishOptionSettings0() || !finishOptionSettings())
            return false;

        return true;
    }

    private void initDestDirectory() throws DocletException {
        if (!destDirName.isEmpty()) {
            DocFile destDir = DocFile.createFileForDirectory(this, destDirName);
            if (!destDir.exists()) {
                //Create the output directory (in case it doesn't exist yet)
                reporter.print(NOTE, getText("doclet.dest_dir_create", destDirName));
                destDir.mkdirs();
            } else if (!destDir.isDirectory()) {
                throw new SimpleDocletException(getText(
                        "doclet.destination_directory_not_directory_0",
                        destDir.getPath()));
            } else if (!destDir.canWrite()) {
                throw new SimpleDocletException(getText(
                        "doclet.destination_directory_not_writable_0",
                        destDir.getPath()));
            }
        }
        DocFileFactory.getFactory(this).setDestDir(destDirName);
    }

    /**
     * Initialize the taglet manager.  The strings to initialize the simple custom tags should
     * be in the following format:  "[tag name]:[location str]:[heading]".
     *
     * @param customTagStrs the set two dimensional arrays of strings.  These arrays contain
     *                      either -tag or -taglet arguments.
     */
    private void initTagletManager(Set<List<String>> customTagStrs) {
        tagletManager = tagletManager == null ?
                new TagletManager(nosince, showversion, showauthor, javafx, this) :
                tagletManager;
        for (List<String> args : customTagStrs) {
            if (args.get(0).equals("-taglet")) {
                tagletManager.addCustomTag(args.get(1), getFileManager(), tagletpath);
                continue;
            }
            List<String> tokens = tokenize(args.get(1), TagletManager.SIMPLE_TAGLET_OPT_SEPARATOR, 3);
            if (tokens.size() == 1) {
                String tagName = args.get(1);
                if (tagletManager.isKnownCustomTag(tagName)) {
                    //reorder a standard tag
                    tagletManager.addNewSimpleCustomTag(tagName, null, "");
                } else {
                    //Create a simple tag with the heading that has the same name as the tag.
                    StringBuilder heading = new StringBuilder(tagName + ":");
                    heading.setCharAt(0, Character.toUpperCase(tagName.charAt(0)));
                    tagletManager.addNewSimpleCustomTag(tagName, heading.toString(), "a");
                }
            } else if (tokens.size() == 2) {
                //Add simple taglet without heading, probably to excluding it in the output.
                tagletManager.addNewSimpleCustomTag(tokens.get(0), tokens.get(1), "");
            } else if (tokens.size() >= 3) {
                tagletManager.addNewSimpleCustomTag(tokens.get(0), tokens.get(2), tokens.get(1));
            } else {
                Messages messages = getMessages();
                messages.error("doclet.Error_invalid_custom_tag_argument", args.get(1));
            }
        }
    }

    /**
     * Given a string, return an array of tokens.  The separator can be escaped
     * with the '\' character.  The '\' character may also be escaped by the
     * '\' character.
     *
     * @param s         the string to tokenize.
     * @param separator the separator char.
     * @param maxTokens the maximum number of tokens returned.  If the
     *                  max is reached, the remaining part of s is appended
     *                  to the end of the last token.
     * @return an array of tokens.
     */
    private List<String> tokenize(String s, char separator, int maxTokens) {
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean prevIsEscapeChar = false;
        for (int i = 0; i < s.length(); i += Character.charCount(i)) {
            int currentChar = s.codePointAt(i);
            if (prevIsEscapeChar) {
                // Case 1:  escaped character
                token.appendCodePoint(currentChar);
                prevIsEscapeChar = false;
            } else if (currentChar == separator && tokens.size() < maxTokens - 1) {
                // Case 2:  separator
                tokens.add(token.toString());
                token = new StringBuilder();
            } else if (currentChar == '\\') {
                // Case 3:  escape character
                prevIsEscapeChar = true;
            } else {
                // Case 4:  regular character
                token.appendCodePoint(currentChar);
            }
        }
        if (token.length() > 0) {
            tokens.add(token.toString());
        }
        return tokens;
    }

    private void addToSet(Set<String> s, String str) {
        StringTokenizer st = new StringTokenizer(str, ":");
        String current;
        while (st.hasMoreTokens()) {
            current = st.nextToken();
            s.add(current);
        }
    }

    /**
     * Add a trailing file separator, if not found. Remove superfluous
     * file separators if any. Preserve the front double file separator for
     * UNC paths.
     *
     * @param path Path under consideration.
     * @return String Properly constructed path string.
     */
    public static String addTrailingFileSep(String path) {
        String fs = System.getProperty("file.separator");
        String dblfs = fs + fs;
        int indexDblfs;
        while ((indexDblfs = path.indexOf(dblfs, 1)) >= 0) {
            path = path.substring(0, indexDblfs) +
                    path.substring(indexDblfs + fs.length());
        }
        if (!path.endsWith(fs))
            path += fs;
        return path;
    }

    /**
     * This checks for the validity of the options used by the user.
     * As of this writing, this checks only docencoding.
     *
     * @return true if all the options are valid.
     */
    public boolean generalValidOptions() {
        if (docencoding != null) {
            if (!checkOutputFileEncoding(docencoding)) {
                return false;
            }
        }
        if (docencoding == null && (encoding != null && !encoding.isEmpty())) {
            if (!checkOutputFileEncoding(encoding)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check the validity of the given Source or Output File encoding on this
     * platform.
     *
     * @param docencoding output file encoding.
     */
    private boolean checkOutputFileEncoding(String docencoding) {
        OutputStream ost = new ByteArrayOutputStream();
        OutputStreamWriter osw = null;
        try {
            osw = new OutputStreamWriter(ost, docencoding);
        } catch (UnsupportedEncodingException exc) {
            reporter.print(ERROR, getText("doclet.Encoding_not_supported", docencoding));
            return false;
        } finally {
            try {
                if (osw != null) {
                    osw.close();
                }
            } catch (IOException exc) {
            }
        }
        return true;
    }

    /**
     * Return true if the given doc-file subdirectory should be excluded and
     * false otherwise.
     *
     * @param docfilesubdir the doc-files subdirectory to check.
     * @return true if the directory is excluded.
     */
    public boolean shouldExcludeDocFileDir(String docfilesubdir) {
        return excludedDocFileDirs.contains(docfilesubdir);
    }

    /**
     * Return true if the given qualifier should be excluded and false otherwise.
     *
     * @param qualifier the qualifier to check.
     * @return true if the qualifier should be excluded
     */
    public boolean shouldExcludeQualifier(String qualifier) {
        if (excludedQualifiers.contains("all") ||
                excludedQualifiers.contains(qualifier) ||
                excludedQualifiers.contains(qualifier + ".*")) {
            return true;
        } else {
            int index = -1;
            while ((index = qualifier.indexOf(".", index + 1)) != -1) {
                if (excludedQualifiers.contains(qualifier.substring(0, index + 1) + "*")) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Return the qualified name of the Element if its qualifier is not excluded.
     * Otherwise return the unqualified Element name.
     *
     * @param te the TypeElement to check.
     * @return the class name
     */
    public String getClassName(TypeElement te) {
        PackageElement pkg = utils.containingPackage(te);
        return shouldExcludeQualifier(utils.getPackageName(pkg))
                ? utils.getSimpleName(te)
                : utils.getFullyQualifiedName(te);
    }

    /**
     * Convenience method to obtain a resource from the doclet's
     * {@link Resources resources}.
     * Equivalent to <code>getResources.getText(key);</code>.
     *
     * @param key the key for the desired string
     * @return the string for the given key
     * @throws MissingResourceException if the key is not found in either
     *                                  bundle.
     */
    public abstract String getText(String key);

    /**
     * Convenience method to obtain a resource from the doclet's
     * {@link Resources resources}.
     * Equivalent to <code>getResources.getText(key, args);</code>.
     *
     * @param key  the key for the desired string
     * @param args values to be substituted into the resulting string
     * @return the string for the given key
     * @throws MissingResourceException if the key is not found in either
     *                                  bundle.
     */
    public abstract String getText(String key, String... args);

    /**
     * Convenience method to obtain a resource from the doclet's
     * {@link Resources resources} as a {@code Content} object.
     *
     * @param key the key for the desired string
     * @return a content tree for the text
     */
    public abstract Content getContent(String key);

    /**
     * Convenience method to obtain a resource from the doclet's
     * {@link Resources resources} as a {@code Content} object.
     *
     * @param key the key for the desired string
     * @param o   string or content argument added to configuration text
     * @return a content tree for the text
     */
    public abstract Content getContent(String key, Object o);

    /**
     * Convenience method to obtain a resource from the doclet's
     * {@link Resources resources} as a {@code Content} object.
     *
     * @param key the key for the desired string
     * @param o1  resource argument
     * @param o2  resource argument
     * @return a content tree for the text
     */
    public abstract Content getContent(String key, Object o1, Object o2);

    /**
     * Get the configuration string as a content.
     *
     * @param key the key for the desired string
     * @param o0  string or content argument added to configuration text
     * @param o1  string or content argument added to configuration text
     * @param o2  string or content argument added to configuration text
     * @return a content tree for the text
     */
    public abstract Content getContent(String key, Object o0, Object o1, Object o2);

    /**
     * Return true if the TypeElement element is getting documented, depending upon
     * -nodeprecated option and the deprecation information. Return true if
     * -nodeprecated is not used. Return false if -nodeprecated is used and if
     * either TypeElement element is deprecated or the containing package is deprecated.
     *
     * @param te the TypeElement for which the page generation is checked
     * @return true if it is a generated doc.
     */
    public boolean isGeneratedDoc(TypeElement te) {
        if (!nodeprecated) {
            return true;
        }
        return !(utils.isDeprecated(te) || utils.isDeprecated(utils.containingPackage(te)));
    }

    /**
     * Return the doclet specific instance of a writer factory.
     *
     * @return the {@link WriterFactory} for the doclet.
     */
    public abstract WriterFactory getWriterFactory();

    /**
     * Return the input stream to the builder XML.
     *
     * @return the input steam to the builder XML.
     * @throws DocFileIOException when the given XML file cannot be found or opened.
     */
    public InputStream getBuilderXML() throws DocFileIOException {
        return builderXMLPath == null ?
                BaseConfiguration.class.getResourceAsStream(DEFAULT_BUILDER_XML) :
                DocFile.createFileForInput(this, builderXMLPath).openInputStream();
    }

    /**
     * Return the Locale for this document.
     *
     * @return the current locale
     */
    public abstract Locale getLocale();

    /**
     * Return the path of the overview file and null if it does not exist.
     *
     * @return the path of the overview file.
     */
    public abstract JavaFileObject getOverviewPath();

    /**
     * Return the current file manager.
     *
     * @return JavaFileManager
     */
    public abstract JavaFileManager getFileManager();

    private void setTabWidth(int n) {
        sourcetab = n;
        tabSpaces = String.format("%" + n + "s", "");
    }

    public abstract boolean showMessage(DocTreePath path, String key);

    public abstract boolean showMessage(Element e, String key);

    public static abstract class Option implements Doclet.Option, Comparable<Option> {
        private final String[] names;
        private final String parameters;
        private final String description;
        private final int argCount;

        protected Option(Resources resources, String name, int argCount) {
            this(resources, null, name, argCount);
        }

        protected Option(Resources resources, String keyBase, String name, int argCount) {
            this.names = name.trim().split("\\s+");
            if (keyBase == null) {
                keyBase = "doclet.usage." + names[0].toLowerCase().replaceAll("^-+", "");
            }
            String desc = getOptionsMessage(resources, keyBase + ".description");
            if (desc.isEmpty()) {
                this.description = "<MISSING KEY>";
                this.parameters = "<MISSING KEY>";
            } else {
                this.description = desc;
                this.parameters = getOptionsMessage(resources, keyBase + ".parameters");
            }
            this.argCount = argCount;
        }

        protected Option(Resources resources, String name) {
            this(resources, name, 0);
        }

        private String getOptionsMessage(Resources resources, String key) {
            try {
                return resources.getText(key);
            } catch (MissingResourceException ignore) {
                return "";
            }
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public Option.Kind getKind() {
            return Doclet.Option.Kind.STANDARD;
        }

        @Override
        public List<String> getNames() {
            return Arrays.asList(names);
        }

        @Override
        public String getParameters() {
            return parameters;
        }

        @Override
        public String toString() {
            return Arrays.toString(names);
        }

        @Override
        public int getArgumentCount() {
            return argCount;
        }

        public boolean matches(String option) {
            for (String name : names) {
                boolean matchCase = name.startsWith("--");
                if (option.startsWith("--") && option.contains("=")) {
                    return name.equals(option.substring(option.indexOf("=") + 1));
                } else if (matchCase) {
                    return name.equals(option);
                }
                return name.toLowerCase().equals(option.toLowerCase());
            }
            return false;
        }

        @Override
        public int compareTo(Option that) {
            return this.getNames().get(0).compareTo(that.getNames().get(0));
        }
    }

    public abstract class XOption extends Option {

        public XOption(Resources resources, String prefix, String name, int argCount) {
            super(resources, prefix, name, argCount);
        }

        public XOption(Resources resources, String name, int argCount) {
            super(resources, name, argCount);
        }

        public XOption(Resources resources, String name) {
            this(resources, name, 0);
        }

        @Override
        public Option.Kind getKind() {
            return Doclet.Option.Kind.EXTENDED;
        }
    }

    public abstract class Hidden extends Option {

        public Hidden(Resources resources, String name, int argCount) {
            super(resources, name, argCount);
        }

        public Hidden(Resources resources, String name) {
            this(resources, name, 0);
        }

        @Override
        public Option.Kind getKind() {
            return Doclet.Option.Kind.OTHER;
        }
    }

    /*
     * Splits the elements in a collection to its individual
     * collection.
     */
    static private class Splitter {

        final Set<ModuleElement> mset = new LinkedHashSet<>();
        final Set<PackageElement> pset = new LinkedHashSet<>();
        final Set<TypeElement> tset = new LinkedHashSet<>();

        Splitter(DocletEnvironment docEnv, boolean included) {

            Set<? extends Element> inset = included
                    ? docEnv.getIncludedElements()
                    : docEnv.getSpecifiedElements();

            for (Element e : inset) {
                new SimpleElementVisitor9<Void, Void>() {
                    @Override
                    @DefinedBy(Api.LANGUAGE_MODEL)
                    public Void visitModule(ModuleElement e, Void p) {
                        mset.add(e);
                        return null;
                    }

                    @Override
                    @DefinedBy(Api.LANGUAGE_MODEL)
                    public Void visitPackage(PackageElement e, Void p) {
                        pset.add(e);
                        return null;
                    }

                    @Override
                    @DefinedBy(Api.LANGUAGE_MODEL)
                    public Void visitType(TypeElement e, Void p) {
                        tset.add(e);
                        return null;
                    }

                    @Override
                    @DefinedBy(Api.LANGUAGE_MODEL)
                    protected Void defaultAction(Element e, Void p) {
                        throw new AssertionError("unexpected element: " + e);
                    }

                }.visit(e);
            }
        }
    }

    /**
     * Returns whether or not to allow JavaScript in comments.
     * Default is off; can be set true from a command line option.
     *
     * @return the allowScriptInComments
     */
    public boolean isAllowScriptInComments() {
        return allowScriptInComments;
    }

    public VisibleMemberMap getVisibleMemberMap(TypeElement te, VisibleMemberMap.Kind kind) {
        EnumMap<Kind, Reference<VisibleMemberMap>> cacheMap = typeElementMemberCache
                .computeIfAbsent(te, k -> new EnumMap<>(VisibleMemberMap.Kind.class));

        Reference<VisibleMemberMap> vmapRef = cacheMap.get(kind);
        // recompute, if referent has been garbage collected
        VisibleMemberMap vMap = vmapRef == null ? null : vmapRef.get();
        if (vMap == null) {
            vMap = new VisibleMemberMap(te, kind, this);
            cacheMap.put(kind, new SoftReference<>(vMap));
        }
        return vMap;
    }
}
