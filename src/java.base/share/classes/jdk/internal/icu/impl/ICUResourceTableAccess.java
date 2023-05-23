// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2009-2014, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */
package jdk.internal.icu.impl;

import jdk.internal.icu.util.ULocale;
import jdk.internal.icu.util.UResourceBundle;

/**
 * Static utility functions for probing resource tables, used by ULocale and
 * LocaleDisplayNames.
 */
public class ICUResourceTableAccess {
    /**
     * Utility to fetch locale display data from resource bundle tables.  Convenience
     * wrapper for {@link #getTableString(ICUResourceBundle, String, String, String, String)}.
     */
    public static String getTableString(String path, ULocale locale, String tableName,
            String itemName, String defaultValue) {
        ICUResourceBundle bundle = (ICUResourceBundle) UResourceBundle.
            getBundleInstance(path, locale.getBaseName());
        return getTableString(bundle, tableName, null, itemName, defaultValue);
    }

    /**
     * Utility to fetch locale display data from resource bundle tables.  Uses fallback
     * through the "Fallback" resource if available.
     */
    public static String getTableString(ICUResourceBundle bundle, String tableName,
            String subtableName, String item, String defaultValue) {
        String result = null;
        try {
            for (;;) {
                ICUResourceBundle table = bundle.findWithFallback(tableName);
                if (table == null) {
                    return defaultValue;
                }
                ICUResourceBundle stable = table;
                if (subtableName != null) {
                    stable = table.findWithFallback(subtableName);
                }
                if (stable != null) {
                    result = stable.findStringWithFallback(item);
                    if (result != null) {
                        break; // possible real exception
                    }
                }

                // if we get here, stable was null, or there was no string for the item
                if (subtableName == null) {
                    // may be a deprecated code
                    String currentName = null;
                    if (tableName.equals("Countries")) {
                        currentName = LocaleIDs.getCurrentCountryID(item);
                    } else if (tableName.equals("Languages")) {
                        currentName = LocaleIDs.getCurrentLanguageID(item);
                    }
                    if (currentName != null) {
                        result = table.findStringWithFallback(currentName);
                        if (result != null) {
                            break; // possible real exception
                        }
                    }
                }

                // still can't figure it out? try the fallback mechanism
                String fallbackLocale = table.findStringWithFallback("Fallback"); // again, possible exception
                if (fallbackLocale == null) {
                    return defaultValue;
                }

                if (fallbackLocale.length() == 0) {
                    fallbackLocale = "root";
                }

                if (fallbackLocale.equals(table.getULocale().getName())) {
                    return defaultValue;
                }

                bundle = (ICUResourceBundle) UResourceBundle.getBundleInstance(
                        bundle.getBaseName(), fallbackLocale);
            }
        } catch (Exception e) {
            // If something is seriously wrong, we might call getString on a resource that is
            // not a string.  That will throw an exception, which we catch and ignore here.
        }

        // If the result is empty return item instead
        return ((result != null && result.length() > 0) ? result : defaultValue);
    }
}
