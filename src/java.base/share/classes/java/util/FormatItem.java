/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.util;

import static java.util.Formatter.DateTime.*;
import java.io.IOException;
import java.lang.invoke.*;
import java.lang.invoke.MethodHandles.Lookup;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.text.DecimalFormatSymbols;
import java.util.Formatter.FormatSpecifier;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.JavaUtilDateAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.util.FormatConcatItem;
import jdk.internal.util.DateTimeUtils;
import jdk.internal.util.DecimalDigits;
import jdk.internal.util.HexDigits;
import jdk.internal.util.OctalDigits;

import sun.util.calendar.BaseCalendar;

import static java.lang.invoke.MethodType.methodType;

/**
 * A specialized objects used by FormatterBuilder that knows how to insert
 * themselves into a concatenation performed by StringConcatFactory.
 *
 * @since 21
 *
 * Warning: This class is part of PreviewFeature.Feature.STRING_TEMPLATES.
 *          Do not rely on its availability.
 */
class FormatItem {
    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();
    private static final JavaUtilDateAccess JDA = SharedSecrets.getJavaUtilDateAccess();

    private static final MethodHandle CHAR_MIX =
            JLA.stringConcatHelper("mix",
                    MethodType.methodType(long.class, long.class,char.class));

    private static final MethodHandle STRING_PREPEND =
            JLA.stringConcatHelper("prepend",
                    MethodType.methodType(long.class, long.class, byte[].class,
                            String.class));

    private static final MethodHandle SELECT_GETCHAR_MH =
            JLA.stringConcatHelper("selectGetChar",
                    MethodType.methodType(MethodHandle.class, long.class));

    private static final MethodHandle SELECT_PUTCHAR_MH =
            JLA.stringConcatHelper("selectPutChar",
                    MethodType.methodType(MethodHandle.class, long.class));

    private static long charMix(long lengthCoder, char value) {
        try {
            return (long)CHAR_MIX.invokeExact(lengthCoder, value);
        } catch (Error | RuntimeException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }

    private static long stringMix(long lengthCoder, String value) {
        return JLA.stringConcatMix(lengthCoder, value);
    }

    private static long stringPrepend(long lengthCoder, byte[] buffer,
                                            String value) throws Throwable {
        return (long)STRING_PREPEND.invokeExact(lengthCoder, buffer, value);
    }

    private static MethodHandle selectGetChar(long indexCoder) throws Throwable {
        return (MethodHandle)SELECT_GETCHAR_MH.invokeExact(indexCoder);
    }

    private static MethodHandle selectPutChar(long indexCoder) throws Throwable {
        return (MethodHandle)SELECT_PUTCHAR_MH.invokeExact(indexCoder);
    }

    private static final MethodHandle PUT_CHAR_DIGIT;

    static {
        try {
            Lookup lookup = MethodHandles.lookup();
            PUT_CHAR_DIGIT = lookup.findStatic(FormatItem.class, "putByte",
                    MethodType.methodType(void.class,
                            byte[].class, int.class, int.class));
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("putByte lookup failed", ex);
        }
    }

    private static void putByte(byte[] buffer, int index, int ch) {
        buffer[index] = (byte)ch;
    }

    private static boolean isLatin1(long lengthCoder) {
        return JLA.stringConcatHelpeIsLatin1(lengthCoder);
    }

    private FormatItem() {
        throw new AssertionError("private constructor");
    }

    /**
     * Decimal value format item.
     */
    static final class FormatItemDecimal implements FormatConcatItem {
        private final char groupingSeparator;
        private final char zeroDigit;
        private final char minusSign;
        private final int digitOffset;
        private final byte[] digits;
        private final int length;
        private final boolean isNegative;
        private final int width;
        private final byte prefixSign;
        private final int groupSize;
        private final long value;
        private final boolean parentheses;

        FormatItemDecimal(DecimalFormatSymbols dfs, int width, char sign,
                          boolean parentheses, int groupSize, long value) throws Throwable {
            this.groupingSeparator = dfs.getGroupingSeparator();
            this.zeroDigit = dfs.getZeroDigit();
            this.minusSign = dfs.getMinusSign();
            this.digitOffset = this.zeroDigit - '0';
            int length = DecimalDigits.INSTANCE.size(value);
            this.digits = new byte[length];
            DecimalDigits.INSTANCE.digits(value, this.digits, length, PUT_CHAR_DIGIT);
            this.isNegative = value < 0L;
            this.length = this.isNegative ? length - 1 : length;
            this.width = width;
            this.groupSize = groupSize;
            this.value = value;
            this.parentheses = parentheses && isNegative;
            this.prefixSign = (byte)(isNegative ? (parentheses ? '\0' : minusSign) : sign);
        }

        private int signLength() {
            return (prefixSign != '\0' ? 1 : 0) + (parentheses ? 2 : 0);
        }

        private int groupLength() {
            return 0 < groupSize ? (length - 1) / groupSize : 0;
        }

        @Override
        public long mix(long lengthCoder) {
            return JLA.stringConcatCoder(zeroDigit) |
                    (lengthCoder +
                     Integer.max(length + signLength() + groupLength(), width));
        }

        @Override
        public long prepend(long lengthCoder, byte[] buffer) throws Throwable {
            MethodHandle putCharMH = selectPutChar(lengthCoder);

            if (parentheses) {
                putCharMH.invokeExact(buffer, (int)--lengthCoder, (int)')');
            }

            if (0 < groupSize) {
                int groupIndex = groupSize;

                for (int i = 1; i <= length; i++) {
                    if (groupIndex-- == 0) {
                        putCharMH.invokeExact(buffer, (int)--lengthCoder,
                                (int)groupingSeparator);
                        groupIndex = groupSize - 1;
                    }

                    putCharMH.invokeExact(buffer, (int)--lengthCoder,
                            digits[digits.length - i] + digitOffset);
                }
            } else {
                for (int i = 1; i <= length; i++) {
                    putCharMH.invokeExact(buffer, (int)--lengthCoder,
                            digits[digits.length - i] + digitOffset);
                }
            }

            for (int i = length + signLength() + groupLength(); i < width; i++) {
                putCharMH.invokeExact(buffer, (int)--lengthCoder, (int)'0');
            }

            if (parentheses) {
                putCharMH.invokeExact(buffer, (int)--lengthCoder, (int)'(');
            }
            if (prefixSign != '\0') {
                putCharMH.invokeExact(buffer, (int)--lengthCoder, (int)prefixSign);
            }

            return lengthCoder;
        }
    }

    /**
     * Hexadecimal format item.
     */
    static final class FormatItemHexadecimal implements FormatConcatItem {
        private final int width;
        private final boolean hasPrefix;
        private final long value;
        private final int length;

        FormatItemHexadecimal(int width, boolean hasPrefix, long value) {
            this.width = width;
            this.hasPrefix = hasPrefix;
            this.value = value;
            this.length = HexDigits.INSTANCE.size(value);
        }

        private int prefixLength() {
            return hasPrefix ? 2 : 0;
        }

        private int zeroesLength() {
            return Integer.max(0, width - length - prefixLength());
        }

        @Override
        public long mix(long lengthCoder) {
            return lengthCoder + length + prefixLength() + zeroesLength();
        }

        @Override
        public long prepend(long lengthCoder, byte[] buffer) throws Throwable {
            MethodHandle putCharMH = selectPutChar(lengthCoder);
            HexDigits.INSTANCE.digits(value, buffer, (int)lengthCoder, putCharMH);
            lengthCoder -= length;

            for (int i = 0; i < zeroesLength(); i++) {
                putCharMH.invokeExact(buffer, (int)--lengthCoder, (int)'0');
            }

            if (hasPrefix) {
                putCharMH.invokeExact(buffer, (int)--lengthCoder, (int)'x');
                putCharMH.invokeExact(buffer, (int)--lengthCoder, (int)'0');
            }

            return lengthCoder;
        }
    }

    /**
     * Hexadecimal format item.
     */
    static final class FormatItemOctal implements FormatConcatItem {
        private final int width;
        private final boolean hasPrefix;
        private final long value;
        private final int length;

        FormatItemOctal(int width, boolean hasPrefix, long value) {
            this.width = width;
            this.hasPrefix = hasPrefix;
            this.value = value;
            this.length = OctalDigits.INSTANCE.size(value);
        }

        private int prefixLength() {
            return hasPrefix && value != 0 ? 1 : 0;
        }

        private int zeroesLength() {
            return Integer.max(0, width - length - prefixLength());
        }

        @Override
        public long mix(long lengthCoder) {
            return lengthCoder + length + prefixLength() + zeroesLength();
        }

        @Override
        public long prepend(long lengthCoder, byte[] buffer) throws Throwable {
            MethodHandle putCharMH = selectPutChar(lengthCoder);
            OctalDigits.INSTANCE.digits(value, buffer, (int)lengthCoder, putCharMH);
            lengthCoder -= length;

            for (int i = 0; i < zeroesLength(); i++) {
                putCharMH.invokeExact(buffer, (int)--lengthCoder, (int)'0');
            }

            if (hasPrefix && value != 0) {
                putCharMH.invokeExact(buffer, (int)--lengthCoder, (int)'0');
            }

            return lengthCoder;
        }
    }

    /**
     * Boolean format item.
     */
    static final class FormatItemBoolean implements FormatConcatItem {
        private final boolean value;

        FormatItemBoolean(boolean value) {
            this.value = value;
        }

        @Override
        public long mix(long lengthCoder) {
            return lengthCoder + (value ? "true".length() : "false".length());
        }

        @Override
        public long prepend(long lengthCoder, byte[] buffer) throws Throwable {
            MethodHandle putCharMH = selectPutChar(lengthCoder);

            if (value) {
                putCharMH.invokeExact(buffer, (int)--lengthCoder, (int)'e');
                putCharMH.invokeExact(buffer, (int)--lengthCoder, (int)'u');
                putCharMH.invokeExact(buffer, (int)--lengthCoder, (int)'r');
                putCharMH.invokeExact(buffer, (int)--lengthCoder, (int)'t');
            } else {
                putCharMH.invokeExact(buffer, (int)--lengthCoder, (int)'e');
                putCharMH.invokeExact(buffer, (int)--lengthCoder, (int)'s');
                putCharMH.invokeExact(buffer, (int)--lengthCoder, (int)'l');
                putCharMH.invokeExact(buffer, (int)--lengthCoder, (int)'a');
                putCharMH.invokeExact(buffer, (int)--lengthCoder, (int)'f');
            }

            return lengthCoder;
         }
    }

    /**
     * Character format item.
     */
    static final class FormatItemCharacter implements FormatConcatItem {
        private final char value;

        FormatItemCharacter(char value) {
            this.value = value;
        }

        @Override
        public long mix(long lengthCoder) {
            return charMix(lengthCoder, value);
        }

        @Override
        public long prepend(long lengthCoder, byte[] buffer) throws Throwable {
            MethodHandle putCharMH = selectPutChar(lengthCoder);
            putCharMH.invokeExact(buffer, (int)--lengthCoder, (int)value);

            return lengthCoder;
        }
    }

    /**
     * String format item.
     */
    static final class FormatItemString implements FormatConcatItem {
        private String value;

        FormatItemString(String value) {
            this.value = value;
        }

        @Override
        public long mix(long lengthCoder) {
            return stringMix(lengthCoder, value);
        }

        @Override
        public long prepend(long lengthCoder, byte[] buffer) throws Throwable {
            return stringPrepend(lengthCoder, buffer, value);
        }
    }

    /**
     * FormatSpecifier format item.
     */
    static final class FormatItemFormatSpecifier implements FormatConcatItem {
        private StringBuilder sb;

        FormatItemFormatSpecifier(FormatSpecifier fs, Locale locale, Object value) {
            this.sb = new StringBuilder(64);
            Formatter formatter = new Formatter(this.sb, locale);

            try {
                fs.print(formatter, value, locale);
            } catch (IOException ex) {
                throw new AssertionError("FormatItemFormatSpecifier IOException", ex);
            }
        }

        FormatItemFormatSpecifier(Locale locale,
                                  int flags, int width, int precision,
                                  Formattable formattable) {
            this.sb = new StringBuilder(64);
            Formatter formatter = new Formatter(this.sb, locale);
            formattable.formatTo(formatter, flags, width, precision);
        }

        @Override
        public long mix(long lengthCoder) {
            return JLA.stringBuilderConcatMix(lengthCoder, sb);
        }

        @Override
        public long prepend(long lengthCoder, byte[] buffer) throws Throwable {
            return JLA.stringBuilderConcatPrepend(lengthCoder, buffer, sb);
        }
    }

    abstract static sealed class FormatItemModifier implements FormatConcatItem
        permits FormatItemFillLeft,
                FormatItemFillRight
    {
        private final long itemLengthCoder;
        protected final FormatConcatItem item;

        FormatItemModifier(FormatConcatItem item) {
            this.itemLengthCoder = item.mix(0L);
            this.item = item;
        }

        int length() {
            return (int)itemLengthCoder;
        }

        long coder() {
            return itemLengthCoder & ~Integer.MAX_VALUE;
        }

        @Override
        public abstract long mix(long lengthCoder);

        @Override
        public abstract long prepend(long lengthCoder, byte[] buffer) throws Throwable;
    }

    /**
     * Fill left format item.
     */
    static final class FormatItemFillLeft extends FormatItemModifier
            implements FormatConcatItem {
        private final int width;

        FormatItemFillLeft(int width, FormatConcatItem item) {
            super(item);
            this.width = Integer.max(length(), width);
        }

        @Override
        public long mix(long lengthCoder) {
            return (lengthCoder | coder()) + width;
        }

        @Override
        public long prepend(long lengthCoder, byte[] buffer) throws Throwable {
            MethodHandle putCharMH = selectPutChar(lengthCoder);
            lengthCoder = item.prepend(lengthCoder, buffer);

            for (int i = length(); i < width; i++) {
                putCharMH.invokeExact(buffer, (int)--lengthCoder, (int)' ');
            }

            return lengthCoder;
        }
    }

    /**
     * Fill right format item.
     */
    static final class FormatItemFillRight extends FormatItemModifier
            implements FormatConcatItem {
        private final int width;

        FormatItemFillRight(int width, FormatConcatItem item) {
            super(item);
            this.width = Integer.max(length(), width);
        }

        @Override
        public long mix(long lengthCoder) {
            return (lengthCoder | coder()) + width;
        }

        @Override
        public long prepend(long lengthCoder, byte[] buffer) throws Throwable {
            MethodHandle putCharMH = selectPutChar(lengthCoder);

            for (int i = length(); i < width; i++) {
                putCharMH.invokeExact(buffer, (int)--lengthCoder, (int)' ');
            }

            lengthCoder = item.prepend(lengthCoder, buffer);

            return lengthCoder;
        }
    }


    /**
     * Null format item.
     */
    static final class FormatItemNull implements FormatConcatItem {
        FormatItemNull() {
        }

        @Override
        public long mix(long lengthCoder) {
            return lengthCoder + "null".length();
        }

        @Override
        public long prepend(long lengthCoder, byte[] buffer) throws Throwable {
            MethodHandle putCharMH = selectPutChar(lengthCoder);

            putCharMH.invokeExact(buffer, (int)--lengthCoder, (int)'l');
            putCharMH.invokeExact(buffer, (int)--lengthCoder, (int)'l');
            putCharMH.invokeExact(buffer, (int)--lengthCoder, (int)'u');
            putCharMH.invokeExact(buffer, (int)--lengthCoder, (int)'n');

            return lengthCoder;
        }
    }

    static final class FormatItemLocalDate implements FormatConcatItem {
        final char c;
        final LocalDate value;
        FormatItemLocalDate(char c, LocalDate value) {
            this.c = c;
            this.value = value;
        }

        @Override
        public long mix(long lengthCoder) {
            int size = switch (c) {
                case DATE -> 8;
                case ISO_STANDARD_DATE -> DateTimeUtils.yearSize(value.getYear()) + 6;
                default -> throw new UnknownFormatConversionException("Unsupported field: " + c);
            };
            return lengthCoder + size;
        }

        @Override
        public long prepend(long lengthCoder, byte[] buffer) {
            boolean latin1 = isLatin1(lengthCoder);
            switch (c) {
                case DATE: {
                    lengthCoder -= 8;
                    int off = (int) lengthCoder;
                    int year = value.getYear(), month = value.getMonthValue(), dayOfMonth = value.getDayOfMonth();
                    if (latin1) {
                        getDateCharsLatin1(buffer, off, year, month, dayOfMonth);
                    } else {
                        getDateCharsUTF16(buffer, off, year, month, dayOfMonth);
                    }
                    break;
                }
                case ISO_STANDARD_DATE: {
                    lengthCoder -= DateTimeUtils.yearSize(value.getYear()) + 6;
                    int off = (int) lengthCoder;
                    if (latin1) {
                        DateTimeUtils.getCharsLatin1(buffer, off, value);
                    } else {
                        DateTimeUtils.getCharsUTF16(buffer, off, value);
                    }
                    break;
                }
                default:
                    throw new UnknownFormatConversionException("Unsupported field: " + c);
            }
            return lengthCoder;
        }
    }

    static final class FormatItemLocalTime implements FormatConcatItem {
        final char c;
        final LocalTime value;

        FormatItemLocalTime(char c, LocalTime value) {
            this.c = c;
            this.value = value;
        }

        @Override
        public long mix(long lengthCoder) {
            int size = switch (c) {
                case TIME         -> 8;
                case TIME_12_HOUR -> 11;
                case TIME_24_HOUR -> 5;
                default -> throw new UnknownFormatConversionException("Unsupported field: " + c);
            };
            return lengthCoder + size;
        }

        @Override
        public long prepend(long lengthCoder, byte[] buffer) {
            boolean latin1 = isLatin1(lengthCoder);
            return switch (c) {
                case TIME         -> prependTime(lengthCoder, buffer, latin1, value);
                case TIME_12_HOUR -> prependTime12Hour(lengthCoder, buffer, latin1, value);
                case TIME_24_HOUR -> prependTime24Hour(lengthCoder, buffer, latin1, value);
                default -> throw new UnknownFormatConversionException("Unsupported field: " + c);
            };
        }

        static long prependTime(long lengthCoder, byte[] buffer, boolean latin1, LocalTime value) {
            lengthCoder -= 8;
            int off = (int) lengthCoder;
            int hour   = value.getHour(),
                minute = value.getMinute(),
                second = value.getSecond();
            if (latin1) {
                DateTimeUtils.getLocalTimeCharsLatin1(buffer, off, hour, minute, second);
            } else {
                DateTimeUtils.getLocalTimeCharsUTF16(buffer, off, hour, minute, second);
            }
            return lengthCoder;
        }

        static long prependTime12Hour(long lengthCoder, byte[] buffer, boolean latin1, LocalTime value) {
            lengthCoder -= 11;
            int off = (int) lengthCoder;
            int hour   = value.getHour(),
                minute = value.getMinute(),
                second = value.getSecond();
            if (latin1) {
                getTime12HourCharsLatin1(buffer, off, hour, minute, second);
            } else {
                getTime12HourCharsUTF16(buffer, off, hour, minute, second);
            }
            return lengthCoder;
        }

        static long prependTime24Hour(long lengthCoder, byte[] buffer, boolean latin1, LocalTime value) {
            lengthCoder -= 5;
            int off = (int) lengthCoder;
            int hour   = value.getHour(),
                minute = value.getMinute();
            if (latin1) {
                getTime24HourCharsLatin1(buffer, off, hour, minute);
            } else {
                getTime24HourCharsUTF16(buffer, off, hour, minute);
            }
            return lengthCoder;
        }
    }

    static final class FormatItemDate implements FormatConcatItem {
        final char c;
        final Date value;

        FormatItemDate(char c, Date value) {
            this.c = c;
            this.value = value;
        }

        @Override
        public long mix(long lengthCoder) {
            int size = switch (c) {
                case TIME_12_HOUR      -> 11;
                case TIME_24_HOUR      -> 5;
                case TIME, DATE        -> 8;
                case DATE_TIME         -> DateTimeUtils.stringSize(value);
                case ISO_STANDARD_DATE -> isoStandardDateSize(JDA.normalize(value));
                default -> throw new UnknownFormatConversionException("Unsupported field: " + c);
            };
            return lengthCoder + size;
        }

        @Override
        public long prepend(long lengthCoder, byte[] buffer) {
            boolean latin1 = isLatin1(lengthCoder);
            BaseCalendar.Date date = JDA.normalize(value);
            return switch (c) {
                case TIME              -> prependTime(lengthCoder, buffer, latin1, date);
                case TIME_12_HOUR      -> prependTime12Hour(lengthCoder, buffer, latin1, date);
                case TIME_24_HOUR      -> prependTime24Hour(lengthCoder, buffer, latin1, date);
                case DATE              -> prependDate(lengthCoder, buffer, latin1, date);
                case DATE_TIME         -> prependDateTime(lengthCoder, buffer, latin1, date);
                case ISO_STANDARD_DATE -> prependISODate(lengthCoder, buffer, latin1, date);
                default -> throw new UnknownFormatConversionException("Unsupported field: " + c);
            };
        }

        static int isoStandardDateSize(BaseCalendar.Date value) {
            return Math.max(4, JLA.stringSize(value.getYear())) + 6;
        }

        static long prependDate(long lengthCoder, byte[] buffer, boolean latin1, BaseCalendar.Date date) {
            lengthCoder -= 8;
            int off = (int) lengthCoder;
            int year       = date.getYear(),
                month      = date.getMonth(),
                dayOfMonth = date.getDayOfMonth();
            if (latin1) {
                getDateCharsLatin1(buffer, off, year, month, dayOfMonth);
            } else {
                getDateCharsUTF16(buffer, off, year, month, dayOfMonth);
            }
            return lengthCoder;
        }

        static long prependISODate(long lengthCoder, byte[] buffer, boolean latin1, BaseCalendar.Date date) {
            lengthCoder -= isoStandardDateSize(date);
            int off = (int) lengthCoder;
            if (latin1) {
                DateTimeUtils.getDateCharsLatin1(buffer, off, date);
            } else {
                DateTimeUtils.getDateCharsUTF16(buffer, off, date);
            }
            return lengthCoder;
        }

        static long prependTime(long lengthCoder, byte[] buffer, boolean latin1, BaseCalendar.Date date) {
            lengthCoder -= 8;
            int off = (int) lengthCoder;
            int hour   = date.getHours(),
                minute = date.getMinutes(),
                second = date.getSeconds();
            if (latin1) {
                DateTimeUtils.getLocalTimeCharsLatin1(buffer, off, hour, minute, second);
            } else {
                DateTimeUtils.getLocalTimeCharsUTF16(buffer, off, hour, minute, second);
            }
            return lengthCoder;
        }

        static long prependTime12Hour(long lengthCoder, byte[] buffer, boolean latin1, BaseCalendar.Date date) {
            lengthCoder -= 11;
            int off = (int) lengthCoder;
            int hour   = date.getHours(),
                minute = date.getMinutes(),
                second = date.getSeconds();
            if (latin1) {
                getTime12HourCharsLatin1(buffer, off, hour, minute, second);
            } else {
                getTime12HourCharsUTF16(buffer, off, hour, minute, second);
            }
            return lengthCoder;
        }

        static long prependTime24Hour(long lengthCoder, byte[] buffer, boolean latin1, BaseCalendar.Date date) {
            lengthCoder -= 5;
            int off = (int) lengthCoder;
            int hour   = date.getHours(),
                minute = date.getMinutes();
            if (latin1) {
                getTime24HourCharsLatin1(buffer, off, hour, minute);
            } else {
                getTime24HourCharsUTF16(buffer, off, hour, minute);
            }
            return lengthCoder;
        }

        static long prependDateTime(long lengthCoder, byte[] buffer, boolean latin1, BaseCalendar.Date date) {
            lengthCoder -= DateTimeUtils.stringSize(date);
            int off = (int) lengthCoder;
            if (latin1) {
                DateTimeUtils.getCharsLatin1(buffer, off, date);
            } else {
                DateTimeUtils.getCharsUTF16(buffer, off, date);
            }
            return lengthCoder;
        }
    }

    private static void getDateCharsLatin1(byte[] buffer, int off, int year, int month, int dayOfMonth) {
        JLA.writeDigitPairLatin1(buffer, off, month);
        buffer[off + 2] = '/';
        JLA.writeDigitPairLatin1(buffer, off + 3, dayOfMonth);
        buffer[off + 5] = '/';
        JLA.writeDigitPairLatin1(buffer, off + 6, year % 100);
    }

    private static void getDateCharsUTF16(byte[] buffer, int off, int year, int month, int dayOfMonth) {
        JLA.writeDigitPairUTF16(buffer, off, month);
        JLA.putCharUTF16(buffer, off + 2, '/');
        JLA.writeDigitPairUTF16(buffer, off + 3, dayOfMonth);
        JLA.putCharUTF16(buffer, off + 5, '/');
        JLA.writeDigitPairUTF16(buffer, off + 6, year % 100);
    }

    private static void getTime24HourCharsLatin1(byte[] buffer, int off, int hour, int minute) {
        JLA.writeDigitPairLatin1(buffer, off, hour);
        buffer[off + 2] = ':';
        JLA.writeDigitPairLatin1(buffer, off + 3, minute);
    }

    private static void getTime24HourCharsUTF16(byte[] buffer, int off, int hour, int minute) {
        JLA.writeDigitPairUTF16(buffer, off, hour);
        JLA.putCharUTF16(buffer, off + 2, ':');
        JLA.writeDigitPairUTF16(buffer, off + 3, minute);
    }

    private static void getTime12HourCharsLatin1(byte[] buffer, int off, int hour, int minute, int second) {
        int h12 = hour == 0 ? 12
                : hour > 12 ? hour - 12 : hour;
        JLA.writeDigitPairLatin1(buffer, off, h12);
        buffer[off + 2] = ':';
        JLA.writeDigitPairLatin1(buffer, off + 3, minute);
        buffer[off + 5] = ':';
        JLA.writeDigitPairLatin1(buffer, off + 6, second);
        buffer[off + 8] = ' ';
        buffer[off + 9] = (byte) (hour < 12 ? 'A' : 'P');
        buffer[off + 10] = 'M';
    }

    private static void getTime12HourCharsUTF16(byte[] buffer, int off, int hour, int minute, int second) {
        int h12 = hour == 0 ? 12
                : hour > 12 ? hour - 12 : hour;
        JLA.writeDigitPairUTF16(buffer, off, h12);
        JLA.putCharUTF16(buffer, off + 2, ':');
        JLA.writeDigitPairUTF16(buffer, off + 3, minute);
        JLA.putCharUTF16(buffer, off + 5, ':');
        JLA.writeDigitPairUTF16(buffer, off + 6, second);
        JLA.putCharUTF16(buffer, off + 8, ' ');
        JLA.putCharUTF16(buffer, off + 9, hour < 12 ? 'A' : 'P');
        JLA.putCharUTF16(buffer, off + 10, 'M');
    }
}
