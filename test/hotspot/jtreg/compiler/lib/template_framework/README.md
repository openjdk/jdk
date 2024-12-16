# Template Framework
The Template Framework allows the generation of code with Templates. The goal is that these Templates are easy to write, and allow regression tests to cover a larger scope, and to make temlate based fuzzing easy to extend.

## Motivation
Often one has to write a large set of tests over many types and code shapes and constant values to more adequately cover compiler features, bugs and optimizations. This is tedious work, and often one writes fewer tests than could have been or should have been written because of time limitations.

With the [Compile Framework](../compile_framework/README.md), we can easily compile and invoke runtime-generated Java code. The Template Framework now provides an easy way to generate such Java code. Templates can be specified with Strings. A simple syntax allows defining holes in these Templates, that can be filled with parameter values or recursive Templates.

This allows the test writer to specify just one or a few Templates, possibly with some parameter holes and a list of parameter values, or with Template holes for recursive Template instantiation. The Template Framework then takes care of generating code for each parameter value, and for filling in recursive Template instantiations, possibly with random code, code shapes and constant values.

## How to use the Template Framework
TODO

## Use case: Regression Fest
TODO

## Use case: Extensive feature testing (targetted Fuzzer)
TODO

## Use case: General purpose Template based Fuzzer.
TODO
