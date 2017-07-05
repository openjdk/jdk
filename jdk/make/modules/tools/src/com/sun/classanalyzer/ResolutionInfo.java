/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package com.sun.classanalyzer;

import com.sun.classanalyzer.Klass.Method;

/**
 *
 * @author mchung
 */
public class ResolutionInfo implements Comparable<ResolutionInfo> {

    enum Type {

        REFLECTION("reflection", true),
        NATIVE("native", true),
        INTERFACE("interface", false),
        SUPER("super", false),
        EXPLICIT("explicit", false),
        VERIFICATION("verification", false),
        METHODTRACE("method trace", true),
        CONSTANT_POOL("constant pool", true),
        CHECKED_EXCEPTION("throws", true),
        METHOD("method", true),
        FIELD("field", true),
        EXTENDS("extends", true),
        IMPLEMENTS("implements", true),
        NOINFO("No info", false);

        private final String name;
        private final boolean hasInfo;

        private Type(String name, boolean hasInfo) {
            this.name = name;
            this.hasInfo = hasInfo;
        }

        public String getName() {
            return name;
        }

        public boolean hasInfo() {
            return hasInfo;
        }

        public static Type getType(String s) {
            if (s.isEmpty()) {
                return NOINFO;
            }
            for (Type t : values()) {
                if (s.equals(t.name)) {
                    return t;
                }
            }
            // Need to fix the VM output to add "native"
            // throw new IllegalArgumentException("Invalid ResolutionInfo.type \"" + s + "\"");
            System.out.println("WARNING: Invalid ResolutionInfo.type \"" + s + "\"");
            return null;
        }
    }
    final Klass fromClass;
    final Method method;
    final Klass toClass;
    final int linenumber;
    final Type type;
    final String info;
    private boolean isPublic = false;

    private ResolutionInfo(Klass from, Klass to, int linenumber, Type type, String info) {
        this.fromClass = from;
        this.method = null;
        this.toClass = to;
        this.linenumber = linenumber;
        this.type = type;
        this.info = info;
    }

    private ResolutionInfo(Klass from, Method m, Klass to, int linenumber, Type type) {
        this.fromClass = from;
        this.method = m;
        this.toClass = to;
        this.linenumber = linenumber;
        this.type = type;
        this.info = m.toString();
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublicAccess(boolean value) {
        isPublic = value;
    }
    static ResolutionInfo resolved(Klass from, Klass to) {
        return new ResolutionInfo(from, to, 0, Type.NOINFO, "");
    }

    static ResolutionInfo resolved(Klass from, Klass to, int linenumber) {
        return new ResolutionInfo(from, to, linenumber, Type.NOINFO, "");
    }

    static ResolutionInfo resolved(Klass from, Klass to, int linenumber, String reason) {
        String[] ss = reason.split("\\s+");
        Type type;
        String info;
        if (linenumber == -1) {
            type = Type.NATIVE;
            info = ss[0];  // native method name
        } else {
            info = ss.length == 2 ? ss[1] : "";
            type = Type.getType(ss[0]);
            if (type == null) {
                if (reason.isEmpty()) {
                    throw new IllegalArgumentException("Invalid type: " + reason + " (" + ss[0] + ")" + ss.length);
                }
                // assume it's native
                type = Type.NATIVE;
                info = reason.isEmpty() ? ss[0] : reason;
            }
        }

        return new ResolutionInfo(from, to, linenumber, type, info);
    }

    static ResolutionInfo resolved(Klass from, Klass to, Method callee) {
        return new ResolutionInfo(from, callee, to, 0, Type.METHODTRACE);
    }

    static ResolutionInfo resolvedConstantPool(Klass from, Klass to, int index) {
        return new ResolutionInfo(from, to, 0, Type.CONSTANT_POOL, "#" + index);
    }

    static ResolutionInfo resolvedField(Klass from, Klass to, String fieldname) {
        return new ResolutionInfo(from, to, 0, Type.FIELD, fieldname);
    }

    static ResolutionInfo resolvedMethodSignature(Klass from, Klass to, Method m) {
        return new ResolutionInfo(from, m, to, 0, Type.METHOD);
    }

    static ResolutionInfo resolvedCheckedException(Klass from, Klass to, Method m) {
        return new ResolutionInfo(from, m, to, 0, Type.CHECKED_EXCEPTION);
    }

    static ResolutionInfo resolvedExtends(Klass from, Klass to) {
        String info = from.getClassName() + " implements " + to.getClassName();
        return new ResolutionInfo(from, to, 0, Type.EXTENDS, info);
    }

    static ResolutionInfo resolvedImplements(Klass from, Klass to) {
        String info = from.getClassName() + " implements " + to.getClassName();
        return new ResolutionInfo(from, to, 0, Type.IMPLEMENTS, info);
    }

    @Override
    public int compareTo(ResolutionInfo ri) {
        if (this.fromClass == ri.fromClass &&
                this.toClass == ri.toClass &&
                this.linenumber == ri.linenumber &&
                this.type == ri.type &&
                this.info.equals(ri.info)) {
            return 0;
        } else if (this.fromClass == ri.fromClass) {
            if (this.linenumber > ri.linenumber) {
                return 1;
            } else if (this.linenumber < ri.linenumber) {
                return -1;
            } else if (this.type != ri.type) {
                return this.type.getName().compareTo(ri.type.getName());
            } else if (this.toClass != ri.toClass) {
                return this.toClass.compareTo(ri.toClass);
            } else {
                return this.info.compareTo(ri.info);
            }
        } else {
            return this.fromClass.compareTo(ri.fromClass);
        }
    }
}
