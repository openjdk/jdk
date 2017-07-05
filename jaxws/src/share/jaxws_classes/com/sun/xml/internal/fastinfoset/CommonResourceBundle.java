/*
 * Copyright (c) 2004, 2012, Oracle and/or its affiliates. All rights reserved.
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
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */

package com.sun.xml.internal.fastinfoset;

import java.util.Locale;
import java.util.ResourceBundle;

/** Resource bundle implementation for localized messages.
 */
public class CommonResourceBundle extends AbstractResourceBundle {

    public static final String BASE_NAME = "com.sun.xml.internal.fastinfoset.resources.ResourceBundle";
    private static volatile CommonResourceBundle instance = null;
    private static Locale locale = null;
    private ResourceBundle bundle = null;

    protected CommonResourceBundle() {
        // Load the resource bundle of default locale
        bundle = ResourceBundle.getBundle(BASE_NAME);
    }

    protected CommonResourceBundle(Locale locale) {
        // Load the resource bundle of specified locale
        bundle = ResourceBundle.getBundle(BASE_NAME, locale);
    }

    public static CommonResourceBundle getInstance() {
        if (instance == null) {
            synchronized (CommonResourceBundle.class) {
                instance = new CommonResourceBundle();
                //**need to know where to get the locale
                //String localeString = CommonProperties.getInstance()
                //                  .getProperty("omar.common.locale");
                locale = parseLocale(/*localeString*/null);
            }
        }

        return instance;
    }

    public static CommonResourceBundle getInstance(Locale locale) {
        if (instance == null) {
            synchronized (CommonResourceBundle.class) {
                instance = new CommonResourceBundle(locale);
            }
        } else {
            synchronized (CommonResourceBundle.class) {
                if (CommonResourceBundle.locale != locale) {
                    instance = new CommonResourceBundle(locale);
                }
            }
        }
        return instance;
    }


    public ResourceBundle getBundle() {
        return bundle;
    }
    public ResourceBundle getBundle(Locale locale) {
        return ResourceBundle.getBundle(BASE_NAME, locale);
    }

}
