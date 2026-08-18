package com.dbs.edoc.notification.services.notification.external;

import com.dbs.edoc.config.DynamicStringProperty;
import com.dbs.edoc.crypto.CryptoUtilException;
import com.dbs.edoc.notification.error.ExchangeMessageException;
import com.dbs.edoc.notification.services.notification.Mail;
import com.dbs.edoc.notification.services.notification.ews.CloudMailExchangeService;
import com.dbs.edoc.notification.services.notification.ews.InternalMailExchangeService;
import com.google.gson.Gson;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExternalMailKeeperService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalMailKeeperService.class);
    private static final DynamicStringProperty MAIL_ACCESS_CREDENTIALS = new DynamicStringProperty("sender.mail.access.credentials", "");
    private final Map<String, List<String>> mailAccessCredentialsRef;
    private final Gson gson;
    private final CloudMailExchangeService cloudMailExchangeService;
    private final InternalMailExchangeService internalMailExchangeService;

    @Autowired
    public ExternalMailKeeperService(CloudMailExchangeService cloudMailExchangeService,
                                     InternalMailExchangeService internalMailExchangeService,
                                     Gson gson) {
        this.gson = gson;
        this.cloudMailExchangeService = cloudMailExchangeService;
        this.internalMailExchangeService = internalMailExchangeService;

        LOGGER.info("Initializing External Mail Keeper service");
        this.mailAccessCredentialsRef = populateMailAccessCredentials();
        LOGGER.info("Initializing completed for External Mail Keeper service");
    }

    public synchronized void saveMailToOutbox(Mail mail, byte[] attachment, String fileName) throws ExchangeMessageException, CryptoUtilException {

        String senderEmail = resolveSenderEmail(mail);
        LOGGER.info("Resolved Sender Email [{}]", senderEmail);

        final List<String> domainPasswordPair = mailAccessCredentialsRef.get(senderEmail.toLowerCase().trim());
        if (domainPasswordPair != null) {
            LOGGER.info("Mail Credentials found for Sender Email [{}] for the email [{}]", senderEmail, mail.getSubject());
            LOGGER.info("Saving Email [{}] into Sent Items of Email Box [{}]", mail.getSubject(), senderEmail);
            if (domainPasswordPair.get(2).equals("CLOUD")) {
                LOGGER.info("Sender Email [{}] is a cloud Email account. Handing over to Office365 Exchange Service", senderEmail);
                cloudMailExchangeService.saveMailToOutbox(senderEmail, domainPasswordPair, mail, attachment, fileName);
            } else {
                LOGGER.info("Sender Email [{}] is an on premise Email account. Handing over to simple Exchange Service", senderEmail);
                internalMailExchangeService.saveMailToOutbox(senderEmail, domainPasswordPair, mail, attachment, fileName);
            }
        } else {
            LOGGER.info("No Credentials found for Sender Mail [{}]. Will not be saving a copy to Sent Items", senderEmail);
        }
        LOGGER.info("Saved Email with subject [{}] to sent folder successfully", mail.getSubject());
    }

    private String resolveSenderEmail(Mail mail) {
        String senderEmail = mail.getSender();
        if (senderEmail.contains("<") && senderEmail.contains(">")) {
            senderEmail = StringUtils.substringBetween(senderEmail, "<", ">").trim();
        }
        return senderEmail.toLowerCase();

    }

    private Map<String, List<String>> populateMailAccessCredentials() {
        final Map<String, List<String>> map = gson.fromJson(MAIL_ACCESS_CREDENTIALS.get(), Map.class);
        if (map != null) {
            Map<String, List<String>> filteredMap = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                filteredMap.put(entry.getKey().toLowerCase().trim(), entry.getValue());
            }
            return filteredMap;
        }
        return Collections.emptyMap();
    }

}
