/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.jshell.jdi;

import java.util.HashMap;
import java.util.Objects;
import com.sun.jdi.ReferenceType;
import java.util.List;
import com.sun.jdi.VirtualMachine;

/**
 * Tracks the state of a class.
 */
class ClassTracker {

    private final VirtualMachine vm;
    private final HashMap<String, ClassInfo> map;

    ClassTracker(VirtualMachine vm) {
        this.vm = vm;
        this.map = new HashMap<>();
    }

    /**
     * Associates a class name, class bytes, and ReferenceType.
     */
    class ClassInfo {

        // The name of the class -- always set
        private final String className;

        // The corresponding compiled class bytes when a load or redefine
        // is started.  May not be the loaded bytes.  May be null.
        private byte[] bytes;

        // The class bytes successfully loaded/redefined into the remote VM.
        private byte[] loadedBytes;

        // The corresponding JDI ReferenceType.  Used by redefineClasses and
        // acts as indicator of successful load (null if not loaded).
        private ReferenceType rt;

        private ClassInfo(String className) {
            this.className = className;
        }

        String getClassName() {
            return className;
        }

        byte[] getLoadedBytes() {
            return loadedBytes;
        }

        byte[] getBytes() {
            return bytes;
        }

        private void setBytes(byte[] potentialBytes) {
            this.bytes = potentialBytes;
        }

        // The class has been successful loaded redefined.  The class bytes
        // sent are now actually loaded.
        void markLoaded() {
            loadedBytes = bytes;
        }

        // Ask JDI for the ReferenceType, null if not loaded.
        ReferenceType getReferenceTypeOrNull() {
            if (rt == null) {
                rt = nameToRef(className);
            }
            return rt;
        }

        private ReferenceType nameToRef(String name) {
            List<ReferenceType> rtl = vm.classesByName(name);
            if (rtl.size() != 1) {
                return null;
            }
            return rtl.get(0);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ClassInfo
                    && ((ClassInfo) o).className.equals(className);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(this.className);
        }
    }

    // Map a class name to the current compiled class bytes.
    ClassInfo classInfo(String className, byte[] bytes) {
        ClassInfo ci = get(className);
        ci.setBytes(bytes);
        return ci;
    }

    // Lookup the ClassInfo by class name, create if it does not exist.
    ClassInfo get(String className) {
        return map.computeIfAbsent(className, k -> new ClassInfo(k));
    }
}
