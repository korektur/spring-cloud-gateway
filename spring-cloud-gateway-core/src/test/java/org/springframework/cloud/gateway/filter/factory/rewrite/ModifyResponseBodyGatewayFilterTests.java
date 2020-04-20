/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.filter.factory.rewrite;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "spring.codec.max-in-memory-size=40")
@DirtiesContext
public class ModifyResponseBodyGatewayFilterTests extends BaseWebClientTests {

	private static final String toLarge;

	static {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 1000; i++) {
			sb.append("to-large-");
		}
		toLarge = sb.toString();
	}

	@Test
	public void testModificationOfResponseBody() {
		URI uri = UriComponentsBuilder.fromUriString(this.baseUri + "/withoutFilter")
				.build(true).toUri();

		testClient.get().uri(uri).header("Host", "www.modifyresponsebodyjava.org")
				.accept(MediaType.APPLICATION_JSON).exchange().expectBody()
				.json("{\"value\": \"httpbin compatible home\", \"length\": 23}");
	}

	@Test
	public void testCacheFilter() {
		URI uri = UriComponentsBuilder.fromUriString(this.baseUri + "/withFilter")
				.build(true).toUri();

		testClient.get().uri(uri).header("Host", "www.modifyresponsebodyjava.org")
				.accept(MediaType.APPLICATION_JSON).exchange().expectBody()
				.json("{\"value\": \"intercepted\"}");
	}

	@Test
	public void testCacheFilterWithModifier() {
		URI uri = UriComponentsBuilder.fromUriString(this.baseUri + "/withFilterAndModifier")
				.build(true).toUri();

		testClient.get().uri(uri).header("Host", "www.modifyresponsebodyjava.org")
				.accept(MediaType.APPLICATION_JSON).exchange().expectBody()
				.json("{\"value\": \"intercepted\"}");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Bean
		public GatewayFilter cacheGatewayFilter() {
			return (exchange, chain) -> {
				ServerHttpResponse response = exchange.getResponse();
				response.setStatusCode(HttpStatus.OK);
				DataBufferFactory dataBufferFactory = response.bufferFactory();
				DataBuffer wrap = dataBufferFactory.wrap(
						"{\"value\": \"intercepted\"}".getBytes(StandardCharsets.UTF_8));
				return response.writeWith(Mono.just(wrap))
						.doOnSuccess(v -> response.setComplete());
			};
		}

		@Value("${test.uri}")
		String uri;

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder,
				GatewayFilter cacheGatewayFilter) {
			return builder.routes()
					.route("modify_response_java_test", r -> r.path("/withoutFilter")
							.filters(f -> f.setPath("/httpbin/").modifyResponseBody(
									String.class, Map.class,
									(webExchange, originalResponse) -> {
										Map<String, Object> modifiedResponse = new HashMap<>();
										modifiedResponse.put("value", originalResponse);
										modifiedResponse.put("length",
												originalResponse.length());
										return Mono.just(modifiedResponse);
									}))
							.uri(uri))
					.route("modify_response_java_test_with_filter",
							r -> r.path("/withFilter")
									.filters(f -> f.setPath("/httpbin/")
											.filter(cacheGatewayFilter))
									.uri(uri))
					.route("modify_response_java_test_with_filter",
							r -> r.path("/withFilterAndModifier")
									.filters(f -> f.setPath("/httpbin/")
											.filter(cacheGatewayFilter)
											.modifyResponseBody(Map.class, Map.class,
													(webExchange, originalResponse) -> {
														Map<String, Object> modifiedResponse = new HashMap<>();
														modifiedResponse.put("value",
																originalResponse);
														modifiedResponse.put("length",
																originalResponse
																		.size());
														return Mono
																.just(modifiedResponse);
													}))
									.uri(uri))
					.build();
		}

	}

}
