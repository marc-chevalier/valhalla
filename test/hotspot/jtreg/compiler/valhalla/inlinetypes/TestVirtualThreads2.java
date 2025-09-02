/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @key randomness
 * @summary Test that Virtual Threads work well with Value Objects.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xbatch -XX:CompileCommand=dontinline,*::* -XX:CompileCommand=compileonly,TestVirtualThreads2*::*
 *                   TestVirtualThreads2 111111011 101111111
 **/

import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;

import jdk.test.whitebox.WhiteBox;

public class TestVirtualThreads2 {
    static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    static final int COMP_LEVEL_SIMPLE = 1; // C1
    static final int COMP_LEVEL_FULL_OPTIMIZATION = 4; // C2 or JVMCI

    static value class SmallValue {
        int x1;
        int x2;

        public SmallValue(int i) {
            this.x1 = i;
            this.x2 = i;
        }

        public String toString() {
            return "x1 = " + x1 + ", x2 = " + x2;
        }
    }

    // Large value class with oops (and different number of fields) that requires stack extension/repair
    static value class LargeValueWithOops {
        Object x1;
        Object x2;
        Object x3;
        Object x4;
        Object x5;

        public LargeValueWithOops(Object obj) {
            this.x1 = obj;
            this.x2 = obj;
            this.x3 = obj;
            this.x4 = obj;
            this.x5 = obj;
        }

        public String toString() {
            return "x1 = " + x1 + ", x2 = " + x2 + ", x3 = " + x3 + ", x4 = " + x4 + ", x5 = " + x5;
        }

        public void verify(String loc, Object obj) {
            if (x1 != obj || x2 != obj || x3 != obj || x4 != obj || x5 != obj) {
                throw new RuntimeException("Incorrect result at " + loc + " for obj = " + obj + ": " + this);
            }
        }

        public static void verify(LargeValueWithOops val, String loc, Object obj, boolean useNull) {
            val.verify(loc, obj);
        }
    }

    public static void dontInline() { }

    public static LargeValueWithOops testLargeValueWithOops(LargeValueWithOops val) {
        dontInline(); // Prevent C2 from optimizing out below checks
        return val;
    }

    public static LargeValueWithOops testLargeValueWithOopsHelper(Object obj) {
        LargeValueWithOops val = new LargeValueWithOops(obj);
        val = testLargeValueWithOops(val);
        LargeValueWithOops.verify(val, "helper", obj, false);
        return val;
    }

    static class GarbageProducerThread extends Thread {
        public void run() {
            for (;;) {
                // Produce some garbage and then let the GC do its work
                Object[] arrays = new Object[1024];
                for (int i = 0; i < arrays.length; i++) {
                    arrays[i] = new int[1024];
                }
                System.gc();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // Sometimes, exclude some methods from compilation with C1 and/or C2 to stress test the calling convention
        if (true) {
        // if (true || Utils.getRandomInstance().nextBoolean()) {
            ArrayList<Method> methods = new ArrayList<Method>();
            Collections.addAll(methods, SmallValue.class.getDeclaredMethods());
            Collections.addAll(methods, LargeValueWithOops.class.getDeclaredMethods());
            Collections.addAll(methods, TestVirtualThreads2.class.getDeclaredMethods());
            System.out.println(args[0]);
            System.out.println(args[1]);
            System.out.println("Methods:");
            int i = 0;
            for (Method m : methods) {
                System.out.println(i + ": " + m);
                i++;
            }
            System.out.println("Accepting for C1 compilation:");
            i = 0;
            for (Method m : methods) {
                if (args[0].charAt(i) == '1') {
                // if (Utils.getRandomInstance().nextBoolean()) {
                    //System.out.println(i + ": " + m);
                    WHITE_BOX.makeMethodNotCompilable(m, COMP_LEVEL_SIMPLE, false);
                } else {
                    System.out.println(i + ": " + m);
                }
                i++;
            }
            System.out.println("Accepting for C2 compilation:");
            i = 0;
            for (Method m : methods) {
                if (args[1].charAt(i) == '1') {
                // if (Utils.getRandomInstance().nextBoolean()) {
                    // System.out.println(i + ": " + m);
                    WHITE_BOX.makeMethodNotCompilable(m, COMP_LEVEL_FULL_OPTIMIZATION, false);
                } else {
                    System.out.println(i + ": " + m);
                }
                i++;
            }
        }

        // Start another thread that does some allocations and calls System.gc()
        // to trigger GCs while virtual threads are parked.
        Thread garbage_producer = new GarbageProducerThread();
        garbage_producer.setDaemon(true);
        garbage_producer.start();

        CountDownLatch cdl = new CountDownLatch(1);
        Thread.ofPlatform().start(() -> {
            try {
                // Trigger compilation
                for (int i = 0; i < 500_000; i++) {
                    Object val = new SmallValue(i);
                    var v = testLargeValueWithOopsHelper(val);
                    LargeValueWithOops.verify(v, "return", val, false);
                }
                cdl.countDown();
            } catch (Exception e) {
                System.out.println("Exception thrown: " + e);
                e.printStackTrace(System.out);
                System.exit(1);
            }
        });
        cdl.await();
    }
}
