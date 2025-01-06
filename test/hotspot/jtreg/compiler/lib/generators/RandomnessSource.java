package compiler.lib.generators;

public interface RandomnessSource {
    long nextLong();
    long nextLong(long lo, long hi);
    int nextInt();
    int nextInt(int lo, int hi);
    double nextDouble(double lo, double hi);
    float nextFloat(float lo, float hi);
}
