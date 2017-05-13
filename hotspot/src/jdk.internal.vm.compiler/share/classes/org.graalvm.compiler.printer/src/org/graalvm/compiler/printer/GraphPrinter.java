/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.core.common.util.Util.JAVA_SPECIFICATION_VERSION;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.util.ModuleAPI;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.nodes.ConstantNode;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.services.Services;

interface GraphPrinter extends Closeable {

    /**
     * Starts a new group of graphs with the given name, short name and method byte code index (BCI)
     * as properties.
     */
    void beginGroup(String name, String shortName, ResolvedJavaMethod method, int bci, Map<Object, Object> properties) throws IOException;

    /**
     * Prints an entire {@link Graph} with the specified title, optionally using short names for
     * nodes.
     */
    void print(Graph graph, String title, Map<Object, Object> properties) throws IOException;

    SnippetReflectionProvider getSnippetReflectionProvider();

    /**
     * Ends the current group.
     */
    void endGroup() throws IOException;

    @Override
    void close();

    /**
     * A JVMCI package dynamically exported to trusted modules.
     */
    String JVMCI_RUNTIME_PACKAGE = JVMCI.class.getPackage().getName();

    /**
     * {@code jdk.vm.ci} module.
     */
    Object JVMCI_MODULE = JAVA_SPECIFICATION_VERSION < 9 ? null : ModuleAPI.getModule.invoke(Services.class);

    /**
     * Classes whose {@link #toString()} method does not run any untrusted code.
     */
    Set<Class<?>> TRUSTED_CLASSES = new HashSet<>(Arrays.asList(
                    String.class,
                    Class.class,
                    Boolean.class,
                    Byte.class,
                    Character.class,
                    Short.class,
                    Integer.class,
                    Float.class,
                    Long.class,
                    Double.class));
    int MAX_CONSTANT_TO_STRING_LENGTH = 50;

    /**
     * Determines if invoking {@link Object#toString()} on an instance of {@code c} will only run
     * trusted code.
     */
    static boolean isToStringTrusted(Class<?> c) {
        if (TRUSTED_CLASSES.contains(c)) {
            return true;
        }
        if (JAVA_SPECIFICATION_VERSION < 9) {
            if (c.getClassLoader() == Services.class.getClassLoader()) {
                // Loaded by the JVMCI class loader
                return true;
            }
        } else {
            Object module = ModuleAPI.getModule.invoke(c);
            if (JVMCI_MODULE == module || (Boolean) ModuleAPI.isExportedTo.invoke(JVMCI_MODULE, JVMCI_RUNTIME_PACKAGE, module)) {
                // Can access non-statically-exported package in JVMCI
                return true;
            }
        }
        return false;
    }

    /**
     * Sets or updates the {@code "rawvalue"} and {@code "toString"} properties in {@code props} for
     * {@code cn} if it's a boxed Object value and {@code snippetReflection} can access the raw
     * value.
     */
    default void updateStringPropertiesForConstant(Map<Object, Object> props, ConstantNode cn) {
        SnippetReflectionProvider snippetReflection = getSnippetReflectionProvider();
        if (snippetReflection != null && cn.getValue() instanceof JavaConstant) {
            JavaConstant constant = (JavaConstant) cn.getValue();
            if (constant.getJavaKind() == JavaKind.Object) {
                Object obj = snippetReflection.asObject(Object.class, constant);
                if (obj != null) {
                    String toString = GraphPrinter.constantToString(obj);
                    String rawvalue = GraphPrinter.truncate(toString);
                    // Overwrite the value inserted by
                    // ConstantNode.getDebugProperties()
                    props.put("rawvalue", rawvalue);
                    if (!rawvalue.equals(toString)) {
                        props.put("toString", toString);
                    }
                }
            }
        }
    }

    static String truncate(String s) {
        if (s.length() > MAX_CONSTANT_TO_STRING_LENGTH) {
            return s.substring(0, MAX_CONSTANT_TO_STRING_LENGTH - 3) + "...";
        }
        return s;
    }

    static String constantToString(Object value) {
        Class<?> c = value.getClass();
        if (c.isArray()) {
            return constantArrayToString(value);
        } else if (value instanceof Enum) {
            return ((Enum<?>) value).name();
        } else if (isToStringTrusted(c)) {
            return value.toString();
        }
        return MetaUtil.getSimpleName(c, true) + "@" + System.identityHashCode(value);

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
}
