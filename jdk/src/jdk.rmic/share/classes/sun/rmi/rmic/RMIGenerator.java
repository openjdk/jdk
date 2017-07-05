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

/*****************************************************************************/
/*                    Copyright (c) IBM Corporation 1998                     */
/*                                                                           */
/* (C) Copyright IBM Corp. 1998                                              */
/*                                                                           */
/*****************************************************************************/

package sun.rmi.rmic;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import sun.tools.java.Type;
import sun.tools.java.Identifier;
import sun.tools.java.ClassDefinition;
import sun.tools.java.ClassDeclaration;
import sun.tools.java.ClassNotFound;
import sun.tools.java.ClassFile;
import sun.tools.java.MemberDefinition;
import com.sun.corba.se.impl.util.Utility;

/**
 * A Generator object will generate the Java source code of the stub
 * and skeleton classes for an RMI remote implementation class, using
 * a particular stub protocol version.
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 *
 * @author      Peter Jones,  Bryan Atsatt
 */
public class RMIGenerator implements RMIConstants, Generator {

    private static final Hashtable<String, Integer> versionOptions = new Hashtable<>();
    static {
        versionOptions.put("-v1.1", STUB_VERSION_1_1);
        versionOptions.put("-vcompat", STUB_VERSION_FAT);
        versionOptions.put("-v1.2", STUB_VERSION_1_2);
    }

    /**
     * Default constructor for Main to use.
     */
    public RMIGenerator() {
        version = STUB_VERSION_1_2;     // default is -v1.2 (see 4638155)
    }

    /**
     * Examine and consume command line arguments.
     * @param argv The command line arguments. Ignore null
     * and unknown arguments. Set each consumed argument to null.
     * @param error Report any errors using the main.error() methods.
     * @return true if no errors, false otherwise.
     */
    public boolean parseArgs(String argv[], Main main) {
        String explicitVersion = null;
        for (int i = 0; i < argv.length; i++) {
            if (argv[i] != null) {
                String arg = argv[i].toLowerCase();
                if (versionOptions.containsKey(arg)) {
                    if (explicitVersion != null &&
                        !explicitVersion.equals(arg))
                    {
                        main.error("rmic.cannot.use.both",
                                   explicitVersion, arg);
                        return false;
                    }
                    explicitVersion = arg;
                    version = versionOptions.get(arg);
                    argv[i] = null;
                }
            }
        }
        return true;
    }

    /**
     * Generate the source files for the stub and/or skeleton classes
     * needed by RMI for the given remote implementation class.
     *
     * @param env       compiler environment
     * @param cdef      definition of remote implementation class
     *                  to generate stubs and/or skeletons for
     * @param destDir   directory for the root of the package hierarchy
     *                  for generated files
     */
    public void generate(BatchEnvironment env, ClassDefinition cdef, File destDir) {
        RemoteClass remoteClass = RemoteClass.forClass(env, cdef);
        if (remoteClass == null)        // exit if an error occurred
            return;

        RMIGenerator gen;
        try {
            gen = new RMIGenerator(env, cdef, destDir, remoteClass, version);
        } catch (ClassNotFound e) {
            env.error(0, "rmic.class.not.found", e.name);
            return;
        }
        gen.generate();
    }

    private void generate() {
        env.addGeneratedFile(stubFile);

        try {
            IndentingWriter out = new IndentingWriter(
                new OutputStreamWriter(new FileOutputStream(stubFile)));
            writeStub(out);
            out.close();
            if (env.verbose()) {
                env.output(Main.getText("rmic.wrote", stubFile.getPath()));
            }
            env.parseFile(new ClassFile(stubFile));
        } catch (IOException e) {
            env.error(0, "cant.write", stubFile.toString());
            return;
        }

        if (version == STUB_VERSION_1_1 ||
            version == STUB_VERSION_FAT)
        {
            env.addGeneratedFile(skeletonFile);

            try {
                IndentingWriter out = new IndentingWriter(
                    new OutputStreamWriter(
                        new FileOutputStream(skeletonFile)));
                writeSkeleton(out);
                out.close();
                if (env.verbose()) {
                    env.output(Main.getText("rmic.wrote",
                        skeletonFile.getPath()));
                }
                env.parseFile(new ClassFile(skeletonFile));
            } catch (IOException e) {
                env.error(0, "cant.write", stubFile.toString());
                return;
            }
        } else {
            /*
             * For bugid 4135136: if skeleton files are not being generated
             * for this compilation run, delete old skeleton source or class
             * files for this remote implementation class that were
             * (presumably) left over from previous runs, to avoid user
             * confusion from extraneous or inconsistent generated files.
             */

            File outputDir = Util.getOutputDirectoryFor(remoteClassName,destDir,env);
            File skeletonClassFile = new File(outputDir,skeletonClassName.getName().toString() + ".class");

            skeletonFile.delete();      // ignore failures (no big deal)
            skeletonClassFile.delete();
        }
    }

    /**
     * Return the File object that should be used as the source file
     * for the given Java class, using the supplied destination
     * directory for the top of the package hierarchy.
     */
    protected static File sourceFileForClass(Identifier className,
                                             Identifier outputClassName,
                                             File destDir,
                                             BatchEnvironment env)
    {
        File packageDir = Util.getOutputDirectoryFor(className,destDir,env);
        String outputName = Names.mangleClass(outputClassName).getName().toString();

        // Is there any existing _Tie equivalent leftover from a
        // previous invocation of rmic -iiop? Only do this once per
        // class by looking for skeleton generation...

        if (outputName.endsWith("_Skel")) {
            String classNameStr = className.getName().toString();
            File temp = new File(packageDir, Utility.tieName(classNameStr) + ".class");
            if (temp.exists()) {

                // Found a tie. Is IIOP generation also being done?

                if (!env.getMain().iiopGeneration) {

                    // No, so write a warning...

                    env.error(0,"warn.rmic.tie.found",
                              classNameStr,
                              temp.getAbsolutePath());
                }
            }
        }

        String outputFileName = outputName + ".java";
        return new File(packageDir, outputFileName);
    }


    /** rmic environment for this object */
    private BatchEnvironment env;

    /** the remote class that this instance is generating code for */
    private RemoteClass remoteClass;

    /** version of the stub protocol to use in code generation */
    private int version;

    /** remote methods for remote class, indexed by operation number */
    private RemoteClass.Method[] remoteMethods;

    /**
     * Names for the remote class and the stub and skeleton classes
     * to be generated for it.
     */
    private Identifier remoteClassName;
    private Identifier stubClassName;
    private Identifier skeletonClassName;

    private ClassDefinition cdef;
    private File destDir;
    private File stubFile;
    private File skeletonFile;

    /**
     * Names to use for the java.lang.reflect.Method static fields
     * corresponding to each remote method.
     */
    private String[] methodFieldNames;

    /** cached definition for certain exception classes in this environment */
    private ClassDefinition defException;
    private ClassDefinition defRemoteException;
    private ClassDefinition defRuntimeException;

    /**
     * Create a new stub/skeleton Generator object for the given
     * remote implementation class to generate code according to
     * the given stub protocol version.
     */
    private RMIGenerator(BatchEnvironment env, ClassDefinition cdef,
                           File destDir, RemoteClass remoteClass, int version)
        throws ClassNotFound
    {
        this.destDir     = destDir;
        this.cdef        = cdef;
        this.env         = env;
        this.remoteClass = remoteClass;
        this.version     = version;

        remoteMethods = remoteClass.getRemoteMethods();

        remoteClassName = remoteClass.getName();
        stubClassName = Names.stubFor(remoteClassName);
        skeletonClassName = Names.skeletonFor(remoteClassName);

        methodFieldNames = nameMethodFields(remoteMethods);

        stubFile = sourceFileForClass(remoteClassName,stubClassName, destDir , env);
        skeletonFile = sourceFileForClass(remoteClassName,skeletonClassName, destDir, env);

        /*
         * Initialize cached definitions for exception classes used
         * in the generation process.
         */
        defException =
            env.getClassDeclaration(idJavaLangException).
                getClassDefinition(env);
        defRemoteException =
            env.getClassDeclaration(idRemoteException).
                getClassDefinition(env);
        defRuntimeException =
            env.getClassDeclaration(idJavaLangRuntimeException).
                getClassDefinition(env);
    }

    /**
     * Write the stub for the remote class to a stream.
     */
    private void writeStub(IndentingWriter p) throws IOException {

        /*
         * Write boiler plate comment.
         */
        p.pln("// Stub class generated by rmic, do not edit.");
        p.pln("// Contents subject to change without notice.");
        p.pln();

        /*
         * If remote implementation class was in a particular package,
         * declare the stub class to be in the same package.
         */
        if (remoteClassName.isQualified()) {
            p.pln("package " + remoteClassName.getQualifier() + ";");
            p.pln();
        }

        /*
         * Declare the stub class; implement all remote interfaces.
         */
        p.plnI("public final class " +
            Names.mangleClass(stubClassName.getName()));
        p.pln("extends " + idRemoteStub);
        ClassDefinition[] remoteInterfaces = remoteClass.getRemoteInterfaces();
        if (remoteInterfaces.length > 0) {
            p.p("implements ");
            for (int i = 0; i < remoteInterfaces.length; i++) {
                if (i > 0)
                    p.p(", ");
                p.p(remoteInterfaces[i].getName().toString());
            }
            p.pln();
        }
        p.pOlnI("{");

        if (version == STUB_VERSION_1_1 ||
            version == STUB_VERSION_FAT)
        {
            writeOperationsArray(p);
            p.pln();
            writeInterfaceHash(p);
            p.pln();
        }

        if (version == STUB_VERSION_FAT ||
            version == STUB_VERSION_1_2)
        {
            p.pln("private static final long serialVersionUID = " +
                STUB_SERIAL_VERSION_UID + ";");
            p.pln();

            /*
             * We only need to declare and initialize the static fields of
             * Method objects for each remote method if there are any remote
             * methods; otherwise, skip this code entirely, to avoid generating
             * a try/catch block for a checked exception that cannot occur
             * (see bugid 4125181).
             */
            if (methodFieldNames.length > 0) {
                if (version == STUB_VERSION_FAT) {
                    p.pln("private static boolean useNewInvoke;");
                }
                writeMethodFieldDeclarations(p);
                p.pln();

                /*
                 * Initialize java.lang.reflect.Method fields for each remote
                 * method in a static initializer.
                 */
                p.plnI("static {");
                p.plnI("try {");
                if (version == STUB_VERSION_FAT) {
                    /*
                     * Fat stubs must determine whether the API required for
                     * the JDK 1.2 stub protocol is supported in the current
                     * runtime, so that it can use it if supported.  This is
                     * determined by using the Reflection API to test if the
                     * new invoke method on RemoteRef exists, and setting the
                     * static boolean "useNewInvoke" to true if it does, or
                     * to false if a NoSuchMethodException is thrown.
                     */
                    p.plnI(idRemoteRef + ".class.getMethod(\"invoke\",");
                    p.plnI("new java.lang.Class[] {");
                    p.pln(idRemote + ".class,");
                    p.pln("java.lang.reflect.Method.class,");
                    p.pln("java.lang.Object[].class,");
                    p.pln("long.class");
                    p.pOln("});");
                    p.pO();
                    p.pln("useNewInvoke = true;");
                }
                writeMethodFieldInitializers(p);
                p.pOlnI("} catch (java.lang.NoSuchMethodException e) {");
                if (version == STUB_VERSION_FAT) {
                    p.pln("useNewInvoke = false;");
                } else {
                    /*
                     * REMIND: By throwing an Error here, the application will
                     * get the NoSuchMethodError directly when the stub class
                     * is initialized.  If we throw a RuntimeException
                     * instead, the application would get an
                     * ExceptionInInitializerError.  Would that be more
                     * appropriate, and if so, which RuntimeException should
                     * be thrown?
                     */
                    p.plnI("throw new java.lang.NoSuchMethodError(");
                    p.pln("\"stub class initialization failed\");");
                    p.pO();
                }
                p.pOln("}");            // end try/catch block
                p.pOln("}");            // end static initializer
                p.pln();
            }
        }

        writeStubConstructors(p);
        p.pln();

        /*
         * Write each stub method.
         */
        if (remoteMethods.length > 0) {
            p.pln("// methods from remote interfaces");
            for (int i = 0; i < remoteMethods.length; ++i) {
                p.pln();
                writeStubMethod(p, i);
            }
        }

        p.pOln("}");                    // end stub class
    }

    /**
     * Write the constructors for the stub class.
     */
    private void writeStubConstructors(IndentingWriter p)
        throws IOException
    {
        p.pln("// constructors");

        /*
         * Only stubs compatible with the JDK 1.1 stub protocol need
         * a no-arg constructor; later versions use reflection to find
         * the constructor that directly takes a RemoteRef argument.
         */
        if (version == STUB_VERSION_1_1 ||
            version == STUB_VERSION_FAT)
        {
            p.plnI("public " + Names.mangleClass(stubClassName.getName()) +
                "() {");
            p.pln("super();");
            p.pOln("}");
        }

        p.plnI("public " + Names.mangleClass(stubClassName.getName()) +
            "(" + idRemoteRef + " ref) {");
        p.pln("super(ref);");
        p.pOln("}");
    }

    /**
     * Write the stub method for the remote method with the given "opnum".
     */
    private void writeStubMethod(IndentingWriter p, int opnum)
        throws IOException
    {
        RemoteClass.Method method = remoteMethods[opnum];
        Identifier methodName = method.getName();
        Type methodType = method.getType();
        Type paramTypes[] = methodType.getArgumentTypes();
        String paramNames[] = nameParameters(paramTypes);
        Type returnType = methodType.getReturnType();
        ClassDeclaration[] exceptions = method.getExceptions();

        /*
         * Declare stub method; throw exceptions declared in remote
         * interface(s).
         */
        p.pln("// implementation of " +
            methodType.typeString(methodName.toString(), true, false));
        p.p("public " + returnType + " " + methodName + "(");
        for (int i = 0; i < paramTypes.length; i++) {
            if (i > 0)
                p.p(", ");
            p.p(paramTypes[i] + " " + paramNames[i]);
        }
        p.plnI(")");
        if (exceptions.length > 0) {
            p.p("throws ");
            for (int i = 0; i < exceptions.length; i++) {
                if (i > 0)
                    p.p(", ");
                p.p(exceptions[i].getName().toString());
            }
            p.pln();
        }
        p.pOlnI("{");

        /*
         * The RemoteRef.invoke methods throw Exception, but unless this
         * stub method throws Exception as well, we must catch Exceptions
         * thrown from the invocation.  So we must catch Exception and
         * rethrow something we can throw: UnexpectedException, which is a
         * subclass of RemoteException.  But for any subclasses of Exception
         * that we can throw, like RemoteException, RuntimeException, and
         * any of the exceptions declared by this stub method, we want them
         * to pass through unharmed, so first we must catch any such
         * exceptions and rethrow it directly.
         *
         * We have to be careful generating the rethrowing catch blocks
         * here, because javac will flag an error if there are any
         * unreachable catch blocks, i.e. if the catch of an exception class
         * follows a previous catch of it or of one of its superclasses.
         * The following method invocation takes care of these details.
         */
        Vector<ClassDefinition> catchList = computeUniqueCatchList(exceptions);

        /*
         * If we need to catch any particular exceptions (i.e. this method
         * does not declare java.lang.Exception), put the entire stub
         * method in a try block.
         */
        if (catchList.size() > 0) {
            p.plnI("try {");
        }

        if (version == STUB_VERSION_FAT) {
            p.plnI("if (useNewInvoke) {");
        }
        if (version == STUB_VERSION_FAT ||
            version == STUB_VERSION_1_2)
        {
            if (!returnType.isType(TC_VOID)) {
                p.p("Object $result = ");               // REMIND: why $?
            }
            p.p("ref.invoke(this, " + methodFieldNames[opnum] + ", ");
            if (paramTypes.length > 0) {
                p.p("new java.lang.Object[] {");
                for (int i = 0; i < paramTypes.length; i++) {
                    if (i > 0)
                        p.p(", ");
                    p.p(wrapArgumentCode(paramTypes[i], paramNames[i]));
                }
                p.p("}");
            } else {
                p.p("null");
            }
            p.pln(", " + method.getMethodHash() + "L);");
            if (!returnType.isType(TC_VOID)) {
                p.pln("return " +
                    unwrapArgumentCode(returnType, "$result") + ";");
            }
        }
        if (version == STUB_VERSION_FAT) {
            p.pOlnI("} else {");
        }
        if (version == STUB_VERSION_1_1 ||
            version == STUB_VERSION_FAT)
        {
            p.pln(idRemoteCall + " call = ref.newCall((" + idRemoteObject +
                ") this, operations, " + opnum + ", interfaceHash);");

            if (paramTypes.length > 0) {
                p.plnI("try {");
                p.pln("java.io.ObjectOutput out = call.getOutputStream();");
                writeMarshalArguments(p, "out", paramTypes, paramNames);
                p.pOlnI("} catch (java.io.IOException e) {");
                p.pln("throw new " + idMarshalException +
                    "(\"error marshalling arguments\", e);");
                p.pOln("}");
            }

            p.pln("ref.invoke(call);");

            if (returnType.isType(TC_VOID)) {
                p.pln("ref.done(call);");
            } else {
                p.pln(returnType + " $result;");        // REMIND: why $?
                p.plnI("try {");
                p.pln("java.io.ObjectInput in = call.getInputStream();");
                boolean objectRead =
                    writeUnmarshalArgument(p, "in", returnType, "$result");
                p.pln(";");
                p.pOlnI("} catch (java.io.IOException e) {");
                p.pln("throw new " + idUnmarshalException +
                    "(\"error unmarshalling return\", e);");
                /*
                 * If any only if readObject has been invoked, we must catch
                 * ClassNotFoundException as well as IOException.
                 */
                if (objectRead) {
                    p.pOlnI("} catch (java.lang.ClassNotFoundException e) {");
                    p.pln("throw new " + idUnmarshalException +
                        "(\"error unmarshalling return\", e);");
                }
                p.pOlnI("} finally {");
                p.pln("ref.done(call);");
                p.pOln("}");
                p.pln("return $result;");
            }
        }
        if (version == STUB_VERSION_FAT) {
            p.pOln("}");                // end if/else (useNewInvoke) block
        }

        /*
         * If we need to catch any particular exceptions, finally write
         * the catch blocks for them, rethrow any other Exceptions with an
         * UnexpectedException, and end the try block.
         */
        if (catchList.size() > 0) {
            for (Enumeration<ClassDefinition> enumeration = catchList.elements();
                 enumeration.hasMoreElements();)
            {
                ClassDefinition def = enumeration.nextElement();
                p.pOlnI("} catch (" + def.getName() + " e) {");
                p.pln("throw e;");
            }
            p.pOlnI("} catch (java.lang.Exception e) {");
            p.pln("throw new " + idUnexpectedException +
                "(\"undeclared checked exception\", e);");
            p.pOln("}");                // end try/catch block
        }

        p.pOln("}");                    // end stub method
    }

    /**
     * Compute the exceptions which need to be caught and rethrown in a
     * stub method before wrapping Exceptions in UnexpectedExceptions,
     * given the exceptions declared in the throws clause of the method.
     * Returns a Vector containing ClassDefinition objects for each
     * exception to catch.  Each exception is guaranteed to be unique,
     * i.e. not a subclass of any of the other exceptions in the Vector,
     * so the catch blocks for these exceptions may be generated in any
     * order relative to each other.
     *
     * RemoteException and RuntimeException are each automatically placed
     * in the returned Vector (if none of their superclasses are already
     * present), since those exceptions should always be directly rethrown
     * by a stub method.
     *
     * The returned Vector will be empty if java.lang.Exception or one
     * of its superclasses is in the throws clause of the method, indicating
     * that no exceptions need to be caught.
     */
    private Vector<ClassDefinition> computeUniqueCatchList(ClassDeclaration[] exceptions) {
        Vector<ClassDefinition> uniqueList = new Vector<>();       // unique exceptions to catch

        uniqueList.addElement(defRuntimeException);
        uniqueList.addElement(defRemoteException);

        /* For each exception declared by the stub method's throws clause: */
    nextException:
        for (int i = 0; i < exceptions.length; i++) {
            ClassDeclaration decl = exceptions[i];
            try {
                if (defException.subClassOf(env, decl)) {
                    /*
                     * (If java.lang.Exception (or a superclass) was declared
                     * in the throws clause of this stub method, then we don't
                     * have to bother catching anything; clear the list and
                     * return.)
                     */
                    uniqueList.clear();
                    break;
                } else if (!defException.superClassOf(env, decl)) {
                    /*
                     * Ignore other Throwables that do not extend Exception,
                     * since they do not need to be caught anyway.
                     */
                    continue;
                }
                /*
                 * Compare this exception against the current list of
                 * exceptions that need to be caught:
                 */
                for (int j = 0; j < uniqueList.size();) {
                    ClassDefinition def = uniqueList.elementAt(j);
                    if (def.superClassOf(env, decl)) {
                        /*
                         * If a superclass of this exception is already on
                         * the list to catch, then ignore and continue;
                         */
                        continue nextException;
                    } else if (def.subClassOf(env, decl)) {
                        /*
                         * If a subclass of this exception is on the list
                         * to catch, then remove it.
                         */
                        uniqueList.removeElementAt(j);
                    } else {
                        j++;    // else continue comparing
                    }
                }
                /* This exception is unique: add it to the list to catch. */
                uniqueList.addElement(decl.getClassDefinition(env));
            } catch (ClassNotFound e) {
                env.error(0, "class.not.found", e.name, decl.getName());
                /*
                 * REMIND: We do not exit from this exceptional condition,
                 * generating questionable code and likely letting the
                 * compiler report a resulting error later.
                 */
            }
        }
        return uniqueList;
    }

    /**
     * Write the skeleton for the remote class to a stream.
     */
    private void writeSkeleton(IndentingWriter p) throws IOException {
        if (version == STUB_VERSION_1_2) {
            throw new Error("should not generate skeleton for version");
        }

        /*
         * Write boiler plate comment.
         */
        p.pln("// Skeleton class generated by rmic, do not edit.");
        p.pln("// Contents subject to change without notice.");
        p.pln();

        /*
         * If remote implementation class was in a particular package,
         * declare the skeleton class to be in the same package.
         */
        if (remoteClassName.isQualified()) {
            p.pln("package " + remoteClassName.getQualifier() + ";");
            p.pln();
        }

        /*
         * Declare the skeleton class.
         */
        p.plnI("public final class " +
            Names.mangleClass(skeletonClassName.getName()));
        p.pln("implements " + idSkeleton);
        p.pOlnI("{");

        writeOperationsArray(p);
        p.pln();

        writeInterfaceHash(p);
        p.pln();

        /*
         * Define the getOperations() method.
         */
        p.plnI("public " + idOperation + "[] getOperations() {");
        p.pln("return (" + idOperation + "[]) operations.clone();");
        p.pOln("}");
        p.pln();

        /*
         * Define the dispatch() method.
         */
        p.plnI("public void dispatch(" + idRemote + " obj, " +
            idRemoteCall + " call, int opnum, long hash)");
        p.pln("throws java.lang.Exception");
        p.pOlnI("{");

        if (version == STUB_VERSION_FAT) {
            p.plnI("if (opnum < 0) {");
            if (remoteMethods.length > 0) {
                for (int opnum = 0; opnum < remoteMethods.length; opnum++) {
                    if (opnum > 0)
                        p.pO("} else ");
                    p.plnI("if (hash == " +
                        remoteMethods[opnum].getMethodHash() + "L) {");
                    p.pln("opnum = " + opnum + ";");
                }
                p.pOlnI("} else {");
            }
            /*
             * Skeleton throws UnmarshalException if it does not recognize
             * the method hash; this is what UnicastServerRef.dispatch()
             * would do.
             */
            p.pln("throw new " +
                idUnmarshalException + "(\"invalid method hash\");");
            if (remoteMethods.length > 0) {
                p.pOln("}");
            }
            /*
             * Ignore the validation of the interface hash if the
             * operation number was negative, since it is really a
             * method hash instead.
             */
            p.pOlnI("} else {");
        }

        p.plnI("if (hash != interfaceHash)");
        p.pln("throw new " +
            idSkeletonMismatchException + "(\"interface hash mismatch\");");
        p.pO();

        if (version == STUB_VERSION_FAT) {
            p.pOln("}");                // end if/else (opnum < 0) block
        }
        p.pln();

        /*
         * Cast remote object instance to our specific implementation class.
         */
        p.pln(remoteClassName + " server = (" + remoteClassName + ") obj;");

        /*
         * Process call according to the operation number.
         */
        p.plnI("switch (opnum) {");
        for (int opnum = 0; opnum < remoteMethods.length; opnum++) {
            writeSkeletonDispatchCase(p, opnum);
        }
        p.pOlnI("default:");
        /*
         * Skeleton throws UnmarshalException if it does not recognize
         * the operation number; this is consistent with the case of an
         * unrecognized method hash.
         */
        p.pln("throw new " + idUnmarshalException +
            "(\"invalid method number\");");
        p.pOln("}");                    // end switch statement

        p.pOln("}");                    // end dispatch() method

        p.pOln("}");                    // end skeleton class
    }

    /**
     * Write the case block for the skeleton's dispatch method for
     * the remote method with the given "opnum".
     */
    private void writeSkeletonDispatchCase(IndentingWriter p, int opnum)
        throws IOException
    {
        RemoteClass.Method method = remoteMethods[opnum];
        Identifier methodName = method.getName();
        Type methodType = method.getType();
        Type paramTypes[] = methodType.getArgumentTypes();
        String paramNames[] = nameParameters(paramTypes);
        Type returnType = methodType.getReturnType();

        p.pOlnI("case " + opnum + ": // " +
            methodType.typeString(methodName.toString(), true, false));
        /*
         * Use nested block statement inside case to provide an independent
         * namespace for local variables used to unmarshal parameters for
         * this remote method.
         */
        p.pOlnI("{");

        if (paramTypes.length > 0) {
            /*
             * Declare local variables to hold arguments.
             */
            for (int i = 0; i < paramTypes.length; i++) {
                p.pln(paramTypes[i] + " " + paramNames[i] + ";");
            }

            /*
             * Unmarshal arguments from call stream.
             */
            p.plnI("try {");
            p.pln("java.io.ObjectInput in = call.getInputStream();");
            boolean objectsRead = writeUnmarshalArguments(p, "in",
                paramTypes, paramNames);
            p.pOlnI("} catch (java.io.IOException e) {");
            p.pln("throw new " + idUnmarshalException +
                "(\"error unmarshalling arguments\", e);");
            /*
             * If any only if readObject has been invoked, we must catch
             * ClassNotFoundException as well as IOException.
             */
            if (objectsRead) {
                p.pOlnI("} catch (java.lang.ClassNotFoundException e) {");
                p.pln("throw new " + idUnmarshalException +
                    "(\"error unmarshalling arguments\", e);");
            }
            p.pOlnI("} finally {");
            p.pln("call.releaseInputStream();");
            p.pOln("}");
        } else {
            p.pln("call.releaseInputStream();");
        }

        if (!returnType.isType(TC_VOID)) {
            /*
             * Declare variable to hold return type, if not void.
             */
            p.p(returnType + " $result = ");            // REMIND: why $?
        }

        /*
         * Invoke the method on the server object.
         */
        p.p("server." + methodName + "(");
        for (int i = 0; i < paramNames.length; i++) {
            if (i > 0)
                p.p(", ");
            p.p(paramNames[i]);
        }
        p.pln(");");

        /*
         * Always invoke getResultStream(true) on the call object to send
         * the indication of a successful invocation to the caller.  If
         * the return type is not void, keep the result stream and marshal
         * the return value.
         */
        p.plnI("try {");
        if (!returnType.isType(TC_VOID)) {
            p.p("java.io.ObjectOutput out = ");
        }
        p.pln("call.getResultStream(true);");
        if (!returnType.isType(TC_VOID)) {
            writeMarshalArgument(p, "out", returnType, "$result");
            p.pln(";");
        }
        p.pOlnI("} catch (java.io.IOException e) {");
        p.pln("throw new " +
            idMarshalException + "(\"error marshalling return\", e);");
        p.pOln("}");

        p.pln("break;");                // break from switch statement

        p.pOlnI("}");                   // end nested block statement
        p.pln();
    }

    /**
     * Write declaration and initializer for "operations" static array.
     */
    private void writeOperationsArray(IndentingWriter p)
        throws IOException
    {
        p.plnI("private static final " + idOperation + "[] operations = {");
        for (int i = 0; i < remoteMethods.length; i++) {
            if (i > 0)
                p.pln(",");
            p.p("new " + idOperation + "(\"" +
                remoteMethods[i].getOperationString() + "\")");
        }
        p.pln();
        p.pOln("};");
    }

    /**
     * Write declaration and initializer for "interfaceHash" static field.
     */
    private void writeInterfaceHash(IndentingWriter p)
        throws IOException
    {
        p.pln("private static final long interfaceHash = " +
            remoteClass.getInterfaceHash() + "L;");
    }

    /**
     * Write declaration for java.lang.reflect.Method static fields
     * corresponding to each remote method in a stub.
     */
    private void writeMethodFieldDeclarations(IndentingWriter p)
        throws IOException
    {
        for (int i = 0; i < methodFieldNames.length; i++) {
            p.pln("private static java.lang.reflect.Method " +
                methodFieldNames[i] + ";");
        }
    }

    /**
     * Write code to initialize the static fields for each method
     * using the Java Reflection API.
     */
    private void writeMethodFieldInitializers(IndentingWriter p)
        throws IOException
    {
        for (int i = 0; i < methodFieldNames.length; i++) {
            p.p(methodFieldNames[i] + " = ");
            /*
             * Here we look up the Method object in the arbitrary interface
             * that we find in the RemoteClass.Method object.
             * REMIND: Is this arbitrary choice OK?
             * REMIND: Should this access be part of RemoteClass.Method's
             * abstraction?
             */
            RemoteClass.Method method = remoteMethods[i];
            MemberDefinition def = method.getMemberDefinition();
            Identifier methodName = method.getName();
            Type methodType = method.getType();
            Type paramTypes[] = methodType.getArgumentTypes();

            p.p(def.getClassDefinition().getName() + ".class.getMethod(\"" +
                methodName + "\", new java.lang.Class[] {");
            for (int j = 0; j < paramTypes.length; j++) {
                if (j > 0)
                    p.p(", ");
                p.p(paramTypes[j] + ".class");
            }
            p.pln("});");
        }
    }


    /*
     * Following are a series of static utility methods useful during
     * the code generation process:
     */

    /**
     * Generate an array of names for fields that correspond to the given
     * array of remote methods.  Each name in the returned array is
     * guaranteed to be unique.
     *
     * The name of a method is included in its corresponding field name
     * to enhance readability of the generated code.
     */
    private static String[] nameMethodFields(RemoteClass.Method[] methods) {
        String[] names = new String[methods.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = "$method_" + methods[i].getName() + "_" + i;
        }
        return names;
    }

    /**
     * Generate an array of names for parameters corresponding to the
     * given array of types for the parameters.  Each name in the returned
     * array is guaranteed to be unique.
     *
     * A representation of the type of a parameter is included in its
     * corresponding field name to enhance the readability of the generated
     * code.
     */
    private static String[] nameParameters(Type[] types) {
        String[] names = new String[types.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = "$param_" +
                generateNameFromType(types[i]) + "_" + (i + 1);
        }
        return names;
    }

    /**
     * Generate a readable string representing the given type suitable
     * for embedding within a Java identifier.
     */
    private static String generateNameFromType(Type type) {
        int typeCode = type.getTypeCode();
        switch (typeCode) {
        case TC_BOOLEAN:
        case TC_BYTE:
        case TC_CHAR:
        case TC_SHORT:
        case TC_INT:
        case TC_LONG:
        case TC_FLOAT:
        case TC_DOUBLE:
            return type.toString();
        case TC_ARRAY:
            return "arrayOf_" + generateNameFromType(type.getElementType());
        case TC_CLASS:
            return Names.mangleClass(type.getClassName().getName()).toString();
        default:
            throw new Error("unexpected type code: " + typeCode);
        }
    }

    /**
     * Write a snippet of Java code to marshal a value named "name" of
     * type "type" to the java.io.ObjectOutput stream named "stream".
     *
     * Primitive types are marshalled with their corresponding methods
     * in the java.io.DataOutput interface, and objects (including arrays)
     * are marshalled using the writeObject method.
     */
    private static void writeMarshalArgument(IndentingWriter p,
                                             String streamName,
                                             Type type, String name)
        throws IOException
    {
        int typeCode = type.getTypeCode();
        switch (typeCode) {
        case TC_BOOLEAN:
            p.p(streamName + ".writeBoolean(" + name + ")");
            break;
        case TC_BYTE:
            p.p(streamName + ".writeByte(" + name + ")");
            break;
        case TC_CHAR:
            p.p(streamName + ".writeChar(" + name + ")");
            break;
        case TC_SHORT:
            p.p(streamName + ".writeShort(" + name + ")");
            break;
        case TC_INT:
            p.p(streamName + ".writeInt(" + name + ")");
            break;
        case TC_LONG:
            p.p(streamName + ".writeLong(" + name + ")");
            break;
        case TC_FLOAT:
            p.p(streamName + ".writeFloat(" + name + ")");
            break;
        case TC_DOUBLE:
            p.p(streamName + ".writeDouble(" + name + ")");
            break;
        case TC_ARRAY:
        case TC_CLASS:
            p.p(streamName + ".writeObject(" + name + ")");
            break;
        default:
            throw new Error("unexpected type code: " + typeCode);
        }
    }

    /**
     * Write Java statements to marshal a series of values in order as
     * named in the "names" array, with types as specified in the "types"
     * array", to the java.io.ObjectOutput stream named "stream".
     */
    private static void writeMarshalArguments(IndentingWriter p,
                                              String streamName,
                                              Type[] types, String[] names)
        throws IOException
    {
        if (types.length != names.length) {
            throw new Error("parameter type and name arrays different sizes");
        }

        for (int i = 0; i < types.length; i++) {
            writeMarshalArgument(p, streamName, types[i], names[i]);
            p.pln(";");
        }
    }

    /**
     * Write a snippet of Java code to unmarshal a value of type "type"
     * from the java.io.ObjectInput stream named "stream" into a variable
     * named "name" (if "name" is null, the value in unmarshalled and
     * discarded).
     *
     * Primitive types are unmarshalled with their corresponding methods
     * in the java.io.DataInput interface, and objects (including arrays)
     * are unmarshalled using the readObject method.
     */
    private static boolean writeUnmarshalArgument(IndentingWriter p,
                                                  String streamName,
                                                  Type type, String name)
        throws IOException
    {
        boolean readObject = false;

        if (name != null) {
            p.p(name + " = ");
        }

        int typeCode = type.getTypeCode();
        switch (type.getTypeCode()) {
        case TC_BOOLEAN:
            p.p(streamName + ".readBoolean()");
            break;
        case TC_BYTE:
            p.p(streamName + ".readByte()");
            break;
        case TC_CHAR:
            p.p(streamName + ".readChar()");
            break;
        case TC_SHORT:
            p.p(streamName + ".readShort()");
            break;
        case TC_INT:
            p.p(streamName + ".readInt()");
            break;
        case TC_LONG:
            p.p(streamName + ".readLong()");
            break;
        case TC_FLOAT:
            p.p(streamName + ".readFloat()");
            break;
        case TC_DOUBLE:
            p.p(streamName + ".readDouble()");
            break;
        case TC_ARRAY:
        case TC_CLASS:
            p.p("(" + type + ") " + streamName + ".readObject()");
            readObject = true;
            break;
        default:
            throw new Error("unexpected type code: " + typeCode);
        }
        return readObject;
    }

    /**
     * Write Java statements to unmarshal a series of values in order of
     * types as in the "types" array from the java.io.ObjectInput stream
     * named "stream" into variables as named in "names" (for any element
     * of "names" that is null, the corresponding value is unmarshalled
     * and discarded).
     */
    private static boolean writeUnmarshalArguments(IndentingWriter p,
                                                   String streamName,
                                                   Type[] types,
                                                   String[] names)
        throws IOException
    {
        if (types.length != names.length) {
            throw new Error("parameter type and name arrays different sizes");
        }

        boolean readObject = false;
        for (int i = 0; i < types.length; i++) {
            if (writeUnmarshalArgument(p, streamName, types[i], names[i])) {
                readObject = true;
            }
            p.pln(";");
        }
        return readObject;
    }

    /**
     * Return a snippet of Java code to wrap a value named "name" of
     * type "type" into an object as appropriate for use by the
     * Java Reflection API.
     *
     * For primitive types, an appropriate wrapper class instantiated
     * with the primitive value.  For object types (including arrays),
     * no wrapping is necessary, so the value is named directly.
     */
    private static String wrapArgumentCode(Type type, String name) {
        int typeCode = type.getTypeCode();
        switch (typeCode) {
        case TC_BOOLEAN:
            return ("(" + name +
                    " ? java.lang.Boolean.TRUE : java.lang.Boolean.FALSE)");
        case TC_BYTE:
            return "new java.lang.Byte(" + name + ")";
        case TC_CHAR:
            return "new java.lang.Character(" + name + ")";
        case TC_SHORT:
            return "new java.lang.Short(" + name + ")";
        case TC_INT:
            return "new java.lang.Integer(" + name + ")";
        case TC_LONG:
            return "new java.lang.Long(" + name + ")";
        case TC_FLOAT:
            return "new java.lang.Float(" + name + ")";
        case TC_DOUBLE:
            return "new java.lang.Double(" + name + ")";
        case TC_ARRAY:
        case TC_CLASS:
            return name;
        default:
            throw new Error("unexpected type code: " + typeCode);
        }
    }

    /**
     * Return a snippet of Java code to unwrap a value named "name" into
     * a value of type "type", as appropriate for the Java Reflection API.
     *
     * For primitive types, the value is assumed to be of the corresponding
     * wrapper type, and a method is called on the wrapper type to retrieve
     * the primitive value.  For object types (include arrays), no
     * unwrapping is necessary; the value is simply cast to the expected
     * real object type.
     */
    private static String unwrapArgumentCode(Type type, String name) {
        int typeCode = type.getTypeCode();
        switch (typeCode) {
        case TC_BOOLEAN:
            return "((java.lang.Boolean) " + name + ").booleanValue()";
        case TC_BYTE:
            return "((java.lang.Byte) " + name + ").byteValue()";
        case TC_CHAR:
            return "((java.lang.Character) " + name + ").charValue()";
        case TC_SHORT:
            return "((java.lang.Short) " + name + ").shortValue()";
        case TC_INT:
            return "((java.lang.Integer) " + name + ").intValue()";
        case TC_LONG:
            return "((java.lang.Long) " + name + ").longValue()";
        case TC_FLOAT:
            return "((java.lang.Float) " + name + ").floatValue()";
        case TC_DOUBLE:
            return "((java.lang.Double) " + name + ").doubleValue()";
        case TC_ARRAY:
        case TC_CLASS:
            return "((" + type + ") " + name + ")";
        default:
            throw new Error("unexpected type code: " + typeCode);
        }
    }
}
