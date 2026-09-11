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

/*
 * @test
 * @run junit TestMappedSegmentKeepAlive
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import java.util.stream.Stream;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.junit.jupiter.api.Assertions.*;

public class TestMappedSegmentKeepAlive {

    static final Path tempPath;

    static {
        try {
            File file = File.createTempFile("buffer", "txt");
            file.deleteOnExit();
            tempPath = file.toPath();
            Files.write(file.toPath(), new byte[256], StandardOpenOption.WRITE);

        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @ParameterizedTest
    @MethodSource("mappedOps")
    public void testExceptionOnAcquire(Consumer<MemorySegment> op) throws Throwable {
        try (Arena arena = Arena.ofConfined();
            FileChannel fileChannel = FileChannel.open(tempPath, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            MemorySegment segment = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0L, 8L, arena);
            AtomicReference<Throwable> throwableRef = new AtomicReference<>();
            Thread t = Thread.ofPlatform()
                .uncaughtExceptionHandler((_, throwable) -> throwableRef.set(throwable))
                .start(() -> {
                    // Provoke a WrongThreadException when acquiring the session
                    // Make sure we properly release the session again
                    assertThrows(WrongThreadException.class, () -> op.accept(segment));
                });
            t.join();
            // propagate any exceptions from the nested thread
            if (throwableRef.get() != null) {
                throw throwableRef.get();
            }
        } // close should succeed
    }

    public static Stream<Arguments> mappedOps() {
        return Stream.of(
            arguments((Consumer<MemorySegment>) MemorySegment::force),
            arguments((Consumer<MemorySegment>) MemorySegment::load),
            arguments((Consumer<MemorySegment>) MemorySegment::unload),
            arguments((Consumer<MemorySegment>) MemorySegment::isLoaded)
        );
    }
}
