
package jdk.nashorn.internal.codegen;

import java.lang.reflect.Field;
import jdk.nashorn.internal.ir.FunctionNode;

/**
 * Since Compiler class doesn't give us access to its private {@code functionNode} field, we use this reflection-based
 * access-check disabling helper to get to it in compilation tests.
 *
 */
public class CompilerAccess {
    private static final Field FUNCTION_NODE_FIELD = getCompilerFunctionNodeField();
    static {
        FUNCTION_NODE_FIELD.setAccessible(true);
    }

    /**
     * Given a compiler, return its {@code functionNode} field, representing the root function (i.e. the compiled script).
     * @param compiler the compiler that already run its {@link Compiler#compile()} method.
     * @return the root function node representing the compiled script.
     * @throws IllegalAccessException
     */
    public static FunctionNode getScriptNode(Compiler compiler) throws IllegalAccessException {
        return (FunctionNode)FUNCTION_NODE_FIELD.get(compiler);
    }

    private static Field getCompilerFunctionNodeField() {
        try {
            return Compiler.class.getDeclaredField("functionNode");
        } catch (NoSuchFieldException e) {
            throw new AssertionError("", e);
        }
    }
}
