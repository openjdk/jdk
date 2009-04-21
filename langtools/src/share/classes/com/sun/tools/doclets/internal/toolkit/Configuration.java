/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.doclets.internal.toolkit;

import com.sun.tools.doclets.internal.toolkit.taglets.*;
import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.tools.doclets.internal.toolkit.builders.BuilderFactory;
import com.sun.javadoc.*;
import java.util.*;
import java.io.*;

/**
 * Configure the output based on the options. Doclets should sub-class
 * Configuration, to configure and add their own options. This class contains
 * all user options which are supported by the 1.1 doclet and the standard
 * doclet.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @author Robert Field.
 * @author Atul Dambalkar.
 * @author Jamie Ho
 */
public abstract class Configuration {

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
     * supress excessive warnings about serial tag.
     */
    public boolean serialwarn = false;

    /**
     * The specified amount of space between tab stops.
     */
    public int sourcetab = DocletConstants.DEFAULT_TAB_STOP_LENGTH;

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
    public final MetaKeywords metakeywords = new MetaKeywords(this);

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
    public abstract void setSpecificDocletOptions(String[][] options);

    /**
     * Return the doclet specific {@link MessageRetriever}
     * @return the doclet specific MessageRetriever.
     */
    public abstract MessageRetriever getDocletSpecificMsg();

    /**
     * An array of the packages specified on the command-line merged
     * with the array of packages that contain the classes specified on the
     * command-line.  The array is sorted.
     */
    public PackageDoc[] packages;

    /**
     * Constructor. Constructs the message retriever with resource file.
     */
    public Configuration() {
        message =
            new MessageRetriever(this,
            "com.sun.tools.doclets.internal.toolkit.resources.doclets");
        excludedDocFileDirs = new HashSet<String>();
        excludedQualifiers = new HashSet<String>();
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
        option = option.toLowerCase();
        if (option.equals("-author") ||
            option.equals("-docfilessubdirs") ||
            option.equals("-keywords") ||
            option.equals("-linksource") ||
            option.equals("-nocomment") ||
            option.equals("-nodeprecated") ||
            option.equals("-nosince") ||
            option.equals("-notimestamp") ||
            option.equals("-quiet") ||
            option.equals("-xnodate") ||
            option.equals("-version")) {
            return 1;
        } else if (option.equals("-d") ||
                   option.equals("-docencoding") ||
                   option.equals("-encoding") ||
                   option.equals("-excludedocfilessubdir") ||
                   option.equals("-link") ||
                   option.equals("-sourcetab") ||
                   option.equals("-noqualifier") ||
                   option.equals("-output") ||
                   option.equals("-sourcepath") ||
                   option.equals("-tag") ||
                   option.equals("-taglet") ||
                   option.equals("-tagletpath")) {
            return 2;
        } else if (option.equals("-group") ||
                   option.equals("-linkoffline")) {
            return 3;
        } else {
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

    private void initPackageArray() {
        Set<PackageDoc> set = new HashSet<PackageDoc>(Arrays.asList(root.specifiedPackages()));
        ClassDoc[] classes = root.specifiedClasses();
        for (int i = 0; i < classes.length; i++) {
            set.add(classes[i].containingPackage());
        }
        ArrayList<PackageDoc> results = new ArrayList<PackageDoc>(set);
        Collections.sort(results);
        packages = results.toArray(new PackageDoc[] {});
    }

    /**
     * Set the command line options supported by this configuration.
     *
     * @param options the two dimensional array of options.
     */
    public void setOptions(String[][] options) {
        LinkedHashSet<String[]> customTagStrs = new LinkedHashSet<String[]>();
        for (int oi = 0; oi < options.length; ++oi) {
            String[] os = options[oi];
            String opt = os[0].toLowerCase();
            if (opt.equals("-d")) {
                destDirName = addTrailingFileSep(os[1]);
                docFileDestDirName = destDirName;
            } else  if (opt.equals("-docfilessubdirs")) {
                copydocfilesubdirs = true;
            } else  if (opt.equals("-docencoding")) {
                docencoding = os[1];
            } else  if (opt.equals("-encoding")) {
                encoding = os[1];
            } else  if (opt.equals("-author")) {
                showauthor = true;
            } else  if (opt.equals("-version")) {
                showversion = true;
            } else  if (opt.equals("-nodeprecated")) {
                nodeprecated = true;
            } else  if (opt.equals("-sourcepath")) {
                sourcepath = os[1];
            } else if (opt.equals("-classpath") &&
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
                    sourcetab = Integer.parseInt(os[1]);
                } catch (NumberFormatException e) {
                    //Set to -1 so that warning will be printed
                    //to indicate what is valid argument.
                    sourcetab = -1;
                }
                if (sourcetab <= 0) {
                    message.warning("doclet.sourcetab_warning");
                    sourcetab = DocletConstants.DEFAULT_TAB_STOP_LENGTH;
                }
            } else  if (opt.equals("-notimestamp")) {
                notimestamp = true;
            } else  if (opt.equals("-nocomment")) {
                nocomment = true;
            } else if (opt.equals("-tag") || opt.equals("-taglet")) {
                customTagStrs.add(os);
            } else if (opt.equals("-tagletpath")) {
                tagletpath = os[1];
            } else  if (opt.equals("-keywords")) {
                keywords = true;
            } else  if (opt.equals("-serialwarn")) {
                serialwarn = true;
            } else if (opt.equals("-group")) {
                group.checkPackageGroups(os[1], os[2]);
            } else if (opt.equals("-link")) {
                String url = os[1];
                extern.url(url, url, root, false);
            } else if (opt.equals("-linkoffline")) {
                String url = os[1];
                String pkglisturl = os[2];
                extern.url(url, pkglisturl, root, true);
            }
        }
        if (sourcepath.length() == 0) {
            sourcepath = System.getProperty("env.class.path") == null ? "" :
                System.getProperty("env.class.path");
        }
        if (docencoding == null) {
            docencoding = encoding;
        }

        classDocCatalog = new ClassDocCatalog(root.specifiedClasses());
        initTagletManager(customTagStrs);
    }

    /**
     * Set the command line options supported by this configuration.
     *
     * @throws DocletAbortException
     */
    public void setOptions() {
        initPackageArray();
        setOptions(root.options());
        setSpecificDocletOptions(root.options());
    }


    /**
     * Initialize the taglet manager.  The strings to initialize the simple custom tags should
     * be in the following format:  "[tag name]:[location str]:[heading]".
     * @param customTagStrs the set two dimentional arrays of strings.  These arrays contain
     * either -tag or -taglet arguments.
     */
    private void initTagletManager(Set<String[]> customTagStrs) {
        tagletManager = tagletManager == null ?
            new TagletManager(nosince, showversion, showauthor, message) :
            tagletManager;
        String[] args;
        for (Iterator<String[]> it = customTagStrs.iterator(); it.hasNext(); ) {
            args = it.next();
            if (args[0].equals("-taglet")) {
                tagletManager.addCustomTag(args[1], tagletpath);
                continue;
            }
            String[] tokens = Util.tokenize(args[1],
                TagletManager.SIMPLE_TAGLET_OPT_SEPERATOR, 3);
            if (tokens.length == 1) {
                String tagName = args[1];
                if (tagletManager.isKnownCustomTag(tagName)) {
                    //reorder a standard tag
                    tagletManager.addNewSimpleCustomTag(tagName, null, "");
                } else {
                    //Create a simple tag with the heading that has the same name as the tag.
                    StringBuffer heading = new StringBuffer(tagName + ":");
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

    private void addToSet(Set<String> s, String str){
        StringTokenizer st = new StringTokenizer(str, ":");
        String current;
        while(st.hasMoreTokens()){
            current = st.nextToken();
            s.add(current);
        }
    }

    /**
     * Add a traliling file separator, if not found or strip off extra trailing
     * file separators if any.
     *
     * @param path Path under consideration.
     * @return String Properly constructed path string.
     */
    String addTrailingFileSep(String path) {
        String fs = System.getProperty("file.separator");
        String dblfs = fs + fs;
        int indexDblfs;
        while ((indexDblfs = path.indexOf(dblfs)) >= 0) {
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
            String opt = os[0].toLowerCase();
            if (opt.equals("-d")) {
                String destdirname = addTrailingFileSep(os[1]);
                File destDir = new File(destdirname);
                if (!destDir.exists()) {
                    //Create the output directory (in case it doesn't exist yet)
                    reporter.printNotice(getText("doclet.dest_dir_create",
                        destdirname));
                    (new File(destdirname)).mkdirs();
                } else if (!destDir.isDirectory()) {
                    reporter.printError(getText(
                        "doclet.destination_directory_not_directory_0",
                        destDir.getPath()));
                    return false;
                } else if (!destDir.canWrite()) {
                    reporter.printError(getText(
                        "doclet.destination_directory_not_writable_0",
                        destDir.getPath()));
                    return false;
                }
            } else if (opt.equals("-docencoding")) {
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

    /**
     * Return true if the doc element is getting documented, depending upon
     * -nodeprecated option and @deprecated tag used. Return true if
     * -nodeprecated is not used or @deprecated tag is not used.
     */
    public boolean isGeneratedDoc(Doc doc) {
        if (!nodeprecated) {
            return true;
        }
        return (doc.tags("deprecated")).length == 0;
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
    public InputStream getBuilderXML() throws FileNotFoundException {
        return builderXMLPath == null ?
            Configuration.class.getResourceAsStream(DEFAULT_BUILDER_XML) :
            new FileInputStream(new File(builderXMLPath));
    }

    /**
     * Return the Locale for this document.
     */
    public abstract Locale getLocale();

    /**
     * Return the comparator that will be used to sort member documentation.
     * To no do any sorting, return null.
     *
     * @return the {@link java.util.Comparator} used to sort members.
     */
    public abstract Comparator<ProgramElementDoc> getMemberComparator();
}
