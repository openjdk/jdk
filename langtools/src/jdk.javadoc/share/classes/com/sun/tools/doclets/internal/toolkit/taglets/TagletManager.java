/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.internal.toolkit.taglets;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;

import javax.tools.DocumentationTool;
import javax.tools.JavaFileManager;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.tools.javac.util.StringUtils;

/**
 * Manages the<code>Taglet</code>s used by doclets.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 * @since 1.4
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
    private LinkedHashMap<String,Taglet> customTags;

    /**
     * The array of custom tags that can appear in packages.
     */
    private Taglet[] packageTags;

    /**
     * The array of custom tags that can appear in classes or interfaces.
     */
    private Taglet[] typeTags;

    /**
     * The array of custom tags that can appear in fields.
     */
    private Taglet[] fieldTags;

    /**
     * The array of custom tags that can appear in constructors.
     */
    private Taglet[] constructorTags;

    /**
     * The array of custom tags that can appear in methods.
     */
    private Taglet[] methodTags;

    /**
     * The array of custom tags that can appear in the overview.
     */
    private Taglet[] overviewTags;

    /**
     * The array of custom tags that can appear in comments.
     */
    private Taglet[] inlineTags;

    /**
     * The array of custom tags that can appear in the serialized form.
     */
    private Taglet[] serializedFormTags;

    /**
     * The message retriever that will be used to print error messages.
     */
    private MessageRetriever message;

    /**
     * Keep track of standard tags.
     */
    private Set<String> standardTags;

    /**
     * Keep track of standard tags in lowercase to compare for better
     * error messages when a tag like @docRoot is mistakenly spelled
     * lowercase @docroot.
     */
    private Set<String> standardTagsLowercase;

    /**
     * Keep track of overriden standard tags.
     */
    private Set<String> overridenStandardTags;

    /**
     * Keep track of the tags that may conflict
     * with standard tags in the future (any custom tag without
     * a period in its name).
     */
    private Set<String> potentiallyConflictingTags;

    /**
     * The set of unseen custom tags.
     */
    private Set<String> unseenCustomTags;

    /**
     * True if we do not want to use @since tags.
     */
    private boolean nosince;

    /**
     * True if we want to use @version tags.
     */
    private boolean showversion;

    /**
     * True if we want to use @author tags.
     */
    private boolean showauthor;

    /**
     * True if we want to use JavaFX-related tags (@propertyGetter,
     * @propertySetter, @propertyDescription, @defaultValue, @treatAsPrivate).
     */
    private boolean javafx;

    private boolean exportInternalAPI;

    /**
     * Construct a new <code>TagletManager</code>.
     * @param nosince true if we do not want to use @since tags.
     * @param showversion true if we want to use @version tags.
     * @param showauthor true if we want to use @author tags.
     * @param message the message retriever to print warnings.
     */
    public TagletManager(boolean nosince, boolean showversion,
                         boolean showauthor, boolean javafx,
                         boolean exportInternalAPI,
                         MessageRetriever message) {
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
        this.exportInternalAPI = exportInternalAPI;
        this.message = message;
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
     * @param tagletPath  the path to the class representing the custom tag.
     */
    public void addCustomTag(String classname, JavaFileManager fileManager, String tagletPath) {
        try {
            Class<?> customTagClass = null;
            // construct class loader
            String cpString = null;   // make sure env.class.path defaults to dot

            ClassLoader tagClassLoader;
            if (fileManager != null && fileManager.hasLocation(DocumentationTool.Location.TAGLET_PATH)) {
                tagClassLoader = fileManager.getClassLoader(DocumentationTool.Location.TAGLET_PATH);
            } else {
                // do prepends to get correct ordering
                cpString = appendPath(System.getProperty("env.class.path"), cpString);
                cpString = appendPath(System.getProperty("java.class.path"), cpString);
                cpString = appendPath(tagletPath, cpString);
                tagClassLoader = new URLClassLoader(pathToURLs(cpString));
            }

            if (exportInternalAPI) {
                exportInternalAPI(tagClassLoader);
            }

            customTagClass = tagClassLoader.loadClass(classname);

            Method meth = customTagClass.getMethod("register",
                                                   Map.class);
            Object[] list = customTags.values().toArray();
            Taglet lastTag = (list != null && list.length > 0)
                ? (Taglet) list[list.length-1] : null;
            meth.invoke(null, customTags);
            list = customTags.values().toArray();
            Object newLastTag = (list != null&& list.length > 0)
                ? list[list.length-1] : null;
            if (lastTag != newLastTag) {
                //New taglets must always be added to the end of the LinkedHashMap.
                //If the current and previous last taglet are not equal, that
                //means a new Taglet has been added.
                message.notice("doclet.Notice_taglet_registered", classname);
                if (newLastTag != null) {
                    checkTaglet(newLastTag);
                }
            }
        } catch (Exception exc) {
            exc.printStackTrace();
            message.error("doclet.Error_taglet_not_registered", exc.getClass().getName(), classname);
        }

    }

    /**
     * Export javadoc internal API to the unnamed module for a classloader.
     * This is to support continued use of existing non-standard doclets that
     * use the internal toolkit API and related classes.
     * @param cl the classloader
     */
    private void exportInternalAPI(ClassLoader cl) {
        String[] packages = {
            "com.sun.tools.doclets",
            "com.sun.tools.doclets.standard",
            "com.sun.tools.doclets.internal.toolkit",
            "com.sun.tools.doclets.internal.toolkit.taglets",
            "com.sun.tools.doclets.internal.toolkit.builders",
            "com.sun.tools.doclets.internal.toolkit.util",
            "com.sun.tools.doclets.internal.toolkit.util.links",
            "com.sun.tools.doclets.formats.html",
            "com.sun.tools.doclets.formats.html.markup"
        };

        try {
            Method getModuleMethod = Class.class.getDeclaredMethod("getModule");
            Object thisModule = getModuleMethod.invoke(getClass());

            Class<?> moduleClass = Class.forName("java.lang.reflect.Module");
            Method addExportsMethod = moduleClass.getDeclaredMethod("addExports", String.class, moduleClass);

            Method getUnnamedModuleMethod = ClassLoader.class.getDeclaredMethod("getUnnamedModule");
            Object target = getUnnamedModuleMethod.invoke(cl);

            for (String pack : packages) {
                addExportsMethod.invoke(thisModule, pack, target);
            }
        } catch (Exception e) {
            // do nothing
            e.printStackTrace();
        }
    }

    private String appendPath(String path1, String path2) {
        if (path1 == null || path1.length() == 0) {
            return path2 == null ? "." : path2;
        } else if (path2 == null || path2.length() == 0) {
            return path1;
        } else {
            return path1  + File.pathSeparator + path2;
        }
    }

    /**
     * Utility method for converting a search path string to an array
     * of directory and JAR file URLs.
     *
     * @param path the search path string
     * @return the resulting array of directory and JAR file URLs
     */
    private URL[] pathToURLs(String path) {
        Set<URL> urls = new LinkedHashSet<>();
        for (String s: path.split(File.pathSeparator)) {
            if (s.isEmpty()) continue;
            try {
                urls.add(new File(s).getAbsoluteFile().toURI().toURL());
            } catch (MalformedURLException e) {
                message.error("doclet.MalformedURL", s);
            }
        }
        return urls.toArray(new URL[urls.size()]);
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
        locations = StringUtils.toLowerCase(locations);
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
        } else if (taglet instanceof com.sun.tools.doclets.Taglet) {
            com.sun.tools.doclets.Taglet legacyTaglet = (com.sun.tools.doclets.Taglet) taglet;
            customTags.remove(legacyTaglet.getName());
            customTags.put(legacyTaglet.getName(), new LegacyTaglet(legacyTaglet));
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
     * @param doc the Doc object that holds the tags.
     * @param tags the list of <code>Tag</code>s to check.
     * @param areInlineTags true if the array of tags are inline and false otherwise.
     */
    public void checkTags(Doc doc, Tag[] tags, boolean areInlineTags) {
        if (tags == null) {
            return;
        }
        Taglet taglet;
        for (Tag tag : tags) {
            String name = tag.name();
            if (name.length() > 0 && name.charAt(0) == '@') {
                name = name.substring(1, name.length());
            }
            if (! (standardTags.contains(name) || customTags.containsKey(name))) {
                if (standardTagsLowercase.contains(StringUtils.toLowerCase(name))) {
                    message.warning(tag.position(), "doclet.UnknownTagLowercase", tag.name());
                    continue;
                } else {
                    message.warning(tag.position(), "doclet.UnknownTag", tag.name());
                    continue;
                }
            }
            //Check if this tag is being used in the wrong location.
            if ((taglet = customTags.get(name)) != null) {
                if (areInlineTags && ! taglet.isInlineTag()) {
                    printTagMisuseWarn(taglet, tag, "inline");
                }
                if ((doc instanceof RootDoc) && ! taglet.inOverview()) {
                    printTagMisuseWarn(taglet, tag, "overview");
                } else if ((doc instanceof PackageDoc) && ! taglet.inPackage()) {
                    printTagMisuseWarn(taglet, tag, "package");
                } else if ((doc instanceof ClassDoc) && ! taglet.inType()) {
                    printTagMisuseWarn(taglet, tag, "class");
                } else if ((doc instanceof ConstructorDoc) && ! taglet.inConstructor()) {
                    printTagMisuseWarn(taglet, tag, "constructor");
                } else if ((doc instanceof FieldDoc) && ! taglet.inField()) {
                    printTagMisuseWarn(taglet, tag, "field");
                } else if ((doc instanceof MethodDoc) && ! taglet.inMethod()) {
                    printTagMisuseWarn(taglet, tag, "method");
                }
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
    private void printTagMisuseWarn(Taglet taglet, Tag tag, String holderType) {
        Set<String> locationsSet = new LinkedHashSet<>();
        if (taglet.inOverview()) {
            locationsSet.add("overview");
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
        message.warning(tag.position(), "doclet.tag_misuse",
            "@" + taglet.getName(), holderType, combined_locations.toString());
    }

    /**
     * Return the array of <code>Taglet</code>s that can
     * appear in packages.
     * @return the array of <code>Taglet</code>s that can
     * appear in packages.
     */
    public Taglet[] getPackageCustomTaglets() {
        if (packageTags == null) {
            initCustomTagletArrays();
        }
        return packageTags;
    }

    /**
     * Return the array of <code>Taglet</code>s that can
     * appear in classes or interfaces.
     * @return the array of <code>Taglet</code>s that can
     * appear in classes or interfaces.
     */
    public Taglet[] getTypeCustomTaglets() {
        if (typeTags == null) {
            initCustomTagletArrays();
        }
        return typeTags;
    }

    /**
     * Return the array of inline <code>Taglet</code>s that can
     * appear in comments.
     * @return the array of <code>Taglet</code>s that can
     * appear in comments.
     */
    public Taglet[] getInlineCustomTaglets() {
        if (inlineTags == null) {
            initCustomTagletArrays();
        }
        return inlineTags;
    }

    /**
     * Return the array of <code>Taglet</code>s that can
     * appear in fields.
     * @return the array of <code>Taglet</code>s that can
     * appear in field.
     */
    public Taglet[] getFieldCustomTaglets() {
        if (fieldTags == null) {
            initCustomTagletArrays();
        }
        return fieldTags;
    }

    /**
     * Return the array of <code>Taglet</code>s that can
     * appear in the serialized form.
     * @return the array of <code>Taglet</code>s that can
     * appear in the serialized form.
     */
    public Taglet[] getSerializedFormTaglets() {
        if (serializedFormTags == null) {
            initCustomTagletArrays();
        }
        return serializedFormTags;
    }

    /**
     * @return the array of <code>Taglet</code>s that can
     * appear in the given Doc.
     */
    public Taglet[] getCustomTaglets(Doc doc) {
        if (doc instanceof ConstructorDoc) {
            return getConstructorCustomTaglets();
        } else if (doc instanceof MethodDoc) {
            return getMethodCustomTaglets();
        } else if (doc instanceof FieldDoc) {
            return getFieldCustomTaglets();
        } else if (doc instanceof ClassDoc) {
            return getTypeCustomTaglets();
        } else if (doc instanceof PackageDoc) {
            return getPackageCustomTaglets();
        } else if (doc instanceof RootDoc) {
            return getOverviewCustomTaglets();
        }
        return null;
    }

    /**
     * Return the array of <code>Taglet</code>s that can
     * appear in constructors.
     * @return the array of <code>Taglet</code>s that can
     * appear in constructors.
     */
    public Taglet[] getConstructorCustomTaglets() {
        if (constructorTags == null) {
            initCustomTagletArrays();
        }
        return constructorTags;
    }

    /**
     * Return the array of <code>Taglet</code>s that can
     * appear in methods.
     * @return the array of <code>Taglet</code>s that can
     * appear in methods.
     */
    public Taglet[] getMethodCustomTaglets() {
        if (methodTags == null) {
            initCustomTagletArrays();
        }
        return methodTags;
    }

    /**
     * Return the array of <code>Taglet</code>s that can
     * appear in an overview.
     * @return the array of <code>Taglet</code>s that can
     * appear in overview.
     */
    public Taglet[] getOverviewCustomTaglets() {
        if (overviewTags == null) {
            initCustomTagletArrays();
        }
        return overviewTags;
    }

    /**
     * Initialize the custom tag arrays.
     */
    private void initCustomTagletArrays() {
        Iterator<Taglet> it = customTags.values().iterator();
        ArrayList<Taglet> pTags = new ArrayList<>(customTags.size());
        ArrayList<Taglet> tTags = new ArrayList<>(customTags.size());
        ArrayList<Taglet> fTags = new ArrayList<>(customTags.size());
        ArrayList<Taglet> cTags = new ArrayList<>(customTags.size());
        ArrayList<Taglet> mTags = new ArrayList<>(customTags.size());
        ArrayList<Taglet> iTags = new ArrayList<>(customTags.size());
        ArrayList<Taglet> oTags = new ArrayList<>(customTags.size());
        ArrayList<Taglet> sTags = new ArrayList<>();
        Taglet current;
        while (it.hasNext()) {
            current = it.next();
            if (current.inPackage() && !current.isInlineTag()) {
                pTags.add(current);
            }
            if (current.inType() && !current.isInlineTag()) {
                tTags.add(current);
            }
            if (current.inField() && !current.isInlineTag()) {
                fTags.add(current);
            }
            if (current.inConstructor() && !current.isInlineTag()) {
                cTags.add(current);
            }
            if (current.inMethod() && !current.isInlineTag()) {
                mTags.add(current);
            }
            if (current.isInlineTag()) {
                iTags.add(current);
            }
            if (current.inOverview() && !current.isInlineTag()) {
                oTags.add(current);
            }
        }
        packageTags = pTags.toArray(new Taglet[] {});
        typeTags = tTags.toArray(new Taglet[] {});
        fieldTags = fTags.toArray(new Taglet[] {});
        constructorTags = cTags.toArray(new Taglet[] {});
        methodTags = mTags.toArray(new Taglet[] {});
        overviewTags = oTags.toArray(new Taglet[] {});
        inlineTags = iTags.toArray(new Taglet[] {});

        //Init the serialized form tags
        sTags.add(customTags.get("serialData"));
        sTags.add(customTags.get("throws"));
        if (!nosince)
            sTags.add(customTags.get("since"));
        sTags.add(customTags.get("see"));
        serializedFormTags = sTags.toArray(new Taglet[] {});
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
        addStandardTaglet(new SimpleTaglet("exception", null,
                SimpleTaglet.METHOD + SimpleTaglet.CONSTRUCTOR));
        addStandardTaglet(!nosince, new SimpleTaglet("since", message.getText("doclet.Since"),
               SimpleTaglet.ALL));
        addStandardTaglet(showversion, new SimpleTaglet("version", message.getText("doclet.Version"),
                SimpleTaglet.PACKAGE + SimpleTaglet.TYPE + SimpleTaglet.OVERVIEW));
        addStandardTaglet(showauthor, new SimpleTaglet("author", message.getText("doclet.Author"),
                SimpleTaglet.PACKAGE + SimpleTaglet.TYPE + SimpleTaglet.OVERVIEW));
        addStandardTaglet(new SimpleTaglet("serialData", message.getText("doclet.SerialData"),
            SimpleTaglet.EXCLUDED));
        customTags.put((temp = new SimpleTaglet("factory", message.getText("doclet.Factory"),
            SimpleTaglet.METHOD)).getName(), temp);
        addStandardTaglet(new SeeTaglet());
        //Standard inline tags
        addStandardTaglet(new DocRootTaglet());
        addStandardTaglet(new InheritDocTaglet());
        addStandardTaglet(new ValueTaglet());
        addStandardTaglet(new LiteralTaglet());
        addStandardTaglet(new CodeTaglet());
        addStandardTaglet(new IndexTaglet());

        // Keep track of the names of standard tags for error
        // checking purposes. The following are not handled above.
        // See, for example, com.sun.tools.javadoc.Comment
        standardTags.add("deprecated");
        standardTags.add("link");
        standardTags.add("linkplain");
        standardTags.add("serial");
        standardTags.add("serialField");
        standardTags.add("Text");
    }

    /**
     * Initialize JavaFX-related tags.
     */
    private void initJavaFXTaglets() {
        addStandardTaglet(new PropertyGetterTaglet());
        addStandardTaglet(new PropertySetterTaglet());
        addStandardTaglet(new SimpleTaglet("propertyDescription",
                message.getText("doclet.PropertyDescription"),
                SimpleTaglet.FIELD + SimpleTaglet.METHOD));
        addStandardTaglet(new SimpleTaglet("defaultValue", message.getText("doclet.DefaultValue"),
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
            standardTagsLowercase.add(StringUtils.toLowerCase(standardTag));
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
            message.notice(noticeKey, result);
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
