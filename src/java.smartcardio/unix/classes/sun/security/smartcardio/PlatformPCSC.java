/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Red Hat Inc. All rights reserved.
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

package sun.security.smartcardio;

import java.io.File;
import java.io.IOException;

import java.security.AccessController;
import java.security.PrivilegedAction;

import sun.security.util.Debug;

/**
 * Platform specific code and functions for Unix / MUSCLE based PC/SC
 * implementations.
 *
 * @since   1.6
 * @author  Andreas Sterbenz
 */
class PlatformPCSC {

    static final Debug debug = Debug.getInstance("pcsc");

    private static final String PROP_NAME = "sun.security.smartcardio.library";

    // The architecture templates are for Debian-based systems: https://wiki.debian.org/Multiarch/Tuples
    // 32-bit arm differs from the pattern of the rest and has to be specified explicitly
    private static final String[] LIB_TEMPLATES = { "/usr/$LIBISA/libpcsclite.so",
                                                    "/usr/local/$LIBISA/libpcsclite.so",
                                                    "/usr/lib/$ARCH-linux-gnu/libpcsclite.so",
                                                    "/usr/lib/arm-linux-gnueabi/libpcsclite.so",
                                                    "/usr/lib/arm-linux-gnueabihf/libpcsclite.so" };
    private static final String[] LIB_SUFFIXES = { ".1", ".0", "" };
    private static final String PCSC_FRAMEWORK = "/System/Library/Frameworks/PCSC.framework/Versions/Current/PCSC";

    PlatformPCSC() {
        // empty
    }

    @SuppressWarnings({"removal", "restricted"})
    static final Throwable initException
            = AccessController.doPrivileged(new PrivilegedAction<Throwable>() {
        public Throwable run() {
            try {
                System.loadLibrary("j2pcsc");
                String library = getLibraryName();
                if (debug != null) {
                    debug.println("Using PC/SC library: " + library);
                }
                initialize(library);
                return null;
            } catch (Throwable e) {
                return e;
            }
        }
    });

    // expand $LIBISA to the system specific directory name for libraries
    // expand $ARCH to the Debian system architecture in use
    private static String expand(String lib) {
        int k = lib.indexOf("$LIBISA");
        if (k != -1) {
            String libDir;
            if ("64".equals(System.getProperty("sun.arch.data.model"))) {
                // assume Linux convention
                libDir = "lib64";
            } else {
                // must be 32-bit
                libDir = "lib";
            }
            lib = lib.replace("$LIBISA", libDir);
        }

        k = lib.indexOf("$ARCH");
        if (k != -1) {
            String arch = System.getProperty("os.arch");
            lib = lib.replace("$ARCH", getDebianArchitecture(arch));
        }

        return lib;
    }

    private static String getDebianArchitecture(String jdkArch) {
        return switch (jdkArch) {
            case "amd64" -> "x86_64";
            case "ppc" -> "powerpc";
            case "ppc64" -> "powerpc64";
            case "ppc64le" -> "powerpc64le";
            default -> jdkArch;
        };
    }

    private static String getLibraryName() throws IOException {
        // if system property is set, use that library
        String lib = expand(System.getProperty(PROP_NAME, "").trim());
        if (lib.length() != 0) {
            return lib;
        }

        for (String template : LIB_TEMPLATES) {
            for (String suffix : LIB_SUFFIXES) {
                lib = expand(template) + suffix;
                if (debug != null) {
                    debug.println("Looking for " + lib);
                }
                if (new File(lib).isFile()) {
                    // if library exists, use that
                    return lib;
                }
            }
        }

        // As of macos 11, framework libraries have been removed from the file
        // system, but in such a way that they can still be dlopen()ed, even
        // though they can no longer be open()ed.
        //
        // https://developer.apple.com/documentation/macos-release-notes/macos-big-sur-11_0_1-release-notes
        //
        // """New in macOS Big Sur 11.0.1, the system ships with a built-in
        // dynamic linker cache of all system-provided libraries. As part of
        // this change, copies of dynamic libraries are no longer present on
        // the filesystem. Code that attempts to check for dynamic library
        // presence by looking for a file at a path or enumerating a directory
        // will fail. Instead, check for library presence by attempting to
        // dlopen() the path, which will correctly check for the library in the
        // cache."""
        //
        // The directory structure remains otherwise intact, so check for
        // existence of the containing directory instead of the file.
        lib = PCSC_FRAMEWORK;
        if (new File(lib).getParentFile().isDirectory()) {
            // if PCSC.framework exists, use that
            return lib;
        }
        throw new IOException("No PC/SC library found on this system");
    }

    private static native void initialize(String libraryName);

    // PCSC constants defined differently under Windows and MUSCLE
    // MUSCLE version
    static final int SCARD_PROTOCOL_T0     =  0x0001;
    static final int SCARD_PROTOCOL_T1     =  0x0002;
    static final int SCARD_PROTOCOL_RAW    =  0x0004;

    static final int SCARD_UNKNOWN         =  0x0001;
    static final int SCARD_ABSENT          =  0x0002;
    static final int SCARD_PRESENT         =  0x0004;
    static final int SCARD_SWALLOWED       =  0x0008;
    static final int SCARD_POWERED         =  0x0010;
    static final int SCARD_NEGOTIABLE      =  0x0020;
    static final int SCARD_SPECIFIC        =  0x0040;

}
