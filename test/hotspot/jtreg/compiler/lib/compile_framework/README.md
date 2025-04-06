# Compile Framework
The Compile Framework allows the compilation and execution of Java and Jasm sources, which are generated at runtime.

## Motivation
We want to be able to generate Java and Jasm source code in the form of Strings at runtime, then compile them, load the classes and invoke some methods. This allows us to write more elaborate tests. For example small dedicated fuzzers that are targetted at some specific compiler optimization.

This is more powerful than hand-written tests, as we can generalize tests and cover more examples. It can also be better than a script-generated test: those are static and often the script is not integrated with the generated test. Another limitation of a generator script is that it is only run once, creating fixed static tests. Compilation at runtime allows us to randomly generate tests each time.

Of course we could compile at runtime without this framework, but it abstracts away the complexity of compilation, and allows the test-writer to focus on the generation of the source code.

## How to Use the Compile Framework

Please reference the examples found in [examples](../../../testlibrary_tests/compile_framework/examples/). Some basic tests can be found in [tests](../../../testlibrary_tests/compile_framework/tests/).

Here a very simple example:

    // Create a new CompileFramework instance.
    CompileFramework compileFramework = new CompileFramework();

    // Add a java source file.
    compileFramework.addJavaSourceCode("XYZ", "<your XYZ definition string>");

    // Compile the source file.
    compileFramework.compile();

    // Object returnValue = XYZ.test(5);
    Object returnValue = compileFramework.invoke("XYZ", "test", new Object[] {5});

### Creating a new Compile Framework Instance

First, one must create a `new CompileFramework()`, which creates two directories: a sources and a classes directory (see `sourcesDir` and `classesDir` in [CompileFramework](./CompileFramework.java)). The sources directory is where all the sources are placed by the Compile Framework, and the classes directory is where all the compiled classes are placed by the Compile Framework.

The Compile Framework prints the names of the directories, they are subdirectories of the JTREG scratch directory `JTWork/scratch`.

### Adding Sources to the Compilation

Java and Jasm sources can be added to the compilation using `compileFramework.addJavaSourceCode()` and `compileFramework.addJasmSourceCode()`. The source classes can depend on each other, and they can also use the IR Framework ([IRFrameworkJavaExample](../../../testlibrary_tests/compile_framework/examples/IRFrameworkJavaExample.java)).

When using the IR Framework, or any other library that needs to be compiled, it can be necessary to explicitly let JTREG compile that library. For example with `@compile ../../../compiler/lib/ir_framework/TestFramework.java`. Otherwise, the corresponding class files may not be available, and a corresponding failure will be encounter at class loading.

### Compiling

All sources are compiled with `compileFramework.compile()`. First, the sources are stored to the sources directory, then compiled, and then the class-files stored in the classes directory. The respective directory names are printed, so that the user can easily access the generated files for debugging.

### Interacting with the Compiled Code

The compiled code is then loaded with a `ClassLoader`. The classes can be accessed directly with `compileFramework.getClass(name)`. Specific methods can also directly be invoked with `compileFramework.invoke()`.

Should one require the modified classpath that includes the compiled classes, this is available with `compileFramework.getEscapedClassPathOfCompiledClasses()`. This can be necessary if the test launches any other VMs that also access the compiled classes. This is for example necessary when using the IR Framework.

### Running the Compiled Code in a New VM

One can also run the compiled code in a new VM. For this, one has to set the classpath with `compileFramework.getEscapedClassPathOfCompiledClasses()` ([RunWithFlagsExample](../../../testlibrary_tests/compile_framework/examples/RunWithFlagsExample.java))

### Verbose Printing

For debugging purposes, one can enable verbose printing, with `-DCompileFrameworkVerbose=true`.
