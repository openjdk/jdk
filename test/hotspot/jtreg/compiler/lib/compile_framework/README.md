# Compile Framework
This compile framework allows the compilation and execution of Java and Jasm sources, which are generated at runtime.

## Motivation
We want to be able to generate Java and Jasm source code in the form of Strings at runtime, then compile them, load the classes and invoke some methods. This allows us to write more elaborate tests. For example small dedicated fuzzers that are targetted at some specific compiler optimization.

This is more powerful than hand-written tests, as we can generalize tests and cover more examples. It can also be better than a script-generated test: those are static and often the script is not checked in with the test. Also, the script is only run once, giving a static tests. Compilation at runtime allows us to randomly generate tests each time.

Of course we could compile at runtime without this framework, but it abstracts away the complexity of compilation, and allows the test-writer to focus on the generation of the source code.

## How to Use the Framework

Please reference the examples found in [examples](../../../testlibrary_tests/compile_framework/examples/). Some basic tests can be found in [tests](../../../testlibrary_tests/compile_framework/tests/).

Here a very simple example:

    // Create a new CompileFramework instance.
    CompileFramework comp = new CompileFramework();

    // Add a java source file.
    comp.addJavaSourceCode("XYZ", "<your XYZ definition string>");

    // Compile the source file.
    comp.compile();

    // Object ret = XYZ.test(5);
    Object ret = comp.invoke("XYZ", "test", new Object[] {5});

### Creating a new Compile Framework Instance

First, one must create a `new CompileFramework()`, which creates two directories: a sources and a classes directory. The sources directory is where all the sources are placed by the Compile Framework, and the classes directory is where all the compiled classes are placed by the Compile Framework.

### Adding Sources to the Compilation

Java and Jasm sources can be added to the compilation using `comp.addJavaSourceCode` and `comp.addJasmSourceCode`. The source classes can depend on each other, and they can also use the IR-Framework ([TestFrameworkJavaExample](../../../testlibrary_tests/compile_framework/examples/TestFrameworkJavaExample.java)).

### Compiling

All sources are compiled with `comp.compile()`. First, the sources are stored to the srouces directory, then compiled, and then the class-files stored in the classes directory. The respective directory names are printed, so that the user can easily access the generated files for debugging.

### Interacting with the compiled code

The compiled code is then loaded with a ClassLoader. The classes can be accessed directly with `comp.getClass(name)`. Specific methods can also be directly invoked with `comp.invoke`.

Should one require the modified classpath that includes the compiled classes, this is available with `comp.getEscapedClassPathOfCompiledClasses()`. This can be necessary if the test launches any other VM's that also access the compiled classes. This is for example necessary when using the IR-Framework.
