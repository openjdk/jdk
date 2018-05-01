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
package vm.share.gc;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import metaspace.stressHierarchy.common.exceptions.GotWrongOOMEException;
import nsk.share.gc.gp.classload.GeneratedClassProducer;
import nsk.share.test.ExecutionController;

public class TriggerUnloadingByFillingMetaspace implements
        TriggerUnloadingHelper {

    private static final int NUMBER_OF_THREADS = 30;

    private static class FillMetaspace {
        private volatile boolean gotOOME = false;
        private ExecutionController stresser;
        private GeneratedClassProducer generatedClassProducer = new GeneratedClassProducer("metaspace.stressHierarchy.common.HumongousClass");

        public FillMetaspace(ExecutionController stresser) { this.stresser = stresser; }

        private class FillMetaspaceTask implements Callable<Object> {
            @Override
            public Object call() throws Exception {
                while (stresser.continueExecution() && ! gotOOME) {
                    try {
                        generatedClassProducer.create(-100500); //argument is not used.
                    } catch (OutOfMemoryError oome) {
                        if (!isInMetaspace(oome)) {
                            throw new GotWrongOOMEException("Got OOME in heap while gaining OOME in metaspace. Test result can't be valid.");
                        }
                        gotOOME = true;
                    }
                }
                return null;
            }
        }
    }

    private static boolean isInMetaspace(OutOfMemoryError error) {
        return error.getMessage().trim().toLowerCase().contains("metadata");
    }

    @Override
    public void triggerUnloading(ExecutionController stresser) {
        try {
            FillMetaspace fillMetaspace = new FillMetaspace(stresser);
            ArrayList<Callable<Object>> tasks = new ArrayList<Callable<Object>>(NUMBER_OF_THREADS);
            for (int i = 0; i < NUMBER_OF_THREADS; i++) {
                tasks.add(fillMetaspace.new FillMetaspaceTask());
            }
            ExecutorService executorService = Executors.newCachedThreadPool();
            try {
                executorService.invokeAll(tasks);
            } catch (InterruptedException e) {
                System.out.println("Process of gaining OOME in metaspace was interrupted.");
                e.printStackTrace();
            }
        } catch (OutOfMemoryError e) {
            if (!isInMetaspace(e)) {
                throw new GotWrongOOMEException("Got OOME in heap while gaining OOME in metaspace. Test result can't be valid.");
            }
            return;
        }
    }

}
