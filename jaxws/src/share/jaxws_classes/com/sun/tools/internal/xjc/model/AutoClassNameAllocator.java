/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.model;

import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;

import com.sun.tools.internal.xjc.api.ClassNameAllocator;

/**
 * {@link ClassNameAllocator} filter that performs automatic name conflict resolution.
 *
 * @author Kohsuke Kawaguchi
 */
public class AutoClassNameAllocator implements ClassNameAllocator {
    private final ClassNameAllocator core;

    private final Map<String,Set<String>> names = new HashMap<String,Set<String>>();

    public AutoClassNameAllocator(ClassNameAllocator core) {
        this.core = core;
    }

    public String assignClassName(String packageName, String className) {
        className = determineName(packageName, className);
        if(core!=null)
            className = core.assignClassName(packageName,className);
        return className;
    }

    private String determineName(String packageName, String className) {
        Set<String> s = names.get(packageName);
        if(s==null) {
            s = new HashSet<String>();
            names.put(packageName,s);
        }

        if(s.add(className))
            return className;

        for(int i=2;true;i++) {
            if(s.add(className+i))
                return className+i;
        }
    }
}
