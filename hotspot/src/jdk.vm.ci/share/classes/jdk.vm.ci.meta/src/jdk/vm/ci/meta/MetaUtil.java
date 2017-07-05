/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.meta;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Miscellaneous collection of utility methods used by {@code jdk.vm.ci.meta} and its clients.
 */
public class MetaUtil {

    private static class ClassInfo {
        public long totalSize;
        public long instanceCount;

        @Override
        public String toString() {
            return "totalSize=" + totalSize + ", instanceCount=" + instanceCount;
        }
    }

    /**
     * Returns the number of bytes occupied by this constant value or constant object and
     * recursively all values reachable from this value.
     *
     * @param constant the constant whose bytes should be measured
     * @param printTopN print total size and instance count of the top n classes is desired
     * @return the number of bytes occupied by this constant
     */
    public static long getMemorySizeRecursive(MetaAccessProvider access, ConstantReflectionProvider constantReflection, JavaConstant constant, PrintStream out, int printTopN) {
        Set<JavaConstant> marked = new HashSet<>();
        Deque<JavaConstant> stack = new ArrayDeque<>();
        if (constant.getJavaKind() == JavaKind.Object && constant.isNonNull()) {
            marked.add(constant);
        }
        final HashMap<ResolvedJavaType, ClassInfo> histogram = new HashMap<>();
        stack.push(constant);
        long sum = 0;
        while (!stack.isEmpty()) {
            JavaConstant c = stack.pop();
            long memorySize = access.getMemorySize(constant);
            sum += memorySize;
            if (c.getJavaKind() == JavaKind.Object && c.isNonNull()) {
                ResolvedJavaType clazz = access.lookupJavaType(c);
                if (!histogram.containsKey(clazz)) {
                    histogram.put(clazz, new ClassInfo());
                }
                ClassInfo info = histogram.get(clazz);
                info.instanceCount++;
                info.totalSize += memorySize;
                ResolvedJavaType type = access.lookupJavaType(c);
                if (type.isArray()) {
                    if (!type.getComponentType().isPrimitive()) {
                        int length = constantReflection.readArrayLength(c);
                        for (int i = 0; i < length; i++) {
                            JavaConstant value = constantReflection.readArrayElement(c, i);
                            pushConstant(marked, stack, value);
                        }
                    }
                } else {
                    ResolvedJavaField[] instanceFields = type.getInstanceFields(true);
                    for (ResolvedJavaField f : instanceFields) {
                        if (f.getJavaKind() == JavaKind.Object) {
                            JavaConstant value = constantReflection.readFieldValue(f, c);
                            pushConstant(marked, stack, value);
                        }
                    }
                }
            }
        }
        ArrayList<ResolvedJavaType> clazzes = new ArrayList<>();
        clazzes.addAll(histogram.keySet());
        Collections.sort(clazzes, new Comparator<ResolvedJavaType>() {

            @Override
            public int compare(ResolvedJavaType o1, ResolvedJavaType o2) {
                long l1 = histogram.get(o1).totalSize;
                long l2 = histogram.get(o2).totalSize;
                if (l1 > l2) {
                    return -1;
                } else if (l1 == l2) {
                    return 0;
                } else {
                    return 1;
                }
            }
        });

        int z = 0;
        for (ResolvedJavaType c : clazzes) {
            if (z > printTopN) {
                break;
            }
            out.println("Class " + c + ", " + histogram.get(c));
            ++z;
        }

        return sum;
    }

    private static void pushConstant(Set<JavaConstant> marked, Deque<JavaConstant> stack, JavaConstant value) {
        if (value.isNonNull()) {
            if (!marked.contains(value)) {
                marked.add(value);
                stack.push(value);
            }
        }
    }

    /**
     * Calls {@link JavaType#resolve(ResolvedJavaType)} on an array of types.
     */
    public static ResolvedJavaType[] resolveJavaTypes(JavaType[] types, ResolvedJavaType accessingClass) {
        ResolvedJavaType[] result = new ResolvedJavaType[types.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = types[i].resolve(accessingClass);
        }
        return result;
    }

    /**
     * Extends the functionality of {@link Class#getSimpleName()} to include a non-empty string for
     * anonymous and local classes.
     *
     * @param clazz the class for which the simple name is being requested
     * @param withEnclosingClass specifies if the returned name should be qualified with the name(s)
     *            of the enclosing class/classes of {@code clazz} (if any). This option is ignored
     *            if {@code clazz} denotes an anonymous or local class.
     * @return the simple name
     */
    public static String getSimpleName(Class<?> clazz, boolean withEnclosingClass) {
        final String simpleName = clazz.getSimpleName();
        if (simpleName.length() != 0) {
            if (withEnclosingClass) {
                String prefix = "";
                Class<?> enclosingClass = clazz;
                while ((enclosingClass = enclosingClass.getEnclosingClass()) != null) {
                    prefix = enclosingClass.getSimpleName() + "." + prefix;
                }
                return prefix + simpleName;
            }
            return simpleName;
        }
        // Must be an anonymous or local class
        final String name = clazz.getName();
        int index = name.indexOf('$');
        if (index == -1) {
            return name;
        }
        index = name.lastIndexOf('.', index);
        if (index == -1) {
            return name;
        }
        return name.substring(index + 1);
    }

    static String internalNameToJava(String name, boolean qualified, boolean classForNameCompatible) {
        switch (name.charAt(0)) {
            case 'L': {
                String result = name.substring(1, name.length() - 1).replace('/', '.');
                if (!qualified) {
                    final int lastDot = result.lastIndexOf('.');
                    if (lastDot != -1) {
                        result = result.substring(lastDot + 1);
                    }
                }
                return result;
            }
            case '[':
                return classForNameCompatible ? name.replace('/', '.') : internalNameToJava(name.substring(1), qualified, classForNameCompatible) + "[]";
            default:
                if (name.length() != 1) {
                    throw new IllegalArgumentException("Illegal internal name: " + name);
                }
                return JavaKind.fromPrimitiveOrVoidTypeChar(name.charAt(0)).getJavaName();
        }
    }

    /**
     * Turns an class name in internal format into a resolved Java type.
     */
    public static ResolvedJavaType classForName(String internal, MetaAccessProvider metaAccess, ClassLoader cl) {
        JavaKind k = JavaKind.fromTypeString(internal);
        try {
            String n = internalNameToJava(internal, true, true);
            return metaAccess.lookupJavaType(k.isPrimitive() ? k.toJavaClass() : Class.forName(n, true, cl));
        } catch (ClassNotFoundException cnfe) {
            throw new IllegalArgumentException("could not instantiate class described by " + internal, cnfe);
        }
    }

    /**
     * Convenient shortcut for calling
     * {@link #appendLocation(StringBuilder, ResolvedJavaMethod, int)} without having to supply a
     * {@link StringBuilder} instance and convert the result to a string.
     */
    public static String toLocation(ResolvedJavaMethod method, int bci) {
        return appendLocation(new StringBuilder(), method, bci).toString();
    }

    /**
     * Appends a string representation of a location specified by a given method and bci to a given
     * {@link StringBuilder}. If a stack trace element with a non-null file name and non-negative
     * line number is {@linkplain ResolvedJavaMethod#asStackTraceElement(int) available} for the
     * given method, then the string returned is the {@link StackTraceElement#toString()} value of
     * the stack trace element, suffixed by the bci location. For example:
     *
     * <pre>
     *     java.lang.String.valueOf(String.java:2930) [bci: 12]
     * </pre>
     *
     * Otherwise, the string returned is the value of applying {@link JavaMethod#format(String)}
     * with the format string {@code "%H.%n(%p)"}, suffixed by the bci location. For example:
     *
     * <pre>
     *     java.lang.String.valueOf(int) [bci: 12]
     * </pre>
     *
     * @param sb
     * @param method
     * @param bci
     */
    public static StringBuilder appendLocation(StringBuilder sb, ResolvedJavaMethod method, int bci) {
        if (method != null) {
            StackTraceElement ste = method.asStackTraceElement(bci);
            if (ste.getFileName() != null && ste.getLineNumber() > 0) {
                sb.append(ste);
            } else {
                sb.append(method.format("%H.%n(%p)"));
            }
        } else {
            sb.append("Null method");
        }
        return sb.append(" [bci: ").append(bci).append(']');
    }

    static void appendProfile(StringBuilder buf, AbstractJavaProfile<?, ?> profile, int bci, String type, String sep) {
        if (profile != null) {
            AbstractProfiledItem<?>[] pitems = profile.getItems();
            if (pitems != null) {
                buf.append(String.format("%s@%d:", type, bci));
                for (int j = 0; j < pitems.length; j++) {
                    AbstractProfiledItem<?> pitem = pitems[j];
                    buf.append(String.format(" %.6f (%s)%s", pitem.getProbability(), pitem.getItem(), sep));
                }
                if (profile.getNotRecordedProbability() != 0) {
                    buf.append(String.format(" %.6f <other %s>%s", profile.getNotRecordedProbability(), type, sep));
                } else {
                    buf.append(String.format(" <no other %s>%s", type, sep));
                }
            }
        }
    }

    /**
     * Converts a Java source-language class name into the internal form.
     *
     * @param className the class name
     * @return the internal name form of the class name
     */
    public static String toInternalName(String className) {
        if (className.startsWith("[")) {
            /* Already in the correct array style. */
            return className.replace('.', '/');
        }

        StringBuilder result = new StringBuilder();
        String base = className;
        while (base.endsWith("[]")) {
            result.append("[");
            base = base.substring(0, base.length() - 2);
        }

        switch (base) {
            case "boolean":
                result.append("Z");
                break;
            case "byte":
                result.append("B");
                break;
            case "short":
                result.append("S");
                break;
            case "char":
                result.append("C");
                break;
            case "int":
                result.append("I");
                break;
            case "float":
                result.append("F");
                break;
            case "long":
                result.append("J");
                break;
            case "double":
                result.append("D");
                break;
            case "void":
                result.append("V");
                break;
            default:
                result.append("L").append(base.replace('.', '/')).append(";");
                break;
        }
        return result.toString();
    }

    /**
     * Prepends the String {@code indentation} to every line in String {@code lines}, including a
     * possibly non-empty line following the final newline.
     */
    public static String indent(String lines, String indentation) {
        if (lines.length() == 0) {
            return lines;
        }
        final String newLine = "\n";
        if (lines.endsWith(newLine)) {
            return indentation + (lines.substring(0, lines.length() - 1)).replace(newLine, newLine + indentation) + newLine;
        }
        return indentation + lines.replace(newLine, newLine + indentation);
    }

    /**
     * Gets a string representation of an object based soley on its class and its
     * {@linkplain System#identityHashCode(Object) identity hash code}. This avoids and calls to
     * virtual methods on the object such as {@link Object#hashCode()}.
     */
    public static String identityHashCodeString(Object obj) {
        if (obj == null) {
            return "null";
        }
        return obj.getClass().getName() + "@" + System.identityHashCode(obj);
    }

    /**
     * Used to lookup constants from {@link Modifier} that are not public (VARARGS, SYNTHETIC etc.).
     */
    static int getNonPublicModifierStaticField(String name) {
        try {
            Field field = Modifier.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(null);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            throw new InternalError(e);
        }
    }
}
