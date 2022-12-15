package jdk.internal.org.commonmark.internal.util;

import java.util.BitSet;

public class AsciiMatcher implements CharMatcher {
    private final BitSet set;

    private AsciiMatcher(Builder builder) {
        this.set = builder.set;
    }

    @Override
    public boolean matches(char c) {
        return set.get(c);
    }

    public Builder newBuilder() {
        return new Builder((BitSet) set.clone());
    }

    public static Builder builder() {
        return new Builder(new BitSet());
    }

    public static class Builder {
        private final BitSet set;

        private Builder(BitSet set) {
            this.set = set;
        }

        public Builder c(char c) {
            if (c > 127) {
                throw new IllegalArgumentException("Can only match ASCII characters");
            }
            set.set(c);
            return this;
        }

        public Builder range(char from, char toInclusive) {
            for (char c = from; c <= toInclusive; c++) {
                c(c);
            }
            return this;
        }

        public AsciiMatcher build() {
            return new AsciiMatcher(this);
        }
    }
}
