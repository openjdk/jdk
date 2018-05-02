/*
 * Copyright (c) 2006, 2018, Oracle and/or its affiliates. All rights reserved.
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
package nsk.share.jdi;

import java.io.*;
import java.util.*;

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import nsk.share.Consts;
import nsk.share.TestBug;

/*
 * Class contains debugger classes based on JDIEventsDebugger intended for JDI events testing
 */
public class EventTestTemplates {

    // how many times rerun test in case when tested events aren't generated
    static final int MAX_TEST_RUN_NUMBER = 10;

    /*
     * Method contains common code used by EventFilterTests and StressTestTemplate
     */
    static void runTestWithRerunPossibilty(JDIEventsDebugger debugger, Runnable testRunner) {
        for (int i = 0; i < MAX_TEST_RUN_NUMBER; i++) {
            testRunner.run();

            /*
             * If events weren't generated at all but test accepts missing events
             * try to rerun test (rerun test only if it didn't fail yet)
             */
            if (debugger.eventsNotGenerated() && debugger.getSuccess()) {
                if (i < MAX_TEST_RUN_NUMBER - 1) {
                    debugger.log.display("\nWARNING: tested events weren't generated at all, trying to rerun test (rerun attempt " + (i + 1) + ")\n");
                    continue;
                } else {
                    debugger.setSuccess(false);
                    debugger.log.complain("Tested events weren't generated after " + MAX_TEST_RUN_NUMBER + " runs, test FAILED");
                }
            } else
                break;
        }
    }

    /*
     * Debugger class for testing event filters, subclasses should parse command
     * line and call method JDIEventsDebugger.eventFilterTestTemplate with
     * required arguments.
     *
     * Class handles common for event filter tests parameters:
     * - debuggee class name (e.g. -debuggeeClassName nsk.share.jdi.MonitorEventsDebuggee)
     * - tested event type (e.g. -eventType MONITOR_CONTENTED_ENTER)
     */
    public static abstract class EventFilterTest extends JDIEventsDebugger {
        protected String debuggeeClassName;

        protected EventType eventType;

        protected String[] doInit(String[] args, PrintStream out) {
            args = super.doInit(args, out);

            ArrayList<String> standardArgs = new ArrayList<String>();

            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("-debuggeeClassName") && (i < args.length - 1)) {
                    debuggeeClassName = args[i + 1];
                    i++;
                } else if (args[i].equals("-eventType") && (i < args.length - 1)) {
                    try {
                        eventType = EventType.valueOf(args[i + 1]);
                    } catch (IllegalArgumentException e) {
                        TestBug testBug = new TestBug("Invalid event type : " + args[i + 1], e);
                        throw testBug;
                    }
                    i++;
                } else
                    standardArgs.add(args[i]);
            }

            if (eventType == null)
                throw new TestBug("Test requires 'eventType' parameter");

            if (debuggeeClassName == null)
                throw new TestBug("Test requires 'debuggeeClassName' parameter");

            return standardArgs.toArray(new String[] {});
        }

        abstract protected int getTestFiltersNumber();

        // This class is required to call runTestWithRerunPossibilty()
        class FilterTestRunner implements Runnable {
            private int filterIndex;

            FilterTestRunner(int filterIndex) {
                this.filterIndex = filterIndex;
            }

            public void run() {
                eventFilterTestTemplate(eventType, filterIndex);
            }
        }

        public final void doTest() {
            prepareDebuggee(new EventType[] {eventType});

            int filtersNumber = getTestFiltersNumber();

            if (filtersNumber <= 0)
                throw new TestBug("Invalid filtersNumber: " + filtersNumber);

            for (int filterIndex = 0; filterIndex < getTestFiltersNumber(); filterIndex++) {
                runTestWithRerunPossibilty(this, new FilterTestRunner(filterIndex));
            }

            eventHandler.stopEventHandler();
            removeDefaultBreakpoint();
        }

        // can't control events from system libraries, so save events only from nsk packages
        protected boolean shouldSaveEvent(Event event) {
            return isEventFromNSK(event);
        }

        protected String debuggeeClassName() {
            return debuggeeClassName;
        }
    }

    /*
     * Class for testing class exclusion filter, this filter is added by
     * request's method addClassExclusionFilter(String classPattern) Expected
     * command line parameter:
     * - class patterns which should be passed to adding filter method (e.g. -classPatterns java.*:*.Foo)
     */
    public static class ClassExclusionFilterTest extends EventFilterTest {
        protected String classPatterns[];

        public static void main(String argv[]) {
            System.exit(run(argv, System.out) + Consts.JCK_STATUS_BASE);
        }

        public static int run(String argv[], PrintStream out) {
            return new ClassExclusionFilterTest().runIt(argv, out);
        }

        protected String[] doInit(String[] args, PrintStream out) {
            args = super.doInit(args, out);

            ArrayList<String> standardArgs = new ArrayList<String>();

            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("-classPatterns") && (i < args.length - 1)) {
                    classPatterns = args[i + 1].split(":");
                    i++;
                } else
                    standardArgs.add(args[i]);
            }

            if (classPatterns == null || classPatterns.length == 0)
                throw new TestBug("Test requires at least one class name pattern");

            return standardArgs.toArray(new String[] {});
        }

        protected int getTestFiltersNumber() {
            return classPatterns.length;
        }

        protected EventFilters.DebugEventFilter[] createTestFilters(int testedFilterIndex) {
            if (testedFilterIndex < 0 || testedFilterIndex >= classPatterns.length)
                throw new TestBug("Invalid testedFilterIndex: " + testedFilterIndex);

            return new EventFilters.DebugEventFilter[]{new EventFilters.ClassExclusionFilter(classPatterns[testedFilterIndex])};
        }
    }

    /*
     * Subclass of ClassExclusionFilterTest, create ClassFilter instead of
     * ClassExclusionFilter, this filter is added by request's method
     * addClassFilter(String classPattern)
     */
    public static class ClassFilterTest_ClassName extends ClassExclusionFilterTest {
        public static void main(String argv[]) {
            System.exit(run(argv, System.out) + Consts.JCK_STATUS_BASE);
        }

        public static int run(String argv[], PrintStream out) {
            return new ClassFilterTest_ClassName().runIt(argv, out);
        }

        protected EventFilters.DebugEventFilter[] createTestFilters(int testedFilterIndex) {
            if (testedFilterIndex < 0 || testedFilterIndex >= classPatterns.length)
                throw new TestBug("Invalid testedFilterIndex: " + testedFilterIndex);

            return new EventFilters.DebugEventFilter[]{new EventFilters.ClassFilter(classPatterns[testedFilterIndex])};
        }
    }

    /*
     * Subclass of ClassExclusionFilterTest, create ClassReferenceFilter instead
     * of ClassExclusionFilter, this filter is added by request's method
     * addClassFilter(ReferenceType referenceType)
     */
    public static class ClassFilterTest_ReferenceType extends ClassExclusionFilterTest {
        public static void main(String argv[]) {
            System.exit(run(argv, System.out) + Consts.JCK_STATUS_BASE);
        }

        public static int run(String argv[], PrintStream out) {
            return new ClassFilterTest_ReferenceType().runIt(argv, out);
        }

        protected int getTestFiltersNumber() {
            return classPatterns.length;
        }

        protected EventFilters.DebugEventFilter[] createTestFilters(int testedFilterIndex) {
            if (testedFilterIndex < 0 || testedFilterIndex >= classPatterns.length)
                throw new TestBug("Invalid testedFilterIndex: " + testedFilterIndex);

            ReferenceType referenceType = debuggee.classByName(classPatterns[testedFilterIndex]);
            if (referenceType == null)
                throw new TestBug("Invalid class name is passed: " + classPatterns[testedFilterIndex]);

            return new EventFilters.DebugEventFilter[]{new EventFilters.ClassReferenceFilter(referenceType)};
        }
    }

    /*
     * Class for testing instance filter, this filter is added by request's
     * method addInstanceFilter(ObjectReference instance). Class tests 3 following
     * cases:
     * - add filter for single object
     * - add filter for the same object 2 times, expect behavior such as in previous case
     * - add filter for 2 different objects, so events shouldn't be received
     */
    public static class InstanceFilterTest extends EventFilterTest {
        public static void main(String argv[]) {
            System.exit(run(argv, System.out) + Consts.JCK_STATUS_BASE);
        }

        public static int run(String argv[], PrintStream out) {
            return new InstanceFilterTest().runIt(argv, out);
        }

        protected int getTestFiltersNumber() {
            return 3;
        }

        protected EventFilters.DebugEventFilter[] createTestFilters(int testedFilterIndex) {
            List<ObjectReference> objects = getEventObjects();

            if (objects.size() < 2) {
                throw new TestBug("Debuggee didn't create event generating objects");
            }

            switch (testedFilterIndex) {
            case 0:
                // filter for 1 object
                return new EventFilters.DebugEventFilter[]{new EventFilters.ObjectReferenceFilter(objects.get(0))};
            case 1:
                // add 2 filters for the same object
                return new EventFilters.DebugEventFilter[]{new EventFilters.ObjectReferenceFilter(objects.get(0)),
                        new EventFilters.ObjectReferenceFilter(objects.get(0))};
            case 2:
                // add 2 filters for 2 different objects, so don't expect any event
                return new EventFilters.DebugEventFilter[]{new EventFilters.ObjectReferenceFilter(objects.get(0)),
                        new EventFilters.ObjectReferenceFilter(objects.get(1))};
            default:
                throw new TestBug("Invalid testedFilterIndex: " + testedFilterIndex);
            }
        }
    }

    /*
     * Class for testing thread filter, this filter is added by request's method
     * addThreadFilter(ThreadReference thread). Class test 3 follows cases:
     * - add filter for single thread
     * - add filter for the same thread 2 times, expect behavior such as in previous case
     * - add filter for 2 different threads, so events shouldn't be received
     */
    public static class ThreadFilterTest extends EventFilterTest {
        public static void main(String argv[]) {
            System.exit(run(argv, System.out) + Consts.JCK_STATUS_BASE);
        }

        public static int run(String argv[], PrintStream out) {
            return new ThreadFilterTest().runIt(argv, out);
        }

        protected int getTestFiltersNumber() {
            return 3;
        }

        protected EventFilters.DebugEventFilter[] createTestFilters(int testedFilterIndex) {
            List<ThreadReference> threads = getEventThreads();

            if (threads.size() < 2) {
                throw new TestBug("Debuggee didn't create event generating threads");
            }

            switch (testedFilterIndex) {
            case 0:
                // filter for 1 thread
                return new EventFilters.DebugEventFilter[]{new EventFilters.ThreadFilter(threads.get(0))};
            case 1:
                // add 2 filters for the same thread
                return new EventFilters.DebugEventFilter[]{new EventFilters.ThreadFilter(threads.get(0)),
                        new EventFilters.ThreadFilter(threads.get(0))};
            case 2:
                // add 2 filters for 2 different threads, so don't expect any event
                return new EventFilters.DebugEventFilter[]{new EventFilters.ThreadFilter(threads.get(0)),
                        new EventFilters.ThreadFilter(threads.get(1))};
            default:
                throw new TestBug("Invalid testedFilterIndex: " + testedFilterIndex);
            }
        }
    }

    /*
     * Class is intended for stress testing, expected command line parameters:
     * - debuggee class name (e.g.: -debuggeeClassName nsk.share.jdi.MonitorEventsDebuggee)
     * - one or more tested event types (e.g.: -eventTypes MONITOR_CONTENTED_ENTER:MONITOR_CONTENTED_ENTERED)
     * - number of events which should be generated during test execution (e.g.: -eventsNumber 500)
     * - number of threads which simultaneously generate events (e.g.: -threadsNumber 10)
     *
     * Class parses command line and calls method JDIEventsDebugger.stressTestTemplate.
     */
    public static class StressTestTemplate extends JDIEventsDebugger {
        protected String debuggeeClassName;

        protected EventType testedEventTypes[];

        protected int eventsNumber = 1;

        protected int threadsNumber = 1;

        public static void main(String argv[]) {
            System.exit(run(argv, System.out) + Consts.JCK_STATUS_BASE);
        }

        public static int run(String argv[], PrintStream out) {
            return new StressTestTemplate().runIt(argv, out);
        }

        protected String[] doInit(String[] args, PrintStream out) {
            args = super.doInit(args, out);

            ArrayList<String> standardArgs = new ArrayList<String>();

            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("-eventsNumber") && (i < args.length - 1)) {
                    eventsNumber = Integer.parseInt(args[i + 1]);

                    // for this stress test events number is equivalent of iterations
                    // (don't support 0 value of IterationsFactor)
                    if (stressOptions.getIterationsFactor() != 0)
                        eventsNumber *= stressOptions.getIterationsFactor();

                    i++;
                } else if (args[i].equals("-threadsNumber") && (i < args.length - 1)) {
                    threadsNumber = Integer.parseInt(args[i + 1]);

                    // if 'threadsNumber' is specified test should take in account stress options
                    threadsNumber *= stressOptions.getThreadsFactor();

                    i++;
                } else if (args[i].equals("-debuggeeClassName") && (i < args.length - 1)) {
                    debuggeeClassName = args[i + 1];
                    i++;
                } else if (args[i].equals("-eventTypes") && (i < args.length - 1)) {
                    String eventTypesNames[] = args[i + 1].split(":");
                    testedEventTypes = new EventType[eventTypesNames.length];
                    try {
                        for (int j = 0; j < eventTypesNames.length; j++)
                            testedEventTypes[j] = EventType.valueOf(eventTypesNames[j]);
                    } catch (IllegalArgumentException e) {
                        throw new TestBug("Invalid event type", e);
                    }

                    i++;
                } else
                    standardArgs.add(args[i]);
            }

            if (testedEventTypes == null || testedEventTypes.length == 0)
                throw new TestBug("Test requires 'eventTypes' parameter");

            if (debuggeeClassName == null)
                throw new TestBug("Test requires 'debuggeeClassName' parameter");

            return standardArgs.toArray(new String[] {});
        }

        // can't control events from system libraries, so save events only from nsk packages
        protected boolean shouldSaveEvent(Event event) {
            return isEventFromNSK(event);
        }

        protected String debuggeeClassName() {
            return debuggeeClassName;
        }

        public void doTest() {
            prepareDebuggee(testedEventTypes);

            runTestWithRerunPossibilty(this, new Runnable() {
                public void run() {
                    stressTestTemplate(testedEventTypes, eventsNumber, threadsNumber);
                }
            });

            eventHandler.stopEventHandler();
            removeDefaultBreakpoint();
        }
    }

    static public boolean isEventFromNSK(Event event) {
        if (event instanceof MonitorContendedEnterEvent) {
            return ((MonitorContendedEnterEvent) event).location() != null && ((MonitorContendedEnterEvent) event).monitor().type().name().startsWith("nsk.");
        }
        if (event instanceof MonitorContendedEnteredEvent) {
            return ((MonitorContendedEnteredEvent) event).location() != null  && ((MonitorContendedEnteredEvent) event).monitor().type().name().startsWith("nsk.");
        }
        if (event instanceof MonitorWaitEvent) {
            return ((MonitorWaitEvent) event).monitor().type().name().startsWith("nsk.");
        }
        if (event instanceof MonitorWaitedEvent) {
            return ((MonitorWaitedEvent) event).monitor().type().name().startsWith("nsk.");
        }

        // don't filter other events
        return true;
    }

}
