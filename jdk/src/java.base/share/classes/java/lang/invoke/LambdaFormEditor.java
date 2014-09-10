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

package java.lang.invoke;

import java.util.Arrays;
import static java.lang.invoke.LambdaForm.*;
import static java.lang.invoke.LambdaForm.BasicType.*;
import static java.lang.invoke.MethodHandleImpl.Intrinsic;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import sun.invoke.util.Wrapper;

/** Transforms on LFs.
 *  A lambda-form editor can derive new LFs from its base LF.
 *  The editor can cache derived LFs, which simplifies the reuse of their underlying bytecodes.
 *  To support this caching, a LF has an optional pointer to its editor.
 */
class LambdaFormEditor {
    final LambdaForm lambdaForm;

    private LambdaFormEditor(LambdaForm lambdaForm) {
        this.lambdaForm = lambdaForm;
    }

    // Factory method.
    static LambdaFormEditor lambdaFormEditor(LambdaForm lambdaForm) {
        // TO DO:  Consider placing intern logic here, to cut down on duplication.
        // lambdaForm = findPreexistingEquivalent(lambdaForm)
        return new LambdaFormEditor(lambdaForm);
    }

    /** A description of a cached transform, possibly associated with the result of the transform.
     *  The logical content is a sequence of byte values, starting with a Kind.ordinal value.
     *  The sequence is unterminated, ending with an indefinite number of zero bytes.
     *  Sequences that are simple (short enough and with small enough values) pack into a 64-bit long.
     */
    private static final class Transform {
        final long packedBytes;
        final byte[] fullBytes;
        final LambdaForm result;  // result of transform, or null, if there is none available

        private enum Kind {
            NO_KIND,  // necessary because ordinal must be greater than zero
            BIND_ARG, ADD_ARG, DUP_ARG,
            SPREAD_ARGS,
            FILTER_ARG, FILTER_RETURN, FILTER_RETURN_TO_ZERO,
            COLLECT_ARGS, COLLECT_ARGS_TO_VOID, COLLECT_ARGS_TO_ARRAY,
            FOLD_ARGS, FOLD_ARGS_TO_VOID,
            PERMUTE_ARGS
            //maybe add more for guard with test, catch exception, pointwise type conversions
        }

        private static final boolean STRESS_TEST = false; // turn on to disable most packing
        private static final int
                PACKED_BYTE_SIZE = (STRESS_TEST ? 2 : 4),
                PACKED_BYTE_MASK = (1 << PACKED_BYTE_SIZE) - 1,
                PACKED_BYTE_MAX_LENGTH = (STRESS_TEST ? 3 : 64 / PACKED_BYTE_SIZE);

        private static long packedBytes(byte[] bytes) {
            if (bytes.length > PACKED_BYTE_MAX_LENGTH)  return 0;
            long pb = 0;
            int bitset = 0;
            for (int i = 0; i < bytes.length; i++) {
                int b = bytes[i] & 0xFF;
                bitset |= b;
                pb |= (long)b << (i * PACKED_BYTE_SIZE);
            }
            if (!inRange(bitset))
                return 0;
            return pb;
        }
        private static long packedBytes(int b0, int b1) {
            assert(inRange(b0 | b1));
            return (  (b0 << 0*PACKED_BYTE_SIZE)
                    | (b1 << 1*PACKED_BYTE_SIZE));
        }
        private static long packedBytes(int b0, int b1, int b2) {
            assert(inRange(b0 | b1 | b2));
            return (  (b0 << 0*PACKED_BYTE_SIZE)
                    | (b1 << 1*PACKED_BYTE_SIZE)
                    | (b2 << 2*PACKED_BYTE_SIZE));
        }
        private static long packedBytes(int b0, int b1, int b2, int b3) {
            assert(inRange(b0 | b1 | b2 | b3));
            return (  (b0 << 0*PACKED_BYTE_SIZE)
                    | (b1 << 1*PACKED_BYTE_SIZE)
                    | (b2 << 2*PACKED_BYTE_SIZE)
                    | (b3 << 3*PACKED_BYTE_SIZE));
        }
        private static boolean inRange(int bitset) {
            assert((bitset & 0xFF) == bitset);  // incoming values must fit in *unsigned* byte
            return ((bitset & ~PACKED_BYTE_MASK) == 0);
        }
        private static byte[] fullBytes(int... byteValues) {
            byte[] bytes = new byte[byteValues.length];
            int i = 0;
            for (int bv : byteValues) {
                bytes[i++] = bval(bv);
            }
            assert(packedBytes(bytes) == 0);
            return bytes;
        }

        private byte byteAt(int i) {
            long pb = packedBytes;
            if (pb == 0) {
                if (i >= fullBytes.length)  return 0;
                return fullBytes[i];
            }
            assert(fullBytes == null);
            if (i > PACKED_BYTE_MAX_LENGTH)  return 0;
            int pos = (i * PACKED_BYTE_SIZE);
            return (byte)((pb >>> pos) & PACKED_BYTE_MASK);
        }

        Kind kind() { return Kind.values()[byteAt(0)]; }

        private Transform(long packedBytes, byte[] fullBytes, LambdaForm result) {
            this.packedBytes = packedBytes;
            this.fullBytes = fullBytes;
            this.result = result;
        }
        private Transform(long packedBytes) {
            this(packedBytes, null, null);
            assert(packedBytes != 0);
        }
        private Transform(byte[] fullBytes) {
            this(0, fullBytes, null);
        }

        private static byte bval(int b) {
            assert((b & 0xFF) == b);  // incoming value must fit in *unsigned* byte
            return (byte)b;
        }
        private static byte bval(Kind k) {
            return bval(k.ordinal());
        }
        static Transform of(Kind k, int b1) {
            byte b0 = bval(k);
            if (inRange(b0 | b1))
                return new Transform(packedBytes(b0, b1));
            else
                return new Transform(fullBytes(b0, b1));
        }
        static Transform of(Kind k, int b1, int b2) {
            byte b0 = (byte) k.ordinal();
            if (inRange(b0 | b1 | b2))
                return new Transform(packedBytes(b0, b1, b2));
            else
                return new Transform(fullBytes(b0, b1, b2));
        }
        static Transform of(Kind k, int b1, int b2, int b3) {
            byte b0 = (byte) k.ordinal();
            if (inRange(b0 | b1 | b2 | b3))
                return new Transform(packedBytes(b0, b1, b2, b3));
            else
                return new Transform(fullBytes(b0, b1, b2, b3));
        }
        private static final byte[] NO_BYTES = {};
        static Transform of(Kind k, int... b123) {
            return ofBothArrays(k, b123, NO_BYTES);
        }
        static Transform of(Kind k, int b1, byte[] b234) {
            return ofBothArrays(k, new int[]{ b1 }, b234);
        }
        static Transform of(Kind k, int b1, int b2, byte[] b345) {
            return ofBothArrays(k, new int[]{ b1, b2 }, b345);
        }
        private static Transform ofBothArrays(Kind k, int[] b123, byte[] b456) {
            byte[] fullBytes = new byte[1 + b123.length + b456.length];
            int i = 0;
            fullBytes[i++] = bval(k);
            for (int bv : b123) {
                fullBytes[i++] = bval(bv);
            }
            for (byte bv : b456) {
                fullBytes[i++] = bv;
            }
            long packedBytes = packedBytes(fullBytes);
            if (packedBytes != 0)
                return new Transform(packedBytes);
            else
                return new Transform(fullBytes);
        }

        Transform withResult(LambdaForm result) {
            return new Transform(this.packedBytes, this.fullBytes, result);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Transform && equals((Transform)obj);
        }
        public boolean equals(Transform that) {
            return this.packedBytes == that.packedBytes && Arrays.equals(this.fullBytes, that.fullBytes);
        }
        @Override
        public int hashCode() {
            if (packedBytes != 0) {
                assert(fullBytes == null);
                return Long.hashCode(packedBytes);
            }
            return Arrays.hashCode(fullBytes);
        }
        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder();
            long bits = packedBytes;
            if (bits != 0) {
                buf.append("(");
                while (bits != 0) {
                    buf.append(bits & PACKED_BYTE_MASK);
                    bits >>>= PACKED_BYTE_SIZE;
                    if (bits != 0)  buf.append(",");
                }
                buf.append(")");
            }
            if (fullBytes != null) {
                buf.append("unpacked");
                buf.append(Arrays.toString(fullBytes));
            }
            if (result != null) {
                buf.append(" result=");
                buf.append(result);
            }
            return buf.toString();
        }
    }

    /** Find a previously cached transform equivalent to the given one, and return its result. */
    private LambdaForm getInCache(Transform key) {
        assert(key.result == null);
        // The transformCache is one of null, Transform, Transform[], or ConcurrentHashMap.
        Object c = lambdaForm.transformCache;
        Transform k = null;
        if (c instanceof ConcurrentHashMap) {
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<Transform,Transform> m = (ConcurrentHashMap<Transform,Transform>) c;
            k = m.get(key);
        } else if (c == null) {
            return null;
        } else if (c instanceof Transform) {
            // one-element cache avoids overhead of an array
            Transform t = (Transform)c;
            if (t.equals(key))  k = t;
        } else {
            Transform[] ta = (Transform[])c;
            for (int i = 0; i < ta.length; i++) {
                Transform t = ta[i];
                if (t == null)  break;
                if (t.equals(key)) { k = t; break; }
            }
        }
        assert(k == null || key.equals(k));
        return k == null ? null : k.result;
    }

    /** Arbitrary but reasonable limits on Transform[] size for cache. */
    private static final int MIN_CACHE_ARRAY_SIZE = 4, MAX_CACHE_ARRAY_SIZE = 16;

    /** Cache a transform with its result, and return that result.
     *  But if an equivalent transform has already been cached, return its result instead.
     */
    private LambdaForm putInCache(Transform key, LambdaForm form) {
        key = key.withResult(form);
        for (int pass = 0; ; pass++) {
            Object c = lambdaForm.transformCache;
            if (c instanceof ConcurrentHashMap) {
                @SuppressWarnings("unchecked")
                ConcurrentHashMap<Transform,Transform> m = (ConcurrentHashMap<Transform,Transform>) c;
                Transform k = m.putIfAbsent(key, key);
                return k != null ? k.result : form;
            }
            assert(pass == 0);
            synchronized (lambdaForm) {
                c = lambdaForm.transformCache;
                if (c instanceof ConcurrentHashMap)
                    continue;
                if (c == null) {
                    lambdaForm.transformCache = key;
                    return form;
                }
                Transform[] ta;
                if (c instanceof Transform) {
                    Transform k = (Transform)c;
                    if (k.equals(key)) {
                        return k.result;
                    }
                    // expand one-element cache to small array
                    ta = new Transform[MIN_CACHE_ARRAY_SIZE];
                    ta[0] = k;
                    lambdaForm.transformCache = c = ta;
                } else {
                    // it is already expanded
                    ta = (Transform[])c;
                }
                int len = ta.length;
                int i;
                for (i = 0; i < len; i++) {
                    Transform k = ta[i];
                    if (k == null) {
                        break;
                    }
                    if (k.equals(key)) {
                        return k.result;
                    }
                }
                if (i < len) {
                    // just fall through to cache update
                } else if (len < MAX_CACHE_ARRAY_SIZE) {
                    len = Math.min(len * 2, MAX_CACHE_ARRAY_SIZE);
                    ta = Arrays.copyOf(ta, len);
                    lambdaForm.transformCache = ta;
                } else {
                    ConcurrentHashMap<Transform, Transform> m = new ConcurrentHashMap<>(MAX_CACHE_ARRAY_SIZE * 2);
                    for (Transform k : ta) {
                        m.put(k, k);
                    }
                    lambdaForm.transformCache = m;
                    // The second iteration will update for this query, concurrently.
                    continue;
                }
                ta[i] = key;
                return form;
            }
        }
    }

    private LambdaFormBuffer buffer() {
        return new LambdaFormBuffer(lambdaForm);
    }

    /// Editing methods for method handles.  These need to have fast paths.

    private BoundMethodHandle.SpeciesData oldSpeciesData() {
        return BoundMethodHandle.speciesData(lambdaForm);
    }
    private BoundMethodHandle.SpeciesData newSpeciesData(BasicType type) {
        return oldSpeciesData().extendWith(type);
    }

    BoundMethodHandle bindArgumentL(BoundMethodHandle mh, int pos, Object value) {
        assert(mh.speciesData() == oldSpeciesData());
        BasicType bt = L_TYPE;
        MethodType type2 = bindArgumentType(mh, pos, bt);
        LambdaForm form2 = bindArgumentForm(1+pos);
        return mh.copyWithExtendL(type2, form2, value);
    }
    BoundMethodHandle bindArgumentI(BoundMethodHandle mh, int pos, int value) {
        assert(mh.speciesData() == oldSpeciesData());
        BasicType bt = I_TYPE;
        MethodType type2 = bindArgumentType(mh, pos, bt);
        LambdaForm form2 = bindArgumentForm(1+pos);
        return mh.copyWithExtendI(type2, form2, value);
    }

    BoundMethodHandle bindArgumentJ(BoundMethodHandle mh, int pos, long value) {
        assert(mh.speciesData() == oldSpeciesData());
        BasicType bt = J_TYPE;
        MethodType type2 = bindArgumentType(mh, pos, bt);
        LambdaForm form2 = bindArgumentForm(1+pos);
        return mh.copyWithExtendJ(type2, form2, value);
    }

    BoundMethodHandle bindArgumentF(BoundMethodHandle mh, int pos, float value) {
        assert(mh.speciesData() == oldSpeciesData());
        BasicType bt = F_TYPE;
        MethodType type2 = bindArgumentType(mh, pos, bt);
        LambdaForm form2 = bindArgumentForm(1+pos);
        return mh.copyWithExtendF(type2, form2, value);
    }

    BoundMethodHandle bindArgumentD(BoundMethodHandle mh, int pos, double value) {
        assert(mh.speciesData() == oldSpeciesData());
        BasicType bt = D_TYPE;
        MethodType type2 = bindArgumentType(mh, pos, bt);
        LambdaForm form2 = bindArgumentForm(1+pos);
        return mh.copyWithExtendD(type2, form2, value);
    }

    private MethodType bindArgumentType(BoundMethodHandle mh, int pos, BasicType bt) {
        assert(mh.form == lambdaForm);
        assert(mh.form.names[1+pos].type == bt);
        assert(BasicType.basicType(mh.type().parameterType(pos)) == bt);
        return mh.type().dropParameterTypes(pos, pos+1);
    }

    /// Editing methods for lambda forms.
    // Each editing method can (potentially) cache the edited LF so that it can be reused later.

    LambdaForm bindArgumentForm(int pos) {
        Transform key = Transform.of(Transform.Kind.BIND_ARG, pos);
        LambdaForm form = getInCache(key);
        if (form != null) {
            assert(form.parameterConstraint(0) == newSpeciesData(lambdaForm.parameterType(pos)));
            return form;
        }
        LambdaFormBuffer buf = buffer();
        buf.startEdit();

        BoundMethodHandle.SpeciesData oldData = oldSpeciesData();
        BoundMethodHandle.SpeciesData newData = newSpeciesData(lambdaForm.parameterType(pos));
        Name oldBaseAddress = lambdaForm.parameter(0);  // BMH holding the values
        Name newBaseAddress;
        NamedFunction getter = newData.getterFunction(oldData.fieldCount());

        if (pos != 0) {
            // The newly created LF will run with a different BMH.
            // Switch over any pre-existing BMH field references to the new BMH class.
            buf.replaceFunctions(oldData.getterFunctions(), newData.getterFunctions(), oldBaseAddress);
            newBaseAddress = oldBaseAddress.withConstraint(newData);
            buf.renameParameter(0, newBaseAddress);
            buf.replaceParameterByNewExpression(pos, new Name(getter, newBaseAddress));
        } else {
            // cannot bind the MH arg itself, unless oldData is empty
            assert(oldData == BoundMethodHandle.SpeciesData.EMPTY);
            newBaseAddress = new Name(L_TYPE).withConstraint(newData);
            buf.replaceParameterByNewExpression(0, new Name(getter, newBaseAddress));
            buf.insertParameter(0, newBaseAddress);
        }

        buf.endEdit();
        form = buf.lambdaForm();
        return putInCache(key, form);
    }
}
