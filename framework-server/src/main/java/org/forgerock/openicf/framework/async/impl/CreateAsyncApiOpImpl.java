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
 */

package org.forgerock.openicf.framework.async.impl;

import java.util.Set;

import org.forgerock.openicf.common.protobuf.CommonObjectMessages;
import org.forgerock.openicf.common.protobuf.OperationMessages;
import org.forgerock.openicf.common.protobuf.OperationMessages.CreateOpRequest;
import org.forgerock.openicf.common.protobuf.RPCMessages;
import org.forgerock.openicf.common.rpc.RemoteRequestFactory;
import org.forgerock.openicf.common.rpc.RequestDistributor;
import org.forgerock.openicf.framework.async.CreateAsyncApiOp;
import org.forgerock.openicf.framework.remote.MessagesUtil;
import org.forgerock.openicf.framework.remote.rpc.RemoteOperationContext;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionGroup;
import org.forgerock.openicf.framework.remote.rpc.WebSocketConnectionHolder;
import org.forgerock.util.Function;
import org.forgerock.util.promise.Promise;
import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorKey;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

import com.google.protobuf.ByteString;

public class CreateAsyncApiOpImpl extends AbstractAPIOperation implements CreateAsyncApiOp {

    private static final Log logger = Log.getLog(CreateAsyncApiOpImpl.class);

    public CreateAsyncApiOpImpl(
            final RequestDistributor<WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> remoteConnection,
            final ConnectorKey connectorKey,
            final Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction, long timeout) {
        super(remoteConnection, connectorKey, facadeKeyFunction, timeout);
    }

    public Uid create(final ObjectClass objectClass, final Set<Attribute> createAttributes,
            final OperationOptions options) {
        return asyncTimeout(createAsync(objectClass, createAttributes, options));
    }

    public Promise<Uid, RuntimeException> createAsync(final ObjectClass objectClass,
            final Set<Attribute> createAttributes, final OperationOptions options) {
        Assertions.nullCheck(objectClass, "objectClass");
        if (ObjectClass.ALL.equals(objectClass)) {
            throw new UnsupportedOperationException(
                    "Operation is not allowed on __ALL__ object class");
        }
        Assertions.nullCheck(createAttributes, "createAttributes");
        // check to make sure there's not a uid..
        if (AttributeUtil.getUidAttribute(createAttributes) != null) {
            throw new InvalidAttributeValueException("Parameter 'createAttributes' contains a uid.");
        }

        CreateOpRequest.Builder requestBuilder =
                CreateOpRequest.newBuilder().setObjectClass(objectClass.getObjectClassValue());

        requestBuilder.setCreateAttributes(MessagesUtil.serializeLegacy(createAttributes));

        if (options != null) {
            requestBuilder.setOptions(MessagesUtil.serializeLegacy(options));
        }

        return submitRequest(new InternalRequestFactory(getConnectorKey(), getFacadeKeyFunction(),
                OperationMessages.OperationRequest.newBuilder().setCreateOpRequest(requestBuilder)));
    }

    private static class InternalRequestFactory extends
            AbstractRemoteOperationRequestFactory<Uid, InternalRequest> {
        private final OperationMessages.OperationRequest.Builder operationRequest;

        public InternalRequestFactory(
                final ConnectorKey connectorKey,
                final Function<RemoteOperationContext, ByteString, RuntimeException> facadeKeyFunction,
                final OperationMessages.OperationRequest.Builder operationRequest) {
            super(connectorKey, facadeKeyFunction);
            this.operationRequest = operationRequest;
        }

        public InternalRequest createRemoteRequest(
                final RemoteOperationContext context,
                final long requestId,
                final CompletionCallback<Uid, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback) {
            RPCMessages.RPCRequest.Builder builder = createRPCRequest(context);
            if (null != builder) {
                return new InternalRequest(context, requestId, completionCallback, builder);
            } else {
                return null;
            }
        }

        protected OperationMessages.OperationRequest.Builder createOperationRequest(
                final RemoteOperationContext remoteContext) {
            return operationRequest;
        }
    }

    private static class InternalRequest
            extends
            AbstractRemoteOperationRequestFactory.AbstractRemoteOperationRequest<Uid, OperationMessages.CreateOpResponse> {

        public InternalRequest(
                final RemoteOperationContext context,
                final long requestId,
                final RemoteRequestFactory.CompletionCallback<Uid, RuntimeException, WebSocketConnectionGroup, WebSocketConnectionHolder, RemoteOperationContext> completionCallback,
                final RPCMessages.RPCRequest.Builder requestBuilder) {
            super(context, requestId, completionCallback, requestBuilder);

        }

        protected OperationMessages.CreateOpResponse getOperationResponseMessages(
                OperationMessages.OperationResponse message) {
            if (message.hasCreateOpResponse()) {
                return message.getCreateOpResponse();
            } else {
                logger.ok(OPERATION_EXPECTS_MESSAGE, getRequestId(), "CreateOpResponse");
                return null;
            }
        }

        protected void handleOperationResponseMessages(WebSocketConnectionHolder sourceConnection,
                OperationMessages.CreateOpResponse message) {
            if (message.hasUid()) {
                getResultHandler().handleResult(
                        MessagesUtil.deserializeMessage(message.getUid(), Uid.class));
            } else {
                getResultHandler().handleResult(null);
            }
        }
    }

    // ----

    public static AbstractLocalOperationProcessor<Uid, OperationMessages.CreateOpRequest> createProcessor(
            long requestId, WebSocketConnectionHolder socket,
            OperationMessages.CreateOpRequest message) {
        return new InternalCreateLocalOperationProcessor(requestId, socket, message);
    }

    private static class InternalCreateLocalOperationProcessor extends
            AbstractLocalOperationProcessor<Uid, OperationMessages.CreateOpRequest> {

        protected InternalCreateLocalOperationProcessor(long requestId, WebSocketConnectionHolder socket,
                                                        OperationMessages.CreateOpRequest message) {
            super(requestId, socket, message);
        }

        protected RPCMessages.RPCResponse.Builder createOperationResponse(
                RemoteOperationContext remoteContext, Uid result) {
            OperationMessages.CreateOpResponse.Builder response =
                    OperationMessages.CreateOpResponse.newBuilder();
            if (null != result) {
                response.setUid(MessagesUtil.serializeMessage(result,
                        CommonObjectMessages.Uid.class));
            }

            return RPCMessages.RPCResponse.newBuilder().setOperationResponse(
                    OperationMessages.OperationResponse.newBuilder().setCreateOpResponse(response));
        }

        protected Uid executeOperation(ConnectorFacade connectorFacade,
                OperationMessages.CreateOpRequest requestMessage) {
            final ObjectClass objectClass = new ObjectClass(requestMessage.getObjectClass());
            final Set<Attribute> createAttributes =
                    MessagesUtil.deserializeLegacy(requestMessage.getCreateAttributes());
            OperationOptions operationOptions = null;
            if (!requestMessage.getOptions().isEmpty()) {
                operationOptions = MessagesUtil.deserializeLegacy(requestMessage.getOptions());
            }
            return connectorFacade.create(objectClass, createAttributes, operationOptions);
        }
    }
}
