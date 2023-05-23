// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
******************************************************************************
* Copyright (C) 2004-2016, International Business Machines Corporation and
* others. All Rights Reserved.
******************************************************************************
*/

package jdk.internal.icu.impl;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

import jdk.internal.icu.util.ULocale;
import jdk.internal.icu.util.UResourceBundle;

/**
 * just a wrapper for Java ListResourceBundles and
 * @author ram
 *
 */
@SuppressWarnings("deprecation")
public final class ResourceBundleWrapper extends UResourceBundle {
    private ResourceBundle bundle = null;
    private String localeID = null;
    private String baseName = null;
    private List<String> keys = null;

    /** Loader for bundle instances, for caching. */
    private static abstract class Loader {
        abstract ResourceBundleWrapper load();
    }

    private static CacheBase<String, ResourceBundleWrapper, Loader> BUNDLE_CACHE =
            new SoftCache<String, ResourceBundleWrapper, Loader>() {
        @Override
        protected ResourceBundleWrapper createInstance(String unusedKey, Loader loader) {
            return loader.load();
        }
    };

    private ResourceBundleWrapper(ResourceBundle bundle){
        this.bundle=bundle;
    }

    @Override
    protected Object handleGetObject(String aKey){
        ResourceBundleWrapper current = this;
        Object obj = null;
        while(current!=null){
            try{
                obj = current.bundle.getObject(aKey);
                break;
            }catch(MissingResourceException ex){
                current = (ResourceBundleWrapper)current.getParent();
            }
        }
        if (obj == null){
            throw new MissingResourceException("Can't find resource for bundle "
                                               +baseName
                                               +", key "+aKey,
                                               this.getClass().getName(),
                                               aKey);
        }
        return obj;
    }

    @Override
    public Enumeration<String> getKeys(){
        return Collections.enumeration(keys);
    }

    private void initKeysVector(){
        ResourceBundleWrapper current = this;
        keys = new ArrayList<String>();
        while(current!=null){
            Enumeration<String> e = current.bundle.getKeys();
            while(e.hasMoreElements()){
                String elem = e.nextElement();
                if(!keys.contains(elem)){
                    keys.add(elem);
                }
            }
            current = (ResourceBundleWrapper)current.getParent();
        }
    }
    @Override
    protected String getLocaleID(){
        return localeID;
    }

    @Override
    protected String getBaseName(){
        return bundle.getClass().getName().replace('.','/');
    }

    @Override
    public ULocale getULocale(){
        return new ULocale(localeID);
    }

    @Override
    public UResourceBundle getParent(){
        return (UResourceBundle)parent;
    }

    // Flag for enabling/disabling debugging code
    private static final boolean DEBUG = ICUDebug.enabled("resourceBundleWrapper");

    // This method is for super class's instantiateBundle method
    public static ResourceBundleWrapper getBundleInstance(String baseName, String localeID,
            ClassLoader root, boolean disableFallback) {
        if (root == null) {
            root = ClassLoaderUtil.getClassLoader();
        }
        ResourceBundleWrapper b;
        if (disableFallback) {
            b = instantiateBundle(baseName, localeID, null, root, disableFallback);
        } else {
            b = instantiateBundle(baseName, localeID, ULocale.getDefault().getBaseName(),
                    root, disableFallback);
        }
        if(b==null){
            String separator ="_";
            if(baseName.indexOf('/')>=0){
                separator = "/";
            }
            throw new MissingResourceException("Could not find the bundle "+ baseName+separator+ localeID,"","");
        }
        return b;
    }

    private static boolean localeIDStartsWithLangSubtag(String localeID, String lang) {
        return localeID.startsWith(lang) &&
                (localeID.length() == lang.length() || localeID.charAt(lang.length()) == '_');
    }

    private static ResourceBundleWrapper instantiateBundle(
             final String baseName, final String localeID, final String defaultID,
             final ClassLoader root, final boolean disableFallback) {
        final String name = localeID.isEmpty() ? baseName : baseName + '_' + localeID;
        String cacheKey = disableFallback ? name : name + '#' + defaultID;
        return BUNDLE_CACHE.getInstance(cacheKey, new Loader() {
                @Override
                public ResourceBundleWrapper load() {
            ResourceBundleWrapper parent = null;
            int i = localeID.lastIndexOf('_');

            boolean loadFromProperties = false;
            boolean parentIsRoot = false;
            if (i != -1) {
                String locName = localeID.substring(0, i);
                parent = instantiateBundle(baseName, locName, defaultID, root, disableFallback);
            }else if(!localeID.isEmpty()){
                parent = instantiateBundle(baseName, "", defaultID, root, disableFallback);
                parentIsRoot = true;
            }
            ResourceBundleWrapper b = null;
            try {
                Class<? extends ResourceBundle> cls =
                        root.loadClass(name).asSubclass(ResourceBundle.class);
                ResourceBundle bx = cls.newInstance();
                b = new ResourceBundleWrapper(bx);
                if (parent != null) {
                    b.setParent(parent);
                }
                b.baseName=baseName;
                b.localeID = localeID;
            } catch (ClassNotFoundException e) {
                loadFromProperties = true;
            } catch (NoClassDefFoundError e) {
                loadFromProperties = true;
            } catch (Exception e) {
                if (DEBUG)
                    System.out.println("failure");
                if (DEBUG)
                    System.out.println(e);
            }

            if (loadFromProperties) {
                try {
                    final String resName = name.replace('.', '/') + ".properties";
                    InputStream stream = root.getResourceAsStream(resName);
                    if (stream != null) {
                        // make sure it is buffered
                        stream = new java.io.BufferedInputStream(stream);
                        try {
                            b = new ResourceBundleWrapper(new PropertyResourceBundle(stream));
                            if (parent != null) {
                                b.setParent(parent);
                            }
                            b.baseName=baseName;
                            b.localeID=localeID;
                        } catch (Exception ex) {
                            // throw away exception
                        } finally {
                            try {
                                stream.close();
                            } catch (Exception ex) {
                                // throw away exception
                            }
                        }
                    }

                    // if a bogus locale is passed then the parent should be
                    // the default locale not the root locale!
                    if (b == null && !disableFallback &&
                            !localeID.isEmpty() && localeID.indexOf('_') < 0 &&
                            !localeIDStartsWithLangSubtag(defaultID, localeID)) {
                        // localeID is only a language subtag, different from the default language.
                        b = instantiateBundle(baseName, defaultID, defaultID, root, disableFallback);
                    }
                    // if still could not find the bundle then return the parent
                    if(b==null && (!parentIsRoot || !disableFallback)){
                        b=parent;
                    }
                } catch (Exception e) {
                    if (DEBUG)
                        System.out.println("failure");
                    if (DEBUG)
                        System.out.println(e);
                }
            }
            if(b!=null){
                b.initKeysVector();
            }else{
                if(DEBUG)System.out.println("Returning null for "+baseName+"_"+localeID);
            }
            return b;
        }});
    }
}
