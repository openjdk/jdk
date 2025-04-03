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
 * <h2>Provides interfaces describing constant pool entries for the {@link java.lang.classfile} library.</h2>
 *
 * The {@code java.lang.classfile.constantpool} package contains interfaces describing constant pool entries in the
 * {@code class} file format.  Constant pool entries are low-level models to faithfully represent the exact structure
 * of a {@code class} file.
 * <p>
 * Unless otherwise specified, passing {@code null} or an array or collection containing a {@code null} element as an
 * argument to a constructor or method of any Class-File API class or interface will cause a {@link NullPointerException}
 * to be thrown.
 *
 * <h2 id="reading">Reading the constant pool entries</h2>
 * When read from {@code class} files, the pool entries are lazily inflated; the contents of these entries, besides the
 * bare structure, are not evaluated to speed up parsing.  Entries to users interest, usually accessed from other models
 * and elements, have their contents read on demand.  For example, to search for methods, a user should filter first by
 * access flags and then by method name, and use {@link Utf8Entry#equalsString(String)} instead of checking equality
 * against {@link Utf8Entry#stringValue()}.  This avoids inflation of UTF-8 entries as much as possible:
 * {@snippet lang="java" class="PackageSnippets" region="isStaticWorkMethod"}
 * <p>
 * The entries also define accessors to validated symbolic information with nominal descriptor abstractions from the
 * {@link java.lang.constant} package.  These symbolic information accessors perform validation against the read
 * {@code class} files, and throw {@link IllegalArgumentException} when the accessed constant pool entry contains
 * invalid data.  The nominal descriptors represent validated data, which saves users from extra validations in future
 * processing.
 * <p>
 * Due to the lazy nature of {@code class} file parsing, {@link IllegalArgumentException} indicating malformed
 * {@code class} file data can be thrown at any method invocation.  For example, an exception may come from a {@link
 * ClassEntry} when it is first read from the constant pool (referring to an invalid index or wrong type of entry), when
 * its referred UTF-8 entry is expanded (malformed UTF-8 data), or when its symbolic information is accessed (the string
 * is not valid for a class entry).
 *
 * <h2 id="writing">Writing the constant pool entries</h2>
 * In general, users do not need to worry about working with the constant pool and its entries when writing {@code
 * class} files.  Most Class-File API models and elements have two sets of factory methods: one that accepts symbolic
 * information representing the uses, and another that accepts constant pool entries.  The constant pool builder
 * associated with {@code class} file builders, {@link ClassFileBuilder#constantPool}, automatically creates or reuses
 * pool entries from the symbolic information.  Validated data in symbolic information helps {@code class} file
 * generation by avoiding extraneous parsing of raw constant pool entry data.
 * <p>
 * As always, users can use factories that accept constant pool entries if they already have them by hand, or if they
 * desire fine-grained control over {@code class} file generation.
 * <p>
 * If many models and elements are reused from another {@link ClassModel} in class building, the class building process
 * can use a constant pool builder that extends from the given {@code ClassModel}, available through {@link
 * ConstantPoolBuilder#of(ClassModel) ConstantPoolBuilder::of(ClassModel)}, so that byte data with constant pool
 * references can be copied in batch, speeding up class building.  This is especially applicable to class transformations,
 * and {@link ClassFile.ConstantPoolSharingOption ConstantPoolSharingOption} exists to control this behavior.
 *
 * @jvms 4.4 The Constant Pool
 * @since 24
 */
package java.lang.classfile.constantpool;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassFileBuilder;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
