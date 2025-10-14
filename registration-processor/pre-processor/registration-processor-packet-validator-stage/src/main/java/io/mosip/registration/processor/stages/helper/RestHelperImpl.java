package io.mosip.registration.processor.stages.helper;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import javax.net.ssl.SSLException;
import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.reactive.function.client.WebClient.RequestBodyUriSpec;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.processor.core.constant.LoggerFileConstant;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.rest.client.utils.RestApiClient;
import io.mosip.registration.processor.stages.dto.AsyncRequestDTO;
import io.mosip.registration.processor.stages.exception.RestServiceException;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;

@Component
@NoArgsConstructor
public class RestHelperImpl implements RestHelper {

	private static Logger mosipLogger = RegProcessorLogger.getLogger(RestHelperImpl.class);

	@Autowired
	private RestApiClient restApiClient;

	@Autowired
	private ObjectMapper mapper;

	@Override
	public Supplier<Object> requestAsync(@Valid AsyncRequestDTO request) {
		try {
			Mono<?> sendRequest = request(request, getSslContext());
			return () -> sendRequest.block();
		} catch (RestServiceException | IOException e) {
			mosipLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(), "",
					"RestHelperImpl::SslContext()::error");
			return () -> new RestServiceException("UNABLE_TO_PROCESS", e);
		}
	}

	private SslContext getSslContext() throws RestServiceException {
		try {
			return SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
		} catch (SSLException e) {
			mosipLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(), "",
					"RestHelperImpl::SslContext()::error");
			throw new RestServiceException("UNKNOWN_ERROR", e);
		}
	}

	private Mono<?> request(AsyncRequestDTO request, SslContext sslContext) throws IOException {
		WebClient webClient;
		Mono<?> monoResponse;
		RequestBodySpec uri;
		ResponseSpec exchange;
		RequestBodyUriSpec method;

		if (request.getHeaders() != null) {
			org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder = WebClient.builder()
					.clientConnector(new ReactorClientHttpConnector(builder -> builder.sslContext(sslContext)))
					.baseUrl(request.getUri());
			if (request.getHeaders().getContentType() != null) {
				webClientBuilder = webClientBuilder.defaultHeader(HttpHeaders.CONTENT_TYPE,
						request.getHeaders().getContentType().toString());
			}
			webClient = webClientBuilder.build();
		} else {
			webClient = WebClient.builder()
					.clientConnector(new ReactorClientHttpConnector(builder -> builder.sslContext(sslContext)))
					.baseUrl(request.getUri()).build();
		}

		method = webClient.method(request.getHttpMethod());
		if (request.getParams() != null && request.getPathVariables() == null) {
			uri = method.uri(builder -> builder.queryParams(request.getParams()).build());
		} else if (request.getParams() == null && request.getPathVariables() != null) {
			uri = method.uri(builder -> builder.build(request.getPathVariables()));
		} else {
			uri = method.uri(builder -> builder.build());
		}

		uri.header("cookie", restApiClient.getToken());

		if (request.getRequestBody() != null) {
			exchange = uri.syncBody(request.getRequestBody()).retrieve();
		} else {
			exchange = uri.retrieve();
		}

		monoResponse = exchange.bodyToMono(request.getResponseType());

		return monoResponse;
	}

	@Override
	public CompletableFuture<Void> requestFireAndForget(AsyncRequestDTO request) {
		try {
			Mono<Object> mono = (Mono<Object>) request(request, getSslContext());

			// Convert Mono to CompletableFuture and ignore the result
			return mono.then() // converts Mono<Object> -> Mono<Void>
					.toFuture();

		} catch (RestServiceException | IOException e) {
			CompletableFuture<Void> failed = new CompletableFuture<>();
			failed.completeExceptionally(e);
			return failed;
		}
	}
}
