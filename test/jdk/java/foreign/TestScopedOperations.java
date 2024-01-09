/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestScopedOperations
 */

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
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
        Arena arena = Arena.ofConfined();
        Z obj = scopedOperation.apply(arena);
        arena.close();
        try {
            scopedOperation.accept(obj);
            fail();
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage().contains("closed"));
        }
    }

    @Test(dataProvider = "scopedOperations")
    public <Z> void testOpOutsideConfinement(String name, ScopedOperation<Z> scopedOperation) {
        try (Arena arena = Arena.ofConfined()) {
            Z obj = scopedOperation.apply(arena);
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
            assertEquals(failed.get().getClass(), WrongThreadException.class);
            assertTrue(failed.get().getMessage().contains("outside"));
        } catch (InterruptedException ex) {
            throw new AssertionError(ex);
        }
    }

    static List<ScopedOperation> scopedOperations = new ArrayList<>();

    static {
        // session operations
        ScopedOperation.ofScope(session -> session.allocate(100, 1), "MemorySession::allocate");
        ScopedOperation.ofScope(session -> {
            try (FileChannel fileChannel = FileChannel.open(tempPath, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                fileChannel.map(FileChannel.MapMode.READ_WRITE, 0L, 10L, session);
            } catch (IOException ex) {
                fail();
            }
        }, "FileChannel::map");
        // segment operations
        ScopedOperation.ofSegment(s -> s.toArray(JAVA_BYTE), "MemorySegment::toArray(BYTE)");
        ScopedOperation.ofSegment(s -> s.copyFrom(s), "MemorySegment::copyFrom");
        ScopedOperation.ofSegment(s -> s.mismatch(s), "MemorySegment::mismatch");
        ScopedOperation.ofSegment(s -> s.fill((byte) 0), "MemorySegment::fill");
        // allocator operations
        ScopedOperation.ofScope(a -> a.allocate(1), "Arena::allocate/size");
        ScopedOperation.ofScope(a -> a.allocate(1, 1), "Arena::allocate/size/align");
        ScopedOperation.ofScope(a -> a.allocate(JAVA_BYTE), "Arena::allocate/layout");
        ScopedOperation.ofScope(a -> a.allocateFrom(JAVA_BYTE, (byte) 0), "Arena::allocate/byte");
        ScopedOperation.ofScope(a -> a.allocateFrom(ValueLayout.JAVA_CHAR, (char) 0), "Arena::allocate/char");
        ScopedOperation.ofScope(a -> a.allocateFrom(ValueLayout.JAVA_SHORT, (short) 0), "Arena::allocate/short");
        ScopedOperation.ofScope(a -> a.allocateFrom(ValueLayout.JAVA_INT, 0), "Arena::allocate/int");
        ScopedOperation.ofScope(a -> a.allocateFrom(ValueLayout.JAVA_FLOAT, 0f), "Arena::allocate/float");
        ScopedOperation.ofScope(a -> a.allocateFrom(ValueLayout.JAVA_LONG, 0L), "Arena::allocate/long");
        ScopedOperation.ofScope(a -> a.allocateFrom(ValueLayout.JAVA_DOUBLE, 0d), "Arena::allocate/double");
        ScopedOperation.ofScope(a -> a.allocate(JAVA_BYTE, 1L), "Arena::allocate/size");
        ScopedOperation.ofScope(a -> a.allocateFrom(JAVA_BYTE, new byte[]{0}), "Arena::allocateFrom/byte");
        ScopedOperation.ofScope(a -> a.allocateFrom(ValueLayout.JAVA_CHAR, new char[]{0}), "Arena::allocateFrom/char");
        ScopedOperation.ofScope(a -> a.allocateFrom(ValueLayout.JAVA_SHORT, new short[]{0}), "Arena::allocateFrom/short");
        ScopedOperation.ofScope(a -> a.allocateFrom(ValueLayout.JAVA_INT, new int[]{0}), "Arena::allocateFrom/int");
        ScopedOperation.ofScope(a -> a.allocateFrom(ValueLayout.JAVA_FLOAT, new float[]{0}), "Arena::allocateFrom/float");
        ScopedOperation.ofScope(a -> a.allocateFrom(ValueLayout.JAVA_LONG, new long[]{0}), "Arena::allocateFrom/long");
        ScopedOperation.ofScope(a -> a.allocateFrom(ValueLayout.JAVA_DOUBLE, new double[]{0}), "Arena::allocateFrom/double");
        var source = MemorySegment.ofArray(new byte[]{});
        ScopedOperation.ofScope(a -> a.allocateFrom(ValueLayout.JAVA_INT, source, JAVA_BYTE, 0, 1), "Arena::allocateFrom/5arg");
    };

    @DataProvider(name = "scopedOperations")
    static Object[][] scopedOperations() {
        return scopedOperations.stream().map(op -> new Object[] { op.name, op }).toArray(Object[][]::new);
    }

    static class ScopedOperation<X> implements Consumer<X>, Function<Arena, X> {

        final Function<Arena, X> factory;
        final Consumer<X> operation;
        final String name;

        private ScopedOperation(Function<Arena, X> factory, Consumer<X> operation, String name) {
            this.factory = factory;
            this.operation = operation;
            this.name = name;
        }

        @Override
        public void accept(X obj) {
            operation.accept(obj);
        }

        @Override
        public X apply(Arena session) {
            return factory.apply(session);
        }

        static <Z> void of(Function<Arena, Z> factory, Consumer<Z> consumer, String name) {
            scopedOperations.add(new ScopedOperation<>(factory, consumer, name));
        }

        static void ofScope(Consumer<Arena> scopeConsumer, String name) {
            scopedOperations.add(new ScopedOperation<>(Function.identity(), scopeConsumer, name));
        }

        static void ofSegment(Consumer<MemorySegment> segmentConsumer, String name) {
            for (SegmentFactory segmentFactory : SegmentFactory.values()) {
                scopedOperations.add(new ScopedOperation<>(
                        segmentFactory.segmentFactory,
                        segmentConsumer,
                        segmentFactory.name() + "/" + name));
            }
        }

        enum SegmentFactory {

            NATIVE(session -> session.allocate(10, 1)),
            MAPPED(session -> {
                try (FileChannel fileChannel = FileChannel.open(Path.of("foo.txt"), StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                    return fileChannel.map(FileChannel.MapMode.READ_WRITE, 0L, 10L, session);
                } catch (IOException ex) {
                    throw new AssertionError(ex);
                }
            }),
            UNSAFE(session -> MemorySegment.NULL.reinterpret(10, session, null));

            static {
                try {
                    File f = new File("foo.txt");
                    f.createNewFile();
                    f.deleteOnExit();
                } catch (IOException ex) {
                    throw new ExceptionInInitializerError(ex);
                }
            }

            final Function<Arena, MemorySegment> segmentFactory;

            SegmentFactory(Function<Arena, MemorySegment> segmentFactory) {
                this.segmentFactory = segmentFactory;
            }
        }
    }
}
