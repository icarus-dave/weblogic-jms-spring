package com.test.weblogic;

import java.io.IOException;
import java.security.PrivilegedAction;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A simple thread factory that ensures all threads are executed with simple
 * user credentials
 */
public class WeblogicSecureThreadFactory implements ThreadFactory {
    private String name;
    private boolean daemon = true;

    private static AtomicLong threadCounter = new AtomicLong();
    private WeblogicSecurityBean weblogicSecurityBean;

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDaemon() {
        return daemon;
    }

    public void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    public String getName() {
        return name;
    }

    public WeblogicSecurityBean getWeblogicSecurityBean() {
        return weblogicSecurityBean;
    }

    public void setWeblogicSecurityBean(WeblogicSecurityBean weblogicSecurityBean) {
        this.weblogicSecurityBean = weblogicSecurityBean;
    }


    public String toString() {
        if (name != null) return "WeblogicSecureThreadFactory[" + name + "]";
        else return "WeblogicSecureThreadFactory";
    }

    public Thread newThread(final Runnable runnable) {

        long counter = threadCounter.getAndIncrement();
        String threadName = "WeblogicSecureThreadFactory Thread " + counter + " - " + name;

        Runnable proxyCreds = new Runnable() {
            @Override
            public void run() {
                try {
                    weblogicSecurityBean.runPrivilegedActionAsSubject(new PrivilegedAction() {
                        @Override
                        public Object run() {
                            runnable.run();
                            return null;
                        }
                    });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        Thread answer = new Thread(proxyCreds, threadName);
        answer.setDaemon(daemon);

        return answer;

    }

}
