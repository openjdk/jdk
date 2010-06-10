/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
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

/*
 * Licensed Materials - Property of IBM
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 */

package sun.rmi.rmic.iiop;

import java.util.Vector;
import sun.tools.java.Identifier;
import sun.tools.java.ClassNotFound;
import sun.tools.java.ClassDefinition;
import sun.tools.java.ClassDeclaration;
import sun.tools.java.MemberDefinition;
import sun.tools.java.CompilerError;
import sun.tools.tree.Node;
import sun.tools.tree.LocalMember;
import sun.tools.tree.CharExpression;
import sun.tools.tree.IntegerExpression;
import sun.rmi.rmic.IndentingWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Enumeration;
import java.io.File;

/**
 * A CompoundType is an abstract base class for all IIOP class and
 * interface types.
 *
 * @author      Bryan Atsatt
 */
public abstract class CompoundType extends Type {

    protected Method[] methods;
    protected InterfaceType[] interfaces;
    protected Member[] members;
    protected ClassDefinition classDef;
    protected ClassDeclaration classDecl;

    protected boolean isCORBAObject = false;
    protected boolean isIDLEntity = false;
    protected boolean isAbstractBase = false;
    protected boolean isValueBase = false;
    protected boolean isCORBAUserException = false;
    protected boolean isException = false;
    protected boolean isCheckedException = false;
    protected boolean isRemoteExceptionOrSubclass = false;
    protected String idlExceptionName;
    protected String qualifiedIDLExceptionName;

    //_____________________________________________________________________
    // Public Interfaces
    //_____________________________________________________________________

    /**
     * Return true if this type implements
     * org.omg.CORBA.Object.
     */
    public boolean isCORBAObject () {
        return isCORBAObject;
    }

    /**
     * Return true if this type implements
     * org.omg.CORBA.portable.IDLEntity.
     */
    public boolean isIDLEntity () {
        return isIDLEntity;
    }

    /**
     * Return true if this type implements
     * org.omg.CORBA.portable.ValueBase.
     */
    public boolean isValueBase () {
        return isValueBase;
    }

    /**
     * Return true if this type is a CORBA
     * abstract interface.
     */
    public boolean isAbstractBase () {
        return isAbstractBase;
    }

    /**
     * Return true if this type is an exception.
     */
    public boolean isException () {
        return isException;
    }

    /**
     * Return true if this type is a "checked" exception.
     * Result if valid iff isException() returns true.
     */
    public boolean isCheckedException () {
        return isCheckedException;
    }

    /**
     * Return true if this type is a java.rmi.RemoteException
     * or one of its subclasses. Result if valid iff isException()
     * returns true.
     */
    public boolean isRemoteExceptionOrSubclass () {
        return isRemoteExceptionOrSubclass;
    }

    /**
     * Return true if this type is exactly
     * org.omg.CORBA.UserException.
     */
    public boolean isCORBAUserException () {
        return isCORBAUserException;
    }

    /**
     * Return true if this type implements
     * isIDLEntity() && isException().
     */
    public boolean isIDLEntityException () {
        return isIDLEntity() && isException();
    }
    /**
     * Return true if isIDLEntity() && !isValueBase()
     * && !isAbstractBase() && !isCORBAObject()
     * && !isIDLEntityException().
     */
    public boolean isBoxed () {
        return (isIDLEntity() && !isValueBase() &&
                !isAbstractBase() && !isCORBAObject() &&
                !isIDLEntityException());
    }

    /**
     * If this type represents an exception, return the
     * IDL name including the "Ex" mangling, otherwise
     * return null.
     */
    public String getIDLExceptionName () {
        return idlExceptionName;
    }

    /**
     * If this type represents an exception, return the
     * qualified IDL name including the "Ex" mangling,
     * otherwise return null.
     * @param global If true, prepends "::".
     */
    public String getQualifiedIDLExceptionName (boolean global) {
        if (qualifiedIDLExceptionName != null &&
            global &&
            getIDLModuleNames().length > 0) {
            return IDL_NAME_SEPARATOR + qualifiedIDLExceptionName;
        } else {
            return qualifiedIDLExceptionName;
        }
    }

    /**
     * Return signature for this type  (e.g. com.acme.Dynamite
     * would return "com.acme.Dynamite", byte = "B")
     */
    public String getSignature() {
        String sig = classDecl.getType().getTypeSignature();
        if (sig.endsWith(";")) {
            sig = sig.substring(0,sig.length()-1);
        }
        return sig;
    }

    /**
     * Return the ClassDeclaration for this type.
     */
    public ClassDeclaration getClassDeclaration() {
        return classDecl;
    }

    /**
     * Return the ClassDefinition for this type.
     */
    public ClassDefinition getClassDefinition() {
        return classDef;
    }

    /**
     * Return the parent class of this type. Returns null if this
     * type is an interface or if there is no parent.
     */
    public ClassType getSuperclass() {
        return null;
    }

    /**
     * Return an array of interfaces directly implemented by this type.
     * <p>
     * The order of the array returned is arbitrary.
     */
    public InterfaceType[] getInterfaces() {
        if( interfaces != null ) {
            return (InterfaceType[]) interfaces.clone();
        }
        return null;
    }

    /**
     * Return an array of Type.Method objects representing all
     * of the methods implemented directly by this type.
     */
    public Method[] getMethods() {
        if( methods != null ) {
            return (Method[]) methods.clone();
        }
        return null;
    }

    /**
     * Return an array of Type.Member objects representing all of
     * the data members directly implemented by this interface.
     */
    public Member[] getMembers() {
        if( members != null ) {
            return (Member[]) members.clone();
        }
        return null;
    }

    /**
     * Create a CompoundType object for the given class.
     *
     * If the class is not a properly formed or if some other error occurs, the
     * return value will be null, and errors will have been reported to the
     * supplied BatchEnvironment.
     */
    public static CompoundType forCompound (ClassDefinition classDef,
                                            ContextStack stack) {
        CompoundType result = null;

        try {
            result = (CompoundType) makeType(classDef.getType(),classDef,stack);
        } catch (ClassCastException e) {}

        return result;
    }


    //_____________________________________________________________________
    // Subclass/Internal Interfaces
    //_____________________________________________________________________

    /**
     * Release all resources.
     */
    protected void destroy () {
        if (!destroyed) {
            super.destroy();

            if (methods != null) {
                for (int i = 0; i < methods.length; i++) {
                    if (methods[i] != null) methods[i].destroy();
                }
                methods = null;
            }

            if (interfaces != null) {
                for (int i = 0; i < interfaces.length; i++) {
                    if (interfaces[i] != null) interfaces[i].destroy();
                }
                interfaces = null;
            }

            if (members != null) {
                for (int i = 0; i < members.length; i++) {
                    if (members[i] != null) members[i].destroy();
                }
                members = null;
            }

            classDef = null;
            classDecl = null;
        }
    }

    /*
     * Load a Class instance. Return null if fail.
     */
    protected Class loadClass() {

        Class ourClass = null;

        // To avoid getting out-of-date Class instances, and
        // to ensure that there is an instance, we must compile
        // any classes that we've seen and which are not yet
        // compiled. We can't just compile this class, 'cuz it
        // may have dependencies on classes which have not been
        // compiled...

        try {
            env.getMain().compileAllClasses(env);
        } catch (Exception e1) {
            for (Enumeration e = env.getClasses() ; e.hasMoreElements() ; ) {
                ClassDeclaration c = (ClassDeclaration)e.nextElement();
            }
            failedConstraint(26,false,stack,"required classes");
            env.flushErrors();
        }

        // Now try to get the Class...
        // The outer try block is there for people who might want to use
        // the compiler at run-time of their AS.
        // They could set and use their own context class loader for loading
        // classes directly.
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            ourClass = cl.loadClass(getQualifiedName());
        } catch(ClassNotFoundException cfe) {

            try {
                ourClass = env.classPathLoader.loadClass(getQualifiedName());
            } catch (NullPointerException e) {
                // This should never happen
            } catch (ClassNotFoundException e) {
                // Fall through to the next case (which is to look in the
                // output directory for generated files)
            }
        }

        /* This piece of code used to cause the compiler to ignore jar files
           on its classpath
        try {
            ourClass = Util.loadClass(getQualifiedName(),null,null);
        } catch (ClassNotFoundException e) {
        } catch (LinkageError e) {
        }
        */

        if (ourClass == null) {

            // Try one last thing. If the class was compiled into
            // a directory that's not in the classpath, the load
            // will fail. Let's get the bits off the disk and load
            // it directly...

            if (env.loader == null) {
                File destDir = env.getMain().getDestinationDir();
                if (destDir == null) {
                    destDir = new File(".");
                }
                env.loader = new DirectoryLoader(destDir);
            }

            try {
                ourClass = env.loader.loadClass(getQualifiedName());
            } catch (Exception e) {}
        }

        return ourClass;
    }

    // Print "extends XX"

    protected boolean printExtends (IndentingWriter writer,
                                    boolean useQualifiedNames,
                                    boolean useIDLNames,
                                    boolean globalIDLNames) throws IOException {

        ClassType parent = getSuperclass();

        if (parent != null && (!useIDLNames ||
                               (!parent.isType(TYPE_ANY) && !parent.isType(TYPE_CORBA_OBJECT)))) {
            writer.p(" extends ");
            parent.printTypeName(writer,useQualifiedNames,useIDLNames,globalIDLNames);
            return true;
        }
        return false;
    }

    // Print "implements XX, YY"

    protected void printImplements (IndentingWriter writer,
                                    String prefix,
                                    boolean useQualifiedNames,
                                    boolean useIDLNames,
                                    boolean globalIDLNames) throws IOException {

        InterfaceType[] interfaces = getInterfaces();

        String adjective = " implements";

        if (isInterface()) {
            adjective = " extends";
        }

        if (useIDLNames) {
            adjective = ":";
        }

        for (int i = 0; i < interfaces.length; i++) {
            if (!useIDLNames || (!interfaces[i].isType(TYPE_ANY) && !interfaces[i].isType(TYPE_CORBA_OBJECT))) {
                if (i == 0) {
                    writer.p(prefix + adjective + " ");
                } else {
                    writer.p(", ");
                }
                interfaces[i].printTypeName(writer,useQualifiedNames,useIDLNames,globalIDLNames);
            }
        }
    }

    // Print members

    protected void printMembers (       IndentingWriter writer,
                                        boolean useQualifiedNames,
                                        boolean useIDLNames,
                                        boolean globalIDLNames) throws IOException {

        CompoundType.Member[] members = getMembers();

        for (int i = 0; i < members.length; i++) {
            if (!members[i].isInnerClassDeclaration()) {
                Type it = members[i].getType();
                String visibility = members[i].getVisibility();
                String name;

                if (useIDLNames) {
                    name = members[i].getIDLName();
                } else {
                    name = members[i].getName();
                }

                String value = members[i].getValue();

                writer.p(visibility);
                if (visibility.length() > 0) {
                    writer.p(" ");
                }
                it.printTypeName(writer,useQualifiedNames,useIDLNames,globalIDLNames);
                writer.p(" " + name);

                if (value != null) {
                    writer.pln(" = " + value + ";");
                } else {
                    writer.pln(";");
                }
            }
        }
    }

    // Print methods

    protected void printMethods (       IndentingWriter writer,
                                        boolean useQualifiedNames,
                                        boolean useIDLNames,
                                        boolean globalIDLNames) throws IOException {

        CompoundType.Method[] methods = getMethods();

        for (int m = 0; m < methods.length; m++) {
            CompoundType.Method theMethod = methods[m];
            printMethod(theMethod,writer,useQualifiedNames,useIDLNames,globalIDLNames);
        }
    }

    // Print a method...

    protected void printMethod (CompoundType.Method it,
                                IndentingWriter writer,
                                boolean useQualifiedNames,
                                boolean useIDLNames,
                                boolean globalIDLNames) throws IOException {


        // Write visibility...

        String visibility = it.getVisibility();

        writer.p(visibility);
        if (visibility.length() > 0) {
            writer.p(" ");
        }

        // Write return type...

        it.getReturnType().printTypeName(writer,useQualifiedNames,useIDLNames,globalIDLNames);

        // Write method name...

        if (useIDLNames) {
            writer.p(" " + it.getIDLName());
        } else {
            writer.p(" " + it.getName());
        }

        // Write arguments...

        writer.p(" (");
        Type[] args = it.getArguments();
        String[] argNames = it.getArgumentNames();

        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                writer.p(", ");
            }

            if (useIDLNames) {
                writer.p("in ");
            }

            args[i].printTypeName(writer,useQualifiedNames,useIDLNames,globalIDLNames);
            writer.p(" " + argNames[i]);
        }
        writer.p(")");

        // Write exceptions...

        ClassType[] exceptions;

        if (isType(TYPE_IMPLEMENTATION)) {
            exceptions = it.getImplExceptions();
        } else {
            exceptions = it.getExceptions();
        }

        for (int i = 0; i < exceptions.length; i++) {
            if (i == 0) {
                if (useIDLNames) {
                    writer.p(" raises (");
                } else {
                    writer.p(" throws ");
                }
            } else {
                writer.p(", ");
            }

            if (useIDLNames) {
                if (useQualifiedNames) {
                    writer.p(exceptions[i].getQualifiedIDLExceptionName(globalIDLNames));
                } else {
                    writer.p(exceptions[i].getIDLExceptionName());
                }
                writer.p(" [a.k.a. ");
                exceptions[i].printTypeName(writer,useQualifiedNames,useIDLNames,globalIDLNames);
                writer.p("]");
            } else {
                exceptions[i].printTypeName(writer,useQualifiedNames,useIDLNames,globalIDLNames);
            }
        }

        if (useIDLNames && exceptions.length > 0) {
            writer.p(")");
        }

        if (it.isInherited()) {
            writer.p(" // Inherited from ");
        writer.p(it.getDeclaredBy());
        }

        writer.pln(";");
    }

    /**
     * Create a CompoundType instance for the given class. NOTE: This constructor
     * is ONLY for SpecialClassType and SpecialInterfaceType.
     */
    protected CompoundType(ContextStack stack, int typeCode, ClassDefinition classDef) {
        super(stack,typeCode);
        this.classDef = classDef;
        classDecl = classDef.getClassDeclaration();
        interfaces = new InterfaceType[0];
        methods = new Method[0];
        members = new Member[0];

        // If we are an inner class/interface, reset the type codes...

        if (classDef.isInnerClass()) {
            setTypeCode(typeCode | TM_INNER);
        }

        // Set special flags...

        setFlags();
    }

    private void setFlags() {

        try {

        // Set our special interface flags...

            isCORBAObject = env.defCorbaObject.implementedBy(env,classDecl);
            isIDLEntity = env.defIDLEntity.implementedBy(env,classDecl);
            isValueBase = env.defValueBase.implementedBy(env,classDecl);
            isAbstractBase = isInterface() &&   // Interface, not a class.
                             isIDLEntity &&     // Implements IDLEntity.
                             !isValueBase &&    // Does not implement ValueBase.
                             !isCORBAObject;    // Does not implement org.omg.CORBA.Object;
            isCORBAUserException = (classDecl.getName() == idCorbaUserException);

            // Is this an exception?

            if (env.defThrowable.implementedBy(env, classDecl)) {

                // Yes...

                isException = true;

                // Is it a checked exception?

                if (env.defRuntimeException.implementedBy(env,classDecl) ||
                    env.defError.implementedBy(env,classDecl)) {
                    isCheckedException = false;
                } else {
                    isCheckedException = true;
                }

                // Is it java.rmi.RemoteException or a subclass?

                if (env.defRemoteException.implementedBy(env,classDecl)) {
                    isRemoteExceptionOrSubclass = true;
                } else {
                    isRemoteExceptionOrSubclass = false;
                }
            } else {
                isException = false;
            }
        } catch (ClassNotFound e) {
            classNotFound(stack,e);
        }
    }

    /**
     * Create a CompoundType instance for the given class.  The resulting
     * object is not yet completely initialized.
     */
    protected CompoundType(ContextStack stack, ClassDefinition classDef,
                           int typeCode) {
        super(stack,typeCode);
        this.classDef = classDef;
        classDecl = classDef.getClassDeclaration();

        // If we are an inner class/interface, reset the type codes...

        if (classDef.isInnerClass()) {
            setTypeCode(typeCode | TM_INNER);
        }

        // Set special flags...

        setFlags();

        // Set names...

        Identifier id = classDef.getName();
        String idlName;
        String[] idlModuleNames;

        try {

            // These can fail if we get case-sensitive name matches...

            idlName = IDLNames.getClassOrInterfaceName(id,env);
            idlModuleNames = IDLNames.getModuleNames(id,isBoxed(),env);

            setNames(id,idlModuleNames,idlName);

            // Is this an exception?

            if (isException()) {

                // Yes, so set our mangled exception names...

                isException = true;
                idlExceptionName = IDLNames.getExceptionName(getIDLName());
                qualifiedIDLExceptionName =
                    IDLNames.getQualifiedName(getIDLModuleNames(),idlExceptionName);
            }

            // Set interfaces, methods and members...

            interfaces = null;          // set in initialize()
            methods = null;                     // set in initialize()
            members = null;                 // set in initialize()

        } catch (Exception e) {
            failedConstraint(7,false,stack,id.toString(),e.getMessage());
            throw new CompilerError("");
        }
    }

    /**
     * Initialize this instance.
     */
    protected boolean initialize (      Vector directInterfaces,
                                        Vector directMethods,
                                        Vector directMembers,
                                        ContextStack stack,
                                        boolean quiet) {

        boolean result = true;

        // Initialize our arrays...

        if (directInterfaces != null && directInterfaces.size() > 0) {
            interfaces = new InterfaceType[directInterfaces.size()];
            directInterfaces.copyInto(interfaces);
        } else {
            interfaces = new InterfaceType[0];
        }

        if (directMethods != null && directMethods.size() > 0) {
            methods = new Method[directMethods.size()];
            directMethods.copyInto(methods);

            // Now set the idl names for each...

            try {
                IDLNames.setMethodNames(this, methods,env);
            } catch (Exception e) {
                failedConstraint(13,quiet,stack,getQualifiedName(),e.getMessage());
                result = false;
            }

        } else {
            methods = new Method[0];
        }

        if (directMembers != null && directMembers.size() > 0) {
            members = new Member[directMembers.size()];
            directMembers.copyInto(members);

            // If we have any un-initialized inner classes, now is the time
            // to init them...

            for (int i = 0; i < members.length; i++) {
                if (members[i].isInnerClassDeclaration()) {
                    try {
                        members[i].init(stack,this);
                    } catch (CompilerError e) {
                        return false;
                    }
                }
            }

            // Now set the idl names for each...

            try {
                IDLNames.setMemberNames(this, members,methods,env);
            } catch (Exception e) {
                int constraint = classDef.isInterface() ? 19 : 20;
                failedConstraint(constraint,quiet,stack,getQualifiedName(),e.getMessage());
                result = false;
            }

        } else {
            members = new Member[0];
        }

        // Set our repositoryID...

        if (result) {
            result = setRepositoryID();
        }

        return result;
    }

    /*
     * Return Type or null if error. classDef may be null.
     */
    protected static Type makeType (sun.tools.java.Type theType,
                                    ClassDefinition classDef,
                                    ContextStack stack) {

        if (stack.anyErrors()) return null;

        // See if we can find this type in the cache.  If so, return it...

        String key = theType.toString();

        Type result = getType(key,stack);

        if (result != null) {
            return result;
        }

        // Gotta try with context...

        result = getType(key + stack.getContextCodeString(),stack);

        if (result != null) {
            return result;
        }

        // Gotta map it...

        BatchEnvironment env = stack.getEnv();
        int typeCode = theType.getTypeCode();
        switch (typeCode) {
        case TC_BOOLEAN:
        case TC_BYTE:
        case TC_CHAR:
        case TC_SHORT:
        case TC_INT:
        case TC_LONG:
        case TC_FLOAT:
        case TC_DOUBLE:
            {
                // Primitive...

                result = PrimitiveType.forPrimitive(theType,stack);
                break;
            }

        case TC_ARRAY:
            {
                // Array.

                result = ArrayType.forArray(theType,stack);
                break;
            }

        case TC_CLASS:
            {
                try {
                                // First, make sure we have the class definition...

                    ClassDefinition theClass = classDef;

                    if (theClass == null) {
                        theClass = env.getClassDeclaration(theType).getClassDefinition(env);
                    }

                                // Is it an interface or a class?

                    if (theClass.isInterface()) {

                        // An interface. Is it a special case?

                        result = SpecialInterfaceType.forSpecial(theClass,stack);

                        if (result == null) {

                            // No, does it implement java.rmi.Remote?

                            if (env.defRemote.implementedBy(env,theClass.getClassDeclaration())) {

                                // Yep, so just see if we can create an instance of RemoteType
                                // from it...

                                boolean parentIsValue = stack.isParentAValue();
                                result = RemoteType.forRemote(theClass,stack,parentIsValue);

                                // If we did not succeed AND we are in a value context, then
                                // go ahead and make an NC type out of it...

                                if (result == null && parentIsValue) {
                                    result = NCInterfaceType.forNCInterface(theClass,stack);
                                }
                            } else {

                                // Nope, is it an AbstractType?

                                result = AbstractType.forAbstract(theClass,stack,true);

                                if (result == null) {

                                    // No, so treat it as a non-conforming interface type...

                                    result = NCInterfaceType.forNCInterface(theClass,stack);
                                }
                            }
                        }
                    } else {

                        // A class. Is it a special case?

                        result = SpecialClassType.forSpecial(theClass,stack);

                        if (result == null) {

                            ClassDeclaration classDecl = theClass.getClassDeclaration();

                            // Nope, does it implement java.rmi.Remote?

                            if (env.defRemote.implementedBy(env,classDecl)) {

                                // Yep, so just see if we can create an instance of
                                // ImplementationType from it...

                                boolean parentIsValue = stack.isParentAValue();
                                result = ImplementationType.forImplementation(theClass,stack,parentIsValue);

                                // If we did not succeed AND inValue is true, then
                                // go ahead and make an NC type out of it...

                                if (result == null && parentIsValue) {
                                    result = NCClassType.forNCClass(theClass,stack);
                                }
                            } else {

                                // No, does it implement Serializable?

                                if (env.defSerializable.implementedBy(env,classDecl)) {

                                    // Yep, so just see if we can create an instance of ValueType
                                    // from it...

                                    result = ValueType.forValue(theClass,stack,true);
                                }

                                if (result == null) {

                                    // Treat it as a non-conforming class type...

                                    result = NCClassType.forNCClass(theClass,stack);
                                }
                            }
                        }
                    }
                } catch (ClassNotFound e) {
                    classNotFound(stack,e);
                }
                break;
            }

        default: throw new CompilerError("Unknown typecode (" + typeCode + ") for " + theType.getTypeSignature());
        }

        return result;
    }

    /*
     * Check if exception is RemoteException or one of its parents.
     */
    public static boolean isRemoteException (ClassType ex,
                                             BatchEnvironment env) {
        sun.tools.java.Type exceptionType = ex.getClassDeclaration().getType();

        if (exceptionType.equals(env.typeRemoteException) ||
            exceptionType.equals(env.typeIOException) ||
            exceptionType.equals(env.typeException) ||
            exceptionType.equals(env.typeThrowable)) {

            return true;
        }
        return false;
    }

    /*
     * Check if method is conforming.
     */
    protected boolean isConformingRemoteMethod (Method method, boolean quiet)
        throws ClassNotFound {

        // Do we have one exception that is RemoteException or
        // a superclass of RemoteException?

        boolean haveRemote = false;
        ClassType[] exceptions = method.getExceptions();

        for (int i = 0; i < exceptions.length; i++) {

            // Is it a conforming exception?

            if (isRemoteException(exceptions[i],env)) {

                // Got it.

                haveRemote = true;
                break;
            }
        }

        // Do we have our exception?

        if (!haveRemote) {

            // No, so report failure...

            failedConstraint(5,quiet,stack,method.getEnclosing(), method.toString());
        }

        // Are any of the arguments exceptions which implement IDLEntity?
        // If so, report failure...

        boolean noIDLEntity = !isIDLEntityException(method.getReturnType(),method,quiet);
        if (noIDLEntity) {
            Type[] args = method.getArguments();
            for (int i = 0; i < args.length; i++) {
                if (isIDLEntityException(args[i],method,quiet)) {
                    noIDLEntity = false;
                    break;
                }
            }
        }

        return (haveRemote && noIDLEntity);
    }

    protected boolean isIDLEntityException(Type type, CompoundType.Method method,boolean quiet)
        throws ClassNotFound {
        if (type.isArray()) {
            type = type.getElementType();
        }
        if (type.isCompound()){
            if (((CompoundType)type).isIDLEntityException()) {
                failedConstraint(18,quiet,stack,method.getEnclosing(), method.toString());
                return true;
            }
        }
        return false;
    }

    /**
     * Convert all invalid types to valid ones.
     */
    protected void swapInvalidTypes () {

        // Walk all interfaces and check them...

        for (int i = 0; i < interfaces.length; i++) {
            if (interfaces[i].getStatus() != STATUS_VALID) {
                interfaces[i] = (InterfaceType)getValidType(interfaces[i]);
            }
        }

        // Update methods...

        for (int i = 0; i < methods.length; i++) {
            methods[i].swapInvalidTypes();
        }

        // Update members...

        for (int i = 0; i < members.length; i++) {
            members[i].swapInvalidTypes();
        }
    }

    /*
     * Add matching types to list. Return true if this type has not
     * been previously checked, false otherwise.
     */
    protected boolean addTypes (int typeCodeFilter,
                                HashSet checked,
                                Vector matching) {

        // Check self.

        boolean result = super.addTypes(typeCodeFilter,checked,matching);

        // Have we been checked before?

        if (result) {

            // Nope, so walk parent(s) and check them...

            ClassType parent = getSuperclass();

            if (parent != null) {
                parent.addTypes(typeCodeFilter,checked,matching);
            }

            // Walk all interfaces and check them...

            //if (interfaces == null) System.out.println("NULL for " +getQualifiedName() + " interfaces");
            for (int i = 0; i < interfaces.length; i++) {

                // Now recurse and add it and any referenced types...

                //if (interfaces[i] == null) System.out.println("NULL for " +getQualifiedName() + " interfaces[" + i + "]");
                interfaces[i].addTypes(typeCodeFilter,checked,matching);
            }

            // Walk all methods and check arguments...

            //if (methods == null) System.out.println("NULL for " +getQualifiedName() + " methods");
            for (int i = 0; i < methods.length; i++) {

                // Add return type...
                //if (methods[i] == null) System.out.println("NULL for " +getQualifiedName() + " methods[" + i + "]");
                //if (methods[i].getReturnType() == null) System.out.println("NULL for " +getQualifiedName() + methods[i]);
                methods[i].getReturnType().addTypes(typeCodeFilter,checked,matching);

                // Add args...

                Type[] args = methods[i].getArguments();
                //if (args == null) System.out.println("NULL for " + getQualifiedName() + " args");

                for (int j = 0; j < args.length; j++) {

                    Type arg = args[j];
                    //if (arg == null) System.out.println("NULL for " + getQualifiedName() + " arg[" +j+"]");

                                // Add argument...

                    arg.addTypes(typeCodeFilter,checked,matching);
                }

                // Add exceptions...

                ClassType[] exceptions = methods[i].getExceptions();
                //if (exceptions == null) System.out.println("NULL for " + getQualifiedName() + " exceptions");

                for (int j = 0; j < exceptions.length; j++) {

                    ClassType ex = exceptions[j];

                                // Add argument...

                    ex.addTypes(typeCodeFilter,checked,matching);
                }
            }

            // Walk all members and add em...

            //if (members == null) System.out.println("NULL for " +getQualifiedName() + " members");
            for (int i = 0; i < members.length; i++) {
                //if (members[i] == null) System.out.println("NULL for " +getQualifiedName() + " members[" + i + "]");
                Type cType = members[i].getType();
                //if (cType == null) System.out.println("NULL for " + getQualifiedName() + " cType");

                // Add it...

                cType.addTypes(typeCodeFilter,checked,matching);
            }
        }

        return result;
    }

    /*
     * Return true if theType is a conforming constant type.
     */
    private boolean isConformingConstantType (MemberDefinition member) {
        return isConformingConstantType(member.getType(),member);
    }

    /*
     * Return true if theType is a conforming constant type.
     */
    private boolean isConformingConstantType (sun.tools.java.Type theType,MemberDefinition member) {

        // Constraint 3:    Constants must be either primitives or String.

        boolean result = true;
        int typeCode = theType.getTypeCode();
        switch (typeCode) {
        case TC_BOOLEAN:
        case TC_BYTE:
        case TC_CHAR:
        case TC_SHORT:
        case TC_INT:
        case TC_LONG:
        case TC_FLOAT:
        case TC_DOUBLE: // Primitive, so OK...
            {
                break;
            }

        case TC_CLASS:  // Must be java.lang.String
            {
                if (theType.getClassName() != idJavaLangString) {
                    failedConstraint(3,false,stack,member.getClassDefinition(),member.getName());
                    result = false;
                }
                break;
            }

        case TC_ARRAY: // Array constants are not allowed.
            {
                failedConstraint(3,false,stack,member.getClassDefinition(),member.getName());
                result = false;
                break;
            }

        default:
            throw new Error("unexpected type code: " + typeCode);
        }

        return result;
    }


    /*
     * Update any method from 'currentMethods' which is defined in a
     * parent class so that it's 'declaredBy' field specifies the
     * parent.
     * @param current The class or interface to gather methods from.
     * @param currentMethods The list into which to put the methods.
     *  for contraint 6.
     * @param quiet true if silent errors.
     * @param stack the context stack.
     * @return currentMethods or null if failed a constraint check.
     */
    protected Vector updateParentClassMethods(ClassDefinition current,
                                              Vector currentMethods,
                                              boolean quiet,
                                              ContextStack stack)
        throws ClassNotFound {

        ClassDeclaration parentDecl = current.getSuperClass(env);

        while (parentDecl != null) {

            ClassDefinition parentDef = parentDecl.getClassDefinition(env);
            Identifier currentID = parentDecl.getName();

            if ( currentID == idJavaLangObject ) break;

            // Walk all members of this class and update any that
            // already exist in currentMethods...

            for (MemberDefinition member = parentDef.getFirstMember();
                 member != null;
                 member = member.getNextMember()) {

                if (member.isMethod() &&
                    !member.isInitializer() &&
                    !member.isConstructor() &&
                    !member.isPrivate()) {

                    // It's a method.  Is it valid?

                    Method method;
                    try {
                        method = new Method((CompoundType)this,member,quiet,stack);
                    } catch (Exception e) {
                        // Don't report anything here, it's already been reported...
                        return null;
                    }

                    // Have we already seen it?

                    int index = currentMethods.indexOf(method);
                    if (index >= 0) {

                        // Yes, so update it...

                        Method currentMethod = (Method)currentMethods.elementAt(index);
                        currentMethod.setDeclaredBy(currentID);
                    }
                    else currentMethods.addElement(method);
                }
            }

            // Update parent and keep walking up the chain...

            parentDecl = parentDef.getSuperClass(env);
        }

        return currentMethods;
    }

    /*
     * Add all of the public and protected methods defined in
     * current (other than initializers) to allMethods. If a sub-interface
     * re-declares an inherited method, it will not be added.
     * @param current The class or interface to gather methods from.
     * @param directMethods The list into which to put the methods.
     * @param noMultiInheritedMethods A flag to enable/disable checking
     *  for contraint 6.
     * @param quiet true if silent errors.
     * @param stack the context stack.
     * @return directMethods or null if failed a constraint check.
     */
    protected Vector addAllMethods (ClassDefinition current, Vector directMethods,
                                    boolean noMultiInheritedMethods,
                                    boolean quiet,
                                    ContextStack stack)
        throws ClassNotFound {

        // Constraint 6:    Multiple inherited interfaces may not
        //                  declare the same method.

        ClassDeclaration[] interfaces = current.getInterfaces();

        // We want to add members starting at the _least_ derived
        // interfaces.  To do so, recurse until we have no more
        // interfaces...

        for (int i = 0; i < interfaces.length; i++) {

            Vector result = addAllMethods(interfaces[i].getClassDefinition(env),
                                          directMethods,
                                          noMultiInheritedMethods,quiet,stack);
            if (result == null) {
                return null;
            }
        }

        // Walk all members of this interface, adding any unique methods
        // other than initializers and private methods...

        for (MemberDefinition member = current.getFirstMember();
             member != null;
             member = member.getNextMember())
            {
                if (member.isMethod() &&
                    !member.isInitializer() &&
                    !member.isPrivate()) {

                    // It's a method.  Is it valid?

                    Method method;
                    try {
                        method = new Method((CompoundType)this,member,quiet,stack);
                    } catch (Exception e) {
                        // Don't report anything here, it's already been reported...
                        return null;
                    }

                                // Have we already seen it?

                    if (!directMethods.contains(method)) {

                        // Nope, so add it...

                        directMethods.addElement(method);

                    } else {

                        // Yes. This is an error unless we are looking at the
                        // target interface (or this is a ValueType). Are we?

                        if (noMultiInheritedMethods && current != classDef  &&
                            !stack.isParentAValue() && !stack.getContext().isValue()) {

                            // Nope. Say so and signal error by returning null..

                            Method existingMethod = (Method) directMethods.elementAt(directMethods.indexOf(method));
                            ClassDefinition existingMemberClassDef = existingMethod.getMemberDefinition().getClassDefinition();

                            // There are more legal cases to consider here.
                            // If the two methods belong to interfaces that inherit from each other
                            // then it is just a redefinition which is legal.
                            if ( current != existingMemberClassDef &&
                                 ! inheritsFrom(current, existingMemberClassDef) &&
                                 ! inheritsFrom(existingMemberClassDef, current))
                            {
                                //Identifier int1 = existingMethod.getEnclosing().getIdentifier();
                                //Identifier int2 = current.getName();
                                //String message = int1.toString() + " and " + int2.toString();
                                String message = existingMemberClassDef.getName() + " and " + current.getName();
                                failedConstraint(6,quiet,stack,classDef,message,method);
                                return null;
                            }
                        }

                        // Bug fix 5014329

                        // find a matching method.
                        int index = directMethods.indexOf(method);
                        Method other = (Method) directMethods.get(index);

                        // merge the two methods, such that the new method
                        // will contain only those exception that can be thrown
                        // by both these methods, not just one of them.
                        Method newMethod = method.mergeWith(other);

                        // replace the old method with the new.
                        directMethods.set(index, newMethod);
                    }
                }
            }

        return directMethods;
    }

    // This should really be a method on ClassDefinition, but it takes too long to change the shared source.
    // Works for both, classes and interfaces.
    protected boolean inheritsFrom(ClassDefinition def, ClassDefinition otherDef) {
        if (def == otherDef)
            return true;

        ClassDefinition superDef;
        if (def.getSuperClass() != null) {
            superDef = def.getSuperClass().getClassDefinition();
            if (inheritsFrom(superDef, otherDef))
                return true;
        }

        ClassDeclaration[] interfaces = def.getInterfaces();
        for (int i=0; i<interfaces.length; i++) {
            superDef = interfaces[i].getClassDefinition();
            if (inheritsFrom(superDef, otherDef))
                return true;
        }
        return false;
    }

    /*
     * Add all of the interfaces implemented directly by current
     * to the list. Returns null if any are non-conforming.
     */
    protected Vector addRemoteInterfaces (Vector list,
                                          boolean allowNonConforming,
                                          ContextStack stack) throws ClassNotFound {

        // Add all the interfaces of current...

        ClassDefinition theInterface = getClassDefinition();
        ClassDeclaration[] interfaces = theInterface.getInterfaces();

        stack.setNewContextCode(ContextStack.IMPLEMENTS);

        for (int i = 0; i < interfaces.length; i++) {

            ClassDefinition def = interfaces[i].getClassDefinition(env);

            // Is it a SpecialInterfaceType...

            InterfaceType it = SpecialInterfaceType.forSpecial(def,stack);;

            if (it == null) {

                // No, is it Remote?

                if (env.defRemote.implementedBy(env, interfaces[i])) {

                    // Yes, so it must be a RemoteType.

                    it = RemoteType.forRemote(def,stack,false);

                } else {

                    // Then try Abstract...

                    it = AbstractType.forAbstract(def,stack,true);

                    if (it == null && allowNonConforming) {

                        // Must be non-conforming...

                        it = NCInterfaceType.forNCInterface(def,stack);
                    }
                }
            }

            if (it != null) {
                list.addElement(it);
            } else {
                return null;
            }
        }

        return list;
    }

    /*
     * Add all of the interfaces implemented directly by current
     * to the list.
     */
    protected Vector addNonRemoteInterfaces (Vector list,
                                             ContextStack stack) throws ClassNotFound {

        // Add all the interfaces of current...

        ClassDefinition theInterface = getClassDefinition();
        ClassDeclaration[] interfaces = theInterface.getInterfaces();

        stack.setNewContextCode(ContextStack.IMPLEMENTS);

        for (int i = 0; i < interfaces.length; i++) {

            ClassDefinition def = interfaces[i].getClassDefinition(env);

            // First try SpecialInterfaceType...

            InterfaceType it = SpecialInterfaceType.forSpecial(def,stack);

            if (it == null) {

                // Then try AbstractType...

                it = AbstractType.forAbstract(def,stack,true);

                if (it == null) {

                    // Then try NCInterfaceType...

                    it = NCInterfaceType.forNCInterface(def,stack);
                }
            }

            if (it != null) {
                list.addElement(it);
            } else {
                return null;
            }
        }

        return list;
    }


    /*
     * Walk self, adding constants and data members.
     * @return true if all conform, false otherwise.
     */
    protected boolean addAllMembers (Vector allMembers,
                                     boolean onlyConformingConstants,   // AND inner classes.
                                     boolean quiet,
                                     ContextStack stack) {

        boolean result = true;

        // Walk all members of this interface...

        for (MemberDefinition member = getClassDefinition().getFirstMember();
             member != null && result;
             member = member.getNextMember())
            {
                if (!member.isMethod()) {

                    try {
                        String value = null;

                        // Prod it to setValue if it is a constant...

                        member.getValue(env);

                                // Get the value, if any...

                        Node node = member.getValue();

                        if (node != null) {
                            // We don't want to change the code in CharExpression,
                            // which is shared among tools, to return the right string
                            // in case the type is char, so we treat it special here.
                            if (member.getType().getTypeCode() == TC_CHAR) {
                                Integer intValue = (Integer)((IntegerExpression)node).getValue();
                                value = "L'" + String.valueOf((char)intValue.intValue()) + "'";
                            } else {
                                value = node.toString();
                            }
                        }

                        // Are we supposed to allow only conforming constants?

                        if (onlyConformingConstants && member.getInnerClass() == null) {

                                // Yep, so check it...

                            if (value == null || !isConformingConstantType(member)) {
                                failedConstraint(3,quiet,stack,member.getClassDefinition(),member.getName());
                                result = false;
                                break;
                            }
                        }

                        // Make member and add to list...

                        try {
                            Member newMember = new Member(member,value,stack,this);
                            allMembers.addElement(newMember);
                        } catch (CompilerError e) {
                            result = false;
                        }

                    } catch (ClassNotFound e) {
                        classNotFound(stack,e);
                        result = false;
                    }
                }
            }

        return result;
    }
    /*
     * Walk self, adding constants.
     * @return true if all conform, false otherwise.
     */
    protected boolean addConformingConstants (Vector allMembers,
                                              boolean quiet,
                                              ContextStack stack) {

        boolean result = true;

        // Walk all members of this interface...

        for (MemberDefinition member = getClassDefinition().getFirstMember();
             member != null && result;
             member = member.getNextMember())
            {
                if (!member.isMethod()) {

                    try {
                        String value = null;

                        // Prod it to setValue if it is a constant...

                        member.getValue(env);

                                // Get the value, if any...

                        Node node = member.getValue();

                        if (node != null) {
                            value = node.toString();
                        }


                        // Is it a constant?

                        if (value != null) {

                            // Yes, is it conforming?

                            if (!isConformingConstantType(member)) {
                                failedConstraint(3,quiet,stack,member.getClassDefinition(),member.getName());
                                result = false;
                                break;
                            }

                            // Yes, so make a member and add to list...

                            try {
                                Member newMember = new Member(member,value,stack,this);
                                allMembers.addElement(newMember);
                            } catch (CompilerError e) {
                                result = false;
                            }
                        }
                    } catch (ClassNotFound e) {
                        classNotFound(stack,e);
                        result = false;
                    }
                }
            }

        return result;
    }

    protected ValueType[] getMethodExceptions (MemberDefinition member,
                                               boolean quiet,
                                               ContextStack stack) throws Exception {

        boolean result = true;
        stack.setNewContextCode(ContextStack.METHOD_EXCEPTION);
        ClassDeclaration[] except = member.getExceptions(env);
        ValueType[] exceptions = new ValueType[except.length];

        try {
            for (int i = 0; i < except.length; i++) {
                ClassDefinition theClass = except[i].getClassDefinition(env);
                try {
                    ValueType type = ValueType.forValue(theClass,stack,false);
                    if (type != null) {
                            exceptions[i] = type;
                        } else {
                            result = false;
                        }
                } catch (ClassCastException e1) {
                    failedConstraint(22,quiet,stack,getQualifiedName());
                    throw new CompilerError("Method: exception " + theClass.getName() + " not a class type!");
                } catch (NullPointerException e2) {
                    failedConstraint(23,quiet,stack,getQualifiedName());
                    throw new CompilerError("Method: caught null pointer exception");
                }
            }
        } catch (ClassNotFound e) {
            classNotFound(quiet,stack,e);
            result = false;
        }

        if (!result) {
            throw new Exception();
        }

        // Remove any duplicates (javac seems to allow them, but rmic will
        // generate bad ties)...

        int dupCount = 0;
        for (int i = 0; i < exceptions.length; i++) {
            for (int j = 0; j < exceptions.length; j++) {
                if (i != j && exceptions[i] != null && exceptions[i] == exceptions[j]) {
                    exceptions[j] = null;
                    dupCount++;
                }
            }
        }
        if (dupCount > 0) {
            int offset = 0;
            ValueType[] temp = new ValueType[exceptions.length - dupCount];
            for (int i = 0; i < exceptions.length; i++) {
                if (exceptions[i] != null) {
                    temp[offset++] = exceptions[i];
                }
            }
            exceptions = temp;
        }

        return exceptions;
    }


    protected static String getVisibilityString (MemberDefinition member) {
        String vis = "";
        String prefix = "";

        if (member.isPublic()) {
            vis += "public";
            prefix = " ";
        } else if (member.isProtected()) {
            vis += "protected";
            prefix = " ";
        } else if (member.isPrivate()) {
            vis += "private";
            prefix = " ";
        }

        if (member.isStatic()) {
            vis += prefix;
            vis += "static";
            prefix = " ";
        }

        if (member.isFinal()) {
            vis += prefix;
            vis += "final";
            prefix = " ";
        }

        return vis;
    }

    protected boolean assertNotImpl(Type type,
                                    boolean quiet,
                                    ContextStack stack,
                                    CompoundType enclosing,
                                    boolean dataMember) {

        if (type.isType(TYPE_IMPLEMENTATION)) {
            int constraint = dataMember ? 28 : 21;
            failedConstraint(constraint,quiet,stack,type,enclosing.getName());
            return false;
        }
        return true;
    }

    //_____________________________________________________________________
    // Inner Class "Method"
    //_____________________________________________________________________

    /**
     * A CompoundType.Method object encapsulates IIOP-specific information
     * about a particular method in the interface represented by the outer
     * instance.
     */
    public class Method implements ContextElement, Cloneable {

        /**
         * Is this method inherited?
         */
        public boolean isInherited () {
            return declaredBy != enclosing.getIdentifier();
        }

        /**
         * Is this method an attribute?
         * Return true if getAttributeKind != ATTRIBUTE_NONE.
         */
        public boolean isAttribute () {
            return attributeKind != ATTRIBUTE_NONE;
        }

        /**
         * Is this method a read-write attribute?
         */
        public boolean isReadWriteAttribute () {
            return attributeKind == ATTRIBUTE_IS_RW ||
                attributeKind == ATTRIBUTE_GET_RW;
        }

        /**
         * Return the attribute kind.
         */
        public int getAttributeKind() {
            return attributeKind;
        }

        /**
         * Return the attribute name. Will be null if
         * attribute kind == ATTRIBUTE_NONE.
         */
        public String getAttributeName() {
            return attributeName;
        }

        /**
         * For kinds ATTRIBUTE_GET_RW or ATTRIBUTE_IS_RW, return
         * the index of the matching ATTRIBUTE_SET method, and
         * vice-versa. For all other cases, return -1.
         */
        public int getAttributePairIndex() {
            return attributePairIndex;
        }

        /**
         * Return context element name.
         */
        public String getElementName() {
            return memberDef.toString();
        }

        /**
         * Equality check based on method signature.
         */
        public boolean equals(Object obj) {
            Method other = (Method) obj;

            if (getName().equals(other.getName()) &&
                arguments.length == other.arguments.length) {

                for (int i = 0; i < arguments.length; i++) {
                    if (! arguments[i].equals(other.arguments[i])) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        /**
         * Return a new Method object that is a legal combination of
         * this method object and another one.
         *
         * This requires determining the exceptions declared by the
         * combined method, which must be only those exceptions
         * that may thrown by both of the old methods.
         */
        public Method mergeWith(Method other) {
            if (!equals(other)) {
                env.error(0, "attempt to merge method failed:", getName(),
                          enclosing.getClassDefinition().getName());
            }

            Vector legalExceptions = new Vector();
            try {
                collectCompatibleExceptions(
                      other.exceptions, exceptions, legalExceptions);
                collectCompatibleExceptions(
                      exceptions, other.exceptions, legalExceptions);
            } catch (ClassNotFound e) {
                env.error(0, "class.not.found", e.name,
                          enclosing.getClassDefinition().getName());
                return null;
            }

            Method merged = (Method) clone();
            merged.exceptions = new ValueType[legalExceptions.size()];
            legalExceptions.copyInto(merged.exceptions);
            merged.implExceptions = merged.exceptions;

            return merged;
        }

        /**
         * Add to the supplied list all exceptions in the "from" array
         * that are subclasses of an exception in the "with" array.
         */
        private void collectCompatibleExceptions(
                ValueType[] from, ValueType[] with, Vector list)
                throws ClassNotFound {

            for (int i = 0; i < from.length; i++) {
                ClassDefinition exceptionDef = from[i].getClassDefinition();
                if (!list.contains(from[i])) {
                    for (int j = 0; j < with.length; j++) {
                        if (exceptionDef.subClassOf(
                                enclosing.getEnv(),
                                with[j].getClassDeclaration())) {
                            list.addElement(from[i]);
                            break;
                        }
                    }
                }
            }
        }

        /**
         * Return the compound type which contains this method.
         */
        public CompoundType getEnclosing() {
            return enclosing;
        }

        /**
         * Return the identifier for the class or interface which
         * declares this method.
         */
        public Identifier getDeclaredBy() {
            return declaredBy;
        }

        /**
         * Return the visibility (e.g. "public final") of this member.
         */
        public String getVisibility() {
            return vis;
        }

        /**
         * Methods to check various attributes.
         */
        public boolean isPublic() {
            return memberDef.isPublic();
        }

        public boolean isProtected() {
            return memberDef.isPrivate();
        }

        public boolean isPrivate() {
            return memberDef.isPrivate();
        }

        public boolean isStatic() {
            return memberDef.isStatic();
        }

        /**
         * Return the name of this method.
         */
        public String getName() {
            return name;
        }

        /**
         * IDL_Naming
         * Return the IDL name of this method.
         */
        public String getIDLName() {
            return idlName;
        }

        /**
         * Return the type of this method.
         */
        public sun.tools.java.Type getType() {
            return memberDef.getType();
        }

        /**
         * Return true if this is a constructor.
         */
        public boolean isConstructor () {
            return memberDef.isConstructor();
        }

        /**
         * Return true if this is NOT a constructor && is not
         * an attribute.
         */
        public boolean isNormalMethod () {
            return (!memberDef.isConstructor()) && attributeKind == ATTRIBUTE_NONE;
        }

        /**
         * Get the return type of this method. May be null.
         */
        public Type getReturnType() {
            return returnType;
        }

        /**
         * Return the argument types of this method.
         */
        public Type[] getArguments() {
            return (Type[]) arguments.clone();
        }

        /**
         * Return the names of the argument types of this method.
         */
        public String[] getArgumentNames() {
            return argumentNames;
        }

        /**
         * Return the MemberDefinition from which this method was created.
         */
        public MemberDefinition getMemberDefinition() {
            return memberDef;
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
        public ValueType[] getExceptions() {
            return (ValueType[]) exceptions.clone();
        }

        /**
         * Same as getExceptions(), except when method is in an
         * ImplementationType and the exceptions list is narrower.
         */
        public ValueType[] getImplExceptions() {
            return (ValueType[]) implExceptions.clone();
        }

        /**
         * Return an array containing only those exceptions which
         * need to be caught.  Removes java.rmi.RemoteException,
         * java.lang.RuntimeException, java.lang.Error, and their
         * subclasses, then removes any exceptions which are more
         * derived than another in the list. Returns null if no
         * exceptions need to be caught.
         */
        public ValueType[] getUniqueCatchList(ValueType[] list) {
            ValueType[] result = list;
            int newSize = list.length;

            try {

                // First, remove RemoteException, RuntimeException, Error, and their subclasses...
                for (int i = 0; i < list.length; i++) {
                    ClassDeclaration decl = list[i].getClassDeclaration();
                    if (env.defRemoteException.superClassOf(env, decl) ||
                        env.defRuntimeException.superClassOf(env, decl) ||
                        env.defError.superClassOf(env, decl)) {
                        list[i] = null;
                        newSize--;
                    }
                }

                // Now remove derived types...
                for (int i = 0; i < list.length; i++) {
                    if (list[i] != null) {
                        ClassDefinition current = list[i].getClassDefinition();
                        for (int j = 0; j < list.length; j++) {
                            if (j != i && list[i] != null && list[j] != null &&
                                current.superClassOf(env, list[j].getClassDeclaration())) {
                                list[j] = null;
                                newSize--;
                            }
                        }
                    }
                }

            } catch (ClassNotFound e) {
                classNotFound(stack,e); // Report error but do not stop.
            }

            // Create new list if we removed anything...

            if (newSize < list.length) {
                ValueType[] temp = new ValueType[newSize];
                int offset = 0;
                for (int i = 0; i < list.length; i++) {
                    if (list[i] != null) {
                        temp[offset++] = list[i];
                    }
                }
                list = temp;
            }

            if (list.length == 0) {
                return null;
            } else {
                return list;
            }
        }

        /**
         * Return an array containing only those exceptions which need to be
         * handled explicitly by the stub.  Removes java.lang.RuntimeException,
         * java.lang.Error, and their subclasses, since these are all passed
         * back as CORBA system exceptions.  Also removes subclasses of
         * java.rmi.RemoteException but not java.rmi.RemoteException itself,
         * since this may need to be thrown by the stub.
         */
        public ValueType[] getFilteredStubExceptions(ValueType[] list) {
            ValueType[] result = list;
            int newSize = list.length;

            try {

                for (int i = 0; i < list.length; i++) {
                    ClassDeclaration decl = list[i].getClassDeclaration();
                    if ((env.defRemoteException.superClassOf(env, decl) &&
                         !env.defRemoteException.getClassDeclaration().equals(decl)) ||
                        env.defRuntimeException.superClassOf(env, decl) ||
                        env.defError.superClassOf(env, decl)) {
                        list[i] = null;
                        newSize--;
                    }
                }

            } catch (ClassNotFound e) {
                classNotFound(stack,e); // Report error but do not stop.
            }

            // Create new list if we removed anything...

            if (newSize < list.length) {
                ValueType[] temp = new ValueType[newSize];
                int offset = 0;
                for (int i = 0; i < list.length; i++) {
                    if (list[i] != null) {
                        temp[offset++] = list[i];
                    }
                }
                list = temp;
            }

            return list;
        }

        /**
         * Return the string representation of this method.
         */
        public String toString() {

            if (stringRep == null) {

                StringBuffer result = new StringBuffer(returnType.toString());

                // Add name...

                result.append(" ");
                result.append(getName());
                result.append(" (");

                // Add arguments...

                for (int i = 0; i < arguments.length; i++) {
                    if (i > 0) {
                        result.append(", ");
                    }
                    result.append(arguments[i]);
                    result.append(" ");
                    result.append(argumentNames[i]);
                }

                result.append(")");

                // Add exceptions...

                for (int i = 0; i < exceptions.length; i++) {
                    if (i == 0) {
                        result.append(" throws ");
                    } else {
                        result.append(", ");
                    }
                    result.append(exceptions[i]);
                }

                result.append(";");

                stringRep = result.toString();
            }

            return stringRep;
        }


        /**
         * Set attribute kind. May only be called during initialization.
         */
        public void setAttributeKind(int kind) {
            attributeKind = kind;
        }

        /**
         * Set pair index. May only be called during initialization.
         */
        public void setAttributePairIndex(int index) {
            attributePairIndex = index;
        }

        /**
         * Set attribute name. May only be called during initialization.
         */
        public void setAttributeName(String name) {
            attributeName = name;
        }

        /**
         * Set the idl name. May only be called during initialization.
         */
        public void setIDLName (String idlName) {
            this.idlName=idlName;
        }

        /**
         * Set the implExceptions array. May only be called during initialization.
         */
        public void setImplExceptions (ValueType[] exceptions) {
            implExceptions = exceptions;
        }

        /**
         * Set the declaredBy Identifier. May only be called during initialization.
         */
        public void setDeclaredBy (Identifier by) {
            declaredBy = by;
        }

        /**
         * Convert all invalid types to valid ones.
         */
        protected void swapInvalidTypes () {

            // Check return type...

            if (returnType.getStatus() != STATUS_VALID) {
                returnType = getValidType(returnType);
            }

            // Check args...

            for (int i = 0; i < arguments.length; i++) {
                if (arguments[i].getStatus() != STATUS_VALID) {
                    arguments[i] = getValidType(arguments[i]);
                }
            }

            // Check exceptions...

            for (int i = 0; i < exceptions.length; i++) {
                if (exceptions[i].getStatus() != STATUS_VALID) {
                    exceptions[i] = (ValueType)getValidType(exceptions[i]);
                }
            }

            // Check implExceptions...

            for (int i = 0; i < implExceptions.length; i++) {
                if (implExceptions[i].getStatus() != STATUS_VALID) {
                    implExceptions[i] = (ValueType)getValidType(implExceptions[i]);
                }
            }
        }

        /**
         * Release all resources.
         */
        public void destroy () {
            if (memberDef != null) {
                memberDef = null;
                enclosing = null;
                if (exceptions != null) {
                    for (int i = 0; i < exceptions.length; i++) {
                        if (exceptions[i] != null) exceptions[i].destroy();
                        exceptions[i] = null;
                    }
                    exceptions = null;
                }

                if (implExceptions != null) {
                    for (int i = 0; i < implExceptions.length; i++) {
                        if (implExceptions[i] != null) implExceptions[i].destroy();
                        implExceptions[i] = null;
                    }
                    implExceptions = null;
                }

                if (returnType != null) returnType.destroy();
                returnType = null;

                if (arguments != null) {
                    for (int i = 0; i < arguments.length; i++) {
                        if (arguments[i] != null) arguments[i].destroy();
                        arguments[i] = null;
                    }
                    arguments = null;
                }

                if (argumentNames != null) {
                    for (int i = 0; i < argumentNames.length; i++) {
                        argumentNames[i] = null;
                    }
                    argumentNames = null;
                }

                vis = null;
                name = null;
                idlName = null;
                stringRep = null;
                attributeName = null;
                declaredBy = null;
            }
        }

        private MemberDefinition memberDef;
        private CompoundType enclosing;
        private ValueType[] exceptions;
        private ValueType[] implExceptions;
        private Type returnType;
        private Type[] arguments;
        private String[] argumentNames;
        private String vis;
        private String name;
        private String idlName;
        private String stringRep = null;
        private int attributeKind = ATTRIBUTE_NONE;
        private String attributeName = null;
        private int attributePairIndex = -1;
        private Identifier declaredBy = null;

        /**
         * Make up an argument name for the given type.
         */
        private String makeArgName (int argNum, Type type) {
            return "arg" + argNum;
        }

        /**
         * Create a new Method object corresponding to the given
         * method definition.
         */
        public Method (CompoundType enclosing,
                       MemberDefinition memberDef,
                       boolean quiet,
                       ContextStack stack) throws Exception {

            this.enclosing = enclosing;
            this.memberDef = memberDef;
            vis = getVisibilityString(memberDef);
            idlName = null; // See setIDLName()
            boolean valid = true;
            declaredBy = memberDef.getClassDeclaration().getName();

            // Set name...

            name = memberDef.getName().toString();

            // Update the context...

            stack.setNewContextCode(ContextStack.METHOD);
            stack.push(this);

            // Set return type...

            stack.setNewContextCode(ContextStack.METHOD_RETURN);
            sun.tools.java.Type methodType = memberDef.getType();
            sun.tools.java.Type rtnType = methodType.getReturnType();

            if (rtnType == sun.tools.java.Type.tVoid) {
                returnType = PrimitiveType.forPrimitive(rtnType,stack);
            } else {
                returnType = makeType(rtnType,null,stack);
                if (returnType == null ||
                    !assertNotImpl(returnType,quiet,stack,enclosing,false)) {
                    valid = false;
                    failedConstraint(24,quiet,stack,enclosing.getName());
                }
            }

            // Set arguments and argument names...

            stack.setNewContextCode(ContextStack.METHOD_ARGUMENT);
            sun.tools.java.Type[] args = memberDef.getType().getArgumentTypes();
            arguments = new Type[args.length];
            argumentNames = new String[args.length];
            Vector origArgNames = memberDef.getArguments();

            for (int i = 0; i < args.length; i++) {
                Type type = null;
                try {
                    type = makeType(args[i],null,stack);
                } catch (Exception e) {
                }

                if (type != null) {
                    if (!assertNotImpl(type,quiet,stack,enclosing,false)) {
                        valid = false;
                    } else {
                    arguments[i] = type;
                    if (origArgNames != null) {
                        LocalMember local = (LocalMember)origArgNames.elementAt(i+1);
                        argumentNames[i] = local.getName().toString();
                    } else {
                        argumentNames[i] = makeArgName(i,type);
                    }
                    }
                } else {
                    valid = false;
                    failedConstraint(25,false,stack,enclosing.getQualifiedName(),name);
                }
            }

            if (!valid) {
                stack.pop(false);
                throw new Exception();
            }

            // Set exceptions...

            try {
                exceptions = enclosing.getMethodExceptions(memberDef,quiet,stack);
                implExceptions = exceptions;
                stack.pop(true);
            } catch (Exception e) {
                stack.pop(false);
                throw new Exception();
            }
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
    }

    //_____________________________________________________________________
    // Inner Class "Member"
    //_____________________________________________________________________

    /**
     * An CompoundType.Member object wraps a Type and a value representing
     * a data member, including constants.
     */
    public class Member implements ContextElement, Cloneable {

        /**
         * Return context element name.
         */
        public String getElementName() {
            return "\"" + getName() + "\"";
        }

        /**
         * Return the type of this member.
         */
        public Type getType() {
            return type;
        }

        /**
         * Return the name of this member.
         */
        public String getName() {
            return name;
        }

        /**
         * IDL_Naming
         * Return the IDL name of this member.
         */
        public String getIDLName() {
            return idlName;
        }

        /**
         * Return the visibility (e.g. "public final") of this member.
         */
        public String getVisibility() {
            return vis;
        }

        /**
         * Methods to check various attributes.
         */
        public boolean isPublic() {
            return member.isPublic();
        }

        public boolean isPrivate() {
            return member.isPrivate();
        }

        public boolean isStatic() {
            return member.isStatic();
        }

        public boolean isFinal() {
            return member.isFinal();
        }

        public boolean isTransient() {
            if (forceTransient) return true;
            return member.isTransient();
        }

        /**
         * Return the value of this member. May be null.
         */
        public String getValue() {
            return value;
        }

        /**
         * Return true if this member represents an inner class declaration,
         * false otherwise.
         */
        public boolean isInnerClassDeclaration() {
            return innerClassDecl;
        }

        /**
         * Return true if this member represents a constant.
         */
        public boolean isConstant () {
            return constant;
        }

        /**
         * Return the string representation of this constant.
         */
        public String toString() {

            String result = type.toString();

            if (value != null) {
                result += (" = " + value);
            }

            return result;
        }

        /**
         * Convert all invalid types to valid ones.
         */
        protected void swapInvalidTypes () {
            if (type.getStatus() != STATUS_VALID) {
                type = getValidType(type);
            }
        }

        protected void setTransient() {
            if (! isTransient()) {
                forceTransient = true;
                if (vis.length() > 0) {
                    vis = vis + " transient";
                } else {
                    vis = "transient";
                }
            }
        }

        protected MemberDefinition getMemberDefinition() {
            return member;
        }

        /**
         * Release all resources.
         */
        public void destroy () {
            if (type != null) {
                type.destroy();
                type = null;
                vis = null;
                value = null;
                name = null;
                idlName = null;
                member = null;
            }
        }

        private Type type;
        private String vis;
        private String value;
        private String name;
        private String idlName;
        private boolean innerClassDecl;
        private boolean constant;
        private MemberDefinition member;
        private boolean forceTransient;

        /**
         * Create a new Member object.
         */
        public Member(MemberDefinition member,
                      String value,
                      ContextStack stack,
                      CompoundType enclosing) {
            this.member = member;
            this.value = value;
            forceTransient = false;
            innerClassDecl = member.getInnerClass() != null;

            // If we are not an inner class, finish initializing now.
            // Otherwise, wait until outer class is finished, then
            // call init to avoid potential recursion problems...

            if (!innerClassDecl) {
                init (stack,enclosing);
            }
        }

        public void init (ContextStack stack, CompoundType enclosing) {

            constant = false;
            name = member.getName().toString();
            vis = getVisibilityString(member);
            idlName = null;

            // Add self to stack...

            int contextCode = ContextStack.MEMBER;
            stack.setNewContextCode(contextCode);

            // Check for special contextCodes...

            if (member.isVariable()) {
                if (value != null && member.isConstant()) {
                    contextCode = ContextStack.MEMBER_CONSTANT;
                    this.constant = true;
                } else if (member.isStatic()) {
                    contextCode = ContextStack.MEMBER_STATIC;
                } else if (member.isTransient()) {
                    contextCode = ContextStack.MEMBER_TRANSIENT;
                }
            }

            stack.setNewContextCode(contextCode);
            stack.push(this);

            type = makeType(member.getType(),null,stack);

            if (type == null ||
                (!innerClassDecl &&
                 !member.isStatic() &&
                 !member.isTransient() &&
                 !assertNotImpl(type,false,stack,enclosing,true))) {
                stack.pop(false);
                throw new CompilerError("");
            }

            // Clean up primitive constant values...

            if (constant && type.isPrimitive()) {
                if (type.isType(TYPE_LONG) || type.isType(TYPE_FLOAT) || type.isType(TYPE_DOUBLE)) {
                    int length = value.length();
                    char lastChar = value.charAt(length-1);
                    if (!Character.isDigit(lastChar)) {
                        this.value = value.substring(0,length-1);
                    }
                } else if (type.isType(TYPE_BOOLEAN)) {
                    value = value.toUpperCase();
                }
            }
            if (constant && type.isType(TYPE_STRING)) {
                value = "L" + value;
            }
            stack.pop(true);
        }

        public void setIDLName (String name) {
            this.idlName = name;
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
    }
}
