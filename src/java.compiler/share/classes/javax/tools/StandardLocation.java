/*
 * Copyright (c) 2006, 2023, Oracle and/or its affiliates. All rights reserved.
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

package javax.tools;

import javax.tools.JavaFileManager.Location;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * Standard locations of file objects.
 *
 * @since 1.6
 */
public enum StandardLocation implements Location {

    /**
     * Location of new class files.
     */
    CLASS_OUTPUT,

    /**
     * Location of new source files.
     */
    SOURCE_OUTPUT,

    /**
     * Location to search for user class files.
     */
    CLASS_PATH,

    /**
     * Location to search for existing source files.
     */
    SOURCE_PATH,

    /**
     * Location to search for annotation processors.
     */
    ANNOTATION_PROCESSOR_PATH,

    /**
     * Location to search for modules containing annotation processors.
     * @since 9
     */
    ANNOTATION_PROCESSOR_MODULE_PATH,

    /**
     * Location to search for platform classes.  Sometimes called
     * the boot class path.
     */
    PLATFORM_CLASS_PATH,

    /**
     * Location of new native header files.
     * @since 1.8
     */
    NATIVE_HEADER_OUTPUT,

    /**
     * Location to search for the source code of modules.
     * @since 9
     */
    MODULE_SOURCE_PATH,

    /**
     * Location to search for upgradeable system modules.
     * @since 9
     */
    UPGRADE_MODULE_PATH,

    /**
     * Location to search for system modules.
     * @since 9
     */
    SYSTEM_MODULES,

    /**
     * Location to search for precompiled user modules.
     * @since 9
     */
    MODULE_PATH,

    /**
     * Location to search for module patches.
     * @since 9
     */
    PATCH_MODULE_PATH;

    /**
     * Canonical location instances.
     */
    private static final ConcurrentHashMap<String, Location> LOCATIONS = new ConcurrentHashMap<>();

    private static class LazyPatternHolder {
        /**
         * Regexp that checks for the word "MODULE".
         */
        static final Pattern MODULE_WORD_PATTERN = Pattern.compile("\\bMODULE\\b");
    }

    /* package private */ static final boolean computeIsModuleOrientedLocation(String name) {
        return LazyPatternHolder.MODULE_WORD_PATTERN.matcher(name).matches();
    }

    /**
     * Returns a location object with the given name.  The following
     * property must hold: {@code locationFor(x) ==
     * locationFor(y)} if and only if {@code x.equals(y)}.
     * The returned location will be an output location if and only if
     * name ends with {@code "_OUTPUT"}. It will be considered to
     * be a module-oriented location if the name contains the word
     * {@code "MODULE"}.
     *
     * @param name a name
     * @return a location
     */
    public static Location locationFor(final String name) {
        Objects.requireNonNull(name, "name");

        // Check for immediate hit.
        Location loc = LOCATIONS.get(name);
        if (loc != null) {
            return loc;
        }

        // Need to create the cache entry.
        Location newLoc = null;

        // See if this is one of the known Locations first.
        for (Location location : values()) {
            if (location.getName().equals(name)) {
                newLoc = location;
                break;
            }
        }

        // Compute the fitting instance for unknown location, if needed.
        if (newLoc == null) {
            boolean isOutputLocation = name.endsWith("_OUTPUT");
            boolean isModuleOrientedLocation = computeIsModuleOrientedLocation(name);
            newLoc = new Location() {
                @Override public String getName() { return name; }
                @Override public boolean isOutputLocation() { return isOutputLocation; }
                @Override public boolean isModuleOrientedLocation() { return isModuleOrientedLocation; }
            };
        }

        // Thread-safe install.
        Location exist = LOCATIONS.putIfAbsent(name, newLoc);
        return (exist != null) ? exist : newLoc;
    }

    @Override
    public String getName() { return name(); }

    @Override
    public boolean isOutputLocation() {
        switch (this) {
            case CLASS_OUTPUT:
            case SOURCE_OUTPUT:
            case NATIVE_HEADER_OUTPUT:
                return true;
            default:
                return false;
        }
    }

    /**
     * {@inheritDoc}
     * @since 9
     */
    @Override
    public boolean isModuleOrientedLocation() {
        switch (this) {
            case MODULE_SOURCE_PATH:
            case ANNOTATION_PROCESSOR_MODULE_PATH:
            case UPGRADE_MODULE_PATH:
            case SYSTEM_MODULES:
            case MODULE_PATH:
            case PATCH_MODULE_PATH:
                return true;
            default:
                return false;
        }
    }
}
