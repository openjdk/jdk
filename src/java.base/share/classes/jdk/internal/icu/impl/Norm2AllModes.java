// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 *   Copyright (C) 2009-2016, International Business Machines
 *   Corporation and others.  All Rights Reserved.
 *******************************************************************************
 */

package jdk.internal.icu.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

import jdk.internal.icu.text.Normalizer;
import jdk.internal.icu.text.Normalizer2;

@SuppressWarnings("deprecation")
public final class Norm2AllModes {
    // Public API dispatch via Normalizer2 subclasses -------------------------- ***

    // Normalizer2 implementation for the old UNORM_NONE.
    public static final class NoopNormalizer2 extends Normalizer2 {
        @Override
        public StringBuilder normalize(CharSequence src, StringBuilder dest) {
            if(dest!=src) {
                dest.setLength(0);
                return dest.append(src);
            } else {
                throw new IllegalArgumentException();
            }
        }
        @Override
        public Appendable normalize(CharSequence src, Appendable dest) {
            if(dest!=src) {
                try {
                    return dest.append(src);
                } catch(IOException e) {
                    throw new UncheckedIOException(e);  // Avoid declaring "throws IOException".
                }
            } else {
                throw new IllegalArgumentException();
            }
        }
        @Override
        public StringBuilder normalizeSecondAndAppend(StringBuilder first, CharSequence second) {
            if(first!=second) {
                return first.append(second);
            } else {
                throw new IllegalArgumentException();
            }
        }
        @Override
        public StringBuilder append(StringBuilder first, CharSequence second) {
            if(first!=second) {
                return first.append(second);
            } else {
                throw new IllegalArgumentException();
            }
        }
        @Override
        public String getDecomposition(int c) {
            return null;
        }
        // No need to override the default getRawDecomposition().
        @Override
        public boolean isNormalized(CharSequence s) { return true; }
        @Override
        public Normalizer.QuickCheckResult quickCheck(CharSequence s) { return Normalizer.YES; }
        @Override
        public int spanQuickCheckYes(CharSequence s) { return s.length(); }
        @Override
        public boolean hasBoundaryBefore(int c) { return true; }
        @Override
        public boolean hasBoundaryAfter(int c) { return true; }
        @Override
        public boolean isInert(int c) { return true; }
    }

    // Intermediate class:
    // Has Normalizer2Impl and does boilerplate argument checking and setup.
    public static abstract class Normalizer2WithImpl extends Normalizer2 {
        public Normalizer2WithImpl(Normalizer2Impl ni) {
            impl=ni;
        }

        // normalize
        @Override
        public StringBuilder normalize(CharSequence src, StringBuilder dest) {
            if(dest==src) {
                throw new IllegalArgumentException();
            }
            dest.setLength(0);
            normalize(src, new Normalizer2Impl.ReorderingBuffer(impl, dest, src.length()));
            return dest;
        }
        @Override
        public Appendable normalize(CharSequence src, Appendable dest) {
            if(dest==src) {
                throw new IllegalArgumentException();
            }
            Normalizer2Impl.ReorderingBuffer buffer=
                new Normalizer2Impl.ReorderingBuffer(impl, dest, src.length());
            normalize(src, buffer);
            buffer.flush();
            return dest;
        }
        protected abstract void normalize(CharSequence src, Normalizer2Impl.ReorderingBuffer buffer);

        // normalize and append
        @Override
        public StringBuilder normalizeSecondAndAppend(StringBuilder first, CharSequence second) {
            return normalizeSecondAndAppend(first, second, true);
        }
        @Override
        public StringBuilder append(StringBuilder first, CharSequence second) {
            return normalizeSecondAndAppend(first, second, false);
        }
        public StringBuilder normalizeSecondAndAppend(
                StringBuilder first, CharSequence second, boolean doNormalize) {
            if(first==second) {
                throw new IllegalArgumentException();
            }
            normalizeAndAppend(
                second, doNormalize,
                new Normalizer2Impl.ReorderingBuffer(impl, first, first.length()+second.length()));
            return first;
        }
        protected abstract void normalizeAndAppend(
                CharSequence src, boolean doNormalize, Normalizer2Impl.ReorderingBuffer buffer);

        @Override
        public String getDecomposition(int c) {
            return impl.getDecomposition(c);
        }
        @Override
        public String getRawDecomposition(int c) {
            return impl.getRawDecomposition(c);
        }
        @Override
        public int composePair(int a, int b) {
            return impl.composePair(a, b);
        }

        @Override
        public int getCombiningClass(int c) {
            return impl.getCC(impl.getNorm16(c));
        }

        // quick checks
        @Override
        public boolean isNormalized(CharSequence s) {
            return s.length()==spanQuickCheckYes(s);
        }
        @Override
        public Normalizer.QuickCheckResult quickCheck(CharSequence s) {
            return isNormalized(s) ? Normalizer.YES : Normalizer.NO;
        }

        public abstract int getQuickCheck(int c);

        public final Normalizer2Impl impl;
    }

    public static final class DecomposeNormalizer2 extends Normalizer2WithImpl {
        public DecomposeNormalizer2(Normalizer2Impl ni) {
            super(ni);
        }

        @Override
        protected void normalize(CharSequence src, Normalizer2Impl.ReorderingBuffer buffer) {
            impl.decompose(src, 0, src.length(), buffer);
        }
        @Override
        protected void normalizeAndAppend(
                CharSequence src, boolean doNormalize, Normalizer2Impl.ReorderingBuffer buffer) {
            impl.decomposeAndAppend(src, doNormalize, buffer);
        }
        @Override
        public int spanQuickCheckYes(CharSequence s) {
            return impl.decompose(s, 0, s.length(), null);
        }
        @Override
        public int getQuickCheck(int c) {
            return impl.isDecompYes(impl.getNorm16(c)) ? 1 : 0;
        }
        @Override
        public boolean hasBoundaryBefore(int c) { return impl.hasDecompBoundaryBefore(c); }
        @Override
        public boolean hasBoundaryAfter(int c) { return impl.hasDecompBoundaryAfter(c); }
        @Override
        public boolean isInert(int c) { return impl.isDecompInert(c); }
    }

    public static final class ComposeNormalizer2 extends Normalizer2WithImpl {
        public ComposeNormalizer2(Normalizer2Impl ni, boolean fcc) {
            super(ni);
            onlyContiguous=fcc;
        }

        @Override
        protected void normalize(CharSequence src, Normalizer2Impl.ReorderingBuffer buffer) {
            impl.compose(src, 0, src.length(), onlyContiguous, true, buffer);
        }
        @Override
        protected void normalizeAndAppend(
                CharSequence src, boolean doNormalize, Normalizer2Impl.ReorderingBuffer buffer) {
            impl.composeAndAppend(src, doNormalize, onlyContiguous, buffer);
        }

        @Override
        public boolean isNormalized(CharSequence s) {
            // 5: small destCapacity for substring normalization
            return impl.compose(s, 0, s.length(),
                                onlyContiguous, false,
                                new Normalizer2Impl.ReorderingBuffer(impl, new StringBuilder(), 5));
        }
        @Override
        public Normalizer.QuickCheckResult quickCheck(CharSequence s) {
            int spanLengthAndMaybe=impl.composeQuickCheck(s, 0, s.length(), onlyContiguous, false);
            if((spanLengthAndMaybe&1)!=0) {
                return Normalizer.MAYBE;
            } else if((spanLengthAndMaybe>>>1)==s.length()) {
                return Normalizer.YES;
            } else {
                return Normalizer.NO;
            }
        }
        @Override
        public int spanQuickCheckYes(CharSequence s) {
            return impl.composeQuickCheck(s, 0, s.length(), onlyContiguous, true)>>>1;
        }
        @Override
        public int getQuickCheck(int c) {
            return impl.getCompQuickCheck(impl.getNorm16(c));
        }
        @Override
        public boolean hasBoundaryBefore(int c) { return impl.hasCompBoundaryBefore(c); }
        @Override
        public boolean hasBoundaryAfter(int c) {
            return impl.hasCompBoundaryAfter(c, onlyContiguous);
        }
        @Override
        public boolean isInert(int c) {
            return impl.isCompInert(c, onlyContiguous);
        }

        private final boolean onlyContiguous;
    }

    public static final class FCDNormalizer2 extends Normalizer2WithImpl {
        public FCDNormalizer2(Normalizer2Impl ni) {
            super(ni);
        }

        @Override
        protected void normalize(CharSequence src, Normalizer2Impl.ReorderingBuffer buffer) {
            impl.makeFCD(src, 0, src.length(), buffer);
        }
        @Override
        protected void normalizeAndAppend(
                CharSequence src, boolean doNormalize, Normalizer2Impl.ReorderingBuffer buffer) {
            impl.makeFCDAndAppend(src, doNormalize, buffer);
        }
        @Override
        public int spanQuickCheckYes(CharSequence s) {
            return impl.makeFCD(s, 0, s.length(), null);
        }
        @Override
        public int getQuickCheck(int c) {
            return impl.isDecompYes(impl.getNorm16(c)) ? 1 : 0;
        }
        @Override
        public boolean hasBoundaryBefore(int c) { return impl.hasFCDBoundaryBefore(c); }
        @Override
        public boolean hasBoundaryAfter(int c) { return impl.hasFCDBoundaryAfter(c); }
        @Override
        public boolean isInert(int c) { return impl.isFCDInert(c); }
    }

    // instance cache ---------------------------------------------------------- ***

    private Norm2AllModes(Normalizer2Impl ni) {
        impl=ni;
        comp=new ComposeNormalizer2(ni, false);
        decomp=new DecomposeNormalizer2(ni);
        fcd=new FCDNormalizer2(ni);
        fcc=new ComposeNormalizer2(ni, true);
    }

    public final Normalizer2Impl impl;
    public final ComposeNormalizer2 comp;
    public final DecomposeNormalizer2 decomp;
    public final FCDNormalizer2 fcd;
    public final ComposeNormalizer2 fcc;

    private static Norm2AllModes getInstanceFromSingleton(Norm2AllModesSingleton singleton) {
        if(singleton.exception!=null) {
            throw singleton.exception;
        }
        return singleton.allModes;
    }
    public static Norm2AllModes getNFCInstance() {
        return getInstanceFromSingleton(NFCSingleton.INSTANCE);
    }
    public static Norm2AllModes getNFKCInstance() {
        return getInstanceFromSingleton(NFKCSingleton.INSTANCE);
    }
    public static Norm2AllModes getNFKC_CFInstance() {
        return getInstanceFromSingleton(NFKC_CFSingleton.INSTANCE);
    }
    // For use in properties APIs.
    public static Normalizer2WithImpl getN2WithImpl(int index) {
        switch(index) {
        case 0: return getNFCInstance().decomp;  // NFD
        case 1: return getNFKCInstance().decomp; // NFKD
        case 2: return getNFCInstance().comp;    // NFC
        case 3: return getNFKCInstance().comp;   // NFKC
        default: return null;
        }
    }
    public static Norm2AllModes getInstance(ByteBuffer bytes, String name) {
        if(bytes==null) {
            Norm2AllModesSingleton singleton;
            if(name.equals("nfc")) {
                singleton=NFCSingleton.INSTANCE;
            } else if(name.equals("nfkc")) {
                singleton=NFKCSingleton.INSTANCE;
            } else if(name.equals("nfkc_cf")) {
                singleton=NFKC_CFSingleton.INSTANCE;
            } else {
                singleton=null;
            }
            if(singleton!=null) {
                if(singleton.exception!=null) {
                    throw singleton.exception;
                }
                return singleton.allModes;
            }
        }
        return cache.getInstance(name, bytes);
    }
    private static CacheBase<String, Norm2AllModes, ByteBuffer> cache =
        new SoftCache<String, Norm2AllModes, ByteBuffer>() {
            @Override
            protected Norm2AllModes createInstance(String key, ByteBuffer bytes) {
                Normalizer2Impl impl;
                if(bytes==null) {
                    impl=new Normalizer2Impl().load(key+".nrm");
                } else {
                    impl=new Normalizer2Impl().load(bytes);
                }
                return new Norm2AllModes(impl);
            }
        };

    public static final NoopNormalizer2 NOOP_NORMALIZER2=new NoopNormalizer2();
    /**
     * Gets the FCD normalizer, with the FCD data initialized.
     * @return FCD normalizer
     */
    public static Normalizer2 getFCDNormalizer2() {
        return getNFCInstance().fcd;
    }

    private static final class Norm2AllModesSingleton {
        private Norm2AllModesSingleton(String name) {
            try {
                Normalizer2Impl impl=new Normalizer2Impl().load(name+".nrm");
                allModes=new Norm2AllModes(impl);
            } catch(RuntimeException e) {
                exception=e;
            }
        }

        private Norm2AllModes allModes;
        private RuntimeException exception;
    }
    private static final class NFCSingleton {
        private static final Norm2AllModesSingleton INSTANCE=new Norm2AllModesSingleton("nfc");
    }
    private static final class NFKCSingleton {
        private static final Norm2AllModesSingleton INSTANCE=new Norm2AllModesSingleton("nfkc");
    }
    private static final class NFKC_CFSingleton {
        private static final Norm2AllModesSingleton INSTANCE=new Norm2AllModesSingleton("nfkc_cf");
    }
}
