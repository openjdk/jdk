/*
 * Copyright (c) 1996, 2020, Oracle and/or its affiliates. All rights reserved.
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

package java.io;

import java.io.ObjectOutput;
import java.io.ObjectInput;

/**
 * A {@code Serializable} class that fully implements its own protocol
 * for reading and writing to a serialization stream.
 * <p>
 * Only the identity of the class of an {@code Externalizable} instance is
 * written in the serialization stream and it is the responsibility
 * of the class to save and restore the contents of its instances.
 * The {@code writeExternal} and {@code readExternal} methods give the
 * class complete control over the format and contents of the stream
 * for an object and its supertypes. These methods must explicitly
 * coordinate with the supertype to save its state. These methods supersede
 * customized implementations of {@code writeObject} and {@code readObject}
 * methods.
 * <p>
 * Object Serialization uses the {@code Serializable} and {@code Externalizable}
 * interfaces.  Object persistence mechanisms can use them as well.  Each
 * object to be stored is tested for the {@code Externalizable} interface. If
 * the object supports {@code Externalizable}, the {@code writeExternal} method
 * is called. If the object does not support {@code Externalizable} and does
 * implement {@code Serializable}, the object is saved using
 * {@code ObjectOutputStream}.
 * <p>
 * When an {@code Externalizable} object is reconstructed, an instance is
 * created using the public no-arg constructor, then the
 * {@code readExternal} method called.
 * <p>
 * An {@code Externalizable} instance can designate a substitution object via
 * the {@code writeReplace} and {@code readResolve} methods documented in the
 * {@link Serializable} interface.
 * <p>
 * Record classes can implement {@code Externalizable}, but it is ignored:
 * instances will receive the same treatment as other {@code Serializable}
 * instances, as defined by the
 * <a href="{@docRoot}/../specs/serialization/serial-arch.html#serialization-of-records">
 * <cite>Java Object Serialization Specification,</cite> Section 1.13,
 * "Serialization of Records"</a>.
 *
 * <div class="preview-block">
 *      <div class="preview-comment">
 *          <p>{@linkplain Class#isValue Value classes} that are not records are
 *          permitted to implement {@code Externalizable}, but the class has no
 *          mutable fields, and so is unlikely to be able to properly implement
 *          the {@code readExternal} method. Instead, the {@code writeReplace}
 *          method should be used to designate an alternative object for
 *          serialization. At deserialization time, the alternative object can
 *          implement {@code readResolve} to construct the expected value class
 *          instance.
 *      </div>
 * </div>
 *
 * @see java.io.ObjectOutputStream
 * @see java.io.ObjectInputStream
 * @see java.io.ObjectOutput
 * @see java.io.ObjectInput
 * @see java.io.Serializable
 * @since   1.1
 */
public interface Externalizable extends java.io.Serializable {
    /**
     * The object implements the writeExternal method to save its contents
     * by calling the methods of DataOutput for its primitive values or
     * calling the writeObject method of ObjectOutput for objects, strings,
     * and arrays.
     *
     * @serialData Overriding methods should use this tag to describe
     *             the data layout of this Externalizable object.
     *             List the sequence of element types and, if possible,
     *             relate the element to a public/protected field and/or
     *             method of this Externalizable class.
     *
     * @param     out the stream to write the object to
     * @throws    IOException Includes any I/O exceptions that may occur
     */
    void writeExternal(ObjectOutput out) throws IOException;

    /**
     * The object implements the readExternal method to restore its
     * contents by calling the methods of DataInput for primitive
     * types and readObject for objects, strings and arrays.  The
     * readExternal method must read the values in the same sequence
     * and with the same types as were written by writeExternal.
     *
     * @param     in the stream to read data from in order to restore the object
     * @throws    IOException if I/O errors occur
     * @throws    ClassNotFoundException If the class for an object being
     *            restored cannot be found.
     */
    void readExternal(ObjectInput in) throws IOException, ClassNotFoundException;
}
