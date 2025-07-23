/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.constant.ClassOrInterfaceDescImpl;
import jdk.internal.constant.ConstantUtils;
import sun.invoke.util.VerifyAccess;
import sun.invoke.util.VerifyType;
import sun.invoke.util.Wrapper;

import java.lang.classfile.*;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.LambdaForm.BasicType;
import java.lang.invoke.LambdaForm.Name;
import java.lang.invoke.LambdaForm.NamedFunction;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import jdk.internal.constant.MethodTypeDescImpl;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;
import static java.lang.invoke.LambdaForm.*;
import static java.lang.invoke.LambdaForm.BasicType.*;
import static java.lang.invoke.MethodHandleNatives.Constants.*;
import static java.lang.invoke.MethodHandleStatics.*;
import static java.lang.invoke.MethodHandles.Lookup.IMPL_LOOKUP;
import static jdk.internal.constant.ConstantUtils.validateInternalClassName;

/**
 * Code generation backend for LambdaForm.
 * <p>
 * @author John Rose, JSR 292 EG
 */
class InvokerBytecodeGenerator {
    /** Define class names for convenience. */
    private static final ClassDesc CD_CasesHolder = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/invoke/MethodHandleImpl$CasesHolder;");
    private static final ClassDesc CD_DirectMethodHandle = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/invoke/DirectMethodHandle;");
    private static final ClassDesc CD_MemberName = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/invoke/MemberName;");
    private static final ClassDesc CD_MethodHandleImpl = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/invoke/MethodHandleImpl;");
    private static final ClassDesc CD_LambdaForm = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/invoke/LambdaForm;");
    private static final ClassDesc CD_LambdaForm_Name = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/invoke/LambdaForm$Name;");
    private static final ClassDesc CD_LoopClauses = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/invoke/MethodHandleImpl$LoopClauses;");
    private static final ClassDesc CD_Object_array = ConstantUtils.CD_Object_array;
    private static final ClassDesc CD_MethodHandle_array = CD_MethodHandle.arrayType();
    private static final ClassDesc CD_MethodHandle_array2 = CD_MethodHandle_array.arrayType();

    private static final MethodTypeDesc MTD_boolean_Object = MethodTypeDescImpl.ofValidated(CD_boolean, CD_Object);
    private static final MethodTypeDesc MTD_Object_int = MethodTypeDescImpl.ofValidated(CD_Object, CD_int);
    private static final MethodTypeDesc MTD_Object_Class = MethodTypeDescImpl.ofValidated(CD_Object, CD_Class);
    private static final MethodTypeDesc MTD_Object_Object = MethodTypeDescImpl.ofValidated(CD_Object, CD_Object);

    private static final String CLASS_PREFIX = "java/lang/invoke/LambdaForm$";
    private static final String SOURCE_PREFIX = "LambdaForm$";

    /** Name of its super class*/
    static final ClassDesc INVOKER_SUPER_DESC = CD_Object;

    /** Name of new class */
    private final String name;
    private final String className;
    private final ConstantPoolBuilder pool = ConstantPoolBuilder.of();
    private final ClassEntry classEntry;

    private final LambdaForm lambdaForm;
    private final String     invokerName;
    private final MethodType invokerType;

    /** Info about local variables in compiled lambda form */
    private int[]       localsMap;    // index
    private Class<?>[]  localClasses; // type

    private final List<ClassData> classData = new ArrayList<>();

    private static final MemberName.Factory MEMBERNAME_FACTORY = MemberName.getFactory();
    private static final Class<?> HOST_CLASS = LambdaForm.class;
    private static final MethodHandles.Lookup LOOKUP = lookup();

    private static MethodHandles.Lookup lookup() {
        try {
            return MethodHandles.privateLookupIn(HOST_CLASS, IMPL_LOOKUP);
        } catch (IllegalAccessException e) {
            throw newInternalError(e);
        }
    }

    /** Main constructor; other constructors delegate to this one. */
    private InvokerBytecodeGenerator(LambdaForm lambdaForm, int localsMapSize,
                                     String name, String invokerName, MethodType invokerType) {
        int p = invokerName.indexOf('.');
        if (p > -1) {
            name = invokerName.substring(0, p);
            invokerName = invokerName.substring(p + 1);
        }
        if (dumper().isEnabled()) {
            name = makeDumpableClassName(name);
        }
        this.name = name;
        this.className = CLASS_PREFIX.concat(name);
        validateInternalClassName(name);
        this.classEntry = pool.classEntry(ConstantUtils.internalNameToDesc(className));
        this.lambdaForm = lambdaForm;
        this.invokerName = invokerName;
        this.invokerType = invokerType;
        this.localsMap = new int[localsMapSize+1]; // last entry of localsMap is count of allocated local slots
        this.localClasses = new Class<?>[localsMapSize+1];
    }

    /** For generating LambdaForm interpreter entry points. */
    private InvokerBytecodeGenerator(String name, String invokerName, MethodType invokerType) {
        this(null, invokerType.parameterCount(),
             name, invokerName, invokerType);
        MethodType mt = invokerType.erase();
        // Create an array to map name indexes to locals indexes.
        localsMap[0] = 0; // localsMap has at least one element
        for (int i = 1, index = 0; i < localsMap.length; i++) {
            Class<?> cl = mt.parameterType(i - 1);
            index += (cl == long.class || cl == double.class) ? 2 : 1;
            localsMap[i] = index;
        }
    }

    /** For generating customized code for a single LambdaForm. */
    private InvokerBytecodeGenerator(String name, LambdaForm form, MethodType invokerType) {
        this(name, form.lambdaName(), form, invokerType);
    }

    /** For generating customized code for a single LambdaForm. */
    InvokerBytecodeGenerator(String name, String invokerName,
            LambdaForm form, MethodType invokerType) {
        this(form, form.names.length,
             name, invokerName, invokerType);
        // Create an array to map name indexes to locals indexes.
        Name[] names = form.names;
        for (int i = 0, index = 0; i < localsMap.length; i++) {
            localsMap[i] = index;
            if (i < names.length) {
                BasicType type = names[i].type();
                index += type.basicTypeSlots();
            }
        }
    }

    /** instance counters for dumped classes */
    private static final HashMap<String,Integer> DUMP_CLASS_FILES_COUNTERS =
            dumper().isEnabled() ?  new HashMap<>(): null;

    private static String makeDumpableClassName(String className) {
        Integer ctr;
        synchronized (DUMP_CLASS_FILES_COUNTERS) {
            ctr = DUMP_CLASS_FILES_COUNTERS.get(className);
            if (ctr == null)  ctr = 0;
            DUMP_CLASS_FILES_COUNTERS.put(className, ctr+1);
        }

        var buf = new StringBuilder(className.length() + 3).append(className);
        int ctrVal = ctr;
        if (ctrVal < 10) {
            buf.repeat('0', 2);
        } else if (ctrVal < 100) {
            buf.append('0');
        }
        buf.append(ctrVal);
        return buf.toString();
    }

    record ClassData(FieldRefEntry field, Object value) {}

    FieldRefEntry classData(ClassFileBuilder<?, ?> cfb, Object arg, ClassDesc desc) {
        // unique static variable name
        String name;
        List<ClassData> classData = this.classData;
        if (dumper().isEnabled()) {
            Class<?> c = arg.getClass();
            while (c.isArray()) {
                c = c.getComponentType();
            }
            name = "_DATA_" + c.getSimpleName() + "_" + classData.size();
        } else {
            name = "_D_" + classData.size();
        }
        var field = pool.fieldRefEntry(classEntry, pool.nameAndTypeEntry(name, desc));
        classData.add(new ClassData(field, arg));
        return field;
    }

    /**
     * Extract the MemberName of a newly-defined method.
     */
    private MemberName loadMethod(byte[] classFile) {
        Class<?> invokerClass = LOOKUP.makeHiddenClassDefiner(className, classFile, dumper())
                                      .defineClass(true, classDataValues());
        return resolveInvokerMember(invokerClass, invokerName, invokerType);
    }

    private static MemberName resolveInvokerMember(Class<?> invokerClass, String name, MethodType type) {
        MemberName member = new MemberName(invokerClass, name, type, REF_invokeStatic);
        try {
            member = MEMBERNAME_FACTORY.resolveOrFail(REF_invokeStatic, member,
                                                      HOST_CLASS, LM_TRUSTED,
                                                      ReflectiveOperationException.class);
        } catch (ReflectiveOperationException e) {
            throw newInternalError(e);
        }
        return member;
    }

    /**
     * Set up class file generation.
     */
    private byte[] classFileSetup(Consumer<? super ClassBuilder> config) {
        try {
            return ClassFile.of().build(classEntry, pool, new Consumer<>() {
                @Override
                public void accept(ClassBuilder clb) {
                    clb.withFlags(ACC_FINAL | ACC_SUPER)
                       .withSuperclass(INVOKER_SUPER_DESC)
                       .with(SourceFileAttribute.of(clb.constantPool().utf8Entry(SOURCE_PREFIX + name)));
                    config.accept(clb);
                }
            });
        } catch (RuntimeException e) {
            throw new BytecodeGenerationException(e);
        }
    }

    private void methodSetup(ClassBuilder clb, Consumer<? super MethodBuilder> config) {
        var invokerDesc = methodDesc(invokerType);
        clb.withMethod(invokerName, invokerDesc, ACC_STATIC, config);
    }

    /**
     * Returns the class data object that will be passed to `Lookup.defineHiddenClassWithClassData`.
     * The classData is loaded in the <clinit> method of the generated class.
     * If the class data contains only one single object, this method returns  that single object.
     * If the class data contains more than one objects, this method returns a List.
     *
     * This method returns null if no class data.
     */
    private Object classDataValues() {
        final List<ClassData> cd = classData;
        int size = cd.size();
        return switch (size) {
            case 0 -> null;             // special case (classData is not used by <clinit>)
            case 1 -> cd.get(0).value;  // special case (single object)
            case 2 -> List.of(cd.get(0).value, cd.get(1).value);
            case 3 -> List.of(cd.get(0).value, cd.get(1).value, cd.get(2).value);
            case 4 -> List.of(cd.get(0).value, cd.get(1).value, cd.get(2).value, cd.get(3).value);
            default -> {
                Object[] data = new Object[size];
                for (int i = 0; i < size; i++) {
                    data[i] = classData.get(i).value;
                }
                yield List.of(data);
            }
        };
    }

    /*
     * <clinit> to initialize the static final fields with the live class data
     * LambdaForms can't use condy due to bootstrapping issue.
     */
    static void clinit(ClassBuilder clb, ClassEntry classEntry, List<ClassData> classData) {
        if (classData.isEmpty())
            return;

        clb.withMethodBody(CLASS_INIT_NAME, MTD_void, ACC_STATIC, new Consumer<>() {
            @Override
            public void accept(CodeBuilder cob) {
                cob.ldc(classEntry)
                   .invokestatic(CD_MethodHandles, "classData", MTD_Object_Class);
                int size = classData.size();
                if (size == 1) {
                    var field = classData.getFirst().field;
                    // add the static field
                    clb.withField(field.name(), field.type(), ACC_STATIC | ACC_FINAL);

                    var ft = field.typeSymbol();
                    if (ft != CD_Object)
                        cob.checkcast(ft);
                    cob.putstatic(field);
                } else {
                    cob.checkcast(CD_List)
                       .astore(0);
                    int index = 0;
                    var listGet = cob.constantPool().interfaceMethodRefEntry(CD_List, "get", MTD_Object_int);
                    for (int i = 0; i < size; i++) {
                        var field = classData.get(i).field;
                        // add the static field
                        clb.withField(field.name(), field.type(), ACC_STATIC | ACC_FINAL);
                        // initialize the static field
                        cob.aload(0)
                           .loadConstant(index++)
                           .invokeinterface(listGet);
                        var ft = field.typeSymbol();
                        if (ft != CD_Object)
                            cob.checkcast(ft);
                        cob.putstatic(field);
                    }
                }
                cob.return_();
            }
        });
    }

    private void emitLoadInsn(CodeBuilder cob, TypeKind type, int index) {
        cob.loadLocal(type, localsMap[index]);
    }

    private void emitStoreInsn(CodeBuilder cob, TypeKind type, int index) {
        cob.storeLocal(type, localsMap[index]);
    }

    /**
     * Emit a boxing call.
     */
    private void emitBoxing(CodeBuilder cob, TypeKind tk) {
        TypeConvertingMethodAdapter.box(cob, tk);
    }

    /**
     * Emit an unboxing call (plus preceding checkcast).
     */
    private void emitUnboxing(CodeBuilder cob, TypeKind target) {
        switch (target) {
            case BOOLEAN -> emitReferenceCast(cob, Boolean.class, null);
            case CHAR -> emitReferenceCast(cob, Character.class, null);
            case BYTE, DOUBLE, FLOAT, INT, LONG, SHORT ->
                emitReferenceCast(cob, Number.class, null);
            default -> {}
        }
        TypeConvertingMethodAdapter.unbox(cob, target);
    }

    /**
     * Emit an implicit conversion for an argument which must be of the given pclass.
     * This is usually a no-op, except when pclass is a subword type or a reference other than Object or an interface.
     *
     * @param ptype type of value present on stack
     * @param pclass type of value required on stack
     * @param arg compile-time representation of value on stack (Node, constant) or null if none
     */
    private void emitImplicitConversion(CodeBuilder cob, BasicType ptype, Class<?> pclass, Object arg) {
        assert(basicType(pclass) == ptype);  // boxing/unboxing handled by caller
        if (pclass == ptype.basicTypeClass() && ptype != L_TYPE)
            return;   // nothing to do
        switch (ptype) {
            case L_TYPE:
                if (VerifyType.isNullConversion(Object.class, pclass, false)) {
                    if (PROFILE_LEVEL > 0)
                        emitReferenceCast(cob, Object.class, arg);
                    return;
                }
                emitReferenceCast(cob, pclass, arg);
                return;
            case I_TYPE:
                if (!VerifyType.isNullConversion(int.class, pclass, false))
                    emitPrimCast(cob, ptype.basicTypeKind(), TypeKind.from(pclass));
                return;
        }
        throw newInternalError("bad implicit conversion: tc="+ptype+": "+pclass);
    }

    /** Update localClasses type map.  Return true if the information is already present. */
    private boolean assertStaticType(Class<?> cls, Name n) {
        int local = n.index();
        Class<?> aclass = localClasses[local];
        if (aclass != null && (aclass == cls || cls.isAssignableFrom(aclass))) {
            return true;  // type info is already present
        } else if (aclass == null || aclass.isAssignableFrom(cls)) {
            localClasses[local] = cls;  // type info can be improved
        }
        return false;
    }

    private void emitReferenceCast(CodeBuilder cob, Class<?> cls, Object arg) {
        Name writeBack = null;  // local to write back result
        if (arg instanceof Name n) {
            if (lambdaForm.useCount(n) > 1) {
                // This guy gets used more than once.
                writeBack = n;
                if (assertStaticType(cls, n)) {
                    return; // this cast was already performed
                }
            }
        }
        if (isStaticallyNameable(cls)) {
            ClassDesc sig = classDesc(cls);
            cob.checkcast(sig);
        } else {
            cob.getstatic(classData(cob, cls, CD_Class))
               .swap()
               .invokevirtual(CD_Class, "cast", MTD_Object_Object);
            if (Object[].class.isAssignableFrom(cls))
                cob.checkcast(CD_Object_array);
            else if (PROFILE_LEVEL > 0)
                cob.checkcast(CD_Object);
        }
        if (writeBack != null) {
            cob.dup();
            emitStoreInsn(cob, TypeKind.REFERENCE, writeBack.index());
        }
    }

    private static MemberName resolveFrom(String name, MethodType type, Class<?> holder) {
        assert(!UNSAFE.shouldBeInitialized(holder)) : holder + "not initialized";
        MemberName member = new MemberName(holder, name, type, REF_invokeStatic);
        MemberName resolvedMember = MemberName.getFactory().resolveOrNull(REF_invokeStatic, member, holder, LM_TRUSTED);
        traceLambdaForm(name, type, holder, resolvedMember);
        return resolvedMember;
    }

    private static MemberName lookupPregenerated(LambdaForm form, MethodType invokerType) {
        if (form.customized != null) {
            // No pre-generated version for customized LF
            return null;
        }
        String name = form.kind.methodName;
        switch (form.kind) {
            case BOUND_REINVOKER: {
                name = name + "_" + BoundMethodHandle.speciesDataFor(form).key();
                return resolveFrom(name, invokerType, DelegatingMethodHandle.Holder.class);
            }
            case DELEGATE:                  return resolveFrom(name, invokerType, DelegatingMethodHandle.Holder.class);
            case IDENTITY:                  // fall-through
            case CONSTANT: {
                name = name + "_" + form.returnType().basicTypeChar();
                return resolveFrom(name, invokerType, LambdaForm.Holder.class);
            }
            case EXACT_INVOKER:             // fall-through
            case EXACT_LINKER:              // fall-through
            case LINK_TO_CALL_SITE:         // fall-through
            case LINK_TO_TARGET_METHOD:     // fall-through
            case GENERIC_INVOKER:           // fall-through
            case GENERIC_LINKER:            return resolveFrom(name, invokerType, Invokers.Holder.class);
            case FIELD_ACCESS:              // fall-through
            case FIELD_ACCESS_INIT:         // fall-through
            case VOLATILE_FIELD_ACCESS:     // fall-through
            case VOLATILE_FIELD_ACCESS_INIT:// fall-through
            case FIELD_ACCESS_B:              // fall-through
            case FIELD_ACCESS_INIT_B:         // fall-through
            case VOLATILE_FIELD_ACCESS_B:     // fall-through
            case VOLATILE_FIELD_ACCESS_INIT_B:// fall-through
            case FIELD_ACCESS_C:              // fall-through
            case FIELD_ACCESS_INIT_C:         // fall-through
            case VOLATILE_FIELD_ACCESS_C:     // fall-through
            case VOLATILE_FIELD_ACCESS_INIT_C:// fall-through
            case FIELD_ACCESS_S:              // fall-through
            case FIELD_ACCESS_INIT_S:         // fall-through
            case VOLATILE_FIELD_ACCESS_S:     // fall-through
            case VOLATILE_FIELD_ACCESS_INIT_S:// fall-through
            case FIELD_ACCESS_Z:              // fall-through
            case FIELD_ACCESS_INIT_Z:         // fall-through
            case VOLATILE_FIELD_ACCESS_Z:     // fall-through
            case VOLATILE_FIELD_ACCESS_INIT_Z:// fall-through
            case FIELD_ACCESS_CAST:              // fall-through
            case FIELD_ACCESS_INIT_CAST:         // fall-through
            case VOLATILE_FIELD_ACCESS_CAST:     // fall-through
            case VOLATILE_FIELD_ACCESS_INIT_CAST:// fall-through
            case DIRECT_NEW_INVOKE_SPECIAL: // fall-through
            case DIRECT_INVOKE_INTERFACE:   // fall-through
            case DIRECT_INVOKE_SPECIAL:     // fall-through
            case DIRECT_INVOKE_SPECIAL_IFC: // fall-through
            case DIRECT_INVOKE_STATIC:      // fall-through
            case DIRECT_INVOKE_STATIC_INIT: // fall-through
            case DIRECT_INVOKE_VIRTUAL:     return resolveFrom(name, invokerType, DirectMethodHandle.Holder.class);
        }
        return null;
    }

    /**
     * Generate customized bytecode for a given LambdaForm.
     */
    static MemberName generateCustomizedCode(LambdaForm form, MethodType invokerType) {
        MemberName pregenerated = lookupPregenerated(form, invokerType);
        if (pregenerated != null)  return pregenerated; // pre-generated bytecode

        InvokerBytecodeGenerator g = new InvokerBytecodeGenerator("MH", form, invokerType);
        return g.loadMethod(g.generateCustomizedCodeBytes());
    }

    /** Generates code to check that actual receiver and LambdaForm matches */
    private boolean checkActualReceiver(CodeBuilder cob) {
        // Expects MethodHandle on the stack and actual receiver MethodHandle in slot #0
        cob.dup()
           .aload(0)
           .invokestatic(CD_MethodHandleImpl, "assertSame", MethodTypeDescImpl.ofValidated(CD_void, CD_Object, CD_Object));
        return true;
    }

    static final Annotation DONTINLINE      = Annotation.of(ClassOrInterfaceDescImpl.ofValidated("Ljdk/internal/vm/annotation/DontInline;"));
    static final Annotation FORCEINLINE     = Annotation.of(ClassOrInterfaceDescImpl.ofValidated("Ljdk/internal/vm/annotation/ForceInline;"));
    static final Annotation HIDDEN          = Annotation.of(ClassOrInterfaceDescImpl.ofValidated("Ljdk/internal/vm/annotation/Hidden;"));
    static final Annotation INJECTEDPROFILE = Annotation.of(ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/invoke/InjectedProfile;"));
    static final Annotation LF_COMPILED     = Annotation.of(ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/invoke/LambdaForm$Compiled;"));

    // Suppress method in backtraces displayed to the user, mark this method as
    // a compiled LambdaForm, then either force or prohibit inlining.
    public static final RuntimeVisibleAnnotationsAttribute LF_DONTINLINE_ANNOTATIONS = RuntimeVisibleAnnotationsAttribute.of(HIDDEN, LF_COMPILED, DONTINLINE);
    public static final RuntimeVisibleAnnotationsAttribute LF_DONTINLINE_PROFILE_ANNOTATIONS = RuntimeVisibleAnnotationsAttribute.of(HIDDEN, LF_COMPILED, DONTINLINE, INJECTEDPROFILE);
    public static final RuntimeVisibleAnnotationsAttribute LF_FORCEINLINE_ANNOTATIONS = RuntimeVisibleAnnotationsAttribute.of(HIDDEN, LF_COMPILED, FORCEINLINE);
    public static final RuntimeVisibleAnnotationsAttribute LF_FORCEINLINE_PROFILE_ANNOTATIONS = RuntimeVisibleAnnotationsAttribute.of(HIDDEN, LF_COMPILED, FORCEINLINE, INJECTEDPROFILE);

    /**
     * Generate an invoker method for the passed {@link LambdaForm}.
     */
    private byte[] generateCustomizedCodeBytes() {
        final byte[] classFile = classFileSetup(new Consumer<>() {
            @Override
            public void accept(ClassBuilder clb) {
                addMethod(clb, true);
                clinit(clb, classEntry, classData);
                bogusMethod(clb, lambdaForm);
            }
        });
        return classFile;
    }

    void addMethod(ClassBuilder clb, boolean alive) {
        methodSetup(clb, new Consumer<>() {
            @Override
            public void accept(MethodBuilder mb) {

                if (lambdaForm.forceInline) {
                    mb.accept(LF_FORCEINLINE_ANNOTATIONS);
                } else {
                    mb.accept(LF_DONTINLINE_ANNOTATIONS);
                }

                if (alive) {
                    classData(mb, lambdaForm, CD_LambdaForm); // keep LambdaForm instance & its compiled form lifetime tightly coupled.
                }

                mb.withCode(new Consumer<>() {
                    @Override
                    public void accept(CodeBuilder cob) {
                        if (lambdaForm.customized != null) {
                            // Since LambdaForm is customized for a particular MethodHandle, it's safe to substitute
                            // receiver MethodHandle (at slot #0) with an embedded constant and use it instead.
                            // It enables more efficient code generation in some situations, since embedded constants
                            // are compile-time constants for JIT compiler.
                            cob.getstatic(classData(cob, lambdaForm.customized, CD_MethodHandle));
                            assert(checkActualReceiver(cob)); // expects MethodHandle on top of the stack
                            cob.astore(0);
                        }

                        // iterate over the form's names, generating bytecode instructions for each
                        // start iterating at the first name following the arguments
                        Name onStack = null;
                        for (int i = lambdaForm.arity; i < lambdaForm.names.length; i++) {
                            Name name = lambdaForm.names[i];

                            emitStoreResult(cob, onStack);
                            onStack = name;  // unless otherwise modified below
                            MethodHandleImpl.Intrinsic intr = name.function.intrinsicName();
                            switch (intr) {
                                case SELECT_ALTERNATIVE:
                                    assert lambdaForm.isSelectAlternative(i);
                                    if (PROFILE_GWT) {
                                        assert(name.arguments[0] instanceof Name n &&
                                                n.refersTo(MethodHandleImpl.class, "profileBoolean"));
                                        if (lambdaForm.forceInline) {
                                            mb.with(LF_FORCEINLINE_PROFILE_ANNOTATIONS);
                                        } else {
                                            mb.with(LF_DONTINLINE_PROFILE_ANNOTATIONS);
                                        }
                                    }
                                    onStack = emitSelectAlternative(cob, name, lambdaForm.names[i+1]);
                                    i++;  // skip MH.invokeBasic of the selectAlternative result
                                    continue;
                                case GUARD_WITH_CATCH:
                                    assert lambdaForm.isGuardWithCatch(i);
                                    onStack = emitGuardWithCatch(cob, i);
                                    i += 2; // jump to the end of GWC idiom
                                    continue;
                                case TRY_FINALLY:
                                    assert lambdaForm.isTryFinally(i);
                                    onStack = emitTryFinally(cob, i);
                                    i += 2; // jump to the end of the TF idiom
                                    continue;
                                case TABLE_SWITCH:
                                    assert lambdaForm.isTableSwitch(i);
                                    int numCases = (Integer) name.function.intrinsicData();
                                    onStack = emitTableSwitch(cob, i, numCases);
                                    i += 2; // jump to the end of the TS idiom
                                    continue;
                                case LOOP:
                                    assert lambdaForm.isLoop(i);
                                    onStack = emitLoop(cob, i);
                                    i += 2; // jump to the end of the LOOP idiom
                                    continue;
                                case ARRAY_LOAD:
                                    emitArrayLoad(cob, name);
                                    continue;
                                case ARRAY_STORE:
                                    emitArrayStore(cob, name);
                                    continue;
                                case ARRAY_LENGTH:
                                    emitArrayLength(cob, name);
                                    continue;
                                case IDENTITY:
                                    assert(name.arguments.length == 1);
                                    emitPushArguments(cob, name, 0);
                                    continue;
                                case NONE:
                                    // no intrinsic associated
                                    break;
                                default:
                                    throw newInternalError("Unknown intrinsic: "+intr);
                            }

                            MemberName member = name.function.member();
                            if (isStaticallyInvocable(member)) {
                                emitStaticInvoke(cob, member, name);
                            } else {
                                emitInvoke(cob, name);
                            }
                        }

                        // return statement
                        emitReturn(cob, onStack);
                    }
                });
            }
        });
    }

    /**
     * The BytecodeGenerationException.
     */
    @SuppressWarnings("serial")
    static final class BytecodeGenerationException extends RuntimeException {
        BytecodeGenerationException(Exception cause) {
            super(cause);
        }
    }

    void emitArrayLoad(CodeBuilder cob, Name name)   {
        Class<?> elementType = name.function.methodType().parameterType(0).getComponentType();
        assert elementType != null;
        emitPushArguments(cob, name, 0);
        if (elementType.isPrimitive()) {
            cob.arrayLoad(TypeKind.from(elementType));
        } else {
            cob.aaload();
        }
    }

    void emitArrayStore(CodeBuilder cob, Name name)  {
        Class<?> elementType = name.function.methodType().parameterType(0).getComponentType();
        assert elementType != null;
        emitPushArguments(cob, name, 0);
        if (elementType.isPrimitive()) {
            cob.arrayStore(TypeKind.from(elementType));
        } else {
            cob.aastore();
        }
    }

    void emitArrayLength(CodeBuilder cob, Name name) {
        assert name.function.methodType().parameterType(0).isArray();
        emitPushArguments(cob, name, 0);
        cob.arraylength();
    }

    /**
     * Emit an invoke for the given name.
     */
    void emitInvoke(CodeBuilder cob, Name name) {
        assert(!name.isLinkerMethodInvoke());  // should use the static path for these
        if (true) {
            // push receiver
            MethodHandle target = name.function.resolvedHandle();
            assert(target != null) : name.exprString();
            cob.getstatic(classData(cob, target, CD_MethodHandle));
            emitReferenceCast(cob, MethodHandle.class, target);
        } else {
            // load receiver
            cob.aload(0);
            emitReferenceCast(cob, MethodHandle.class, null);
            cob.getfield(CD_MethodHandle, "form", CD_LambdaForm)
               .getfield(CD_LambdaForm, "names", CD_LambdaForm_Name);
            // TODO more to come
        }

        // push arguments
        emitPushArguments(cob, name, 0);

        // invocation
        MethodType type = name.function.methodType();
        cob.invokevirtual(CD_MethodHandle, "invokeBasic", methodDesc(type.basicType()));
    }

    private static final Class<?>[] STATICALLY_INVOCABLE_PACKAGES = {
        // Sample classes from each package we are willing to bind to statically:
        java.lang.Object.class,
        java.util.Arrays.class,
        jdk.internal.misc.Unsafe.class
        //MethodHandle.class already covered
    };

    static boolean isStaticallyInvocable(NamedFunction ... functions) {
        for (NamedFunction nf : functions) {
            if (!isStaticallyInvocable(nf.member())) {
                return false;
            }
        }
        return true;
    }

    static boolean isStaticallyInvocable(Name name) {
        return isStaticallyInvocable(name.function.member());
    }

    static boolean isStaticallyInvocable(MemberName member) {
        if (member == null)  return false;
        if (member.isConstructor())  return false;
        Class<?> cls = member.getDeclaringClass();
        // Fast-path non-private members declared by MethodHandles, which is a common
        // case
        if (MethodHandle.class.isAssignableFrom(cls) && !member.isPrivate()) {
            assert(isStaticallyInvocableType(member.getMethodOrFieldType()));
            return true;
        }
        if (cls.isArray() || cls.isPrimitive())
            return false;  // FIXME
        if (cls.isAnonymousClass() || cls.isLocalClass())
            return false;  // inner class of some sort
        if (cls.getClassLoader() != MethodHandle.class.getClassLoader())
            return false;  // not on BCP
        if (cls.isHidden())
            return false;
        if (!isStaticallyInvocableType(member.getMethodOrFieldType()))
            return false;
        if (!member.isPrivate() && VerifyAccess.isSamePackage(MethodHandle.class, cls))
            return true;   // in java.lang.invoke package
        if (member.isPublic() && isStaticallyNameable(cls))
            return true;
        return false;
    }

    private static boolean isStaticallyInvocableType(MethodType mtype) {
        if (!isStaticallyNameable(mtype.returnType()))
            return false;
        for (Class<?> ptype : mtype.ptypes())
            if (!isStaticallyNameable(ptype))
                return false;
        return true;
    }

    static boolean isStaticallyNameable(Class<?> cls) {
        if (cls == Object.class)
            return true;
        if (MethodHandle.class.isAssignableFrom(cls)) {
            assert(!cls.isHidden());
            return true;
        }
        while (cls.isArray())
            cls = cls.getComponentType();
        if (cls.isPrimitive())
            return true;  // int[].class, for example
        if (cls.isHidden())
            return false;
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

    void emitStaticInvoke(CodeBuilder cob, Name name) {
        emitStaticInvoke(cob, name.function.member(), name);
    }

    /**
     * Emit an invoke for the given name, using the MemberName directly.
     */
    void emitStaticInvoke(CodeBuilder cob, MemberName member, Name name) {
        assert(member.equals(name.function.member()));
        Class<?> defc = member.getDeclaringClass();
        ClassDesc cdesc = classDesc(defc);
        String mname = member.getName();
        byte refKind = member.getReferenceKind();
        if (refKind == REF_invokeSpecial) {
            // in order to pass the verifier, we need to convert this to invokevirtual in all cases
            assert(member.canBeStaticallyBound()) : member;
            refKind = REF_invokeVirtual;
        }

        assert(!(member.getDeclaringClass().isInterface() && refKind == REF_invokeVirtual));

        // push arguments
        emitPushArguments(cob, name, 0);

        // invocation
        if (member.isMethod()) {
            var methodTypeDesc = methodDesc(member.getMethodType());
            cob.invoke(refKindOpcode(refKind), cdesc, mname, methodTypeDesc,
                                  member.getDeclaringClass().isInterface());
        } else {
            var fieldTypeDesc = classDesc(member.getFieldType());
            cob.fieldAccess(refKindOpcode(refKind), cdesc, mname, fieldTypeDesc);
        }
        // Issue a type assertion for the result, so we can avoid casts later.
        if (name.type == L_TYPE) {
            Class<?> rtype = member.getInvocationType().returnType();
            assert(!rtype.isPrimitive());
            if (rtype != Object.class && !rtype.isInterface()) {
                assertStaticType(rtype, name);
            }
        }
    }

    Opcode refKindOpcode(byte refKind) {
        switch (refKind) {
        case REF_invokeVirtual:      return Opcode.INVOKEVIRTUAL;
        case REF_invokeStatic:       return Opcode.INVOKESTATIC;
        case REF_invokeSpecial:      return Opcode.INVOKESPECIAL;
        case REF_invokeInterface:    return Opcode.INVOKEINTERFACE;
        case REF_getField:           return Opcode.GETFIELD;
        case REF_putField:           return Opcode.PUTFIELD;
        case REF_getStatic:          return Opcode.GETSTATIC;
        case REF_putStatic:          return Opcode.PUTSTATIC;
        }
        throw new InternalError("refKind="+refKind);
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
    private Name emitSelectAlternative(CodeBuilder cob, Name selectAlternativeName, Name invokeBasicName) {
        assert isStaticallyInvocable(invokeBasicName);

        Name receiver = (Name) invokeBasicName.arguments[0];

        Label L_fallback = cob.newLabel();
        Label L_done     = cob.newLabel();

        // load test result
        emitPushArgument(cob, selectAlternativeName, 0);

        // if_icmpne L_fallback
        cob.ifeq(L_fallback);

        // invoke selectAlternativeName.arguments[1]
        Class<?>[] preForkClasses = localClasses.clone();
        emitPushArgument(cob, selectAlternativeName, 1);  // get 2nd argument of selectAlternative
        emitStoreInsn(cob, TypeKind.REFERENCE, receiver.index());  // store the MH in the receiver slot
        emitStaticInvoke(cob, invokeBasicName);

        // goto L_done
        cob.goto_w(L_done)
           // L_fallback:
           .labelBinding(L_fallback);

        // invoke selectAlternativeName.arguments[2]
        System.arraycopy(preForkClasses, 0, localClasses, 0, preForkClasses.length);
        emitPushArgument(cob, selectAlternativeName, 2);  // get 3rd argument of selectAlternative
        emitStoreInsn(cob, TypeKind.REFERENCE, receiver.index());  // store the MH in the receiver slot
        emitStaticInvoke(cob, invokeBasicName);

        // L_done:
        cob.labelBinding(L_done);
        // for now do not bother to merge typestate; just reset to the dominator state
        System.arraycopy(preForkClasses, 0, localClasses, 0, preForkClasses.length);

        return invokeBasicName;  // return what's on stack
    }

    /**
     * Emit bytecode for the guardWithCatch idiom.
     *
     * The pattern looks like (Cf. MethodHandleImpl.makeGuardWithCatch):
     * <blockquote><pre>{@code
     *  guardWithCatch=Lambda(a0:L,a1:L,a2:L,a3:L,a4:L,a5:L,a6:L,a7:L)=>{
     *    t8:L=MethodHandle.invokeBasic(a4:L,a6:L,a7:L);
     *    t9:L=MethodHandleImpl.guardWithCatch(a1:L,a2:L,a3:L,t8:L);
     *   t10:I=MethodHandle.invokeBasic(a5:L,t9:L);t10:I}
     * }</pre></blockquote>
     *
     * It is compiled into bytecode equivalent of the following code:
     * <blockquote><pre>{@code
     *  try {
     *      return a1.invokeBasic(a6, a7);
     *  } catch (Throwable e) {
     *      if (!a2.isInstance(e)) throw e;
     *      return a3.invokeBasic(ex, a6, a7);
     *  }}</pre></blockquote>
     */
    private Name emitGuardWithCatch(CodeBuilder cob, int pos) {
        Name args    = lambdaForm.names[pos];
        Name invoker = lambdaForm.names[pos+1];
        Name result  = lambdaForm.names[pos+2];

        Label L_startBlock = cob.newLabel();
        Label L_endBlock = cob.newLabel();
        Label L_handler = cob.newLabel();
        Label L_done = cob.newLabel();

        Class<?> returnType = result.function.resolvedHandle().type().returnType();
        MethodType type = args.function.resolvedHandle().type()
                              .dropParameterTypes(0,1)
                              .changeReturnType(returnType);

        cob.exceptionCatch(L_startBlock, L_endBlock, L_handler, CD_Throwable)
           // Normal case
           .labelBinding(L_startBlock);
        // load target
        emitPushArgument(cob, invoker, 0);
        emitPushArguments(cob, args, 1); // skip 1st argument: method handle
        cob.invokevirtual(CD_MethodHandle, "invokeBasic", methodDesc(type.basicType()))
           .labelBinding(L_endBlock)
           .goto_w(L_done)
           // Exceptional case
           .labelBinding(L_handler)
           // Check exception's type
           .dup();
        // load exception class
        emitPushArgument(cob, invoker, 1);
        cob.swap()
           .invokevirtual(CD_Class, "isInstance", MTD_boolean_Object);
        Label L_rethrow = cob.newLabel();
        cob.ifeq(L_rethrow);

        // Invoke catcher
        // load catcher
        emitPushArgument(cob, invoker, 2);
        cob.swap();
        emitPushArguments(cob, args, 1); // skip 1st argument: method handle
        MethodType catcherType = type.insertParameterTypes(0, Throwable.class);
        cob.invokevirtual(CD_MethodHandle, "invokeBasic", methodDesc(catcherType.basicType()))
           .goto_w(L_done)
           .labelBinding(L_rethrow)
           .athrow()
           .labelBinding(L_done);

        return result;
    }

    /**
     * Emit bytecode for the tryFinally idiom.
     * <p>
     * The pattern looks like (Cf. MethodHandleImpl.makeTryFinally):
     * <blockquote><pre>{@code
     * // a0: BMH
     * // a1: target, a2: cleanup
     * // a3: box, a4: unbox
     * // a5 (and following): arguments
     * tryFinally=Lambda(a0:L,a1:L,a2:L,a3:L,a4:L,a5:L)=>{
     *   t6:L=MethodHandle.invokeBasic(a3:L,a5:L);         // box the arguments into an Object[]
     *   t7:L=MethodHandleImpl.tryFinally(a1:L,a2:L,t6:L); // call the tryFinally executor
     *   t8:L=MethodHandle.invokeBasic(a4:L,t7:L);t8:L}    // unbox the result; return the result
     * }</pre></blockquote>
     * <p>
     * It is compiled into bytecode equivalent to the following code:
     * <blockquote><pre>{@code
     * Throwable t;
     * Object r;
     * try {
     *     r = a1.invokeBasic(a5);
     * } catch (Throwable thrown) {
     *     t = thrown;
     *     throw t;
     * } finally {
     *     r = a2.invokeBasic(t, r, a5);
     * }
     * return r;
     * }</pre></blockquote>
     * <p>
     * Specifically, the bytecode will have the following form (the stack effects are given for the beginnings of
     * blocks, and for the situations after executing the given instruction - the code will have a slightly different
     * shape if the return type is {@code void}):
     * <blockquote><pre>{@code
     * TRY:                 (--)
     *                      load target                             (-- target)
     *                      load args                               (-- args... target)
     *                      INVOKEVIRTUAL MethodHandle.invokeBasic  (depends)
     * FINALLY_NORMAL:      (-- r_2nd* r)
     *                      store returned value                    (--)
     *                      load cleanup                            (-- cleanup)
     *                      ACONST_NULL                             (-- t cleanup)
     *                      load returned value                     (-- r_2nd* r t cleanup)
     *                      load args                               (-- args... r_2nd* r t cleanup)
     *                      INVOKEVIRTUAL MethodHandle.invokeBasic  (-- r_2nd* r)
     *                      GOTO DONE
     * CATCH:               (-- t)
     *                      DUP                                     (-- t t)
     * FINALLY_EXCEPTIONAL: (-- t t)
     *                      load cleanup                            (-- cleanup t t)
     *                      SWAP                                    (-- t cleanup t)
     *                      load default for r                      (-- r_2nd* r t cleanup t)
     *                      load args                               (-- args... r_2nd* r t cleanup t)
     *                      INVOKEVIRTUAL MethodHandle.invokeBasic  (-- r_2nd* r t)
     *                      POP/POP2*                               (-- t)
     *                      ATHROW
     * DONE:                (-- r)
     * }</pre></blockquote>
     * * = depends on whether the return type takes up 2 stack slots.
     */
    private Name emitTryFinally(CodeBuilder cob, int pos) {
        Name args    = lambdaForm.names[pos];
        Name invoker = lambdaForm.names[pos+1];
        Name result  = lambdaForm.names[pos+2];

        Label lFrom = cob.newLabel();
        Label lTo = cob.newLabel();
        Label lCatch = cob.newLabel();
        Label lDone = cob.newLabel();

        Class<?> returnType = result.function.resolvedHandle().type().returnType();
        BasicType basicReturnType = BasicType.basicType(returnType);
        boolean isNonVoid = returnType != void.class;

        MethodType type = args.function.resolvedHandle().type()
                .dropParameterTypes(0,1)
                .changeReturnType(returnType);
        MethodType cleanupType = type.insertParameterTypes(0, Throwable.class);
        if (isNonVoid) {
            cleanupType = cleanupType.insertParameterTypes(1, returnType);
        }
        MethodTypeDesc cleanupDesc = methodDesc(cleanupType.basicType());

        // exception handler table
        cob.exceptionCatch(lFrom, lTo, lCatch, CD_Throwable);

        // TRY:
        cob.labelBinding(lFrom);
        emitPushArgument(cob, invoker, 0); // load target
        emitPushArguments(cob, args, 1); // load args (skip 0: method handle)
        cob.invokevirtual(CD_MethodHandle, "invokeBasic", methodDesc(type.basicType()))
           .labelBinding(lTo);

        // FINALLY_NORMAL:
        int index = extendLocalsMap(new Class<?>[]{ returnType });
        if (isNonVoid) {
            emitStoreInsn(cob, basicReturnType.basicTypeKind(), index);
        }
        emitPushArgument(cob, invoker, 1); // load cleanup
        cob.aconst_null();
        if (isNonVoid) {
            emitLoadInsn(cob, basicReturnType.basicTypeKind(), index);
        }
        emitPushArguments(cob, args, 1); // load args (skip 0: method handle)
        cob.invokevirtual(CD_MethodHandle, "invokeBasic", cleanupDesc)
           .goto_w(lDone)
           // CATCH:
           .labelBinding(lCatch)
           .dup();

        // FINALLY_EXCEPTIONAL:
        emitPushArgument(cob, invoker, 1); // load cleanup
        cob.swap();
        if (isNonVoid) {
            emitZero(cob, BasicType.basicType(returnType)); // load default for result
        }
        emitPushArguments(cob, args, 1); // load args (skip 0: method handle)
        cob.invokevirtual(CD_MethodHandle, "invokeBasic", cleanupDesc);
        if (isNonVoid) {
            emitPopInsn(cob, basicReturnType);
        }
        cob.athrow()
           // DONE:
           .labelBinding(lDone);

        return result;
    }

    private void emitPopInsn(CodeBuilder cob, BasicType type) {
        switch (type) {
            case I_TYPE, F_TYPE, L_TYPE -> cob.pop();
            case J_TYPE, D_TYPE -> cob.pop2();
            default -> throw new InternalError("unknown type: " + type);
        }
    }

    private Name emitTableSwitch(CodeBuilder cob, int pos, int numCases) {
        Name args    = lambdaForm.names[pos];
        Name invoker = lambdaForm.names[pos + 1];
        Name result  = lambdaForm.names[pos + 2];

        Class<?> returnType = result.function.resolvedHandle().type().returnType();
        MethodType caseType = args.function.resolvedHandle().type()
            .dropParameterTypes(0, 1) // drop collector
            .changeReturnType(returnType);
        MethodTypeDesc caseDescriptor = methodDesc(caseType.basicType());

        emitPushArgument(cob, invoker, 2); // push cases
        cob.getfield(CD_CasesHolder, "cases", CD_MethodHandle_array);
        int casesLocal = extendLocalsMap(new Class<?>[] { MethodHandle[].class });
        emitStoreInsn(cob, TypeKind.REFERENCE, casesLocal);

        Label endLabel = cob.newLabel();
        Label defaultLabel = cob.newLabel();
        List<SwitchCase> cases = new ArrayList<>(numCases);
        for (int i = 0; i < numCases; i++) {
            cases.add(SwitchCase.of(i, cob.newLabel()));
        }

        emitPushArgument(cob, invoker, 0); // push switch input
        cob.tableswitch(0, numCases - 1, defaultLabel, cases)
           .labelBinding(defaultLabel);
        emitPushArgument(cob, invoker, 1); // push default handle
        emitPushArguments(cob, args, 1); // again, skip collector
        cob.invokevirtual(CD_MethodHandle, "invokeBasic", caseDescriptor)
           .goto_(endLabel);

        for (int i = 0; i < numCases; i++) {
            cob.labelBinding(cases.get(i).target());
            // Load the particular case:
            emitLoadInsn(cob, TypeKind.REFERENCE, casesLocal);
            cob.loadConstant(i)
               .aaload();

            // invoke it:
            emitPushArguments(cob, args, 1); // again, skip collector
            cob.invokevirtual(CD_MethodHandle, "invokeBasic", caseDescriptor)
               .goto_(endLabel);
        }

        cob.labelBinding(endLabel);

        return result;
    }

    /**
     * Emit bytecode for the loop idiom.
     * <p>
     * The pattern looks like (Cf. MethodHandleImpl.loop):
     * <blockquote><pre>{@code
     * // a0: BMH
     * // a1: LoopClauses (containing an array of arrays: inits, steps, preds, finis)
     * // a2: box, a3: unbox
     * // a4 (and following): arguments
     * loop=Lambda(a0:L,a1:L,a2:L,a3:L,a4:L)=>{
     *   t5:L=MethodHandle.invokeBasic(a2:L,a4:L);          // box the arguments into an Object[]
     *   t6:L=MethodHandleImpl.loop(bt:L,a1:L,t5:L);        // call the loop executor (with supplied types in bt)
     *   t7:L=MethodHandle.invokeBasic(a3:L,t6:L);t7:L}     // unbox the result; return the result
     * }</pre></blockquote>
     * <p>
     * It is compiled into bytecode equivalent to the code seen in {@link MethodHandleImpl#loop(BasicType[],
     * MethodHandleImpl.LoopClauses, Object...)}, with the difference that no arrays
     * will be used for local state storage. Instead, the local state will be mapped to actual stack slots.
     * <p>
     * Bytecode generation applies an unrolling scheme to enable better bytecode generation regarding local state type
     * handling. The generated bytecode will have the following form ({@code void} types are ignored for convenience).
     * Assume there are {@code C} clauses in the loop.
     * <blockquote><pre>{@code
     * PREINIT: ALOAD_1
     *          CHECKCAST LoopClauses
     *          GETFIELD LoopClauses.clauses
     *          ASTORE clauseDataIndex          // place the clauses 2-dimensional array on the stack
     * INIT:    (INIT_SEQ for clause 1)
     *          ...
     *          (INIT_SEQ for clause C)
     * LOOP:    (LOOP_SEQ for clause 1)
     *          ...
     *          (LOOP_SEQ for clause C)
     *          GOTO LOOP
     * DONE:    ...
     * }</pre></blockquote>
     * <p>
     * The {@code INIT_SEQ_x} sequence for clause {@code x} (with {@code x} ranging from {@code 0} to {@code C-1}) has
     * the following shape. Assume slot {@code vx} is used to hold the state for clause {@code x}.
     * <blockquote><pre>{@code
     * INIT_SEQ_x:  ALOAD clauseDataIndex
     *              ICONST_0
     *              AALOAD      // load the inits array
     *              ICONST x
     *              AALOAD      // load the init handle for clause x
     *              load args
     *              INVOKEVIRTUAL MethodHandle.invokeBasic
     *              store vx
     * }</pre></blockquote>
     * <p>
     * The {@code LOOP_SEQ_x} sequence for clause {@code x} (with {@code x} ranging from {@code 0} to {@code C-1}) has
     * the following shape. Again, assume slot {@code vx} is used to hold the state for clause {@code x}.
     * <blockquote><pre>{@code
     * LOOP_SEQ_x:  ALOAD clauseDataIndex
     *              ICONST_1
     *              AALOAD              // load the steps array
     *              ICONST x
     *              AALOAD              // load the step handle for clause x
     *              load locals
     *              load args
     *              INVOKEVIRTUAL MethodHandle.invokeBasic
     *              store vx
     *              ALOAD clauseDataIndex
     *              ICONST_2
     *              AALOAD              // load the preds array
     *              ICONST x
     *              AALOAD              // load the pred handle for clause x
     *              load locals
     *              load args
     *              INVOKEVIRTUAL MethodHandle.invokeBasic
     *              IFNE LOOP_SEQ_x+1   // predicate returned false -> jump to next clause
     *              ALOAD clauseDataIndex
     *              ICONST_3
     *              AALOAD              // load the finis array
     *              ICONST x
     *              AALOAD              // load the fini handle for clause x
     *              load locals
     *              load args
     *              INVOKEVIRTUAL MethodHandle.invokeBasic
     *              GOTO DONE           // jump beyond end of clauses to return from loop
     * }</pre></blockquote>
     */
    private Name emitLoop(CodeBuilder cob, int pos) {
        Name args    = lambdaForm.names[pos];
        Name invoker = lambdaForm.names[pos+1];
        Name result  = lambdaForm.names[pos+2];

        // extract clause and loop-local state types
        // find the type info in the loop invocation
        BasicType[] loopClauseTypes = (BasicType[]) invoker.arguments[0];
        Class<?>[] loopLocalStateTypes = Stream.of(loopClauseTypes)
                .filter(bt -> bt != BasicType.V_TYPE)
                .map(BasicType::basicTypeClass).toArray(Class<?>[]::new);
        Class<?>[] localTypes = new Class<?>[loopLocalStateTypes.length + 1];
        localTypes[0] = MethodHandleImpl.LoopClauses.class;
        System.arraycopy(loopLocalStateTypes, 0, localTypes, 1, loopLocalStateTypes.length);

        final int clauseDataIndex = extendLocalsMap(localTypes);
        final int firstLoopStateIndex = clauseDataIndex + 1;

        Class<?> returnType = result.function.resolvedHandle().type().returnType();
        MethodType loopType = args.function.resolvedHandle().type()
                .dropParameterTypes(0,1)
                .changeReturnType(returnType);
        MethodType loopHandleType = loopType.insertParameterTypes(0, loopLocalStateTypes);
        MethodType predType = loopHandleType.changeReturnType(boolean.class);
        MethodType finiType = loopHandleType;

        final int nClauses = loopClauseTypes.length;

        // indices to invoker arguments to load method handle arrays
        final int inits = 1;
        final int steps = 2;
        final int preds = 3;
        final int finis = 4;

        Label lLoop = cob.newLabel();
        Label lDone = cob.newLabel();
        Label lNext;

        // PREINIT:
        emitPushArgument(cob, MethodHandleImpl.LoopClauses.class, invoker.arguments[1]);
        cob.getfield(CD_LoopClauses, "clauses", CD_MethodHandle_array2);
        emitStoreInsn(cob, TypeKind.REFERENCE, clauseDataIndex);

        // INIT:
        for (int c = 0, state = 0; c < nClauses; ++c) {
            MethodType cInitType = loopType.changeReturnType(loopClauseTypes[c].basicTypeClass());
            emitLoopHandleInvoke(cob, invoker, inits, c, args, false, cInitType, loopLocalStateTypes, clauseDataIndex,
                    firstLoopStateIndex);
            if (cInitType.returnType() != void.class) {
                emitStoreInsn(cob, BasicType.basicType(cInitType.returnType()).basicTypeKind(), firstLoopStateIndex + state);
                ++state;
            }
        }

        // LOOP:
        cob.labelBinding(lLoop);

        for (int c = 0, state = 0; c < nClauses; ++c) {
            lNext = cob.newLabel();

            MethodType stepType = loopHandleType.changeReturnType(loopClauseTypes[c].basicTypeClass());
            boolean isVoid = stepType.returnType() == void.class;

            // invoke loop step
            emitLoopHandleInvoke(cob, invoker, steps, c, args, true, stepType, loopLocalStateTypes, clauseDataIndex,
                    firstLoopStateIndex);
            if (!isVoid) {
                emitStoreInsn(cob, BasicType.basicType(stepType.returnType()).basicTypeKind(), firstLoopStateIndex + state);
                ++state;
            }

            // invoke loop predicate
            emitLoopHandleInvoke(cob, invoker, preds, c, args, true, predType, loopLocalStateTypes, clauseDataIndex,
                    firstLoopStateIndex);
            cob.ifne(lNext);

            // invoke fini
            emitLoopHandleInvoke(cob, invoker, finis, c, args, true, finiType, loopLocalStateTypes, clauseDataIndex,
                    firstLoopStateIndex);
            cob.goto_w(lDone)
               // this is the beginning of the next loop clause
               .labelBinding(lNext);
        }

        cob.goto_w(lLoop)
           // DONE:
           .labelBinding(lDone);

        return result;
    }

    private int extendLocalsMap(Class<?>[] types) {
        int firstSlot = localsMap.length - 1;
        localsMap = Arrays.copyOf(localsMap, localsMap.length + types.length);
        localClasses = Arrays.copyOf(localClasses, localClasses.length + types.length);
        System.arraycopy(types, 0, localClasses, firstSlot, types.length);
        int index = localsMap[firstSlot - 1] + 1;
        int lastSlots = 0;
        for (int i = 0; i < types.length; ++i) {
            localsMap[firstSlot + i] = index;
            lastSlots = BasicType.basicType(localClasses[firstSlot + i]).basicTypeSlots();
            index += lastSlots;
        }
        localsMap[localsMap.length - 1] = index - lastSlots;
        return firstSlot;
    }

    private void emitLoopHandleInvoke(CodeBuilder cob, Name holder, int handles, int clause, Name args, boolean pushLocalState,
                                      MethodType type, Class<?>[] loopLocalStateTypes, int clauseDataSlot,
                                      int firstLoopStateSlot) {
        // load handle for clause
        emitPushClauseArray(cob, clauseDataSlot, handles);
        cob.loadConstant(clause)
           .aaload();
        // load loop state (preceding the other arguments)
        if (pushLocalState) {
            for (int s = 0; s < loopLocalStateTypes.length; ++s) {
                emitLoadInsn(cob, BasicType.basicType(loopLocalStateTypes[s]).basicTypeKind(), firstLoopStateSlot + s);
            }
        }
        // load loop args (skip 0: method handle)
        emitPushArguments(cob, args, 1);
        cob.invokevirtual(CD_MethodHandle, "invokeBasic", methodDesc(type));
    }

    private void emitPushClauseArray(CodeBuilder cob, int clauseDataSlot, int which) {
        emitLoadInsn(cob, TypeKind.REFERENCE, clauseDataSlot);
        cob.loadConstant(which - 1)
           .aaload();
    }

    private void emitZero(CodeBuilder cob, BasicType type) {
        switch (type) {
            case I_TYPE -> cob.iconst_0();
            case J_TYPE -> cob.lconst_0();
            case F_TYPE -> cob.fconst_0();
            case D_TYPE -> cob.dconst_0();
            case L_TYPE -> cob.aconst_null();
            default -> throw new InternalError("unknown type: " + type);
        };
    }

    private void emitPushArguments(CodeBuilder cob, Name args, int start) {
        MethodType type = args.function.methodType();
        for (int i = start; i < args.arguments.length; i++) {
            emitPushArgument(cob, type.parameterType(i), args.arguments[i]);
        }
    }

    private void emitPushArgument(CodeBuilder cob, Name name, int paramIndex) {
        Object arg = name.arguments[paramIndex];
        Class<?> ptype = name.function.methodType().parameterType(paramIndex);
        emitPushArgument(cob, ptype, arg);
    }

    private void emitPushArgument(CodeBuilder cob, Class<?> ptype, Object arg) {
        BasicType bptype = basicType(ptype);
        if (arg instanceof Name n) {
            emitLoadInsn(cob, n.type.basicTypeKind(), n.index());
            emitImplicitConversion(cob, n.type, ptype, n);
        } else if ((arg == null || arg instanceof String) && bptype == L_TYPE) {
            cob.loadConstant((ConstantDesc)arg);
        } else {
            if (Wrapper.isWrapperType(arg.getClass()) && bptype != L_TYPE) {
                cob.loadConstant((ConstantDesc)arg);
            } else {
                cob.getstatic(classData(cob, arg, CD_Object));
                emitImplicitConversion(cob, L_TYPE, ptype, arg);
            }
        }
    }

    /**
     * Store the name to its local, if necessary.
     */
    private void emitStoreResult(CodeBuilder cob, Name name) {
        if (name != null && name.type != V_TYPE) {
            // non-void: actually assign
            emitStoreInsn(cob, name.type.basicTypeKind(), name.index());
        }
    }

    /**
     * Emits a return statement from a LF invoker. If required, the result type is cast to the correct return type.
     */
    private void emitReturn(CodeBuilder cob, Name onStack) {
        // return statement
        Class<?> rclass = invokerType.returnType();
        BasicType rtype = lambdaForm.returnType();
        assert(rtype == basicType(rclass));  // must agree
        if (rtype == V_TYPE) {
            // void
            cob.return_();
            // it doesn't matter what rclass is; the JVM will discard any value
        } else {
            LambdaForm.Name rn = lambdaForm.names[lambdaForm.result];

            // put return value on the stack if it is not already there
            if (rn != onStack) {
                emitLoadInsn(cob, rtype.basicTypeKind(), lambdaForm.result);
            }

            emitImplicitConversion(cob, rtype, rclass, rn);

            // generate actual return statement
            cob.return_(rtype.basicTypeKind());
        }
    }

    /**
     * Emit a type conversion bytecode casting from "from" to "to".
     */
    private void emitPrimCast(CodeBuilder cob, TypeKind from, TypeKind to) {
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
        if (from != to && from != TypeKind.BOOLEAN) try {
            cob.conversion(from, to);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("unhandled prim cast: " + from + "2" + to);
        }
    }

    /**
     * Generate bytecode for a LambdaForm.vmentry which calls interpretWithArguments.
     */
    static MemberName generateLambdaFormInterpreterEntryPoint(MethodType mt) {
        assert(isValidSignature(basicTypeSignature(mt)));
        String name = "interpret_"+basicTypeChar(mt.returnType());
        MethodType type = mt;  // includes leading argument
        type = type.changeParameterType(0, MethodHandle.class);
        InvokerBytecodeGenerator g = new InvokerBytecodeGenerator("LFI", name, type);
        return g.loadMethod(g.generateLambdaFormInterpreterEntryPointBytes());
    }

    private byte[] generateLambdaFormInterpreterEntryPointBytes() {
        final byte[] classFile = classFileSetup(new Consumer<>() {
            @Override
            public void accept(ClassBuilder clb) {
                methodSetup(clb, new Consumer<>() {
                    @Override
                    public void accept(MethodBuilder mb) {

                        mb.with(RuntimeVisibleAnnotationsAttribute.of(List.of(
                                HIDDEN,    // Suppress this method in backtraces displayed to the user.
                                DONTINLINE // Don't inline the interpreter entry.
                        )));

                        mb.withCode(new Consumer<>() {
                            @Override
                            public void accept(CodeBuilder cob) {
                                // create parameter array
                                cob.loadConstant(invokerType.parameterCount())
                                   .anewarray(CD_Object);

                                // fill parameter array
                                for (int i = 0; i < invokerType.parameterCount(); i++) {
                                    Class<?> ptype = invokerType.parameterType(i);
                                    cob.dup()
                                       .loadConstant(i);
                                    emitLoadInsn(cob, basicType(ptype).basicTypeKind(), i);
                                    // box if primitive type
                                    if (ptype.isPrimitive()) {
                                        emitBoxing(cob, TypeKind.from(ptype));
                                    }
                                    cob.aastore();
                                }
                                // invoke
                                cob.aload(0)
                                   .getfield(CD_MethodHandle, "form", CD_LambdaForm)
                                   .swap()  // swap form and array; avoid local variable
                                   .invokevirtual(CD_LambdaForm, "interpretWithArguments", MethodTypeDescImpl.ofValidated(CD_Object, CD_Object_array));

                                // maybe unbox
                                Class<?> rtype = invokerType.returnType();
                                TypeKind rtypeK = TypeKind.from(rtype);
                                if (rtype.isPrimitive() && rtype != void.class) {
                                    emitUnboxing(cob, rtypeK);
                                }

                                // return statement
                                cob.return_(rtypeK);
                            }
                        });
                    }
                });
                clinit(clb, classEntry, classData);
                bogusMethod(clb, invokerType);
            }
        });
        return classFile;
    }

    /**
     * Generate bytecode for a NamedFunction invoker.
     */
    static MemberName generateNamedFunctionInvoker(MethodTypeForm typeForm) {
        MethodType invokerType = NamedFunction.INVOKER_METHOD_TYPE;
        String invokerName = "invoke_" + shortenSignature(basicTypeSignature(typeForm.erasedType()));
        InvokerBytecodeGenerator g = new InvokerBytecodeGenerator("NFI", invokerName, invokerType);
        return g.loadMethod(g.generateNamedFunctionInvokerImpl(typeForm));
    }

    private byte[] generateNamedFunctionInvokerImpl(MethodTypeForm typeForm) {
        MethodType dstType = typeForm.erasedType();
        final byte[] classFile = classFileSetup(new Consumer<>() {
            @Override
            public void accept(ClassBuilder clb) {
                methodSetup(clb, new Consumer<>() {
                    @Override
                    public void accept(MethodBuilder mb) {

                        mb.with(RuntimeVisibleAnnotationsAttribute.of(List.of(
                                HIDDEN,    // Suppress this method in backtraces displayed to the user.
                                FORCEINLINE // Force inlining of this invoker method.
                        )));

                        mb.withCode(new Consumer<>() {
                            @Override
                            public void accept(CodeBuilder cob) {
                                // Load receiver
                                cob.aload(0);

                                // Load arguments from array
                                for (int i = 0; i < dstType.parameterCount(); i++) {
                                    cob.aload(1)
                                       .loadConstant(i)
                                       .aaload();

                                    // Maybe unbox
                                    Class<?> dptype = dstType.parameterType(i);
                                    if (dptype.isPrimitive()) {
                                        TypeKind dstTK = TypeKind.from(dptype);
                                        TypeKind srcTK = dstTK.asLoadable();
                                        emitUnboxing(cob, srcTK);
                                        emitPrimCast(cob, srcTK, dstTK);
                                    }
                                }

                                // Invoke
                                MethodTypeDesc targetDesc = methodDesc(dstType.basicType());
                                cob.invokevirtual(CD_MethodHandle, "invokeBasic", targetDesc);

                                // Box primitive types
                                Class<?> rtype = dstType.returnType();
                                if (rtype != void.class && rtype.isPrimitive()) {
                                    TypeKind srcTK = TypeKind.from(rtype);
                                    TypeKind dstTK = srcTK.asLoadable();
                                    // boolean casts not allowed
                                    emitPrimCast(cob, srcTK, dstTK);
                                    emitBoxing(cob, dstTK);
                                }

                                // If the return type is void we return a null reference.
                                if (rtype == void.class) {
                                    cob.aconst_null();
                                }
                               cob.areturn();  // NOTE: NamedFunction invokers always return a reference value.
                            }
                        });
                    }
                });
                clinit(clb, classEntry, classData);
                bogusMethod(clb, dstType);
            }
        });
        return classFile;
    }

    /**
     * Emit a bogus method that just loads some string constants. This is to get the constants into the constant pool
     * for debugging purposes.
     */
    private void bogusMethod(ClassBuilder clb, Object os) {
        if (dumper().isEnabled()) {
            clb.withMethodBody("dummy", MTD_void, ACC_STATIC, new Consumer<>() {
                @Override
                public void accept(CodeBuilder cob) {
                    cob.ldc(os.toString())
                       .pop()
                       .return_();
                }
            });
        }
    }

    static ClassDesc classDesc(Class<?> cls) {
//        assert(VerifyAccess.isTypeVisible(cls, Object.class)) : cls.getName();
        return cls == MethodHandle.class ? CD_MethodHandle
             : cls == DirectMethodHandle.class ? CD_DirectMethodHandle
             : cls == Object.class ? CD_Object
             : cls == MemberName.class ? CD_MemberName
             : cls == MethodType.class ? CD_MethodType
             : cls.isPrimitive() ? Wrapper.forPrimitiveType(cls).basicClassDescriptor()
             : ConstantUtils.referenceClassDesc(cls.descriptorString());
    }

    static MethodTypeDesc methodDesc(MethodType mt) {
        var params = new ClassDesc[mt.parameterCount()];
        for (int i = 0; i < params.length; i++) {
            params[i] = classDesc(mt.parameterType(i));
        }
        return MethodTypeDescImpl.ofValidated(classDesc(mt.returnType()), params);
    }
}
