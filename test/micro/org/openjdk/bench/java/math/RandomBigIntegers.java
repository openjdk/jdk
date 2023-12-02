package org.openjdk.bench.java.math;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Random;

public class RandomBigIntegers {

    public static void main(String[] args) {
        System.getProperties();
        String javaInfo = "JVM: ";
        javaInfo += " " + System.getProperty("java.vm.name");
        javaInfo += " " + System.getProperty("java.vm.version");
        String osInfo = "OS: ";
        osInfo += " " + System.getProperty("os.name");
        osInfo += " " + System.getProperty("os.version");

        System.out.println(javaInfo);
        System.out.println(osInfo);

        for (int numBits = 1; numBits >= 0; numBits <<= 1) {
            Random rnd = new Random();
            long t0, t1;
            t0 = System.nanoTime();
            new BigInteger(numBits, rnd);
            t1 = System.nanoTime();

            System.out.println("numBits=" + numBits + ", execution time=" + Duration.ofNanos(t1 - t0));
        }
    }
}
