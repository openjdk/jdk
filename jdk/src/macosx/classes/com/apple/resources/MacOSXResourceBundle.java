/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.apple.resources;

import java.security.*;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.io.*;

public class MacOSXResourceBundle extends PropertyResourceBundle {
    MacOSXResourceBundle(InputStream stream) throws IOException {
        super(stream);
    }

    void setItsParent(ResourceBundle rb) {
        setParent(rb);
    }

    public static ResourceBundle getMacResourceBundle(String baseJavaBundle) throws Exception {
        return getMacResourceBundle(baseJavaBundle, null);
    }

    public static ResourceBundle getMacResourceBundle(String baseJavaBundle, String filename) throws Exception {
        LoadNativeBundleAction lnba = new LoadNativeBundleAction(baseJavaBundle, filename);
        return (ResourceBundle)java.security.AccessController.doPrivileged(lnba);
    }
}

class LoadNativeBundleAction implements PrivilegedExceptionAction {
    String mBaseJavaBundle;
    String mFilenameOverride;

    LoadNativeBundleAction(String baseJavaBundle, String filenameOverride) {
        mBaseJavaBundle = baseJavaBundle;
        mFilenameOverride = filenameOverride;
    }

    public Object run() {
        java.util.ResourceBundle returnValue = null;
        MacOSXResourceBundle macOSrb = null;

        // Load the Mac OS X resources.
        // Use a base filename if we were given one. Otherwise, we will look for the last piece of the bundle path
        // with '.properties' appended. Either way, the native method will take care of the extension.
        String filename = mFilenameOverride;

        if (filename == null) {
            filename = mBaseJavaBundle.substring(mBaseJavaBundle.lastIndexOf('.') + 1);
        }

        File propsFile = null;
        String propertyFileName = getPathToBundleFile(filename);
        InputStream stream = null;

        try {
            propsFile = new File(propertyFileName);
            stream = new FileInputStream(propsFile);
            stream = new java.io.BufferedInputStream(stream);
            macOSrb = new MacOSXResourceBundle(stream);
        } catch (Exception e) {
            //e.printStackTrace();
            //System.out.println("Failed to create resources from application bundle.  Using Java-based resources.");
        } finally {
            try {
                if (stream != null) stream.close();
                stream = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        returnValue = ResourceBundle.getBundle(mBaseJavaBundle);

        // If we have a platform-specific bundle, make it the parent of the generic bundle, so failures propagate up to the parent.
        if (returnValue != null) {
            if (macOSrb != null) {
                macOSrb.setItsParent(returnValue);
                returnValue = macOSrb;
            }
        }

        return returnValue;
    }

    private static native String getPathToBundleFile(String filename);
}

