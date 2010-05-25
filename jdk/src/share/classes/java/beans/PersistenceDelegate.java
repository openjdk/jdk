/*
 * Copyright (c) 2000, 2010, Oracle and/or its affiliates. All rights reserved.
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
package java.beans;

/**
 * The PersistenceDelegate class takes the responsibility
 * for expressing the state of an instance of a given class
 * in terms of the methods in the class's public API. Instead
 * of associating the responsibility of persistence with
 * the class itself as is done, for example, by the
 * <code>readObject</code> and <code>writeObject</code>
 * methods used by the <code>ObjectOutputStream</code>, streams like
 * the <code>XMLEncoder</code> which
 * use this delegation model can have their behavior controlled
 * independently of the classes themselves. Normally, the class
 * is the best place to put such information and conventions
 * can easily be expressed in this delegation scheme to do just that.
 * Sometimes however, it is the case that a minor problem
 * in a single class prevents an entire object graph from
 * being written and this can leave the application
 * developer with no recourse but to attempt to shadow
 * the problematic classes locally or use alternative
 * persistence techniques. In situations like these, the
 * delegation model gives a relatively clean mechanism for
 * the application developer to intervene in all parts of the
 * serialization process without requiring that modifications
 * be made to the implementation of classes which are not part
 * of the application itself.
 * <p>
 * In addition to using a delegation model, this persistence
 * scheme differs from traditional serialization schemes
 * in requiring an analog of the <code>writeObject</code>
 * method without a corresponding <code>readObject</code>
 * method. The <code>writeObject</code> analog encodes each
 * instance in terms of its public API and there is no need to
 * define a <code>readObject</code> analog
 * since the procedure for reading the serialized form
 * is defined by the semantics of method invocation as laid
 * out in the Java Language Specification.
 * Breaking the dependency between <code>writeObject</code>
 * and <code>readObject</code> implementations, which may
 * change from version to version, is the key factor
 * in making the archives produced by this technique immune
 * to changes in the private implementations of the classes
 * to which they refer.
 * <p>
 * A persistence delegate, may take control of all
 * aspects of the persistence of an object including:
 * <ul>
 * <li>
 * Deciding whether or not an instance can be mutated
 * into another instance of the same class.
 * <li>
 * Instantiating the object, either by calling a
 * public constructor or a public factory method.
 * <li>
 * Performing the initialization of the object.
 * </ul>
 * @see XMLEncoder
 *
 * @since 1.4
 *
 * @author Philip Milne
 */

public abstract class PersistenceDelegate {

    /**
     * The <code>writeObject</code> is a single entry point to the persistence
     * and is used by a <code>Encoder</code> in the traditional
     * mode of delegation. Although this method is not final,
     * it should not need to be subclassed under normal circumstances.
     * <p>
     * This implementation first checks to see if the stream
     * has already encountered this object. Next the
     * <code>mutatesTo</code> method is called to see if
     * that candidate returned from the stream can
     * be mutated into an accurate copy of <code>oldInstance</code>.
     * If it can, the <code>initialize</code> method is called to
     * perform the initialization. If not, the candidate is removed
     * from the stream, and the <code>instantiate</code> method
     * is called to create a new candidate for this object.
     *
     * @param oldInstance The instance that will be created by this expression.
     * @param out The stream to which this expression will be written.
     *
     * @throws NullPointerException if {@code out} is {@code null}
     */
    public void writeObject(Object oldInstance, Encoder out) {
        Object newInstance = out.get(oldInstance);
        if (!mutatesTo(oldInstance, newInstance)) {
            out.remove(oldInstance);
            out.writeExpression(instantiate(oldInstance, out));
        }
        else {
            initialize(oldInstance.getClass(), oldInstance, newInstance, out);
        }
    }

    /**
     * Returns true if an <em>equivalent</em> copy of <code>oldInstance</code> may be
     * created by applying a series of statements to <code>newInstance</code>.
     * In the specification of this method, we mean by equivalent that the modified instance
     * is indistinguishable from <code>oldInstance</code> in the behavior
     * of the relevant methods in its public API. [Note: we use the
     * phrase <em>relevant</em> methods rather than <em>all</em> methods
     * here only because, to be strictly correct, methods like <code>hashCode</code>
     * and <code>toString</code> prevent most classes from producing truly
     * indistinguishable copies of their instances].
     * <p>
     * The default behavior returns <code>true</code>
     * if the classes of the two instances are the same.
     *
     * @param oldInstance The instance to be copied.
     * @param newInstance The instance that is to be modified.
     * @return True if an equivalent copy of <code>newInstance</code> may be
     *         created by applying a series of mutations to <code>oldInstance</code>.
     */
    protected boolean mutatesTo(Object oldInstance, Object newInstance) {
        return (newInstance != null && oldInstance != null &&
                oldInstance.getClass() == newInstance.getClass());
    }

    /**
     * Returns an expression whose value is <code>oldInstance</code>.
     * This method is used to characterize the constructor
     * or factory method that should be used to create the given object.
     * For example, the <code>instantiate</code> method of the persistence
     * delegate for the <code>Field</code> class could be defined as follows:
     * <pre>
     * Field f = (Field)oldInstance;
     * return new Expression(f, f.getDeclaringClass(), "getField", new Object[]{f.getName()});
     * </pre>
     * Note that we declare the value of the returned expression so that
     * the value of the expression (as returned by <code>getValue</code>)
     * will be identical to <code>oldInstance</code>.
     *
     * @param oldInstance The instance that will be created by this expression.
     * @param out The stream to which this expression will be written.
     * @return An expression whose value is <code>oldInstance</code>.
     *
     * @throws NullPointerException if {@code out} is {@code null}
     */
    protected abstract Expression instantiate(Object oldInstance, Encoder out);

    /**
     * Produce a series of statements with side effects on <code>newInstance</code>
     * so that the new instance becomes <em>equivalent</em> to <code>oldInstance</code>.
     * In the specification of this method, we mean by equivalent that, after the method
     * returns, the modified instance is indistinguishable from
     * <code>newInstance</code> in the behavior of all methods in its
     * public API.
     * <p>
     * The implementation typically achieves this goal by producing a series of
     * "what happened" statements involving the <code>oldInstance</code>
     * and its publicly available state. These statements are sent
     * to the output stream using its <code>writeExpression</code>
     * method which returns an expression involving elements in
     * a cloned environment simulating the state of an input stream during
     * reading. Each statement returned will have had all instances
     * the old environment replaced with objects which exist in the new
     * one. In particular, references to the target of these statements,
     * which start out as references to <code>oldInstance</code> are returned
     * as references to the <code>newInstance</code> instead.
     * Executing these statements effects an incremental
     * alignment of the state of the two objects as a series of
     * modifications to the objects in the new environment.
     * By the time the initialize method returns it should be impossible
     * to tell the two instances apart by using their public APIs.
     * Most importantly, the sequence of steps that were used to make
     * these objects appear equivalent will have been recorded
     * by the output stream and will form the actual output when
     * the stream is flushed.
     * <p>
     * The default implementation, calls the <code>initialize</code>
     * method of the type's superclass.
     *
     * @param oldInstance The instance to be copied.
     * @param newInstance The instance that is to be modified.
     * @param out The stream to which any initialization statements should be written.
     *
     * @throws NullPointerException if {@code out} is {@code null}
     */
    protected void initialize(Class<?> type,
                              Object oldInstance, Object newInstance,
                              Encoder out)
    {
        Class superType = type.getSuperclass();
        PersistenceDelegate info = out.getPersistenceDelegate(superType);
        info.initialize(superType, oldInstance, newInstance, out);
    }
}
