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

package compiler.valhalla.inlinetypes;

import jdk.internal.vm.annotation.NullRestricted;
import jdk.internal.vm.annotation.Strict;
import jdk.test.lib.Asserts;

import static compiler.valhalla.inlinetypes.InlineTypes.*;

/*
 * @test
 * @bug 8367156
 * @summary On Aarch64, when the frame is too big and we need to repair it after
 *          scalarization of the arguments, we cannot use ldp to get the stack
 *          increment and rfp as is only has a 7 bit offset. Use two ldr with 9-bit
 *          offsets instead.
 * @library /test/lib /test/jdk/java/lang/invoke/common /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.misc
 * @run main/othervm
 *      -Xcomp
 *      -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.RepairStackWithBigFrame::test2
 *      -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:-UseACmpProfile
 *      -XX:+AlwaysIncrementalInline
 *      -XX:FlatArrayElementMaxOops=5
 *      -XX:+UseArrayFlattening
 *      -XX:-UseArrayLoadStoreProfile
 *      -XX:+UseFieldFlattening
 *      -XX:+InlineTypePassFieldsAsArgs
 *      -XX:+InlineTypeReturnedAsFields
 *      compiler.valhalla.inlinetypes.RepairStackWithBigFrame
 */

public class RepairStackWithBigFrame {
    public static void main(String[] args) {
        new RepairStackWithBigFrame().test1();
    }

    static {
        // Make sure RuntimeException is loaded to prevent uncommon traps in IR verified tests
        // RuntimeException tmp = new RuntimeException("42");
        // NullPointerException _1 = new NullPointerException("");
    }

    // Helper methods

    @Strict
    @NullRestricted
    private static final MyValue1 testValue1 = MyValue1.createWithFieldsInline(rI, rL);

    @Strict
    @NullRestricted
    MyValue1 valueField1 = testValue1;


    public long test2(MyValue1 arg) {
        MyAbstract vt1 = MyValue1.createWithFieldsInline(rI, rL);
        MyAbstract vt2 = MyValue1.createWithFieldsDontInline(rI, rL);
        MyAbstract vt3 = arg;
        MyAbstract vt4 = valueField1;
        return ((MyValue1)vt1).hash() + ((MyValue1)vt2).hash() +
               ((MyValue1)vt3).hash() + ((MyValue1)vt4).hash();
    }

    public void test1() {
        long result = test2(valueField1);
        Asserts.assertEQ(result, 4*testValue1.hash());
    }
}
