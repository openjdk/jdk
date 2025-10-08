/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * Event annotation, determines if an event should be synchronous by default.
 * A synchronous event should be emitted from current thread and an asynchronous
 * event should be emitted from {@code "TargetThread"}.
 * <p>
 * If an event doesn't have the annotation, then by default the event is
 * a synchronous event.
 * <p>
 * Delivering asynchronous event is not guaranteed, for the reasons:
 * <ul>
 * <li>If target thread is a virtual thread, the event won't be emitted</li>
 * <li>Target thread may have been terminated</li>
 * </ul>
 * @since 26
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@MetadataDefinition
public @interface EventMode {
    public static final String SYNCHRONOUS = "synchronous";
    public static final String ASYNCHRONOUS = "Asynchronous";

    /**
     * Setting name {@code "EventMode"}, signifies that the event should be
     * recorded.
     */
    public static final String NAME = "mode";

    /**
     * Returns {@code Synchronous} if by default the event should be a synchronous event, {@code Asynchronous} otherwise.
     *
     * @return {@code Synchronous} if by default the event should be a asynchronous event, {@code Asynchronous } otherwise
     */
    String value() default SYNCHRONOUS;
}
