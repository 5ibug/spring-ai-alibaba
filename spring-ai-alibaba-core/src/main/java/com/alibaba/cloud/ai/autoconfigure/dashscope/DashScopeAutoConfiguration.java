/*
 * Copyright 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.autoconfigure.dashscope;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.alibaba.cloud.ai.dashscope.api.DashScopeAgentApi;
import com.alibaba.cloud.ai.dashscope.api.DashScopeImageApi;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.image.DashScopeImageModel;
import com.alibaba.dashscope.audio.asr.transcription.Transcription;
import com.alibaba.dashscope.audio.tts.SpeechSynthesizer;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.ApiKey;
import com.alibaba.dashscope.utils.Constants;
import org.jetbrains.annotations.NotNull;

import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import static com.alibaba.cloud.ai.dashscope.common.DashScopeApiConstants.DASHSCOPE_API_KEY;

/**
 * @author nuocheng.lxm
 * @date 2024/8/16 11:45
 */
@AutoConfiguration(after = { RestClientAutoConfiguration.class, WebClientAutoConfiguration.class,
		SpringAiRetryAutoConfiguration.class })
@ConditionalOnClass(DashScopeApi.class)
@EnableConfigurationProperties({ DashScopeConnectionProperties.class, DashScopeChatProperties.class,
		DashScopeImageProperties.class, DashScopeAudioTranscriptionProperties.class,
		DashScopeAudioSpeechProperties.class, DashScopeEmbeddingProperties.class })
@ImportAutoConfiguration(classes = { SpringAiRetryAutoConfiguration.class, RestClientAutoConfiguration.class,
		WebClientAutoConfiguration.class })
public class DashScopeAutoConfiguration {

	@Bean
	@Scope("prototype")
	@ConditionalOnMissingBean
	public SpeechSynthesizer speechSynthesizer() {
		return new SpeechSynthesizer();
	}

	@Bean
	@ConditionalOnMissingBean
	public Transcription transcription() {
		return new Transcription();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = DashScopeChatProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public DashScopeChatModel dashscopeChatModel(DashScopeConnectionProperties commonProperties,
			DashScopeChatProperties chatProperties, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder, List<FunctionCallback> toolFunctionCallbacks,
			FunctionCallbackContext functionCallbackContext, RetryTemplate retryTemplate,
			ResponseErrorHandler responseErrorHandler) {

		if (!CollectionUtils.isEmpty(toolFunctionCallbacks)) {
			chatProperties.getOptions().getFunctionCallbacks().addAll(toolFunctionCallbacks);
		}

		var dashscopeApi = dashscopeChatApi(commonProperties, chatProperties, restClientBuilder, webClientBuilder,
				responseErrorHandler);

		return new DashScopeChatModel(dashscopeApi, chatProperties.getOptions(), functionCallbackContext,
				retryTemplate);
	}

	public DashScopeApi dashscopeChatApi(DashScopeConnectionProperties commonProperties,
			DashScopeChatProperties chatProperties, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder, ResponseErrorHandler responseErrorHandler) {

		DashScopeAutoConfiguration.ResolvedConnectionProperties resolved = resolveConnectionProperties(commonProperties,
				chatProperties, "chat");

		return new DashScopeApi(resolved.baseUrl(), resolved.apiKey(), resolved.workspaceId(), restClientBuilder,
				webClientBuilder, responseErrorHandler);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = DashScopeEmbeddingProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public DashScopeEmbeddingModel dashscopeEmbeddingModel(DashScopeConnectionProperties commonProperties,
			DashScopeEmbeddingProperties embeddingProperties, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder, RetryTemplate retryTemplate,
			ResponseErrorHandler responseErrorHandler) {

		var dashScopeApi = dashscopeEmbeddingApi(commonProperties, embeddingProperties, restClientBuilder,
				webClientBuilder, responseErrorHandler);

		return new DashScopeEmbeddingModel(dashScopeApi, embeddingProperties.getMetadataMode(),
				embeddingProperties.getOptions(), retryTemplate);
	}

	public DashScopeApi dashscopeEmbeddingApi(DashScopeConnectionProperties commonProperties,
			DashScopeEmbeddingProperties embeddingProperties, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder, ResponseErrorHandler responseErrorHandler) {

		DashScopeAutoConfiguration.ResolvedConnectionProperties resolved = resolveConnectionProperties(commonProperties,
				embeddingProperties, "embedding");

		return new DashScopeApi(resolved.baseUrl(), resolved.apiKey(), resolved.workspaceId(), restClientBuilder,
				webClientBuilder, responseErrorHandler);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = DashScopeEmbeddingProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public DashScopeAgentApi dashscopeAgentApi(DashScopeConnectionProperties commonProperties,
			DashScopeChatProperties chatProperties, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder, ResponseErrorHandler responseErrorHandler) {

		DashScopeAutoConfiguration.ResolvedConnectionProperties resolved = resolveConnectionProperties(commonProperties,
				chatProperties, "chat");

		return new DashScopeAgentApi(resolved.baseUrl(), resolved.apiKey(), resolved.workspaceId(), restClientBuilder,
				webClientBuilder, responseErrorHandler);
	}

	@Bean
	public RestClientCustomizer restClientCustomizer(DashScopeConnectionProperties commonProperties) {
		return restClientBuilder -> restClientBuilder
			.requestFactory(ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
				.withReadTimeout(Duration.ofSeconds(commonProperties.getReadTimeout()))));
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = DashScopeImageProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public DashScopeImageModel dashScopeImageModel(DashScopeConnectionProperties commonProperties,
			DashScopeImageProperties imageProperties, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder, RetryTemplate retryTemplate,
			ResponseErrorHandler responseErrorHandler) {

		DashScopeAutoConfiguration.ResolvedConnectionProperties resolved = resolveConnectionProperties(commonProperties,
				imageProperties, "image");

		var dashScopeImageApi = new DashScopeImageApi(resolved.baseUrl(), resolved.apiKey(), resolved.workspaceId(),
				restClientBuilder, webClientBuilder, responseErrorHandler);

		return new DashScopeImageModel(dashScopeImageApi, imageProperties.getOptions(), retryTemplate);
	}

	@Bean
	@ConditionalOnMissingBean
	public FunctionCallbackContext springAiFunctionManager(ApplicationContext context) {
		FunctionCallbackContext manager = new FunctionCallbackContext();
		manager.setApplicationContext(context);
		return manager;
	}

	private record ResolvedConnectionProperties(String baseUrl, String apiKey, String workspaceId,
			MultiValueMap<String, String> headers) {
	}

	private static @NotNull DashScopeAutoConfiguration.ResolvedConnectionProperties resolveConnectionProperties(
			DashScopeParentProperties commonProperties, DashScopeParentProperties modelProperties, String modelType) {

		String baseUrl = StringUtils.hasText(modelProperties.getBaseUrl()) ? modelProperties.getBaseUrl()
				: commonProperties.getBaseUrl();
		String apiKey = StringUtils.hasText(modelProperties.getApiKey()) ? modelProperties.getApiKey()
				: commonProperties.getApiKey();
		String workspaceId = StringUtils.hasText(modelProperties.getWorkspaceId()) ? modelProperties.getWorkspaceId()
				: commonProperties.getWorkspaceId();

		Map<String, List<String>> connectionHeaders = new HashMap<>();
		if (StringUtils.hasText(workspaceId)) {
			connectionHeaders.put("DashScope-Workspace", List.of(workspaceId));
		}

		Assert.hasText(baseUrl,
				"DashScope base URL must be set.  Use the connection property: spring.ai.dashscope.base-url or spring.ai.dashscope."
						+ modelType + ".base-url property.");
		Assert.hasText(apiKey,
				"DashScope API key must be set. Use the connection property: spring.ai.dashscope.api-key or spring.ai.dashscope."
						+ modelType + ".api-key property.");

		return new DashScopeAutoConfiguration.ResolvedConnectionProperties(baseUrl, apiKey, workspaceId,
				CollectionUtils.toMultiValueMap(connectionHeaders));
	}

	/**
	 * Setting the API key.
	 * @param connectionProperties {@link DashScopeConnectionProperties}
	 */
	private void settingApiKey(DashScopeConnectionProperties connectionProperties) {
		String apiKey;
		try {
			// It is recommended to set the key by defining the api-key in an environment
			// variable.
			var envKey = System.getenv(DASHSCOPE_API_KEY);
			if (Objects.nonNull(envKey)) {
				Constants.apiKey = envKey;
				return;
			}
			if (Objects.nonNull(connectionProperties.getApiKey())) {
				apiKey = connectionProperties.getApiKey();
			}
			else {
				apiKey = ApiKey.getApiKey(null);
			}

			Constants.apiKey = apiKey;
		}
		catch (NoApiKeyException e) {
			throw new RuntimeException(e.getMessage());
		}

	}

}