/*
 * Copyright 2024-2026 Bundesdruckerei GmbH
 * For the license see the accompanying file LICENSE.MD.
 */
package de.bdr.statuslist.config

import org.apache.catalina.connector.Connector
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty("server.internal-api-port", "server.internal-api-prefixes")
class InternalApiConfiguration(
    @param:Value("\${server.port}") val port: Int,
    @param:Value("\${server.internal-api-port}") val internalApiPort: Int,
    @param:Value("\${server.internal-api-prefixes}") val internalApiPrefixes: List<String>,
) : WebServerFactoryCustomizer<TomcatServletWebServerFactory> {
    private val log: Logger = LoggerFactory.getLogger(InternalApiConfiguration::class.java)

    override fun customize(factory: TomcatServletWebServerFactory) {
        if (internalApiPort != port) {
            val connector = Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL)
            connector.port = internalApiPort
            connector.scheme = "http"
            factory.addAdditionalConnectors(connector)
            log.info("Internal API port $internalApiPort configured")
        } else {
            log.warn("Internal API port equals default port: $internalApiPort")
        }
    }

    @Bean
    @ConditionalOnExpression("\${server.internal-api-port} != \${server.port}")
    fun trustedApiFilter(): FilterRegistrationBean<InternalApiFilter> {
        return FilterRegistrationBean(InternalApiFilter(internalApiPort, internalApiPrefixes))
    }
}
