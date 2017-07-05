/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.istack.internal.localization;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Localizes the {@link Localizable} into a message
 * by using a configured {@link Locale}.
 *
 * @author WS Development Team
 */
public class Localizer {

    private final Locale _locale;
    private final HashMap _resourceBundles;

    public Localizer() {
        this(Locale.getDefault());
    }

    public Localizer(Locale l) {
        _locale = l;
        _resourceBundles = new HashMap();
    }

    public Locale getLocale() {
        return _locale;
    }

    public String localize(Localizable l) {
        String key = l.getKey();
        if (key == Localizable.NOT_LOCALIZABLE) {
            // this message is not localizable
            return (String) l.getArguments()[0];
        }
        String bundlename = l.getResourceBundleName();

        try {
            ResourceBundle bundle =
                (ResourceBundle) _resourceBundles.get(bundlename);

            if (bundle == null) {
                try {
                    bundle = ResourceBundle.getBundle(bundlename, _locale);
                } catch (MissingResourceException e) {
                    // work around a bug in the com.sun.enterprise.deployment.WebBundleArchivist:
                    //   all files with an extension different from .class (hence all the .properties files)
                    //   get copied to the top level directory instead of being in the package where they
                    //   are defined
                    // so, since we can't find the bundle under its proper name, we look for it under
                    //   the top-level package

                    int i = bundlename.lastIndexOf('.');
                    if (i != -1) {
                        String alternateBundleName =
                            bundlename.substring(i + 1);
                        try {
                            bundle =
                                ResourceBundle.getBundle(
                                    alternateBundleName,
                                    _locale);
                        } catch (MissingResourceException e2) {
                            // give up
                            return getDefaultMessage(l);
                        }
                    }
                }

                _resourceBundles.put(bundlename, bundle);
            }

            if (bundle == null) {
                return getDefaultMessage(l);
            }

            if (key == null)
                key = "undefined";

            String msg;
            try {
                msg = bundle.getString(key);
            } catch (MissingResourceException e) {
                // notice that this may throw a MissingResourceException of its own (caught below)
                msg = bundle.getString("undefined");
            }

            // localize all arguments to the given localizable object
            Object[] args = l.getArguments();
            for (int i = 0; i < args.length; ++i) {
                if (args[i] instanceof Localizable)
                    args[i] = localize((Localizable) args[i]);
            }

            String message = MessageFormat.format(msg, args);
            return message;

        } catch (MissingResourceException e) {
            return getDefaultMessage(l);
        }

    }

    private String getDefaultMessage(Localizable l) {
        String key = l.getKey();
        Object[] args = l.getArguments();
        StringBuilder sb = new StringBuilder();
        sb.append("[failed to localize] ");
        sb.append(key);
        if (args != null) {
            sb.append('(');
            for (int i = 0; i < args.length; ++i) {
                if (i != 0)
                    sb.append(", ");
                sb.append(String.valueOf(args[i]));
            }
            sb.append(')');
        }
        return sb.toString();
    }

}
