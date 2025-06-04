/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.formats.html.taglets;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.SimpleElementVisitor14;
import javax.tools.JavaFileManager;
import javax.tools.StandardJavaFileManager;

import com.sun.source.doctree.BlockTagTree;
import com.sun.source.doctree.DocTree;

import com.sun.source.doctree.InlineTagTree;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Taglet.Location;
import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.doclets.formats.html.HtmlOptions;
import jdk.javadoc.internal.doclets.toolkit.DocletElement;
import jdk.javadoc.internal.doclets.toolkit.Messages;
import jdk.javadoc.internal.doclets.toolkit.Resources;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;
import jdk.javadoc.internal.doclint.DocLint;

import static com.sun.source.doctree.DocTree.Kind.AUTHOR;
import static com.sun.source.doctree.DocTree.Kind.EXCEPTION;
import static com.sun.source.doctree.DocTree.Kind.HIDDEN;
import static com.sun.source.doctree.DocTree.Kind.PARAM;
import static com.sun.source.doctree.DocTree.Kind.PROVIDES;
import static com.sun.source.doctree.DocTree.Kind.SEE;
import static com.sun.source.doctree.DocTree.Kind.SERIAL;
import static com.sun.source.doctree.DocTree.Kind.SERIAL_DATA;
import static com.sun.source.doctree.DocTree.Kind.SERIAL_FIELD;
import static com.sun.source.doctree.DocTree.Kind.SINCE;
import static com.sun.source.doctree.DocTree.Kind.THROWS;
import static com.sun.source.doctree.DocTree.Kind.USES;
import static com.sun.source.doctree.DocTree.Kind.VERSION;

import static javax.tools.DocumentationTool.Location.TAGLET_PATH;

/**
 * Manages the {@code Taglet}s used by the standard doclet.
 */
public class TagletManager {

    /**
     * All taglets, keyed either by their {@link Taglet#getName() name},
     * or by an alias.
     *
     * In general, taglets do <i>not</i> provide aliases;
     * the one instance that does is {@code ThrowsTaglet}, which handles
     * both {@code @throws} tags and {@code @exception} tags.
     */
    private final LinkedHashMap<String, Taglet> allTaglets;

    /**
     * Block (non-inline) taglets, grouped by {@link Location location}.
     */
    private Map<Location, List<Taglet>> blockTagletsByLocation;

    /**
     * The taglets that can appear inline in descriptive text.
     */
    private Map<String, Taglet> inlineTags;

    /**
     * The taglets that can appear in the serialized form.
     */
    private List<Taglet> serializedFormTags;

    private final DocletEnvironment docEnv;
    private final Doclet doclet;

    private final Utils utils;
    private final Messages messages;
    private final Resources resources;

    /**
     * Keep track of standard tags.
     */
    private final Set<String> standardTags;

    /**
     * Keep track of overridden standard tags.
     */
    private final Set<String> overriddenStandardTags;

    /**
     * Keep track of the tags that may conflict
     * with standard tags in the future (any custom tag without
     * a period in its name).
     */
    private final Set<String> potentiallyConflictingTags;

    /**
     * The set of unseen custom tags.
     */
    private final Set<String> unseenCustomTags;

    /**
     * True if we do not want to use {@code @since} tags.
     */
    private final boolean nosince;

    /**
     * True if we want to use {@code @version} tags.
     */
    private final boolean showversion;

    /**
     * True if we want to use {@code @author} tags.
     */
    private final boolean showauthor;

    /**
     * True if we want to use JavaFX-related tags ({@code @defaultValue}, {@code @treatAsPrivate}).
     */
    private final boolean javafx;

    /**
     * Show the taglets table when it has been initialized.
     */
    private final boolean showTaglets;

    private final String tagletPath;

    private final HtmlConfiguration config;

    /**
     * Constructs a new {@code TagletManager}.
     *
     * @param config the configuration for this taglet manager
     */
    public TagletManager(HtmlConfiguration config) {
        overriddenStandardTags = new HashSet<>();
        potentiallyConflictingTags = new HashSet<>();
        standardTags = new HashSet<>();
        unseenCustomTags = new HashSet<>();
        allTaglets = new LinkedHashMap<>();
        this.config = config;
        HtmlOptions options = config.getOptions();
        this.nosince = options.noSince();
        this.showversion = options.showVersion();
        this.showauthor = options.showAuthor();
        this.javafx = options.javafx();
        this.docEnv = config.docEnv;
        this.doclet = config.doclet;
        this.messages = config.getMessages();
        this.resources = config.getDocResources();
        this.showTaglets = options.showTaglets();
        this.utils = config.utils;
        this.tagletPath = options.tagletPath();
        initStandardTaglets();
    }

    public Set<String> getAllTagletNames() {
        return allTaglets.keySet();
    }

    /**
     * Initializes the location TAGLET_PATH which is used to locate the custom taglets.
     *
     * @param fileManager the file manager to load classes and resources
     * @throws IOException if an error occurs while setting the location
     */
    public void initTagletPath(JavaFileManager fileManager) throws IOException {
        if (fileManager instanceof StandardJavaFileManager sfm) {
            if (tagletPath != null) {
                List<File> paths = new ArrayList<>();
                for (String pathname : tagletPath.split(File.pathSeparator)) {
                    paths.add(new File(pathname));
                }
                sfm.setLocation(TAGLET_PATH, paths);
            } else if (!sfm.hasLocation(TAGLET_PATH)) {
                sfm.setLocation(TAGLET_PATH, List.of());
            }
        } else if (tagletPath != null) {
            messages.error("doclet.not_standard_file_manager");
        }
    }

    /**
     * Adds a new {@code Taglet}.
     *
     * Prints a message to indicate whether or not the Taglet was registered properly.
     *
     * @param classname  the name of the class representing the custom tag
     * @param fileManager the file manager to load classes and resources
     */
    public void addCustomTag(String classname, JavaFileManager fileManager) {
        ClassLoader tagClassLoader = fileManager.getClassLoader(TAGLET_PATH);
        if (config.workArounds.accessInternalAPI()) {
            Module thisModule = getClass().getModule();
            Module tagletLoaderUnnamedModule = tagClassLoader.getUnnamedModule();
            List<String> pkgs = List.of(
                    "jdk.javadoc.doclet",
                    "jdk.javadoc.internal.doclets.toolkit",
                    "jdk.javadoc.internal.doclets.formats.html");
            pkgs.forEach(p -> thisModule.addOpens(p, tagletLoaderUnnamedModule));
        }
        try {
            Class<? extends jdk.javadoc.doclet.Taglet> customTagClass =
                    tagClassLoader.loadClass(classname).asSubclass(jdk.javadoc.doclet.Taglet.class);
            jdk.javadoc.doclet.Taglet instance = customTagClass.getConstructor().newInstance();
            registerTaglet(instance);
        } catch (ReflectiveOperationException | ExceptionInInitializerError | ClassCastException exc) {
            messages.error("doclet.Error_taglet_not_registered", exc.getClass().getName(),
                    classname);
        }
    }

    /**
     * Loads taglets from a taglet path using the service loader.
     *
     * @param fileManager the file manager to load the taglets
     * @throws IOException if an error occurs while getting the service loader
     */
    public void loadTaglets(JavaFileManager fileManager) throws IOException {
        Iterable<? extends File> location = ((StandardJavaFileManager) fileManager).getLocation(TAGLET_PATH);
        if (location != null && location.iterator().hasNext()) {
            ServiceLoader<jdk.javadoc.doclet.Taglet> serviceLoader =
                    fileManager.getServiceLoader(TAGLET_PATH, jdk.javadoc.doclet.Taglet.class);
            for (jdk.javadoc.doclet.Taglet taglet : serviceLoader) {
                registerTaglet(taglet);
            }
        }
    }

    /**
     * Registers the {@code Taglet}.
     *
     * Prints a message if the {@code Taglet} got registered properly.
     *
     * @param instance the {@code Taglet} instance
     */
    private void registerTaglet(jdk.javadoc.doclet.Taglet instance) {
        instance.init(docEnv, doclet);
        Taglet newLegacy = new UserTaglet(instance);
        allTaglets.put(newLegacy.getName(), newLegacy);
        messages.notice("doclet.Notice_taglet_registered", instance.getClass().getName());
    }

    /**
     * Adds a new {@code SimpleTaglet}.
     *
     * If this tag already exists and the header passed as an argument is {@code null},
     * move tag to the back of the list. If this tag already exists and the
     * header passed as an argument is not {@code null}, overwrite previous tag
     * with the new one. Otherwise, add a new SimpleTaglet to the list.
     *
     * @param tagName the name of this tag
     * @param header the header to output
     * @param locations the possible locations that this tag can appear in
     */
    public void addNewSimpleCustomTag(String tagName, String header, String locations) {
        if (tagName == null || locations == null) {
            return;
        }
        // remove + put in both branches below move the tag to the back of the map's ordering
        Taglet tag = allTaglets.remove(tagName);
        if (tag == null || header != null) {
            allTaglets.put(tagName, new SimpleTaglet(config, tagName, header, locations));
            if (Utils.toLowerCase(locations).indexOf('x') == -1) {
                checkTagName(tagName);
            }
        } else {
            // Move existing tag to the back
            allTaglets.put(tagName, tag);
        }
    }

    /**
     * Given a tag name, add it to the set of tags it belongs to.
     */
    private void checkTagName(String name) {
        if (standardTags.contains(name)) {
            overriddenStandardTags.add(name);
        } else {
            if (name.indexOf('.') == -1) {
                potentiallyConflictingTags.add(name);
            }
            unseenCustomTags.add(name);
        }
    }

    /**
     * Reports that a tag was seen in a doc comment.
     * It is removed from the list of custom tags that have not yet been seen.
     *
     * @param name the name of the tag
     */
    void seenTag(String name) {
        unseenCustomTags.remove(name);
    }

    /**
     * Given a series of {@code DocTree}s, check for misuse and spelling mistakes.
     *
     * @param element the tags holder
     * @param trees the trees containing the comments
     */
    public void checkTags(Element element, Iterable<? extends DocTree> trees) {
        for (DocTree tag : trees) {
            String name = switch (tag.getKind()) {
                case UNKNOWN_INLINE_TAG -> ((InlineTagTree) tag).getTagName();
                case UNKNOWN_BLOCK_TAG -> ((BlockTagTree) tag).getTagName();
                default -> tag.getKind().tagName;
            };
            if (name == null) {
                continue; // not a tag
            }
            if (!allTaglets.containsKey(name)) {
                if (!config.isDocLintSyntaxGroupEnabled()) {
                    var ch = utils.getCommentHelper(element);
                    List<String> suggestions = DocLint.suggestSimilar(allTaglets.keySet(), name);
                    if (!suggestions.isEmpty()) {
                        messages.warning(ch.getDocTreePath(tag), "doclet.UnknownTagWithHint",
                                String.join(", ", suggestions)); // TODO: revisit after 8041488
                    } else {
                        messages.warning(ch.getDocTreePath(tag), "doclet.UnknownTag");
                    }
                }
                continue; // unknown tag
            }
            final Taglet taglet = allTaglets.get(name);
            if (taglet instanceof SimpleTaglet st && !st.isEnabled()) {
                continue; // taglet has been disabled
            }

            // Check and verify tag usage
            new SimpleElementVisitor14<Void, Void>() {
                @Override
                public Void visitModule(ModuleElement e, Void p) {
                    if (!taglet.inModule()) {
                        printTagMisuseWarn(utils.getCommentHelper(e), taglet, tag, "module");
                    }
                    return null;
                }

                @Override
                public Void visitPackage(PackageElement e, Void p) {
                    if (!taglet.inPackage()) {
                        printTagMisuseWarn(utils.getCommentHelper(e), taglet, tag, "package");
                    }
                    return null;
                }

                @Override
                public Void visitType(TypeElement e, Void p) {
                    if (!taglet.inType()) {
                        printTagMisuseWarn(utils.getCommentHelper(e), taglet, tag, "class");
                    }
                    return null;
                }

                @Override
                public Void visitExecutable(ExecutableElement e, Void p) {
                    if (utils.isConstructor(e) && !taglet.inConstructor()) {
                        printTagMisuseWarn(utils.getCommentHelper(e), taglet, tag, "constructor");
                    } else if (!taglet.inMethod()) {
                        printTagMisuseWarn(utils.getCommentHelper(e), taglet, tag, "method");
                    }
                    return null;
                }

                @Override
                public Void visitVariable(VariableElement e, Void p) {
                    if (utils.isField(e) && !taglet.inField()) {
                        printTagMisuseWarn(utils.getCommentHelper(e), taglet, tag, "field");
                    }
                    return null;
                }

                @Override
                public Void visitUnknown(Element e, Void p) {
                    if (utils.isOverviewElement(e) && !taglet.inOverview()) {
                        printTagMisuseWarn(utils.getCommentHelper(e), taglet, tag, "overview");
                    }
                    return null;
                }

                @Override
                protected Void defaultAction(Element e, Void p) {
                    return null;
                }
            }.visit(element);
        }
    }

    /**
     * Given the taglet, the tag and the type of documentation that the tag
     * was found in, print a tag misuse warning.
     *
     * @param taglet the taglet representing the misused tag
     * @param tag the misused tag
     * @param holderType the type of documentation that the misused tag was found in
     */
    private void printTagMisuseWarn(CommentHelper ch, Taglet taglet, DocTree tag, String holderType) {
        Set<String> locationsSet = new LinkedHashSet<>();
        // The following names should be localized
        if (taglet.inOverview()) {
            locationsSet.add("overview");
        }
        if (taglet.inModule()) {
            locationsSet.add("module");
        }
        if (taglet.inPackage()) {
            locationsSet.add("package");
        }
        if (taglet.inType()) {
            locationsSet.add("class/interface");
        }
        if (taglet.inConstructor())  {
            locationsSet.add("constructor");
        }
        if (taglet.inField()) {
            locationsSet.add("field");
        }
        if (taglet.inMethod()) {
            locationsSet.add("method");
        }
        if (locationsSet.isEmpty()) {
            //This known tag is excluded.
            return;
        }
        var combined_locations = String.join(", ", locationsSet);
        messages.warning(ch.getDocTreePath(tag), "doclet.tag_misuse",
            "@" + taglet.getName(), holderType, combined_locations);
    }

    /**
     * Returns the taglets that can appear inline, in descriptive text.
     * @return the taglets that can appear inline
     */
    Map<String, Taglet> getInlineTaglets() {
        if (inlineTags == null) {
            initTaglets();
        }
        return inlineTags;
    }

    /**
     * Returns the taglets that can appear in the serialized form.
     * @return the taglet that can appear in the serialized form
     */
    public List<Taglet> getSerializedFormTaglets() {
        if (serializedFormTags == null) {
            initTaglets();
        }
        return serializedFormTags;
    }

    /**
     * Returns the custom tags for a given element.
     *
     * @param e the element to get custom tags for
     * @return the array of {@code Taglet}s that can
     * appear in the given element
     */
    @SuppressWarnings("fallthrough")
    public List<Taglet> getBlockTaglets(Element e) {
        if (blockTagletsByLocation == null) {
            initTaglets();
        }

        switch (e.getKind()) {
            case CONSTRUCTOR:
                return blockTagletsByLocation.get(Location.CONSTRUCTOR);
            case METHOD:
                return blockTagletsByLocation.get(Location.METHOD);
            case ENUM_CONSTANT:
            case FIELD:
                return blockTagletsByLocation.get(Location.FIELD);
            case ANNOTATION_TYPE:
            case INTERFACE:
            case CLASS:
            case ENUM:
            case RECORD:
                return blockTagletsByLocation.get(Location.TYPE);
            case MODULE:
                return blockTagletsByLocation.get(Location.MODULE);
            case PACKAGE:
                return blockTagletsByLocation.get(Location.PACKAGE);
            case OTHER:
                if (e instanceof DocletElement de) {
                    switch (de.getSubKind()) {
                        case DOCFILE:
                            return blockTagletsByLocation.get(Location.PACKAGE);
                        case OVERVIEW:
                            return blockTagletsByLocation.get(Location.OVERVIEW);
                        default:
                            // fall through
                    }
                }
                // fall through
            default:
                throw new AssertionError("unknown element: " + e + " ,kind: " + e.getKind());
        }
    }

    /**
     * Initialize the tag collections.
     */
    private void initTaglets() {

        blockTagletsByLocation = new EnumMap<>(Location.class);
        for (Location site : Location.values()) {
            blockTagletsByLocation.put(site, new ArrayList<>());
        }

        inlineTags = new LinkedHashMap<>();

        allTaglets.forEach((name, t) -> {
            if (t.isInlineTag()) {
                inlineTags.put(t.getName(), t);
            }

            if (t.isBlockTag() && t.getName().equals(name)) {
                t.getAllowedLocations().forEach(l -> blockTagletsByLocation.get(l).add(t));
            }
        });

        // init the serialized form tags for the serialized form page
        serializedFormTags = new ArrayList<>();
        serializedFormTags.add(allTaglets.get(SERIAL_DATA.tagName));
        serializedFormTags.add(allTaglets.get(PARAM.tagName));
        serializedFormTags.add(allTaglets.get(THROWS.tagName));
        if (!nosince)
            serializedFormTags.add(allTaglets.get(SINCE.tagName));
        serializedFormTags.add(allTaglets.get(SEE.tagName));

        if (showTaglets) {
            showTaglets(System.out);
        }
    }

    /**
     * Initialize standard Javadoc tags for ordering purposes.
     */
    private void initStandardTaglets() {
        if (javafx) {
            initJavaFXTaglets();
        }

        addStandardTaglet(new ParamTaglet(config));
        addStandardTaglet(new ReturnTaglet(config));
        addStandardTaglet(new ThrowsTaglet(config), EXCEPTION);
        addStandardTaglet(
                SimpleTaglet.createWithDefaultForNested(config, SINCE, resources.getText("doclet.Since"),
                    EnumSet.allOf(Location.class), !nosince));
        addStandardTaglet(
                SimpleTaglet.createWithDefaultForNested(config, VERSION, resources.getText("doclet.Version"),
                    EnumSet.of(Location.OVERVIEW, Location.MODULE, Location.PACKAGE, Location.TYPE), showversion));
        addStandardTaglet(
                SimpleTaglet.createWithDefaultForNested(config, AUTHOR, resources.getText("doclet.Author"),
                    EnumSet.of(Location.OVERVIEW, Location.MODULE, Location.PACKAGE, Location.TYPE), showauthor));
        addStandardTaglet(
                new SimpleTaglet(config, SERIAL_DATA, resources.getText("doclet.SerialData"),
                    EnumSet.noneOf(Location.class)));
        addStandardTaglet(
                new SimpleTaglet(config, HIDDEN, null,
                    EnumSet.of(Location.TYPE, Location.METHOD, Location.FIELD)));

        // This appears to be a default custom (non-standard) taglet
        Taglet factoryTaglet = new SimpleTaglet(config, "factory", resources.getText("doclet.Factory"),
                EnumSet.of(Location.METHOD));
        allTaglets.put(factoryTaglet.getName(), factoryTaglet);

        addStandardTaglet(new SeeTaglet(config));
        addStandardTaglet(new SpecTaglet(config));

        // Standard inline tags
        addStandardTaglet(new DocRootTaglet(config));
        addStandardTaglet(new InheritDocTaglet(config));
        addStandardTaglet(new ValueTaglet(config));
        addStandardTaglet(new LinkTaglet(config, DocTree.Kind.LINK));
        addStandardTaglet(new LinkTaglet(config, DocTree.Kind.LINK_PLAIN));
        addStandardTaglet(new LiteralTaglet(config, DocTree.Kind.CODE));
        addStandardTaglet(new LiteralTaglet(config, DocTree.Kind.LITERAL));
        addStandardTaglet(new SnippetTaglet(config));
        addStandardTaglet(new IndexTaglet(config));
        addStandardTaglet(new SummaryTaglet(config));
        addStandardTaglet(new SystemPropertyTaglet(config));

        // Keep track of the names of standard tags for error checking purposes.
        // The following are not handled above.
        addStandardTaglet(new DeprecatedTaglet(config));
        addStandardTaglet(new BaseTaglet(config, USES, false, EnumSet.of(jdk.javadoc.doclet.Taglet.Location.MODULE)));
        addStandardTaglet(new BaseTaglet(config, PROVIDES, false, EnumSet.of(jdk.javadoc.doclet.Taglet.Location.MODULE)));
        addStandardTaglet(
                new SimpleTaglet(config, SERIAL, null,
                        EnumSet.of(jdk.javadoc.doclet.Taglet.Location.PACKAGE, jdk.javadoc.doclet.Taglet.Location.TYPE, jdk.javadoc.doclet.Taglet.Location.FIELD)));
        addStandardTaglet(
                new SimpleTaglet(config, SERIAL_FIELD, null, EnumSet.of(jdk.javadoc.doclet.Taglet.Location.FIELD)));
    }

    /**
     * Initialize JavaFX-related tags.
     */
    private void initJavaFXTaglets() {
        addStandardTaglet(new SimpleTaglet(config, "propertyDescription",
                resources.getText("doclet.PropertyDescription"),
                EnumSet.of(jdk.javadoc.doclet.Taglet.Location.METHOD, jdk.javadoc.doclet.Taglet.Location.FIELD)));
        addStandardTaglet(new SimpleTaglet(config, "defaultValue", resources.getText("doclet.DefaultValue"),
                EnumSet.of(jdk.javadoc.doclet.Taglet.Location.METHOD, jdk.javadoc.doclet.Taglet.Location.FIELD)));
        addStandardTaglet(new SimpleTaglet(config, "treatAsPrivate", null,
                EnumSet.of(jdk.javadoc.doclet.Taglet.Location.TYPE, jdk.javadoc.doclet.Taglet.Location.METHOD, jdk.javadoc.doclet.Taglet.Location.FIELD)));
    }

    private void addStandardTaglet(Taglet taglet) {
        String name = taglet.getName();
        allTaglets.put(name, taglet);
        standardTags.add(name);
    }

    private void addStandardTaglet(Taglet taglet, DocTree.Kind alias) {
        addStandardTaglet(taglet);
        String name = alias.tagName;
        allTaglets.put(name, taglet);
        standardTags.add(name);
    }

    public boolean isKnownCustomTag(String tagName) {
        return allTaglets.containsKey(tagName);
    }

    /**
     * Print a list of {@link Taglet}s that might conflict with
     * standard tags in the future and a list of standard tags
     * that have been overridden.
     */
    public void printReport() {
        printReportHelper("doclet.Notice_taglet_conflict_warn", potentiallyConflictingTags);
        printReportHelper("doclet.Notice_taglet_overridden", overriddenStandardTags);
        printReportHelper("doclet.Notice_taglet_unseen", unseenCustomTags);
    }

    private void printReportHelper(String noticeKey, Set<String> names) {
        if (!names.isEmpty()) {
            StringBuilder result = new StringBuilder();
            for (String name : names) {
                result.append(result.length() == 0 ? " " : ", ");
                result.append("@").append(name);
            }
            messages.notice(noticeKey, result);
        }
    }

    /**
     * Given the name of a tag, return the corresponding taglet.
     *
     * @param name the name of the taglet to retrieve
     * @return the corresponding taglet or {@code null} if the tag is unknown
     */
    Taglet getTaglet(String name) {
        if (name.indexOf("@") == 0) {
            return allTaglets.get(name.substring(1));
        } else {
            return allTaglets.get(name);
        }
    }

    public Taglet getTaglet(DocTree.Kind kind) {
        return switch (kind) {
            case DEPRECATED, LINK, LINK_PLAIN, PARAM, RETURN, THROWS -> getTaglet(kind.tagName);
            default ->
                throw new IllegalArgumentException(kind.toString());
        };
    }

    /*
     * The output of this method is the basis for a table at the end of the
     * doc comment specification, so any changes in the output may indicate
     * a need for a corresponding update to the spec.
     */
    private void showTaglets(PrintStream out) {
        Map<String, Taglet> taglets = new TreeMap<>(allTaglets);

        taglets.forEach((n, t) -> {
            // give preference to simpler block form if a tag can be either
            String name = t.isBlockTag() ? "@" + n : "{@" + n + "}";
            out.println(String.format("%20s", name) + ": "
                    + format(t.isBlockTag(), "block")+ " "
                    + format(t.inOverview(), "overview") + " "
                    + format(t.inModule(), "module") + " "
                    + format(t.inPackage(), "package") + " "
                    + format(t.inType(), "type") + " "
                    + format(t.inConstructor(),"constructor") + " "
                    + format(t.inMethod(), "method") + " "
                    + format(t.inField(), "field") + " "
                    + format(t.isInlineTag(), "inline")+ " "
                    + format((t instanceof SimpleTaglet st) && !st.isEnabled(), "disabled"));
        });
    }

    private String format(boolean b, String s) {
        return b ? s : ".".repeat(s.length()); // "replace" all with "."
    }
}
