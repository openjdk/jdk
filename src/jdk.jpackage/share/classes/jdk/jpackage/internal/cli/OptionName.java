/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.cli;

import java.util.Objects;

record OptionName(String name) implements Comparable<OptionName> {

    OptionName(String name) {
        Objects.requireNonNull(name);
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Name should not be empty");
        }

        if (name.charAt(0) != '-') {
            this.name = name;
        } else if (name.length() == 1) {
            throw new IllegalArgumentException("Short option without a name");
        } else if (name.charAt(1) != '-') {
            // Reverse operation for a short option name
            this.name = name.substring(1, 2);
        } else if (name.length() == 2) {
            throw new IllegalArgumentException("Long option without a name");
        } else {
            // Reverse operation for a long option name
            this.name = name.substring(2);
        }
    }

    static OptionName of(String name) {
        return new OptionName(name);
    }

    boolean isShort() {
        return name.length() == 1;
    }

    String formatForCommandLine() {
        return (isShort() ? "-" : "--") + name;
    }

    @Override
    public int compareTo(OptionName other) {
        return name.compareTo(other.name);
    }
}
