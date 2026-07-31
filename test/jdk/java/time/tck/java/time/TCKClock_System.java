/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Copyright (c) 2008-2012 Stephen Colebourne & Michael Nascimento Santos
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of JSR-310 nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package tck.java.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test system clock.
 */
public class TCKClock_System extends AbstractTCKTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    //-----------------------------------------------------------------------
    @Test
    public void test_instant() {
        Clock system = Clock.systemUTC();
        assertEquals(ZoneOffset.UTC, system.getZone());
        for (int i = 0; i < 10000; i++) {
            // assume can eventually get these within 10 milliseconds
            Instant instant = system.instant();
            long systemMillis = System.currentTimeMillis();
            if (systemMillis - instant.toEpochMilli() < 10) {
                return;  // success
            }
        }
        fail();
    }

    @Test
    public void test_millis() {
        Clock system = Clock.systemUTC();
        assertEquals(ZoneOffset.UTC, system.getZone());
        for (int i = 0; i < 10000; i++) {
            // assume can eventually get these within 10 milliseconds
            long instant = system.millis();
            long systemMillis = System.currentTimeMillis();
            if (systemMillis - instant < 10) {
                return;  // success
            }
        }
        fail();
    }

    //-------------------------------------------------------------------------
    @Test
    public void test_systemUTC() {
        Clock test = Clock.systemUTC();
        assertEquals(ZoneOffset.UTC, test.getZone());
        assertEquals(Clock.system(ZoneOffset.UTC), test);
    }

    @Test
    public void test_systemDefaultZone() {
        Clock test = Clock.systemDefaultZone();
        assertEquals(ZoneId.systemDefault(), test.getZone());
        assertEquals(Clock.system(ZoneId.systemDefault()), test);
    }

    @Test
    public void test_system_ZoneId() {
        Clock test = Clock.system(PARIS);
        assertEquals(PARIS, test.getZone());
    }

    @Test
    public void test_zoneId_nullZoneId() {
        Assertions.assertThrows(NullPointerException.class, () -> Clock.system(null));
    }

    //-------------------------------------------------------------------------
    @Test
    public void test_withZone() {
        Clock test = Clock.system(PARIS);
        Clock changed = test.withZone(MOSCOW);
        assertEquals(PARIS, test.getZone());
        assertEquals(MOSCOW, changed.getZone());
    }

    @Test
    public void test_withZone_equal() {
        Clock test = Clock.system(PARIS);
        Clock changed = test.withZone(PARIS);
        assertEquals(PARIS, changed.getZone());
    }

    @Test
    public void test_withZone_fromUTC() {
        Clock test = Clock.systemUTC();
        Clock changed = test.withZone(PARIS);
        assertEquals(PARIS, changed.getZone());
    }

    @Test
    public void test_withZone_null() {
        Assertions.assertThrows(NullPointerException.class, () -> Clock.systemUTC().withZone(null));
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_equals() {
        Clock a = Clock.systemUTC();
        Clock b = Clock.systemUTC();
        assertEquals(true, a.equals(a));
        assertEquals(true, a.equals(b));
        assertEquals(true, b.equals(a));
        assertEquals(true, b.equals(b));

        Clock c = Clock.system(PARIS);
        Clock d = Clock.system(PARIS);
        assertEquals(true, c.equals(c));
        assertEquals(true, c.equals(d));
        assertEquals(true, d.equals(c));
        assertEquals(true, d.equals(d));

        assertEquals(false, a.equals(c));
        assertEquals(false, c.equals(a));

        assertEquals(false, a.equals(null));
        assertEquals(false, a.equals("other type"));
        assertEquals(false, a.equals(Clock.fixed(Instant.now(), ZoneOffset.UTC)));
    }

    @Test
    public void test_hashCode() {
        Clock a = Clock.system(ZoneOffset.UTC);
        Clock b = Clock.system(ZoneOffset.UTC);
        assertEquals(a.hashCode(), a.hashCode());
        assertEquals(b.hashCode(), a.hashCode());

        Clock c = Clock.system(PARIS);
        assertEquals(false, a.hashCode() == c.hashCode());
    }

}
