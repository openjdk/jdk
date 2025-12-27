/*
 * Copyright (c) 1994, 2025, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

/**
 * Thrown when an application attempts to use {@code null} in a
 * case where an object is required. These include:
 * <ul>
 * <li>Calling the instance method of a {@code null} object.
 * <li>Accessing or modifying the field of a {@code null} object.
 * <li>Taking the length of {@code null} as if it were an array.
 * <li>Accessing or modifying the slots of {@code null} as if it
 *     were an array.
 * <li>Throwing {@code null} as if it were a {@code Throwable}
 *     value.
 * </ul>
 * <p>
 * Applications should throw instances of this class to indicate
 * other illegal uses of the {@code null} object.
 *
 * {@code NullPointerException} objects may be constructed by the
 * virtual machine as if {@linkplain Throwable#Throwable(String,
 * Throwable, boolean, boolean) suppression were disabled and/or the
 * stack trace was not writable}.
 *
 * @since   1.0
 */
public class NullPointerException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 5162710183389028792L;

    /**
     * Constructs a {@code NullPointerException} with no detail message.
     */
    public NullPointerException() {
        extendedMessageState = 0;
        super();
        extendedMessageState |= CONSTRUCTOR_FINISHED;
    }

    /**
     * Constructs a {@code NullPointerException} with the specified
     * detail message.
     *
     * @param   s   the detail message.
     */
    public NullPointerException(String s) {
        extendedMessageState = 0;
        super(s);
        extendedMessageState |= CONSTRUCTOR_FINISHED;
    }


    // Creates an NPE with a custom backtrace configuration.
    // The exception has no message if detailed NPE is not enabled.
    NullPointerException(int stackOffset, int searchSlot) {
        extendedMessageState = setupCustomBackTrace(stackOffset, searchSlot);
        super();
        extendedMessageState |= CONSTRUCTOR_FINISHED;
    }

    private static int setupCustomBackTrace(int stackOffset, int searchSlot) {
        return CUSTOM_TRACE
                | encode(stackOffset, STACK_OFFSET_SIZE, STACK_OFFSET_SHIFT)
                | encode(searchSlot, SEARCH_SLOT_SIZE, SEARCH_SLOT_SHIFT);
    }

    private static final int
            CONSTRUCTOR_FINISHED = 0x1,
            MESSAGE_COMPUTED = 0x2,
            CUSTOM_TRACE = 0x4;
    private static final int
            STACK_OFFSET_SHIFT = 4,
            STACK_OFFSET_SIZE = 4,
            SEARCH_SLOT_SHIFT = 8,
            SEARCH_SLOT_SIZE = 4;

    private static int encode(int data, int size, int shift) {
        int max = (1 << size) - 1;
        if ((data & ~max) != 0)
            throw new InternalError(); // bad arguments from trusted callers
        return ((data & max) << shift);
    }

    private static int decode(int encoded, int size, int shift) {
        int max = (1 << size) - 1;
        return (encoded >> shift) & max;
    }

    // Access these fields only while holding this object's monitor lock.
    private transient int extendedMessageState;
    private transient String extendedMessage;

    /**
     * {@inheritDoc}
     */
    public synchronized Throwable fillInStackTrace() {
        // If the stack trace is changed the extended NPE algorithm
        // will compute a wrong message. So compute it beforehand.
        ensureMessageComputed();
        return super.fillInStackTrace();
    }

    /**
     * Returns the detail message string of this throwable.
     *
     * <p> If a non-null message was supplied in a constructor it is
     * returned. Otherwise, an implementation specific message or
     * {@code null} is returned.
     *
     * @implNote
     * If no explicit message was passed to the constructor, and as
     * long as certain internal information is available, a verbose
     * description of the null reference is returned.
     * The internal information is not available in deserialized
     * NullPointerExceptions.
     *
     * @return the detail message string, which may be {@code null}.
     */
    public String getMessage() {
        String message = super.getMessage();
        if (message == null) {
            synchronized(this) {
                ensureMessageComputed();
                return extendedMessage;
            }
        }
        return message;
    }

    // Must be called only while holding this object's monitor lock.
    private void ensureMessageComputed() {
        if ((extendedMessageState & (MESSAGE_COMPUTED | CONSTRUCTOR_FINISHED)) == CONSTRUCTOR_FINISHED) {
            int stackOffset = decode(extendedMessageState, STACK_OFFSET_SIZE, STACK_OFFSET_SHIFT);
            int searchSlot = (extendedMessageState & CUSTOM_TRACE) != 0
                    ? decode(extendedMessageState, SEARCH_SLOT_SIZE, SEARCH_SLOT_SHIFT)
                    : -1;
            extendedMessage = getExtendedNPEMessage(stackOffset, searchSlot);
            extendedMessageState |= MESSAGE_COMPUTED;
        }
    }

    // Must be called only while holding this object's monitor lock.
    // Gets an extended exception message. There are two modes:
    // 1. `searchSlot >= 0`, follow the explicit stack offset and search slot
    //    configurations to trace how a particular argument, which turns out to
    //    be `null`, was evaluated.
    // 2. `searchSlot == -1`, stack offset is 0 (a call to the nullary constructor)
    //    and the search slot will be derived by bytecode tracing.  The message
    //    will also include the action that caused the NPE along with the source
    //    of the `null`.
    // If the backtracking cannot find a verifiable result, this method returns `null`.
    private native String getExtendedNPEMessage(int stackOffset, int searchSlot);
}
