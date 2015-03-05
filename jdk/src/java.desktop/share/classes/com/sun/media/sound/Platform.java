/*
 * Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.media.sound;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.StringTokenizer;



/**
 * Audio configuration class for exposing attributes specific to the platform or system.
 *
 * @author Kara Kytle
 * @author Florian Bomers
 */
final class Platform {


    // STATIC FINAL CHARACTERISTICS

    // native library we need to load
    private static final String libNameMain     = "jsound";
    private static final String libNameALSA     = "jsoundalsa";
    private static final String libNameDSound   = "jsoundds";

    // extra libs handling: bit flags for each different library
    public static final int LIB_MAIN     = 1;
    public static final int LIB_ALSA     = 2;
    public static final int LIB_DSOUND   = 4;

    // bit field of the constants above. Willbe set in loadLibraries
    private static int loadedLibs = 0;

    // features: the main native library jsound reports which feature is
    // contained in which lib
    public static final int FEATURE_MIDIIO       = 1;
    public static final int FEATURE_PORTS        = 2;
    public static final int FEATURE_DIRECT_AUDIO = 3;

    // SYSTEM CHARACTERISTICS
    // vary according to hardware architecture

    // intel is little-endian.  sparc is big-endian.
    private static boolean bigEndian;

    static {
        if(Printer.trace)Printer.trace(">> Platform.java: static");

        loadLibraries();
        readProperties();
    }


    /**
     * Private constructor.
     */
    private Platform() {
    }


    // METHODS FOR INTERNAL IMPLEMENTATION USE


    /**
     * Dummy method for forcing initialization.
     */
    static void initialize() {

        if(Printer.trace)Printer.trace("Platform: initialize()");
    }


    /**
     * Determine whether the system is big-endian.
     */
    static boolean isBigEndian() {

        return bigEndian;
    }


    // PRIVATE METHODS

    /**
     * Load the native library or libraries.
     */
    private static void loadLibraries() {
        if(Printer.trace)Printer.trace(">>Platform.loadLibraries");

        // load the main library
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            System.loadLibrary(libNameMain);
            return null;
        });
        // just for the heck of it...
        loadedLibs |= LIB_MAIN;

        // now try to load extra libs. They are defined at compile time in the Makefile
        // with the define EXTRA_SOUND_JNI_LIBS
        String extraLibs = nGetExtraLibraries();
        // the string is the libraries, separated by white space
        StringTokenizer st = new StringTokenizer(extraLibs);
        while (st.hasMoreTokens()) {
            final String lib = st.nextToken();
            try {
                AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                    System.loadLibrary(lib);
                    return null;
                });

                if (lib.equals(libNameALSA)) {
                    loadedLibs |= LIB_ALSA;
                    if (Printer.debug) Printer.debug("Loaded ALSA lib successfully.");
                } else if (lib.equals(libNameDSound)) {
                    loadedLibs |= LIB_DSOUND;
                    if (Printer.debug) Printer.debug("Loaded DirectSound lib successfully.");
                } else {
                    if (Printer.err) Printer.err("Loaded unknown lib '"+lib+"' successfully.");
                }
            } catch (Throwable t) {
                if (Printer.err) Printer.err("Couldn't load library "+lib+": "+t.toString());
            }
        }
    }


    static boolean isMidiIOEnabled() {
        return isFeatureLibLoaded(FEATURE_MIDIIO);
    }

    static boolean isPortsEnabled() {
        return isFeatureLibLoaded(FEATURE_PORTS);
    }

    static boolean isDirectAudioEnabled() {
        return isFeatureLibLoaded(FEATURE_DIRECT_AUDIO);
    }

    private static boolean isFeatureLibLoaded(int feature) {
        if (Printer.debug) Printer.debug("Platform: Checking for feature "+feature+"...");
        int requiredLib = nGetLibraryForFeature(feature);
        boolean isLoaded = (requiredLib != 0) && ((loadedLibs & requiredLib) == requiredLib);
        if (Printer.debug) Printer.debug("          ...needs library "+requiredLib+". Result is loaded="+isLoaded);
        return isLoaded;
    }

    // the following native methods are implemented in Platform.c
    private native static boolean nIsBigEndian();
    private native static String nGetExtraLibraries();
    private native static int nGetLibraryForFeature(int feature);

    /**
     * Read the required system properties.
     */
    private static void readProperties() {
        // $$fb 2002-03-06: implement check for endianness in native. Facilitates porting !
        bigEndian = nIsBigEndian();
    }
}
