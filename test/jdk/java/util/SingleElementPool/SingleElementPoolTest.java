/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Basic tests of SingleElementPool
 * @modules java.base/jdk.internal.util
 * @run junit SingleElementPoolTest
 */

import jdk.internal.util.SingleElementPool;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SingleElementPoolTest {

    @Test
    public void basic() {
        final List<TestElement> recycledElements = new ArrayList<>();
        TestElement element = new TestElement();
        SingleElementPool.SingleElementPoolImpl<TestElement> pool =
                new SingleElementPool.SingleElementPoolImpl<>(element, TestElement::new, recycledElements::add);

        TestElement first = pool.take();
        assertSame(element, first);
        TestElement second = pool.take();
        assertNotSame(first, second);
        pool.release(second);
        pool.release(first);
        TestElement reused = pool.take();
        assertSame(element, reused);
        pool.release(reused);
        pool.close();
        assertEquals(List.of(second, first), recycledElements);
    }

    @Test
    public void closeReleased() {
        final List<TestElement> recycledElements = new ArrayList<>();
        TestElement element = new TestElement();
        SingleElementPool.SingleElementPoolImpl<TestElement> pool =
                new SingleElementPool.SingleElementPoolImpl<>(element, TestElement::new, recycledElements::add);
        TestElement first = pool.take();
        pool.release(first);

        pool.close();

        assertEquals(List.of(first), recycledElements);

        // Check idempotency
        pool.close();
        assertEquals(List.of(first), recycledElements);
    }

    @Test
    public void closeNotReleased() {
        final List<TestElement> recycledElements = new ArrayList<>();
        TestElement element = new TestElement();
        SingleElementPool.SingleElementPoolImpl<TestElement> pool =
                new SingleElementPool.SingleElementPoolImpl<>(element, TestElement::new, recycledElements::add);
        TestElement first = pool.take();
        pool.close();

        assertTrue(recycledElements.isEmpty());

        // Check idempotency
        pool.close();
        assertTrue(recycledElements.isEmpty());
    }

    @Test
    public void invariants() {
        assertThrows(NullPointerException.class, () -> SingleElementPool.of(null, _ -> {}));
        assertThrows(NullPointerException.class, () -> SingleElementPool.of(Object::new, null));
        assertDoesNotThrow(() -> SingleElementPool.of(null, Object::new, _ -> {}));
    }

    static final class TestElement{}

}
