/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.util.Arrays;
import org.testng.annotations.Test;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;

import jdk.test.lib.util.ForceGC;

/* @test
 * @bug 8277072
 * @library /test/lib/
 * @build jdk.test.lib.util.ForceGC
 * @summary ObjectStreamClass caches keep ClassLoaders alive
 * @run testng TestOSCClassLoaderLeak
 */
public class TestOSCClassLoaderLeak {

    @Test
    public void testClassLoaderLeak() throws Exception {
        TestClassLoader myOwnClassLoader = new TestClassLoader();
        Class<?> loadClass = myOwnClassLoader.loadClass("ObjectStreamClass_MemoryLeakExample");
        Constructor con = loadClass.getConstructor();
        con.setAccessible(true);
        Object objectStreamClass_MemoryLeakExample = con.newInstance();
        objectStreamClass_MemoryLeakExample.toString();

        WeakReference<Object> myOwnClassLoaderWeakReference = new WeakReference<>(myOwnClassLoader);
        assertFalse(myOwnClassLoaderWeakReference.refersTo(null));
        objectStreamClass_MemoryLeakExample = null;
        myOwnClassLoader = null;
        loadClass = null;
        con = null;
        assertFalse(myOwnClassLoaderWeakReference.refersTo(null));

        assertTrue(ForceGC.wait(() -> myOwnClassLoaderWeakReference.refersTo(null)));
    }
}

class ObjectStreamClass_MemoryLeakExample {
    private static final ObjectStreamField[] fields = ObjectStreamClass.lookup(TestClass.class).getFields();
    public ObjectStreamClass_MemoryLeakExample() {
    }

    @Override
    public String toString() {
        return Arrays.toString(fields);
    }
}

class TestClassLoader extends ClassLoader {

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (name.equals("TestClass") || name.equals("ObjectStreamClass_MemoryLeakExample")) {
            byte[] bt = loadClassData(name);
            return defineClass(name, bt, 0, bt.length);
        } else {
            return super.loadClass(name);
        }
    }

    private static byte[] loadClassData(String className) {
        ByteArrayOutputStream byteSt = new ByteArrayOutputStream();
        try (InputStream is = TestClassLoader.class.getClassLoader().getResourceAsStream(className.replace(".", "/") + ".class")) {
            int len = 0;
            while ((len = is.read()) != -1) {
                byteSt.write(len);
            }
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        return byteSt.toByteArray();
    }
}

class TestClass implements Serializable {
    public String x;
}
