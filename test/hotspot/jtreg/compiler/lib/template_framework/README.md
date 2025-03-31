# Template Framework
The Template Framework allows the generation of code with Templates. The goal is that these Templates are easy to write, and allow regression tests to cover a larger scope, and to make template based fuzzing easy to extend.

The Template Framework only generates code in the form of a String. This code can then be compiled and executed, for example with help of the [Compile Framework](../compile_framework/README.md).

The basic functionalities of the Template Framework are described in the [Template Interface](./Template.java), together with some examples. More examples can be found in [TestSimple.java](../../../testlibrary_tests/template_framework/examples/TestSimple.java) and [TestTutorial.java](../../../testlibrary_tests/template_framework/examples/TestTutorial.java).

The [Template Library](../template_library/README.md) provides a large number of Templates which can be used to create anything from simple regression tests to complex fuzzers.
