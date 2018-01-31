package com.bugsnag;

import com.bugsnag.delivery.Delivery;
import com.bugsnag.serialization.Serializer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.*;

public class SessionTrackerTest {

    private SessionTracker sessionTracker;
    private Configuration configuration;

    @Before
    public void setUp() throws Throwable {
        configuration = new Configuration("api-key");
        sessionTracker = new SessionTracker(configuration);
        assertNull(sessionTracker.getSession());
    }

    @Test
    public void startManualSession() throws Throwable {
        sessionTracker.startNewSession(new Date(), false);
        assertNotNull(sessionTracker.getSession());
    }

    @Test
    public void startAutoSessionDisabled() throws Throwable {
        sessionTracker.startNewSession(new Date(), true);
        assertNull(sessionTracker.getSession());
    }

    @Test
    public void startAutoSessionEnabled() throws Throwable {
        configuration.setAutoCaptureSessions(true);
        sessionTracker.startNewSession(new Date(), true);
        assertNotNull(sessionTracker.getSession());
    }

    @Test
    public void startTwoSessionsSameThread() throws Throwable {
        sessionTracker.startNewSession(new Date(), false);
        Session first = sessionTracker.getSession();

        sessionTracker.startNewSession(new Date(), false);
        Session second = sessionTracker.getSession();
        assertNotEquals(first, second);
    }

    @Test
    public void startTwoSessionsDiffThread() throws Throwable {
        sessionTracker.startNewSession(new Date(), false);
        final Session first = sessionTracker.getSession();
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        new Thread(new Runnable() {
            @Override
            public void run() {
                sessionTracker.startNewSession(new Date(), false);
                countDownLatch.countDown();
            }
        }).start();

        countDownLatch.await();
        assertEquals(first, sessionTracker.getSession());
    }

    @Test
    public void disabledReleaseStage() throws Throwable {
        configuration.notifyReleaseStages = new String[]{"prod"};
        configuration.releaseStage = "dev";
        sessionTracker.startNewSession(new Date(), false);
        assertNull(sessionTracker.getSession());
    }

    @Test
    public void enabledReleaseStage() throws Throwable {
        configuration.notifyReleaseStages = new String[]{"prod"};
        configuration.releaseStage = "prod";
        sessionTracker.startNewSession(new Date(), false);
        assertNotNull(sessionTracker.getSession());
    }

    @Test(timeout = 200)
    public void addManySessions() throws Throwable {
        for (int k = 0; k < 1000; k++) {
            sessionTracker.startNewSession(new Date(), false);
        }
    }

    @Test
    public void zeroSessionDelivery() throws Throwable {
        CustomDelivery sessionDelivery = new CustomDelivery() {
            @Override
            public void deliver(Serializer serializer, Object object, Map<String, String> headers) {
                fail("Should not be called if no sessions enqueued");
            }
        };
        configuration.sessionDelivery = sessionDelivery;
        sessionTracker.flushSessions(new Date());
        assertFalse(sessionDelivery.delivered);
    }

    @Test
    public void noDateChangeSessionDelivery() throws Throwable {
        CustomDelivery sessionDelivery = new CustomDelivery() {
            @Override
            public void deliver(Serializer serializer, Object object, Map<String, String> headers) {
                fail("Should not be called if date has not exceeded batch period");
            }
        };
        configuration.sessionDelivery = sessionDelivery;
        sessionTracker.startNewSession(new Date(1309209859), false);
        sessionTracker.flushSessions(new Date(1309209859));
        assertTrue(sessionDelivery.delivered);
    }

    @Test
    public void multiSessionDelivery() throws Throwable {
        CustomDelivery sessionDelivery = new CustomDelivery() {
            @Override
            public void deliver(Serializer serializer, Object object, Map<String, String> headers) {
                SessionPayload payload = (SessionPayload) object;
                assertEquals(2, payload.getSessionCounts().size());
            }
        };
        configuration.sessionDelivery = sessionDelivery;
        sessionTracker.startNewSession(new Date(5092340L), false);
        sessionTracker.startNewSession(new Date(125098234L), false);
        sessionTracker.startNewSession(new Date(1509207501L), false);
        sessionTracker.startNewSession(new Date(1509209834L), false);
        sessionTracker.flushSessions(new Date(1509209834L));
        assertTrue(sessionDelivery.delivered);
    }

    @Test
    public void sessionDeliveryDiffMin() throws Throwable {
        CustomDelivery sessionDelivery = new CustomDelivery() {
            @Override
            public void deliver(Serializer serializer, Object object, Map<String, String> headers) {
                SessionPayload payload = (SessionPayload) object;
                assertEquals(1, payload.getSessionCounts().size());
            }
        };
        configuration.sessionDelivery = sessionDelivery;

        // 2 mins apart
        sessionTracker.startNewSession(new Date(10000000L), false);
        sessionTracker.flushSessions(new Date(10120000L));
        assertTrue(sessionDelivery.delivered);
    }

    @Test
    public void sessionDeliverySameMin() throws Throwable {
        CustomDelivery sessionDelivery = new CustomDelivery() {
            @Override
            public void deliver(Serializer serializer, Object object, Map<String, String> headers) {
                SessionPayload payload = (SessionPayload) object;
                assertEquals(1, payload.getSessionCounts().size());
            }
        };
        configuration.sessionDelivery = sessionDelivery;

        // 1 hour apart
        sessionTracker.startNewSession(new Date(10000000L), false);
        sessionTracker.flushSessions(new Date(13600000L));
        assertTrue(sessionDelivery.delivered);
    }

    abstract static class CustomDelivery implements Delivery {
        boolean delivered;

        @Override
        public void deliver(Serializer serializer, Object object, Map<String, String> headers) {
            delivered = true;
        }

        @Override
        public void close() {
        }
    }
}