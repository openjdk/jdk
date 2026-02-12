/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, Alibaba Group Holding Limited. All Rights Reserved.
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
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Copyright (c) 2011-2012, Stephen Colebourne & Michael Nascimento Santos
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
package java.time.format;

import static java.time.temporal.ChronoField.EPOCH_DAY;
import static java.time.temporal.ChronoField.INSTANT_SECONDS;
import static java.time.temporal.ChronoField.OFFSET_SECONDS;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.chrono.ChronoLocalDate;
import java.time.chrono.Chronology;
import java.time.chrono.IsoChronology;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQueries;
import java.time.temporal.TemporalQuery;
import java.time.temporal.ValueRange;
import java.util.Locale;
import java.util.Objects;

/**
 * Context object used during date and time printing.
 * <p>
 * This class provides a single wrapper to items used in the format.
 *
 * @since 1.8
 */
final class DateTimePrintContext {

    /**
     * The temporal being output.
     */
    private final TemporalAccessor temporal;
    /**
     * The formatter, not null.
     */
    private final DateTimeFormatter formatter;

    /**
     * Creates a new instance of the context.
     *
     * @param temporal  the temporal object being output, not null
     * @param formatter  the formatter controlling the format, not null
     */
    DateTimePrintContext(TemporalAccessor temporal, DateTimeFormatter formatter) {
        this.temporal = adjust(temporal, formatter);
        this.formatter = formatter;
    }

    /**
     * Adjusts the given {@link TemporalAccessor} using chronology and time-zone from a formatter if present.
     * <p>
     * This method serves as an optimization front-end that checks for non-null overrides in the formatter.
     * If neither chronology nor time-zone is specified in the formatter, returns the original temporal unchanged.
     * Otherwise, delegates to the core adjustment method {@link #adjustWithOverride(TemporalAccessor, Chronology, ZoneId)}.
     *
     * @implNote Optimizes for the common case where formatters don't specify chronology/time-zone
     *           by avoiding unnecessary processing. Most formatters have null for these properties.
     * @param temporal  the temporal object to adjust, not null
     * @param formatter the formatter providing potential chronology and time-zone overrides
     * @return the adjusted temporal, or the original if no overrides are present in the formatter
     */
    private static TemporalAccessor adjust(final TemporalAccessor temporal, DateTimeFormatter formatter) {
        // normal case first (early return is an optimization)
        Chronology overrideChrono = formatter.getChronology();
        ZoneId overrideZone = formatter.getZone();
        if (overrideChrono == null && overrideZone == null) {
            return temporal;
        }

        // Placing the non-null cases in a separate method allows more flexible code optimizations
        return adjustWithOverride(temporal, overrideChrono, overrideZone);
    }

    /**
     * Adjusts the given {@link TemporalAccessor} with optional overriding chronology and time-zone.
     * <p>
     * This method minimizes changes by returning the original temporal if the override parameters
     * are either {@code null} or equivalent to those already present in the temporal. When overrides
     * are applied:
     * <ul>
     *   <li>If a time-zone override is provided and the temporal supports {@link ChronoField#INSTANT_SECONDS},
     *       the result is a zoned date-time using the override time-zone and chronology (defaulting to ISO if not overridden).</li>
     *   <li>Other cases (including partial date-times or mixed chronology/time-zone changes) are delegated
     *       to a secondary adjustment method.</li>
     * </ul>
     *
     * @param temporal       the temporal object to adjust, not null
     * @param overrideChrono the chronology to override (null retains the original chronology)
     * @param overrideZone   the time-zone to override (null retains the original time-zone)
     * @return the adjusted temporal, which may be the original object if no effective changes were made,
     *         or a new object with the applied overrides
     * @implNote Optimizes for common cases where overrides are identical to existing values
     *           or where instant-based temporals can be directly converted with a time-zone.
     */
    private static TemporalAccessor adjustWithOverride(TemporalAccessor temporal, Chronology overrideChrono, ZoneId overrideZone) {
        // ensure minimal change (early return is an optimization)
        Chronology temporalChrono = temporal.query(TemporalQueries.chronology());
        ZoneId temporalZone = temporal.query(TemporalQueries.zoneId());
        if (Objects.equals(overrideChrono, temporalChrono)) {
            overrideChrono = null;
        }
        if (Objects.equals(overrideZone, temporalZone)) {
            overrideZone = null;
        }
        if (overrideChrono == null && overrideZone == null) {
            return temporal;
        }

        // make adjustment
        final Chronology effectiveChrono = (overrideChrono != null ? overrideChrono : temporalChrono);
        if (overrideZone != null) {
            // if have zone and instant, calculation is simple, defaulting chrono if necessary
            if (temporal.isSupported(INSTANT_SECONDS)) {
                Chronology chrono = Objects.requireNonNullElse(effectiveChrono, IsoChronology.INSTANCE);
                return chrono.zonedDateTime(Instant.from(temporal), overrideZone);
            }
        }

        // Split uncommon code branches into a separate method
        return adjustSlow(temporal, overrideZone, temporalZone, overrideChrono, effectiveChrono, temporalChrono);
    }

    /**
     * Internal helper method to adjust temporal fields using override chronology and time-zone in complex cases.
     * <p>
     * Handles non-instant temporal objects by creating a delegate {@link TemporalAccessor} that combines:
     * <ul>
     *   <li>The original temporal's time-related fields</li>
     *   <li>Date fields converted to the effective chronology (if available)</li>
     *   <li>Override zone/chronology information for temporal queries</li>
     * </ul>
     *
     * Performs critical validation before processing:
     * <ul>
     *   <li>Rejects offset changes for non-instant temporal objects with existing offsets</li>
     *   <li>Verifies date field integrity when applying chronology overrides to partial dates</li>
     * </ul>
     *
     * @param temporal        the original temporal object to adjust, not null
     * @param overrideZone    override time-zone (nullable)
     * @param temporalZone    original time-zone from temporal (nullable)
     * @param overrideChrono  override chronology (nullable)
     * @param effectiveChrono precomputed effective chronology (override if present, otherwise temporal's chronology)
     * @param temporalChrono  original chronology from temporal (nullable)
     * @return adjusted temporal accessor combining original fields with overrides
     * @throws DateTimeException if:
     *         <ul>
     *           <li>Applying a {@link ZoneOffset} override to a temporal with conflicting existing offset that doesn't represent an instant</li>
     *           <li>Applying chronology override to temporal with partial date fields</li>
     *         </ul>
     * @implNote Creates an anonymous temporal accessor that:
     *         <ul>
     *           <li>Delegates time-based fields to original temporal</li>
     *           <li>Uses converted date fields when chronology override is applied</li>
     *           <li>Responds to chronology/zone queries with effective values</li>
     *           <li>Preserves precision queries from original temporal</li>
     *         </ul>
     */
    private static TemporalAccessor adjustSlow(
            TemporalAccessor temporal,
            ZoneId overrideZone, ZoneId temporalZone,
            Chronology overrideChrono, Chronology effectiveChrono, Chronology temporalChrono) {
        if (overrideZone != null) {
            // block changing zone on OffsetTime, and similar problem cases
            if (overrideZone.normalized() instanceof ZoneOffset && temporal.isSupported(OFFSET_SECONDS) &&
                    temporal.get(OFFSET_SECONDS) != overrideZone.getRules().getOffset(Instant.EPOCH).getTotalSeconds()) {
                throw new DateTimeException("Unable to apply override zone '" + overrideZone +
                        "' because the temporal object being formatted has a different offset but" +
                        " does not represent an instant: " + temporal);
            }
        }
        final ZoneId effectiveZone = (overrideZone != null ? overrideZone : temporalZone);
        final ChronoLocalDate effectiveDate;
        if (overrideChrono != null) {
            if (temporal.isSupported(EPOCH_DAY)) {
                effectiveDate = effectiveChrono.date(temporal);
            } else {
                // check for date fields other than epoch-day, ignoring case of converting null to ISO
                if (!(overrideChrono == IsoChronology.INSTANCE && temporalChrono == null)) {
                    for (ChronoField f : ChronoField.values()) {
                        if (f.isDateBased() && temporal.isSupported(f)) {
                            throw new DateTimeException("Unable to apply override chronology '" + overrideChrono +
                                    "' because the temporal object being formatted contains date fields but" +
                                    " does not represent a whole date: " + temporal);
                        }
                    }
                }
                effectiveDate = null;
            }
        } else {
            effectiveDate = null;
        }

        // combine available data
        // this is a non-standard temporal that is almost a pure delegate
        // this better handles map-like underlying temporal instances
        return new TemporalAccessor() {
            @Override
            public boolean isSupported(TemporalField field) {
                if (effectiveDate != null && field.isDateBased()) {
                    return effectiveDate.isSupported(field);
                }
                return temporal.isSupported(field);
            }
            @Override
            public ValueRange range(TemporalField field) {
                if (effectiveDate != null && field.isDateBased()) {
                    return effectiveDate.range(field);
                }
                return temporal.range(field);
            }
            @Override
            public long getLong(TemporalField field) {
                if (effectiveDate != null && field.isDateBased()) {
                    return effectiveDate.getLong(field);
                }
                return temporal.getLong(field);
            }
            @SuppressWarnings("unchecked")
            @Override
            public <R> R query(TemporalQuery<R> query) {
                if (query == TemporalQueries.chronology()) {
                    return (R) effectiveChrono;
                }
                if (query == TemporalQueries.zoneId()) {
                    return (R) effectiveZone;
                }
                if (query == TemporalQueries.precision()) {
                    return temporal.query(query);
                }
                return query.queryFrom(this);
            }

            @Override
            public String toString() {
                return temporal +
                        (effectiveChrono != null ? " with chronology " + effectiveChrono : "") +
                        (effectiveZone != null ? " with zone " + effectiveZone : "");
            }
        };
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the temporal object being output.
     *
     * @return the temporal object, not null
     */
    TemporalAccessor getTemporal() {
        return temporal;
    }

    /**
     * Gets the locale.
     * <p>
     * This locale is used to control localization in the format output except
     * where localization is controlled by the DecimalStyle.
     *
     * @return the locale, not null
     */
    Locale getLocale() {
        return formatter.getLocale();
    }

    /**
     * Gets the DecimalStyle.
     * <p>
     * The DecimalStyle controls the localization of numeric output.
     *
     * @return the DecimalStyle, not null
     */
    DecimalStyle getDecimalStyle() {
        return formatter.getDecimalStyle();
    }

    //-----------------------------------------------------------------------
    /**
     * Gets a value using a query.
     *
     * @param query  the query to use, not null
     * @param optional  whether the query is optional, true if the query may be missing
     * @return the result, null if not found and optional is true
     * @throws DateTimeException if the type is not available and the section is not optional
     */
    <R> R getValue(TemporalQuery<R> query, boolean optional) {
        R result = temporal.query(query);
        if (result == null && !optional) {
            throw new DateTimeException("Unable to extract " +
                    query + " from temporal " + temporal);
        }
        return result;
    }

    /**
     * Gets the value of the specified field.
     * <p>
     * This will return the value for the specified field.
     *
     * @param field  the field to find, not null
     * @param optional  whether the field is optional, true if the field may be missing
     * @return the value, null if not found and optional is true
     * @throws DateTimeException if the field is not available and the section is not optional
     */
    Long getValue(TemporalField field, boolean optional) {
        if (optional && !temporal.isSupported(field)) {
            return null;
        }
        return temporal.getLong(field);
    }

    //-----------------------------------------------------------------------
    /**
     * Returns a string version of the context for debugging.
     *
     * @return a string representation of the context, not null
     */
    @Override
    public String toString() {
        return temporal.toString();
    }

}
