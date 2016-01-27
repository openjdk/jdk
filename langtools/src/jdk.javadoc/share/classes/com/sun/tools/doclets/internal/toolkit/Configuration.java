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

package com.sun.tools.doclets.internal.toolkit;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.tools.JavaFileManager;

import com.sun.javadoc.*;
import com.sun.tools.javac.sym.Profiles;
import com.sun.tools.javac.jvm.Profile;
import com.sun.tools.doclets.internal.toolkit.builders.BuilderFactory;
import com.sun.tools.doclets.internal.toolkit.taglets.*;
import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.tools.doclets.internal.toolkit.util.VisibleMemberMap.GetterSetter;
import com.sun.tools.javac.util.StringUtils;

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
    public RootDoc root;

    /**
     * An utility class for commonly used helpers
     */
    public Utils utils;
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
     * Argument for command line option "-Xprofilespath".
     */
    public String profilespath = "";

    /**
     * Generate profiles documentation if profilespath is set and valid profiles
     * are present.
     */
    public boolean showProfiles = false;

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
    public ClassDocCatalog classDocCatalog;

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

    /**
     * Return the build date for the doclet.
     */
    public abstract String getDocletSpecificBuildDate();

    /**
     * This method should be defined in all those doclets(configurations),
     * which want to derive themselves from this Configuration. This method
     * can be used to set its own command line options.
     *
     * @param options The array of option names and values.
     * @throws DocletAbortException
     */
    public abstract void setSpecificDocletOptions(String[][] options) throws Fault;

    /**
     * Return the doclet specific {@link MessageRetriever}
     * @return the doclet specific MessageRetriever.
     */
    public abstract MessageRetriever getDocletSpecificMsg();

    /**
     * A profiles object used to access profiles across various pages.
     */
    public Profiles profiles;

    /**
     * A map of the profiles to packages.
     */
    public Map<String, List<PackageDoc>> profilePackages;

    /**
     * A sorted set of packages specified on the command-line merged with a
     * collection of packages that contain the classes specified on the
     * command-line.
     */
    public SortedSet<PackageDoc> packages;

    // The following three fields provide caches for use by all instances of VisibleMemberMap.
    public final Map<ClassDoc, ProgramElementDoc[]> propertiesCache = new HashMap<>();
    public final Map<ProgramElementDoc, ProgramElementDoc> classPropertiesMap = new HashMap<>();
    public final Map<ProgramElementDoc, GetterSetter> getterSetterMap = new HashMap<>();

    /**
     * Constructor. Constructs the message retriever with resource file.
     */
    public Configuration() {
        message =
            new MessageRetriever(this,
            "com.sun.tools.doclets.internal.toolkit.resources.doclets");
        excludedDocFileDirs = new HashSet<>();
        excludedQualifiers = new HashSet<>();
        setTabWidth(DocletConstants.DEFAULT_TAB_STOP_LENGTH);
        utils = new Utils();
        metakeywords = new MetaKeywords(this);
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

    /**
     * This method should be defined in all those doclets
     * which want to inherit from this Configuration. This method
     * should return the number of arguments to the command line
     * option (including the option name).  For example,
     * -notimestamp is a single-argument option, so this method would
     * return 1.
     *
     * @param option Command line option under consideration.
     * @return number of arguments to option (including the
     * option name). Zero return means option not known.
     * Negative value means error occurred.
     */
    public int optionLength(String option) {
        option = StringUtils.toLowerCase(option);
        switch (option) {
            case "-author":
            case "-docfilessubdirs":
            case "-javafx":
            case "-keywords":
            case "-linksource":
            case "-nocomment":
            case "-nodeprecated":
            case "-nosince":
            case "-notimestamp":
            case "-quiet":
            case "-xnodate":
            case "-version":
                return 1;
            case "-d":
            case "-docencoding":
            case "-encoding":
            case "-excludedocfilessubdir":
            case "-link":
            case "-sourcetab":
            case "-noqualifier":
            case "-output":
            case "-sourcepath":
            case "-tag":
            case "-taglet":
            case "-tagletpath":
            case "-xprofilespath":
                return 2;
            case "-group":
            case "-linkoffline":
                return 3;
            default:
                return -1;  // indicate we don't know about it
        }
    }

    /**
     * Perform error checking on the given options.
     *
     * @param options  the given options to check.
     * @param reporter the reporter used to report errors.
     */
    public abstract boolean validOptions(String options[][],
        DocErrorReporter reporter);

    private void initProfiles() throws IOException {
        if (profilespath.isEmpty())
            return;

        profiles = Profiles.read(new File(profilespath));

        // Group the packages to be documented by the lowest profile (if any)
        // in which each appears
        Map<Profile, List<PackageDoc>> interimResults = new EnumMap<>(Profile.class);
        for (Profile p: Profile.values())
            interimResults.put(p, new ArrayList<PackageDoc>());

        for (PackageDoc pkg: packages) {
            if (nodeprecated && utils.isDeprecated(pkg)) {
                continue;
            }
            // the getProfile method takes a type name, not a package name,
            // but isn't particularly fussy about the simple name -- so just use *
            int i = profiles.getProfile(pkg.name().replace(".", "/") + "/*");
            Profile p = Profile.lookup(i);
            if (p != null) {
                List<PackageDoc> pkgs = interimResults.get(p);
                pkgs.add(pkg);
            }
        }

        // Build the profilePackages structure used by the doclet
        profilePackages = new HashMap<>();
        List<PackageDoc> prev = Collections.<PackageDoc>emptyList();
        int size;
        for (Map.Entry<Profile,List<PackageDoc>> e: interimResults.entrySet()) {
            Profile p = e.getKey();
            List<PackageDoc> pkgs =  e.getValue();
            pkgs.addAll(prev); // each profile contains all lower profiles
            Collections.sort(pkgs);
            size = pkgs.size();
            // For a profile, if there are no packages to be documented, do not add
            // it to profilePackages map.
            if (size > 0)
                profilePackages.put(p.name, pkgs);
            prev = pkgs;
        }

        // Generate profiles documentation if any profile contains any
        // of the packages to be documented.
        showProfiles = !prev.isEmpty();
    }

    private void initPackages() {
        packages = new TreeSet<>(Arrays.asList(root.specifiedPackages()));
        for (ClassDoc aClass : root.specifiedClasses()) {
            packages.add(aClass.containingPackage());
        }
    }

    /**
     * Set the command line options supported by this configuration.
     *
     * @param options the two dimensional array of options.
     */
    public void setOptions(String[][] options) throws Fault {
        LinkedHashSet<String[]> customTagStrs = new LinkedHashSet<>();

        // Some options, specifically -link and -linkoffline, require that
        // the output directory has already been created: so do that first.
        for (String[] os : options) {
            String opt = StringUtils.toLowerCase(os[0]);
            if (opt.equals("-d")) {
                destDirName = addTrailingFileSep(os[1]);
                docFileDestDirName = destDirName;
                ensureOutputDirExists();
                break;
            }
        }

        for (String[] os : options) {
            String opt = StringUtils.toLowerCase(os[0]);
            if (opt.equals("-docfilessubdirs")) {
                copydocfilesubdirs = true;
            } else if (opt.equals("-docencoding")) {
                docencoding = os[1];
            } else if (opt.equals("-encoding")) {
                encoding = os[1];
            } else if (opt.equals("-author")) {
                showauthor = true;
            } else  if (opt.equals("-javafx")) {
                javafx = true;
            } else if (opt.equals("-nosince")) {
                nosince = true;
            } else if (opt.equals("-version")) {
                showversion = true;
            } else if (opt.equals("-nodeprecated")) {
                nodeprecated = true;
            } else if (opt.equals("-sourcepath")) {
                sourcepath = os[1];
            } else if ((opt.equals("-classpath") || opt.equals("-cp")) &&
                       sourcepath.length() == 0) {
                sourcepath = os[1];
            } else if (opt.equals("-excludedocfilessubdir")) {
                addToSet(excludedDocFileDirs, os[1]);
            } else if (opt.equals("-noqualifier")) {
                addToSet(excludedQualifiers, os[1]);
            } else if (opt.equals("-linksource")) {
                linksource = true;
            } else if (opt.equals("-sourcetab")) {
                linksource = true;
                try {
                    setTabWidth(Integer.parseInt(os[1]));
                } catch (NumberFormatException e) {
                    //Set to -1 so that warning will be printed
                    //to indicate what is valid argument.
                    sourcetab = -1;
                }
                if (sourcetab <= 0) {
                    message.warning("doclet.sourcetab_warning");
                    setTabWidth(DocletConstants.DEFAULT_TAB_STOP_LENGTH);
                }
            } else if (opt.equals("-notimestamp")) {
                notimestamp = true;
            } else if (opt.equals("-nocomment")) {
                nocomment = true;
            } else if (opt.equals("-tag") || opt.equals("-taglet")) {
                customTagStrs.add(os);
            } else if (opt.equals("-tagletpath")) {
                tagletpath = os[1];
            }  else if (opt.equals("-xprofilespath")) {
                profilespath = os[1];
            } else if (opt.equals("-keywords")) {
                keywords = true;
            } else if (opt.equals("-serialwarn")) {
                serialwarn = true;
            } else if (opt.equals("-group")) {
                group.checkPackageGroups(os[1], os[2]);
            } else if (opt.equals("-link")) {
                String url = os[1];
                extern.link(url, url, root, false);
            } else if (opt.equals("-linkoffline")) {
                String url = os[1];
                String pkglisturl = os[2];
                extern.link(url, pkglisturl, root, true);
            }
        }
        if (sourcepath.length() == 0) {
            sourcepath = System.getProperty("env.class.path") == null ? "" :
                System.getProperty("env.class.path");
        }
        if (docencoding == null) {
            docencoding = encoding;
        }

        classDocCatalog = new ClassDocCatalog(root.specifiedClasses(), this);
        initTagletManager(customTagStrs);
    }

    /**
     * Set the command line options supported by this configuration.
     *
     * @throws DocletAbortException
     */
    public void setOptions() throws Fault {
        initPackages();
        setOptions(root.options());
        try {
            initProfiles();
        } catch (Exception e) {
            throw new DocletAbortException(e);
        }
        setSpecificDocletOptions(root.options());
    }

    private void ensureOutputDirExists() throws Fault {
        DocFile destDir = DocFile.createFileForDirectory(this, destDirName);
        if (!destDir.exists()) {
            //Create the output directory (in case it doesn't exist yet)
            root.printNotice(getText("doclet.dest_dir_create", destDirName));
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
    private void initTagletManager(Set<String[]> customTagStrs) {
        tagletManager = tagletManager == null ?
            new TagletManager(nosince, showversion, showauthor, javafx, message) :
            tagletManager;
        for (String[] args : customTagStrs) {
            if (args[0].equals("-taglet")) {
                tagletManager.addCustomTag(args[1], getFileManager(), tagletpath);
                continue;
            }
            String[] tokens = tokenize(args[1],
                                       TagletManager.SIMPLE_TAGLET_OPT_SEPARATOR, 3);
            if (tokens.length == 1) {
                String tagName = args[1];
                if (tagletManager.isKnownCustomTag(tagName)) {
                    //reorder a standard tag
                    tagletManager.addNewSimpleCustomTag(tagName, null, "");
                } else {
                    //Create a simple tag with the heading that has the same name as the tag.
                    StringBuilder heading = new StringBuilder(tagName + ":");
                    heading.setCharAt(0, Character.toUpperCase(tagName.charAt(0)));
                    tagletManager.addNewSimpleCustomTag(tagName, heading.toString(), "a");
                }
            } else if (tokens.length == 2) {
                //Add simple taglet without heading, probably to excluding it in the output.
                tagletManager.addNewSimpleCustomTag(tokens[0], tokens[1], "");
            } else if (tokens.length >= 3) {
                tagletManager.addNewSimpleCustomTag(tokens[0], tokens[2], tokens[1]);
            } else {
                message.error("doclet.Error_invalid_custom_tag_argument", args[1]);
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
    private String[] tokenize(String s, char separator, int maxTokens) {
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
        return tokens.toArray(new String[] {});
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
     * This works exactly like
     * {@link com.sun.javadoc.Doclet#validOptions(String[][],
     * DocErrorReporter)}. This will validate the options which are shared
     * by our doclets. For example, this method will flag an error using
     * the DocErrorReporter if user has used "-nohelp" and "-helpfile" option
     * together.
     *
     * @param options  options used on the command line.
     * @param reporter used to report errors.
     * @return true if all the options are valid.
     */
    public boolean generalValidOptions(String options[][],
            DocErrorReporter reporter) {
        boolean docencodingfound = false;
        String encoding = "";
        for (int oi = 0; oi < options.length; oi++) {
            String[] os = options[oi];
            String opt = StringUtils.toLowerCase(os[0]);
            if (opt.equals("-docencoding")) {
                docencodingfound = true;
                if (!checkOutputFileEncoding(os[1], reporter)) {
                    return false;
                }
            } else if (opt.equals("-encoding")) {
                encoding = os[1];
            }
        }
        if (!docencodingfound && encoding.length() > 0) {
            if (!checkOutputFileEncoding(encoding, reporter)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check the validity of the given profile. Return false if there are no
     * valid packages to be documented for the profile.
     *
     * @param profileName the profile that needs to be validated.
     * @return true if the profile has valid packages to be documented.
     */
    public boolean shouldDocumentProfile(String profileName) {
        return profilePackages.containsKey(profileName);
    }

    /**
     * Check the validity of the given Source or Output File encoding on this
     * platform.
     *
     * @param docencoding output file encoding.
     * @param reporter    used to report errors.
     */
    private boolean checkOutputFileEncoding(String docencoding,
            DocErrorReporter reporter) {
        OutputStream ost= new ByteArrayOutputStream();
        OutputStreamWriter osw = null;
        try {
            osw = new OutputStreamWriter(ost, docencoding);
        } catch (UnsupportedEncodingException exc) {
            reporter.printError(getText("doclet.Encoding_not_supported",
                docencoding));
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
     */
    public boolean shouldExcludeDocFileDir(String docfilesubdir){
        if (excludedDocFileDirs.contains(docfilesubdir)) {
            return true;
        } else {
            return false;
        }
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
     * Return the qualified name of the <code>ClassDoc</code> if it's qualifier is not excluded.  Otherwise,
     * return the unqualified <code>ClassDoc</code> name.
     * @param cd the <code>ClassDoc</code> to check.
     */
    public String getClassName(ClassDoc cd) {
        PackageDoc pd = cd.containingPackage();
        if (pd != null && shouldExcludeQualifier(cd.containingPackage().name())) {
            return cd.name();
        } else {
            return cd.qualifiedName();
        }
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
        } catch (Exception e) {
            //Check the shared properties file.
            return message.getText(key, a1);
        }
    }

    public String getText(String key, String a1, String a2) {
        try {
            //Check the doclet specific properties file.
            return getDocletSpecificMsg().getText(key, a1, a2);
        } catch (Exception e) {
            //Check the shared properties file.
            return message.getText(key, a1, a2);
        }
    }

    public String getText(String key, String a1, String a2, String a3) {
        try {
            //Check the doclet specific properties file.
            return getDocletSpecificMsg().getText(key, a1, a2, a3);
        } catch (Exception e) {
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
     * @param o   string or content argument added to configuration text
     * @return a content tree for the text
     */
    public Content getResource(String key, Object o1, Object o2) {
        return getResource(key, o1, o2, null);
    }

    /**
     * Get the configuration string as a content.
     *
     * @param key the key to look for in the configuration file
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
     * Return true if the ClassDoc element is getting documented, depending upon
     * -nodeprecated option and the deprecation information. Return true if
     * -nodeprecated is not used. Return false if -nodeprecated is used and if
     * either ClassDoc element is deprecated or the containing package is deprecated.
     *
     * @param cd the ClassDoc for which the page generation is checked
     */
    public boolean isGeneratedDoc(ClassDoc cd) {
        if (!nodeprecated) {
            return true;
        }
        return !(utils.isDeprecated(cd) || utils.isDeprecated(cd.containingPackage()));
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
     */
    public abstract Locale getLocale();

    /**
     * Return the current file manager.
     */
    public abstract JavaFileManager getFileManager();

    /**
     * Return the comparator that will be used to sort member documentation.
     * To no do any sorting, return null.
     *
     * @return the {@link java.util.Comparator} used to sort members.
     */
    public abstract Comparator<ProgramElementDoc> getMemberComparator();

    private void setTabWidth(int n) {
        sourcetab = n;
        tabSpaces = String.format("%" + n + "s", "");
    }

    public abstract boolean showMessage(SourcePosition pos, String key);
}
