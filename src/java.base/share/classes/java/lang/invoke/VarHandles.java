/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.invoke;

import static java.lang.invoke.MethodHandleStatics.UNSAFE;

final class VarHandles {

    static VarHandle makeFieldHandle(MemberName f, Class<?> refc, Class<?> type, boolean isWriteAllowedOnFinalFields) {
        if (!f.isStatic()) {
            long foffset = MethodHandleNatives.objectFieldOffset(f);
            if (!type.isPrimitive()) {
                return f.isFinal() && !isWriteAllowedOnFinalFields
                       ? new VarHandleReferences.FieldInstanceReadOnly(refc, foffset, type)
                       : new VarHandleReferences.FieldInstanceReadWrite(refc, foffset, type);
            }
            else if (type == boolean.class) {
                return f.isFinal() && !isWriteAllowedOnFinalFields
                       ? new VarHandleBooleans.FieldInstanceReadOnly(refc, foffset)
                       : new VarHandleBooleans.FieldInstanceReadWrite(refc, foffset);
            }
            else if (type == byte.class) {
                return f.isFinal() && !isWriteAllowedOnFinalFields
                       ? new VarHandleBytes.FieldInstanceReadOnly(refc, foffset)
                       : new VarHandleBytes.FieldInstanceReadWrite(refc, foffset);
            }
            else if (type == short.class) {
                return f.isFinal() && !isWriteAllowedOnFinalFields
                       ? new VarHandleShorts.FieldInstanceReadOnly(refc, foffset)
                       : new VarHandleShorts.FieldInstanceReadWrite(refc, foffset);
            }
            else if (type == char.class) {
                return f.isFinal() && !isWriteAllowedOnFinalFields
                       ? new VarHandleChars.FieldInstanceReadOnly(refc, foffset)
                       : new VarHandleChars.FieldInstanceReadWrite(refc, foffset);
            }
            else if (type == int.class) {
                return f.isFinal() && !isWriteAllowedOnFinalFields
                       ? new VarHandleInts.FieldInstanceReadOnly(refc, foffset)
                       : new VarHandleInts.FieldInstanceReadWrite(refc, foffset);
            }
            else if (type == long.class) {
                return f.isFinal() && !isWriteAllowedOnFinalFields
                       ? new VarHandleLongs.FieldInstanceReadOnly(refc, foffset)
                       : new VarHandleLongs.FieldInstanceReadWrite(refc, foffset);
            }
            else if (type == float.class) {
                return f.isFinal() && !isWriteAllowedOnFinalFields
                       ? new VarHandleFloats.FieldInstanceReadOnly(refc, foffset)
                       : new VarHandleFloats.FieldInstanceReadWrite(refc, foffset);
            }
            else if (type == double.class) {
                return f.isFinal() && !isWriteAllowedOnFinalFields
                       ? new VarHandleDoubles.FieldInstanceReadOnly(refc, foffset)
                       : new VarHandleDoubles.FieldInstanceReadWrite(refc, foffset);
            }
            else {
                throw new UnsupportedOperationException();
            }
        }
        else {
            // TODO This is not lazy on first invocation
            // and might cause some circular initialization issues

            // Replace with something similar to direct method handles
            // where a barrier is used then elided after use

            if (UNSAFE.shouldBeInitialized(refc))
                UNSAFE.ensureClassInitialized(refc);

            Object base = MethodHandleNatives.staticFieldBase(f);
            long foffset = MethodHandleNatives.staticFieldOffset(f);
            if (!type.isPrimitive()) {
                return f.isFinal() && !isWriteAllowedOnFinalFields
                       ? new VarHandleReferences.FieldStaticReadOnly(base, foffset, type)
                       : new VarHandleReferences.FieldStaticReadWrite(base, foffset, type);
            }
            else if (type == boolean.class) {
                return f.isFinal() && !isWriteAllowedOnFinalFields
                       ? new VarHandleBooleans.FieldStaticReadOnly(base, foffset)
                       : new VarHandleBooleans.FieldStaticReadWrite(base, foffset);
            }
            else if (type == byte.class) {
                return f.isFinal() && !isWriteAllowedOnFinalFields
                       ? new VarHandleBytes.FieldStaticReadOnly(base, foffset)
                       : new VarHandleBytes.FieldStaticReadWrite(base, foffset);
            }
            else if (type == short.class) {
                return f.isFinal() && !isWriteAllowedOnFinalFields
                       ? new VarHandleShorts.FieldStaticReadOnly(base, foffset)
                       : new VarHandleShorts.FieldStaticReadWrite(base, foffset);
            }
            else if (type == char.class) {
                return f.isFinal() && !isWriteAllowedOnFinalFields
                       ? new VarHandleChars.FieldStaticReadOnly(base, foffset)
                       : new VarHandleChars.FieldStaticReadWrite(base, foffset);
            }
            else if (type == int.class) {
                return f.isFinal() && !isWriteAllowedOnFinalFields
                       ? new VarHandleInts.FieldStaticReadOnly(base, foffset)
                       : new VarHandleInts.FieldStaticReadWrite(base, foffset);
            }
            else if (type == long.class) {
                return f.isFinal() && !isWriteAllowedOnFinalFields
                       ? new VarHandleLongs.FieldStaticReadOnly(base, foffset)
                       : new VarHandleLongs.FieldStaticReadWrite(base, foffset);
            }
            else if (type == float.class) {
                return f.isFinal() && !isWriteAllowedOnFinalFields
                       ? new VarHandleFloats.FieldStaticReadOnly(base, foffset)
                       : new VarHandleFloats.FieldStaticReadWrite(base, foffset);
            }
            else if (type == double.class) {
                return f.isFinal() && !isWriteAllowedOnFinalFields
                       ? new VarHandleDoubles.FieldStaticReadOnly(base, foffset)
                       : new VarHandleDoubles.FieldStaticReadWrite(base, foffset);
            }
            else {
                throw new UnsupportedOperationException();
            }
        }
    }

    static VarHandle makeArrayElementHandle(Class<?> arrayClass) {
        if (!arrayClass.isArray())
            throw new IllegalArgumentException("not an array: " + arrayClass);

        Class<?> componentType = arrayClass.getComponentType();

        int aoffset = UNSAFE.arrayBaseOffset(arrayClass);
        int ascale = UNSAFE.arrayIndexScale(arrayClass);
        int ashift = 31 - Integer.numberOfLeadingZeros(ascale);

        if (!componentType.isPrimitive()) {
            return new VarHandleReferences.Array(aoffset, ashift, arrayClass);
        }
        else if (componentType == boolean.class) {
            return new VarHandleBooleans.Array(aoffset, ashift);
        }
        else if (componentType == byte.class) {
            return new VarHandleBytes.Array(aoffset, ashift);
        }
        else if (componentType == short.class) {
            return new VarHandleShorts.Array(aoffset, ashift);
        }
        else if (componentType == char.class) {
            return new VarHandleChars.Array(aoffset, ashift);
        }
        else if (componentType == int.class) {
            return new VarHandleInts.Array(aoffset, ashift);
        }
        else if (componentType == long.class) {
            return new VarHandleLongs.Array(aoffset, ashift);
        }
        else if (componentType == float.class) {
            return new VarHandleFloats.Array(aoffset, ashift);
        }
        else if (componentType == double.class) {
            return new VarHandleDoubles.Array(aoffset, ashift);
        }
        else {
            throw new UnsupportedOperationException();
        }
    }

    static VarHandle byteArrayViewHandle(Class<?> viewArrayClass,
                                         boolean be) {
        if (!viewArrayClass.isArray())
            throw new IllegalArgumentException("not an array: " + viewArrayClass);

        Class<?> viewComponentType = viewArrayClass.getComponentType();

        if (viewComponentType == long.class) {
            return new VarHandleByteArrayAsLongs.ArrayHandle(be);
        }
        else if (viewComponentType == int.class) {
            return new VarHandleByteArrayAsInts.ArrayHandle(be);
        }
        else if (viewComponentType == short.class) {
            return new VarHandleByteArrayAsShorts.ArrayHandle(be);
        }
        else if (viewComponentType == char.class) {
            return new VarHandleByteArrayAsChars.ArrayHandle(be);
        }
        else if (viewComponentType == double.class) {
            return new VarHandleByteArrayAsDoubles.ArrayHandle(be);
        }
        else if (viewComponentType == float.class) {
            return new VarHandleByteArrayAsFloats.ArrayHandle(be);
        }

        throw new UnsupportedOperationException();
    }

    static VarHandle makeByteBufferViewHandle(Class<?> viewArrayClass,
                                              boolean be) {
        if (!viewArrayClass.isArray())
            throw new IllegalArgumentException("not an array: " + viewArrayClass);

        Class<?> viewComponentType = viewArrayClass.getComponentType();

        if (viewComponentType == long.class) {
            return new VarHandleByteArrayAsLongs.ByteBufferHandle(be);
        }
        else if (viewComponentType == int.class) {
            return new VarHandleByteArrayAsInts.ByteBufferHandle(be);
        }
        else if (viewComponentType == short.class) {
            return new VarHandleByteArrayAsShorts.ByteBufferHandle(be);
        }
        else if (viewComponentType == char.class) {
            return new VarHandleByteArrayAsChars.ByteBufferHandle(be);
        }
        else if (viewComponentType == double.class) {
            return new VarHandleByteArrayAsDoubles.ByteBufferHandle(be);
        }
        else if (viewComponentType == float.class) {
            return new VarHandleByteArrayAsFloats.ByteBufferHandle(be);
        }

        throw new UnsupportedOperationException();
    }

//    /**
//     * A helper program to generate the VarHandleGuards class with a set of
//     * static guard methods each of which corresponds to a particular shape and
//     * performs a type check of the symbolic type descriptor with the VarHandle
//     * type descriptor before linking/invoking to the underlying operation as
//     * characterized by the operation member name on the VarForm of the
//     * VarHandle.
//     * <p>
//     * The generated class essentially encapsulates pre-compiled LambdaForms,
//     * one for each method, for the most set of common method signatures.
//     * This reduces static initialization costs, footprint costs, and circular
//     * dependencies that may arise if a class is generated per LambdaForm.
//     * <p>
//     * A maximum of L*T*S methods will be generated where L is the number of
//     * access modes kinds (or unique operation signatures) and T is the number
//     * of variable types and S is the number of shapes (such as instance field,
//     * static field, or array access).
//     * If there are 4 unique operation signatures, 5 basic types (Object, int,
//     * long, float, double), and 3 shapes then a maximum of 60 methods will be
//     * generated.  However, the number is likely to be less since there
//     * be duplicate signatures.
//     * <p>
//     * Each method is annotated with @LambdaForm.Compiled to inform the runtime
//     * that such methods should be treated as if a method of a class that is the
//     * result of compiling a LambdaForm.  Annotation of such methods is
//     * important for correct evaluation of certain assertions and method return
//     * type profiling in HotSpot.
//     */
//    public static class GuardMethodGenerator {
//
//        static final String GUARD_METHOD_SIG_TEMPLATE = "<RETURN> <NAME>_<SIGNATURE>(<PARAMS>)";
//
//        static final String GUARD_METHOD_TEMPLATE =
//                "@ForceInline\n" +
//                "@LambdaForm.Compiled\n" +
//                "final static <METHOD> throws Throwable {\n" +
//                "    if (handle.vform.methodType_table[ad.type] == ad.symbolicMethodType) {\n" +
//                "        <RESULT_ERASED>MethodHandle.linkToStatic(<LINK_TO_STATIC_ARGS>);<RETURN_ERASED>\n" +
//                "    }\n" +
//                "    else {\n" +
//                "        MethodHandle mh = handle.getMethodHandle(ad.mode);\n" +
//                "        <RETURN>mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(<LINK_TO_INVOKER_ARGS>);\n" +
//                "    }\n" +
//                "}";
//
//        static final String GUARD_METHOD_TEMPLATE_V =
//                "@ForceInline\n" +
//                "@LambdaForm.Compiled\n" +
//                "final static <METHOD> throws Throwable {\n" +
//                "    if (handle.vform.methodType_table[ad.type] == ad.symbolicMethodType) {\n" +
//                "        MethodHandle.linkToStatic(<LINK_TO_STATIC_ARGS>);\n" +
//                "    }\n" +
//                "    else if (handle.vform.getMethodType_V(ad.type) == ad.symbolicMethodType) {\n" +
//                "        MethodHandle.linkToStatic(<LINK_TO_STATIC_ARGS>);\n" +
//                "    }\n" +
//                "    else {\n" +
//                "        MethodHandle mh = handle.getMethodHandle(ad.mode);\n" +
//                "        mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(<LINK_TO_INVOKER_ARGS>);\n" +
//                "    }\n" +
//                "}";
//
//        // A template for deriving the operations
//        // could be supported by annotating VarHandle directly with the
//        // operation kind and shape
//        interface VarHandleTemplate {
//            Object get();
//
//            void set(Object value);
//
//            boolean compareAndSet(Object actualValue, Object expectedValue);
//
//            Object compareAndExchange(Object actualValue, Object expectedValue);
//
//            Object getAndUpdate(Object value);
//        }
//
//        static class HandleType {
//            final Class<?> receiver;
//            final Class<?>[] intermediates;
//            final Class<?> value;
//
//            HandleType(Class<?> receiver, Class<?> value, Class<?>... intermediates) {
//                this.receiver = receiver;
//                this.intermediates = intermediates;
//                this.value = value;
//            }
//        }
//
//        /**
//         * @param args parameters
//         */
//        public static void main(String[] args) {
//            System.out.println("package java.lang.invoke;");
//            System.out.println();
//            System.out.println("import jdk.internal.vm.annotation.ForceInline;");
//            System.out.println();
//            System.out.println("// This class is auto-generated by " +
//                               GuardMethodGenerator.class.getName() +
//                               ". Do not edit.");
//            System.out.println("final class VarHandleGuards {");
//
//            System.out.println();
//
//            // Declare the stream of shapes
//            Stream<HandleType> hts = Stream.of(
//                    // Object->Object
//                    new HandleType(Object.class, Object.class),
//                    // Object->int
//                    new HandleType(Object.class, int.class),
//                    // Object->long
//                    new HandleType(Object.class, long.class),
//                    // Object->float
//                    new HandleType(Object.class, float.class),
//                    // Object->double
//                    new HandleType(Object.class, double.class),
//
//                    // <static>->Object
//                    new HandleType(null, Object.class),
//                    // <static>->int
//                    new HandleType(null, int.class),
//                    // <static>->long
//                    new HandleType(null, long.class),
//                    // <static>->float
//                    new HandleType(null, float.class),
//                    // <static>->double
//                    new HandleType(null, double.class),
//
//                    // Array[int]->Object
//                    new HandleType(Object.class, Object.class, int.class),
//                    // Array[int]->int
//                    new HandleType(Object.class, int.class, int.class),
//                    // Array[int]->long
//                    new HandleType(Object.class, long.class, int.class),
//                    // Array[int]->float
//                    new HandleType(Object.class, float.class, int.class),
//                    // Array[int]->double
//                    new HandleType(Object.class, double.class, int.class),
//
//                    // Array[long]->int
//                    new HandleType(Object.class, int.class, long.class),
//                    // Array[long]->long
//                    new HandleType(Object.class, long.class, long.class)
//            );
//
//            hts.flatMap(ht -> Stream.of(VarHandleTemplate.class.getMethods()).
//                    map(m -> generateMethodType(m, ht.receiver, ht.value, ht.intermediates))).
//                    distinct().
//                    map(mt -> generateMethod(mt)).
//                    forEach(s -> {
//                        System.out.println(s);
//                        System.out.println();
//                    });
//
//            System.out.println("}");
//        }
//
//        static MethodType generateMethodType(Method m, Class<?> receiver, Class<?> value, Class<?>... intermediates) {
//            Class<?> returnType = m.getReturnType() == Object.class
//                                  ? value : m.getReturnType();
//
//            List<Class<?>> params = new ArrayList<>();
//            if (receiver != null)
//                params.add(receiver);
//            for (int i = 0; i < intermediates.length; i++) {
//                params.add(intermediates[i]);
//            }
//            for (Parameter p : m.getParameters()) {
//                params.add(value);
//            }
//            return MethodType.methodType(returnType, params);
//        }
//
//        static String generateMethod(MethodType mt) {
//            Class<?> returnType = mt.returnType();
//
//            LinkedHashMap<String, Class<?>> params = new LinkedHashMap<>();
//            params.put("handle", VarHandle.class);
//            for (int i = 0; i < mt.parameterCount(); i++) {
//                params.put("arg" + i, mt.parameterType(i));
//            }
//            params.put("ad", VarHandle.AccessDescriptor.class);
//
//            // Generate method signature line
//            String RETURN = className(returnType);
//            String NAME = "guard";
//            String SIGNATURE = getSignature(mt);
//            String PARAMS = params.entrySet().stream().
//                    map(e -> className(e.getValue()) + " " + e.getKey()).
//                    collect(joining(", "));
//            String METHOD = GUARD_METHOD_SIG_TEMPLATE.
//                    replace("<RETURN>", RETURN).
//                    replace("<NAME>", NAME).
//                    replace("<SIGNATURE>", SIGNATURE).
//                    replace("<PARAMS>", PARAMS);
//
//            // Generate method
//            params.remove("ad");
//
//            List<String> LINK_TO_STATIC_ARGS = params.keySet().stream().
//                    collect(toList());
//            LINK_TO_STATIC_ARGS.add("handle.vform.getMemberName(ad.mode)");
//            List<String> LINK_TO_STATIC_ARGS_V = params.keySet().stream().
//                    collect(toList());
//            LINK_TO_STATIC_ARGS_V.add("handle.vform.getMemberName_V(ad.mode)");
//
//            List<String> LINK_TO_INVOKER_ARGS = params.keySet().stream().
//                    collect(toList());
//
//            RETURN = returnType == void.class
//                     ? ""
//                     : returnType == Object.class
//                       ? "return "
//                       : "return (" + returnType.getName() + ") ";
//
//            String RESULT_ERASED = returnType == void.class
//                                   ? ""
//                                   : returnType != Object.class
//                                     ? "return (" + returnType.getName() + ") "
//                                     : "Object r = ";
//
//            String RETURN_ERASED = returnType != Object.class
//                                   ? ""
//                                   : " return ad.returnType.cast(r);";
//
//            String template = returnType == void.class
//                              ? GUARD_METHOD_TEMPLATE_V
//                              : GUARD_METHOD_TEMPLATE;
//            return template.
//                    replace("<METHOD>", METHOD).
//                    replace("<NAME>", NAME).
//                    replaceAll("<RETURN>", RETURN).
//                    replace("<RESULT_ERASED>", RESULT_ERASED).
//                    replace("<RETURN_ERASED>", RETURN_ERASED).
//                    replaceAll("<LINK_TO_STATIC_ARGS>", LINK_TO_STATIC_ARGS.stream().
//                            collect(joining(", "))).
//                    replaceAll("<LINK_TO_STATIC_ARGS_V>", LINK_TO_STATIC_ARGS_V.stream().
//                            collect(joining(", "))).
//                    replace("<LINK_TO_INVOKER_ARGS>", LINK_TO_INVOKER_ARGS.stream().
//                            collect(joining(", ")))
//                    ;
//        }
//
//        static String className(Class<?> c) {
//            String n = c.getName();
//            if (n.startsWith("java.lang.")) {
//                n = n.replace("java.lang.", "");
//                if (n.startsWith("invoke.")) {
//                    n = n.replace("invoke.", "");
//                }
//            }
//            return n.replace('$', '.');
//        }
//
//        static String getSignature(MethodType m) {
//            StringBuilder sb = new StringBuilder(m.parameterCount() + 1);
//
//            for (int i = 0; i < m.parameterCount(); i++) {
//                Class<?> pt = m.parameterType(i);
//                sb.append(getCharType(pt));
//            }
//
//            sb.append('_').append(getCharType(m.returnType()));
//
//            return sb.toString();
//        }
//
//        static char getCharType(Class<?> pt) {
//            if (pt == void.class) {
//                return 'V';
//            }
//            else if (!pt.isPrimitive()) {
//                return 'L';
//            }
//            else if (pt == boolean.class) {
//                return 'Z';
//            }
//            else if (pt == int.class) {
//                return 'I';
//            }
//            else if (pt == long.class) {
//                return 'J';
//            }
//            else if (pt == float.class) {
//                return 'F';
//            }
//            else if (pt == double.class) {
//                return 'D';
//            }
//            else {
//                throw new IllegalStateException(pt.getName());
//            }
//        }
//    }
}
