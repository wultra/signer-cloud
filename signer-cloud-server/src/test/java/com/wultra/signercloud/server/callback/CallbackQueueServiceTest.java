package com.wultra.signercloud.server.callback;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test for {@link CallbackQueueService}.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@ExtendWith(MockitoExtension.class)
class CallbackQueueServiceTest {

    @Mock
    private CallbackService callbackService;

    @Mock
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @InjectMocks
    private CallbackQueueService tested;

    @Test
    void testSubmitToExecutor() {
        final CallbackEventData input = CallbackEventData.builder().build();

        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0)
                    .run();
            return null;
        }).when(threadPoolTaskExecutor).execute(any());

        tested.submitToExecutor(input);

        verify(threadPoolTaskExecutor).execute(any());
        verify(callbackService, never()).moveCallbackEventToPending(any());
        verify(callbackService).dispatchInstantCallbackEvent(input);
    }
}
