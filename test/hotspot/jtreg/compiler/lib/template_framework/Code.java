/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package compiler.lib.template_framework;

import java.util.List;

/**
 * This class collects code, i.e. {@link String}s or {@link List}s of {@link String}s.
 * All the {@link String}s are later collected in a {@link StringBuilder}. If we used a {@link StringBuilder}
 * directly to collect the {@link String}s, we could not as easily insert code at an "earlier" position, i.e.
 * reaching out to a {@link Hook#anchor}.
 */
sealed interface Code permits Code.Token, Code.CodeList {

    record Token(String s) implements Code {
        @Override
        public void renderTo(StringBuilder builder) {
            builder.append(s);
        }
    }

    record CodeList(List<Code> list) implements Code {
        @Override
        public void renderTo(StringBuilder builder) {
            list.forEach(code -> code.renderTo(builder));
        }
    }

    void renderTo(StringBuilder builder);
}
