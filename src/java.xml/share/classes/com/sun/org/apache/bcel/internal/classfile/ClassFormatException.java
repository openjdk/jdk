/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.bcel.internal.classfile;

/**
 * Thrown when the BCEL attempts to read a class file and determines that a class is malformed or otherwise cannot be interpreted as a class file.
 */
public class ClassFormatException extends RuntimeException {

    private static final long serialVersionUID = -3569097343160139969L;

    /**
     * Constructs a new instance with {@code null} as its detail message. The cause is not initialized, and may subsequently be initialized by a call to
     * {@link #initCause}.
     */
    public ClassFormatException() {
    }

    /**
     * Constructs a new instance with the specified detail message. The cause is not initialized, and may subsequently be initialized by a call to
     * {@link #initCause}.
     *
     * @param message the detail message. The detail message is saved for later retrieval by the {@link #getMessage()} method.
     */
    public ClassFormatException(final String message) {
        super(message);
    }

    /**
     * Constructs a new instance with the specified detail message and cause.
     * <p>
     * Note that the detail message associated with {@code cause} is <i>not</i> automatically incorporated in this runtime exception's detail message.
     *
     * @param message the detail message (which is saved for later retrieval by the {@link #getMessage()} method).
     * @param cause   the cause (which is saved for later retrieval by the {@link #getCause()} method). (A {@code null} value is permitted, and indicates that
     *                the cause is nonexistent or unknown.)
     * @since 6.0
     */
    public ClassFormatException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new instance with the specified cause and a detail message of {@code (cause==null ? null : cause.toString())} (which typically contains the
     * class and detail message of {@code cause}). This constructor is useful for runtime exceptions that are little more than wrappers for other throwables.
     *
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method). (A {@code null} value is permitted, and indicates that the
     *              cause is nonexistent or unknown.)
     * @since 6.7.0
     */
    public ClassFormatException(final Throwable cause) {
        super(cause);
    }
}
