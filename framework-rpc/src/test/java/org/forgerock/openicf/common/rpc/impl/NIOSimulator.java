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
 * Portions Copyrighted 2022 Wren Security
 */

package org.forgerock.openicf.common.rpc.impl;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.openicf.common.rpc.MessageListener;
import org.forgerock.openicf.common.rpc.RemoteConnectionContext;
import org.forgerock.openicf.common.rpc.RemoteConnectionGroup;
import org.forgerock.openicf.common.rpc.RemoteConnectionHolder;
import org.testng.Reporter;

@SuppressWarnings("unchecked")
public class NIOSimulator<
        G extends RemoteConnectionGroup<G, H, P>,
        H extends RemoteConnectionHolder<G, H, P>,
        P extends RemoteConnectionContext<G, H, P>
    > implements Closeable, Runnable {

    private static final AtomicInteger SELECTOR_COUNTER = new AtomicInteger();

    private static final AtomicLong CONNECTION_COUNTER = new AtomicLong();

    private final MessageListener<G, H, P> serverListener;

    private final Thread selectorThread = new Thread(this, "nios-selector-" + SELECTOR_COUNTER.incrementAndGet());

    private final ExecutorService workerService = Executors.newFixedThreadPool(10);

    private final List<RemoteConnectionHolderImpl> connections = new CopyOnWriteArrayList<>();

    public NIOSimulator(MessageListener<G, H, P> serverListener) {
        this.serverListener = serverListener;
        selectorThread.start();
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            boolean processed = false;
            for (RemoteConnectionHolderImpl connection : connections) {
                if (connection.messageQueue.isEmpty()) {
                    continue;
                }
                boolean available = connection.workerLock.compareAndSet(false, true);
                if (available) {
                    processed = true;
                    workerService.submit(() -> {
                       try {
                           Runnable message = connection.messageQueue.poll();
                           if (message == null || !connection.active.get()) {
                               return;
                           }
                           message.run();
                       } finally {
                           connection.workerLock.set(false);
                       }
                    });
                }
            }
            if (!processed) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public RemoteConnectionHolder<G, H, P> connect(
            final MessageListener<G, H, P> clientListener, P serverContext, P clientContext) {
        return new RemoteConnectionHolderImpl(serverListener, clientListener, serverContext, clientContext);
    }

    @Override
    public void close() throws IOException {
        for (RemoteConnectionHolderImpl connection: connections) {
            connection.close();
        }
        selectorThread.interrupt();
        workerService.shutdownNow();
    }

    public class RemoteConnectionHolderImpl implements RemoteConnectionHolder<G, H, P> {

        private final long id = CONNECTION_COUNTER.incrementAndGet();

        private final RemoteConnectionHolderImpl reverseConnection;

        private final P context;

        private final MessageListener<G, H, P> remoteListener;

        private final AtomicBoolean active = new AtomicBoolean(true);

        private final ExecutorService submitQueue = Executors.newFixedThreadPool(1, task -> new Thread(task, "nios-connection-" + id));

        private final Queue<Runnable> messageQueue = new ConcurrentLinkedQueue<>();

        private final AtomicBoolean workerLock = new AtomicBoolean();

        public RemoteConnectionHolderImpl(final MessageListener<G, H, P> serverListener,
                final MessageListener<G, H, P> clientListener, P serverContext, P clientContext) {
            reverseConnection = new RemoteConnectionHolderImpl(this, clientListener, serverContext);
            remoteListener = serverListener;
            context = clientContext;
            connections.add(this);
            remoteListener.onConnect((H) reverseConnection);
            clientListener.onConnect((H) this);
        }

        public RemoteConnectionHolderImpl(RemoteConnectionHolderImpl reverseConnection,
                MessageListener<G, H, P> clientListener, P serverContext) {
            this.reverseConnection = reverseConnection;
            remoteListener = clientListener;
            context = serverContext;
            connections.add(this);
        }

        @Override
        public P getRemoteConnectionContext() {
            return context;
        }

        @Override
        public Future<?> sendBytes(final byte[] data) {
            return submitQueue.submit(() -> {
                if (active.get()) {
                    messageQueue.offer(() -> getMessageListener().onMessage(getReverseConnection(), data));
                } else {
                    throw new IOException("Simulated Connection is Closed");
                }
                return null;
            });
        }

        @Override
        public Future<?> sendString(final String data) {
            return submitQueue.submit(() -> {
                if (active.get()) {
                    messageQueue.offer(() -> getMessageListener().onMessage(getReverseConnection(), data));
                } else {
                    Reporter.log("Simulated Connection is Closed");
                    throw new IOException("Simulated Connection is Closed");
                }
                return null;
            });
        }

        @Override
        public void sendPing(final byte[] applicationData) throws Exception {
            submitQueue.submit(() -> {
                if (active.get()) {
                    getMessageListener().onPing(getReverseConnection(), applicationData);
                } else {
                    throw new IOException("Simulated Connection is Closed");
                }
                return null;
            }).get();
        }

        @Override
        public void sendPong(final byte[] applicationData) throws Exception {
            submitQueue.submit(() -> {
                if (active.get()) {
                    getMessageListener().onPong(getReverseConnection(), applicationData);
                } else {
                    throw new IOException("Simulated Connection is Closed");
                }
                return null;
            }).get();
        }

        @Override
        public void close() {
            if (active.compareAndSet(true, false)) {
                tryClose();
            }
        }

        protected void tryClose() {
            connections.remove(this);
            reverseConnection.close();
            submitQueue.shutdownNow();
            messageQueue.clear();
            reverseConnection.getMessageListener().onClose((H) this, 100, "Connection Closed");
        }

        protected H getReverseConnection() {
            return (H) reverseConnection;
        }

        protected MessageListener<G, H, P> getMessageListener() {
            return remoteListener;
        }

    }

}
