/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, JetBrains s.r.o.. All rights reserved.
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

package sun.swing;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import java.util.List;
import java.util.function.Function;
import java.util.concurrent.CopyOnWriteArrayList;

public class AccessibleComponentAccessor {
    private static final List<Function<AccessibleContext, Object>> accessors = new CopyOnWriteArrayList<>();

    public static void addAccessor(Function<AccessibleContext, Object> accessor) {
        accessors.add(accessor);
    }

    public static Accessible getAccessible(AccessibleContext context) {
        for (Function<AccessibleContext, Object> accessor : accessors) {
            Object o = accessor.apply(context);
            if (o instanceof Accessible accessible) {
                return accessible;
            }
        }
        return null;
    }
}
