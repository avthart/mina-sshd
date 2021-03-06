/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sshd.common.random;

import org.apache.sshd.common.util.SecurityUtils;
import org.apache.sshd.util.test.BaseTestSupport;
import org.apache.sshd.util.test.Utils;
import org.junit.Assume;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * TODO Add javadoc
 *
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RandomTest extends BaseTestSupport {
    public RandomTest() {
        super();
    }

    @Test
    public void testJce() {
        long t = testRandom(new JceRandom());
        System.out.println("JCE: " + t + " micro");
    }

    @Test
    public void testBc() {
        Assume.assumeTrue("BouncyCastle not registered", SecurityUtils.isBouncyCastleRegistered());
        long t = testRandom(Utils.getRandomizerInstance());
        System.out.println("BC:  " + t + " micro");
    }

    private static long testRandom(Random random) {
        byte[] bytes = new byte[32];
        long l0 = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            random.fill(bytes, 8, 16);
        }
        long l1 = System.nanoTime();
        return (l1 - l0) / 1000;
    }
}
