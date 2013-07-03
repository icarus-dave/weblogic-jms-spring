package com.test.weblogic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import weblogic.jndi.Environment;
import weblogic.security.Security;
import weblogic.security.auth.Authenticate;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

/**
 * Common configuration for weblogic security beans
 */
public class WeblogicSecurityBean implements InitializingBean {
    Logger logger = LoggerFactory.getLogger(WeblogicSecurityBean.class);

    private String providerUrl;
    private String securityPrincipal;
    private String securityCredentials;
    private Subject subject;

    private boolean authenticated = false;

    public boolean isAuthenticated() {
        return authenticated;
    }

    public String getSecurityPrincipal() {
        return securityPrincipal;
    }

    public void setSecurityPrincipal(String securityPrincipal) {
        this.securityPrincipal = securityPrincipal;
    }

    public String getProviderUrl() {
        return providerUrl;
    }

    public void setProviderUrl(String providerUrl) {
        this.providerUrl = providerUrl;
    }

    public String getSecurityCredentials() {
        return securityCredentials;
    }

    public void setSecurityCredentials(String securityCredentials) {
        this.securityCredentials = securityCredentials;
    }

    public void afterPropertiesSet() {
        try {
            authenticate();
        } catch (IOException e) {
            logger.warn("Could not carry out initial authentication due to IOException, it will be re-attempted" +
                    " when an actual privileged action occurs",e);
        }
    }

    protected Subject getSubject() {
        return subject;
    }

    public synchronized void authenticate() throws IOException {
        if (authenticated) return;

        Environment environment = new Environment();
        environment.setProviderUrl(providerUrl);
        environment.setSecurityPrincipal(securityPrincipal);
        environment.setSecurityCredentials(securityCredentials);

        subject = new Subject();

        try {
            Authenticate.authenticate(environment, subject);
            authenticated = true;
        } catch (LoginException e) {
            throw new RuntimeException("JMS authentication failure", e);
        }
    }

    public void runPrivilegedExceptionActionAsSubject(PrivilegedExceptionAction privilegedExceptionAction) throws IOException, PrivilegedActionException {
        if (!authenticated) authenticate();
        Security.runAs(subject,privilegedExceptionAction);
    }

    public void runPrivilegedActionAsSubject(PrivilegedAction privilegedAction) throws IOException {
        if (!authenticated) authenticate();
        Security.runAs(subject,privilegedAction);
    }
}
