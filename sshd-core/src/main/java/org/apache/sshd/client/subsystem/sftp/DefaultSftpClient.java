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
package org.apache.sshd.client.subsystem.sftp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.StreamCorruptedException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sshd.client.channel.ChannelSubsystem;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.FactoryManagerUtils;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.subsystem.sftp.SftpConstants;
import org.apache.sshd.common.subsystem.sftp.extensions.ParserUtils;
import org.apache.sshd.common.subsystem.sftp.extensions.VersionsParser.Versions;
import org.apache.sshd.common.util.GenericUtils;
import org.apache.sshd.common.util.ValidateUtils;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.buffer.BufferUtils;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;

import static org.apache.sshd.common.subsystem.sftp.SftpConstants.SFTP_V3;
import static org.apache.sshd.common.subsystem.sftp.SftpConstants.SFTP_V6;
import static org.apache.sshd.common.subsystem.sftp.SftpConstants.SSH_FXP_INIT;
import static org.apache.sshd.common.subsystem.sftp.SftpConstants.SSH_FXP_STATUS;
import static org.apache.sshd.common.subsystem.sftp.SftpConstants.SSH_FXP_VERSION;

/**
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public class DefaultSftpClient extends AbstractSftpClient {
    private final ClientSession clientSession;
    private final ChannelSubsystem channel;
    private final Map<Integer, Buffer> messages = new HashMap<>();
    private final AtomicInteger cmdId = new AtomicInteger(100);
    private final Buffer receiveBuffer = new ByteArrayBuffer();
    private final byte[] workBuf = new byte[Integer.SIZE / Byte.SIZE];  // TODO in JDK-8 use Integer.BYTES
    private boolean closing;
    private int version;
    private final Map<String, byte[]> extensions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, byte[]> exposedExtensions = Collections.unmodifiableMap(extensions);

    public DefaultSftpClient(ClientSession clientSession) throws IOException {
        this.clientSession = ValidateUtils.checkNotNull(clientSession, "No client session", GenericUtils.EMPTY_OBJECT_ARRAY);
        this.channel = clientSession.createSubsystemChannel(SftpConstants.SFTP_SUBSYSTEM_NAME);
        this.channel.setOut(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                write(new byte[]{(byte) b}, 0, 1);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                data(b, off, len);
            }
        });
        this.channel.setErr(new ByteArrayOutputStream(Byte.MAX_VALUE));
        this.channel.open().verify(FactoryManagerUtils.getLongProperty(clientSession, SFTP_CHANNEL_OPEN_TIMEOUT, DEFAULT_CHANNEL_OPEN_TIMEOUT));
        this.channel.onClose(new Runnable() {
            @SuppressWarnings("synthetic-access")
            @Override
            public void run() {
                synchronized (messages) {
                    closing = true;
                    messages.notifyAll();
                }
            }
        });
        init();
    }

    @Override
    public int getVersion() {
        return version;
    }

    @Override
    public ClientSession getClientSession() {
        return clientSession;
    }

    @Override
    public ClientChannel getClientChannel() {
        return channel;
    }

    @Override
    public Map<String, byte[]> getServerExtensions() {
        return exposedExtensions;
    }

    @Override
    public boolean isClosing() {
        return closing;
    }

    @Override
    public boolean isOpen() {
        return this.channel.isOpen();
    }

    @Override
    public void close() throws IOException {
        if (isOpen()) {
            this.channel.close(false);
        }
    }

    /**
     * Receive binary data
     * @param buf   The buffer for the incoming data
     * @param start Offset in buffer to place the data
     * @param len   Available space in buffer for the data
     * @return Actual size of received data
     * @throws IOException If failed to receive incoming data
     */
    protected int data(byte[] buf, int start, int len) throws IOException {
        Buffer incoming = new ByteArrayBuffer(buf, start, len);
        // If we already have partial data, we need to append it to the buffer and use it
        if (receiveBuffer.available() > 0) {
            receiveBuffer.putBuffer(incoming);
            incoming = receiveBuffer;
        }
        // Process commands
        int rpos = incoming.rpos();
        for (int count = 0; receive(incoming); count++) {
            if (log.isTraceEnabled()) {
                log.trace("Processed " + count + " data messages");
            }
        }

        int read = incoming.rpos() - rpos;
        // Compact and add remaining data
        receiveBuffer.compact();
        if (receiveBuffer != incoming && incoming.available() > 0) {
            receiveBuffer.putBuffer(incoming);
        }
        return read;
    }

    /**
     * Read SFTP packets from buffer
     *
     * @param incoming The received {@link Buffer}
     * @return {@code true} if data from incoming buffer was processed
     * @throws IOException if failed to process the buffer
     * @see #process(Buffer)
     */
    protected boolean receive(Buffer incoming) throws IOException {
        int rpos = incoming.rpos();
        int wpos = incoming.wpos();
        clientSession.resetIdleTimeout();
        if ((wpos - rpos) > 4) {
            int length = incoming.getInt();
            if (length < 5) {
                throw new IOException("Illegal sftp packet length: " + length);
            }
            if ((wpos - rpos) >= (length + 4)) {
                incoming.rpos(rpos);
                incoming.wpos(rpos + 4 + length);
                process(incoming);
                incoming.rpos(rpos + 4 + length);
                incoming.wpos(wpos);
                return true;
            }
        }
        incoming.rpos(rpos);
        return false;
    }

    /**
     * Process an SFTP packet
     *
     * @param incoming The received {@link Buffer}
     * @throws IOException if failed to process the buffer
     */
    protected void process(Buffer incoming) throws IOException {
        Buffer buffer = new ByteArrayBuffer(incoming.available());
        buffer.putBuffer(incoming);
        buffer.rpos(5);
        int id = buffer.getInt();
        buffer.rpos(0);
        synchronized (messages) {
            messages.put(id, buffer);
            messages.notifyAll();
        }
    }

    @Override
    public int send(int cmd, Buffer buffer) throws IOException {
        int id = cmdId.incrementAndGet();
        int len = buffer.available();
        if (log.isTraceEnabled()) {
            log.trace("send(cmd={}, len={}) id = {}",
                    cmd, len, id);
        }

        OutputStream dos = channel.getInvertedIn();
        BufferUtils.writeInt(dos, 1 /* cmd */ + (Integer.SIZE / Byte.SIZE) /* id */ + buffer.available(), workBuf);
        dos.write(cmd & 0xFF);
        BufferUtils.writeInt(dos, id, workBuf);
        dos.write(buffer.array(), buffer.rpos(), len);
        dos.flush();
        return id;
    }

    @Override
    public Buffer receive(int id) throws IOException {
        Integer reqId = id;
        synchronized (messages) {
            for (int count = 1;; count++) {
                if (closing) {
                    throw new SshException("Channel has been closed");
                }
                Buffer buffer = messages.remove(reqId);
                if (buffer != null) {
                    return buffer;
                }
                try {
                    messages.wait();
                } catch (InterruptedException e) {
                    throw (IOException) new InterruptedIOException("Interrupted while waiting for messages at iteration #" + count).initCause(e);
                }
            }
        }
    }

    protected Buffer read() throws IOException {
        InputStream dis = channel.getInvertedOut();
        int length = BufferUtils.readInt(dis, workBuf);
        // must have at least command + length
        // TODO in jdk-8 use Integer.BYTES
        if (length < (1 + (Integer.SIZE / Byte.SIZE))) {
            throw new IllegalArgumentException("Bad length: " + length);
        }

        // TODO in jdk-8 use Integer.BYTES
        Buffer buffer = new ByteArrayBuffer(length + (Integer.SIZE / Byte.SIZE));
        buffer.putInt(length);
        int nb = length;
        while (nb > 0) {
            int readLen = dis.read(buffer.array(), buffer.wpos(), nb);
            if (readLen < 0) {
                throw new IllegalArgumentException("Premature EOF while read " + length + " bytes - remaining=" + nb);
            }
            buffer.wpos(buffer.wpos() + readLen);
            nb -= readLen;
        }

        return buffer;
    }

    protected void init() throws IOException {
        // Init packet
        OutputStream dos = channel.getInvertedIn();
        BufferUtils.writeInt(dos, 5 /* total length */, workBuf);
        dos.write(SSH_FXP_INIT);
        BufferUtils.writeInt(dos, SFTP_V6, workBuf);
        dos.flush();

        Buffer buffer;
        synchronized (messages) {
            while (messages.isEmpty()) {
                try {
                    messages.wait();
                } catch (InterruptedException e) {
                    throw (IOException) new InterruptedIOException("Interruppted init()").initCause(e);
                }
            }
            buffer = messages.remove(messages.keySet().iterator().next());

        }

        int length = buffer.getInt();
        int type = buffer.getUByte();
        int id = buffer.getInt();
        if (type == SSH_FXP_VERSION) {
            if (id < SFTP_V3) {
                throw new SshException("Unsupported sftp version " + id);
            }
            version = id;

            while (buffer.available() > 0) {
                String name = buffer.getString();
                byte[] data = buffer.getBytes();
                extensions.put(name, data);
            }
        } else if (type == SSH_FXP_STATUS) {
            int substatus = buffer.getInt();
            String msg = buffer.getString();
            String lang = buffer.getString();
            if (log.isTraceEnabled()) {
                log.trace("init(id={}) - status: {} [{}] {}", Integer.valueOf(id), Integer.valueOf(substatus), lang, msg);
            }

            throwStatusException(id, substatus, msg, lang);
        } else {
            throw new SshException("Unexpected SFTP packet received: type=" + type + ", id=" + id + ", length=" + length);
        }
    }

    /**
     * @param selector The {@link SftpVersionSelector} to use
     * @return The selected version (may be same as current)
     * @throws IOException If failed to negotiate
     */
    public int negotiateVersion(SftpVersionSelector selector) throws IOException {
        int current = getVersion();
        Set<Integer> available = GenericUtils.asSortedSet(Collections.singleton(current));
        Map<String, ?> parsed = getParsedServerExtensions();
        Collection<String> extensions = ParserUtils.supportedExtensions(parsed);
        if ((GenericUtils.size(extensions) > 0) && extensions.contains(SftpConstants.EXT_VERSION_SELECT)) {
            Versions vers = GenericUtils.isEmpty(parsed) ? null : (Versions) parsed.get(SftpConstants.EXT_VERSIONS);
            Collection<String> reported = (vers == null) ? null : vers.versions;
            if (GenericUtils.size(reported) > 0) {
                for (String v : reported) {
                    if (!available.add(Integer.valueOf(v))) {
                        continue;
                    }
                }
            }
        }

        int selected = selector.selectVersion(current, new ArrayList<>(available));
        if (log.isDebugEnabled()) {
            log.debug("negotiateVersion({}) {} -> {}", current, available, selected);
        }

        if (selected == current) {
            return current;
        }

        if (!available.contains(Integer.valueOf(selected))) {
            throw new StreamCorruptedException("Selected version (" + selected + ") not part of available: " + available);
        }

        String verVal = String.valueOf(selected);
        Buffer buffer = new ByteArrayBuffer((Integer.SIZE / Byte.SIZE) + SftpConstants.EXT_VERSION_SELECT.length()     // extension name
                + (Integer.SIZE / Byte.SIZE) + verVal.length());
        buffer.putString(SftpConstants.EXT_VERSION_SELECT);
        buffer.putString(verVal);
        checkStatus(SftpConstants.SSH_FXP_EXTENDED, buffer);
        version = selected;
        return selected;
    }
}
