package jdk.nashorn.internal.objects;

import static jdk.nashorn.internal.runtime.linker.Lookup.MH;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.nashorn.internal.codegen.CompilationException;
import jdk.nashorn.internal.codegen.Compiler;
import jdk.nashorn.internal.codegen.FunctionSignature;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.runtime.CodeInstaller;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptFunctionData;
import jdk.nashorn.internal.runtime.ScriptObject;

/**
 * A trampoline is a promise to compile a {@link ScriptFunction} later. It just looks like
 * the call to the script function, but when invoked it will compile the script function
 * (in a new compile unit) and invoke it
 */
public final class ScriptFunctionTrampolineImpl extends ScriptFunctionImpl {

    private CodeInstaller<Context> installer;

    /** Function node to lazily recompile when trampoline is hit */
    private FunctionNode functionNode;

    /**
     * Constructor
     *
     * @param installer    opaque code installer from context
     * @param functionNode function node to lazily compile when trampoline is hit
     * @param data         {@link ScriptFunctionData} for function
     * @param scope        scope
     * @param allocator    allocator
     */
    public ScriptFunctionTrampolineImpl(final CodeInstaller<Context> installer, final FunctionNode functionNode, final ScriptFunctionData data, final ScriptObject scope, final MethodHandle allocator) {
        super(null, data, scope, allocator);

        this.installer    = installer;
        this.functionNode = functionNode;

        data.setMethodHandles(makeTrampoline(), allocator);
    }

    private final MethodHandle makeTrampoline() {
        final MethodType mt =
            new FunctionSignature(
                true,
                functionNode.needsCallee(),
                Type.OBJECT,
                functionNode.getParameters().size()).
            getMethodType();

        return
            MH.bindTo(
                MH.asCollector(
                    findOwnMH(
                        "trampoline",
                        Object.class,
                        Object[].class),
                    Object[].class,
                    mt.parameterCount()),
                this);
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findVirtual(MethodHandles.lookup(), ScriptFunctionTrampolineImpl.class, name, MH.type(rtype, types));
    }

    @Override
    protected ScriptFunction makeBoundFunction(final ScriptFunctionData data) {
        //prevent trampoline recompilation cycle if a function is bound before use
        compile();
        return super.makeBoundFunction(data);
    }

    private MethodHandle compile() throws CompilationException {
        final Compiler compiler = new Compiler(installer, functionNode);

        compiler.compile();

        final Class<?> clazz = compiler.install();
        /* compute function signature for lazy method. this can be done first after compilation, as only then do we know
         * the final state about callees, scopes and specialized parameter types */
        final FunctionSignature signature = new FunctionSignature(true, functionNode.needsCallee(), Type.OBJECT, functionNode.getParameters().size());
        final MethodType        mt        = signature.getMethodType();

        MethodHandle mh = MH.findStatic(MethodHandles.publicLookup(), clazz, functionNode.getName(), mt);
        if (functionNode.needsCallee()) {
            mh = MH.bindTo(mh, this);
        }

        // now the invoker method looks like the one our superclass is expecting
        resetInvoker(mh);

        return mh;
    }

    @SuppressWarnings("unused")
    private Object trampoline(final Object... args) throws CompilationException {
        Compiler.LOG.info(">>> TRAMPOLINE: Hitting trampoline for '" + functionNode.getName() + "'");
        MethodHandle mh = compile();

        Compiler.LOG.info("<<< COMPILED TO: " + mh);
        // spread the array to invididual args of the correct type
        mh = MH.asSpreader(mh, Object[].class, mh.type().parameterCount());

        try {
            //invoke the real method the trampoline points to. this only happens once
            return mh.invoke(args);
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
