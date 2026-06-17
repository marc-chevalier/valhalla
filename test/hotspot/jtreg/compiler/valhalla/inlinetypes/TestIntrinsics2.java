/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.valhalla.inlinetypes;

import compiler.lib.ir_framework.*;
import jdk.internal.value.ValueClass;
import jdk.test.lib.Asserts;

import java.lang.reflect.Array;

import static compiler.valhalla.inlinetypes.InlineTypes.rI;

/*
 * @test
 * @key randomness
 * @summary Test intrinsic support for value classes.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   compiler.valhalla.inlinetypes.TestIntrinsics2 0
 */

public class TestIntrinsics2 {
    public static void main(String[] args) {
        Scenario[] scenarios = InlineTypes.DEFAULT_SCENARIOS;

        InlineTypes.getFramework()
                   .addScenarios(scenarios[Integer.parseInt(args[0])])
                   .addFlags("-Xbootclasspath/a:.", "-XX:+UnlockDiagnosticVMOptions", "-XX:+WhiteBoxAPI",
                             "-XX:CompileCommand=inline,jdk.internal.misc.Unsafe::*",
                             "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
                             "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED")
                   .addHelperClasses(MyValue1.class,
                                     MyValue2.class,
                                     MyValue2Inline.class)
                   .start();
    }

    @Test
    public void test53(Class<?> c1, int len) {
        MyValue1[][] va1 = (MyValue1[][])Array.newInstance(MyValue1[].class, len);
        Object[][] va3 = (Object[][])Array.newInstance(c1, len);
        for (int i = 0; i < len; ++i) {
            Asserts.assertEQ(va1[i], null);
            Asserts.assertEQ(va3[i], null);
            va1[i] = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
            va3[i] = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, MyValue1.DEFAULT);
            var va1i = va1[i];
            Asserts.assertNE(va1i, null);
        }
    }

    @Run(test = "test53")
    public void test53_verifier() {
        test53(MyValue1[].class, 7);
    }
}
