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
package java.lang.classfile;

import jdk.internal.javac.PreviewFeature;

/**
 * Bidirectional mapper between the classfile representation of an attribute and
 * how that attribute is modeled in the API.  The attribute mapper is used
 * to parse the classfile representation into a model, and to write the model
 * representation back to a classfile.  For each standard attribute, there is a
 * predefined attribute mapper defined in {@link Attributes}. For nonstandard
 * attributes, clients can define their own {@linkplain AttributeMapper}.
 * Classes that model nonstandard attributes should extend {@link
 * CustomAttribute}.
 * @param <A> the attribute type
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public interface AttributeMapper<A> {

    /**
     * Attribute stability indicator
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    enum AttributeStability {

        /**
         * The attribute contains only pure data, such as timestamps, and can always be bulk-copied.
         */
        STATELESS,

        /**
         * The attribute contains only pure data and CP refs, so can be bulk-copied when CP sharing is in effect,
         * and need to be exploded and rewritten when CP sharing is not in effect.
         */
        CP_REFS,

        /**
         * The attribute may contain labels, so need to be exploded and rewritten when the Code array is perturbed.
         */
        LABELS,

        /**
         * The attribute may contain indexes into structured not managed by the library (type variable lists, etc)
         * and so we consult the {@link ClassFile.AttributesProcessingOption} option to determine whether to preserve
         * or drop it during transformation.
         */
        UNSTABLE,

        /**
         * The attribute is completely unknown and so we consult the {@link ClassFile.AttributesProcessingOption} option
         * to determine whether to preserve or drop it during transformation.
         */
        UNKNOWN
    }

    /**
     * {@return the name of the attribute}
     */
    String name();

    /**
     * Create an {@link Attribute} instance from a classfile.
     *
     * @param enclosing The class, method, field, or code attribute in which
     *                  this attribute appears
     * @param cf The {@link ClassReader} describing the classfile to read from
     * @param pos The offset into the classfile at which the attribute starts
     * @return the new attribute
     */
    A readAttribute(AttributedElement enclosing, ClassReader cf, int pos);

    /**
     * Write an {@link Attribute} instance to a classfile.
     *
     * @param buf The {@link BufWriter} to which the attribute should be written
     * @param attr The attribute to write
     */
    void writeAttribute(BufWriter buf, A attr);

    /**
     * {@return whether this attribute may appear more than once in a given location}
     *
     * @implSpec The default implementation returns {@code false}
     */
    default boolean allowMultiple() {
        return false;
    }

    /**
     * {@return attribute stability indicator}
     */
    AttributeStability stability();
}
