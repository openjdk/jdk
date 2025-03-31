# Template Framework
The Template Framework allows the generation of code with Templates. The goal is that these Templates are easy to write, and allow regression tests to cover a larger scope, and to make temlate based fuzzing easy to extend.

The Template Framework only generates code in the form of a String. This code can then be compiled and executed, for example with help of the [Compile Framework](../compile_framework/README.md).

The basic functionalities of the Template Framework are described in the [Template Class](./Template.java), together with some examples. More examples can be found in [TestSimple.java](../../../testlibrary_tests/template_framework/examples/TestSimple.java) and [TestTutorial.java](../../../testlibrary_tests/template_framework/examples/TestTutorial.java).
