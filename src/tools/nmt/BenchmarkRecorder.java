package nmt;

import com.sun.tools.javac.Main;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

// helper for recording RECORD_COUNT of benchmarks, then selecting the "best" for further analysis
public class BenchmarkRecorder
{
    static final int RECORD_COUNT = 11; // must be odd (so that it is easier to find median)!
	static
	{
		if ((RECORD_COUNT%2) == 0)
		{
			throw new RuntimeException("RECORD_COUNT is not odd: "+RECORD_COUNT);
		}
	}

	static class Result implements Comparable<Result>
	{
		long pid;
		long memory;

		public Result(long p, long m)
		{
			this.pid = p;
			this.memory = m;
		}

		public long pid()
		{
			return this.pid;
		}

		public long memory()
		{
			return this.memory;
		}

		@Override public int compareTo(Result r)
		{
			return (int)(this.memory - r.memory);
		}
	}

    static void print_best_record(String mode, String recording_path, String java_path, String[] args) throws Exception
	{
		final String[] NMT_FLAGS = {"-XX:NativeMemoryTracking=off", "-XX:NativeMemoryTracking=summary", "-XX:NativeMemoryTracking=detail"};
		final String RECORD_FLAG = "-XX:NMTRecordMemoryNMT_Allocations=2147483647";
		final Result[] RESULTS = new Result[RECORD_COUNT];
		for (int f = 0; f < NMT_FLAGS.length; f++)
		{
			System.out.printf(String.format("\nrecording with NMT '%s'\n", NMT_FLAGS[f]));
			long min_pid = 0;
			long max_pid = 0;
			long min_result = Long.MAX_VALUE;
			long max_result = Long.MIN_VALUE;
			double total_result = 0.0;
			for (int i = 0; i < RECORD_COUNT; i++)
			{
				System.out.printf(String.format("\n%d/%d\n", (i+1), RECORD_COUNT));
				int nmt_args_size = 4;
				String[] cmd = new String[nmt_args_size+args.length];
				cmd[0] = java_path;
				cmd[1] = "-XX:+UnlockDiagnosticVMOptions";
				cmd[2] = NMT_FLAGS[f];
				cmd[3] = RECORD_FLAG;
				for (int a = 0; a < args.length; a++)
				{
					cmd[nmt_args_size+a] = args[a];
				}
				Execute.ProcessRunner pr = Execute.execCmd(cmd, recording_path);
				String output = pr.output();
				if (output == null)
				{
					throw new RuntimeException("output == null");
				}
				long pid = pr.pid();
				long result = Benchmark.examine_recording_with_pid(mode, java_path, pid, recording_path.toString());
				total_result += result;
				RESULTS[i] = new Result(pid, result);
				if (result < min_result)
				{
					min_pid = pid;
					min_result = result;
				}
				else if (result > max_result)
				{
					max_pid = pid;
					max_result = result;
				}
				System.out.printf(String.format("   current: %,10d [%d]\n", result, pid));
			}
			System.out.printf(String.format("\n"));
			System.out.printf(String.format("       min: %,10d [%d]\n", min_result, min_pid));
			System.out.printf(String.format("   average: %,10d\n", (int)(total_result/(double)RECORD_COUNT)));
			Arrays.sort(RESULTS);
			Result r = RESULTS[RECORD_COUNT/2];
			System.out.printf(String.format("    median: %,10d [%d]\n", r.memory(), r.pid()));
			System.out.printf(String.format("       max: %,10d [%d]\n", max_result, max_pid));
			System.out.printf(String.format("     range: %,10d %.3f\n", (max_result-min_result), 100.0*(double)(max_result-min_result)/(double)(r.memory())));
		}
	}

	public static void record(String mode, String wdir_path, String jdk_bin_path) throws Exception
	{
		String java_path = Path.of(jdk_bin_path, "java").toAbsolutePath().toString();
		String recordings_path = Path.of(wdir_path, "recordings").toAbsolutePath().toString();
		String demos_path = Path.of(wdir_path, "demos").toAbsolutePath().toString();

		Files.createDirectories(Path.of(recordings_path));

		boolean recordHelloWorld = true;
		if (recordHelloWorld)
		{
			System.err.println("\n\nrecording HelloWorld...");
			String rec_path = Path.of(recordings_path, "HelloWorld").toAbsolutePath().toString();
			Files.createDirectories(Path.of(rec_path));

			String[] args = {"-cp", demos_path, "HelloWorld"};
			print_best_record(mode, rec_path, java_path, args);
		}

		boolean recordJ2Ddemo = false;
		if (recordJ2Ddemo)
		{
			System.err.println("\n\nrecording J2Ddemo...");
			String rec_path = Path.of(recordings_path, "J2Ddemo").toAbsolutePath().toString();
			Files.createDirectories(Path.of(rec_path));

			String jar_path = Path.of(demos_path, "J2Ddemo.jar").toAbsolutePath().toString();
			String[] args = {"-jar", jar_path};
			print_best_record(mode, rec_path, java_path, args);
		}
	}
}
