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

/**
 * A RemoteConnectionContext is a custom context to provide application specific
 * information to create the
 * {@link org.forgerock.openicf.common.rpc.RemoteRequest}.
 *
 * <p>The {@link org.forgerock.openicf.common.rpc.RemoteRequest} may depends on
 * which {@link org.forgerock.openicf.common.rpc.RemoteConnectionGroup}
 * distributes the request. Instance of this class is provided to
 * {@link org.forgerock.openicf.common.rpc.RemoteRequestFactory} to produce the
 * {@link org.forgerock.openicf.common.rpc.RemoteRequest} before
 * {@link org.forgerock.openicf.common.rpc.RemoteConnectionGroup#trySendMessage(org.forgerock.util.promise.Function)}
 * sending the message.
 */
public interface RemoteConnectionContext<G extends RemoteConnectionGroup<G, H, P>, H extends RemoteConnectionHolder<G, H, P>, P extends RemoteConnectionContext<G, H, P>> {

    /**
     * Return the {@link org.forgerock.openicf.common.rpc.RemoteConnectionGroup}
     * to which this instance belongs.
     * 
     * @return the
     *         {@link org.forgerock.openicf.common.rpc.RemoteConnectionGroup} to
     *         which this instance belongs to.
     */
    G getRemoteConnectionGroup();

}
