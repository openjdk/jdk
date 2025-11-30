Constant Folding in the Hotspot Compiler
===

Hotspot compiler can fold constant field value access when it constructs its IR
(intermediate representation), unlocking significant performance improvements.
However, it is implemented as a dangerous primitive that can lead to incorrect
programs if used incorrectly.

## What is constant folding?

Constant folding means a read of a variable of a constant value can be replaced
by the read constant value, during the construction of an IR graph.  The
related logic resides in `ci/ciField.hpp` (compiler interface field).

For example, if a field `int a` has a constant value `4` and a field `int b` has
constant value `5`, the constant folding will replace the `a + b` in the IR with
a value of `9`.

## How is a value determined to be constant?

Constantness is decided on a per-variable and per-value basis.  This includes
the location of the variable so that a variable might have a constant value,
and the value of the variable so the read value is constant.

### Field constants

Whether a field may have a constant value is determined by the
`ciField::is_constant()` method in Hotspot.  The value of `_is_constant` is
determined in `ciField::initialize_from`.  It is roughly as follows:

1. A field may be constant if it is stable.
2. Before Java 9, `putfield`  in `<clinit>` could write to an instance final
   field and `putstatic` in `<init>` could write to a static final field.  Such
   written final fields from pre-53-major classes are considered never constant.
3. Otherwise, if a static final field is not `System.in`, `System.out`, or
   `System.err`, it may be constant.
4. If an instance final field comes from a record class, a hidden class, it may
   be constant.
5. If an instance final field is declared in a system class that is either:

   1. Marked `@TrustFinalFields`
   2. In one of the trusted system packages specified in
      `trust_final_non_static_fields` in `ciField.cpp`

   It may be constant.  Note that such a field is not protected from reflective
   modification through core reflection `Field.set`.
6. If the field is `CallSite.target`, it may be constant. (This has extra
   treatments like nmethod dependency, so is not quite as other constant
   fields)

A `ciField` models a field declaration in the JVM, so an inherited field (as
in a field reference, static or instance) in a subclass or subinterface shares
the constantness settings.

After a field is considered to be possibly constant, its value is fetched from
the runtime and examined.  If the field is stable, and the value is zero or null
(the default value), this read value is not constant.  Only non-stable final
fields can have their zero or null values considered constant.

### Array constants

If an array field is stable, the type system in the compiler of Hotspot marks
the array to be stable up to its declared level.  (See the code of the most
generic variant of `Type::make_constant_from_field`)  As a result, access to
nested array components _with a constant index_ can be treated as a constant
value, if the read value is not zero or null (the default value).

This means the stable annotation is not as helpful for random access (it only
elides loading the array reference), and null components in an "immutable" array
may cause surprising slowdowns.

## How can I verify constant folding?

Since constant folding makes a huge difference in API performance characteristics,
tests are necessary to guarantee they happen.

The most reliable way to ensure folding is IR tests in the compiler; we can
expect compiler to eliminate known foldable IR structures when its inputs are
eligible.  For example, in the initial constant folding example of `a + b`, an
IR test can verify the int addition is eliminated in the resulting IR by
constant folding.

An example test is `compiler.c2.irTests.constantFold.TestOptional`.  Note that
IR tests need to be run in a debug (fastdebug) configure profile, which is not
used for most jdk library tests.

JMH benchmarks can be another way to verify, except they are costly to run and
their trend is hard to track.  Prefer the compiler tests instead.

## Relation to final mechanisms

### `Field.trustedFinal` property

A `Field` object has a `trustedFinal` field, which when set to `true`, prevents
core reflection or method handles from creating a setter for this field in any
scenario.  This is derived from `fieldDescriptor::is_trusted_final()`, which
designates final fields that are static, declared in a record class, or declared
in a hidden class as `trustedFinal`.  This rule is different from the "may be
constant variable" rule from above; in particular, it does not protect the
instance final fields in eligible system classes per rule 5 above.

Note that a `Field` object also models a field declaration in the JVM like a
`ciField`, so an inherited field in a subclass or subinterface shares the
`trustedFinal` setting.

### Make Final Mean Final

As noted for `Field.trustedFinal`, protections are missing for some system
classes.  The effort to make final mean final, that all illegal final field
modifications must use `--enable-final-field-mutation` and `--add-opens`,
represents a step toward this goal.

In JEP 500, when `--illegal-final-field-mutation=deny`, a final field `f` is
still mutable if for some module `M` (including the unnamed module) that could
perform final field mutation, one of the following stands:

1. `f` is a public field declared in a public class, and the package
   of the declaring class is exported by the containing module to `M`.
2. The package of the declaring class of `f` is open to `M`.
3. `f` is in `M`.

This set of rules is more complex than the existing logic in `fieldDescriptor`,
and being in `jdk.internal.module.ModuleBootstrap`, is not easy to export to
the JVM runtime.  In addition, the performance implication of enabling final
field mutation on the command line may also be concerning to users.

In conclusion, trusting based on final field mutation settings is possible, but
whether the cost is worth the return is under investigation.
