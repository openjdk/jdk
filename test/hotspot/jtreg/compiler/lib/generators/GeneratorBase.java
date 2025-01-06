package compiler.lib.generators;

abstract class GeneratorBase<T> implements Generator<T> {
    Generators g;

    GeneratorBase(Generators g) {
        this.g = g;
    }
}
