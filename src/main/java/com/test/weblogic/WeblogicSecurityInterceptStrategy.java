package com.test.weblogic;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.spi.InterceptStrategy;

import java.security.PrivilegedExceptionAction;

/**
 * A basic intercept strategy that captures all callouts to weblogic JMS targets and ensures
 * it is wrapped in the appropriate security requirements
 */
public class WeblogicSecurityInterceptStrategy implements InterceptStrategy {

    private String weblogicComponentPrefix = "jms";
    private WeblogicSecurityBean weblogicSecurityBean;

    public void setWeblogicComponentPrefix(String weblogicComponentPrefix) {
        this.weblogicComponentPrefix = weblogicComponentPrefix;
    }

    public String getWeblogicComponentPrefix() {
        return weblogicComponentPrefix;
    }

    public WeblogicSecurityBean getWeblogicSecurityBean() {
        return weblogicSecurityBean;
    }

    public void setWeblogicSecurityBean(WeblogicSecurityBean weblogicSecurityBean) {
        this.weblogicSecurityBean = weblogicSecurityBean;
    }

    @Override
    public Processor wrapProcessorInInterceptors(CamelContext context, ProcessorDefinition<?> definition, final Processor target, Processor nextTarget) throws Exception {
        //ideally we'd be using a threadfactory but the Camel implementation can't be changed at the moment
        return new Processor() {
            @Override
            public void process(final Exchange exchange) throws Exception {
                weblogicSecurityBean.runPrivilegedExceptionActionAsSubject(new PrivilegedExceptionAction() {
                    @Override
                    public Object run() throws Exception {
                        target.process(exchange);
                        return null;
                    }
                });
            }
        };
    }
}
