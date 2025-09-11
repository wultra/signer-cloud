package com.wultra.signercloud.server.callback;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link CallbackService}.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class CallbackServiceIntegrationTest {

    @Autowired
    private CallbackService tested;

    @Autowired
    private CallbackEventRepository callbackEventRepository;

    private MockWebServer mockWebServer;

    @BeforeEach
    void setup() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    void cleanup() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    void testDispatchInstantCallbackEvent_wrongStatus() {
        final String idempotencyKey = UUID.randomUUID().toString();

        final long id = callbackEventRepository.save(CallbackEvent.builder()
                .status(CallbackEventStatus.PENDING)
                .callbackId(1L)
                .callbackData("""
                        {"foo":"bar"}""")
                .timestampCreated(LocalDateTime.now())
                .idempotencyKey(idempotencyKey)
                .build()).getId();

        final CallbackEventData input = CallbackEventData.builder()
                .id(id)
                .status(CallbackEventStatus.PENDING)
                .build();

        tested.dispatchInstantCallbackEvent(input);

        final Optional<CallbackEvent> callbackEvent = callbackEventRepository.findById(id);
        assertTrue(callbackEvent.isPresent());
        assertEquals(CallbackEventStatus.PENDING, callbackEvent.get().getStatus());
    }

    @Test
    void testDispatchInstantCallbackEvent_success() throws Exception {
        final String idempotencyKey = UUID.randomUUID().toString();

        final long id = callbackEventRepository.save(CallbackEvent.builder()
                .status(CallbackEventStatus.PROCESSING)
                .callbackId(1L)
                .callbackData("""
                        {"foo":"bar"}""")
                .timestampCreated(LocalDateTime.now())
                .idempotencyKey(idempotencyKey)
                .build()).getId();

        final CallbackData config = CallbackData.builder()
                .id(1L)
                .url(mockWebServer.url("callback").toString())
                .build();

        final CallbackEventData input = CallbackEventData.builder()
                .id(id)
                .idempotencyKey(idempotencyKey)
                .status(CallbackEventStatus.PROCESSING)
                .config(config)
                .callbackData(Map.of("foo", "bar"))
                .build();

        mockWebServer.enqueue(new MockResponse().setResponseCode(200));

        tested.dispatchInstantCallbackEvent(input);

        final RecordedRequest recordedRequest = mockWebServer.takeRequest(1L, TimeUnit.SECONDS);
        assertNotNull(recordedRequest);
        assertEquals("POST /callback HTTP/1.1", recordedRequest.getRequestLine());
        assertEquals(idempotencyKey, recordedRequest.getHeader("Idempotency-Key"));
        assertEquals("""
            {"foo":"bar"}""", recordedRequest.getBody().readUtf8());

        Awaitility.await()
                .atMost(Duration.ofSeconds(5L))
                .until(() -> callbackEventRepository.findById(id)
                        .map(CallbackEvent::getStatus)
                        .filter(CallbackEventStatus.COMPLETED::equals)
                        .isPresent());
    }

    @Test
    void testDispatchInstantCallbackEvent_error_failed() throws Exception {
        final String idempotencyKey = UUID.randomUUID().toString();

        final long id = callbackEventRepository.save(CallbackEvent.builder()
                .status(CallbackEventStatus.PROCESSING)
                .attempts(1) // set attempts to 1, so that after failure it will be 2 and overreach maxAttempts=1
                .callbackId(1L)
                .callbackData("""
                        {"foo":"bar"}""")
                .timestampCreated(LocalDateTime.now())
                .idempotencyKey(idempotencyKey)
                .build()).getId();

        final CallbackData config = CallbackData.builder()
                .id(1L)
                .url(mockWebServer.url("callback").toString())
                .build();

        final CallbackEventData input = CallbackEventData.builder()
                .id(id)
                .idempotencyKey(idempotencyKey)
                .status(CallbackEventStatus.PROCESSING)
                .config(config)
                .callbackData(Map.of("foo", "bar"))
                .build();

        mockWebServer.enqueue(new MockResponse().setResponseCode(400));

        tested.dispatchInstantCallbackEvent(input);

        final RecordedRequest recordedRequest = mockWebServer.takeRequest(1L, TimeUnit.SECONDS);
        assertNotNull(recordedRequest);
        assertEquals("POST /callback HTTP/1.1", recordedRequest.getRequestLine());
        assertEquals(idempotencyKey, recordedRequest.getHeader("Idempotency-Key"));
        assertEquals("""
            {"foo":"bar"}""", recordedRequest.getBody().readUtf8());

        Awaitility.await()
                .atMost(Duration.ofSeconds(5L))
                .until(() -> callbackEventRepository.findById(id)
                        .filter(it -> it.getStatus() == CallbackEventStatus.FAILED && it.getAttempts() == 2)
                        .isPresent());
    }

    @Test
    void testDispatchInstantCallbackEvent_error_pending() throws Exception {
        final String idempotencyKey = UUID.randomUUID().toString();

        final long id = callbackEventRepository.save(CallbackEvent.builder()
                .status(CallbackEventStatus.PROCESSING)
                .attempts(0) // set attempts to 0, so that after failure it will be 1
                .callbackId(1L)
                .callbackData("""
                        {"foo":"bar"}""")
                .timestampCreated(LocalDateTime.now())
                .idempotencyKey(idempotencyKey)
                .build()).getId();

        final CallbackData config = CallbackData.builder()
                .id(1L)
                .url(mockWebServer.url("callback").toString())
                .build();

        final CallbackEventData input = CallbackEventData.builder()
                .id(id)
                .idempotencyKey(idempotencyKey)
                .status(CallbackEventStatus.PROCESSING)
                .config(config)
                .callbackData(Map.of("foo", "bar"))
                .build();

        mockWebServer.enqueue(new MockResponse().setResponseCode(400));

        tested.dispatchInstantCallbackEvent(input);

        final RecordedRequest recordedRequest = mockWebServer.takeRequest(1L, TimeUnit.SECONDS);
        assertNotNull(recordedRequest);
        assertEquals("POST /callback HTTP/1.1", recordedRequest.getRequestLine());
        assertEquals(idempotencyKey, recordedRequest.getHeader("Idempotency-Key"));
        assertEquals("""
            {"foo":"bar"}""", recordedRequest.getBody().readUtf8());

        Awaitility.await()
                .atMost(Duration.ofSeconds(5L))
                .until(() -> callbackEventRepository.findById(id)
                        .filter(it -> it.getStatus() == CallbackEventStatus.PENDING && it.getAttempts() == 1)
                        .isPresent());
    }
}
