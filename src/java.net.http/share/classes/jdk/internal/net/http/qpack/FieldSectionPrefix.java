/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.http.qpack;

import jdk.internal.net.http.http3.Http3Error;

public record FieldSectionPrefix(long requiredInsertCount, long base) {

    public static FieldSectionPrefix decode(long encodedRIC, long deltaBase,
                                            int baseSign, DynamicTable dynamicTable,
                                            DecodingCallback callback) {
        long decodedRIC = decodeRIC(encodedRIC, dynamicTable, callback);
        long decodedBase = decodeBase(decodedRIC, deltaBase, baseSign);
        return new FieldSectionPrefix(decodedRIC, decodedBase);
    }

    private static long decodeRIC(long encodedRIC, DynamicTable dynamicTable, DecodingCallback callback) {
        if (encodedRIC == 0) {
            return 0;
        }
        long maxEntries = dynamicTable.maxEntries();
        long insertCount = dynamicTable.insertCount();
        long fullRange = 2 * maxEntries;
        if (encodedRIC > fullRange) {
            throw decompressionFailed(callback);
        }
        long maxValue = insertCount + maxEntries;
        long maxWrapped = (maxValue/fullRange) * fullRange;
        long ric = maxWrapped + encodedRIC - 1;
        if (ric > maxValue) {
            if (ric <= fullRange) {
                throw decompressionFailed(callback);
            }
            ric -= fullRange;
        }

        if (ric == 0) {
            throw decompressionFailed(callback);
        }
        return ric;
    }

    private static long decodeBase(long decodedRic, long deltaBase, int signBit) {
        if (signBit == 0) {
            return decodedRic + deltaBase;
        } else {
            return decodedRic - deltaBase - 1;
        }
    }

    private static IllegalStateException decompressionFailed(DecodingCallback callback) {
        var decompressionFailed = new IllegalStateException("QPACK decompression failed");
        callback.onError(decompressionFailed, Http3Error.QPACK_DECOMPRESSION_FAILED);
        return decompressionFailed;
    }
}
