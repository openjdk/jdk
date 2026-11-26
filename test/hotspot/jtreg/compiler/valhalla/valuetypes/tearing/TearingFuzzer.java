/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package compiler.valhalla.valuetypes.tearing;

import compiler.lib.compile_framework.CompileFramework;
import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import java.util.ArrayList;

/*
 * @test
 * @key stress randomness
 * @bug 8398028
 * @summary Verify that flat accesses always satisfy the atomicity requirement.
 * @enablePreview
 * @library /test/lib /
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -XX:ObjectAlignmentInBytes=16
 *                   -XX:+UnlockExperimentalVMOptions -XX:+Use16ByteAtomicFlattening
 *                   -Xint ${test.main.class}
 * @run main/othervm -XX:ObjectAlignmentInBytes=16
 *                   -XX:+UnlockExperimentalVMOptions -XX:+Use16ByteAtomicFlattening
 *                   -XX:TieredStopAtLevel=3 ${test.main.class}
 */
public class TearingFuzzer {
    private static final String PACKAGE_NAME = TearingFuzzer.class.getPackageName();
    private static final String CLASS_NAME = TearingFuzzer.class.getSimpleName() + "Test";

    // JDK-8397262: Fail with C2
    public static void main(String[] args) {
        String testClassCanonicalName = PACKAGE_NAME + "." + CLASS_NAME;
        var comp = new CompileFramework();
        comp.addJavaSourceCode(testClassCanonicalName, generate(comp));
        comp.compile(
                "--enable-preview", "-source", Integer.toString(Runtime.version().feature()),
                "--add-exports=java.base/jdk.internal.value=ALL-UNNAMED",
                "--add-exports=java.base/jdk.internal.vm.annotation=ALL-UNNAMED"
        );
        comp.invoke(testClassCanonicalName, "testEntry", new String[0]);
    }

    private static String generate(CompileFramework comp) {
        int myValueIdxBegin = 1;
        int myValueIdxEnd = 8;
        var tokens = new ArrayList<TemplateToken>();
        tokens.add(generateMyValueTemplate().asToken());
        var holderTemplate = generateHolderTemplate();
        var loadInstanceTemplate = generateLoadInstanceTemplate();
        var loadArrayTemplate = generateLoadArrayTemplate();
        var storeInstanceTemplate = generateStoreInstanceTemplate();
        var storeArrayTemplate = generateStoreArrayTemplate();
        var testInstanceTemplate = generateTestInstanceTemplate();
        var testArrayTemplate = generateTestArrayTemplate();
        for (int myValueIdx = myValueIdxBegin; myValueIdx < myValueIdxEnd; myValueIdx++) {
            tokens.add(holderTemplate.asToken(myValueIdx));
            tokens.add(loadInstanceTemplate.asToken(myValueIdx));
            tokens.add(loadArrayTemplate.asToken(myValueIdx));
            tokens.add(storeInstanceTemplate.asToken(myValueIdx));
            tokens.add(storeArrayTemplate.asToken(myValueIdx));
            tokens.add(testInstanceTemplate.asToken(myValueIdx, 1, 1));
            tokens.add(testInstanceTemplate.asToken(myValueIdx, 2, 1));
            tokens.add(testArrayTemplate.asToken(myValueIdx, 1, 1));
            tokens.add(testArrayTemplate.asToken(myValueIdx, 1, 2));
            tokens.add(testArrayTemplate.asToken(myValueIdx, 2, 1));
            tokens.add(testArrayTemplate.asToken(myValueIdx, 2, 2));
            tokens.add(testArrayTemplate.asToken(myValueIdx, 3, 1));
            tokens.add(testArrayTemplate.asToken(myValueIdx, 3, 2));
        }

        var mainBodyTokens = new ArrayList<TemplateToken>();
        var mainBodyTemplate = generateMainBodyTemplate();
        for (int myValueIdx = myValueIdxBegin; myValueIdx < myValueIdxEnd; myValueIdx++) {
            mainBodyTokens.add(mainBodyTemplate.asToken("NullableInstance" + myValueIdx, 1, 1));
            mainBodyTokens.add(mainBodyTemplate.asToken("NullableInstance" + myValueIdx, 2, 1));
            mainBodyTokens.add(mainBodyTemplate.asToken("NullFreeInstance" + myValueIdx, 1, 1));
            mainBodyTokens.add(mainBodyTemplate.asToken("NullFreeInstance" + myValueIdx, 2, 1));
            mainBodyTokens.add(mainBodyTemplate.asToken("NullableArray" + myValueIdx, 1, 1));
            mainBodyTokens.add(mainBodyTemplate.asToken("NullableArray" + myValueIdx, 1, 2));
            mainBodyTokens.add(mainBodyTemplate.asToken("NullableArray" + myValueIdx, 2, 1));
            mainBodyTokens.add(mainBodyTemplate.asToken("NullableArray" + myValueIdx, 2, 2));
            mainBodyTokens.add(mainBodyTemplate.asToken("NullableArray" + myValueIdx, 3, 1));
            mainBodyTokens.add(mainBodyTemplate.asToken("NullableArray" + myValueIdx, 3, 2));
            mainBodyTokens.add(mainBodyTemplate.asToken("NullFreeArray" + myValueIdx, 1, 1));
            mainBodyTokens.add(mainBodyTemplate.asToken("NullFreeArray" + myValueIdx, 1, 2));
            mainBodyTokens.add(mainBodyTemplate.asToken("NullFreeArray" + myValueIdx, 2, 1));
            mainBodyTokens.add(mainBodyTemplate.asToken("NullFreeArray" + myValueIdx, 2, 2));
            mainBodyTokens.add(mainBodyTemplate.asToken("NullFreeArray" + myValueIdx, 3, 1));
            mainBodyTokens.add(mainBodyTemplate.asToken("NullFreeArray" + myValueIdx, 3, 2));
        }

        var template = Template.make(() -> Template.scope(
                Template.let("packageName", PACKAGE_NAME),
                Template.let("className", CLASS_NAME),
                Template.let("classpath", comp.getEscapedClassPathOfCompiledClasses()),
                """
                package #packageName;
                // --- IMPORTS start ---
                import java.lang.invoke.VarHandle;
                import java.util.concurrent.ThreadLocalRandom;
                import java.util.random.RandomGeneratorFactory;
                import java.util.Random;
                import jdk.internal.value.ValueClass;
                import jdk.internal.vm.annotation.NullRestricted;
                import jdk.test.lib.Asserts;
                import jdk.test.lib.Utils;
                // --- IMPORTS end   ---
                public class #className {
                    public static void testEntry() throws InterruptedException {
                """,
                mainBodyTokens,
                """
                    }
                // --- LIST OF TESTS start ---
                """,
                tokens,
                """
                // --- LIST OF TESTS end   ---
                }
                """
        ));

        return template.render();
    }

    private static Template.ZeroArgs generateMyValueTemplate() {
        return Template.make(() -> Template.scope(
                """
                private static final Random RANDOM = Utils.getRandomInstance();
                private static final int ARRAY_LENGTH = 100;

                private static value class MyValue1 {
                    byte x;

                    MyValue1(int v) {
                        this.x = (byte) v;
                        super();
                    }

                    void verify() {}
                }

                private static value class MyValue2 {
                    byte x;
                    byte y;

                    MyValue2(int v) {
                        this.x = (byte) v;
                        this.y = (byte) v;
                        super();
                    }

                    void verify() {
                        Asserts.assertTrue(x == y);
                    }
                }

                private static value class MyValue3 {
                    byte x;
                    byte y;
                    byte z;

                    MyValue3(int v) {
                        this.x = (byte) v;
                        this.y = (byte) v;
                        this.z = (byte) v;
                        super();
                    }

                    void verify() {
                        Asserts.assertTrue(x == y && y == z);
                    }
                }

                private static value class MyValue4 {
                    short x;
                    short y;

                    MyValue4(int v) {
                        this.x = (short) v;
                        this.y = (short) v;
                        super();
                    }

                    void verify() {
                        Asserts.assertTrue(x == y);
                    }
                }

                private static value class MyValue5 {
                    short x;
                    short y;
                    short z;

                    MyValue5(int v) {
                        this.x = (short) v;
                        this.y = (short) v;
                        this.z = (short) v;
                        super();
                    }

                    void verify() {
                        Asserts.assertTrue(x == y && y == z);
                    }
                }

                private static value class MyValue6 {
                    int x;
                    int y;

                    MyValue6(int v) {
                        this.x = v;
                        this.y = v;
                        super();
                    }

                    void verify() {
                        Asserts.assertTrue(x == y);
                    }
                }

                private static value class MyValue7 {
                    int x;
                    int y;
                    int z;

                    MyValue7(int v) {
                        this.x = v;
                        this.y = v;
                        this.z = v;
                        super();
                    }

                    void verify() {
                        Asserts.assertTrue(x == y && y == z);
                    }
                }
                """
        ));
    }

    private static Template.OneArg<Integer> generateHolderTemplate() {
        return Template.make("idx", _ -> Template.scope(
                """
                private static class NullableInstance#idx implements Cloneable {
                    MyValue#idx v;

                    @Override
                    public NullableInstance#idx clone() {
                        try {
                            return (NullableInstance#idx) super.clone();
                        } catch (CloneNotSupportedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

                private static class NullFreeInstance#idx implements Cloneable {
                    @NullRestricted
                    MyValue#idx v;

                    NullFreeInstance#idx(MyValue#idx v) {
                        this.v = v;
                        super();
                    }

                    @Override
                    public NullFreeInstance#idx clone() {
                        try {
                            return (NullFreeInstance#idx) super.clone();
                        } catch (CloneNotSupportedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

                private static final NullableInstance#idx NULLABLE_INSTANCE_#idx = new NullableInstance#idx();
                private static final NullFreeInstance#idx NULL_FREE_INSTANCE_#idx = new NullFreeInstance#idx(new MyValue#idx(0));
                private static final MyValue#idx[] NULLABLE_ARRAY_#idx = new MyValue#idx[ARRAY_LENGTH];
                private static final MyValue#idx[] NULL_FREE_ARRAY_#idx = (MyValue#idx[]) ValueClass.newNullRestrictedAtomicArray(MyValue#idx.class, ARRAY_LENGTH, new MyValue#idx(0));
                """
        ));
    }

    private static Template.OneArg<Integer> generateLoadInstanceTemplate() {
        return Template.make("idx", _ -> Template.scope(
                """
                private static void load1NullableInstance#idx() {
                    var v = NULLABLE_INSTANCE_#idx.v;
                    if (v != null) {
                        v.verify();
                    }
                }

                private static void load1NullFreeInstance#idx() {
                    NULL_FREE_INSTANCE_#idx.v.verify();
                }

                private static NullableInstance#idx nullableInstance#{idx}Tmp2;
                private static void load2NullableInstance#idx() {
                    nullableInstance#{idx}Tmp2 = NULLABLE_INSTANCE_#idx.clone();
                    VarHandle.fullFence();
                    var v = nullableInstance#{idx}Tmp2.v;
                    if (v != null) {
                        v.verify();
                    }
                }

                private static NullFreeInstance#idx nullFreeInstance#{idx}Tmp2;
                private static void load2NullFreeInstance#idx() {
                    nullFreeInstance#{idx}Tmp2 = NULL_FREE_INSTANCE_#idx.clone();
                    VarHandle.fullFence();
                    nullFreeInstance#{idx}Tmp2.v.verify();
                }
                """
        ));
    }

    private static Template.OneArg<Integer> generateLoadArrayTemplate() {
        return Template.make("idx", _ -> Template.scope(
                """
                private static void load1NullableArray#idx(int idx) {
                    var v = NULLABLE_ARRAY_#idx[idx];
                    if (v != null) {
                        v.verify();
                    }
                }

                private static void load1NullFreeArray#idx(int idx) {
                    NULL_FREE_ARRAY_#idx[idx].verify();
                }

                private static MyValue#idx[] nullableArray#{idx}Tmp2;
                private static void load2NullableArray#idx(int idx) {
                    nullableArray#{idx}Tmp2 = NULLABLE_ARRAY_#idx.clone();
                    VarHandle.fullFence();
                    for (int i = 0; i < nullableArray#{idx}Tmp2.length; i++) {
                        var v = nullableArray#{idx}Tmp2[i];
                        if (v != null) {
                            v.verify();
                        }
                    }
                }

                private static MyValue#idx[] nullFreeArray#{idx}Tmp2;
                private static void load2NullFreeArray#idx(int idx) {
                    nullFreeArray#{idx}Tmp2 = NULL_FREE_ARRAY_#idx.clone();
                    VarHandle.fullFence();
                    for (int i = 0; i < nullFreeArray#{idx}Tmp2.length; i++) {
                        nullFreeArray#{idx}Tmp2[i].verify();
                    }
                }

                private static final MyValue#idx[] NULLABLE_ARRAY_#{idx}_TMP3 = new MyValue#idx[ARRAY_LENGTH];
                private static void load3NullableArray#idx(int len) {
                    System.arraycopy(NULLABLE_ARRAY_#idx, 0, NULLABLE_ARRAY_#{idx}_TMP3, 0, len);
                    VarHandle.fullFence();
                    for (int i = 0; i < len; i++) {
                        var v = NULLABLE_ARRAY_#{idx}_TMP3[i];
                        if (v != null) {
                            v.verify();
                        }
                    }
                }

                private static MyValue#idx[] NULL_FREE_ARRAY_#{idx}_TMP3 = (MyValue#idx[]) ValueClass.newNullRestrictedAtomicArray(MyValue#idx.class, ARRAY_LENGTH, new MyValue#idx(0));
                private static void load3NullFreeArray#idx(int len) {
                    System.arraycopy(NULL_FREE_ARRAY_#idx, 0, NULL_FREE_ARRAY_#{idx}_TMP3, 0, len);
                    VarHandle.fullFence();
                    for (int i = 0; i < len; i++) {
                        NULL_FREE_ARRAY_#{idx}_TMP3[i].verify();
                    }
                }
                """
        ));
    }

    private static Template.OneArg<Integer> generateStoreInstanceTemplate() {
        return Template.make("idx", _ -> Template.scope(
                """
                private static void store1NullableInstance#idx(MyValue#idx v) {
                    NULLABLE_INSTANCE_#idx.v = v;
                }

                private static void store1NullFreeInstance#idx(MyValue#idx v) {
                    NULL_FREE_INSTANCE_#idx.v = v;
                }
                """
        ));
    }

    private static Template.OneArg<Integer> generateStoreArrayTemplate() {
        return Template.make("idx", _ -> Template.scope(
                """
                private static void store1NullableArray#idx(MyValue#idx v, int idx) {
                    NULLABLE_ARRAY_#idx[idx] = v;
                }

                private static void store1NullFreeInstance#idx(MyValue#idx v, int idx) {
                    NULL_FREE_ARRAY_#idx[idx] = v;
                }

                static final MyValue#idx[] NULLABLE_ARRAY_#{idx}_TMP4 = new MyValue#idx[ARRAY_LENGTH];
                private static void store2NullableArray#idx(MyValue#idx v, int len) {
                    for (int i = 0; i < len; i++) {
                        NULLABLE_ARRAY_#{idx}_TMP4[i] = v;
                    }
                    VarHandle.fullFence();
                    System.arraycopy(NULLABLE_ARRAY_#{idx}_TMP4, 0, NULLABLE_ARRAY_#idx, 0, len);
                }

                static final MyValue#idx[] NULL_FREE_ARRAY_#{idx}_TMP4 = (MyValue#idx[]) ValueClass.newNullRestrictedAtomicArray(MyValue#idx.class, ARRAY_LENGTH, new MyValue#idx(0));
                private static void store2NullFreeArray#idx(MyValue#idx v, int len) {
                    for (int i = 0; i < len; i++) {
                        NULL_FREE_ARRAY_#{idx}_TMP4[i] = v;
                    }
                    VarHandle.fullFence();
                    System.arraycopy(NULL_FREE_ARRAY_#{idx}_TMP4, 0, NULL_FREE_ARRAY_#idx, 0, len);
                }
                """
        ));
    }

    private static Template.ThreeArgs<Integer, Integer, Integer> generateTestInstanceTemplate() {
        return Template.make("idx", "loadIdx", "storeIdx", (_, _, _) -> Template.scope(
                """
                private static void testNullableInstance#{idx}Load#{loadIdx}Store#{storeIdx}() throws InterruptedException {
                    var loadThread = new Thread(() -> {
                        for (int i = 0; i < 20000; i++) {
                            load#{loadIdx}NullableInstance#idx();
                        }
                    });

                    long seed = RANDOM.nextLong();
                    var storeThread = new Thread(() -> {
                        var random = RandomGeneratorFactory.getDefault().create(seed);
                        for (int i = 0; i < 20000; i++) {
                            MyValue#idx v;
                            if ((i & 7) == 0) {
                                v = null;
                            } else {
                                v = new MyValue#idx(random.nextInt());
                            }
                            store#{storeIdx}NullableInstance#idx(v);
                        }
                    });

                    loadThread.start();
                    storeThread.start();
                    loadThread.join();
                    storeThread.join();
                }

                private static void testNullFreeInstance#{idx}Load#{loadIdx}Store#{storeIdx}() throws InterruptedException {
                    var loadThread = new Thread(() -> {
                        for (int i = 0; i < 20000; i++) {
                            load#{loadIdx}NullFreeInstance#idx();
                        }
                    });

                    long seed = RANDOM.nextLong();
                    var storeThread = new Thread(() -> {
                        var random = RandomGeneratorFactory.getDefault().create(seed);
                        for (int i = 0; i < 20000; i++) {
                            MyValue#idx v = new MyValue#idx(random.nextInt());
                            store#{storeIdx}NullableInstance#idx(v);
                        }
                    });

                    loadThread.start();
                    storeThread.start();
                    loadThread.join();
                    storeThread.join();
                }
                """
        ));
    }

    private static Template.ThreeArgs<Integer, Integer, Integer> generateTestArrayTemplate() {
        return Template.make("idx", "loadIdx", "storeIdx", (_, _, _) -> Template.scope(
                """
                private static void testNullableArray#{idx}Load#{loadIdx}Store#{storeIdx}() throws InterruptedException {
                    int idx = RANDOM.nextInt(ARRAY_LENGTH);
                    var loadThread = new Thread(() -> {
                        for (int i = 0; i < 20000; i++) {
                            load#{loadIdx}NullableArray#idx(idx);
                        }
                    });

                    long seed = RANDOM.nextLong();
                    var storeThread = new Thread(() -> {
                        var random = RandomGeneratorFactory.getDefault().create(seed);
                        for (int i = 0; i < 20000; i++) {
                            MyValue#idx v;
                            if ((i & 7) == 0) {
                                v = null;
                            } else {
                                v = new MyValue#idx(random.nextInt());
                            }
                            store#{storeIdx}NullableArray#idx(v, idx);
                        }
                    });

                    loadThread.start();
                    storeThread.start();
                    loadThread.join();
                    storeThread.join();
                }

                private static void testNullFreeArray#{idx}Load#{loadIdx}Store#{storeIdx}() throws InterruptedException {
                    int idx = RANDOM.nextInt(ARRAY_LENGTH);
                    var loadThread = new Thread(() -> {
                        for (int i = 0; i < 20000; i++) {
                            load#{loadIdx}NullFreeArray#idx(idx);
                        }
                    });

                    long seed = RANDOM.nextLong();
                    var storeThread = new Thread(() -> {
                        var random = RandomGeneratorFactory.getDefault().create(seed);
                        for (int i = 0; i < 20000; i++) {
                            MyValue#idx v = new MyValue#idx(random.nextInt());
                            store#{storeIdx}NullableArray#idx(v, idx);
                        }
                    });

                    loadThread.start();
                    storeThread.start();
                    loadThread.join();
                    storeThread.join();
                }
                """
        ));
    }

    private static Template.ThreeArgs<String, Integer, Integer> generateMainBodyTemplate() {
        return Template.make("key", "loadIdx", "storeIdx", (_, _, _) -> Template.scope(
                """
                test#{key}Load#{loadIdx}Store#{storeIdx}();
                """
        ));
    }
}
