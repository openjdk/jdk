/*
 * Copyright 1999-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.awt;

import sun.util.logging.PlatformLogger;

abstract class AttributeValue {
    private static final PlatformLogger log = PlatformLogger.getLogger("java.awt.AttributeValue");
    private final int value;
    private final String[] names;

    protected AttributeValue(int value, String[] names) {
        if (log.isLoggable(PlatformLogger.FINEST)) {
            log.finest("value = " + value + ", names = " + names);
        }

        if (log.isLoggable(PlatformLogger.FINER)) {
            if ((value < 0) || (names == null) || (value >= names.length)) {
                log.finer("Assertion failed");
            }
        }
        this.value = value;
        this.names = names;
    }
    // This hashCode is used by the sun.awt implementation as an array
    // index.
    public int hashCode() {
        return value;
    }
    public String toString() {
        return names[value];
    }
}
