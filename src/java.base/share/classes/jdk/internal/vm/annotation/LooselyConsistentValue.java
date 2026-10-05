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

package jdk.internal.vm.annotation;

import java.lang.annotation.*;

/**
 * A loosely-consistent value class is a class that is willing to tolerate
 * data corruption when fields or arrays storing instances of the class are
 * updated under race. Specifically, a value object read from such a field may
 * contain combinations of field values that were never set by a previous
 * constructor invocation.
 * <p>
 * Users of a class with this annotation take responsibility for ensuring the
 * integrity of their data by avoiding race conditions.
 * <p>
 * The HotSpot VM uses this annotation to enable non-atomic strategies for
 * reading and writing to flattened fields and arrays of the annotated class's
 * type.
 * <p>
 * Because these behaviors are not specified by Java SE, this annotation should
 * only be used by internal JDK code for experimental purposes and should not
 * affect user-observable outcomes.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface LooselyConsistentValue {
}
