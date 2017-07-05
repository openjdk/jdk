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
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Copyright (c) 2009-2012, Stephen Colebourne & Michael Nascimento Santos
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
package build.tools.tzdb;

import static build.tools.tzdb.Utils.*;
import java.util.Objects;

/**
 * A rule expressing how to create a transition.
 * <p>
 * This class allows rules for identifying future transitions to be expressed.
 * A rule might be written in many forms:
 * <p><ul>
 * <li>the 16th March
 * <li>the Sunday on or after the 16th March
 * <li>the Sunday on or before the 16th March
 * <li>the last Sunday in February
 * </ul><p>
 * These different rule types can be expressed and queried.
 *
 * <h3>Specification for implementors</h3>
 * This class is immutable and thread-safe.
 *
 * @since 1.8
 */
final class ZoneOffsetTransitionRule {

    /**
     * The month of the month-day of the first day of the cutover week.
     * The actual date will be adjusted by the dowChange field.
     */
    final int month;
    /**
     * The day-of-month of the month-day of the cutover week.
     * If positive, it is the start of the week where the cutover can occur.
     * If negative, it represents the end of the week where cutover can occur.
     * The value is the number of days from the end of the month, such that
     * {@code -1} is the last day of the month, {@code -2} is the second
     * to last day, and so on.
     */
    final byte dom;
    /**
     * The cutover day-of-week, -1 to retain the day-of-month.
     */
    final int dow;
    /**
     * The cutover time in the 'before' offset.
     */
    final LocalTime time;
    /**
     * Whether the cutover time is midnight at the end of day.
     */
    final boolean timeEndOfDay;
    /**
     * The definition of how the local time should be interpreted.
     */
    final TimeDefinition timeDefinition;
    /**
     * The standard offset at the cutover.
     */
    final ZoneOffset standardOffset;
    /**
     * The offset before the cutover.
     */
    final ZoneOffset offsetBefore;
    /**
     * The offset after the cutover.
     */
    final ZoneOffset offsetAfter;

    /**
     * Creates an instance defining the yearly rule to create transitions between two offsets.
     *
     * @param month  the month of the month-day of the first day of the cutover week, from 1 to 12
     * @param dayOfMonthIndicator  the day of the month-day of the cutover week, positive if the week is that
     *  day or later, negative if the week is that day or earlier, counting from the last day of the month,
     *  from -28 to 31 excluding 0
     * @param dayOfWeek  the required day-of-week, -1 if the month-day should not be changed
     * @param time  the cutover time in the 'before' offset, not null
     * @param timeEndOfDay  whether the time is midnight at the end of day
     * @param timeDefnition  how to interpret the cutover
     * @param standardOffset  the standard offset in force at the cutover, not null
     * @param offsetBefore  the offset before the cutover, not null
     * @param offsetAfter  the offset after the cutover, not null
     * @throws IllegalArgumentException if the day of month indicator is invalid
     * @throws IllegalArgumentException if the end of day flag is true when the time is not midnight
     */
    ZoneOffsetTransitionRule(
            int month,
            int dayOfMonthIndicator,
            int dayOfWeek,
            LocalTime time,
            boolean timeEndOfDay,
            TimeDefinition timeDefnition,
            ZoneOffset standardOffset,
            ZoneOffset offsetBefore,
            ZoneOffset offsetAfter) {
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(timeDefnition, "timeDefnition");
        Objects.requireNonNull(standardOffset, "standardOffset");
        Objects.requireNonNull(offsetBefore, "offsetBefore");
        Objects.requireNonNull(offsetAfter, "offsetAfter");
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month must be between 1 and 12");
        }
        if (dayOfMonthIndicator < -28 || dayOfMonthIndicator > 31 || dayOfMonthIndicator == 0) {
            throw new IllegalArgumentException("Day of month indicator must be between -28 and 31 inclusive excluding zero");
        }
        if (timeEndOfDay && time.equals(LocalTime.MIDNIGHT) == false) {
            throw new IllegalArgumentException("Time must be midnight when end of day flag is true");
        }
        this.month = month;
        this.dom = (byte) dayOfMonthIndicator;
        this.dow = dayOfWeek;
        this.time = time;
        this.timeEndOfDay = timeEndOfDay;
        this.timeDefinition = timeDefnition;
        this.standardOffset = standardOffset;
        this.offsetBefore = offsetBefore;
        this.offsetAfter = offsetAfter;
    }

    //-----------------------------------------------------------------------
    /**
     * Checks if this object equals another.
     * <p>
     * The entire state of the object is compared.
     *
     * @param otherRule  the other object to compare to, null returns false
     * @return true if equal
     */
    @Override
    public boolean equals(Object otherRule) {
        if (otherRule == this) {
            return true;
        }
        if (otherRule instanceof ZoneOffsetTransitionRule) {
            ZoneOffsetTransitionRule other = (ZoneOffsetTransitionRule) otherRule;
            return month == other.month && dom == other.dom && dow == other.dow &&
                timeDefinition == other.timeDefinition &&
                time.equals(other.time) &&
                timeEndOfDay == other.timeEndOfDay &&
                standardOffset.equals(other.standardOffset) &&
                offsetBefore.equals(other.offsetBefore) &&
                offsetAfter.equals(other.offsetAfter);
        }
        return false;
    }

    /**
     * Returns a suitable hash code.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        int hash = ((time.toSecondOfDay() + (timeEndOfDay ? 1 : 0)) << 15) +
                (month << 11) + ((dom + 32) << 5) +
                ((dow == -1 ? 8 : dow) << 2) + (timeDefinition.ordinal());
        return hash ^ standardOffset.hashCode() ^
                offsetBefore.hashCode() ^ offsetAfter.hashCode();
    }

}
