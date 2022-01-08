package jdk.internal.util.random;
import java.util.Random;
import java.util.random.RandomGenerator;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
@SuppressWarnings("serial")
/**
 * Class used to wrap a {@link java.util.random.RandomGenerator} to {@link java.util.Random}
 * 
 *
 */
public class RandomWrapper extends Random implements RandomGenerator {
    private final RandomGenerator randomToWrap;
    
    public RandomWrapper(RandomGenerator randomToWrap) {
	this.randomToWrap = randomToWrap;
    }
    
    /**
     * setSeed does not exist in {@link java.util.random.RandomGenerator} so can't use it
     */
    @Override
    public synchronized void setSeed(long seed) {
    }
    
    @Override
    public void nextBytes(byte[] bytes) {
	this.randomToWrap.nextBytes(bytes);
    }
    
    @Override
    public int nextInt() {
	return this.randomToWrap.nextInt();
    }
    
    @Override
    public int nextInt(int bound) {
	return this.randomToWrap.nextInt(bound);
    }
    
    @Override
    public long nextLong() {
	return this.randomToWrap.nextLong();
    }
    
    @Override
    public boolean nextBoolean() {
	return this.randomToWrap.nextBoolean();
    }
 
    @Override
    public float nextFloat() {
	return this.randomToWrap.nextFloat();
    }
    
    @Override
    public double nextDouble() {
	return this.randomToWrap.nextDouble();
    }
    
    @Override
    public synchronized double nextGaussian() {
	return this.randomToWrap.nextGaussian();
    }
    
    @Override
    public IntStream ints(long streamSize) {
	return this.randomToWrap.ints(streamSize);
    }
    
    @Override
    public IntStream ints() {
	return this.randomToWrap.ints();
    }
    
    @Override
    public IntStream ints(long streamSize, int randomNumberOrigin, int randomNumberBound) {
	return this.randomToWrap.ints(streamSize, randomNumberOrigin, randomNumberBound);
    }
    
    @Override
    public IntStream ints(int randomNumberOrigin, int randomNumberBound) {
	return this.randomToWrap.ints(randomNumberOrigin,randomNumberBound);
    }
    
    @Override
    public LongStream longs(long streamSize) {
	return this.randomToWrap.longs(streamSize);
    }
    
    @Override
    public LongStream longs() {
	return this.randomToWrap.longs();
    }
    
    @Override
    public LongStream longs(long streamSize, long randomNumberOrigin, long randomNumberBound) {
	return this.randomToWrap.longs(streamSize, randomNumberOrigin, randomNumberBound);
    }
    
    @Override
    public LongStream longs(long randomNumberOrigin, long randomNumberBound) {
	return this.randomToWrap.longs(randomNumberOrigin, randomNumberBound);
    }
    
    @Override
    public DoubleStream doubles(long streamSize) {
	return this.randomToWrap.doubles(streamSize);
    }
    
    @Override
    public DoubleStream doubles() {
	return this.randomToWrap.doubles();
    }
    
    @Override
    public DoubleStream doubles(long streamSize, double randomNumberOrigin, double randomNumberBound) {
	return this.randomToWrap.doubles(streamSize, randomNumberOrigin, randomNumberBound);
    }
    
    @Override
    public DoubleStream doubles(double randomNumberOrigin, double randomNumberBound) {
	return this.randomToWrap.doubles(randomNumberOrigin,randomNumberBound);
    }
}
