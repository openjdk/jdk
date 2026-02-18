package compiler.lib.generators;

import compiler.lib.ir_framework.ForceInline;

public record IntRange(int lo, int hi) {
    public IntRange {
        if (lo > hi) {
            throw new IllegalArgumentException("lo > hi");
        }
    }

    @ForceInline
    public int clamp(int v) {
        return Math.min(hi, Math.max(v, lo));
    }

    public static IntRange generate(Generator<Integer> g) {
        var a = g.next();
        var b = g.next();
        if (a > b) {
            var tmp = a;
            a = b;
            b = tmp;
        }
        return new IntRange(a, b);
    }
}