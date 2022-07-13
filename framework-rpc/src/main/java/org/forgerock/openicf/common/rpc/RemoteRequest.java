/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 ForgeRock AS. All rights reserved.
 * Portions Copyright 2018 Wren Security.
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
 */

package org.forgerock.openicf.common.rpc;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.forgerock.util.Function;
import org.forgerock.util.promise.ExceptionHandler;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.PromiseImpl;
import org.forgerock.util.promise.ResultHandler;

/**
 * A RemoteRequest represents a locally requested procedure call executed
 * remotely.
 *
 * <p>The RemoteRequest and {@link LocalRequest} are the representation of the
 * same call on caller and receiver side.
 *
 */
public abstract class RemoteRequest<
        V,
        E extends Exception,
        G extends RemoteConnectionGroup<G, H, P>,
        H extends RemoteConnectionHolder<G, H, P>,
        P extends RemoteConnectionContext<G, H, P>> {

    private final P context;
    private final long requestId;
    private final RemoteRequestFactory.CompletionCallback<V, E, G, H, P> completionCallback;

    private Long requestTime = null;
    private PromiseImpl<V, E> promise = null;
    private final ReentrantLock lock = new ReentrantLock();

    public RemoteRequest(P context, long requestId,
            RemoteRequestFactory.CompletionCallback<V, E, G, H, P> completionCallback) {
        this.context = context;
        this.requestId = requestId;
        this.completionCallback = completionCallback;
    }

    /**
     * Check if this object was marked inconsistent and should not be disposed.
     *
     * @return 'true' when object is still active or 'false' when this can be
     *         disposed.
     *
     * @see #inconsistent()
     */
    public abstract boolean check();

    /**
     * Signs that the object state is inconsistent.
     */
    public abstract void inconsistent();

    public abstract void handleIncomingMessage(final H sourceConnection, final Object message);

    protected abstract MessageElement createMessageElement(P remoteContext, long requestId);

    protected abstract void tryCancelRemote(P remoteContext, long requestId);

    protected abstract E createCancellationException(Throwable cancellationException);

    public long getRequestId() {
        return requestId;
    }

    public Long getRequestTime() {
        return requestTime;
    }

    public Promise<V, E> getPromise() {
        return promise;
    }

    protected ResultHandler<V> getResultHandler() {
        return promise;
    }

    protected ExceptionHandler<E> getExceptionHandler() {
        return promise;
    }

    protected P getConnectionContext() {
        return context;
    }

    protected boolean cancel() {
        return promise.cancel(false);
    }

    public Function<H, Promise<V, E>, Exception> getSendFunction() {
        final Promise<V, E> resultPromise = promise;
        if (null == resultPromise) {
            final MessageElement message = createMessageElement(context, requestId);
            if (message == null || !(message.isString() || message.isByte())) {
                throw new IllegalStateException("RemoteRequest has empty message");
            }
            return new Function<H, Promise<V, E>, Exception>() {

                @Override
                public Promise<V, E> apply(H remoteConnectionHolder) throws Exception {
                    if (null == promise) {
                        // Single thread should process it so it should not
                        // return false
                        if (lock.tryLock(1, TimeUnit.MINUTES)) {
                            try {
                                if (null == promise) {

                                    promise = new PromiseImpl<V, E>() {

                                        @Override
                                        protected E tryCancel(boolean mayInterruptIfRunning) {
                                            if (mayInterruptIfRunning) {
                                                try {
                                                    tryCancelRemote(context, requestId);
                                                } catch (final Throwable t) {
                                                    return createCancellationException(t);
                                                }
                                            }
                                            return createCancellationException(null);
                                        }

                                    };

                                    promise.thenOnResultOrException(new Runnable() {
                                        @Override
                                        public void run() {
                                            completionCallback.complete(RemoteRequest.this);
                                        }
                                    });

                                    try {
                                        if (message.isByte()) {
                                            remoteConnectionHolder.sendBytes(message.byteMessage)
                                                    .get();
                                        } else if (message.isString()) {
                                            remoteConnectionHolder
                                                    .sendString(message.stringMessage).get();
                                        }
                                    } catch (final Exception e) {
                                        promise = null;
                                        throw e;
                                    } catch (final Throwable t) {
                                        promise = null;
                                        throw new Exception(t);
                                    }
                                    // Message has been delivered - Report
                                    // success
                                    requestTime = System.currentTimeMillis();
                                }
                            } finally {
                                lock.unlock();
                            }
                        }
                    }
                    return promise;
                }
            };
        } else {
            return new Function<H, Promise<V, E>, Exception>() {

                @Override
                public Promise<V, E> apply(H value) throws Exception {
                    return resultPromise;
                }
            };
        }
    }

    // --- inner Classes

    public final static class MessageElement {
        private final String stringMessage;
        private final byte[] byteMessage;

        private MessageElement(String stringMessage, byte[] byteMessage) {
            this.stringMessage = stringMessage;
            this.byteMessage = byteMessage;
        }

        public boolean isString() {
            return null != stringMessage;
        }

        public boolean isByte() {
            return null != byteMessage;
        }

        public static MessageElement createStringMessage(String message) {
            return new MessageElement(message, null);
        }

        public static MessageElement createByteMessage(byte[] message) {
            return new MessageElement(null, message);
        }
    }
}
