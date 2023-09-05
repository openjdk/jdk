# IR Test Framework
This folder contains a test framework whose main purpose is to perform regex-based checks on the C2 IR shape of test methods emitted by the VM flags `-XX:+PrintIdeal`, `-XX:CompileCommand=PrintIdealPhase` and/or `-XX:+PrintOptoAssembly`. The framework can also be used for other non-IR matching (and non-compiler) tests by providing easy to use annotations for commonly used testing patterns and compiler control flags.

## 1. How to Use the Framework
The framework is intended to be used in JTreg tests. The JTreg header of the test must contain `@library /test/lib /` (2 paths) and should be run as a driver with `@run driver`. Annotate the test code with the supported framework annotations and call the framework from within the test's `main()` method. A simple example is shown below:

    /*
     * @test
     * @summary A simple test using the test framework.
     * @library /test/lib /
     * @run driver my.package.MySimpleTest
     */

    package my.package;

    import compiler.lib.ir_framework.*;

    public class MySimpleTest {

        public static void main(String[] args) {
            TestFramework.run(); // The framework runs all tests of this class.
        }

        @Test
        @IR(failOn = IRNode.STORE) // Fail if the IR of myTest() contains any stores.
        public void myTest() {
            /* ... */
        }
    }

There are various ways how to set up and run a test within the `main()` method of a JTreg test. These are described and can be found in the [TestFramework](./TestFramework.java) class.

## 2. Features
The framework offers various annotations and flags to control how your test code should be invoked and being checked. This section gives an overview over all these features.

### 2.1 Different Tests
There are three kinds of tests depending on how much control is needed over the test invocation.
#### Base Tests
The simplest form of testing provides a single `@Test` annotated method which the framework will invoke as part of the testing. The test method has no or well-defined arguments that the framework can automatically provide.

More information on base tests with a precise definition can be found in the Javadocs of [Test](./Test.java). Concrete examples on how to specify a base test can be found in [BaseTestsExample](../../../testlibrary_tests/ir_framework/examples/BaseTestExample.java).

#### Checked Tests
The base tests do not provide any way of verification by user code. A checked test enables this by allowing the user to define an additional `@Check` annotated method which is invoked directly after the `@Test` annotated method. This allows the user to perform various checks about the test method including return value verification.

More information on checked tests with a precise definition can be found in the Javadocs of [Check](./Check.java). Concrete examples on how to specify a checked test can be found in [CheckedTestsExample](../../../testlibrary_tests/ir_framework/examples/CheckedTestExample.java).

#### Custom Run Tests
Neither the base nor the checked tests provide any control over how a `@Test` annotated method is invoked in terms of customized argument values and/or conditions for the invocation itself. A custom run test gives full control over the invocation of the `@Test` annotated method to the user. The framework calls a dedicated `@Run` annotated method from which the user can invoke the `@Test` method according to his/her needs.

More information on checked tests with a precise definition can be found in the Javadocs of [Run](./Run.java). Concrete examples on how to specify a custom run test can be found in [CustomRunTestsExample](../../../testlibrary_tests/ir_framework/examples/CustomRunTestExample.java).

### 2.2 IR Verification
The main feature of this framework is to perform a simple but yet powerful regex-based C2 IR matching on the output of `-XX:+PrintIdeal`, `-XX:+PrintOptoAssembly` and/or on specific compile phases emitted by the compile command `-XX:CompileCommand=PrintIdealPhase` which supports the same set of compile phases as the Ideal Graph Visualizer (IGV).

The user has the possibility to add one or more `@IR` annotations to any `@Test` annotated method (regardless of the kind of test mentioned in section 2.1) to specify regex constraints/rules on the compiled IR shape of any compile phase (for simplicity, the framework treats the output of `-XX:+PrintIdeal` and `-XX:+PrintOptoAssembly` as a separate compile phase next to the compile phases emitted by `-XX:CompileCommand=PrintIdealPhase`).

#### Pre-defined Regexes for IR Nodes
To perform a matching on a C2 IR node, the user can directly use the `public static final` strings defined in class [IRNode](./IRNode.java) which mostly represent either a real IR node or group of IR nodes as found in the C2 compiler as node classes (there are rare exceptions). These strings represent special placeholder strings (referred to as "IR placeholder string" or just "IR node") which are replaced by the framework by regexes depending on which compile phases (defined with attribute `phase` in [@IR](./IR.java)) the IR rule should be applied on. If an IR node placeholder string cannot be used for a specific compile phase (e.g. the IR node does not exist in this phase), a format violation will be reported.

The exact mapping from an IR node placeholder string to regexes for different compile phases together with a default phase (see next section) is defined in a static block directly below the corresponding IR node placeholder string in [IRNode](./IRNode.java).

#### Composite IR Nodes
There are also special composite IR node placeholder strings which expect an additional user defined string which are then inserted in the final regex. For example, `IRNode.STORE_OF_FIELD` matches any store to the user defined field name. In the following `@IR` rule, we fail because we have a store to `iFld`:

```
@Test
@IR(failOn = {IRNode.STORE_OF_FIELD, "iFld"})
public void test() {
    iFld = 34;
}
```

#### Vector IR Nodes
For vector nodes, we not only check for the presence of the node, but also its type and size (number of elements in the vector). Every node has an associated type, for example `IRNode.LOAD_VECTOR_I` has type `int` and `IRNode.LOAD_VECTOR_F` has type `float`. The size can be explicitly specified as an additional argument. For example:

```
@IR(counts = {IRNode.LOAD_VECTOR_F, IRNode.VECTOR_SIZE_16, "> 0"},
    applyIf = {"MaxVectorSize", "=64"},
    applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"})
static float[] test() {
    float[] a = new float[1024*8];
    for (int i = 0; i < a.length; i++) {
        a[i]++;
    }
    return a;
}
```

However, the size does not have to be specified. In most cases, one either wants to have vectorization at the maximal possible vector width, or no vectorization at all. Hence, for lower bound counts ('>' or '>=') and equal count comparisons with strictly positive count (e.g. "=2") the default size is `IRNode.VECTOR_SIZE_MAX`, and for upper bound counts ('<' or '<=' or '=0' or failOn) the default is `IRNode.VECTOR_SIZE_ANY`. On machines with 'canTrustVectorSize == false' (default Cascade Lake) the maximal vector width is not predictable currently (32 byte for SuperWord and 64 byte for VectorAPI). Hence, on such a machine we have to automatically weaken the IR rules. All lower bound counts are performed checking with `IRNode.VECTOR_SIZE_ANY`. Upper bound counts with no user specified size are performed with `IRNode.VECTOR_SIZE_ANY` but upper bound counts with a user specified size are not checked at all. Equal count comparisons with strictly positive count are also not checked at all. Details and reasoning can be found in [RawIRNode](./driver/irmatching/irrule/checkattribute/parsing/RawIRNode.java).

More examples can be found in [IRExample](../../../testlibrary_tests/ir_framework/examples/IRExample.java). You can also find many examples in the Vector API and SuperWord tests, when searching for `IRNode.VECTOR_SIZE` or `IRNode.LOAD_VECTOR`.

#### User-defined Regexes

The user can also directly specify user-defined regexes in combination with a required compile phase (there is no default compile phase known by the framework for custom regexes). If such a user-defined regex represents a not yet supported C2 IR node, it is highly encouraged to directly add a new IR node placeholder string definition to [IRNode](./IRNode.java) for it instead together with a static regex mapping block.

#### Default Compile Phase
When not specifying any compile phase with `phase` in [@IR](./IR.java) (or explicitly setting `CompilePhase.DEFAULT`), the framework will perform IR matching on a default compile phase which for most IR nodes is `CompilePhase.PRINT_IDEAL` (output of flag `-XX:+PrintIdeal`, the state of the machine independent ideal graph after applying optimizations). The default phase for each IR node is defined in the static regex mapping block below each IR node placeholder string in [IRNode](./IRNode.java).

#### Two Kinds of IR Checks
The [@IR](./IR.java) annotation provides two kinds of checks:

 - `failOn`: A list of one or more IR nodes/user-defined regexes which are not allowed to occur in any compilation output of any compile phase.
 - `counts`: A list of one or more "IR node/user-defined regex - counter" pairs which specify how often each IR node/user-defined regex should be matched on the compilation output of each compile phase.

#### Disable/Enable IR Rules based on VM Flags
One might also want to restrict the application of certain `@IR` rules depending on the used flags in the test VM. These could be flags defined by the user or by JTreg. In the latter case, the flags must be whitelisted in `JTREG_WHITELIST_FLAGS` in [TestFramework](./TestFramework.java) (i.e. have no unexpected impact on the IR except if the flag simulates a specific machine setup like `UseAVX={1,2,3}` etc.) to enable an IR verification by the framework. The `@IR` rules thus have an option to restrict their application:

- `applyIf`: Only apply a rule if a flag has the specified value/range of values.
- `applyIfNot`: Only apply a rule if a flag has **not** a specified value/range of values
               (inverse of `applyIf`).
- `applyIfAnd`: Only apply a rule if **all** flags have the specified value/range of values.
- `applyIfOr`:  Only apply a rule if **at least one** flag has the specified value/range of values.

#### Disable/Enable IR Rules based on available CPU Features
Sometimes, an `@IR` rule should only be applied if a certain CPU feature is present. This can be done with the attributes `applyIfCPUFeatureXXX` in [@IR](./IR.java) which follow the same logic as the `applyIfXXX` methods for flags in the previous section. An example with `applyIfCPUFeatureXXX` can be found in [TestCPUFeatureCheck](../../../testlibrary_tests/ir_framework/tests/TestCPUFeatureCheck.java) (internal framework test).

If a `@Test` annotated method has multiple preconditions (for example `applyIf` and `applyIfCPUFeature`), they are evaluated as a logical conjunction. It's worth noting that flags in `applyIf` are checked only if the CPU features in `applyIfCPUFeature` are matched when they are both specified. This avoids the VM flag being evaluated on hardware that does not support it. An example with both `applyIfCPUFeatureXXX` and `applyIfXXX` can be found in [TestPreconditions](../../../testlibrary_tests/ir_framework/tests/TestPreconditions.java) (internal framework test).

#### Implicitly Skipping IR Verification
An IR verification cannot always be performed. Certain VM flags explicitly disable IR verification, change the IR shape in unexpected ways letting IR rules fail or even make IR verification impossible:

- `-DVerifyIR=false` is used
- The test is run with a non-debug build.
- `-Xcomp`, `-Xint`, `-XX:-UseCompile`, `-XX:CompileThreshold`, `-DFlipC1C2=true`, or `-DExcludeRandom=true` are used.
- JTreg specifies non-whitelisted flags as VM and/or Javaoptions (could change the IR in unexpected ways).

More information about IR matching can be found in the Javadocs of [IR](./IR.java). Concrete examples on how to specify IR constraint/rules can be found in [IRExample](../../../testlibrary_tests/ir_framework/examples/IRExample.java), [TestIRMatching](../../../testlibrary_tests/ir_framework/tests/TestIRMatching.java) (internal framework test), and [TestPhaseIRMatching](../../../testlibrary_tests/ir_framework/tests/TestPhaseIRMatching.java) (internal framework test).

### 2.3 Test VM Flags and Scenarios
The recommended way to use the framework is by defining a single `@run driver` statement in the JTreg header which, however, does not allow the specification of additional test VM flags. Instead, the user has the possibility to provide VM flags by calling `TestFramework.runWithFlags()` or by creating a `TestFramework` builder object on which `addFlags()` can be called.

If a user wants to provide multiple flag combinations for a single test, he or she has the option to provide different scenarios. A scenario based flag will always have precedence over other user defined flags. More information about scenarios can be found in the Javadocs of [Scenario](./Scenario.java).

### 2.4 Compiler Controls
The framework allows the use of additional compiler control annotations for helper method and classes in the same fashion as JMH does. The following annotations are supported and described in the referenced Javadocs for the annotation class:

- [@DontInline](./DontInline.java)
- [@ForceInline](./ForceInline.java)
- [@DontCompile](./DontCompile.java)
- [@ForceCompile](./ForceCompile.java)
- [@ForceCompileClassInitializer](./ForceCompileClassInitializer.java)

### 2.5 Framework Debug and Stress Flags
The framework provides various stress and debug flags. They should mainly be used as JTreg VM and/or Javaoptions (apart from `VerifyIR`). The following (property) flags are supported:

- `-DVerifyIR=false`: Explicitly disable IR verification. This is useful, for example, if some scenarios use VM flags that let `@IR` annotation rules fail and the user does not want to provide separate IR rules or add flag preconditions to the already existing IR rules.
- `-DTest=test1,test2`: Provide a list of `@Test` method names which should be executed.
- `-DExclude=test3`: Provide a list of `@Test` method names which should be excluded from execution.
- `-DScenarios=1,2`: Provide a list of scenario indexes to specify which scenarios should be executed.
- `-DWarmup=200`: Provide a new default value of the number of warm-up iterations (framework default is 2000). This might have an influence on the resulting IR and could lead to matching failures (the user can also set a fixed default warm-up value in a test with `testFrameworkObject.setDefaultWarmup(200)`).
- `-DReportStdout=true`: Print the standard output of the test VM.
- `-DVerbose=true`: Enable more fain-grained logging (slows the execution down).
- `-DReproduce=true`: Flag to use when directly running a test VM to bypass dependencies to the driver VM state (for example, when reproducing an issue).
- `-DPrintTimes=true`: Print the execution time measurements of each executed test.
- `-DVerifyVM=true`: The framework runs the test VM with additional verification flags (slows the execution down).
- `-DExcluceRandom=true`: The framework randomly excludes some methods from compilation. IR verification is disabled completely with this flag.
- `-DFlipC1C2=true`: The framework compiles all `@Test` annotated method with C1 if a C2 compilation would have been applied and vice versa. IR verification is disabled completely with this flag.
- `-DShuffleTests=false`: Disables the random execution order of all tests (such a shuffling is always done by default).
- `-DDumpReplay=true`: Add the `DumpReplay` directive to the test VM.
- `-DGCAfter=true`: Perform `System.gc()` after each test (slows the execution down).
- `-DTestCompilationTimeout=20`: Change the default waiting time (default: 10s) for a compilation of a normal `@Test` annotated method.
- `-DWaitForCompilationTimeout=20`: Change the default waiting time (default: 10s) for a compilation of a `@Test` annotated method with compilation level [WAIT\_FOR\_COMPILATION](./CompLevel.java).
- `-DIgnoreCompilerControls=true`: Ignore all compiler controls applied in the framework. This includes any compiler control annotations (`@DontCompile`, `@DontInline`, `@ForceCompile`, `@ForceInline`, `@ForceCompileStaticInitializer`), the exclusion of `@Run` and `@Check` methods from compilation, and the directive to not inline `@Test` annotated methods.
- `-DExcludeRandom=true`: Randomly exclude some methods from compilation.
- `-DPreferCommandLineFlags=true`: Prefer flags set via the command line over flags specified by the tests.

## 3. Test Framework Execution
This section gives an overview of how the framework is executing a JTreg test that calls the framework from within its `main()` method.

The framework will spawn a new "test VM" to execute the user defined tests. The test VM collects all tests of the test class specified by the user code in `main()` and ensures that there is no violation of the required format by the framework. In a next step, the framework does the following for each test in general:
1. Warm the test up for a predefined number of times (default 2000). This can also be adapted for all tests by using `testFrameworkobject.setDefaultWarmup(100)` or for individual tests with an additional [@Warmup](./Warmup.java) annotation.
2. After the warm-up is finished, the framework compiles the associated `@Test` annotated method at the specified compilation level (default: C2).
3. After the compilation, the test is invoked one more time.

Once the test VM terminates, IR verification (if possible) is performed on the output of the test VM. If any test throws an exception during its execution or if IR matching fails, the failures are collected and reported in a pretty format. Check the standard error and output for more information and how to reproduce these failures.

Some of the steps above can be different due to the kind of the test or due to using non-default annotation properties. These details and differences are described in the Javadocs for the three tests (see section 2.1 Different Tests).

More information about the internals and the workflow of the framework can be found in the Javadocs of [TestFramework](./TestFramework.java).

## 4. Internal Framework Tests
There are various tests to verify the correctness of the test framework. These tests can be found in [ir_framework](../../../testlibrary_tests/ir_framework) and can directly be run with JTreg. The tests are part of the normal JTreg tests of HotSpot and should be run upon changing the framework code as a minimal form of testing.

Additional testing was performed by converting all compiler Inline Types tests that used the currently present IR test framework in Valhalla (see [JDK-8263024](https://bugs.openjdk.org/browse/JDK-8263024)). It is strongly advised to make sure a change to the framework still lets these converted tests in Valhalla pass as part of an additional testing step.

## 5. Framework Package Structure
A user only needs to import classes from the package `compiler.lib.ir_framework` (e.g. `import compiler.lib.ir_framework.*;`) which represents the interface classes to the framework. The remaining framework internal classes are kept in separate subpackages and should not directly be imported:

- `compiler.lib.ir_framework.driver`: These classes are used while running the driver VM (same VM as the one running the user code's `main()` method of a JTreg test).
- `compiler.lib.ir_framework.flag`: These classes are used while running the flag VM to determine additional flags for the test VM which are required for IR verification.
- `compiler.lib.ir_framework.test`: These classes are used while running the test VM (i.e. the actual execution of the user tests as described in section 3).
- `compiler.lib.ir_framework.shared`: These classes can be called from either the driver, flag, or test VM.

## 6. Summary
The initial design and feature set was kept simple and straight forward and serves well for small to medium sized tests. There are a lot of possibilities to further enhance the framework and make it more powerful. This can be tackled in additional RFEs. A few ideas can be found as subtasks of the [initial RFE](https://bugs.openjdk.org/browse/JDK-8254129) for this framework.
