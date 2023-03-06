/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.template;

import java.util.List;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.FormatProcessor;

import jdk.internal.javac.PreviewFeature;

/**
 * Built-in policies using this additional interface have the flexibility to
 * specialize the composition of the templated string by returning a customized
 * {@link MethodHandle} from {@link ProcessorLinkage#linkage linkage}.
 * These specializations are typically implemented to improve performance;
 * specializing value types or avoiding boxing and vararg arrays.
 *
 * @implNote This interface is sealed to only allow standard processors.
 *
 * @since 21
 */
@PreviewFeature(feature=PreviewFeature.Feature.STRING_TEMPLATES)
public sealed interface ProcessorLinkage permits FormatProcessor {
    /**
     * This method creates a {@link MethodHandle} that when invoked with arguments of
     * those specified in {@code type} returns a result that equals that returned by
     * the template processor's process method. The difference being that this method
     * can preview the template's fragments and value types in advance of usage and
     * thereby has the opportunity to produce a specialized implementation.
     *
     * @param fragments  string template fragments
     * @param type       method type
     *
     * @return {@link MethodHandle} for the processor applied to template
     *
     * @throws NullPointerException if any of the arguments are null
     */
    MethodHandle linkage(List<String> fragments, MethodType type);
}
