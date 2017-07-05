/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

package sun.dyn.anon;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import sun.misc.IOUtils;

/**
 * Anonymous class loader.  Will load any valid classfile, producing
 * a {@link Class} metaobject, without installing that class in the
 * system dictionary.  Therefore, {@link Class#forName(String)} will never
 * produce a reference to an anonymous class.
 * <p>
 * The access permissions of the anonymous class are borrowed from
 * a <em>host class</em>.  The new class behaves as if it were an
 * inner class of the host class.  It can access the host's private
 * members, if the creator of the class loader has permission to
 * do so (or to create accessible reflective objects).
 * <p>
 * When the anonymous class is loaded, elements of its constant pool
 * can be patched to new values.  This provides a hook to pre-resolve
 * named classes in the constant pool to other classes, including
 * anonymous ones.  Also, string constants can be pre-resolved to
 * any reference.  (The verifier treats non-string, non-class reference
 * constants as plain objects.)
 *  <p>
 * Why include the patching function?  It makes some use cases much easier.
 * Second, the constant pool needed some internal patching anyway,
 * to anonymize the loaded class itself.  Finally, if you are going
 * to use this seriously, you'll want to build anonymous classes
 * on top of pre-existing anonymous classes, and that requires patching.
 *
 * <p>%%% TO-DO:
 * <ul>
 * <li>needs better documentation</li>
 * <li>needs more security work (for safe delegation)</li>
 * <li>needs a clearer story about error processing</li>
 * <li>patch member references also (use ';' as delimiter char)</li>
 * <li>patch method references to (conforming) method handles</li>
 * </ul>
 *
 * @author jrose
 * @author Remi Forax
 * @see <a href="http://blogs.sun.com/jrose/entry/anonymous_classes_in_the_vm">
 *      http://blogs.sun.com/jrose/entry/anonymous_classes_in_the_vm</a>
 */

public class AnonymousClassLoader {
    final Class<?> hostClass;

    // Note: Do not refactor the calls to checkHostClass unless you
    //       also adjust this constant:
    private static int CHC_CALLERS = 3;

    public AnonymousClassLoader() {
        this.hostClass = checkHostClass(null);
    }
    public AnonymousClassLoader(Class<?> hostClass) {
        this.hostClass = checkHostClass(hostClass);
    }

    private static Class<?> getTopLevelClass(Class<?> clazz) {
      for(Class<?> outer = clazz.getDeclaringClass(); outer != null;
          outer = outer.getDeclaringClass()) {
        clazz = outer;
      }
      return clazz;
    }

    private static Class<?> checkHostClass(Class<?> hostClass) {
        // called only from the constructor
        // does a context-sensitive check on caller class
        // CC[0..3] = {Reflection, this.checkHostClass, this.<init>, caller}
        Class<?> caller = sun.reflect.Reflection.getCallerClass(CHC_CALLERS);

        if (caller == null) {
            // called from the JVM directly
            if (hostClass == null)
                return AnonymousClassLoader.class; // anything central will do
            return hostClass;
        }

        if (hostClass == null)
            hostClass = caller; // default value is caller itself

        // anonymous class will access hostClass on behalf of caller
        Class<?> callee = hostClass;

        if (caller == callee)
            // caller can always nominate itself to grant caller's own access rights
            return hostClass;

        // normalize caller and callee to their top-level classes:
        caller = getTopLevelClass(caller);
        callee = getTopLevelClass(callee);
        if (caller == callee)
            return caller;

        ClassLoader callerCL = caller.getClassLoader();
        if (callerCL == null) {
            // caller is trusted code, so accept the proposed hostClass
            return hostClass;
        }

        // %%% should do something with doPrivileged, because trusted
        // code should have a way to execute on behalf of
        // partially-trusted clients

        // Does the caller have the right to access the private
        // members of the callee?  If not, raise an error.
        final int ACC_PRIVATE = 2;
        try {
            sun.reflect.Reflection.ensureMemberAccess(caller, callee, null, ACC_PRIVATE);
        } catch (IllegalAccessException ee) {
            throw new IllegalArgumentException(ee);
        }

        return hostClass;
    }

    public Class<?> loadClass(byte[] classFile) {
        if (defineAnonymousClass == null) {
            // no JVM support; try to fake an approximation
            try {
                return fakeLoadClass(new ConstantPoolParser(classFile).createPatch());
            } catch (InvalidConstantPoolFormatException ee) {
                throw new IllegalArgumentException(ee);
            }
        }
        return loadClass(classFile, null);
    }

    public Class<?> loadClass(ConstantPoolPatch classPatch) {
        if (defineAnonymousClass == null) {
            // no JVM support; try to fake an approximation
            return fakeLoadClass(classPatch);
        }
        Object[] patches = classPatch.patchArray;
        // Convert class names (this late in the game)
        // to use slash '/' instead of dot '.'.
        // Java likes dots, but the JVM likes slashes.
        for (int i = 0; i < patches.length; i++) {
            Object value = patches[i];
            if (value != null) {
                byte tag = classPatch.getTag(i);
                switch (tag) {
                case ConstantPoolVisitor.CONSTANT_Class:
                    if (value instanceof String) {
                        if (patches == classPatch.patchArray)
                            patches = patches.clone();
                        patches[i] = ((String)value).replace('.', '/');
                    }
                    break;
                case ConstantPoolVisitor.CONSTANT_Fieldref:
                case ConstantPoolVisitor.CONSTANT_Methodref:
                case ConstantPoolVisitor.CONSTANT_InterfaceMethodref:
                case ConstantPoolVisitor.CONSTANT_NameAndType:
                    // When/if the JVM supports these patches,
                    // we'll probably need to reformat them also.
                    // Meanwhile, let the class loader create the error.
                    break;
                }
            }
        }
        return loadClass(classPatch.outer.classFile, classPatch.patchArray);
    }

    private Class<?> loadClass(byte[] classFile, Object[] patchArray) {
        try {
            return (Class<?>)
                defineAnonymousClass.invoke(unsafe,
                                            hostClass, classFile, patchArray);
        } catch (Exception ex) {
            throwReflectedException(ex);
            throw new RuntimeException("error loading into "+hostClass, ex);
        }
    }

    private static void throwReflectedException(Exception ex) {
        if (ex instanceof InvocationTargetException) {
            Throwable tex = ((InvocationTargetException)ex).getTargetException();
            if (tex instanceof Error)
                throw (Error) tex;
            ex = (Exception) tex;
        }
        if (ex instanceof RuntimeException) {
            throw (RuntimeException) ex;
        }
    }

    private Class<?> fakeLoadClass(ConstantPoolPatch classPatch) {
        // Implementation:
        // 1. Make up a new name nobody has used yet.
        // 2. Inspect the tail-header of the class to find the this_class index.
        // 3. Patch the CONSTANT_Class for this_class to the new name.
        // 4. Add other CP entries required by (e.g.) string patches.
        // 5. Flatten Class constants down to their names, making sure that
        //    the host class loader can pick them up again accurately.
        // 6. Generate the edited class file bytes.
        //
        // Potential limitations:
        // * The class won't be truly anonymous, and may interfere with others.
        // * Flattened class constants might not work, because of loader issues.
        // * Pseudo-string constants will not flatten down to real strings.
        // * Method handles will (of course) fail to flatten to linkage strings.
        if (true)  throw new UnsupportedOperationException("NYI");
        Object[] cpArray;
        try {
            cpArray = classPatch.getOriginalCP();
        } catch (InvalidConstantPoolFormatException ex) {
            throw new RuntimeException(ex);
        }
        int thisClassIndex = classPatch.getParser().getThisClassIndex();
        String thisClassName = (String) cpArray[thisClassIndex];
        synchronized (AnonymousClassLoader.class) {
            thisClassName = thisClassName+"\\|"+(++fakeNameCounter);
        }
        classPatch.putUTF8(thisClassIndex, thisClassName);
        byte[] classFile = null;
        return unsafe.defineClass(null, classFile, 0, classFile.length,
                                  hostClass.getClassLoader(),
                                  hostClass.getProtectionDomain());
    }
    private static int fakeNameCounter = 99999;

    // ignore two warnings on this line:
    static sun.misc.Unsafe unsafe = sun.misc.Unsafe.getUnsafe();
    // preceding line requires that this class be on the boot class path

    static private final Method defineAnonymousClass;
    static {
        Method dac = null;
        Class<? extends sun.misc.Unsafe> unsafeClass = unsafe.getClass();
        try {
            dac = unsafeClass.getMethod("defineAnonymousClass",
                                        Class.class,
                                        byte[].class,
                                        Object[].class);
        } catch (Exception ee) {
            dac = null;
        }
        defineAnonymousClass = dac;
    }

    private static void noJVMSupport() {
        throw new UnsupportedOperationException("no JVM support for anonymous classes");
    }


    private static native Class<?> loadClassInternal(Class<?> hostClass,
                                                     byte[] classFile,
                                                     Object[] patchArray);

    public static byte[] readClassFile(Class<?> templateClass) throws IOException {
        String templateName = templateClass.getName();
        int lastDot = templateName.lastIndexOf('.');
        java.net.URL url = templateClass.getResource(templateName.substring(lastDot+1)+".class");
        java.net.URLConnection connection = url.openConnection();
        int contentLength = connection.getContentLength();
        if (contentLength < 0)
            throw new IOException("invalid content length "+contentLength);

        return IOUtils.readFully(connection.getInputStream(), contentLength, true);
    }
}
