/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.apple.internal.jobjc.generator.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Functional programming constructs and utilities. Java for Lisp and Haskell nerds.
 */
public abstract class Fp {
    /**
     * Multiple dynamic dispatch (multi-methods) for Java.
     *
     * This is implemented with Java reflection:
     * Class.getDeclaredMethod and Method.invoke.
     * It is about 20-40 times slower than chains of
     * "if instanceof" statements.
     */
    public static abstract class Dispatcher{
        /**
         * Shorthand, works only if no arg is null.
         */
        public static final <R> R dispatch(Class clazz, Object instance, String method, Object... args) throws NoSuchMethodException{
            Class[] types = new Class[args.length];
            for(int i = 0; i < args.length; i++) types[i] = args[i].getClass();
            return (R) dispatch(clazz, instance, method, args, types);
        }

        /**
         * Dispatch `args` of `types` to `method` on `clazz` for `instance`. If `method` is static, `instance` should be null.
         */
        public static final <R> R dispatch(Class clazz, Object instance, String method, Object[] args, Class[] types) throws NoSuchMethodException{
            try{
                java.lang.reflect.Method m = clazz.getDeclaredMethod(method, types);
                m.setAccessible(true);
                return (R) m.invoke(instance, args);
            }
            catch(NoSuchMethodException x){
                if(clazz.getSuperclass() != null)  return (R) dispatch(clazz.getSuperclass(), instance, method, args, types);
                else                               throw x;
            }
            catch(Exception x){
                throw new RuntimeException(x);
            }
        }
    }

    /**
     * The "Maybe" type encapsulates an optional value. A value of type
     * "Maybe a" either contains a value of type "a" (represented as "Just a"),
     * or it is empty (represented as "Nothing").
     *
     * http://haskell.org/ghc/docs/latest/html/libraries/base/Data-Maybe.html
     */
    public static abstract class Maybe<A>{
        public abstract boolean isJust();
        public abstract boolean isNothing();
        public abstract A fromJust() throws ClassCastException;
        public abstract A fromMaybe(final A fallback);

        public static class Nothing<A> extends Maybe<A>{
            @Override public A fromJust() throws ClassCastException { throw new ClassCastException("Cannot extract value from Nothing."); }
            @Override public A fromMaybe(A fallback) { return fallback; }
            @Override public boolean isJust() { return false; }
            @Override public boolean isNothing() { return true; }
        }
        public static class Just<A> extends Maybe<A>{
            public final A a;
            public Just(A a){ this.a = a; }
            @Override public A fromJust(){ return a; }
            @Override public A fromMaybe(A fallback) { return a; }
            @Override public boolean isJust() { return true; }
            @Override public boolean isNothing() { return false; }
        }
    }

    public static class NonNull<A>{
        public final A obj;
        public NonNull(A o){
            if(o==null) throw new RuntimeException("o may not be null.");
            this.obj = o;
        }
    }

    // Closures
    public static interface Map0<A>{ A apply(); }
    public static interface Map1<A,B>{ B apply(final A a); }
    public static interface Map2<A,B,C>{ C apply(final A a, final B b); }

    public static class CacheMap<K extends Comparable<K>,V>{
        private Map<K,V> cache = new TreeMap<K,V>();
        public V get(K key, Map0<V> create){
            if(cache.containsKey(key)) return cache.get(key);
            V value = create.apply();
            cache.put(key, value);
            return value;
        }
    }

    public static class Curry2to1<A,B,C> implements Map1<B,C>{
        private Map2<A,B,C> target; private A a;
        public Curry2to1(Map2<A, B, C> targett, A aa) { target = targett; a = aa; }
        public C apply(B b) { return target.apply(a, b); }
    }

    // Tuple
    public static class Pair <A,B> implements Comparable<Pair<A,B>>{
        public final A a; public final B b;
        public Pair(final A aa, final B bb){ a=aa; b=bb; }
        @Override public int hashCode(){ return (a==null ? 0 : a.hashCode()) + (b==null ? 0 : b.hashCode()); }
        @Override public boolean equals(Object o){
            if(!(o instanceof Pair)) return false;
            Pair<?,?> p = (Pair<?,?>) o;
            return QA.bothNullOrEquals(a, p.a) && QA.bothNullOrEquals(b, p.b);
        }
        @Override public String toString(){ return "(" + a + ", " + b + ")"; }
        public int compareTo(Pair<A, B> o){ return toString().compareTo(o.toString()); }
    }

    /**
     * @return [fn(x) | x <- items]
     */
    public static <A,B> List<B> map(Map1<A,B> fn, final Collection<A> xs){
        ArrayList<B> rs = new ArrayList<B>(xs.size());
        for(A x : xs) rs.add(fn.apply(x));
        return rs;
    }

    public static <A,B,C> List<C> map2(Map2<A,B,C> fn, final Collection<A> as, final Collection<B> bs){
        assert as.size() == bs.size();
        ArrayList<C> cs = new ArrayList<C>(as.size());
        Iterator<A> aiter = as.iterator();
        Iterator<B> biter = bs.iterator();
        while(aiter.hasNext() && biter.hasNext())
            cs.add(fn.apply(aiter.next(), biter.next()));
        return cs;
    }

    /**
     * Same as map, but does not retain results.
     */
    public static <A> void each(Map1<A,?> fn, final Collection<A> xs){
        for(A x : xs) fn.apply(x);
    }

    /**
     * @return [x | x <- items, take(x)]
     */
    public static <A> List<A> filter(Map1<A,Boolean> take, final Collection<A> xs){
        List<A> rs = new ArrayList<A>(xs.size());
        for(A x : xs) if(take.apply(x)) rs.add(x);
        return rs;
    }

    /**
     * @return [x | x <- items, take(x)]
     */
    public static <A> Set<A> filterSet(Map1<A,Boolean> take, final Collection<A> xs){
        Set<A> rs = new HashSet<A>(xs.size());
        for(A x : xs) if(take.apply(x)) rs.add(x);
        return rs;
    }

    /**
     * @return the first x in items that satisfies take(x), or null if none
     */
    public static <X> X find(Map1<X,Boolean> take, final Collection<X> xs){
        for(X x : xs) if(take.apply(x)) return x;
        return null;
    }

    public static <A,B> A foldl(final Map2<A,B,A> f, A a, final Collection<B> xs){
        for(B b : xs) a = f.apply(a, b);
        return a;
    }

    /**
     * @return All x : p(x) == true
     */
    public static <A> boolean all(Map1<A,Boolean> p, Collection<A> xs) {
        for(A x : xs) if(!p.apply(x)) return false;
        return true;
    }

    /**
     * @return Any x : p(x) == true
     */
    public static <A> boolean any(Map1<A,Boolean> p, Collection<A> xs) {
        for(A x : xs) if(p.apply(x)) return true;
        return false;
    }

    public static <A> String join(final String sep, final Collection<A> xs) {
        if(xs.size() == 0) return "";
        if(xs.size() == 1) return xs.iterator().next().toString();
        return Fp.foldl(new Fp.Map2<String, A, String>(){
            public String apply(String a, A b) {
                String sb = b==null? "null" : b.toString();
                return a == null ? sb : a + sep + sb;
            }}, null, xs);
    }

    public static Map2<Integer,Integer,Integer> operatorPlus = new Map2<Integer, Integer, Integer>(){
        public Integer apply(Integer a, Integer b) { return (int)a + (int)b;}
    };

    public static int sum(Collection<Integer> xs){ return foldl(operatorPlus, 0, xs); }

    public static <A> List<A> append(Collection<A> xs, Collection<A> ys) {
        List<A> rs = new ArrayList<A>(xs.size() + ys.size());
        rs.addAll(xs);
        rs.addAll(ys);
        return rs;
    }

    public static <A> Set<A> appendSet(Collection<A> xs, Collection<A> ys) {
        Set<A> rs = new HashSet<A>(xs.size() + ys.size());
        rs.addAll(xs);
        rs.addAll(ys);
        return rs;
    }

    public static <K,V> Map<K,V> litMap(K key, V value, Object... pairs){
        Map ret = new HashMap(1 + pairs.length/2);
        ret.put(key, value);
        for(int i = 0; i < pairs.length; i += 2)
            ret.put(pairs[i], pairs[i+1]);
        return ret;
    }
}
