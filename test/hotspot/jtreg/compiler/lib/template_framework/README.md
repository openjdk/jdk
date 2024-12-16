# Template Framework
The Template Framework allows the generation of code with Templates. The goal is that these Templates are easy to write, and allow regression tests to cover a larger scope, and to make temlate based fuzzing easy to extend.

## Motivation
Often one has to write a large set of tests over many types and code shapes and constant values to more adequately cover compiler features, bugs and optimizations. This is tedious work, and often one writes fewer tests than could have been or should have been written because of time limitations.

With the [Compile Framework](../compile_framework/README.md), we can easily compile and invoke runtime-generated Java code. The Template Framework now provides an easy way to generate such Java code. Templates can be specified with Strings. A simple syntax allows defining holes in these Templates, that can be filled with parameter values or recursive Templates.

This allows the test writer to specify just one or a few Templates, possibly with some parameter holes and a list of parameter values, or with Template holes for recursive Template instantiation. The Template Framework then takes care of generating code for each parameter value, and for filling in recursive Template instantiations, possibly with random code, code shapes and constant values.

## How to use the Template Framework
Please reference the examples found in [examples](../../../testlibrary_tests/template_framework/examples/). Some basic tests can be found in [tests](../../../testlibrary_tests/template_framework/tests/).

### Instantiating a single Template

One can instantiate a single Template directly using `template.instantiate()`, if it has no parameter holes. If there are template holes, these can be filled with `template.where` chaining calls, and after filling all the template holes, the template can be instantiated. Here a simple example:

    // Defiie a Template with name "my_example" that has two parameter holes
    // "param1" and "param2", and a recursive call to a "int_con" CodeGenerator
    // which takes parameters "lo" with value 0 and "hi" with value 100.
    Template template = new Template("my_example",
        """
        package p.xyz;

        public class InnerTest {
            public static int test() {
                return #{param1} + #{param2} + #{:int_con(lo=0,hi=100)};
            }
        }
        """
    );

    // The template is instantiated, and the two parameters are replaced with the
    // provided values, and the recursive CodeGenerator call replaces the corresponding
    // Template hole with a random number from 0 to 100. The resulting code is returned
    // as a String that could be passed on to the Compile Framework.
    String code = template.where("param1", "42").where("param2", "7").instantiate();

### Conveniently instantiating multiple tests into a single class with the TestClassInstantiator

The [TestClassInstantiator](./TestClassInstantiator.java) is a convenient facility that allows the instantiation of multiple tests into a single test class. Please refer to the example [TestInstantiationOfManyTests.java](../../../testlibrary_tests/template_framework/examples/TestInstantiationOfManyTests.java) for various ways of using this utility.

TODO example with IR framework???

## Use case: Regression Fest
Hand-written regression tests are very time consuming, and often fewere tests are written than desired due to time constraints of the developer. With Templates, the developer can simply take the reduced regression test, and turn some constants into Template holes that can then be replaced by random constants, or other interesting code shapes that may trigger special cases of the bug or feature under test.

## Use case: Extensive feature testing (targetted Fuzzer)
TODO

## Use case: General purpose Template based Fuzzer.
TODO

## The Standard CodeGeneratorLibrary
TODO: list all CodeGenerators
