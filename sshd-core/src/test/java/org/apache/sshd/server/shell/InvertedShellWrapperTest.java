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
package org.apache.sshd.server.shell;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.sshd.util.test.BaseTestSupport;
import org.apache.sshd.util.test.BogusEnvironment;
import org.apache.sshd.util.test.BogusExitCallback;
import org.apache.sshd.util.test.BogusInvertedShell;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class InvertedShellWrapperTest extends BaseTestSupport {

    @Test
    public void testStreamsAreFlushedBeforeClosing() throws Exception {
        BogusInvertedShell shell = newShell("out", "err");
        shell.setAlive(false);
        try (ByteArrayInputStream in = new ByteArrayInputStream("in".getBytes(StandardCharsets.UTF_8));
             ByteArrayOutputStream out = new ByteArrayOutputStream(50);
             ByteArrayOutputStream err = new ByteArrayOutputStream()) {

            InvertedShellWrapper wrapper = new InvertedShellWrapper(shell);
            wrapper.setInputStream(in);
            wrapper.setOutputStream(out);
            wrapper.setErrorStream(err);
            wrapper.setExitCallback(new BogusExitCallback());
            wrapper.start(new BogusEnvironment());

            wrapper.pumpStreams();

            // check the streams were flushed before exiting
            assertEquals("in", shell.getInputStream().toString());
            assertEquals("out", out.toString());
            assertEquals("err", err.toString());
        }
    }

    private BogusInvertedShell newShell(String contentOut, String contentErr) {
        ByteArrayOutputStream in = new ByteArrayOutputStream(20);
        ByteArrayInputStream out = new ByteArrayInputStream(contentOut.getBytes(StandardCharsets.UTF_8));
        ByteArrayInputStream err = new ByteArrayInputStream(contentErr.getBytes(StandardCharsets.UTF_8));
        return new BogusInvertedShell(in, out, err);
    }
}
