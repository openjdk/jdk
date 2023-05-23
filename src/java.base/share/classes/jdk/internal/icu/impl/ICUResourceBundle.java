// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 * *****************************************************************************
 * Copyright (C) 2005-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 * *****************************************************************************
 */

package jdk.internal.icu.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

import jdk.internal.icu.impl.ICUResourceBundleReader.ReaderValue;
import jdk.internal.icu.impl.URLHandler.URLVisitor;
import jdk.internal.icu.util.ULocale;
import jdk.internal.icu.util.UResourceBundle;
import jdk.internal.icu.util.UResourceBundleIterator;
import jdk.internal.icu.util.UResourceTypeMismatchException;

@SuppressWarnings("deprecation")
public  class ICUResourceBundle extends UResourceBundle {
    /**
     * CLDR string value "\u2205\u2205\u2205" prevents fallback to the parent bundle.
     */
    public static final String NO_INHERITANCE_MARKER = "\u2205\u2205\u2205";

    /**
     * The class loader constant to be used with getBundleInstance API
     */
    public static final ClassLoader ICU_DATA_CLASS_LOADER = ClassLoaderUtil.getClassLoader(ICUData.class);

    /**
     * The name of the resource containing the installed locales
     */
    protected static final String INSTALLED_LOCALES = "InstalledLocales";

    /**
     * Fields for a whole bundle, rather than any specific resource in the bundle.
     * Corresponds roughly to ICU4C/source/common/uresimp.h struct UResourceDataEntry.
     */
    protected static final class WholeBundle {
        WholeBundle(String baseName, String localeID, ClassLoader loader,
                ICUResourceBundleReader reader) {
            this.baseName = baseName;
            this.localeID = localeID;
            this.ulocale = new ULocale(localeID);
            this.loader = loader;
            this.reader = reader;
        }

        String baseName;
        String localeID;
        ULocale ulocale;
        ClassLoader loader;

        /**
         * Access to the bits and bytes of the resource bundle.
         * Hides low-level details.
         */
        ICUResourceBundleReader reader;

        // TODO: Remove topLevelKeys when we upgrade to Java 6 where ResourceBundle caches the keySet().
        Set<String> topLevelKeys;
    }

    WholeBundle wholeBundle;
    private ICUResourceBundle container;

    /** Loader for bundle instances, for caching. */
    private static abstract class Loader {
        abstract ICUResourceBundle load();
    }

    private static CacheBase<String, ICUResourceBundle, Loader> BUNDLE_CACHE =
            new SoftCache<String, ICUResourceBundle, Loader>() {
        @Override
        protected ICUResourceBundle createInstance(String unusedKey, Loader loader) {
            return loader.load();
        }
    };

    /**
     * Returns a functionally equivalent locale, considering keywords as well, for the specified keyword.
     * @param baseName resource specifier
     * @param resName top level resource to consider (such as "collations")
     * @param keyword a particular keyword to consider (such as "collation" )
     * @param locID The requested locale
     * @param isAvailable If non-null, 1-element array of fillin parameter that indicates whether the
     * requested locale was available. The locale is defined as 'available' if it physically
     * exists within the specified tree and included in 'InstalledLocales'.
     * @param omitDefault  if true, omit keyword and value if default.
     * 'de_DE\@collation=standard' -> 'de_DE'
     * @return the locale
     * @internal ICU 3.0
     */
    public static final ULocale getFunctionalEquivalent(String baseName, ClassLoader loader,
            String resName, String keyword, ULocale locID,
            boolean isAvailable[], boolean omitDefault) {
        String kwVal = locID.getKeywordValue(keyword);
        String baseLoc = locID.getBaseName();
        String defStr = null;
        ULocale parent = new ULocale(baseLoc);
        ULocale defLoc = null; // locale where default (found) resource is
        boolean lookForDefault = false; // true if kwVal needs to be set
        ULocale fullBase = null; // base locale of found (target) resource
        int defDepth = 0; // depth of 'default' marker
        int resDepth = 0; // depth of found resource;

        if ((kwVal == null) || (kwVal.length() == 0)
                || kwVal.equals(DEFAULT_TAG)) {
            kwVal = ""; // default tag is treated as no keyword
            lookForDefault = true;
        }

        // Check top level locale first
        ICUResourceBundle r = null;

        r = (ICUResourceBundle) UResourceBundle.getBundleInstance(baseName, parent);
        if (isAvailable != null) {
            isAvailable[0] = false;
            ULocale[] availableULocales = getAvailEntry(baseName, loader)
                    .getULocaleList(ULocale.AvailableType.DEFAULT);
            for (int i = 0; i < availableULocales.length; i++) {
                if (parent.equals(availableULocales[i])) {
                    isAvailable[0] = true;
                    break;
                }
            }
        }
        // determine in which locale (if any) the currently relevant 'default' is
        do {
            try {
                ICUResourceBundle irb = (ICUResourceBundle) r.get(resName);
                defStr = irb.getString(DEFAULT_TAG);
                if (lookForDefault == true) {
                    kwVal = defStr;
                    lookForDefault = false;
                }
                defLoc = r.getULocale();
            } catch (MissingResourceException t) {
                // Ignore error and continue search.
            }
            if (defLoc == null) {
                r = r.getParent();
                defDepth++;
            }
        } while ((r != null) && (defLoc == null));

        // Now, search for the named resource
        parent = new ULocale(baseLoc);
        r = (ICUResourceBundle) UResourceBundle.getBundleInstance(baseName, parent);
        // determine in which locale (if any) the named resource is located
        do {
            try {
                ICUResourceBundle irb = (ICUResourceBundle)r.get(resName);
                /* UResourceBundle urb = */irb.get(kwVal);
                fullBase = irb.getULocale();
                // If the get() completed, we have the full base locale
                // If we fell back to an ancestor of the old 'default',
                // we need to re calculate the "default" keyword.
                if ((fullBase != null) && ((resDepth) > defDepth)) {
                    defStr = irb.getString(DEFAULT_TAG);
                    defLoc = r.getULocale();
                    defDepth = resDepth;
                }
            } catch (MissingResourceException t) {
                // Ignore error,
            }
            if (fullBase == null) {
                r = r.getParent();
                resDepth++;
            }
        } while ((r != null) && (fullBase == null));

        if (fullBase == null && // Could not find resource 'kwVal'
                (defStr != null) && // default was defined
                !defStr.equals(kwVal)) { // kwVal is not default
            // couldn't find requested resource. Fall back to default.
            kwVal = defStr; // Fall back to default.
            parent = new ULocale(baseLoc);
            r = (ICUResourceBundle) UResourceBundle.getBundleInstance(baseName, parent);
            resDepth = 0;
            // determine in which locale (if any) the named resource is located
            do {
                try {
                    ICUResourceBundle irb = (ICUResourceBundle)r.get(resName);
                    ICUResourceBundle urb = (ICUResourceBundle)irb.get(kwVal);

                    // if we didn't fail before this..
                    fullBase = r.getULocale();

                    // If the fetched item (urb) is in a different locale than our outer locale (r/fullBase)
                    // then we are in a 'fallback' situation. treat as a missing resource situation.
                    if(!fullBase.getBaseName().equals(urb.getULocale().getBaseName())) {
                        fullBase = null; // fallback condition. Loop and try again.
                    }

                    // If we fell back to an ancestor of the old 'default',
                    // we need to re calculate the "default" keyword.
                    if ((fullBase != null) && ((resDepth) > defDepth)) {
                        defStr = irb.getString(DEFAULT_TAG);
                        defLoc = r.getULocale();
                        defDepth = resDepth;
                    }
                } catch (MissingResourceException t) {
                    // Ignore error, continue search.
                }
                if (fullBase == null) {
                    r = r.getParent();
                    resDepth++;
                }
            } while ((r != null) && (fullBase == null));
        }

        if (fullBase == null) {
            throw new MissingResourceException(
                "Could not find locale containing requested or default keyword.",
                baseName, keyword + "=" + kwVal);
        }

        if (omitDefault
            && defStr.equals(kwVal) // if default was requested and
            && resDepth <= defDepth) { // default was set in same locale or child
            return fullBase; // Keyword value is default - no keyword needed in locale
        } else {
            return new ULocale(fullBase.getBaseName() + "@" + keyword + "=" + kwVal);
        }
    }

    /**
     * Given a tree path and keyword, return a string enumeration of all possible values for that keyword.
     * @param baseName resource specifier
     * @param keyword a particular keyword to consider, must match a top level resource name
     * within the tree. (i.e. "collations")
     * @internal ICU 3.0
     */
    public static final String[] getKeywordValues(String baseName, String keyword) {
        Set<String> keywords = new HashSet<>();
        ULocale locales[] = getAvailEntry(baseName, ICU_DATA_CLASS_LOADER)
                .getULocaleList(ULocale.AvailableType.DEFAULT);
        int i;

        for (i = 0; i < locales.length; i++) {
            try {
                UResourceBundle b = UResourceBundle.getBundleInstance(baseName, locales[i]);
                // downcast to ICUResourceBundle?
                ICUResourceBundle irb = (ICUResourceBundle) (b.getObject(keyword));
                Enumeration<String> e = irb.getKeys();
                while (e.hasMoreElements()) {
                    String s = e.nextElement();
                    if (!DEFAULT_TAG.equals(s) && !s.startsWith("private-")) {
                        // don't add 'default' items, nor unlisted types
                        keywords.add(s);
                    }
                }
            } catch (Throwable t) {
                //System.err.println("Error in - " + new Integer(i).toString()
                // + " - " + t.toString());
                // ignore the err - just skip that resource
            }
        }
        return keywords.toArray(new String[0]);
    }

    /**
     * This method performs multilevel fallback for fetching items from the
     * bundle e.g: If resource is in the form de__PHONEBOOK{ collations{
     * default{ "phonebook"} } } If the value of "default" key needs to be
     * accessed, then do: <code>
     *  UResourceBundle bundle = UResourceBundle.getBundleInstance("de__PHONEBOOK");
     *  ICUResourceBundle result = null;
     *  if(bundle instanceof ICUResourceBundle){
     *      result = ((ICUResourceBundle) bundle).getWithFallback("collations/default");
     *  }
     * </code>
     *
     * @param path The path to the required resource key
     * @return resource represented by the key
     * @exception MissingResourceException If a resource was not found.
     */
    public ICUResourceBundle getWithFallback(String path) throws MissingResourceException {
        ICUResourceBundle actualBundle = this;

        // now recurse to pick up sub levels of the items
        ICUResourceBundle result = findResourceWithFallback(path, actualBundle, null);

        if (result == null) {
            throw new MissingResourceException(
                "Can't find resource for bundle "
                + this.getClass().getName() + ", key " + getType(),
                path, getKey());
        }

        if (result.getType() == STRING && result.getString().equals(NO_INHERITANCE_MARKER)) {
            throw new MissingResourceException("Encountered NO_INHERITANCE_MARKER", path, getKey());
        }

        return result;
    }

    public ICUResourceBundle at(int index) {
        return (ICUResourceBundle) handleGet(index, null, this);
    }

    public ICUResourceBundle at(String key) {
        // don't ever presume the key is an int in disguise, like ResourceArray does.
        if (this instanceof ICUResourceBundleImpl.ResourceTable) {
            return (ICUResourceBundle) handleGet(key, null, this);
        }
        return null;
    }

    @Override
    public ICUResourceBundle findTopLevel(int index) {
        return (ICUResourceBundle) super.findTopLevel(index);
    }

    @Override
    public ICUResourceBundle findTopLevel(String aKey) {
        return (ICUResourceBundle) super.findTopLevel(aKey);
    }

    /**
     * Like getWithFallback, but returns null if the resource is not found instead of
     * throwing an exception.
     * @param path the path to the resource
     * @return the resource, or null
     */
    public ICUResourceBundle findWithFallback(String path) {
        return findResourceWithFallback(path, this, null);
    }
    public String findStringWithFallback(String path) {
        return findStringWithFallback(path, this, null);
    }

    // will throw type mismatch exception if the resource is not a string
    public String getStringWithFallback(String path) throws MissingResourceException {
        // Optimized form of getWithFallback(path).getString();
        ICUResourceBundle actualBundle = this;
        String result = findStringWithFallback(path, actualBundle, null);

        if (result == null) {
            throw new MissingResourceException(
                "Can't find resource for bundle "
                + this.getClass().getName() + ", key " + getType(),
                path, getKey());
        }

        if (result.equals(NO_INHERITANCE_MARKER)) {
            throw new MissingResourceException("Encountered NO_INHERITANCE_MARKER", path, getKey());
        }
        return result;
    }

    public UResource.Value getValueWithFallback(String path) throws MissingResourceException {
        ICUResourceBundle rb;
        if (path.isEmpty()) {
            rb = this;
        } else {
            rb = findResourceWithFallback(path, this, null);
            if (rb == null) {
                throw new MissingResourceException(
                    "Can't find resource for bundle "
                    + this.getClass().getName() + ", key " + getType(),
                    path, getKey());
            }
        }
        ReaderValue readerValue = new ReaderValue();
        ICUResourceBundleImpl impl = (ICUResourceBundleImpl)rb;
        readerValue.reader = impl.wholeBundle.reader;
        readerValue.res = impl.getResource();
        return readerValue;
    }

    public void getAllItemsWithFallbackNoFail(String path, UResource.Sink sink) {
        try {
            getAllItemsWithFallback(path, sink);
        } catch (MissingResourceException e) {
            // Quietly ignore the exception.
        }
    }

    /**
     * Locates the resource specified by {@code path} in this resource bundle (performing any necessary fallback
     * and following any aliases) and calls the specified {@code sink}'s {@code put()} method with that resource.
     * Then walks the bundle's parent chain, calling {@code put()} on the sink for each item in the parent chain.
     * @param path The path of the desired resource
     * @param sink A {@code UResource.Sink} that gets called for each resource in the parent chain
     */
    public void getAllItemsWithFallback(String path, UResource.Sink sink)
            throws MissingResourceException {
        // Collect existing and parsed key objects into an array of keys,
        // rather than assembling and parsing paths.
        int numPathKeys = countPathKeys(path);  // How much deeper does the path go?
        ICUResourceBundle rb;
        if (numPathKeys == 0) {
            rb = this;
        } else {
            // Get the keys for finding the target.
            int depth = getResDepth();  // How deep are we in this bundle?
            String[] pathKeys = new String[depth + numPathKeys];
            getResPathKeys(path, numPathKeys, pathKeys, depth);
            rb = findResourceWithFallback(pathKeys, depth, this, null);
            if (rb == null) {
                throw new MissingResourceException(
                    "Can't find resource for bundle "
                    + this.getClass().getName() + ", key " + getType(),
                    path, getKey());
            }
        }
        UResource.Key key = new UResource.Key();
        ReaderValue readerValue = new ReaderValue();
        rb.getAllItemsWithFallback(key, readerValue, sink, this);
    }

    /**
     * Locates the resource specified by {@code path} in this resource bundle (performing any necessary fallback and
     * following any aliases) and, if the resource is a table resource, iterates over its immediate child resources (again,
     * following any aliases to get the individual resource values), and calls the specified {@code sink}'s {@code put()}
     * method for each child resource (passing it that resource's key and either its actual value or, if that value is an
     * alias, the value you get by following the alias).  Then walks back over the bundle's parent chain,
     * similarly iterating over each parent table resource's child resources.
     * Does not descend beyond one level of table children.
     * @param path The path of the desired resource
     * @param sink A {@code UResource.Sink} that gets called for each child resource of the specified resource (and each
     * child of the resources in its parent chain).
     */
    public void getAllChildrenWithFallback(final String path, final UResource.Sink sink)
            throws MissingResourceException {
        class AllChildrenSink extends UResource.Sink {
            @Override
            public void put(UResource.Key key, UResource.Value value, boolean noFallback) {
                UResource.Table itemsTable = value.getTable();
                for (int i = 0; itemsTable.getKeyAndValue(i, key, value); ++i) {
                    if (value.getType() == ALIAS) {
                        // if the current entry in the table is an alias, re-fetch it using getAliasedResource():
                        // this will follow the alias (and any aliases it points to) and bring back the real value
                        String aliasPath = value.getAliasString();
                        ICUResourceBundle aliasedResource = getAliasedResource(aliasPath, wholeBundle.loader,
                                "", null, 0, null,
                                null, ICUResourceBundle.this);
                        ICUResourceBundleImpl aliasedResourceImpl = (ICUResourceBundleImpl)aliasedResource;
                        ReaderValue aliasedValue = new ReaderValue();
                        aliasedValue.reader = aliasedResourceImpl.wholeBundle.reader;
                        aliasedValue.res = aliasedResourceImpl.getResource();
                        sink.put(key, aliasedValue, noFallback);
                    } else {
                        sink.put(key, value, noFallback);
                    }
                }
            }
        }

        getAllItemsWithFallback(path, new AllChildrenSink());
    }

    private void getAllItemsWithFallback(
            UResource.Key key, ReaderValue readerValue, UResource.Sink sink, UResourceBundle requested) {
        // We recursively enumerate child-first,
        // only storing parent items in the absence of child items.
        // The sink needs to store a placeholder value for the no-fallback/no-inheritance marker
        // to prevent a parent item from being stored.
        //
        // It would be possible to recursively enumerate parent-first,
        // overriding parent items with child items.
        // When the sink sees the no-fallback/no-inheritance marker,
        // then it would remove the parent's item.
        // We would deserialize parent values even though they are overridden in a child bundle.
        ICUResourceBundleImpl impl = (ICUResourceBundleImpl)this;
        readerValue.reader = impl.wholeBundle.reader;
        readerValue.res = impl.getResource();
        key.setString(this.key != null ? this.key : "");
        sink.put(key, readerValue, parent == null);
        if (parent != null) {
            // We might try to query the sink whether
            // any fallback from the parent bundle is still possible.
            ICUResourceBundle parentBundle = (ICUResourceBundle)parent;
            ICUResourceBundle rb;
            int depth = getResDepth();
            if (depth == 0) {
                rb = parentBundle;
            } else {
                // Re-fetch the path keys: They may differ from the original ones
                // if we had followed an alias.
                String[] pathKeys = new String[depth];
                getResPathKeys(pathKeys, depth);
                rb = findResourceWithFallback(pathKeys, 0, parentBundle, requested);
            }
            if (rb != null) {
                rb.getAllItemsWithFallback(key, readerValue, sink, requested);
            }
        }
    }

    /**
     * Return a set of the locale names supported by a collection of resource
     * bundles.
     *
     * @param bundlePrefix the prefix of the resource bundles to use.
     */
    public static Set<String> getAvailableLocaleNameSet(String bundlePrefix, ClassLoader loader) {
        return getAvailEntry(bundlePrefix, loader).getLocaleNameSet();
    }

    /**
     * Return a set of all the locale names supported by a collection of
     * resource bundles.
     */
    public static Set<String> getFullLocaleNameSet() {
        return getFullLocaleNameSet(ICUData.ICU_BASE_NAME, ICU_DATA_CLASS_LOADER);
    }

    /**
     * Return a set of all the locale names supported by a collection of
     * resource bundles.
     *
     * @param bundlePrefix the prefix of the resource bundles to use.
     */
    public static Set<String> getFullLocaleNameSet(String bundlePrefix, ClassLoader loader) {
        return getAvailEntry(bundlePrefix, loader).getFullLocaleNameSet();
    }

    /**
     * Return a set of the locale names supported by a collection of resource
     * bundles.
     */
    public static Set<String> getAvailableLocaleNameSet() {
        return getAvailableLocaleNameSet(ICUData.ICU_BASE_NAME, ICU_DATA_CLASS_LOADER);
    }

    /**
     * Get the set of Locales installed in the specified bundles, for the specified type.
     * @return the list of available locales
     */
    public static final ULocale[] getAvailableULocales(String baseName, ClassLoader loader,
            ULocale.AvailableType type) {
        return getAvailEntry(baseName, loader).getULocaleList(type);
    }

    /**
     * Get the set of ULocales installed the base bundle.
     * @return the list of available locales
     */
    public static final ULocale[] getAvailableULocales() {
        return getAvailableULocales(ICUData.ICU_BASE_NAME, ICU_DATA_CLASS_LOADER, ULocale.AvailableType.DEFAULT);
    }

    /**
     * Get the set of ULocales installed the base bundle, for the specified type.
     * @return the list of available locales for the specified type
     */
    public static final ULocale[] getAvailableULocales(ULocale.AvailableType type) {
        return getAvailableULocales(ICUData.ICU_BASE_NAME, ICU_DATA_CLASS_LOADER, type);
    }

    /**
     * Get the set of Locales installed in the specified bundles.
     * @return the list of available locales
     */
    public static final ULocale[] getAvailableULocales(String baseName, ClassLoader loader) {
        return getAvailableULocales(baseName, loader, ULocale.AvailableType.DEFAULT);
    }

    /**
     * Get the set of Locales installed in the specified bundles, for the specified type.
     * @return the list of available locales
     */
    public static final Locale[] getAvailableLocales(String baseName, ClassLoader loader,
            ULocale.AvailableType type) {
        return getAvailEntry(baseName, loader).getLocaleList(type);
    }

    /**
     * Get the set of ULocales installed the base bundle.
     * @return the list of available locales
     */
    public static final Locale[] getAvailableLocales() {
        return getAvailableLocales(ICUData.ICU_BASE_NAME, ICU_DATA_CLASS_LOADER, ULocale.AvailableType.DEFAULT);
    }

    /**
     * Get the set of Locales installed the base bundle, for the specified type.
     * @return the list of available locales
     */
    public static final Locale[] getAvailableLocales(ULocale.AvailableType type) {
        return getAvailableLocales(ICUData.ICU_BASE_NAME, ICU_DATA_CLASS_LOADER, type);
    }

    /**
     * Get the set of Locales installed in the specified bundles.
     * @return the list of available locales
     */
    public static final Locale[] getAvailableLocales(String baseName, ClassLoader loader) {
        return getAvailableLocales(baseName, loader, ULocale.AvailableType.DEFAULT);
    }

    /**
     * Convert a list of ULocales to a list of Locales.  ULocales with a script code will not be converted
     * since they cannot be represented as a Locale.  This means that the two lists will <b>not</b> match
     * one-to-one, and that the returned list might be shorter than the input list.
     * @param ulocales a list of ULocales to convert to a list of Locales.
     * @return the list of converted ULocales
     */
    public static final Locale[] getLocaleList(ULocale[] ulocales) {
        ArrayList<Locale> list = new ArrayList<>(ulocales.length);
        HashSet<Locale> uniqueSet = new HashSet<>();
        for (int i = 0; i < ulocales.length; i++) {
            Locale loc = ulocales[i].toLocale();
            if (!uniqueSet.contains(loc)) {
                list.add(loc);
                uniqueSet.add(loc);
            }
        }
        return list.toArray(new Locale[list.size()]);
    }

    /**
     * Returns the locale of this resource bundle. This method can be used after
     * a call to getBundle() to determine whether the resource bundle returned
     * really corresponds to the requested locale or is a fallback.
     *
     * @return the locale of this resource bundle
     */
    @Override
    public Locale getLocale() {
        return getULocale().toLocale();
    }


    // ========== privates ==========
    private static final String ICU_RESOURCE_INDEX = "res_index";

    private static final String DEFAULT_TAG = "default";

    // The name of text file generated by ICU4J build script including all locale names
    // (canonical, alias and root)
    private static final String FULL_LOCALE_NAMES_LIST = "fullLocaleNames.lst";

    // Flag for enabling/disabling debugging code
    private static final boolean DEBUG = ICUDebug.enabled("localedata");

    private static final class AvailableLocalesSink extends UResource.Sink {

        EnumMap<ULocale.AvailableType, ULocale[]> output;

        public AvailableLocalesSink(EnumMap<ULocale.AvailableType, ULocale[]> output) {
            this.output = output;
        }

        @Override
        public void put(UResource.Key key, UResource.Value value, boolean noFallback) {
            UResource.Table resIndexTable = value.getTable();
            for (int i = 0; resIndexTable.getKeyAndValue(i, key, value); ++i) {
                ULocale.AvailableType type;
                if (key.contentEquals("InstalledLocales")) {
                    type = ULocale.AvailableType.DEFAULT;
                } else if (key.contentEquals("AliasLocales")) {
                    type = ULocale.AvailableType.ONLY_LEGACY_ALIASES;
                } else {
                    // CLDRVersion, etc.
                    continue;
                }
                UResource.Table availableLocalesTable = value.getTable();
                ULocale[] locales = new ULocale[availableLocalesTable.getSize()];
                for (int j = 0; availableLocalesTable.getKeyAndValue(j, key, value); ++j) {
                    locales[j] = new ULocale(key.toString());
                }
                output.put(type, locales);
            }
        }
    }

    private static final EnumMap<ULocale.AvailableType, ULocale[]> createULocaleList(
            String baseName, ClassLoader root) {
        // the canned list is a subset of all the available .res files, the idea
        // is we don't export them
        // all. gotta be a better way to do this, since to add a locale you have
        // to update this list,
        // and it's embedded in our binary resources.
        ICUResourceBundle rb = (ICUResourceBundle) UResourceBundle.instantiateBundle(baseName, ICU_RESOURCE_INDEX, root, true);

        EnumMap<ULocale.AvailableType, ULocale[]> result = new EnumMap<>(ULocale.AvailableType.class);
        AvailableLocalesSink sink = new AvailableLocalesSink(result);
        rb.getAllItemsWithFallback("", sink);
        return result;
    }

    // Same as createULocaleList() but catches the MissingResourceException
    // and returns the data in a different form.
    private static final void addLocaleIDsFromIndexBundle(String baseName,
            ClassLoader root, Set<String> locales) {
        ICUResourceBundle bundle;
        try {
            bundle = (ICUResourceBundle) UResourceBundle.instantiateBundle(baseName, ICU_RESOURCE_INDEX, root, true);
            bundle = (ICUResourceBundle) bundle.get(INSTALLED_LOCALES);
        } catch (MissingResourceException e) {
            if (DEBUG) {
                System.out.println("couldn't find " + baseName + '/' + ICU_RESOURCE_INDEX + ".res");
                Thread.dumpStack();
            }
            return;
        }
        UResourceBundleIterator iter = bundle.getIterator();
        iter.reset();
        while (iter.hasNext()) {
            String locstr = iter.next(). getKey();
            locales.add(locstr);
        }
    }

    private static void addLocaleIDsFromListFile(String bn, ClassLoader root, Set<String> locales) {
        try {
            InputStream s = root.getResourceAsStream(bn + FULL_LOCALE_NAMES_LIST);
            if (s != null) {
                BufferedReader br = new BufferedReader(new InputStreamReader(s, "ASCII"));
                try {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.length() != 0 && !line.startsWith("#")) {
                            locales.add(line);
                        }
                    }
                }
                finally {
                    br.close();
                }
            }
        } catch (IOException ignored) {
            // swallow it
        }
    }

    private static Set<String> createFullLocaleNameSet(String baseName, ClassLoader loader) {
        String bn = baseName.endsWith("/") ? baseName : baseName + "/";
        Set<String> set = new HashSet<>();
        String skipScan = ICUConfig.get("jdk.internal.icu.impl.ICUResourceBundle.skipRuntimeLocaleResourceScan", "false");
        if (!skipScan.equalsIgnoreCase("true")) {
            // scan available locale resources under the base url first
            if (baseName.startsWith(ICUData.ICU_BASE_NAME)) {
                String folder;
                if (baseName.length() == ICUData.ICU_BASE_NAME.length()) {
                    folder = "";
                } else if (baseName.charAt(ICUData.ICU_BASE_NAME.length()) == '/') {
                    folder = baseName.substring(ICUData.ICU_BASE_NAME.length() + 1);
                } else {
                    folder = null;
                }
                if (folder != null) {
                    ICUBinary.addBaseNamesInFileFolder(folder, ".res", set);
                }
            }
            set.remove(ICU_RESOURCE_INDEX);  // "res_index"
            // HACK: TODO: Figure out how we can distinguish locale data from other data items.
            Iterator<String> iter = set.iterator();
            while (iter.hasNext()) {
                String name = iter.next();
                if ((name.length() == 1 || name.length() > 3) && name.indexOf('_') < 0) {
                    // Does not look like a locale ID.
                    iter.remove();
                }
            }
        }
        // look for prebuilt full locale names list next
        if (set.isEmpty()) {
            if (DEBUG) System.out.println("unable to enumerate data files in " + baseName);
            addLocaleIDsFromListFile(bn, loader, set);
        }
        if (set.isEmpty()) {
            // Use locale name set as the last resort fallback
            addLocaleIDsFromIndexBundle(baseName, loader, set);
        }
        // We need to have the root locale in the set, but not as "root".
        set.remove("root");
        set.add(ULocale.ROOT.toString());  // ""
        return Collections.unmodifiableSet(set);
    }

    private static Set<String> createLocaleNameSet(String baseName, ClassLoader loader) {
        HashSet<String> set = new HashSet<>();
        addLocaleIDsFromIndexBundle(baseName, loader, set);
        return Collections.unmodifiableSet(set);
    }

    /**
     * Holds the prefix, and lazily creates the Locale[] list or the locale name
     * Set as needed.
     */
    private static final class AvailEntry {
        private String prefix;
        private ClassLoader loader;
        private volatile EnumMap<ULocale.AvailableType, ULocale[]> ulocales;
        private volatile Locale[] locales;
        private volatile Set<String> nameSet;
        private volatile Set<String> fullNameSet;

        AvailEntry(String prefix, ClassLoader loader) {
            this.prefix = prefix;
            this.loader = loader;
        }

        ULocale[] getULocaleList(ULocale.AvailableType type) {
            // Direct data is available for DEFAULT and ONLY_LEGACY_ALIASES
            assert type != ULocale.AvailableType.WITH_LEGACY_ALIASES;
            if (ulocales == null) {
                synchronized(this) {
                    if (ulocales == null) {
                        ulocales = createULocaleList(prefix, loader);
                    }
                }
            }
            return ulocales.get(type);
        }
        Locale[] getLocaleList(ULocale.AvailableType type) {
            if (locales == null) {
                getULocaleList(type);
                synchronized(this) {
                    if (locales == null) {
                        locales = ICUResourceBundle.getLocaleList(ulocales.get(type));
                    }
                }
            }
            return locales;
        }
        Set<String> getLocaleNameSet() {
            if (nameSet == null) {
                synchronized(this) {
                    if (nameSet == null) {
                        nameSet = createLocaleNameSet(prefix, loader);
                    }
                }
            }
            return nameSet;
        }
        Set<String> getFullLocaleNameSet() {
            // When there's no prebuilt index, we iterate through the jar files
            // and read the contents to build it.  If many threads try to read the
            // same jar at the same time, java thrashes.  Synchronize here
            // so that we can avoid this problem. We don't synchronize on the
            // other methods since they don't do this.
            //
            // This is the common entry point for access into the code that walks
            // through the resources, and is cached.  So it's a good place to lock
            // access.  Locking in the URLHandler doesn't give us a common object
            // to lock.
            if (fullNameSet == null) {
                synchronized(this) {
                    if (fullNameSet == null) {
                        fullNameSet = createFullLocaleNameSet(prefix, loader);
                    }
                }
            }
            return fullNameSet;
        }
    }


    /*
     * Cache used for AvailableEntry
     */
    private static CacheBase<String, AvailEntry, ClassLoader> GET_AVAILABLE_CACHE =
        new SoftCache<String, AvailEntry, ClassLoader>()  {
            @Override
            protected AvailEntry createInstance(String key, ClassLoader loader) {
                return new AvailEntry(key, loader);
            }
        };

    /**
     * Stores the locale information in a cache accessed by key (bundle prefix).
     * The cached objects are AvailEntries. The cache is implemented by SoftCache
     * so it can be GC'd.
     */
    private static AvailEntry getAvailEntry(String key, ClassLoader loader) {
        return GET_AVAILABLE_CACHE.getInstance(key, loader);
    }

    private static final ICUResourceBundle findResourceWithFallback(String path,
            UResourceBundle actualBundle, UResourceBundle requested) {
        if (path.length() == 0) {
            return null;
        }
        ICUResourceBundle base = (ICUResourceBundle) actualBundle;
        // Collect existing and parsed key objects into an array of keys,
        // rather than assembling and parsing paths.
        int depth = base.getResDepth();
        int numPathKeys = countPathKeys(path);
        assert numPathKeys > 0;
        String[] keys = new String[depth + numPathKeys];
        getResPathKeys(path, numPathKeys, keys, depth);
        return findResourceWithFallback(keys, depth, base, requested);
    }

    private static final ICUResourceBundle findResourceWithFallback(
            String[] keys, int depth,
            ICUResourceBundle base, UResourceBundle requested) {
        if (requested == null) {
            requested = base;
        }

        for (;;) {  // Iterate over the parent bundles.
            for (;;) {  // Iterate over the keys on the requested path, within a bundle.
                String subKey = keys[depth++];
                ICUResourceBundle sub = (ICUResourceBundle) base.handleGet(subKey, null, requested);
                if (sub == null) {
                    --depth;
                    break;
                }
                if (depth == keys.length) {
                    // We found it.
                    return sub;
                }
                base = sub;
            }
            // Try the parent bundle of the last-found resource.
            ICUResourceBundle nextBase = base.getParent();
            if (nextBase == null) {
                return null;
            }
            // If we followed an alias, then we may have switched bundle (locale) and key path.
            // Set the lower parts of the path according to the last-found resource.
            // This relies on a resource found via alias to have its original location information,
            // rather than the location of the alias.
            int baseDepth = base.getResDepth();
            if (depth != baseDepth) {
                String[] newKeys = new String[baseDepth + (keys.length - depth)];
                System.arraycopy(keys, depth, newKeys, baseDepth, keys.length - depth);
                keys = newKeys;
            }
            base.getResPathKeys(keys, baseDepth);
            base = nextBase;
            depth = 0;  // getParent() returned a top level table resource.
        }
    }

    /**
     * Like findResourceWithFallback(...).getString() but with minimal creation of intermediate
     * ICUResourceBundle objects.
     */
    private static final String findStringWithFallback(String path,
            UResourceBundle actualBundle, UResourceBundle requested) {
        if (path.length() == 0) {
            return null;
        }
        if (!(actualBundle instanceof ICUResourceBundleImpl.ResourceContainer)) {
            return null;
        }
        if (requested == null) {
            requested = actualBundle;
        }

        ICUResourceBundle base = (ICUResourceBundle) actualBundle;
        ICUResourceBundleReader reader = base.wholeBundle.reader;
        int res = RES_BOGUS;

        // Collect existing and parsed key objects into an array of keys,
        // rather than assembling and parsing paths.
        int baseDepth = base.getResDepth();
        int depth = baseDepth;
        int numPathKeys = countPathKeys(path);
        assert numPathKeys > 0;
        String[] keys = new String[depth + numPathKeys];
        getResPathKeys(path, numPathKeys, keys, depth);

        for (;;) {  // Iterate over the parent bundles.
            for (;;) {  // Iterate over the keys on the requested path, within a bundle.
                ICUResourceBundleReader.Container readerContainer;
                if (res == RES_BOGUS) {
                    int type = base.getType();
                    if (type == TABLE || type == ARRAY) {
                        readerContainer = ((ICUResourceBundleImpl.ResourceContainer)base).value;
                    } else {
                        break;
                    }
                } else {
                    int type = ICUResourceBundleReader.RES_GET_TYPE(res);
                    if (ICUResourceBundleReader.URES_IS_TABLE(type)) {
                        readerContainer = reader.getTable(res);
                    } else if (ICUResourceBundleReader.URES_IS_ARRAY(type)) {
                        readerContainer = reader.getArray(res);
                    } else {
                        res = RES_BOGUS;
                        break;
                    }
                }
                String subKey = keys[depth++];
                res = readerContainer.getResource(reader, subKey);
                if (res == RES_BOGUS) {
                    --depth;
                    break;
                }
                ICUResourceBundle sub;
                if (ICUResourceBundleReader.RES_GET_TYPE(res) == ALIAS) {
                    base.getResPathKeys(keys, baseDepth);
                    sub = getAliasedResource(base, keys, depth, subKey, res, null, requested);
                } else {
                    sub = null;
                }
                if (depth == keys.length) {
                    // We found it.
                    if (sub != null) {
                        return sub.getString();  // string from alias handling
                    } else {
                        String s = reader.getString(res);
                        if (s == null) {
                            throw new UResourceTypeMismatchException("");
                        }
                        return s;
                    }
                }
                if (sub != null) {
                    base = sub;
                    reader = base.wholeBundle.reader;
                    res = RES_BOGUS;
                    // If we followed an alias, then we may have switched bundle (locale) and key path.
                    // Reserve space for the lower parts of the path according to the last-found resource.
                    // This relies on a resource found via alias to have its original location information,
                    // rather than the location of the alias.
                    baseDepth = base.getResDepth();
                    if (depth != baseDepth) {
                        String[] newKeys = new String[baseDepth + (keys.length - depth)];
                        System.arraycopy(keys, depth, newKeys, baseDepth, keys.length - depth);
                        keys = newKeys;
                        depth = baseDepth;
                    }
                }
            }
            // Try the parent bundle of the last-found resource.
            ICUResourceBundle nextBase = base.getParent();
            if (nextBase == null) {
                return null;
            }
            // We probably have not yet set the lower parts of the key path.
            base.getResPathKeys(keys, baseDepth);
            base = nextBase;
            reader = base.wholeBundle.reader;
            depth = baseDepth = 0;  // getParent() returned a top level table resource.
        }
    }

    private int getResDepth() {
        return (container == null) ? 0 : container.getResDepth() + 1;
    }

    /**
     * Fills some of the keys array with the keys on the path to this resource object.
     * Writes the top-level key into index 0 and increments from there.
     *
     * @param keys
     * @param depth must be {@link #getResDepth()}
     */
    private void getResPathKeys(String[] keys, int depth) {
        ICUResourceBundle b = this;
        while (depth > 0) {
            keys[--depth] = b.key;
            b = b.container;
            assert (depth == 0) == (b.container == null);
        }
    }

    private static int countPathKeys(String path) {
        if (path.isEmpty()) {
            return 0;
        }
        int num = 1;
        for (int i = 0; i < path.length(); ++i) {
            if (path.charAt(i) == RES_PATH_SEP_CHAR) {
                ++num;
            }
        }
        return num;
    }

    /**
     * Fills some of the keys array (from start) with the num keys from the path string.
     *
     * @param path path string
     * @param num must be {@link #countPathKeys(String)}
     * @param keys
     * @param start index where the first path key is stored
     */
    private static void getResPathKeys(String path, int num, String[] keys, int start) {
        if (num == 0) {
            return;
        }
        if (num == 1) {
            keys[start] = path;
            return;
        }
        int i = 0;
        for (;;) {
            int j = path.indexOf(RES_PATH_SEP_CHAR, i);
            assert j >= i;
            keys[start++] = path.substring(i, j);
            if (num == 2) {
                assert path.indexOf(RES_PATH_SEP_CHAR, j + 1) < 0;
                keys[start] = path.substring(j + 1);
                break;
            } else {
                i = j + 1;
                --num;
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof ICUResourceBundle) {
            ICUResourceBundle o = (ICUResourceBundle) other;
            if (getBaseName().equals(o.getBaseName())
                    && getLocaleID().equals(o.getLocaleID())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        assert false : "hashCode not designed";
        return 42;
    }

    public enum OpenType {  // C++ uresbund.cpp: enum UResOpenType
        /**
         * Open a resource bundle for the locale;
         * if there is not even a base language bundle, then fall back to the default locale;
         * if there is no bundle for that either, then load the root bundle.
         *
         * <p>This is the default bundle loading behavior.
         */
        LOCALE_DEFAULT_ROOT,
        // TODO: ICU ticket #11271 "consistent default locale across locale trees"
        // Add an option to look at the main locale tree for whether to
        // fall back to root directly (if the locale has main data) or
        // fall back to the default locale first (if the locale does not even have main data).
        /**
         * Open a resource bundle for the locale;
         * if there is not even a base language bundle, then load the root bundle;
         * never fall back to the default locale.
         *
         * <p>This is used for algorithms that have good pan-Unicode default behavior,
         * such as case mappings, collation, and segmentation (BreakIterator).
         */
        LOCALE_ROOT,
        /**
         * Open a resource bundle for the locale;
         * if there is not even a base language bundle, then fail;
         * never fall back to the default locale nor to the root locale.
         *
         * <p>This is used when fallback to another language is not desired
         * and the root locale is not generally useful.
         * For example, {@link jdk.internal.icu.util.LocaleData#setNoSubstitute(boolean)}
         * or currency display names for {@link jdk.internal.icu.text.LocaleDisplayNames}.
         */
        LOCALE_ONLY,
        /**
         * Open a resource bundle for the exact bundle name as requested;
         * no fallbacks, do not load parent bundles.
         *
         * <p>This is used for supplemental (non-locale) data.
         */
        DIRECT
    };

    // This method is for super class's instantiateBundle method
    public static ICUResourceBundle getBundleInstance(String baseName, String localeID,
            ClassLoader root, boolean disableFallback) {
        return getBundleInstance(baseName, localeID, root,
                disableFallback ? OpenType.DIRECT : OpenType.LOCALE_DEFAULT_ROOT);
    }

    public static ICUResourceBundle getBundleInstance(
            String baseName, ULocale locale, OpenType openType) {
        if (locale == null) {
            locale = ULocale.getDefault();
        }
        return getBundleInstance(baseName, locale.getBaseName(),
                ICUResourceBundle.ICU_DATA_CLASS_LOADER, openType);
    }

    public static ICUResourceBundle getBundleInstance(String baseName, String localeID,
            ClassLoader root, OpenType openType) {
        if (baseName == null) {
            baseName = ICUData.ICU_BASE_NAME;
        }
        localeID = ULocale.getBaseName(localeID);
        ICUResourceBundle b;
        if (openType == OpenType.LOCALE_DEFAULT_ROOT) {
            b = instantiateBundle(baseName, localeID, null, ULocale.getDefault().getBaseName(),
                    root, openType);
        } else {
            b = instantiateBundle(baseName, localeID, null, null, root, openType);
        }
        if(b==null){
            throw new MissingResourceException(
                    "Could not find the bundle "+ baseName+"/"+ localeID+".res","","");
        }
        return b;
    }

    private static boolean localeIDStartsWithLangSubtag(String localeID, String lang) {
        return localeID.startsWith(lang) &&
                (localeID.length() == lang.length() || localeID.charAt(lang.length()) == '_');
    }

    private static final Comparator<String[]> COMPARE_FIRST_ELEMENT = new Comparator<String[]>() {
        @Override
        public int compare(String[] pair1, String[] pair2) {
            return pair1[0].compareTo(pair2[0]);
        }
    };

    private static String getExplicitParent(String localeID) {
        return LocaleFallbackData.PARENT_LOCALE_TABLE.get(localeID);
    }

    private static String getDefaultScript(String language, String region) {
        String localeID = language + "_" + region;
        String result = LocaleFallbackData.DEFAULT_SCRIPT_TABLE.get(localeID);
        if (result == null) {
            result = LocaleFallbackData.DEFAULT_SCRIPT_TABLE.get(language);
        }
        if (result == null) {
            result = "Latn";
        }
        return result;
    }

    private static String getParentLocaleID(String name, String origName, OpenType openType) {
        // early out if the locale ID has a variant code or ends with _
        if (name.endsWith("_") || !ULocale.getVariant(name).isEmpty()) {
            int lastUnderbarPos = name.lastIndexOf('_');
            if (lastUnderbarPos >= 0) {
                return name.substring(0, lastUnderbarPos);
            } else {
                return null;
            }
        }

        // TODO: Is there a better way to break the locale ID up into its consituent parts?
        ULocale nameLocale = new ULocale(name);
        String language = nameLocale.getLanguage();
        String script = nameLocale.getScript();
        String region = nameLocale.getCountry();

        // if our open type is LOCALE_DEFAULT_ROOT, first look the locale ID up in the parent locale table; if that
        // table specifies a parent for it, return that (we don't do this for the other open types-- if we're not
        // falling back through the system default locale, we also want to do straight truncation fallback instead
        // of looking things up in the parent locale table-- see https://www.unicode.org/reports/tr35/tr35.html#Parent_Locales:
        // "Collation data, however, is an exception...")
        if (openType == OpenType.LOCALE_DEFAULT_ROOT) {
            String parentID = getExplicitParent(name);
            if (parentID != null) {
                return parentID.equals("root") ? null : parentID;
            }
        }

        // if it's not in the parent locale table, figure out the fallback script algorithmically
        // (see CLDR-15265 for an explanation of the algorithm)
        if (!script.isEmpty() && !region.isEmpty()) {
            // if "name" has both script and region, is the script the default script?
            // - if so, remove it and keep the region
            // - if not, remove the region and keep the script
            if (getDefaultScript(language, region).equals(script)) {
                return language + "_" + region;
            } else {
                return language + "_" + script;
            }
        } else if (!region.isEmpty()) {
            // if "name" has region but not script, did the original locale ID specify a script?
            // - if yes, replace the region with the script from the original locale ID
            // - if no, replace the region with the default script for that language and region
            String origNameScript = ULocale.getScript(origName);
            if (!origNameScript.isEmpty()) {
                return language + "_" + origNameScript;
            } else {
                return language + "_" + getDefaultScript(language, region);
            }
        } else if (!script.isEmpty()) {
            // if "name" has script but not region (and our open type is LOCALE_DEFAULT_ROOT), is the script the
            // default script for the language?
            // - if so, remove it from the locale ID
            // - if not, return "root" (bypassing the system default locale ID)
            // (we don't do this for other open types for the same reason we don't look things up in the parent
            // locale table for other open types-- see the reference to UTS #35 above)
            if (openType != OpenType.LOCALE_DEFAULT_ROOT || getDefaultScript(language, null).equals(script)) {
                return language;
            } else {
                return /*"root"*/null;
            }
        } else {
            // if "name" just contains a language code, return null so the calling code falls back to "root"
            return null;
        }
    }

    private static ICUResourceBundle instantiateBundle(
            final String baseName, final String localeID, final String origLocaleID, final String defaultID,
            final ClassLoader root, final OpenType openType) {
        assert localeID.indexOf('@') < 0;
        assert defaultID == null || defaultID.indexOf('@') < 0;
        final String fullName = ICUResourceBundleReader.getFullName(baseName, localeID);
        char openTypeChar = (char)('0' + openType.ordinal());
        String cacheKey = openType != OpenType.LOCALE_DEFAULT_ROOT ?
                fullName + '#' + openTypeChar :
                    fullName + '#' + openTypeChar + '#' + defaultID;
        return BUNDLE_CACHE.getInstance(cacheKey, new Loader() {
                @Override
                public ICUResourceBundle load() {
            if(DEBUG) System.out.println("Creating "+fullName);
            // here we assume that java type resource bundle organization
            // is required then the base name contains '.' else
            // the resource organization is of ICU type
            // so clients can instantiate resources of the type
            // com.mycompany.data.MyLocaleElements_en.res and
            // com.mycompany.data.MyLocaleElements.res
            //
            final String rootLocale = (baseName.indexOf('.')==-1) ? "root" : "";
            String localeName = localeID.isEmpty() ? rootLocale : localeID;
            ICUResourceBundle b = ICUResourceBundle.createBundle(baseName, localeName, root);

            if(DEBUG)System.out.println("The bundle created is: "+b+" and openType="+openType+" and bundle.getNoFallback="+(b!=null && b.getNoFallback()));
            if (openType == OpenType.DIRECT || (b != null && b.getNoFallback())) {
                // no fallback because the caller said so or because the bundle says so
                //
                // TODO for b!=null: In C++, ures_openDirect() builds the parent chain
                // for its bundle unless its nofallback flag is set.
                // Otherwise we get test failures.
                // For example, item aliases are followed via ures_openDirect(),
                // and fail if the target bundle needs fallbacks but the chain is not set.
                // Figure out why Java does not build the parent chain
                // for a bundle that does not have nofallback.
                // Are the relevant test cases just disabled?
                // Do item aliases not get followed via "direct" loading?
                return b;
            }

            // fallback to locale ID parent
            if(b == null){
                OpenType localOpenType = openType;
                if (openType == OpenType.LOCALE_DEFAULT_ROOT && localeName.equals(defaultID)) {
                    localOpenType = OpenType.LOCALE_ROOT;
                }
                String origLocaleName = (origLocaleID != null) ? origLocaleID : localeName;
                String fallbackLocaleID = getParentLocaleID(localeName, origLocaleName, openType);
                if (fallbackLocaleID != null) {
                    b = instantiateBundle(baseName, fallbackLocaleID, origLocaleName, defaultID, root, localOpenType);
                }else{
                    if(localOpenType == OpenType.LOCALE_DEFAULT_ROOT &&
                            !localeIDStartsWithLangSubtag(defaultID, localeName)) {
                        // Go to the default locale before root.
                        b = instantiateBundle(baseName, defaultID, null, defaultID, root, localOpenType);
                    } else if(localOpenType != OpenType.LOCALE_ONLY && !rootLocale.isEmpty()) {
                        // Ultimately go to root.
                        b = ICUResourceBundle.createBundle(baseName, rootLocale, root);
                    }
                }
            }else{
                UResourceBundle parent = null;
                localeName = b.getLocaleID();
                int i = localeName.lastIndexOf('_');

                // TODO: C++ uresbund.cpp also checks for %%ParentIsRoot. Why not Java?
                String parentLocaleName = ((ICUResourceBundleImpl.ResourceTable)b).findString("%%Parent");
                if (parentLocaleName != null) {
                    parent = instantiateBundle(baseName, parentLocaleName, null, defaultID, root, openType);
                } else if (i != -1) {
                    parent = instantiateBundle(baseName, localeName.substring(0, i), null, defaultID, root, openType);
                } else if (!localeName.equals(rootLocale)){
                    parent = instantiateBundle(baseName, rootLocale, null, defaultID, root, openType);
                }

                if (!b.equals(parent)){
                    b.setParent(parent);
                }
            }
            return b;
        }});
    }

    ICUResourceBundle get(String aKey, HashMap<String, String> aliasesVisited, UResourceBundle requested) {
        ICUResourceBundle obj = (ICUResourceBundle)handleGet(aKey, aliasesVisited, requested);
        if (obj == null) {
            obj = getParent();
            if (obj != null) {
                //call the get method to recursively fetch the resource
                obj = obj.get(aKey, aliasesVisited, requested);
            }
            if (obj == null) {
                String fullName = ICUResourceBundleReader.getFullName(getBaseName(), getLocaleID());
                throw new MissingResourceException(
                        "Can't find resource for bundle " + fullName + ", key "
                                + aKey, this.getClass().getName(), aKey);
            }
        }
        return obj;
    }

    /** Data member where the subclasses store the key. */
    protected String key;

    /**
     * A resource word value that means "no resource".
     * Note: 0xffffffff == -1
     * This has the same value as UResourceBundle.NONE, but they are semantically
     * different and should be used appropriately according to context:
     * NONE means "no type".
     * (The type of RES_BOGUS is RES_RESERVED=15 which was defined in ICU4C ures.h.)
     */
    public static final int RES_BOGUS = 0xffffffff;
    //blic static final int RES_MAX_OFFSET = 0x0fffffff;

    /**
     * Resource type constant for aliases;
     * internally stores a string which identifies the actual resource
     * storing the data (can be in a different resource bundle).
     * Resolved internally before delivering the actual resource through the API.
     */
    public static final int ALIAS = 3;

    /** Resource type constant for tables with 32-bit count, key offsets and values. */
    public static final int TABLE32 = 4;

    /**
     * Resource type constant for tables with 16-bit count, key offsets and values.
     * All values are STRING_V2 strings.
     */
    public static final int TABLE16 = 5;

    /** Resource type constant for 16-bit Unicode strings in formatVersion 2. */
    public static final int STRING_V2 = 6;

    /**
     * Resource type constant for arrays with 16-bit count and values.
     * All values are STRING_V2 strings.
     */
    public static final int ARRAY16 = 9;

    /* Resource type 15 is not defined but effectively used by RES_BOGUS=0xffffffff. */

    /**
    * Create a bundle using a reader.
    * @param baseName The name for the bundle.
    * @param localeID The locale identification.
    * @param root The ClassLoader object root.
    * @return the new bundle
    */
    public static ICUResourceBundle createBundle(String baseName, String localeID, ClassLoader root) {
        ICUResourceBundleReader reader = ICUResourceBundleReader.getReader(baseName, localeID, root);
        if (reader == null) {
            // could not open the .res file
            return null;
        }
        return getBundle(reader, baseName, localeID, root);
    }

    @Override
    protected String getLocaleID() {
        return wholeBundle.localeID;
    }

    @Override
    protected String getBaseName() {
        return wholeBundle.baseName;
    }

    @Override
    public ULocale getULocale() {
        return wholeBundle.ulocale;
    }

    /**
     * Returns true if this is the root bundle, or an item in the root bundle.
     */
    public boolean isRoot() {
        return wholeBundle.localeID.isEmpty() || wholeBundle.localeID.equals("root");
    }

    @Override
    public ICUResourceBundle getParent() {
        return (ICUResourceBundle) parent;
    }

    @Override
    protected void setParent(ResourceBundle parent) {
        this.parent = parent;
    }

    @Override
    public String getKey() {
        return key;
    }

    /**
     * Get the noFallback flag specified in the loaded bundle.
     * @return The noFallback flag.
     */
    private boolean getNoFallback() {
        return wholeBundle.reader.getNoFallback();
    }

    private static ICUResourceBundle getBundle(ICUResourceBundleReader reader,
                                               String baseName, String localeID,
                                               ClassLoader loader) {
        ICUResourceBundleImpl.ResourceTable rootTable;
        int rootRes = reader.getRootResource();
        if(ICUResourceBundleReader.URES_IS_TABLE(ICUResourceBundleReader.RES_GET_TYPE(rootRes))) {
            WholeBundle wb = new WholeBundle(baseName, localeID, loader, reader);
            rootTable = new ICUResourceBundleImpl.ResourceTable(wb, rootRes);
        } else {
            throw new IllegalStateException("Invalid format error");
        }
        String aliasString = rootTable.findString("%%ALIAS");
        if(aliasString != null) {
            return (ICUResourceBundle)UResourceBundle.getBundleInstance(baseName, aliasString);
        } else {
            return rootTable;
        }
    }
    /**
     * Constructor for the root table of a bundle.
     */
    protected ICUResourceBundle(WholeBundle wholeBundle) {
        this.wholeBundle = wholeBundle;
    }
    // constructor for inner classes
    protected ICUResourceBundle(ICUResourceBundle container, String key) {
        this.key = key;
        wholeBundle = container.wholeBundle;
        this.container = container;
        parent = container.parent;
    }

    private static final char RES_PATH_SEP_CHAR = '/';
    private static final String RES_PATH_SEP_STR = "/";
    private static final String ICUDATA = "ICUDATA";
    private static final char HYPHEN = '-';
    private static final String LOCALE = "LOCALE";

    /**
     * Returns the resource object referred to from the alias _resource int's path string.
     * Throws MissingResourceException if not found.
     *
     * If the alias path does not contain a key path:
     * If keys != null then keys[:depth] is used.
     * Otherwise the base key path plus the key parameter is used.
     *
     * @param base A direct or indirect container of the alias.
     * @param keys The key path to the alias, or null. (const)
     * @param depth The length of the key path, if keys != null.
     * @param key The alias' own key within this current container, if keys == null.
     * @param _resource The alias resource int.
     * @param aliasesVisited Set of alias path strings already visited, for detecting loops.
     *        We cannot change the type (e.g., to Set<String>) because it is used
     *        in protected/@stable UResourceBundle methods.
     * @param requested The original resource object from which the lookup started,
     *        which is the starting point for "/LOCALE/..." aliases.
     * @return the aliased resource object
     */
    protected static ICUResourceBundle getAliasedResource(
            ICUResourceBundle base, String[] keys, int depth,
            String key, int _resource,
            HashMap<String, String> aliasesVisited,
            UResourceBundle requested) {
        WholeBundle wholeBundle = base.wholeBundle;
        ClassLoader loaderToUse = wholeBundle.loader;
        String rpath = wholeBundle.reader.getAlias(_resource);
        String baseName = wholeBundle.baseName;

        // TODO: We need not build the baseKeyPath array if the rpath includes a keyPath
        // (except for an exception message string).
        // Try to avoid unnecessary work+allocation.
        int baseDepth = base.getResDepth();
        String[] baseKeyPath = new String[baseDepth + 1];
        base.getResPathKeys(baseKeyPath, baseDepth);
        baseKeyPath[baseDepth] = key;
        return getAliasedResource(rpath, loaderToUse, baseName, keys, depth, baseKeyPath, aliasesVisited, requested);
    }

    protected static ICUResourceBundle getAliasedResource(
            String rpath, ClassLoader loaderToUse, String baseName,
            String[] keys, int depth, String[] baseKeyPath,
            HashMap<String, String> aliasesVisited,
            UResourceBundle requested) {
        String locale;
        String keyPath = null;
        String bundleName;
        if (aliasesVisited == null) {
            aliasesVisited = new HashMap<>();
        }
        if (aliasesVisited.get(rpath) != null) {
            throw new IllegalArgumentException(
                    "Circular references in the resource bundles");
        }
        aliasesVisited.put(rpath, "");
        if (rpath.indexOf(RES_PATH_SEP_CHAR) == 0) {
            int i = rpath.indexOf(RES_PATH_SEP_CHAR, 1);
            int j = rpath.indexOf(RES_PATH_SEP_CHAR, i + 1);
            bundleName = rpath.substring(1, i);
            if (j < 0) {
                locale = rpath.substring(i + 1);
            } else {
                locale = rpath.substring(i + 1, j);
                keyPath = rpath.substring(j + 1, rpath.length());
            }
            //there is a path included
            if (bundleName.equals(ICUDATA)) {
                bundleName = ICUData.ICU_BASE_NAME;
                loaderToUse = ICU_DATA_CLASS_LOADER;
            }else if(bundleName.indexOf(ICUDATA)>-1){
                int idx = bundleName.indexOf(HYPHEN);
                if(idx>-1){
                    bundleName = ICUData.ICU_BASE_NAME+RES_PATH_SEP_STR+bundleName.substring(idx+1,bundleName.length());
                    loaderToUse = ICU_DATA_CLASS_LOADER;
                }
            }
        } else {
            //no path start with locale
            int i = rpath.indexOf(RES_PATH_SEP_CHAR);
            if (i != -1) {
                locale = rpath.substring(0, i);
                keyPath = rpath.substring(i + 1);
            } else {
                locale = rpath;
            }
            bundleName = baseName;
        }
        ICUResourceBundle bundle = null;
        ICUResourceBundle sub = null;
        if(bundleName.equals(LOCALE)){
            bundleName = baseName;
            keyPath = rpath.substring(LOCALE.length() + 2/* prepending and appending / */, rpath.length());

            // Get the top bundle of the requested bundle
            bundle = (ICUResourceBundle)requested;
            while (bundle.container != null) {
                bundle = bundle.container;
            }
            sub = ICUResourceBundle.findResourceWithFallback(keyPath, bundle, null);
        }else{
            bundle = getBundleInstance(bundleName, locale, loaderToUse, false);

            int numKeys;
            if (keyPath != null) {
                numKeys = countPathKeys(keyPath);
                if (numKeys > 0) {
                    keys = new String[numKeys];
                    getResPathKeys(keyPath, numKeys, keys, 0);
                }
            } else if (keys != null) {
                numKeys = depth;
            } else {
                keys = baseKeyPath;
                numKeys = baseKeyPath.length;
            }
            if (numKeys > 0) {
                sub = bundle;
                for (int i = 0; sub != null && i < numKeys; ++i) {
                    sub = sub.get(keys[i], aliasesVisited, requested);
                }
            }
        }
        if (sub == null) {
            throw new MissingResourceException(locale, baseName, baseKeyPath[baseKeyPath.length - 1]);
        }
        // TODO: If we know that sub is not cached,
        // then we should set its container and key to the alias' location,
        // so that it behaves as if its value had been copied into the alias location.
        // However, findResourceWithFallback() must reroute its bundle and key path
        // to where the alias data comes from.
        return sub;
    }

    /**
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    public final Set<String> getTopLevelKeySet() {
        return wholeBundle.topLevelKeys;
    }

    /**
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    public final void setTopLevelKeySet(Set<String> keySet) {
        wholeBundle.topLevelKeys = keySet;
    }

    // This is the worker function for the public getKeys().
    // TODO: Now that UResourceBundle uses handleKeySet(), this function is obsolete.
    // It is also not inherited from ResourceBundle, and it is not implemented
    // by ResourceBundleWrapper despite its documentation requiring all subclasses to
    // implement it.
    // Consider deprecating UResourceBundle.handleGetKeys(), and consider making it always return null.
    @Override
    protected Enumeration<String> handleGetKeys() {
        return Collections.enumeration(handleKeySet());
    }

    @Override
    protected boolean isTopLevelResource() {
        return container == null;
    }
}
