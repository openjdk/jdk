/*
 * Copyright (c) 2003, 2011, Oracle and/or its affiliates. All rights reserved.
 */

/* Copyright  (c) 2002 Graz University of Technology. All rights reserved.
 *
 * Redistribution and use in  source and binary forms, with or without
 * modification, are permitted  provided that the following conditions are met:
 *
 * 1. Redistributions of  source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in  binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. The end-user documentation included with the redistribution, if any, must
 *    include the following acknowledgment:
 *
 *    "This product includes software developed by IAIK of Graz University of
 *     Technology."
 *
 *    Alternately, this acknowledgment may appear in the software itself, if
 *    and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Graz University of Technology" and "IAIK of Graz University of
 *    Technology" must not be used to endorse or promote products derived from
 *    this software without prior written permission.
 *
 * 5. Products derived from this software may not be called
 *    "IAIK PKCS Wrapper", nor may "IAIK" appear in their name, without prior
 *    written permission of Graz University of Technology.
 *
 *  THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESSED OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 *  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE LICENSOR BE
 *  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY,
 *  OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 *  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 *  OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 *  ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 *  OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 *  OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *  POSSIBILITY  OF SUCH DAMAGE.
 */

package sun.security.pkcs11.wrapper;

import java.util.*;


/**
 * This is the superclass of all checked exceptions used by this package. An
 * exception of this class indicates that a function call to the underlying
 * PKCS#11 module returned a value not equal to CKR_OK. The application can get
 * the returned value by calling getErrorCode(). A return value not equal to
 * CKR_OK is the only reason for such an exception to be thrown.
 * PKCS#11 defines the meaning of an error-code, which may depend on the
 * context in which the error occurs.
 *
 * @author <a href="mailto:Karl.Scheibelhofer@iaik.at"> Karl Scheibelhofer </a>
 * @invariants
 */
public class PKCS11Exception extends Exception {
    private static final long serialVersionUID = 4877072363729195L;

    /**
     * The code of the error which was the reason for this exception.
     */
    protected long errorCode_;

    private static final Map<Long,String> errorMap;

    static {
        int[] errorCodes = new int[] {
            0x00000000,
            0x00000001,
            0x00000002,
            0x00000003,
            0x00000005,
            0x00000006,
            0x00000007,
            0x00000008,
            0x00000009,
            0x0000000A,
            0x00000010,
            0x00000011,
            0x00000012,
            0x00000013,
            0x00000020,
            0x00000021,
            0x00000030,
            0x00000031,
            0x00000032,
            0x00000040,
            0x00000041,
            0x00000050,
            0x00000051,
            0x00000054,
            0x00000060,
            0x00000062,
            0x00000063,
            0x00000064,
            0x00000065,
            0x00000066,
            0x00000067,
            0x00000068,
            0x00000069,
            0x0000006A,
            0x00000070,
            0x00000071,
            0x00000082,
            0x00000090,
            0x00000091,
            0x000000A0,
            0x000000A1,
            0x000000A2,
            0x000000A3,
            0x000000A4,
            0x000000B0,
            0x000000B1,
            0x000000B3,
            0x000000B4,
            0x000000B5,
            0x000000B6,
            0x000000B7,
            0x000000B8,
            0x000000C0,
            0x000000C1,
            0x000000D0,
            0x000000D1,
            0x000000E0,
            0x000000E1,
            0x000000E2,
            0x000000F0,
            0x000000F1,
            0x000000F2,
            0x00000100,
            0x00000101,
            0x00000102,
            0x00000103,
            0x00000104,
            0x00000105,
            0x00000110,
            0x00000112,
            0x00000113,
            0x00000114,
            0x00000115,
            0x00000120,
            0x00000121,
            0x00000130,
            0x00000150,
            0x00000160,
            0x00000170,
            0x00000180,
            0x00000190,
            0x00000191,
            0x000001A0,
            0x000001A1,
            0x00000200,
            0x80000000,
        };
        String[] errorMessages = new String[] {
            "CKR_OK",
            "CKR_CANCEL",
            "CKR_HOST_MEMORY",
            "CKR_SLOT_ID_INVALID",
            "CKR_GENERAL_ERROR",
            "CKR_FUNCTION_FAILED",
            "CKR_ARGUMENTS_BAD",
            "CKR_NO_EVENT",
            "CKR_NEED_TO_CREATE_THREADS",
            "CKR_CANT_LOCK",
            "CKR_ATTRIBUTE_READ_ONLY",
            "CKR_ATTRIBUTE_SENSITIVE",
            "CKR_ATTRIBUTE_TYPE_INVALID",
            "CKR_ATTRIBUTE_VALUE_INVALID",
            "CKR_DATA_INVALID",
            "CKR_DATA_LEN_RANGE",
            "CKR_DEVICE_ERROR",
            "CKR_DEVICE_MEMORY",
            "CKR_DEVICE_REMOVED",
            "CKR_ENCRYPTED_DATA_INVALID",
            "CKR_ENCRYPTED_DATA_LEN_RANGE",
            "CKR_FUNCTION_CANCELED",
            "CKR_FUNCTION_NOT_PARALLEL",
            "CKR_FUNCTION_NOT_SUPPORTED",
            "CKR_KEY_HANDLE_INVALID",
            "CKR_KEY_SIZE_RANGE",
            "CKR_KEY_TYPE_INCONSISTENT",
            "CKR_KEY_NOT_NEEDED",
            "CKR_KEY_CHANGED",
            "CKR_KEY_NEEDED",
            "CKR_KEY_INDIGESTIBLE",
            "CKR_KEY_FUNCTION_NOT_PERMITTED",
            "CKR_KEY_NOT_WRAPPABLE",
            "CKR_KEY_UNEXTRACTABLE",
            "CKR_MECHANISM_INVALID",
            "CKR_MECHANISM_PARAM_INVALID",
            "CKR_OBJECT_HANDLE_INVALID",
            "CKR_OPERATION_ACTIVE",
            "CKR_OPERATION_NOT_INITIALIZED",
            "CKR_PIN_INCORRECT",
            "CKR_PIN_INVALID",
            "CKR_PIN_LEN_RANGE",
            "CKR_PIN_EXPIRED",
            "CKR_PIN_LOCKED",
            "CKR_SESSION_CLOSED",
            "CKR_SESSION_COUNT",
            "CKR_SESSION_HANDLE_INVALID",
            "CKR_SESSION_PARALLEL_NOT_SUPPORTED",
            "CKR_SESSION_READ_ONLY",
            "CKR_SESSION_EXISTS",
            "CKR_SESSION_READ_ONLY_EXISTS",
            "CKR_SESSION_READ_WRITE_SO_EXISTS",
            "CKR_SIGNATURE_INVALID",
            "CKR_SIGNATURE_LEN_RANGE",
            "CKR_TEMPLATE_INCOMPLETE",
            "CKR_TEMPLATE_INCONSISTENT",
            "CKR_TOKEN_NOT_PRESENT",
            "CKR_TOKEN_NOT_RECOGNIZED",
            "CKR_TOKEN_WRITE_PROTECTED",
            "CKR_UNWRAPPING_KEY_HANDLE_INVALID",
            "CKR_UNWRAPPING_KEY_SIZE_RANGE",
            "CKR_UNWRAPPING_KEY_TYPE_INCONSISTENT",
            "CKR_USER_ALREADY_LOGGED_IN",
            "CKR_USER_NOT_LOGGED_IN",
            "CKR_USER_PIN_NOT_INITIALIZED",
            "CKR_USER_TYPE_INVALID",
            "CKR_USER_ANOTHER_ALREADY_LOGGED_IN",
            "CKR_USER_TOO_MANY_TYPES",
            "CKR_WRAPPED_KEY_INVALID",
            "CKR_WRAPPED_KEY_LEN_RANGE",
            "CKR_WRAPPING_KEY_HANDLE_INVALID",
            "CKR_WRAPPING_KEY_SIZE_RANGE",
            "CKR_WRAPPING_KEY_TYPE_INCONSISTENT",
            "CKR_RANDOM_SEED_NOT_SUPPORTED",
            "CKR_RANDOM_NO_RNG",
            "CKR_DOMAIN_PARAMS_INVALID",
            "CKR_BUFFER_TOO_SMALL",
            "CKR_SAVED_STATE_INVALID",
            "CKR_INFORMATION_SENSITIVE",
            "CKR_STATE_UNSAVEABLE",
            "CKR_CRYPTOKI_NOT_INITIALIZED",
            "CKR_CRYPTOKI_ALREADY_INITIALIZED",
            "CKR_MUTEX_BAD",
            "CKR_MUTEX_NOT_LOCKED",
            "CKR_FUNCTION_REJECTED",
            "CKR_VENDOR_DEFINED",
        };
        errorMap = new HashMap<Long,String>();
        for (int i = 0; i < errorCodes.length; i++) {
            errorMap.put(Long.valueOf(errorCodes[i]), errorMessages[i]);
        }
    }


    /**
     * Constructor taking the error code as defined for the CKR_* constants
     * in PKCS#11.
     */
    public PKCS11Exception(long errorCode) {
        errorCode_ = errorCode;
    }

    /**
     * This method gets the corresponding text error message from
     * a property file. If this file is not available, it returns the error
     * code as a hex-string.
     *
     * @return The message or the error code; e.g. "CKR_DEVICE_ERROR" or
     *         "0x00000030".
     * @preconditions
     * @postconditions (result <> null)
     */
    public String getMessage() {
        String message = errorMap.get(Long.valueOf(errorCode_));
        if (message == null) {
            message = "0x" + Functions.toFullHexString((int)errorCode_);
        }
        return message;
    }

    /**
     * Returns the PKCS#11 error code.
     *
     * @return The error code; e.g. 0x00000030.
     * @preconditions
     * @postconditions
     */
    public long getErrorCode() {
        return errorCode_ ;
    }

}
