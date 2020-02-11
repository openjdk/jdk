/*
 * Copyright (c) 1998, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;

import com.sun.source.util.DocTreePath;

import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;
import jdk.javadoc.doclet.StandardDoclet;
import jdk.javadoc.doclet.Taglet;
import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.DocletException;
import jdk.javadoc.internal.doclets.toolkit.Messages;
import jdk.javadoc.internal.doclets.toolkit.Resources;
import jdk.javadoc.internal.doclets.toolkit.WriterFactory;
import jdk.javadoc.internal.doclets.toolkit.util.DocFile;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;

import static javax.tools.Diagnostic.Kind.*;

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
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
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

    public Map<Element, List<DocPath>> localStylesheetMap = new HashMap<>();

    private final HtmlOptions options;

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
        contents = new Contents(this);
        options = new HtmlOptions(this);

        String v;
        try {
            // the version bundle is not localized
            ResourceBundle rb = ResourceBundle.getBundle(versionBundleName, Locale.getDefault());
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
    public final Date startTime = new Date();

    @Override
    public String getDocletVersion() {
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
        return contents;
    }

    @Override
    public Messages getMessages() {
        return messages;
    }

    @Override
    public HtmlOptions getOptions() {
        return options;
    }

    @Override
    public boolean finishOptionSettings() {
        if (!options.validateOptions()) {
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
        docPaths = new DocPaths(utils);
        setCreateOverview();
        setTopFile(docEnv);
        workArounds.initDocLint(options.doclintOpts(), tagletManager.getAllTagletNames());
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
        if (options.createOverview()) {
            topFile = DocPaths.INDEX;
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

    @Override
    public WriterFactory getWriterFactory() {
        return new WriterFactoryImpl(this);
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
        if (overviewpath != null && getFileManager() instanceof StandardJavaFileManager) {
            StandardJavaFileManager fm = (StandardJavaFileManager) getFileManager();
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
                .map(ssf -> DocFile.createFileForInput(this, ssf)).map(file -> DocPath.create(file.getName()))
                .collect(Collectors.toList());
    }

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
            tagSearchIndexMap.computeIfAbsent(unicode, k -> new ArrayList<>()).add(sii);
        }
        tagSearchIndexKeys = tagSearchIndexMap.keySet();
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
