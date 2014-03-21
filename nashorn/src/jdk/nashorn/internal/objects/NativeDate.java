/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.objects;

import static java.lang.Double.NaN;
import static java.lang.Double.isInfinite;
import static java.lang.Double.isNaN;
import static jdk.nashorn.internal.runtime.ECMAErrors.rangeError;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;

import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import jdk.nashorn.internal.objects.annotations.Attribute;
import jdk.nashorn.internal.objects.annotations.Constructor;
import jdk.nashorn.internal.objects.annotations.Function;
import jdk.nashorn.internal.objects.annotations.ScriptClass;
import jdk.nashorn.internal.objects.annotations.SpecializedConstructor;
import jdk.nashorn.internal.objects.annotations.Where;
import jdk.nashorn.internal.parser.DateParser;
import jdk.nashorn.internal.runtime.ConsString;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptEnvironment;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.linker.Bootstrap;
import jdk.nashorn.internal.runtime.linker.InvokeByName;

/**
 * ECMA 15.9 Date Objects
 *
 */
@ScriptClass("Date")
public final class NativeDate extends ScriptObject {

    private static final String INVALID_DATE = "Invalid Date";

    private static final int YEAR        = 0;
    private static final int MONTH       = 1;
    private static final int DAY         = 2;
    private static final int HOUR        = 3;
    private static final int MINUTE      = 4;
    private static final int SECOND      = 5;
    private static final int MILLISECOND = 6;

    private static final int FORMAT_DATE_TIME       = 0;
    private static final int FORMAT_DATE            = 1;
    private static final int FORMAT_TIME            = 2;
    private static final int FORMAT_LOCAL_DATE_TIME = 3;
    private static final int FORMAT_LOCAL_DATE      = 4;
    private static final int FORMAT_LOCAL_TIME      = 5;

    // Constants defined in ECMA 15.9.1.10
    private static final int    hoursPerDay      = 24;
    private static final int    minutesPerHour   = 60;
    private static final int    secondsPerMinute = 60;
    private static final int    msPerSecond   = 1_000;
    private static final int    msPerMinute  = 60_000;
    private static final double msPerHour = 3_600_000;
    private static final double msPerDay = 86_400_000;

    private static int[][] firstDayInMonth = {
            {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334}, // normal year
            {0, 31, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335}  // leap year
    };

    private static String[] weekDays = {
            "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"
    };

    private static String[] months = {
            "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    };

    private static final Object TO_ISO_STRING = new Object();

    private static InvokeByName getTO_ISO_STRING() {
        return Global.instance().getInvokeByName(TO_ISO_STRING,
                new Callable<InvokeByName>() {
                    @Override
                    public InvokeByName call() {
                        return new InvokeByName("toISOString", ScriptObject.class, Object.class, Object.class);
                    }
                });
    }

    private double time;
    private final TimeZone timezone;

    // initialized by nasgen
    private static PropertyMap $nasgenmap$;

    private NativeDate(final double time, final ScriptObject proto, final PropertyMap map) {
        super(proto, map);
        final ScriptEnvironment env = Global.getEnv();

        this.time = time;
        this.timezone = env._timezone;
    }

    NativeDate(final double time, final Global global) {
        this(time, global.getDatePrototype(), $nasgenmap$);
    }

    private NativeDate (final double time) {
        this(time, Global.instance());
    }

    private NativeDate() {
        this(System.currentTimeMillis());
    }

    @Override
    public String getClassName() {
        return "Date";
    }

    // ECMA 8.12.8 [[DefaultValue]] (hint)
    @Override
    public Object getDefaultValue(final Class<?> hint) {
        // When the [[DefaultValue]] internal method of O is called with no hint,
        // then it behaves as if the hint were Number, unless O is a Date object
        // in which case it behaves as if the hint were String.
        return super.getDefaultValue(hint == null ? String.class : hint);
    }

    /**
     * Constructor - ECMA 15.9.3.1 new Date
     *
     * @param isNew is this Date constructed with the new operator
     * @param self  self references
     * @return Date representing now
     */
    @SpecializedConstructor
    public static Object construct(final boolean isNew, final Object self) {
        final NativeDate result = new NativeDate();
        return isNew ? result : toStringImpl(result, FORMAT_DATE_TIME);
    }

    /**
     * Constructor - ECMA 15.9.3.1 new Date (year, month [, date [, hours [, minutes [, seconds [, ms ] ] ] ] ] )
     *
     * @param isNew is this Date constructed with the new operator
     * @param self  self reference
     * @param args  arguments
     * @return new Date
     */
    @Constructor(arity = 7)
    public static Object construct(final boolean isNew, final Object self, final Object... args) {
        if (! isNew) {
            return toStringImpl(new NativeDate(), FORMAT_DATE_TIME);
        }

        NativeDate result;
        switch (args.length) {
        case 0:
            result = new NativeDate();
            break;

        case 1:
            double num;
            final Object arg = JSType.toPrimitive(args[0]);
            if (arg instanceof String || arg instanceof ConsString) {
                num = parseDateString(arg.toString());
            } else {
                num = timeClip(JSType.toNumber(args[0]));
            }
            result = new NativeDate(num);
            break;

        default:
            result = new NativeDate(0);
            final double[] d = convertCtorArgs(args);
            if (d == null) {
                result.setTime(Double.NaN);
            } else {
                final double time = timeClip(utc(makeDate(d), result.getTimeZone()));
                result.setTime(time);
            }
            break;
         }

         return result;
    }

    @Override
    public String safeToString() {
        final String str = isValidDate() ? toISOStringImpl(this) : INVALID_DATE;
        return "[Date " + str + "]";
    }

    @Override
    public String toString() {
        return isValidDate() ? toString(this).toString() : INVALID_DATE;
    }

    /**
     * ECMA 15.9.4.2 Date.parse (string)
     *
     * @param self self reference
     * @param string string to parse as date
     * @return Date interpreted from the string, or NaN for illegal values
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object parse(final Object self, final Object string) {
        return parseDateString(JSType.toString(string));
    }

    /**
     * ECMA 15.9.4.3 Date.UTC (year, month [, date [, hours [, minutes [, seconds [, ms ] ] ] ] ] )
     *
     * @param self self reference
     * @param args mandatory args are year, month. Optional are date, hours, minutes, seconds and milliseconds
     * @return a time clip according to the ECMA specification
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 7, where = Where.CONSTRUCTOR)
    public static Object UTC(final Object self, final Object... args) {
        final NativeDate nd = new NativeDate(0);
        final double[] d = convertCtorArgs(args);
        final double time = d == null ? Double.NaN : timeClip(makeDate(d));
        nd.setTime(time);
        return time;
    }

    /**
     * ECMA 15.9.4.4 Date.now ( )
     *
     * @param self self reference
     * @return a Date that points to the current moment in time
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, where = Where.CONSTRUCTOR)
    public static Object now(final Object self) {
        return (double)System.currentTimeMillis();
    }

    /**
     * ECMA 15.9.5.2 Date.prototype.toString ( )
     *
     * @param self self reference
     * @return string value that represents the Date in the current time zone
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object toString(final Object self) {
        return toStringImpl(self, FORMAT_DATE_TIME);
    }

    /**
     * ECMA 15.9.5.3 Date.prototype.toDateString ( )
     *
     * @param self self reference
     * @return string value with the "date" part of the Date in the current time zone
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object toDateString(final Object self) {
        return toStringImpl(self, FORMAT_DATE);
    }

    /**
     * ECMA 15.9.5.4 Date.prototype.toTimeString ( )
     *
     * @param self self reference
     * @return string value with "time" part of Date in the current time zone
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object toTimeString(final Object self) {
        return toStringImpl(self, FORMAT_TIME);
    }

    /**
     * ECMA 15.9.5.5 Date.prototype.toLocaleString ( )
     *
     * @param self self reference
     * @return string value that represents the Data in the current time zone and locale
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object toLocaleString(final Object self) {
        return toStringImpl(self, FORMAT_LOCAL_DATE_TIME);
    }

    /**
     * ECMA 15.9.5.6 Date.prototype.toLocaleDateString ( )
     *
     * @param self self reference
     * @return string value with the "date" part of the Date in the current time zone and locale
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object toLocaleDateString(final Object self) {
        return toStringImpl(self, FORMAT_LOCAL_DATE);
    }

    /**
     * ECMA 15.9.5.7 Date.prototype.toLocaleTimeString ( )
     *
     * @param self self reference
     * @return string value with the "time" part of Date in the current time zone and locale
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object toLocaleTimeString(final Object self) {
        return toStringImpl(self, FORMAT_LOCAL_TIME);
    }

    /**
     * ECMA 15.9.5.8 Date.prototype.valueOf ( )
     *
     * @param self self reference
     * @return valueOf - a number which is this time value
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object valueOf(final Object self) {
        final NativeDate nd = getNativeDate(self);
        return (nd != null) ? nd.getTime() : Double.NaN;
    }

    /**
     * ECMA 15.9.5.9 Date.prototype.getTime ( )
     *
     * @param self self reference
     * @return time
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object getTime(final Object self) {
        final NativeDate nd = getNativeDate(self);
        return (nd != null) ? nd.getTime() : Double.NaN;
    }

    /**
     * ECMA 15.9.5.10 Date.prototype.getFullYear ( )
     *
     * @param self self reference
     * @return full year
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object getFullYear(final Object self) {
        return getField(self, YEAR);
    }

    /**
     * ECMA 15.9.5.11 Date.prototype.getUTCFullYear( )
     *
     * @param self self reference
     * @return UTC full year
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object getUTCFullYear(final Object self) {
        return getUTCField(self, YEAR);
    }

    /**
     * B.2.4 Date.prototype.getYear ( )
     *
     * @param self self reference
     * @return year
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object getYear(final Object self) {
        final NativeDate nd = getNativeDate(self);
        return (nd != null && nd.isValidDate()) ? (yearFromTime(nd.getLocalTime()) - 1900) : Double.NaN;
    }

    /**
     * ECMA 15.9.5.12 Date.prototype.getMonth ( )
     *
     * @param self self reference
     * @return month
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object getMonth(final Object self) {
        return getField(self, MONTH);
    }

    /**
     * ECMA 15.9.5.13 Date.prototype.getUTCMonth ( )
     *
     * @param self self reference
     * @return UTC month
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object getUTCMonth(final Object self) {
        return getUTCField(self, MONTH);
    }

    /**
     * ECMA 15.9.5.14 Date.prototype.getDate ( )
     *
     * @param self self reference
     * @return date
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object getDate(final Object self) {
        return getField(self, DAY);
    }

    /**
     * ECMA 15.9.5.15 Date.prototype.getUTCDate ( )
     *
     * @param self self reference
     * @return UTC Date
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object getUTCDate(final Object self) {
        return getUTCField(self, DAY);
    }

    /**
     * ECMA 15.9.5.16 Date.prototype.getDay ( )
     *
     * @param self self reference
     * @return day
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object getDay(final Object self) {
        final NativeDate nd = getNativeDate(self);
        return (nd != null && nd.isValidDate()) ? weekDay(nd.getLocalTime()) : Double.NaN;
    }

    /**
     * ECMA 15.9.5.17 Date.prototype.getUTCDay ( )
     *
     * @param self self reference
     * @return UTC day
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object getUTCDay(final Object self) {
        final NativeDate nd = getNativeDate(self);
        return (nd != null && nd.isValidDate()) ? weekDay(nd.getTime()) : Double.NaN;
    }

    /**
     * ECMA 15.9.5.18 Date.prototype.getHours ( )
     *
     * @param self self reference
     * @return hours
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object getHours(final Object self) {
        return getField(self, HOUR);
    }

    /**
     * ECMA 15.9.5.19 Date.prototype.getUTCHours ( )
     *
     * @param self self reference
     * @return UTC hours
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object getUTCHours(final Object self) {
        return getUTCField(self, HOUR);
    }

    /**
     * ECMA 15.9.5.20 Date.prototype.getMinutes ( )
     *
     * @param self self reference
     * @return minutes
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object getMinutes(final Object self) {
        return getField(self, MINUTE);
    }

    /**
     * ECMA 15.9.5.21 Date.prototype.getUTCMinutes ( )
     *
     * @param self self reference
     * @return UTC minutes
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object getUTCMinutes(final Object self) {
        return getUTCField(self, MINUTE);
    }

    /**
     * ECMA 15.9.5.22 Date.prototype.getSeconds ( )
     *
     * @param self self reference
     * @return seconds
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object getSeconds(final Object self) {
        return getField(self, SECOND);
    }

    /**
     * ECMA 15.9.5.23 Date.prototype.getUTCSeconds ( )
     *
     * @param self self reference
     * @return UTC seconds
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object getUTCSeconds(final Object self) {
        return getUTCField(self, SECOND);
    }

    /**
     * ECMA 15.9.5.24 Date.prototype.getMilliseconds ( )
     *
     * @param self self reference
     * @return milliseconds
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object getMilliseconds(final Object self) {
        return getField(self, MILLISECOND);
    }

    /**
     * ECMA 15.9.5.25 Date.prototype.getUTCMilliseconds ( )
     *
     * @param self self reference
     * @return UTC milliseconds
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object getUTCMilliseconds(final Object self) {
        return getUTCField(self, MILLISECOND);
    }

    /**
     * ECMA 15.9.5.26 Date.prototype.getTimezoneOffset ( )
     *
     * @param self self reference
     * @return time zone offset or NaN if N/A
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object getTimezoneOffset(final Object self) {
        final NativeDate nd = getNativeDate(self);
        if (nd != null && nd.isValidDate()) {
            final long msec = (long) nd.getTime();
            return - nd.getTimeZone().getOffset(msec) / msPerMinute;
        }
        return Double.NaN;
    }

    /**
     * ECMA 15.9.5.27 Date.prototype.setTime (time)
     *
     * @param self self reference
     * @param time time
     * @return time
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object setTime(final Object self, final Object time) {
        final NativeDate nd  = getNativeDate(self);
        final double     num = timeClip(JSType.toNumber(time));
        nd.setTime(num);
        return num;
    }

    /**
     * ECMA 15.9.5.28 Date.prototype.setMilliseconds (ms)
     *
     * @param self self reference
     * @param args milliseconds
     * @return time
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object setMilliseconds(final Object self, final Object... args) {
        final NativeDate nd = getNativeDate(self);
        setFields(nd, MILLISECOND, args, true);
        return nd.getTime();
    }

    /**
     * ECMA 15.9.5.29 Date.prototype.setUTCMilliseconds (ms)
     *
     * @param self self reference
     * @param args utc milliseconds
     * @return time
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object setUTCMilliseconds(final Object self, final Object... args) {
        final NativeDate nd = getNativeDate(self);
        setFields(nd, MILLISECOND, args, false);
        return nd.getTime();
    }

    /**
     * ECMA 15.9.5.30 Date.prototype.setSeconds (sec [, ms ] )
     *
     * @param self self reference
     * @param args seconds (milliseconds optional second argument)
     * @return time
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object setSeconds(final Object self, final Object... args) {
        final NativeDate nd = getNativeDate(self);
        setFields(nd, SECOND, args, true);
        return nd.getTime();
    }

    /**
     * ECMA 15.9.5.31 Date.prototype.setUTCSeconds (sec [, ms ] )
     *
     * @param self self reference
     * @param args UTC seconds (milliseconds optional second argument)
     * @return time
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object setUTCSeconds(final Object self, final Object... args) {
        final NativeDate nd = getNativeDate(self);
        setFields(nd, SECOND, args, false);
        return nd.getTime();
    }

    /**
     * ECMA 15.9.5.32 Date.prototype.setMinutes (min [, sec [, ms ] ] )
     *
     * @param self self reference
     * @param args minutes (seconds and milliseconds are optional second and third arguments)
     * @return time
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 3)
    public static Object setMinutes(final Object self, final Object... args) {
        final NativeDate nd = getNativeDate(self);
        setFields(nd, MINUTE, args, true);
        return nd.getTime();
    }

    /**
     * ECMA 15.9.5.33 Date.prototype.setUTCMinutes (min [, sec [, ms ] ] )
     *
     * @param self self reference
     * @param args minutes (seconds and milliseconds are optional second and third arguments)
     * @return time
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 3)
    public static Object setUTCMinutes(final Object self, final Object... args) {
        final NativeDate nd = getNativeDate(self);
        setFields(nd, MINUTE, args, false);
        return nd.getTime();
    }

    /**
     * ECMA 15.9.5.34 Date.prototype.setHours (hour [, min [, sec [, ms ] ] ] )
     *
     * @param self self reference
     * @param args hour (optional arguments after are minutes, seconds, milliseconds)
     * @return time
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 4)
    public static Object setHours(final Object self, final Object... args) {
        final NativeDate nd = getNativeDate(self);
        setFields(nd, HOUR, args, true);
        return nd.getTime();
    }

    /**
     * ECMA 15.9.5.35 Date.prototype.setUTCHours (hour [, min [, sec [, ms ] ] ] )
     *
     * @param self self reference
     * @param args hour (optional arguments after are minutes, seconds, milliseconds)
     * @return time
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 4)
    public static Object setUTCHours(final Object self, final Object... args) {
        final NativeDate nd = getNativeDate(self);
        setFields(nd, HOUR, args, false);
        return nd.getTime();
    }

    /**
     * ECMA 15.9.5.36 Date.prototype.setDate (date)
     *
     * @param self self reference
     * @param args date
     * @return time
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object setDate(final Object self, final Object... args) {
        final NativeDate nd = getNativeDate(self);
        setFields(nd, DAY, args, true);
        return nd.getTime();
    }

    /**
     * ECMA 15.9.5.37 Date.prototype.setUTCDate (date)
     *
     * @param self self reference
     * @param args UTC date
     * @return time
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 1)
    public static Object setUTCDate(final Object self, final Object... args) {
        final NativeDate nd = getNativeDate(self);
        setFields(nd, DAY, args, false);
        return nd.getTime();
    }

    /**
     * ECMA 15.9.5.38 Date.prototype.setMonth (month [, date ] )
     *
     * @param self self reference
     * @param args month (optional second argument is date)
     * @return time
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object setMonth(final Object self, final Object... args) {
        final NativeDate nd = getNativeDate(self);
        setFields(nd, MONTH, args, true);
        return nd.getTime();
    }

    /**
     * ECMA 15.9.5.39 Date.prototype.setUTCMonth (month [, date ] )
     *
     * @param self self reference
     * @param args UTC month (optional second argument is date)
     * @return time
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 2)
    public static Object setUTCMonth(final Object self, final Object... args) {
        final NativeDate nd = ensureNativeDate(self);
        setFields(nd, MONTH, args, false);
        return nd.getTime();
    }

    /**
     * ECMA 15.9.5.40 Date.prototype.setFullYear (year [, month [, date ] ] )
     *
     * @param self self reference
     * @param args year (optional second and third arguments are month and date)
     * @return time
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 3)
    public static Object setFullYear(final Object self, final Object... args) {
        final NativeDate nd   = ensureNativeDate(self);
        if (nd.isValidDate()) {
            setFields(nd, YEAR, args, true);
        } else {
            final double[] d = convertArgs(args, 0, YEAR, YEAR, 3);
            if (d != null) {
                nd.setTime(timeClip(utc(makeDate(makeDay(d[0], d[1], d[2]), 0), nd.getTimeZone())));
            } else {
                nd.setTime(NaN);
            }
        }
        return nd.getTime();
    }

    /**
     * ECMA 15.9.5.41 Date.prototype.setUTCFullYear (year [, month [, date ] ] )
     *
     * @param self self reference
     * @param args UTC full year (optional second and third arguments are month and date)
     * @return time
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE, arity = 3)
    public static Object setUTCFullYear(final Object self, final Object... args) {
        final NativeDate nd   = ensureNativeDate(self);
        if (nd.isValidDate()) {
            setFields(nd, YEAR, args, false);
        } else {
            final double[] d = convertArgs(args, 0, YEAR, YEAR, 3);
            nd.setTime(timeClip(makeDate(makeDay(d[0], d[1], d[2]), 0)));
        }
        return nd.getTime();
    }

    /**
     * ECMA B.2.5 Date.prototype.setYear (year)
     *
     * @param self self reference
     * @param year year
     * @return NativeDate
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object setYear(final Object self, final Object year) {
        final NativeDate nd = getNativeDate(self);
        if (isNaN(nd.getTime())) {
            nd.setTime(utc(0, nd.getTimeZone()));
        }

        final double yearNum = JSType.toNumber(year);
        if (isNaN(yearNum)) {
            nd.setTime(NaN);
            return nd.getTime();
        }
        int yearInt = (int)yearNum;
        if (0 <= yearInt && yearInt <= 99) {
            yearInt += 1900;
        }
        setFields(nd, YEAR, new Object[] {yearInt}, true);

        return nd.getTime();
    }

    /**
     * ECMA 15.9.5.42 Date.prototype.toUTCString ( )
     *
     * @param self self reference
     * @return string representation of date
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object toUTCString(final Object self) {
        return toGMTStringImpl(self);
    }

    /**
     * ECMA B.2.6 Date.prototype.toGMTString ( )
     *
     * See {@link NativeDate#toUTCString(Object)}
     *
     * @param self self reference
     * @return string representation of date
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object toGMTString(final Object self) {
        return toGMTStringImpl(self);
    }

    /**
     * ECMA 15.9.5.43 Date.prototype.toISOString ( )
     *
     * @param self self reference
     * @return string representation of date
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object toISOString(final Object self) {
        return toISOStringImpl(self);
    }

    /**
     * ECMA 15.9.5.44 Date.prototype.toJSON ( key )
     *
     * Provides a string representation of this Date for use by {@link NativeJSON#stringify(Object, Object, Object, Object)}
     *
     * @param self self reference
     * @param key ignored
     * @return JSON representation of this date
     */
    @Function(attributes = Attribute.NOT_ENUMERABLE)
    public static Object toJSON(final Object self, final Object key) {
        // NOTE: Date.prototype.toJSON is generic. Accepts other objects as well.
        final Object selfObj = Global.toObject(self);
        if (!(selfObj instanceof ScriptObject)) {
            return null;
        }
        final ScriptObject sobj  = (ScriptObject)selfObj;
        final Object       value = sobj.getDefaultValue(Number.class);
        if (value instanceof Number) {
            final double num = ((Number)value).doubleValue();
            if (isInfinite(num) || isNaN(num)) {
                return null;
            }
        }

        try {
            final InvokeByName toIsoString = getTO_ISO_STRING();
            final Object func = toIsoString.getGetter().invokeExact(sobj);
            if (Bootstrap.isCallable(func)) {
                return toIsoString.getInvoker().invokeExact(func, sobj, key);
            }
            throw typeError("not.a.function", ScriptRuntime.safeToString(func));
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // -- Internals below this point

    private static double parseDateString(final String str) {

        final DateParser parser = new DateParser(str);
        if (parser.parse()) {
            final Integer[] fields = parser.getDateFields();
            double d = makeDate(fields);
            if (fields[DateParser.TIMEZONE] != null) {
                d -= fields[DateParser.TIMEZONE] * 60000;
            } else {
                d = utc(d, Global.getEnv()._timezone);
            }
            d = timeClip(d);
            return d;
        }

        return Double.NaN;
    }

    private static void zeroPad(final StringBuilder sb, final int n, final int length) {
        for (int l = 1, d = 10; l < length; l++, d *= 10) {
            if (n < d) {
                sb.append('0');
            }
        }
        sb.append(n);
    }

    private static String toStringImpl(final Object self, final int format) {
        final NativeDate nd = getNativeDate(self);

        if (nd != null && nd.isValidDate()) {
            final StringBuilder sb = new StringBuilder(40);
            final double t = nd.getLocalTime();

            switch (format) {

                case FORMAT_DATE_TIME:
                case FORMAT_DATE :
                case FORMAT_LOCAL_DATE_TIME:
                    // EEE MMM dd yyyy
                    sb.append(weekDays[weekDay(t)])
                            .append(' ')
                            .append(months[monthFromTime(t)])
                            .append(' ');
                    zeroPad(sb, dayFromTime(t), 2);
                    sb.append(' ');
                    zeroPad(sb, yearFromTime(t), 4);
                    if (format == FORMAT_DATE) {
                        break;
                    }
                    sb.append(' ');

                    //$FALL-THROUGH$
                case FORMAT_TIME:
                    final TimeZone tz = nd.getTimeZone();
                    final double utcTime = nd.getTime();
                    int offset = tz.getOffset((long) utcTime) / 60000;
                    final boolean inDaylightTime = offset != tz.getRawOffset() / 60000;
                    // Convert minutes to HHmm timezone offset
                    offset = (offset / 60) * 100 + offset % 60;

                    // HH:mm:ss GMT+HHmm
                    zeroPad(sb, hourFromTime(t), 2);
                    sb.append(':');
                    zeroPad(sb, minFromTime(t), 2);
                    sb.append(':');
                    zeroPad(sb, secFromTime(t), 2);
                    sb.append(" GMT")
                            .append(offset < 0 ? '-' : '+');
                    zeroPad(sb, Math.abs(offset), 4);
                    sb.append(" (")
                            .append(tz.getDisplayName(inDaylightTime, TimeZone.SHORT, Locale.US))
                            .append(')');
                    break;

                case FORMAT_LOCAL_DATE:
                    // yyyy-MM-dd
                    zeroPad(sb, yearFromTime(t), 4);
                    sb.append('-');
                    zeroPad(sb, monthFromTime(t) + 1, 2);
                    sb.append('-');
                    zeroPad(sb, dayFromTime(t), 2);
                    break;

                case FORMAT_LOCAL_TIME:
                    // HH:mm:ss
                    zeroPad(sb, hourFromTime(t), 2);
                    sb.append(':');
                    zeroPad(sb, minFromTime(t), 2);
                    sb.append(':');
                    zeroPad(sb, secFromTime(t), 2);
                    break;

                default:
                    throw new IllegalArgumentException("format: " + format);
            }

            return sb.toString();
        }

        return INVALID_DATE;
    }

    private static String toGMTStringImpl(final Object self) {
        final NativeDate nd = getNativeDate(self);

        if (nd != null && nd.isValidDate()) {
            final StringBuilder sb = new StringBuilder(29);
            final double t = nd.getTime();
            // EEE, dd MMM yyyy HH:mm:ss z
            sb.append(weekDays[weekDay(t)])
                    .append(", ");
            zeroPad(sb, dayFromTime(t), 2);
            sb.append(' ')
                    .append(months[monthFromTime(t)])
                    .append(' ');
            zeroPad(sb, yearFromTime(t), 4);
            sb.append(' ');
            zeroPad(sb, hourFromTime(t), 2);
            sb.append(':');
            zeroPad(sb, minFromTime(t), 2);
            sb.append(':');
            zeroPad(sb, secFromTime(t), 2);
            sb.append(" GMT");
            return sb.toString();
        }

        throw rangeError("invalid.date");
    }

    private static String toISOStringImpl(final Object self) {
        final NativeDate nd = getNativeDate(self);

        if (nd != null && nd.isValidDate()) {
            final StringBuilder sb = new StringBuilder(24);
            final double t = nd.getTime();
            // yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
            zeroPad(sb, yearFromTime(t), 4);
            sb.append('-');
            zeroPad(sb, monthFromTime(t) + 1, 2);
            sb.append('-');
            zeroPad(sb, dayFromTime(t), 2);
            sb.append('T');
            zeroPad(sb, hourFromTime(t), 2);
            sb.append(':');
            zeroPad(sb, minFromTime(t), 2);
            sb.append(':');
            zeroPad(sb, secFromTime(t), 2);
            sb.append('.');
            zeroPad(sb, msFromTime(t), 3);
            sb.append("Z");
            return sb.toString();
        }

        throw rangeError("invalid.date");
    }

    // ECMA 15.9.1.2 Day (t)
    private static double day(final double t) {
        return Math.floor(t / msPerDay);
    }

    // ECMA 15.9.1.2 TimeWithinDay (t)
    private static double timeWithinDay(final double t) {
        return t % msPerDay;
    }

    // ECMA 15.9.1.3 InLeapYear (t)
    private static boolean isLeapYear(final int y) {
        return y % 4 == 0 && (y % 100 != 0 || y % 400 == 0);
    }

    // ECMA 15.9.1.3 DaysInYear (y)
    private static int daysInYear(final int y) {
        return isLeapYear(y) ? 366 : 365;
    }

    // ECMA 15.9.1.3 DayFromYear (y)
    private static double dayFromYear(final double y) {
        return 365 * (y - 1970)
                + Math.floor((y -1969) / 4.0)
                - Math.floor((y - 1901) / 100.0)
                + Math.floor((y - 1601) / 400.0);
    }

    // ECMA 15.9.1.3 Year Number
    private static double timeFromYear(final int y) {
        return dayFromYear(y) * msPerDay;
    }

    // ECMA 15.9.1.3 Year Number
    private static int yearFromTime(final double t) {
        int y = (int) Math.floor(t / (msPerDay * 365.2425)) + 1970;
        final double t2 = timeFromYear(y);
        if (t2 > t) {
            y--;
        } else if (t2 + msPerDay * daysInYear(y) <= t) {
            y++;
        }
        return y;
    }

    private static int dayWithinYear(final double t, final int year) {
        return (int) (day(t) - dayFromYear(year));
    }

    private static int monthFromTime(final double t) {
        final int year = yearFromTime(t);
        final int day = dayWithinYear(t, year);
        final int[] firstDay = firstDayInMonth[isLeapYear(year) ? 1 : 0];
        int month = 0;

        while (month < 11 && firstDay[month + 1] <= day) {
            month++;
        }
        return month;
    }

    private static int dayFromTime(final double t)  {
        final int year = yearFromTime(t);
        final int day = dayWithinYear(t, year);
        final int[] firstDay = firstDayInMonth[isLeapYear(year) ? 1 : 0];
        int month = 0;

        while (month < 11 && firstDay[month + 1] <= day) {
            month++;
        }
        return 1 + day - firstDay[month];
    }

    private static int dayFromMonth(final int month, final int year) {
        assert(month >= 0 && month <= 11);
        final int[] firstDay = firstDayInMonth[isLeapYear(year) ? 1 : 0];
        return firstDay[month];
    }

    private static int weekDay(final double time) {
        final int day = (int) (day(time) + 4) % 7;
        return day < 0 ? day + 7 : day;
    }

    // ECMA 15.9.1.9 LocalTime
    private static double localTime(final double time, final TimeZone tz) {
        return time + tz.getOffset((long) time);
    }

    // ECMA 15.9.1.9 UTC
    private static double utc(final double time, final TimeZone tz) {
        return time - tz.getOffset((long) (time - tz.getRawOffset()));
    }

    // ECMA 15.9.1.10 Hours, Minutes, Second, and Milliseconds
    private static int hourFromTime(final double t) {
        final int h = (int) (Math.floor(t / msPerHour) % hoursPerDay);
        return h < 0 ? h + hoursPerDay: h;
    }
    private static int minFromTime(final double t) {
        final int m = (int) (Math.floor(t / msPerMinute) % minutesPerHour);
        return m < 0 ? m + minutesPerHour : m;
    }

    private static int secFromTime(final double t) {
        final int s = (int) (Math.floor(t / msPerSecond) % secondsPerMinute);
        return s < 0 ? s + secondsPerMinute : s;
    }

    private static int msFromTime(final double t) {
        final int m = (int) (t % msPerSecond);
        return m < 0 ? m + msPerSecond : m;
    }

    private static int valueFromTime(final int unit, final double t) {
        switch (unit) {
            case YEAR: return yearFromTime(t);
            case MONTH: return monthFromTime(t);
            case DAY: return dayFromTime(t);
            case HOUR: return hourFromTime(t);
            case MINUTE: return minFromTime(t);
            case SECOND: return secFromTime(t);
            case MILLISECOND: return msFromTime(t);
            default: throw new IllegalArgumentException(Integer.toString(unit));
        }
    }

    // ECMA 15.9.1.11 MakeTime (hour, min, sec, ms)
    private static double makeTime(final double hour, final double min, final double sec, final double ms) {
        return hour * 3600000 + min * 60000 + sec * 1000 + ms;
    }

    // ECMA 15.9.1.12 MakeDay (year, month, date)
    private static double makeDay(final double year, final double month, final double date) {
        final double y = year + Math.floor(month / 12);
        int m = (int) (month % 12);
        if (m < 0) {
            m += 12;
        }
        double d = dayFromYear(y);
        d += dayFromMonth(m, (int) y);

        return d + date - 1;
    }

    // ECMA 15.9.1.13 MakeDate (day, time)
    private static double makeDate(final double day, final double time) {
        return day * msPerDay + time;
    }


    private static double makeDate(final Integer[] d) {
        final double time = makeDay(d[0], d[1], d[2]) * msPerDay;
        return time + makeTime(d[3], d[4], d[5], d[6]);
    }

    private static double makeDate(final double[] d) {
        final double time = makeDay(d[0], d[1], d[2]) * msPerDay;
        return time + makeTime(d[3], d[4], d[5], d[6]);
    }

    // Convert Date constructor args, checking for NaN, filling in defaults etc.
    private static double[] convertCtorArgs(final Object[] args) {
        final double[] d = new double[7];
        boolean nullReturn = false;

        // should not bailout on first NaN or infinite. Need to convert all
        // subsequent args for possible side-effects via valueOf/toString overrides
        // on argument objects.
        for (int i = 0; i < d.length; i++) {
            if (i < args.length) {
                final double darg = JSType.toNumber(args[i]);
                if (isNaN(darg) || isInfinite(darg)) {
                    nullReturn = true;
                }

                d[i] = (long)darg;
            } else {
                d[i] = i == 2 ? 1 : 0; // day in month defaults to 1
            }
        }

        if (0 <= d[0] && d[0] <= 99) {
            d[0] += 1900;
        }

        return nullReturn? null : d;
    }

    // This method does the hard work for all setter methods: If a value is provided
    // as argument it is used, otherwise the value is calculated from the existing time value.
    private static double[] convertArgs(final Object[] args, final double time, final int fieldId, final int start, final int length) {
        final double[] d = new double[length];
        boolean nullReturn = false;

        // Need to call toNumber on all args for side-effects - even if an argument
        // fails to convert to number, subsequent toNumber calls needed for possible
        // side-effects via valueOf/toString overrides.
        for (int i = start; i < start + length; i++) {
            if (fieldId <= i && i < fieldId + args.length) {
                final double darg = JSType.toNumber(args[i - fieldId]);
                if (isNaN(darg) || isInfinite(darg)) {
                    nullReturn = true;
                }

                d[i - start] = (long) darg;
            } else {
                // Date.prototype.set* methods require first argument to be defined
                if (i == fieldId) {
                    nullReturn = true;
                }

                if (!nullReturn && !isNaN(time)) {
                    d[i - start] = valueFromTime(i, time);
                }
            }
        }

        return nullReturn ? null : d;
    }

    // ECMA 15.9.1.14 TimeClip (time)
    private static double timeClip(final double time) {
        if (isInfinite(time) || isNaN(time) || Math.abs(time) > 8.64e15) {
            return Double.NaN;
        }
        return (long)time;
    }

    private static NativeDate ensureNativeDate(final Object self) {
        return getNativeDate(self);
    }

    private static NativeDate getNativeDate(final Object self) {
        if (self instanceof NativeDate) {
            return (NativeDate)self;
        } else if (self != null && self == Global.instance().getDatePrototype()) {
            return Global.instance().DEFAULT_DATE;
        } else {
            throw typeError("not.a.date", ScriptRuntime.safeToString(self));
        }
    }

    private static Object getField(final Object self, final int field) {
        final NativeDate nd = getNativeDate(self);
        return (nd != null && nd.isValidDate()) ? valueFromTime(field, nd.getLocalTime()) : Double.NaN;
    }

    private static Object getUTCField(final Object self, final int field) {
        final NativeDate nd = getNativeDate(self);
        return (nd != null && nd.isValidDate()) ? valueFromTime(field, nd.getTime()) : Double.NaN;
    }

    private static void setFields(final NativeDate nd, final int fieldId, final Object[] args, final boolean local) {
        int start, length;
        if (fieldId < HOUR) {
            start = YEAR;
            length = 3;
        } else {
            start = HOUR;
            length = 4;
        }
        final double time = local ? nd.getLocalTime() : nd.getTime();
        final double d[] = convertArgs(args, time, fieldId, start, length);

        if (! nd.isValidDate()) {
            return;
        }

        double newTime;
        if (d == null) {
            newTime = NaN;
        } else {
            if (start == YEAR) {
                newTime = makeDate(makeDay(d[0], d[1], d[2]), timeWithinDay(time));
            } else {
                newTime = makeDate(day(time), makeTime(d[0], d[1], d[2], d[3]));
            }
            if (local) {
                newTime = utc(newTime, nd.getTimeZone());
            }
            newTime = timeClip(newTime);
        }
        nd.setTime(newTime);
    }

    private boolean isValidDate() {
        return !isNaN(time);
    }

    private double getLocalTime() {
        return localTime(time, timezone);
    }

    private double getTime() {
        return time;
    }

    private void setTime(final double time) {
        this.time = time;
    }

    private TimeZone getTimeZone() {
        return timezone;
    }

}
