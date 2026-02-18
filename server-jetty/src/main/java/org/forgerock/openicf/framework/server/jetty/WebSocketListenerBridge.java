/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 ForgeRock AS. All rights reserved.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Portions Copyright 2026 Wren Security.
 */
package org.forgerock.openicf.framework.server.jetty;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.forgerock.openicf.common.protobuf.RPCMessages;
import org.forgerock.openicf.framework.remote.ConnectionPrincipal;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.forgerock.util.promise.Promises;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebSocketListenerBridge implements Session.Listener.AutoDemanding {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketListenerBridge.class);

    private final ConnectionPrincipal<?> connectionPrincipal;

    private volatile Session session;

    private boolean hasCloseBeenCalled = false;

    private RemoteOperationContext context = null;

    private final WebSocketConnectionHolder adapter = new WebSocketConnectionHolder() {

        @Override
        protected void handshake(RPCMessages.HandshakeMessage message) {
            context = connectionPrincipal.handshake(this, message);
        }

        @Override
        public boolean isOperational() {
            return session != null && session.isOpen();
        }

        @Override
        public RemoteOperationContext getRemoteConnectionContext() {
            return context;
        }

        @Override
        public Future<?> sendBytes(byte[] data) {
            if (isOperational()) {
                CompletableFuture<Void> future = new CompletableFuture<>();
                session.sendBinary(ByteBuffer.wrap(data), Callback.from(
                        () -> future.complete(null),
                        future::completeExceptionally));
                return future;
            } else {
                return Promises.newExceptionPromise(new ConnectorIOException(
                        "Socket is not connected."));
            }
        }

        @Override
        public Future<?> sendString(String data) {
            if (isOperational()) {
                CompletableFuture<Void> future = new CompletableFuture<>();
                session.sendText(data, Callback.from(
                        () -> future.complete(null),
                        future::completeExceptionally));
                return future;
            } else {
                return Promises.newExceptionPromise(new ConnectorIOException(
                        "Socket is not connected."));
            }
        }

        @Override
        public void sendPing(byte[] applicationData) throws Exception {
            if (isOperational()) {
                session.sendPing(ByteBuffer.wrap(applicationData), Callback.NOOP);
            } else {
                throw new ConnectorIOException("Socket is not connected.");
            }
        }

        @Override
        public void sendPong(byte[] applicationData) throws Exception {
            if (isOperational()) {
                session.sendPong(ByteBuffer.wrap(applicationData), Callback.NOOP);
            } else {
                throw new ConnectorIOException("Socket is not connected.");
            }
        }

        @Override
        protected void tryClose() {
            session.close(StatusCode.NORMAL, "TEST003", Callback.NOOP);
        }

    };

    public WebSocketListenerBridge(ConnectionPrincipal<?> connectionPrincipal) {
        this.connectionPrincipal = connectionPrincipal;
    }

    @Override
    public void onWebSocketOpen(Session session) {
        this.session = session;
        if (logger.isDebugEnabled()) {
            logger.debug("Connect from: {}", session.getRemoteSocketAddress());
        }
        connectionPrincipal.getOperationMessageListener().onConnect(adapter);
    }

    @Override
    public void onWebSocketBinary(ByteBuffer payload, Callback callback) {
        byte[] data = new byte[payload.remaining()];
        payload.get(data);
        logger.debug("onBinaryMessage('{}')", data.length);
        connectionPrincipal.getOperationMessageListener().onMessage(adapter, data);
        callback.succeed();
    }

    @Override
    public void onWebSocketText(String message) {
        logger.debug("onTextMessage('{}')", message);
        connectionPrincipal.getOperationMessageListener().onMessage(adapter, message);
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason, Callback callback) {
        if (hasCloseBeenCalled) {
            // avoid duplicate close events (possible when using harsh
            // Session.disconnect())
            callback.succeed();
            return;
        }
        hasCloseBeenCalled = true;
        connectionPrincipal.getOperationMessageListener().onClose(adapter, statusCode, reason);
        callback.succeed();
    }

    @Override
    public void onWebSocketPing(ByteBuffer payload) {
        byte[] b = new byte[payload.remaining()];
        payload.get(b);
        connectionPrincipal.getOperationMessageListener().onPing(adapter, b);
    }

    @Override
    public void onWebSocketPong(ByteBuffer payload) {
        byte[] b = new byte[payload.remaining()];
        payload.get(b);
        connectionPrincipal.getOperationMessageListener().onPong(adapter, b);
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        logger.debug("onError:", cause);
        connectionPrincipal.getOperationMessageListener().onError(cause);
    }

}
