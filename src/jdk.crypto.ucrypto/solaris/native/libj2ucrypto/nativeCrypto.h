/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

#include <libsoftcrypto.h> // redirects to libucrypto.h starting 11.3

#ifndef _Included_com_oracle_security_ucrypto_NativeCrypto
#define _Included_com_oracle_security_ucrypto_NativeCrypto
#ifdef __cplusplus
extern "C" {
#endif

// used by nativeCryptoMD.c
#undef com_oracle_security_ucrypto_NativeDigestMD_MECH_MD5
#define com_oracle_security_ucrypto_NativeDigestMD_MECH_MD5 1L
#undef com_oracle_security_ucrypto_NativeDigestMD_MECH_SHA1
#define com_oracle_security_ucrypto_NativeDigestMD_MECH_SHA1 2L
#undef com_oracle_security_ucrypto_NativeDigestMD_MECH_SHA256
#define com_oracle_security_ucrypto_NativeDigestMD_MECH_SHA256 3L
#undef com_oracle_security_ucrypto_NativeDigestMD_MECH_SHA224
#define com_oracle_security_ucrypto_NativeDigestMD_MECH_SHA224 4L
#undef com_oracle_security_ucrypto_NativeDigestMD_MECH_SHA384
#define com_oracle_security_ucrypto_NativeDigestMD_MECH_SHA384 5L
#undef com_oracle_security_ucrypto_NativeDigestMD_MECH_SHA512
#define com_oracle_security_ucrypto_NativeDigestMD_MECH_SHA512 6L

#define J2UC_DEBUG 0

#ifdef __cplusplus
}
#endif
#endif
