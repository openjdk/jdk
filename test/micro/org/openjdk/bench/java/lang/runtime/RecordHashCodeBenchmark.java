package org.openjdk.bench.java.lang.runtime;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@Fork(value = 1)
@Warmup(iterations = 1, time = 3)
@Measurement(iterations = 2, time = 3)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.Throughput)
public class RecordHashCodeBenchmark {

    record One(int a) {}

    @State(Scope.Thread)
    public static class BenchmarkState {
        KeyClass k1 = new KeyClass(new One(1), "a");
        KeyClass k3 = new KeyClass(new One(1), new String("a"));
        KeyRecord k2 = new KeyRecord(new One(1), "a");
        KeyRecord k4 = new KeyRecord(new One(1), new String("a"));
    }

    @Benchmark
    public int classHashCode(BenchmarkState state) {
        return state.k1.hashCode();
    }

    @Benchmark
    public int classHashCodeNoProfile(BenchmarkState state) {
        return state.k1.hashCodeNoProfile();
    }

    @Benchmark
    public int recordHashCode(BenchmarkState state) {
        return state.k2.hashCode();
    }

    @Benchmark
    public boolean classEquals(BenchmarkState state) {
        return state.k1.equals(state.k3);
    }

    @Benchmark
    public boolean classEqualsNoProfile(BenchmarkState state) {
        return state.k1.equalsNoProfile(state.k3);
    }

    @Benchmark
    public boolean recordEquals(BenchmarkState state) {
        return state.k2.equals(state.k4);
    }


    private static final record KeyRecord(Object key1, Object key2) {}

    private static final class KeyClass {
        private final Object key1;
        private final Object key2;

        KeyClass(Object key1, Object key2) {
            this.key1 = key1;
            this.key2 = key2;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((key1 == null) ? 0 : key1.hashCode());
            result = prime * result + ((key2 == null) ? 0 : key2.hashCode());
            return result;
        }

        public int hashCodeNoProfile() {
            final int prime = 31;
            int result = 1;
            result = prime * result + Objects.hashCode(key1);
            result = prime * result + Objects.hashCode(key2);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            KeyClass other = (KeyClass) obj;
            if (key1 == null) {
                if (other.key1 != null)
                    return false;
            }
            else if (!key1.equals(other.key1))
                return false;
            if (key2 == null) {
                if (other.key2 != null)
                    return false;
            }
            else if (!key2.equals(other.key2))
                return false;
            return true;
        }

        public boolean equalsNoProfile(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            KeyClass other = (KeyClass) obj;
            return Objects.equals(key1, other.key1) && Objects.equals(key2, other.key2);
        }

        @Override
        public String toString() {
            return "KeyClass [key1=" + key1 + ", key2=" + key2 + "]";
        }
    }
}