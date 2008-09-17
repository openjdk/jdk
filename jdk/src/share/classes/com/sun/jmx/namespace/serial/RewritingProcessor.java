/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.jmx.namespace.serial;


import javax.management.ObjectInstance;
import javax.management.ObjectName;

/**
 * An object that can rewrite ObjectNames contained in input/output
 * parameters when entering/leaving a {@link javax.management.namespace
 * namespace}.
 * <p>When entering a {@link javax.management.namespace
 *    namespace}, the {@code namespace} prefix is stripped from
 *    ObjectNames contained in input parameters. When leaving a
 *    {@code namespace},
 *    the {@code namespace} prefix is prepended to the ObjectNames contained in
 *    the result parameters returned from that {@code namespace}.
 * </p>
 * <p>Objects that need to perform these operations usually use a
 *    {@code RewritingProcessor} for that purpose.<br>
 *    The {@code RewritingProcessor} allows a somewhat larger
 *    transformation in which part of a prefix {@link #newRewritingProcessor
 *    remove} can be replaced by another prefix {@link #newRewritingProcessor
 *    add}. The transformation described above correspond to the case where
 *    {@code remove} is the stripped {@link javax.management.namespace
 *    namespace} prefix (removed when entering the {@code namespace}) and
 *    {@code add} is the empty String {@code ""}.
 *    <br>
 *    It is interesting to note that {@link
 *    javax.management.JMXNamespaces#narrowToNamespace narrowToNamespace}
 *    operations use the inverse transformation (that is, {@code remove} is
 *    the empty String {@code ""} and {@code add} is the {@link
 *    javax.management.namespace namespace} prefix).
 *    <br>
 *    On a more general scale, {@link #rewriteInput rewriteInput} removes
 *    {@link #newRewritingProcessor remove} and the prepend {@link
 *     #newRewritingProcessor add}, and {@link #rewriteOutput rewriteOutput}
 *    does the opposite, removing {@link #newRewritingProcessor add}, and
 *    then adding {@link #newRewritingProcessor remove}.
 *    <br>
 *    An implementation of {@code RewritingProcessor} should make sure that
 *    <code>rewriteInput(rewriteOutput(x,clp),clp)</code> and
 *    <code>rewriteOutput(rewriteInput(x,clp),clp)</code> always return
 *    {@code x} or an exact clone of {@code x}.
 * </p>
 * <p>A default implementation of {@code RewritingProcessor} based on
 * Java Object Serialization can be
 * obtained from {@link #newRewritingProcessor newRewritingProcessor}.
 * </p>
 * <p>
 * By default, the instances of {@code RewritingProcessor} returned by
 * {@link #newRewritingProcessor newRewritingProcessor} will rewrite
 * ObjectNames contained in instances of classes they don't know about by
 * serializing and then deserializing such object instances. This will
 * happen even if such instances don't - or can't contain ObjectNames,
 * because the default implementation of {@code RewritingProcessor} will
 * not be able to determine whether instances of such classes can/do contain
 * instance of ObjectNames before serializing/deserializing them.
 * </p>
 * <p>If you are using custom classes that the default implementation of
 * {@code RewritingProcessor} don't know about, it can be interesting to
 * prevent an instance of {@code RewritingProcessor} to serialize/deserialize
 * instances of such classes for nothing. In that case, you could customize
 * the behavior of such a {@code RewritingProcessor} by wrapping it in a
 * custom subclass of {@code RewritingProcessor} as shown below:
 * <pre>
 * public class MyRewritingProcessor extends RewritingProcessor {
 *      MyRewritingProcessor(String remove, String add) {
 *          this(RewritingProcessor.newRewritingProcessor(remove,add));
 *      }
 *      MyRewritingProcessor(RewritingProcessor delegate) {
 *          super(delegate);
 *      }
 *
 *  <T> T rewriteInput(T input) {
 *          if (input == null) return null;
 *          if (MyClass.equals(input.getClass())) {
 *              // I know that MyClass doesn't contain any ObjectName
 *              return (T) input;
 *          }
 *          return super.rewriteInput(input);
 *      }
 *  <T> T rewriteOutput(T result) {
 *          if (result == null) return null;
 *          if (MyClass.equals(result.getClass())) {
 *              // I know that MyClass doesn't contain any ObjectName
 *              return (T) result;
 *          }
 *          return super.rewriteOutput(result);
 *      }
 * }
 * </pre>
 * </p>
 * <p>Such a subclass may also provide an alternate way of rewriting
 *    custom subclasses for which rewriting is needed - for instance:
 * <pre>
 * public class MyRewritingProcessor extends RewritingProcessor {
 *      MyRewritingProcessor(String remove, String add) {
 *          this(RewritingProcessor.newRewritingProcessor(remove,add));
 *      }
 *      MyRewritingProcessor(RewritingProcessor delegate) {
 *          super(delegate);
 *      }
 *
 *  <T> T rewriteInput(T input) {
 *          if (input == null) return null;
 *          if (MyClass.equals(input.getClass())) {
 *              // I know that MyClass doesn't contain any ObjectName
 *              return (T) input;
 *          } else if (MyOtherClass.equals(input.getClass())) {
 *              // Returns a new instance in which ObjectNames have been
 *              // replaced.
 *              final ObjectName aname = ((MyOtherClass)input).getName();
 *              return (T) (new MyOtherClass(super.rewriteInput(aname)));
 *          }
 *          return super.rewriteInput(input,clp);
 *      }
 *  <T> T rewriteOutput(T result) {
 *          if (result == null) return null;
 *          if (MyClass.equals(result.getClass())) {
 *              // I know that MyClass doesn't contain any ObjectName
 *              return (T) result;
 *          } else if (MyOtherClass.equals(result.getClass())) {
 *              // Returns a new instance in which ObjectNames have been
 *              // replaced.
 *              final ObjectName aname = ((MyOtherClass)result).getName();
 *              return (T) (new MyOtherClass(super.rewriteOutput(aname)));
 *          }
 *          return super.rewriteOutput(result,clp);
 *      }
 * }
 * </pre>
 * </p>
 * <p>If your application only uses {@link javax.management.MXBean MXBeans},
 * or MBeans using simple types, and doesn't define any custom subclass of
 * {@link javax.management.Notification}, you should never write such
 * such {@code RewitingProcessor} implementations.
 * </p>
 * <p><b>
 * This API is a Sun internal API and is subject to changes without notice.
 * </b></p>
 * @since 1.7
 */
public abstract class RewritingProcessor {
    /**
     * A logger for this class.
     **/
    private final RewritingProcessor delegate;

    /**
     * Creates a new instance of RewritingProcessor.
     * <p>This is equivalent to calling {@link
     * #RewritingProcessor(RewritingProcessor) RewritingProcessor(null)}.
     * </p>
     **/
    protected RewritingProcessor() {
        this(null);
    }

    /**
     * Creates a new instance of RewritingProcessor, with a delegate.
     * @param delegate a {@code RewritingProcessor} to which all the
     *        calls will be delegated. When implementing a subclass
     *        of  {@code RewritingProcessor}, calling {@link
     *        #rewriteInput super.rewriteInput} will invoke
     *        {@code delegate.rewriteInput} and calling {@link
     *        #rewriteOutput super.rewriteOutput} will invoke
     *        {@code delegate.rewriteOutput}.
     *
     **/
    protected RewritingProcessor(RewritingProcessor delegate) {
        this.delegate = delegate;
    }

    /**
     * Rewrites ObjectNames when {@link RewritingProcessor leaving} a {@link
     * javax.management.namespace namespace}.
     * <p>
     * Returns {@code obj}, if it is known that {@code obj} doesn't contain
     * any ObjectName, or a new copied instance of {@code obj} in which
     * ObjectNames (if any) will have been rewritten, if {@code obj} contains
     * ObjectNames, or if it is not known whether {@code obj} contains
     * ObjectNames or not.
     * </p>
     * <p>
     * The default implementation of this method is as follows: if the
     * {@link #RewritingProcessor(RewritingProcessor) delegate} is {@code
     * null}, throws an {@link IllegalArgumentException}. Otherwise,
     * returns {@code delegate.rewriteOutput(obj)}.
     * </p>
     * <p>This behavior can be overridden by subclasses as shown in this
     * class {@link RewritingProcessor description}.
     * </p>
     * @param obj The result to be rewritten if needed.
     *
     * @return {@code obj}, or a clone of {@code obj} in which ObjectNames
     *         have been rewritten. See this class {@link RewritingProcessor
     *         description} for more details.
     * @throws IllegalArgumentException if this implementation does not know
     *         how to rewrite the object.
     **/
    public <T> T rewriteOutput(T obj) {
        if (obj == null) return null;
        if (delegate != null)
            return delegate.rewriteOutput(obj);
        throw new IllegalArgumentException("can't rewrite "+
                obj.getClass().getName());
    }

    /**
     * Rewrites ObjectNames when {@link RewritingProcessor entering} a {@link
     * javax.management.namespace namespace}.
     * <p>
     * Returns {@code obj}, if it is known that {@code obj} doesn't contain
     * any ObjectName, or a new copied instance of {@code obj} in which
     * ObjectNames (if any) will have been rewritten, if {@code obj} contains
     * ObjectNames, or if it is not known whether {@code obj} contains
     * ObjectNames or not.
     * </p>
     * <p>
     * The default implementation of this method is as follows: if the
     * {@link #RewritingProcessor(RewritingProcessor) delegate} is {@code
     * null}, throws an {@link IllegalArgumentException}. Otherwise,
     * returns {@code delegate.rewriteInput(obj)}.
     * </p>
     * <p>This behavior can be overridden by subclasses as shown in this
     * class {@link RewritingProcessor description}.
     * </p>
     * @param obj The result to be rewritten if needed.
     * @return {@code obj}, or a clone of {@code obj} in which ObjectNames
     *         have been rewritten. See this class {@link RewritingProcessor
     *         description} for more details.
     * @throws IllegalArgumentException if this implementation does not know
     *         how to rewrite the object.
     **/
    public <T> T rewriteInput(T obj) {
        if (obj == null) return null;
        if (delegate != null)
            return delegate.rewriteInput(obj);
        throw new IllegalArgumentException("can't rewrite "+
                obj.getClass().getName());
    }

    /**
     * Translate a routing ObjectName from the target (calling) context to
     * the source (called) context when {@link RewritingProcessor entering} a
     * {@link javax.management.namespace namespace}.
     * <p>
     * The default implementation of this method is as follows: if the
     * {@link #RewritingProcessor(RewritingProcessor) delegate} is {@code
     * null}, throws an {@link IllegalArgumentException}. Otherwise,
     * returns {@code delegate.toSourceContext(targetName)}.
     * </p>
     * <p>This behavior can be overridden by subclasses as shown in this
     * class {@link RewritingProcessor description}.
     * </p>
     * @param targetName The routing target ObjectName to translate.
     * @return The ObjectName translated to the source context.
     * @throws IllegalArgumentException if this implementation does not know
     *         how to rewrite the object.
     **/
    public ObjectName toSourceContext(ObjectName targetName) {
        if (delegate != null)
            return delegate.toSourceContext(targetName);
        throw new IllegalArgumentException("can't rewrite targetName: "+
               " no delegate.");
    }

    /**
     * Translate an ObjectName returned from the source context into
     * the target (calling) context when {@link RewritingProcessor leaving} a
     * {@link javax.management.namespace namespace}.
     * <p>
     * The default implementation of this method is as follows: if the
     * {@link #RewritingProcessor(RewritingProcessor) delegate} is {@code
     * null}, throws an {@link IllegalArgumentException}. Otherwise,
     * returns {@code delegate.toTargetContext(sourceName)}.
     * </p>
     * <p>This behavior can be overridden by subclasses as shown in this
     * class {@link RewritingProcessor description}.
     * </p>
     * @param sourceName The routing source ObjectName to translate to the
     *        target context.
     * @return The ObjectName translated to the target context.
     * @throws IllegalArgumentException if this implementation does not know
     *         how to rewrite the object.
     **/
    public ObjectName toTargetContext(ObjectName sourceName) {
        if (delegate != null)
            return delegate.toTargetContext(sourceName);
        throw new IllegalArgumentException("can't rewrite sourceName: "+
               " no delegate.");
    }

    /**
     * Translate an ObjectInstance returned from the source context into
     * the target (calling) context when {@link RewritingProcessor leaving} a
     * {@link javax.management.namespace namespace}.
     * <p>
     * The default implementation of this method is as follows: if the
     * {@link #RewritingProcessor(RewritingProcessor) delegate} is {@code
     * null}, throws an {@link IllegalArgumentException}. Otherwise,
     * returns {@code delegate.toTargetContext(sourceMoi)}.
     * </p>
     * <p>This behavior can be overridden by subclasses as shown in this
     * class {@link RewritingProcessor description}.
     * </p>
     * @param sourceMoi The routing source ObjectInstance to translate.
     * @return The ObjectInstance translated to the target context.
     * @throws IllegalArgumentException if this implementation does not know
     *         how to rewrite the object.
     **/
    public ObjectInstance toTargetContext(ObjectInstance sourceMoi) {
        if (delegate != null)
            return delegate.toTargetContext(sourceMoi);
        throw new IllegalArgumentException("can't rewrite sourceName: "+
               " no delegate.");
    }

    /**
     * Creates a new default instance of {@link RewritingProcessor}.
     * @param remove The prefix to remove from {@link ObjectName ObjectNames}
     *        when {@link RewritingProcessor entering} the {@link
     *        javax.management.namespace namespace}.
     * @param add The prefix to add to {@link ObjectName ObjectNames}
     *        when {@link RewritingProcessor entering} the {@link
     *        javax.management.namespace namespace} (this is performed
     *        after having removed the {@code remove} prefix.
     * @return A new {@link RewritingProcessor} processor object that will
     *         perform the requested operation, using Java serialization if
     *         necessary.
     **/
    public static RewritingProcessor newRewritingProcessor(String remove,
            String add) {
        return new DefaultRewritingProcessor(remove,add);
    }

}
