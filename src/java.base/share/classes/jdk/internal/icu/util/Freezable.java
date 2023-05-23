// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 ******************************************************************************
 * Copyright (C) 2005-2016, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                               *
 ******************************************************************************
*/
package jdk.internal.icu.util;

/**
 * Provides a flexible mechanism for controlling access, without requiring that
 * a class be immutable. Once frozen, an object can never be unfrozen, so it is
 * thread-safe from that point onward. Once the object has been frozen, 
 * it must guarantee that no changes can be made to it. Any attempt to alter 
 * it must raise an UnsupportedOperationException exception. This means that when 
 * the object returns internal objects, or if anyone has references to those internal
 * objects, that those internal objects must either be immutable, or must also
 * raise exceptions if any attempt to modify them is made. Of course, the object
 * can return clones of internal objects, since those are safe.
 * <h2>Background</h2>
 * <p>
 * There are often times when you need objects to be objects 'safe', so that
 * they can't be modified. Examples are when objects need to be thread-safe, or
 * in writing robust code, or in caches. If you are only creating your own
 * objects, you can guarantee this, of course -- but only if you don't make a
 * mistake. If you have objects handed into you, or are creating objects using
 * others handed into you, it is a different story. It all comes down to whether
 * you want to take the Blanche Dubois approach (&quot;depend on the kindness of
 * strangers&quot;) or the Andy Grove approach (&quot;Only the Paranoid
 * Survive&quot;).
 * </p>
 * <p>
 * For example, suppose we have a simple class:
 * </p>
 * 
 * <pre>
 * public class A {
 *      protected Collection b;
 * 
 *      protected Collection c;
 * 
 *      public Collection get_b() {
 *              return b;
 *      }
 * 
 *      public Collection get_c() {
 *              return c;
 *      }
 * 
 *      public A(Collection new_b, Collection new_c) {
 *              b = new_b;
 *              c = new_c;
 *      }
 * }
 * </pre>
 * 
 * <p>
 * Since the class doesn't have any setters, someone might think that it is
 * immutable. You know where this is leading, of course; this class is unsafe in
 * a number of ways. The following illustrates that.
 * </p>
 * 
 * <pre>
 *  public test1(SupposedlyImmutableClass x, SafeStorage y) {
 *    // unsafe getter
 *    A a = x.getA();
 *    Collection col = a.get_b();
 *    col.add(something); // a has now been changed, and x too
 *
 *    // unsafe constructor
 *    a = new A(col, col);
 *    y.store(a);
 *    col.add(something); // a has now been changed, and y too
 *  }
 * </pre>
 * 
 * <p>
 * There are a few different techniques for having safe classes.
 * </p>
 * <ol>
 * <li>Const objects. In C++, you can declare parameters const.</li>
 * <li>Immutable wrappers. For example, you can put a collection in an
 * immutable wrapper.</li>
 * <li>Always-Immutable objects. Java uses this approach, with a few
 * variations. Examples:
 * <ol>
 * <li>Simple. Once a Color is created (eg from R, G, and B integers) it is
 * immutable.</li>
 * <li>Builder Class. There is a separate 'builder' class. For example,
 * modifiable Strings are created using StringBuffer (which doesn't have the
 * full String API available). Once you want an immutable form, you create one
 * with toString().</li>
 * <li>Primitives. These are always safe, since they are copied on input/output
 * from methods.</li>
 * </ol>
 * </li>
 * <li>Cloning. Where you need an object to be safe, you clone it.</li>
 * </ol>
 * <p>
 * There are advantages and disadvantages of each of these.
 * </p>
 * <ol>
 * <li>Const provides a certain level of protection, but since const can be and
 * is often cast away, it only protects against most inadvertent mistakes. It
 * also offers no threading protection, since anyone who has a pointer to the
 * (unconst) object in another thread can mess you up.</li>
 * <li>Immutable wrappers are safer than const in that the constness can't be
 * cast away. But other than that they have all the same problems: not safe if
 * someone else keeps hold of the original object, or if any of the objects
 * returned by the class are mutable.</li>
 * <li>Always-Immutable Objects are safe, but usage can require excessive
 * object creation.</li>
 * <li>Cloning is only safe if the object truly has a 'safe' clone; defined as
 * one that <i>ensures that no change to the clone affects the original</i>.
 * Unfortunately, many objects don't have a 'safe' clone, and always cloning can
 * require excessive object creation.</li>
 * </ol>
 * <h2>Freezable Model</h2>
 * <p>
 * The <code>Freezable</code> model supplements these choices by giving you
 * the ability to build up an object by calling various methods, then when it is
 * in a final state, you can <i>make</i> it immutable. Once immutable, an
 * object cannot <i>ever </i>be modified, and is completely thread-safe: that
 * is, multiple threads can have references to it without any synchronization.
 * If someone needs a mutable version of an object, they can use
 * <code>cloneAsThawed()</code>, and modify the copy. This provides a simple,
 * effective mechanism for safe classes in circumstances where the alternatives
 * are insufficient or clumsy. (If an object is shared before it is immutable,
 * then it is the responsibility of each thread to mutex its usage (as with
 * other objects).)
 * </p>
 * <p>
 * Here is what needs to be done to implement this interface, depending on the
 * type of the object.
 * </p>
 * <h3><b>Immutable Objects</b></h3>
 * <p>
 * These are the easiest. You just use the interface to reflect that, by adding
 * the following:
 * </p>
 * 
 * <pre>
 *  public class A implements Freezable&lt;A&gt; {
 *   ...
 *   public final boolean isFrozen() {return true;}
 *   public final A freeze() {return this;}
 *   public final A cloneAsThawed() { return this; }
 *   }
 * </pre>
 * 
 * <p>
 * These can be final methods because subclasses of immutable objects must
 * themselves be immutable. (Note: <code>freeze</code> is returning
 * <code>this</code> for chaining.)
 * </p>
 * <h3><b>Mutable Objects</b></h3>
 * <p>
 * Add a protected 'flagging' field:
 * </p>
 * 
 * <pre>
 * protected volatile boolean frozen; // WARNING: must be volatile
 * </pre>
 * 
 * <p>
 * Add the following methods:
 * </p>
 * 
 * <pre>
 * public final boolean isFrozen() {
 *      return frozen;
 * };
 * 
 * public A freeze() {
 *      frozen = true;  // WARNING: must be final statement before return
 *      return this;
 * }
 * </pre>
 * 
 * <p>
 * Add a <code>cloneAsThawed()</code> method following the normal pattern for
 * <code>clone()</code>, except that <code>frozen=false</code> in the new
 * clone.
 * </p>
 * <p>
 * Then take the setters (that is, any method that can change the internal state
 * of the object), and add the following as the first statement:
 * </p>
 * 
 * <pre>
 * if (isFrozen()) {
 *      throw new UnsupportedOperationException(&quot;Attempt to modify frozen object&quot;);
 * }
 * </pre>
 * 
 * <h4><b>Subclassing</b></h4>
 * <p>
 * Any subclass of a <code>Freezable</code> will just use its superclass's
 * flagging field. It must override <code>freeze()</code> and
 * <code>cloneAsThawed()</code> to call the superclass, but normally does not
 * override <code>isFrozen()</code>. It must then just pay attention to its
 * own getters, setters and fields.
 * </p>
 * <h4><b>Internal Caches</b></h4>
 * <p>
 * Internal caches are cases where the object is logically unmodified, but
 * internal state of the object changes. For example, there are const C++
 * functions that cast away the const on the &quot;this&quot; pointer in order
 * to modify an object cache. These cases are handled by mutexing the internal
 * cache to ensure thread-safety. For example, suppose that UnicodeSet had an
 * internal marker to the last code point accessed. In this case, the field is
 * not externally visible, so the only thing you need to do is to synchronize
 * the field for thread safety.
 * </p>
 * <h4>Unsafe Internal Access</h4>
 * <p>
 * Internal fields are called <i>safe</i> if they are either
 * <code>frozen</code> or immutable (such as String or primitives). If you've
 * never allowed internal access to these, then you are all done. For example,
 * converting UnicodeSet to be <code>Freezable</code> is just accomplished
 * with the above steps. But remember that you <i><b>have</b></i> allowed
 * access to unsafe internals if you have any code like the following, in a
 * getter, setter, or constructor:
 * </p>
 * 
 * <pre>
 * Collection getStuff() {
 *      return stuff;
 * } // caller could keep reference &amp; modify
 * 
 * void setStuff(Collection x) {
 *      stuff = x;
 * } // caller could keep reference &amp; modify
 * 
 * MyClass(Collection x) {
 *      stuff = x;
 * } // caller could keep reference &amp; modify
 * </pre>
 * 
 * <p>
 * These also illustrated in the code sample in <b>Background</b> above.
 * </p>
 * <p>
 * To deal with unsafe internals, the simplest course of action is to do the
 * work in the <code>freeze()</code> function. Just make all of your internal
 * fields frozen, and set the frozen flag. Any subsequent getter/setter will
 * work properly. Here is an example:
 * </p>
 * <p><b>Warning!</b> The 'frozen' boolean MUST be volatile, and must be set as the last statement
 * in the method.</p>
 * <pre>
 * public A freeze() {
 *      if (!frozen) {
 *              foo.freeze();
 *              frozen = true;
 *      }
 *      return this;
 * }
 * </pre>
 * 
 * <p>
 * If the field is a <code>Collection</code> or <code>Map</code>, then to
 * make it frozen you have two choices. If you have never allowed access to the
 * collection from outside your object, then just wrap it to prevent future
 * modification.
 * </p>
 * 
 * <pre>
 * zone_to_country = Collections.unmodifiableMap(zone_to_country);
 * </pre>
 * 
 * <p>
 * If you have <i>ever</i> allowed access, then do a <code>clone()</code>
 * before wrapping it.
 * </p>
 * 
 * <pre>
 * zone_to_country = Collections.unmodifiableMap(zone_to_country.clone());
 * </pre>
 * 
 * <p>
 * If a collection <i>(or any other container of objects)</i> itself can
 * contain mutable objects, then for a safe clone you need to recurse through it
 * to make the entire collection immutable. The recursing code should pick the
 * most specific collection available, to avoid the necessity of later
 * downcasing.
 * </p>
 * <blockquote>
 * <p>
 * <b>Note: </b>An annoying flaw in Java is that the generic collections, like
 * <code>Map</code> or <code>Set</code>, don't have a <code>clone()</code>
 * operation. When you don't know the type of the collection, the simplest
 * course is to just create a new collection:
 * </p>
 * 
 * <pre>
 * zone_to_country = Collections.unmodifiableMap(new HashMap(zone_to_country));
 * </pre>
 * 
 * </blockquote>
 * @stable ICU 3.8
 */
public interface Freezable<T> extends Cloneable {
    /**
     * Determines whether the object has been frozen or not.
     * @stable ICU 3.8
     */
    public boolean isFrozen();

    /**
     * Freezes the object.
     * @return the object itself.
     * @stable ICU 3.8
     */
    public T freeze();

    /**
     * Provides for the clone operation. Any clone is initially unfrozen.
     * @stable ICU 3.8
     */
    public T cloneAsThawed();
}
