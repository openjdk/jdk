/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8136421
 * @requires (os.simpleArch == "x64" | os.simpleArch == "sparcv9" | os.simpleArch == "aarch64")
 * @library / /testlibrary /test/lib
 * @compile ../common/CompilerToVMHelper.java
 * @build compiler.jvmci.compilerToVM.GetSymbolTest
 * @run main ClassFileInstaller jdk.vm.ci.hotspot.CompilerToVMHelper
 *                              compiler.jvmci.common.testcases.SingleImplementer
 *                              compiler.jvmci.common.testcases.SingleImplementerInterface
 *                              compiler.jvmci.compilerToVM.GetSymbolTest
 *                              compiler.jvmci.common.CTVMUtilities
 *                              jdk.test.lib.Utils
 *                              jdk.test.lib.Asserts
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *     -Xbootclasspath/a:. compiler.jvmci.compilerToVM.GetSymbolTest
 */

package compiler.jvmci.compilerToVM;

import jdk.vm.ci.hotspot.CompilerToVMHelper;
import compiler.jvmci.common.CTVMUtilities;
import compiler.jvmci.common.testcases.SingleImplementer;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.ConstantPool;
import jdk.test.lib.Utils;

public class GetSymbolTest {
    private static final int CONSTANT_POOL_UTF8_TAG = 1; // see jvms, section 4.4

    private static final Function<Member[], List<String>> NAMES = members ->
            Stream.of(members)
                    .map(Member::getName)
                    .collect(Collectors.toList());

    public static void main(String[] args) {
        new GetSymbolTest().test(SingleImplementer.class);
    }

    private void test(Class<?> aClass) {
        Utils.ensureClassIsLoaded(aClass);
        Method method;
        try {
            method = aClass.getDeclaredMethod("nonInterfaceMethod");
        } catch (NoSuchMethodException e) {
            throw new Error("TEST BUG: can't find test method", e);
        }
        HotSpotResolvedJavaMethod resolvedMethod
                = CTVMUtilities.getResolvedMethod(aClass, method);
        List<String> symbols;
        try {
            symbols = getSymbols(resolvedMethod);
        } catch (ReflectiveOperationException e) {
            throw new Error("TEST BUG: can't access private members", e);
        }
        List<String> classSymbols = new ArrayList<>();
        classSymbols.addAll(NAMES.apply(aClass.getDeclaredFields()));
        classSymbols.addAll(NAMES.apply(aClass.getDeclaredMethods()));
        // Check that all members of test class have symbols from constant pool
        for (String s : classSymbols) {
            if (!symbols.contains(s)) {
                // failed. print all symbols found by getSymbol
                System.out.println("getSymbol data:");
                for (String ctvmValue : symbols) {
                    System.out.println(ctvmValue);
                }
                throw new AssertionError("Unable to find symbol " + s
                        + " using CompilerToVM.getSymbol");
            }
        }
    }

    private List<String> getSymbols(HotSpotResolvedJavaMethod
            metaspaceMethod) throws ReflectiveOperationException {
        List<String> symbols = new ArrayList<>();
        ConstantPool pool = metaspaceMethod.getConstantPool();
        long length = pool.length();
        // jvms-4.1: The constant_pool table is indexed from 1 ...
        for (int i = 1; i < length; i++) {
            if (getTag(pool, i) == CONSTANT_POOL_UTF8_TAG) {
                long entryPointer;
                Method getEntryAt = pool.getClass()
                        .getDeclaredMethod("getEntryAt", int.class);
                getEntryAt.setAccessible(true);
                entryPointer = (Long) getEntryAt.invoke(pool, i);
                String symbol = CompilerToVMHelper.getSymbol(entryPointer);
                symbols.add(symbol);
            }
        }
        return symbols;
    }

    private int getTag(ConstantPool pool, int index)
            throws ReflectiveOperationException {
        Object jvmConstant;
        Method getTag = pool.getClass().getDeclaredMethod("getTagAt",
                int.class);
        getTag.setAccessible(true);
        jvmConstant = getTag.invoke(pool, index);
        Field tagCode = jvmConstant.getClass().getDeclaredField("tag");
        tagCode.setAccessible(true);
        return tagCode.getInt(jvmConstant);
    }
}
