/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Access to the localizable resources used by a doclet.
 * The resources are split across two resource bundles:
 * one that contains format-neutral strings common to
 * all supported formats, and one that contains strings
 * specific to the selected doclet, such as the standard
 * HTML doclet.
 */
public class Resources {
    public final String annotationTypeSummary;
    public final String classSummary;
    private final BaseConfiguration configuration;
    private final String commonBundleName;
    private final String docletBundleName;
    public final String enumSummary;
    public final String errorSummary;
    public final String exceptionSummary;
    public final String interfaceSummary;
    public final String packageSummary;

    protected ResourceBundle commonBundle;
    protected ResourceBundle docletBundle;

    /**
     * Creates a {@code Resources} to provide access the resource
     * bundles used by a doclet.
     *
     * @param configuration the configuration for the doclet,
     *  to provide access the locale to be used when accessing the
     *  names resource bundles.
     * @param commonBundleName the name of the bundle containing the strings
     *  common to all output formats
     * @param docletBundleName the name of the bundle containing the strings
     *  specific to a particular format
     */
    public Resources(BaseConfiguration configuration, String commonBundleName, String docletBundleName) {
        this.configuration = configuration;
        this.commonBundleName = commonBundleName;
        this.docletBundleName = docletBundleName;
        this.annotationTypeSummary = getText("doclet.Annotation_Types_Summary");
        this.classSummary = getText("doclet.Class_Summary");
        this.enumSummary = getText("doclet.Enum_Summary");
        this.errorSummary = getText("doclet.Error_Summary");
        this.exceptionSummary = getText("doclet.Exception_Summary");
        this.interfaceSummary = getText("doclet.Interface_Summary");
        this.packageSummary = getText("doclet.Package_Summary");
    }

    /**
     * Gets the string for the given key from one of the doclet's
     * resource bundles.
     *
     * The more specific bundle is checked first;
     * if it is not there, the common bundle is then checked.
     *
     * @param key the key for the desired string
     * @return the string for the given key
     * @throws MissingResourceException if the key is not found in either
     *  bundle.
     */
    public String getText(String key) throws MissingResourceException {
        initBundles();

        if (docletBundle.containsKey(key))
            return docletBundle.getString(key);

        return commonBundle.getString(key);
    }
    /**
     * Gets the string for the given key from one of the doclet's
     * resource bundles, substituting additional arguments into
     * into the resulting string with {@link MessageFormat#format}.
     *
     * The more specific bundle is checked first;
     * if it is not there, the common bundle is then checked.
     *
     * @param key the key for the desired string
     * @param args values to be substituted into the resulting string
     * @return the string for the given key
     * @throws MissingResourceException if the key is not found in either
     *  bundle.
     */
    public String getText(String key, Object... args) throws MissingResourceException {
        return MessageFormat.format(getText(key), args);
    }

    /**
     * Lazily initializes the bundles. This is (currently) necessary because
     * this object may be created before the locale to be used is known.
     */
    protected void initBundles() {
        if (commonBundle == null) {
            Locale locale = configuration.getLocale();
            this.commonBundle = ResourceBundle.getBundle(commonBundleName, locale);
            this.docletBundle = ResourceBundle.getBundle(docletBundleName, locale);
        }
    }
}
