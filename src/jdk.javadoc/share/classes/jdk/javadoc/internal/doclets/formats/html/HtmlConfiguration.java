/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.DocumentationTool;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;

import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;
import jdk.javadoc.doclet.StandardDoclet;
import jdk.javadoc.doclet.Taglet;
import jdk.javadoc.internal.Versions;
import jdk.javadoc.internal.doclets.formats.html.taglets.TagletManager;
import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.BaseOptions;
import jdk.javadoc.internal.doclets.toolkit.DocletException;
import jdk.javadoc.internal.doclets.toolkit.Messages;
import jdk.javadoc.internal.doclets.toolkit.Resources;
import jdk.javadoc.internal.doclets.toolkit.util.DeprecatedAPIListBuilder;
import jdk.javadoc.internal.doclets.toolkit.util.DocFile;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.NewAPIBuilder;
import jdk.javadoc.internal.doclets.toolkit.util.PreviewAPIListBuilder;
import jdk.javadoc.internal.doclets.toolkit.util.RestrictedAPIListBuilder;
import jdk.javadoc.internal.doclets.toolkit.util.SimpleDocletException;

/**
 * Configure the output based on the command-line options.
 * <p>
 * Also determine the length of the command-line option. For example,
 * for a option "-header" there will be a string argument associated, then the
 * the length of option "-header" is two. But for option "-nohelp" no argument
 * is needed so it's length is 1.
 * </p>
 * <p>
 * Also do the error checking on the options used. For example it is illegal to
 * use "-helpfile" option when already "-nohelp" option is used.
 * </p>
 */
public class HtmlConfiguration extends BaseConfiguration {

    /**
     * Default charset for HTML.
     */
    public static final String HTML_DEFAULT_CHARSET = "utf-8";

    public final Resources docResources;

    /**
     * First file to appear in the right-hand frame in the generated
     * documentation.
     */
    public DocPath topFile = DocPath.empty;

    /**
     * The TypeElement for the class file getting generated.
     */
    public TypeElement currentTypeElement = null;  // Set this TypeElement in the ClassWriter.

    /**
     * The collections of items for the main index.
     * This field is only initialized if {@code options.createIndex()}
     * is {@code true}.
     * This index is populated somewhat lazily:
     * 1. items found in doc comments are found while generating declaration pages
     * 2. items for elements are added in bulk before generating the index files
     * 3. additional items are added as needed
     */
    public HtmlIndexBuilder indexBuilder;

    /**
     * The collection of deprecated items, if any, to be displayed on the deprecated-list page,
     * or null if the page should not be generated.
     * The page will not be generated if {@link BaseOptions#noDeprecated() no deprecated items}
     * are to be included in the documentation,
     * or if the page is {@link HtmlOptions#noDeprecatedList() not wanted},
     * or if there are no deprecated elements being documented.
     */
    protected DeprecatedAPIListBuilder deprecatedAPIListBuilder;

    /**
     * The collection of preview items, if any, to be displayed on the preview-list page,
     * or null if the page should not be generated.
     * The page will not be generated if there are no preview elements being documented.
     */
    protected PreviewAPIListBuilder previewAPIListBuilder;

    /**
     * The collection of new API items, if any, to be displayed on the new-list page,
     * or null if the page should not be generated.
     * The page is only generated if the {@code --since} option is used with release
     * names matching {@code @since} tags in the documented code.
     */
    protected NewAPIBuilder newAPIPageBuilder;

    /**
     * The collection of restricted methods, if any, to be displayed on the
     * restricted-list page, or null if the page should not be generated.
     * The page will not be generated if there are no restricted methods to be
     * documented.
     */
    protected RestrictedAPIListBuilder restrictedAPIListBuilder;

    public Contents contents;

    public final Messages messages;

    public DocPaths docPaths;

    public HtmlIds htmlIds;

    public Map<Element, List<DocPath>> localStylesheetMap = new HashMap<>();

    private final HtmlOptions options;

    /**
     * The taglet manager.
     */
    public TagletManager tagletManager;

    /**
     * Kinds of conditional pages.
     */
    // Note: this should (eventually) be merged with Navigation.PageMode,
    // which performs a somewhat similar role
    public enum ConditionalPage {
        CONSTANT_VALUES, DEPRECATED, EXTERNAL_SPECS, PREVIEW, RESTRICTED,
        SERIALIZED_FORM, SYSTEM_PROPERTIES, NEW
    }

    /**
     * A set of values indicating which conditional pages should be generated.
     *
     * The set is computed lazily, although values must (obviously) be set before
     * they are required, such as when deciding whether to generate links
     * to these files in the navigation bar, on each page, the help file, and so on.
     *
     * The value for any page may depend on both command-line options to enable or
     * disable a page, and on content being found for the page, such as deprecated
     * items to appear in the summary page of deprecated items.
     */
    public final Set<ConditionalPage> conditionalPages;

    /**
     * The build date, to be recorded in generated files.
     */
    private ZonedDateTime buildDate;

    /**
     * The set of packages for which we have copied the doc files.
     */
    private final Set<PackageElement> containingPackagesSeen;

    /**
     * Constructs the full configuration needed by the doclet, including
     * the format-specific part, defined in this class, and the format-independent
     * part, defined in the supertype.
     *
     * @apiNote The {@code doclet} parameter is used when
     * {@link Taglet#init(DocletEnvironment, Doclet) initializing tags}.
     * Some doclets (such as the {@link StandardDoclet}), may delegate to another
     * (such as the {@link HtmlDoclet}).  In such cases, the primary doclet (i.e
     * {@code StandardDoclet}) should be provided here, and not any internal
     * class like {@code HtmlDoclet}.
     *
     * @param doclet   the doclet for this run of javadoc
     * @param locale   the locale for the generated documentation
     * @param reporter the reporter to use for console messages
     */
    public HtmlConfiguration(Doclet doclet, Locale locale, Reporter reporter) {
        super(doclet, locale, reporter);

        // Use the default locale for console messages.
        Resources msgResources = new Resources(Locale.getDefault(),
                BaseConfiguration.sharedResourceBundleName,
                "jdk.javadoc.internal.doclets.formats.html.resources.standard");

        // Use the provided locale for generated docs
        // Ideally, the doc resources would be in different resource files than the
        // message resources, so that we do not have different copies of the same resources.
        if (locale.equals(Locale.getDefault())) {
            docResources = msgResources;
        } else {
            docResources = new Resources(locale,
                    BaseConfiguration.sharedResourceBundleName,
                    "jdk.javadoc.internal.doclets.formats.html.resources.standard");
        }

        messages = new Messages(this, msgResources);
        options = new HtmlOptions(this);
        containingPackagesSeen = new HashSet<>();

        Runtime.Version v;
        try {
            v = Versions.javadocVersion();
        } catch (RuntimeException e) {
            assert false : e;
            v = Runtime.version(); // arguably, the only sensible default
        }
        docletVersion = v;

        conditionalPages = EnumSet.noneOf(ConditionalPage.class);
    }

    @Override
    protected void initConfiguration(DocletEnvironment docEnv,
                                     Function<String, String> resourceKeyMapper) {
        super.initConfiguration(docEnv, resourceKeyMapper);
        contents = new Contents(this);
        htmlIds = new HtmlIds(this);
    }

    private final Runtime.Version docletVersion;

    @Override
    public Runtime.Version getDocletVersion() {
        return docletVersion;
    }

    @Override
    public Resources getDocResources() {
        return docResources;
    }

    /**
     * Returns a utility object providing commonly used fragments of content.
     *
     * @return a utility object providing commonly used fragments of content
     */
    public Contents getContents() {
        return Objects.requireNonNull(contents);
    }

    @Override
    public Messages getMessages() {
        return messages;
    }

    @Override
    public HtmlOptions getOptions() {
        return options;
    }

    /**
     * {@return the packages for which we have copied the doc files}
     *
     * @see ClassWriter#copyDocFiles()
     */
    public Set<PackageElement> getContainingPackagesSeen() {
        return containingPackagesSeen;
    }

    @Override
    public boolean finishOptionSettings() {
        if (!options.validateOptions()) {
            return false;
        }

        ZonedDateTime zdt = options.date();
        buildDate = zdt != null ? zdt : ZonedDateTime.now();

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
        if (options.createIndex()) {
            indexBuilder = new HtmlIndexBuilder(this);
        }
        docPaths = new DocPaths(utils);
        setCreateOverview();
        setTopFile();
        initDocLint(options.doclintOpts(), tagletManager.getAllTagletNames());
        return true;
    }

    /**
     * {@return the date to be recorded in generated files}
     */
    public ZonedDateTime getBuildDate() {
        return buildDate;
    }

    /**
     * Decide the page which will appear first in the right-hand frame. It will
     * be "overview-summary.html" if "-overview" option is used or no
     * "-overview" but the number of packages is more than one. It will be
     * "package-summary.html" of the respective package if there is only one
     * package to document. It will be a class page(first in the sorted order),
     * if only classes are provided on the command line.
     */
    protected void setTopFile() {
        if (!checkForDeprecation()) {
            return;
        }
        if (options.createOverview()) {
            topFile = DocPaths.INDEX;
        } else {
            if (showModules) {
                topFile = DocPath.empty.resolve(docPaths.moduleSummary(modules.first()));
            } else if (!packages.isEmpty()) {
                topFile = docPaths.forPackage(packages.first()).resolve(DocPaths.PACKAGE_SUMMARY);
            }
        }
    }

    protected TypeElement getValidClass(List<TypeElement> classes) {
        if (!options.noDeprecated()) {
            return classes.get(0);
        }
        for (TypeElement te : classes) {
            if (!utils.isDeprecated(te)) {
                return te;
            }
        }
        return null;
    }

    protected boolean checkForDeprecation() {
        for (TypeElement te : getIncludedTypeElements()) {
            if (isGeneratedDoc(te)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generate "overview.html" page if option "-overview" is used or number of
     * packages is more than one. Sets {@code HtmlOptions.createOverview} field to true.
     */
    protected void setCreateOverview() {
        if (!options.noOverview()) {
            if (options.overviewPath() != null
                    || modules.size() > 1
                    || (modules.isEmpty() && packages.size() > 1)) {
                options.setCreateOverview(true);
            }
        }
    }

    public WriterFactory getWriterFactory() {
        // TODO: this is called many times: why not create and use a single instance?
        return new WriterFactory(this);
    }

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
        String overviewpath = options.overviewPath();
        if (overviewpath != null && getFileManager() instanceof StandardJavaFileManager fm) {
            return fm.getJavaFileObjects(overviewpath).iterator().next();
        }
        return null;
    }

    public DocPath getMainStylesheet() {
        String stylesheetfile = options.stylesheetFile();
        if(!stylesheetfile.isEmpty()){
            DocFile docFile = DocFile.createFileForInput(this, stylesheetfile);
            return DocPath.create(docFile.getName());
        }
        return  null;
    }

    public List<DocPath> getAdditionalStylesheets() {
        return options.additionalStylesheets().stream()
                .map(ssf -> DocFile.createFileForInput(this, ssf))
                .map(file -> DocPath.create(file.getName()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public List<DocPath> getAdditionalScripts() {
        return options.additionalScripts().stream()
                .map(sf -> DocFile.createFileForInput(this, sf))
                .map(file -> DocPath.create(file.getName()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public JavaFileManager getFileManager() {
        return docEnv.getJavaFileManager();
    }

    @Override
    protected boolean finishOptionSettings0() throws DocletException {
        if (options.docEncoding() == null) {
            if (options.charset() == null) {
                String charset = (options.encoding() == null) ? HTML_DEFAULT_CHARSET : options.encoding();
                options.setCharset(charset);
                options.setDocEncoding((options.charset()));
            } else {
                options.setDocEncoding(options.charset());
            }
        } else {
            if (options.charset() == null) {
                options.setCharset(options.docEncoding());
            } else if (!options.charset().equals(options.docEncoding())) {
                messages.error("doclet.Option_conflict", "-charset", "-docencoding");
                return false;
            }
        }

        String snippetPath = options.snippetPath();
        if (snippetPath != null) {
            Messages messages = getMessages();
            JavaFileManager fm = getFileManager();
            if (fm instanceof StandardJavaFileManager) {
                try {
                    List<Path> sp = Arrays.stream(snippetPath.split(File.pathSeparator))
                            .map(Path::of)
                            .toList();
                    StandardJavaFileManager sfm = (StandardJavaFileManager) fm;
                    sfm.setLocationFromPaths(DocumentationTool.Location.SNIPPET_PATH, sp);
                } catch (IOException | InvalidPathException e) {
                    throw new SimpleDocletException(messages.getResources().getText(
                            "doclet.error_setting_snippet_path", snippetPath, e), e);
                }
            } else {
                throw new SimpleDocletException(messages.getResources().getText(
                        "doclet.cannot_use_snippet_path", snippetPath));
            }
        }

        initTagletManager(options.customTagStrs());

        return super.finishOptionSettings0();
    }

    /**
     * Initialize the taglet manager.  The strings to initialize the simple custom tags should
     * be in the following format:  "[tag name]:[location str]:[heading]".
     *
     * @param customTagStrs the set two-dimensional arrays of strings.  These arrays contain
     *                      either -tag or -taglet arguments.
     */
    private void initTagletManager(Set<List<String>> customTagStrs) {
        tagletManager = tagletManager != null ? tagletManager : new TagletManager(this);
        JavaFileManager fileManager = getFileManager();
        Messages messages = getMessages();
        try {
            tagletManager.initTagletPath(fileManager);
            tagletManager.loadTaglets(fileManager);

            for (List<String> args : customTagStrs) {
                if (args.get(0).equals("-taglet")) {
                    tagletManager.addCustomTag(args.get(1), fileManager);
                    continue;
                }
                /* Since there are few constraints on the characters in a tag name,
                 * and real world examples with ':' in the tag name, we cannot simply use
                 * String.split(regex);  instead, we tokenize the string, allowing
                 * special characters to be escaped with '\'. */
                List<String> tokens = tokenize(args.get(1), 3);
                switch (tokens.size()) {
                    case 1 -> {
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
                    }

                    case 2 ->
                        //Add simple taglet without heading, probably to excluding it in the output.
                            tagletManager.addNewSimpleCustomTag(tokens.get(0), tokens.get(1), "");

                    case 3 ->
                            tagletManager.addNewSimpleCustomTag(tokens.get(0), tokens.get(2), tokens.get(1));

                    default ->
                            messages.error("doclet.Error_invalid_custom_tag_argument", args.get(1));
                }
            }
        } catch (IOException e) {
            messages.error("doclet.taglet_could_not_set_location", e.toString());
        }
    }

    /**
     * Given a string, return an array of tokens, separated by ':'.
     * The separator character can be escaped with the '\' character.
     * The '\' character may also be escaped with the '\' character.
     *
     * @param s         the string to tokenize
     * @param maxTokens the maximum number of tokens returned.  If the
     *                  max is reached, the remaining part of s is appended
     *                  to the end of the last token.
     * @return an array of tokens
     */
    private List<String> tokenize(String s, int maxTokens) {
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean prevIsEscapeChar = false;
        for (int i = 0; i < s.length(); i += Character.charCount(i)) {
            int currentChar = s.codePointAt(i);
            if (prevIsEscapeChar) {
                // Case 1:  escaped character
                token.appendCodePoint(currentChar);
                prevIsEscapeChar = false;
            } else if (currentChar == ':' && tokens.size() < maxTokens - 1) {
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

}
