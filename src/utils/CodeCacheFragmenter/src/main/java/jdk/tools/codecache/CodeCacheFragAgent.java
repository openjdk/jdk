package jdk.tools.codecache;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;
import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.code.BlobType;
import jdk.test.whitebox.code.CodeBlob;

public class CodeCacheFragAgent {

  // Configurable variables (can be overridden via agent arguments)
  private static int minBlobSize = 500;
  private static int maxBlobSize = 10000;
  private static int avgBlobSize = 2000;
  private static int divBlobSize = 500;
  private static int requiredStableGcRounds = 3;
  private static double fillPercent = 50.0;

  private static final WhiteBox WHITEBOX = WhiteBox.getWhiteBox();
  private static long seed = System.currentTimeMillis();

  // Parse agent arguments and update configurable variables
  private static boolean parseArguments(String args) {
    if (args == null || args.trim().isEmpty()) {
      return true; // Use default values
    }

    String[] pairs = args.split(",");
    for (String pair : pairs) {
      String[] keyValue = pair.split("=", 2);
      if (keyValue.length != 2) {
        System.err.println("Invalid argument format: " + pair + ". Expected key=value");
        continue;
      }

      String key = keyValue[0].trim();
      String value = keyValue[1].trim();

      try {
        switch (key) {
          case "MinBlobSize":
            minBlobSize = Integer.parseInt(value);
            break;
          case "MaxBlobSize":
            maxBlobSize = Integer.parseInt(value);
            break;
          case "AvgBlobSize":
            avgBlobSize = Integer.parseInt(value);
            break;
          case "DivBlobSize":
            divBlobSize = Integer.parseInt(value);
            break;
          case "RequiredStableGcRounds":
            requiredStableGcRounds = Integer.parseInt(value);
            break;
          case "FillPercentage":
            fillPercent = Double.parseDouble(value);
            break;
          case "RandomSeed":
            seed = Long.parseLong(value);
            break;
          default:
            System.err.println("Unknown parameter: " + key);
            break;
        }
      } catch (NumberFormatException e) {
        System.err.println("Invalid value for " + key + ": " + value + ". Must be a number.");
      }
    }

    // Validate parameters
    if (fillPercent < 0.0 || fillPercent > 100.0) {
      System.err.println("FillPercentage must be between 0 and 100: " + fillPercent);
      return false;
    }

    if (minBlobSize <= 0 || maxBlobSize <= 0 || avgBlobSize <= 0 || divBlobSize <= 0) {
      System.err.println("Blob size parameters must be positive values");
      return false;
    }

    if (minBlobSize > maxBlobSize) {
      System.err.println("MinBlobSize (" + minBlobSize + ") cannot be greater than MaxBlobSize (" + maxBlobSize + ")");
      return false;
    }

    if (requiredStableGcRounds <= 0) {
      System.err.println("RequiredStableGcRounds must be positive: " + requiredStableGcRounds);
      return false;
    }

    return true;
  }

  public static void premain(String args) {
    // Parse agent arguments to configure variables
    if (!parseArguments(args)) {
      return;
    }

    // Check total size of non profiled heap
    long nonProfiledSize = BlobType.MethodNonProfiled.getSize();
    if (nonProfiledSize == 0) {
      System.err.println("Size of MethodNonProfiled heap must not be zero");
      return;
    }

    // Ensure no nmethods get added to CodeCache
    WHITEBOX.lockCompilation();

    // Deoptimized all nmethods
    WHITEBOX.deoptimizeAll();

    // Trigger GC until the number of non profiled blobs is stable
    gcUntilNonProfiledStable();

    // Fill up NonProfiled heap with randomly sized dummy blobs
    List<Long> blobs = new ArrayList<Long>();

    RandomGenerator blobGen = new Random(seed);
    while (true) {
      long addr = generateDummyBlob(blobGen);

      if (addr == -1) {
        break;
      }

      blobs.add(addr);
    }

    // Create list of indices
    List<Integer> indices = new ArrayList<>();
    for (int i = 0; i < blobs.size(); i++) {
      indices.add(i);
    }

    // Deterministically "shuffle" indices by sorting on a pseudo-random value.
    // Each index is assigned a stable pseudo-random key derived from (index + seed),
    // ensuring a reproducible ordering that is resilient to small changes in the
    // total number of code blobs between runs.
    indices.sort(Comparator.comparingLong(i -> new java.util.SplittableRandom(i + seed).nextLong()));

    // Free blobs until we hit the desired fragmentation amount
    long currentFilled = nonProfiledSize;
    for (Integer index : indices) {
      if (100.0 * currentFilled / nonProfiledSize <= fillPercent) {
        break;
      }

      long addr = blobs.get(index);
      currentFilled -= CodeBlob.getCodeBlob(addr).size;
      WHITEBOX.freeCodeBlob(addr);
    }

    // Unlock compilation
    WHITEBOX.unlockCompilation();
  }

  // Generate a randomly sized code cache blob
  // Returns the address if successfully created, otherwise returns -1
  public static long generateDummyBlob(RandomGenerator blobGen) {
    int size = -1;

    while (size < minBlobSize || size > maxBlobSize) {
      size = (int) blobGen.nextGaussian(avgBlobSize, divBlobSize);
    }

    long addr = WHITEBOX.allocateCodeBlob(size, BlobType.MethodNonProfiled.id);
    if (addr == 0) {
      return -1;
    }

    BlobType actualType = CodeBlob.getCodeBlob(addr).code_blob_type;
    if (actualType.id != BlobType.MethodNonProfiled.id) {
      WHITEBOX.freeCodeBlob(addr);
      return -1;
    }

    return addr;
  }

  // Returns the number of live CodeBlobs currently present in the
  // MethodNonProfiled code heap
  private static int countNonProfiledCodeBlobs() {
    Object[] entries = WHITEBOX.getCodeHeapEntries(BlobType.MethodNonProfiled.id);
    return entries == null ? 0 : entries.length;
  }

  // Run GCs until the MethodNonProfiled code heap blob count stabilizes
  private static void gcUntilNonProfiledStable() {
    int previousBlobs = Integer.MAX_VALUE;
    int stableCount = 0;

    while (stableCount < requiredStableGcRounds) {
      WHITEBOX.fullGC();
      int currentBlobs = countNonProfiledCodeBlobs();

      if (currentBlobs != previousBlobs) {
        stableCount = 0;
      } else {
        stableCount++;
      }

      previousBlobs = currentBlobs;
    }
  }
}
