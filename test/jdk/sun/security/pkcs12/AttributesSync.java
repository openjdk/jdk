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

/*
 * @test
 * @bug 8309667
 * @modules java.base/sun.security.pkcs12:+open
 * @summary TLS handshake fails because of ConcurrentModificationException
 *          in PKCS12KeyStore.engineGetEntry
 */

import sun.security.pkcs12.PKCS12KeyStore;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.PKCS12Attribute;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AttributesSync {

    static volatile boolean success = true;

    public static void main(String[] args) throws Exception {
        Runnable r = AttributesSync::get;
        var all = IntStream.range(0, 100).mapToObj(i -> new Thread(r))
                .collect(Collectors.toList());
        for (var c : all) {
            c.start();
        }
        for (var c : all) {
            c.join();
        }
        if (!success) {
            throw new RuntimeException();
        }
    }

    static Set attrs;

    static {
        try {
            PKCS12KeyStore p12 = new PKCS12KeyStore();

            // p12e = new PKCS12KeyStore.Entry();
            Class<?> clazz = Class.forName(p12.getClass().getName() + "$Entry");
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object p12e = ctor.newInstance();

            // p12e.alias = "hello";
            Field f = clazz.getDeclaredField("alias");
            f.setAccessible(true);
            f.set(p12e, "hello");

            // p12.getAttributes(p12e);
            Method mgetA = p12.getClass().getDeclaredMethod("getAttributes", clazz);
            mgetA.setAccessible(true);
            mgetA.invoke(p12, p12e);

            // attrs = p12e.attributes;
            f = clazz.getDeclaredField("attributes");
            f.setAccessible(true);
            attrs = (Set) f.get(p12e);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    static void get() {
        try {
            for (int i = 0; i < 100; i++) {
                Thread.sleep(1);
                attrs.add(new PKCS12Attribute("1.2.3." + i, "4.5.6"));
                new HashSet<>(attrs);
                Thread.sleep(1);
            }
        } catch (Exception e) {
            success = false;
            e.printStackTrace();
        }
    }
}
