/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8304031 8338406 8338546 8361909
 * @summary Testing handling of various constant descriptors in ClassFile API.
 * @modules java.base/jdk.internal.constant
 *          java.base/jdk.internal.classfile.impl
 * @run junit ConstantDescSymbolsTest
 */

import java.lang.classfile.constantpool.*;
import java.lang.constant.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.lang.classfile.ClassFile;
import java.util.stream.Stream;

import jdk.internal.classfile.impl.Util;
import jdk.internal.constant.ConstantUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.constant.ConstantDescs.*;

import static org.junit.jupiter.api.Assertions.*;

final class ConstantDescSymbolsTest {

    @Test
    void testNulls() {
        var cpb = ConstantPoolBuilder.of();
        assertThrows(NullPointerException.class, () -> cpb.loadableConstantEntry(null));
        assertThrows(NullPointerException.class, () -> cpb.constantValueEntry(null));
    }

    // Testing that primitive class descs are encoded properly as loadable constants.
    @Test
    void testPrimitiveClassDesc() throws Throwable {
        ClassDesc ape = ClassDesc.of("Ape");
        var lookup = MethodHandles.lookup();
        Class<?> a = lookup.defineClass(ClassFile.of().build(ape, clb -> {
            clb.withSuperclass(CD_Object);
            clb.withInterfaceSymbols(Supplier.class.describeConstable().orElseThrow());
            clb.withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC, cob -> {
                cob.aload(0);
                cob.invokespecial(CD_Object, INIT_NAME, MTD_void);
                cob.return_();
            });
            clb.withMethodBody("get", MethodTypeDesc.of(CD_Object), ACC_PUBLIC, cob -> {
                cob.loadConstant(CD_int);
                cob.areturn();
            });
            clb.withMethodBody("get2", MethodTypeDesc.of(CD_Class), ACC_PUBLIC, cob -> {
                Assertions.assertThrows(IllegalArgumentException.class, () -> cob.constantPool().classEntry(CD_long));
                var t = cob.constantPool().loadableConstantEntry(CD_long);
                cob.ldc(t);
                cob.areturn();
            });
        }));
        Supplier<?> t = (Supplier<?>) lookup.findConstructor(a, MethodType.methodType(void.class))
                .asType(MethodType.methodType(Supplier.class))
                .invokeExact();
        assertSame(int.class, t.get());
    }

    // Tests that condy symbols with non-static-method bootstraps are using the right lookup descriptor.
    @Test
    void testConstantDynamicNonStaticBootstrapMethod() throws Throwable {
        record CondyBoot(MethodHandles.Lookup lookup, String name, Class<?> type) {
        }
        var bootClass = CondyBoot.class.describeConstable().orElseThrow();
        var bootMhDesc = MethodHandleDesc.ofConstructor(bootClass, CD_MethodHandles_Lookup, CD_String, CD_Class);
        var condyDesc = DynamicConstantDesc.of(bootMhDesc);

        var targetCd = ClassDesc.of("Bat");
        var lookup = MethodHandles.lookup();
        Class<?> a = lookup.defineClass(ClassFile.of().build(targetCd, clb -> {
            clb.withInterfaceSymbols(Supplier.class.describeConstable().orElseThrow())
                    .withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC, cob -> cob
                            .aload(0).invokespecial(CD_Object, INIT_NAME, MTD_void).return_())
                    .withMethodBody("get", MethodTypeDesc.of(CD_Object), ACC_PUBLIC, cob -> cob
                            .loadConstant(condyDesc).areturn());
        }));
        @SuppressWarnings("unchecked")
        Supplier<CondyBoot> t = (Supplier<CondyBoot>) lookup.findConstructor(a, MethodType.methodType(void.class))
                .asType(MethodType.methodType(Supplier.class)).invokeExact();
        var cb = t.get();
        assertEquals(MethodHandles.Lookup.ORIGINAL, cb.lookup.lookupModes() & MethodHandles.Lookup.ORIGINAL);
        assertSame(a, cb.lookup.lookupClass());
        assertEquals(DEFAULT_NAME, cb.name);
        assertEquals(CondyBoot.class, cb.type);
    }

    static Stream<ClassDesc> classOrInterfaceEntries() {
        return Stream.of(
                CD_Object, CD_Float, CD_Long, CD_String, ClassDesc.of("Ape"),
                CD_String.nested("Whatever"), CD_MethodHandles_Lookup, ClassDesc.ofInternalName("one/Two"),
                ClassDesc.ofDescriptor("La/b/C;"), ConstantDescSymbolsTest.class.describeConstable().orElseThrow(),
                CD_Boolean, CD_ConstantBootstraps, CD_MethodHandles
        );
    }

    @ParameterizedTest
    @MethodSource("classOrInterfaceEntries")
    void testConstantPoolBuilderClassOrInterfaceEntry(ClassDesc cd) {
        assertTrue(cd.isClassOrInterface());
        ConstantPoolBuilder cp = ConstantPoolBuilder.of();
        var internal = ConstantUtils.dropFirstAndLastChar(cd.descriptorString());

        // 1. ClassDesc
        var ce = cp.classEntry(cd);
        assertSame(cd, ce.asSymbol(), "Symbol propagation on create");

        // 1.1. Bare addition
        assertTrue(ce.name().equalsString(internal), "Adding to bare pool");

        // 1.2. Lookup existing
        assertSame(ce, cp.classEntry(cd), "Finding by identical CD");

        // 1.3. Lookup existing - equal but different ClassDesc
        var cd1 = ClassDesc.ofDescriptor(cd.descriptorString());
        assertSame(ce, cp.classEntry(cd1), "Finding by another equal CD");

        // 1.3.1. Lookup existing - equal but different ClassDesc, equal but different string
        var cd2 = ClassDesc.ofDescriptor("" + cd.descriptorString());
        assertSame(ce, cp.classEntry(cd2), "Finding by another equal CD");

        // 1.4. Lookup existing - with utf8 internal name
        var utf8 = cp.utf8Entry(internal);
        assertSame(ce, cp.classEntry(utf8), "Finding CD by UTF8");

        // 2. ClassEntry exists, no ClassDesc
        cp = ConstantPoolBuilder.of();
        utf8 = cp.utf8Entry(internal);
        ce = cp.classEntry(utf8);
        var found = cp.classEntry(cd);
        assertSame(ce, found, "Finding non-CD CEs with CD");
        assertEquals(cd, ce.asSymbol(), "Symbol propagation on find");

        // 3. Utf8Entry exists, no ClassEntry
        cp = ConstantPoolBuilder.of();
        utf8 = cp.utf8Entry(internal);
        ce = cp.classEntry(cd);
        assertSame(utf8, ce.name(), "Reusing existing utf8 entry");
        assertEquals(cd, ce.asSymbol(), "Symbol propagation on create with utf8");
    }

    @ParameterizedTest
    @MethodSource("equalityCases")
    <T, P extends PoolEntry> void testAsSymbolEquality(ValidSymbolCase<T, P> validSymbolCase, String entryState, P p) {
        var asSymbol = validSymbolCase.translator.extractor.apply(p);
        assertEquals(validSymbolCase.sym, asSymbol, "asSym vs sym");
        assertEquals(validSymbolCase.other, asSymbol, "asSym vs other sym");
    }

    @ParameterizedTest
    @MethodSource("equalityCases")
    <T, P extends PoolEntry> void testMatchesOriginalEquality(ValidSymbolCase<T, P> validSymbolCase, String entryState, P p) {
        assertTrue(validSymbolCase.translator.tester.test(p, validSymbolCase.sym));
    }

    @ParameterizedTest
    @MethodSource("equalityCases")
    <T, P extends PoolEntry> void testMatchesEquivalentEquality(ValidSymbolCase<T, P> validSymbolCase, String entryState, P p) {
        assertTrue(validSymbolCase.translator.tester.test(p, validSymbolCase.other));
    }

    @ParameterizedTest
    @MethodSource("inequalityCases")
    <T, P extends PoolEntry> void testAsSymbolInequality(ValidSymbolCase<T, P> validSymbolCase, String stateName, P p) {
        var asSymbol = validSymbolCase.translator.extractor.apply(p);
        assertEquals(validSymbolCase.sym, asSymbol, "asSymbol vs original");
        assertNotEquals(validSymbolCase.other, asSymbol, "asSymbol vs inequal");
    }

    @ParameterizedTest
    @MethodSource("inequalityCases")
    <T, P extends PoolEntry> void testMatchesOriginalInequality(ValidSymbolCase<T, P> validSymbolCase, String stateName, P p) {
        assertTrue(validSymbolCase.translator.tester.test(p, validSymbolCase.sym));
    }

    @ParameterizedTest
    @MethodSource("inequalityCases")
    <T, P extends PoolEntry> void testMatchesNonEquivalentInequality(ValidSymbolCase<T, P> validSymbolCase, String stateName, P p) {
        assertFalse(validSymbolCase.translator.tester.test(p, validSymbolCase.other));
    }

    @ParameterizedTest
    @MethodSource("malformedCases")
    <T, P extends PoolEntry> void testAsSymbolMalformed(InvalidSymbolCase<T, P> baseCase, String entryState, P p) {
        assertThrows(IllegalArgumentException.class, () -> baseCase.translator.extractor.apply(p));
    }

    @ParameterizedTest
    @MethodSource("malformedCases")
    <T, P extends PoolEntry> void testMatchesMalformed(InvalidSymbolCase<T, P> baseCase, String entryState, P p) {
        assertFalse(baseCase.translator.tester.test(p, baseCase.target));
    }

    // Support for complex pool entry creation with different inflation states.
    // Inflation states include:
    //   - bound/unbound,
    //   - asSymbol()
    //   - matches() resulting in match
    //   - matches() resulting in mismatch

    // a pool entry, suitable for testing lazy behaviors and has descriptive name
    record StatefulPoolEntry<P>(String desc, Supplier<P> factory) {
    }

    // Test pool entry <-> nominal descriptor, also the equals methods
    record SymbolicTranslator<T, P extends PoolEntry>(String name, BiFunction<ConstantPoolBuilder, T, P> writer,
                                                      BiPredicate<P, T> tester, Function<P, T> extractor) {
        private P createUnboundEntry(T symbol) {
            ConstantPoolBuilder cpb = ConstantPoolBuilder.of(); // Temp pool does not support some entries
            return writer.apply(cpb, symbol);
        }

        @SuppressWarnings("unchecked")
        private P toBoundEntry(P unboundEntry) {
            ConstantPoolBuilder cpb = (ConstantPoolBuilder) unboundEntry.constantPool();
            int index = unboundEntry.index();
            var bytes = ClassFile.of().build(cpb.classEntry(ClassDesc.of("Test")), cpb, _ -> {
            });
            return (P) ClassFile.of().parse(bytes).constantPool().entryByIndex(index);
        }

        // Spawn entries to test from a nominal descriptor
        public Stream<StatefulPoolEntry<P>> entriesSpawner(T original) {
            return spawnBounded(() -> this.createUnboundEntry(original));
        }

        // Spawn additional bound entries to test from an initial unbound entry
        public Stream<StatefulPoolEntry<P>> spawnBounded(Supplier<P> original) {
            return Stream.of(new StatefulPoolEntry<>(original.get().toString(), original))
                    .mapMulti((s, sink) -> {
                        sink.accept(s); // unbound
                        sink.accept(new StatefulPoolEntry<>(s.desc + "+lazy", () -> toBoundEntry(s.factory.get()))); // bound
                    });
        }

        // Add extra stage of entry spawn to "inflate" entries via positive/negative tests
        public StatefulPoolEntry<P> inflateByMatching(StatefulPoolEntry<P> last, T arg, String msg) {
            return new StatefulPoolEntry<>("+matches(" + msg + ")", () -> {
                var ret = last.factory.get();
                tester.test(ret, arg);
                return ret;
            });
        }

        // Add extra stage of entry spawn to "inflate" entries via descriptor computation
        // This should not be used if the pool entry may be invalid (i.e. throws IAE)
        public StatefulPoolEntry<P> inflateByComputeSymbol(StatefulPoolEntry<P> last) {
            return new StatefulPoolEntry<>(last.desc + "+asSymbol()", () -> {
                var ret = last.factory.get();
                extractor.apply(ret);
                return ret;
            });
        }

        @Override
        public String toString() {
            return name; // don't include lambda garbage in failure reports
        }
    }

    // A case testing valid symbol sym; other is another symbol that may match or mismatch.
    record ValidSymbolCase<T, P extends PoolEntry>(SymbolicTranslator<T, P> translator, T sym, T other) {
    }

    // Current supported conversions
    static final SymbolicTranslator<String, Utf8Entry> UTF8_STRING_TRANSLATOR = new SymbolicTranslator<>("Utf8", ConstantPoolBuilder::utf8Entry, Utf8Entry::equalsString, Utf8Entry::stringValue);
    static final SymbolicTranslator<ClassDesc, Utf8Entry> UTF8_CLASS_TRANSLATOR = new SymbolicTranslator<>("FieldTypeUtf8", ConstantPoolBuilder::utf8Entry, Utf8Entry::isFieldType, Util::fieldTypeSymbol);
    static final SymbolicTranslator<MethodTypeDesc, Utf8Entry> UTF8_METHOD_TYPE_TRANSLATOR = new SymbolicTranslator<>("MethodTypeUtf8", ConstantPoolBuilder::utf8Entry, Utf8Entry::isMethodType, Util::methodTypeSymbol);
    static final SymbolicTranslator<ClassDesc, ClassEntry> CLASS_ENTRY_TRANSLATOR = new SymbolicTranslator<>("ClassEntry", ConstantPoolBuilder::classEntry, ClassEntry::matches, ClassEntry::asSymbol);
    static final SymbolicTranslator<MethodTypeDesc, MethodTypeEntry> METHOD_TYPE_ENTRY_TRANSLATOR = new SymbolicTranslator<>("MethodTypeEntry", ConstantPoolBuilder::methodTypeEntry, MethodTypeEntry::matches, MethodTypeEntry::asSymbol);
    static final SymbolicTranslator<String, StringEntry> STRING_ENTRY_TRANSLATOR = new SymbolicTranslator<>("StringEntry", ConstantPoolBuilder::stringEntry, StringEntry::equalsString, StringEntry::stringValue);
    static final SymbolicTranslator<PackageDesc, PackageEntry> PACKAGE_ENTRY_TRANSLATOR = new SymbolicTranslator<>("PackageEntry", ConstantPoolBuilder::packageEntry, PackageEntry::matches, PackageEntry::asSymbol);
    static final SymbolicTranslator<ModuleDesc, ModuleEntry> MODULE_ENTRY_TRANSLATOR = new SymbolicTranslator<>("ModuleEntry", ConstantPoolBuilder::moduleEntry, ModuleEntry::matches, ModuleEntry::asSymbol);

    // Create arguments of tuple (ValidSymbolCase, entryState, PoolEntry) to verify symbolic behavior of pool entries
    // with particular inflation states
    static <T, P extends PoolEntry> void specializeInflation(ValidSymbolCase<T, P> validSymbolCase, Consumer<Arguments> callArgs) {
        validSymbolCase.translator.entriesSpawner(validSymbolCase.sym)
                .<StatefulPoolEntry<P>>mapMulti((src, sink) -> {
                    sink.accept(src);
                    sink.accept(validSymbolCase.translator.inflateByMatching(src, validSymbolCase.sym, "same symbol"));
                    sink.accept(validSymbolCase.translator.inflateByMatching(src, validSymbolCase.other, "another symbol"));
                    sink.accept(validSymbolCase.translator.inflateByComputeSymbol(src));
                })
                .forEach(stateful -> callArgs.accept(Arguments.of(validSymbolCase, stateful.desc, stateful.factory.get())));
    }

    static Stream<Arguments> equalityCases() {
        return Stream.of(
                new ValidSymbolCase<>(CLASS_ENTRY_TRANSLATOR, CD_Object, ClassDesc.ofInternalName("java/lang/Object")), // class or interface
                new ValidSymbolCase<>(CLASS_ENTRY_TRANSLATOR, CD_Object.arrayType(), ClassDesc.ofDescriptor("[Ljava/lang/Object;")), // array
                new ValidSymbolCase<>(UTF8_CLASS_TRANSLATOR, CD_int, ClassDesc.ofDescriptor("I")), // primitive
                new ValidSymbolCase<>(UTF8_CLASS_TRANSLATOR, CD_Object, ClassDesc.ofInternalName("java/lang/Object")), // class or interface
                new ValidSymbolCase<>(UTF8_CLASS_TRANSLATOR, CD_Object.arrayType(), ClassDesc.ofDescriptor("[Ljava/lang/Object;")), // array
                new ValidSymbolCase<>(UTF8_STRING_TRANSLATOR, "Ab\u0000c", "Ab\u0000c"),
                new ValidSymbolCase<>(UTF8_METHOD_TYPE_TRANSLATOR, MTD_void, MethodTypeDesc.ofDescriptor("()V")),
                new ValidSymbolCase<>(UTF8_METHOD_TYPE_TRANSLATOR, MethodTypeDesc.of(CD_int, CD_Long), MethodTypeDesc.ofDescriptor("(Ljava/lang/Long;)I")),
                new ValidSymbolCase<>(METHOD_TYPE_ENTRY_TRANSLATOR, MethodTypeDesc.of(CD_Object), MethodTypeDesc.ofDescriptor("()Ljava/lang/Object;")),
                new ValidSymbolCase<>(STRING_ENTRY_TRANSLATOR, "Ape", new String("Ape".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)),
                new ValidSymbolCase<>(PACKAGE_ENTRY_TRANSLATOR, PackageDesc.of("java.lang"), PackageDesc.ofInternalName("java/lang")),
                new ValidSymbolCase<>(MODULE_ENTRY_TRANSLATOR, ModuleDesc.of("java.base"), ModuleDesc.of(new String("java.base".getBytes(StandardCharsets.US_ASCII), StandardCharsets.US_ASCII)))
        ).mapMulti(ConstantDescSymbolsTest::specializeInflation);
    }

    static Stream<Arguments> inequalityCases() {
        return Stream.of(
                new ValidSymbolCase<>(CLASS_ENTRY_TRANSLATOR, CD_Object, ClassDesc.ofInternalName("java/io/Object")), // class or interface
                new ValidSymbolCase<>(CLASS_ENTRY_TRANSLATOR, CD_Object.arrayType(), ClassDesc.ofDescriptor("[Ljava/lang/String;")), // array
                new ValidSymbolCase<>(UTF8_CLASS_TRANSLATOR, CD_int, ClassDesc.ofDescriptor("S")), // primitive
                new ValidSymbolCase<>(UTF8_CLASS_TRANSLATOR, CD_Object, ClassDesc.ofInternalName("java/lang/String")), // class or interface
                new ValidSymbolCase<>(UTF8_CLASS_TRANSLATOR, CD_Object.arrayType(), ClassDesc.ofDescriptor("[Ljava/lang/System;")), // array
                new ValidSymbolCase<>(UTF8_STRING_TRANSLATOR, "Ab\u0000c", "Abdc"),
                new ValidSymbolCase<>(UTF8_METHOD_TYPE_TRANSLATOR, MTD_void, MethodTypeDesc.ofDescriptor("()I")),
                new ValidSymbolCase<>(UTF8_METHOD_TYPE_TRANSLATOR, MethodTypeDesc.of(CD_int, CD_Short), MethodTypeDesc.ofDescriptor("(Ljava/lang/Long;)I")),
                new ValidSymbolCase<>(METHOD_TYPE_ENTRY_TRANSLATOR, MethodTypeDesc.of(CD_String), MethodTypeDesc.ofDescriptor("()Ljava/lang/Object;")),
                new ValidSymbolCase<>(STRING_ENTRY_TRANSLATOR, "Cat", new String("Ape".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)),
                new ValidSymbolCase<>(PACKAGE_ENTRY_TRANSLATOR, PackageDesc.of("java.lang"), PackageDesc.ofInternalName("java/util")),
                new ValidSymbolCase<>(MODULE_ENTRY_TRANSLATOR, ModuleDesc.of("java.base"), ModuleDesc.of(new String("java.desktop".getBytes(StandardCharsets.US_ASCII), StandardCharsets.US_ASCII)))
        ).mapMulti(ConstantDescSymbolsTest::specializeInflation);
    }

    record InvalidSymbolCase<T, P extends PoolEntry>(SymbolicTranslator<T, P> translator, Supplier<P> factory, T target) {
    }

    // Type hint function
    private static <P extends PoolEntry> Supplier<P> badFactory(Function<ConstantPoolBuilder, P> func) {
        return () -> func.apply(ConstantPoolBuilder.of());
    }

    static <T, P extends PoolEntry> void specializeInflation(InvalidSymbolCase<T, P> invalidSymbolCase, Consumer<Arguments> callArgs) {
        invalidSymbolCase.translator.spawnBounded(invalidSymbolCase.factory)
                .<StatefulPoolEntry<P>>mapMulti((src, sink) -> {
                    sink.accept(src);
                    sink.accept(invalidSymbolCase.translator.inflateByMatching(src, invalidSymbolCase.target, "target"));
                })
                .forEach(stateful -> callArgs.accept(Arguments.of(invalidSymbolCase, stateful.desc, stateful.factory.get())));
    }

    static Stream<Arguments> malformedCases() {
        return Stream.of(
                new InvalidSymbolCase<>(CLASS_ENTRY_TRANSLATOR, badFactory(b -> b.classEntry(b.utf8Entry("java.lang.Object"))), CD_Object), // class or interface
                new InvalidSymbolCase<>(CLASS_ENTRY_TRANSLATOR, badFactory(b -> b.classEntry(b.utf8Entry("[Ljava/lang/String"))), CD_String.arrayType()), // array
                new InvalidSymbolCase<>(UTF8_CLASS_TRANSLATOR, badFactory(b -> b.utf8Entry("int")), ClassDesc.ofDescriptor("I")), // primitive
                new InvalidSymbolCase<>(UTF8_CLASS_TRANSLATOR, badFactory(b -> b.utf8Entry("Ljava/lang/String")), CD_String), // class or interface
                new InvalidSymbolCase<>(UTF8_CLASS_TRANSLATOR, badFactory(b -> b.utf8Entry("[Ljava/lang/String")), CD_String.arrayType()), // array
                new InvalidSymbolCase<>(METHOD_TYPE_ENTRY_TRANSLATOR, badFactory(b -> b.methodTypeEntry(b.utf8Entry("()"))), MTD_void),
                new InvalidSymbolCase<>(METHOD_TYPE_ENTRY_TRANSLATOR, badFactory(b -> b.methodTypeEntry(b.utf8Entry("(V)"))), MTD_void),
                new InvalidSymbolCase<>(UTF8_METHOD_TYPE_TRANSLATOR, badFactory(b -> b.utf8Entry("()Ljava/lang/String")), MethodTypeDesc.of(CD_String)),
                new InvalidSymbolCase<>(PACKAGE_ENTRY_TRANSLATOR, badFactory(b -> b.packageEntry(b.utf8Entry("java.lang"))), PackageDesc.of("java.lang")),
                new InvalidSymbolCase<>(MODULE_ENTRY_TRANSLATOR, badFactory(b -> b.moduleEntry(b.utf8Entry("java@base"))), ModuleDesc.of("java.base"))
        ).mapMulti(ConstantDescSymbolsTest::specializeInflation);
    }
}
