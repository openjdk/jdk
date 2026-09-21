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
 * @requires vm.compiler2.enabled
 * @requires vm.debug
 * @modules java.base/jdk.internal.vm.annotation java.base/jdk.internal.misc
 * @key randomness
 * @library /test/lib
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run junit/othervm
 *   -Xbootclasspath/a:.
 *   -XX:+UnlockDiagnosticVMOptions
 *   -XX:+WhiteBoxAPI
 *   -XX:CompileCommand=dontinline,TestSharedCloseDeopts::outOfLine
 *   -XX:CompileCommand=dontinline,TestSharedCloseDeopts::payloadWithAccess
 *   -XX:CompileCommand=dontinline,TestSharedCloseDeopts::payloadWithoutAccess
 *   -XX:-UseOnStackReplacement
 *   -XX:-BackgroundCompilation
 *   -Xlog:foreign+deoptimization=trace:file=test_deopts.txt:none
 *   TestSharedCloseDeopts
 */

import jdk.test.whitebox.WhiteBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class TestSharedCloseDeopts {
    static final WhiteBox WB = WhiteBox.getWhiteBox();
    static final Method PAYLOAD_METHOD;
    static final int C2_COMPILED_LEVEL = 4;
    static final String SHARED_SCOPE_CLOSED_DEOPT_REASON = "constraint";
    static final Path LOG_FILE = Path.of("test_deopts.txt");

    static {
        try {
            PAYLOAD_METHOD = TestSharedCloseDeopts.class.getDeclaredMethod("payloadWithAccess",
                    MemorySegment.Scope.class, MemorySegment.class, AtomicBoolean.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @BeforeAll
    public static void before() throws Throwable {
        for (int i = 0; i < 20_000; i++) {
            // compile first to avoid noise
            outOfLine();
        }

        for (TestCase testCase : (Iterable<TestCase>) () -> cases().iterator()) {
            // do a dry run to compile the payload* methods
            // and reduce other noise due to uncommon traps triggering
            AtomicBoolean hold = new AtomicBoolean();
            AtomicReference<Throwable> uncaughtException = new AtomicReference<>();
            Thread t;
            try (Arena arena = Arena.ofShared()) {
                MemorySegment segment = arena.allocate(ValueLayout.JAVA_INT);
                t = Thread.ofPlatform()
                    .uncaughtExceptionHandler((_, ex) -> uncaughtException.set(ex))
                    .start(() -> {
                        while (arena.scope().isAlive()) {
                            try {
                                testCase.payload().run(segment.scope(), segment, hold);
                            } catch (IllegalStateException e) {
                                // arena was close, we can return
                                return;
                            }
                        }
                    });

                // await compilation while polluting the profile to avoid
                // uncommon trap deopt as result of loop backedge test
                do {
                    for (int i = 0; i < 100_000; i++) {
                        hold.setRelease(true);
                        hold.setRelease(false);
                    }
                } while (WB.getMethodCompilationLevel(PAYLOAD_METHOD, false) != C2_COMPILED_LEVEL);
            }
            t.join();
            if (uncaughtException.get() != null) {
                throw uncaughtException.get();
            }
        }
    }

    @ParameterizedTest
    @MethodSource("cases")
    public void testDeopts(TestCase testCase) throws Throwable {
        // clear log file for this test case
        Files.writeString(LOG_FILE, "", StandardOpenOption.TRUNCATE_EXISTING);

        AtomicBoolean hold = new AtomicBoolean(true); // hold worker thread in loop
        AtomicReference<Throwable> uncaughtException = new AtomicReference<>();
        Thread t;
        int deoptCountBeforeClose;
        try (Arena arena = Arena.ofShared();
            Arena _ = Arena.ofShared()) {
            MemorySegment segment = arena.allocate(ValueLayout.JAVA_INT);
            t = Thread.ofPlatform()
                .uncaughtExceptionHandler((_, ex) -> uncaughtException.set(ex))
                .start(() ->  testCase.payload.run(segment.scope(), segment, hold));
            // give it a moment to get there
            Thread.sleep(1000);
            deoptCountBeforeClose = WB.getDeoptCount(SHARED_SCOPE_CLOSED_DEOPT_REASON, null);
        }
        // We closed 2 shared arenas here. One of them was unrelated. Expect at most 1 deopt
        int deoptCountAfterClose = WB.getDeoptCount(SHARED_SCOPE_CLOSED_DEOPT_REASON, null);
        assertEquals(deoptCountBeforeClose + (testCase.hasAccess() ? 1 : 0), deoptCountAfterClose);

        hold.setRelease(false); // release thread from loop
        t.join();
        if (uncaughtException.get() != null) {
            throw uncaughtException.get();
        }

        // assert that we saw the frame during scope closure using UL log messages
        List<String> log = Files.readAllLines(LOG_FILE);
        // look for both scope closures. One with the same scope, one with the other
        assertTrue(log.contains("Inspected compiled frame. has_scoped_access=" + testCase.hasAccess() + " is_session_live=false:"));
        assertTrue(log.contains("Inspected compiled frame. has_scoped_access=" + testCase.hasAccess() + " is_session_live=true:"));
    }

    // using same scope, but no scoped access
    // should not deopt in this case
    public static MemorySegment.Scope payloadWithAccess(MemorySegment.Scope scope, MemorySegment segment,
                                                        AtomicBoolean hold) {
        segment.set(ValueLayout.JAVA_INT, 0L, 42);
        while (hold.getAcquire()) {
            // conditionally hold at the post call safepoint
            outOfLine();
        }
        return scope;
    }

    // same but without a scoped access
    public static MemorySegment.Scope payloadWithoutAccess(MemorySegment.Scope scope, MemorySegment segment,
                                                           AtomicBoolean hold) {
        while (hold.getAcquire()) {
            outOfLine();
        }
        return scope;
    }

    public static void outOfLine() {
    }

    // test helpers
    public interface Payload {
        MemorySegment.Scope run(MemorySegment.Scope scope, MemorySegment segment, AtomicBoolean hold);
    }

    public record TestCase(String name, Payload payload, boolean hasAccess) {}

    public static Stream<TestCase> cases() {
        return Stream.of(
                new TestCase("payloadWithAccess", TestSharedCloseDeopts::payloadWithAccess, true),
                new TestCase("payloadWithoutAccess", TestSharedCloseDeopts::payloadWithoutAccess, false)
        );
    }
}
