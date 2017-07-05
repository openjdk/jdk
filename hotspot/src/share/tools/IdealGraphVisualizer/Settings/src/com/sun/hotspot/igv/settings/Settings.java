/*
 * Copyright 1998-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.hotspot.igv.settings;

import java.util.prefs.Preferences;

/**
 *
 * @author Thomas Wuerthinger
 */
public class Settings {

    public final static String NODE_TEXT = "nodeText";
    public final static String NODE_TEXT_DEFAULT = "[idx] [name]";
    public final static String NODE_WIDTH = "nodeWidth";
    public final static String NODE_WIDTH_DEFAULT = "100";
    public final static String PORT = "port";
    public final static String PORT_DEFAULT = "4444";
    public final static String DIRECTORY = "directory";
    public final static String DIRECTORY_DEFAULT = System.getProperty("user.dir");

    public static Preferences get() {
        return Preferences.userNodeForPackage(Settings.class);
    }
}
