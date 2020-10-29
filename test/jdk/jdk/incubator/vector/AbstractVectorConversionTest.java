/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
import jdk.incubator.vector.*;
import jdk.internal.vm.annotation.ForceInline;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.AfterMethod;
import org.testng.ITestResult;

import java.util.Arrays;
import java.util.List;
import java.nio.*;
import java.util.function.IntFunction;

abstract class AbstractVectorConversionTest {
    static final int INVOC_COUNT = Integer.getInteger("jdk.incubator.vector.test.loop-iterations", 1000);

    static VectorOperators.Conversion<Byte,Byte> B2B = VectorOperators.Conversion.ofCast(byte.class, byte.class);
    static VectorOperators.Conversion<Short,Short> S2S = VectorOperators.Conversion.ofCast(short.class, short.class);
    static VectorOperators.Conversion<Integer,Integer> I2I = VectorOperators.Conversion.ofCast(int.class, int.class);
    static VectorOperators.Conversion<Long,Long> L2L = VectorOperators.Conversion.ofCast(long.class, long.class);
    static VectorOperators.Conversion<Float,Float> F2F = VectorOperators.Conversion.ofCast(float.class, float.class);
    static VectorOperators.Conversion<Double,Double> D2D = VectorOperators.Conversion.ofCast(double.class, double.class);

    static VectorShape getMaxBit() {
        return VectorShape.S_Max_BIT;
    }

    static <T> IntFunction<T> withToString(String s, IntFunction<T> f) {
        return new IntFunction<T>() {
            @Override
            public T apply(int v) {
                return f.apply(v);
            }

            @Override
            public String toString() {
                return s;
            }
        };
    }

    interface ToByteF {
        byte apply(int i);
    }

    static byte[] fill_byte(int s , ToByteF f) {
        return fill_byte(new byte[s], f);
    }

    static byte[] fill_byte(byte[] a, ToByteF f) {
        for (int i = 0; i < a.length; i++) {
            a[i] = f.apply(i);
        }
        return a;
    }

    interface ToBoolF {
        boolean apply(int i);
    }

    static boolean[] fill_bool(int s , ToBoolF f) {
        return fill_bool(new boolean[s], f);
    }

    static boolean[] fill_bool(boolean[] a, ToBoolF f) {
        for (int i = 0; i < a.length; i++) {
            a[i] = f.apply(i);
        }
        return a;
    }

    interface ToShortF {
        short apply(int i);
    }


    static short[] fill_short(int s , ToShortF f) {
        return fill_short(new short[s], f);
    }

    static short[] fill_short(short[] a, ToShortF f) {
        for (int i = 0; i < a.length; i++) {
            a[i] = f.apply(i);
        }
        return a;
    }

    interface ToIntF {
        int apply(int i);
    }

    static int[] fill_int(int s , ToIntF f) {
        return fill_int(new int[s], f);
    }

    static int[] fill_int(int[] a, ToIntF f) {
        for (int i = 0; i < a.length; i++) {
            a[i] = f.apply(i);
        }
        return a;
    }

    interface ToLongF {
        long apply(int i);
    }

    static long[] fill_long(int s , ToLongF f) {
        return fill_long(new long[s], f);
    }

    static long[] fill_long(long[] a, ToLongF f) {
        for (int i = 0; i < a.length; i++) {
            a[i] = f.apply(i);
        }
        return a;
    }

    interface ToFloatF {
        float apply(int i);
    }

    static float[] fill_float(int s , ToFloatF f) {
        return fill_float(new float[s], f);
    }

    static float[] fill_float(float[] a, ToFloatF f) {
        for (int i = 0; i < a.length; i++) {
            a[i] = f.apply(i);
        }
        return a;
    }

    interface ToDoubleF {
        double apply(int i);
    }

    static double[] fill_double(int s , ToDoubleF f) {
        return fill_double(new double[s], f);
    }

    static double[] fill_double(double[] a, ToDoubleF f) {
        for (int i = 0; i < a.length; i++) {
            a[i] = f.apply(i);
        }
        return a;
    }

    static final List<IntFunction<byte[]>> BYTE_GENERATORS = List.of(
            withToString("byte(i)", (int s) -> {
                return fill_byte(s, i -> (byte)(i+1));
            })
    );


    @AfterMethod
    public void getRunTime(ITestResult tr) {
       long time = tr.getEndMillis() - tr.getStartMillis();
       System.out.println(tr.getName() + " took " + time + " ms");
    }

    @DataProvider
    public Object[][] byteUnaryOpProvider() {
        return BYTE_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }

    static final List<IntFunction<boolean[]>> BOOL_GENERATORS = List.of(
        withToString("boolean(i%3)", (int s) -> {
            return fill_bool(s, i -> i % 3 == 0);
        })
    );

    @DataProvider
    public Object[][] booleanUnaryOpProvider() {
        return BOOL_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }

    static final List<IntFunction<short[]>> SHORT_GENERATORS = List.of(
            withToString("short(i)", (int s) -> {
                return fill_short(s, i -> (short)(i*100+1));
            })
    );

    @DataProvider
    public Object[][] shortUnaryOpProvider() {
        return SHORT_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }

    static final List<IntFunction<int[]>> INT_GENERATORS = List.of(
            withToString("int(i)", (int s) -> {
                return fill_int(s, i -> (int)(i^((i&1)-1)));
            })
    );

    @DataProvider
    public Object[][] intUnaryOpProvider() {
        return INT_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }

    static final List<IntFunction<long[]>> LONG_GENERATORS = List.of(
            withToString("long(i)", (int s) -> {
                return fill_long(s, i -> (long)(i^((i&1)-1)));
            })
    );

    @DataProvider
    public Object[][] longUnaryOpProvider() {
        return LONG_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }

    static final List<IntFunction<float[]>> FLOAT_GENERATORS = List.of(
            withToString("float(i)", (int s) -> {
                return fill_float(s, i -> (float)(i * 10 + 0.1));
            })
    );


    @DataProvider
    public Object[][] floatUnaryOpProvider() {
        return FLOAT_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }

    static final List<IntFunction<double[]>> DOUBLE_GENERATORS = List.of(
            withToString("double(i)", (int s) -> {
                return fill_double(s, i -> (double)(i * 10 + 0.1));
            })
    );

    @DataProvider
    public Object[][] doubleUnaryOpProvider() {
        return DOUBLE_GENERATORS.stream().
                map(f -> new Object[]{f}).
                toArray(Object[][]::new);
    }


    public enum ConvAPI { CONVERT, CONVERTSHAPE, CASTSHAPE, REINTERPRETSHAPE };

    static <E> E[] getBoxedArray(Class<E> toClass, int len) {
       if(toClass.equals(Byte.class)) {
         byte[] b = new byte[len];
         return (E[])(getBoxedArray(b));
       } else if(toClass.equals(Short.class)) {
         short [] s = new short[len];
         return (E[])(getBoxedArray(s));
       } else if(toClass.equals(Integer.class)) {
         int[] i = new int[len];
         return (E[])(getBoxedArray(i));
       } else if(toClass.equals(Long.class)) {
         long[] l = new long[len];
         return (E[])(getBoxedArray(l));
       } else if(toClass.equals(Float.class)) {
         float[] f = new float[len];
         return (E[])(getBoxedArray(f));
       } else if(toClass.equals(Double.class)) {
         double[] d = new double[len];
         return (E[])(getBoxedArray(d));
       } else
         assert(false);
       return null;
    }

    static <E> void copyPrimArrayToBoxedArray(E [] boxed_arr, int index, List<?> arrL) {
      var arr = (arrL.get(0));
      if (boxed_arr instanceof Byte []) {
        byte [] barr = (byte[])arr;
        assert(boxed_arr.length >= index + barr.length);
        for(int i = 0 ; i < barr.length; i++)
           boxed_arr[i+index] = (E)Byte.valueOf(barr[i]);
      }
      else if (boxed_arr instanceof Short []) {
        short [] sarr = (short[])arr;
        assert(boxed_arr.length >= index + sarr.length);
        for(int i = 0 ; i < sarr.length; i++)
           boxed_arr[i+index] = (E)Short.valueOf(sarr[i]);
      }
      else if (boxed_arr instanceof Integer []) {
        int [] iarr = (int[])arr;
        assert(boxed_arr.length >= index + iarr.length);
        for(int i = 0 ; i < iarr.length; i++)
           boxed_arr[i+index] = (E)Integer.valueOf(iarr[i]);
      }
      else if (boxed_arr instanceof Long []) {
        long [] larr = (long[])arr;
        assert(boxed_arr.length >= index + larr.length);
        for(int i = 0 ; i < larr.length; i++)
           boxed_arr[i+index] = (E)Long.valueOf(larr[i]);
      }
      else if (boxed_arr instanceof Float []) {
        float [] farr = (float[])arr;
        assert(boxed_arr.length >= index + farr.length);
        for(int i = 0 ; i < farr.length; i++)
           boxed_arr[i+index] = (E)Float.valueOf(farr[i]);
      }
      else if (boxed_arr instanceof Double []) {
        double [] darr = (double[])arr;
        assert(boxed_arr.length >= index + darr.length);
        for(int i = 0 ; i < darr.length; i++)
           boxed_arr[i+index] = (E)Double.valueOf(darr[i]);
      }
      else
        assert(false);
    }

    static Byte[] getBoxedArray(byte[] arr) {
      Byte[] boxed_arr = new Byte[arr.length];
      for (int i = 0; i < arr.length; i++)
        boxed_arr[i] = Byte.valueOf(arr[i]);
      return boxed_arr;
    }
    static Short[] getBoxedArray(short[] arr) {
      Short[] boxed_arr = new Short[arr.length];
      for (int i = 0; i < arr.length; i++)
        boxed_arr[i] = Short.valueOf(arr[i]);
      return boxed_arr;
    }
    static Integer[] getBoxedArray(int[] arr) {
      Integer[] boxed_arr = new Integer[arr.length];
      for (int i = 0; i < arr.length; i++)
        boxed_arr[i] = Integer.valueOf(arr[i]);
      return boxed_arr;
    }
    static Long[] getBoxedArray(long[] arr) {
      Long[] boxed_arr = new Long[arr.length];
      for (int i = 0; i < arr.length; i++)
        boxed_arr[i] = Long.valueOf(arr[i]);
      return boxed_arr;
    }
    static Float[] getBoxedArray(float[] arr) {
      Float[] boxed_arr = new Float[arr.length];
      for (int i = 0; i < arr.length; i++)
        boxed_arr[i] = Float.valueOf(arr[i]);
      return boxed_arr;
    }
    static Double[] getBoxedArray(double[] arr) {
      Double[] boxed_arr = new Double[arr.length];
      for (int i = 0; i < arr.length; i++)
        boxed_arr[i] = Double.valueOf(arr[i]);
      return boxed_arr;
    }

    static <E> Number zeroValue(E to) {
      if (to.getClass().equals(Byte.class))
        return Byte.valueOf((byte)0);
      else if (to.getClass().equals(Short.class))
        return Short.valueOf((short)0);
      else if (to.getClass().equals(Integer.class))
        return Integer.valueOf(0);
      else if (to.getClass().equals(Long.class))
        return Long.valueOf((long)0);
      else if (to.getClass().equals(Float.class))
        return Float.valueOf((float)0);
      else if (to.getClass().equals(Double.class))
        return Double.valueOf((double)0);
      else
        assert (false);
      return null;
    }

    static <E , F > Number convertValue(E from, F to) {
      if (to.getClass().equals(Byte.class))
        return Byte.valueOf(((Number)from).byteValue());
      else if (to.getClass().equals(Short.class))
        return Short.valueOf(((Number)from).shortValue());
      else if (to.getClass().equals(Integer.class))
        return Integer.valueOf(((Number)from).intValue());
      else if (to.getClass().equals(Long.class))
        return Long.valueOf(((Number)from).longValue());
      else if (to.getClass().equals(Float.class))
        return Float.valueOf(((Number)from).floatValue());
      else if (to.getClass().equals(Double.class))
        return Double.valueOf(((Number)from).doubleValue());
      else
        assert (false);
      return null;
    }

    static <E> void putValue(ByteBuffer bb, E [] arr, int index) {
      if (arr[index].getClass().equals(Byte.class))
        bb.put(((Byte)(arr[index])).byteValue());
      else if (arr[index].getClass().equals(Short.class))
        bb.putShort(((Short)arr[index]).shortValue());
      else if (arr[index].getClass().equals(Integer.class))
        bb.putInt(((Integer)arr[index]).intValue());
      else if (arr[index].getClass().equals(Long.class))
        bb.putLong(((Long)arr[index]).longValue());
      else if (arr[index].getClass().equals(Float.class))
        bb.putFloat(((Float)arr[index]).floatValue());
      else if (arr[index].getClass().equals(Double.class))
        bb.putDouble(((Double)arr[index]).doubleValue());
      else
        assert (false);
    }

    static <F> Number getValue(ByteBuffer bb, Class<?> toClass) {
      if (toClass.equals(Byte.class))
        return (Number)(Byte.valueOf(bb.get()));
      else if (toClass.equals(Short.class))
        return (Number)(Short.valueOf(bb.getShort()));
      else if (toClass.equals(Integer.class))
        return (Number)(Integer.valueOf(bb.getInt()));
      else if (toClass.equals(Long.class))
        return (Number)(Long.valueOf(bb.getLong()));
      else if (toClass.equals(Float.class))
        return (Number)(Float.valueOf(bb.getFloat()));
      else if (toClass.equals(Double.class))
        return (Number)(Double.valueOf(bb.getDouble()));
      else
        assert (false);
      return null;
    }

    static <E , F > void
    expanding_reinterpret_scalar(E[] in, F[] out, int in_vec_size, int out_vec_size,
                                 int in_vec_lane_cnt, int out_vec_lane_cnt,
                                 int in_idx,  int out_idx, int part) {
      int SLICE_FACTOR = Math.max(in_vec_size, out_vec_size) / Math.min(in_vec_size, out_vec_size);
      int ELEMENTS_IN_SLICE = in_vec_lane_cnt / SLICE_FACTOR;
      assert (part < SLICE_FACTOR && part >= 0);
      int start_idx = in_idx + part * ELEMENTS_IN_SLICE;
      int end_idx = start_idx + ELEMENTS_IN_SLICE;
      var bb = ByteBuffer.allocate(out_vec_size);
      for (int i = start_idx; i < end_idx ; i++)
        putValue(bb, in, i);
      bb.rewind();
      Class<?> toClass = out[0].getClass();
      for (int i = 0; i < out_vec_lane_cnt; i++)
         out[i + out_idx] = (F)(Vector64ConversionTests.<F>getValue(bb, toClass));
    }

    static <E , F > void
    contracting_reinterpret_scalar(E[] in, F[] out, int in_vec_size, int out_vec_size,
                                   int in_vec_lane_cnt, int out_vec_lane_cnt,
                                   int in_idx,  int out_idx, int part) {
      int SLICE_FACTOR = Math.max(in_vec_size, out_vec_size) / Math.min(in_vec_size, out_vec_size);
      int ELEMENTS_OUT_SLICE = out_vec_lane_cnt / SLICE_FACTOR;
      assert (part > -SLICE_FACTOR && part <= 0);
      int start_idx = out_idx + (-part) * ELEMENTS_OUT_SLICE;
      int end_idx = start_idx + ELEMENTS_OUT_SLICE;
      for (int i = 0; i < out_vec_lane_cnt; i++)
        out[i+out_idx] = (F)(zeroValue(out[i]));
      var bb = ByteBuffer.allocate(in_vec_size);
      for (int i = 0; i < in_vec_lane_cnt; i++)
        putValue(bb, in, i + in_idx);
      bb.rewind();
      Class<?> toClass = out[0].getClass();
      for (int i = start_idx; i < end_idx; i++)
        out[i] =
            (F)(Vector64ConversionTests.<F>getValue(bb, toClass));
    }

    static <E , F > void
    expanding_conversion_scalar(E[] in, F[] out, int in_vec_len, int out_vec_len,
                                int in_idx,  int out_idx, int part) {
      int SLICE_FACTOR = Math.max(in_vec_len, out_vec_len) / Math.min(in_vec_len, out_vec_len);
      assert (part < SLICE_FACTOR && part >= 0);
      int start_idx = part * out_vec_len;
      for (int i = 0; i < out_vec_len; i++)
        out[i + out_idx] = (F)(Vector64ConversionTests.<E, F>convertValue(in[i + start_idx + in_idx], out[i + out_idx]));
    }

    static <E , F > void
    contracting_conversion_scalar(E[] in, F[] out, int in_vec_len, int out_vec_len,
                               int in_idx,  int out_idx, int part) {
      int SLICE_FACTOR = Math.max(out_vec_len, in_vec_len) / Math.min(out_vec_len, in_vec_len);
      assert (part > -SLICE_FACTOR && part <= 0);
      int start_idx = -part * in_vec_len;
      for (int i = 0; i < out_vec_len; i++)
        out[i+out_idx] = (F)(zeroValue(out[i+out_idx]));
      for (int i = 0; i < in_vec_len; i++)
        out[i + start_idx + out_idx] =
            (F)(Vector64ConversionTests.<E, F>convertValue(in[i+in_idx], out[i + start_idx+ out_idx]));
    }

    static int [] getPartsArray(int m , boolean is_contracting_conv) {
      int [] parts = new int[m];
      int part_init = is_contracting_conv ? -m+1 : 0;
      for(int i = 0; i < parts.length ; i++)
        parts[i] = part_init+i;
      return parts;
    }

    static <E> void assertResultsEquals(E[] ref, E[] res, int species_len) {
      Assert.assertEquals(res.length , ref.length);
      int TRIP_COUNT = res.length - (res.length & ~(species_len - 1));
      for (int i = 0; i < TRIP_COUNT; i++) {
        System.out.println("res[" + i + "] = " + res[i] + " ref[" + i +
                           "] = " + ref[i]);
        Assert.assertEquals(res[i], ref[i]);
      }
    }

    static Vector<?> vectorFactory(List<?> arrL, int sindex, VectorSpecies<?> SPECIES) {
       var arr = arrL.get(0);
       if (SPECIES.elementType().equals(byte.class))
         return ByteVector.fromArray((VectorSpecies<Byte>)(SPECIES), (byte[])(arr), sindex);
       else if (SPECIES.elementType().equals(short.class))
         return ShortVector.fromArray((VectorSpecies<Short>)(SPECIES), (short[])(arr), sindex);
       else if (SPECIES.elementType().equals(int.class))
         return IntVector.fromArray((VectorSpecies<Integer>)(SPECIES), (int[])(arr), sindex);
       else if (SPECIES.elementType().equals(long.class))
         return LongVector.fromArray((VectorSpecies<Long>)(SPECIES), (long[])(arr), sindex);
       else if (SPECIES.elementType().equals(float.class))
         return FloatVector.fromArray((VectorSpecies<Float>)(SPECIES), (float[])(arr), sindex);
       else if (SPECIES.elementType().equals(double.class))
         return DoubleVector.fromArray((VectorSpecies<Double>)(SPECIES), (double[])(arr), sindex);
       else
         assert(false);
       return null;
    }

    static <E,F,I,O> void conversion_kernel(VectorSpecies<?> SPECIES, VectorSpecies<?> OSPECIES,
                                            I[] boxed_a, O[] boxed_ref, O[] boxed_res, List<?> unboxed_a,
                                            VectorOperators.Conversion OP, ConvAPI API, int in_len) {
      int src_species_len = SPECIES.length();
      int dst_species_len = OSPECIES.length();
      boolean is_contracting_conv =  src_species_len * OSPECIES.elementSize() < OSPECIES.vectorBitSize();
      int m = Math.max(dst_species_len,src_species_len) / Math.min(src_species_len,dst_species_len);

      int [] parts = getPartsArray(m, is_contracting_conv);
      for (int ic = 0; ic < INVOC_COUNT; ic++) {
         for (int i=0, j=0; i < in_len; i += src_species_len, j+= dst_species_len) {
            int part = parts[i % parts.length];
            var av = Vector64ConversionTests.<I>vectorFactory(unboxed_a, i, SPECIES);
            F rv = null;
            switch(API) {
              default:
                assert(false);
                break;
              case CONVERT:
                rv = ((F)(av.convert(OP, part)));
                break;
              case CONVERTSHAPE:
                rv = ((F)(av.convertShape(OP, OSPECIES, part)));
                break;
              case CASTSHAPE:
                rv = ((F)(av.castShape(OSPECIES, part)));
                break;
            }
            copyPrimArrayToBoxedArray(boxed_res, j, Arrays.asList(((Vector)(rv)).toArray()));
            if (is_contracting_conv) {
              contracting_conversion_scalar(boxed_a, boxed_ref, src_species_len, dst_species_len, i, j, part);
            } else {
              expanding_conversion_scalar(boxed_a, boxed_ref, src_species_len, dst_species_len, i, j , part);
            }
         }
      }
      assertResultsEquals(boxed_res, boxed_ref, dst_species_len);
    }

    static <E,F,I,O> void reinterpret_kernel(VectorSpecies<?> SPECIES, VectorSpecies<?> OSPECIES,
                                            I[] boxed_a, O[] boxed_ref, O[] boxed_res, List<?> unboxed_a,
                                            int in_len) {
      int src_vector_size = SPECIES.vectorBitSize();
      int dst_vector_size = OSPECIES.vectorBitSize();
      int src_vector_lane_cnt = SPECIES.length();
      int dst_vector_lane_cnt = OSPECIES.length();
      boolean is_contracting_conv =  src_vector_size < dst_vector_size;
      int m = Math.max(dst_vector_size,src_vector_size) / Math.min(dst_vector_size, src_vector_size);

      int [] parts = getPartsArray(m, is_contracting_conv);
      for (int ic = 0; ic < INVOC_COUNT; ic++) {
        for (int i = 0, j=0; i < in_len; i += src_vector_lane_cnt, j+= dst_vector_lane_cnt) {
          int part = parts[i % parts.length];
          var av = Vector64ConversionTests.<I>vectorFactory(unboxed_a, i, SPECIES);
          F rv = (F)(av.reinterpretShape(OSPECIES, part));
          copyPrimArrayToBoxedArray(boxed_res, j, Arrays.asList(((Vector)(rv)).toArray()));
          if (is_contracting_conv) {
             contracting_reinterpret_scalar(boxed_a, boxed_ref, src_vector_size, dst_vector_size,
                                            src_vector_lane_cnt, dst_vector_lane_cnt, i, j, part);
          } else {
             expanding_reinterpret_scalar(boxed_a, boxed_ref, src_vector_size, dst_vector_size,
                                          src_vector_lane_cnt, dst_vector_lane_cnt, i, j, part);
          }
        }
      }
      assertResultsEquals(boxed_res, boxed_ref, dst_vector_lane_cnt);
    }
}
