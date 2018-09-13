/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.test.lib.process;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public interface OutputBuffer {
  public static class OutputBufferException extends RuntimeException {
    private static final long serialVersionUID = 8528687792643129571L;

    public OutputBufferException(Throwable cause) {
      super(cause);
    }
  }

  /**
   * Returns the stdout result
   *
   * @return stdout result
   */
  public String getStdout();
  /**
   * Returns the stderr result
   *
   * @return stderr result
   */
  public String getStderr();
  public int getExitValue();

  public static OutputBuffer of(Process p) {
    return new LazyOutputBuffer(p);
  }

  public static OutputBuffer of(String stdout, String stderr, int exitValue) {
    return new EagerOutputBuffer(stdout, stderr, exitValue);
  }

  public static OutputBuffer of(String stdout, String stderr) {
    return of(stdout, stderr, -1);
  }

  class LazyOutputBuffer implements OutputBuffer {
    private static class StreamTask {
      private final ByteArrayOutputStream buffer;
      private final Future<Void> future;

      private StreamTask(InputStream stream) {
        this.buffer = new ByteArrayOutputStream();
        this.future = new StreamPumper(stream, buffer).process();
      }

      public String get() {
        try {
          future.get();
          return buffer.toString();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new OutputBufferException(e);
        } catch (ExecutionException | CancellationException e) {
          throw new OutputBufferException(e);
        }
      }
    }

    private final StreamTask outTask;
    private final StreamTask errTask;
    private final Process p;

    private LazyOutputBuffer(Process p) {
      this.p = p;
      outTask = new StreamTask(p.getInputStream());
      errTask = new StreamTask(p.getErrorStream());
    }

    @Override
    public String getStdout() {
      return outTask.get();
    }

    @Override
    public String getStderr() {
      return errTask.get();
    }

    @Override
    public int getExitValue() {
      try {
        return p.waitFor();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new OutputBufferException(e);
      }
    }
  }

  class EagerOutputBuffer implements OutputBuffer {
    private final String stdout;
    private final String stderr;
    private final int exitValue;

    private EagerOutputBuffer(String stdout, String stderr, int exitValue) {
      this.stdout = stdout;
      this.stderr = stderr;
      this.exitValue = exitValue;
    }

    @Override
    public String getStdout() {
      return stdout;
    }

    @Override
    public String getStderr() {
      return stderr;
    }

    @Override
    public int getExitValue() {
      return exitValue;
    }
  }
}
