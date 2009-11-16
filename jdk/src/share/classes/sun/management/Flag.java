/*
 * Copyright 2003-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.management;

import java.util.*;
import com.sun.management.VMOption;
import com.sun.management.VMOption.Origin;

/**
 * Flag class is a helper class for constructing a VMOption.
 * It has the static methods for getting the Flag objects, each
 * corresponds to one VMOption.
 *
 */
class Flag {
    private String name;
    private Object value;
    private Origin origin;
    private boolean writeable;
    private boolean external;

    Flag(String name, Object value, boolean writeable,
         boolean external, Origin origin) {
        this.name = name;
        this.value = value == null ? "" : value ;
        this.origin = origin;
        this.writeable = writeable;
        this.external = external;
    }

    Object getValue() {
        return value;
    }

    boolean isWriteable() {
        return writeable;
    }

    boolean isExternal() {
        return external;
    }

    VMOption getVMOption() {
        return new VMOption(name, value.toString(), writeable, origin);
    }

    static Flag getFlag(String name) {
        Flag[] fs = new Flag[1];
        String[] names = new String[1];
        names[0] = name;
        int count = getFlags(names, fs, 1);
        if (count == 1) {
            return fs[0];
        } else {
            return null;
        }
    }

    static List<Flag> getAllFlags() {
        int numFlags = getInternalFlagCount();
        Flag[] fs = new Flag[numFlags];

        // Get all internal flags with names = null
        int count = getFlags(null, fs, numFlags);
        return Arrays.asList(fs);
    }

    private static native String[] getAllFlagNames();
    private static native int getFlags(String[] names, Flag[] flags, int count);
    private static native int getInternalFlagCount();

    // These set* methods are synchronized on the class object
    // to avoid multiple threads updating the same flag at the same time.
    static synchronized native void setLongValue(String name, long value);
    static synchronized native void setBooleanValue(String name, boolean value);
    static synchronized native void setStringValue(String name, String value);

    static {
        initialize();
    }
    private static native void initialize();
}
