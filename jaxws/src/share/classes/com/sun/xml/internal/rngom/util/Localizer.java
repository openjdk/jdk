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
package com.sun.xml.internal.rngom.util;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Localizes messages.
 */
public class Localizer {
    private final Class cls;
    private ResourceBundle bundle;
    /**
     * If non-null, any resources that weren't found in this localizer
     * will be delegated to the parent.
     */
    private final Localizer parent;

    public Localizer(Class cls) {
        this(null,cls);
    }

    public Localizer(Localizer parent, Class cls) {
        this.parent = parent;
        this.cls = cls;
    }

    private String getString(String key) {
        try {
            return getBundle().getString(key);
        } catch( MissingResourceException e ) {
            // delegation
            if(parent!=null)
                return parent.getString(key);
            else
                throw e;
        }
    }

    public String message(String key) {
        return MessageFormat.format(getString(key), new Object[]{});
    }

    public String message(String key, Object arg) {
        return MessageFormat.format(getString(key),
            new Object[]{arg});
    }

    public String message(String key, Object arg1, Object arg2) {
        return MessageFormat.format(getString(key), new Object[]{
                arg1, arg2});
    }

    public String message(String key, Object[] args) {
        return MessageFormat.format(getString(key), args);
    }

    private ResourceBundle getBundle() {
        if (bundle == null) {
            String s = cls.getName();
            int i = s.lastIndexOf('.');
            if (i > 0)
                s = s.substring(0, i + 1);
            else
                s = "";
            bundle = ResourceBundle.getBundle(s + "Messages");
        }
        return bundle;
    }
}
