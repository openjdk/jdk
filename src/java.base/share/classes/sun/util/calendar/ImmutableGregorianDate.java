/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.util.calendar;

import java.util.Objects;
import java.util.TimeZone;

/*
 * This class is immutable, and thus any methods from the base classes
 * that can modify the state are overridden to throw an exception.
 */
final class ImmutableGregorianDate extends BaseCalendar.Date {
    private final BaseCalendar.Date date;

    ImmutableGregorianDate(BaseCalendar.Date date) {
        this.date = Objects.requireNonNull(date);
    }

    @Override
    public Era getEra() {
        return date.getEra();
    }

    @Override
    public CalendarDate setEra(Era era) {
        unsupported(); return this;
    }

    @Override
    public int getYear() {
        return date.getYear();
    }

    @Override
    public CalendarDate setYear(int year) {
        unsupported(); return this;
    }

    @Override
    public CalendarDate addYear(int n) {
        unsupported(); return this;
    }

    @Override
    public boolean isLeapYear() {
        return date.isLeapYear();
    }

    @Override
    void setLeapYear(boolean leapYear) {
        unsupported();
    }

    @Override
    public int getMonth() {
        return date.getMonth();
    }

    @Override
    public CalendarDate setMonth(int month) {
        unsupported(); return this;
    }

    @Override
    public CalendarDate addMonth(int n) {
        unsupported(); return this;
    }

    @Override
    public int getDayOfMonth() {
        return date.getDayOfMonth();
    }

    @Override
    public CalendarDate setDayOfMonth(int date) {
        unsupported(); return this;
    }

    @Override
    public int getDayOfWeek() {
        return date.getDayOfWeek();
    }

    @Override
    public int getHours() {
        return date.getHours();
    }

    @Override
    public CalendarDate setHours(int hours) {
        unsupported(); return this;
    }

    @Override
    public CalendarDate addHours(int n) {
        unsupported(); return this;
    }

    @Override
    public int getMinutes() {
        return date.getMinutes();
    }

    @Override
    public CalendarDate setMinutes(int minutes) {
        unsupported(); return this;
    }

    @Override
    public int getSeconds() {
        return date.getSeconds();
    }

    @Override
    public CalendarDate setSeconds(int seconds) {
        unsupported(); return this;
    }

    @Override
    public int getMillis() {
        return date.getMillis();
    }

    @Override
    public CalendarDate setMillis(int millis) {
        unsupported(); return this;
    }

    @Override
    public long getTimeOfDay() {
        return date.getTimeOfDay();
    }

    @Override
    public CalendarDate setDate(int year, int month, int dayOfMonth) {
        unsupported(); return this;
    }

    @Override
    public CalendarDate setTimeOfDay(int hours, int minutes, int seconds, int millis) {
        unsupported(); return this;
    }

    @Override
    protected void setTimeOfDay(long fraction) {
        unsupported();
    }

    @Override
    public boolean isNormalized() {
        return date.isNormalized();
    }

    @Override
    public boolean isDaylightTime() {
        return date.isDaylightTime();
    }

    @Override
    public TimeZone getZone() {
        return date.getZone();
    }

    @Override
    public CalendarDate setZone(TimeZone zoneinfo) {
        unsupported(); return this;
    }

    @Override
    public boolean isSameDate(CalendarDate date) {
        return date.isSameDate(date);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof ImmutableGregorianDate igd) {
            return date.equals(igd.date);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return date.hashCode();
    }

    @Override
    public Object clone() {
        return super.clone();
    }

    @Override
    public String toString() {
        return date.toString();
    }

    @Override
    protected void setDayOfWeek(int dayOfWeek) {
        unsupported();
    }

    @Override
    protected void setNormalized(boolean normalized) {
        unsupported();
    }

    @Override
    public int getZoneOffset() {
        return date.getZoneOffset();
    }

    @Override
    protected void setZoneOffset(int offset) {
        unsupported();
    }

    @Override
    public int getDaylightSaving() {
        return date.getDaylightSaving();
    }

    @Override
    protected void setDaylightSaving(int daylightSaving) {
        unsupported();
    }

    @Override
    public int getNormalizedYear() {
        return date.getNormalizedYear();
    }

    @Override
    public void setNormalizedYear(int normalizedYear) {
        unsupported();
    }

    private void unsupported() {
        throw new UnsupportedOperationException();
    }
}
