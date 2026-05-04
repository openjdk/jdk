/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Datadog, Inc. All rights reserved.
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

package jdk.jfr;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Event annotation, specifies the maximum rate of events per time unit, (for
 * example, {@code "100/s"}).
 * <p>
 * If the event class annotated with {@code Throttle} is filtered by other
 * settings, such as a {@link jdk.jfr.Threshold} or a user-defined setting, the
 * throttling will happen after those settings have been applied.
 *
 * @since 25
 */
@MetadataDefinition
@Target({ ElementType.TYPE })
@Inherited
@Retention(RetentionPolicy.RUNTIME)
public @interface Throttle {
    /**
     * Setting name {@code "throttle"} for configuring throttled events.
     */
    public static final String NAME = "throttle";

    /**
     * The throttle rate, for example {@code "100/s"}.
     * <p>
     * String representation of a non-negative {@code long} value followed by a
     * forward slash ("/") and one of the following units: <br>
     * <ul style="list-style-type:none">
     * <li>{@code "ns"} (nanoseconds)</li>
     * <li>{@code "us"} (microseconds)</li>
     * <li>{@code "ms"} (milliseconds)</li>
     * <li>{@code "s"} (seconds)</li>
     * <li>{@code "m"} (minutes)</li>
     * <li>{@code "h"} (hours)</li>
     * <li>{@code "d"} (days)</li>
     * </ul>
     * <p>
     * Example values, {@code "6000/m"}, {@code "10/ms"} and {@code "200/s"}.
     * <p>
     * Specifying zero, for example {@code "0/s"}, results in no events being
     * emitted.
     * <p>
     * Specifying {@code "off"} (case-sensitive) results in all events being emitted.
     *
     * @return the throttle value, default {@code "off"}
     */
    String value() default "off";
}
