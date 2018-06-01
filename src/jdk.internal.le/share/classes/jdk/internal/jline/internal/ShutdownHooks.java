/*
 * Copyright (c) 2002-2016, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.jline.internal;

import java.util.ArrayList;
import java.util.List;

import static jdk.internal.jline.internal.Preconditions.checkNotNull;

/**
 * Manages the JLine shutdown-hook thread and tasks to execute on shutdown.
 *
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 * @since 2.7
 */
public class ShutdownHooks
{
    public static final String JLINE_SHUTDOWNHOOK = "jline.shutdownhook";

    private static final boolean enabled = Configuration.getBoolean(JLINE_SHUTDOWNHOOK, true);

    private static final List<Task> tasks = new ArrayList<Task>();

    private static Thread hook;

    public static synchronized <T extends Task> T add(final T task) {
        checkNotNull(task);

        // If not enabled ignore
        if (!enabled) {
            Log.debug("Shutdown-hook is disabled; not installing: ", task);
            return task;
        }

        // Install the hook thread if needed
        if (hook == null) {
            hook = addHook(new Thread("JLine Shutdown Hook")
            {
                @Override
                public void run() {
                    runTasks();
                }
            });
        }

        // Track the task
        Log.debug("Adding shutdown-hook task: ", task);
        tasks.add(task);

        return task;
    }

    private static synchronized void runTasks() {
        Log.debug("Running all shutdown-hook tasks");

        // Iterate through copy of tasks list
        for (Task task : tasks.toArray(new Task[tasks.size()])) {
            Log.debug("Running task: ", task);
            try {
                task.run();
            }
            catch (Throwable e) {
                Log.warn("Task failed", e);
            }
        }

        tasks.clear();
    }

    private static Thread addHook(final Thread thread) {
        Log.debug("Registering shutdown-hook: ", thread);
        try {
            Runtime.getRuntime().addShutdownHook(thread);
        }
        catch (AbstractMethodError e) {
            // JDK 1.3+ only method. Bummer.
            Log.debug("Failed to register shutdown-hook", e);
        }
        return thread;
    }

    public static synchronized void remove(final Task task) {
        checkNotNull(task);

        // ignore if not enabled or hook never installed
        if (!enabled || hook == null) {
            return;
        }

        // Drop the task
        tasks.remove(task);

        // If there are no more tasks, then remove the hook thread
        if (tasks.isEmpty()) {
            removeHook(hook);
            hook = null;
        }
    }

    private static void removeHook(final Thread thread) {
        Log.debug("Removing shutdown-hook: ", thread);

        try {
            Runtime.getRuntime().removeShutdownHook(thread);
        }
        catch (AbstractMethodError e) {
            // JDK 1.3+ only method. Bummer.
            Log.debug("Failed to remove shutdown-hook", e);
        }
        catch (IllegalStateException e) {
            // The VM is shutting down, not a big deal; ignore
        }
    }

    /**
     * Essentially a {@link Runnable} which allows running to throw an exception.
     */
    public static interface Task
    {
        void run() throws Exception;
    }
}
