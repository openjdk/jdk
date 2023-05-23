// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2004-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */

package jdk.internal.icu.util;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import jdk.internal.icu.impl.ICUData;
import jdk.internal.icu.impl.ICUResourceBundle;
import jdk.internal.icu.impl.ICUResourceBundleReader;
import jdk.internal.icu.impl.ResourceBundleWrapper;

/**
 * {@icuenhanced java.util.ResourceBundle}.{@icu _usage_}
 *
 * <p>A class representing a collection of resource information pertaining to a given
 * locale. A resource bundle provides a way of accessing locale- specific information in a
 * data file. You create a resource bundle that manages the resources for a given locale
 * and then ask it for individual resources.
 *
 * <p>In ResourceBundle, an object is created and the sub-items are fetched using the
 * getString and getObject methods.  In UResourceBundle, each individual element of a
 * resource is a resource by itself.
 *
 * <p>Resource bundles in ICU are currently defined using text files that conform to the
 * following <a
 * href="https://github.com/unicode-org/icu-docs/blob/main/design/bnf_rb.txt">BNF
 * definition</a>.  More on resource bundle concepts and syntax can be found in the <a
 * href="https://unicode-org.github.io/icu/userguide/locale/resources">Users Guide</a>.
 *
 * <p>The packaging of ICU *.res files can be of two types
 * ICU4C:
 * <pre>
 *       root.res
 *         |
 *      --------
 *     |        |
 *   fr.res  en.res
 *     |
 *   --------
 *  |        |
 * fr_CA.res fr_FR.res
 * </pre>
 * JAVA/JDK:
 * <pre>
 *    LocaleElements.res
 *         |
 *      -------------------
 *     |                   |
 * LocaleElements_fr.res  LocaleElements_en.res
 *     |
 *   ---------------------------
 *  |                            |
 * LocaleElements_fr_CA.res   LocaleElements_fr_FR.res
 * </pre>
 *
 * Depending on the organization of your resources, the syntax to getBundleInstance will
 * change.  To open ICU style organization use:
 *
 * <pre>
 *      UResourceBundle bundle =
 *          UResourceBundle.getBundleInstance("com/mycompany/resources",
 *                                            "en_US", myClassLoader);
 * </pre>
 * To open Java/JDK style organization use:
 * <pre>
 *      UResourceBundle bundle =
 *          UResourceBundle.getBundleInstance("com.mycompany.resources.LocaleElements",
 *                                            "en_US", myClassLoader);
 * </pre>
 *
 * <p>Note: Please use pass a class loader for loading non-ICU resources. Java security does not
 * allow loading of resources across jar files. You must provide your class loader
 * to load the resources

 * @stable ICU 3.0
 * @author ram
 */
public abstract class UResourceBundle extends ResourceBundle {


    /**
     * {@icu} Creates a resource bundle using the specified base name and locale.
     * ICU_DATA_CLASS is used as the default root.
     * @param baseName string containing the name of the data package.
     *                    If null the default ICU package name is used.
     * @param localeName the locale for which a resource bundle is desired
     * @throws MissingResourceException If no resource bundle for the specified base name
     * can be found
     * @return a resource bundle for the given base name and locale
     * @stable ICU 3.0
     */
    public static UResourceBundle getBundleInstance(String baseName, String localeName){
        return getBundleInstance(baseName, localeName, ICUResourceBundle.ICU_DATA_CLASS_LOADER,
                                 false);
    }

    /**
     * {@icu} Creates a resource bundle using the specified base name, locale, and class root.
     *
     * @param baseName string containing the name of the data package.
     *                    If null the default ICU package name is used.
     * @param localeName the locale for which a resource bundle is desired
     * @param root the class object from which to load the resource bundle
     * @throws MissingResourceException If no resource bundle for the specified base name
     * can be found
     * @return a resource bundle for the given base name and locale
     * @stable ICU 3.0
     */
    public static UResourceBundle getBundleInstance(String baseName, String localeName,
                                                    ClassLoader root){
        return getBundleInstance(baseName, localeName, root, false);
    }

    /**
     * {@icu} Creates a resource bundle using the specified base name, locale, and class
     * root.
     *
     * @param baseName string containing the name of the data package.
     *                    If null the default ICU package name is used.
     * @param localeName the locale for which a resource bundle is desired
     * @param root the class object from which to load the resource bundle
     * @param disableFallback Option to disable locale inheritance.
     *                          If true the fallback chain will not be built.
     * @throws MissingResourceException
     *     if no resource bundle for the specified base name can be found
     * @return a resource bundle for the given base name and locale
     * @stable ICU 3.0
     *
     */
    protected static UResourceBundle getBundleInstance(String baseName, String localeName,
                                                       ClassLoader root, boolean disableFallback) {
        return instantiateBundle(baseName, localeName, root, disableFallback);
    }

    /**
     * {@icu} Sole constructor.  (For invocation by subclass constructors, typically
     * implicit.)  This is public for compatibility with Java, whose compiler
     * will generate public default constructors for an abstract class.
     * @stable ICU 3.0
     */
    public UResourceBundle() {
    }

    /**
     * {@icu} Creates a UResourceBundle for the locale specified, from which users can extract
     * resources by using their corresponding keys.
     * @param locale  specifies the locale for which we want to open the resource.
     *                If null the bundle for default locale is opened.
     * @return a resource bundle for the given locale
     * @stable ICU 3.0
     */
    public static UResourceBundle getBundleInstance(ULocale locale) {
        if (locale==null) {
            locale = ULocale.getDefault();
        }
        return getBundleInstance(ICUData.ICU_BASE_NAME, locale.getBaseName(),
                                 ICUResourceBundle.ICU_DATA_CLASS_LOADER, false);
    }

    /**
     * {@icu} Creates a UResourceBundle for the default locale and specified base name,
     * from which users can extract resources by using their corresponding keys.
     * @param baseName string containing the name of the data package.
     *                    If null the default ICU package name is used.
     * @return a resource bundle for the given base name and default locale
     * @stable ICU 3.0
     */
    public static UResourceBundle getBundleInstance(String baseName) {
        if (baseName == null) {
            baseName = ICUData.ICU_BASE_NAME;
        }
        ULocale uloc = ULocale.getDefault();
        return getBundleInstance(baseName, uloc.getBaseName(), ICUResourceBundle.ICU_DATA_CLASS_LOADER,
                                 false);
    }

    /**
     * {@icu} Creates a UResourceBundle for the specified locale and specified base name,
     * from which users can extract resources by using their corresponding keys.
     * @param baseName string containing the name of the data package.
     *                    If null the default ICU package name is used.
     * @param locale  specifies the locale for which we want to open the resource.
     *                If null the bundle for default locale is opened.
     * @return a resource bundle for the given base name and locale
     * @stable ICU 3.0
     */

    public static UResourceBundle getBundleInstance(String baseName, Locale locale) {
        if (baseName == null) {
            baseName = ICUData.ICU_BASE_NAME;
        }
        ULocale uloc = locale == null ? ULocale.getDefault() : ULocale.forLocale(locale);

        return getBundleInstance(baseName, uloc.getBaseName(),
                                 ICUResourceBundle.ICU_DATA_CLASS_LOADER, false);
    }

    /**
     * {@icu} Creates a UResourceBundle, from which users can extract resources by using
     * their corresponding keys.
     * @param baseName string containing the name of the data package.
     *                    If null the default ICU package name is used.
     * @param locale  specifies the locale for which we want to open the resource.
     *                If null the bundle for default locale is opened.
     * @return a resource bundle for the given base name and locale
     * @stable ICU 3.0
     */
    public static UResourceBundle getBundleInstance(String baseName, ULocale locale) {
        if (baseName == null) {
            baseName = ICUData.ICU_BASE_NAME;
        }
        if (locale == null) {
            locale = ULocale.getDefault();
        }
        return getBundleInstance(baseName, locale.getBaseName(),
                                 ICUResourceBundle.ICU_DATA_CLASS_LOADER, false);
    }

    /**
     * {@icu} Creates a UResourceBundle for the specified locale and specified base name,
     * from which users can extract resources by using their corresponding keys.
     * @param baseName string containing the name of the data package.
     *                    If null the default ICU package name is used.
     * @param locale  specifies the locale for which we want to open the resource.
     *                If null the bundle for default locale is opened.
     * @param loader  the loader to use
     * @return a resource bundle for the given base name and locale
     * @stable ICU 3.8
     */
    public static UResourceBundle getBundleInstance(String baseName, Locale locale,
                                                    ClassLoader loader) {
        if (baseName == null) {
            baseName = ICUData.ICU_BASE_NAME;
        }
        ULocale uloc = locale == null ? ULocale.getDefault() : ULocale.forLocale(locale);
        return getBundleInstance(baseName, uloc.getBaseName(), loader, false);
    }

    /**
     * {@icu} Creates a UResourceBundle, from which users can extract resources by using
     * their corresponding keys.<br><br>
     * Note: Please use this API for loading non-ICU resources. Java security does not
     * allow loading of resources across jar files. You must provide your class loader
     * to load the resources
     * @param baseName string containing the name of the data package.
     *                    If null the default ICU package name is used.
     * @param locale  specifies the locale for which we want to open the resource.
     *                If null the bundle for default locale is opened.
     * @param loader  the loader to use
     * @return a resource bundle for the given base name and locale
     * @stable ICU 3.8
     */
    public static UResourceBundle getBundleInstance(String baseName, ULocale locale,
                                                    ClassLoader loader) {
        if (baseName == null) {
            baseName = ICUData.ICU_BASE_NAME;
        }
        if (locale == null) {
            locale = ULocale.getDefault();
        }
        return getBundleInstance(baseName, locale.getBaseName(), loader, false);
    }

    /**
     * {@icu} Returns the RFC 3066 conformant locale id of this resource bundle.
     * This method can be used after a call to getBundleInstance() to
     * determine whether the resource bundle returned really
     * corresponds to the requested locale or is a fallback.
     *
     * @return the locale of this resource bundle
     * @stable ICU 3.0
     */
    public abstract ULocale getULocale();

    /**
     * {@icu} Returns the localeID
     * @return The string representation of the localeID
     * @stable ICU 3.0
     */
    protected abstract String getLocaleID();

    /**
     * {@icu} Returns the base name of the resource bundle
     * @return The string representation of the base name
     * @stable ICU 3.0
     */
    protected abstract String getBaseName();

    /**
     * {@icu} Returns the parent bundle
     * @return The parent bundle
     * @stable ICU 3.0
     */
    protected abstract UResourceBundle getParent();


    /**
     * Returns the locale of this bundle
     * @return the locale of this resource bundle
     * @stable ICU 3.0
     */
    @Override
    public Locale getLocale(){
        return getULocale().toLocale();
    }

    private enum RootType { MISSING, ICU, JAVA }

    private static Map<String, RootType> ROOT_CACHE = new ConcurrentHashMap<String, RootType>();

    private static RootType getRootType(String baseName, ClassLoader root) {
        RootType rootType = ROOT_CACHE.get(baseName);

        if (rootType == null) {
            String rootLocale = (baseName.indexOf('.')==-1) ? "root" : "";
            try{
                ICUResourceBundle.getBundleInstance(baseName, rootLocale, root, true);
                rootType = RootType.ICU;
            }catch(MissingResourceException ex){
                try{
                    ResourceBundleWrapper.getBundleInstance(baseName, rootLocale, root, true);
                    rootType = RootType.JAVA;
                }catch(MissingResourceException e){
                    //throw away the exception
                    rootType = RootType.MISSING;
                }
            }

            ROOT_CACHE.put(baseName, rootType);
        }

        return rootType;
    }

    private static void setRootType(String baseName, RootType rootType) {
        ROOT_CACHE.put(baseName, rootType);
    }

    /**
     * {@icu} Loads a new resource bundle for the given base name, locale and class loader.
     * Optionally will disable loading of fallback bundles.
     * @param baseName string containing the name of the data package.
     *                    If null the default ICU package name is used.
     * @param localeName the locale for which a resource bundle is desired
     * @param root the class object from which to load the resource bundle
     * @param disableFallback disables loading of fallback lookup chain
     * @throws MissingResourceException If no resource bundle for the specified base name
     * can be found
     * @return a resource bundle for the given base name and locale
     * @stable ICU 3.0
     */
    protected static UResourceBundle instantiateBundle(String baseName, String localeName,
                                                       ClassLoader root, boolean disableFallback) {
        RootType rootType = getRootType(baseName, root);

        switch (rootType) {
        case ICU:
            return ICUResourceBundle.getBundleInstance(baseName, localeName, root, disableFallback);

        case JAVA:
            return ResourceBundleWrapper.getBundleInstance(baseName, localeName, root,
                                                           disableFallback);

        case MISSING:
        default:
            UResourceBundle b;
            try{
                b = ICUResourceBundle.getBundleInstance(baseName, localeName, root,
                                                        disableFallback);
                setRootType(baseName, RootType.ICU);
            }catch(MissingResourceException ex){
                b = ResourceBundleWrapper.getBundleInstance(baseName, localeName, root,
                                                            disableFallback);
                setRootType(baseName, RootType.JAVA);
            }
            return b;
        }
    }

    /**
     * {@icu} Returns a binary data item from a binary resource, as a read-only ByteBuffer.
     *
     * @return a pointer to a chunk of unsigned bytes which live in a memory mapped/DLL
     * file.
     * @see #getIntVector
     * @see #getInt
     * @throws MissingResourceException If no resource bundle can be found.
     * @throws UResourceTypeMismatchException If the resource has a type mismatch.
     * @stable ICU 3.8
     */
    public ByteBuffer getBinary() {
        throw new UResourceTypeMismatchException("");
    }

    /**
     * Returns a string from a string resource type
     *
     * @return a string
     * @see #getBinary()
     * @see #getIntVector
     * @see #getInt
     * @throws MissingResourceException If resource bundle is missing.
     * @throws UResourceTypeMismatchException If resource bundle has a type mismatch.
     * @stable ICU 3.8
     */
    public String getString() {
        throw new UResourceTypeMismatchException("");
    }

    /**
     * Returns a string array from a array resource type
     *
     * @return a string
     * @see #getString()
     * @see #getIntVector
     * @throws MissingResourceException If resource bundle is missing.
     * @throws UResourceTypeMismatchException If resource bundle has a type mismatch.
     * @stable ICU 3.8
     */
    public String[] getStringArray() {
        throw new UResourceTypeMismatchException("");
    }

    /**
     * {@icu} Returns a binary data from a binary resource, as a byte array with a copy
     * of the bytes from the resource bundle.
     *
     * @param ba  The byte array to write the bytes to. A null variable is OK.
     * @return an array of bytes containing the binary data from the resource.
     * @see #getIntVector
     * @see #getInt
     * @throws MissingResourceException If resource bundle is missing.
     * @throws UResourceTypeMismatchException If resource bundle has a type mismatch.
     * @stable ICU 3.8
     */
    public byte[] getBinary(byte[] ba) {
        throw new UResourceTypeMismatchException("");
    }

    /**
     * {@icu} Returns a 32 bit integer array from a resource.
     *
     * @return a pointer to a chunk of unsigned bytes which live in a memory mapped/DLL file.
     * @see #getBinary()
     * @see #getInt
     * @throws MissingResourceException If resource bundle is missing.
     * @throws UResourceTypeMismatchException If resource bundle has a type mismatch.
     * @stable ICU 3.8
     */
    public int[] getIntVector() {
        throw new UResourceTypeMismatchException("");
    }

    /**
     * {@icu} Returns a signed integer from a resource.
     *
     * @return an integer value
     * @see #getIntVector
     * @see #getBinary()
     * @throws MissingResourceException If resource bundle is missing.
     * @throws UResourceTypeMismatchException If resource bundle type mismatch.
     * @stable ICU 3.8
     */
    public int getInt() {
        throw new UResourceTypeMismatchException("");
    }

    /**
     * {@icu} Returns a unsigned integer from a resource.
     * This integer is originally 28 bit and the sign gets propagated.
     *
     * @return an integer value
     * @see #getIntVector
     * @see #getBinary()
     * @throws MissingResourceException If resource bundle is missing.
     * @throws UResourceTypeMismatchException If resource bundle type mismatch.
     * @stable ICU 3.8
     */
    public int getUInt() {
        throw new UResourceTypeMismatchException("");
    }

    /**
     * {@icu} Returns a resource in a given resource that has a given key.
     *
     * @param aKey               a key associated with the wanted resource
     * @return                  a resource bundle object representing the resource
     * @throws MissingResourceException If resource bundle is missing.
     * @stable ICU 3.8
     */
    public UResourceBundle get(String aKey) {
        UResourceBundle obj = findTopLevel(aKey);
        if (obj == null) {
            String fullName = ICUResourceBundleReader.getFullName(getBaseName(), getLocaleID());
            throw new MissingResourceException(
                    "Can't find resource for bundle " + fullName + ", key "
                    + aKey, this.getClass().getName(), aKey);
        }
        return obj;
    }

    /**
     * Returns a resource in a given resource that has a given key, or null if the
     * resource is not found.
     *
     * @param aKey the key associated with the wanted resource
     * @return the resource, or null
     * @see #get(String)
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    protected UResourceBundle findTopLevel(String aKey) {
        // NOTE: this only works for top-level resources.  For resources at lower
        // levels, it fails when you fall back to the parent, since you're now
        // looking at root resources, not at the corresponding nested resource.
        for (UResourceBundle res = this; res != null; res = res.getParent()) {
            UResourceBundle obj = res.handleGet(aKey, null, this);
            if (obj != null) {
                return obj;
            }
        }
        return null;
    }

    /**
     * Returns the string in a given resource at the specified index.
     *
     * @param index an index to the wanted string.
     * @return a string which lives in the resource.
     * @throws IndexOutOfBoundsException If the index value is out of bounds of accepted values.
     * @throws UResourceTypeMismatchException If resource bundle type mismatch.
     * @stable ICU 3.8
     */
    public String getString(int index) {
        ICUResourceBundle temp = (ICUResourceBundle)get(index);
        if (temp.getType() == STRING) {
            return temp.getString();
        }
        throw new UResourceTypeMismatchException("");
    }

    /**
     * {@icu} Returns the resource in a given resource at the specified index.
     *
     * @param index an index to the wanted resource.
     * @return the sub resource UResourceBundle object
     * @throws IndexOutOfBoundsException If the index value is out of bounds of accepted values.
     * @throws MissingResourceException If the resource bundle is missing.
     * @stable ICU 3.8
     */
    public UResourceBundle get(int index) {
        UResourceBundle obj = handleGet(index, null, this);
        if (obj == null) {
            obj = getParent();
            if (obj != null) {
                obj = obj.get(index);
            }
            if (obj == null)
                throw new MissingResourceException(
                        "Can't find resource for bundle "
                                + this.getClass().getName() + ", key "
                                + getKey(), this.getClass().getName(), getKey());
        }
        return obj;
    }

    /**
     * Returns a resource in a given resource that has a given index, or null if the
     * resource is not found.
     *
     * @param index the index of the resource
     * @return the resource, or null
     * @see #get(int)
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    protected UResourceBundle findTopLevel(int index) {
        // NOTE: this _barely_ works for top-level resources.  For resources at lower
        // levels, it fails when you fall back to the parent, since you're now
        // looking at root resources, not at the corresponding nested resource.
        // Not only that, but unless the indices correspond 1-to-1, the index will
        // lose meaning.  Essentially this only works if the child resource arrays
        // are prefixes of their parent arrays.
        for (UResourceBundle res = this; res != null; res = res.getParent()) {
            UResourceBundle obj = res.handleGet(index, null, this);
            if (obj != null) {
                return obj;
            }
        }
        return null;
    }

    /**
     * Returns the keys in this bundle as an enumeration
     * @return an enumeration containing key strings,
     *         which is empty if this is not a bundle or a table resource
     * @stable ICU 3.8
     */
    @Override
    public Enumeration<String> getKeys() {
        return Collections.enumeration(keySet());
    }

    /**
     * Returns a Set of all keys contained in this ResourceBundle and its parent bundles.
     * @return a Set of all keys contained in this ResourceBundle and its parent bundles,
     *         which is empty if this is not a bundle or a table resource
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Override
    @Deprecated
    public Set<String> keySet() {
        // TODO: Java 6 ResourceBundle has keySet() which calls handleKeySet()
        // and caches the results.
        // When we upgrade to Java 6, we still need to check for isTopLevelResource().
        // Keep the else branch as is. The if body should just return super.keySet().
        // Remove then-redundant caching of the keys.
        Set<String> keys = null;
        ICUResourceBundle icurb = null;
        if(isTopLevelResource() && this instanceof ICUResourceBundle) {
            // We do not cache the top-level keys in this base class so that
            // not every string/int/binary... resource has to have a keys cache field.
            icurb = (ICUResourceBundle)this;
            keys = icurb.getTopLevelKeySet();
        }
        if(keys == null) {
            if(isTopLevelResource()) {
                TreeSet<String> newKeySet;
                if(parent == null) {
                    newKeySet = new TreeSet<String>();
                } else if(parent instanceof UResourceBundle) {
                    newKeySet = new TreeSet<String>(((UResourceBundle)parent).keySet());
                } else {
                    // TODO: Java 6 ResourceBundle has keySet(); use it when we upgrade to Java 6
                    // and remove this else branch.
                    newKeySet = new TreeSet<String>();
                    Enumeration<String> parentKeys = parent.getKeys();
                    while(parentKeys.hasMoreElements()) {
                        newKeySet.add(parentKeys.nextElement());
                    }
                }
                newKeySet.addAll(handleKeySet());
                keys = Collections.unmodifiableSet(newKeySet);
                if(icurb != null) {
                    icurb.setTopLevelKeySet(keys);
                }
            } else {
                return handleKeySet();
            }
        }
        return keys;
    }

    /**
     * Returns a Set of the keys contained <i>only</i> in this ResourceBundle.
     * This does not include further keys from parent bundles.
     * @return a Set of the keys contained only in this ResourceBundle,
     *         which is empty if this is not a bundle or a table resource
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Override
    @Deprecated
    protected Set<String> handleKeySet() {
        return Collections.emptySet();
    }

    /**
     * {@icu} Returns the size of a resource. Size for scalar types is always 1, and for
     * vector/table types is the number of child resources.
     *
     * <br><b>Note:</b> Integer array is treated as a scalar type. There are no APIs to
     * access individual members of an integer array. It is always returned as a whole.
     * @return number of resources in a given resource.
     * @stable ICU 3.8
     */
    public int getSize() {
        return 1;
    }

    /**
     * {@icu} Returns the type of a resource.
     * Available types are {@link #INT INT}, {@link #ARRAY ARRAY},
     * {@link #BINARY BINARY}, {@link #INT_VECTOR INT_VECTOR},
     * {@link #STRING STRING}, {@link #TABLE TABLE}.
     *
     * @return type of the given resource.
     * @stable ICU 3.8
     */
    public int getType() {
        return NONE;
    }

    /**
     * {@icu} Return the version number associated with this UResourceBundle as an
     * VersionInfo object.
     * @return VersionInfo object containing the version of the bundle
     * @stable ICU 3.8
     */
    public VersionInfo getVersion() {
        return null;
    }

    /**
     * {@icu} Returns the iterator which iterates over this
     * resource bundle
     * @return UResourceBundleIterator that iterates over the resources in the bundle
     * @stable ICU 3.8
     */
    public UResourceBundleIterator getIterator() {
        return new UResourceBundleIterator(this);
    }

    /**
     * {@icu} Returns the key associated with a given resource. Not all the resources have
     * a key - only those that are members of a table.
     * @return a key associated to this resource, or null if it doesn't have a key
     * @stable ICU 3.8
     */
    public String getKey() {
        return null;
    }

    /**
     * {@icu} Resource type constant for "no resource".
     * @stable ICU 3.8
     */
    public static final int NONE = -1;

    /**
     * {@icu} Resource type constant for strings.
     * @stable ICU 3.8
     */
    public static final int STRING = 0;

    /**
     * {@icu} Resource type constant for binary data.
     * @stable ICU 3.8
     */
    public static final int BINARY = 1;

    /**
     * {@icu} Resource type constant for tables of key-value pairs.
     * @stable ICU 3.8
     */
    public static final int TABLE = 2;

    /**
     * {@icu} Resource type constant for a single 28-bit integer, interpreted as
     * signed or unsigned by the getInt() function.
     * @see #getInt
     * @stable ICU 3.8
     */
    public static final int INT = 7;

    /**
     * {@icu} Resource type constant for arrays of resources.
     * @stable ICU 3.8
     */
    public static final int ARRAY = 8;

    /**
     * Resource type constant for vectors of 32-bit integers.
     * @see #getIntVector
     * @stable ICU 3.8
     */
    public static final int INT_VECTOR = 14;

    //====== protected members ==============

    /**
     * {@icu} Actual worker method for fetching a resource based on the given key.
     * Sub classes must override this method if they support resources with keys.
     * @param aKey the key string of the resource to be fetched
     * @param aliasesVisited hashtable object to hold references of resources already seen
     * @param requested the original resource bundle object on which the get method was invoked.
     *                  The requested bundle and the bundle on which this method is invoked
     *                  are the same, except in the cases where aliases are involved.
     * @return UResourceBundle a resource associated with the key
     * @stable ICU 3.8
     */
    protected UResourceBundle handleGet(String aKey, HashMap<String, String> aliasesVisited,
                                        UResourceBundle requested) {
        return null;
    }

    /**
     * {@icu} Actual worker method for fetching a resource based on the given index.
     * Sub classes must override this method if they support arrays of resources.
     * @param index the index of the resource to be fetched
     * @param aliasesVisited hashtable object to hold references of resources already seen
     * @param requested the original resource bundle object on which the get method was invoked.
     *                  The requested bundle and the bundle on which this method is invoked
     *                  are the same, except in the cases where aliases are involved.
     * @return UResourceBundle a resource associated with the index
     * @stable ICU 3.8
     */
    protected UResourceBundle handleGet(int index, HashMap<String, String> aliasesVisited,
                                        UResourceBundle requested) {
        return null;
    }

    /**
     * {@icu} Actual worker method for fetching the array of strings in a resource.
     * Sub classes must override this method if they support arrays of strings.
     * @return String[] An array of strings containing strings
     * @stable ICU 3.8
     */
    protected String[] handleGetStringArray() {
        return null;
    }

    /**
     * {@icu} Actual worker method for fetching the keys of resources contained in the resource.
     * Sub classes must override this method if they support keys and associated resources.
     *
     * @return Enumeration An enumeration of all the keys in this resource.
     * @stable ICU 3.8
     */
    protected Enumeration<String> handleGetKeys(){
        return null;
    }

    /**
     * {@inheritDoc}
     * @stable ICU 3.8
     */
    // this method is declared in ResourceBundle class
    // so cannot change the signature
    // Override this method
    @Override
    protected Object handleGetObject(String aKey) {
        return handleGetObjectImpl(aKey, this);
    }

    /**
     * Override the superclass method
     */
    // To facilitate XPath style aliases we need a way to pass the reference
    // to requested locale. The only way I could figure out is to implement
    // the look up logic here. This has a disadvantage that if the client
    // loads an ICUResourceBundle, calls ResourceBundle.getObject method
    // with a key that does not exist in the bundle then the lookup is
    // done twice before throwing a MissingResourceExpection.
    private Object handleGetObjectImpl(String aKey, UResourceBundle requested) {
        Object obj = resolveObject(aKey, requested);
        if (obj == null) {
            UResourceBundle parentBundle = getParent();
            if (parentBundle != null) {
                obj = parentBundle.handleGetObjectImpl(aKey, requested);
            }
            if (obj == null)
                throw new MissingResourceException(
                    "Can't find resource for bundle "
                    + this.getClass().getName() + ", key " + aKey,
                    this.getClass().getName(), aKey);
        }
        return obj;
    }

    // Routine for figuring out the type of object to be returned
    // string or string array
    private Object resolveObject(String aKey, UResourceBundle requested) {
        if (getType() == STRING) {
            return getString();
        }
        UResourceBundle obj = handleGet(aKey, null, requested);
        if (obj != null) {
            if (obj.getType() == STRING) {
                return obj.getString();
            }
            try {
                if (obj.getType() == ARRAY) {
                    return obj.handleGetStringArray();
                }
            } catch (UResourceTypeMismatchException ex) {
                return obj;
            }
        }
        return obj;
    }

    /**
     * Is this a top-level resource, that is, a whole bundle?
     * @return true if this is a top-level resource
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    protected boolean isTopLevelResource() {
        return true;
    }
}
