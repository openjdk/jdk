package compiler.lib.generators;

import java.util.ArrayList;
import java.util.Collection;

class RandomElementGenerator<T> extends GeneratorBase<T> {
    private final ArrayList<T> elements;
    private final Generator<Integer> generator;

    RandomElementGenerator(Generators g, Collection<T> elements) {
        super(g);
        this.elements = new ArrayList<>(elements);
        if (this.elements.isEmpty()) throw new EmptyGeneratorException();
        this.generator = g.uniformInts(0, elements.size() - 1);
    }

    @Override
    public final T next() {
        return elements.get(generator.next());
    }
}