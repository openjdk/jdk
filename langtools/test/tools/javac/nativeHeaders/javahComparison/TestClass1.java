/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

public class TestClass1 {
    // simple types
    byte b;
    short s;
    int i;
    long l;
    float f;
    double d;
    Object o;
    String t;
    List<String> g;

    // constants
    static final byte bc = 0;
    static final short sc = 0;
    static final int ic = 0;
    static final long lc = 0;
    static final float fc = 0;
    static final double dc = 0;
    static final Object oc = null;
    static final String tc = "";
    static final List<String> gc = null;

    // simple arrays
    byte[] ba;
    short[] sa; // not handled corrected by javah v6
    int[] ia;
    long[] la;
    float[] fa;
    double[] da;
    Object[] oa;
    String[] ta;
    List<String>[] ga;

    // multidimensional arrays
    byte[][] baa;
    short[][] saa;
    int[][] iaa;
    long[][] laa;
    float[][] faa;
    double[][] daa;
    Object[][] oaa;
    String[][] taa;
    List<String>[] gaa;

    // simple Java methods
    byte bm() { return 0; }
    short sm() { return 0; }
    int im() { return 0; }
    long lm() { return 0; }
    float fm() { return 0; }
    double dm() { return 0; }
    Object om() { return null; }
    String tm() { return ""; }
    List<String> gm() { return null; }
    void vm() { }
    byte[] bam() { return null; }
    short[] sam() { return null; }
    int[] iam() { return null; }
    long[] lam() { return null; }
    float[] fam() { return null; }
    double[] dam() { return null; }
    Object[] oam() { return null; }
    String[] tam() { return null; }
    List<String>[] gam() { return null; }
    byte[][] baam() { return null; }
    short[][] saam() { return null; }
    int[][] iaam() { return null; }
    long[][] laam() { return null; }
    float[][] faam() { return null; }
    double[][] daam() { return null; }
    Object[][] oaam() { return null; }
    String[][] taam() { return null; }
    List<String>[] gaam() { return null; }

    // simple native methods
    native byte bmn();
    native short smn();
    native int imn();
    native long lmn();
    native float fmn();
    native double dmn();
    native Object omn();
    native String tmn();
    native List<String> gmn();
    native void vmn();
    native byte[] bamn();
    native short[] samn();
    native int[] iamn();
    native long[] lamn();
    native float[] famn();
    native double[] damn();
    native Object[] oamn();
    native String[] tamn();
    native List<String>[] gamn();
    native byte[][] baamn();
    native short[][] saamn();
    native int[][] iaamn();
    native long[][] laamn();
    native float[][] faamn();
    native double[][] daamn();
    native Object[][] oaamn();
    native String[][] taamn();
    native List<String>[] gaamn();

    // overloaded Java methods
    byte bm1() { return 0; }
    short sm1() { return 0; }
    int im1() { return 0; }
    long lm1() { return 0; }
    float fm1() { return 0; }
    double dm1() { return 0; }
    Object om1() { return null; }
    String tm1() { return ""; }
    List<String> gm1() { return null; }
    void vm1() { }

    byte bm2(int i) { return 0; }
    short sm2(int i) { return 0; }
    int im2(int i) { return 0; }
    long lm2(int i) { return 0; }
    float fm2(int i) { return 0; }
    double dm2(int i) { return 0; }
    Object om2(int i) { return null; }
    String tm2(int i) { return ""; }
    List<String> gm2(int i) { return null; }
    void vm2(int i) { }

    // overloaded native methods
    native byte bmn1();
    native short smn1();
    native int imn1();
    native long lmn1();
    native float fmn1();
    native double dmn1();
    native Object omn1();
    native String tmn1();
    native List<String> gmn1();
    native void vmn1();

    native byte bmn2(int i);
    native short smn2(int i);
    native int imn2(int i);
    native long lmn2(int i);
    native float fmn2(int i);
    native double dmn2(int i);
    native Object omn2(int i);
    native String tmn2(int i);
    native List<String> gmn2(int i);
    native void vmn2(int i);

    // arg types for Java methods
    void mb(byte b) { }
    void ms(short s) { }
    void mi(int i) { }
    void ml(long l) { }
    void mf(float f) { }
    void md(double d) { }
    void mo(Object o) { }
    void mt(String t) { }
    void mg(List<String> g) { }

    // arg types for native methods
    native void mbn(byte b);
    native void msn(short s);
    native void min(int i);
    native void mln(long l);
    native void mfn(float f);
    native void mdn(double d);
    native void mon(Object o);
    native void mtn(String t);
    native void mgn(List<String> g);

    static class Inner1 {
        // simple types
        byte b;
        short s;
        int i;
        long l;
        float f;
        double d;
        Object o;
        String t;
        List<String> g;

        // constants
        static final byte bc = 0;
        static final short sc = 0;
        static final int ic = 0;
        static final long lc = 0;
        static final float fc = 0;
        static final double dc = 0;
        static final Object oc = null;
        static final String tc = "";
        static final List<String> gc = null;

        // simple arrays
        byte[] ba;
        // short[] sa; // not handled corrected by javah v6
        int[] ia;
        long[] la;
        float[] fa;
        double[] da;
        Object[] oa;
        String[] ta;
        List<String>[] ga;

        // multidimensional arrays
        byte[][] baa;
        short[][] saa;
        int[][] iaa;
        long[][] laa;
        float[][] faa;
        double[][] daa;
        Object[][] oaa;
        String[][] taa;
        List<String>[] gaa;

        // simple Java methods
        byte bm() { return 0; }
        short sm() { return 0; }
        int im() { return 0; }
        long lm() { return 0; }
        float fm() { return 0; }
        double dm() { return 0; }
        Object om() { return null; }
        String tm() { return ""; }
        List<String> gm() { return null; }
        void vm() { }

        // simple native methods
        native byte bmn();
        native short smn();
        native int imn();
        native long lmn();
        native float fmn();
        native double dmn();
        native Object omn();
        native String tmn();
        native List<String> gmn();
        native void vmn();

        // overloaded Java methods
        byte bm1() { return 0; }
        short sm1() { return 0; }
        int im1() { return 0; }
        long lm1() { return 0; }
        float fm1() { return 0; }
        double dm1() { return 0; }
        Object om1() { return null; }
        String tm1() { return ""; }
        List<String> gm1() { return null; }
        void vm1() { }

        byte bm2(int i) { return 0; }
        short sm2(int i) { return 0; }
        int im2(int i) { return 0; }
        long lm2(int i) { return 0; }
        float fm2(int i) { return 0; }
        double dm2(int i) { return 0; }
        Object om2(int i) { return null; }
        String tm2(int i) { return ""; }
        List<String> gm2(int i) { return null; }
        void vm2(int i) { }

        // overloaded native methods
        native byte bmn1();
        native short smn1();
        native int imn1();
        native long lmn1();
        native float fmn1();
        native double dmn1();
        native Object omn1();
        native String tmn1();
        native List<String> gmn1();
        native void vmn1();

        native byte bmn2(int i);
        native short smn2(int i);
        native int imn2(int i);
        native long lmn2(int i);
        native float fmn2(int i);
        native double dmn2(int i);
        native Object omn2(int i);
        native String tmn2(int i);
        native List<String> gmn2(int i);
        native void vmn2(int i);

        // arg types for Java methods
        void mb(byte b) { }
        void ms(short s) { }
        void mi(int i) { }
        void ml(long l) { }
        void mf(float f) { }
        void md(double d) { }
        void mo(Object o) { }
        void mt(String t) { }
        void mg(List<String> g) { }

        // arg types for native methods
        native void mbn(byte b);
        native void msn(short s);
        native void min(int i);
        native void mln(long l);
        native void mfn(float f);
        native void mdn(double d);
        native void mon(Object o);
        native void mtn(String t);
        native void mgn(List<String> g);
    }

    class Inner2 {
        // simple types
        byte b;
        short s;
        int i;
        long l;
        float f;
        double d;
        Object o;
        String t;
        List<String> g;

        // constants
        static final byte bc = 0;
        static final short sc = 0;
        static final int ic = 0;
        static final long lc = 0;
        static final float fc = 0;
        static final double dc = 0;
        //static final Object oc = null;
        static final String tc = "";
        //static final List<String> gc = null;

        // simple arrays
        byte[] ba;
        // short[] sa; // not handled corrected by javah v6
        int[] ia;
        long[] la;
        float[] fa;
        double[] da;
        Object[] oa;
        String[] ta;
        List<String>[] ga;

        // multidimensional arrays
        byte[][] baa;
        short[][] saa;
        int[][] iaa;
        long[][] laa;
        float[][] faa;
        double[][] daa;
        Object[][] oaa;
        String[][] taa;
        List<String>[] gaa;

        // simple Java methods
        byte bm() { return 0; }
        short sm() { return 0; }
        int im() { return 0; }
        long lm() { return 0; }
        float fm() { return 0; }
        double dm() { return 0; }
        Object om() { return null; }
        String tm() { return ""; }
        List<String> gm() { return null; }
        void vm() { }

        // simple native methods
        native byte bmn();
        native short smn();
        native int imn();
        native long lmn();
        native float fmn();
        native double dmn();
        native Object omn();
        native String tmn();
        native List<String> gmn();
        native void vmn();

        // overloaded Java methods
        byte bm1() { return 0; }
        short sm1() { return 0; }
        int im1() { return 0; }
        long lm1() { return 0; }
        float fm1() { return 0; }
        double dm1() { return 0; }
        Object om1() { return null; }
        String tm1() { return ""; }
        List<String> gm1() { return null; }
        void vm1() { }

        byte bm2(int i) { return 0; }
        short sm2(int i) { return 0; }
        int im2(int i) { return 0; }
        long lm2(int i) { return 0; }
        float fm2(int i) { return 0; }
        double dm2(int i) { return 0; }
        Object om2(int i) { return null; }
        String tm2(int i) { return ""; }
        List<String> gm2(int i) { return null; }
        void vm2(int i) { }

        // overloaded native methods
        native byte bmn1();
        native short smn1();
        native int imn1();
        native long lmn1();
        native float fmn1();
        native double dmn1();
        native Object omn1();
        native String tmn1();
        native List<String> gmn1();
        native void vmn1();

        native byte bmn2(int i);
        native short smn2(int i);
        native int imn2(int i);
        native long lmn2(int i);
        native float fmn2(int i);
        native double dmn2(int i);
        native Object omn2(int i);
        native String tmn2(int i);
        native List<String> gmn2(int i);
        native void vmn2(int i);

        // arg types for Java methods
        void mb(byte b) { }
        void ms(short s) { }
        void mi(int i) { }
        void ml(long l) { }
        void mf(float f) { }
        void md(double d) { }
        void mo(Object o) { }
        void mt(String t) { }
        void mg(List<String> g) { }

        // arg types for native methods
        native void mbn(byte b);
        native void msn(short s);
        native void min(int i);
        native void mln(long l);
        native void mfn(float f);
        native void mdn(double d);
        native void mon(Object o);
        native void mtn(String t);
        native void mgn(List<String> g);
    }

}
