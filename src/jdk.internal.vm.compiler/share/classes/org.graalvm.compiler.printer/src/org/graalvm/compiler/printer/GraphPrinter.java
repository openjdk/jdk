/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.printer;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Scope;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.util.JavaConstantFormatter;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.serviceprovider.GraalServices;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.ResolvedJavaMethod;

interface GraphPrinter extends Closeable, JavaConstantFormatter {

    /**
     * Starts a new group of graphs with the given name, short name and method byte code index (BCI)
     * as properties.
     */
    void beginGroup(DebugContext debug, String name, String shortName, ResolvedJavaMethod method, int bci, Map<Object, Object> properties) throws IOException;

    /**
     * Prints an entire {@link Graph} with the specified title, optionally using short names for
     * nodes.
     */
    void print(DebugContext debug, Graph graph, Map<Object, Object> properties, int id, String format, Object... args) throws IOException;

    SnippetReflectionProvider getSnippetReflectionProvider();

    /**
     * Ends the current group.
     */
    void endGroup() throws IOException;

    @Override
    void close();

    /**
     * Classes whose {@link #toString()} method does not run any untrusted code.
     */
    List<Class<?>> TRUSTED_CLASSES = Arrays.asList(
                    String.class,
                    Class.class,
                    Boolean.class,
                    Byte.class,
                    Character.class,
                    Short.class,
                    Integer.class,
                    Float.class,
                    Long.class,
                    Double.class);
    int MAX_CONSTANT_TO_STRING_LENGTH = 50;

    /**
     * Determines if invoking {@link Object#toString()} on an instance of {@code c} will only run
     * trusted code.
     */
    static boolean isToStringTrusted(Class<?> c) {
        if (TRUSTED_CLASSES.contains(c)) {
            return true;
        }
        if (GraalServices.isToStringTrusted(c)) {
            return true;
        }
        if (c.getClassLoader() == GraphPrinter.class.getClassLoader()) {
            return true;
        }
        return false;
    }

    /**
     * Use the real {@link Object#toString()} method for {@link JavaConstant JavaConstants} that are
     * wrapping trusted types, other just return the results of {@link JavaConstant#toString()}.
     */
    @Override
    default String format(JavaConstant constant) {
        SnippetReflectionProvider snippetReflection = getSnippetReflectionProvider();
        if (snippetReflection != null) {
            if (constant.getJavaKind() == JavaKind.Object) {
                Object obj = null;
                /*
                 * Ignore any exceptions on unknown JavaConstant implementations in debugging code.
                 */
                try {
                    obj = snippetReflection.asObject(Object.class, constant);
                } catch (Throwable ex) {
                }
                if (obj != null) {
                    return GraphPrinter.constantToString(obj);
                }
            }
        }
        return constant.toString();
    }

    /**
     * Sets or updates the {@code "rawvalue"} and {@code "toString"} properties in {@code props} for
     * {@code cn} if it's a boxed Object value and {@code snippetReflection} can access the raw
     * value.
     */
    default void updateStringPropertiesForConstant(Map<Object, Object> props, ConstantNode cn) {
        if (cn.isJavaConstant() && cn.getStackKind().isObject()) {
            String toString = format(cn.asJavaConstant());
            String rawvalue = GraphPrinter.truncate(toString);
            // Overwrite the value inserted by
            // ConstantNode.getDebugProperties()
            props.put("rawvalue", rawvalue);
            if (!rawvalue.equals(toString)) {
                props.put("toString", toString);
            }
        }
    }

    /**
     * Replaces all {@link JavaType} elements in {@code args} with the result of
     * {@link JavaType#getUnqualifiedName()}.
     *
     * @return a copy of {@code args} with the above mentioned substitutions or {@code args} if no
     *         substitutions were performed
     */
    default Object[] simplifyClassArgs(Object... args) {
        Object[] res = args;
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof JavaType) {
                if (args == res) {
                    res = new Object[args.length];
                    for (int a = 0; a < i; a++) {
                        res[a] = args[a];
                    }
                }
                res[i] = ((JavaType) arg).getUnqualifiedName();
            } else {
                res[i] = arg;
            }
        }
        return res;
    }

    static String truncate(String s) {
        if (s.length() > MAX_CONSTANT_TO_STRING_LENGTH) {
            return s.substring(0, MAX_CONSTANT_TO_STRING_LENGTH - 3) + "...";
        }
        return s;
    }

    static String constantToString(Object value) {
        Class<?> c = value.getClass();
        String suffix = "";
        if (c.isArray()) {
            return constantArrayToString(value);
        } else if (value instanceof Enum) {
            return ((Enum<?>) value).name();
        } else if (isToStringTrusted(c)) {
            try {
                return value.toString();
            } catch (Throwable t) {
                suffix = "[toString error: " + t.getClass().getName() + "]";
                if (isToStringTrusted(t.getClass())) {
                    try {
                        suffix = "[toString error: " + t + "]";
                    } catch (Throwable t2) {
                        // No point in going further
                    }
                }
            }
        }
        return MetaUtil.getSimpleName(c, true) + "@" + Integer.toHexString(System.identityHashCode(value)) + suffix;
    }

    static String constantArrayToString(Object array) {
        Class<?> componentType = array.getClass().getComponentType();
        assert componentType != null;
        int arrayLength = Array.getLength(array);
        StringBuilder buf = new StringBuilder(MetaUtil.getSimpleName(componentType, true)).append('[').append(arrayLength).append("]{");
        int length = arrayLength;
        boolean primitive = componentType.isPrimitive();
        for (int i = 0; i < length; i++) {
            if (primitive) {
                buf.append(Array.get(array, i));
            } else {
                Object o = ((Object[]) array)[i];
                buf.append(o == null ? "null" : constantToString(o));
            }
            if (i != length - 1) {
                buf.append(", ");
            }
        }
        return buf.append('}').toString();
    }

    @SuppressWarnings("try")
    static StructuredGraph.ScheduleResult getScheduleOrNull(Graph graph) {
        if (graph instanceof StructuredGraph) {
            StructuredGraph sgraph = (StructuredGraph) graph;
            StructuredGraph.ScheduleResult scheduleResult = sgraph.getLastSchedule();
            if (scheduleResult == null) {
                DebugContext debug = graph.getDebug();
                try (Scope scope = debug.disable()) {
                    SchedulePhase schedule = new SchedulePhase(graph.getOptions());
                    schedule.apply(sgraph);
                    scheduleResult = sgraph.getLastSchedule();
                } catch (Throwable t) {
                }
            }
            return scheduleResult;
        }
        return null;
    }
}
