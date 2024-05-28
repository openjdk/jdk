package nmt;

import java.nio.file.Paths;

public class Main
{
	public static void main(String[] args) throws Exception
	{
		boolean DEBUG = false;
		if (DEBUG)
		{
			System.out.println("\n\n\n");
			for (int i = 0; i < args.length; i++)
			{
				System.out.println("args["+i+"]: \""+args[i]+"\"");
			}
			System.out.println("");
		}

		if (args.length >= 1)
		{
			String mode = args[0];
			
			if (true)
			{
				String java_path = Paths.get(args[1]).toAbsolutePath().toString();
				String path = Paths.get(args[2]).toAbsolutePath().toString();
				long pid = Long.parseLong(args[3]);
				Benchmark.examine_recording_with_pid(mode, java_path, pid, path);
			}
			else
			{
				// helper for recording benchmarks
				String wdir_path = Paths.get(args[0]).toAbsolutePath().toString();
				String jdk_bin_path = Paths.get(args[1]).toAbsolutePath().toString();
				BenchmarkRecorder.record(mode, wdir_path, jdk_bin_path);
			}
		}
	}
}
