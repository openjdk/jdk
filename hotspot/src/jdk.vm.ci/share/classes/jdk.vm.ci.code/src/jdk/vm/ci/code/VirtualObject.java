/*
 * Copyright (c) 2010, 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.code;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaValue;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * An instance of this class represents an object whose allocation was removed by escape analysis.
 * The information stored in the {@link VirtualObject} is used during deoptimization to recreate the
 * object.
 */
public final class VirtualObject implements JavaValue {

    private final ResolvedJavaType type;
    private JavaValue[] values;
    private JavaKind[] slotKinds;
    private final int id;

    /**
     * Creates a new {@link VirtualObject} for the given type, with the given fields. If
     * {@code type} is an instance class then {@code values} provides the values for the fields
     * returned by {@link ResolvedJavaType#getInstanceFields(boolean) getInstanceFields(true)}. If
     * {@code type} is an array then the length of the values array determines the reallocated array
     * length.
     *
     * @param type the type of the object whose allocation was removed during compilation. This can
     *            be either an instance of an array type.
     * @param id a unique id that identifies the object within the debug information for one
     *            position in the compiled code.
     * @return a new {@link VirtualObject} instance.
     */
    public static VirtualObject get(ResolvedJavaType type, int id) {
        return new VirtualObject(type, id);
    }

    private VirtualObject(ResolvedJavaType type, int id) {
        this.type = type;
        this.id = id;
    }

    private static StringBuilder appendValue(StringBuilder buf, JavaValue value, Set<VirtualObject> visited) {
        if (value instanceof VirtualObject) {
            VirtualObject vo = (VirtualObject) value;
            buf.append("vobject:").append(vo.type.toJavaName(false)).append(':').append(vo.id);
            if (!visited.contains(vo)) {
                visited.add(vo);
                buf.append('{');
                if (vo.values == null) {
                    buf.append("<uninitialized>");
                } else {
                    if (vo.type.isArray()) {
                        for (int i = 0; i < vo.values.length; i++) {
                            if (i != 0) {
                                buf.append(',');
                            }
                            buf.append(i).append('=');
                            appendValue(buf, vo.values[i], visited);
                        }
                    } else {
                        ResolvedJavaField[] fields = vo.type.getInstanceFields(true);
                        assert fields.length == vo.values.length : vo.type + ", fields=" + Arrays.toString(fields) + ", values=" + Arrays.toString(vo.values);
                        for (int i = 0; i < vo.values.length; i++) {
                            if (i != 0) {
                                buf.append(',');
                            }
                            buf.append(fields[i].getName()).append('=');
                            appendValue(buf, vo.values[i], visited);
                        }
                    }
                }
                buf.append('}');
            }
        } else {
            buf.append(value);
        }
        return buf;
    }

    @Override
    public String toString() {
        Set<VirtualObject> visited = Collections.newSetFromMap(new IdentityHashMap<VirtualObject, Boolean>());
        return appendValue(new StringBuilder(), this, visited).toString();
    }

    /**
     * Returns the type of the object whose allocation was removed during compilation. This can be
     * either an instance of an array type.
     */
    public ResolvedJavaType getType() {
        return type;
    }

    /**
     * Returns the array containing all the values to be stored into the object when it is
     * recreated. This field is intentional exposed as a mutable array that a compiler may modify
     * (e.g. during register allocation).
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "`values` is intentional mutable")//
    public JavaValue[] getValues() {
        return values;
    }

    /**
     * Returns the kind of the value at {@code index}.
     */
    public JavaKind getSlotKind(int index) {
        return slotKinds[index];
    }

    /**
     * Returns the unique id that identifies the object within the debug information for one
     * position in the compiled code.
     */
    public int getId() {
        return id;
    }

    /**
     * Overwrites the current set of values with a new one.
     *
     * @param values an array containing all the values to be stored into the object when it is
     *            recreated.
     * @param slotKinds an array containing the Java kinds of the values. This must have the same
     *            length as {@code values}. This array is now owned by this object and must not be
     *            mutated by the caller.
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "caller transfers ownership of `slotKinds`")
    public void setValues(JavaValue[] values, JavaKind[] slotKinds) {
        assert values.length == slotKinds.length;
        this.values = values;
        this.slotKinds = slotKinds;
    }

    @Override
    public int hashCode() {
        return 42 + type.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof VirtualObject) {
            VirtualObject l = (VirtualObject) o;
            if (!l.type.equals(type) || l.values.length != values.length) {
                return false;
            }
            for (int i = 0; i < values.length; i++) {
                /*
                 * Virtual objects can form cycles. Calling equals() could therefore lead to
                 * infinite recursion.
                 */
                if (!same(values[i], l.values[i])) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static boolean same(Object o1, Object o2) {
        return o1 == o2;
    }
}
