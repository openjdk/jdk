/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package jdk.vm.ci.code;

/**
 * Represents a compiled instance of a method. It may have been invalidated or removed in the
 * meantime.
 */
public class InstalledCode {

    /**
     * Address of the entity (e.g., HotSpot {@code nmethod} or {@code RuntimeStub}) representing
     * this installed code.
     */
    protected long address;

    /**
     * Address of the entryPoint of this installed code.
     */
    protected long entryPoint;

    /**
     * Counts how often the address field was reassigned.
     */
    protected long version;

    protected final String name;

    /**
     * The maximum length of an InstalledCode name. This name is typically installed into
     * the code cache so it should have a reasonable limit.
     */
    public static final int MAX_NAME_LENGTH = 2048;

    /**
     * @param name the name to be associated with the installed code. Can be null and
     *        must be no longer than {@link #MAX_NAME_LENGTH}.
     *
     * @throws IllegalArgumentException if {@code name.length >} {@link #MAX_NAME_LENGTH}
     */
    public InstalledCode(String name) {
        if (name != null && name.length() > MAX_NAME_LENGTH) {
            String msg = String.format("name length (%d) is greater than %d (name[0:%s] = %s)",
                                        name.length(), MAX_NAME_LENGTH, MAX_NAME_LENGTH, name.substring(0, MAX_NAME_LENGTH));
            throw new IllegalArgumentException(msg);
        }
        this.name = name;
    }

    /**
     * @return the address of entity (e.g., HotSpot {@code nmethod} or {@code RuntimeStub})
     *         representing this installed code
     */
    public long getAddress() {
        return address;
    }

    /**
     * @return the address of the normal entry point of the installed code.
     */
    public long getEntryPoint() {
        return entryPoint;
    }

    /**
     * @return the version number of this installed code
     */
    public final long getVersion() {
        return version;
    }

    /**
     * Returns the name of this installed code.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the start address of this installed code if it is {@linkplain #isValid() valid}, 0
     * otherwise.
     */
    public long getStart() {
        return 0;
    }

    /**
     * @return true if the code represented by this object is still valid for invocation, false
     *         otherwise (may happen due to deopt, etc.)
     */
    public boolean isValid() {
        return entryPoint != 0;
    }

    /**
     * @return true if this object still points to installed code
     */
    public boolean isAlive() {
        return address != 0;
    }

    /**
     * Returns a copy of this installed code if it is {@linkplain #isValid() valid}, null otherwise.
     */
    public byte[] getCode() {
        return null;
    }

    /**
     * Equivalent to calling {@link #invalidate(boolean)} with a {@code true} argument.
     */
    public void invalidate() {
        invalidate(true);
    }

    /**
     * Invalidates this installed code such that any subsequent
     * {@linkplain #executeVarargs(Object...) invocation} will throw an
     * {@link InvalidInstalledCodeException}.
     *
     * If this installed code is already {@linkplain #isValid() invalid}, this method has no effect.
     * A subsequent call to {@link #isAlive()} or {@link #isValid()} on this object will return
     * {@code false}.
     *
     * @param deoptimize if {@code true}, all existing invocations will be immediately deoptimized.
     *            If {@code false}, any existing invocation will continue until it completes or
     *            there is a subsequent call to this method with {@code deoptimize == true} before
     *            the invocation completes.
     */
    public void invalidate(boolean deoptimize) {
        throw new UnsupportedOperationException();
    }

    /**
     * Executes the installed code with a variable number of arguments.
     *
     * @param args the array of object arguments
     * @return the value returned by the executed code
     */
    @SuppressWarnings("unused")
    public Object executeVarargs(Object... args) throws InvalidInstalledCodeException {
        throw new UnsupportedOperationException();
    }
}
