/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * TestCase contains source code to be compiled
 * and expected lines to be covered by a line number table attribute.
 */
public class TestCase {
    public final String src;
    public final List<String> extraCompilerOptions;


    private final String name;
    private final MethodData[] methodData;

    public String getName() {
        return name;
    }

    public TestCase(String src, Collection<Integer> expectedLines, String name) {
        this(src, name, new MethodData(null, expectedLines, false));
    }

    public TestCase(String src, String name, MethodData... methodData) {
        this(src, List.of(), name, methodData);
    }

    public TestCase(String src, Collection<Integer> expectedLines,
                    boolean exactLines, List<String> extraCompilerOptions,
                    String name) {
        this(src, extraCompilerOptions, name, new MethodData(null, expectedLines, exactLines));
    }

    public TestCase(String src, List<String> extraCompilerOptions,
                    String name, MethodData... methodData) {
        this.src = src;
        this.extraCompilerOptions = extraCompilerOptions;
        this.name = name;
        this.methodData = methodData;
    }

    public MethodData findData(String methodName) {
        for (MethodData md : methodData) {
            if (Objects.equals(md.methodName(), methodName)) {
                return md;
            }
        }

        return null;
    }

    record MethodData(String methodName, Collection<Integer> expectedLines, boolean exactLines) {

        public MethodData {
            expectedLines = new HashSet<>(expectedLines);
        }

    }
}
