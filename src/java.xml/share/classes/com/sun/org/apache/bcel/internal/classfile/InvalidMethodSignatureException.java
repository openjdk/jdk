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
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.sun.org.apache.bcel.internal.classfile;

/**
 * Thrown when the BCEL attempts to read a class file and determines that a class is malformed or otherwise cannot be interpreted as a class file.
 *
 * @since 6.8.0
 */
public class InvalidMethodSignatureException extends ClassFormatException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new instance with the specified invalid signature as the message.
     *
     * @param signature The invalid signature is saved for later retrieval by the {@link #getMessage()} method.
     */
    public InvalidMethodSignatureException(final String signature) {
        super(signature);
    }

    /**
     * Constructs a new instance with the specified invalid signature as the message and a cause.
     *
     * @param signature The invalid signature is saved for later retrieval by the {@link #getMessage()} method.
     * @param cause     the cause (which is saved for later retrieval by the {@link #getCause()} method). A {@code null} value is permitted, and indicates that
     *                  the cause is nonexistent or unknown.
     */
    public InvalidMethodSignatureException(final String signature, final Throwable cause) {
        super(signature, cause);
    }

}
