package compiler.lib.generators;

import java.util.List;

final class RestrictableMixedGenerator<T extends Comparable<T>> extends MixedGenerator<RestrictableGenerator<T>, T> implements RestrictableGenerator<T> {
    RestrictableMixedGenerator(Generators g, List<RestrictableGenerator<T>> generators, List<Integer> weights) {
        super(g, generators, weights);
    }

    RestrictableMixedGenerator(RestrictableMixedGenerator<T> other, T newLo, T newHi) {
        super(other, (generator) -> {
            try {
                return generator.restricted(newLo, newHi);
            } catch (EmptyGeneratorException e) {
                return null;
            }
        });
    }

    @Override
    public RestrictableGenerator<T> restricted(T newLo, T newHi) {
        return new RestrictableMixedGenerator<>(this, newLo, newHi);
    }
}
