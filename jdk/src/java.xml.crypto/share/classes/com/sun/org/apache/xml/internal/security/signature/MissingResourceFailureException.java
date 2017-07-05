/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sun.org.apache.xml.internal.security.signature;

/**
 * Thrown by {@link com.sun.org.apache.xml.internal.security.signature.SignedInfo#verify()} when
 * testing the signature fails because of uninitialized
 * {@link com.sun.org.apache.xml.internal.security.signature.Reference}s.
 *
 * @author Christian Geuer-Pollmann
 * @see ReferenceNotInitializedException
 */
public class MissingResourceFailureException extends XMLSignatureException {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    /** Field uninitializedReference */
    private Reference uninitializedReference = null;

    /**
     * MissingKeyResourceFailureException constructor.
     * @param msgID
     * @param reference
     * @see #getReference
     */
    public MissingResourceFailureException(String msgID, Reference reference) {
        super(msgID);

        this.uninitializedReference = reference;
    }

    /**
     * Constructor MissingResourceFailureException
     *
     * @param msgID
     * @param exArgs
     * @param reference
     * @see #getReference
     */
    public MissingResourceFailureException(String msgID, Object exArgs[], Reference reference) {
        super(msgID, exArgs);

        this.uninitializedReference = reference;
    }

    /**
     * Constructor MissingResourceFailureException
     *
     * @param msgID
     * @param originalException
     * @param reference
     * @see #getReference
     */
    public MissingResourceFailureException(
        String msgID, Exception originalException, Reference reference
    ) {
        super(msgID, originalException);

        this.uninitializedReference = reference;
    }

    /**
     * Constructor MissingResourceFailureException
     *
     * @param msgID
     * @param exArgs
     * @param originalException
     * @param reference
     * @see #getReference
     */
    public MissingResourceFailureException(
        String msgID, Object exArgs[], Exception originalException, Reference reference
    ) {
        super(msgID, exArgs, originalException);

        this.uninitializedReference = reference;
    }

    /**
     * used to set the uninitialized {@link com.sun.org.apache.xml.internal.security.signature.Reference}
     *
     * @param reference the Reference object
     * @see #getReference
     */
    public void setReference(Reference reference) {
        this.uninitializedReference = reference;
    }

    /**
     * used to get the uninitialized {@link com.sun.org.apache.xml.internal.security.signature.Reference}
     *
     * This allows to supply the correct {@link com.sun.org.apache.xml.internal.security.signature.XMLSignatureInput}
     * to the {@link com.sun.org.apache.xml.internal.security.signature.Reference} to try again verification.
     *
     * @return the Reference object
     * @see #setReference
     */
    public Reference getReference() {
        return this.uninitializedReference;
    }
}
