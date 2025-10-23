/*
 * Copyright (c) 1995, 2025, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

/**
 * An instance of {@code ThreadDeath} was originally specified to be thrown
 * by a victim thread when "stopped" with the {@link Thread} API.
 *
 * @deprecated {@code Thread} originally specified a "{@code stop}" method to stop a
 *      victim thread by causing the victim thread to throw a {@code ThreadDeath}. It
 *      was inherently unsafe and deprecated in an early JDK release. The {@code stop}
 *      method has since been removed and {@code ThreadDeath} is deprecated, for removal.
 *
 * @since   1.0
 */
@Deprecated(since="20", forRemoval=true)
public class ThreadDeath extends Error {
    @java.io.Serial
    private static final long serialVersionUID = -4417128565033088268L;

    /**
     * Constructs a {@code ThreadDeath}.
     */
    public ThreadDeath() {}
}
