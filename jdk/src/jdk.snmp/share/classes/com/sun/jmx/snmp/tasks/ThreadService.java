/*
 * Copyright (c) 2002, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.jmx.snmp.tasks;

import java.util.ArrayList;
import com.sun.jmx.snmp.tasks.Task;
import com.sun.jmx.snmp.tasks.TaskServer;

/**
 * This class implements a {@link com.sun.jmx.snmp.tasks.TaskServer} over
 * a thread pool.
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 **/
public class ThreadService implements TaskServer {

    public ThreadService(int threadNumber) {
        if (threadNumber <= 0) {
            throw new IllegalArgumentException("The thread number should bigger than zero.");
        }

        minThreads = threadNumber;
        threadList = new ExecutorThread[threadNumber];

        priority = Thread.currentThread().getPriority();
        cloader = Thread.currentThread().getContextClassLoader();

    }

// public methods
// --------------

    /**
     * Submit a task to be executed.
     * Once a task is submitted, it is guaranteed that either
     * {@link com.sun.jmx.snmp.tasks.Task#run() task.run()} or
     * {@link com.sun.jmx.snmp.tasks.Task#cancel() task.cancel()} will be called.
     * This implementation of TaskServer uses a thread pool to execute
     * the submitted tasks.
     * @param task The task to be executed.
     * @exception IllegalArgumentException if the submitted task is null.
     **/
    public void submitTask(Task task) throws IllegalArgumentException {
        submitTask((Runnable)task);
    }

    /**
     * Submit a task to be executed.
     * This implementation of TaskServer uses a thread pool to execute
     * the submitted tasks.
     * @param task The task to be executed.
     * @exception IllegalArgumentException if the submitted task is null.
     **/
    public void submitTask(Runnable task) throws IllegalArgumentException {
        stateCheck();

        if (task == null) {
            throw new IllegalArgumentException("No task specified.");
        }

        synchronized(jobList) {
            jobList.add(jobList.size(), task);

            jobList.notify();
        }

        createThread();
    }

    public Runnable removeTask(Runnable task) {
        stateCheck();

        Runnable removed = null;
        synchronized(jobList) {
            int lg = jobList.indexOf(task);
            if (lg >= 0) {
                removed = jobList.remove(lg);
            }
        }
        if (removed != null && removed instanceof Task)
            ((Task) removed).cancel();
        return removed;
    }

    public void removeAll() {
        stateCheck();

        final Object[] jobs;
        synchronized(jobList) {
            jobs = jobList.toArray();
            jobList.clear();
        }
        final int len = jobs.length;
        for (int i=0; i<len ; i++) {
            final Object o = jobs[i];
            if (o!= null && o instanceof Task) ((Task)o).cancel();
        }
    }

    // to terminate
    public void terminate() {

        if (terminated == true) {
            return;
        }

        terminated = true;

        synchronized(jobList) {
            jobList.notifyAll();
        }

        removeAll();

        for (int i=0; i<currThreds; i++) {
            try {
                threadList[i].interrupt();
            } catch (Exception e) {
                // TODO
            }
        }

        threadList = null;
    }

// private classes
// ---------------

    // A thread used to execute jobs
    //
    private class ExecutorThread extends Thread {
        public ExecutorThread() {
            super(threadGroup, "ThreadService-"+counter++);
            setDaemon(true);

            // init
            this.setPriority(priority);
            this.setContextClassLoader(cloader);

            idle++;
        }

        public void run() {

            while(!terminated) {
                Runnable job = null;

                synchronized(jobList) {
                    if (jobList.size() > 0) {
                        job = jobList.remove(0);
                        if (jobList.size() > 0) {
                            jobList.notify();
                        }

                    } else {
                        try {
                            jobList.wait();
                        } catch (InterruptedException ie) {
                            // terminated ?
                        } finally {
                        }
                        continue;
                    }
                }
                if (job != null) {
                    try {
                        idle--;
                        job.run();
                    } catch (Exception e) {
                        // TODO
                        e.printStackTrace();
                    } finally {
                        idle++;
                    }
                }

                // re-init
                this.setPriority(priority);
                Thread.interrupted();
                this.setContextClassLoader(cloader);
            }
        }
    }

// private methods
    private void stateCheck() throws IllegalStateException {
        if (terminated) {
            throw new IllegalStateException("The thread service has been terminated.");
        }
    }

    private void createThread() {
        if (idle < 1) {
            synchronized(threadList) {
                if (jobList.size() > 0 && currThreds < minThreads) {
                    ExecutorThread et = new ExecutorThread();
                    et.start();
                    threadList[currThreds++] = et;
                }
            }
        }
    }


// protected or private variables
// ------------------------------
    private ArrayList<Runnable> jobList = new ArrayList<Runnable>(0);

    private ExecutorThread[] threadList;
    private int minThreads = 1;
    private int currThreds = 0;
    private int idle = 0;

    private boolean terminated = false;
    private int priority;
    private ThreadGroup threadGroup = new ThreadGroup("ThreadService");
    private ClassLoader cloader;

    private static long counter = 0;

    private int addedJobs = 1;
    private int doneJobs = 1;
}
