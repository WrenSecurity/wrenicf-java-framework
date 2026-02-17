/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2015-2016 ForgeRock AS.
 * Portions Copyright 2018-2026 Wren Security.
 */

package org.forgerock.openicf.framework.server.jetty;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import javax.security.auth.callback.NameCallback;
import org.eclipse.jetty.ee10.websocket.server.JettyServerUpgradeRequest;
import org.eclipse.jetty.ee10.websocket.server.JettyServerUpgradeResponse;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServletFactory;
import org.forgerock.openicf.framework.ConnectorFramework;
import org.forgerock.openicf.framework.ConnectorFrameworkFactory;
import org.forgerock.openicf.framework.remote.ReferenceCountedObject;
import org.forgerock.util.Utils;
import org.identityconnectors.common.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenICFWebSocketServletBase extends JettyWebSocketServlet {

    public static final String INIT_PARAM_CONNECTOR_FRAMEWORK_FACTORY_CLASS =
            "connector-framework-factory-class";
    public static final String INIT_PARAM_CONNECTION_FACTORY_METHOD = "connection-factory-method";
    public static final String INIT_PARAM_CONNECTION_METHOD_DEFAULT = "acquire";

    private static final long serialVersionUID = 6089858120348026823L;

    private static final Logger logger = LoggerFactory.getLogger(OpenICFWebSocketServletBase.class);

    private boolean privateConnectorFramework = false;
    private boolean privateExecutorService = false;

    private ReferenceCountedObject<ConnectorFramework>.Reference connectorFramework = null;
    private ScheduledExecutorService executorService = null;

    public OpenICFWebSocketServletBase() {
    }

    public OpenICFWebSocketServletBase(final ConnectorFrameworkFactory connectorFramework) {
        this(connectorFramework.acquire(), null);
        privateConnectorFramework = true;
    }

    public OpenICFWebSocketServletBase(final ReferenceCountedObject<ConnectorFramework>.Reference connectorFramework,
           final ScheduledExecutorService executorService) {
        this.connectorFramework = connectorFramework;
        this.executorService = executorService;
    }

    @Override
    public void destroy() {
        super.destroy();
        if (privateExecutorService && executorService != null) {
            try {
                executorService.shutdown();
                executorService = null;
            } catch (Throwable e) {
                logger.warn("Failed to shut down executor service", e);
            }
        }
        if (privateConnectorFramework && connectorFramework != null) {
            try {
                connectorFramework.release();
                connectorFramework = null;
            } catch (Exception e) {
                logger.warn("Failed to release connector framework", e);
            }
        }
    }

    @Override
    protected void configure(JettyWebSocketServletFactory factory) {
        factory.addMapping("/", getWebsocketCreator());

        String max = getInitParameter("maxAsyncWriteTimeout");
        if (max != null) {
            factory.setIdleTimeout(Duration.ofMillis(Long.parseLong(max)));
        }
    }

    protected ConnectorFramework getConnectorFramework() {
        if (null == connectorFramework) {
            ConnectorFrameworkFactory cf = getConnectorFrameworkFactory();
            if (null != cf) {
                connectorFramework = cf.acquire();
                configure(connectorFramework.get());
            } else {
                throw new RuntimeException("Failed to initialise connectorFramework");
            }
        }
        return connectorFramework.get();
    }

    protected ScheduledExecutorService getExecutorService() {
        if (null == executorService) {
            executorService = Executors.newScheduledThreadPool(1, Utils
                    .newThreadFactory(null, "OpenICF WebSocket Servlet Scheduler %d", false));
            if (executorService instanceof ScheduledThreadPoolExecutor) {
                ((ScheduledThreadPoolExecutor) executorService)
                        .setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
                ((ScheduledThreadPoolExecutor) executorService)
                        .setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            }
        }
        return executorService;
    }

    /**
     * Overwrite with custom implementation.
     *
     * @return
     */
    protected OpenICFWebSocketCreator getWebsocketCreator() {
        return new OpenICFWebSocketCreator(getConnectorFramework(), getExecutorService());
    }

    /**
     * Overwrite with custom implementation.
     */
    protected void configure(ConnectorFramework connectorFramework) {
        logger.info("Implementation should overwrite this method");
    }

    /**
     * Overwrite with custom implementation.
     *
     * @return
     */
    protected ConnectorFrameworkFactory getConnectorFrameworkFactory() {
        final ServletConfig config = getServletConfig();
        if (config != null) {
            // Check for configured connection factory class first.
            final String className =
                    config.getInitParameter(INIT_PARAM_CONNECTOR_FRAMEWORK_FACTORY_CLASS);
            if (className != null) {
                try {
                    final Class<?> cls = Class.forName(className);
                    final String tmp =
                            config.getInitParameter(INIT_PARAM_CONNECTION_FACTORY_METHOD);
                    final String methodName =
                            tmp != null ? tmp : INIT_PARAM_CONNECTION_METHOD_DEFAULT;
                    try {
                        // Try method which accepts ServletConfig.
                        final Method factoryMethod =
                                cls.getDeclaredMethod(methodName, ServletConfig.class);
                        return (ConnectorFrameworkFactory) factoryMethod.invoke(null, config);
                    } catch (final NoSuchMethodException e) {
                        // Try no-arg method.
                        final Method factoryMethod = cls.getDeclaredMethod(methodName);
                        return (ConnectorFrameworkFactory) factoryMethod.invoke(null);
                    }
                } catch (final Exception e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
        }
        throw new RuntimeException("Unable to initialize ConnectionFactory");
    }

    public static Authenticator createDefaultAuthenticator() {
        return new ServletAttributeAuthenticator(null);
    }

    public static class ServletAttributeAuthenticator implements Authenticator {

        private static final String DEFAULT = Authenticator.class.getName();
        private final String attributeName;


        public ServletAttributeAuthenticator(String attributeName) {
            this.attributeName = StringUtil.isBlank(attributeName) ? DEFAULT : attributeName;
        }

        public void setAttributeValue(HttpServletRequest httpRequest, String value) {
            httpRequest.setAttribute(attributeName, value);
        }

        @Override
        public void authenticate(JettyServerUpgradeRequest request, JettyServerUpgradeResponse response,
                NameCallback callback) {
            Object value = request.getHttpServletRequest().getAttribute(attributeName);
            if (value instanceof String) {
                callback.setName((String) value);
            }
        }

    }

}
