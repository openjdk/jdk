/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.javadoc.internal.doclets.formats.html;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.tools.Diagnostic;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlDocument;
import jdk.javadoc.internal.doclets.toolkit.Messages;
import jdk.javadoc.internal.doclets.toolkit.util.DocFile;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;

/**
 * A class to write {@link HtmlDocument} objects using tasks of an {@link ExecutorService}.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class BackgroundWriter {
    /**
     * Whether or not the feature is enabled. By default, the feature is enabled.
     */
    public static final boolean IS_ENABLED =
            !Boolean.getBoolean("javadoc.internal.bgWriter.disabled");

    /**
     * The number of background threads to create, to write out files.
     * Just one thread is created, primarily because one is enough.
     * Experiments show that the utilization of this thread is currently
     * about 8%, meaning we cannot generate pages fast enough to
     * warrant more threads. This may change if we ever start to
     * generate pages on multiple threads.
     */
    private final int BACKGROUND_THREADS =
            Integer.getInteger("javadoc.internal.bgWriter.threads", 1);

    /**
     * The number of tasks awaiting execution.
     * Since we currently consume (execute) tasks faster than we can
     * produce them, there is no point in queuing any additional tasks.
     * That just uses up memory, and so has a regressive impact on
     * performance, by causing more GC cycles.
     */
    private final int QUEUED_TASKS =
            Integer.getInteger("javadoc.internal.bgWriter.queue", 1);

    /**
     * A messages object, used to write messages should any errors occur.
     * Note that because the errors are handled here, and not passed up
     * to the AbstractDoclet, we cannot dump the stack in the case of
     * an error, although we could set a flag to do so, if necessary.
     */
    private final Messages messages;

    /**
     * The executor for the background tasks.
     */
    private final ExecutorService executor;

    /**
     * A semaphore to help restrict the number of queued and active tasks.
     * See "Java Concurrency In Practice", by Brian Goetz et al,
     * 8.3.3: Saturation Policies (last paragraph) and
     * Listing 8.4: Using a Semaphore to throttle task submission.
     */
    private final Semaphore semaphore;

    // The following members are just used to help monitor execution.

    /**
     * Indicates whether tasks have been submitted to the executor.
     * Set when the first task is scheduled, at which point {@link #start}
     * will be initialized.
     */
    private boolean started;

    /**
     * The time, in nanos, at which the writer accepts the first task.
     * Used to help compute the utilization of the threads.
     */
    private long start;

    /**
     * The cumulative time, in nanos, spent writing documents.
     */
    private final AtomicLong taskBusy = new AtomicLong(0);

    /**
     * Whether to report additional information, such as the overall utilization of the
     * executor.
     */
    private final boolean verbose = Boolean.getBoolean("javadoc.internal.bgWriter.verbose");

    /**
     * Creates a {@code BackgroundWriter}.
     *
     * @implNote
     * The writer uses a {@link Executors#newFixedThreadPool fixed thread pool} of
     * {@link #BACKGROUND_THREADS} background threads and a queue that is
     * restricted to {@link #QUEUED_TASKS} queued tasks.
     *
     * @param messages used to write out any error messages and other output
     */
    public BackgroundWriter(Messages messages) {
        this.messages = messages;
        executor = Executors.newFixedThreadPool(BACKGROUND_THREADS);
        semaphore = new Semaphore(QUEUED_TASKS + BACKGROUND_THREADS);
    }

    /**
     * Writes the given document at some point in the future, using the internal executor.
     * A semaphore is used to throttle the number of requests, although in practice this
     * rarely takes effect, because it is quicker to write documents than to generate them.
     *
     * @param doc  the document to be written
     * @param file the file to which to write the document
     */
    public void writeLater(HtmlDocument doc, DocFile file) {
        if (!started) {
            start = System.nanoTime();
            started = true;
        }

        try {
            semaphore.acquire();
            try {
                executor.execute(() -> {
                    try {
                        long taskStart  = System.nanoTime();
                        doc.write(file);
                        long taskEnd  = System.nanoTime();
                        taskBusy.addAndGet(taskEnd - taskStart);
                    } catch (DocFileIOException e) {
                        error(e);
                    } finally {
                        semaphore.release();
                    }
                });
            } catch (RejectedExecutionException e) {
                semaphore.release();
            }
        } catch (InterruptedException e) {
            messages.error("doclet.exception.interrupted");
        }

    }

    /**
     * Shuts down the internal executor service, releasing all resources.
     */
    public void finish() {
        try {
            executor.shutdown();
            boolean ok = executor.awaitTermination(5, TimeUnit.MINUTES);
            if (ok && started && verbose) {
                double elapsed = System.nanoTime() - start;
                double utilization = ((double) taskBusy.get()) / BACKGROUND_THREADS / elapsed * 100;
                messages.getReporter().print(Diagnostic.Kind.NOTE,
                        String.format("Background writer: threads: %d, queue: %d, utilization: %d%%",
                            BACKGROUND_THREADS, QUEUED_TASKS, ((int) utilization)));
            }

        } catch (InterruptedException e) {
            messages.error("doclet.exception.interrupted");
        }
    }

    /**
     * Reports an exception that occurred on the background thread.
     *
     * @param e the exception
     */
    private void error(DocFileIOException e) {
        switch (e.mode) {
            case READ ->
                    messages.error("doclet.exception.read.file",
                        e.fileName.getPath(), e.getCause());

            case WRITE ->
                    messages.error("doclet.exception.write.file",
                        e.fileName.getPath(), e.getCause());
        }
    }
}
