/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * <h2>Provides interfaces describing {@code class} file attributes for the {@link java.lang.classfile} library.</h2>
 *
 * The {@code java.lang.classfile.attribute} package contains interfaces describing specific {@code class} file
 * attributes, including predefined (JVMS {@jvms 4.7}) and JDK-specific nonstandard attributes, whose mappers are
 * defined in {@link Attributes}.  This package summary provides an overview to the {@code class} file attribute system,
 * including {@link Attribute}, {@link AttributedElement}, {@link AttributeMapper}, and {@link CustomAttribute}, which
 * do not reside in this package.
 * <p>
 * Unless otherwise specified, passing {@code null} or an array or collection containing a {@code null} element as an
 * argument to a constructor or method of any Class-File API class or interface will cause a {@link NullPointerException}
 * to be thrown.
 *
 * <h2 id="reading">Reading Attributes</h2>
 * The general way to obtain attributes is through {@link AttributedElement}.  In addition to that, many attributes
 * implement {@link ClassElement}, {@link FieldElement}, {@link MethodElement}, or {@link CodeElement}, and these
 * attributes are generally delivered when their enclosing elements are viewed as {@link CompoundElement}s in streaming
 * traversal, unless otherwise specified.
 * <p>
 * When read from {@code class} files, the attributes are lazily inflated; the contents of these attributes are not
 * evaluated to speed up parsing, and user-defined attributes from {@link AttributeMapper#readAttribute} should be
 * lazy too.  Contents to users interest can be read on demand, so errors in one attribute does not prevent access to
 * other attributes.
 * <p>
 * Attribute contents are represented with constant pool entries to closely represent the original {@code class} file.
 * These entries provide conversion methods to view them as validated symbolic descriptors.  Check {@link
 * java.lang.classfile.constantpool} for effective reading of constant pool entries, which can affect attribute reading
 * speed as well.  See this example of checking the presence of a {@link Deprecated} annotation:
 * {@snippet lang="java" class="PackageSnippets" region="hasDeprecated"}
 * <p>
 * Due to the lazy nature of {@code class} file parsing, {@link IllegalArgumentException} indicating malformed
 * {@code class} file data can be thrown at any method invocation, either from the attribute itself due to structural
 * corruption, or from a constant pool entry referred by the attribute.  Some attributes, such as annotation attributes,
 * must be ignored silently if they are malformed per JVMS; as a result, attribute processing code should anticipate
 * {@link IllegalArgumentException} and skip, instead of propagating the failure, on such attributes.
 *
 * <h2 id="writing">Writing Attributes</h2>
 * Most attributes implement at least one of {@link ClassElement}, {@link FieldElement}, {@link MethodElement}, or
 * {@link CodeElement}, so they can be sent to the respective {@link ClassFileBuilder} to be written as part of those
 * structure.  Attributes define if they can {@linkplain AttributeMapper#allowMultiple() appear multiple times} in one
 * structure; if they cannot, the last attribute instance supplied to the builder is the one written to the final
 * structure.  Some attributes, such as {@link BootstrapMethodsAttribute}, implement none of those interfaces.  They are
 * created through other means, specified in the modeling interface for each of the attributes.  Attributes for a {@link
 * RecordComponentInfo} are supplied through its factory methods.
 * <p>
 * The attribute factories generally have two sets of factory methods: one that accepts symbolic information
 * representing the uses, and another that accepts constant pool entries.  Most of time, the symbolic factories are
 * sufficent, but the constant pool entry ones can be used for fine-grained control over {@code class} file generation;
 * see "{@linkplain java.lang.classfile.constantpool##writing Writing the constant pool entries}" for more details.
 * <p>
 * Many attributes can be bulk-copied if the data it depends on does not change; this information is exposed in {@link
 * AttributeMapper#stability()} and is documented for each attribute on its modeling interface.  Ability to bulk-copy
 * can massively speed up {@code class} file generation or transformation.  In addition, in conjunction with {@link
 * ClassFile.AttributesProcessingOption}, attributes read from other {@code class} files that cannot confirm its data
 * is still valid for the currently building {@code class} file may be dropped.
 *
 * @see Attribute
 * @see AttributeMapper
 * @see Attributes
 * @jvms 4.7 Attributes
 * @since 24
 */
package java.lang.classfile.attribute;

import java.lang.classfile.Attribute;
import java.lang.classfile.AttributeMapper;
import java.lang.classfile.AttributedElement;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassFileBuilder;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CompoundElement;
import java.lang.classfile.CustomAttribute;
import java.lang.classfile.FieldElement;
import java.lang.classfile.MethodElement;
