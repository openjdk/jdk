package compiler.lib.generators;

import compiler.lib.ir_framework.ForceInline;

public record LongRange(long lo, long hi) {
    public LongRange {
        if (lo > hi) {
            throw new IllegalArgumentException("lo > hi");
        }
    }

    @ForceInline
    public long clamp(long v) {
        return Math.min(hi, Math.max(v, lo));
    }

    public static LongRange generate(Generator<Long> g) {
        var a = g.next();
        var b = g.next();
        if (a > b) {
            var tmp = a;
            a = b;
            b = tmp;
        }
        return new LongRange(a, b);
    }
}