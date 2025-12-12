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
 * Represents a scope with its tokens. Boolean flags indicate if names,
 * hashtag replacements and {@link Template#setFuelCost} are local, or escape to
 * outer scopes.
 *
 * <p>
 * Note: We want the {@link ScopeToken} to be public, but the internals of the
 *       record should be private. One way to solve this is with a public interface
 *       that exposes nothing but its name, and a private implementation via a
 *       record that allows easy destructuring with pattern matching.
 */
record ScopeTokenImpl(List<Token> tokens,
                      boolean isTransparentForNames,
                      boolean isTransparentForHashtags,
                      boolean isTransparentForSetFuelCost) implements ScopeToken, Token {}
