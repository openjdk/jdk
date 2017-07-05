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

import java.util.Objects;

/**
 * A definition of the way a local time can be converted to the actual
 * transition date-time.
 * <p>
 * Time zone rules are expressed in one of three ways:
 * <p><ul>
 * <li>Relative to UTC</li>
 * <li>Relative to the standard offset in force</li>
 * <li>Relative to the wall offset (what you would see on a clock on the wall)</li>
 * </ul><p>
 */
public enum TimeDefinition {
    /** The local date-time is expressed in terms of the UTC offset. */
    UTC,
    /** The local date-time is expressed in terms of the wall offset. */
    WALL,
    /** The local date-time is expressed in terms of the standard offset. */
    STANDARD;

    /**
     * Converts the specified local date-time to the local date-time actually
     * seen on a wall clock.
     * <p>
     * This method converts using the type of this enum.
     * The output is defined relative to the 'before' offset of the transition.
     * <p>
     * The UTC type uses the UTC offset.
     * The STANDARD type uses the standard offset.
     * The WALL type returns the input date-time.
     * The result is intended for use with the wall-offset.
     *
     * @param dateTime  the local date-time, not null
     * @param standardOffset  the standard offset, not null
     * @param wallOffset  the wall offset, not null
     * @return the date-time relative to the wall/before offset, not null
     */
    public LocalDateTime createDateTime(LocalDateTime dateTime, ZoneOffset standardOffset, ZoneOffset wallOffset) {
        switch (this) {
            case UTC: {
                int difference = wallOffset.getTotalSeconds() - ZoneOffset.UTC.getTotalSeconds();
                return dateTime.plusSeconds(difference);
            }
            case STANDARD: {
                int difference = wallOffset.getTotalSeconds() - standardOffset.getTotalSeconds();
                return dateTime.plusSeconds(difference);
            }
            default:  // WALL
                return dateTime;
        }
    }

}
