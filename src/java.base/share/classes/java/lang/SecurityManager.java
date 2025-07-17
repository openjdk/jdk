/*
 * Copyright (c) 1995, 2024, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import java.io.FileDescriptor;
import java.net.InetAddress;
import java.security.AccessController;
import java.security.Permission;
import java.util.Set;

/**
 * SecurityManager was originally specified to allow an application implement
 * a security policy. It allowed an application to determine, before performing
 * a possibly unsafe or security sensitive operation, whether the operation was
 * being attempted in a security context that allowed the operation.
 * This feature no longer exists.
 *
 * @since   1.0
 * @deprecated There is no replacement for this class.
 */
@Deprecated(since="17", forRemoval=true)
public class SecurityManager {

    /**
     * Constructs a new {@code SecurityManager}. Setting a security manager with
     * {@link System#setSecurityManager(SecurityManager)} is not supported.
     */
    public SecurityManager() { }

    /**
     * Returns the current execution stack as an array of classes.
     * <p>
     * The length of the array is the number of methods on the execution
     * stack. The element at index {@code 0} is the class of the
     * currently executing method, the element at index {@code 1} is
     * the class of that method's caller, and so on.
     *
     * @apiNote The {@code StackWalker} class can be used as a replacement
     * for this method.
     *
     * @return  the execution stack.
     */
    protected Class<?>[] getClassContext() {
        return StackWalkerHolder.STACK_WALKER
                .walk(s -> s.map(StackWalker.StackFrame::getDeclaringClass)
                        .skip(1L)
                        .toArray(Class[]::new));
    }

    private static class StackWalkerHolder {
        static final StackWalker STACK_WALKER =
            StackWalker.getInstance(
                Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE,
                       StackWalker.Option.DROP_METHOD_INFO));
    }

    /**
     * Returns an {@code AccessControlContext} where the {@code checkPermission}
     * method always throws an {@code AccessControlException} and the
     * {@code getDomainCombiner} method always returns {@code null}.
     *
     * @return  an {@code AccessControlContext} as specified above
     * @see     java.security.AccessControlContext AccessControlContext
     * @apiNote This method originally returned a snapshot of the current
     *       calling context, which included the current thread's access
     *       control context and any limited privilege scope. This method has
     *       been changed to always return an innocuous
     *       {@code AccessControlContext} that fails all permission checks.
     *       {@linkplain SecurityManager The Security Manager} is no longer
     *       supported. There is no replacement for the Security Manager or
     *       this method.
     */
    @SuppressWarnings("removal")
    public Object getSecurityContext() {
        return AccessController.getContext();
    }

    /**
     * Throws {@code SecurityException}.
     *
     * @param     perm   ignored
     * @throws    SecurityException always
     * @since     1.2
     */
    public void checkPermission(Permission perm) {
        throw new SecurityException();
    }

    /**
     * Throws {@code SecurityException}.
     *
     * @param      perm      ignored
     * @param      context   ignored
     * @throws     SecurityException always
     * @since      1.2
     */
    public void checkPermission(Permission perm, Object context) {
        throw new SecurityException();
    }

    /**
     * Throws {@code SecurityException}.
     *
     * @throws    SecurityException always
     */
    public void checkCreateClassLoader() {
        throw new SecurityException();
    }

    /**
     * Throws {@code SecurityException}.
     *
     * @param      t   ignored
     * @throws     SecurityException always
     */
    public void checkAccess(Thread t) {
        throw new SecurityException();
    }

    /**
     * Throws {@code SecurityException}.
     *
     * @param      g   ignored
     * @throws     SecurityException always
     */
    public void checkAccess(ThreadGroup g) {
        throw new SecurityException();
    }

    /**
     * Throws {@code SecurityException}.
     *
     * @param      status   ignored
     * @throws    SecurityException always
     */
    public void checkExit(int status) {
        throw new SecurityException();
    }

    /**
     * Throws {@code SecurityException}.
     *
     * @param      cmd   ignored
     * @throws     SecurityException always
     */
    public void checkExec(String cmd) {
        throw new SecurityException();
    }

    /**
     * Throws {@code SecurityException}.
     *
     * @param      lib   ignored
     * @throws     SecurityException always
     */
    public void checkLink(String lib) {
        throw new SecurityException();
    }

    /**
     * Throws {@code SecurityException}.
     *
     * @param      fd   the system-dependent file descriptor
     * @throws     SecurityException always
     */
    public void checkRead(FileDescriptor fd) {
        throw new SecurityException();
    }

    /**
     * Throws {@code SecurityException}.
     *
     * @param      file   ignored
     * @throws     SecurityException always
     */
    public void checkRead(String file) {
        throw new SecurityException();
    }

    /**
     * Throws {@code SecurityException}.
     *
     * @param      file      ignored
     * @param      context   ignored
     * @throws     SecurityException always
     */
    public void checkRead(String file, Object context) {
        throw new SecurityException();
    }

    /**
     * Throws {@code SecurityException}.
     *
     * @param     fd   ignored
     * @throws    SecurityException always
     */
    public void checkWrite(FileDescriptor fd) {
        throw new SecurityException();
    }

    /**
     * Throws {@code SecurityException}.
     *
     * @param      file   ignored
     * @throws     SecurityException always
     */
    public void checkWrite(String file) {
        throw new SecurityException();
    }

    /**
     * Throws {@code SecurityException}.
     *
     * @param      file   ignored
     * @throws     SecurityException always
     */
    public void checkDelete(String file) {
        throw new SecurityException();
    }

    /**
     * Throws {@code SecurityException}.
     *
     * @param      host   ignored
     * @param      port   ignored
     * @throws     SecurityException always
     */
    public void checkConnect(String host, int port) {
        throw new SecurityException();
    }

    /**
     * Throws {@code SecurityException}.
     *
     * @param      host      ignored
     * @param      port      ignored
     * @param      context   ignored
     * @throws     SecurityException always
     */
    public void checkConnect(String host, int port, Object context) {
        throw new SecurityException();
    }

    /**
     * Throws {@code SecurityException}.
     *
     * @param      port   ignored
     * @throws     SecurityException always
     */
    public void checkListen(int port) {
        throw new SecurityException();
    }

    /**
     * Throws {@code SecurityException}.
     *
     * @param      host   ignored
     * @param      port   ignored
     * @throws     SecurityException always
     */
    public void checkAccept(String host, int port) {
        throw new SecurityException();
    }

    /**
     * Throws {@code SecurityException}.
     *
     * @param      maddr  ignored
     * @throws     SecurityException always
     * @since      1.1
     */
    public void checkMulticast(InetAddress maddr) {
        throw new SecurityException();
    }

    /**
     * Throws {@code SecurityException}.
     *
     * @param      maddr  ignored
     * @param      ttl    ignored
     * @throws     SecurityException always
     * @since      1.1
     */
    public void checkMulticast(InetAddress maddr, byte ttl) {
        throw new SecurityException();
    }

    /**
     * Throws {@code SecurityException}.
     *
     * @throws     SecurityException always
     */
    public void checkPropertiesAccess() {
        throw new SecurityException();
    }

    /**
     * Throws {@code SecurityException}.
     *
     * @param      key  ignored
     * @throws     SecurityException always
     */
    public void checkPropertyAccess(String key) {
        throw new SecurityException();
    }

    /**
     * Throws {@code SecurityException}.
     *
     * @throws     SecurityException always
     * @since   1.1
     */
    public void checkPrintJobAccess() {
        throw new SecurityException();
    }

    /**
     * Throws {@code SecurityException}.
     *
     * @param      pkg   ignored
     * @throws     SecurityException always
     */
    public void checkPackageAccess(String pkg) {
        throw new SecurityException();
    }

    /**
     * Throws {@code SecurityException}.
     *
     * @param      pkg   ignored
     * @throws     SecurityException always
     */
    public void checkPackageDefinition(String pkg) {
        throw new SecurityException();
    }

    /**
     * Throws {@code SecurityException}.
     *
     * @throws     SecurityException always
     */
    public void checkSetFactory() {
        throw new SecurityException();
    }

    /**
     * Throws {@code SecurityException}.
     *
     * @param     target ignored
     * @throws    SecurityException always
     * @since   1.1
     */
    public void checkSecurityAccess(String target) {
        throw new SecurityException();
    }

    /**
     * {@return the current Thread's {@code ThreadGroup}}
     * @since   1.1
     */
    public ThreadGroup getThreadGroup() {
        return Thread.currentThread().getThreadGroup();
    }
}
