/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.awt;

import jdk.internal.util.OperatingSystem;

import java.util.HashMap;
import java.util.Map;

import static sun.awt.OSInfo.OSType.*;

/**
 * @author Pavel Porvatov
 */
public class OSInfo {
    public static enum OSType {
        WINDOWS,
        LINUX,
        MACOSX,
        AIX,
        UNKNOWN
    }

    /*
       The map windowsVersionMap must contain all windows version constants except WINDOWS_UNKNOWN,
       and so the method getWindowsVersion() will return the constant for known OS.
       It allows compare objects by "==" instead of "equals".
     */
    public static final WindowsVersion WINDOWS_UNKNOWN = new WindowsVersion(-1, -1);

    private static final String OS_VERSION = "os.version";

    private static final Map<String, WindowsVersion> windowsVersionMap = new HashMap<String, OSInfo.WindowsVersion>();

    // Cache the OSType for getOSType()
    private static final OSType CURRENT_OSTYPE = getOSTypeImpl();

    private OSInfo() {
        // Don't allow to create instances
    }

    /**
     * Returns type of operating system.
     */
    public static OSType getOSType() {
        return CURRENT_OSTYPE;
    }

    private static OSType getOSTypeImpl() {
        OperatingSystem os = OperatingSystem.current();
        return switch (os) {
            // Map OperatingSystem enum values to OSType enum values.
            case WINDOWS -> WINDOWS;
            case LINUX -> LINUX;
            case MACOS -> MACOSX;
            case AIX -> AIX;
            default -> UNKNOWN;
        };
    }

    public static WindowsVersion getWindowsVersion() {
        String osVersion = System.getProperty(OS_VERSION);

        if (osVersion == null) {
            return WINDOWS_UNKNOWN;
        }

        synchronized (windowsVersionMap) {
            WindowsVersion result = windowsVersionMap.get(osVersion);

            if (result == null) {
                // Try parse version and put object into windowsVersionMap
                String[] arr = osVersion.split("\\.");

                if (arr.length == 2) {
                    try {
                        result = new WindowsVersion(Integer.parseInt(arr[0]), Integer.parseInt(arr[1]));
                    } catch (NumberFormatException e) {
                        return WINDOWS_UNKNOWN;
                    }
                } else {
                    return WINDOWS_UNKNOWN;
                }

                windowsVersionMap.put(osVersion, result);
            }

            return result;
        }
    }

    public static class WindowsVersion implements Comparable<WindowsVersion> {
        private final int major;

        private final int minor;

        private WindowsVersion(int major, int minor) {
            this.major = major;
            this.minor = minor;
        }

        public int getMajor() {
            return major;
        }

        public int getMinor() {
            return minor;
        }

        public int compareTo(WindowsVersion o) {
            int result = major - o.getMajor();

            if (result == 0) {
                result = minor - o.getMinor();
            }

            return result;
        }

        public boolean equals(Object obj) {
            return obj instanceof WindowsVersion && compareTo((WindowsVersion) obj) == 0;
        }

        public int hashCode() {
            return 31 * major + minor;
        }

        public String toString() {
            return major + "." + minor;
        }
    }
}
