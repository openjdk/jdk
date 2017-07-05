/*
 * Copyright 2000-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.lang;

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
    private String declaringClass;
    private String methodName;
    private String fileName;
    private int    lineNumber;

    /**
     * Creates a stack trace element representing the specified execution
     * point.
     *
     * @param declaringClass the fully qualified name of the class containing
     *        the execution point represented by the stack trace element
     * @param methodName the name of the method containing the execution point
     *        represented by the stack trace element
     * @param fileName the name of the file containing the execution point
     *         represented by the stack trace element, or <tt>null</tt> if
     *         this information is unavailable
     * @param lineNumber the line number of the source line containing the
     *         execution point represented by this stack trace element, or
     *         a negative number if this information is unavailable. A value
     *         of -2 indicates that the method containing the execution point
     *         is a native method
     * @throws NullPointerException if <tt>declaringClass</tt> or
     *         <tt>methodName</tt> is null
     * @since 1.5
     */
    public StackTraceElement(String declaringClass, String methodName,
                             String fileName, int lineNumber) {
        if (declaringClass == null)
            throw new NullPointerException("Declaring class is null");
        if (methodName == null)
            throw new NullPointerException("Method name is null");

        this.declaringClass = declaringClass;
        this.methodName     = methodName;
        this.fileName       = fileName;
        this.lineNumber     = lineNumber;
    }

    /**
     * Returns the name of the source file containing the execution point
     * represented by this stack trace element.  Generally, this corresponds
     * to the <tt>SourceFile</tt> attribute of the relevant <tt>class</tt>
     * file (as per <i>The Java Virtual Machine Specification</i>, Section
     * 4.7.7).  In some systems, the name may refer to some source code unit
     * other than a file, such as an entry in source repository.
     *
     * @return the name of the file containing the execution point
     *         represented by this stack trace element, or <tt>null</tt> if
     *         this information is unavailable.
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Returns the line number of the source line containing the execution
     * point represented by this stack trace element.  Generally, this is
     * derived from the <tt>LineNumberTable</tt> attribute of the relevant
     * <tt>class</tt> file (as per <i>The Java Virtual Machine
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
     * Returns the fully qualified name of the class containing the
     * execution point represented by this stack trace element.
     *
     * @return the fully qualified name of the <tt>Class</tt> containing
     *         the execution point represented by this stack trace element.
     */
    public String getClassName() {
        return declaringClass;
    }

    /**
     * Returns the name of the method containing the execution point
     * represented by this stack trace element.  If the execution point is
     * contained in an instance or class initializer, this method will return
     * the appropriate <i>special method name</i>, <tt>&lt;init&gt;</tt> or
     * <tt>&lt;clinit&gt;</tt>, as per Section 3.9 of <i>The Java Virtual
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
     * @return <tt>true</tt> if the method containing the execution point
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
     *   <tt>"MyClass.mash(MyClass.java:9)"</tt> - Here, <tt>"MyClass"</tt>
     *   is the <i>fully-qualified name</i> of the class containing the
     *   execution point represented by this stack trace element,
     *   <tt>"mash"</tt> is the name of the method containing the execution
     *   point, <tt>"MyClass.java"</tt> is the source file containing the
     *   execution point, and <tt>"9"</tt> is the line number of the source
     *   line containing the execution point.
     * <li>
     *   <tt>"MyClass.mash(MyClass.java)"</tt> - As above, but the line
     *   number is unavailable.
     * <li>
     *   <tt>"MyClass.mash(Unknown Source)"</tt> - As above, but neither
     *   the file name nor the line  number are available.
     * <li>
     *   <tt>"MyClass.mash(Native Method)"</tt> - As above, but neither
     *   the file name nor the line  number are available, and the method
     *   containing the execution point is known to be a native method.
     * </ul>
     * @see    Throwable#printStackTrace()
     */
    public String toString() {
        return getClassName() + "." + methodName +
            (isNativeMethod() ? "(Native Method)" :
             (fileName != null && lineNumber >= 0 ?
              "(" + fileName + ":" + lineNumber + ")" :
              (fileName != null ?  "("+fileName+")" : "(Unknown Source)")));
    }

    /**
     * Returns true if the specified object is another
     * <tt>StackTraceElement</tt> instance representing the same execution
     * point as this instance.  Two stack trace elements <tt>a</tt> and
     * <tt>b</tt> are equal if and only if:
     * <pre>
     *     equals(a.getFileName(), b.getFileName()) &&
     *     a.getLineNumber() == b.getLineNumber()) &&
     *     equals(a.getClassName(), b.getClassName()) &&
     *     equals(a.getMethodName(), b.getMethodName())
     * </pre>
     * where <tt>equals</tt> is defined as:
     * <pre>
     *     static boolean equals(Object a, Object b) {
     *         return a==b || (a != null && a.equals(b));
     *     }
     * </pre>
     *
     * @param  obj the object to be compared with this stack trace element.
     * @return true if the specified object is another
     *         <tt>StackTraceElement</tt> instance representing the same
     *         execution point as this instance.
     */
    public boolean equals(Object obj) {
        if (obj==this)
            return true;
        if (!(obj instanceof StackTraceElement))
            return false;
        StackTraceElement e = (StackTraceElement)obj;
        return e.declaringClass.equals(declaringClass) && e.lineNumber == lineNumber
            && eq(methodName, e.methodName) && eq(fileName, e.fileName);
    }

    private static boolean eq(Object a, Object b) {
        return a==b || (a != null && a.equals(b));
    }

    /**
     * Returns a hash code value for this stack trace element.
     */
    public int hashCode() {
        int result = 31*declaringClass.hashCode() + methodName.hashCode();
        result = 31*result + (fileName == null ?   0 : fileName.hashCode());
        result = 31*result + lineNumber;
        return result;
    }

    private static final long serialVersionUID = 6992337162326171013L;
}
