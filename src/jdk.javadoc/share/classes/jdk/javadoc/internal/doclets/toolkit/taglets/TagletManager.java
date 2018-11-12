/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.taglets;

import java.io.*;
import java.util.*;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.SimpleElementVisitor9;
import javax.tools.JavaFileManager;
import javax.tools.StandardJavaFileManager;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.DocletElement;
import jdk.javadoc.internal.doclets.toolkit.Messages;
import jdk.javadoc.internal.doclets.toolkit.Resources;

import jdk.javadoc.internal.doclets.toolkit.taglets.BaseTaglet.Site;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

import static javax.tools.DocumentationTool.Location.*;

import static com.sun.source.doctree.DocTree.Kind.*;

/**
 * Manages the {@code Taglet}s used by doclets.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 */

public class TagletManager {

    /**
     * The default separator for the simple tag option.
     */
    public static final char SIMPLE_TAGLET_OPT_SEPARATOR = ':';

    /**
     * The map of all taglets.
     */
    private final LinkedHashMap<String,Taglet> allTaglets;

    /**
     * Block (non-line) taglets, grouped by Site
     */
    private Map<Site, List<Taglet>> blockTagletsBySite;

    /**
     * The taglets that can appear inline in descriptive text.
     */
    private List<Taglet> inlineTags;

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
     * Keep track of standard tags in lowercase to compare for better
     * error messages when a tag like @docRoot is mistakenly spelled
     * lowercase @docroot.
     */
    private final Set<String> standardTagsLowercase;

    /**
     * Keep track of overriden standard tags.
     */
    private final Set<String> overridenStandardTags;

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
     * True if we do not want to use @since tags.
     */
    private final boolean nosince;

    /**
     * True if we want to use @version tags.
     */
    private final boolean showversion;

    /**
     * True if we want to use @author tags.
     */
    private final boolean showauthor;

    /**
     * True if we want to use JavaFX-related tags (@defaultValue, @treatAsPrivate).
     */
    private final boolean javafx;

    /**
     * Show the taglets table when it has been initialized.
     */
    private final boolean showTaglets;

    /**
     * Construct a new {@code TagletManager}.
     * @param nosince true if we do not want to use @since tags.
     * @param showversion true if we want to use @version tags.
     * @param showauthor true if we want to use @author tags.
     * @param javafx indicates whether javafx is active.
     * @param configuration the configuration for this taglet manager
     */
    public TagletManager(boolean nosince, boolean showversion,
                         boolean showauthor, boolean javafx,
                         BaseConfiguration configuration) {
        overridenStandardTags = new HashSet<>();
        potentiallyConflictingTags = new HashSet<>();
        standardTags = new HashSet<>();
        standardTagsLowercase = new HashSet<>();
        unseenCustomTags = new HashSet<>();
        allTaglets = new LinkedHashMap<>();
        this.nosince = nosince;
        this.showversion = showversion;
        this.showauthor = showauthor;
        this.javafx = javafx;
        this.docEnv = configuration.docEnv;
        this.doclet = configuration.doclet;
        this.messages = configuration.getMessages();
        this.resources = configuration.getResources();
        this.showTaglets = configuration.showTaglets;
        this.utils = configuration.utils;
        initStandardTaglets();
    }

    /**
     * Add a new {@code Taglet}.  This is used to add a Taglet from within
     * a Doclet.  No message is printed to indicate that the Taglet is properly
     * registered because these Taglets are typically added for every execution of the
     * Doclet.  We don't want to see this type of error message every time.
     * @param customTag the new {@code Taglet} to add.
     */
    public void addCustomTag(Taglet customTag) {
        if (customTag != null) {
            String name = customTag.getName();
            allTaglets.remove(name);
            allTaglets.put(name, customTag);
            checkTagName(name);
        }
    }

    public Set<String> getAllTagletNames() {
        return allTaglets.keySet();
    }

    /**
     * Add a new {@code Taglet}.  Print a message to indicate whether or not
     * the Taglet was registered properly.
     * @param classname  the name of the class representing the custom tag.
     * @param fileManager the filemanager to load classes and resources.
     * @param tagletPath  the path to the class representing the custom tag.
     */
    public void addCustomTag(String classname, JavaFileManager fileManager, String tagletPath) {
        try {
            ClassLoader tagClassLoader;
            if (!fileManager.hasLocation(TAGLET_PATH)) {
                List<File> paths = new ArrayList<>();
                if (tagletPath != null) {
                    for (String pathname : tagletPath.split(File.pathSeparator)) {
                        paths.add(new File(pathname));
                    }
                }
                if (fileManager instanceof StandardJavaFileManager) {
                    ((StandardJavaFileManager) fileManager).setLocation(TAGLET_PATH, paths);
                }
            }
            tagClassLoader = fileManager.getClassLoader(TAGLET_PATH);
            Class<? extends jdk.javadoc.doclet.Taglet> customTagClass =
                    tagClassLoader.loadClass(classname).asSubclass(jdk.javadoc.doclet.Taglet.class);
            jdk.javadoc.doclet.Taglet instance = customTagClass.getConstructor().newInstance();
            instance.init(docEnv, doclet);
            Taglet newLegacy = new UserTaglet(instance);
            String tname = newLegacy.getName();
            Taglet t = allTaglets.get(tname);
            if (t != null) {
                allTaglets.remove(tname);
            }
            allTaglets.put(tname, newLegacy);
            messages.notice("doclet.Notice_taglet_registered", classname);
        } catch (Exception exc) {
            messages.error("doclet.Error_taglet_not_registered", exc.getClass().getName(), classname);
        }
    }

    /**
     * Add a new {@code SimpleTaglet}.  If this tag already exists
     * and the header passed as an argument is null, move tag to the back of the
     * list. If this tag already exists and the header passed as an argument is
     * not null, overwrite previous tag with new one.  Otherwise, add new
     * SimpleTaglet to list.
     * @param tagName the name of this tag
     * @param header the header to output.
     * @param locations the possible locations that this tag
     * can appear in.
     */
    public void addNewSimpleCustomTag(String tagName, String header, String locations) {
        if (tagName == null || locations == null) {
            return;
        }
        Taglet tag = allTaglets.get(tagName);
        if (tag == null || header != null) {
            allTaglets.remove(tagName);
            allTaglets.put(tagName, new SimpleTaglet(tagName, header, locations));
            if (Utils.toLowerCase(locations).indexOf('x') == -1) {
                checkTagName(tagName);
            }
        } else {
            //Move to back
            allTaglets.remove(tagName);
            allTaglets.put(tagName, tag);
        }
    }

    /**
     * Given a tag name, add it to the set of tags it belongs to.
     */
    private void checkTagName(String name) {
        if (standardTags.contains(name)) {
            overridenStandardTags.add(name);
        } else {
            if (name.indexOf('.') == -1) {
                potentiallyConflictingTags.add(name);
            }
            unseenCustomTags.add(name);
        }
    }

    /**
     * Given a name of a seen custom tag, remove it from the set of unseen
     * custom tags.
     * @param name the name of the seen custom tag.
     */
    void seenCustomTag(String name) {
        unseenCustomTags.remove(name);
    }

    /**
     * Given a series of {@code DocTree}s, check for spelling mistakes.
     * @param element the tags holder
     * @param trees the trees containing the comments
     * @param areInlineTags true if the array of tags are inline and false otherwise.
     */
    public void checkTags(Element element, Iterable<? extends DocTree> trees, boolean areInlineTags) {
        if (trees == null) {
            return;
        }
        CommentHelper ch = utils.getCommentHelper(element);
        for (DocTree tag : trees) {
            String name = tag.getKind().tagName;
            if (name == null) {
                continue;
            }
            if (name.length() > 0 && name.charAt(0) == '@') {
                name = name.substring(1, name.length());
            }
            if (! (standardTags.contains(name) || allTaglets.containsKey(name))) {
                if (standardTagsLowercase.contains(Utils.toLowerCase(name))) {
                    messages.warning(ch.getDocTreePath(tag), "doclet.UnknownTagLowercase", ch.getTagName(tag));
                    continue;
                } else {
                    messages.warning(ch.getDocTreePath(tag), "doclet.UnknownTag", ch.getTagName(tag));
                    continue;
                }
            }
            final Taglet taglet = allTaglets.get(name);
            // Check and verify tag usage
            if (taglet != null) {
                if (areInlineTags && !taglet.isInlineTag()) {
                    printTagMisuseWarn(ch, taglet, tag, "inline");
                }
                // nothing more to do
                if (element == null) {
                    return;
                }
                new SimpleElementVisitor9<Void, Void>() {
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
    }

    /**
     * Given the taglet, the tag and the type of documentation that the tag
     * was found in, print a tag misuse warning.
     * @param taglet the taglet representing the misused tag.
     * @param tag the misused tag.
     * @param holderType the type of documentation that the misused tag was found in.
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
        if (taglet.isInlineTag()) {
            locationsSet.add("inline text");
        }
        if (locationsSet.isEmpty()) {
            //This known tag is excluded.
            return;
        }
        StringBuilder combined_locations = new StringBuilder();
        for (String location: locationsSet) {
            if (combined_locations.length() > 0) {
                combined_locations.append(", ");
            }
            combined_locations.append(location);
        }
        messages.warning(ch.getDocTreePath(tag), "doclet.tag_misuse",
            "@" + taglet.getName(), holderType, combined_locations.toString());
    }

    /**
     * Returns the taglets that can appear inline, in descriptive text.
     * @return the taglets that can appear inline
     */
    List<Taglet> getInlineTaglets() {
        if (inlineTags == null) {
            initBlockTaglets();
        }
        return inlineTags;
    }

    /**
     * Returns the taglets that can appear in the serialized form.
     * @return the taglet that can appear in the serialized form
     */
    public List<Taglet> getSerializedFormTaglets() {
        if (serializedFormTags == null) {
            initBlockTaglets();
        }
        return serializedFormTags;
    }

    /**
     * Returns the custom tags for a given element.
     *
     * @param e the element to get custom tags for
     * @return the array of {@code Taglet}s that can
     * appear in the given element.
     */
    @SuppressWarnings("fallthrough")
    public List<Taglet> getBlockTaglets(Element e) {
        if (blockTagletsBySite == null) {
            initBlockTaglets();
        }

        switch (e.getKind()) {
            case CONSTRUCTOR:
                return blockTagletsBySite.get(Site.CONSTRUCTOR);
            case METHOD:
                return blockTagletsBySite.get(Site.METHOD);
            case ENUM_CONSTANT:
            case FIELD:
                return blockTagletsBySite.get(Site.FIELD);
            case ANNOTATION_TYPE:
            case INTERFACE:
            case CLASS:
            case ENUM:
                return blockTagletsBySite.get(Site.TYPE);
            case MODULE:
                return blockTagletsBySite.get(Site.MODULE);
            case PACKAGE:
                return blockTagletsBySite.get(Site.PACKAGE);
            case OTHER:
                if (e instanceof DocletElement) {
                    DocletElement de = (DocletElement)e;
                    switch (de.getSubKind()) {
                        case DOCFILE:
                            return blockTagletsBySite.get(Site.PACKAGE);
                        case OVERVIEW:
                            return blockTagletsBySite.get(Site.OVERVIEW);
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
     * Initialize the custom tag Lists.
     */
    private void initBlockTaglets() {

        blockTagletsBySite = new EnumMap<>(Site.class);
        for (Site site : Site.values()) {
            blockTagletsBySite.put(site, new ArrayList<>());
        }

        inlineTags = new ArrayList<>();

        for (Taglet current : allTaglets.values()) {
            if (current.isInlineTag()) {
                inlineTags.add(current);
            } else {
                if (current.inOverview()) {
                    blockTagletsBySite.get(Site.OVERVIEW).add(current);
                }
                if (current.inModule()) {
                    blockTagletsBySite.get(Site.MODULE).add(current);
                }
                if (current.inPackage()) {
                    blockTagletsBySite.get(Site.PACKAGE).add(current);
                }
                if (current.inType()) {
                    blockTagletsBySite.get(Site.TYPE).add(current);
                }
                if (current.inConstructor()) {
                    blockTagletsBySite.get(Site.CONSTRUCTOR).add(current);
                }
                if (current.inMethod()) {
                    blockTagletsBySite.get(Site.METHOD).add(current);
                }
                if (current.inField()) {
                    blockTagletsBySite.get(Site.FIELD).add(current);
                }
            }
        }

        //Init the serialized form tags
        serializedFormTags = new ArrayList<>();
        serializedFormTags.add(allTaglets.get(SERIAL_DATA.tagName));
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

        addStandardTaglet(new ParamTaglet());
        addStandardTaglet(new ReturnTaglet());
        addStandardTaglet(new ThrowsTaglet());
        addStandardTaglet(
                new SimpleTaglet(EXCEPTION.tagName, null,
                    EnumSet.of(Site.METHOD, Site.CONSTRUCTOR)));
        addStandardTaglet(
                new SimpleTaglet(SINCE.tagName, resources.getText("doclet.Since"),
                    EnumSet.allOf(Site.class), !nosince));
        addStandardTaglet(
                new SimpleTaglet(VERSION.tagName, resources.getText("doclet.Version"),
                    EnumSet.of(Site.OVERVIEW, Site.MODULE, Site.PACKAGE, Site.TYPE), showversion));
        addStandardTaglet(
                new SimpleTaglet(AUTHOR.tagName, resources.getText("doclet.Author"),
                    EnumSet.of(Site.OVERVIEW, Site.MODULE, Site.PACKAGE, Site.TYPE), showauthor));
        addStandardTaglet(
                new SimpleTaglet(SERIAL_DATA.tagName, resources.getText("doclet.SerialData"),
                    EnumSet.noneOf(Site.class)));
        addStandardTaglet(
                new SimpleTaglet(HIDDEN.tagName, null,
                    EnumSet.of(Site.TYPE, Site.METHOD, Site.FIELD)));

        // This appears to be a default custom (non-standard) taglet
        Taglet factoryTaglet = new SimpleTaglet("factory", resources.getText("doclet.Factory"),
                EnumSet.of(Site.METHOD));
        allTaglets.put(factoryTaglet.getName(), factoryTaglet);

        addStandardTaglet(new SeeTaglet());

        // Standard inline tags
        addStandardTaglet(new DocRootTaglet());
        addStandardTaglet(new InheritDocTaglet());
        addStandardTaglet(new ValueTaglet());
        addStandardTaglet(new LiteralTaglet());
        addStandardTaglet(new CodeTaglet());
        addStandardTaglet(new IndexTaglet());
        addStandardTaglet(new SummaryTaglet());
        addStandardTaglet(new SystemPropertyTaglet());

        // Keep track of the names of standard tags for error checking purposes.
        // The following are not handled above.
        addStandardTaglet(new DeprecatedTaglet());
        addStandardTaglet(new BaseTaglet(LINK.tagName, true, EnumSet.allOf(Site.class)));
        addStandardTaglet(new BaseTaglet(LINK_PLAIN.tagName, true, EnumSet.allOf(Site.class)));
        addStandardTaglet(new BaseTaglet(USES.tagName, false, EnumSet.of(Site.MODULE)));
        addStandardTaglet(new BaseTaglet(PROVIDES.tagName, false, EnumSet.of(Site.MODULE)));
        addStandardTaglet(
                new SimpleTaglet(SERIAL.tagName, null,
                    EnumSet.of(Site.PACKAGE, Site.TYPE, Site.FIELD)));
        addStandardTaglet(
                new SimpleTaglet(SERIAL_FIELD.tagName, null, EnumSet.of(Site.FIELD)));
    }

    /**
     * Initialize JavaFX-related tags.
     */
    private void initJavaFXTaglets() {
        addStandardTaglet(new PropertyGetterTaglet());
        addStandardTaglet(new PropertySetterTaglet());
        addStandardTaglet(new SimpleTaglet("propertyDescription",
                resources.getText("doclet.PropertyDescription"),
                EnumSet.of(Site.METHOD, Site.FIELD)));
        addStandardTaglet(new SimpleTaglet("defaultValue", resources.getText("doclet.DefaultValue"),
                EnumSet.of(Site.METHOD, Site.FIELD)));
        addStandardTaglet(new SimpleTaglet("treatAsPrivate", null,
                EnumSet.of(Site.TYPE, Site.METHOD, Site.FIELD)));
    }

    private void addStandardTaglet(Taglet taglet) {
        String name = taglet.getName();
        allTaglets.put(name, taglet);
        standardTags.add(name);
        standardTagsLowercase.add(Utils.toLowerCase(name));
    }

    public boolean isKnownCustomTag(String tagName) {
        return allTaglets.containsKey(tagName);
    }

    /**
     * Print a list of {@link Taglet}s that might conflict with
     * standard tags in the future and a list of standard tags
     * that have been overriden.
     */
    public void printReport() {
        printReportHelper("doclet.Notice_taglet_conflict_warn", potentiallyConflictingTags);
        printReportHelper("doclet.Notice_taglet_overriden", overridenStandardTags);
        printReportHelper("doclet.Notice_taglet_unseen", unseenCustomTags);
    }

    private void printReportHelper(String noticeKey, Set<String> names) {
        if (names.size() > 0) {
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
     * Return null if the tag is unknown.
     *
     * @param name the name of the taglet to retrieve.
     * @return return the corresponding taglet. Return null if the tag is
     *         unknown.
     */
    Taglet getTaglet(String name) {
        if (name.indexOf("@") == 0) {
            return allTaglets.get(name.substring(1));
        } else {
            return allTaglets.get(name);
        }

    }

    /*
     * The output of this method is the basis for a table at the end of the
     * doc comment specification, so any changes in the output may indicate
     * a need for a corresponding update to the spec.
     */
    private void showTaglets(PrintStream out) {
        Set<Taglet> taglets = new TreeSet<>((o1, o2) -> o1.getName().compareTo(o2.getName()));
        taglets.addAll(allTaglets.values());

        for (Taglet t : taglets) {
            String name = t.isInlineTag() ? "{@" + t.getName() + "}" : "@" + t.getName();
            out.println(String.format("%20s", name) + ": "
                    + format(t.inOverview(), "overview") + " "
                    + format(t.inModule(), "module") + " "
                    + format(t.inPackage(), "package") + " "
                    + format(t.inType(), "type") + " "
                    + format(t.inConstructor(),"constructor") + " "
                    + format(t.inMethod(), "method") + " "
                    + format(t.inField(), "field") + " "
                    + format(t.isInlineTag(), "inline")+ " "
                    + format((t instanceof SimpleTaglet) && !((SimpleTaglet)t).enabled, "disabled"));
        }
    }

    private String format(boolean b, String s) {
        return b ? s : s.replaceAll(".", "."); // replace all with "."
    }
}
