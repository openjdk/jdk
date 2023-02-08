/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 
 import java.lang.invoke.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import static java.lang.invoke.MethodHandleInfo.*;


/* @test
 * @bug 8068253
 * @compile Classes.java
 * @run main Test8068253
*/
public class Test8068253 {
    
    public static void main(String... args) {
        test();
    }
    
    @SuppressWarnings("auxiliaryclass")
    static void test() {
        resolve(REF_invokeStatic, "A", "m1", "()V");
        resolve(REF_invokeStatic, "B", "m1", "()V");
        resolve(REF_invokeStatic, "I", "m2", "()V");
        failResolve(REF_invokeStatic, "G", "m2", "()V");
        
        resolve(REF_invokeVirtual, "A", "m3", "()V");
        resolve(REF_invokeVirtual, "B", "m3", "()V");
        resolve(REF_invokeVirtual, "C", "m3", "()V");
        resolve(REF_invokeVirtual, "A", "m4", "()V");
        resolve(REF_invokeVirtual, "B", "m4", "()V");
        resolve(REF_invokeVirtual, "C", "m4", "()V");

        resolve(REF_invokeVirtual, "G", "m5", "()V");
        resolve(REF_invokeVirtual, "H", "m5", "()V");
        resolve(REF_invokeVirtual, "G", "m6", "()V");
        resolve(REF_invokeVirtual, "H", "m6", "()V");

        resolve(REF_invokeVirtual, "A", "toString", "()Ljava/lang/String;");
        resolve(REF_invokeVirtual, "B", "toString", "()Ljava/lang/String;");
        resolve(REF_invokeVirtual, "C", "toString", "()Ljava/lang/String;");
        resolve(REF_invokeVirtual, "G", "toString", "()Ljava/lang/String;");
        resolve(REF_invokeVirtual, "H", "toString", "()Ljava/lang/String;");
        resolve(REF_invokeVirtual, "[LA;", "toString", "()Ljava/lang/String;");

        resolve(REF_invokeInterface, "I", "m5", "()V");
        resolve(REF_invokeInterface, "K", "m5", "()V");
        resolve(REF_invokeInterface, "I", "m6", "()V");
        resolve(REF_invokeInterface, "K", "m6", "()V");
        resolve(REF_invokeInterface, "I", "toString", "()Ljava/lang/String;");
        resolve(REF_invokeInterface, "K", "toString", "()Ljava/lang/String;");
        
        resolve(A.lookup(), REF_invokeSpecial, "A", "m3", "()V");
        resolve(A.lookup(), REF_invokeSpecial, "A", "m7", "()V");
        resolve(A.lookup(), REF_invokeSpecial, "A", "toString", "()Ljava/lang/String;");

        resolve(B.lookup(), REF_invokeSpecial, "B", "m3", "()V");
        resolve(B.lookup(), REF_invokeSpecial, "A", "m3", "()V");
        failResolve(B.lookup(), REF_invokeSpecial, "B", "m7", "()V");
        failResolve(B.lookup(), REF_invokeSpecial, "A", "m7", "()V");
        resolve(B.lookup(), REF_invokeSpecial, "B", "toString", "()Ljava/lang/String;");
        resolve(B.lookup(), REF_invokeSpecial, "A", "toString", "()Ljava/lang/String;");

        resolve(C.lookup(), REF_invokeSpecial, "C", "m3", "()V");
        resolve(C.lookup(), REF_invokeSpecial, "B", "m3", "()V");
        resolve(C.lookup(), REF_invokeSpecial, "A", "m3", "()V");
        resolve(C.lookup(), REF_invokeSpecial, "C", "m7", "()V");
        failResolve(C.lookup(), REF_invokeSpecial, "B", "m7", "()V");
        failResolve(C.lookup(), REF_invokeSpecial, "A", "m7", "()V");
        resolve(C.lookup(), REF_invokeSpecial, "C", "m4", "()V");
        failResolve(C.lookup(), REF_invokeSpecial, "B", "m4", "()V");
        failResolve(C.lookup(), REF_invokeSpecial, "A", "m4", "()V");
        resolve(C.lookup(), REF_invokeSpecial, "C", "m8", "()V");
        resolve(C.lookup(), REF_invokeSpecial, "B", "m8", "()V");
        resolve(C.lookup(), REF_invokeSpecial, "A", "m8", "()V");
        resolve(C.lookup(), REF_invokeSpecial, "C", "toString", "()Ljava/lang/String;");
        resolve(C.lookup(), REF_invokeSpecial, "B", "toString", "()Ljava/lang/String;");
        resolve(C.lookup(), REF_invokeSpecial, "A", "toString", "()Ljava/lang/String;");

        failResolve(I.lookup(), REF_invokeSpecial, "I", "m5", "()V");
        resolve(I.lookup(), REF_invokeSpecial, "I", "m6", "()V");
        resolve(I.lookup(), REF_invokeSpecial, "I", "toString", "()Ljava/lang/String;");
        //resolve(I.lookup(), REF_invokeSpecial, "java.lang.Object", "toString", "()Ljava/lang/String;"); // bug: JDK-8301721

        resolve(K.lookup(), REF_invokeSpecial, "K", "m5", "()V");
        failResolve(K.lookup(), REF_invokeSpecial, "I", "m5", "()V");
        resolve(K.lookup(), REF_invokeSpecial, "K", "m6", "()V");
        resolve(K.lookup(), REF_invokeSpecial, "I", "m6", "()V");
        resolve(K.lookup(), REF_invokeSpecial, "K", "toString", "()Ljava/lang/String;");
        resolve(K.lookup(), REF_invokeSpecial, "I", "toString", "()Ljava/lang/String;");

        resolve(H.lookup(), REF_invokeSpecial, "H", "m5", "()V");
        failResolve(H.lookup(), REF_invokeSpecial, "I", "m5", "()V");
        resolve(H.lookup(), REF_invokeSpecial, "H", "m6", "()V");
        resolve(H.lookup(), REF_invokeSpecial, "I", "m6", "()V");
        resolve(H.lookup(), REF_invokeSpecial, "H", "toString", "()Ljava/lang/String;");
        resolve(H.lookup(), REF_invokeSpecial, "I", "toString", "()Ljava/lang/String;");
        
        resolve(REF_newInvokeSpecial, "A", "<init>", "()V"); // abstract, should fail?
        resolve(REF_newInvokeSpecial, "B", "<init>", "()V"); // abstract, should fail?
        resolve(REF_newInvokeSpecial, "C", "<init>", "()V");
        failResolve(REF_newInvokeSpecial, "I", "<init>", "()V");

        resolve(REF_getField, "A", "x1", "I");
        resolve(REF_getField, "B", "x1", "I");
        resolve(REF_getField, "C", "x1", "I");
        resolve(REF_putField, "A", "x1", "I");
        resolve(REF_putField, "B", "x1", "I");
        resolve(REF_putField, "C", "x1", "I");
        
        resolve(REF_getStatic, "A", "x2", "I");
        resolve(REF_getStatic, "B", "x2", "I");
        resolve(REF_getStatic, "C", "x2", "I");
        resolve(REF_putStatic, "A", "x2", "I");
        resolve(REF_putStatic, "B", "x2", "I");
        resolve(REF_putStatic, "C", "x2", "I");
        
        resolve(REF_getStatic, "A", "x3", "I");
        resolve(REF_getStatic, "B", "x3", "I");
        resolve(REF_getStatic, "C", "x3", "I");
        failResolve(REF_putStatic, "A", "x3", "I");
        failResolve(REF_putStatic, "B", "x3", "I");
        failResolve(REF_putStatic, "C", "x3", "I");

        resolve(REF_getStatic, "I", "x4", "I");
        resolve(REF_getStatic, "J", "x4", "I");
        resolve(REF_getStatic, "K", "x4", "I");
        resolve(REF_getStatic, "G", "x4", "I");
        resolve(REF_getStatic, "H", "x4", "I");
        failResolve(REF_putStatic, "I", "x4", "I");
        failResolve(REF_putStatic, "J", "x4", "I");
        failResolve(REF_putStatic, "K", "x4", "I");
        failResolve(REF_putStatic, "G", "x4", "I");
        failResolve(REF_putStatic, "H", "x4", "I");
    }

    static MethodHandles.Lookup lookup = MethodHandles.lookup();
    
    static void resolve(int kind, String type, String name, String descriptor) {
        resolve(lookup, kind, type, name, descriptor);
    }

    static void resolve(MethodHandles.Lookup lookup, int kind, String type,
                    String name, String descriptor) {
        MHQuery initial = new MHQuery(lookup, kind, type, name, descriptor);
        try {
            MHQuery resolved = initial.resolvedQuery();
            if (!initial.equals(resolved))
                throw new AssertionError("Incorrect resolution: " + initial + " --> " + resolved);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Can't resolve " + initial);
            //e.printStackTrace(System.out);
        }
    }

    static void failResolve(int kind, String type, String name, String descriptor) {
        failResolve(lookup, kind, type, name, descriptor);
    }

    static void failResolve(MethodHandles.Lookup lookup, int kind, String type,
                    String name, String descriptor) {
        MHQuery initial = new MHQuery(lookup, kind, type, name, descriptor);
        try {
            MHQuery resolved = initial.resolvedQuery();
            throw new AssertionError("Resolution succeeded: " + initial + " --> " + resolved);
        } catch (ReflectiveOperationException e) {
            // expected
        }
    }


    /**
     * Support class encoding a lookup query, equivalent to a
     * CONSTANT_MethodHandle in a class file
     */
    public record MHQuery(MethodHandles.Lookup lookup, int kind,
                          String type, String name, String descriptor) {

        public String kindString() {
            return MethodHandleInfo.referenceKindToString(kind);
        }

        public Class<?> referencedType() throws ReflectiveOperationException {
            if (type.startsWith("[")) {
                return (Class<?>) ClassDesc.ofDescriptor(type)
                                           .resolveConstantDesc(lookup);
            } else {
                return (Class<?>) ClassDesc.of(type)
                                           .resolveConstantDesc(lookup);
            }
        }
        
        public Class<?> fieldType() throws ReflectiveOperationException {
            return (Class<?>) ClassDesc.ofDescriptor(descriptor)
                                       .resolveConstantDesc(lookup);
        }
        
        public MethodType methodType() throws ReflectiveOperationException {
            return (MethodType) MethodTypeDesc.ofDescriptor(descriptor)
                                              .resolveConstantDesc(lookup);
        }
        
        public MethodHandle resolve() throws ReflectiveOperationException {
            Class<?> reft = referencedType();
            return switch (kind) {
                case 1 -> lookup.findGetter(reft, name, fieldType());
                case 2 -> lookup.findStaticGetter(reft, name, fieldType());
                case 3 -> lookup.findSetter(reft, name, fieldType());
                case 4 -> lookup.findStaticSetter(reft, name, fieldType());
                case 5 -> {
                    if (reft.isInterface()) throw badType();
                    yield lookup.findVirtual(reft, name, methodType());
                }
                case 6 -> lookup.findStatic(reft, name, methodType());
                case 7 -> lookup.findSpecial(reft, name, methodType(), lookup.lookupClass());
                case 8 -> {
                    if (!name.equals("<init>")) throw badName();
                    yield lookup.findConstructor(reft, methodType());
                }
                case 9 -> {
                    if (!reft.isInterface()) throw badType();
                    yield lookup.findVirtual(reft, name, methodType());
                }
                default -> throw badKind();
            };
        }
        
        /** Map a direct method handle back to a lookup query */
        public MHQuery resolvedQuery() throws ReflectiveOperationException {
            MethodHandleInfo info = lookup.revealDirect(resolve());
            int kind2 = info.getReferenceKind();
            String type2 = info.getDeclaringClass().getName();
            String name2 = info.getName();
            MethodType mt = info.getMethodType();
            String descriptor2 = switch (info.getReferenceKind()) {
                case 1 -> mt.returnType().descriptorString();
                case 2 -> mt.returnType().descriptorString();
                case 3 -> mt.parameterType(0).descriptorString();
                case 4 -> mt.parameterType(0).descriptorString();
                default -> mt.descriptorString();
            };
            return new MHQuery(lookup, kind2, type2, name2, descriptor2);
        }
        
        public String toString() {
            return String.format("%s %s.%s:%s", kindString(), type, name, descriptor);
        }

        private ReflectiveOperationException badKind() {
            return new ReflectiveOperationException("unexpected kind: " + kind);
        }
        
        private ReflectiveOperationException badType() {
            return new ReflectiveOperationException("unexpected type: " + type);
        }

        private ReflectiveOperationException badName() {
            return new ReflectiveOperationException("unexpected name: " + name);
        }
    }

}
