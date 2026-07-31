/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @summary Test ObjectMethods::bootstrap call via condy
 * @modules java.base/jdk.internal.value
 * @enablePreview
 * @run testng/othervm ObjectMethodsViaCondy
 */

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup.ClassOption;
import java.lang.invoke.MethodType;
import java.lang.runtime.ObjectMethods;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import org.testng.annotations.Test;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;
import static java.lang.invoke.MethodType.methodType;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;

public class ObjectMethodsViaCondy {
    public static value record ValueRecord(int i, String name) {
        static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
        static final MethodType TO_STRING_DESC = methodType(String.class, ValueRecord.class);
        static final String NAME_LIST = "i;name";
        private static final ClassDesc CD_ValueRecord = ValueRecord.class.describeConstable().orElseThrow();
        private static final ClassDesc CD_ObjectMethods = ObjectMethods.class.describeConstable().orElseThrow();
        private static final MethodTypeDesc MTD_ObjectMethods_bootstrap = MethodTypeDesc.of(CD_Object, CD_MethodHandles_Lookup, CD_String,
                ClassDesc.ofInternalName("java/lang/invoke/TypeDescriptor"), CD_Class, CD_String, CD_MethodHandle.arrayType());
        static final List<DirectMethodHandleDesc> ACCESSORS = accessors();

        private static List<DirectMethodHandleDesc> accessors() {
            try {
                return List.of(
                        MethodHandleDesc.ofField(DirectMethodHandleDesc.Kind.GETTER, CD_ValueRecord, "i", CD_int),
                        MethodHandleDesc.ofField(DirectMethodHandleDesc.Kind.GETTER, CD_ValueRecord, "name", CD_String)
                );
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        /**
         * Returns the method handle for the given method for this ValueRecord class.
         * This method defines a hidden class to invoke the ObjectMethods::bootstrap method
         * via condy.
         *
         * @param methodName   the name of the method to generate, which must be one of
         *                     {@code "equals"}, {@code "hashCode"}, or {@code "toString"}
         */
        static MethodHandle makeBootstrapMethod(String methodName) throws Throwable {
            String className = "Test-" + methodName;
            ClassDesc testClass = ClassDesc.of(className);
            byte[] bytes = ClassFile.of().build(testClass, clb -> clb
                    .withVersion(JAVA_19_VERSION, 0)
                    .withFlags(ACC_FINAL | ACC_SUPER)
                    .withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC, cob -> cob
                            .aload(0)
                            .invokespecial(CD_Object, INIT_NAME, MTD_void)
                            .return_())
                    .withMethodBody("bootstrap", MethodTypeDesc.of(CD_Object), ACC_PUBLIC | ACC_STATIC, cob -> cob
                            .loadConstant(DynamicConstantDesc.ofNamed(
                                    MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC, CD_ObjectMethods,
                                            "bootstrap", MTD_ObjectMethods_bootstrap),
                                    methodName,
                                    CD_MethodHandle,
                                    Stream.concat(Stream.of(CD_ValueRecord, NAME_LIST), ACCESSORS.stream()).toArray(ConstantDesc[]::new)))
                            .areturn())
            );

            Path p = Paths.get(className + ".class");
            try (OutputStream os = Files.newOutputStream(p)) {
                os.write(bytes);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            MethodHandles.Lookup lookup = LOOKUP.defineHiddenClass(bytes, true, ClassOption.NESTMATE);
            MethodType mtype = MethodType.methodType(Object.class);
            MethodHandle mh = lookup.findStatic(lookup.lookupClass(), "bootstrap", mtype);
            return (MethodHandle) mh.invoke();
        }
    }

    @Test
    public void testToString() throws Throwable {
        MethodHandle handle = ValueRecord.makeBootstrapMethod("toString");
        assertEquals((String)handle.invokeExact(new ValueRecord(10, "ten")), "ValueRecord[i=10, name=ten]");
        assertEquals((String)handle.invokeExact(new ValueRecord(40, "forty")), "ValueRecord[i=40, name=forty]");
    }

    @Test
    public void testToEquals() throws Throwable {
        MethodHandle handle = ValueRecord.makeBootstrapMethod("equals");
        assertTrue((boolean)handle.invoke(new ValueRecord(10, "ten"), new ValueRecord(10, "ten")));
        assertFalse((boolean)handle.invoke(new ValueRecord(11, "eleven"), new ValueRecord(10, "ten")));
    }
}
