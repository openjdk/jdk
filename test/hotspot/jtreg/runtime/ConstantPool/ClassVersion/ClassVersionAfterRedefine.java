/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * @bug 8267555
 * @requires vm.jvmti
 * @summary Class redefinition with a different class file version
 * @library /test/lib
 * @compile TestClassOld.jasm TestClassNew.jasm
 * @run main/othervm -Djdk.attach.allowAttachSelf test.ClassVersionAfterRedefine
 */

package test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import com.sun.tools.attach.VirtualMachine;
import static jdk.test.lib.Asserts.assertTrue;

public class ClassVersionAfterRedefine extends ClassLoader {

    private static String myName = ClassVersionAfterRedefine.class.getName();
    private static Instrumentation instrumentation;

    private static byte[] getBytecodes(String name) throws Exception {
        InputStream is = ClassVersionAfterRedefine.class.getResourceAsStream(name + ".class");
        byte[] buf = is.readAllBytes();
        System.out.println("sizeof(" + name + ".class) == " + buf.length);
        return buf;
    }

    private static int getStringIndex(String needle, byte[] buf) {
        return getStringIndex(needle, buf, 0);
    }

    private static int getStringIndex(String needle, byte[] buf, int offset) {
        outer:
        for (int i = offset; i < buf.length - offset - needle.length(); i++) {
            for (int j = 0; j < needle.length(); j++) {
                if (buf[i + j] != (byte)needle.charAt(j)) continue outer;
            }
            return i;
        }
        return 0;
    }

    private static void replaceString(byte[] buf, String name, int index) {
        for (int i = index; i < index + name.length(); i++) {
            buf[i] = (byte)name.charAt(i - index);
        }
    }

    private static void replaceAllStrings(byte[] buf, String oldString, String newString) throws Exception {
        assertTrue(oldString.length() == newString.length(), "must have same length");
        int index = -1;
        while ((index = getStringIndex(oldString, buf, index + 1)) != 0) {
            replaceString(buf, newString, index);
        }
    }

    public static void agentmain(String args, Instrumentation inst) {
        System.out.println("Loading Java Agent.");
        instrumentation = inst;
    }

    private static void loadInstrumentationAgent(String myName, byte[] buf) throws Exception {
        // Create agent jar file on the fly
        Manifest m = new Manifest();
        m.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        m.getMainAttributes().put(new Attributes.Name("Agent-Class"), myName);
        m.getMainAttributes().put(new Attributes.Name("Can-Redefine-Classes"), "true");
        File jarFile = File.createTempFile("agent", ".jar");
        jarFile.deleteOnExit();
        JarOutputStream jar = new JarOutputStream(new FileOutputStream(jarFile), m);
        jar.putNextEntry(new JarEntry(myName.replace('.', '/') + ".class"));
        jar.write(buf);
        jar.close();
        String pid = Long.valueOf(ProcessHandle.current().pid()).toString();
        System.out.println("Our pid is = " + pid);
        VirtualMachine vm = VirtualMachine.attach(pid);
        System.out.println(jarFile.getAbsolutePath());
        vm.loadAgent(jarFile.getAbsolutePath());
    }

    public static void main(String[] s) throws Exception {

        byte[] buf = getBytecodes(myName.substring(myName.lastIndexOf(".") + 1));
        loadInstrumentationAgent(myName, buf);

        buf = getBytecodes("TestClassOld");
        // Poor man's renaming of class "TestClassOld" to "TestClassXXX"
        replaceAllStrings(buf, "TestClassOld", "TestClassXXX");
        ClassVersionAfterRedefine cvar = new ClassVersionAfterRedefine();
        Class<?> old = cvar.defineClass(null, buf, 0, buf.length);
        Method foo = old.getMethod("foo");
        Object result = foo.invoke(null);
        assertTrue("java-lang-String".equals(result));
        System.out.println(old.getSimpleName() + ".foo() = " + result);

        buf = getBytecodes("TestClassNew");
        // Rename class "TestClassNew" to "TestClassXXX" so we can use it for
        // redefining the original version of "TestClassXXX" (i.e. "TestClassOld").
        replaceAllStrings(buf, "TestClassNew", "TestClassXXX");
        instrumentation.redefineClasses(new ClassDefinition(old, buf));
        result = foo.invoke(null);
        assertTrue("java.lang.String".equals(result));
        System.out.println(old.getSimpleName() + ".foo() = " + result);
    }
}
