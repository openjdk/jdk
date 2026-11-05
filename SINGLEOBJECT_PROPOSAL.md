# OpenJDK `javac` Proposal & Prototype: Contextual Keyword `singleobject`

## 1. Overview & Motivation
Creating a thread-safe Singleton class in Java that supports class inheritance (`extends`) and interface implementation (`implements`) currently requires repetitive boilerplate:
- Declaring the class `final`
- Declaring a `private` constructor
- Creating a `private static final` instance field
- Creating a `public static getInstance()` accessor method

While `enum` singletons (`enum Foo { INSTANCE; }`) avoid this boilerplate, **`enum` classes implicitly extend `java.lang.Enum` and therefore cannot extend a superclass (`extends BaseClass`)**.

This prototype extends the OpenJDK `javac` compiler (`jdk.compiler` module) with a new **contextual modifier keyword** `singleobject`:

```java
public singleobject class Demo extends BaseService implements Runnable {
    public void connect(String url) { ... }
}
```

At compile time, `javac` validates and desugars `Demo` into:
```java
public final class Demo extends BaseService implements Runnable {
    private static final Demo $INSTANCE = new Demo();
    private Demo() { super(); }
    public static final Demo getInstance() { return $INSTANCE; }
    public void connect(String url) { ... }
}
```

---

## 2. Modified OpenJDK (`jdk.compiler`) Files
1. `src/jdk.compiler/share/classes/com/sun/tools/javac/util/Names.java`
   - Registered `singleobject`, `getInstance`, and `$INSTANCE` in the compiler `Name.Table`.
2. `src/jdk.compiler/share/classes/com/sun/tools/javac/code/Flags.java`
   - Added `SINGLEOBJECT = 1L << 21` flag and included it in `ExtendedStandardFlags`, `ExtendedClassFlags`, `ExtendedMemberClassFlags`, `ExtendedMemberStaticClassFlags`, `ModifierFlags`, and `Flags.Flag`.
3. `src/jdk.compiler/share/classes/com/sun/tools/javac/parser/JavacParser.java`
   - Added contextual modifier lookahead `isSingleObjectClassStart(boolean local)` in `modifiersOpt()` and registered `singleobject` as a restricted type name in `restrictedTypeNameStartingAtSource()`.
4. `src/jdk.compiler/share/classes/com/sun/tools/javac/comp/Check.java`
   - Made `SINGLEOBJECT` classes implicitly `FINAL` (and `STATIC` when nested).
   - Enforced `PRIVATE` access on constructors of `SINGLEOBJECT` classes.
   - Disallowed illegal modifier combinations (`abstract`, `interface`, `sealed`, `non-sealed`, `@interface`).
5. `src/jdk.compiler/share/classes/com/sun/tools/javac/comp/TypeEnter.java`
   - Updated `BasicConstructorHelper.constructorSymbol()` to synthesize a `PRIVATE` default constructor for `SINGLEOBJECT` classes.
   - Added `addSingleObjectMembers(JCClassDecl)` in `MembersPhase.finishClass()` to synthesize `$INSTANCE` and `getInstance()`.
6. `test/langtools/tools/javac/singleobject/SingleObjectTest.java` & `SingleObjectNegTest.java`
   - Positive and negative `jtreg` compiler tests.

---

## 3. How to Build and Run (`JDK 17`)

### Step 1: Compile the Modified `javac` Classes
```bash
rm -rf build/javac-patch
javac --patch-module jdk.compiler=src/jdk.compiler/share/classes \
      -d build/javac-patch \
      src/jdk.compiler/share/classes/com/sun/tools/javac/util/Names.java \
      src/jdk.compiler/share/classes/com/sun/tools/javac/code/Flags.java \
      src/jdk.compiler/share/classes/com/sun/tools/javac/parser/JavacParser.java \
      src/jdk.compiler/share/classes/com/sun/tools/javac/comp/Check.java \
      src/jdk.compiler/share/classes/com/sun/tools/javac/comp/TypeEnter.java
rm -f build/javac-patch/module-info.class
```

### Step 2: Compile & Run `examples/Demo.java` and `SingleObjectTest.java`
```bash
mkdir -p build/demo-out
java --patch-module jdk.compiler=build/javac-patch \
     -m jdk.compiler/com.sun.tools.javac.Main \
     -d build/demo-out \
     examples/Demo.java \
     test/langtools/tools/javac/singleobject/SingleObjectTest.java

java -cp build/demo-out Demo
java -cp build/demo-out SingleObjectTest
javap -p build/demo-out/Demo.class
```

---

## 4. Official OpenJDK Submission Templates

### A. Template for [bugreport.java.com](https://bugreport.java.com/bugreport/) (To get a `JDK-83xxxxx` Issue ID)
- **Type**: Enhancement (RFE)
- **Component**: Core Libraries / Tools (`javac`)
- **Synopsis**: `Add contextual class modifier 'singleobject' for compiler-synthesized singletons`
- **Description**:
```text
MOTIVATION:
While Java enums (`enum Foo { INSTANCE; }`) provide a concise way to declare singletons, they implicitly extend `java.lang.Enum` and therefore cannot extend a superclass (`extends BaseClass`). Developers needing a singleton class that extends an existing base class must still write repetitive boilerplate (final class modifier, private constructor, static final instance holder field, and public static getInstance() method).

PROPOSAL & WORKING PROTOTYPE:
Introduce a contextual class modifier `singleobject` (e.g., `public singleobject class AppConfig extends BaseService`).
When `javac` encounters a `singleobject` class declaration:
1. The class is implicitly `final` (and `static` if a member class), and cannot be combined with `abstract`, `interface`, `sealed`, or `non-sealed`.
2. Any default or explicit constructor must be `private` (external instantiation via `new` is rejected at compile time).
3. In `TypeEnter`, `javac` synthesizes `private static final T $INSTANCE = new T();` and `public static final T getInstance() { return $INSTANCE; }`.
4. As a contextual keyword recognized in `JavacParser.modifiersOpt()`, existing code using `singleobject` as a variable or method identifier remains 100% source-compatible.

A working prototype in `jdk.compiler` (`Names`, `Flags`, `JavacParser`, `Check`, `TypeEnter`) along with `jtreg` tests is available at:
https://github.com/macrodatasystems/jdk/tree/feat/singleobject-keyword
```

### B. Email Template for `amber-dev@openjdk.org`
- **To**: `amber-dev@openjdk.org`
- **Subject**: `Prototype / RFC: Contextual class modifier 'singleobject' for singletons extending a superclass`
- **Body**: Use the same text as above, linking to `https://github.com/macrodatasystems/jdk/tree/feat/singleobject-keyword`.
