/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.invoke;

import sun.invoke.util.VerifyAccess;
import java.lang.invoke.LambdaForm.Name;
import java.lang.invoke.MethodHandles.Lookup;

import sun.invoke.util.Wrapper;

import java.io.*;
import java.util.*;

import jdk.internal.org.objectweb.asm.*;

import java.lang.reflect.*;
import static java.lang.invoke.MethodHandleStatics.*;
import static java.lang.invoke.MethodHandleNatives.Constants.*;
import static java.lang.invoke.MethodHandles.Lookup.IMPL_LOOKUP;
import sun.invoke.util.ValueConversions;
import sun.invoke.util.VerifyType;

/**
 * Code generation backend for LambdaForm.
 * <p>
 * @author John Rose, JSR 292 EG
 */
class InvokerBytecodeGenerator {
    /** Define class names for convenience. */
    private static final String MH      = "java/lang/invoke/MethodHandle";
    private static final String BMH     = "java/lang/invoke/BoundMethodHandle";
    private static final String LF      = "java/lang/invoke/LambdaForm";
    private static final String LFN     = "java/lang/invoke/LambdaForm$Name";
    private static final String CLS     = "java/lang/Class";
    private static final String OBJ     = "java/lang/Object";
    private static final String OBJARY  = "[Ljava/lang/Object;";

    private static final String LF_SIG  = "L" + LF + ";";
    private static final String LFN_SIG = "L" + LFN + ";";
    private static final String LL_SIG  = "(L" + OBJ + ";)L" + OBJ + ";";

    /** Name of its super class*/
    private static final String superName = LF;

    /** Name of new class */
    private final String className;

    /** Name of the source file (for stack trace printing). */
    private final String sourceFile;

    private final LambdaForm lambdaForm;
    private final String     invokerName;
    private final MethodType invokerType;
    private final int[] localsMap;

    /** ASM bytecode generation. */
    private ClassWriter cw;
    private MethodVisitor mv;

    private static final MemberName.Factory MEMBERNAME_FACTORY = MemberName.getFactory();
    private static final Class<?> HOST_CLASS = LambdaForm.class;

    private InvokerBytecodeGenerator(LambdaForm lambdaForm, int localsMapSize,
                                     String className, String invokerName, MethodType invokerType) {
        if (invokerName.contains(".")) {
            int p = invokerName.indexOf(".");
            className = invokerName.substring(0, p);
            invokerName = invokerName.substring(p+1);
        }
        if (DUMP_CLASS_FILES) {
            className = makeDumpableClassName(className);
        }
        this.className  = superName + "$" + className;
        this.sourceFile = "LambdaForm$" + className;
        this.lambdaForm = lambdaForm;
        this.invokerName = invokerName;
        this.invokerType = invokerType;
        this.localsMap = new int[localsMapSize];
    }

    private InvokerBytecodeGenerator(String className, String invokerName, MethodType invokerType) {
        this(null, invokerType.parameterCount(),
             className, invokerName, invokerType);
        // Create an array to map name indexes to locals indexes.
        for (int i = 0; i < localsMap.length; i++) {
            localsMap[i] = invokerType.parameterSlotCount() - invokerType.parameterSlotDepth(i);
        }
    }

    private InvokerBytecodeGenerator(String className, LambdaForm form, MethodType invokerType) {
        this(form, form.names.length,
             className, form.debugName, invokerType);
        // Create an array to map name indexes to locals indexes.
        Name[] names = form.names;
        for (int i = 0, index = 0; i < localsMap.length; i++) {
            localsMap[i] = index;
            index += Wrapper.forBasicType(names[i].type).stackSlots();
        }
    }


    /** instance counters for dumped classes */
    private final static HashMap<String,Integer> DUMP_CLASS_FILES_COUNTERS;
    /** debugging flag for saving generated class files */
    private final static File DUMP_CLASS_FILES_DIR;

    static {
        if (DUMP_CLASS_FILES) {
            DUMP_CLASS_FILES_COUNTERS = new HashMap<>();
            try {
                File dumpDir = new File("DUMP_CLASS_FILES");
                if (!dumpDir.exists()) {
                    dumpDir.mkdirs();
                }
                DUMP_CLASS_FILES_DIR = dumpDir;
                System.out.println("Dumping class files to "+DUMP_CLASS_FILES_DIR+"/...");
            } catch (Exception e) {
                throw newInternalError(e);
            }
        } else {
            DUMP_CLASS_FILES_COUNTERS = null;
            DUMP_CLASS_FILES_DIR = null;
        }
    }

    static void maybeDump(final String className, final byte[] classFile) {
        if (DUMP_CLASS_FILES) {
            System.out.println("dump: " + className);
            java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction<Void>() {
                public Void run() {
                    try {
                        String dumpName = className;
                        //dumpName = dumpName.replace('/', '-');
                        File dumpFile = new File(DUMP_CLASS_FILES_DIR, dumpName+".class");
                        dumpFile.getParentFile().mkdirs();
                        FileOutputStream file = new FileOutputStream(dumpFile);
                        file.write(classFile);
                        file.close();
                        return null;
                    } catch (IOException ex) {
                        throw newInternalError(ex);
                    }
                }
            });
        }

    }

    private static String makeDumpableClassName(String className) {
        Integer ctr;
        synchronized (DUMP_CLASS_FILES_COUNTERS) {
            ctr = DUMP_CLASS_FILES_COUNTERS.get(className);
            if (ctr == null)  ctr = 0;
            DUMP_CLASS_FILES_COUNTERS.put(className, ctr+1);
        }
        String sfx = ctr.toString();
        while (sfx.length() < 3)
            sfx = "0"+sfx;
        className += sfx;
        return className;
    }

    class CpPatch {
        final int index;
        final String placeholder;
        final Object value;
        CpPatch(int index, String placeholder, Object value) {
            this.index = index;
            this.placeholder = placeholder;
            this.value = value;
        }
        public String toString() {
            return "CpPatch/index="+index+",placeholder="+placeholder+",value="+value;
        }
    }

    Map<Object, CpPatch> cpPatches = new HashMap<>();

    int cph = 0;  // for counting constant placeholders

    String constantPlaceholder(Object arg) {
        String cpPlaceholder = "CONSTANT_PLACEHOLDER_" + cph++;
        if (DUMP_CLASS_FILES) cpPlaceholder += " <<" + arg.toString() + ">>";  // debugging aid
        if (cpPatches.containsKey(cpPlaceholder)) {
            throw new InternalError("observed CP placeholder twice: " + cpPlaceholder);
        }
        // insert placeholder in CP and remember the patch
        int index = cw.newConst((Object) cpPlaceholder);  // TODO check if aready in the constant pool
        cpPatches.put(cpPlaceholder, new CpPatch(index, cpPlaceholder, arg));
        return cpPlaceholder;
    }

    Object[] cpPatches(byte[] classFile) {
        int size = getConstantPoolSize(classFile);
        Object[] res = new Object[size];
        for (CpPatch p : cpPatches.values()) {
            if (p.index >= size)
                throw new InternalError("in cpool["+size+"]: "+p+"\n"+Arrays.toString(Arrays.copyOf(classFile, 20)));
            res[p.index] = p.value;
        }
        return res;
    }

    /**
     * Extract the number of constant pool entries from a given class file.
     *
     * @param classFile the bytes of the class file in question.
     * @return the number of entries in the constant pool.
     */
    private static int getConstantPoolSize(byte[] classFile) {
        // The first few bytes:
        // u4 magic;
        // u2 minor_version;
        // u2 major_version;
        // u2 constant_pool_count;
        return ((classFile[8] & 0xFF) << 8) | (classFile[9] & 0xFF);
    }

    /**
     * Extract the MemberName of a newly-defined method.
     */
    private MemberName loadMethod(byte[] classFile) {
        Class<?> invokerClass = loadAndInitializeInvokerClass(classFile, cpPatches(classFile));
        return resolveInvokerMember(invokerClass, invokerName, invokerType);
    }

    /**
     * Define a given class as anonymous class in the runtime system.
     */
    private static Class<?> loadAndInitializeInvokerClass(byte[] classBytes, Object[] patches) {
        Class<?> invokerClass = UNSAFE.defineAnonymousClass(HOST_CLASS, classBytes, patches);
        UNSAFE.ensureClassInitialized(invokerClass);  // Make sure the class is initialized; VM might complain.
        return invokerClass;
    }

    private static MemberName resolveInvokerMember(Class<?> invokerClass, String name, MethodType type) {
        MemberName member = new MemberName(invokerClass, name, type, REF_invokeStatic);
        //System.out.println("resolveInvokerMember => "+member);
        //for (Method m : invokerClass.getDeclaredMethods())  System.out.println("  "+m);
        try {
            member = MEMBERNAME_FACTORY.resolveOrFail(REF_invokeStatic, member, HOST_CLASS, ReflectiveOperationException.class);
        } catch (ReflectiveOperationException e) {
            throw newInternalError(e);
        }
        //System.out.println("resolveInvokerMember => "+member);
        return member;
    }

    /**
     * Set up class file generation.
     */
    private void classFilePrologue() {
        cw = new ClassWriter(ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL + Opcodes.ACC_SUPER, className, null, superName, null);
        cw.visitSource(sourceFile, null);

        String invokerDesc = invokerType.toMethodDescriptorString();
        mv = cw.visitMethod(Opcodes.ACC_STATIC, invokerName, invokerDesc, null, null);
    }

    /**
     * Tear down class file generation.
     */
    private void classFileEpilogue() {
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /*
     * Low-level emit helpers.
     */
    private void emitConst(Object con) {
        if (con == null) {
            mv.visitInsn(Opcodes.ACONST_NULL);
            return;
        }
        if (con instanceof Integer) {
            emitIconstInsn((int) con);
            return;
        }
        if (con instanceof Long) {
            long x = (long) con;
            if (x == (short) x) {
                emitIconstInsn((int) x);
                mv.visitInsn(Opcodes.I2L);
                return;
            }
        }
        if (con instanceof Float) {
            float x = (float) con;
            if (x == (short) x) {
                emitIconstInsn((int) x);
                mv.visitInsn(Opcodes.I2F);
                return;
            }
        }
        if (con instanceof Double) {
            double x = (double) con;
            if (x == (short) x) {
                emitIconstInsn((int) x);
                mv.visitInsn(Opcodes.I2D);
                return;
            }
        }
        if (con instanceof Boolean) {
            emitIconstInsn((boolean) con ? 1 : 0);
            return;
        }
        // fall through:
        mv.visitLdcInsn(con);
    }

    private void emitIconstInsn(int i) {
        int opcode;
        switch (i) {
        case 0:  opcode = Opcodes.ICONST_0;  break;
        case 1:  opcode = Opcodes.ICONST_1;  break;
        case 2:  opcode = Opcodes.ICONST_2;  break;
        case 3:  opcode = Opcodes.ICONST_3;  break;
        case 4:  opcode = Opcodes.ICONST_4;  break;
        case 5:  opcode = Opcodes.ICONST_5;  break;
        default:
            if (i == (byte) i) {
                mv.visitIntInsn(Opcodes.BIPUSH, i & 0xFF);
            } else if (i == (short) i) {
                mv.visitIntInsn(Opcodes.SIPUSH, (char) i);
            } else {
                mv.visitLdcInsn(i);
            }
            return;
        }
        mv.visitInsn(opcode);
    }

    /*
     * NOTE: These load/store methods use the localsMap to find the correct index!
     */
    private void emitLoadInsn(char type, int index) {
        int opcode;
        switch (type) {
        case 'I':  opcode = Opcodes.ILOAD;  break;
        case 'J':  opcode = Opcodes.LLOAD;  break;
        case 'F':  opcode = Opcodes.FLOAD;  break;
        case 'D':  opcode = Opcodes.DLOAD;  break;
        case 'L':  opcode = Opcodes.ALOAD;  break;
        default:
            throw new InternalError("unknown type: " + type);
        }
        mv.visitVarInsn(opcode, localsMap[index]);
    }
    private void emitAloadInsn(int index) {
        emitLoadInsn('L', index);
    }

    private void emitStoreInsn(char type, int index) {
        int opcode;
        switch (type) {
        case 'I':  opcode = Opcodes.ISTORE;  break;
        case 'J':  opcode = Opcodes.LSTORE;  break;
        case 'F':  opcode = Opcodes.FSTORE;  break;
        case 'D':  opcode = Opcodes.DSTORE;  break;
        case 'L':  opcode = Opcodes.ASTORE;  break;
        default:
            throw new InternalError("unknown type: " + type);
        }
        mv.visitVarInsn(opcode, localsMap[index]);
    }
    private void emitAstoreInsn(int index) {
        emitStoreInsn('L', index);
    }

    /**
     * Emit a boxing call.
     *
     * @param type primitive type class to box.
     */
    private void emitBoxing(Class<?> type) {
        Wrapper wrapper = Wrapper.forPrimitiveType(type);
        String owner = "java/lang/" + wrapper.wrapperType().getSimpleName();
        String name  = "valueOf";
        String desc  = "(" + wrapper.basicTypeChar() + ")L" + owner + ";";
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, owner, name, desc);
    }

    /**
     * Emit an unboxing call (plus preceding checkcast).
     *
     * @param type wrapper type class to unbox.
     */
    private void emitUnboxing(Class<?> type) {
        Wrapper wrapper = Wrapper.forWrapperType(type);
        String owner = "java/lang/" + wrapper.wrapperType().getSimpleName();
        String name  = wrapper.primitiveSimpleName() + "Value";
        String desc  = "()" + wrapper.basicTypeChar();
        mv.visitTypeInsn(Opcodes.CHECKCAST, owner);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, name, desc);
    }

    /**
     * Emit an implicit conversion.
     *
     * @param ptype type of value present on stack
     * @param pclass type of value required on stack
     */
    private void emitImplicitConversion(char ptype, Class<?> pclass) {
        switch (ptype) {
        case 'L':
            if (VerifyType.isNullConversion(Object.class, pclass))
                return;
            if (isStaticallyNameable(pclass)) {
                mv.visitTypeInsn(Opcodes.CHECKCAST, getInternalName(pclass));
            } else {
                mv.visitLdcInsn(constantPlaceholder(pclass));
                mv.visitTypeInsn(Opcodes.CHECKCAST, CLS);
                mv.visitInsn(Opcodes.SWAP);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CLS, "cast", LL_SIG);
                if (pclass.isArray())
                    mv.visitTypeInsn(Opcodes.CHECKCAST, OBJARY);
            }
            return;
        case 'I':
            if (!VerifyType.isNullConversion(int.class, pclass))
                emitPrimCast(ptype, Wrapper.basicTypeChar(pclass));
            return;
        case 'J':
            assert(pclass == long.class);
            return;
        case 'F':
            assert(pclass == float.class);
            return;
        case 'D':
            assert(pclass == double.class);
            return;
        }
        throw new InternalError("bad implicit conversion: tc="+ptype+": "+pclass);
    }

    /**
     * Emits an actual return instruction conforming to the given return type.
     */
    private void emitReturnInsn(Class<?> type) {
        int opcode;
        switch (Wrapper.basicTypeChar(type)) {
        case 'I':  opcode = Opcodes.IRETURN;  break;
        case 'J':  opcode = Opcodes.LRETURN;  break;
        case 'F':  opcode = Opcodes.FRETURN;  break;
        case 'D':  opcode = Opcodes.DRETURN;  break;
        case 'L':  opcode = Opcodes.ARETURN;  break;
        case 'V':  opcode = Opcodes.RETURN;   break;
        default:
            throw new InternalError("unknown return type: " + type);
        }
        mv.visitInsn(opcode);
    }

    private static String getInternalName(Class<?> c) {
        assert(VerifyAccess.isTypeVisible(c, Object.class));
        return c.getName().replace('.', '/');
    }

    /**
     * Generate customized bytecode for a given LambdaForm.
     */
    static MemberName generateCustomizedCode(LambdaForm form, MethodType invokerType) {
        InvokerBytecodeGenerator g = new InvokerBytecodeGenerator("MH", form, invokerType);
        return g.loadMethod(g.generateCustomizedCodeBytes());
    }

    /**
     * Generate an invoker method for the passed {@link LambdaForm}.
     */
    private byte[] generateCustomizedCodeBytes() {
        classFilePrologue();

        // Suppress this method in backtraces displayed to the user.
        mv.visitAnnotation("Ljava/lang/invoke/LambdaForm$Hidden;", true);

        // Mark this method as a compiled LambdaForm
        mv.visitAnnotation("Ljava/lang/invoke/LambdaForm$Compiled;", true);

        // Force inlining of this invoker method.
        mv.visitAnnotation("Ljava/lang/invoke/ForceInline;", true);

        // iterate over the form's names, generating bytecode instructions for each
        // start iterating at the first name following the arguments
        for (int i = lambdaForm.arity; i < lambdaForm.names.length; i++) {
            Name name = lambdaForm.names[i];
            MemberName member = name.function.member();

            if (isSelectAlternative(member)) {
                // selectAlternative idiom
                // FIXME: make sure this idiom is really present!
                emitSelectAlternative(name, lambdaForm.names[i + 1]);
                i++;  // skip MH.invokeBasic of the selectAlternative result
            } else if (isStaticallyInvocable(member)) {
                emitStaticInvoke(member, name);
            } else {
                emitInvoke(name);
            }

            // store the result from evaluating to the target name in a local if required
            // (if this is the last value, i.e., the one that is going to be returned,
            // avoid store/load/return and just return)
            if (i == lambdaForm.names.length - 1 && i == lambdaForm.result) {
                // return value - do nothing
            } else if (name.type != 'V') {
                // non-void: actually assign
                emitStoreInsn(name.type, name.index());
            }
        }

        // return statement
        emitReturn();

        classFileEpilogue();
        bogusMethod(lambdaForm);

        final byte[] classFile = cw.toByteArray();
        maybeDump(className, classFile);
        return classFile;
    }

    /**
     * Emit an invoke for the given name.
     */
    void emitInvoke(Name name) {
        if (true) {
            // push receiver
            MethodHandle target = name.function.resolvedHandle;
            assert(target != null) : name.exprString();
            mv.visitLdcInsn(constantPlaceholder(target));
            mv.visitTypeInsn(Opcodes.CHECKCAST, MH);
        } else {
            // load receiver
            emitAloadInsn(0);
            mv.visitTypeInsn(Opcodes.CHECKCAST, MH);
            mv.visitFieldInsn(Opcodes.GETFIELD, MH, "form", LF_SIG);
            mv.visitFieldInsn(Opcodes.GETFIELD, LF, "names", LFN_SIG);
            // TODO more to come
        }

        // push arguments
        for (int i = 0; i < name.arguments.length; i++) {
            emitPushArgument(name, i);
        }

        // invocation
        MethodType type = name.function.methodType();
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MH, "invokeBasic", type.basicType().toMethodDescriptorString());
    }

    static private Class<?>[] STATICALLY_INVOCABLE_PACKAGES = {
        // Sample classes from each package we are willing to bind to statically:
        java.lang.Object.class,
        java.util.Arrays.class,
        sun.misc.Unsafe.class
        //MethodHandle.class already covered
    };

    static boolean isStaticallyInvocable(MemberName member) {
        if (member == null)  return false;
        if (member.isConstructor())  return false;
        Class<?> cls = member.getDeclaringClass();
        if (cls.isArray() || cls.isPrimitive())
            return false;  // FIXME
        if (cls.isAnonymousClass() || cls.isLocalClass())
            return false;  // inner class of some sort
        if (cls.getClassLoader() != MethodHandle.class.getClassLoader())
            return false;  // not on BCP
        MethodType mtype = member.getMethodOrFieldType();
        if (!isStaticallyNameable(mtype.returnType()))
            return false;
        for (Class<?> ptype : mtype.parameterArray())
            if (!isStaticallyNameable(ptype))
                return false;
        if (!member.isPrivate() && VerifyAccess.isSamePackage(MethodHandle.class, cls))
            return true;   // in java.lang.invoke package
        if (member.isPublic() && isStaticallyNameable(cls))
            return true;
        return false;
    }

    static boolean isStaticallyNameable(Class<?> cls) {
        while (cls.isArray())
            cls = cls.getComponentType();
        if (cls.isPrimitive())
            return true;  // int[].class, for example
        // could use VerifyAccess.isClassAccessible but the following is a safe approximation
        if (cls.getClassLoader() != Object.class.getClassLoader())
            return false;
        if (VerifyAccess.isSamePackage(MethodHandle.class, cls))
            return true;
        if (!Modifier.isPublic(cls.getModifiers()))
            return false;
        for (Class<?> pkgcls : STATICALLY_INVOCABLE_PACKAGES) {
            if (VerifyAccess.isSamePackage(pkgcls, cls))
                return true;
        }
        return false;
    }

    /**
     * Emit an invoke for the given name, using the MemberName directly.
     */
    void emitStaticInvoke(MemberName member, Name name) {
        assert(member.equals(name.function.member()));
        String cname = getInternalName(member.getDeclaringClass());
        String mname = member.getName();
        String mtype;
        byte refKind = member.getReferenceKind();
        if (refKind == REF_invokeSpecial) {
            // in order to pass the verifier, we need to convert this to invokevirtual in all cases
            assert(member.canBeStaticallyBound()) : member;
            refKind = REF_invokeVirtual;
        }

        if (member.getDeclaringClass().isInterface() && refKind == REF_invokeVirtual) {
            // Methods from Object declared in an interface can be resolved by JVM to invokevirtual kind.
            // Need to convert it back to invokeinterface to pass verification and make the invocation works as expected.
            refKind = REF_invokeInterface;
        }

        // push arguments
        for (int i = 0; i < name.arguments.length; i++) {
            emitPushArgument(name, i);
        }

        // invocation
        if (member.isMethod()) {
            mtype = member.getMethodType().toMethodDescriptorString();
            mv.visitMethodInsn(refKindOpcode(refKind), cname, mname, mtype,
                               member.getDeclaringClass().isInterface());
        } else {
            mtype = MethodType.toFieldDescriptorString(member.getFieldType());
            mv.visitFieldInsn(refKindOpcode(refKind), cname, mname, mtype);
        }
    }
    int refKindOpcode(byte refKind) {
        switch (refKind) {
        case REF_invokeVirtual:      return Opcodes.INVOKEVIRTUAL;
        case REF_invokeStatic:       return Opcodes.INVOKESTATIC;
        case REF_invokeSpecial:      return Opcodes.INVOKESPECIAL;
        case REF_invokeInterface:    return Opcodes.INVOKEINTERFACE;
        case REF_getField:           return Opcodes.GETFIELD;
        case REF_putField:           return Opcodes.PUTFIELD;
        case REF_getStatic:          return Opcodes.GETSTATIC;
        case REF_putStatic:          return Opcodes.PUTSTATIC;
        }
        throw new InternalError("refKind="+refKind);
    }

    /**
     * Check if MemberName is a call to MethodHandleImpl.selectAlternative.
     */
    private boolean isSelectAlternative(MemberName member) {
        return member != null &&
               member.getDeclaringClass() == MethodHandleImpl.class &&
               member.getName().equals("selectAlternative");
    }

    /**
     * Emit bytecode for the selectAlternative idiom.
     *
     * The pattern looks like (Cf. MethodHandleImpl.makeGuardWithTest):
     * <blockquote><pre>{@code
     *   Lambda(a0:L,a1:I)=>{
     *     t2:I=foo.test(a1:I);
     *     t3:L=MethodHandleImpl.selectAlternative(t2:I,(MethodHandle(int)int),(MethodHandle(int)int));
     *     t4:I=MethodHandle.invokeBasic(t3:L,a1:I);t4:I}
     * }</pre></blockquote>
     */
    private void emitSelectAlternative(Name selectAlternativeName, Name invokeBasicName) {
        MethodType type = selectAlternativeName.function.methodType();

        Name receiver = (Name) invokeBasicName.arguments[0];

        Label L_fallback = new Label();
        Label L_done     = new Label();

        // load test result
        emitPushArgument(selectAlternativeName, 0);
        mv.visitInsn(Opcodes.ICONST_1);

        // if_icmpne L_fallback
        mv.visitJumpInsn(Opcodes.IF_ICMPNE, L_fallback);

        // invoke selectAlternativeName.arguments[1]
        MethodHandle target = (MethodHandle) selectAlternativeName.arguments[1];
        emitPushArgument(selectAlternativeName, 1);  // get 2nd argument of selectAlternative
        emitAstoreInsn(receiver.index());  // store the MH in the receiver slot
        emitInvoke(invokeBasicName);

        // goto L_done
        mv.visitJumpInsn(Opcodes.GOTO, L_done);

        // L_fallback:
        mv.visitLabel(L_fallback);

        // invoke selectAlternativeName.arguments[2]
        MethodHandle fallback = (MethodHandle) selectAlternativeName.arguments[2];
        emitPushArgument(selectAlternativeName, 2);  // get 3rd argument of selectAlternative
        emitAstoreInsn(receiver.index());  // store the MH in the receiver slot
        emitInvoke(invokeBasicName);

        // L_done:
        mv.visitLabel(L_done);
    }

    private void emitPushArgument(Name name, int paramIndex) {
        Object arg = name.arguments[paramIndex];
        char ptype = name.function.parameterType(paramIndex);
        MethodType mtype = name.function.methodType();
        if (arg instanceof Name) {
            Name n = (Name) arg;
            emitLoadInsn(n.type, n.index());
            emitImplicitConversion(n.type, mtype.parameterType(paramIndex));
        } else if ((arg == null || arg instanceof String) && ptype == 'L') {
            emitConst(arg);
        } else {
            if (Wrapper.isWrapperType(arg.getClass()) && ptype != 'L') {
                emitConst(arg);
            } else {
                mv.visitLdcInsn(constantPlaceholder(arg));
                emitImplicitConversion('L', mtype.parameterType(paramIndex));
            }
        }
    }

    /**
     * Emits a return statement from a LF invoker. If required, the result type is cast to the correct return type.
     */
    private void emitReturn() {
        // return statement
        if (lambdaForm.result == -1) {
            // void
            mv.visitInsn(Opcodes.RETURN);
        } else {
            LambdaForm.Name rn = lambdaForm.names[lambdaForm.result];
            char rtype = Wrapper.basicTypeChar(invokerType.returnType());

            // put return value on the stack if it is not already there
            if (lambdaForm.result != lambdaForm.names.length - 1) {
                emitLoadInsn(rn.type, lambdaForm.result);
            }

            // potentially generate cast
            // rtype is the return type of the invoker - generated code must conform to this
            // rn.type is the type of the result Name in the LF
            if (rtype != rn.type) {
                // need cast
                if (rtype == 'L') {
                    // possibly cast the primitive to the correct type for boxing
                    char boxedType = Wrapper.forWrapperType(invokerType.returnType()).basicTypeChar();
                    if (boxedType != rn.type) {
                        emitPrimCast(rn.type, boxedType);
                    }
                    // cast primitive to reference ("boxing")
                    emitBoxing(invokerType.returnType());
                } else {
                    // to-primitive cast
                    if (rn.type != 'L') {
                        // prim-to-prim cast
                        emitPrimCast(rn.type, rtype);
                    } else {
                        // ref-to-prim cast ("unboxing")
                        throw new InternalError("no ref-to-prim (unboxing) casts supported right now");
                    }
                }
            }

            // generate actual return statement
            emitReturnInsn(invokerType.returnType());
        }
    }

    /**
     * Emit a type conversion bytecode casting from "from" to "to".
     */
    private void emitPrimCast(char from, char to) {
        // Here's how.
        // -   indicates forbidden
        // <-> indicates implicit
        //      to ----> boolean  byte     short    char     int      long     float    double
        // from boolean    <->        -        -        -        -        -        -        -
        //      byte        -       <->       i2s      i2c      <->      i2l      i2f      i2d
        //      short       -       i2b       <->      i2c      <->      i2l      i2f      i2d
        //      char        -       i2b       i2s      <->      <->      i2l      i2f      i2d
        //      int         -       i2b       i2s      i2c      <->      i2l      i2f      i2d
        //      long        -     l2i,i2b   l2i,i2s  l2i,i2c    l2i      <->      l2f      l2d
        //      float       -     f2i,i2b   f2i,i2s  f2i,i2c    f2i      f2l      <->      f2d
        //      double      -     d2i,i2b   d2i,i2s  d2i,i2c    d2i      d2l      d2f      <->
        if (from == to) {
            // no cast required, should be dead code anyway
            return;
        }
        Wrapper wfrom = Wrapper.forBasicType(from);
        Wrapper wto   = Wrapper.forBasicType(to);
        if (wfrom.isSubwordOrInt()) {
            // cast from {byte,short,char,int} to anything
            emitI2X(to);
        } else {
            // cast from {long,float,double} to anything
            if (wto.isSubwordOrInt()) {
                // cast to {byte,short,char,int}
                emitX2I(from);
                if (wto.bitWidth() < 32) {
                    // targets other than int require another conversion
                    emitI2X(to);
                }
            } else {
                // cast to {long,float,double} - this is verbose
                boolean error = false;
                switch (from) {
                case 'J':
                         if (to == 'F') { mv.visitInsn(Opcodes.L2F); }
                    else if (to == 'D') { mv.visitInsn(Opcodes.L2D); }
                    else error = true;
                    break;
                case 'F':
                         if (to == 'J') { mv.visitInsn(Opcodes.F2L); }
                    else if (to == 'D') { mv.visitInsn(Opcodes.F2D); }
                    else error = true;
                    break;
                case 'D':
                         if (to == 'J') { mv.visitInsn(Opcodes.D2L); }
                    else if (to == 'F') { mv.visitInsn(Opcodes.D2F); }
                    else error = true;
                    break;
                default:
                    error = true;
                    break;
                }
                if (error) {
                    throw new IllegalStateException("unhandled prim cast: " + from + "2" + to);
                }
            }
        }
    }

    private void emitI2X(char type) {
        switch (type) {
        case 'B':  mv.visitInsn(Opcodes.I2B);  break;
        case 'S':  mv.visitInsn(Opcodes.I2S);  break;
        case 'C':  mv.visitInsn(Opcodes.I2C);  break;
        case 'I':  /* naught */                break;
        case 'J':  mv.visitInsn(Opcodes.I2L);  break;
        case 'F':  mv.visitInsn(Opcodes.I2F);  break;
        case 'D':  mv.visitInsn(Opcodes.I2D);  break;
        case 'Z':
            // For compatibility with ValueConversions and explicitCastArguments:
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitInsn(Opcodes.IAND);
            break;
        default:   throw new InternalError("unknown type: " + type);
        }
    }

    private void emitX2I(char type) {
        switch (type) {
        case 'J':  mv.visitInsn(Opcodes.L2I);  break;
        case 'F':  mv.visitInsn(Opcodes.F2I);  break;
        case 'D':  mv.visitInsn(Opcodes.D2I);  break;
        default:   throw new InternalError("unknown type: " + type);
        }
    }

    private static String basicTypeCharSignature(String prefix, MethodType type) {
        StringBuilder buf = new StringBuilder(prefix);
        for (Class<?> ptype : type.parameterList())
            buf.append(Wrapper.forBasicType(ptype).basicTypeChar());
        buf.append('_').append(Wrapper.forBasicType(type.returnType()).basicTypeChar());
        return buf.toString();
    }

    /**
     * Generate bytecode for a LambdaForm.vmentry which calls interpretWithArguments.
     */
    static MemberName generateLambdaFormInterpreterEntryPoint(String sig) {
        assert(LambdaForm.isValidSignature(sig));
        //System.out.println("generateExactInvoker "+sig);
        // compute method type
        // first parameter and return type
        char tret = LambdaForm.signatureReturn(sig);
        MethodType type = MethodType.methodType(LambdaForm.typeClass(tret), MethodHandle.class);
        // other parameter types
        int arity = LambdaForm.signatureArity(sig);
        for (int i = 1; i < arity; i++) {
            type = type.appendParameterTypes(LambdaForm.typeClass(sig.charAt(i)));
        }
        InvokerBytecodeGenerator g = new InvokerBytecodeGenerator("LFI", "interpret_"+tret, type);
        return g.loadMethod(g.generateLambdaFormInterpreterEntryPointBytes());
    }

    private byte[] generateLambdaFormInterpreterEntryPointBytes() {
        classFilePrologue();

        // Suppress this method in backtraces displayed to the user.
        mv.visitAnnotation("Ljava/lang/invoke/LambdaForm$Hidden;", true);

        // Don't inline the interpreter entry.
        mv.visitAnnotation("Ljava/lang/invoke/DontInline;", true);

        // create parameter array
        emitIconstInsn(invokerType.parameterCount());
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");

        // fill parameter array
        for (int i = 0; i < invokerType.parameterCount(); i++) {
            Class<?> ptype = invokerType.parameterType(i);
            mv.visitInsn(Opcodes.DUP);
            emitIconstInsn(i);
            emitLoadInsn(Wrapper.basicTypeChar(ptype), i);
            // box if primitive type
            if (ptype.isPrimitive()) {
                emitBoxing(ptype);
            }
            mv.visitInsn(Opcodes.AASTORE);
        }
        // invoke
        emitAloadInsn(0);
        mv.visitFieldInsn(Opcodes.GETFIELD, MH, "form", "Ljava/lang/invoke/LambdaForm;");
        mv.visitInsn(Opcodes.SWAP);  // swap form and array; avoid local variable
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, LF, "interpretWithArguments", "([Ljava/lang/Object;)Ljava/lang/Object;");

        // maybe unbox
        Class<?> rtype = invokerType.returnType();
        if (rtype.isPrimitive() && rtype != void.class) {
            emitUnboxing(Wrapper.asWrapperType(rtype));
        }

        // return statement
        emitReturnInsn(rtype);

        classFileEpilogue();
        bogusMethod(invokerType);

        final byte[] classFile = cw.toByteArray();
        maybeDump(className, classFile);
        return classFile;
    }

    /**
     * Generate bytecode for a NamedFunction invoker.
     */
    static MemberName generateNamedFunctionInvoker(MethodTypeForm typeForm) {
        MethodType invokerType = LambdaForm.NamedFunction.INVOKER_METHOD_TYPE;
        String invokerName = basicTypeCharSignature("invoke_", typeForm.erasedType());
        InvokerBytecodeGenerator g = new InvokerBytecodeGenerator("NFI", invokerName, invokerType);
        return g.loadMethod(g.generateNamedFunctionInvokerImpl(typeForm));
    }

    static int nfi = 0;

    private byte[] generateNamedFunctionInvokerImpl(MethodTypeForm typeForm) {
        MethodType dstType = typeForm.erasedType();
        classFilePrologue();

        // Suppress this method in backtraces displayed to the user.
        mv.visitAnnotation("Ljava/lang/invoke/LambdaForm$Hidden;", true);

        // Force inlining of this invoker method.
        mv.visitAnnotation("Ljava/lang/invoke/ForceInline;", true);

        // Load receiver
        emitAloadInsn(0);

        // Load arguments from array
        for (int i = 0; i < dstType.parameterCount(); i++) {
            emitAloadInsn(1);
            emitIconstInsn(i);
            mv.visitInsn(Opcodes.AALOAD);

            // Maybe unbox
            Class<?> dptype = dstType.parameterType(i);
            if (dptype.isPrimitive()) {
                Class<?> sptype = dstType.basicType().wrap().parameterType(i);
                Wrapper dstWrapper = Wrapper.forBasicType(dptype);
                Wrapper srcWrapper = dstWrapper.isSubwordOrInt() ? Wrapper.INT : dstWrapper;  // narrow subword from int
                emitUnboxing(srcWrapper.wrapperType());
                emitPrimCast(srcWrapper.basicTypeChar(), dstWrapper.basicTypeChar());
            }
        }

        // Invoke
        String targetDesc = dstType.basicType().toMethodDescriptorString();
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MH, "invokeBasic", targetDesc);

        // Box primitive types
        Class<?> rtype = dstType.returnType();
        if (rtype != void.class && rtype.isPrimitive()) {
            Wrapper srcWrapper = Wrapper.forBasicType(rtype);
            Wrapper dstWrapper = srcWrapper.isSubwordOrInt() ? Wrapper.INT : srcWrapper;  // widen subword to int
            // boolean casts not allowed
            emitPrimCast(srcWrapper.basicTypeChar(), dstWrapper.basicTypeChar());
            emitBoxing(dstWrapper.primitiveType());
        }

        // If the return type is void we return a null reference.
        if (rtype == void.class) {
            mv.visitInsn(Opcodes.ACONST_NULL);
        }
        emitReturnInsn(Object.class);  // NOTE: NamedFunction invokers always return a reference value.

        classFileEpilogue();
        bogusMethod(dstType);

        final byte[] classFile = cw.toByteArray();
        maybeDump(className, classFile);
        return classFile;
    }

    /**
     * Emit a bogus method that just loads some string constants. This is to get the constants into the constant pool
     * for debugging purposes.
     */
    private void bogusMethod(Object... os) {
        if (DUMP_CLASS_FILES) {
            mv = cw.visitMethod(Opcodes.ACC_STATIC, "dummy", "()V", null, null);
            for (Object o : os) {
                mv.visitLdcInsn(o.toString());
                mv.visitInsn(Opcodes.POP);
            }
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
    }
}
