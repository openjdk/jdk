# Template Framework
The Template Framework allows the generation of code with Templates. The goal is that these Templates are easy to write, and allow regression tests to cover a larger scope, and to make template based fuzzing easy to extend.

We want to make it easy to generate variants of tests. Often, we would like to have a set of tests, corresponding to a set of types, a set of operators, a set of constants, etc. Writing all the tests by hand is cumbersome or even impossible. When generating such tests with scripts, it would be preferable if the code generation happens automatically, and the generator script was checked into the code base. Code generation can go beyond simple regression tests, and one might want to generate random code from a list of possible templates, to fuzz individual Java features and compiler optimizations.

The Template Framework provides a facility to generate code with Templates. Templates are essentially a list of tokens that are concatenated (i.e. rendered) to a String. The Templates can have "holes", which are filled (replaced) by different values at each Template instantiation. For example, these "holes" can be filled with different types, operators or constants. Templates can also be nested, allowing a modular use of Templates.

Detailed documentation can be found in [Template.java](./Template.java).

The Template Framework only generates code in the form of a String. This code can then be compiled and executed, for example with the help of the [Compile Framework](../compile_framework/README.md).

The basic functionalities of the Template Framework are described in the [Template Interface](./Template.java), together with some examples. More examples can be found in [TestSimple.java](../../../testlibrary_tests/template_framework/examples/TestSimple.java), [TestAdvanced.java](../../../testlibrary_tests/template_framework/examples/TestAdvanced.java) and [TestTutorial.java](../../../testlibrary_tests/template_framework/examples/TestTutorial.java).
