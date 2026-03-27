/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.utils;

import java.io.IOException;

/**
 * Exception thrown when attempting to use a closed resource.
 *
 * <p>
 * The ClosedException is thrown when an operation is attempted on a resource
 * (such as a terminal, reader, or writer) that has been closed. This exception
 * extends IOException and provides the same constructors for different ways of
 * specifying the error message and cause.
 * </p>
 *
 * <p>
 * This exception is typically thrown by JLine components when methods are called
 * after the component has been closed, such as attempting to read from a closed
 * terminal or write to a closed output stream.
 * </p>
 */
public class ClosedException extends IOException {

    private static final long serialVersionUID = 3085420657077696L;

    public ClosedException() {}

    public ClosedException(String message) {
        super(message);
    }

    public ClosedException(String message, Throwable cause) {
        super(message, cause);
    }

    public ClosedException(Throwable cause) {
        super(cause);
    }
}
