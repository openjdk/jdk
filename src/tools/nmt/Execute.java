package nmt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.stream.Collectors;

public class Execute 
{
    public static class ProcessRunner
	{
		long pid;
		String output;

		public ProcessRunner(String args[], Path wdir)
		{
			try
			{
				ProcessBuilder pb = new ProcessBuilder(Arrays.asList(args));
				pb.directory(wdir.toFile());
				Process p = pb.start();
				this.pid = p.pid();
				BufferedReader out = new BufferedReader(new InputStreamReader(p.getInputStream()));
				this.output = out.lines().collect(Collectors.joining(System.lineSeparator()));
				p.waitFor();
				if (p.exitValue() != 0)
				{
					//throw new RuntimeException("ProcessRunner exited with "+p.exitValue());
					System.err.println("ProcessRunner exited with "+p.exitValue());
					//System.exit(1);
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}

		public String output()
		{
			return this.output;
		}

		public long pid()
		{
			return this.pid;
		}
	}

	public static ProcessRunner execCmd(String cmd[])
	{
		return execCmd(cmd, Paths.get("").toAbsolutePath().toString());
	}
	public static ProcessRunner execCmd(String cmd[], String path)
	{
		final boolean DEBUG = false;
		if (DEBUG)
		{
			System.err.println("execCmd");
			for (int c = 0; c < cmd.length; c++)
			{
				System.err.println("  cmd["+c+"]:"+cmd[c]);
			}
			System.err.println("  path["+path+"]");
		}
		ProcessRunner pr = new ProcessRunner(cmd, Path.of(path));
		if (DEBUG)
		{
			System.err.println("  result:["+pr.output()+"]");
			System.err.println("  pid:["+pr.pid()+"]");
		}
		return pr;
	}
}
