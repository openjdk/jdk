/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.apple.internal.jobjc.generator;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class RestrictedKeywords {
    static final String[] JAVA_KEYWORD_CONFLICTS = {
        "wait", "null", "class", "new", "toString", "finalize", "boolean", "interface", "final", "static"
    };

    static final Set<String> originalRestrictedSet = new HashSet<String>(Arrays.asList(JAVA_KEYWORD_CONFLICTS));

    public static Set<String> getNewRestrictedSet() {
        return new HashSet<String>(originalRestrictedSet);
    }

    public static boolean isRestricted(String s){
        return originalRestrictedSet.contains(s);
    }
}
