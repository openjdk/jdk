/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Copyright (c) 2008-2012, Stephen Colebourne & Michael Nascimento Santos
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
package tck.java.time.chrono;

import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoLocalDate;
import java.time.chrono.Chronology;
import java.time.chrono.Era;
import java.time.chrono.IsoChronology;
import java.time.temporal.ChronoField;
import java.time.temporal.Queries;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQuery;
import java.time.temporal.ValueRange;
import java.util.List;

import org.testng.annotations.Test;

/**
 * Test.
 */
@Test
public class TCKChronology {
    // Can only work with IsoChronology here
    // others may be in separate module

    @Test
    public void factory_from_TemporalAccessor_dateWithChronlogy() {
        assertEquals(Chronology.from(LocalDate.of(2012, 6, 30)), IsoChronology.INSTANCE);
    }

    @Test
    public void factory_from_TemporalAccessor_chronology() {
        assertEquals(Chronology.from(new TemporalAccessor() {
            @Override
            public boolean isSupported(TemporalField field) {
                throw new UnsupportedOperationException();
            }
            @Override
            public long getLong(TemporalField field) {
                throw new UnsupportedOperationException();
            }
            @SuppressWarnings("unchecked")
            @Override
            public <R> R query(TemporalQuery<R> query) {
                if (query == Queries.chronology()) {
                    return (R) IsoChronology.INSTANCE;
                }
                throw new UnsupportedOperationException();
            }
        }), IsoChronology.INSTANCE);
    }

    @Test
    public void factory_from_TemporalAccessor_noChronology() {
        assertEquals(Chronology.from(new TemporalAccessor() {
            @Override
            public boolean isSupported(TemporalField field) {
                throw new UnsupportedOperationException();
            }

            @Override
            public long getLong(TemporalField field) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <R> R query(TemporalQuery<R> query) {
                if (query == Queries.chronology()) {
                    return null;
                }
                throw new UnsupportedOperationException();
            }
        }), IsoChronology.INSTANCE);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void factory_from_TemporalAccessor_null() {
        Chronology.from(null);
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_date_TemporalAccessor() {
        assertEquals(IsoChronology.INSTANCE.date(new TemporalAccessor() {
            @Override
            public boolean isSupported(TemporalField field) {
                if (field == ChronoField.EPOCH_DAY) {
                    return true;
                }
                throw new UnsupportedOperationException();
            }

            @Override
            public long getLong(TemporalField field) {
                if (field == ChronoField.EPOCH_DAY) {
                    return LocalDate.of(2012, 6, 30).toEpochDay();
                }
                throw new UnsupportedOperationException();
            }

            @SuppressWarnings("unchecked")
            @Override
            public <R> R query(TemporalQuery<R> query) {
                if (query == Queries.localDate()) {
                    return (R) LocalDate.of(2012, 6, 30);
                }
                throw new UnsupportedOperationException();
            }
        }), LocalDate.of(2012, 6, 30));
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_date_TemporalAccessor_null() {
        IsoChronology.INSTANCE.date(null);
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_localDateTime_TemporalAccessor() {
        assertEquals(IsoChronology.INSTANCE.localDateTime(new TemporalAccessor() {
            @Override
            public boolean isSupported(TemporalField field) {
                if (field == ChronoField.EPOCH_DAY || field == ChronoField.NANO_OF_DAY) {
                    return true;
                }
                throw new UnsupportedOperationException();
            }

            @Override
            public long getLong(TemporalField field) {
                if (field == ChronoField.EPOCH_DAY) {
                    return LocalDate.of(2012, 6, 30).toEpochDay();
                }
                if (field == ChronoField.NANO_OF_DAY) {
                    return LocalTime.of(12, 30, 40).toNanoOfDay();
                }
                throw new UnsupportedOperationException();
            }

            @SuppressWarnings("unchecked")
            @Override
            public <R> R query(TemporalQuery<R> query) {
                if (query == Queries.localDate()) {
                    return (R) LocalDate.of(2012, 6, 30);
                }
                if (query == Queries.localTime()) {
                    return (R) LocalTime.of(12, 30, 40);
                }
                throw new UnsupportedOperationException();
            }
        }), LocalDateTime.of(2012, 6, 30, 12, 30, 40));
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_localDateTime_TemporalAccessor_null() {
        IsoChronology.INSTANCE.localDateTime(null);
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_zonedDateTime_TemporalAccessor() {
        assertEquals(IsoChronology.INSTANCE.zonedDateTime(new TemporalAccessor() {
            @Override
            public boolean isSupported(TemporalField field) {
                if (field == ChronoField.EPOCH_DAY || field == ChronoField.NANO_OF_DAY ||
                        field == ChronoField.INSTANT_SECONDS || field == ChronoField.NANO_OF_SECOND) {
                    return true;
                }
                throw new UnsupportedOperationException();
            }

            @Override
            public long getLong(TemporalField field) {
                if (field == ChronoField.INSTANT_SECONDS) {
                    return ZonedDateTime.of(2012, 6, 30, 12, 30, 40, 0, ZoneId.of("Europe/London")).toEpochSecond();
                }
                if (field == ChronoField.NANO_OF_SECOND) {
                    return 0;
                }
                if (field == ChronoField.EPOCH_DAY) {
                    return LocalDate.of(2012, 6, 30).toEpochDay();
                }
                if (field == ChronoField.NANO_OF_DAY) {
                    return LocalTime.of(12, 30, 40).toNanoOfDay();
                }
                throw new UnsupportedOperationException();
            }

            @SuppressWarnings("unchecked")
            @Override
            public <R> R query(TemporalQuery<R> query) {
                if (query == Queries.localDate()) {
                    return (R) LocalDate.of(2012, 6, 30);
                }
                if (query == Queries.localTime()) {
                    return (R) LocalTime.of(12, 30, 40);
                }
                if (query == Queries.zoneId() || query == Queries.zone()) {
                    return (R) ZoneId.of("Europe/London");
                }
                throw new UnsupportedOperationException();
            }
        }), ZonedDateTime.of(2012, 6, 30, 12, 30, 40, 0, ZoneId.of("Europe/London")));
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_zonedDateTime_TemporalAccessor_null() {
        IsoChronology.INSTANCE.zonedDateTime(null);
    }

}
