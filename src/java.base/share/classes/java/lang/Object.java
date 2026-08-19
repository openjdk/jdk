/*
 * Copyright (c) 1994, 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.vm.annotation.AOTSafeClassInitializer;
import jdk.internal.vm.annotation.IntrinsicCandidate;

/**
 * Class {@code Object} is the root of the class hierarchy.
 * Every class has {@code Object} as a superclass. All objects,
 * including arrays, implement the methods of this class.
 *
 * <div class="preview-block">
 *      <div class="preview-comment">
 *          When preview features are enabled, subclasses of {@code java.lang.Object}
 *          are either {@linkplain Class#isValue value classes} or identity classes.
 *          A <em>value object</em> is an instance of a non-abstract value class. All
 *          other objects, including arrays, are <em>identity objects</em>. See
 *          The Java Language Specification {@jls value-objects-8.1.1.5 Value Classes}.
 *          <p>
 *          When preview features are disabled, all classes are identity classes and
 *          all objects are identity objects.
 *          <p>
 *          It is not possible to synchronize on a value object. An attempt to {@code
 *          synchronize} on a value object causes {@link IdentityException} to be thrown.
 *          <p>
 *          The {@link #finalize()} method of a value class will never be invoked by
 *          the garbage collector.
 *          <p>
 *          A {@linkplain java.lang.ref.Reference Reference object} can only refer to an
 *          object with identity. Creating a reference object with a value object as
 *          the referent throws {@code IdentityException}.
 *          <p>
 *          It is impossible to mutate any value object field, either directly
 *          or {@linkplain java.lang.reflect.Field#set reflectively},
 *          even if access to the field has been granted.
 *
 *          <h2 id="Indistinguishability">Object Distinguishability</h2>
 *          Two value references appear to be exactly the same value
 *          whenever their corresponding field values are (recursively)
 *          exactly the same.  In that case the two references are
 *          said to be <em>indistinguishable</em>.  One reference can
 *          be substituted for the other with no change to program
 *          behavior.  For all purposes in the JVM, the referenced
 *          objects don't just appear to be the same value: they are
 *          the same.
 *          <p>
 *          Value indistinguishability can be counter-intuitive when
 *          the two values were created independently.  After all,
 *          with identity objects, distinct creation events always
 *          create distinct objects.  But two values created in
 *          completely different places can turn out to be the same.
 *          In this respect, value objects work like primitives.
 *          The single number 4 is the same value everywhere,
 *          regardless of how many places and times it was recomputed,
 *          and regardless of where, how, and how many times it is
 *          stored.  For a longer discussion of these points, see
 *          <a href="https://openjdk.org/jeps/401#Programming-without-identity">
 *          JEP 401, "Programming without identity" section</a>.
 *          <p>
 *          Any two objects might be told apart by observing a
 *          difference between their field values (for immutable
 *          fields).  But for two value objects, the JVM provides no
 *          other way to make distinctions.  If they are fieldwise
 *          equivalent, the VM makes them look exactly the same.
 *          This is true whether they were created independently or
 *          not, and whether they are stored in one heap location or
 *          in many places on heap and stack.
 *          <p>
 *          In short, unlike a regular identity object, a value has no
 *          object identity, leaving only its field states to carry
 *          the full semantic weight.
 *          <p>
 *          In detail, two object references are said to be
 *          <em>indistinguishable</em> if and only if
 *          <ul>
 *          <li>(a) both are the null reference, or</li>
 *          <li>(b) both are the same identity object, or</li>
 *          <li>(c) both refer to value objects of the same class, and</li>
 *          <li><ul><li>their reference fields are pairwise indistinguishable,
 *                      appealing recursively to this same definition, and</li>
 *                  <li>their primitive fields are also pairwise
 *                      indistinguishable, in the sense of bit-wise
 *                      equivalence.</li>
 *              </ul></li>
 *          </ul>
 *          The equality operator {@code ==} detects exactly this
 *          condition of indistinguishability when applied directly to
 *          any type, with the exception of {@code float} and {@code
 *          double}.
 *          <p>
 *          There are special rules for {@code ==} when applied to
 *          {@code float} and {@code double} operands, requiring
 *          {@code -0.0 == 0.0} and {@code Double.NaN != Double.NaN},
 *          even though the first pair of values are in fact
 *          distinguishable, while the second pair are not.  Thus,
 *          {@code ==} fails to detect indistinguishability of
 *          floating-point values. The {@link Double#compare} method
 *          also fails to detect indistinguishability, comparing all
 *          {@code NaN} values as equal even if their raw bits differ.
 *          See also {@linkplain Double##equivalenceRelation this
 *          discussion of equivalence of floating-point values}.
 *          Indistinguishability of floating-point values must be
 *          tested by examining the bit-wise representation, as a
 *          {@linkplain Float#floatToRawIntBits raw <code>int</code>} or
 *          {@linkplain Double#doubleToRawLongBits raw <code>long</code>}.
 *          <p>
 *          While the {@code ==} operator performs a basic machine
 *          word comparison for most operands, it is more complicated
 *          when applied to value objects.  It may need to compare
 *          several field values, or even walk recursively into nested
 *          values.  Such walks are usually short.  And since values
 *          are inherently acyclic, the recursion always bottoms out.
 *          In its general structure, a value object is the root of a
 *          tree of value objects, whose leaves can be any mix of primitives
 *          and identity objects.
 *          The tree can also contain {@code null}s; those can be
 *          thought of as either a kind of internal tree structure, or
 *          as a third kind of leaf.
 *          <p>
 *          The {@code ==} operator on a deeply nested value has similar
 *          costs to a call to {@link Object#equals} on a deeply nested
 *          tree of {@link java.util.ArrayList ArrayList}s.
 *          Both can require significant machine resources for
 *          tree walking, because a large tree may need to be
 *          walked, to prove there are no differences.  See also
 *          <a href="https://openjdk.org/jeps/401#Comparing-value-objects">
 *          JEP 401, "Comparing value objects" section</a>.
 *          <p>
 *          The authoritative definition of the equality operator,
 *          including its connection to indistinguishability,
 *          is found in the Java Language Specification
 *          {@jls value-objects-15.21.3 Object Equality Operators}.
 *          The JVM instruction which implements {@code ==} has also
 *          been extended to detect indistinguishability; see JVMS
 *          section 6.5, <i>if_acmp&lt;cond&gt;</i>, in the JEP 401 preview
 *          specifications.
 *          <p>
 *          The {@code ==} operator supplies the behavior of the
 *          default {@link Object#equals} method.  As a consequence,
 *          that built-in method can sometimes be used, without any
 *          override, for comparing the structure of two values.  This
 *          affects the design of value classes.  An author of a new
 *          value class must determine, within the class's own
 *          semantic rules, whether two <em>distinguishable</em>
 *          instances of that class ({@code x!=y}) should ever be
 *          regarded as <em>interchangeable</em> (semantically
 *          equivalent, {@code x.equals(y)}).  For example, does the
 *          class contain an array (or string or list) whose identity
 *          is insignificant?  If so, the class must override the
 *          {@code equals} and {@code hashCode} methods, providing
 *          logic which discounts non-semantic representational
 *          differences (such as the identity of an array, string, or
 *          list).  But for some simple classes, if values are at all
 *          distinguishable (if they have any differing leaf items),
 *          then {@code equals} should return {@code false}; it should
 *          never hide any representational differences.  In that
 *          case, the class author should let the JVM do what it
 *          already knows how to do, and refrain from overriding the
 *          {@code equals} and {@code hashCode} methods.
 *          <p>
 *          These considerations (of whether to override {@code
 *          equals} or not) affect clients as well as authors of value
 *          classes.  There is a long-standing practice to precede an
 *          {@code equals} call comparing two identity objects with
 *          {@code ==}, as a cheap way to sometimes avoid calling the
 *          more expensive {@code equals} method.  (It looks like
 *          {@code x==y || x.equals(y)}.)  But users of value classes
 *          should know that using {@code ==} first is <em>not</em>
 *          likely to be much cheaper than calling {@code equals}.
 *          The idiom created for identity objects is not incorrect
 *          for value objects, but you are likely to ask the JVM to do
 *          the same kind of tree-walking work twice, once for {@code
 *          ==} as a failed optimization, and once for {@code equals}
 *          to get the real answer.  Just call {@code equals} or
 *          {@link java.util.Objects::equals}.
 *          As a special case, if a value class documents a commitment
 *          never to override {@code Object.equals}, and in that case
 *          only, {@code ==} will be a shorthand for {@code equals}.
 *          There is no special accommodation to make {@code ==}
 *          easier to use, and
 *          <a href="https://openjdk.org/jeps/401#Non-Goals"> JEP 401
 *          explicitly disavows</a> fixing {@code ==} to make it align
 *          more closely with value objects or {@code equals}.
 *          <p>
 *          Equality methods and hash methods come in pairs, so the
 *          similarity (for values) between the two forms of equality
 *          ({@code ==} and {@code equals}) corresponds to an
 *          analogous similarity between the two kinds of {@code
 *          hashCode} methods.  Just as {@code ==} must walk over a pair
 *          of value trees, {@link java.lang.System#identityHashCode}
 *          must walk a value's tree as well, computing a hash based
 *          on its structure and the field values at its leaves.
 *          <p>
 *          Note that the identity-sensitive operations (field
 *          mutation, synchronization, weak references) provide ways
 *          to make time-varying distinctions between two identity
 *          objects, whether or not they have the same corresponding
 *          field values.  Hypothetically extending those operations
 *          to value objects would allow code to distinguish, over
 *          time-varying conditions, values which would otherwise be
 *          indistinguishable.  The JVM does not allow value objects
 *          to vary over time, as it could cause a particular value
 *          reference to change over time, even though it has not been
 *          updated by assignment.
 *          <p>
 *          As a rule, both identity and value objects are created by
 *          invoking constructors. (Arrays and deserialization can
 *          break the rule.)  When a new object first becomes visible
 *          as an assignable reference, it carries whatever field
 *          values it was initially given.
 *          If it is an identity object it also has a freshly assigned
 *          identity.  If it is a value object, its fields will never
 *          change, and because it has no identity, it will
 *          immediately be indistinguishable from any previously
 *          created value object with those same field values.  Thus,
 *          object creation is subtly different between values and
 *          identity objects.  For more details, see
 *          {@jls value-objects-4.3.1 Objects} and
 *          {@jls value-objects-15.9.4 Run-Time Evaluation of Class
 *          Instance Creation Expressions}.
 *          <p>
 *          This difference applies evenly across all kinds of object
 *          creation events: {@code new} expressions, creation by
 *          reflection ({@code newInstance}) or method handle or JNI,
 *          lambda capture or autoboxing, deserialization, and more.
 *          When creating an identity object, you will always get
 *          a fresh identity distinct from all others, but if it is a
 *          value, it might be indistinguishable from some
 *          already-existing value.
 *          <p>
 *          It is true that object creation events necessarily
 *          allocate memory, somewhere, to hold the new object's
 *          fields.  And one might suppose that the JVM secretly knows
 *          the one heap block where the new value is stored.  One
 *          might even guess that an object's memory address
 *          determines its identity.  But that would be a fallacy of
 *          oversimplification.  When the GC is working, any single
 *          object might be stored temporarily in two blocks, linked
 *          by some forwarding mechanism.  When the JVM creates any
 *          kind of object it may delay or omit creation of the heap
 *          block, and use some kind of bookkeeping (not a memory
 *          address) to track the object's identity.  For identity
 *          objects, this delay requires prior escape analysis; for
 *          value objects it comes for free.
 *          <p>
 *          For values, the implementation possibilities are
 *          boundless, because the whole value object lives in its
 *          collection of individual field values, and nowhere else.
 *          During or after construction, the JVM might choose to
 *          store the value in one heap block, on the stack, split up
 *          fieldwise in registers, or inlined into a containing
 *          object or array.  It might use all these techniques
 *          simultaneously, because the JVM can split one value into
 *          many copies.  It might also merge two copies of a value
 *          object into one stored value, if it can prove they are
 *          indistinguishable.
 *          <p>
 *          Value objects are built into the core of the language
 *          and JVM.  An historical approximation to the concept is
 *          <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">
 *          value-based objects</a>, which are regular identity objects
 *          for which the use of both {@code ==} and identity-sensitive
 *          operations is strongly discouraged.  Some classes previously
 *          marked as value based are migrated to proper value classes.
 *      </div>
 * </div>
 *
 * @see     java.lang.Class
 * @since   1.0
 */
@AOTSafeClassInitializer // for hierarchy checks
public class Object {

    /**
     * Constructs a new object.
     */
    @IntrinsicCandidate
    public Object() {}

    /**
     * Returns the runtime class of this {@code Object}. The returned
     * {@code Class} object is the object that is locked by {@code
     * static synchronized} methods of the represented class.
     *
     * <p><b>The actual result type is {@code Class<? extends |X|>}
     * where {@code |X|} is the erasure of the static type of the
     * expression on which {@code getClass} is called.</b> For
     * example, no cast is required in this code fragment:</p>
     *
     * <p>
     * {@code Number n = 0;                             }<br>
     * {@code Class<? extends Number> c = n.getClass(); }
     * </p>
     *
     * @return The {@code Class} object that represents the runtime
     *         class of this object.
     * @jls 15.8.2 Class Literals
     */
    @IntrinsicCandidate
    public final native Class<?> getClass();

    /**
     * {@return a hash code value for this object} This method is
     * supported for the benefit of hash tables such as those provided by
     * {@link java.util.HashMap}.
     * <p>
     * The general contract of {@code hashCode} is:
     * <ul>
     * <li>Whenever it is invoked on the same object more than once during
     *     an execution of a Java application, the {@code hashCode} method
     *     must consistently return the same integer, provided no information
     *     used in {@code equals} comparisons on the object is modified.
     *     This integer need not remain consistent from one execution of an
     *     application to another execution of the same application.
     * <li>If two objects are equal according to the {@link
     *     #equals(Object) equals} method, then calling the {@code
     *     hashCode} method on each of the two objects must produce the
     *     same integer result.
     * <li>It is <em>not</em> required that if two objects are unequal
     *     according to the {@link #equals(Object) equals} method, then
     *     calling the {@code hashCode} method on each of the two objects
     *     must produce distinct integer results.  However, the programmer
     *     should be aware that producing distinct integer results for
     *     unequal objects may improve the performance of hash tables.
     * </ul>
     *
     * @implSpec
     * As far as is reasonably practical, the {@code hashCode} method defined
     * by class {@code Object} returns distinct integers for distinct objects.
     *
     * @apiNote
     * The {@link java.util.Objects#hash(Object...) hash} and {@link
     * java.util.Objects#hashCode(Object) hashCode} methods of {@link
     * java.util.Objects} can be used to help construct simple hash codes.
     *
     * @see     java.lang.Object#equals(java.lang.Object)
     * @see     java.lang.System#identityHashCode
     */
    @IntrinsicCandidate
    public native int hashCode();

    /**
     * Indicates whether some other object is "equal to" this one.
     * <p>
     * The {@code equals} method implements an <dfn>{@index "equivalence relation"}</dfn>
     * on non-null object references:
     * <ul>
     * <li>It is <i>reflexive</i>: for any non-null reference value
     *     {@code x}, {@code x.equals(x)} should return
     *     {@code true}.
     * <li>It is <i>symmetric</i>: for any non-null reference values
     *     {@code x} and {@code y}, {@code x.equals(y)}
     *     should return {@code true} if and only if
     *     {@code y.equals(x)} returns {@code true}.
     * <li>It is <i>transitive</i>: for any non-null reference values
     *     {@code x}, {@code y}, and {@code z}, if
     *     {@code x.equals(y)} returns {@code true} and
     *     {@code y.equals(z)} returns {@code true}, then
     *     {@code x.equals(z)} should return {@code true}.
     * <li>It is <i>consistent</i>: for any non-null reference values
     *     {@code x} and {@code y}, multiple invocations of
     *     {@code x.equals(y)} consistently return {@code true}
     *     or consistently return {@code false}, provided no
     *     information used in {@code equals} comparisons on the
     *     objects is modified.
     * <li>For any non-null reference value {@code x},
     *     {@code x.equals(null)} should return {@code false}.
     * </ul>
     *
     * <p>
     * An equivalence relation partitions the elements it operates on
     * into <i>equivalence classes</i>; all the members of an
     * equivalence class are equal to each other. Members of an
     * equivalence class are substitutable for each other, at least
     * for some purposes.
     *
     * @implSpec
     * The {@code equals} method for class {@code Object} implements
     * the most discriminating possible equivalence relation on objects,
     * which is {@linkplain ##Indistinguishability indistinguishability}.
     * That is, for any non-null reference values {@code x} and
     * {@code y}, this method returns {@code true} if and only
     * if {@code x == y} has the value {@code true}.
     * <p>
     * In other words, under the object equality equivalence
     * relation, each equivalence class only has a single
     * distinguishable element.
     *
     * @apiNote
     * It is generally necessary to override the {@link #hashCode() hashCode}
     * method whenever this method is overridden, so as to maintain the
     * general contract for the {@code hashCode} method, which states
     * that equal objects must have equal hash codes.
     * <p>The two-argument {@link java.util.Objects#equals(Object,
     * Object) Objects.equals} method implements an equivalence relation
     * on two possibly-null object references.
     *
     * @param   obj   the reference object with which to compare.
     * @return  {@code true} if this object is the same as the obj
     *          argument; {@code false} otherwise.
     * @see     #hashCode()
     * @see     java.util.HashMap
     */
    public boolean equals(Object obj) {
        return (this == obj);
    }

    /**
     * Creates and returns a copy of this object.  The precise meaning
     * of "copy" may depend on the class of the object. The general
     * intent is that, for any object {@code x}, the expression:
     * <blockquote>
     * <pre>
     * x.clone() != x</pre></blockquote>
     * will be true, and that the expression:
     * <blockquote>
     * <pre>
     * x.clone().getClass() == x.getClass()</pre></blockquote>
     * will also be {@code true}, but these are not absolute requirements.
     * The clone of a value object, in particular, may be
     * {@linkplain ##Indistinguishability indistinguishable} from
     * the original.
     * <p>
     * While it is typically the case that:
     * <blockquote>
     * <pre>
     * x.clone().equals(x)</pre></blockquote>
     * will be {@code true}, this is not an absolute requirement.
     * <p>
     * By convention, the {@code clone} method of an identity class should return an
     * object obtained by calling {@code super.clone}. If a class and all of its
     * superclasses (except {@code Object}) obey this convention, it will be the
     * case that {@code x.clone().getClass() == x.getClass()}.
     * <p>
     * By convention, the object returned by this method should be independent
     * of this object (which is being cloned).  To achieve this independence,
     * it may be necessary to modify one or more fields of the object returned
     * by {@code super.clone} before returning it.  Typically, this means
     * copying any mutable objects that comprise the internal "deep structure"
     * of the object being cloned and replacing the references to these
     * objects with references to the copies.  If a class contains only
     * primitive fields or references to immutable objects, then it is usually
     * the case that no fields in the object returned by {@code super.clone}
     * need to be modified.
     * <p>
     * Value classes may similarly perform deep copies of any mutable field
     * values before constructing a new class instance with those copies.
     *
     * @apiNote
     * It should be rare for new classes to implement the {@link Cloneable} interface.
     * Copy constructors and static factory methods provide a more explicit and flexible
     * means of creating copies, allowing classes to define and document their copying
     * semantics without the constraints imposed by {@code Cloneable} interface and
     * the {@code clone} method.
     *
     * @implSpec
     * The method {@code clone} for class {@code Object} performs a
     * specific cloning operation. First, if the class of this object does
     * not implement the interface {@code Cloneable}, then a
     * {@code CloneNotSupportedException} is thrown. Note that all arrays
     * are considered to implement the interface {@code Cloneable} and that
     * the return type of the {@code clone} method of an array type {@code T[]}
     * is {@code T[]} where T is any reference or primitive type.
     * <p>
     * For an identity object, this method creates a new instance of the class of
     * this object and initializes all its fields with exactly the contents of
     * the corresponding fields of this object, as if by assignment; the
     * contents of the fields are not themselves cloned. Thus, this method
     * performs a "shallow copy" of this object, not a "deep copy" operation.
     * <p>
     * For a value object, this method returns an object that is
     * {@linkplain ##Indistinguishability indistinguishable}
     * from this object.
     * <p>
     * The class {@code Object} does not itself implement the interface
     * {@code Cloneable}, so calling the {@code clone} method on an object
     * whose class is {@code Object} will result in throwing an
     * exception at run time.
     *
     * @return     a clone of this instance.
     * @throws  CloneNotSupportedException  if the object's class does not
     *               support the {@code Cloneable} interface. Subclasses
     *               that override the {@code clone} method can also
     *               throw this exception to indicate that an instance cannot
     *               be cloned.
     * @see java.lang.Cloneable
     */
    @IntrinsicCandidate
    protected native Object clone() throws CloneNotSupportedException;

    /**
     * {@return a string representation of the object}
     *
     * Satisfying this method's contract implies a non-{@code null}
     * result must be returned.
     *
     * @apiNote
     * In general, the
     * {@code toString} method returns a string that
     * "textually represents" this object. The result should
     * be a concise but informative representation that is easy for a
     * person to read.
     * It is recommended that all subclasses override this method.
     * The string output is not necessarily stable over time or across
     * JVM invocations.
     * @implSpec
     * The {@code toString} method for class {@code Object}
     * returns a string consisting of the name of the class of which the
     * object is an instance, the at-sign character `{@code @}', and
     * the unsigned hexadecimal representation of the hash code of the
     * object. In other words, this method returns a string equal to the
     * value of:
     * {@snippet lang=java :
     * getClass().getName() + '@' + Integer.toHexString(hashCode())
     * }
     * The {@link java.util.Objects#toIdentityString(Object)
     * Objects.toIdentityString} method returns the string for an
     * object equal to the string that would be returned if neither
     * the {@code toString} nor {@code hashCode} methods were
     * overridden by the object's class.
     */
    public String toString() {
        return getClass().getName() + "@" + Integer.toHexString(hashCode());
    }

    /**
     * Wakes up a single thread that is waiting on this object's
     * monitor. If any threads are waiting on this object, one of them
     * is chosen to be awakened. The choice is arbitrary and occurs at
     * the discretion of the implementation. A thread waits on an object's
     * monitor by calling one of the {@code wait} methods.
     * <p>
     * The awakened thread will not be able to proceed until the current
     * thread relinquishes the lock on this object. The awakened thread will
     * compete in the usual manner with any other threads that might be
     * actively competing to synchronize on this object; for example, the
     * awakened thread enjoys no reliable privilege or disadvantage in being
     * the next thread to lock this object.
     * <p>
     * This method should only be called by a thread that is the owner
     * of this object's monitor. A thread becomes the owner of the
     * object's monitor in one of three ways:
     * <ul>
     * <li>By executing a synchronized instance method of that object.
     * <li>By executing the body of a {@code synchronized} statement
     *     that synchronizes on the object.
     * <li>For objects of type {@code Class,} by executing a
     *     static synchronized method of that class.
     * </ul>
     * <p>
     * Only one thread at a time can own an object's monitor.
     * <div class="preview-block">
     *      <div class="preview-comment">
     *          The {@code notify} method requires that the current thread be the owner
     *          of the object's monitor. Since it is not possible to synchronize on a
     *          value object, an attempt to call this method on a value object will
     *          always fail with {@code IllegalMonitorStateException}.
     *      </div>
     * </div>
     *
     * @throws  IllegalMonitorStateException  if the current thread is not
     *               the owner of this object's monitor.
     * @see        java.lang.Object#notifyAll()
     * @see        java.lang.Object#wait()
     */
    @IntrinsicCandidate
    public final native void notify();

    /**
     * Wakes up all threads that are waiting on this object's monitor. A
     * thread waits on an object's monitor by calling one of the
     * {@code wait} methods.
     * <p>
     * The awakened threads will not be able to proceed until the current
     * thread relinquishes the lock on this object. The awakened threads
     * will compete in the usual manner with any other threads that might
     * be actively competing to synchronize on this object; for example,
     * the awakened threads enjoy no reliable privilege or disadvantage in
     * being the next thread to lock this object.
     * <p>
     * This method should only be called by a thread that is the owner
     * of this object's monitor. See the {@code notify} method for a
     * description of the ways in which a thread can become the owner of
     * a monitor.
     *
     * <div class="preview-block">
     *      <div class="preview-comment">
     *          The {@code notifyAll} method requires that the current thread be the owner
     *          of the object's monitor. Since it is not possible to synchronize on a
     *          value object, an attempt to call this method on a value object will
     *          always fail with {@code IllegalMonitorStateException}.
     *      </div>
     * </div>
     *
     * @throws  IllegalMonitorStateException  if the current thread is not
     *               the owner of this object's monitor.
     * @see        java.lang.Object#notify()
     * @see        java.lang.Object#wait()
     */
    @IntrinsicCandidate
    public final native void notifyAll();

    /**
     * Causes the current thread to wait until it is awakened, typically
     * by being <em>notified</em> or <em>interrupted</em>.
     * <p>
     * In all respects, this method behaves as if {@code wait(0L, 0)}
     * had been called. See the specification of the {@link #wait(long, int)} method
     * for details.
     *
     * <div class="preview-block">
     *      <div class="preview-comment">
     *          The {@code wait} method requires that the current thread be the owner
     *          of the object's monitor. Since it is not possible to synchronize on a
     *          value object, an attempt to call this method on a value object will
     *          always fail with {@code IllegalMonitorStateException}.
     *      </div>
     * </div>
     *
     * @throws IllegalMonitorStateException if the current thread is not
     *         the owner of the object's monitor
     * @throws InterruptedException if any thread interrupted the current thread before or
     *         while the current thread was waiting. The <em>interrupted status</em> of the
     *         current thread is cleared when this exception is thrown.
     * @see    #notify()
     * @see    #notifyAll()
     * @see    #wait(long)
     * @see    #wait(long, int)
     */
    public final void wait() throws InterruptedException {
        wait(0L);
    }

    /**
     * Causes the current thread to wait until it is awakened, typically
     * by being <em>notified</em> or <em>interrupted</em>, or until a
     * certain amount of real time has elapsed.
     * <p>
     * In all respects, this method behaves as if {@code wait(timeoutMillis, 0)}
     * had been called. See the specification of the {@link #wait(long, int)} method
     * for details.
     *
     * <div class="preview-block">
     *      <div class="preview-comment">
     *          The {@code wait} method requires that the current thread be the owner
     *          of the object's monitor. Since it is not possible to synchronize on a
     *          value object, an attempt to call this method on a value object will
     *          always fail with {@code IllegalMonitorStateException}.
     *      </div>
     * </div>
     *
     * @param  timeoutMillis the maximum time to wait, in milliseconds
     * @throws IllegalArgumentException if {@code timeoutMillis} is negative
     * @throws IllegalMonitorStateException if the current thread is not
     *         the owner of the object's monitor
     * @throws InterruptedException if any thread interrupted the current thread before or
     *         while the current thread was waiting. The <em>interrupted status</em> of the
     *         current thread is cleared when this exception is thrown.
     * @see    #notify()
     * @see    #notifyAll()
     * @see    #wait()
     * @see    #wait(long, int)
     */
    public final void wait(long timeoutMillis) throws InterruptedException {
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }

        if (Thread.currentThread() instanceof VirtualThread vthread) {
            try {
                wait0(timeoutMillis);
            } catch (InterruptedException e) {
                // virtual thread's interrupted status needs to be cleared
                vthread.getAndClearInterrupt();
                throw e;
            }
        } else {
            wait0(timeoutMillis);
        }
    }

    // final modifier so method not in vtable
    private final native void wait0(long timeoutMillis) throws InterruptedException;

    /**
     * Causes the current thread to wait until it is awakened, typically
     * by being <em>notified</em> or <em>interrupted</em>, or until a
     * certain amount of real time has elapsed.
     * <p>
     * The current thread must own this object's monitor lock. See the
     * {@link #notify notify} method for a description of the ways in which
     * a thread can become the owner of a monitor lock.
     * <p>
     * This method causes the current thread (referred to here as <var>T</var>) to
     * place itself in the wait set for this object and then to relinquish any
     * and all synchronization claims on this object. Note that only the locks
     * on this object are relinquished; any other objects on which the current
     * thread may be synchronized remain locked while the thread waits.
     * <p>
     * Thread <var>T</var> then becomes disabled for thread scheduling purposes
     * and lies dormant until one of the following occurs:
     * <ul>
     * <li>Some other thread invokes the {@code notify} method for this
     * object and thread <var>T</var> happens to be arbitrarily chosen as
     * the thread to be awakened.
     * <li>Some other thread invokes the {@code notifyAll} method for this
     * object.
     * <li>Some other thread {@linkplain Thread#interrupt() interrupts}
     * thread <var>T</var>.
     * <li>The specified amount of real time has elapsed, more or less.
     * The amount of real time, in nanoseconds, is given by the expression
     * {@code 1000000 * timeoutMillis + nanos}. If {@code timeoutMillis} and {@code nanos}
     * are both zero, then real time is not taken into consideration and the
     * thread waits until awakened by one of the other causes.
     * <li>Thread <var>T</var> is awakened spuriously. (See below.)
     * </ul>
     * <p>
     * The thread <var>T</var> is then removed from the wait set for this
     * object and re-enabled for thread scheduling. It competes in the
     * usual manner with other threads for the right to synchronize on the
     * object; once it has regained control of the object, all its
     * synchronization claims on the object are restored to the status quo
     * ante - that is, to the situation as of the time that the {@code wait}
     * method was invoked. Thread <var>T</var> then returns from the
     * invocation of the {@code wait} method. Thus, on return from the
     * {@code wait} method, the synchronization state of the object and of
     * thread {@code T} is exactly as it was when the {@code wait} method
     * was invoked.
     * <p>
     * A thread can wake up without being notified, interrupted, or timing out, a
     * so-called <em>spurious wakeup</em>.  While this will rarely occur in practice,
     * applications must guard against it by testing for the condition that should
     * have caused the thread to be awakened, and continuing to wait if the condition
     * is not satisfied. See the example below.
     * <p>
     * For more information on this topic, see section 14.2,
     * "Condition Queues," in Brian Goetz and others' <cite>Java Concurrency
     * in Practice</cite> (Addison-Wesley, 2006) or Item 81 in Joshua
     * Bloch's <cite>Effective Java, Third Edition</cite> (Addison-Wesley,
     * 2018).
     * <p>
     * If the current thread is {@linkplain java.lang.Thread#interrupt() interrupted}
     * by any thread before or while it is waiting, then an {@code InterruptedException}
     * is thrown.  The <em>interrupted status</em> of the current thread is cleared when
     * this exception is thrown. This exception is not thrown until the lock status of
     * this object has been restored as described above.
     *
     * <div class="preview-block">
     *      <div class="preview-comment">
     *          The {@code wait} method requires that the current thread be the owner
     *          of the object's monitor. Since it is not possible to synchronize on a
     *          value object, an attempt to call this method on a value object will
     *          always fail with {@code IllegalMonitorStateException}.
     *      </div>
     * </div>
     *
     * @apiNote
     * The recommended approach to waiting is to check the condition being awaited in
     * a {@code while} loop around the call to {@code wait}, as shown in the example
     * below. Among other things, this approach avoids problems that can be caused
     * by spurious wakeups.
     *
     * {@snippet lang=java :
     *     synchronized (obj) {
     *         while ( <condition does not hold and timeout not exceeded> ) {
     *             long timeoutMillis = ... ; // recompute timeout values
     *             int nanos = ... ;
     *             obj.wait(timeoutMillis, nanos);
     *         }
     *         ... // Perform action appropriate to condition or timeout
     *     }
     * }
     *
     * @param  timeoutMillis the maximum time to wait, in milliseconds
     * @param  nanos   additional time, in nanoseconds, in the range 0-999999 inclusive
     * @throws IllegalArgumentException if {@code timeoutMillis} is negative,
     *         or if the value of {@code nanos} is out of range
     * @throws IllegalMonitorStateException if the current thread is not
     *         the owner of the object's monitor
     * @throws InterruptedException if any thread interrupted the current thread before or
     *         while the current thread was waiting. The <em>interrupted status</em> of the
     *         current thread is cleared when this exception is thrown.
     * @see    #notify()
     * @see    #notifyAll()
     * @see    #wait()
     * @see    #wait(long)
     */
    public final void wait(long timeoutMillis, int nanos) throws InterruptedException {
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException("timeoutMillis value is negative");
        }

        if (nanos < 0 || nanos > 999999) {
            throw new IllegalArgumentException(
                                "nanosecond timeout value out of range");
        }

        if (nanos > 0 && timeoutMillis < Long.MAX_VALUE) {
            timeoutMillis++;
        }

        wait(timeoutMillis);
    }

    /**
     * Called by the garbage collector on an identity object when garbage collection
     * determines that there are no more references to the object.
     * An identity class may override the {@code finalize} method to dispose of
     * system resources or to perform other cleanup.
     * <div class="preview-block">
     *      <div class="preview-comment">
     *          The {@code finalize} method of a value class is never directly
     *          invoked by the garbage collector. This includes the case where an
     *          abstract value class declares a {@code finalize} method and the
     *          class is extended by an identity class; the garbage collector never
     *          directly invokes the {@code finalize} method declared by the
     *          abstract value class.
     *      </div>
     * </div>
     * <p>
     * <b>When running in a Java virtual machine in which finalization has been
     * disabled or removed, the garbage collector will never call {@code finalize()}
     * for any object. In a Java virtual machine in which finalization is
     * enabled, the garbage collector might call {@code finalize} only after an
     * indefinite delay.</b>
     * <p>
     * The general contract of {@code finalize} is that it is invoked
     * if and when the Java virtual
     * machine has determined that there is no longer any
     * means by which this object can be accessed by any thread that has
     * not yet died, except as a result of an action taken by the
     * finalization of some other object or class which is ready to be
     * finalized. The {@code finalize} method may take any action, including
     * making this object available again to other threads; the usual purpose
     * of {@code finalize}, however, is to perform cleanup actions before
     * the object is irrevocably discarded. For example, the finalize method
     * for an object that represents an input/output connection might perform
     * explicit I/O transactions to break the connection before the object is
     * permanently discarded.
     * <p>
     * The {@code finalize} method of class {@code Object} performs no
     * special action; it simply returns normally. Subclasses of
     * {@code Object} may override this definition.
     * <p>
     * The Java programming language does not guarantee which thread will
     * invoke the {@code finalize} method for any given object. It is
     * guaranteed, however, that the thread that invokes finalize will not
     * be holding any user-visible synchronization locks when finalize is
     * invoked. If an uncaught exception is thrown by the finalize method,
     * the exception is ignored and finalization of that object terminates.
     * <p>
     * After the {@code finalize} method has been invoked for an object, no
     * further action is taken until the Java virtual machine has again
     * determined that there is no longer any means by which this object can
     * be accessed by any thread that has not yet died, including possible
     * actions by other objects or classes which are ready to be finalized,
     * at which point the object may be discarded.
     * <p>
     * The {@code finalize} method is never invoked more than once by a Java
     * virtual machine for any given object.
     * <p>
     * Any exception thrown by the {@code finalize} method causes
     * the finalization of this object to be halted, but is otherwise
     * ignored.
     *
     * @apiNote
     * Classes that embed non-heap resources have many options
     * for cleanup of those resources. The class must ensure that the
     * lifetime of each instance is longer than that of any resource it embeds.
     * {@link java.lang.ref.Reference#reachabilityFence} can be used to ensure that
     * objects remain reachable while resources embedded in the object are in use.
     * <p>
     * A subclass should avoid overriding the {@code finalize} method
     * unless the subclass embeds non-heap resources that must be cleaned up
     * before the instance is collected.
     * Finalizer invocations are not automatically chained, unlike constructors.
     * If a subclass overrides {@code finalize} it must invoke the superclass
     * finalizer explicitly.
     * To guard against exceptions prematurely terminating the finalize chain,
     * the subclass should use a {@code try-finally} block to ensure
     * {@code super.finalize()} is always invoked. For example,
     * {@snippet lang="java":
     *     @Override
     *     protected void finalize() throws Throwable {
     *         try {
     *             ... // cleanup subclass state
     *         } finally {
     *             super.finalize();
     *         }
     *     }
     * }
     *
     * @deprecated Finalization is deprecated and subject to removal in a future
     * release. The use of finalization can lead to problems with security,
     * performance, and reliability.
     * See <a href="https://openjdk.org/jeps/421">JEP 421</a> for
     * discussion and alternatives.
     * <p>
     * Subclasses that override {@code finalize} to perform cleanup should use
     * alternative cleanup mechanisms and remove the {@code finalize} method.
     * Use {@link java.lang.ref.Cleaner} and
     * {@link java.lang.ref.PhantomReference} as safer ways to release resources
     * when an object becomes unreachable. Alternatively, add a {@code close}
     * method to explicitly release resources, and implement
     * {@code AutoCloseable} to enable use of the {@code try}-with-resources
     * statement.
     * <p>
     * This method will remain in place until finalizers have been removed from
     * most existing code.
     *
     * @throws Throwable the {@code Exception} raised by this method
     * @see java.lang.ref.WeakReference
     * @see java.lang.ref.PhantomReference
     * @jls 12.6 Finalization of Class Instances
     */
    @Deprecated(since="9", forRemoval=true)
    protected void finalize() throws Throwable { }
}
