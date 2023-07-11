/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jlink.internal;

import jdk.internal.util.Architecture;
import jdk.internal.util.OperatingSystem;

import java.util.Locale;

/**
 * Supported OperatingSystem and Architecture.
 */
public record Platform(OperatingSystem os, Architecture arch) {

    /*
     * Returns the {@code Platform} based on the platformString of the form <operating system>-<arch>.
     * @throws IllegalArgumentException if the delimiter is missing or either OS or
     * architecture is not known
     */
    public static Platform parsePlatform(String platformString) {
        String osName;
        String archName;
        int index = platformString.indexOf("-");
        if (index < 0) {
            throw new IllegalArgumentException("platformString missing delimiter: " + platformString);
        }
        osName = platformString.substring(0, index);
        OperatingSystem os = OperatingSystem.valueOf(osName.toUpperCase(Locale.ROOT));

        archName = platformString.substring(index + 1);
        // Alias architecture names, if needed
        archName = archName.replace("amd64", "X64");
        archName = archName.replace("s390x", "S390");
        Architecture arch = Architecture.valueOf(archName.toUpperCase(Locale.ROOT));

        return new Platform(os, arch);
    }

    /**
     * {@return the runtime {@code Platform}}
     */
    public static Platform runtime() {
        return new Platform(OperatingSystem.current(), Architecture.current());
    }

    /**
     * Returns a {@code String} representation of a {@code Platform} in the format of <os>-<arch>
     */
    @Override
    public String toString() {
        return os.toString().toLowerCase(Locale.ROOT) + "-" + arch.toString().toLowerCase(Locale.ROOT);
    }
}
