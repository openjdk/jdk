/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.query;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.StringJoiner;

import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedClassLoader;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordedThreadGroup;
import jdk.jfr.internal.util.ValueFormatter;

public class FieldFormatter {

    public static String formatCompact(Field field, Object object) {
        return format(field, object, true);
    }

    public static String format(Field field, Object object) {
        return format(field, object, false);
    }

    private static String format(Field field, Object object, boolean compact) {
        if (object == null) {
            return field.missingText;
        }
        if (object instanceof Collection<?> c) {
            StringJoiner sj = new StringJoiner(", ");
            for (Object o : c) {
                sj.add(format(field, o, compact));
            }
            return sj.toString();
        }
        if (object instanceof String s) {
            return stripFormatting(s);
        }
        if (object instanceof Double d) {
            if (Double.isNaN(d) || d == Double.NEGATIVE_INFINITY) {
                return field.missingText;
            }
        }
        if (object instanceof Float f) {
            if (Float.isNaN(f) || f == Float.NEGATIVE_INFINITY) {
                return field.missingText;
            }
        }
        if (object instanceof Long l && l == Long.MIN_VALUE) {
            return field.missingText;
        }
        if (object instanceof Integer i && i == Integer.MIN_VALUE) {
            return field.missingText;
        }

        if (object instanceof RecordedFrame f && f.isJavaFrame()) {
            object = f.getMethod();
        }

        if (object instanceof RecordedThread t) {
            if (t.getJavaThreadId() > 0) {
                return t.getJavaName();
            } else {
                return t.getOSName();
            }
        }
        if (object instanceof RecordedClassLoader cl) {
            return format(field, cl.getType(), compact);
        }
        if (object instanceof RecordedStackTrace st) {
            return format(field, st.getFrames().getFirst(), compact);
        }
        if (object instanceof RecordedThreadGroup tg) {
            return tg.getName();
        }
        if (object instanceof RecordedMethod m) {
            return ValueFormatter.formatMethod(m, compact);
        }
        if (object instanceof RecordedClass clazz) {
            String text = ValueFormatter.formatClass(clazz);
            if (compact) {
                return text.substring(text.lastIndexOf(".") + 1);
            }
            return text;
        }
        if (object instanceof RecordedFrame f) {
            if (f.isJavaFrame()) {
                return format(field, f.getMethod(), compact);
            }
            return "<unknown>";
        }
        if (object instanceof Duration d) {
            if (d.getSeconds() == Long.MIN_VALUE && d.getNano() == 0) {
                return field.missingText;
            }
            if (d.equals(ChronoUnit.FOREVER.getDuration())) {
                return "Indefinite";
            }
            return ValueFormatter.formatDuration(d, field.precision);
        }
        if (object instanceof Instant instant) {
            return ValueFormatter.formatTimestamp(instant);
        }
        if (field.percentage) {
            if (object instanceof Number n) {
                double d = n.doubleValue();
                return String.format("%.2f", d * 100) + "%";
            }
        }
        if (field.bits || field.bytes) {
            if (object instanceof Number n) {
                long amount = n.longValue();
                if (field.frequency) {
                    if (field.bytes) {
                        return ValueFormatter.formatBytesPerSecond(amount);
                    }
                    if (field.bits) {
                        return ValueFormatter.formatBitsPerSecond(amount);
                    }
                } else {
                    if (field.bytes) {
                        return ValueFormatter.formatBytes(amount);
                    }
                    if (field.bits) {
                        return ValueFormatter.formatBits(amount);
                    }
                }
            }
        }
        if (field.memoryAddress) {
            if (object instanceof Number n) {
                long d = n.longValue();
                return String.format("0x%08X", d);
            }
        }
        if (field.frequency) {
            if (object instanceof Number) {
                return object + " Hz";
            }
        }
        if (object instanceof Number number) {
            return ValueFormatter.formatNumber(number);
        }
        return object.toString();
    }

    private static String stripFormatting(String text) {
        if (!hasFormatting(text)) {
            return text; // Fast path to avoid allocation
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(isFormatting(c) ? ' ' : c);
        }
        return sb.toString();
    }

    private static boolean hasFormatting(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (isFormatting(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFormatting(char c) {
        return c == '\n' || c == '\r' || c == '\t';
    }
}
