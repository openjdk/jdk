package jdk.internal.dynalink.support;

import static jdk.internal.org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_STATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_SUPER;
import static jdk.internal.org.objectweb.asm.Opcodes.V1_7;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.commons.InstructionAdapter;
import jdk.nashorn.internal.runtime.RewriteException;
import sun.misc.Unsafe;

/**
 * Generates method handles that combine an invocation and a handler for a {@link RewriteException}. Always immediately
 * generates compiled bytecode.
 */
public class CatchExceptionCombinator {
    static {
        System.err.println("*** Running with fast catch combinator handler ***");
    }
    private static final Type METHOD_HANDLE_TYPE = Type.getType(MethodHandle.class);
    private static final String METHOD_HANDLE_TYPE_NAME = METHOD_HANDLE_TYPE.getInternalName();
    private static final String OBJECT_TYPE_NAME = Type.getInternalName(Object.class);

    private static final String HANDLER_TYPE_NAME = "java.lang.invoke.CatchExceptionCombinator$MH";
    private static final String INVOKE_METHOD_NAME = "invoke";

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private static final ConcurrentMap<CombinatorParameters, ClassTemplate> handlerClassBytes = new ConcurrentHashMap<>();

    private static final class CombinatorParameters {
        final MethodType targetType;
        final Class<? extends Throwable> exType;
        final MethodType handlerType;

        CombinatorParameters(final MethodType targetType, final Class<? extends Throwable> exType, MethodType handlerType) {
            this.targetType = targetType;
            this.exType = exType;
            this.handlerType = handlerType;
        }

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof CombinatorParameters) {
                final CombinatorParameters p = (CombinatorParameters)obj;
                return targetType.equals(p.targetType) && exType.equals(p.exType) && handlerType.equals(p.handlerType);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return targetType.hashCode() ^ exType.hashCode() ^ handlerType.hashCode();
        }
    }

    /**
     * Catch exception - create combinator
     * @param target  target
     * @param exType  type to check for
     * @param handler catch handler
     * @return target wrapped in catch handler
     */
    public static MethodHandle catchException(final MethodHandle target, final Class<? extends Throwable> exType, final MethodHandle handler) {
        final MethodType targetType = target.type();
        final MethodType handlerType = handler.type();

        final ClassTemplate classTemplate = handlerClassBytes.computeIfAbsent(
                new CombinatorParameters(targetType, exType, handlerType), new Function<CombinatorParameters, ClassTemplate>() {
            @Override
            public ClassTemplate apply(final CombinatorParameters parameters) {
                return generateClassTemplate(parameters);
            }
        });
        return classTemplate.instantiate(target, handler, targetType);
    }

    private static final class ClassTemplate {
        final byte[] bytes;
        final int target_cp_index;
        final int handler_cp_index;
        final int cp_size;

        ClassTemplate(final byte[] bytes, final int target_cp_index, final int handler_cp_index, final int cp_size) {
            this.bytes = bytes;
            this.target_cp_index = target_cp_index;
            this.handler_cp_index = handler_cp_index;
            this.cp_size = cp_size;
        }

        MethodHandle instantiate(final MethodHandle target, final MethodHandle handler, final MethodType type) {
            final Object[] cpPatch = new Object[cp_size];
            cpPatch[target_cp_index] = target;
            cpPatch[handler_cp_index] = handler;
            final Class<?> handlerClass = UNSAFE.defineAnonymousClass(CatchExceptionCombinator.class, bytes, cpPatch);
            try {
                return MethodHandles.lookup().findStatic(handlerClass, INVOKE_METHOD_NAME, type);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }
    }

    private static ClassTemplate generateClassTemplate(final CombinatorParameters combinatorParameters) {
        final ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        w.visit(V1_7, ACC_PUBLIC | ACC_SUPER, HANDLER_TYPE_NAME, null, OBJECT_TYPE_NAME, null);

        final MethodType targetType = combinatorParameters.targetType;
        final Class<? extends Throwable> exType = combinatorParameters.exType;
        final String methodDescriptor = targetType.toMethodDescriptorString();
        final Class<?> returnType = targetType.returnType();
        final MethodType handlerType = combinatorParameters.handlerType;

        // NOTE: must use strings as placeholders in the constant pool, even if we'll be replacing them with method handles.
        final String targetPlaceholder = "T_PLACEHOLDER";
        final String handlerPlaceholder = "H_PLACEHOLDER";
        final int target_cp_index = w.newConst(targetPlaceholder);
        final int handler_cp_index = w.newConst(handlerPlaceholder);

        final InstructionAdapter mv = new InstructionAdapter(w.visitMethod(ACC_PUBLIC | ACC_STATIC, INVOKE_METHOD_NAME, methodDescriptor, null, null));
        mv.visitAnnotation("Ljava/lang/invoke/LambdaForm$Hidden;", true);
        mv.visitAnnotation("Ljava/lang/invoke/LambdaForm$Compiled;", true);
        mv.visitAnnotation("Ljava/lang/invoke/ForceInline;", true);

        mv.visitCode();

        final Label _try = new Label();
        final Label _end_try= new Label();

        mv.visitLabel(_try);
        // Invoke
        mv.aconst(targetPlaceholder);
        mv.checkcast(METHOD_HANDLE_TYPE);
        final Class<?>[] paramTypes = targetType.parameterArray();
        for(int i = 0, slot = 0; i < paramTypes.length; ++i) {
            final Type paramType = Type.getType(paramTypes[i]);
            mv.load(slot, paramType);
            slot += paramType.getSize();
        }
        generateInvokeBasic(mv, methodDescriptor);
        final Type asmReturnType = Type.getType(returnType);
        mv.areturn(asmReturnType);

        mv.visitTryCatchBlock(_try, _end_try, _end_try, Type.getInternalName(exType));
        mv.visitLabel(_end_try);
        // Handle exception
        mv.aconst(handlerPlaceholder);
        mv.checkcast(METHOD_HANDLE_TYPE);
        mv.swap();
        final Class<?>[] handlerParamTypes = handlerType.parameterArray();
        for(int i = 1, slot = 0; i < handlerParamTypes.length; ++i) {
            final Type paramType = Type.getType(handlerParamTypes[i]);
            mv.load(slot, paramType);
            slot += paramType.getSize();
        }
        generateInvokeBasic(mv, handlerType.toMethodDescriptorString());
        mv.areturn(asmReturnType);

        mv.visitMaxs(0, 0);
        mv.visitEnd();

        w.visitEnd();
        final byte[] bytes = w.toByteArray();
        final int cp_size = (((bytes[8] & 0xFF) << 8) | (bytes[9] & 0xFF));
        return new ClassTemplate(bytes, target_cp_index, handler_cp_index, cp_size);
    }

    private static void generateInvokeBasic(final InstructionAdapter mv, final String methodDesc) {
        mv.invokevirtual(METHOD_HANDLE_TYPE_NAME, "invokeBasic", methodDesc, false);
    }
}
