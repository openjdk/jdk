/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.classfile.impl.verifier;

/// Relevant parts from `signatures.cpp`, such as `SignatureStream`.
final class VerificationSignature {

    enum BasicType {
        T_BOOLEAN(4),
        T_CHAR(5),
        T_FLOAT(6),
        T_DOUBLE(7),
        T_BYTE(8),
        T_SHORT(9),
        T_INT(10),
        T_LONG(11),
        T_OBJECT(12),
        T_ARRAY(13),
        T_VOID(14),
        T_ADDRESS(15),
        T_NARROWOOP(16),
        T_METADATA(17),
        T_NARROWKLASS(18),
        T_CONFLICT(19),
        T_ILLEGAL(99);

        final int type;

        BasicType(int type) {
            this.type = type;
        }

        static BasicType fromSignature(char ch) {
            return switch (ch) {
                case JVM_SIGNATURE_BOOLEAN ->
                    T_BOOLEAN;
                case JVM_SIGNATURE_CHAR ->
                    T_CHAR;
                case JVM_SIGNATURE_FLOAT ->
                    T_FLOAT;
                case JVM_SIGNATURE_DOUBLE ->
                    T_DOUBLE;
                case JVM_SIGNATURE_BYTE ->
                    T_BYTE;
                case JVM_SIGNATURE_SHORT ->
                    T_SHORT;
                case JVM_SIGNATURE_INT ->
                    T_INT;
                case JVM_SIGNATURE_LONG ->
                    T_LONG;
                case JVM_SIGNATURE_CLASS ->
                    T_OBJECT;
                case JVM_SIGNATURE_ARRAY ->
                    T_ARRAY;
                case JVM_SIGNATURE_VOID ->
                    T_VOID;
                default ->
                    throw new IllegalArgumentException("Not a valid type: '" + ch + "'");
            };
        }
    }

    static final char JVM_SIGNATURE_DOT = '.',
            JVM_SIGNATURE_ARRAY = '[',
            JVM_SIGNATURE_BYTE = 'B',
            JVM_SIGNATURE_CHAR = 'C',
            JVM_SIGNATURE_CLASS = 'L',
            JVM_SIGNATURE_ENDCLASS = ';',
            JVM_SIGNATURE_FLOAT = 'F',
            JVM_SIGNATURE_DOUBLE = 'D',
            JVM_SIGNATURE_FUNC = '(',
            JVM_SIGNATURE_ENDFUNC = ')',
            JVM_SIGNATURE_INT = 'I',
            JVM_SIGNATURE_LONG = 'J',
            JVM_SIGNATURE_SHORT = 'S',
            JVM_SIGNATURE_VOID = 'V',
            JVM_SIGNATURE_BOOLEAN = 'Z';

    static boolean isReferenceType(BasicType t) {
        return t == BasicType.T_OBJECT || t == BasicType.T_ARRAY;
    }

    private static final char TYPE2CHAR_TAB[] = new char[]{
        0, 0, 0, 0,
        JVM_SIGNATURE_BOOLEAN, JVM_SIGNATURE_CHAR,
        JVM_SIGNATURE_FLOAT, JVM_SIGNATURE_DOUBLE,
        JVM_SIGNATURE_BYTE, JVM_SIGNATURE_SHORT,
        JVM_SIGNATURE_INT, JVM_SIGNATURE_LONG,
        JVM_SIGNATURE_CLASS, JVM_SIGNATURE_ARRAY,
        JVM_SIGNATURE_VOID, 0,
        0, 0, 0, 0
    };

    static boolean hasEnvelope(char signature_char) {
        return signature_char == JVM_SIGNATURE_CLASS;
    }

    private BasicType type;
    private final String signature;
    private final int limit;
    private int begin, end, arrayPrefix, state;

    private static final int S_FIELD = 0, S_METHOD = 1, S_METHOD_RETURN = 3;

    boolean atReturnType() {
        return state == S_METHOD_RETURN;
    }

    boolean isReference() {
        return isReferenceType(type);
    }

    BasicType type() {
        return type;
    }

    private int rawSymbolBegin() {
        return begin + (hasEnvelope() ? 1 : 0);
    }

    private int rawSymbolEnd() {
        return end - (hasEnvelope() ? 1 : 0);
    }

    private boolean hasEnvelope() {
        return hasEnvelope(signature.charAt(begin));
    }

    String asSymbol() {
        int begin = rawSymbolBegin();
        int end = rawSymbolEnd();
        return signature.substring(begin, end);
    }

    int skipArrayPrefix(int max_skip_length) {
        if (type != BasicType.T_ARRAY) {
            return 0;
        }
        if (arrayPrefix > max_skip_length) {
            // strip some but not all levels of T_ARRAY
            arrayPrefix -= max_skip_length;
            begin += max_skip_length;
            return max_skip_length;
        }
        return skipWholeArrayPrefix();
    }

    static BasicType decodeSignatureChar(char ch) {
        return BasicType.fromSignature(ch);
    }

    private final VerifierImpl context;

    VerificationSignature(String signature, boolean is_method, VerifierImpl context) {
        this.signature = signature;
        this.limit = signature.length();
        int oz = is_method ? S_METHOD : S_FIELD;
        this.state = oz;
        this.begin = this.end = oz;
        this.arrayPrefix = 0;
        this.context = context;
        next();
    }

    private int scanType(BasicType type) {
        int e = end;
        int tem;
        switch (type) {
            case T_OBJECT:
                tem = signature.indexOf(JVM_SIGNATURE_ENDCLASS, e);
                return tem < 0 ? limit : tem + 1;
            case T_ARRAY:
                while (e < limit && signature.charAt(e) == JVM_SIGNATURE_ARRAY) {
                    e++;
                }
                arrayPrefix = e - end;
                if (hasEnvelope(signature.charAt(e))) {
                    tem = signature.indexOf(JVM_SIGNATURE_ENDCLASS, e);
                    return tem < 0 ? limit : tem + 1;
                }
                return e + 1;
            default:
                return e + 1;
        }
    }

    void next() {
        final String sig = signature;
        int len = limit;
        testLen(len);
        begin = end;
        char ch = sig.charAt(begin);
        if (ch == JVM_SIGNATURE_ENDFUNC) {
            state = S_METHOD_RETURN;
            begin = ++end;
            testLen(len);
            ch = sig.charAt(begin);
        }
        try {
            BasicType bt = decodeSignatureChar(ch);
            type = bt;
            end = scanType(bt);
        } catch (IllegalArgumentException iae) {
            throw new IllegalArgumentException("Not a valid signature: '" + signature + "'", iae);
        }
    }

    private void testLen(int len) {
        if (end >= len) {
            if (context == null) {
                throw new IllegalArgumentException("Invalid signature " + signature);
            } else {
                context.verifyError("Invalid signature " + signature);
            }
        }
    }

    int skipWholeArrayPrefix() {
        int whole_array_prefix = arrayPrefix;
        int new_begin = begin + whole_array_prefix;
        begin = new_begin;
        char ch = signature.charAt(new_begin);
        BasicType bt = decodeSignatureChar(ch);
        type = bt;
        return whole_array_prefix;
    }

    @SuppressWarnings("fallthrough")
    static int isValidType(String type, int limit) {
        int index = 0;

        // Iterate over any number of array dimensions
        while (index < limit && type.charAt(index) == JVM_SIGNATURE_ARRAY) {
            ++index;
        }
        if (index >= limit) {
            return -1;
        }
        switch (type.charAt(index)) {
            case JVM_SIGNATURE_BYTE:
            case JVM_SIGNATURE_CHAR:
            case JVM_SIGNATURE_FLOAT:
            case JVM_SIGNATURE_DOUBLE:
            case JVM_SIGNATURE_INT:
            case JVM_SIGNATURE_LONG:
            case JVM_SIGNATURE_SHORT:
            case JVM_SIGNATURE_BOOLEAN:
            case JVM_SIGNATURE_VOID:
                return index + 1;
            case JVM_SIGNATURE_CLASS:
                for (index = index + 1; index < limit; ++index) {
                    char c = type.charAt(index);
                    switch (c) {
                        case JVM_SIGNATURE_ENDCLASS:
                            return index + 1;
                        case '\0':
                        case JVM_SIGNATURE_DOT:
                        case JVM_SIGNATURE_ARRAY:
                            return -1;
                        default: ; // fall through
                        }
                }
            // fall through
            default: ; // fall through
            }
        return -1;
    }

    static boolean isValidMethodSignature(String method_sig) {
        if (method_sig != null) {
            int len = method_sig.length();
            int index = 0;
            if (len > 1 && method_sig.charAt(index) == JVM_SIGNATURE_FUNC) {
                ++index;
                while (index < len && method_sig.charAt(index) != JVM_SIGNATURE_ENDFUNC) {
                    int res = isValidType(method_sig.substring(index), len - index);
                    if (res == -1) {
                        return false;
                    } else {
                        index += res;
                    }
                }
                if (index < len && method_sig.charAt(index) == JVM_SIGNATURE_ENDFUNC) {
                    // check the return type
                    ++index;
                    return (isValidType(method_sig.substring(index), len - index) == (len - index));
                }
            }
        }
        return false;
    }

    static boolean isValidTypeSignature(String sig) {
        if (sig == null) return false;
        int len = sig.length();
        return (len >= 1 && (isValidType(sig, len) == len));
    }
}
