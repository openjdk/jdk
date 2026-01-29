/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.vm.annotation;

import java.lang.annotation.*;

/**
 * A field may be annotated as "stable" to indicate that it is a
 * <em>stable variable</em>, expected to change its value just once.
 * All Java fields are initialized by the VM with a default value
 * (null or zero).  A properly used stable field will be set
 * just once to a non-default value, and keep that value forever.
 * While the field contains its default (null or zero) value,
 * the VM treats it as an ordinary mutable variable.  When a
 * non-default value is stored into the field, the VM is permitted
 * to assume that no more significant changes will occur.  This in
 * turn enables the VM to optimize uses of the stable variable, treating
 * them as constant values.  This behavior is a useful building block
 * for lazy evaluation or memoization of results.  In rare and subtle
 * use cases, stable variables may also assume multiple values over
 * time, with effects as described below.
 * <p>
 * <em>(Warning: the {@code @Stable} annotation is intended for use in the
 * JDK implemention, and with the HotSpot VM, to support optimization
 * of classes and algorithms defined by the JDK.  It is unavailable
 * outside the JDK.)</em>
 *
 * <h2><a id="lifecycle"></a>Stable Variable Life Cycle</h2>
 *
 * For example, suppose a class has two non-final fields of type
 * {@code int} and {@code String}.  Annotating the field declarations
 * with {@code @Stable} creates a pair of stable variables.  The
 * fields are initialized to zero and the null reference,
 * respectively, in the usual way, but storing a non-zero integer in
 * the first field or a non-null reference in the second field will
 * enable the VM to expect that the stored value is now the permanent
 * value of the field, going forward.  This condition may be used by
 * the VM compiler to improve code quality more aggressively,
 * if the VM compiler runs after the stable variable has been
 * given a permanent value, and chooses to observe that value.
 * <p>
 * Since all heap variables begin with a default null value for
 * references (resp., zero for primitives), there is an ambiguity when
 * the VM discovers a stable variable holding a null or primitive zero
 * value.  Does the user intend the VM to constant fold that
 * (uninteresting) value?  Or is the user waiting until later to
 * assign a permanent value to the variable?  The VM does not
 * systematically record stores of a null (resp., zero) to a stable variable,
 * so there is no way for the VM to decide if a field's current value is
 * its undisturbed initial value, or has been overwritten with
 * an intentionally stored null (resp., zero).  This is why the
 * programmer should store non-default values into stable variables,
 * if the consequent optimization is desired.
 * <p>
 * A stable variable may be assigned its permanent value inside a class or
 * object initializer, but in general (with lazy data structures)
 * stable variables are assigned much later.  Depending on the value
 * stored and what races are possible, safe publication may require
 * special handling with a {@code VarHandle} atomic method.
 * (See below.)
 * <p>
 * If an application requires constant folding of a stable variable
 * whose permanent value may be the default value (null or zero),
 * the variable can be refactored to add an extra indirection.
 * This would represent the default value in a non-null "box",
 * such as {@code Integer.valueOf(0)} or a lambda like
 * {@code ()->null}.  Such a refactoring should always be possible,
 * since stable variables should (obviously) never be part of public
 * APIs.
 *
 * <h2><a id="arrays"></a>Stable Arrays</h2>
 *
 * So far, stable variables are fields, but they can be array
 * components as well.  If a stable field is declared as an array
 * type with one dimension, both that array as a whole, and its
 * eventual components, are treated as independent stable variables.
 * When a reference to an array of length <i>N</i> is stored to the
 * field, then the array object itself is taken to be a constant, as
 * with any stable field.  But then all <i>N</i> of the array
 * components are <em>also</em> treated as independent stable
 * variables.  Such a stable array may contain any type, reference or
 * primitive.  Such an array may be also marked {@code final}, and
 * initialized eagerly in the class or object initializer method.
 * Whether any (or all) of its components are also initialized eagerly
 * is up to the application.
 * <p>
 * More generally, if a stable field is declared as an array type with
 * <em>D</em> dimensions, then all the non-null components of the
 * array, and of any sub-arrays up to a nesting depth less than
 * <em>D</em>, are treated as stable variables.  Thus, a stable field
 * declared as an array potentially defines a tree (of fixed depth
 * <em>D</em>) containing many stable variables, with each such stable
 * variable being independently considered for optimization.  In this
 * way, and depending on program execution, a single {@code Stable}
 * annotation can potentially create many independent stable
 * variables.  Since the top-level array reference is always stable,
 * it is in general a bad idea to resize the array, even while keeping
 * all existing components unchanged.  (This could be relaxed in the
 * future, to allow expansion of stable arrays, if there were a use
 * case that could deal correctly with races.  But it would require
 * careful treatment by the compiler, to avoid folding the wrong
 * version of an array.  Anyway, there are other options, such as
 * tree structures, for organizing the expansion of bundles of stable
 * variables.)
 * <p>
 * An array is never intrinsically stable.  There is no change made to
 * an array as it is assigned to a stable variable of array type.
 * This is true even though after such an assignment, the compiler may
 * observe that array and treat its components as stable variables.
 * If the array is aliased to some other variable, uses via that
 * variable will not be treated as stable.  (Such aliasing is not
 * recommended!)  Also, storing an array into a stable variable will
 * not make that array's components into stable variables, unless the
 * variable into which it is stored is statically typed as an array,
 * in the declaration of the stable field which refers to that array,
 * directly or indirectly.
 *
 * <h2><a id="examples"></a>Examples of Stable Variables</h2>
 *
 * In the following example, the only constant-foldable string stored
 * in any stable variable is the string {@code "S"}.  All subarrays are
 * constant.
 *
 * <pre>{@code
 * @Stable String FIELD = null;  // no foldable value yet
 * @Stable int IDNUM = 0;  // no foldable value yet
 * @Stable boolean INITIALIZED = false;  // no foldable value yet
 * @Stable Object[] ARRAY = {
 *   "S",   // string "S" is foldable
 *   new String[] { "X", "Y" },  // array is foldable, not elements
 *   null  // null is not foldable
 * };
 * @Stable Object[][] MATRIX = {
 *   { "S", "S" },   // constant value
 *   { new String[] { "X", "Y" } },  // array is foldable, not elements
 *   { null, "S" },  // array is foldable, but not the null
 *   null       // could be a foldable subarray later
 * };
 * }</pre>
 *
 * When the following method is called, some of the above stable
 * variables will gain their permanent value, a constant-foldable
 * string "S", or a non-default primitive value.
 *
 * <pre>{@code
 * void publishSomeStables() {
 *   // store some more foldable "S" values:
 *   FIELD = "S";
 *   ARRAY[2] = "S";
 *   MATRIX[2][0] = "S";
 *   MATRIX[3] = new Object[] { "S", "S", null };
 *   // and store some foldable primitives:
 *   IDNUM = 42;
 *   INITIALIZED = true;
 *   VarHandle.releaseFence();  //optional, see below
 * }
 * }</pre>
 *
 * <p>
 * Note that a stable boolean variable (i.e., a stable
 * field like {@code INITIALIZED}, or a stable boolean
 * array element) can be constant-folded,
 * but only after it is set to {@code true}.  Even this simple
 * optimization is sometimes useful for responding to a permanent
 * one-shot state change, in such a way that the compiler can remove
 * dead code associated with the initial state.  As with any stable
 * variable, it is in general a bad idea to reset such a variable to
 * its default (i.e., {@code false}), since compiled code might have
 * captured the {@code true} value as a constant, and as long as that
 * compiled code is in use, the reset value will go undetected.
 *
 * <h2><a id="final"></a>Final Variables, Stable Variables, and Memory Effects</h2>
 *
 * Fields which are declared {@code final} may also be annotated as stable.
 * Since final fields already behave as stable variables, such an annotation
 * conveys no additional information regarding change of the field's value, but
 * it conveys information regarding changes to additional component variables if
 * the type of the field is an array type (as described above).
 * <p>
 * In order to assist refactoring between {@code final} and
 * {@code @Stable} field declarations, the Java Memory Model
 * <em>freeze</em> operation is applied to both kinds of fields, when
 * the assignment occurs in a class or object initializer (i.e.,
 * static initialization code in {@code <clinit>} or constructor code
 * in {@code <init>}).  The freezing of a final or stable field is
 * (currently) triggered only when an actual assignment occurs, directly
 * from the initializer method ({@code <clinit>} or {@code <init>}).
 * It is implemented in HotSpot by an appropriate memory barrier
 * instruction at the return point of the initializer method.  In this
 * way, any non-null (or non-zero) value stored to a stable variable
 * (either field or array component) will appear without races to any
 * user of the class or object that has been initialized.
 * <p>
 * (Note: The barrier action of a class initializer is implicit in the
 * unlocking operation specified in JVMS 5.5, Step 10.  The barrier
 * action of an instance initializer is specified as a "freeze action"
 * in JLS 17.5.1.  These disparate barrier actions have parallel
 * effects on static and non-static final and stable variables.)
 * <p>
 * There is no such JMM freeze operation applied to stable field stores in
 * any other context.  This implies that a constructor may choose to
 * initialize a stable variable, rather than "leaving it for later".
 * Such an initial value will be safely published, as if the field were
 * {@code final}.  The stored value may (or may not) contain
 * additional stable variables, not yet initialized.  Note that if a
 * stable variable is written outside of the code of a constructor (or
 * class initializer), then data races are possible, just the same as
 * if there were no {@code @Stable} annotation, and the field were a
 * regular mutable field.  In fact, the usual case for lazily
 * evaluated data structures is to assign to stable variables much
 * later than the enclosing data structure is created.  This means
 * that racing reads and writes might observe nulls (or primitive
 * zeroes) as well as non-default values.
 *
 * <h2><a id="usage"></a>Proper Handling of Stable Variables</h2>
 *
 * A stable variable can appear to be in either of two states,
 * either uninitialized, or else set to a permanent, foldable value.
 * Therefore, most code which reads stable variables should not assume
 * that the value has been set, and should dynamically test for a null
 * (or zero) value.  Code which cannot prove a previous initialization
 * must perform a null (or zero) test on a value loaded
 * from a stable variable.  Code which omits the null (or zero) test should be
 * documented as to why the initialization order is reliable.  In
 * general, some sort of critical section for initialization should be
 * documented, as provably preceding all uses of the (unchecked)
 * stable variable, or else reasons should be given why races are
 * benign, or some other proof given that races are either excluded or
 * benign.  See below for further discussion.
 * <p>
 * After constant folding, the compiler can make use of many aspects of
 * the object: its dynamic type, its length (if it is an array), and
 * the values of its fields (if they are themselves constants, either
 * final or stable).  It is in general a bad idea to reset such
 * variables to any other value, since compiled code might have folded
 * an earlier stored value, and will never detect the reset value.
 * <p>
 * The HotSpot interpreter is not fully aware of stable annotations,
 * and treats annotated fields (and any affected arrays) as regular
 * mutable variables.  Thus, a field annotated as {@code @Stable} may
 * be given a series of values, by explicit assignment, by reflection,
 * or by some other means.  If the HotSpot compiler constant-folds a
 * stable variable, then in some contexts (execution of fully
 * optimized code) the variable will appear to have one "historical"
 * value, observed, captured, and used within the compiled code to the
 * exclusion of any other possible values.  Meanwhile, in other less
 * optimized contexts, the stable variable will appear to have a more
 * recent value.  Race conditions, if allowed, will make this even
 * more complex, since with races there is no definable "most recent"
 * value across all threads.  The compiler can observe any racing
 * value, as it runs concurrently to the application, in its own
 * thread.
 * <p>
 * It is no good to try to "reset" a stable variable by storing its
 * default again, because there is (currently) no way to find and
 * deoptimize any and all affected compiled code.  If you need the
 * bookkeeping, try {@code SwitchPoint} or {@code MutableCallSite},
 * which both are able to reset compiled code that has captured an
 * intermediate state.
 * <p>
 * Note also each compilation task makes its own decisions about
 * whether to observe stable variable values, and how aggressively to
 * constant-fold them.  And a method that uses a stable variable might
 * be inlined by many different compilation tasks.  The net result of
 * all this is that, if stable variables are multiply assigned, the
 * program execution may observe any "historical" value (if it was
 * captured by some particular compilation task), as well as a "most
 * recent" value observed by the interpreter or less-optimized code.
 * <p>
 * For all these reasons, a user who bends the rules for a stable
 * variable, by assigning several values to it, must state the
 * intended purposes carefully in warning documentation on the
 * relevant stable field declaration.  That user's code must function
 * correctly when observing any or all of the assigned values, at any
 * time.  Alternatively, field assignments must be constrained
 * appropriately so that unwanted values are not observable by
 * compiled code.
 * <p>
 * Any class which uses this annotation is responsible for
 * constraining assignments in such a way as not to violate API
 * contracts of the class.  (If the chosen technique is unusual in
 * some way, it should be documented in a comment on the field.)  Such
 * constraints can be arranged in a variety of ways:
 * <ul><li> using the {@code VarHandle} API to perform an explicit
 * atomic operation such as {@code compareAndExchange},
 * {@code setRelease}, {@code releaseFence}, or the like.
 * </li><li> using regular variable access under explicit sychronization
 * </li><li> using some other kind of critical section to avoid races
 * which could affect compiled code
 * </li><li> allowing multiple assignments under benign races, but
 * only of some separately uniquified value
 * </li><li> allowing multiple assignments under benign races, but
 * only of semantically equivalent values, perhaps permitting
 * occasional duplication of cached values
 * </li><li> concealing the effects of multiple assignments in some
 * other API-dependent way
 * </li><li> providing some other internal proof of correctness, while
 * accounting for all possible racing API accesses
 * </li><li> making some appropriate disclaimer in the API about
 * undefined behavior
 * </li></ul>
 * <p>
 * There may be special times when constant folding of stable
 * variables is disabled.  Such times would amount to a critical
 * section locking out the compiler from reading stable variables.
 * During such a critical section, an uninitialized stable variable
 * can be changed in any way, just like a regular mutable variable
 * (field or array component).  It can even be reset to its default.
 * Specifically, this may happen during certain AOT operations.  If a
 * stable variable can be updated multiple times during such a
 * critical section, that fact must be clearly stated as a comment on
 * the field declaration.  (In the future, there may be explicit
 * AOT-related annotations to convey this use case.)  If there is no
 * such warning, maintainers can safely disregard the possibility of
 * an AOT critical section, since the author of the stable variable is
 * relying on one of the other techniques listed above.
 * <p>
 * It is possible to imagine markings for foldable methods or fields,
 * which can constant-fold a wider variety of states and values.  This
 * annotation does not readily extend to such things, for the simple
 * reason that extra VM bookkeeping would be required to record a
 * wider variety of candidate states for constant folding.  Such
 * higher-level mechanisms may be created in the future.  The present
 * low-level annotation is designed as a potential building block to
 * manage their bookkeeping.
 *
 * @implNote
 * This annotation only takes effect for fields of classes loaded by the boot
 * loader.  Annotations on fields of classes loaded outside of the boot loader
 * are ignored.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Stable {
}
