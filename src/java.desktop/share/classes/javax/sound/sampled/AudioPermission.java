/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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

package javax.sound.sampled;

import java.io.Serial;
import java.security.BasicPermission;

/**
 * The {@code AudioPermission} class represents access rights to the audio
 * system resources. An {@code AudioPermission} contains a target name but no
 * actions list; you either have the named permission or you don't.
 * <p>
 * The target name is the name of the audio permission.
 * The names follow the hierarchical property-naming convention. Also, an
 * asterisk can be used to represent all the audio permissions.
 *
 * @apiNote
 * This permission cannot be used for controlling access to resources
 * as the Security Manager is no longer supported.
 * Consequently this class is deprecated and may be removed in a future release.
 *
 * @author Kara Kytle
 * @since 1.3
 * @deprecated There is no replacement for this class.
 */
@Deprecated(since="24", forRemoval=true)
public class AudioPermission extends BasicPermission {

    /**
     * Use serialVersionUID from JDK 1.3 for interoperability.
     */
    @Serial
    private static final long serialVersionUID = -5518053473477801126L;

    /**
     * Creates a new {@code AudioPermission} object that has the specified
     * symbolic name, such as "play" or "record". An asterisk can be used to
     * indicate all audio permissions.
     *
     * @param  name the name of the new {@code AudioPermission}
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is empty
     */
    public AudioPermission(final String name) {
        super(name);
    }

    /**
     * Creates a new {@code AudioPermission} object that has the specified
     * symbolic name, such as "play" or "record". The {@code actions} parameter
     * is currently unused and should be {@code null}.
     *
     * @param  name the name of the new {@code AudioPermission}
     * @param  actions (unused; should be {@code null})
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is empty
     */
    public AudioPermission(final String name, final String actions) {
        super(name, actions);
    }
}
