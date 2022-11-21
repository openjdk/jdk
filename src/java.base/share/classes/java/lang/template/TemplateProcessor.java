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

import jdk.internal.javac.PreviewFeature;

/**
 * This interface is used to implement template processors that do not throw checked
 * exceptions. Any implementation must supply a
 * {@link TemplateProcessor#process(StringTemplate)} method that constructs a result
 * from the information provided by the supplied {@link StringTemplate} instance.
 * <p>
 * For example:
 * {@snippet :
 * TemplateProcessor<Integer> processor = st -> {
 *     String interpolation = st.interpolate();
 *     return Integer.valueOf(interpolation);
 * };
 * }
 *
 * @param <R>  Processor's process result type.
 *
 * @see java.lang.template.ValidatingProcessor
 * @see java.lang.template.StringProcessor
 * @see java.lang.template.StringTemplate
 *
 * @since 20
 *
 * @implNote It is recommended that {@link StringProcessor} be used instead of
 * {@code TemplateProcessor<String>} when the result type is {@link String}.
 *
 * @jls 15.8.6 Process Template Expressions
 */
@PreviewFeature(feature=PreviewFeature.Feature.STRING_TEMPLATES)
@FunctionalInterface
public interface TemplateProcessor<R> extends ValidatingProcessor<R, RuntimeException> {
    /**
     * Constructs a result based on the template fragments and values in the
     * supplied {@link StringTemplate stringTemplate} instance.
     *
     * @param stringTemplate  a {@link StringTemplate} instance
     *
     * @return constructed object of type R
     */
    @Override
    R process(StringTemplate stringTemplate);
}
