/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.javac.util;

import java.util.ResourceBundle;
import java.util.MissingResourceException;
import java.text.MessageFormat;

/**
 *  Support for localized messages.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Messages {
    /** The context key for the Messages object. */
    protected static final Context.Key<Messages> messagesKey =
        new Context.Key<Messages>();

    /** Get the Messages instance for this context. */
    public static Messages instance(Context context) {
        Messages instance = context.get(messagesKey);
        if (instance == null)
            instance = new Messages(context);
        return instance;
    }

    private List<ResourceBundle> bundles = List.nil();

    /** Creates a Messages object.
     */
    public Messages(Context context) {
        context.put(messagesKey, this);
        add(getDefaultBundle());
    }

    /** Creates a Messages object.
     * @param bundle the name to identify the resource buundle of localized messages.
     */
    public Messages(String bundleName) throws MissingResourceException {
        add(bundleName);
    }

    /** Creates a Messages object.
     * @param bundle the name to identif the resource buundle of localized messages.
     */
    public Messages(ResourceBundle bundle) throws MissingResourceException {
        add(bundle);
    }

    /** Add a new resource bundle to the list that is searched for localized messages.
     * @param bundle the name to identify the resource bundle of localized messages.
     */
    public void add(String bundleName) throws MissingResourceException {
        add(ResourceBundle.getBundle(bundleName));
    }

    /** Add a new resource bundle to the list that is searched for localized messages.
     * Resource bundles will be searched in reverse order in which they are added.
     * @param bundle the bundle of localized messages.
     */
    public void add(ResourceBundle bundle) {
        bundles = bundles.prepend(bundle);
    }

    /** Gets the localized string corresponding to a key, formatted with a set of args.
     */
    public String getLocalizedString(String key, Object... args) {
        return getLocalizedString(bundles, key, args);
    }


    /* Static access:
     * javac has a firmly entrenched notion of a default message bundle
     * which it can access from any static context. This is used to get
     * easy access to simple localized strings.
     */

    private static final String defaultBundleName =
        "com.sun.tools.javac.resources.compiler";
    private static ResourceBundle defaultBundle;
    private static Messages defaultMessages;


    /**
     * Gets a localized string from the compiler's default bundle.
     */
    // used to support legacy Log.getLocalizedString
    static String getDefaultLocalizedString(String key, Object... args) {
        return getLocalizedString(List.of(getDefaultBundle()), key, args);
    }

    // used to support legacy static Diagnostic.fragment
    static Messages getDefaultMessages() {
        if (defaultMessages == null)
            defaultMessages = new Messages(getDefaultBundle());
        return defaultMessages;
    }

    public static ResourceBundle getDefaultBundle() {
        try {
            if (defaultBundle == null)
                defaultBundle = ResourceBundle.getBundle(defaultBundleName);
            return defaultBundle;
        }
        catch (MissingResourceException e) {
            throw new Error("Fatal: Resource for compiler is missing", e);
        }
    }

    private static String getLocalizedString(List<ResourceBundle> bundles,
                                             String key,
                                             Object... args) {
       String msg = null;
        for (List<ResourceBundle> l = bundles; l.nonEmpty() && msg == null; l = l.tail) {
            ResourceBundle rb = l.head;
            try {
                msg = rb.getString(key);
            }
            catch (MissingResourceException e) {
                // ignore, try other bundles in list
            }
        }
        if (msg == null) {
            msg = "compiler message file broken: key=" + key +
                " arguments={0}, {1}, {2}, {3}, {4}, {5}, {6}, {7}";
        }
        return MessageFormat.format(msg, args);
    }


}
