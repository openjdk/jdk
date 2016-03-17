/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.module;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import jdk.internal.module.Hasher.HashSupplier;


/**
 * A reference to a module's content.
 *
 * <p> A module reference contains the module's descriptor and its location, if
 * known.  It also has the ability to create a {@link ModuleReader} in order to
 * access the module's content, which may be inside the Java run-time system
 * itself or in an artifact such as a modular JAR file.
 *
 * @see ModuleFinder
 * @see ModuleReader
 * @since 9
 */

public final class ModuleReference {

    private final ModuleDescriptor descriptor;
    private final URI location;
    private final Supplier<ModuleReader> readerSupplier;

    // the function that computes the hash of this module reference
    private final HashSupplier hasher;

    // cached hash string to avoid needing to compute it many times
    private String cachedHash;

    /**
     * Constructs a new instance of this class.
     */
    ModuleReference(ModuleDescriptor descriptor,
                    URI location,
                    Supplier<ModuleReader> readerSupplier,
                    HashSupplier hasher)
    {
        this.descriptor = Objects.requireNonNull(descriptor);
        this.location = location;
        this.readerSupplier = Objects.requireNonNull(readerSupplier);
        this.hasher = hasher;
    }


    /**
     * Constructs a new instance of this class.
     *
     * <p> The {@code readSupplier} parameter is the supplier of the {@link
     * ModuleReader} that may be used to read the module content. Its {@link
     * Supplier#get() get()} method throws {@link UncheckedIOException} if an
     * I/O error occurs opening the module content. The {@code get()} method
     * throws {@link SecurityException} if opening the module is denied by the
     * security manager.
     *
     * @param descriptor
     *        The module descriptor
     * @param location
     *        The module location or {@code null} if not known
     * @param readerSupplier
     *        The {@code Supplier} of the {@code ModuleReader}
     */
    public ModuleReference(ModuleDescriptor descriptor,
                           URI location,
                           Supplier<ModuleReader> readerSupplier)
    {
        this(descriptor, location, readerSupplier, null);
    }


    /**
     * Returns the module descriptor.
     *
     * @return The module descriptor
     */
    public ModuleDescriptor descriptor() {
        return descriptor;
    }


    /**
     * Returns the location of this module's content, if known.
     *
     * <p> This URI, when present, is used as the {@linkplain
     * java.security.CodeSource#getLocation location} value of a {@link
     * java.security.CodeSource CodeSource} so that a module's classes can be
     * granted specific permissions when loaded by a {@link
     * java.security.SecureClassLoader SecureClassLoader}.
     *
     * @return The location or an empty {@code Optional} if not known
     */
    public Optional<URI> location() {
        return Optional.ofNullable(location);
    }


    /**
     * Opens the module content for reading.
     *
     * <p> This method opens the module content by invoking the {@link
     * Supplier#get() get()} method of the {@code readSupplier} specified at
     * construction time. </p>
     *
     * @return A {@code ModuleReader} to read the module
     *
     * @throws IOException
     *         If an I/O error occurs
     * @throws SecurityException
     *         If denied by the security manager
     */
    public ModuleReader open() throws IOException {
        try {
            return readerSupplier.get();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }

    }


    /**
     * Computes the hash of this module, returning it as a hex string.
     * Returns {@code null} if the hash cannot be computed.
     *
     * @throws java.io.UncheckedIOException if an I/O error occurs
     */
    String computeHash(String algorithm) {
        String result = cachedHash;
        if (result != null)
            return result;
        if (hasher == null)
            return null;
        cachedHash = result = hasher.generate(algorithm);
        return result;
    }

    private int hash;

    /**
     * Computes a hash code for this module reference.
     *
     * <p> The hash code is based upon the components of the reference, and
     * satisfies the general contract of the {@link Object#hashCode
     * Object.hashCode} method. </p>
     *
     * @return The hash-code value for this module reference
     */
    @Override
    public int hashCode() {
        int hc = hash;
        if (hc == 0) {
            hc = Objects.hash(descriptor, location, readerSupplier, hasher);
            if (hc != 0) hash = hc;
        }
        return hc;
    }

    /**
     * Tests this module reference for equality with the given object.
     *
     * <p> If the given object is not a {@code ModuleReference} then this
     * method returns {@code false}. Two module references are equal if their
     * module descriptors are equal, their locations are equal or both unknown,
     * and were created with equal supplier objects to access the module
     * content. </p>
     *
     * <p> This method satisfies the general contract of the {@link
     * java.lang.Object#equals(Object) Object.equals} method. </p>
     *
     * @param   ob
     *          the object to which this object is to be compared
     *
     * @return  {@code true} if, and only if, the given object is a module
     *          reference that is equal to this module reference
     */
    @Override
    public boolean equals(Object ob) {
        if (!(ob instanceof ModuleReference))
            return false;
        ModuleReference that = (ModuleReference)ob;

        return Objects.equals(this.descriptor, that.descriptor)
                && Objects.equals(this.location, that.location)
                && Objects.equals(this.readerSupplier, that.readerSupplier)
                && Objects.equals(this.hasher, that.hasher);
    }

    /**
     * Returns a string describing this module reference.
     *
     * @return A string describing this module reference
     */
    @Override
    public String toString() {
        return ("[module " + descriptor().name()
                + ", location=" + location + "]");
    }

}
