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
package org.apache.sshd.server;

import java.util.List;

import org.apache.sshd.common.Factory;
import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.keyprovider.KeyPairProviderHolder;
import org.apache.sshd.server.auth.UserAuth;
import org.apache.sshd.server.auth.gss.GSSAuthenticator;
import org.apache.sshd.server.auth.keyboard.KeyboardInteractiveAuthenticator;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;

/**
 * The <code>ServerFactoryManager</code> enable the retrieval of additional
 * configuration needed specifically for the server side.
 *
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public interface ServerFactoryManager extends FactoryManager, KeyPairProviderHolder {
    /**
     * Key used to retrieve the value of the maximum concurrent open session count per username
     */
    String MAX_CONCURRENT_SESSIONS = "max-concurrent-sessions";
    /**
     * Key used to retrieve the value of the server identification string if not default.
     */
    String SERVER_IDENTIFICATION = "server-identification";
    /**
     * Key used to retrieve the value in the configuration properties map
     * of the maximum number of failed authentication requests before the
     * server closes the connection.
     * @see #DEFAULT_MAX_AUTH_REQUESTS
     */
    String MAX_AUTH_REQUESTS = "max-auth-requests";

    /**
     * Default value for {@link #MAX_AUTH_REQUESTS} if none configured
     */
    int DEFAULT_MAX_AUTH_REQUESTS = 20;

    /**
     * Key used to retrieve the value of welcome banner that will be displayed
     * when a user connects to the server. If {@code null}/empty then no banner
     * will be sent.
     * @see <A HREF="https://www.ietf.org/rfc/rfc4252.txt">RFC-4252 section 5.4</A>
     */
    String WELCOME_BANNER = "welcome-banner";

    /**
     * Key used to denote the language code for the welcome banner (if such
     * a banner is configured). If not set, then {@link #DEFAULT_WELCOME_BANNER_LANGUAGE}
     * is used
     */
    String WELCOME_BANNER_LANGUAGE = "welcome-banner-language";

    /**
     * Default value for {@link #WELCOME_BANNER_LANGUAGE} is not overwritten
     */
    String DEFAULT_WELCOME_BANNER_LANGUAGE = "en";

    /**
     * This key is used when configuring multi-step authentications.
     * The value needs to be a blank separated list of comma separated list
     * of authentication method names.
     * For example, an argument of
     * <code>publickey,password publickey,keyboard-interactive</code>
     * would require the user to complete public key authentication,
     * followed by either password or keyboard interactive authentication.
     * Only methods that are next in one or more lists are offered at each
     * stage, so for this example, it would not be possible to attempt
     * password or keyboard-interactive authentication before public key.
     */
    String AUTH_METHODS = "auth-methods";

    /**
     * Key used to configure the timeout used when receiving a close request
     * on a channel to wait until the command cleanly exits after setting
     * an EOF on the input stream. In milliseconds.
     */
    String COMMAND_EXIT_TIMEOUT = "command-exit-timeout";

    /**
     * Key re-exchange will be automatically performed after the session
     * has sent or received the given amount of bytes.
     * The default value is 1 gigabyte.
     */
    String REKEY_BYTES_LIMIT = "rekey-bytes-limit";

    /**
     * Key re-exchange will be automatically performed after the specified
     * amount of time has elapsed since the last key exchange. In milliseconds.
     * The default value is 1 hour.
     */
    String REKEY_TIME_LIMIT = "rekey-time-limit";

    /**
     * A URL pointing to the moduli file.
     * If not specified, the default internal file will be used.
     */
    String MODULI_URL = "moduli-url";

    /**
     * Retrieve the list of named factories for <code>UserAuth</code> objects.
     *
     * @return a list of named <code>UserAuth</code> factories, never {@code null}
     */
    List<NamedFactory<UserAuth>> getUserAuthFactories();

    /**
     * Retrieve the <code>PublickeyAuthenticator</code> to be used by SSH server.
     * If no authenticator has been configured (i.e. this method returns
     * {@code null}), then client authentication requests based on keys will be
     * rejected.
     *
     * @return the <code>PublickeyAuthenticato</code> or {@code null}
     */
    PublickeyAuthenticator getPublickeyAuthenticator();

    /**
     * Retrieve the <code>PasswordAuthenticator</code> to be used by the SSH server.
     * If no authenticator has been configured (i.e. this method returns
     * {@code null}), then client authentication requests based on passwords
     * will be rejected.
     *
     * @return the <code>PasswordAuthenticator</code> or {@code null}
     */
    PasswordAuthenticator getPasswordAuthenticator();

    /**
     * Retrieve the <code>KeyboardInteractiveAuthenticator</code> to be used by
     * the SSH server. If no authenticator has been configured (i.e. this method returns
     * {@code null}), then client authentication requests based on this method
     * will be rejected.
     *
     * @return The {@link KeyboardInteractiveAuthenticator} or {@code null}
     */
    KeyboardInteractiveAuthenticator getKeyboardInteractiveAuthenticator();

    /**
     * Retrieve the <code>GSSAuthenticator</code> to be used by the SSH server.
     * If no authenticator has been configured (i.e. this method returns
     * {@code null}), then client authentication requests based on gssapi
     * will be rejected.
     *
     * @return the <code>GSSAuthenticator</code> or {@code null}
     */
    GSSAuthenticator getGSSAuthenticator();

    /**
     * Retrieve the <code>ShellFactory</code> object to be used to create shells.
     *
     * @return a valid <code>ShellFactory</code> object or {@code null} if shells
     * are not supported on this server
     */
    Factory<Command> getShellFactory();

    /**
     * Retrieve the <code>CommandFactory</code> to be used to process commands requests.
     *
     * @return a valid <code>CommandFactory</code> object or {@code null} if commands
     * are not supported on this server
     */
    CommandFactory getCommandFactory();

    /**
     * Retrieve the list of named factories for <code>CommandFactory.Command</code> to
     * be used to create subsystems.
     *
     * @return a list of named <code>CommandFactory.Command</code> factories
     * or {@code null} if subsystems are not supported on this server
     */
    List<NamedFactory<Command>> getSubsystemFactories();
}
