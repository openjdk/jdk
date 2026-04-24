% HotSpot Coding Style

## Introduction

This is a collection of rules, guidelines, and suggestions for writing
HotSpot code.  Following these will help new code fit in with existing
HotSpot code, making it easier to read and maintain.  Failure to
follow these guidelines may lead to discussion during code reviews, if
not outright rejection of a change.

### Changing this Document

Proposed changes should be discussed on the
[HotSpot Developers](mailto:hotspot-dev@openjdk.org) mailing
list.  Changes are likely to be cautious and incremental, since HotSpot
coders have been using these guidelines for years.

Substantive changes are approved by
[rough consensus](https://www.rfc-editor.org/rfc/rfc7282.html) of
the [HotSpot Group](https://openjdk.org/census#hotspot) Members.
The Group Lead determines whether consensus has been reached.

Editorial changes (changes that only affect the description of HotSpot
style, not its substance) do not require the full consensus gathering
process.  The normal HotSpot pull request process may be used for
editorial changes, with the additional requirement that the requisite
reviewers are also HotSpot Group Members.

### Why Care About Style?

Some programmers seem to have lexers and even C preprocessors
installed directly behind their eyeballs. The rest of us require code
that is not only functionally correct but also easy to read. More than
that, since there is no one style for easy-to-read code, and since a
mashup of many styles is just as confusing as no style at all, it is
important for coders to be conscious of the many implicit stylistic
choices that historically have gone into the HotSpot code base.

Some of these guidelines are driven by the cross-platform requirements
for HotSpot.  Shared code must work on a variety of platforms, and may
encounter deficiencies in some.  Using platform conditionalization in
shared code is usually avoided, while shared code is strongly
preferred to multiple platform-dependent implementations, so some
language features may be recommended against.

Some of the guidelines here are relatively arbitrary choices among
equally plausible alternatives.  The purpose of stating and enforcing
these rules is largely to provide a consistent look to the code.  That
consistency makes the code more readable by avoiding non-functional
distractions from the interesting functionality.

When changing pre-existing code, it is reasonable to adjust it to
match these conventions. Exception: If the pre-existing code clearly
conforms locally to its own peculiar conventions, it is not worth
reformatting the whole thing.  Also consider separating changes that
make extensive stylistic updates from those which make functional
changes.

### Counterexamples

Many of the guidelines mentioned here have (sometimes widespread)
counterexamples in the HotSpot code base. Finding a counterexample is
not sufficient justification for new code to follow the counterexample
as a precedent, since readers of your code will rightfully expect your
code to follow the greater bulk of precedents documented here.

Occasionally a guideline mentioned here may be just out of synch with
the actual HotSpot code base. If you find that a guideline is
consistently contradicted by a large number of counterexamples, please
bring it up for discussion and possible change. The architectural
rule, of course, is "When in Rome do as the Romans". Sometimes in the
suburbs of Rome the rules are a little different; these differences
can be pointed out here.

## Structure and Formatting

### Factoring and Class Design

* Group related code together, so readers can concentrate on one
section of one file.

* Classes are the primary code structuring mechanism.  Place related
functionality in a class, or a set of related classes.  Use of either
namespaces or public non-member functions is rare in HotSpot code.
Static non-member functions are not uncommon.

* If a class `FooBar` is going to be used in more than one place, put it
a file named fooBar.hpp and fooBar.cpp. If the class is a sidekick
to a more important class `BazBat`, it can go in bazBat.hpp.

* Put a member function `FooBar::bang` into the same file that defined
`FooBar`, or its associated *.inline.hpp or *.cpp file.

* Use public accessor functions for member variables accessed
outside the class.

* Assign names to constant literals and use the names instead.

* Keep functions small, a screenful at most.  Split out chunks of
logic into file-local classes or static functions if needed.

* Factor away nonessential complexity into local inline helper
functions and helper classes.

* Think clearly about internal invariants that apply to each class,
and document them in the form of asserts within member functions.

* Make simple, self-evident contracts for member functions.  If you cannot
communicate a simple contract, redesign the class.

* Implement classes as if expecting rough usage by clients. Check for
incorrect usage of a class using `assert(...)`, `guarantee(...)`,
`ShouldNotReachHere()` and comments wherever needed.  Performance is
almost never a reason to omit asserts.

* When possible, design as if for reusability.  This forces a clear
design of the class's externals, and clean hiding of its internals.

* Initialize all variables and data structures to a known state. If a
class has a constructor, initialize it there.

* Do no optimization before its time. Prove the need to optimize.

* When you must defactor to optimize, preserve as much structure as
possible. If you must hand-inline some name, label the local copy with
the original name.

* If you need to use a hidden detail (e.g., a structure offset), name
it (as a constant or function) in the class that owns it.

* Don't use the Copy and Paste keys to replicate more than a couple
lines of code.  Name what you must repeat.

* If a class needs a member function to change a user-visible attribute, the
change should be done with a "setter" accessor matched to the simple
"getter".

#### Conventions for Lock-free Code

Sometimes variables are accessed concurrently without appropriate synchronization
context, such as a held mutex or at a safepoint. In such cases the variable should
be declared `volatile` and it should NOT be accessed as a normal C++ lvalue. Rather,
access should be performed via functions from `Atomic`, such as `Atomic::load`,
`Atomic::store`, etc.

This special formulation makes it more clear to maintainers that the variable is
accessed concurrently in a lock-free manner.

### Source Files

* All source files must have a globally unique basename. The build
system depends on this uniqueness.

* Keep the include lines within a section alphabetically sorted by their
lowercase value. If an include must be out of order for correctness,
suffix with it a comment such as `// do not reorder`. Source code
processing tools can also use this hint.

* Put conditional inclusions (`#if ...`) at the end of the section of HotSpot
include lines. This also applies to macro-expanded includes of platform
dependent files.

* Put system includes in a section after the HotSpot include lines with a blank
line separating the two sections.

* Do not put non-trivial function implementations in .hpp files. If
the implementation depends on other .hpp files, put it in a .cpp or
a .inline.hpp file.

* .inline.hpp files should only be included in .cpp or .inline.hpp
files.

* All .inline.hpp files should include their corresponding .hpp file as
the first include line with a blank line separating it from the rest of the
include lines. Declarations needed by other files should be put in the .hpp
file, and not in the .inline.hpp file. This rule exists to resolve problems
with circular dependencies between .inline.hpp files.

* Do not include a .hpp file if the corresponding .inline.hpp file is included.

* Use include guards for .hpp and .inline.hpp files. The name of the defined
guard should be derived from the full search path of the file relative to the
hotspot source directory. The guard should be all upper case with all paths
separators and periods replaced by underscores.

* Some build configurations use precompiled headers to speed up the
build times. The precompiled headers are included in the precompiled.hpp
file. Note that precompiled.hpp is just a build time optimization, so
don't rely on it to resolve include problems.

### JTReg Tests

* JTReg tests should have meaningful names.

* JTReg tests associated with specific bugs should be tagged with the
`@bug` keyword in the test description.

* JTReg tests should be organized by component or feature under
`test/`, in a directory hierarchy that generally follows that of the
`src/` directory. There may be additional subdirectories to further
categorize tests by feature. This structure makes it easy to run a
collection of tests associated with a specific feature by specifying
the associated directory as the source of the tests to run.

    * Some (older) tests use the associated bug number in the directory
    name, the test name, or both.  That naming style should no longer be
    used, with existing tests using that style being candidates for migration.

### Naming

* The length of a name may be correlated to the size of its scope.  In
particular, short names (even single letter names) may be fine in a
small scope, but are usually inappropriate for larger scopes.

* Prefer whole words rather than abbreviations, unless the
abbreviation is more widely used than the long form in the code's
domain.

* Choose names consistently. Do not introduce spurious
variations. Abbreviate corresponding terms to a consistent length.

* Global names must be unique, to avoid [One Definition Rule][ODR] (ODR)
violations.  A common prefixing scheme for related global names is
often used.  (This is instead of using namespaces, which are mostly
avoided in HotSpot.)

* Don't give two names to the semantically same thing.  But use
different names for semantically different things, even if they are
representationally the same.  (So use meaningful `typedef` or template
alias names where appropriate.)

* When choosing names, avoid categorical nouns like "variable",
"field", "parameter", "value", and verbs like "compute", "get".
(`storeValue(int param)` is bad.)

* Type names and global names should use mixed-case with the first
letter of each word capitalized (`FooBar`).

* Embedded abbreviations in
otherwise mixed-case names are usually capitalized entirely rather
than being treated as a single word with only the initial letter
capitalized, e.g. "HTML" rather than "Html".

* Function and local variable names use lowercase with words separated
by a single underscore (`foo_bar`).

* Class data member names have a leading underscore, and use lowercase
with words separated by a single underscore (`_foo_bar`).

* Constant names may be upper-case or mixed-case, according to
historical necessity.  (Note: There are many examples of constants
with lowercase names.)

* Constant names should follow an existing pattern, and must have a
distinct appearance from other names in related APIs.

* Class and type names should be noun phrases. Consider an "er" suffix
for a class that represents an action.

* Function names should be verb phrases that reflect changes of state
known to a class's user, or else noun phrases if they cause no change
of state visible to the class's user.

* Getter accessor names are noun phrases, with no "`get_`" noise
word. Boolean getters can also begin with "`is_`" or "`has_`".  Member
function for reading data members usually have the same name as the
data member, exclusive of the leading underscore.

* Setter accessor names prepend "`set_`" to the getter name.

* Other member function names are verb phrases, as if commands to the receiver.

* Avoid leading underscores (as "`_oop`") except in cases required
above. (Names with leading underscores can cause portability
problems.)

### Commenting

* Clearly comment subtle fixes.

* Clearly comment tricky classes and functions.

* If you have to choose between commenting code and writing wiki
content, comment the code. Link from the wiki to the source file if
it makes sense.

* As a general rule don't add bug numbers to comments (they would soon
overwhelm the code). But if the bug report contains significant
information that can't reasonably be added as a comment, then refer to
the bug report.

* Personal names are discouraged in the source code, which is a team
product.

### Macros

* You can almost always use an inline function or class instead of a
macro. Use a macro only when you really need it.

* Templates may be preferable to multi-line macros. (There may be
subtle performance effects with templates on some platforms; revert
to macros if absolutely necessary.)

* `#ifdef`s should not be used to introduce platform-specific code
into shared code (except for `_LP64`). They must be used to manage
header files, in the pattern found at the top of every source
file. They should be used mainly for major build features, including
`PRODUCT`, `ASSERT`, `_LP64`, `INCLUDE_SERIALGC`, `COMPILER1`, etc.

* For build features such as `PRODUCT`, use `#ifdef PRODUCT` for
multiple-line inclusions or exclusions.

* For short inclusions or exclusions based on build features, use
macros like `PRODUCT_ONLY` and `NOT_PRODUCT`. But avoid using them
with multiple-line arguments, since debuggers do not handle that
well.

* Use `CATCH`, `THROW`, etc. for HotSpot-specific exception processing.

### Whitespace

* In general, don't change whitespace unless it improves readability
or consistency.  Gratuitous whitespace changes will make integrations
and backports more difficult.

* Use [One-True-Brace-Style](
https://en.wikipedia.org/wiki/Indentation_style#Variant:_1TBS_(OTBS)).
The opening brace for a function or class
is normally at the end of the line; it is sometimes moved to the
beginning of the next line for emphasis.  Substatements are enclosed
in braces, even if there is only a single statement.  Extremely simple
one-line statements may drop braces around a substatement.

* Indentation levels are two columns.

* There is no hard line length limit.  That said, bear in mind that
excessively long lines can cause difficulties.  Some people like to
have multiple side-by-side windows in their editors, and long lines
may force them to choose among unpleasant options. They can use wide
windows, reducing the number that can fit across the screen, and
wasting a lot of screen real estate because most lines are not that
long.  Alternatively, they can have more windows across the screen,
with long lines wrapping (or worse, requiring scrolling to see in
their entirety), which is harder to read.  Similar issues exist for
side-by-side code reviews.

* Tabs are not allowed in code. Set your editor accordingly.<br>
(Emacs: `(setq-default indent-tabs-mode nil)`.)

* Use good taste to break lines and align corresponding tokens on
adjacent lines.

* Use spaces around operators, especially comparisons and
assignments. (Relaxable for boolean expressions and high-precedence
operators in classic math-style formulas.)

* Put spaces on both sides of control flow keywords `if`, `else`,
`for`, `switch`, etc.  Don't add spaces around the associated
_control_ expressions.  Examples:

    ```
    while (test_foo(args...)) {   // Yes
    while(test_foo(args...)) {    // No, missing space after while
    while ( test_foo(args...) ) { // No, excess spaces around control
    ```

* Use extra parentheses in expressions whenever operator precedence
seems doubtful. Always use parentheses in shift/mask expressions
(`<<`, `&`, `|`).  Don't add whitespace immediately inside
parentheses.

* Use more spaces and blank lines between larger constructs, such as
classes or function definitions.

* If the surrounding code has any sort of vertical organization,
adjust new lines horizontally to be consistent with that
organization. (E.g., trailing backslashes on long macro definitions
often align.)

### Avoid implicit conversions to bool

* Use `bool` for boolean values.
* Do not use ints or pointers as (implicit) booleans with `&&`, `||`,
`if`, `while`. Instead, compare explicitly, i.e. `if (x != 0)` or
`if (ptr != nullptr)`, etc.
* Do not use non-boolean declarations in _condition_ forms, i.e. don't use
`if (T v = value) { ... }`. But see
[Enhanced selection statements](#enhanced-selection-statements).

### Miscellaneous

* Use the [Resource Acquisition Is Initialization][RAII] (RAII)
design pattern to manage bracketed critical
sections. See class `ResourceMark` for an example.

* Use functions from globalDefinitions.hpp and related files when
performing bitwise
operations on integers. Do not code directly as C operators, unless
they are extremely simple. (Examples: `align_up`, `is_power_of_2`,
`exact_log2`.)

* Use arrays with abstractions supporting range checks.

* Always enumerate all cases in a switch statement or provide a default
case. It is ok to have an empty default with comment.

## Use of C++ Features

HotSpot was originally written in a subset of the C++98/03 language.
More recently, support for C++17 is provided, though again,
HotSpot only uses a subset.  (Backports to JDK versions lacking
support for more recent Standards must of course stick with the
original C++98/03 subset.)

This section describes that subset.  Features from the C++98/03
language may be used unless explicitly forbidden here.  Features from
C++11, C++14, and C++17 may be explicitly permitted or explicitly forbidden,
and discussed accordingly here.  There is a third category, undecided
features, about which HotSpot developers have not yet reached a
consensus, or perhaps have not discussed at all.  Use of these
features is also forbidden.

(The use of some features may not be immediately obvious and may slip
in anyway, since the compiler will accept them.  The code review
process is the main defense against this.)

Some features are discussed in their own subsection, typically to provide
more extensive discussion or rationale for limitations.  Features that
don't have their own subsection are listed in omnibus feature sections
for permitted, forbidden, and undecided features.

Lists of new features for C++11, C++14, and C++17, along with links to their
descriptions, can be found in the online documentation for some of the
compilers and libraries.  The C++17 Standard is the definitive
description.

* [C++ Standards Support in GCC](https://gcc.gnu.org/projects/cxx-status.html)
* [C++ Support in Clang](https://clang.llvm.org/cxx_status.html)
* [Visual C++ Language Conformance](https://docs.microsoft.com/en-us/cpp/visual-cpp-language-conformance)
* [libstdc++ Status](https://gcc.gnu.org/onlinedocs/libstdc++/manual/status.html)
* [libc++ Status](https://libcxx.llvm.org/cxx1y_status.html)

As a rule of thumb, permitting features which simplify writing code
and, especially, reading code, is encouraged.

Similar discussions for some other projects:

* [Google C++ Style Guide](https://google.github.io/styleguide/cppguide.html) &mdash;
Currently (2020) targeting C++17.

* [C++11 and C++14 use in Chromium](https://chromium.googlesource.com/chromium/src/+/main/styleguide/c++/c++-features.md) &mdash;
Categorizes features as allowed, banned, or to be discussed.

* [llvm Coding Standards](https://llvm.org/docs/CodingStandards.html) &mdash;
Currently (2020) targeting C++14.

* [Using C++ in Mozilla code](https://firefox-source-docs.mozilla.org/code-quality/coding-style/using_cxx_in_firefox_code.html) &mdash;
C++17 support is required for recent versions (2020).

### Error Handling

Do not use exceptions. Exceptions are disabled by the build configuration
for some platforms.

Rationale: There is significant concern over the performance cost of
exceptions and their usage model and implications for maintainable code.
That's not just a matter of history that has been fixed; there remain
questions and problems even today (2019). See, for example, [Zero cost
deterministic
exceptions](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2018/p0709r0.pdf).
Because of this, HotSpot has always used a build configuration that disables
exceptions where that is available. As a result, HotSpot code uses error
handling mechanisms such as two-phase construction, factory functions,
returning error codes, and immediate termination. Even if the cost of
exceptions were not a concern, the existing body of code was not written with
exception safety in mind. Making HotSpot exception safe would be a very large
undertaking.

In addition to the usual alternatives to exceptions, HotSpot provides its
own exception mechanism. This is based on a set of macros defined in
utilities/exceptions.hpp.

### RTTI (Runtime Type Information)

Do not use [Runtime Type Information][RTTI] (RTTI).
[RTTI][] is disabled by the build configuration for some
platforms.  Among other things, this means `dynamic_cast` cannot be used.

Rationale: Other than to implement exceptions (which HotSpot doesn't use),
most potential uses of [RTTI][] are better done via virtual functions.  Some of
the remainder can be replaced by bespoke mechanisms.  The cost of the
additional runtime data structures needed to support [RTTI][] are deemed not
worthwhile, given the alternatives.

### Memory Allocation

Do not use the standard global allocation and deallocation functions (global
`operator new` and related functions), other than the non-allocating forms of
those functions.  Use of these functions by HotSpot code is disabled for some
platforms.

Rationale: HotSpot often uses "resource" or "arena" allocation.  Even
where heap allocation is used, the standard global functions are
avoided in favor of wrappers around `malloc` and `free` that support the
JVM's Native Memory Tracking (NMT) feature.  Typically, uses of the global
`operator new` are inadvertent and therefore often associated with memory
leaks.

Native memory allocation failures are often treated as non-recoverable.
The place where "out of memory" is (first) detected may be an innocent
bystander, unrelated to the actual culprit.

### Class Inheritance

Use public single inheritance.

Prefer composition rather than non-public inheritance.

Restrict inheritance to the "is-a" case; use composition rather than
non-is-a related inheritance.

Avoid multiple inheritance.  Never use virtual inheritance.

### Namespaces

Avoid using namespaces.  HotSpot code normally uses "all static"
classes rather than namespaces for grouping.  An "all static" class is
not instantiable, has only static members, and is normally derived
(possibly indirectly) from the helper class `AllStatic`.

Benefits of using such classes include:

* Provides access control for members, which is unavailable with
namespaces.

* Avoids [Argument Dependent Lookup][ADL] (ADL).

* Closed for additional members.  Namespaces allow names to be added in
multiple contexts, making it harder to see the complete API.

Namespaces should be used only in cases where one of those "benefits"
is actually a hindrance.

In particular, don't use anonymous namespaces.  They seem like they should
be useful, and indeed have some real benefits for naming and generated code
size on some platforms.  Unfortunately, debuggers don't seem to like them at
all.

<https://groups.google.com/forum/#!topic/mozilla.dev.platform/KsaG3lEEaRM><br>
Suggests Visual Studio debugger might not be able to refer to
anonymous namespace symbols, so can't set breakpoints in them.
Though the discussion seems to go back and forth on that.

<https://firefox-source-docs.mozilla.org/code-quality/coding-style/coding_style_cpp.html><br>
Search for "Anonymous namespaces"
Suggests preferring "static" to anonymous namespaces where applicable,
because of poor debugger support for anonymous namespaces.

<https://sourceware.org/bugzilla/show_bug.cgi?id=16874><br>
Bug for similar gdb problems.

### C++ Standard Library

Only curated parts of the C++ Standard Library may be used by HotSpot code.

Functions that may throw exceptions must not be used.  This is in accordance
with the HotSpot policy of [not using exceptions](#error-handling).

Also in accordance with HotSpot policy, the
[standard global allocator must not be used](#memory-allocation).  This means
that uses of standard container classes cannot presently be used, as doing so
requires specialization on some allocator type that is integrated with the
existing HotSpot allocation mechanisms. (Such allocators may be provided in
the future.)

Standard Library identifiers should usually be fully qualified; `using`
directives must not be used to bring Standard Library identifiers into scope
just to remove the need for namespace qualification.  Requiring qualification
makes it easy to distinguish between references to external libraries and code
that is part of HotSpot.

As with language features, Standard Library facilities are either permitted,
explicitly forbidden, or undecided (and so implicitly forbidden).

Most HotSpot code should not directly `#include` C++ Standard Library headers.
HotSpot provides a set of wrapper headers for the Standard Library headers
containing permitted definitions.  These wrappers are in the `cppstdlib`
directory, with the same name as the corresponding Standard Library header and
a `.hpp` extension.  These wrappers provide a place for any additional code
(some of which may be platform-specific) needed to support HotSpot usage.

These wrappers also provide a place to document HotSpot usage, including any
restrictions.  The set of wrappers and the usage documentation should be
considered part of HotSpot style.  Any changes are subject to the same process
as applies to this document. (For historical reasons, there may be many direct
inclusions of some C++ Standard Library headers.)

Historically, HotSpot has mostly avoided use of the Standard
Library.

(It used to be impossible to use most of it in shared code,
because the build configuration for Solaris with Solaris Studio made
all but a couple of pieces inaccessible.  Support for header-only
parts was added in mid-2017.  Support for Solaris was removed
in 2020.)

Some reasons for this include

* Exceptions. Perhaps the largest core issue with adopting the use of
Standard Library facilities is exceptions. HotSpot does not use
exceptions and, for platforms which allow doing so, builds with them
turned off.  Many Standard Library facilities implicitly or explicitly
use exceptions. On the other hand, many don't, and can be used without
concern for this issue. Others may have a useful subset that doesn't
use exceptions.

* `assert`.  An issue that is quickly encountered is the `assert` macro name
collision ([JDK-8007770](https://bugs.openjdk.org/browse/JDK-8007770)).
(Not all Standard Library implementations use `assert` in header files, but
some  do.) HotSpot provides a mechanism for addressing this, by establishing a
scope around the include of a library header where the HotSpot `assert` macro
is suppressed.  One of the reasons for using wrapper headers rather than
directly including standard headers is to provide a central place to deal with
this issue for each header.

* Memory allocation. HotSpot requires explicit control over where allocations
occur. The C++98/03 `std::allocator` class is too limited to support our
usage. But changes to the allocator concept in more recent Standards removed
some of the limitations, supporting stateful allocators. HotSpot may, in the
future, provide standard-conforming allocators that are integrated with
HotSpot's existing allocation mechanisms.

* Implementation vagaries. Bugs, or simply different implementation choices,
can lead to different behaviors among the various Standard Libraries we need
to deal with. But only selected parts of the Standard Library are being
permitted, and one of the selection criteria is maturity. Some of these
facilities are among the most heavily tested and used C++ codes that exist.

* Inconsistent naming conventions. HotSpot and the C++ Standard use different
naming conventions. The coexistence of those different conventions might
appear jarring and reduce readability. However, experience in some other code
bases suggests this isn't a significant problem, so long as Standard Library
names are namespace-qualified. It is tempting to bring the Standard Library
names into scope via a `using std;` directive. Doing so makes writing code
using those names easier, since the qualifiers don't need to be included. But
there are several reasons not to do that.

    * There is a risk of future name collisions. Additional Standard Library
    headers may be included, adding to the list of names being used. Also,
    future versions of the Standard Library may add currently unknown names to
    the headers already being included.

    * It may harm readability. Code where this is relevant is a mixture of the
    local HotSpot naming conventions and the Standard Library's (or other
    3rd-party library's) naming conventions. With only unqualified names, any
    distinctions from the naming conventions for the different code sources
    are lost. Instead one may end up with an undifferentiated mess, where it's
    not obvious whether an identifier is from local code that is inconsistent
    with HotSpot style (and there's a regretable amount of that for historical
    reasons), or is following some other convention. Having the qualifiers
    disambiguates that.

    * It can be helpful to know, at a glance, whether the definition is in
    HotSpot or elsewhere, for purposes of looking up the definition or
    documentation.

### Type Deduction

Use type deduction only if it makes the code clearer or safer.  Do not
use it merely to avoid the inconvenience of writing an explicit type,
unless that type is itself difficult to write.  An example of the
latter is a function template return type that depends on template
parameters in a non-trivial way.

There are several contexts where types are deduced.

* Function argument deduction.  This is always permitted, and indeed
encouraged.  It is nearly always better to allow the type of a
function template argument to be deduced rather than explicitly
specified.

* `auto` variable declarations
([n1984](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2006/n1984.pdf))<br>
For local variables, this can be used to make the code clearer by
eliminating type information that is obvious or irrelevant.  Excessive
use can make code much harder to understand.

* `auto` for non-type template parameters
([p0127r2](http://wg21.link/p0127r2))<br>
`auto` may be used as a placeholder for the type of a non-type template
parameter. The type is deduced from the value provided in a template
instantiation.

<a name="function-return-type-deduction"></a>
* Function return type deduction
([n3638](https://isocpp.org/files/papers/N3638.html))<br>
Only use if the function body has a very small number of `return`
statements, and generally relatively little other code.

* Class template argument deduction
([n3602](http://wg21.link/n3602), [p0091r3](http://wg21.link/p0091r3))<br>
The template arguments of a class template may be deduced from the arguments
to a constructor. This is similar to ordinary function argument deduction,
though partial deduction with only _some_ template arguments explicitly
provided is not permitted for class template argument deduction. Deduction
guides may be used to provide additional control over the deduction. As with
`auto` variable declarations, excessive use can make code harder to
understand, because explicit type information is lacking. But it can also
remove the need to be explicit about types that are either obvious, or that
are very hard to write. For example, these allow the addition of a scope-guard
mechanism with nice syntax; something like this
```
  ScopeGuard guard{[&]{ ... cleanup code ... }};
```

* Also see [lambda expressions](#lambdaexpressions).

* `decltype(auto)` should be avoided, whether for variables, for non-type
template parameters, or for function return types. There are subtle and
complex differences between this placeholder type and `auto`. Any use would
need very careful explanation.

### Expression SFINAE

[Substitution Failure Is Not An Error][SFINAE] (SFINAE)
is a template metaprogramming technique that makes use of
template parameter substitution failures to make compile-time decisions.

C++11 relaxed the rules for what constitutes a hard-error when
attempting to substitute template parameters with template arguments,
making most deduction errors be substitution errors; see
([n2634](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2008/n2634.html)).
This makes [SFINAE][] more powerful and easier to use.  However, the
implementation complexity for this change is significant, and this
seems to be a place where obscure corner-case bugs in various
compilers can be found.  So while this feature can (and indeed should)
be used (and would be difficult to avoid), caution should be used when
pushing to extremes.

Here are a few closely related example bugs:<br>
<https://gcc.gnu.org/bugzilla/show_bug.cgi?id=95468><br>
<https://developercommunity.visualstudio.com/content/problem/396562/sizeof-deduced-type-is-sometimes-not-a-constant-ex.html>

### Trailing return type syntax for functions

A function's return type may be specified after the parameters and qualifiers
([n2541](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2008/n2541.htm)).
In such a declaration the normal return type is `auto` and the return type is
indicated by `->` followed by the type.  Although both use `auto` in the
"normal" leading return type position, this differs from
[function return type deduction](#function-return-type-deduction),
in that the return type is explicit rather than deduced, but specified in a
trailing position.

Use of trailing return types is permitted.  However, the normal, leading
position for the return type is preferred. A trailing return type should only
be used where it provides some benefit. Such benefits usually arise because a
trailing return type is in a different scope than a leading return type.

* If the function identifier is a nested name specifier, then the trailing
return type occurs in the nested scope. This may permit simpler naming in the
return type because of the different name lookup context.

* The trailing return type is in the scope of the parameters, making their
types accessible via `decltype`. For example
```
template<typename T, typename U> auto add(T t, U u) -> decltype(t + u);
```
rather than
```
template<typename T, typename U> decltype((*(T*)0) + (*(U*)0)) add(T t, U u);
```

* Complex calculated leading return types may obscure the normal syntactic
boundaries, making it more difficult for a reader to find the function name and
parameters. This is particularly common in cases where the return type is
being used for [SFINAE]. A trailing return type may be preferable in such
situations.

### Non-type template parameter values

C++17 extended the arguments permitted for non-type template parameters
([n4268](http://wg21.link/n4268)). The kinds of values (the parameter types)
aren't changed.  However, the values can now be the result of arbitrary
constant expressions (with a few restrictions on the result), rather than a
much more limited and restrictive set of expressions. In particular, the
argument for a pointer or reference type parameter can now be the result of a
constexpr function.

### enum

Where appropriate, _scoped-enums_ should be used.
([n2347](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2007/n2347.pdf))

Use of _unscoped-enums_ is permitted, though ordinary constants may be
preferable when the automatic initializer feature isn't used.

The underlying type (the _enum-base_) of an unscoped enum type should
always be specified explicitly.  When unspecified, the underlying type
is dependent on the range of the enumerator values and the platform.

The underlying type of a _scoped-enum_ should also be specified
explicitly if conversions may be applied to values of that type.

Due to bugs in certain (very old) compilers, there is widespread use
of enums and avoidance of in-class initialization of static integral
constant members.  Compilers having such bugs are no longer supported.
Except where an enum is semantically appropriate, new code should use
integral constants.

### alignas

_Alignment-specifiers_ (`alignas`
[n2341](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2007/n2341.pdf))
are permitted, with restrictions.

_Alignment-specifiers_ are permitted when the requested alignment is a
_fundamental alignment_ (not greater than `alignof(std::max_align_t)`
[C++14 3.11/2](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2014/n4296.pdf)).

_Alignment-specifiers_ with an _extended alignment_ (greater than
`alignof(std::max_align_t)`
[C++14 3.11/3](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2014/n4296.pdf))
may only be used to align variables with static or automatic storage duration
([C++14 3.7.1, 3.7.3](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2014/n4296.pdf)).
As a consequence, _over-aligned types_ are forbidden; this may change if
HotSpot updates to using C++17 or later
([p0035r4](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2016/p0035r4.html)).

Large _extended alignments_ should be avoided, particularly for stack
allocated objects. What is a large value may depend on the platform and
configuration. There may also be hard limits for some platforms.

An _alignment-specifier_ must always be applied to a definition
([C++14 10.6.2/6](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2014/n4296.pdf)).
(C++ allows an _alignment-specifier_ to optionally also be applied to a
declaration, so long as the definition has equivalent alignment. There isn't
any known benefit from duplicating the alignment in a non-definition
declaration, so such duplication should be avoided in HotSpot code.)

Enumerations are forbidden from having _alignment-specifiers_. Aligned
enumerations were originally permitted but insufficiently specified, and were
later (C++20) removed
([CWG 2354](https://cplusplus.github.io/CWG/issues/2354.html)).
Permitting such usage in HotSpot now would just cause problems in the future.

_Alignment-specifiers_ are forbidden in `typedef` and _alias-declarations_.
This may work or may have worked in some versions of some compilers, but was
later (C++14) explicitly disallowed
([CWG 1437](https://cplusplus.github.io/CWG/issues/1437.html)).

The HotSpot macro `ATTRIBUTE_ALIGNED` provides similar capabilities for
platforms that define it. This macro predates the use by HotSpot of C++
versions providing `alignas`. New code should use `alignas`.

### thread_local

Avoid use of `thread_local`
([n2659](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2008/n2659.htm));
and instead, use the HotSpot macro `THREAD_LOCAL`, for which the initializer must
be a constant expression. When `thread_local` must be used, use the Hotspot macro
`APPROVED_CPP_THREAD_LOCAL` to indicate that the use has been given appropriate
consideration.

As was discussed in the review for
[JDK-8230877](https://mail.openjdk.org/pipermail/hotspot-dev/2019-September/039487.html),
`thread_local` allows dynamic initialization and destruction
semantics.  However, that support requires a run-time penalty for
references to non-function-local `thread_local` variables defined in a
different translation unit, even if they don't need dynamic
initialization.  Dynamic initialization and destruction of
non-local `thread_local` variables also has the same ordering
problems as for ordinary non-local variables. So we avoid use of
`thread_local` in general, limiting its use to only those cases where dynamic
initialization or destruction are essential. See
[JDK-8282469](https://bugs.openjdk.org/browse/JDK-8282469)
for further discussion.

### nullptr

Use `nullptr`
([n2431](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2007/n2431.pdf))
rather than `NULL`.  See the paper for reasons to avoid `NULL`.

Don't use (constant expression or literal) 0 for pointers.  Note that C++14
removed non-literal 0 constants from _null pointer constants_, though some
compilers continue to treat them as such.

### &lt;atomic&gt;

Do not use facilities provided by the `<atomic>` header
([n2427](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2007/n2427.html)),
([n2752](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2008/n2752.htm));
instead, use the HotSpot `Atomic` class and related facilities.

Atomic operations in HotSpot code must have semantics which are
consistent with those provided by the JDK's compilers for Java.  There
are platform-specific implementation choices that a C++ compiler might
make or change that are outside the scope of the C++ Standard, and
might differ from what the Java compilers implement.

In addition, HotSpot `Atomic` has a concept of "conservative" memory
ordering, which may differ from (may be stronger than) sequentially
consistent.  There are algorithms in HotSpot that are believed to rely
on that ordering.

### Variable Templates and Inline Variables

The use of variable templates (including static data member templates)
([N3651](https://wg21.link/N3651)) is permitted. They provide parameterized
variables and constants in a simple and direct form, instead of requiring the
use of various workarounds.

Variables with static storage duration and variable templates may be declared
`inline` ([p0386r2](https://wg21.link/p0386r2)), and this usage is
permitted. This has similar effects as for declaring a function inline: it can
be defined, identically, in multiple translation units, must be defined in
every translation unit in which it is [ODR used][ODR], and the behavior of the
program is as if there is exactly one variable.

Declaring a variable inline allows the complete definition to be in a header
file, rather than having a declaration in a header and the definition in a
.cpp file. The guidance on
[initialization](#initializing-variables-with-static-storage-duration) of such
variables still applies. Inline variables with dynamic initializations can
make initialization order problems worse. The few ordering constraints
that exist for non-inline variables don't apply, as there isn't a single
program-designated translation unit containing the definition.

A `constexpr` static data member or static data member template
is implicitly `inline`. As a consequence, an
[ODR use][ODR] of such a member doesn't require a definition in some .cpp
file. (This is a change from pre-C++17. Beginning with C++17, such a
definition is considered a duplicate definition, and is deprecated.)

Declaring a `thread_local` variable template or `inline` variable is forbidden
in HotSpot code.  [The use of `thread_local`](#thread_local) is already
heavily restricted.

### Initializing variables with static storage duration

Variables with static storage duration and _dynamic initialization_
[C++14 3.6.2](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2014/n4296.pdf)).
should be avoided, unless an implementation is permitted to perform the
initialization as a static initialization. The order in which dynamic
initializations occur is incompletely specified.  Initialization order
problems can be difficult to deal with and lead to surprises.

Variables with static storage duration and non-trivial destructors should be
avoided. HotSpot doesn't generally try to cleanup on exit, and running
destructors at exit can lead to problems.

Some of the approaches used in HotSpot to avoid dynamic initialization
include:

* Use the `DeferredStatic<T>` class template. Add a call to its initialization
function at an appropriate place during VM initialization. The underlying
object is never destroyed.

* For objects of class type, use a variable whose value is a pointer to the
class, initialized to `nullptr`. Provide an initialization function that sets
the variable to a dynamically allocated object. Add a call to that function at
an appropriate place during VM initialization. Such objects are usually never
destroyed.

### Uniform Initialization

The use of _uniform initialization_
([n2672](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2008/n2672.htm)),
also known as _brace initialization_, is permitted.

Some relevant sections from cppreference.com:

* [initialization](https://en.cppreference.com/w/cpp/language/initialization)
* [value initialization](https://en.cppreference.com/w/cpp/language/value_initialization)
* [direct initialization](https://en.cppreference.com/w/cpp/language/direct_initialization)
* [list initialization](https://en.cppreference.com/w/cpp/language/list_initialization)
* [aggregate initialization](https://en.cppreference.com/w/cpp/language/aggregate_initialization)

Although related, the use of `std::initializer_list` remains forbidden, as
part of the avoidance of the C++ Standard Library in HotSpot code.

### Mandatory Copy Elision

[Copy elision](https://en.wikipedia.org/wiki/Copy_elision)
(or [here](https://cn.cppreference.com/w/cpp/language/copy_elision.html))
is a compiler optimization used to avoid potentially expensive copies in
certain situations. It is critical to making practical the performance of
return by value or pass by value. It is also unusual in not following the
as-if rule for optimizations - copy elision can be applied even if doing so
bypasses side-effects of copying/moving the object. The C++ standard
explicitly permits this.

However, because it's an optional optimization, the relevant copy/move
constructor must be available and accessible, in case the compiler chooses to
not apply the optimization even in a situation where permitted.

C++17 changed some cases of copy elision so that there is never a copy/move in
these cases ([p0135r1](http://wg21.link/p0135r1)). The interesting cases
involve a function that returns an unnamed temporary object, and constructors.
In such cases the object being initialized from the temporary is always direct
initialized, with no copy/move ever involved; see [RVO] and more specifically
[URVO].

Since this is now standard behavior it can't be avoided in the covered
situations. This could change the behavior of code that relied on side effects
by constructors, but that's both uncommon and was already problematic because
of the previous optional copy elision. But HotSpot code can, and should,
explicitly take advantage of this newly required behavior where it makes sense
to do so.

For example, it may be beneficial to delay construction of the result of a
function until the return statement, rather than having a local variable that
is modified into the desired state and then returned. (Though [NRVO] may apply
in that case.)

It is also now possible to define a factory function for a class that is
neither movable nor copyable, if it can be written in a way that makes use of
this feature.

[RVO]: https://en.wikipedia.org/wiki/Copy_elision#RVO
  "Return Value Optimization"

[NRVO]: https://en.wikipedia.org/wiki/Copy_elision#NRVO
  "Named Return Value Optimization"

[URVO]: https://cn.cppreference.com/w/cpp/language/copy_elision.html
  "Unnamed Return Value Optimization"

### Local Function Objects

* Local function objects, including lambda expressions, may be used.
* Lambda expressions must only be used as a downward value.
* Prefer `[&]` as the capture list of a lambda expression.
* Return type deduction for lambda expressions is permitted, and indeed encouraged.
* An empty parameter list for a lambda expression may be elided.
* A lambda expression must not be `mutable`.
* Generic lambda expressions are permitted.
* Lambda expressions should be relatively simple.
* Anonymous lambda expressions should not overly clutter the enclosing expression.
* An anonymous lambda expression must not be directly invoked.
* Bind expressions are forbidden.

Single-use function objects can be defined locally within a function,
directly at the point of use.  This is an alternative to having a function
or function object class defined at class or namespace scope.

This usage was somewhat limited by C++03, which does not permit such a class
to be used as a template parameter.  That restriction was removed by C++11
([n2657]). Use of this feature is permitted.

Many HotSpot protocols involve "function-like" objects that involve some
named member function rather than a call operator.  For example, a function
that performs some action on all threads might be written as

```
void do_something() {
  struct DoSomething : public ThreadClosure {
    virtual void do_thread(Thread* t) {
      ... do something with t ...
    }
  } closure;
  Threads::threads_do(&closure);
}
```

HotSpot code has historically usually placed the DoSomething class at
namespace (or sometimes class) scope.  This separates the function's code
from its use, often to the detriment of readability.  It requires giving the
class a globally unique name (if at namespace scope).  It also loses the
information that the class is intended for use in exactly one place, and
does not have any subclasses.  (However, the latter can now be indicated by
declaring it `final`.)  Often, for simplicity, a local class will skip
things like access control and accessor functions, giving the enclosing
function direct access to the implementation and eliminating some
boilerplate that might be provided if the class is in some outer (more
accessible) scope.  On the other hand, if there is a lot of surrounding code
in the function body or the local class is of significant size, defining it
locally can increase clutter and reduce readability.

<a name="lambdaexpressions"></a>
C++11 added _lambda expressions_ as a new way to write a function object.
Simple lambda expressions can be significantly more concise than a function
object, eliminating a lot of boiler-plate.  On the other hand, a complex
lambda expression may not provide much, if any, readability benefit compared
to an ordinary function object.  Also, while a lambda can encapsulate a call
to a "function-like" object, it cannot be used in place of such.

A common use for local functions is as one-use [RAII] objects.  The amount
of boilerplate for a function object class (local or not) makes such usage
somewhat clumsy and verbose.  But with the help of a small amount of
supporting utility code, lambdas work particularly well for this use case.

Another use for local functions is [partial application][PARTIALAPP].  Again
here, lambdas are typically much simpler and less verbose than function
object classes.

A lambda is a constexpr function if either the parameter declaration clause is
followed by `constexpr`, or it satisfies the requirements for a constexpr
function ([p0170r1]). Thus, using a lambda to package up some computation
doesn't incur unnecessary overhead or prevent use in a context required to be
compile-time evaluated (such as an array size).

Because of these benefits, lambda expressions are permitted in HotSpot code,
with some restrictions and usage guidance.  An anonymous lambda is one which
is passed directly as an argument.  A named lambda is the value of a
variable, which is its name.

Lambda expressions should only be passed downward.  In particular, a lambda
should not be returned from a function or stored in a global variable,
whether directly or as the value of a member of some other object.  Lambda
capture is syntactically subtle (by design), and propagating a lambda in
such ways can easily pass references to captured values to places where they
are no longer valid.  In particular, members of the enclosing `this` object
are effectively captured by reference, even if the default capture is
by-value.  For such uses-cases a function object class should be used to
make the desired value capturing and propagation explicit.

Limiting the capture list to `[&]` (implicitly capture by reference) is a
simplifying restriction that still provides good support for HotSpot usage,
while reducing the cases a reader must recognize and understand.

* Many common lambda uses require reference capture.  Not permitting it
would substantially reduce the utility of lambdas.

* Referential transparency.  Implicit reference capture makes variable
references in the lambda body have the same meaning they would have in the
enclosing code.  There isn't a semantic barrier across which the meaning of
a variable changes.

* Explicit reference capture introduces significant clutter, especially when
lambda expressions are relatively small and simple, as they should be in
HotSpot code.

* There are a number of reasons why by-value capture might be used, but for
the most part they don't apply to HotSpot code, given other usage restrictions.

    * A primary use-case for by-value capture is to support escaping uses,
    where values captured by-reference might become invalid.  That use-case
    doesn't apply if only downward lambdas are used.

    * By-value capture can also make a lambda-local copy for mutation, which
    requires making the lambda `mutable`; see below.

    * By-value capture might be viewed as an optimization, avoiding any
    overhead for reference capture of cheap to copy values.  But the
    compiler can often eliminate any such overhead.

    * By-value capture by a non-`mutable` lambda makes the captured values
    const, preventing any modification by the lambda and making the captured
    value unaffected by modifications to the outer variable.  But this only
    applies to captured auto variables, not member variables, and is
    inconsistent with referential transparency.

* By-value capture of `this` (using a capture list like `[*this]` ([p0018r3]))
is also not permitted. One of the motivating use-cases is when the lifetime of
the lambda exceeds the lifetime of the object for the containing member
function. That is, we have an upward lambda that is capturing `this` of the
enclosing method. But again, that use-case doesn't apply if only downward
lambdas are used.
  Another use-case is when we simply want the lambda to be operating on a copy
of `this` for some reason. This is sufficiently uncommon that it can be
handled by manual copying, so readers don't need to understand this rare
syntax.

* Non-capturing lambdas (with an empty capture list - `[]`) have limited
utility.  There are cases where no captures are required (pure functions,
for example), but if the function is small and simple then that's obvious
anyway.

* Capture initializers (a C++14 feature - [N3649]) are not permitted.
Capture initializers inherently increase the complexity of the capture list,
and provide little benefit over an additional in-scope local variable.

* The use of `mutable` lambda expressions is forbidden because there don't
seem to be many, if any, good use-cases for them in HotSpot.  A lambda
expression needs to be mutable in order to modify a by-value captured value.
But with only downward lambdas, such usage seems likely to be rare and
complicated.  It is better to use a function object class in any such cases
that arise, rather than requiring all HotSpot developers to understand this
relatively obscure feature.

While it is possible to directly invoke an anonymous lambda expression, that
feature should not be used, as such a form can be confusing to readers.
Instead, name the lambda and call it by name.

Some reasons to prefer a named lambda instead of an anonymous lambda are

* The body contains non-trivial control flow or declarations or other nested
constructs.

* Its role in an argument list is hard to guess without examining the
function declaration.  Give it a name that indicates its purpose.

* It has an unusual capture list.

* It has a complex explicit return type or parameter types.

Lambda expressions, and particularly anonymous lambda expressions, should be
simple and compact.  One-liners are good.  Anonymous lambdas should usually
be limited to a couple lines of body code.  More complex lambdas should be
named.  A named lambda should not clutter the enclosing function and make it
long and complex; do continue to break up large functions via the use of
separate helper functions.

An anonymous lambda expression should either be a one-liner in a one-line
expression, or isolated in its own set of lines.  Don't place part of a
lambda expression on the same line as other arguments to a function.  The
body of a multi-line lambda argument should be indented from the start of
the capture list, as if that were the start of an ordinary function
definition.  The body of a multi-line named lambda should be indented one
step from the variable's indentation.

Some examples:

1. `foo([&] { ++counter; });`
2. `foo(x, [&] { ++counter; });`
3. `foo([&] { if (predicate) ++counter; });`
4. `foo([&] { auto tmp = process(x); tmp.f(); return tmp.g(); })`
5. Separate one-line lambda from other arguments:

    ```
    foo(c.begin(), c.end(),
        [&] (const X& x) { do_something(x); return x.value(); });
    ```
6. Indentation for multi-line lambda:

    ```
    c.do_entries([&] (const X& x) {
                   do_something(x, a);
                   do_something1(x, b);
                   do_something2(x, c);
                 });
    ```
7. Separate multi-line lambda from other arguments:

    ```
    foo(c.begin(), c.end(),
        [&] (const X& x) {
          do_something(x, a);
          do_something1(x, b);
          do_something2(x, c);
        });
    ```
8. Multi-line named lambda:

    ```
    auto do_entry = [&] (const X& x) {
      do_something(x, a);
      do_something1(x, b);
      do_something2(x, c);
    };
    ```

Item 4, and especially items 6 and 7, are pushing the simplicity limits for
anonymous lambdas.  Item 6 might be better written using a named lambda:
```
c.do_entries(do_entry);
```

Note that C++11 also added _bind expressions_ as a way to write a function
object for partial application, using `std::bind` and related facilities
from the Standard Library.  `std::bind` generalizes and replaces some of the
binders from C++03.  Bind expressions are not permitted in HotSpot code.
They don't provide enough benefit over lambdas or local function classes in
the cases where bind expressions are applicable to warrant the introduction
of yet another mechanism in this space into HotSpot code.

References:

* Local and unnamed types as template parameters ([n2657])
* New wording for C++0x lambdas ([n2927])
* Generalized lambda capture (init-capture) ([N3648])
* Generic (polymorphic) lambda expressions ([N3649])

[n2657]: http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2008/n2657.htm
[n2927]: http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2009/n2927.pdf
[N3648]: https://isocpp.org/files/papers/N3648.html
[N3649]: https://isocpp.org/files/papers/N3649.html

References from C++17

* Wording for constexpr lambda ([p0170r1])
* Lambda capture of *this by Value ([p0018r3])

[p0170r1]: http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2016/p0170r1.pdf
[p0018r3]: http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2016/p0018r3.html

References from C++20

* Allow lambda capture [=, this] ([p0409r2])
* Familiar template syntax for generic lambdas ([p0428r2])
* Simplifying implicit lambda capture ([p0588r1])
* Default constructible and assignable stateless lambdas ([p0624r2])
* Lambdas in unevaluated contexts ([p0315r4])
* Allow pack expansion in lambda init-capture ([p0780r2]) ([p2095r0])
* Deprecate implicit capture of this via [=] ([p0806r2])

[p0409r2]: http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2017/p0409r2.html
[p0428r2]: http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2017/p0428r2.pdf
[p0588r1]: http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2017/p0588r1.html
[p0624r2]: http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2017/p0624r2.pdf
[p0315r4]: http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2017/p0315r4.pdf
[p0780r2]: http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2018/p0780r2.html
[p2095r0]: http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2020/p2095r0.html
[p0806r2]: http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2018/p0806r2.html

References from C++23

* Make () more optional for lambdas  ([p1102r2])

[p1102r2]: http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2020/p1102r2.html

### Inheriting constructors

Do not use _inheriting constructors_
([n2540](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2008/n2540.htm)).

C++11 provides simple syntax allowing a class to inherit the constructors of a
base class.  Unfortunately there are a number of problems with the original
specification, and C++17 contains significant revisions ([p0136r1] opens with
a list of 8 Core Issues). Although those issues have been addressed, the
benefits from this feature are small compared to the complexity. Because of
this, HotSpot code must not use inherited constructors.

[p0136r1]: http:/wg21.link/p0136r1 "p0136r1"
[p0195r0](http://wg21.link/p0195r0)

### Attributes

The use of some attributes
([n2761](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2008/n2761.pdf))
(listed below) is permitted.  (Note that some of the attributes defined in
that paper didn't make it into the final specification.)

Attributes are syntactically permitted in a broad set of locations, but
specific attributes are only permitted in a subset of those locations.  In
some cases an attribute that appertains to a given element may be placed in
any of several locations with the same meaning.  In those cases HotSpot has a
preferred location.

* An attribute that appertains to a function is placed at the beginning of the
function's declaration, rather than between the function name and the parameter
list.

[p0068r0](http://wg21.link/p0068r0) is the initial proposal for the attributes
added by C++17.)

Only the following attributes are permitted:

* `[[noreturn]]`
* `[[nodiscard]]` ([p0189r1](http://wg21.link/p0189r1))
* `[[maybe_unused]]` ([p0212r1](http://wg21.link/p0212r1))
* `[[fallthrough]]` ([p0188r1](http://wg21.link/p0188))

The following attributes are expressly forbidden:

* `[[carries_dependency]]` - Related to `memory_order_consume`.
* `[[deprecated]]` - Not relevant in HotSpot code.

Direct use of non-standard (and presumably scoped) attributes in shared code
is also forbidden. Using such would depend on the C++17 feature that an
attribute not recognized by the implementation is ignored
([p0283r2](http://wg21.link/p0283r2)). If such an attribute is needed in
shared code, the well-established technique of providing an `ATTRIBUTE_XXX`
macro with per-compiler definitions (sometimes empty) should be
used. Compilers may warn about unrecognized attributes (whether by name or by
location), in order to report typos or misuse. Disabling such warnings
globally would not be desirable.

The use of `using` directives in attribute lists is also forbidden.
([p0028r0](http://wg21.link/p0028r0))
([p0028r4](http://wg21.link/p0028r4))
We don't generally use scoped attributes in attribute lists with other
attributes. Rather, uses of scoped attributes (which are implementation
defined) are generally hidden behind a portability macro that includes the
surrounding brackets.

### noexcept

Use of `noexcept` exception specifications
([n3050](http://wg21.link/n3050))
are permitted with restrictions described below.

* Only the argument-less form of `noexcept` exception specifications are
permitted.
* Allocation functions that may return `nullptr` to indicate allocation
failure must be declared `noexcept`.
* All other uses of `noexcept` exception specifications are forbidden.
* `noexcept` expressions are forbidden.
* Dynamic exception specifications are forbidden.

HotSpot is built with exceptions disabled, e.g. compile with `-fno-exceptions`
(gcc, clang) or no `/EH` option (MSVC++). So why do we need to consider
`noexcept` at all? It's because `noexcept` exception specifications serve two
distinct purposes.

The first is to allow the compiler to avoid generating code or data in support
of exceptions being thrown by a function. But this is unnecessary, because
exceptions are disabled.

The second is to allow the compiler and library code to choose different
algorithms, depending on whether some function may throw exceptions. This is
only relevant to a certain set of functions.

* Some allocation functions (`operator new` and `operator new[]`) return
`nullptr` to indicate allocation failure. If a `new` expression calls such an
allocation function, it must check for and handle that possibility. Declaring
such a function `noexcept` informs the compiler that `nullptr` is a possible
result. If an allocation function is not declared `noexcept` then the compiler
may elide that checking and handling for a `new` expression calling that
function.

* Certain Standard Library facilities (notably containers) provide different
guarantees for some operations (and may choose different algorithms to
implement those operations), depending on whether certain functions
(constructors, copy/move operations, swap) are nothrow or not. They detect
this using type traits that test whether a function is declared `noexcept`.
This can have a significant performance impact if, for example, copying is
chosen over a potentially throwing move. But this isn't relevant, since
HotSpot forbids the use of most Standard Library facilities.

HotSpot code can assume no exceptions will ever be thrown, even from functions
not declared `noexcept`. So HotSpot code doesn't ever need to check, either
with conditional exception specifications or with `noexcept` expressions.

The exception specification is part of the type of a function
([p0012r1](http://wg21.link/p0012r1). This likely has little impact on HotSpot
code, since the use of `noexcept` is expected to be rare.

Dynamic exception specifications were deprecated in C++11. C++17 removed all
but `throw()`, with that remaining a deprecated equivalent to `noexcept`.

### Enhanced selection statements

C++17 modified the _condition_ part of `if` and `switch` statements, permitting
an _init-statement_ to be included
([p0305r1](http://wg21.link/p0305r1)).

Use of this feature is permitted. (However, complex uses may interfere with
readability.) Limiting the scope of a variable involved in the condition,
while also making the value available to the statement's body, can improve
readability. The alternative method of scope-limiting by introducing a nested
scope isn't very popular and is rarely used.

This new syntax is in addition to the _condition_ being a declaration with a
_brace-or-equal-initializer_. For an `if` statement this new sytax gains that
benefit without violating the long-standing guidance against using
[implicit conversions to `bool`](#avoid-implicit-conversions-to-bool),
which still stands.

For example, uses of Unified Logging sometimes explicitly check whether a
`LogTarget` is enabled.  Instead of
```
  LogTarget(...) lt;
  if (lt.is_enabled()) {
    LogStream log(lt);
    ... use log ...
  }
  ... lt is accessible but probably not needed here ...
```
using this feature one could write
```
  if (LogTarget(...) lt; lt.is_enabled()) {
    LogStream log(lt);
    ... use log ...
  }
```

C++17 also added compile-time `if` statements
([p0292r2](http://wg21.link/p0292r2)). Use of `if constexpr` is
permitted. This feature can replace and (sometimes vastly) simplify many uses
of [SFINAE]. The same declaration and initialization guidance for the
_condition_ part apply here as for ordinary `if` statements.

### Expression Evaluation Order

C++17 tightened up the evaluation order for some kinds of subexpressions
([p0138r2](http://wg21.link/p0138r2)). Note, however, that the Alternate
Evaluation Order for Function Calls alternative in that paper was adopted,
rather than the strict left to right order of evaluation for function call
arguments that was proposed in the main body of the paper.

The primary purpose of this change seems to be to make certain kinds of call
chaining well defined. That's not a style widely used in HotSpot. In general
it is better to continue to avoid questions in this area by isolating
operations with side effects from other statements. In particular, continue to
avoid modifying a value in an expression where it is also used.

### Compatibility with C11

C++17 refers to C11 rather than C99. This means that C11 libraries and
functions may be used in HotSpot. There may be limitations because of
differing levels of compatibility among various compilers and versions of
those compilers.

Note that the C parts of the JDK have been built with C11 selected for some
time ([JDK-8292008](https://bugs.openjdk.org/browse/JDK-8292008)).

### Additional Permitted Features

* `alignof`
([n2341](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2007/n2341.pdf))

* `constexpr`
([n2235](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2007/n2235.pdf))
([n3652](https://isocpp.org/files/papers/N3652.html))

* Sized deallocation
([n3778](https://isocpp.org/files/papers/n3778.html))

* Variadic templates
([n2242](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2007/n2242.pdf))
([n2555](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2008/n2555.pdf))

* Static assertions
([n1720](http://wg21.link/n1720))
([n3928](http://wg21.link/n3928))<br>
Both the original (C++11) two-argument form and the new (C++17)
single-argument form are permitted.

* `decltype`
([n2343](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2007/n2343.pdf))
([n3276](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2011/n3276.pdf))

* Right angle brackets
([n1757](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2005/n1757.html))

* Default template arguments for function templates
([CWG D226](http://www.open-std.org/jtc1/sc22/wg21/docs/cwg_defects.html#226))

* Template aliases
([n2258](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2007/n2258.pdf))

* Delegating constructors
([n1986](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2006/n1986.pdf))

* Explicit conversion operators
([n2437](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2007/n2437.pdf))

* Standard Layout Types
([n2342](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2007/n2342.htm))

* Defaulted and deleted functions
([n2346](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2007/n2346.htm))

* Dynamic initialization and destruction with concurrency
([n2660](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2008/n2660.htm))

* `final` virtual specifiers for classes and virtual functions
([n2928](http://www.open-std.org/JTC1/SC22/WG21/docs/papers/2009/n2928.htm)),
([n3206](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2010/n3206.htm)),
([n3272](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2011/n3272.htm))

* `override` virtual specifiers for virtual functions
([n2928](http://www.open-std.org/JTC1/SC22/WG21/docs/papers/2009/n2928.htm)),
([n3206](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2010/n3206.htm)),
([n3272](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2011/n3272.htm))

* Range-based `for` loops
([n2930](http://www.open-std.org/JTC1/SC22/WG21/docs/papers/2009/n2930.html))
([range-for](https://en.cppreference.com/w/cpp/language/range-for))

* Unrestricted Unions
([n2544](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2008/n2544.pdf))

* Preprocessor Condition `__has_include`
([p0061r0](http://wg21.link/p0061r0))
([p0061r1](http://wg21.link/p0061r1))

* Hexadecimal Floating-Point Literals
([p0245r1](http://wg21.link/p0245r1))

* Construction Rules for `enum class` Values
([p0138r2](http://wg21.link/p0138r2))

* Allow `typename` in template template parameter
([n4051](http://wg21.link/n4051)) &mdash; template template parameters are
barely used (if at all) in HotSpot, but there's no reason to artificially
forbid this syntactic regularization in any such uses.

## Forbidden Features

### Structured Bindings

The use of structured bindings [p0217r3](http://wg21.link/p0217r3) is
forbidden.  Preferred approaches for handling functions with multiple return
values include

* Return a named class/struct intended for that purpose, with named and typed
members/accessors.

* Return a value along with out parameters (usually pointers, sometimes
references).

* Designate a sentinel "failure" value in the normal return value type, with
some out of band location for additional information.  For example, this is
the model typically used with `errno`, where a function returns a normal
result, or -1 to indicate an error, with additional error information in
`errno`.

There is a strong preference for names and explicit types, as opposed to
offsets and implicit types. For example, there are folks who strongly dislike
that some of the Standard Library functions return `std::pair` because `first`
and `second` members don't carry any useful information.

### File System Library

The use of the File System library is forbidden. HotSpot doesn't do very much
with files, and already has adequate mechanisms for its needs. Rewriting in
terms of this new library doesn't provide any obviously significant
benefits. Having a mix of the existing usage and uses of this new library
would be confusing.

[n4100](http://wg21.link/n4100)
[p0218r0](http://wg21.link/p0218r0)
[p0219r1](http://wg21.link/p0219r1)
[p0317r1](http://wg21.link/p0317r1)
[p0392r0](http://wg21.link/p0392r0)
[p0430r2](http://wg21.link/p0430r2)
[p0492r2](http://wg21.link/p0492r2)
[p1164r1](http://wg21.link/p1164r1)

### Aggregate Extensions

Aggregates with base classes are forbidden. C++17 allows aggregate
initialization for classes with base classes
([p0017r1](https://wg21.link/p0017r1)). HotSpot makes very little use of
aggregate classes, preferring explicit constructors even for very simple
classes.

### Additional Forbidden Features

* `<algorithm>`, `<iterator>`, `<numeric>`<br>
Not useful without standard containers or similar classes in HotSpot.

* `<bitset>` - Overlap with HotSpot `BitMap`.

* `<cassert>`, `assert.h` - HotSpot has its own `assert` macro.

* `<exception>`, `<stdexcept>` - Use of [exceptions](#error-handling) is not
permitted.

* Thread support - `<thread>`, `<mutex>`, `<shared_mutex>`,
`<condition_varible>`, `<future>`<br>
HotSpot has its own threading support.

* Streams - HotSpot doesn't use the C++ I/O library.

* `<scoped_allocator>` - Not useful without specialized allocators.

* `<string>` - Requires allocator support, similar to standard containers.

* `<typeinfo>`, `<typeindex>`<br>
Use of [runtime type information](#runtime-type-information) is not permitted.

* `<valarray>` - May allocate, but is not allocator-aware.

* New string and character literals
    * New character types
    ([n2249](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2007/n2249.html))
    * Unicode string literals
    ([n2442](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2007/n2442.htm))
    * Raw string literals
    ([n2442](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2007/n2442.htm))
    * Universal character name literals
    ([n2170](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2007/n2170.html))

    HotSpot doesn't need any of the new character and string literal
    types.

* User-defined literals
([n2765](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2008/n2765.pdf)) &mdash;
User-defined literals should not be added casually, but only
through a proposal to add a specific UDL.

* Inline namespaces
([n2535](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2008/n2535.htm)) &mdash;
HotSpot makes very limited use of namespaces.

* `using namespace` directives.  In particular, don't use `using
namespace std;` to avoid needing to qualify Standard Library names.

* Propagating exceptions
([n2179](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2007/n2179.html)) &mdash;
HotSpot does not permit the use of exceptions, so this feature isn't useful.

* Avoid most operator overloading, preferring named functions.  When
operator overloading is used, ensure the semantics conform to the
normal expected behavior of the operation.

* Avoid most implicit conversion constructors and (implicit or explicit)
conversion operators.  Conversion to `bool` operators aren't needed
because of the
[no implicit boolean](#avoid-implicit-conversions-to-bool)
guideline.)

* Avoid `goto` statements.

* Attributes for namespaces and enumerators
([n4266](http://wg21.link/n4266) &mdash;
The only applicable attribute is [`[[deprecated]]`](#attributes), which is
forbidden.

* Variadic `using` declarations
([p0195r2](http://wg21.link/p0195r2))

* `std::variant<>`
([p0088r3](http://wg21.link/p0088r3)) &mdash;
Even if more of the C++ Standard Library is permitted, this class will remain
forbidded. Invalid accesses are indicated by throwing exceptions.

* `std::any`
([p0220r1](http://wg21.link/p0220r1)) &mdash;
Even if more of the C++ Standard Library is permitted, this class will remain
forbidden. It may require allocation, and always uses the standard
allocator. It requires [RTTI].

* `std::as_const()`
([p0007r1](http://wg21.link/p0007r1)) &mdash;
If sufficiently useful, HotSpot could add such a function. It would likely be
added to globalDefinitions.hpp, where there are already some similar small
utilities.

* `std::clamp()`
([p002501](http://wg21.link/p002501)) &mdash;
This function is already provided in globalDefinitions.hpp.

* Parallel STL Algorithms
([p0024r2](http://wg21.link/p0024r2)) &mdash;
Even if more of the C++ Standard Library is permitted, these will remain
forbidden. They are built on the standard C++ threading mechanisms. HotSpot
doesn't use those mechanisms, instead providing and using its own.

* Cache Line Sizes
[p0154r1](http://wg21.link/p0154r1) &mdash;
HotSpot has its own mechanisms for this, using values like
`DEFAULT_CACHE_LINE_SIZE`. The platform-specific implementation of the HotSpot
mechanisms might use these library functions, but there is no reason to move
away from the current approach. Quoting from [JOSUTTIS]: "... if you know better,
use specific values, but using these values is better than any assumed fixed
size for code supporting multiple platforms."

* `register` storage class removal
[p0001r1](http://wg21.link/p0001r1) &mdash;
The `register` storage class has been removed. `register` is still a keyword,
so still can't be used for normal purposes. Also, this doesn't affect the use
of `register` for gcc-style extended asm code; that's a different syntactic
element with a different meaning.

* Value of `__cplusplus` &mdash;
Testing whether `__cplusplus` is defined or not is permitted, and indeed
required. But the value should not need to be examined. The value is changed
with each revision of the Standard. But we build HotSpot and (most of) the
rest of the JDK with a specifically selected version of the Standard. The
value of `__cplusplus` should be known and unchanging until we change the
project's build configuration again. So examining the value shouldn't ever be
necessary.

* Removal of `++` for `bool`
([p0003r1](http://wg21.link/p0003r1))

* Removal of trigraphs
([n4086](http://wg21.link/n4086))

## Undecided Features

This list is incomplete; it serves to explicitly call out some
features that have not yet been discussed.

Some features are undecided (so implicitly forbidden) because we don't expect
to use them at all. This might be reconsidered if someone finds a good use
case.

Some Standard Library features are undecided (so implicitly forbidden)
because, while this Style Guide forbids the use of such, they may be
sufficiently useful that we want to permit them anyway. Doing so may require
some idiomatic mechanism for addressing things like `assert` incompatibility,
incompatibility with HotSpot's `FORBID_C_FUNCTION` mechanism, and the like.

### std::optional<>

It is undecided whether to permit the use of `std::optional<>`
([p0220r1](http://wg21.link/p0220r1)). It may be sufficiently useful that it
should be permitted despite the usual prohibition against using Standard
Library facilities. Use of the `value()` member function must be forbidden, as
it reports an invalid access by throwing an exception.

### std::byte

It is undecided whether to permit the use of the `std::byte` type
([p0298r3](http://wg21.link/p0298r3)). It may be sufficiently useful that it
should be permitted despite the usual prohibition against using Standard
Library facilities.

It has been suggested that changing the HotSpot `address` type to use
`std::byte` has some benefits. That is, replace
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

### String Views

It is undecided whether to permit the use of `std::string_view`
([p0220r1](http://wg21.link/p0220r1)).

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

### Substring and Subsequence Searching

In addition to simple substring searching, the Standard Library now includes
Boyer-Moore and Boyer-Moore-Horspool searchers, in case someone wants to
search really large texts. That seems an unlikely use-case for HotSpot.  See
[p0220r1](http://wg21.link/p0220r1).

### `new` and `delete` with Over-Aligned Data

It is undecided whether to permit the use of dynamic allocation of overaligned
types ([n3396](http://wg21.link/n3396)).

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

### `std::to_chars()` and `std::from_chars`

It is undecided whether to permit the use of `std::to_chars()` and
`std::from_chars()` ([p0067r5](http://wg21.link/p0067r5)).

These functions provide low-level conversions between character sequences and
numeric values. This seems like a good candidate for use in HotSpot,
potentially replacing various clumsy or less performant alternatives. There is
no memory allocation. Parsing failures are indicated via error codes rather
than exceptions. Various other nice for HotSpot properties.

Note that the published C++17 Standard puts these in `<utility>`, but a defect
report moved them to `<charconv>`. This also needs `<system_error>`.

This would require upgrading the minimum gcc version to 11.1 for floating
point conversion support. The minimum Visual Studio version is already
sufficient.  The minimum clang version requirement hasn't been determined yet.

### `std::launder()`

It is undecided whether to permit the use of `std::launder()`
([p0137r1](http://wg21.link/p0137r1)).

Change to permitted if we discover a place where we need it. Or maybe we
should just permit it, but hope we don't need it.

Also, C++20 revised the relevant part of Object Lifetime in a way that seems
more permissive and with less need of laundering. We don't know if
implementations of prior versions take advantage of the difference.

See Object Lifetime: C++17 6.8/8, C++20 6.7.3/8

### Additional Undecided Features

* Member initializers and aggregates
([n3653](http://www.open-std.org/jtc1/sc22/wg21/docs/papers/2013/n3653.html))

* Rvalue references and move semantics

* Shorthand for nested namespaces
([n4230](http://wg21.link/n4230)) &mdash;
HotSpot makes very little use of namespaces, so this seemingly innocuous
feature probably isn't useful to us.

* Direct list initialization with `auto`
([n3681](http://wg21.link/n3681)) &mdash;
This change fixed some issues with direct list initialization and `auto`. But
we don't use that feature much, if at all. And perhaps shouldn't be using it.

* UTF-8 Character Literals
([n4267](http://wg21.link/n4267)) &mdash;
Do we have a use-case for this?

* Fold Expressions
([n4295](http://wg21.link/n4295)) &mdash;
Provides a simple way to apply operators to a parameter pack. HotSpot doesn't
use variadic templates very much. That makes it questionable that developers
should need to know about this feature.  But if someone does come up with a
good use-case, it's likely that the alternatives are significantly worse,
because pack manipulation without this can be complicated.

* [`<tuple>`](https://en.cppreference.com/w/cpp/header/tuple.html) &mdash;
Prefer named access to class objects, rather than indexed access
to anonymous heterogeneous sequences.  In particular, a standard-layout
class is preferred to a tuple.

* `std::invoke<>()`
([n4169](http://wg21.link/n4169))

* [`<chrono>`](https://en.cppreference.com/w/cpp/header/chrono.html) &mdash;
The argument for chrono is that our existing APIs aren't serving us well.
chrono provides strong type safety. We've had multiple cases of mistakes like
a double seconds being treated as double milliseconds or vice versa, and other
similar errors. But it would be a large effort to adopt chrono. We'd also need
to decide whether to use the predefined clocks or hook up chrono to our
clocks. It may be that using the predefined clocks is fine, but it's a
question that needs careful study.

* [`<initializer_list>`](https://en.cppreference.com/w/cpp/header/initializer_list.html) &mdash;
The potential ambiguity between some forms of direct initialization and
initializer list initialization, and the resolution of that ambiguity, is
unfortunate.

* [`<ratio>`](https://en.cppreference.com/w/cpp/header/ratio.html) &mdash;
`<ratio>` is a *compile-time* rational arithmetic package. It's also fixed
(though parameterized) precision. It's not a general purpose rational
arithmetic facility. It appears to have started out as an implementation
detail of chrono, and was extracted and promoted to a public facility in the
belief that it has broader utility.

* [`<system_error>`](https://en.cppreference.com/w/cpp/header/system_error.html) &mdash;
We don't really have a generally agreed upon mechanism for managing
errors. Instead, we have a plethora of bespoke ad hoc mechanisms. Managing
errors is a topic of substantial discussion. `<system_error>` might end up
being a part of a result from that discussion.


[ADL]: https://en.cppreference.com/w/cpp/language/adl
  "Argument Dependent Lookup"

[ODR]: https://en.cppreference.com/w/cpp/language/definition
  "One Definition Rule"

[RAII]: https://en.cppreference.com/w/cpp/language/raii
  "Resource Acquisition Is Initialization"

[RTTI]: https://en.wikipedia.org/wiki/Run-time_type_information
  "Runtime Type Information"

[SFINAE]: https://en.cppreference.com/w/cpp/language/sfinae
  "Substitution Failure Is Not An Error"

[PARTIALAPP]: https://en.wikipedia.org/wiki/Partial_application
  "Partial Application"

[JOSUTTIS]: https://www.cppstd17.com
  "C++17: The Complete Guide"
