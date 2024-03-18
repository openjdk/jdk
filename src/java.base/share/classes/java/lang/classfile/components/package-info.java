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

/**
 * <h2>Provides specific components, transformations, and tools built on top of the
 * {@link java.lang.classfile} library.</h2>
 *
 * The {@code java.lang.classfile.components} package contains specific
 * transformation components and utility classes helping to compose very complex
 * tasks with minimal effort.
 *
 * <h3>{@link ClassPrinter}</h3>
 * <p>
 * {@link ClassPrinter} is a helper class providing seamless export of a {@link
 * java.lang.classfile.ClassModel}, {@link java.lang.classfile.FieldModel},
 * {@link java.lang.classfile.MethodModel}, or {@link
 * java.lang.classfile.CodeModel} into human-readable structured text in
 * JSON, XML, or YAML format, or into a tree of traversable and printable nodes.
 * <p>
 * Primary purpose of {@link ClassPrinter} is to provide human-readable class
 * info for debugging, exception handling and logging purposes. The printed
 * class also conforms to a standard format to support automated offline
 * processing.
 * <p>
 * The most frequent use case is to simply print a class:
 * {@snippet lang="java" class="PackageSnippets" region="printClass"}
 * <p>
 * {@link ClassPrinter} allows to traverse tree of simple printable nodes to
 * hook custom printer:
 * {@snippet lang="java" class="PackageSnippets" region="customPrint"}
 * <p>
 * Another use case for {@link ClassPrinter} is to simplify writing of automated
 * tests:
 * {@snippet lang="java" class="PackageSnippets" region="printNodesInTest"}
 *
 * <h3>{@link ClassRemapper}</h3>
 * ClassRemapper is a {@link java.lang.classfile.ClassTransform}, {@link
 * java.lang.classfile.FieldTransform}, {@link
 * java.lang.classfile.MethodTransform} and {@link
 * java.lang.classfile.CodeTransform} deeply re-mapping all class references
 * in any form, according to given map or map function.
 * <p>
 * The re-mapping is applied to superclass, interfaces, all kinds of descriptors
 * and signatures, all attributes referencing classes in any form (including all
 * types of annotations), and to all instructions referencing to classes.
 * <p>
 * Primitive types and arrays are never subjects of mapping and are not allowed
 * targets of mapping.
 * <p>
 * Arrays of reference types are always decomposed, mapped as the base reference
 * types and composed back to arrays.
 * <p>
 * Single class remapping example:
 * {@snippet lang="java" class="PackageSnippets" region="singleClassRemap"}
 * <p>
 * Remapping of all classes under specific package:
 * {@snippet lang="java" class="PackageSnippets" region="allPackageRemap"}
 *
 * <h3>{@link CodeLocalsShifter}</h3>
 * {@link CodeLocalsShifter} is a {@link java.lang.classfile.CodeTransform}
 * shifting locals to newly allocated positions to avoid conflicts during code
 * injection. Locals pointing to the receiver or to method arguments slots are
 * never shifted. All locals pointing beyond the method arguments are re-indexed
 * in order of appearance.
 * <p>
 * Sample of code transformation shifting all locals in all methods:
 * {@snippet lang="java" class="PackageSnippets" region="codeLocalsShifting"}
 *
 * <h3>{@link CodeRelabeler}</h3>
 * {@link CodeRelabeler} is a {@link java.lang.classfile.CodeTransform}
 * replacing all occurrences of {@link java.lang.classfile.Label} in the
 * transformed code with new instances.
 * All {@link java.lang.classfile.instruction.LabelTarget} instructions are
 * adjusted accordingly.
 * Relabeled code graph is identical to the original.
 * <p>
 * Primary purpose of {@link CodeRelabeler} is for repeated injections of the
 * same code blocks.
 * Repeated injection of the same code block must be relabeled, so each instance
 * of {@link java.lang.classfile.Label} is bound in the target bytecode
 * exactly once.
 * <p>
 * Sample transformation relabeling all methods:
 * {@snippet lang="java" class="PackageSnippets" region="codeRelabeling"}
 *
 * <h3>Class Instrumentation Sample</h3>
 * Following snippet is sample composition of {@link ClassRemapper}, {@link
 * CodeLocalsShifter} and {@link CodeRelabeler} into fully functional class
 * instrumenting transformation:
 * {@snippet lang="java" class="PackageSnippets" region="classInstrumentation"}
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
package java.lang.classfile.components;

import jdk.internal.javac.PreviewFeature;
