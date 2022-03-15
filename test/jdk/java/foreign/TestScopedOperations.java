/*
 *  Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 */

/*
 * @test
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestScopedOperations
 */

import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.NativeSymbol;
import jdk.incubator.foreign.ResourceScope;
import jdk.incubator.foreign.SegmentAllocator;
import jdk.incubator.foreign.VaList;
import jdk.incubator.foreign.ValueLayout;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static jdk.incubator.foreign.ValueLayout.JAVA_BYTE;
import static jdk.incubator.foreign.ValueLayout.JAVA_INT;
import static jdk.incubator.foreign.ValueLayout.JAVA_LONG;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestScopedOperations {

    static Path tempPath;

    static {
        try {
            File file = File.createTempFile("scopedBuffer", "txt");
            file.deleteOnExit();
            tempPath = file.toPath();
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @Test(dataProvider = "scopedOperations")
    public <Z> void testOpAfterClose(String name, ScopedOperation<Z> scopedOperation) {
        ResourceScope scope = ResourceScope.newConfinedScope();
        Z obj = scopedOperation.apply(scope);
        scope.close();
        try {
            scopedOperation.accept(obj);
            fail();
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage().contains("closed"));
        }
    }

    @Test(dataProvider = "scopedOperations")
    public <Z> void testOpOutsideConfinement(String name, ScopedOperation<Z> scopedOperation) {
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            Z obj = scopedOperation.apply(scope);
            AtomicReference<Throwable> failed = new AtomicReference<>();
            Thread t = new Thread(() -> {
                try {
                    scopedOperation.accept(obj);
                } catch (Throwable ex) {
                    failed.set(ex);
                }
            });
            t.start();
            t.join();
            assertNotNull(failed.get());
            assertEquals(failed.get().getClass(), IllegalStateException.class);
            assertTrue(failed.get().getMessage().contains("outside"));
        } catch (InterruptedException ex) {
            throw new AssertionError(ex);
        }
    }

    static List<ScopedOperation> scopedOperations = new ArrayList<>();

    static {
        // scope operations
        ScopedOperation.ofScope(scope -> scope.addCloseAction(() -> {
        }), "ResourceScope::addOnClose");
        ScopedOperation.ofScope(scope -> {
            ResourceScope scope2 = ResourceScope.newConfinedScope();
            scope2.keepAlive(scope);
            scope2.close();
        }, "ResourceScope::keepAlive");
        ScopedOperation.ofScope(scope -> MemorySegment.allocateNative(100, scope), "MemorySegment::allocateNative");
        ScopedOperation.ofScope(scope -> {
            try {
                MemorySegment.mapFile(tempPath, 0, 10, FileChannel.MapMode.READ_WRITE, scope);
            } catch (IOException ex) {
                fail();
            }
        }, "MemorySegment::mapFromFile");
        ScopedOperation.ofScope(scope -> VaList.make(b -> b.addVarg(JAVA_INT, 42), scope), "VaList::make");
        ScopedOperation.ofScope(scope -> VaList.ofAddress(MemoryAddress.ofLong(42), scope), "VaList::make");
        ScopedOperation.ofScope(SegmentAllocator::newNativeArena, "SegmentAllocator::arenaAllocator");
        // segment operations
        ScopedOperation.ofSegment(s -> s.toArray(JAVA_BYTE), "MemorySegment::toArray(BYTE)");
        ScopedOperation.ofSegment(MemorySegment::address, "MemorySegment::address");
        ScopedOperation.ofSegment(s -> s.copyFrom(s), "MemorySegment::copyFrom");
        ScopedOperation.ofSegment(s -> s.mismatch(s), "MemorySegment::mismatch");
        ScopedOperation.ofSegment(s -> s.fill((byte) 0), "MemorySegment::fill");
        // valist operations
        ScopedOperation.ofVaList(VaList::address, "VaList::address");
        ScopedOperation.ofVaList(VaList::copy, "VaList::copy");
        ScopedOperation.ofVaList(list -> list.nextVarg(ValueLayout.ADDRESS), "VaList::nextVarg/address");
        ScopedOperation.ofVaList(list -> list.nextVarg(ValueLayout.JAVA_INT), "VaList::nextVarg/int");
        ScopedOperation.ofVaList(list -> list.nextVarg(ValueLayout.JAVA_LONG), "VaList::nextVarg/long");
        ScopedOperation.ofVaList(list -> list.nextVarg(ValueLayout.JAVA_DOUBLE), "VaList::nextVarg/double");
        ScopedOperation.ofVaList(VaList::skip, "VaList::skip");
        ScopedOperation.ofVaList(list -> list.nextVarg(MemoryLayout.structLayout(ValueLayout.JAVA_INT),
                SegmentAllocator.prefixAllocator(MemorySegment.ofArray(new byte[4]))), "VaList::nextVargs/segment");
        // allocator operations
        ScopedOperation.ofAllocator(a -> a.allocate(1), "NativeAllocator::allocate/size");
        ScopedOperation.ofAllocator(a -> a.allocate(1, 1), "NativeAllocator::allocate/size/align");
        ScopedOperation.ofAllocator(a -> a.allocate(JAVA_BYTE), "NativeAllocator::allocate/layout");
        ScopedOperation.ofAllocator(a -> a.allocate(JAVA_BYTE, (byte) 0), "NativeAllocator::allocate/byte");
        ScopedOperation.ofAllocator(a -> a.allocate(ValueLayout.JAVA_CHAR, (char) 0), "NativeAllocator::allocate/char");
        ScopedOperation.ofAllocator(a -> a.allocate(ValueLayout.JAVA_SHORT, (short) 0), "NativeAllocator::allocate/short");
        ScopedOperation.ofAllocator(a -> a.allocate(ValueLayout.JAVA_INT, 0), "NativeAllocator::allocate/int");
        ScopedOperation.ofAllocator(a -> a.allocate(ValueLayout.JAVA_FLOAT, 0f), "NativeAllocator::allocate/float");
        ScopedOperation.ofAllocator(a -> a.allocate(ValueLayout.JAVA_LONG, 0L), "NativeAllocator::allocate/long");
        ScopedOperation.ofAllocator(a -> a.allocate(ValueLayout.JAVA_DOUBLE, 0d), "NativeAllocator::allocate/double");
        ScopedOperation.ofAllocator(a -> a.allocateArray(JAVA_BYTE, 1L), "NativeAllocator::allocateArray/size");
        ScopedOperation.ofAllocator(a -> a.allocateArray(JAVA_BYTE, new byte[]{0}), "NativeAllocator::allocateArray/byte");
        ScopedOperation.ofAllocator(a -> a.allocateArray(ValueLayout.JAVA_CHAR, new char[]{0}), "NativeAllocator::allocateArray/char");
        ScopedOperation.ofAllocator(a -> a.allocateArray(ValueLayout.JAVA_SHORT, new short[]{0}), "NativeAllocator::allocateArray/short");
        ScopedOperation.ofAllocator(a -> a.allocateArray(ValueLayout.JAVA_INT, new int[]{0}), "NativeAllocator::allocateArray/int");
        ScopedOperation.ofAllocator(a -> a.allocateArray(ValueLayout.JAVA_FLOAT, new float[]{0}), "NativeAllocator::allocateArray/float");
        ScopedOperation.ofAllocator(a -> a.allocateArray(ValueLayout.JAVA_LONG, new long[]{0}), "NativeAllocator::allocateArray/long");
        ScopedOperation.ofAllocator(a -> a.allocateArray(ValueLayout.JAVA_DOUBLE, new double[]{0}), "NativeAllocator::allocateArray/double");
        // native symbol
        ScopedOperation.of(scope -> NativeSymbol.ofAddress("", MemoryAddress.NULL, scope), NativeSymbol::address, "NativeSymbol::address");
    };

    @DataProvider(name = "scopedOperations")
    static Object[][] scopedOperations() {
        return scopedOperations.stream().map(op -> new Object[] { op.name, op }).toArray(Object[][]::new);
    }

    static class ScopedOperation<X> implements Consumer<X>, Function<ResourceScope, X> {

        final Function<ResourceScope, X> factory;
        final Consumer<X> operation;
        final String name;

        private ScopedOperation(Function<ResourceScope, X> factory, Consumer<X> operation, String name) {
            this.factory = factory;
            this.operation = operation;
            this.name = name;
        }

        @Override
        public void accept(X obj) {
            operation.accept(obj);
        }

        @Override
        public X apply(ResourceScope scope) {
            return factory.apply(scope);
        }

        static <Z> void of(Function<ResourceScope, Z> factory, Consumer<Z> consumer, String name) {
            scopedOperations.add(new ScopedOperation<>(factory, consumer, name));
        }

        static void ofScope(Consumer<ResourceScope> scopeConsumer, String name) {
            scopedOperations.add(new ScopedOperation<>(Function.identity(), scopeConsumer, name));
        }

        static void ofVaList(Consumer<VaList> vaListConsumer, String name) {
            scopedOperations.add(new ScopedOperation<>(scope -> VaList.make(builder -> builder.addVarg(JAVA_LONG, 42), scope),
                    vaListConsumer, name));
        }

        static void ofSegment(Consumer<MemorySegment> segmentConsumer, String name) {
            for (SegmentFactory segmentFactory : SegmentFactory.values()) {
                scopedOperations.add(new ScopedOperation<>(
                        segmentFactory.segmentFactory,
                        segmentConsumer,
                        segmentFactory.name() + "/" + name));
            }
        }

        static void ofAllocator(Consumer<SegmentAllocator> allocatorConsumer, String name) {
            for (AllocatorFactory allocatorFactory : AllocatorFactory.values()) {
                scopedOperations.add(new ScopedOperation<>(
                        allocatorFactory.allocatorFactory,
                        allocatorConsumer,
                        allocatorFactory.name() + "/" + name));
            }
        }

        enum SegmentFactory {

            NATIVE(scope -> MemorySegment.allocateNative(10, scope)),
            MAPPED(scope -> {
                try {
                    return MemorySegment.mapFile(Path.of("foo.txt"), 0, 10, FileChannel.MapMode.READ_WRITE, scope);
                } catch (IOException ex) {
                    throw new AssertionError(ex);
                }
            }),
            UNSAFE(scope -> MemorySegment.ofAddress(MemoryAddress.NULL, 10, scope));

            static {
                try {
                    File f = new File("foo.txt");
                    f.createNewFile();
                    f.deleteOnExit();
                } catch (IOException ex) {
                    throw new ExceptionInInitializerError(ex);
                }
            }

            final Function<ResourceScope, MemorySegment> segmentFactory;

            SegmentFactory(Function<ResourceScope, MemorySegment> segmentFactory) {
                this.segmentFactory = segmentFactory;
            }
        }

        enum AllocatorFactory {
            ARENA_BOUNDED(scope -> SegmentAllocator.newNativeArena(1000, scope)),
            ARENA_UNBOUNDED(SegmentAllocator::newNativeArena);

            final Function<ResourceScope, SegmentAllocator> allocatorFactory;

            AllocatorFactory(Function<ResourceScope, SegmentAllocator> allocatorFactory) {
                this.allocatorFactory = allocatorFactory;
            }
        }
    }
}
