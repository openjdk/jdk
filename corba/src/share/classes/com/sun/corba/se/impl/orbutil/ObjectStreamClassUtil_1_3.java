/*
 * Copyright (c) 2000, 2002, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.orbutil;

// for computing the structural UID
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.DigestOutputStream;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedAction;
import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.util.Arrays;
import java.util.Comparator;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Array;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;


import com.sun.corba.se.impl.io.ObjectStreamClass;

public final class ObjectStreamClassUtil_1_3 {

    // maintained here for backward compatability with JDK 1.3, where
    // writeObject method was not being checked at all, so there is
    // no need to lookup the ObjectStreamClass

    public static long computeSerialVersionUID(final Class cl) {

        long csuid = ObjectStreamClass.getSerialVersionUID(cl);
        if (csuid == 0)
            return csuid; // for non-serializable/proxy classes

        csuid = (ObjectStreamClassUtil_1_3.getSerialVersion(csuid, cl).longValue());
        return csuid;
    }


    // to maintain same suid as the JDK 1.3, we pick
    // up suid only for classes with private,static,final
    // declarations, and compute it for all others

    private static Long getSerialVersion(final long csuid, final Class cl)
    {
        return (Long) AccessController.doPrivileged(new PrivilegedAction() {
          public Object run() {
            long suid;
            try {
                final Field f = cl.getDeclaredField("serialVersionUID");
                int mods = f.getModifiers();
                if (Modifier.isStatic(mods) &&
                    Modifier.isFinal(mods) && Modifier.isPrivate(mods)) {
                    suid = csuid;
                 } else {
                    suid = _computeSerialVersionUID(cl);
                 }
              } catch (NoSuchFieldException ex) {
                  suid = _computeSerialVersionUID(cl);
              //} catch (IllegalAccessException ex) {
              //     suid = _computeSerialVersionUID(cl);
              }
              return new Long(suid);
           }
        });
    }

    public static long computeStructuralUID(boolean hasWriteObject, Class cl) {
        ByteArrayOutputStream devnull = new ByteArrayOutputStream(512);

        long h = 0;
        try {

            if ((!java.io.Serializable.class.isAssignableFrom(cl)) ||
                (cl.isInterface())){
                return 0;
            }

            if (java.io.Externalizable.class.isAssignableFrom(cl)) {
                return 1;
            }

            MessageDigest md = MessageDigest.getInstance("SHA");
            DigestOutputStream mdo = new DigestOutputStream(devnull, md);
            DataOutputStream data = new DataOutputStream(mdo);

            //In the old case, for the caller class, the write Method wasn't considered
            // for rep-id calculations correctly, but for parent classes it was taken
            // into account.  That is the reason there is the klude of getting the write
            // Object method in there

            // Get SUID of parent
            Class parent = cl.getSuperclass();
            if ((parent != null) && (parent != java.lang.Object.class)) {
                boolean hasWriteObjectFlag = false;
                Class [] args = {java.io.ObjectOutputStream.class};
                Method hasWriteObjectMethod = ObjectStreamClassUtil_1_3.getDeclaredMethod(parent, "writeObject", args,
                       Modifier.PRIVATE, Modifier.STATIC);
                if (hasWriteObjectMethod != null)
                    hasWriteObjectFlag = true;
                data.writeLong(ObjectStreamClassUtil_1_3.computeStructuralUID(hasWriteObjectFlag, parent));
            }

            if (hasWriteObject)
                data.writeInt(2);
            else
                data.writeInt(1);

            /* Sort the field names to get a deterministic order */
            Field[] field = ObjectStreamClassUtil_1_3.getDeclaredFields(cl);
            Arrays.sort(field, compareMemberByName);

            for (int i = 0; i < field.length; i++) {
                Field f = field[i];

                                /* Include in the hash all fields except those that are
                                 * transient or static.
                                 */
                int m = f.getModifiers();
                if (Modifier.isTransient(m) || Modifier.isStatic(m))
                    continue;

                data.writeUTF(f.getName());
                data.writeUTF(getSignature(f.getType()));
            }

            /* Compute the hash value for this class.
             * Use only the first 64 bits of the hash.
             */
            data.flush();
            byte hasharray[] = md.digest();
            int minimum = Math.min(8, hasharray.length);
            for (int i = minimum; i > 0; i--) {
                h += (long)(hasharray[i] & 255) << (i * 8);
            }
        } catch (IOException ignore) {
            /* can't happen, but be deterministic anyway. */
            h = -1;
        } catch (NoSuchAlgorithmException complain) {
            throw new SecurityException(complain.getMessage());
        }
        return h;
    }

    /*
     * Compute a hash for the specified class.  Incrementally add
     * items to the hash accumulating in the digest stream.
     * Fold the hash into a long.  Use the SHA secure hash function.
     */
    private static long _computeSerialVersionUID(Class cl) {
        ByteArrayOutputStream devnull = new ByteArrayOutputStream(512);

        long h = 0;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA");
            DigestOutputStream mdo = new DigestOutputStream(devnull, md);
            DataOutputStream data = new DataOutputStream(mdo);


            data.writeUTF(cl.getName());

            int classaccess = cl.getModifiers();
            classaccess &= (Modifier.PUBLIC | Modifier.FINAL |
                            Modifier.INTERFACE | Modifier.ABSTRACT);

            /* Workaround for javac bug that only set ABSTRACT for
             * interfaces if the interface had some methods.
             * The ABSTRACT bit reflects that the number of methods > 0.
             * This is required so correct hashes can be computed
             * for existing class files.
             * Previously this hack was previously present in the VM.
             */
            Method[] method = cl.getDeclaredMethods();
            if ((classaccess & Modifier.INTERFACE) != 0) {
                classaccess &= (~Modifier.ABSTRACT);
                if (method.length > 0) {
                    classaccess |= Modifier.ABSTRACT;
                }
            }

            data.writeInt(classaccess);

            /*
             * Get the list of interfaces supported,
             * Accumulate their names their names in Lexical order
             * and add them to the hash
             */
            if (!cl.isArray()) {
                /* In 1.2fcs, getInterfaces() was modified to return
                 * {java.lang.Cloneable, java.io.Serializable} when
                 * called on array classes.  These values would upset
                 * the computation of the hash, so we explicitly omit
                 * them from its computation.
                 */

                Class interfaces[] = cl.getInterfaces();
                Arrays.sort(interfaces, compareClassByName);

                for (int i = 0; i < interfaces.length; i++) {
                    data.writeUTF(interfaces[i].getName());
                }
            }

            /* Sort the field names to get a deterministic order */
            Field[] field = cl.getDeclaredFields();
            Arrays.sort(field, compareMemberByName);

            for (int i = 0; i < field.length; i++) {
                Field f = field[i];

                /* Include in the hash all fields except those that are
                 * private transient and private static.
                 */
                int m = f.getModifiers();
                if (Modifier.isPrivate(m) &&
                    (Modifier.isTransient(m) || Modifier.isStatic(m)))
                    continue;

                data.writeUTF(f.getName());
                data.writeInt(m);
                data.writeUTF(getSignature(f.getType()));
            }

            // need to find the java replacement for hasStaticInitializer
            if (hasStaticInitializer(cl)) {
                data.writeUTF("<clinit>");
                data.writeInt(Modifier.STATIC); // TBD: what modifiers does it have
                data.writeUTF("()V");
            }

            /*
             * Get the list of constructors including name and signature
             * Sort lexically, add all except the private constructors
             * to the hash with their access flags
             */

            MethodSignature[] constructors =
                MethodSignature.removePrivateAndSort(cl.getDeclaredConstructors());
            for (int i = 0; i < constructors.length; i++) {
                MethodSignature c = constructors[i];
                String mname = "<init>";
                String desc = c.signature;
                desc = desc.replace('/', '.');
                data.writeUTF(mname);
                data.writeInt(c.member.getModifiers());
                data.writeUTF(desc);
            }

            /* Include in the hash all methods except those that are
             * private transient and private static.
             */
            MethodSignature[] methods =
                MethodSignature.removePrivateAndSort(method);
            for (int i = 0; i < methods.length; i++ ) {
                MethodSignature m = methods[i];
                String desc = m.signature;
                desc = desc.replace('/', '.');
                data.writeUTF(m.member.getName());
                data.writeInt(m.member.getModifiers());
                data.writeUTF(desc);
            }

            /* Compute the hash value for this class.
             * Use only the first 64 bits of the hash.
             */
            data.flush();
            byte hasharray[] = md.digest();
            for (int i = 0; i < Math.min(8, hasharray.length); i++) {
                h += (long)(hasharray[i] & 255) << (i * 8);
            }
        } catch (IOException ignore) {
            /* can't happen, but be deterministic anyway. */
            h = -1;
        } catch (NoSuchAlgorithmException complain) {
            throw new SecurityException(complain.getMessage());
        }
        return h;
    }

    /*
     * Comparator object for Classes and Interfaces
     */
    private static Comparator compareClassByName =
        new CompareClassByName();

    private static class CompareClassByName implements Comparator {
        public int compare(Object o1, Object o2) {
            Class c1 = (Class)o1;
            Class c2 = (Class)o2;
            return (c1.getName()).compareTo(c2.getName());
        }
    }

    /*
     * Comparator object for Members, Fields, and Methods
     */
    private static Comparator compareMemberByName =
        new CompareMemberByName();

    private static class CompareMemberByName implements Comparator {
        public int compare(Object o1, Object o2) {
            String s1 = ((Member)o1).getName();
            String s2 = ((Member)o2).getName();

            if (o1 instanceof Method) {
                s1 += getSignature((Method)o1);
                s2 += getSignature((Method)o2);
            } else if (o1 instanceof Constructor) {
                s1 += getSignature((Constructor)o1);
                s2 += getSignature((Constructor)o2);
            }
            return s1.compareTo(s2);
        }
    }

    /**
     * Compute the JVM signature for the class.
     */
    private static String getSignature(Class clazz) {
        String type = null;
        if (clazz.isArray()) {
            Class cl = clazz;
            int dimensions = 0;
            while (cl.isArray()) {
                dimensions++;
                cl = cl.getComponentType();
            }
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < dimensions; i++) {
                sb.append("[");
            }
            sb.append(getSignature(cl));
            type = sb.toString();
        } else if (clazz.isPrimitive()) {
            if (clazz == Integer.TYPE) {
                type = "I";
            } else if (clazz == Byte.TYPE) {
                type = "B";
            } else if (clazz == Long.TYPE) {
                type = "J";
            } else if (clazz == Float.TYPE) {
                type = "F";
            } else if (clazz == Double.TYPE) {
                type = "D";
            } else if (clazz == Short.TYPE) {
                type = "S";
            } else if (clazz == Character.TYPE) {
                type = "C";
            } else if (clazz == Boolean.TYPE) {
                type = "Z";
            } else if (clazz == Void.TYPE) {
                type = "V";
            }
        } else {
            type = "L" + clazz.getName().replace('.', '/') + ";";
        }
        return type;
    }

    /*
     * Compute the JVM method descriptor for the method.
     */
    private static String getSignature(Method meth) {
        StringBuffer sb = new StringBuffer();

        sb.append("(");

        Class[] params = meth.getParameterTypes(); // avoid clone
        for (int j = 0; j < params.length; j++) {
            sb.append(getSignature(params[j]));
        }
        sb.append(")");
        sb.append(getSignature(meth.getReturnType()));
        return sb.toString();
    }

    /*
     * Compute the JVM constructor descriptor for the constructor.
     */
    private static String getSignature(Constructor cons) {
        StringBuffer sb = new StringBuffer();

        sb.append("(");

        Class[] params = cons.getParameterTypes(); // avoid clone
        for (int j = 0; j < params.length; j++) {
            sb.append(getSignature(params[j]));
        }
        sb.append(")V");
        return sb.toString();
    }

    private static Field[] getDeclaredFields(final Class clz) {
        return (Field[]) AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                return clz.getDeclaredFields();
            }
        });
    }

    private static class MethodSignature implements Comparator {
        Member member;
        String signature;      // cached parameter signature

        /* Given an array of Method or Constructor members,
           return a sorted array of the non-private members.*/
        /* A better implementation would be to implement the returned data
           structure as an insertion sorted link list.*/
        static MethodSignature[] removePrivateAndSort(Member[] m) {
            int numNonPrivate = 0;
            for (int i = 0; i < m.length; i++) {
                if (! Modifier.isPrivate(m[i].getModifiers())) {
                    numNonPrivate++;
                }
            }
            MethodSignature[] cm = new MethodSignature[numNonPrivate];
            int cmi = 0;
            for (int i = 0; i < m.length; i++) {
                if (! Modifier.isPrivate(m[i].getModifiers())) {
                    cm[cmi] = new MethodSignature(m[i]);
                    cmi++;
                }
            }
            if (cmi > 0)
                Arrays.sort(cm, cm[0]);
            return cm;
        }

        /* Assumes that o1 and o2 are either both methods
           or both constructors.*/
        public int compare(Object o1, Object o2) {
            /* Arrays.sort calls compare when o1 and o2 are equal.*/
            if (o1 == o2)
                return 0;

            MethodSignature c1 = (MethodSignature)o1;
            MethodSignature c2 = (MethodSignature)o2;

            int result;
            if (isConstructor()) {
                result = c1.signature.compareTo(c2.signature);
            } else { // is a Method.
                result = c1.member.getName().compareTo(c2.member.getName());
                if (result == 0)
                    result = c1.signature.compareTo(c2.signature);
            }
            return result;
        }

        final private boolean isConstructor() {
            return member instanceof Constructor;
        }
        private MethodSignature(Member m) {
            member = m;
            if (isConstructor()) {
                signature = ObjectStreamClassUtil_1_3.getSignature((Constructor)m);
            } else {
                signature = ObjectStreamClassUtil_1_3.getSignature((Method)m);
            }
        }
    }

    /* Find out if the class has a static class initializer <clinit> */
    // use java.io.ObjectStream's hasStaticInitializer method
    // private static native boolean hasStaticInitializer(Class cl);

    private static Method hasStaticInitializerMethod = null;
    /**
     * Returns true if the given class defines a static initializer method,
     * false otherwise.
     */
    private static boolean hasStaticInitializer(Class cl) {
        if (hasStaticInitializerMethod == null) {
            Class classWithThisMethod = null;

            try {
                try {
                    // When using rip-int with Merlin or when this is a Merlin
                    // workspace, the method we want is in sun.misc.ClassReflector
                    // and absent from java.io.ObjectStreamClass.
                    //
                    // When compiling rip-int with JDK 1.3.x, we have to get it
                    // from java.io.ObjectStreamClass.
                    classWithThisMethod = Class.forName("sun.misc.ClassReflector");
                } catch (ClassNotFoundException cnfe) {
                    // Do nothing.  This is either not a Merlin workspace,
                    // or rip-int is being compiled with something other than
                    // Merlin, probably JDK 1.3.  Fall back on java.io.ObjectStreaClass.
                }
                if (classWithThisMethod == null)
                    classWithThisMethod = java.io.ObjectStreamClass.class;

                hasStaticInitializerMethod =
                    classWithThisMethod.getDeclaredMethod("hasStaticInitializer",
                                                          new Class[] { Class.class });
            } catch (NoSuchMethodException ex) {
            }

            if (hasStaticInitializerMethod == null) {
                throw new InternalError("Can't find hasStaticInitializer method on "
                                        + classWithThisMethod.getName());
            }
            hasStaticInitializerMethod.setAccessible(true);
        }
        try {
            Boolean retval = (Boolean)
                hasStaticInitializerMethod.invoke(null, new Object[] { cl });
            return retval.booleanValue();
        } catch (Exception ex) {
            throw new InternalError("Error invoking hasStaticInitializer: "
                                    + ex);
        }
    }

    private static Method getDeclaredMethod(final Class cl, final String methodName, final Class[] args,
                                     final int requiredModifierMask,
                                     final int disallowedModifierMask) {
        return (Method) AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                Method method = null;
                try {
                    method =
                        cl.getDeclaredMethod(methodName, args);
                        int mods = method.getModifiers();
                        if ((mods & disallowedModifierMask) != 0 ||
                            (mods & requiredModifierMask) != requiredModifierMask) {
                            method = null;
                        }
                        //if (!Modifier.isPrivate(mods) ||
                        //    Modifier.isStatic(mods)) {
                        //    method = null;
                        //}
                } catch (NoSuchMethodException e) {
                // Since it is alright if methodName does not exist,
                // no need to do anything special here.
                }
                return method;
            }
        });
    }

}
