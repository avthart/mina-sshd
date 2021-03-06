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

import org.apache.sshd.common.util.OsUtils;

/**
 * A simplistic interactive shell factory
 *
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public class InteractiveProcessShellFactory extends ProcessShellFactory {

    public static final InteractiveProcessShellFactory INSTANCE;

    private static final String[] LINUX_COMMAND;
    private static final String[] WINDOWS_COMMAND;

    static {
        LINUX_COMMAND = new String[] {"/bin/sh", "-i", "-l"};
        WINDOWS_COMMAND = new String[] {"cmd.exe"};
        INSTANCE = new InteractiveProcessShellFactory();
    }

    public InteractiveProcessShellFactory() {
        super(resolveDefaultInteractiveCommand(), TtyOptions.resolveDefaultTtyOptions());
    }

    public static String[] resolveDefaultInteractiveCommand() {
        return resolveInteractiveCommand(OsUtils.isWin32());
    }

    public static String[] resolveInteractiveCommand(boolean isWin32) {
        // return clone(s) to avoid inadvertent modification
        if (isWin32) {
            return WINDOWS_COMMAND.clone();
        } else {
            return LINUX_COMMAND.clone();
        }
    }

}
