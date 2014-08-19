/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.rmi.rmic;

import java.util.Vector;
import java.util.Hashtable;
import java.util.Enumeration;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.security.MessageDigest;
import java.security.DigestOutputStream;
import java.security.NoSuchAlgorithmException;
import sun.tools.java.Type;
import sun.tools.java.ClassDefinition;
import sun.tools.java.ClassDeclaration;
import sun.tools.java.MemberDefinition;
import sun.tools.java.Identifier;
import sun.tools.java.ClassNotFound;

/**
 * A RemoteClass object encapsulates RMI-specific information about
 * a remote implementation class, i.e. a class that implements
 * one or more remote interfaces.
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 *
 * @author      Peter Jones
 */
public class RemoteClass implements sun.rmi.rmic.RMIConstants {

    /**
     * Create a RemoteClass object representing the remote meta-information
     * of the given class.
     *
     * Returns true if successful.  If the class is not a properly formed
     * remote implementation class or if some other error occurs, the
     * return value will be null, and errors will have been reported to
     * the supplied BatchEnvironment.
     */
    public static RemoteClass forClass(BatchEnvironment env,
                                       ClassDefinition implClassDef)
    {
        RemoteClass rc = new RemoteClass(env, implClassDef);
        if (rc.initialize()) {
            return rc;
        } else {
            return null;
        }
    }

    /**
     * Return the ClassDefinition for this class.
     */
    public ClassDefinition getClassDefinition() {
        return implClassDef;
    }

    /**
     * Return the name of the class represented by this object.
     */
    public Identifier getName() {
        return implClassDef.getName();
    }

    /**
     * Return an array of ClassDefinitions representing all of the remote
     * interfaces implemented by this class.
     *
     * A remote interface is any interface that extends Remote,
     * directly or indirectly.  The remote interfaces of a class
     * are the interfaces directly listed in either the class's
     * "implements" clause, or the "implements" clause of any
     * of its superclasses, that are remote interfaces.
     *
     * The order of the array returned is arbitrary, and some elements
     * may be superfluous (i.e., superinterfaces of other interfaces
     * in the array).
     */
    public ClassDefinition[] getRemoteInterfaces() {
        return remoteInterfaces.clone();
    }

    /**
     * Return an array of RemoteClass.Method objects representing all of
     * the remote methods implemented by this class, i.e. all of the
     * methods in the class's remote interfaces.
     *
     * The methods in the array are ordered according to the comparision
     * of the strings consisting of their method name followed by their
     * type signature, so each method's index in the array corresponds
     * to its "operation number" in the JDK 1.1 version of the
     * stub/skeleton protocol.
     */
    public Method[] getRemoteMethods() {
        return remoteMethods.clone();
    }

    /**
     * Return the "interface hash" used to match a stub/skeleton pair for
     * this class in the JDK 1.1 version of the stub/skeleton protocol.
     */
    public long getInterfaceHash() {
        return interfaceHash;
    }

    /**
     * Return string representation of this object, consisting of
     * the string "remote class " followed by the class name.
     */
    public String toString() {
        return "remote class " + implClassDef.getName().toString();
    }

    /** rmic environment for this object */
    private BatchEnvironment env;

    /** the remote implementation class this object corresponds to */
    private ClassDefinition implClassDef;

    /** remote interfaces implemented by this class */
    private ClassDefinition[] remoteInterfaces;

    /** all the remote methods of this class */
    private Method[] remoteMethods;

    /** stub/skeleton "interface hash" for this class */
    private long interfaceHash;

    /** cached definition for certain classes used in this environment */
    private ClassDefinition defRemote;
    private ClassDefinition defException;
    private ClassDefinition defRemoteException;

    /**
     * Create a RemoteClass instance for the given class.  The resulting
     * object is not yet initialized.
     */
    private RemoteClass(BatchEnvironment env, ClassDefinition implClassDef) {
        this.env = env;
        this.implClassDef = implClassDef;
    }

    /**
     * Validate that the remote implementation class is properly formed
     * and fill in the data structures required by the public interface.
     */
    private boolean initialize() {
        /*
         * Verify that the "impl" is really a class, not an interface.
         */
        if (implClassDef.isInterface()) {
            env.error(0, "rmic.cant.make.stubs.for.interface",
                      implClassDef.getName());
            return false;
        }

        /*
         * Initialize cached definitions for the Remote interface and
         * the RemoteException class.
         */
        try {
            defRemote =
                env.getClassDeclaration(idRemote).getClassDefinition(env);
            defException =
                env.getClassDeclaration(idJavaLangException).
                getClassDefinition(env);
            defRemoteException =
                env.getClassDeclaration(idRemoteException).
                getClassDefinition(env);
        } catch (ClassNotFound e) {
            env.error(0, "rmic.class.not.found", e.name);
            return false;
        }

        /*
         * Here we find all of the remote interfaces of our remote
         * implementation class.  For each class up the superclass
         * chain, add each directly-implemented interface that
         * somehow extends Remote to a list.
         */
        Vector<ClassDefinition> remotesImplemented = // list of remote interfaces found
            new Vector<ClassDefinition>();
        for (ClassDefinition classDef = implClassDef;
             classDef != null;)
            {
                try {
                    ClassDeclaration[] interfaces = classDef.getInterfaces();
                    for (int i = 0; i < interfaces.length; i++) {
                        ClassDefinition interfaceDef =
                            interfaces[i].getClassDefinition(env);
                        /*
                         * Add interface to the list if it extends Remote and
                         * it is not already there.
                         */
                        if (!remotesImplemented.contains(interfaceDef) &&
                            defRemote.implementedBy(env, interfaces[i]))
                            {
                                remotesImplemented.addElement(interfaceDef);
                                /***** <DEBUG> */
                                if (env.verbose()) {
                                    System.out.println("[found remote interface: " +
                                                       interfaceDef.getName() + "]");
                                    /***** </DEBUG> */
                                }
                            }
                    }

                    /*
                     * Verify that the candidate remote implementation class
                     * implements at least one remote interface directly.
                     */
                    if (classDef == implClassDef && remotesImplemented.isEmpty()) {
                        if (defRemote.implementedBy(env,
                                                    implClassDef.getClassDeclaration()))
                            {
                                /*
                                 * This error message is used if the class does
                                 * implement a remote interface through one of
                                 * its superclasses, but not directly.
                                 */
                                env.error(0, "rmic.must.implement.remote.directly",
                                          implClassDef.getName());
                            } else {
                                /*
                                 * This error message is used if the class never
                                 * implements a remote interface.
                                 */
                                env.error(0, "rmic.must.implement.remote",
                                          implClassDef.getName());
                            }
                        return false;
                    }

                    /*
                     * Get definition for next superclass.
                     */
                    classDef = (classDef.getSuperClass() != null ?
                                classDef.getSuperClass().getClassDefinition(env) :
                                null);

                } catch (ClassNotFound e) {
                    env.error(0, "class.not.found", e.name, classDef.getName());
                    return false;
                }
            }

        /*
         * The "remotesImplemented" vector now contains all of the remote
         * interfaces directly implemented by the remote class or by any
         * of its superclasses.
         *
         * At this point, we could optimize the list by removing superfluous
         * entries, i.e. any interfaces that are implemented by some other
         * interface in the list anyway.
         *
         * This should be correct; would it be worthwhile?
         *
         *      for (int i = 0; i < remotesImplemented.size();) {
         *          ClassDefinition interfaceDef =
         *              (ClassDefinition) remotesImplemented.elementAt(i);
         *          boolean isOtherwiseImplemented = false;
         *          for (int j = 0; j < remotesImplemented.size; j++) {
         *              if (j != i &&
         *                  interfaceDef.implementedBy(env, (ClassDefinition)
         *                  remotesImplemented.elementAt(j).
         *                      getClassDeclaration()))
         *              {
         *                  isOtherwiseImplemented = true;
         *                  break;
         *              }
         *          }
         *          if (isOtherwiseImplemented) {
         *              remotesImplemented.removeElementAt(i);
         *          } else {
         *              ++i;
         *          }
         *      }
         */

        /*
         * Now we collect the methods from all of the remote interfaces
         * into a hashtable.
         */
        Hashtable<String, Method> methods = new Hashtable<String, Method>();
        boolean errors = false;
        for (Enumeration<ClassDefinition> enumeration
                 = remotesImplemented.elements();
             enumeration.hasMoreElements();)
            {
                ClassDefinition interfaceDef = enumeration.nextElement();
                if (!collectRemoteMethods(interfaceDef, methods))
                    errors = true;
            }
        if (errors)
            return false;

        /*
         * Convert vector of remote interfaces to an array
         * (order is not important for this array).
         */
        remoteInterfaces = new ClassDefinition[remotesImplemented.size()];
        remotesImplemented.copyInto(remoteInterfaces);

        /*
         * Sort table of remote methods into an array.  The elements are
         * sorted in ascending order of the string of the method's name
         * and type signature, so that each elements index is equal to
         * its operation number of the JDK 1.1 version of the stub/skeleton
         * protocol.
         */
        String[] orderedKeys = new String[methods.size()];
        int count = 0;
        for (Enumeration<Method> enumeration = methods.elements();
             enumeration.hasMoreElements();)
            {
                Method m = enumeration.nextElement();
                String key = m.getNameAndDescriptor();
                int i;
                for (i = count; i > 0; --i) {
                    if (key.compareTo(orderedKeys[i - 1]) >= 0) {
                        break;
                    }
                    orderedKeys[i] = orderedKeys[i - 1];
                }
                orderedKeys[i] = key;
                ++count;
            }
        remoteMethods = new Method[methods.size()];
        for (int i = 0; i < remoteMethods.length; i++) {
            remoteMethods[i] = methods.get(orderedKeys[i]);
            /***** <DEBUG> */
            if (env.verbose()) {
                System.out.print("[found remote method <" + i + ">: " +
                                 remoteMethods[i].getOperationString());
                ClassDeclaration[] exceptions =
                    remoteMethods[i].getExceptions();
                if (exceptions.length > 0)
                    System.out.print(" throws ");
                for (int j = 0; j < exceptions.length; j++) {
                    if (j > 0)
                        System.out.print(", ");
                    System.out.print(exceptions[j].getName());
                }
                System.out.println("]");
            }
            /***** </DEBUG> */
        }

        /**
         * Finally, pre-compute the interface hash to be used by
         * stubs/skeletons for this remote class.
         */
        interfaceHash = computeInterfaceHash();

        return true;
    }

    /**
     * Collect and validate all methods from given interface and all of
     * its superinterfaces as remote methods.  Remote methods are added
     * to the supplied hashtable.  Returns true if successful,
     * or false if an error occurred.
     */
    private boolean collectRemoteMethods(ClassDefinition interfaceDef,
                                         Hashtable<String, Method> table)
    {
        if (!interfaceDef.isInterface()) {
            throw new Error(
                            "expected interface, not class: " + interfaceDef.getName());
        }

        /*
         * rmic used to enforce that a remote interface could not extend
         * a non-remote interface, i.e. an interface that did not itself
         * extend from Remote.  The current version of rmic does not have
         * this restriction, so the following code is now commented out.
         *
         * Verify that this interface extends Remote, since all interfaces
         * extended by a remote interface must implement Remote.
         *
         *      try {
         *          if (!defRemote.implementedBy(env,
         *              interfaceDef.getClassDeclaration()))
         *          {
         *              env.error(0, "rmic.can.mix.remote.nonremote",
         *                  interfaceDef.getName());
         *              return false;
         *          }
         *      } catch (ClassNotFound e) {
         *          env.error(0, "class.not.found", e.name,
         *              interfaceDef.getName());
         *          return false;
         *      }
         */

        boolean errors = false;

        /*
         * Search interface's members for methods.
         */
    nextMember:
        for (MemberDefinition member = interfaceDef.getFirstMember();
             member != null;
             member = member.getNextMember())
            {
                if (member.isMethod() &&
                    !member.isConstructor() && !member.isInitializer())
                    {
                        /*
                         * Verify that each method throws RemoteException.
                         */
                        ClassDeclaration[] exceptions = member.getExceptions(env);
                        boolean hasRemoteException = false;
                        for (int i = 0; i < exceptions.length; i++) {
                            /*
                             * rmic used to enforce that a remote method had to
                             * explicitly list RemoteException in its "throws"
                             * clause; i.e., just throwing Exception was not
                             * acceptable.  The current version of rmic does not
                             * have this restriction, so the following code is
                             * now commented out.  Instead, the method is
                             * considered valid if RemoteException is a subclass
                             * of any of the methods declared exceptions.
                             *
                             *  if (exceptions[i].getName().equals(
                             *      idRemoteException))
                             *  {
                             *      hasRemoteException = true;
                             *      break;
                             *  }
                             */
                            try {
                                if (defRemoteException.subClassOf(
                                                                  env, exceptions[i]))
                                    {
                                        hasRemoteException = true;
                                        break;
                                    }
                            } catch (ClassNotFound e) {
                                env.error(0, "class.not.found", e.name,
                                          interfaceDef.getName());
                                continue nextMember;
                            }
                        }
                        /*
                         * If this method did not throw RemoteException as required,
                         * generate the error but continue, so that multiple such
                         * errors can be reported.
                         */
                        if (!hasRemoteException) {
                            env.error(0, "rmic.must.throw.remoteexception",
                                      interfaceDef.getName(), member.toString());
                            errors = true;
                            continue nextMember;
                        }

                        /*
                         * Verify that the implementation of this method throws only
                         * java.lang.Exception or its subclasses (fix bugid 4092486).
                         * JRMP does not support remote methods throwing
                         * java.lang.Throwable or other subclasses.
                         */
                        try {
                            MemberDefinition implMethod = implClassDef.findMethod(
                                                                                  env, member.getName(), member.getType());
                            if (implMethod != null) {           // should not be null
                                exceptions = implMethod.getExceptions(env);
                                for (int i = 0; i < exceptions.length; i++) {
                                    if (!defException.superClassOf(
                                                                   env, exceptions[i]))
                                        {
                                            env.error(0, "rmic.must.only.throw.exception",
                                                      implMethod.toString(),
                                                      exceptions[i].getName());
                                            errors = true;
                                            continue nextMember;
                                        }
                                }
                            }
                        } catch (ClassNotFound e) {
                            env.error(0, "class.not.found", e.name,
                                      implClassDef.getName());
                            continue nextMember;
                        }

                        /*
                         * Create RemoteClass.Method object to represent this method
                         * found in a remote interface.
                         */
                        Method newMethod = new Method(member);
                        /*
                         * Store remote method's representation in the table of
                         * remote methods found, keyed by its name and parameter
                         * signature.
                         *
                         * If the table already contains an entry with the same
                         * method name and parameter signature, then we must
                         * replace the old entry with a Method object that
                         * represents a legal combination of the old and the new
                         * methods; specifically, the combined method must have
                         * a throws list that contains (only) all of the checked
                         * exceptions that can be thrown by both the old or
                         * the new method (see bugid 4070653).
                         */
                        String key = newMethod.getNameAndDescriptor();
                        Method oldMethod = table.get(key);
                        if (oldMethod != null) {
                            newMethod = newMethod.mergeWith(oldMethod);
                            if (newMethod == null) {
                                errors = true;
                                continue nextMember;
                            }
                        }
                        table.put(key, newMethod);
                    }
            }

        /*
         * Recursively collect methods for all superinterfaces.
         */
        try {
            ClassDeclaration[] superDefs = interfaceDef.getInterfaces();
            for (int i = 0; i < superDefs.length; i++) {
                ClassDefinition superDef =
                    superDefs[i].getClassDefinition(env);
                if (!collectRemoteMethods(superDef, table))
                    errors = true;
            }
        } catch (ClassNotFound e) {
            env.error(0, "class.not.found", e.name, interfaceDef.getName());
            return false;
        }

        return !errors;
    }

    /**
     * Compute the "interface hash" of the stub/skeleton pair for this
     * remote implementation class.  This is the 64-bit value used to
     * enforce compatibility between a stub and a skeleton using the
     * JDK 1.1 version of the stub/skeleton protocol.
     *
     * It is calculated using the first 64 bits of a SHA digest.  The
     * digest is from a stream consisting of the following data:
     *     (int) stub version number, always 1
     *     for each remote method, in order of operation number:
     *         (UTF) method name
     *         (UTF) method type signature
     *         for each declared exception, in alphabetical name order:
     *             (UTF) name of exception class
     *
     */
    private long computeInterfaceHash() {
        long hash = 0;
        ByteArrayOutputStream sink = new ByteArrayOutputStream(512);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA");
            DataOutputStream out = new DataOutputStream(
                                                        new DigestOutputStream(sink, md));

            out.writeInt(INTERFACE_HASH_STUB_VERSION);
            for (int i = 0; i < remoteMethods.length; i++) {
                MemberDefinition m = remoteMethods[i].getMemberDefinition();
                Identifier name = m.getName();
                Type type = m.getType();

                out.writeUTF(name.toString());
                // type signatures already use mangled class names
                out.writeUTF(type.getTypeSignature());

                ClassDeclaration exceptions[] = m.getExceptions(env);
                sortClassDeclarations(exceptions);
                for (int j = 0; j < exceptions.length; j++) {
                    out.writeUTF(Names.mangleClass(
                                                   exceptions[j].getName()).toString());
                }
            }
            out.flush();

            // use only the first 64 bits of the digest for the hash
            byte hashArray[] = md.digest();
            for (int i = 0; i < Math.min(8, hashArray.length); i++) {
                hash += ((long) (hashArray[i] & 0xFF)) << (i * 8);
            }
        } catch (IOException e) {
            throw new Error(
                            "unexpected exception computing intetrface hash: " + e);
        } catch (NoSuchAlgorithmException e) {
            throw new Error(
                            "unexpected exception computing intetrface hash: " + e);
        }

        return hash;
    }

    /**
     * Sort array of class declarations alphabetically by their mangled
     * fully-qualified class name.  This is used to feed a method's exceptions
     * in a canonical order into the digest stream for the interface hash
     * computation.
     */
    private void sortClassDeclarations(ClassDeclaration[] decl) {
        for (int i = 1; i < decl.length; i++) {
            ClassDeclaration curr = decl[i];
            String name = Names.mangleClass(curr.getName()).toString();
            int j;
            for (j = i; j > 0; j--) {
                if (name.compareTo(
                                   Names.mangleClass(decl[j - 1].getName()).toString()) >= 0)
                    {
                        break;
                    }
                decl[j] = decl[j - 1];
            }
            decl[j] = curr;
        }
    }


    /**
     * A RemoteClass.Method object encapsulates RMI-specific information
     * about a particular remote method in the remote implementation class
     * represented by the outer instance.
     */
    public class Method implements Cloneable {

        /**
         * Return the definition of the actual class member corresponing
         * to this method of a remote interface.
         *
         * REMIND: Can this method be removed?
         */
        public MemberDefinition getMemberDefinition() {
            return memberDef;
        }

        /**
         * Return the name of this method.
         */
        public Identifier getName() {
            return memberDef.getName();
        }

        /**
         * Return the type of this method.
         */
        public Type getType() {
            return memberDef.getType();
        }

        /**
         * Return an array of the exception classes declared to be
         * thrown by this remote method.
         *
         * For methods with the same name and type signature inherited
         * from multiple remote interfaces, the array will contain
         * the set of exceptions declared in all of the interfaces'
         * methods that can be legally thrown in each of them.
         */
        public ClassDeclaration[] getExceptions() {
            return exceptions.clone();
        }

        /**
         * Return the "method hash" used to identify this remote method
         * in the JDK 1.2 version of the stub protocol.
         */
        public long getMethodHash() {
            return methodHash;
        }

        /**
         * Return the string representation of this method.
         */
        public String toString() {
            return memberDef.toString();
        }

        /**
         * Return the string representation of this method appropriate
         * for the construction of a java.rmi.server.Operation object.
         */
        public String getOperationString() {
            return memberDef.toString();
        }

        /**
         * Return a string consisting of this method's name followed by
         * its method descriptor, using the Java VM's notation for
         * method descriptors (see section 4.3.3 of The Java Virtual
         * Machine Specification).
         */
        public String getNameAndDescriptor() {
            return memberDef.getName().toString() +
                memberDef.getType().getTypeSignature();
        }

        /**
         * Member definition for this method, from one of the remote
         * interfaces that this method was found in.
         *
         * Note that this member definition may be only one of several
         * member defintions that correspond to this remote method object,
         * if several of this class's remote interfaces contain methods
         * with the same name and type signature.  Therefore, this member
         * definition may declare more exceptions thrown that this remote
         * method does.
         */
        private MemberDefinition memberDef;

        /** stub "method hash" to identify this method */
        private long methodHash;

        /**
         * Exceptions declared to be thrown by this remote method.
         *
         * This list can include superfluous entries, such as
         * unchecked exceptions and subclasses of other entries.
         */
        private ClassDeclaration[] exceptions;

        /**
         * Create a new Method object corresponding to the given
         * method definition.
         */
        /*
         * Temporarily comment out the private modifier until
         * the VM allows outer class to access inner class's
         * private constructor
         */
        /* private */ Method(MemberDefinition memberDef) {
            this.memberDef = memberDef;
            exceptions = memberDef.getExceptions(env);
            methodHash = computeMethodHash();
        }

        /**
         * Cloning is supported by returning a shallow copy of this object.
         */
        protected Object clone() {
            try {
                return super.clone();
            } catch (CloneNotSupportedException e) {
                throw new Error("clone failed");
            }
        }

        /**
         * Return a new Method object that is a legal combination of
         * this method object and another one.
         *
         * This requires determining the exceptions declared by the
         * combined method, which must be (only) all of the exceptions
         * declared in both old Methods that may thrown in either of
         * them.
         */
        private Method mergeWith(Method other) {
            if (!getName().equals(other.getName()) ||
                !getType().equals(other.getType()))
                {
                    throw new Error("attempt to merge method \"" +
                                    other.getNameAndDescriptor() + "\" with \"" +
                                    getNameAndDescriptor());
                }

            Vector<ClassDeclaration> legalExceptions
                = new Vector<ClassDeclaration>();
            try {
                collectCompatibleExceptions(
                                            other.exceptions, exceptions, legalExceptions);
                collectCompatibleExceptions(
                                            exceptions, other.exceptions, legalExceptions);
            } catch (ClassNotFound e) {
                env.error(0, "class.not.found", e.name,
                          getClassDefinition().getName());
                return null;
            }

            Method merged = (Method) clone();
            merged.exceptions = new ClassDeclaration[legalExceptions.size()];
            legalExceptions.copyInto(merged.exceptions);

            return merged;
        }

        /**
         * Add to the supplied list all exceptions in the "from" array
         * that are subclasses of an exception in the "with" array.
         */
        private void collectCompatibleExceptions(ClassDeclaration[] from,
                                                 ClassDeclaration[] with,
                                                 Vector<ClassDeclaration> list)
            throws ClassNotFound
        {
            for (int i = 0; i < from.length; i++) {
                ClassDefinition exceptionDef = from[i].getClassDefinition(env);
                if (!list.contains(from[i])) {
                    for (int j = 0; j < with.length; j++) {
                        if (exceptionDef.subClassOf(env, with[j])) {
                            list.addElement(from[i]);
                            break;
                        }
                    }
                }
            }
        }

        /**
         * Compute the "method hash" of this remote method.  The method
         * hash is a long containing the first 64 bits of the SHA digest
         * from the UTF encoded string of the method name and descriptor.
         *
         * REMIND: Should this method share implementation code with
         * the outer class's computeInterfaceHash() method?
         */
        private long computeMethodHash() {
            long hash = 0;
            ByteArrayOutputStream sink = new ByteArrayOutputStream(512);
            try {
                MessageDigest md = MessageDigest.getInstance("SHA");
                DataOutputStream out = new DataOutputStream(
                                                            new DigestOutputStream(sink, md));

                String methodString = getNameAndDescriptor();
                /***** <DEBUG> */
                if (env.verbose()) {
                    System.out.println("[string used for method hash: \"" +
                                       methodString + "\"]");
                }
                /***** </DEBUG> */
                out.writeUTF(methodString);

                // use only the first 64 bits of the digest for the hash
                out.flush();
                byte hashArray[] = md.digest();
                for (int i = 0; i < Math.min(8, hashArray.length); i++) {
                    hash += ((long) (hashArray[i] & 0xFF)) << (i * 8);
                }
            } catch (IOException e) {
                throw new Error(
                                "unexpected exception computing intetrface hash: " + e);
            } catch (NoSuchAlgorithmException e) {
                throw new Error(
                                "unexpected exception computing intetrface hash: " + e);
            }

            return hash;
        }
    }
}
