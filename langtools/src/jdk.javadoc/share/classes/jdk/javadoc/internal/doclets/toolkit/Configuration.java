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

package jdk.javadoc.internal.doclets.toolkit;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

import com.sun.source.util.DocTreePath;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;
import jdk.javadoc.internal.doclets.toolkit.builders.BuilderFactory;
import jdk.javadoc.internal.doclets.toolkit.taglets.TagletManager;
import jdk.javadoc.internal.doclets.toolkit.util.DocFile;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileFactory;
import jdk.javadoc.internal.doclets.toolkit.util.DocletAbortException;
import jdk.javadoc.internal.doclets.toolkit.util.DocletConstants;
import jdk.javadoc.internal.doclets.toolkit.util.Extern;
import jdk.javadoc.internal.doclets.toolkit.util.Group;
import jdk.javadoc.internal.doclets.toolkit.util.MessageRetriever;
import jdk.javadoc.internal.doclets.toolkit.util.MetaKeywords;
import jdk.javadoc.internal.doclets.toolkit.util.TypeElementCatalog;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberMap.GetterSetter;

import static javax.tools.Diagnostic.Kind.*;

/**
 * Configure the output based on the options. Doclets should sub-class
 * Configuration, to configure and add their own options. This class contains
 * all user options which are supported by the 1.1 doclet and the standard
 * doclet.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Robert Field.
 * @author Atul Dambalkar.
 * @author Jamie Ho
 */
public abstract class Configuration {

    /**
     * Exception used to report a problem during setOptions.
     */
    public static class Fault extends Exception {
        private static final long serialVersionUID = 0;

        Fault(String msg) {
            super(msg);
        }

        Fault(String msg, Exception cause) {
            super(msg, cause);
        }
    }

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
    private static final String DEFAULT_BUILDER_XML = "resources/doclet.xml";

    /**
     * The path to Taglets
     */
    public String tagletpath = "";

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
     * The META charset tag used for cross-platform viewing.
     */
    public String charset = "";

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
     * The list of doc-file subdirectories to exclude
     */
    protected Set<String> excludedDocFileDirs;

    /**
     * The list of qualifiers to exclude
     */
    protected Set<String> excludedQualifiers;

    /**
     * The Root of the generated Program Structure from the Doclet API.
     */
    public DocletEnvironment root;

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
     * Sourcepath from where to read the source files. Default is classpath.
     *
     */
    public String sourcepath = "";

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
     * Message Retriever for the doclet, to retrieve message from the resource
     * file for this Configuration, which is common for 1.1 and standard
     * doclets.
     *
     * TODO:  Make this private!!!
     */
    public MessageRetriever message = null;

    /**
     * True if user wants to suppress time stamp in output.
     * Default is false.
     */
    public boolean notimestamp= false;

    /**
     * The package grouping instance.
     */
    public final Group group = new Group(this);

    /**
     * The tracker of external package links.
     */
    public final Extern extern = new Extern(this);

    public  Reporter reporter;

    public Locale locale;

    /**
     * Suppress all messages
     */
    public boolean quiet = false;

    private String urlForLink;

    private String pkglistUrlForLink;

    private String urlForLinkOffline;

    private String pkglistUrlForLinkOffline;

    private List<GroupContainer> groups;

    /**
     * Return the build date for the doclet.
     */
    public abstract String getDocletSpecificBuildDate();

    /**
     * This method should be defined in all those doclets(configurations),
     * which want to derive themselves from this Configuration. This method
     * can be used to finish up the options setup.
     */

    public abstract boolean finishOptionSettings();

    /**
     * Return the doclet specific {@link MessageRetriever}
     * @return the doclet specific MessageRetriever.
     */
    public abstract MessageRetriever getDocletSpecificMsg();

    public CommentUtils cmtUtils;

    /**
     * A sorted set of packages specified on the command-line merged with a
     * collection of packages that contain the classes specified on the
     * command-line.
     */
    public SortedSet<PackageElement> packages;

    protected final List<Doclet.Option> optionsProcessed;

    public final OverviewElement overviewElement;

    // The following three fields provide caches for use by all instances of VisibleMemberMap.
    public final Map<TypeElement, List<Element>> propertiesCache = new HashMap<>();
    public final Map<Element, Element> classPropertiesMap = new HashMap<>();
    public final Map<Element, GetterSetter> getterSetterMap = new HashMap<>();

    public DocFileFactory docFileFactory;

    /**
     * Constructor. Constructs the message retriever with resource file.
     */
    public Configuration() {
        message = new MessageRetriever(this, "jdk.javadoc.internal.doclets.toolkit.resources.doclets");
        excludedDocFileDirs = new HashSet<>();
        excludedQualifiers = new HashSet<>();
        setTabWidth(DocletConstants.DEFAULT_TAB_STOP_LENGTH);
        metakeywords = new MetaKeywords(this);
        optionsProcessed = new ArrayList<>();
        groups = new ArrayList<>(0);
        overviewElement = new OverviewElement(root);
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

    private void initPackages() {
        packages = new TreeSet<>(utils.makePackageComparator());
        packages.addAll(utils.getSpecifiedPackages());
        for (TypeElement aClass : utils.getSpecifiedClasses()) {
            packages.add(utils.containingPackage(aClass));
        }
    }

    public Set<Doclet.Option> getSupportedOptions() {
        Doclet.Option[] options = {
            new Option(this, "author") {
                @Override
                public boolean process(String opt, ListIterator<String> args) {
                    optionsProcessed.add(this);
                    showauthor = true;
                    return true;
                }
            },
            new Hidden(this, "classpath", 1) {
                @Override
                public boolean process(String opt, ListIterator<String> args) {
                    if (sourcepath.length() == 0) {
                        optionsProcessed.add(this);
                        sourcepath = args.next();
                    }
                    return true;
                }
            },
            new Hidden(this, "cp", 1) {
                @Override
                public boolean process(String opt, ListIterator<String> args) {
                    if (sourcepath.length() == 0) {
                        optionsProcessed.add(this);
                        sourcepath = args.next();
                    }
                    return true;
                }
            },
            new Option(this, "d", 1) {
                @Override
                public boolean process(String opt, ListIterator<String> args) {
                    optionsProcessed.add(this);
                    destDirName = addTrailingFileSep(args.next());
                    return true;
                }
            },
            new Option(this, "docencoding", 1) {
                @Override
                public boolean process(String opt, ListIterator<String> args) {
                    optionsProcessed.add(this);
                    docencoding = args.next();
                    return true;
                }
            },
            new Option(this, "docfilessubdirs") {
                @Override
                public boolean process(String opt, ListIterator<String> args) {
                    optionsProcessed.add(this);
                    copydocfilesubdirs = true;
                    return true;
                }
            },
            new Hidden(this, "encoding", 1) {
                @Override
                public boolean process(String opt, ListIterator<String> args) {
                    optionsProcessed.add(this);
                    encoding = args.next();
                    return true;
                }
            },
            new Option(this, "excludedocfilessubdir", 1) {
                @Override
                public boolean process(String opt, ListIterator<String> args) {
                    optionsProcessed.add(this);
                    addToSet(excludedDocFileDirs, args.next());
                    return true;
                }
            },
            new Option(this, "group", 2) {
                @Override
                public boolean process(String opt, ListIterator<String> args) {
                    optionsProcessed.add(this);
                    groups.add(new GroupContainer(args.next(), args.next()));
                    return true;
                }
            },
            new Hidden(this, "javafx") {
                @Override
                public boolean process(String opt, ListIterator<String> args) {
                    optionsProcessed.add(this);
                    javafx = true;
                    return true;
                }
            },
            new Option(this, "keywords") {
                @Override
                public boolean process(String opt, ListIterator<String> args) {
                    optionsProcessed.add(this);
                    keywords = true;
                    return true;
                }
            },
            new Option(this, "link", 1) {
                @Override
                public boolean process(String opt, ListIterator<String> args) {
                    optionsProcessed.add(this);
                    urlForLink = args.next();
                    pkglistUrlForLink = urlForLink;
                    return true;
                }
            },
            new Option(this, "linksource") {
                @Override
                public boolean process(String opt, ListIterator<String> args) {
                    optionsProcessed.add(this);
                    linksource = true;
                    return true;
                }
            },
            new Option(this, "linkoffline", 2) {
                @Override
                public boolean process(String opt, ListIterator<String> args) {
                    optionsProcessed.add(this);
                    urlForLinkOffline = args.next();
                    pkglistUrlForLinkOffline = args.next();
                    return true;
                }
            },
            new Option(this, "nocomment") {
                @Override
                public boolean process(String opt, ListIterator<String> args) {
                    optionsProcessed.add(this);
                    nocomment = true;
                    return true;
                }
            },
            new Option(this, "nodeprecated") {
                @Override
                public boolean process(String opt, ListIterator<String> args) {
                    optionsProcessed.add(this);
                    nodeprecated = true;
                    return true;
                }
            },
            new Option(this, "nosince") {
                @Override
                public boolean process(String opt, ListIterator<String> args) {
                    optionsProcessed.add(this);
                    nosince = true;
                    return true;
                }
            },
            new Option(this, "notimestamp") {
                @Override
                public boolean process(String opt, ListIterator<String> args) {
                    optionsProcessed.add(this);
                    notimestamp = true;
                    return true;
                }
            },
            new Option(this, "noqualifier", 1) {
                @Override
                public boolean process(String opt, ListIterator<String> args) {
                    optionsProcessed.add(this);
                    addToSet(excludedQualifiers, args.next());
                    return true;
                }
            },
            new Hidden(this, "quiet") {
                @Override
                public boolean process(String opt, ListIterator<String> args) {
                    optionsProcessed.add(this);
                    quiet = true;
                    return true;
                }
            },
            new Option(this, "serialwarn") {
                @Override
                public boolean process(String opt, ListIterator<String> args) {
                    optionsProcessed.add(this);
                    serialwarn = true;
                    return true;
                }
            },
            new Hidden(this, "sourcepath", 1) {
                @Override
                public boolean process(String opt, ListIterator<String> args) {
                    optionsProcessed.add(this);
                    sourcepath = args.next();
                    return true;
                }
            },
            new Option(this, "sourcetab", 1) {
                @Override
                public boolean process(String opt, ListIterator<String> args) {
                    optionsProcessed.add(this);
                    linksource = true;
                    try {
                        setTabWidth(Integer.parseInt(args.next()));
                    } catch (NumberFormatException e) {
                             //Set to -1 so that warning will be printed
                        //to indicate what is valid argument.
                        sourcetab = -1;
                    }
                    if (sourcetab <= 0) {
                        message.warning("doclet.sourcetab_warning");
                        setTabWidth(DocletConstants.DEFAULT_TAB_STOP_LENGTH);
                    }
                    return true;
                }
            },
            new Option(this, "tag", 1) {
                @Override
                public boolean process(String opt, ListIterator<String> args) {
                    optionsProcessed.add(this);
                    ArrayList<String> list = new ArrayList<>();
                    list.add(opt);
                    list.add(args.next());
                    customTagStrs.add(list);
                    return true;
                }
            },
             new Option(this, "taglet", 1) {
                @Override
                public boolean process(String opt, ListIterator<String> args) {
                    optionsProcessed.add(this);
                    ArrayList<String> list = new ArrayList<>();
                    list.add(opt);
                    list.add(args.next());
                    customTagStrs.add(list);
                    return true;
                }
            },
            new Option(this, "tagletpath", 1) {
                @Override
                public boolean process(String opt, ListIterator<String> args) {
                    optionsProcessed.add(this);
                    tagletpath = args.next();
                    return true;
                }
            },
            new Option(this, "version") {
                @Override
                public boolean process(String opt, ListIterator<String> args) {
                    optionsProcessed.add(this);
                    showversion = true;
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
    private void finishOptionSettings0() throws Fault {
        ensureOutputDirExists();
        if (urlForLink != null && pkglistUrlForLink != null)
            extern.link(urlForLink, pkglistUrlForLink, reporter, false);
        if (urlForLinkOffline != null && pkglistUrlForLinkOffline != null)
            extern.link(urlForLinkOffline, pkglistUrlForLinkOffline, reporter, true);
        if (sourcepath.length() == 0) {
            sourcepath = System.getProperty("env.class.path", "");
        }
        if (docencoding == null) {
            docencoding = encoding;
        }
        typeElementCatalog = new TypeElementCatalog(utils.getSpecifiedClasses(), this);
        initTagletManager(customTagStrs);
        groups.stream().forEach((grp) -> {
            group.checkPackageGroups(grp.value1, grp.value2);
        });
    }

    /**
     * Set the command line options supported by this configuration.
     *
     * @return
     * @throws DocletAbortException
     */
    public boolean setOptions() {
        try {
            initPackages();
            finishOptionSettings0();
            if (!finishOptionSettings())
                return false;

        } catch (Fault f) {
            throw new DocletAbortException(f.getMessage());
        }
        return true;
    }

    private void ensureOutputDirExists() throws Fault {
        DocFile destDir = DocFile.createFileForDirectory(this, destDirName);
        if (!destDir.exists()) {
            //Create the output directory (in case it doesn't exist yet)
            if (!destDirName.isEmpty())
                reporter.print(NOTE, getText("doclet.dest_dir_create", destDirName));
            destDir.mkdirs();
        } else if (!destDir.isDirectory()) {
            throw new Fault(getText(
                "doclet.destination_directory_not_directory_0",
                destDir.getPath()));
        } else if (!destDir.canWrite()) {
            throw new Fault(getText(
                "doclet.destination_directory_not_writable_0",
                destDir.getPath()));
        }
    }

    /**
     * Initialize the taglet manager.  The strings to initialize the simple custom tags should
     * be in the following format:  "[tag name]:[location str]:[heading]".
     * @param customTagStrs the set two dimensional arrays of strings.  These arrays contain
     * either -tag or -taglet arguments.
     */
    private void initTagletManager(Set<List<String>> customTagStrs) {
        tagletManager = tagletManager == null ?
            new TagletManager(nosince, showversion, showauthor, javafx, message) :
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
                message.error("doclet.Error_invalid_custom_tag_argument", args.get(1));
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
     *
     * @return an array of tokens.
     */
    private List<String> tokenize(String s, char separator, int maxTokens) {
        List<String> tokens = new ArrayList<>();
        StringBuilder  token = new StringBuilder ();
        boolean prevIsEscapeChar = false;
        for (int i = 0; i < s.length(); i += Character.charCount(i)) {
            int currentChar = s.codePointAt(i);
            if (prevIsEscapeChar) {
                // Case 1:  escaped character
                token.appendCodePoint(currentChar);
                prevIsEscapeChar = false;
            } else if (currentChar == separator && tokens.size() < maxTokens-1) {
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

    private void addToSet(Set<String> s, String str){
        StringTokenizer st = new StringTokenizer(str, ":");
        String current;
        while(st.hasMoreTokens()){
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
     * This works exactly like DocErrorReporter. This will validate the options which are shared
     * by our doclets. For example, this method will flag an error using
     * the DocErrorReporter if user has used "-nohelp" and "-helpfile" option
     * together.
     *
     * @return true if all the options are valid.
     */
    public boolean generalValidOptions() {
        boolean docencodingfound = false;
        for (Doclet.Option opt : optionsProcessed) {
            if (opt.matches("-docencoding")) {
                docencodingfound = true;
                if (!checkOutputFileEncoding(docencoding)) {
                    return false;
                }
            };
        }
        if (!docencodingfound && (encoding != null && !encoding.isEmpty())) {
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
     * @param reporter    used to report errors.
     */
    private boolean checkOutputFileEncoding(String docencoding) {
        OutputStream ost= new ByteArrayOutputStream();
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
     * @param docfilesubdir the doc-files subdirectory to check.
     * @return true if the directory is excluded.
     */
    public boolean shouldExcludeDocFileDir(String docfilesubdir){
        return excludedDocFileDirs.contains(docfilesubdir);
    }

    /**
     * Return true if the given qualifier should be excluded and false otherwise.
     * @param qualifier the qualifier to check.
     */
    public boolean shouldExcludeQualifier(String qualifier){
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
     * @param te the TypeElement to check.
     * @return the class name
     */
    public String getClassName(TypeElement te) {
        PackageElement pkg = utils.containingPackage(te);
        return shouldExcludeQualifier(utils.getPackageName(pkg))
                ? utils.getSimpleName(te)
                : utils.getFullyQualifiedName(te);
    }

    public String getText(String key) {
        try {
            //Check the doclet specific properties file.
            return getDocletSpecificMsg().getText(key);
        } catch (Exception e) {
            //Check the shared properties file.
            return message.getText(key);
        }
    }

    public String getText(String key, String a1) {
        try {
            //Check the doclet specific properties file.
            return getDocletSpecificMsg().getText(key, a1);
        } catch (MissingResourceException e) {
            //Check the shared properties file.
            return message.getText(key, a1);
        }
    }

    public String getText(String key, String a1, String a2) {
        try {
            //Check the doclet specific properties file.
            return getDocletSpecificMsg().getText(key, a1, a2);
        } catch (MissingResourceException e) {
            //Check the shared properties file.
            return message.getText(key, a1, a2);
        }
    }

    public String getText(String key, String a1, String a2, String a3) {
        try {
            //Check the doclet specific properties file.
            return getDocletSpecificMsg().getText(key, a1, a2, a3);
        } catch (MissingResourceException e) {
            //Check the shared properties file.
            return message.getText(key, a1, a2, a3);
        }
    }

    public abstract Content newContent();

    /**
     * Get the configuration string as a content.
     *
     * @param key the key to look for in the configuration file
     * @return a content tree for the text
     */
    public Content getResource(String key) {
        Content c = newContent();
        c.addContent(getText(key));
        return c;
    }

    /**
     * Get the configuration string as a content.
     *
     * @param key the key to look for in the configuration file
     * @param o   string or content argument added to configuration text
     * @return a content tree for the text
     */
    public Content getResource(String key, Object o) {
        return getResource(key, o, null, null);
    }

    /**
     * Get the configuration string as a content.
     *
     * @param key the key to look for in the configuration file
     * @param o1 resource argument
     * @param o2 resource argument
     * @return a content tree for the text
     */
    public Content getResource(String key, Object o1, Object o2) {
        return getResource(key, o1, o2, null);
    }

    /**
     * Get the configuration string as a content.
     *
     * @param key the key to look for in the configuration file
     * @param o0  string or content argument added to configuration text
     * @param o1  string or content argument added to configuration text
     * @param o2  string or content argument added to configuration text
     * @return a content tree for the text
     */
    public Content getResource(String key, Object o0, Object o1, Object o2) {
        Content c = newContent();
        Pattern p = Pattern.compile("\\{([012])\\}");
        String text = getText(key);
        Matcher m = p.matcher(text);
        int start = 0;
        while (m.find(start)) {
            c.addContent(text.substring(start, m.start()));

            Object o = null;
            switch (m.group(1).charAt(0)) {
                case '0': o = o0; break;
                case '1': o = o1; break;
                case '2': o = o2; break;
            }

            if (o == null) {
                c.addContent("{" + m.group(1) + "}");
            } else if (o instanceof String) {
                c.addContent((String) o);
            } else if (o instanceof Content) {
                c.addContent((Content) o);
            }

            start = m.end();
        }

        c.addContent(text.substring(start));
        return c;
    }


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
     * @return the {@link WriterFactory} for the doclet.
     */
    public abstract WriterFactory getWriterFactory();

    /**
     * Return the input stream to the builder XML.
     *
     * @return the input steam to the builder XML.
     * @throws FileNotFoundException when the given XML file cannot be found.
     */
    public InputStream getBuilderXML() throws IOException {
        return builderXMLPath == null ?
            Configuration.class.getResourceAsStream(DEFAULT_BUILDER_XML) :
            DocFile.createFileForInput(this, builderXMLPath).openInputStream();
    }

    /**
     * Return the Locale for this document.
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
        private final String name;
        private final String parameters;
        private final String description;
        private final int argCount;

        protected final Configuration c;

        protected Option(Configuration config, String keyName, String name, int argCount) {
            c = config;
            String key = keyName + "name";
            String oname = getOptionsMessage(key);
            if (oname.isEmpty()) {
                this.name = name;
                this.parameters = "<MISSING KEY>";
                this.description = "<MISSING KEY>";
            } else {
                this.name = oname;
                this.parameters = getOptionsMessage(keyName + "parameters");
                this.description = getOptionsMessage(keyName + "description");
            }
            this.argCount = argCount;
        }

        protected Option(String prefix, Configuration config, String name, int argCount) {
            this(config, prefix + name.toLowerCase() + ".", name, argCount);
        }

        protected Option(Configuration config, String name, int argCount) {
            this("doclet.usage.", config,  name, argCount);
        }

        protected Option(Configuration config, String name) {
            this(config, name, 0);
        }

        private String getOptionsMessage(String key) {
            try {
                return c.getDocletSpecificMsg().getText(key, (Object[]) null);
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
        public String getName() {
            return name;
        }

        @Override
        public String getParameters() {
            return parameters;
        }

        /**
         * Maintains the formatting for javadoc -help. Note the space
         * alignment.
         */
        @Override
        public String toString() {
            String opt = name + " " + parameters;
            int optlen = opt.length();
            int spaces = 32 - optlen;
            StringBuffer sb = new StringBuffer("  -").append(opt);
            for (int i = 0; i < spaces; i++) {
                sb.append(" ");
            }
            sb.append(description);
            return sb.toString();
        }

        @Override
        public int getArgumentCount() {
            return argCount;
        }

        @Override
        public boolean matches(String option) {
            String arg = option.startsWith("-") ? option.substring(1) : option;
            return name.toLowerCase().equals(arg.toLowerCase());
        }

        @Override
        public int compareTo(Option that) {
            return this.getName().compareTo(that.getName());
        }
    }

    public abstract class XOption extends Option {

        public XOption(Configuration config, String keyname, String name, int argCount) {
            super(config, keyname, name, argCount);
        }

        public XOption(Configuration config, String name, int argCount) {
            super("doclet.xusage.", config, name, argCount);
        }

        public XOption(Configuration config, String name) {
            this(config, name, 0);
        }

        @Override
        public Option.Kind getKind() {
            return Doclet.Option.Kind.EXTENDED;
        }
    }

    public abstract class Hidden extends Option {

        public Hidden(Configuration config, String name, int argCount) {
            super("doclet.xusage.", config, name, argCount);
        }

        public Hidden(Configuration config, String name) {
            this(config, name, 0);
        }

        @Override
        public Option.Kind getKind() {
            return Doclet.Option.Kind.OTHER;
        }
    }

    /*
     * Stores a pair of Strings.
     */
    protected static class GroupContainer {
        final String value1;
        final String value2;
        public GroupContainer(String value1, String value2) {
            this.value1 = value1;
            this.value2 = value2;
        }
    }
}
