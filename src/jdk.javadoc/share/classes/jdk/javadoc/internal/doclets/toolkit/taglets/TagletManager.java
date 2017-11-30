/*
 * Copyright (c) 2001, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.InvocationTargetException;
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
     * The alternate separator for simple tag options.  Use this
     * when you want the default separator to be in the name of the
     * custom tag.
     */
    public static final String ALT_SIMPLE_TAGLET_OPT_SEPARATOR = "-";

    /**
     * The map of custom tags.
     */
    private final LinkedHashMap<String,Taglet> customTags;

    /**
     * The array of custom tags that can appear in modules.
     */
    private List<Taglet> moduleTags;

    /**
     * The array of custom tags that can appear in packages.
     */
    private List<Taglet> packageTags;

    /**
     * The array of custom tags that can appear in classes or interfaces.
     */
    private List<Taglet> typeTags;

    /**
     * The array of custom tags that can appear in fields.
     */
    private List<Taglet> fieldTags;

    /**
     * The array of custom tags that can appear in constructors.
     */
    private List<Taglet> constructorTags;

    /**
     * The array of custom tags that can appear in methods.
     */
    private List<Taglet> methodTags;

    /**
     * The array of custom tags that can appear in the overview.
     */
    private List<Taglet> overviewTags;

    /**
     * The array of custom tags that can appear in comments.
     */
    private List<Taglet> inlineTags;

    /**
     * The array of custom tags that can appear in the serialized form.
     */
    private List<Taglet> serializedFormTags;

    private final DocletEnvironment docEnv;
    private final Doclet doclet;

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
     * True if we want to use JavaFX-related tags (@propertyGetter,
     * @propertySetter, @propertyDescription, @defaultValue, @treatAsPrivate).
     */
    private final boolean javafx;

    /**
     * Construct a new <code>TagletManager</code>.
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
        customTags = new LinkedHashMap<>();
        this.nosince = nosince;
        this.showversion = showversion;
        this.showauthor = showauthor;
        this.javafx = javafx;
        this.docEnv = configuration.docEnv;
        this.doclet = configuration.doclet;
        this.messages = configuration.getMessages();
        this.resources = configuration.getResources();
        initStandardTaglets();
        initStandardTagsLowercase();
    }

    /**
     * Add a new <code>CustomTag</code>.  This is used to add a Taglet from within
     * a Doclet.  No message is printed to indicate that the Taglet is properly
     * registered because these Taglets are typically added for every execution of the
     * Doclet.  We don't want to see this type of error message every time.
     * @param customTag the new <code>CustomTag</code> to add.
     */
    public void addCustomTag(Taglet customTag) {
        if (customTag != null) {
            String name = customTag.getName();
            if (customTags.containsKey(name)) {
                customTags.remove(name);
            }
            customTags.put(name, customTag);
            checkTagName(name);
        }
    }

    public Set<String> getCustomTagNames() {
        return customTags.keySet();
    }

    /**
     * Add a new <code>Taglet</code>.  Print a message to indicate whether or not
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
            Taglet t = customTags.get(tname);
            if (t != null) {
                customTags.remove(tname);
            }
            customTags.put(tname, newLegacy);
            messages.notice("doclet.Notice_taglet_registered", classname);
        } catch (Exception exc) {
            messages.error("doclet.Error_taglet_not_registered", exc.getClass().getName(), classname);
        }
    }

    /**
     * Add a new <code>SimpleTaglet</code>.  If this tag already exists
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
        Taglet tag = customTags.get(tagName);
        locations = Utils.toLowerCase(locations);
        if (tag == null || header != null) {
            customTags.remove(tagName);
            customTags.put(tagName, new SimpleTaglet(tagName, header, locations));
            if (locations != null && locations.indexOf('x') == -1) {
                checkTagName(tagName);
            }
        } else {
            //Move to back
            customTags.remove(tagName);
            customTags.put(tagName, tag);
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
     * Check the taglet to see if it is a legacy taglet.  Also
     * check its name for errors.
     */
    private void checkTaglet(Object taglet) {
        if (taglet instanceof Taglet) {
            checkTagName(((Taglet) taglet).getName());
        } else if (taglet instanceof jdk.javadoc.doclet.Taglet) {
            jdk.javadoc.doclet.Taglet legacyTaglet = (jdk.javadoc.doclet.Taglet) taglet;
            customTags.remove(legacyTaglet.getName());
            customTags.put(legacyTaglet.getName(), new UserTaglet(legacyTaglet));
            checkTagName(legacyTaglet.getName());
        } else {
            throw new IllegalArgumentException("Given object is not a taglet.");
        }
    }

    /**
     * Given a name of a seen custom tag, remove it from the set of unseen
     * custom tags.
     * @param name the name of the seen custom tag.
     */
    public void seenCustomTag(String name) {
        unseenCustomTags.remove(name);
    }

    /**
     * Given an array of <code>Tag</code>s, check for spelling mistakes.
     * @param utils the utility class to use
     * @param element the tags holder
     * @param trees the trees containing the comments
     * @param areInlineTags true if the array of tags are inline and false otherwise.
     */
    public void checkTags(final Utils utils, Element element,
                          Iterable<? extends DocTree> trees, boolean areInlineTags) {
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
            if (! (standardTags.contains(name) || customTags.containsKey(name))) {
                if (standardTagsLowercase.contains(Utils.toLowerCase(name))) {
                    messages.warning(ch.getDocTreePath(tag), "doclet.UnknownTagLowercase", ch.getTagName(tag));
                    continue;
                } else {
                    messages.warning(ch.getDocTreePath(tag), "doclet.UnknownTag", ch.getTagName(tag));
                    continue;
                }
            }
            final Taglet taglet = customTags.get(name);
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
        String[] locations = locationsSet.toArray(new String[]{});
        if (locations == null || locations.length == 0) {
            //This known tag is excluded.
            return;
        }
        StringBuilder combined_locations = new StringBuilder();
        for (int i = 0; i < locations.length; i++) {
            if (i > 0) {
                combined_locations.append(", ");
            }
            combined_locations.append(locations[i]);
        }
        messages.warning(ch.getDocTreePath(tag), "doclet.tag_misuse",
            "@" + taglet.getName(), holderType, combined_locations.toString());
    }

    /**
     * Return the array of <code>Taglet</code>s that can
     * appear in modules.
     * @return the array of <code>Taglet</code>s that can
     * appear in modules.
     */
    public List<Taglet> getModuleCustomTaglets() {
        if (moduleTags == null) {
            initCustomTaglets();
        }
        return moduleTags;
    }

    /**
     * Return the array of <code>Taglet</code>s that can
     * appear in packages.
     * @return the array of <code>Taglet</code>s that can
     * appear in packages.
     */
    public List<Taglet> getPackageCustomTaglets() {
        if (packageTags == null) {
            initCustomTaglets();
        }
        return packageTags;
    }

    /**
     * Return the array of <code>Taglet</code>s that can
     * appear in classes or interfaces.
     * @return the array of <code>Taglet</code>s that can
     * appear in classes or interfaces.
     */
    public List<Taglet> getTypeCustomTaglets() {
        if (typeTags == null) {
            initCustomTaglets();
        }
        return typeTags;
    }

    /**
     * Return the array of inline <code>Taglet</code>s that can
     * appear in comments.
     * @return the array of <code>Taglet</code>s that can
     * appear in comments.
     */
    public List<Taglet> getInlineCustomTaglets() {
        if (inlineTags == null) {
            initCustomTaglets();
        }
        return inlineTags;
    }

    /**
     * Return the array of <code>Taglet</code>s that can
     * appear in fields.
     * @return the array of <code>Taglet</code>s that can
     * appear in field.
     */
    public List<Taglet> getFieldCustomTaglets() {
        if (fieldTags == null) {
            initCustomTaglets();
        }
        return fieldTags;
    }

    /**
     * Return the array of <code>Taglet</code>s that can
     * appear in the serialized form.
     * @return the array of <code>Taglet</code>s that can
     * appear in the serialized form.
     */
    public List<Taglet> getSerializedFormTaglets() {
        if (serializedFormTags == null) {
            initCustomTaglets();
        }
        return serializedFormTags;
    }

    @SuppressWarnings("fallthrough")
    /**
     * Returns the custom tags for a given element.
     *
     * @param e the element to get custom tags for
     * @return the array of <code>Taglet</code>s that can
     * appear in the given element.
     */
    public List<Taglet> getCustomTaglets(Element e) {
        switch (e.getKind()) {
            case CONSTRUCTOR:
                return getConstructorCustomTaglets();
            case METHOD:
                return getMethodCustomTaglets();
            case ENUM_CONSTANT:
            case FIELD:
                return getFieldCustomTaglets();
            case ANNOTATION_TYPE:
            case INTERFACE:
            case CLASS:
            case ENUM:
                return getTypeCustomTaglets();
            case MODULE:
                return getModuleCustomTaglets();
            case PACKAGE:
                return getPackageCustomTaglets();
            case OTHER:
                if (e instanceof DocletElement) {
                    DocletElement de = (DocletElement)e;
                    switch (de.getSubKind()) {
                        case DOCFILE:
                            return getPackageCustomTaglets();
                        case OVERVIEW:
                            return getOverviewCustomTaglets();
                        default:
                            // fall through
                    }
                }
            default:
                throw new AssertionError("unknown element: " + e + " ,kind: " + e.getKind());
        }
    }

    /**
     * Return a List of <code>Taglet</code>s that can
     * appear in constructors.
     * @return the array of <code>Taglet</code>s that can
     * appear in constructors.
     */
    public List<Taglet> getConstructorCustomTaglets() {
        if (constructorTags == null) {
            initCustomTaglets();
        }
        return constructorTags;
    }

    /**
     * Return a List of <code>Taglet</code>s that can
     * appear in methods.
     * @return the array of <code>Taglet</code>s that can
     * appear in methods.
     */
    public List<Taglet> getMethodCustomTaglets() {
        if (methodTags == null) {
            initCustomTaglets();
        }
        return methodTags;
    }

    /**
     * Return a List of <code>Taglet</code>s that can
     * appear in an overview.
     * @return the array of <code>Taglet</code>s that can
     * appear in overview.
     */
    public List<Taglet> getOverviewCustomTaglets() {
        if (overviewTags == null) {
            initCustomTaglets();
        }
        return overviewTags;
    }

    /**
     * Initialize the custom tag Lists.
     */
    private void initCustomTaglets() {

        moduleTags = new ArrayList<>();
        packageTags = new ArrayList<>();
        typeTags = new ArrayList<>();
        fieldTags = new ArrayList<>();
        constructorTags = new ArrayList<>();
        methodTags = new ArrayList<>();
        inlineTags = new ArrayList<>();
        overviewTags = new ArrayList<>();

        for (Taglet current : customTags.values()) {
            if (current.inModule() && !current.isInlineTag()) {
                moduleTags.add(current);
            }
            if (current.inPackage() && !current.isInlineTag()) {
                packageTags.add(current);
            }
            if (current.inType() && !current.isInlineTag()) {
                typeTags.add(current);
            }
            if (current.inField() && !current.isInlineTag()) {
                fieldTags.add(current);
            }
            if (current.inConstructor() && !current.isInlineTag()) {
                constructorTags.add(current);
            }
            if (current.inMethod() && !current.isInlineTag()) {
                methodTags.add(current);
            }
            if (current.isInlineTag()) {
                inlineTags.add(current);
            }
            if (current.inOverview() && !current.isInlineTag()) {
                overviewTags.add(current);
            }
        }

        //Init the serialized form tags
        serializedFormTags = new ArrayList<>();
        serializedFormTags.add(customTags.get(SERIAL_DATA.tagName));
        serializedFormTags.add(customTags.get(THROWS.tagName));
        if (!nosince)
            serializedFormTags.add(customTags.get(SINCE.tagName));
        serializedFormTags.add(customTags.get(SEE.tagName));
    }

    /**
     * Initialize standard Javadoc tags for ordering purposes.
     */
    private void initStandardTaglets() {
        if (javafx) {
            initJavaFXTaglets();
        }

        Taglet temp;
        addStandardTaglet(new ParamTaglet());
        addStandardTaglet(new ReturnTaglet());
        addStandardTaglet(new ThrowsTaglet());
        addStandardTaglet(new SimpleTaglet(EXCEPTION.tagName, null,
                SimpleTaglet.METHOD + SimpleTaglet.CONSTRUCTOR));
        addStandardTaglet(!nosince, new SimpleTaglet(SINCE.tagName, resources.getText("doclet.Since"),
                SimpleTaglet.ALL));
        addStandardTaglet(showversion, new SimpleTaglet(VERSION.tagName, resources.getText("doclet.Version"),
                SimpleTaglet.MODULE + SimpleTaglet.PACKAGE + SimpleTaglet.TYPE + SimpleTaglet.OVERVIEW));
        addStandardTaglet(showauthor, new SimpleTaglet(AUTHOR.tagName, resources.getText("doclet.Author"),
                SimpleTaglet.MODULE + SimpleTaglet.PACKAGE + SimpleTaglet.TYPE + SimpleTaglet.OVERVIEW));
        addStandardTaglet(new SimpleTaglet(SERIAL_DATA.tagName, resources.getText("doclet.SerialData"),
                SimpleTaglet.EXCLUDED));
        addStandardTaglet(new SimpleTaglet(HIDDEN.tagName, resources.getText("doclet.Hidden"),
                SimpleTaglet.FIELD + SimpleTaglet.METHOD + SimpleTaglet.TYPE));
        customTags.put((temp = new SimpleTaglet("factory", resources.getText("doclet.Factory"),
                SimpleTaglet.METHOD)).getName(), temp);
        addStandardTaglet(new SeeTaglet());
        //Standard inline tags
        addStandardTaglet(new DocRootTaglet());
        addStandardTaglet(new InheritDocTaglet());
        addStandardTaglet(new ValueTaglet());
        addStandardTaglet(new LiteralTaglet());
        addStandardTaglet(new CodeTaglet());
        addStandardTaglet(new IndexTaglet());
        addStandardTaglet(new SummaryTaglet());

        // Keep track of the names of standard tags for error
        // checking purposes. The following are not handled above.
        standardTags.add(DEPRECATED.tagName);
        standardTags.add(LINK.tagName);
        standardTags.add(LINK_PLAIN.tagName);
        standardTags.add(SERIAL.tagName);
        standardTags.add(SERIAL_FIELD.tagName);
    }

    /**
     * Initialize JavaFX-related tags.
     */
    private void initJavaFXTaglets() {
        addStandardTaglet(new PropertyGetterTaglet());
        addStandardTaglet(new PropertySetterTaglet());
        addStandardTaglet(new SimpleTaglet("propertyDescription",
                resources.getText("doclet.PropertyDescription"),
                SimpleTaglet.FIELD + SimpleTaglet.METHOD));
        addStandardTaglet(new SimpleTaglet("defaultValue", resources.getText("doclet.DefaultValue"),
            SimpleTaglet.FIELD + SimpleTaglet.METHOD));
        addStandardTaglet(new SimpleTaglet("treatAsPrivate", null,
                SimpleTaglet.FIELD + SimpleTaglet.METHOD + SimpleTaglet.TYPE));
    }

    void addStandardTaglet(Taglet taglet) {
        String name = taglet.getName();
        customTags.put(name, taglet);
        standardTags.add(name);
    }

    void addStandardTaglet(boolean enable, Taglet taglet) {
        String name = taglet.getName();
        if (enable)
            customTags.put(name, taglet);
        standardTags.add(name);
    }

    /**
     * Initialize lowercase version of standard Javadoc tags.
     */
    private void initStandardTagsLowercase() {
        for (String standardTag : standardTags) {
            standardTagsLowercase.add(Utils.toLowerCase(standardTag));
        }
    }

    public boolean isKnownCustomTag(String tagName) {
        return customTags.containsKey(tagName);
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
            String[] namesArray = names.toArray(new String[] {});
            String result = " ";
            for (int i = 0; i < namesArray.length; i++) {
                result += "@" + namesArray[i];
                if (i + 1 < namesArray.length) {
                    result += ", ";
                }
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
    public Taglet getTaglet(String name) {
        if (name.indexOf("@") == 0) {
            return customTags.get(name.substring(1));
        } else {
            return customTags.get(name);
        }

    }
}
