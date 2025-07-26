# Categorizing new C++17 features for HotSpot

I'm hijacking the PR mechanism as a way to discuss new C++17 features that can
be more easily structured and captured than bare email. Once discussion
settles down I'll turn the results into HotSpot Style Guide changes. I don't
intend to integrate any version of this document to the OpenJDK repository.

Of course, this assumes we're going to move the OpenJDK from C++14 to
C++17. That should also be part of the conversation. Thanks to some
preparatory work, the mainline JDK can already be built with C++17 enabled and
there don't seem to be any problems arising from doing so.

Below is a list of (most of) the new features from C++17, categorized
according to whether they should be (1) permitted in HotSpot, (2) forbidden in
HotSpot, or (3) undecided (so implicitly forbidden). This is an early draft,
and feedback is requested.

The list of features is mostly drawn from the book
[C++17: The Complete Guide](https://www.cppstd17.com/),
by Nicolai M. Josuttis.
It's not a reference. Rather, it provides a summary of each new feature, along
with links to relevant papers. It was invaluable for writing this document,
but shouldn't be required reading for making decisions. Sections here are
chapters and sections from this book. The listed links are also mostly from
this book. Note that there seem to be a few features not covered by this book.

Alternatively, there is a freely available list of features, which includes
very brief summaries:
[Changes between C++14 and C++17 DIS](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2017/p0636r1.html).
There are a couple of features in that paper that aren't in the Josuttis book.

## Permissive vs Restrictive

There are different approaches that could be taken to the catagorization of
new features.

Clearly, if there is some technical blocker, such as relying on RTTI or being
unable to configure allocation, a feature cannot be used in HotSpot.
Similarly for features that are incompatible with existing practice in
HotSpot, such as many new Standard Library features.

Equally clearly, we want to permit features that have strong immediate
use-cases and no technical blockers.

But for many features, it's less clear. And for those we can be initially
inclusive and permit the use of most new features, or we can be restrictive,
and only permit those with known immediate use-cases. Both approaches have
costs and benefits.

Being permissive allows developers to explore usage, potentially finding a
better way to accomplish some task. On the other hand, such exploration can
produce bad usage due to lack of experience with a feature. It can also lead
to review problems, both because reviewers may not be familiar with a feature
and because issues of style and what constitutes readable code may arise.

Another cost of being permissive is that it expands the stuff that all HotSpot
developers must learn or at least be prepared to encounter. That's one of the
reasons we forbid large amounts of C++; not just potential runtime overheads,
but also human training costs. Learning the intricacies of obscure corners of
C++ (and there are a lot of those) may distract making improvements to the
JVM. Meanwhile, we have our own way of doing certain things, some of which are
not mainstream C++, or are problems specific to the JVM. There's plenty for
folks to learn in the JVM, and it's good that they don't have to learn _all_
of C++ as well.

On the other hand, being restrictive may result in dismissal of some feature
that might be exactly what is needed to solve a task elegantly and cleanly,
just because it isn't already in the permitted C++ subset. An example is
Forwarding References, also known as Universal References.

## Permitted features

Some features are permitted without qualifications. In such cases we note the
feature as `PERMITTED_UNQUALIFIED`.

Some features are permitted because they are implicit (don't involve any
explicit syntax or calls) and are unavoidable. In such cases we note the
feature as `PERMITTED_UNAVOIDABLE`. These also have impact on C++ code in the
rest of the OpenJDK, not just on HotSpot code.

### 2. `if` and `switch` with Initialization

Limiting the scope of a variable to the containing statement is
beneficial. The alternative method of scope-limiting via scope nesting isn't
very popular. But complex uses can make the condition form much more
complicated, interfering with readability.

The guidance against using implicit conversions to bool still stands.

[p0305r0](http://wg21.link/p0305r0)
[p0305r1](http://wg21.link/p0305r1)

A specific use-case that was mentioned is UL when checking whether a
`LogTarget` is enabled, e.g.  instead of

```
  LogTarget(...) lt;
  if (lt.is_enabled()) {
    LogStream log(lt);
    ... use log ...
  }
  ... lt is accessible but probably not needed here ...
```

one could write

```
  if (LogTarget(...) lt; lt.is_enabled()) {
    LogStream log(lt);
    ... use log ...
  }
```

or even (if the performance cost of unconditionally creating the stream is okay)

```
  if (LogStreamHandle(...) log; log.is_enabled()) {
    ... use log ...
  }
```

### 3. Inline Variables

`PERMITTED_UNQUALIFIED`

[n4147](http://wg21.link/n4147)
[n4424](http://wg21.link/n4424)
[p0386r2](http://wg21.link/p0386r2)

### 5. Mandatory Copy Elision

`PERMITTED_UNQUALIFIED`
`PERMITTED_UNAVOIDABLE`

Copy elision when initializing an object from a temporary is mandatory. No
copy/move ctor needs to be declared. Note that other forms of copy elision
(such as NRVO) are still optional, and still require a copy/move ctor be
available, even if it might not be used.

No known backward compatibility issues for non-HotSpot OpenJDK.

[p0135r0](http://wg21.link/p0135r0)
[p0135r1](http://wg21.link/p0135r1)

### 6. Lambda Extensions

#### 6.1 `constexpr` Lambdas

`PERMITTED_UNQUALIFIED`

C++17 makes lambdas implicitly `constexpr` when that's possible.

[n4487](http://wg21.link/n4487)
[p0170r1](http://wg21.link/p0170r1)

### 7. New Attributes and Attribute Features

[p0068r0](http://wg21.link/p0068r0)

#### 7.1. `[[nodiscard]]`

`PERMITTED_UNQUALIFIED`

[p0189r1](http://wg21.link/p0189r1)

#### 7.2. `[[maybe_unused]]`

`PERMITTED_UNQUALIFIED`

[p0212r1](http://wg21.link/p0212r1)

#### 7.3. `[[fallthrough]]`

`PERMITTED_UNQUALIFIED`

However, the relevant compiler warning is not currently enabled, or is
disabled, depending on which compiler is involved. Enabling the warnings
likely requires significant work.

[p0188r1](http://wg21.link/p0188r1)

### 8. Other Language Features

#### 8.2. Defined Expression Evaluation Order

`PERMITTED_UNAVOIDABLE`

It is possible that code that previously "worked" despite invoking undefined
behavior will now have defined but different behavior. Hopefully we won't run
into (many) such cases.

[n4228](http://wg21.link/n4228)
[p0145r3](http://wg21.link/p0145r3)

#### 8.3. Relaxed Enum Initialization from Integral Values

`PERMITTED_UNQUALIFIED`

[p0138r0](http://wg21.link/p0138r0)
[p0138r2](http://wg21.link/p0138r2)

#### 8.5. Hexadecimal Floating-Point Literals

`PERMITTED_UNQUALIFIED`

There are floating point values constructed via casts that could be converted
to use this new syntax.

[p0245r0](http://wg21.link/p0245r0)
[p0245r1](http://wg21.link/p0245r1)

#### 8.7. Exception Specifications as Part of the Type

`PERMITTED_UNAVOIDABLE`

The Style Guide needs to be updated regarding exception specifications.
[JDK-8255082](https://bugs.openjdk.org/browse/JDK-8255082)
[PR 25574](https://github.com/openjdk/jdk/pull/25574)

This could cause problems for non-HotSpot OpenJDK code. That would be detected
at build time, and there's no indication of a problem.

[n4320](http://wg21.link/n4320)
[p0012r1](http://wg21.link/p0012r1)

#### 8.8. Single-Argument `static_assert`

`PERMITTED_UNQUALIFIED`

[n3928](http://wg21.link/n3928)

#### 8.9. Preprocessor Condition `__has_include`

`PERMITTED_UNQUALIFIED`

[p0061r0](http://wg21.link/p0061r0)
[p0061r1](http://wg21.link/p0061r1)

### 9. Class Template Argument Deduction

#### 9.1. Use of Class Template

`PERMITTED_UNQUALIFIED`

[n2332](http://wg21.link/n2332)
[n3602](http://wg21.link/n3602)
[p0091r3](http://wg21.link/p0091r3)
[p0512r0](http://wg21.link/p0512r0)
[p0620r0](http://wg21.link/p0620r0)
[p702r1](http://wg21.link/p702r1)

#### 9.2. Deduction Guides

`PERMITTED_UNQUALIFIED`

This feature allows the addition of a scope-guard mechanism with nice syntax.

```
  ScopeGuard guard{[&]{ ... cleanup code ... }};
```

[p0433r2](http://wg21.link/p0433r2)
[p0739r0](http://wg21.link/p0739r0)

### 10. Compile-time If

`PERMITTED_UNQUALIFIED`

For compile-time `if` with initialization, see item 2.

[n3329](http://wg21.link/n3329)
[n4461](http://wg21.link/n4461)
[p0128r0](http://wg21.link/p0128r0)
[p0292r2](http://wg21.link/p0292r2)

### 12. Dealing with String Literals as Template Parameters

`PERMITTED_UNQUALIFIED`

Note: A string literal still can't be used directly as a template parameter.

[n4198](http://wg21.link/n4198)
[n4268](http://wg21.link/n4268)

### 13. Placeholder Types like `auto` as Template Parameters

#### 13.1. Using `auto` for Template Parameters

`PERMITTED_UNQUALIFIED`

See also section 13.2.

[n4469](http://wg21.link/n4469)
[p0127r2](http://wg21.link/p0127r2)

### 21. Extensions to Type Traits

#### 21.1. Type Traits Suffix `_v`

`PERMITTED_UNQUALIFIED`

This is covered by the existing guidance that `<type_traits>` can be used.

[n3854](http://wg21.link/n3854)
[p0006r0](http://wg21.link/p0006r0)

#### 21.2. New Type Traits

`PERMITTED_UNQUALIFIED`

This is covered by the existing guidance that `<type_traits>` can be used.

[lwg2911](http://wg21.link/lwg2911)
[n3619](http://wg21.link/n3619)
[p0185r1](http://wg21.link/p0185r1)
[p0258r0](http://wg21.link/p0258r0)
[p0258r2](http://wg21.link/p0258r2)
[n4446](http://wg21.link/n4446)
[p0604r0](http://wg21.link/p0604r0)
[p0013r0](http://wg21.link/p0013r0)
[p0013r1](http://wg21.link/p0013r1)

### 28. Other Small Library Features and Modifications

#### 28.5. `constexpr` Extensions and Fixes

`PERMITTED_UNQUALIFIED`

Where a function is permitted at all, if it is now `constexpr` then it can be
used as such. Note, however, that most of the functions newly made `constexpr`
by C++17 are forbidden as being in the Standard Library.

[p0505r0](http://wg21.link/p0505r0)
[p0092r1](http://wg21.link/p0092r1)
[p0031r0](http://wg21.link/p0031r0)
[p0426r1](http://wg21.link/p0426r1)

#### 28.6. `noexcept` Extensions and Fixes

`PERMITTED_UNAVOIDABLE`

Where a function is permitted at all, if it is now `noexcept` then it can be
used as such. Note, however, that most of the functions newly made `noexcept`
by C++17 are forbidden as being in the Standard Library.

[n4002](http://wg21.link/n4002)
[n4258](http://wg21.link/n4258)

#### 34.2. Compatibility with C11

`PERMITTED_UNQUALIFIED`

We can now use (some? because of varying levels of compatibility) C11 Standard
Library features from HotSpot (and any other part of the OpenJDK written in
C++).

Not that it matters much, but we already select C11 (where possible) for parts
of the OpenJDK written in C.

This might have some impact on non-HotSpot OpenJDK code written in C++.

[p0063r0](http://wg21.link/p0063r0)
[n3631](http://wg21.link/n3631)
[p0063r3](http://wg21.link/p0063r3)

### 35. Deprecated and Removed Features

#### 35.1. Deprecated and Removed Core Language Features

##### 35.1.1. Throw Specifications

We don't use non-empty `throw` specifications in the OpenJDK. Where we use empty
`throw` specifications, we should switch over to using `noexcept`.

There don't appear to be any non-empty `throw` specifications elsewhere in the
OpenJDK.

See also 8.7.

[p0003r5](http://wg21.link/p0003r5)

##### 35.1.5. Definition/Redeclaration of `static constexpr` Members

(Not in Jossutis book)
`PERMITTED_UNQUALIFIED`

Since C++14 a definition was only needed when the member is ODR-used. That's
relatively rare, so it's likely there aren't many of these no longer needed
definitions. Generalizing the range-based for loop

[p0184r0](http://wg21.link/p0184r0)

## Forbidden features

Some features are forbidden because HotSpot doesn't use most of the C++
Standard Library. In such cases we note the feature as
`FORBIDDEN_STANDARD_LIBRARY`.

Some features are forbidden based on a return on investment argument. If a
feature is permitted then any HotSpot developer may encounter a use, so must
be at least somewhat familiar with it. But if the provided benefit is
(perceived to be) small, or (especially) if the feature is expected to be used
rarely, making every HotSpot developer learn about it may not be a good use of
their time. In such cases we note the feature as `FORBIDDEN_ROI`.

### 1. Structured Bindings

`FORBIDDEN_ROI`

[p0144r0](http://wg21.link/p0144r0)
[p0217r3](http://wg21.link/p0217r3)

### 4. Aggregate Extensions

`FORBIDDEN_ROI`

[n4404](http://wg21.link/n4404)
[p0017r1](http://wg21.link/p0017r1)

### 6. Lambda Extensions

#### 6.2. Passing Copies of `this` to Lambdas

One of the motivating use-cases is when the lifetime of the lambda exceeds the
lifetime of the object for the containing member function. That is, we have an
upward lambda that is capturing `this` of the enclosing method. But since we
already forbid the use of upward lambdas, that use-case isn't relevant.

Another use-case is when we simply want the lambda to be operating on a copy
of `this` for some reason. `FORBIDDEN_ROI`.

[p0018r0](http://wg21.link/p0018r0)
[p0180r3](http://wg21.link/p0180r3)

### 6.3. Capturing by `const` Reference

An explicit capture parameter can be captured by `const` reference through the
use of the library function `std::as_const`. But explicit capture parameters
are recommended against in HotSpot. `FORBIDDEN_ROI`.

### 7. New Attributes and Attribute Features

#### 7.4. General Attribute Extensions

##### 7.4.1. Attributes for namespaces

Probably not relevant for HotSpot. The only relevant attribute seems to be
`[[deprecated]]` and we wouldn't use that in HotSpot.

[n4196](http://wg21.link/n4196)
[n4266](http://wg21.link/n4266)

##### 7.4.2. Attributes for enumerators

Probably not relevant for HotSpot. The only relevant attribute seems to be
`[[deprecated]]` and we wouldn't use that in HotSpot.

[n4196](http://wg21.link/n4196)
[n4266](http://wg21.link/n4266)

##### 7.4.3. `using` directive in attribute lists.

We don't generally use scoped attributes in attribute lists with other
attributes. Rather, any use of scoped attributes (which are implementation
defined) are generally hidden behind a portability macro that includes the
surrounding brackets.

[p0028r0](http://wg21.link/p0028r0)
[p0028r4](http://wg21.link/p0028r4)

### 13. Placeholder Types like `auto` as Template Parameters

#### 13.3. Using `decltype(auto)` as Template Parameter

`FORBIDDEN_ROI`

[n4469](http://wg21.link/n4469)
[p0127r2](http://wg21.link/p0127r2)

### 14. Extended `using` Declarations

#### 14.1. Using Variadic Using Declarations

`FORBIDDEN_ROI`

[p0195r0](http://wg21.link/p0195r0)
[p0195r2](http://wg21.link/p0195r2)

#### 14.2. Variadic Using Declarations for Inheriting Constructors

`FORBIDDEN_ROI`

Inherited constructors are currently a forbidden feature in the style guide,
because there are problems with the specification. Those problems are fixed in
C++17, but it remains dubious that we would use this feature enough make it
worth permitting. Even more so for this new feature.

[n4429](http://wg21.link/n4429)
[p0136r1](http://wg21.lini/p0136r1)

### 16. `std::variant<>`

`FORBIDDEN_STANDARD_LIBRARY`

Additionally, invalid access is indicated by throwing an exception.

[n4218](http://wg21.link/n4218)
[p0088r3](http://wg21.link/p0088r3)
[p0393r3](http://wg21.link/p0393r3)
[p0032r3](http://wg21.link/p0032r3)
[p0504r0](http://wg21.link/p0504r0)
[p0510r0](http://wg21.link/p0510r0)
[p0739r0](http://wg21.link/p0739r0)

### 17. `std::any`

`FORBIDDEN_STANDARD_LIBRARY`

Additionally

* It requires heap allocation for values whose size is greater than some
implementation-dependent small buffer size. There is no mechanism for
specifying an allocator for that allocation.

* It requires RTTI, but HotSpot is built without RTTI support when that option
is available.

* Invalid access may be indicated by throwing an exception. Need to use
alternative form of `any_cast` with a pointer to the `any` to get `nullptr`
instead of an exception for invalid access.

[n1939](http://wg21.link/n1939)
[n3904](http://wg21.link/n3904)
[p0220r1](http://wg21.link/p0220r1)
[p0032r3](http://wg21.link/p0032r3)
[p0504r0](http://wg21.link/p0504r0)

### 20. File System Library

`FORBIDDEN_STANDARD_LIBRARY`
`FORBIDDEN_ROI`

HotSpot doesn't do all that much with files, and already has adequate
mechanisms for its needs. Rewriting in terms of this new library doesn't seem
like a good use of resources. Having a mix of the existing usage and uses of
this new library would just be confusing.

[n4100](http://wg21.link/n4100)
[p0218r0](http://wg21.link/p0218r0)
[p0219r1](http://wg21.link/p0219r1)
[p0317r1](http://wg21.link/p0317r1)
[p0392r0](http://wg21.link/p0392r0)
[p0430r2](http://wg21.link/p0430r2)
[p0492r2](http://wg21.link/p0492r2)
[p1164r1](http://wg21.link/p1164r1)

### 22. Parallel STL Algorithms

Built on the standard C++ threading mechanisms. HotSpot doesn't use those
mechanisms, instead providing and using its own.

[n3850](http://wg21.link/n3850)
[n4276](http://wg21.link/n4276)
[p0024r2](http://wg21.link/p0024r2)
[p0394r4](http://wg21.link/p0394r4)

### 23. New STL Algorithms in Detail

`FORBIDDEN_STANDARD_LIBRARY`

[n3408](http://wg21.link/n3408)
[n3950](http://wg21.link/n3950)
[n4276](http://wg21.link/n4276)
[p0024r2](http://wg21.link/p0024r2)
[p0394r4](http://wg21.link/p0394r4)

### 25. Other Utility Functions and Algorithms

#### 25.1. `size()`, `empty()`, and `data()`

`FORBIDDEN_STANDARD_LIBRARY`

[n4017](http://wg21.link/n4017)
[n4280](http://wg21.link/n4280)

#### 25.2. `as_const()`

`FORBIDDEN_STANDARD_LIBRARY`

If sufficiently useful, HotSpot could add such a function, perhaps in
globalDefinitions.hpp.

[n4380](http://wg21.link/n4380)
[p0007r1](http://wg21.link/p0007r1)

#### 25.3. `clamp()`

`FORBIDDEN_STANDARD_LIBRARY`

HotSpot has a similar (identical?) function in globalDefinitions.hpp.

[n4536](http://wg21.link/n4536)
[p002501](http://wg21.link/p002501)

#### 25.4. `sample()`

`FORBIDDEN_STANDARD_LIBRARY`

[n3842](http://wg21.link/n3842)
[n3925](http://wg21.link/n3925)

### 26. Container and String Extensions

`FORBIDDEN_STANDARD_LIBRARY`

There are several subsections in this chapter of the book, but all are covered
by the blanket forbidden category.

[lwg839](http://wg21.link/lwg839)
[lwg1041](http://wg21.link/lwg1041)
[p0083r3](http://wg21.link/p0083r3)
[p0508r0](http://wg21.link/p0508r0)

[p0084r0](http://wg21.link/p0084r0)
[p0084r2](http://wg21.link/p0084r2)

[n3873](http://wg21.link/n3873)
[n4279](http://wg21.link/n4279)

[184403814](http://drdobbs.com/184403814)
[n3890](http://wg21.link/n3890)
[n4510](http://wg21.link/n4510)
[lwg2391](http://wg21.link/lwg2391)
[p0272r1](http://wg21.link/p0272r1)

### 27. Multithreading and Concurrency

#### 27.1. Supplementary Mutexes and Locks

`FORBIDDEN_STANDARD_LIBRARY`

HotSpot has its own threading mechanisms and mutexes, and does not use the
Standard Library in this area.

[n4470](http://wg21.link/n4470)
[p0156r0](http://wg21.link/p0156r0)
[p0156r2](http://wg21.link/p0156r2)
[p0739r0](http://wg21.link/p0739r0)

[n2406](http://wg21.link/n2406)
[n4508](http://wg21.link/n4508)

#### 27.2. `is_always_lock_free` for Atomics

`FORBIDDEN_STANDARD_LIBRARY`

HotSpot has its own atomics, and does not use the Standard Library in this area.

[n4509](http://wg21.link/n4509)
[p0152r1](http://wg21.link/p0152r1)

#### 27.3. Cache Line Sizes

`FORBIDDEN_STANDARD_LIBRARY`

HotSpot has its own mechanisms for this. It's not clear that switching to
these standard mechanisms is worthwhile. Quoting from the Josuttis book:

"... if you know better, use specific values, but using these values is better
than any assumed fixed size for code supporting multiple platforms."

[n4523](http://wg21.link/n4523)
[p0154r1](http://wg21.link/p0154r1)

### 28. Other Small Library Features and Modifications

#### 28.1. `std::uncaught_exceptions()`

Not useful, since HotSpot is built with exceptions disabled.

[n3614](http://wg21.link/n3614)
[n4259](http://wg21.link/n4259)

#### 28.2. Shared Pointer Improvements

`FORBIDDEN_STANDARD_LIBRARY`

[n3640](http://wg21.link/n3640)
[p0414r2](http://wg21.link/p0414r2)
[n4537](http://wg21.link/n4537)
[p0163r0](http://wg21.link/p0163r0)
[p0033r0](http://wg21.link/p0033r0)
[p0033r1](http://wg21.link/p0033r1)

#### 28.3. Numeric Extensions

`FORBIDDEN_STANDARD_LIBRARY`

[n3845](http://wg21.link/n3845)
[p0295r0](http://wg21.link/p0295r0)
[p0030r0](http://wg21.link/p0030r0)
[p0030r1](http://wg21.link/p0030r1)
[n1422](http://wg21.link/n1422)
[p0226r1](http://wg21.link/p0226r1)

#### 28.4. `chrono` Extensions

`FORBIDDEN_STANDARD_LIBRARY`

[p0092r0](http://wg21.link/p0092r0)
[p0092r1](http://wg21.link/p0092r1)

### 29. Polymorphic Memory Resources

`FORBIDDEN_STANDARD_LIBRARY`

If we ever start using Standard Library containers, we might base the
allocators we provide on this set of features and use the corresponding
containers.

[n3525](http://wg21.link/n3525)
[n3916](http://wg21.link/n3916)
[p0220r1](http://wg21.link/p0220r1)

### 34. Common C++ Settings

#### 34.1. Value of `__cplusplus`

We build HotSpot (and the rest of the OpenJDK) with a specifically selected
version of the Standard. Hence, the value of `__cplusplus` should be known and
unchanging until we change the project's build configuration again. So testing
the value shouldn't ever be necessary.

### 35. Deprecated and Removed Features

#### 35.1. Deprecated and Removed Core Language Features

##### 35.1.2. Keyword `register`

We don't use the `register` keyword in the OpenJDK anymore?

[p0001r1](http://wg21.link/p0001r1)

#### 35.1.3. Disable `++` for `bool`

We don't do this anywhere in OpenJDK?

[p0003r1](http://wg21.link/p0003r1)

#### 35.1.4. Trigraphs

We don't use trigraphs in OpenJDK code.

[n4086](http://wg21.link/n4086)

#### 35.2. Deprecated and Removed Library Features

##### 35.2.1. `auto_ptr`

`auto_ptr` is not used in the OpenJDK.

[n4168](http://wg21.link/n4168)
[n4190](http://wg21.link/n4190)

#### 35.2.2. Algorithm `random_shuffle()`

`random_shuffle()` is not used in the OpenJDK.

[n4190](http://wg21.link/n4190)

#### 35.2.3. `unary_function` and `binary_function`

These aren't used in the OpenJDK.

#### 35.2.4. `ptr_fun()`, `mem_fun()`, and Binders

These aren't used in the OpenJDK.

#### 35.2.5. Allocator Support for `std::function<>`

This feature was never implemented for gcc, to the extent that the API was
never added. There are no uses of allocator support in the OpenJDK.

[p0302r1](http://wg21.link/p0302r1)

#### 35.2.6. Deprecated IOStream Aliases

[p0004r1](http://wg21.link/p0004r1)

#### 35.2.7. Deprecated Library Features

* `result_of<>` - [p0604r0](http://wg21.link/p0604r0)

* `unique()` for shared pointers - [p0521r0](http://wg21.link/p0521r0)

* `<codecvt>` - [p0618r0](http://wg21.link/p0618r0)

* `is_literal_type<>`, `iterator<>`, `raw_storage_iterator<>`,
`get_temporary_buffer()`, `allocator<void>` - [p0174r2](http://wg21.link/p0174r2)

* `<ccomplex>`, `<cstdalign>`, `<cstdbool>`, `<ctgmath>` -
[p0063r3](http://wg21.link/p0063r3)

* `memory_order_consume` - [p0371r1](http://wg21.link/p0371r1)

### Ignoring unsupported non-standard attributes

(Not in Jossutis book)

This might allow non-standard scoped attributes to be used in shared code
without the macros currently used in HotSpot for non-standard attributes. But
since we already have the macro technique well-established, this doesn't seem
all that useful.

May require disabling a warning for some compilers.

[p0283r2](http://wg21.link/p0283r2)

## Undecided features

Some features are undecided (so implicitly forbidden) because we don't expect
to use them at all. This might be reconsidered if someone finds a good use
case. In such cases we note the feature as `UNDECIDED_WHO_CARES`.

Some Standard Library features are undecided (so implicitly forbidden)
because, while the HotSpot Style Guide forbids the use of such, they may be
sufficiently useful that we want to permit them anyway. Doing so may require
some idiomatic mechanism for addressing things like `assert` incompatibility,
incompatibility with HotSpot's `FORBID_C_FUNCTION` mechanism, and the like. In
such cases we note the feature as `UNDECIDED_STANDARD_LIBRARY`.

### 8. Other Language Features

#### 8.1. Nested Namespaces

`UNDECIDED_WHO_CARES`

We don't use namespaces (especially nested namespaces) much, or at all.

[n1524](http://wg21.link/n1524)
[n4026](http://wg21.link/n4026)
[n4230](http://wg21.link/n4230)

#### 8.4. Fixed Direct List Initialization with `auto`

`UNDECIDED_WHO_CARES`

We don't use direct list initialization with `auto` much, or at all.

[n3681](http://wg21.link/n3681)
[n3912](http://wg21.link/n3912)
[n3681](http://wg21.link/n3681)

#### 8.6. UTF-8 Character Literals

`UNDECIDED_WHO_CARES`

[n4197](http://wg21.link/n4197)
[n4267](http://wg21.link/n4267)

### 11. Fold Expressions

`UNDECIDED_WHO_CARES`

HotSpot doesn't use variadic templates very much.

The requirement that the "operator" must be one of the standard binary
operators and the precedence restriction might seem like signficantly
limiting. However, pack expansion on the `pack` argument, and the possibility
of using the comma operator, can achieve a lot.

This could fall under `FORBIDDEN_ROI`. But if someone does come up with a good
use, it's likely that alternatives are significantly worse, because pack
manipulation without this can be pretty contorted.

[n4191](http://wg21.link/n4191)
[n4295](http://wg21.link/n4295)
[p0036](http://wg21.link/p0036)

### 13. Placeholder Types like `auto` as Template Parameters

#### 13.2. Using `auto` as Variable Template Parameter

Variable templates are a C++14 feature that is categorized as "Undecided" in
the HotSpot Style Guide. Being a change to that feature, this new feature is
also being left as undecided.

[n4469](http://wg21.link/n4469)
[p0127r2](http://wg21.link/p0127r2)

### 15. `std::optional<>`

`UNDECIDED_STANDARD_LIBRARY`

Additionally, invalid access may be indicated by throwing an exception.
`value()` throws, `operator*()` and `operator->()` are UB. So use of `value()`
should be forbidden even if `std::optional<>` were permitted.

[n1878](http://wg21.link/n1878)
[n3793](http://wg21.link/n3793)
[p0220r1](http://wg21.link/p0220r1)
[n3765](http://wg21.link/n3765)
[p0307r2](http://wg21.link/p0307r2)
[p0032r3](http://wg21.link/p0032r3)
[p0504r0](http://wg21.link/p0504r0)

### 18. `std::byte`

`UNDECIDED_STANDARD_LIBRARY`

It was suggested that changing the HotSpot `address` type to use `std::byte`
has some benefits. That is, replace
```
typedef u_char*       address;
typedef const u_char* const_address;
```
```
using address       = std::byte*;
using const_address = const std::byte*;
```
in globalDefinitions.hpp.

A specific benefit that was mentioned is that it might improve the horrible
way that gdb handles our current definition of the `address` type.

```
#include <cstddef>

typedef unsigned char* address;
typedef std::byte* address_b;

int main() {

  char* mem;

  address addr = (address)mem;
  address_b addr_b = (address_b)mem;

  return 0;
}
```

```
(gdb) p addr
$1 = (address) 0x7ffff7fe4fa0 <dl_main> "\363\017\036\372Uf\017\357\300H\211\345AWI\211\377AVAUATSH\201\354\210\002"
(gdb) p addr_b
$2 = (address_b) 0x7ffff7fe4fa0 <dl_main>
```

This needs to be explored. Some folks have said they will do so.

[p0298r0](http://wg21.link/p0298r0)
[p0298r3](http://wg21.link/p0298r3)

### 19. String Views

`UNDECIDED_STANDARD_LIBRARY`

HotSpot doesn't use `std::string`, but uses `char*` strings a lot. Wrapping
such in a `std::string_view` to enable the use of various algorithms could be
useful. But since HotSpot also doesn't permit use of `<algorithm>` and the
like, that only gets the limited set of algorithms provided by the view class
directly.

There is also the issue of `NUL` termination; string views are not necessarily
`NUL` terminated. Moreover, if one goes to the work of making one that is
`NUL` terminated, that terminator is included in the size.

There are other caveats. Permitting use of string views would require
discussion of those.

[n3334](http://wg21.link/n3334)
[n3921](http://wg21.link/n3921)
[p0220r1](http://wg21.link/p0220r1)
[p0254r2](http://wg21.link/p0254r2)
[p0403r1](http://wg21.link/p0403r1)
[p0392r0](http://wg21.link/p0392r0)
[p0426r1](http://wg21.link/p0426r1)
[lwg2946](http://wg21.link/lwg2946)

### 24. Substring and Subsequence Searching

`UNDECIDED_STANDARD_LIBRARY`
`UNDECIDED_WHO_CARES`

In addition to simple substring searching, the Standard Library now includes
Boyer-Moore and Boyer-Moore-Horspool searchers, in case someone wants to
search really large texts. That seems an unlikely use-case for HotSpot.

[n3411](http://wg21.link/n3411)
[p0220r1](http://wg21.link/p0220r1)
[p0253r1](http://wg21.link/p0253r1)

### 30. `new` and `delete` with Over-Aligned Data

HotSpot currently only has a couple of over-aligned types that are dynamically
allocated. These are handled manually, not going through `new` expressions, as
that couldn't work before C++17.

One of the ways an over-aligned type might arise is by aligning a data member.
This might be done to avoid destructive interference for concurrent accesses.
But HotSpot uses a different approach, using explicit padding. Again, this is
in part because `new` and `delete` of overaligned types didn't work. But we
might prefer to continue this approach.

We would need to add `operator new` overloads to `CHeapObj<>` and possibly in
other places in order to support this. However, it has been suggested that
implementing it (efficiently) on top of NMT might be difficult. Note that
`posix_memalign` / `_aligned_malloc` don't help here, because of NMT's use of
malloc headers.

If we don't support it we may want to add `operator new` overloads that are
deleted, to prevent attempted uses.

Alignment usage in non-HotSpot parts of the OpenJDK:

* `alignas` used once in harfbuzz, to align a variable.

* libpipewire has `#define SPA_ALIGNED` macro using gcc `aligned` attribute,
but doesn't use it.

* libsleef has `#define ALIGNED` macro using gcc `aligned` attribute. It is
not used for class or member declarations.

[n3396](http://wg21.link/n3396)
[p00354](http://wg21.link/p00354)

### 31. `std::to_chars()` and `std::from_chars()`

`UNDECIDED_STANDARD_LIBRARY`

Low-level conversions between character sequences and numeric values. This
seems like a good candidate for use in HotSpot, potentially replacing various
clumsy or less performant alternatives. No memory allocation, parsing failures
indicated via error codes rather than exceptions, and various other nice for
HotSpot properties.

Note that the published C++17 puts these in `<utility>`, but a defect report
moved them to `<charconv>`. Also needs `<system_error>`.

Would require upgrading minimum gcc version to 11.1 for floating point
conversion support. Minimum Visual Studio version is already sufficient.
Haven't found minimum clang version requirement yet.

[p0067r0](http://wg21.link/p0067r0)
[p0067r5](http://wg21.link/p0067r5)
[p0682r1](http://wg21.link/p0682r1)

### 32. `std::launder()`

`UNDECIDED_STANDARD_LIBRARY`

Change to permitted if we discover a place where we need it. Or maybe we
should just permit it, but hope we don't need it.

Also, C++20 revised the relevant part of Object Lifetime in a way that seems
more permissive and with less need of laundering. We don't know if
implementations of prior versions take advantage of the difference.

Object Lifetime: C++17 6.8/8, C++20 6.7.3/8

[n3903](http://wg21.link/n3903)
[cwg1776](http://wg21.link/cwg1776)
[n4303](http://wg21.link/n4303)
[p0137r1](http://wg21.link/p0137r1)

### 33. Improvements for Implementing Generic Code

#### 33.1. `std::invoke<>()`

`UNDECIDED_WHO_CARES`
`UNDECIDED_STANDARD_LIBRARY`

[n3727](http://wg21.link/n3727)
[n4169](http://wg21.link/n4169)
[p0604r0](http://wg21.link/p0604r0)

### 34. Common C++ Settings

#### 34.3. Dealing with Signal Handlers

Is there anything actionable for us here?

Specifies the set of C++ Standard Library functions that are signal-safe. But
since we mostly don't use the Standard Library, this doesn't matter much.

[p0270r0](http://wg21.link/p0270r0)
[p0270r3](http://wg21.link/p0270r3)

#### 34.4. Forward Progress Guarantees

Is there anything actionable for us here?

[p0296r2](http://wg21.link/p0296r2)

### Allow `typename` in a template template parameter

(Not in Jossutis book)
`UNDECIDED_WHO_CARES`

Template template parameters are barely used (if at all) in HotSpot.

[n4051](http://wg21.link/n4051)

### Further uninitialized algorithms

(Not in Jossutis book)
`UNDECIDED_STANDARD_LIBRARY`

[p0040r3](http://wg21.link/p0040r3)



