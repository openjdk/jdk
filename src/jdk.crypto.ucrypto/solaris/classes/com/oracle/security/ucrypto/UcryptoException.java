/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.security.ucrypto;

import java.util.*;
import java.security.ProviderException;

/**
 * The exception class used by SunUcrypto provider. An exception
 * object of this class indicates that a function call to the underlying
 * native calls returned a value not equal to CRYPTO_SUCCESS.
 *
 * @since 9
 */
public final class UcryptoException extends ProviderException {

    private static final long serialVersionUID = -933864511110035746L;

    // NOTE: check /usr/include/sys/crypto/common.h for updates
    private static final String[] ERROR_MSG = {
        "CRYPTO_SUCCESS",
        "CRYPTO_CANCEL",
        "CRYPTO_HOST_MEMORY",
        "CRYPTO_GENERAL_ERROR",
        "CRYPTO_FAILED",
        "CRYPTO_ARGUMENTS_BAD",
        "CRYPTO_ATTRIBUTE_READ_ONLY",
        "CRYPTO_ATTRIBUTE_SENSITIVE",
        "CRYPTO_ATTRIBUTE_TYPE_INVALID",
        "CRYPTO_ATTRIBUTE_VALUE_INVALID",
        "CRYPTO_CANCELED",
        "CRYPTO_DATA_INVALID",
        "CRYPTO_DATA_LEN_RANGE",
        "CRYPTO_DEVICE_ERROR",
        "CRYPTO_DEVICE_MEMORY",
        "CRYPTO_DEVICE_REMOVED",
        "CRYPTO_ENCRYPTED_DATA_INVALID",
        "CRYPTO_ENCRYPTED_DATA_LEN_RANGE",
        "CRYPTO_KEY_HANDLE_INVALID",
        "CRYPTO_KEY_SIZE_RANGE",
        "CRYPTO_KEY_TYPE_INCONSISTENT",
        "CRYPTO_KEY_NOT_NEEDED",
        "CRYPTO_KEY_CHANGED",
        "CRYPTO_KEY_NEEDED",
        "CRYPTO_KEY_INDIGESTIBLE",
        "CRYPTO_KEY_FUNCTION_NOT_PERMITTED",
        "CRYPTO_KEY_NOT_WRAPPABLE",
        "CRYPTO_KEY_UNEXTRACTABLE",
        "CRYPTO_MECHANISM_INVALID",
        "CRYPTO_MECHANISM_PARAM_INVALID",
        "CRYPTO_OBJECT_HANDLE_INVALID",
        "CRYPTO_OPERATION_IS_ACTIVE",
        "CRYPTO_OPERATION_NOT_INITIALIZED",
        "CRYPTO_PIN_INCORRECT",
        "CRYPTO_PIN_INVALID",
        "CRYPTO_PIN_LEN_RANGE",
        "CRYPTO_PIN_EXPIRED",
        "CRYPTO_PIN_LOCKED",
        "CRYPTO_SESSION_CLOSED",
        "CRYPTO_SESSION_COUNT",
        "CRYPTO_SESSION_HANDLE_INVALID",
        "CRYPTO_SESSION_READ_ONLY",
        "CRYPTO_SESSION_EXISTS",
        "CRYPTO_SESSION_READ_ONLY_EXISTS",
        "CRYPTO_SESSION_READ_WRITE_SO_EXISTS",
        "CRYPTO_SIGNATURE_INVALID",
        "CRYPTO_SIGNATURE_LEN_RANGE",
        "CRYPTO_TEMPLATE_INCOMPLETE",
        "CRYPTO_TEMPLATE_INCONSISTENT",
        "CRYPTO_UNWRAPPING_KEY_HANDLE_INVALID",
        "CRYPTO_UNWRAPPING_KEY_SIZE_RANGE",
        "CRYPTO_UNWRAPPING_KEY_TYPE_INCONSISTENT",
        "CRYPTO_USER_ALREADY_LOGGED_IN",
        "CRYPTO_USER_NOT_LOGGED_IN",
        "CRYPTO_USER_PIN_NOT_INITIALIZED",
        "CRYPTO_USER_TYPE_INVALID",
        "CRYPTO_USER_ANOTHER_ALREADY_LOGGED_IN",
        "CRYPTO_USER_TOO_MANY_TYPES",
        "CRYPTO_WRAPPED_KEY_INVALID",
        "CRYPTO_WRAPPED_KEY_LEN_RANGE",
        "CRYPTO_WRAPPING_KEY_HANDLE_INVALID",
        "CRYPTO_WRAPPING_KEY_SIZE_RANGE",
        "CRYPTO_WRAPPING_KEY_TYPE_INCONSISTENT",
        "CRYPTO_RANDOM_SEED_NOT_SUPPORTED",
        "CRYPTO_RANDOM_NO_RNG",
        "CRYPTO_DOMAIN_PARAMS_INVALID",
        "CRYPTO_BUFFER_TOO_SMALL",
        "CRYPTO_INFORMATION_SENSITIVE",
        "CRYPTO_NOT_SUPPORTED",
        "CRYPTO_QUEUED",
        "CRYPTO_BUFFER_TOO_BIG",
        "CRYPTO_INVALID_CONTEXT",
        "CRYPTO_INVALID_MAC",
        "CRYPTO_MECH_NOT_SUPPORTED",
        "CRYPTO_INCONSISTENT_ATTRIBUTE",
        "CRYPTO_NO_PERMISSION",
        "CRYPTO_INVALID_PROVIDER_ID",
        "CRYPTO_VERSION_MISMATCH",
        "CRYPTO_BUSY",
        "CRYPTO_UNKNOWN_PROVIDER",
        "CRYPTO_MODVERIFICATION_FAILED",
        "CRYPTO_OLD_CTX_TEMPLATE",
        "CRYPTO_WEAK_KEY",
        "CRYPTO_FIPS140_ERROR"
    };

    /**
     * The error code if this exception is triggered by a Ucrypto error.
     */
    private final int errorCode;

    /**
     * This method gets the corresponding text error message from a
     * predefined mapping. If mapping is not found, then it returns the error
     * code as a hex-string.
     *
     * @return The message or the error code; e.g. "CRYPTO_DATA_INVALID" or
     *         "0x88".
     */
    static String getErrorMessage(int errorCode) {
        String message;
        if (errorCode < ERROR_MSG.length) {
            message = ERROR_MSG[errorCode];
        } else {
            message = "0x" + Integer.toHexString(errorCode);
        }
        return message;
    }

    /**
     * Constructor taking the error code as defined for the CRYPTO_* constants
     */
    public UcryptoException(int rv) {
        super(getErrorMessage(rv));
        this.errorCode = rv;
    }

    public UcryptoException(String message) {
        super(message);
        errorCode = -1;
    }

    public UcryptoException(String message, Throwable cause) {
        super(message, cause);
        errorCode = -1;
    }

    /**
     * Returns the Ucrypto error code.
     *
     * @return The error code.
     */
    public int getErrorCode() {
        return errorCode;
    }
}
