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
package org.apache.sshd.client.scp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.common.file.FileSystemFactory;
import org.apache.sshd.common.file.util.MockFileSystem;
import org.apache.sshd.common.file.util.MockPath;
import org.apache.sshd.common.scp.ScpHelper;
import org.apache.sshd.common.scp.ScpTimestamp;
import org.apache.sshd.common.scp.ScpTransferEventListener;
import org.apache.sshd.common.util.ValidateUtils;

/**
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public class DefaultScpClient extends AbstractScpClient {

    private final ClientSession clientSession;
    private final ScpTransferEventListener listener;

    public DefaultScpClient(ClientSession clientSession) {
        this(clientSession, ScpTransferEventListener.EMPTY);
    }

    public DefaultScpClient(ClientSession clientSession, ScpTransferEventListener eventListener) {
        this.clientSession = ValidateUtils.checkNotNull(clientSession, "No client session");
        this.listener = (eventListener == null) ? ScpTransferEventListener.EMPTY : eventListener;
    }

    @Override
    public ClientSession getClientSession() {
        return clientSession;
    }

    @Override
    public void download(String remote, OutputStream local) throws IOException {
        String cmd = createReceiveCommand(remote, Collections.<Option>emptyList());
        ChannelExec channel = openCommandChannel(getClientSession(), cmd);
        try {
            // NOTE: we use a mock file system since we expect no invocations for it
            ScpHelper helper = new ScpHelper(channel.getInvertedOut(), channel.getInvertedIn(), new MockFileSystem(remote), listener);
            helper.receiveFileStream(local, ScpHelper.DEFAULT_RECEIVE_BUFFER_SIZE);
        } finally {
            channel.close(false);
        }
    }

    @Override
    protected void download(String remote, FileSystem fs, Path local, Collection<Option> options) throws IOException {
        String cmd = createReceiveCommand(remote, options);
        ChannelExec channel = openCommandChannel(getClientSession(), cmd);
        try {
            ScpHelper helper = new ScpHelper(channel.getInvertedOut(), channel.getInvertedIn(), fs, listener);
            helper.receive(local,
                    options.contains(Option.Recursive),
                    options.contains(Option.TargetIsDirectory),
                    options.contains(Option.PreserveAttributes),
                    ScpHelper.DEFAULT_RECEIVE_BUFFER_SIZE);
        } finally {
            channel.close(false);
        }
    }

    @Override
    public void upload(final InputStream local, final String remote, final long size, final Collection<PosixFilePermission> perms, final ScpTimestamp time) throws IOException {
        int namePos = ValidateUtils.checkNotNullAndNotEmpty(remote, "No remote location specified").lastIndexOf('/');
        final String name = (namePos < 0)
                ? remote
                : ValidateUtils.checkNotNullAndNotEmpty(remote.substring(namePos + 1), "No name value in remote=%s", remote);
        final String cmd = createSendCommand(remote, (time != null) ? EnumSet.of(Option.PreserveAttributes) : Collections.<Option>emptySet());
        ChannelExec channel = openCommandChannel(clientSession, cmd);
        try {
            ScpHelper helper = new ScpHelper(channel.getInvertedOut(), channel.getInvertedIn(), new MockFileSystem(remote), listener);
            final Path mockPath = new MockPath(remote);
            helper.sendStream(new DefaultScpStreamResolver(name, mockPath, perms, time, size, local, cmd),
                              time != null, ScpHelper.DEFAULT_SEND_BUFFER_SIZE);
        } finally {
            channel.close(false);
        }
    }

    @Override
    protected <T> void runUpload(String remote, Collection<Option> options, Collection<T> local, AbstractScpClient.ScpOperationExecutor<T> executor) throws IOException {
        local = ValidateUtils.checkNotNullAndNotEmpty(local, "Invalid argument local: %s", local);
        remote = ValidateUtils.checkNotNullAndNotEmpty(remote, "Invalid argument remote: %s", remote);
        if (local.size() > 1) {
            options = addTargetIsDirectory(options);
        }

        String cmd = createSendCommand(remote, options);
        ClientSession session = getClientSession();
        ChannelExec channel = openCommandChannel(session, cmd);
        try {
            FactoryManager manager = session.getFactoryManager();
            FileSystemFactory factory = manager.getFileSystemFactory();
            FileSystem fs = factory.createFileSystem(session);
            try {
                ScpHelper helper = new ScpHelper(channel.getInvertedOut(), channel.getInvertedIn(), fs, listener);
                executor.execute(helper, local, options);
            } finally {
                try {
                    fs.close();
                } catch (UnsupportedOperationException e) {
                    // Ignore
                }
            }
        } finally {
            channel.close(false);
        }
    }
}
