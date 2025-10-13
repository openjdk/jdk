/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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


/*
 * The Original Code is HAT. The Initial Developer of the
 * Original Code is Bill Foote, with contributions from others
 * at JavaSoft/Sun.
 */

package jdk.test.lib.hprof.model;

import jdk.test.lib.hprof.util.Misc;

/**
 * Represents a member of the rootset, that is, one of the objects that
 * the GC starts from when marking reachable objects.
 */

public class Root {

    private long id;            // ID of the JavaThing we refer to
    private long referrerId;    // Thread or Class responsible for this, or 0
    private int index = -1;     // Index in Snapshot.roots
    private int type;
    private String description;
    private JavaHeapObject referrer = null;
    private StackTrace stackTrace = null;

    // Values for type.  Higher values are more interesting -- see getType().
    // See also getTypeName()
    public final static int INVALID_TYPE = 0;
    public final static int UNKNOWN = 1;
    public final static int SYSTEM_CLASS = 2;

    public final static int NATIVE_LOCAL = 3;
    public final static int NATIVE_STATIC = 4;
    public final static int THREAD_BLOCK = 5;
    public final static int BUSY_MONITOR = 6;
    public final static int JAVA_LOCAL = 7;
    public final static int NATIVE_STACK = 8;
    public final static int JAVA_STATIC = 9;


    public Root(long id, long referrerId, int type, String description) {
        this(id, referrerId, type, description, null);
    }


    public Root(long id, long referrerId, int type, String description,
                StackTrace stackTrace) {
        this.id = id;
        this.referrerId = referrerId;
        this.type = type;
        this.description = description;
        this.stackTrace = stackTrace;
    }

    public long getId() {
        return id;
    }

    public String getIdString() {
        return Misc.toHex(id);
    }

    public String getDescription() {
        if ("".equals(description)) {
            return getTypeName() + " Reference";
        } else {
            return description;
        }
    }

    /**
     * Return type.  We guarantee that more interesting roots will have
     * a type that is numerically higher.
     */
    public int getType() {
        return type;
    }

    public String getTypeName() {
        switch(type) {
            case INVALID_TYPE:          return "Invalid (?!?)";
            case UNKNOWN:               return "Unknown";
            case SYSTEM_CLASS:          return "System Class";
            case NATIVE_LOCAL:          return "JNI Local";
            case NATIVE_STATIC:         return "JNI Global";
            case THREAD_BLOCK:          return "Thread Block";
            case BUSY_MONITOR:          return "Busy Monitor";
            case JAVA_LOCAL:            return "Java Local";
            case NATIVE_STACK:          return "Native Stack (possibly Java local)";
            case JAVA_STATIC:           return "Java Static";
            default:                    return "??";
        }
    }

    /**
     * Given two Root instances, return the one that is most interesting.
     */
    public Root mostInteresting(Root other) {
        if (other.type > this.type) {
            return other;
        } else {
            return this;
        }
    }

    /**
     * Get the object that's responsible for this root, if there is one.
     * This will be null, a Thread object, or a Class object.
     */
    public JavaHeapObject getReferrer() {
        return referrer;
    }

    public long getReferrerId() {
        return referrerId;
    }

    /**
     * @return the stack trace responsible for this root, or null if there
     * is none.
     */
    public StackTrace getStackTrace() {
        return stackTrace;
    }

    /**
     * @return The index of this root in Snapshot.roots
     */
    public int getIndex() {
        return index;
    }

    void resolve(Snapshot ss) {
        if (referrerId != 0) {
            referrer = ss.findThing(referrerId);
        }
        if (stackTrace != null) {
            stackTrace.resolve(ss);
        }
    }

    void setIndex(int i) {
        index = i;
    }

}
