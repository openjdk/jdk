/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @run testng/othervm --enable-native-access=ALL-UNNAMED StdLibTest
 */

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.incubator.foreign.*;

import org.testng.annotations.*;

import static org.testng.Assert.*;

@Test
public class StdLibTest extends NativeTestHelper {

    final static CLinker abi = CLinker.systemCLinker();

    private StdLibHelper stdLibHelper = new StdLibHelper();

    @Test(dataProvider = "stringPairs")
    void test_strcat(String s1, String s2) throws Throwable {
        assertEquals(stdLibHelper.strcat(s1, s2), s1 + s2);
    }

    @Test(dataProvider = "stringPairs")
    void test_strcmp(String s1, String s2) throws Throwable {
        assertEquals(Math.signum(stdLibHelper.strcmp(s1, s2)), Math.signum(s1.compareTo(s2)));
    }

    @Test(dataProvider = "strings")
    void test_puts(String s) throws Throwable {
        assertTrue(stdLibHelper.puts(s) >= 0);
    }

    @Test(dataProvider = "strings")
    void test_strlen(String s) throws Throwable {
        assertEquals(stdLibHelper.strlen(s), s.length());
    }

    @Test(dataProvider = "instants")
    void test_time(Instant instant) throws Throwable {
        StdLibHelper.Tm tm = stdLibHelper.gmtime(instant.getEpochSecond());
        LocalDateTime localTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        assertEquals(tm.sec(), localTime.getSecond());
        assertEquals(tm.min(), localTime.getMinute());
        assertEquals(tm.hour(), localTime.getHour());
        //day pf year in Java has 1-offset
        assertEquals(tm.yday(), localTime.getDayOfYear() - 1);
        assertEquals(tm.mday(), localTime.getDayOfMonth());
        //days of week starts from Sunday in C, but on Monday in Java, also account for 1-offset
        assertEquals((tm.wday() + 6) % 7, localTime.getDayOfWeek().getValue() - 1);
        //month in Java has 1-offset
        assertEquals(tm.mon(), localTime.getMonth().getValue() - 1);
        assertEquals(tm.isdst(), ZoneOffset.UTC.getRules()
                .isDaylightSavings(Instant.ofEpochMilli(instant.getEpochSecond() * 1000)));
    }

    @Test(dataProvider = "ints")
    void test_qsort(List<Integer> ints) throws Throwable {
        if (ints.size() > 0) {
            int[] input = ints.stream().mapToInt(i -> i).toArray();
            int[] sorted = stdLibHelper.qsort(input);
            Arrays.sort(input);
            assertEquals(sorted, input);
        }
    }

    @Test
    void test_rand() throws Throwable {
        int val = stdLibHelper.rand();
        for (int i = 0 ; i < 100 ; i++) {
            int newVal = stdLibHelper.rand();
            if (newVal != val) {
                return; //ok
            }
            val = newVal;
        }
        fail("All values are the same! " + val);
    }

    @Test(dataProvider = "printfArgs")
    void test_printf(List<PrintfArg> args) throws Throwable {
        String formatArgs = args.stream()
                .map(a -> a.format)
                .collect(Collectors.joining(","));

        String formatString = "hello(" + formatArgs + ")\n";

        String expected = String.format(formatString, args.stream()
                .map(a -> a.javaValue).toArray());

        int found = stdLibHelper.printf(formatString, args);
        assertEquals(found, expected.length());
    }

    @Test(dataProvider = "printfArgs")
    void test_vprintf(List<PrintfArg> args) throws Throwable {
        String formatArgs = args.stream()
                .map(a -> a.format)
                .collect(Collectors.joining(","));

        String formatString = "hello(" + formatArgs + ")\n";

        String expected = String.format(formatString, args.stream()
                .map(a -> a.javaValue).toArray());

        int found = stdLibHelper.vprintf(formatString, args);
        assertEquals(found, expected.length());
    }

    static class StdLibHelper {

        final static MethodHandle strcat = abi.downcallHandle(abi.lookup("strcat").get(),
                FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER))
                .asType(MethodType.methodType(MemoryAddress.class, MemorySegment.class, MemorySegment.class)); // exact signature match

        final static MethodHandle strcmp = abi.downcallHandle(abi.lookup("strcmp").get(),
                FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER));

        final static MethodHandle puts = abi.downcallHandle(abi.lookup("puts").get(),
                FunctionDescriptor.of(C_INT, C_POINTER));

        final static MethodHandle strlen = abi.downcallHandle(abi.lookup("strlen").get(),
                FunctionDescriptor.of(C_INT, C_POINTER));

        final static MethodHandle gmtime = abi.downcallHandle(abi.lookup("gmtime").get(),
                FunctionDescriptor.of(C_POINTER, C_POINTER));

        final static MethodHandle qsort = abi.downcallHandle(abi.lookup("qsort").get(),
                FunctionDescriptor.ofVoid(C_POINTER, C_LONG_LONG, C_LONG_LONG, C_POINTER));

        final static FunctionDescriptor qsortComparFunction = FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER);

        final static MethodHandle qsortCompar;

        final static MethodHandle rand = abi.downcallHandle(abi.lookup("rand").get(),
                FunctionDescriptor.of(C_INT));

        final static MethodHandle vprintf = abi.downcallHandle(abi.lookup("vprintf").get(),
                FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER));

        final static NativeSymbol printfAddr = abi.lookup("printf").get();

        final static FunctionDescriptor printfBase = FunctionDescriptor.of(C_INT, C_POINTER);

        static {
            try {
                //qsort upcall handle
                qsortCompar = MethodHandles.lookup().findStatic(StdLibTest.StdLibHelper.class, "qsortCompare",
                        CLinker.upcallType(qsortComparFunction));
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        }

        String strcat(String s1, String s2) throws Throwable {
            try (ResourceScope scope = ResourceScope.newConfinedScope()) {
                var malloc = SegmentAllocator.nativeAllocator(scope);
                MemorySegment buf = malloc.allocate(s1.length() + s2.length() + 1);
                buf.setUtf8String(0, s1);
                MemorySegment other = malloc.allocateUtf8String(s2);
                return ((MemoryAddress)strcat.invokeExact(buf, other)).getUtf8String(0);
            }
        }

        int strcmp(String s1, String s2) throws Throwable {
            try (ResourceScope scope = ResourceScope.newConfinedScope()) {
                var malloc = SegmentAllocator.nativeAllocator(scope);
                MemorySegment ns1 = malloc.allocateUtf8String(s1);
                MemorySegment ns2 = malloc.allocateUtf8String(s2);
                return (int)strcmp.invoke(ns1, ns2);
            }
        }

        int puts(String msg) throws Throwable {
            try (ResourceScope scope = ResourceScope.newConfinedScope()) {
                var malloc = SegmentAllocator.nativeAllocator(scope);
                MemorySegment s = malloc.allocateUtf8String(msg);
                return (int)puts.invoke(s);
            }
        }

        int strlen(String msg) throws Throwable {
            try (ResourceScope scope = ResourceScope.newConfinedScope()) {
                var malloc = SegmentAllocator.nativeAllocator(scope);
                MemorySegment s = malloc.allocateUtf8String(msg);
                return (int)strlen.invoke(s);
            }
        }

        Tm gmtime(long arg) throws Throwable {
            try (ResourceScope scope = ResourceScope.newConfinedScope()) {
                MemorySegment time = MemorySegment.allocateNative(8, scope);
                time.set(C_LONG_LONG, 0, arg);
                return new Tm((MemoryAddress)gmtime.invoke(time));
            }
        }

        static class Tm {

            //Tm pointer should never be freed directly, as it points to shared memory
            private final MemorySegment base;

            static final long SIZE = 56;

            Tm(MemoryAddress addr) {
                this.base = MemorySegment.ofAddress(addr, SIZE, ResourceScope.globalScope());
            }

            int sec() {
                return base.get(C_INT, 0);
            }
            int min() {
                return base.get(C_INT, 4);
            }
            int hour() {
                return base.get(C_INT, 8);
            }
            int mday() {
                return base.get(C_INT, 12);
            }
            int mon() {
                return base.get(C_INT, 16);
            }
            int year() {
                return base.get(C_INT, 20);
            }
            int wday() {
                return base.get(C_INT, 24);
            }
            int yday() {
                return base.get(C_INT, 28);
            }
            boolean isdst() {
                return base.get(C_BOOL, 32);
            }
        }

        int[] qsort(int[] arr) throws Throwable {
            //init native array
            try (ResourceScope scope = ResourceScope.newConfinedScope()) {
                var malloc = SegmentAllocator.nativeAllocator(scope);
                MemorySegment nativeArr = malloc.allocateArray(C_INT, arr);

                //call qsort
                NativeSymbol qsortUpcallStub = abi.upcallStub(qsortCompar, qsortComparFunction, scope);

                qsort.invoke(nativeArr, (long)arr.length, C_INT.byteSize(), qsortUpcallStub);

                //convert back to Java array
                return nativeArr.toArray(C_INT);
            }
        }

        static int qsortCompare(MemoryAddress addr1, MemoryAddress addr2) {
            return addr1.get(C_INT, 0) -
                   addr2.get(C_INT, 0);
        }

        int rand() throws Throwable {
            return (int)rand.invokeExact();
        }

        int printf(String format, List<PrintfArg> args) throws Throwable {
            try (ResourceScope scope = ResourceScope.newConfinedScope()) {
                var malloc = SegmentAllocator.nativeAllocator(scope);
                MemorySegment formatStr = malloc.allocateUtf8String(format);
                return (int)specializedPrintf(args).invoke(formatStr,
                        args.stream().map(a -> a.nativeValue(scope)).toArray());
            }
        }

        int vprintf(String format, List<PrintfArg> args) throws Throwable {
            try (ResourceScope scope = ResourceScope.newConfinedScope()) {
                var malloc = SegmentAllocator.nativeAllocator(scope);
                MemorySegment formatStr = malloc.allocateUtf8String(format);
                VaList vaList = VaList.make(b -> args.forEach(a -> a.accept(b, scope)), scope);
                return (int)vprintf.invoke(formatStr, vaList);
            }
        }

        private MethodHandle specializedPrintf(List<PrintfArg> args) {
            //method type
            MethodType mt = MethodType.methodType(int.class, MemoryAddress.class);
            FunctionDescriptor fd = printfBase;
            List<MemoryLayout> variadicLayouts = new ArrayList<>(args.size());
            for (PrintfArg arg : args) {
                mt = mt.appendParameterTypes(arg.carrier);
                variadicLayouts.add(arg.layout);
            }
            MethodHandle mh = abi.downcallHandle(printfAddr,
                    fd.asVariadic(variadicLayouts.toArray(new MemoryLayout[args.size()])));
            return mh.asSpreader(1, Object[].class, args.size());
        }
    }

    /*** data providers ***/

    @DataProvider
    public static Object[][] ints() {
        return perms(0, new Integer[] { 0, 1, 2, 3, 4 }).stream()
                .map(l -> new Object[] { l })
                .toArray(Object[][]::new);
    }

    @DataProvider
    public static Object[][] strings() {
        return perms(0, new String[] { "a", "b", "c" }).stream()
                .map(l -> new Object[] { String.join("", l) })
                .toArray(Object[][]::new);
    }

    @DataProvider
    public static Object[][] stringPairs() {
        Object[][] strings = strings();
        Object[][] stringPairs = new Object[strings.length * strings.length][];
        int pos = 0;
        for (Object[] s1 : strings) {
            for (Object[] s2 : strings) {
                stringPairs[pos++] = new Object[] { s1[0], s2[0] };
            }
        }
        return stringPairs;
    }

    @DataProvider
    public static Object[][] instants() {
        Instant start = ZonedDateTime.of(LocalDateTime.parse("2017-01-01T00:00:00"), ZoneOffset.UTC).toInstant();
        Instant end = ZonedDateTime.of(LocalDateTime.parse("2017-12-31T00:00:00"), ZoneOffset.UTC).toInstant();
        Object[][] instants = new Object[100][];
        for (int i = 0 ; i < instants.length ; i++) {
            Instant instant = start.plusSeconds((long)(Math.random() * (end.getEpochSecond() - start.getEpochSecond())));
            instants[i] = new Object[] { instant };
        }
        return instants;
    }

    @DataProvider
    public static Object[][] printfArgs() {
        ArrayList<List<PrintfArg>> res = new ArrayList<>();
        List<List<PrintfArg>> perms = new ArrayList<>(perms(0, PrintfArg.values()));
        for (int i = 0 ; i < 100 ; i++) {
            Collections.shuffle(perms);
            res.addAll(perms);
        }
        return res.stream()
                .map(l -> new Object[] { l })
                .toArray(Object[][]::new);
    }

    enum PrintfArg implements BiConsumer<VaList.Builder, ResourceScope> {

        INTEGRAL(int.class, C_INT, "%d", scope -> 42, 42, VaList.Builder::addVarg),
        STRING(MemoryAddress.class, C_POINTER, "%s", scope -> {
            var segment = MemorySegment.allocateNative(4, scope);
            segment.setUtf8String(0, "str");
            return segment.address();
        }, "str", VaList.Builder::addVarg),
        CHAR(byte.class, C_CHAR, "%c", scope -> (byte) 'h', 'h', (builder, layout, value) -> builder.addVarg(C_INT, (int)value)),
        DOUBLE(double.class, C_DOUBLE, "%.4f", scope ->1.2345d, 1.2345d, VaList.Builder::addVarg);

        final Class<?> carrier;
        final ValueLayout layout;
        final String format;
        final Function<ResourceScope, ?> nativeValueFactory;
        final Object javaValue;
        @SuppressWarnings("rawtypes")
        final VaListBuilderCall builderCall;

        <Z, L extends ValueLayout> PrintfArg(Class<?> carrier, L layout, String format, Function<ResourceScope, Z> nativeValueFactory, Object javaValue, VaListBuilderCall<Z, L> builderCall) {
            this.carrier = carrier;
            this.layout = layout;
            this.format = format;
            this.nativeValueFactory = nativeValueFactory;
            this.javaValue = javaValue;
            this.builderCall = builderCall;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void accept(VaList.Builder builder, ResourceScope scope) {
            builderCall.build(builder, layout, nativeValueFactory.apply(scope));
        }

        interface VaListBuilderCall<V, L> {
            void build(VaList.Builder builder, L layout, V value);
        }

        public Object nativeValue(ResourceScope scope) {
            return nativeValueFactory.apply(scope);
        }
    }

    static <Z> Set<List<Z>> perms(int count, Z[] arr) {
        if (count == arr.length) {
            return Set.of(List.of());
        } else {
            return Arrays.stream(arr)
                    .flatMap(num -> {
                        Set<List<Z>> perms = perms(count + 1, arr);
                        return Stream.concat(
                                //take n
                                perms.stream().map(l -> {
                                    List<Z> li = new ArrayList<>(l);
                                    li.add(num);
                                    return li;
                                }),
                                //drop n
                                perms.stream());
                    }).collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }
}
