/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package java.dyn;

import sun.dyn.util.BytecodeName;
import sun.dyn.Access;
import sun.dyn.CallSiteImpl;
import sun.dyn.MethodHandleImpl;

/**
 * An {@code invokedynamic} call site, as reified by the
 * containing class's bootstrap method.
 * Every call site object corresponds to a distinct instance
 * of the <code>invokedynamic</code> instruction, and vice versa.
 * Every call site has one state variable, called the {@code target}.
 * It is typed as a {@link MethodHandle}.  This state is never null, and
 * it is the responsibility of the bootstrap method to produce call sites
 * which have been pre-linked to an initial target method.
 * <p>
 * (Note:  The bootstrap method may elect to produce call sites of a
 * language-specific subclass of {@code CallSite}.  In such a case,
 * the subclass may claim responsibility for initializing its target to
 * a non-null value, by overriding {@link #initialTarget}.)
 * <p>
 * An {@code invokedynamic} instruction which has not yet been executed
 * is said to be <em>unlinked</em>.  When an unlinked call site is executed,
 * the containing class's bootstrap method is called to manufacture a call site,
 * for the instruction.  If the bootstrap method does not assign a non-null
 * value to the new call site's target variable, the method {@link #initialTarget}
 * is called to produce the new call site's first target method.
 * <p>
 * @see Linkage#registerBootstrapMethod(java.lang.Class, java.dyn.MethodHandle)
 * @author John Rose, JSR 292 EG
 */
public class CallSite
        // Note: This is an implementation inheritance hack, and will be removed
        // with a JVM change which moves the required hidden state onto this class.
        extends CallSiteImpl
{
    private static final Access IMPL_TOKEN = Access.getToken();

    /*

    // Fields used only by the JVM.  Do not use or change.
    private Object vmmethod;
    int callerMID, callerBCI;  // supplied by the JVM

    private MethodHandle target;

    final Object caller;  // usually a class
    final String name;
    final MethodType type;
    */

    /**
     * Make a call site given the parameters from a call to the bootstrap method.
     * The resulting call site is in an unlinked state, which means that before
     * it is returned from a bootstrap method call it must be provided with
     * a target method via a call to {@link CallSite#setTarget}.
     * @param caller the class in which the relevant {@code invokedynamic} instruction occurs
     * @param name the name specified by the {@code invokedynamic} instruction
     * @param type the method handle type derived from descriptor of the {@code invokedynamic} instruction
     */
    public CallSite(Object caller, String name, MethodType type) {
        super(IMPL_TOKEN, caller, name, type);
    }

    private static void privateInitializeCallSite(CallSite site, int callerMID, int callerBCI) {
        site.callerMID = callerMID;
        site.callerBCI = callerBCI;
        site.ensureTarget();
    }
    private void ensureTarget() {
        // Note use of super, which accesses the field directly,
        // without deferring to possible subclass overrides.
        if (super.getTarget() == null) {
            super.setTarget(this.initialTarget());
            super.getTarget().type();  // provoke NPE if still null
        }
    }

    /**
     * Just after a call site is created by a bootstrap method handle,
     * if the target has not been initialized by the factory method itself,
     * the method {@code initialTarget} is called to produce an initial
     * non-null target.  (Live call sites must never have null targets.)
     * <p>
     * If the bootstrap method itself does not initialize the call site,
     * this method must be overridden, because it just raises an
     * {@code InvokeDynamicBootstrapError}, which in turn causes the
     * linkage of the {@code invokedynamic} instruction to terminate
     * abnormally.
     */
    protected MethodHandle initialTarget() {
        throw new InvokeDynamicBootstrapError("target must be initialized before call site is linked: "+this);
    }

    /**
     * Report the current linkage state of the call site.  (This is mutable.)
     * The value may not be null after the {@code CallSite} object is returned
     * from the bootstrap method of the {@code invokedynamic} instruction.
     * When an {@code invokedynamic} instruction is executed, the target method
     * of its associated {@code call site} object is invoked directly,
     * as if via {@link MethodHandle}{@code .invoke}.
     * <p>
     * The interactions of {@code getTarget} with memory are the same
     * as of a read from an ordinary variable, such as an array element or a
     * non-volatile, non-final field.
     * <p>
     * In particular, the current thread may choose to reuse the result
     * of a previous read of the target from memory, and may fail to see
     * a recent update to the target by another thread.
     * @return the current linkage state of the call site
     * @see #setTarget
     */
    public MethodHandle getTarget() {
        return super.getTarget();
    }

    /**
     * Link or relink the call site, by setting its target method.
     * <p>
     * The interactions of {@code setTarget} with memory are the same
     * as of a write to an ordinary variable, such as an array element or a
     * non-volatile, non-final field.
     * <p>
     * In particular, unrelated threads may fail to see the updated target
     * until they perform a read from memory.
     * Stronger guarantees can be created by putting appropriate operations
     * into the bootstrap method and/or the target methods used
     * at any given call site.
     * @param target the new target, or null if it is to be unlinked
     * @throws NullPointerException if the proposed new target is null
     * @throws WrongMethodTypeException if the proposed new target
     *         has a method type that differs from the call site's {@link #type()}
     */
    public void setTarget(MethodHandle target) {
        checkTarget(target);
        super.setTarget(target);
    }

    protected void checkTarget(MethodHandle target) {
        target.type();  // provoke NPE
        if (!canSetTarget(target))
            throw new WrongMethodTypeException(String.valueOf(target)+target.type()+" should be of type "+type());
    }

    protected boolean canSetTarget(MethodHandle target) {
        return (target != null && target.type() == type());
    }

    /**
     * Report the class containing the call site.
     * This is an immutable property of the call site, set from the first argument to the constructor.
     * @return class containing the call site
     */
    public Class<?> callerClass() {
        return (Class) caller;
    }

    /**
     * Report the method name specified in the {@code invokedynamic} instruction.
     * This is an immutable property of the call site, set from the second argument to the constructor.
     * <p>
     * Note that the name is a JVM bytecode name, and as such can be any
     * non-empty string, as long as it does not contain certain "dangerous"
     * characters such as slash {@code '/'} and dot {@code '.'}.
     * See the Java Virtual Machine specification for more details.
     * <p>
     * Application such as a language runtimes may need to encode
     * arbitrary program element names and other configuration information
     * into the name.  A standard convention for doing this is
     * <a href="http://blogs.sun.com/jrose/entry/symbolic_freedom_in_the_vm">specified here</a>.
     * @return method name specified by the call site
     */
    public String name() {
        return name;
    }

    /**
     * Report the method name specified in the {@code invokedynamic} instruction,
     * as a series of components, individually demangled according to
     * the standard convention
     * <a href="http://blogs.sun.com/jrose/entry/symbolic_freedom_in_the_vm">specified here</a>.
     * <p>
     * Non-empty runs of characters between dangerous characters are demangled.
     * Each component is either a completely arbitrary demangled string,
     * or else a character constant for a punctuation character, typically ':'.
     * (In principle, the character can be any dangerous character that the
     * JVM lets through in a method name, such as '$' or ']'.
     * Runtime implementors are encouraged to use colon ':' for building
     * structured names.)
     * <p>
     * In the common case where the name contains no dangerous characters,
     * the result is an array whose only element array is the demangled
     * name at the call site.  Such a demangled name can be any sequence
     * of any number of any unicode characters.
     * @return method name components specified by the call site
     */
    public Object[] nameComponents() {
        return BytecodeName.parseBytecodeName(name);
    }

    /**
     * Report the resolved result and parameter types of this call site,
     * which are derived from its bytecode-level invocation descriptor.
     * The types are packaged into a {@link MethodType}.
     * Any linked target of this call site must be exactly this method type.
     * This is an immutable property of the call site, set from the third argument to the constructor.
     * @return method type specified by the call site
     */
    public MethodType type() {
        return type;
    }

    @Override
    public String toString() {
        return "CallSite#"+hashCode()+"["+name+type+" => "+getTarget()+"]";
    }

    // Package-local constant:
    static final MethodHandle GET_TARGET = MethodHandleImpl.getLookup(IMPL_TOKEN).
            findVirtual(CallSite.class, "getTarget", MethodType.methodType(MethodHandle.class));
}
