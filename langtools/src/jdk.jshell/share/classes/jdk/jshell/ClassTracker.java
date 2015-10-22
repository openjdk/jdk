/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jshell;

import java.util.Arrays;
import java.util.HashMap;
import com.sun.jdi.ReferenceType;

/**
 * Tracks the state of a class through compilation and loading in the remote
 * environment.
 *
 * @author Robert Field
 */
class ClassTracker {

    private final JShell state;
    private final HashMap<String, ClassInfo> map;

    ClassTracker(JShell state) {
        this.state = state;
        this.map = new HashMap<>();
    }

    class ClassInfo {

        private final String className;
        private byte[] bytes;
        private byte[] loadedBytes;
        private ReferenceType rt;

        private ClassInfo(String className) {
            this.className = className;
        }

        String getClassName() {
            return className;
        }

        byte[] getBytes() {
            return bytes;
        }

        void setBytes(byte[] bytes) {
            this.bytes = bytes;
        }

        void setLoaded() {
            loadedBytes = bytes;
        }

        boolean isLoaded() {
            return Arrays.equals(loadedBytes, bytes);
        }

        ReferenceType getReferenceTypeOrNull() {
            if (rt == null) {
                rt = state.executionControl().nameToRef(className);
            }
            return rt;
        }
    }

    ClassInfo classInfo(String className, byte[] bytes) {
        ClassInfo ci = map.computeIfAbsent(className, k -> new ClassInfo(k));
        ci.setBytes(bytes);
        return ci;
    }

    ClassInfo get(String className) {
        return map.get(className);
    }

}
