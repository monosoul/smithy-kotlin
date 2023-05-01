/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.auth

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.InlineKotlinWriter
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.integration.AuthSchemeHandler
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.model.knowledge.AuthIndex
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.model.shapes.OperationShape

/**
 * Generates the auth scheme resolver to use for a service (type + implementation)
 */
open class AuthSchemeProviderGenerator {
    companion object {
        fun getSymbol(settings: KotlinSettings): Symbol = buildSymbol {
            name = "AuthSchemeProvider"
            namespace = "${settings.pkg.name}.auth"
            definitionFile = "$name.kt"
        }

        fun getDefaultSymbol(settings: KotlinSettings): Symbol = buildSymbol {
            name = "DefaultAuthSchemeProvider"
            namespace = "${settings.pkg.name}.auth"
            definitionFile = "$name.kt"
        }
    }

    fun render(ctx: ProtocolGenerator.GenerationContext) {
        ctx.delegator.useSymbolWriter(getSymbol(ctx.settings)) { writer ->
            renderTypealias(ctx, writer)
        }

        ctx.delegator.useSymbolWriter(getDefaultSymbol(ctx.settings)) { writer ->
            renderDefaultImpl(ctx, writer)
            writer.write("")
        }
    }

    private fun renderTypealias(ctx: ProtocolGenerator.GenerationContext, writer: KotlinWriter) {
        val paramsSymbol = AuthSchemeParametersGenerator.getSymbol(ctx.settings)
        writer.write(
            "internal typealias AuthSchemeProvider = #T<#T>",
            RuntimeTypes.Auth.Identity.AuthSchemeProvider,
            paramsSymbol,
        )
    }

    private fun renderDefaultImpl(ctx: ProtocolGenerator.GenerationContext, writer: KotlinWriter) {
        writer.withBlock(
            "internal object #T : #T {",
            "}",
            getDefaultSymbol(ctx.settings),
            getSymbol(ctx.settings),
        ) {
            val paramsSymbol = AuthSchemeParametersGenerator.getSymbol(ctx.settings)
            val authIndex = AuthIndex()
            val operationsWithOverrides = authIndex.operationsWithOverrides(ctx)

            withBlock(
                "private val operationOverrides = mapOf<#T, List<#T>>(",
                ")",
                KotlinTypes.String,
                RuntimeTypes.Auth.Identity.AuthSchemeOption,
            ) {
                operationsWithOverrides.forEach { op ->
                    val authHandlersForOperation = authIndex.effectiveAuthHandlersForOperation(ctx, op)
                    renderAuthOptionsListOverrideForOperation(ctx, "\"${op.id.name}\"", authHandlersForOperation, writer, op)
                }
            }

            withBlock(
                "private val serviceDefaults = listOf<#T>(",
                ")",
                RuntimeTypes.Auth.Identity.AuthSchemeOption,
            ) {
                val defaultHandlers = authIndex.effectiveAuthHandlersForService(ctx)

                defaultHandlers.forEach {
                    val inlineWriter: InlineKotlinWriter = {
                        it.authSchemeProviderInstantiateAuthOptionExpr(ctx, null, this)
                    }
                    write("#W,", inlineWriter)
                }
            }

            withBlock(
                "override suspend fun resolveAuthScheme(params: #T): List<#T> {",
                "}",
                paramsSymbol,
                RuntimeTypes.Auth.Identity.AuthSchemeOption,
            ) {
                withBlock("return operationOverrides.getOrElse(params.operationName) {", "}") {
                    write("serviceDefaults")
                }
            }

            // render any helper methods
            val allAuthSchemeHandlers = authIndex.authHandlersForService(ctx)
            allAuthSchemeHandlers.forEach { it.authSchemeProviderRenderAdditionalMethods(ctx, writer) }
        }
    }

    private fun renderAuthOptionsListOverrideForOperation(
        ctx: ProtocolGenerator.GenerationContext,
        case: String,
        handlers: List<AuthSchemeHandler>,
        writer: KotlinWriter,
        op: OperationShape,
    ) {
        writer.withBlock("#L to listOf(", "),", case) {
            handlers.forEach {
                val inlineWriter: InlineKotlinWriter = {
                    it.authSchemeProviderInstantiateAuthOptionExpr(ctx, op, this)
                }
                write("#W,", inlineWriter)
            }
        }
    }
}