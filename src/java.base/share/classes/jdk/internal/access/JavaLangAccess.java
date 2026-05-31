/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.access;

import java.io.InputStream;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.module.ModuleDescriptor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Stream;

import jdk.internal.loader.NativeLibraries;
import jdk.internal.misc.CarrierThreadLocal;
import jdk.internal.module.ServicesCatalog;
import jdk.internal.reflect.ConstantPool;
import jdk.internal.vm.Continuation;
import jdk.internal.vm.ContinuationScope;
import jdk.internal.vm.StackableScope;
import jdk.internal.vm.ThreadContainer;
import sun.reflect.annotation.AnnotationType;
import sun.nio.ch.Interruptible;

public interface JavaLangAccess {

    /**
     * Returns the list of {@code Method} objects for the declared public
     * methods of this class or interface that have the specified method name
     * and parameter types.
     */
    List<Method> getDeclaredPublicMethods(Class<?> klass, String name, Class<?>... parameterTypes);

    /**
     * Return most specific method that matches name and parameterTypes.
     */
    Method findMethod(Class<?> klass, boolean publicOnly, String name, Class<?>... parameterTypes);

    /**
     * Return the constant pool for a class.
     */
    ConstantPool getConstantPool(Class<?> klass);

    /**
     * Compare-And-Set the AnnotationType instance corresponding to this class.
     * (This method only applies to annotation types.)
     */
    boolean casAnnotationType(Class<?> klass, AnnotationType oldType, AnnotationType newType);

    /**
     * Get the AnnotationType instance corresponding to this class.
     * (This method only applies to annotation types.)
     */
    AnnotationType getAnnotationType(Class<?> klass);

    /**
     * Get the declared annotations for a given class, indexed by their types.
     */
    Map<Class<? extends Annotation>, Annotation> getDeclaredAnnotationMap(Class<?> klass);

    /**
     * Get the array of bytes that is the class-file representation
     * of this Class' annotations.
     */
    byte[] getRawClassAnnotations(Class<?> klass);

    /**
     * Get the array of bytes that is the class-file representation
     * of this Class' type annotations.
     */
    byte[] getRawClassTypeAnnotations(Class<?> klass);

    /**
     * Get the array of bytes that is the class-file representation
     * of this Executable's type annotations.
     */
    byte[] getRawExecutableTypeAnnotations(Executable executable);

    /**
     * Get the int value of the Class's class-file access flags.
     */
    int getClassFileAccessFlags(Class<?> klass);

    /**
     * Returns the elements of an enum class or null if the
     * Class object does not represent an enum type;
     * the result is uncloned, cached, and shared by all callers.
     */
    <E extends Enum<E>> E[] getEnumConstantsShared(Class<E> klass);

    /**
     * Returns the big-endian packed minor-major version of the class file
     * of this class.
     */
    int classFileVersion(Class<?> clazz);

    /**
     * Set current thread's blocker field.
     */
    void blockedOn(Interruptible b);

    /**
     * Registers a shutdown hook.
     *
     * It is expected that this method with registerShutdownInProgress=true
     * is only used to register DeleteOnExitHook since the first file
     * may be added to the delete on exit list by the application shutdown
     * hooks.
     *
     * @param slot  the slot in the shutdown hook array, whose element
     *              will be invoked in order during shutdown
     * @param registerShutdownInProgress true to allow the hook
     *        to be registered even if the shutdown is in progress.
     * @param hook  the hook to be registered
     *
     * @throws IllegalStateException if shutdown is in progress and
     *         the slot is not valid to register.
     */
    void registerShutdownHook(int slot, boolean registerShutdownInProgress, Runnable hook);

    /**
     * Invokes the finalize method of the given object.
     */
    void invokeFinalize(Object o) throws Throwable;

    /**
     * Returns the ConcurrentHashMap used as a storage for ClassLoaderValue(s)
     * associated with the given class loader, creating it if it doesn't already exist.
     */
    ConcurrentHashMap<?, ?> createOrGetClassLoaderValueMap(ClassLoader cl);

    /**
     * Defines a class with the given name to a class loader.
     */
    Class<?> defineClass(ClassLoader cl, String name, byte[] b, ProtectionDomain pd, String source);

    /**
     * Defines a class with the given name to a class loader with
     * the given flags and class data.
     *
     * @see java.lang.invoke.MethodHandles.Lookup#defineClass
     */
    Class<?> defineClass(ClassLoader cl, Class<?> lookup, String name, byte[] b, ProtectionDomain pd, boolean initialize, int flags, Object classData);

    /**
     * Returns a class loaded by the bootstrap class loader.
     */
    Class<?> findBootstrapClassOrNull(String name);

    /**
     * Define a Package of the given name and module by the given class loader.
     */
    Package definePackage(ClassLoader cl, String name, Module module);

    /**
     * Defines a new module to the Java virtual machine. The module
     * is defined to the given class loader.
     *
     * The URI is for information purposes only, it can be {@code null}.
     */
    Module defineModule(ClassLoader loader, ModuleDescriptor descriptor, URI uri);

    /**
     * Defines the unnamed module for the given class loader.
     */
    Module defineUnnamedModule(ClassLoader loader);

    /**
     * Updates the readability so that module m1 reads m2. The new read edge
     * does not result in a strong reference to m2 (m2 can be GC'ed).
     *
     * This method is the same as m1.addReads(m2) but without a caller check.
     */
    void addReads(Module m1, Module m2);

    /**
     * Updates module m to read all unnamed modules.
     */
    void addReadsAllUnnamed(Module m);

    /**
     * Updates module m1 to export a package unconditionally.
     */
    void addExports(Module m1, String pkg);

    /**
     * Updates module m1 to export a package to module m2. The export does
     * not result in a strong reference to m2 (m2 can be GC'ed).
     */
    void addExports(Module m1, String pkg, Module m2);

    /**
     * Updates a module m to export a package to all unnamed modules.
     */
    void addExportsToAllUnnamed(Module m, String pkg);

    /**
     * Updates module m1 to open a package to module m2. Opening the
     * package does not result in a strong reference to m2 (m2 can be GC'ed).
     */
    void addOpens(Module m1, String pkg, Module m2);

    /**
     * Updates module m to open a package to all unnamed modules.
     */
    void addOpensToAllUnnamed(Module m, String pkg);

    /**
     * Updates module m to use a service.
     */
    void addUses(Module m, Class<?> service);

    /**
     * Returns true if module m reflectively exports a package to other
     */
    boolean isReflectivelyExported(Module module, String pn, Module other);

    /**
     * Returns true if module m reflectively opens a package to other
     */
    boolean isReflectivelyOpened(Module module, String pn, Module other);

    /**
     * Updates module m to allow access to restricted methods.
     */
    void addEnableNativeAccess(Module m);

    /**
     * Updates module named {@code name} in layer {@code layer} to allow access to
     * restricted methods. Returns true iff the given module exists in the given layer.
     */
    boolean addEnableNativeAccess(ModuleLayer layer, String name);

    /**
     * Updates all unnamed modules to allow access to restricted methods.
     */
    void addEnableNativeAccessToAllUnnamed();

    /**
     * Ensure that the given module has native access. If not, warn or throw exception
     * depending on the configuration.
     * @param m the module in which native access occurred
     * @param owner the owner of the restricted method being called (or the JNI method being bound)
     * @param methodName the name of the restricted method being called (or the JNI method being bound)
     * @param currentClass the class calling the restricted method (for JNI, this is the same as {@code owner})
     * @param jni {@code true}, if this event is related to a JNI method being bound
     */
    void ensureNativeAccess(Module m, Class<?> owner, String methodName, Class<?> currentClass, boolean jni);

    /**
     * Enable code in all unnamed modules to mutate final instance fields.
     */
    void addEnableFinalMutationToAllUnnamed();

    /**
     * Enable code in a given module to mutate final instance fields.
     */
    boolean tryEnableFinalMutation(Module m);

    /**
     * Return true if code in a given module is allowed to mutate final instance fields.
     */
    boolean isFinalMutationEnabled(Module m);

    /**
     * Return true if a given module has statically exported the given package to a given other module.
     */
    boolean isStaticallyExported(Module module, String pn, Module other);

    /**
     * Return true if a given module has statically opened the given package to a given other module.
     */
    boolean isStaticallyOpened(Module module, String pn, Module other);

    /**
     * Returns the ServicesCatalog for the given Layer.
     */
    ServicesCatalog getServicesCatalog(ModuleLayer layer);

    /**
     * Record that this layer has at least one module defined to the given
     * class loader.
     */
    void bindToLoader(ModuleLayer layer, ClassLoader loader);

    /**
     * Returns an ordered stream of layers. The first element is the
     * given layer, the remaining elements are its parents, in DFS order.
     */
    Stream<ModuleLayer> layers(ModuleLayer layer);

    /**
     * Returns a stream of the layers that have modules defined to the
     * given class loader.
     */
    Stream<ModuleLayer> layers(ClassLoader loader);

    /**
     * Count the number of leading positive bytes in the range.
     *
     * @implSpec Implementations of this method must perform bounds checks.
     */
    int countPositives(byte[] ba, int off, int len);

    /**
     * Count the number of leading non-zero ascii chars in the String.
     */
    int countNonZeroAscii(String s);

    /**
     * Constructs a new {@code String} with the supplied Latin1 bytes.
     * <p>
     * <b>WARNING: The caller of this method shall relinquish and transfer the
     * ownership of the byte array to the callee</b>, since the latter will not
     * make a copy.
     *
     * @param bytes the byte array source
     * @return the newly created string
     */
    String uncheckedNewStringWithLatin1Bytes(byte[] bytes);

    /**
     * Constructs a new {@code String} by decoding the specified byte array
     * using the specified {@code Charset}.
     * <p>
     * <b>WARNING: The caller of this method shall relinquish and transfer the
     * ownership of the byte array to the callee</b>, since the latter will not
     * make a copy.
     *
     * @param bytes the byte array source
     * @param cs the Charset
     * @return the newly created string
     * @throws CharacterCodingException for malformed or unmappable bytes
     * @throws NullPointerException If {@code bytes} or {@code cs} is null
     */
    String uncheckedNewStringOrThrow(byte[] bytes, Charset cs) throws CharacterCodingException;

    /**
     * {@return the sequence of bytes obtained by encoding the given string in
     * the specified {@code Charset}}
     * <p>
     * <b>WARNING: This method returns the {@code byte[]} backing the provided
     * {@code String}, if the input is ASCII. Hence, the returned byte array
     * must not be modified.</b>
     *
     * @param s the string to encode
     * @param cs the charset
     * @throws NullPointerException If {@code s} or {@code cs} is null
     * @throws CharacterCodingException for malformed input or unmappable characters
     */
    byte[] uncheckedGetBytesOrThrow(String s, Charset cs) throws CharacterCodingException;

    /**
     * Get the {@code char} at {@code index} in a {@code byte[]} in internal
     * UTF-16 representation.
     * <p>
     * <b>WARNING: This method does not perform any bound checks.</b>
     *
     * @param bytes the UTF-16 encoded bytes
     * @param index of the char to retrieve, 0 <= index < (bytes.length >> 1)
     * @return the char value
     */
    char uncheckedGetUTF16Char(byte[] bytes, int index);

    /**
     * Put the {@code ch} at {@code index} in a {@code byte[]} in internal
     * UTF-16 representation.
     * <p>
     * <b>WARNING: This method does not perform any bound checks.</b>
     *
     * @param bytes the UTF-16 encoded bytes
     * @param index of the char to retrieve, 0 <= index < (bytes.length >> 1)
     */
    void uncheckedPutCharUTF16(byte[] bytes, int index, int ch);

    /**
     * {@return the sequence of bytes obtained by encoding the given string in UTF-8}
     *
     * @param s the string to encode
     * @throws NullPointerException If {@code s} is null
     * @throws CharacterCodingException For malformed input or unmappable characters
     */
    byte[] getBytesUTF8OrThrow(String s) throws CharacterCodingException;

    /**
     * Inflated copy from {@code byte[]} to {@code char[]}, as defined by
     * {@code StringLatin1.inflate}.
     *
     * @implSpec Implementations of this method must perform bounds checks.
     */
    void inflateBytesToChars(byte[] src, int srcOff, char[] dst, int dstOff, int len);

    /**
     * Decodes ASCII from the source byte array into the destination
     * char array.
     *
     * @implSpec Implementations of this method must perform bounds checks.
     *
     * @return the number of bytes successfully decoded, at most len
     */
    int decodeASCII(byte[] src, int srcOff, char[] dst, int dstOff, int len);

    /**
     * Returns the initial `System.in` to determine if it is replaced
     * with `System.setIn(newIn)` method
     */
    InputStream initialSystemIn();

    /**
     * Returns the initial value of System.err.
     */
    PrintStream initialSystemErr();

    /**
     * Encodes as many ASCII codepoints as possible from the source
     * character array into the destination byte array, assuming that
     * the encoding is ASCII compatible.
     *
     * @param sa the source character array
     * @param sp the index of the source array to start reading from
     * @param da the target byte array
     * @param dp the index of the target array to start writing to
     * @param len the total number of characters to be encoded
     * @return the total number of characters successfully encoded
     * @throws NullPointerException if any of the provided arrays is null
     */
    int encodeASCII(char[] sa, int sp, byte[] da, int dp, int len);

    /**
     * Set the cause of Throwable
     * @param cause set t's cause to new value
     */
    void setCause(Throwable t, Throwable cause);

    /**
     * Get protection domain of the given Class
     */
    ProtectionDomain protectionDomain(Class<?> c);

    /**
     * Get a method handle of string concat helper method
     */
    MethodHandle stringConcatHelper(String name, MethodType methodType);

    /**
     * Creates helper for string concatenation.
     * <p>
     * <b>WARNING: The caller of this method shall relinquish and transfer the
     * ownership of the string array to the callee</b>, since the latter will not
     * make a copy.
     */
    Object uncheckedStringConcat1(String[] constants);

    /**
     * Get the string initial coder, When COMPACT_STRINGS is on, it returns 0, and when it is off, it returns 1.
     */
    byte stringInitCoder();

    /**
     * Get the Coder of String, which is used by StringConcatFactory to calculate the initCoder of constants
     */
    byte stringCoder(String str);

    /**
     * Join strings
     */
    String join(String prefix, String suffix, String delimiter, String[] elements, int size);

    /**
     * Concatenation of prefix and suffix characters to a String for early bootstrap
     */
    String concat(String prefix, Object value, String suffix);

    /*
     * Get the class data associated with the given class.
     * @param c the class
     * @see java.lang.invoke.MethodHandles.Lookup#defineHiddenClass(byte[], boolean, MethodHandles.Lookup.ClassOption...)
     */
    Object classData(Class<?> c);

    /**
     * Returns the {@link NativeLibraries} object associated with the provided class loader.
     * This is used by {@link SymbolLookup#loaderLookup()}.
     */
    NativeLibraries nativeLibrariesFor(ClassLoader loader);

    /**
     * Returns an array of all platform threads.
     */
    Thread[] getAllThreads();

    /**
     * Returns the ThreadContainer for a thread, may be null.
     */
    ThreadContainer threadContainer(Thread thread);

    /**
     * Starts a thread in the given ThreadContainer.
     */
    void start(Thread thread, ThreadContainer container);

    /**
     * Returns the top of the given thread's stackable scope stack.
     */
    StackableScope headStackableScope(Thread thread);

    /**
     * Sets the top of the current thread's stackable scope stack.
     */
    void setHeadStackableScope(StackableScope scope);

    /**
     * Returns the Thread object for the current platform thread. If the
     * current thread is a virtual thread then this method returns the carrier.
     */
    Thread currentCarrierThread();

    /**
     * Returns the value of the current carrier thread's copy of a thread-local.
     */
    <T> T getCarrierThreadLocal(CarrierThreadLocal<T> local);

    /**
     * Sets the value of the current carrier thread's copy of a thread-local.
     */
    <T> void setCarrierThreadLocal(CarrierThreadLocal<T> local, T value);

    /**
     * Removes the value of the current carrier thread's copy of a thread-local.
     */
    void removeCarrierThreadLocal(CarrierThreadLocal<?> local);

    /**
     * Returns the current thread's scoped values cache
     */
    Object[] scopedValueCache();

    /**
     * Sets the current thread's scoped values cache
     */
    void setScopedValueCache(Object[] cache);

    /**
     * Return the current thread's scoped value bindings.
     */
    Object scopedValueBindings();

    /**
     * Returns the native thread ID for the given platform thread or 0 if not set.
     */
    long nativeThreadID(Thread thread);

    /**
     * Sets the native thread ID for the current platform thread.
     */
    void setThreadNativeID(long id);

    /**
     * Returns the innermost mounted continuation
     */
    Continuation getContinuation(Thread thread);

    /**
     * Sets the innermost mounted continuation
     */
    void setContinuation(Thread thread, Continuation continuation);

    /**
     * The ContinuationScope of virtual thread continuations
     */
    ContinuationScope virtualThreadContinuationScope();

    /**
     * Parks the current virtual thread.
     * @throws WrongThreadException if the current thread is not a virtual thread
     */
    void parkVirtualThread();

    /**
     * Parks the current virtual thread for up to the given waiting time.
     * @param nanos the maximum number of nanoseconds to wait
     * @throws WrongThreadException if the current thread is not a virtual thread
     */
    void parkVirtualThread(long nanos);

    /**
     * Re-enables a virtual thread for scheduling. If the thread was parked then
     * it will be unblocked, otherwise its next attempt to park will not block
     * @param thread the virtual thread to unpark
     * @throws IllegalArgumentException if the thread is not a virtual thread
     * @throws RejectedExecutionException if the scheduler cannot accept a task
     */
    void unparkVirtualThread(Thread thread);

    /**
     * Returns the virtual thread default scheduler.
     */
    Executor virtualThreadDefaultScheduler();

    /**
     * Creates a new StackWalker
     */
    StackWalker newStackWalkerInstance(Set<StackWalker.Option> options,
                                       ContinuationScope contScope,
                                       Continuation continuation);
    /**
     * Returns '<loader-name>' @<id> if classloader has a name
     * explicitly set otherwise <qualified-class-name> @<id>
     */
    String getLoaderNameID(ClassLoader loader);

    /**
     * Copy the string bytes to an existing segment, avoiding intermediate copies.
     */
    void copyToSegmentRaw(String string, MemorySegment segment, long offset, int srcIndex, int srcLength);

    /**
     * Are the string bytes compatible with the given charset?
     */
    boolean bytesCompatible(String string, Charset charset, int srcIndex, int numChars);

    /**
     * Finish initialization of the StackTraceElement objects in a stack trace.
     */
    void finishInit(StackTraceElement[] stackTrace);
}
