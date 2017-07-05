/*
 * Copyright (c) 2000, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;

/**
 * An element in a stack trace, as returned by {@link
 * Throwable#getStackTrace()}.  Each element represents a single stack frame.
 * All stack frames except for the one at the top of the stack represent
 * a method invocation.  The frame at the top of the stack represents the
 * execution point at which the stack trace was generated.  Typically,
 * this is the point at which the throwable corresponding to the stack trace
 * was created.
 *
 * @since  1.4
 * @author Josh Bloch
 */
public final class StackTraceElement implements java.io.Serializable {
    // Normally initialized by VM (public constructor added in 1.5)
    private String moduleName;
    private String moduleVersion;
    private String declaringClass;
    private String methodName;
    private String fileName;
    private int    lineNumber;

    /**
     * Creates a stack trace element representing the specified execution
     * point. The {@link #getModuleName module name} and {@link
     * #getModuleVersion module version} of the stack trace element will
     * be {@code null}.
     *
     * @param declaringClass the fully qualified name of the class containing
     *        the execution point represented by the stack trace element
     * @param methodName the name of the method containing the execution point
     *        represented by the stack trace element
     * @param fileName the name of the file containing the execution point
     *         represented by the stack trace element, or {@code null} if
     *         this information is unavailable
     * @param lineNumber the line number of the source line containing the
     *         execution point represented by this stack trace element, or
     *         a negative number if this information is unavailable. A value
     *         of -2 indicates that the method containing the execution point
     *         is a native method
     * @throws NullPointerException if {@code declaringClass} or
     *         {@code methodName} is null
     * @since 1.5
     */
    public StackTraceElement(String declaringClass, String methodName,
                             String fileName, int lineNumber) {
        this(null, null, declaringClass, methodName, fileName, lineNumber);
    }

    /**
     * Creates a stack trace element representing the specified execution
     * point.
     *
     * @param moduleName the module name if the class containing the
     *        execution point represented by the stack trace is in a named
     *        module; can be {@code null}
     * @param moduleVersion the module version if the class containing the
     *        execution point represented by the stack trace is in a named
     *        module that has a version; can be {@code null}
     * @param declaringClass the fully qualified name of the class containing
     *        the execution point represented by the stack trace element
     * @param methodName the name of the method containing the execution point
     *        represented by the stack trace element
     * @param fileName the name of the file containing the execution point
     *        represented by the stack trace element, or {@code null} if
     *        this information is unavailable
     * @param lineNumber the line number of the source line containing the
     *        execution point represented by this stack trace element, or
     *        a negative number if this information is unavailable. A value
     *        of -2 indicates that the method containing the execution point
     *        is a native method
     * @throws NullPointerException if {@code declaringClass} is {@code null}
     *         or {@code methodName} is {@code null}
     * @since 9
     */
    public StackTraceElement(String moduleName, String moduleVersion,
                             String declaringClass, String methodName,
                             String fileName, int lineNumber) {
        this.moduleName     = moduleName;
        this.moduleVersion  = moduleVersion;
        this.declaringClass = Objects.requireNonNull(declaringClass, "Declaring class is null");
        this.methodName     = Objects.requireNonNull(methodName, "Method name is null");
        this.fileName       = fileName;
        this.lineNumber     = lineNumber;
    }

    /**
     * Returns the name of the source file containing the execution point
     * represented by this stack trace element.  Generally, this corresponds
     * to the {@code SourceFile} attribute of the relevant {@code class}
     * file (as per <i>The Java Virtual Machine Specification</i>, Section
     * 4.7.7).  In some systems, the name may refer to some source code unit
     * other than a file, such as an entry in source repository.
     *
     * @return the name of the file containing the execution point
     *         represented by this stack trace element, or {@code null} if
     *         this information is unavailable.
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Returns the line number of the source line containing the execution
     * point represented by this stack trace element.  Generally, this is
     * derived from the {@code LineNumberTable} attribute of the relevant
     * {@code class} file (as per <i>The Java Virtual Machine
     * Specification</i>, Section 4.7.8).
     *
     * @return the line number of the source line containing the execution
     *         point represented by this stack trace element, or a negative
     *         number if this information is unavailable.
     */
    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * Returns the module name of the module containing the execution point
     * represented by this stack trace element.
     *
     * @return the module name of the {@code Module} containing the execution
     *         point represented by this stack trace element; {@code null}
     *         if the module name is not available.
     * @since 9
     * @see java.lang.reflect.Module#getName()
     */
    public String getModuleName() {
        return moduleName;
    }

    /**
     * Returns the module version of the module containing the execution point
     * represented by this stack trace element.
     *
     * @return the module version of the {@code Module} containing the execution
     *         point represented by this stack trace element; {@code null}
     *         if the module version is not available.
     * @since 9
     * @see java.lang.module.ModuleDescriptor.Version
     */
    public String getModuleVersion() {
        return moduleVersion;
    }

    /**
     * Returns the fully qualified name of the class containing the
     * execution point represented by this stack trace element.
     *
     * @return the fully qualified name of the {@code Class} containing
     *         the execution point represented by this stack trace element.
     */
    public String getClassName() {
        return declaringClass;
    }

    /**
     * Returns the name of the method containing the execution point
     * represented by this stack trace element.  If the execution point is
     * contained in an instance or class initializer, this method will return
     * the appropriate <i>special method name</i>, {@code <init>} or
     * {@code <clinit>}, as per Section 3.9 of <i>The Java Virtual
     * Machine Specification</i>.
     *
     * @return the name of the method containing the execution point
     *         represented by this stack trace element.
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * Returns true if the method containing the execution point
     * represented by this stack trace element is a native method.
     *
     * @return {@code true} if the method containing the execution point
     *         represented by this stack trace element is a native method.
     */
    public boolean isNativeMethod() {
        return lineNumber == -2;
    }

    /**
     * Returns a string representation of this stack trace element.  The
     * format of this string depends on the implementation, but the following
     * examples may be regarded as typical:
     * <ul>
     * <li>
     *   {@code "MyClass.mash(my.module@9.0/MyClass.java:101)"} - Here,
     *   {@code "MyClass"} is the <i>fully-qualified name</i> of the class
     *   containing the execution point represented by this stack trace element,
     *   {@code "mash"} is the name of the method containing the execution
     *   point, {@code "my.module"} is the module name, {@code "9.0"} is the
     *   module version, and {@code "101"} is the line number of the source
     *   line containing the execution point.
     * <li>
     *   {@code "MyClass.mash(my.module@9.0/MyClass.java)"} - As above, but the
     *   line number is unavailable.
     * <li>
     *   {@code "MyClass.mash(my.module@9.0/Unknown Source)"} - As above, but
     *   neither the file name nor the line  number are available.
     * <li>
     *   {@code "MyClass.mash(my.module@9.0/Native Method)"} - As above, but
     *   neither the file name nor the line  number are available, and the
     *   method containing the execution point is known to be a native method.
     * </ul>
     * If the execution point is not in a named module, {@code "my.module@9.0/"}
     * will be omitted from the above.
     *
     * @see    Throwable#printStackTrace()
     */
    public String toString() {
        String mid = "";
        if (moduleName != null) {
            mid = moduleName;
            if (moduleVersion != null)
                mid += "@" + moduleVersion;
            mid += "/";
        }
        return getClassName() + "." + methodName + "(" + mid +
             (isNativeMethod() ? "Native Method)" :
              (fileName != null && lineNumber >= 0 ?
               fileName + ":" + lineNumber + ")" :
                (fileName != null ?  ""+fileName+")" : "Unknown Source)")));
    }

    /**
     * Returns true if the specified object is another
     * {@code StackTraceElement} instance representing the same execution
     * point as this instance.  Two stack trace elements {@code a} and
     * {@code b} are equal if and only if:
     * <pre>{@code
     *     equals(a.getFileName(), b.getFileName()) &&
     *     a.getLineNumber() == b.getLineNumber()) &&
     *     equals(a.getModuleName(), b.getModuleName()) &&
     *     equals(a.getModuleVersion(), b.getModuleVersion()) &&
     *     equals(a.getClassName(), b.getClassName()) &&
     *     equals(a.getMethodName(), b.getMethodName())
     * }</pre>
     * where {@code equals} has the semantics of {@link
     * java.util.Objects#equals(Object, Object) Objects.equals}.
     *
     * @param  obj the object to be compared with this stack trace element.
     * @return true if the specified object is another
     *         {@code StackTraceElement} instance representing the same
     *         execution point as this instance.
     */
    public boolean equals(Object obj) {
        if (obj==this)
            return true;
        if (!(obj instanceof StackTraceElement))
            return false;
        StackTraceElement e = (StackTraceElement)obj;
        return e.declaringClass.equals(declaringClass) &&
            Objects.equals(moduleName, e.moduleName) &&
            Objects.equals(moduleVersion, e.moduleVersion) &&
            e.lineNumber == lineNumber &&
            Objects.equals(methodName, e.methodName) &&
            Objects.equals(fileName, e.fileName);
    }

    /**
     * Returns a hash code value for this stack trace element.
     */
    public int hashCode() {
        int result = 31*declaringClass.hashCode() + methodName.hashCode();
        result = 31*result + Objects.hashCode(moduleName);
        result = 31*result + Objects.hashCode(moduleVersion);
        result = 31*result + Objects.hashCode(fileName);
        result = 31*result + lineNumber;
        return result;
    }

    private static final long serialVersionUID = 6992337162326171013L;
}
