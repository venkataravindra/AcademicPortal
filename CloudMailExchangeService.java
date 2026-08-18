package com.dbs.edoc.notification.services.notification.ews;

import com.azure.core.http.HttpClient;
import com.azure.core.http.ProxyOptions;
import com.azure.core.http.netty.NettyAsyncHttpClientBuilder;
import com.azure.core.http.policy.ExponentialBackoffOptions;
import com.azure.core.http.policy.RetryOptions;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.dbs.edoc.config.DynamicStringProperty;
import com.dbs.edoc.crypto.CryptoUtilException;
import com.dbs.edoc.notification.services.notification.Mail;
import com.microsoft.graph.models.*;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CloudMailExchangeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudMailExchangeService.class);
    private static final String STATIC_RESOURCE_PATH = "static";
    private static final String IMAGE_CONTENT_TYPE = "image/png";
    private static final String DBS_INDIA_LOGO = "dbsIndialogo";
    private static final String DBS_INDIA_LOGO_PNG = "dbsIndialogo.png";
    private static final String DBS_LOGO_CONTENT_ID = "dbslogo";
    private static final String DBS_LOGO_PNG = "dbs_logo.png";


    private static final DynamicStringProperty CLIENT_ID = new DynamicStringProperty("cloud.graph.client.id", "fe1570ca-4a02-4a92-a83b-11351edee2de");
    private static final DynamicStringProperty CLIENT_SECRET = new DynamicStringProperty("cloud.graph.client.secret", "");
    private static final DynamicStringProperty TENANT_ID = new DynamicStringProperty("cloud.graph.tenant.id", "278ad577-c008-4fc4-ad48-4467be94beb5");
    private static final DynamicStringProperty PROXY_PASSWORD = new DynamicStringProperty("cloud.graph.proxy.pwd", "");
    private static final DynamicStringProperty PROXY_USER = new DynamicStringProperty("cloud.graph.proxy.user", "gedocadprx01");

    private static final DynamicStringProperty GRAPH_DEFAULT_SCOPE = new DynamicStringProperty("cloud.graph.default.scope.url","https://graph.microsoft.com/.default");

    private GraphServiceClient graphServiceClient;

    public CloudMailExchangeService() {
        try {
            graphServiceClient = buildGraphClient();
            LOGGER.info("Initialized Cloud Mail Exchange Service");
        } catch (Exception ex) {
            LOGGER.error("Error occurred while initializing Cloud Mail Exchange Service", ex);
        }
    }

    private GraphServiceClient buildGraphClient() throws CryptoUtilException {
        LOGGER.info("Building GraphServiceClient...");
        ProxyOptions proxyOptions = buildProxyOptions();
        HttpClient httpClient = buildHttpClient(proxyOptions);
        ClientSecretCredential credential = buildCredential(httpClient);

        GraphServiceClient graphClient = new GraphServiceClient(credential, GRAPH_DEFAULT_SCOPE.getValue());
        LOGGER.info("GraphServiceClient created successfully");
        return graphClient;
    }

    private ProxyOptions buildProxyOptions() throws CryptoUtilException {
        LOGGER.info("Configuring proxy settings...");
        String proxy_password = PROXY_PASSWORD.getValue();
        String proxyHost = System.getProperty("https.proxyHost");
        String proxyPort = System.getProperty("https.proxyPort");
        if (StringUtils.isBlank(proxy_password)) {
            proxy_password = System.getenv().get("EDOC_PROXY_PASSWORD") != null ?System.getenv().get("EDOC_PROXY_PASSWORD"):"";
            LOGGER.info("proxy_password {} ", StringUtils.isNotBlank(proxy_password) ? proxy_password.length(): "EMPTY");
        }
        LOGGER.info("configured rawUser {} {} {} :", PROXY_USER.get(), proxyHost, proxyPort);
        if (StringUtils.isBlank(proxyHost) || StringUtils.isBlank(proxyPort)
                || StringUtils.isBlank(PROXY_USER.get()) || StringUtils.isBlank(proxy_password)) {
            LOGGER.info("No proxy configuration detected");
            throw new RuntimeException("Missing proxy");
        } else {
            LOGGER.info("Proxy configured with authentication: proxyHost {}: proxyPort {} : proxyUser {} :",
                    proxyHost, proxyPort,PROXY_USER.get());
        }
        try {
            int port = Integer.parseInt(proxyPort);
            ProxyOptions proxyOptions = new ProxyOptions(
                    ProxyOptions.Type.HTTP,
                    new InetSocketAddress(proxyHost, port)
            );
            proxyOptions.setCredentials(PROXY_USER.get(), proxy_password);
            return proxyOptions;
        } catch (NumberFormatException e) {
            LOGGER.error("Invalid proxy port number: {}", proxyPort, e);
            throw new RuntimeException("Invalid proxy port configuration", e);
        } catch (Exception e) {
            LOGGER.error("Failed to configure proxy: {}", e.getMessage(), e);
            throw new RuntimeException("Proxy configuration failed", e);
        }
    }

    private HttpClient buildHttpClient(ProxyOptions proxyOptions) {
        LOGGER.info("Building HttpClient with proxy configuration...");
        try {
            NettyAsyncHttpClientBuilder builder = new NettyAsyncHttpClientBuilder();
            if (proxyOptions != null) {
                builder.proxy(proxyOptions);
            }
            HttpClient httpClient = builder.build();
            LOGGER.info("HttpClient built successfully");
            return httpClient;
        } catch (Exception e) {
            LOGGER.error("Failed to build HttpClient: {}", e.getMessage(), e);
            throw new RuntimeException("HttpClient build failed", e);
        }
    }

    private ClientSecretCredential buildCredential(HttpClient httpClient) {
        String tenantId = TENANT_ID.getValue().trim();
        String clientId = CLIENT_ID.getValue().trim();
        String clientSecret = CLIENT_SECRET.getValue().trim();

        LOGGER.info("Building ClientSecretCredential with tenantId: {} clientId: {} secretLength: {}",
                tenantId, clientId, clientSecret.length());

        try {
            if (StringUtils.isBlank(tenantId) || StringUtils.isBlank(clientId) || StringUtils.isBlank(clientSecret)) {
                String errorMsg = String.format("Missing Azure credentials - tenantId: %s, clientId: %s, secret: %s",
                        StringUtils.isBlank(tenantId) ? "EMPTY" : "SET",
                        StringUtils.isBlank(clientId) ? "EMPTY" : "SET",
                        StringUtils.isBlank(clientSecret) ? "EMPTY" : "SET");
                LOGGER.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }

            ClientSecretCredentialBuilder credentialBuilder = new ClientSecretCredentialBuilder()
                    .tenantId(tenantId)
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .retryOptions(new RetryOptions(new ExponentialBackoffOptions()
                            .setMaxRetries(3)
                            .setBaseDelay(Duration.ofSeconds(2))
                            .setMaxDelay(Duration.ofMinutes(1))));

            if (httpClient != null) {
                credentialBuilder.httpClient(httpClient);
                LOGGER.info("HttpClient with proxy configuration applied to credential");
            }

            ClientSecretCredential credential = credentialBuilder.build();
            LOGGER.info("ClientSecretCredential created successfully");
            return credential;
        } catch (Exception e) {
            LOGGER.error("Failed to build ClientSecretCredential: {}", e.getMessage(), e);
            throw new RuntimeException("Credential build failed", e);
        }
    }

    public void saveMailToOutbox(String senderEmail, List<String> domainPasswordPair, Mail mail, byte[] attachment, String fileName) throws CryptoUtilException {
        Message messageToSave = getGraphRequestMessage(mail, attachment, fileName);
        GraphServiceClient graphServiceClient = buildGraphClient();
        LOGGER.info("Sending email from {} to recipients {} {} {}", mail.getSender(), mail.getTo(), mail.getBcc(), mail.getCc());
        graphServiceClient.users().byUserId(resolveSenderEmail(mail))
                .mailFolders()
                .byMailFolderId("sentitems")
                .messages()
                .post(messageToSave);
    }


    @NotNull
    private Message getGraphRequestMessage(Mail mail, byte[] attachment, String fileName) {
        // Create the message
        Message message = new Message();
        // Set basic message properties
        message.setSubject(mail.getSubject());
        message.setBody(createMessageBody(mail));
        boolean mailTo = Arrays.stream(mail.getTo())
                .allMatch(str -> str == null || str.isBlank());
        boolean mailCc = Arrays.stream(mail.getCc())
                .allMatch(str -> str == null || str.isBlank());
        boolean mailBcc = Arrays.stream(mail.getBcc())
                .allMatch(str -> str == null || str.isBlank());

        // Set recipients
        if (!mailTo) {
            LOGGER.info("mail.getTo() - {}", mail.getTo());
            message.setToRecipients(createRecipients(mail.getTo()));
        }
        if (!mailCc) {
            LOGGER.info("mail.getCc() - {}", mail.getCc());
            message.setCcRecipients(createRecipients(mail.getCc()));
        }
        if (!mailBcc) {
            LOGGER.info("mail.getBcc() - {}", mail.getBcc());
            message.setBccRecipients(createRecipients(mail.getBcc()));
        }
        // Add PDF attachment
        if (attachment != null) {
            FileAttachment fileAttachment = new FileAttachment();
            fileAttachment.setName(fileName);
            fileAttachment.setContentType("application/pdf");
            fileAttachment.setContentBytes(attachment);
            message.setAttachments(Arrays.asList(fileAttachment));
        }
        // Add inline images (logos) to message body
        List<Attachment> attachments = new ArrayList<>();
        if (message.getAttachments() != null) {
            attachments.addAll(message.getAttachments());
        }
        // Add DBS logo as inline attachment
        attachments.add(createInlineImageAttachment(DBS_LOGO_CONTENT_ID, DBS_LOGO_PNG));
        // Add DBS India logo as inline attachment
        attachments.add(createInlineImageAttachment(DBS_INDIA_LOGO, DBS_INDIA_LOGO_PNG));
        message.setAttachments(attachments);
        LOGGER.info("Sending external email with Attachments template: ");
        return message;
    }

    private String resolveSenderEmail(Mail mail) {
        String senderEmail = mail.getSender();
        if (senderEmail.contains("<") && senderEmail.contains(">")) {
            senderEmail = org.apache.commons.lang.StringUtils.substringBetween(senderEmail, "<", ">").trim();
        }
        return senderEmail.toLowerCase();

    }


    private ItemBody createMessageBody(Mail mail) {
        ItemBody body = new ItemBody();
        body.setContentType(mail.isAsHtml() ? BodyType.Html : BodyType.Text);
        body.setContent(mail.getBody());
        return body;
    }

    private List<Recipient> createRecipients(String[] emailAddresses) {
        return Arrays.stream(emailAddresses)
                .map(email -> {
                    Recipient recipient = new Recipient();
                    if(!email.trim().isEmpty()) {
                        EmailAddress emailAddress = new EmailAddress();
                        emailAddress.setAddress(email.trim());
                        recipient.setEmailAddress(emailAddress);
                    }
                    return recipient;
                })
                .collect(Collectors.toList());
    }

    private FileAttachment createInlineImageAttachment(String contentId, String fileName) {
        try {
            // Load image from classpath
            ClassPathResource resource = new ClassPathResource(Path.of(STATIC_RESOURCE_PATH, fileName).toString());
            byte[] imageBytes = resource.getInputStream().readAllBytes();

            FileAttachment attachment = new FileAttachment();
            attachment.setName(fileName);
            attachment.setContentType(IMAGE_CONTENT_TYPE);
            attachment.setContentBytes(imageBytes);
            attachment.setContentId(contentId);
            attachment.setIsInline(true);

            return attachment;
        } catch (IOException e) {
            LOGGER.error("Failed to load inline image: {}", fileName, e);
            throw new RuntimeException("Failed to load inline image", e);
        }
    }
}
