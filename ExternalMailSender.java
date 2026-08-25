package com.dbs.edoc.notification.services.notification.external;

import com.dbs.edoc.config.DynamicIntProperty;
import com.dbs.edoc.config.DynamicStringListProperty;
import com.dbs.edoc.config.DynamicStringProperty;
import com.dbs.edoc.notification.api.notification.NotificationResponse;
import com.dbs.edoc.notification.api.util.DocumentEmailTemplate;
import com.dbs.edoc.notification.domain.entity.ExternalMailBoxItem;
import com.dbs.edoc.notification.error.MailSendingRuntimeException;
import com.dbs.edoc.notification.services.notification.DocumentEncryptionUtil;
import com.dbs.edoc.notification.services.notification.Mail;
import com.dbs.edoc.notification.services.notification.MessageUtils;
import com.dbs.edoc.notification.util.NonEdocUtils;
import com.dbs.edoc.notification.web.context.HttpReqContextHolder;
import com.dbs.edoc.s3.DownloadResponse;
import com.dbs.edoc.s3.S3Client;
import jakarta.activation.DataSource;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.util.ByteArrayDataSource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;


@Component
public class ExternalMailSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalMailSender.class);
    private static final String DEFAULT_TIMEOUT = "60000";
    private static final DynamicStringProperty SMTP_HOST = new DynamicStringProperty("smtp.hostname", "localhost");
    private static final DynamicIntProperty SMTP_PORT = new DynamicIntProperty("smtp.port", 9212);
    private static final DynamicStringProperty SMTP_USERNAME = new DynamicStringProperty("smtp.username", "");
    private static final DynamicStringProperty SMTP_PASSWORD = new DynamicStringProperty("smtp.password", "");
    private static final DynamicStringProperty SMTP_CONNECT_TMOUT = new DynamicStringProperty("mail.smtp.connectiontimeout", DEFAULT_TIMEOUT);
    private static final DynamicStringProperty SMTP_READ_TMOUT = new DynamicStringProperty("mail.smtp.timeout", DEFAULT_TIMEOUT);
    private static final DynamicStringProperty SMTP_WRITE_TMOUT = new DynamicStringProperty("mail.smtp.writetimeout", DEFAULT_TIMEOUT);
    private static final DynamicStringListProperty RETRY_EXCEPTIONS = new DynamicStringListProperty("smtp.retry.exception.list",
            Arrays.asList("org.springframework.mail.MailSendException", "org.springframework.jms.JmsException", "com.dbs.edoc.notification.error.ExchangeMessageException"));
    private static final String IMAGE_CONTENT_TYPE = "image/png";
    private static final String STATIC_RESOURCE_PATH = "static";
    private static final String ENCODING_VALUE = "UTF-8";
    private static final String EMAIL_SENT_SUCCESSFULLY = "Email sent successfully";
    private static final String EMAIL_SAVED_TO_INTERNAL_MAILBOX_SUCCESSFULLY = "Email saved to external mailbox successfully: {}";
    private static final String ERROR_SENDING_EMAIL = "Error sending email";
    private static final String DBS_LOGO_CONTENT_ID = "dbslogo";
    private static final String DBS_LOGO_PNG = "dbs_logo.png";
    private static final String DOC_EMAIL_ITEMTYPE = "DOC_EMAIL";
    private static final String RETRY_SAVE_STATUS = "RETRY_SAVE";
    private static final String SENT_STATUS = "SENT";
    private static final String DEFAULT_ENTITY = "DEFAULT";

    private static final String DBS_INDIA_LOGO = "dbsIndialogo";

    private static final String DBS_INDIA_LOGO_PNG = "dbsIndialogo.png";

    private final AtomicReference<JavaMailSender> mailSenderRef;
    private final ExternalMailBoxService mailBoxService;
    private ExternalMailKeeperService mailKeeperService;
    private DocumentEncryptionUtil encryptionUtil;

    private S3Client s3Client;

    @Autowired
    public ExternalMailSender(ExternalMailBoxService externalMailBoxService,
                              ExternalMailKeeperService externalMailKeeperService,
                              S3Client s3Client,
                              DocumentEncryptionUtil encryptionUtil) {
        this.mailBoxService = externalMailBoxService;
        this.mailKeeperService = externalMailKeeperService;
        this.s3Client = s3Client;
        this.encryptionUtil = encryptionUtil;
        this.mailSenderRef = new AtomicReference<>(createMailSenderInstance());
        final Runnable recreateMailSenderFunction = () -> mailSenderRef.set(createMailSenderInstance());
        SMTP_PORT.addCallback(recreateMailSenderFunction);
        SMTP_USERNAME.addCallback(recreateMailSenderFunction);
        SMTP_HOST.addCallback(recreateMailSenderFunction);
    }

    NotificationResponse send(String templateName, Mail mail) {
        ExternalMailBoxItem mailBoxItem = mailBoxService.save(createMailBoxItem(ExternalMailBoxItem.emailMailboxItem(templateName), mail));
        mailBoxItem.itemTypeNotificationEmail();
        try {
            JavaMailSender mailSender = mailSenderRef.get();
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage,true, ENCODING_VALUE);
            MessageUtils.updateFrom(mail, helper);
            LOGGER.info("Sending external template: {}", templateName);
            helper.addInline(DBS_LOGO_CONTENT_ID, new ClassPathResource(Path.of(STATIC_RESOURCE_PATH, DBS_LOGO_PNG).toString()), IMAGE_CONTENT_TYPE);
            mailSender.send(mimeMessage);
            LOGGER.info(EMAIL_SENT_SUCCESSFULLY);
            mailBoxService.save(mailBoxItem.sentStatus());
            LOGGER.info(EMAIL_SAVED_TO_INTERNAL_MAILBOX_SUCCESSFULLY, mailBoxItem.getId());
            return NotificationResponse.success();
        } catch (Exception e) {
            LOGGER.error(ERROR_SENDING_EMAIL, e);
            handleExceptionOnSend(mailBoxItem, e);
            return NotificationResponse.error(e.getMessage());
        }
    }

    public NotificationResponse sendMailWithAttachments(String templateName, Mail mail, byte[] attachment, DocumentEmailTemplate request) {
        ExternalMailBoxItem externalMailBoxItem = buildMailBoxDocId(createMailBoxItem(ExternalMailBoxItem.emailMailboxItem(templateName), mail), request);
        ExternalMailBoxItem mailBoxItem = mailBoxService.save(externalMailBoxItem);
        mailBoxItem.itemTypeDocEmail();
        try {
            JavaMailSender mailSender = mailSenderRef.get();
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, ENCODING_VALUE);
            MessageUtils.updateFrom(mail, helper);
            DataSource dataSource = new ByteArrayDataSource(attachment, "application/pdf");
            helper.addAttachment(MimeUtility.encodeWord(request.getFile()), dataSource);
            helper.addInline(DBS_LOGO_CONTENT_ID, new ClassPathResource(Path.of(STATIC_RESOURCE_PATH, DBS_LOGO_PNG).toString()));
            helper.addInline(DBS_INDIA_LOGO, new ClassPathResource(Path.of(STATIC_RESOURCE_PATH, DBS_INDIA_LOGO_PNG).toString()));
            LOGGER.info("Sending external email with Attachments template: {}", templateName);
            mailSender.send(mimeMessage);
            mailBoxService.save(mailBoxItem.sentStatus());
            LOGGER.info(EMAIL_SENT_SUCCESSFULLY);
        } catch (Exception e) {
            LOGGER.error(ERROR_SENDING_EMAIL, e);
            handleExceptionOnSend(mailBoxItem, e);
            return NotificationResponse.error(e.getMessage());
        }
        return NotificationResponse.success();

    }

    NotificationResponse sendWelcomeNotification(String templateName, Mail mail, String entityCode) {
        ExternalMailBoxItem mailBoxItem = mailBoxService.save(createMailBoxItem(ExternalMailBoxItem.emailMailboxItem(templateName), mail));
        mailBoxItem.itemTypeWelcomeEmail();
        try {
            JavaMailSender mailSender = mailSenderRef.get();
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, ENCODING_VALUE);
            MessageUtils.updateFrom(mail, helper);
            helper.addInline(DBS_LOGO_CONTENT_ID, new ClassPathResource(Path.of(STATIC_RESOURCE_PATH, DBS_LOGO_PNG).toString()), IMAGE_CONTENT_TYPE);
            helper.addInline("dbsbg", new ClassPathResource(Path.of(STATIC_RESOURCE_PATH, "dbs_bg.png").toString()), IMAGE_CONTENT_TYPE);
            helper.addInline("dbsaward", new ClassPathResource(Path.of(STATIC_RESOURCE_PATH, "dbs_award.png").toString()), IMAGE_CONTENT_TYPE);
            helper.addInline("passrevamp", new ClassPathResource(Path.of(STATIC_RESOURCE_PATH, getPasswordMailImageName(entityCode)).toString()), IMAGE_CONTENT_TYPE);
            helper.addInline(DBS_INDIA_LOGO, new ClassPathResource(Path.of(STATIC_RESOURCE_PATH, DBS_INDIA_LOGO_PNG).toString()), IMAGE_CONTENT_TYPE);
            LOGGER.info("Sending Welcome email notification template: {}", templateName);
            mailSender.send(mimeMessage);
            LOGGER.info(EMAIL_SENT_SUCCESSFULLY);
            mailBoxService.save(mailBoxItem.sentStatus());
            LOGGER.info(EMAIL_SAVED_TO_INTERNAL_MAILBOX_SUCCESSFULLY, mailBoxItem.getId());
            return NotificationResponse.success();
        } catch (Exception e) {
            LOGGER.error(ERROR_SENDING_EMAIL, e);
            handleExceptionOnSend(mailBoxItem, e);
            return NotificationResponse.error(e.getMessage());
        }
    }

    private void handleExceptionOnSend(ExternalMailBoxItem mailBoxItem, Exception e) {
        if (isRetryException(e)) {
            mailBoxService.save(mailBoxItem.retryStatus(e.toString()));
        } else {
            mailBoxService.save(mailBoxItem.errorStatus(e.toString()));
        }
        throw new MailSendingRuntimeException(e);
    }

    private void handleExceptionOnRetry(ExternalMailBoxItem mailBoxItem, Exception e) {
        if (isRetryException(e)) {
            if(DOC_EMAIL_ITEMTYPE.equalsIgnoreCase(mailBoxItem.getItemTypeString())
                    && (SENT_STATUS.equalsIgnoreCase(mailBoxItem.getStatusValue()) || RETRY_SAVE_STATUS.equalsIgnoreCase(mailBoxItem.getStatusValue()))) {
                LOGGER.error("handleExceptionOnRetry - update DOC_EMAIL retry status and retry count for subject {} ",mailBoxItem.getSubject());
                mailBoxItem.newRetryAttempt().retrySaveStatus(e.toString());
            }  else {
                LOGGER.error("handleExceptionOnRetry - update OTHER MAIL retry status and retry count for subject {}", mailBoxItem.getSubject());
                mailBoxItem.newRetryAttempt().retryStatus(e.toString());
            }
        } else {
            LOGGER.error("handleExceptionOnRetry - update error status and retry count for {}", mailBoxItem.getSubject());
            mailBoxItem.errorStatus(e.toString()).newRetryAttempt();
        }
    }

    private boolean isRetryException(Exception e) {
        for (String exClass : RETRY_EXCEPTIONS.getValue()) {
            try {
                Class<?> retryClass = Class.forName(exClass);
                if (retryClass.isInstance(e)) {
                    return true;
                }
            } catch (Exception e1) {
                LOGGER.error("Invalid class '{}' in property {}", exClass, RETRY_EXCEPTIONS.getName());
            }
        }

        LOGGER.warn("Configured retry exception class NOT found for exception '{}'", e.getClass().getName());
        return false;
    }

    private JavaMailSender createMailSenderInstance() {
        String hostname = SMTP_HOST.getValue();
        int port = SMTP_PORT.getValue();
        String username = SMTP_USERNAME.get();
        String password = SMTP_PASSWORD.get();
        Properties properties = System.getProperties();
        properties.put("mail.mime.splitlongparameters", "false");
        properties.put("mail.mime.encodefilename", "true");
        properties.put("mail.mime.encodeparameters", "false");
        properties.put("mail.mime.charset", ENCODING_VALUE);
        properties.put(SMTP_CONNECT_TMOUT.getName(), SMTP_CONNECT_TMOUT.getValue());
        properties.put(SMTP_READ_TMOUT.getName(), SMTP_READ_TMOUT.getValue());
        properties.put(SMTP_WRITE_TMOUT.getName(), SMTP_WRITE_TMOUT.getValue());
        LOGGER.info("Create Mail Sender instance with: hostname: '{}', port: '{}'", hostname, port);
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setJavaMailProperties(properties);
        mailSender.setHost(hostname);
        mailSender.setPort(port);

        if (StringUtils.isNotBlank(username)) {
            LOGGER.info("External Mail Sender will use configured username '{}'", username);
            mailSender.setUsername(username);
        }
        if (StringUtils.isNotBlank(password)) {
            LOGGER.info("External Mail Sender will use configured password '******'");
            mailSender.setPassword(password);
        }

        try {
            mailSender.testConnection();
            LOGGER.info("SMPT mail server connectivity test successful!");
        } catch (Exception e) {
            LOGGER.error("SMPT mail server connectivity test failed!", e);
        }
        return mailSender;
    }

    private ExternalMailBoxItem createMailBoxItem(ExternalMailBoxItem mailBoxItem, Mail mail) {
        mailBoxItem.setSender(mail.getSender());
        if (Objects.nonNull(mail.getTo())) {
            mailBoxItem.setRecipients(String.join(",", mail.getTo()));
        }
        if (Objects.nonNull(mail.getCc())) {
            mailBoxItem.setRecipientsCc(String.join(",", mail.getCc()));
        }
        if (Objects.nonNull(mail.getBcc())) {
            mailBoxItem.setRecipientsBcc(String.join(",", mail.getBcc()));
        }
        mailBoxItem.setSubject(mail.getSubject());
        mailBoxItem.setBody(mail.getBody());
        mailBoxItem.setHtmlBody(mail.isAsHtml());
        mailBoxItem.setRequestId(HttpReqContextHolder.getHttpRequestId());
        return mailBoxItem;
    }

    void sendEmailRetry(ExternalMailBoxItem mailBoxItem){
        LOGGER.info("***RETRYING*** external email with id : [{}] and subject: [{}] and status [{}}", mailBoxItem.getId(), mailBoxItem.getSubject(), mailBoxItem.getStatus());
        try {
            JavaMailSender mailSender = mailSenderRef.get();
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage,true, ENCODING_VALUE);
            MessageUtils.updateFrom(mailBoxItem, helper);

            if("NOTIFICATION_EMAIL".equalsIgnoreCase(mailBoxItem.getItemTypeString())) {
                helper.addInline(DBS_LOGO_CONTENT_ID, new ClassPathResource(Path.of(STATIC_RESOURCE_PATH, DBS_LOGO_PNG).toString()), IMAGE_CONTENT_TYPE);
                mailSender.send(mimeMessage);
                LOGGER.info(EMAIL_SENT_SUCCESSFULLY);
                mailBoxItem.sentStatus().newRetryAttempt().sentStatus();
                LOGGER.info(EMAIL_SAVED_TO_INTERNAL_MAILBOX_SUCCESSFULLY, mailBoxItem);
            }
            else if("WELCOME_EMAIL".equalsIgnoreCase(mailBoxItem.getItemTypeString())) {
                helper.addInline(DBS_LOGO_CONTENT_ID, new ClassPathResource(Path.of(STATIC_RESOURCE_PATH, DBS_LOGO_PNG).toString()), IMAGE_CONTENT_TYPE);
                helper.addInline("dbsbg", new ClassPathResource(Path.of(STATIC_RESOURCE_PATH, "dbs_bg.png").toString()), IMAGE_CONTENT_TYPE);
                helper.addInline("dbsaward", new ClassPathResource(Path.of(STATIC_RESOURCE_PATH, "dbs_award.png").toString()), IMAGE_CONTENT_TYPE);
                helper.addInline(DBS_INDIA_LOGO, new ClassPathResource(Path.of(STATIC_RESOURCE_PATH, DBS_INDIA_LOGO_PNG).toString()), IMAGE_CONTENT_TYPE);
                mailSender.send(mimeMessage);
                LOGGER.info(EMAIL_SENT_SUCCESSFULLY);
                mailBoxItem.sentStatus().newRetryAttempt().sentStatus();
                LOGGER.info(EMAIL_SAVED_TO_INTERNAL_MAILBOX_SUCCESSFULLY, mailBoxItem);
            }
            else if(DOC_EMAIL_ITEMTYPE.equalsIgnoreCase(mailBoxItem.getItemTypeString())) {

                DownloadResponse downloadResponse = s3Client.download(mailBoxItem.getS3Url());
                if (StringUtils.isNotBlank(downloadResponse.errorMessage))
                    throw new IOException(downloadResponse.errorMessage);
                byte[] documentContent = downloadResponse.content;
                String fileName = downloadResponse.filename;
                byte[] encryptedDoc = encryptionUtil.protectDocument(mailBoxItem.getDocumentKey(), documentContent, fileName);

                DataSource dataSource = new ByteArrayDataSource(encryptedDoc, "application/pdf");
                helper.addAttachment(MimeUtility.encodeWord(fileName), dataSource);
                helper.addInline(DBS_LOGO_CONTENT_ID, new ClassPathResource(Path.of(STATIC_RESOURCE_PATH, DBS_LOGO_PNG).toString()));
                helper.addInline(DBS_INDIA_LOGO, new ClassPathResource(Path.of(STATIC_RESOURCE_PATH, DBS_INDIA_LOGO_PNG).toString()));

                if(RETRY_SAVE_STATUS.equalsIgnoreCase(mailBoxItem.getStatusValue())
                        || SENT_STATUS.equalsIgnoreCase(mailBoxItem.getStatusValue())){
                    LOGGER.info("***RETRY_SAVE_STATUS*** - saving email for [{}]", mailBoxItem.getSubject());
                    mailKeeperService.saveMailToOutbox(MessageUtils.mailFrom(mailBoxItem), encryptedDoc, fileName);
                    mailBoxItem.saveStatus().newRetryAttempt().saveStatus();
                } else {
                    LOGGER.info("***Retrying*** - sending email for [{}] and saving ", mailBoxItem.getSubject());
                    mailSender.send(mimeMessage);
                    mailBoxItem.sentStatus().newRetryAttempt().sentStatus();
                    mailKeeperService.saveMailToOutbox(MessageUtils.mailFrom(mailBoxItem), encryptedDoc, fileName);
                    mailBoxItem.saveStatus().newRetryAttempt().saveStatus();
                }
            }

        } catch (Exception e) {
            LOGGER.error(" ***RETRYING*** - Exception external email: ", e);
            handleExceptionOnRetry(mailBoxItem, e);
        }
    }

    private ExternalMailBoxItem buildMailBoxDocId(ExternalMailBoxItem externalMailBoxItem, DocumentEmailTemplate request) {
        externalMailBoxItem.setDocId(Long.valueOf(request.getParameters().get("docId").toString()));
        externalMailBoxItem.setS3Url((String)request.getParameters().get("s3Url"));
        externalMailBoxItem.setDocumentKey((String)request.getParameters().get("documentKey"));
        return externalMailBoxItem;
    }

    private String getPasswordMailImageName(String entity) {
        String defaultImageName = "PasswordRevamp.png";
        String imageName;
        if (StringUtils.isBlank(entity) || DEFAULT_ENTITY.equalsIgnoreCase(entity)) {
            imageName = defaultImageName;
        }
        else {
            String convertedEntity = NonEdocUtils.getEdocEntityFromStaticEntity(entity);
            imageName = StringUtils.isBlank(convertedEntity) ? defaultImageName : ("PasswordRevamp_" + convertedEntity + ".png");
            ClassPathResource imagePath = new ClassPathResource(Path.of(STATIC_RESOURCE_PATH, imageName).toString());
            if (!imagePath.exists()) {
                imageName = defaultImageName;
            }
        }
        LOGGER.info("Entity: {}, password mail image: {}", entity, imageName);
        return imageName;
    }

}
