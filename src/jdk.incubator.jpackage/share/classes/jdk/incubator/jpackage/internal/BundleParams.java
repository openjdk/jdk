/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.jpackage.internal;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static jdk.incubator.jpackage.internal.StandardBundlerParam.*;

public class BundleParams {

    final protected Map<String, ? super Object> params;

    // RelativeFileSet
    public static final String PARAM_APP_RESOURCES      = "appResources";

    // String - Icon file name
    public static final String PARAM_ICON               = "icon";

    // String - Name of bundle file and native launcher
    public static final String PARAM_NAME               = "name";

    // String - application vendor, used by most of the bundlers
    public static final String PARAM_VENDOR             = "vendor";

    // String - email name and email, only used for debian */
    public static final String PARAM_EMAIL              = "email";

    // String - vendor <email>, only used for debian */
    public static final String PARAM_MAINTAINER         = "maintainer";

    /* String - Copyright. Used on Mac */
    public static final String PARAM_COPYRIGHT          = "copyright";

    // String - GUID on windows for MSI, CFBundleIdentifier on Mac
    // If not compatible with requirements then bundler either do not bundle
    // or autogenerate
    public static final String PARAM_IDENTIFIER         = "identifier";

    /* boolean - shortcut preferences */
    public static final String PARAM_SHORTCUT           = "shortcutHint";
    // boolean - menu shortcut preference
    public static final String PARAM_MENU               = "menuHint";

    // String - Application version. Format may differ for different bundlers
    public static final String PARAM_VERSION            = "appVersion";

    // String - Application release. Used on Linux.
    public static final String PARAM_RELEASE            = "appRelease";

    // String - Optional application description. Used by MSI and on Linux
    public static final String PARAM_DESCRIPTION        = "description";

    // String - License type. Needed on Linux (rpm)
    public static final String PARAM_LICENSE_TYPE       = "licenseType";

    // String - File with license. Format is OS/bundler specific
    public static final String PARAM_LICENSE_FILE       = "licenseFile";

    // String Main application class.
    // Not used directly but used to derive default values
    public static final String PARAM_APPLICATION_CLASS  = "applicationClass";

    // boolean - Adds a dialog to let the user choose a directory
    // where the product will be installed.
    public static final String PARAM_INSTALLDIR_CHOOSER = "installdirChooser";

    /**
     * create a new bundle with all default values
     */
    public BundleParams() {
        params = new HashMap<>();
    }

    /**
     * Create a bundle params with a copy of the params
     * @param params map of initial parameters to be copied in.
     */
    public BundleParams(Map<String, ?> params) {
        this.params = new HashMap<>(params);
    }

    public void addAllBundleParams(Map<String, ? super Object> params) {
        this.params.putAll(params);
    }

    // NOTE: we do not care about application parameters here
    // as they will be embeded into jar file manifest and
    // java launcher will take care of them!

    public Map<String, ? super Object> getBundleParamsAsMap() {
        return new HashMap<>(params);
    }

    public String getName() {
        return APP_NAME.fetchFrom(params);
    }

    public void setAppResourcesList(
            List<jdk.incubator.jpackage.internal.RelativeFileSet> rfs) {
        putUnlessNull(APP_RESOURCES_LIST.getID(), rfs);
    }

    private void putUnlessNull(String param, Object value) {
        if (value != null) {
            params.put(param, value);
        }
    }
}
