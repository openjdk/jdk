/*
 *  Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

package jdk.internal.module;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class IllegalNativeAccessChecker {

    private final Collection<String> allowedModuleNames;
    private final boolean allowAllUnnamedModules;

    private IllegalNativeAccessChecker(Set<String> allowedModuleNames, boolean allowAllUnnamedModules) {
        this.allowedModuleNames = Collections.unmodifiableSet(allowedModuleNames);
        this.allowAllUnnamedModules = allowAllUnnamedModules;
    }

    // system-wide IllegalNativeAccessChecker
    private static volatile IllegalNativeAccessChecker checker;

    static Collection<String> enableNativeAccessModules() {
        return checker().allowedModuleNames;
    }

    public static boolean enableNativeAccessAllUnnamedModules() {
        return checker().allowAllUnnamedModules;
    }

    private static IllegalNativeAccessChecker checker() {
        if (checker == null) {
            Set<String> allowedModuleNames = new HashSet<>();
            boolean allowAllUnnamedModules = false;
            for (String str : decode()) {
                if (str.equals("ALL-UNNAMED")) {
                    allowAllUnnamedModules = true;
                } else {
                    allowedModuleNames.add(str);
                }
            }
            checker = new IllegalNativeAccessChecker(allowedModuleNames, allowAllUnnamedModules);
        }
        return checker;
    }

    /**
     * Returns the set of module names specified by --enable-native-access options.
     */
    private static Set<String> decode() {
        String prefix = "jdk.module.enable.native.access.";
        int index = 0;
        // the system property is removed after decoding
        String value = getAndRemoveProperty(prefix + index);
        Set<String> modules = new HashSet<>();
        if (value == null) {
            return modules;
        }
        while (value != null) {
            for (String s : value.split(",")) {
                if (!s.isEmpty())
                    modules.add(s);
            }
            index++;
            value = getAndRemoveProperty(prefix + index);
        }
        return modules;
    }

    /**
     * Gets and remove the named system property
     */
    private static String getAndRemoveProperty(String key) {
        return (String)System.getProperties().remove(key);
    }
}
