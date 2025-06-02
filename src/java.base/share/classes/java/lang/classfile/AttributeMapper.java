/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.classfile.attribute.BootstrapMethodsAttribute;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.UnknownAttribute;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.Utf8Entry;

/**
 * Bidirectional mapper between the {@code class} file representation of an
 * attribute and its API model.  The attribute mapper identifies an attribute
 * by its {@linkplain Attribute#attributeName name}, and is used to parse the
 * {@code class} file representation into a model, and to write the model
 * representation back to a {@code class} file.
 * <p>
 * {@link Attributes} defines the mappers for predefined attributes in the JVMS
 * and certain conventional attributes.  For other attributes (JVMS {@jvms
 * 4.7.1}), users can define their own {@code AttributeMapper}; classes that
 * model those attributes should extend {@link CustomAttribute}.  To read those
 * attributes, user-defined {@code AttributeMapper}s must be registered to the
 * {@link ClassFile.AttributeMapperOption}.
 *
 * @param <A> the attribute type
 * @see Attributes
 * @see ClassFile.AttributeMapperOption
 * @see java.lang.classfile.attribute
 * @since 24
 */
public interface AttributeMapper<A extends Attribute<A>> {

    /**
     * Indicates the data dependency of the {@code class} file representation
     * of an attribute.  Whether an attribute can be bulk-copied by its binary
     * representation to a new {@code class} file depends on if its data refers
     * to other parts of its enclosing {@code class} file.
     *
     * @apiNote
     * This dependency is called "stability" because it indicates the conditions
     * for a {@code class} file attribute to be eligible for bulk-copying to
     * another {@code class} file.
     *
     * @see AttributeMapper#stability()
     * @since 24
     */
    enum AttributeStability {

        /**
         * The attribute contains only standalone data, and has no reference to
         * other parts of its enclosing {@code class} file, besides the name of
         * the attribute.  Thus, its contents can always be bulk-copied to
         * another {@code class} file.
         * <p>
         * For example, a bit mask is standalone data.
         */
        STATELESS,

        /**
         * In addition to standalone data, the attribute refers to the constant
         * pool, including the {@link BootstrapMethodsAttribute BootstrapMethods}
         * attribute, of its enclosing {@code class} file.  Thus, it can be
         * bulk-copied when the destination {@code class} file extends its
         * constant pool from that of the original {@code class}.  It must be
         * expanded to translate constant pool references and rewritten when
         * constant pool indices are not compatible.
         * <p>
         * For example, a {@link Utf8Entry} is a reference to the constant pool.
         *
         * @see ConstantPoolBuilder#of(ClassModel)
         * @see ClassFile.ConstantPoolSharingOption
         */
        CP_REFS,

        /**
         * In addition to standalone data and references to the constant pool,
         * the attribute refers to positions into the {@code code} array of a
         * {@link CodeAttribute Code} attribute.  Thus, it can be bulked-copied
         * when the {@code code} array is unchanged, which requires that the
         * destination {@code class} file extends its constant pool from that of
         * the original {@code class}.  It must be expanded to translate {@link
         * Label}s or constant pool references and rewritten if the {@code code}
         * array is perturbed, including when constant pool indices are not
         * compatible.
         * <p>
         * For example, a bci value, modeled by a {@link Label}, is a reference
         * to a position in the {@code code} array.
         */
        LABELS,

        /**
         * The attribute refers to structures not managed by the library (type
         * variable lists, etc.).  As a result, even when the attribute is
         * expanded, those references may not be correctly translated, and the
         * rewritten results may be incorrect.
         * <p>
         * If the attribute is read from a {@code class} file, {@link
         * ClassFile.AttributesProcessingOption} determines whether to preserve
         * or drop the attribute during transformation.
         *
         * @see ClassFile.AttributesProcessingOption#DROP_UNSTABLE_ATTRIBUTES
         */
        UNSTABLE,

        /**
         * The attribute is completely unknown.  As a result, expanding and
         * rewriting is not possible, and any difference between the destination
         * {@code class} file and its enclosing {@code class} file may make the
         * attribute incorrect.
         * <p>
         * {@link ClassFile.AttributesProcessingOption} determines whether to
         * preserve or drop the attribute during transformation.
         *
         * @see UnknownAttribute
         * @see ClassFile.AttributesProcessingOption#DROP_UNSTABLE_ATTRIBUTES
         * @see ClassFile.AttributesProcessingOption#DROP_UNKNOWN_ATTRIBUTES
         */
        UNKNOWN
    }

    /**
     * {@return the name of the attribute}
     */
    String name();

    /**
     * Creates an {@link Attribute} instance from a {@code class} file for the
     * Class-File API.
     * <p>
     * This method is called by the Class-File API to support reading of
     * attributes.  Users should never call this method.
     * <p>
     * The Class-File API makes these promises about the call to this method:
     * <ul>
     * <li>The {@link Utf8Entry} for the name of the attribute is accessible
     * with {@code cf.readEntry(pos - 6, Utf8Entry.class)}, and is validated;
     * <li>The length of the attribute is accessible with {@code cf.readInt(pos
     * - 4)}, and is validated to be positive and not beyond the length of the
     * {@code class} file;
     * <li>The {@link AttributedElement} attribute access functionalities on the
     * {@code enclosing} model may not be accessed when this method is called,
     * but can be accessed later by the returned attribute when it is accessible
     * to users.
     * </ul>
     * <p>
     * The returned {@code Attribute} must fulfill these requirements:
     * <ul>
     * <li>{@link Attribute#attributeMapper()} returns this mapper;
     * <li>{@link Attribute#attributeName()} returns the attribute name in the
     * {@code class} file.
     * </ul>
     *
     * @apiNote
     * Implementations of this method should perform minimal work to return an
     * attribute, as this method is called even if the resulting attribute is
     * never used.  In particular, the implementation should avoid checking the
     * validity of the attribute {@code class} file data or performing actions
     * that may throw exceptions.
     *
     * @param enclosing the structure in which this attribute appears
     * @param cf provides access to the {@code class} file to read from
     * @param pos the offset into the {@code class} file at which the contents
     *            of the attribute starts
     * @return the read attribute
     */
    A readAttribute(AttributedElement enclosing, ClassReader cf, int pos);

    /**
     * Writes an {@link Attribute} instance to a {@code class} file for the
     * Class-File API.
     * <p>
     * This method is called by the Class-File API to support writing of
     * attributes.  Users should never call this method.
     * <p>
     * The Class-File API makes these promises about the call to this method:
     * <ul>
     * <li>{@link Attribute#attributeMapper() attr.attributeMapper()} returns
     * this mapper;
     * <li>The {@code buf} may already have data written, that its {@link
     * BufWriter#size() size} may not be {@code 0}.
     * </ul>
     * <p>
     * The {@code class} file writing must fulfill these requirements:
     * <ul>
     * <li>The attribute name {@code u2} and attribute length {@code u4} must
     * be written to the {@code buf};
     * <li>{@link Attribute#attributeName() attr.attributeName()} is written as
     * if with {@code buf.writeIndex(attr.attributeName())};
     * <li>The attribute length is the length, in bytes, of attribute contents
     * written to the {@code buf}, not including the 6 bytes used by the name
     * and the length;
     * <li>If any information in the API model of the attribute, {@code attr},
     * cannot be represented in the {@code class} file format of the attribute,
     * an {@link IllegalArgumentException} is thrown.
     * </ul>
     *
     * @apiNote
     * {@link BufWriter#patchInt} can be used to update the attribute length
     * after the attribute contents are written to the {@code buf}.
     *
     * @param buf the {@link BufWriter} to which the attribute should be written
     * @param attr the attribute to write
     * @throws IllegalArgumentException if some data in the API model of the
     *         attribute is invalid for the {@code class} file format
     */
    void writeAttribute(BufWriter buf, A attr);

    /**
     * {@return whether this attribute may appear more than once in one
     * structure}
     * <p>
     * If an attribute does not allow multiple instances in one structure,
     * can be supplied to a {@link ClassFileBuilder}, and multiple instances of
     * the attribute are supplied to the builder, the last supplied attribute
     * appears on the built structure.
     *
     * @implSpec The default implementation returns {@code false}.
     */
    default boolean allowMultiple() {
        return false;
    }

    /**
     * {@return the data dependency of this attribute on the {@code class} file}
     */
    AttributeStability stability();
}
