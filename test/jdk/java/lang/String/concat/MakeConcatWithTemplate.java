/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.StringConcatFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @test
 * @summary Test StringConcatFactory.makeConcatWithTemplate... methods.
 * @enablePreview true
 */

public class MakeConcatWithTemplate {
    public static void main(String... args) {
        makeConcatWithTemplate();
        makeConcatWithTemplateCluster();
        makeConcatWithTemplateGetters();
    }

    static List<String> fragments(int n) {
        String[] array = new String[n];
        Arrays.fill(array, "abc");
        return Arrays.asList(array);
    }

    static List<Class<?>> types(int n) {
        Class<?>[] array = new Class<?>[n];
        Arrays.fill(array, int.class);
        return Arrays.asList(array);
    }

    static List<Integer> values(int n) {
        Integer[] array = new Integer[n];
        Arrays.fill(array, 123);
        return Arrays.asList(array);
    }

    static List<MethodHandle> getters(int n) {
        MethodHandle[] array = new MethodHandle[n];
        MethodHandle m = MethodHandles.dropArguments(MethodHandles.constant(int.class, 123), 0, Object.class);
        Arrays.fill(array, m);
        return Arrays.asList(array);
    }

    static void makeConcatWithTemplate() {
        try {
            int n = StringConcatFactory.MAX_INDY_CONCAT_ARG_SLOTS - 1;
            MethodHandle m = StringConcatFactory.makeConcatWithTemplate(fragments(n + 1), types(n));
            m.invokeWithArguments(values(n));
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

        try {
            int n = StringConcatFactory.MAX_INDY_CONCAT_ARG_SLOTS;
            MethodHandle m = StringConcatFactory.makeConcatWithTemplate(fragments(n + 1), types(n));
            m.invokeWithArguments(values(n));
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

        boolean threw = false;
        try {
            int n = StringConcatFactory.MAX_INDY_CONCAT_ARG_SLOTS + 1;
            MethodHandle m = StringConcatFactory.makeConcatWithTemplate(fragments(n + 1), types(n));
            m.invokeWithArguments(values(n));
        } catch (Throwable e) {
            threw = true;
        }

        if (!threw) {
            throw new RuntimeException("Exception expected - makeConcatWithTemplate");
        }
    }

    static void makeConcatWithTemplateCluster() {
        int n = StringConcatFactory.MAX_INDY_CONCAT_ARG_SLOTS;
        int c = 3;
        try {
            List<MethodHandle> ms = StringConcatFactory.makeConcatWithTemplateCluster(fragments(c * n + 1), types(c * n), n);
            MethodHandle m0 = ms.get(0);
            MethodHandle m1 = ms.get(1);
            MethodHandle m2 = ms.get(2);
            MethodHandle m3 = ms.get(3);

            String s = (String)m0.invokeWithArguments(values(n));
            List<Object> args = new ArrayList<>();
            args.add(s);
            args.addAll(values(n - 1)); // one less for carry over string
            s = (String)m1.invokeWithArguments(args);
            args.clear();
            args.add(s);
            args.addAll(values(n - 1)); // one less for carry over string
            s = (String)m2.invokeWithArguments(args);
            args.clear();
            args.add(s);
            args.addAll(values(2)); // two remaining carry overs
            s = (String)m3.invokeWithArguments(args);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    static void makeConcatWithTemplateGetters() {
        int n = StringConcatFactory.MAX_INDY_CONCAT_ARG_SLOTS;
        int c = 3;
        try {
            MethodHandle m = StringConcatFactory.makeConcatWithTemplateGetters(fragments(c * n + 1), getters(c * n), n);
            String s = (String)m.invoke(null);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

}
