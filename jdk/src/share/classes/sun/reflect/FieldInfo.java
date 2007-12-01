/*
 * Copyright 2001 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** NOTE: obsolete as of JDK 1.4 B75 and should be removed from the
    workspace (FIXME) */

public class FieldInfo {
    // Set by the VM directly. Do not move these fields around or add
    // others before (or after) them without also modifying the VM's code.
    private String name;
    private String signature;
    private int    modifiers;
    // This is compatible with the old reflection implementation's
    // "slot" value to allow sun.misc.Unsafe to work
    private int    slot;

    // Not really necessary to provide a constructor since the VM
    // creates these directly
    FieldInfo() {
    }

    public String name() {
        return name;
    }

    /** This is in "external" format, i.e. having '.' as separator
        rather than '/' */
    public String signature() {
        return signature;
    }

    public int modifiers() {
        return modifiers;
    }

    public int slot() {
        return slot;
    }

    /** Convenience routine */
    public boolean isPublic() {
        return (Modifier.isPublic(modifiers()));
    }
}
