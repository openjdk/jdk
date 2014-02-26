/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.ir;

/**
 * Interface used by AccessNodes, IndexNodes and IdentNodes to signal that when evaluated, their value will be treated
 * as a function and immediately invoked, e.g. {@code foo()}, {@code foo.bar()} or {@code foo[bar]()}. Used to customize
 * the priority of composite dynamic operations when emitting {@code INVOKEDYNAMIC} instructions that implement them,
 * namely prioritize {@code getMethod} over {@code getElem} or {@code getProp}. An access or ident node with isFunction
 * set to true will be emitted as {@code dyn:getMethod|getProp|getElem} while one with it set to false will be emitted
 * as {@code dyn:getProp|getElem|getMethod}. Similarly, an index node with isFunction set to true will be emitted as
 * {@code dyn:getMethod|getElem|getProp} while the one set to false will be emitted as {@code dyn:getElem|getProp|getMethod}.
 */
public interface FunctionCall {
    /**
     * Returns true if the value of this expression will be treated as a function and immediately invoked.
     * @return true if the value of this expression will be treated as a function and immediately invoked.
     */
    public boolean isFunction();
}
