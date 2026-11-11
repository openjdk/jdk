/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.value;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;

/**
 * A JDK internal annotation that facilitates serialization and deserialization of non-record
 * value classes without requiring those value classes to implement the {@code writeReplace()}
 * and {@code readObject()} methods.
 * <p>
 * Non-record value classes, unless they implement the {@code writeReplace()}
 * and {@code readObject()} methods, cannot be serialized or deserialized. Certain pre-existing
 * {@code Serializable} identity classes within the JDK are value classes in preview-mode, and
 * must remain compatible. Such a value class may choose to annotate a constructor or a static
 * method in that class with the {@code Deserializer} annotation. During serialization and
 * deserialization of value objects of those classes, the {@code java.io.ObjectOutputStream} and
 * {@code java.io.ObjectInputStream} will check for the presence of this annotation and when
 * present, will relax the requirement of {@code writeReplace()} and {@code readObject()}
 * methods in that class.
 * <p>
 * During deserialization, the constructor or the method annotated with the {@code Deserializer}
 * will be invoked to create the value object instance from the stream.
 * <p>
 * {@code Deserializer} is a temporary measure for legacy serialization migration compatibility;
 * future value object persistence would be handled by other mechanisms. The {@code Deserializer}
 * annotation isn't for general purpose usage, even in the classes that belong to the JDK; it is
 * meant to be used only by a very select few classes and the legacy serialization places several
 * unspecified restrictions on its usage.
 * <p>
 * This annotation only takes effect for classes loaded by the boot loader. The presence of this
 * annotation in classes loaded outside of the boot loader is ignored.
 *
 * @since 28
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {CONSTRUCTOR, METHOD})
public @interface Deserializer {
    /**
     * The names of the serial fields that the constructor or static method,
     * annotated with {@code Deserializer}, expects to be passed when invoked during
     * deserialization of the value object from the stream. The serial field types are
     * the corresponding parameter types.
     */
    String[] value();
}
