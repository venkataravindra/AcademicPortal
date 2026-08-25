package com.dbs.edoc.notification.services.notification.external;

import com.dbs.edoc.config.DynamicIntProperty;
import com.dbs.edoc.notification.domain.entity.ExternalMailBoxItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.Closeable;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.SECONDS;

@Service
public class ExternalMailSendingRetryScheduler implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalMailSendingRetryScheduler.class);
    private static final DynamicIntProperty RETRY_FREQ = new DynamicIntProperty("external.mailbox.retry.frequency.seconds", 60);

    private final ScheduledExecutorService executorService;
    private final AtomicReference<ScheduledFuture<?>> scheduledFutureRef;
    private final ExternalMailBoxService mailBoxService;
    private final ExternalMailSender mailSender;

    @Autowired
    public ExternalMailSendingRetryScheduler(ExternalMailBoxService internalMailBoxService, ExternalMailSender mailSender) {
        this.mailBoxService = internalMailBoxService;
        this.mailSender = mailSender;
        this.executorService = Executors.newScheduledThreadPool(10);
        this.scheduledFutureRef = new AtomicReference<>(createScheduledFuture());
        RETRY_FREQ.addCallback(() -> {
            ScheduledFuture<?> oldScheduledFuture = scheduledFutureRef.get();
            scheduledFutureRef.set(createScheduledFuture());
            if (nonNull(oldScheduledFuture)) oldScheduledFuture.cancel(true);
        });
    }

    private ScheduledFuture<?> createScheduledFuture() {

        Integer frequency = RETRY_FREQ.getValue();
        LOGGER.info("MailBoxItems with 'RETRY' status will be check every {} seconds to be dispatched ", frequency);
        return executorService.scheduleAtFixedRate(() -> {
            LOGGER.info("Schedule is started at {}", OffsetDateTime.now());
            Optional<ExternalMailBoxItem> mailBoxItem;
            do {
                mailBoxItem = mailBoxService.findOneAndEnqueueForRetry();
                if (mailBoxItem.isPresent() && !mailBoxItem.get().hasSaveStatus()) {
                    LOGGER.info("MailBoxItems is being processed [{}] ", mailBoxItem.get().getSubject());
                    ExternalMailBoxItem retryItem = mailBoxItem.get();
                    executorService.execute(() -> {
                        mailSender.sendEmailRetry(retryItem);
                        LOGGER.info("MailBoxItems with 'RETRY' Done for {} ", retryItem.getSubject());
                        mailBoxService.saveAndDequeueFromRetry(retryItem);
                        LOGGER.info("MailBoxItems Dequeued for {} ", retryItem.getSubject());
                        if (retryItem.hasSaveStatus()) LOGGER.info("Retry successful for mailbox item: {}", retryItem);

                    });
                }
            } while (mailBoxItem.isPresent());
        }, frequency, frequency, SECONDS);
    }

    @Override
    public void close() throws IOException {
        ofNullable(scheduledFutureRef.get()).ifPresent(future -> future.cancel(true));
        executorService.shutdown();
    }
}
