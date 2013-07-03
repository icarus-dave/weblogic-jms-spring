package com.test.weblogic;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.DelegateAsyncProcessor;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import org.apache.camel.test.spring.CamelSpringTestSupport;

import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.test.annotation.DirtiesContext;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import java.security.PrivilegedAction;

/**
 * A simple test to show that we can publish to a JMS topic using standalone Spring JMS components
 * and subscribe to a JMS topic using Camel JMS. The relevant settings are specified in defaults.properties
 */
public class SpringCamelPubSubTest extends CamelSpringTestSupport {
    public static final String[] CAMEL_CONTEXT_PATHS = {"META-INF/spring/spring.xml"};

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(CAMEL_CONTEXT_PATHS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                //A route to subscribe to the JMS destination and send through to a mock for testing
                from("jms:{{jms.destinationType}}:{{jms.destination}}")
                        .routeId("camelJMSSubscriber")
                        .to("mock:testJmsDestination");

                /*
                A route for publishing to a JMS destination
                I'm using a second route because I want to show how it reacts
                when we don't have control over the calling thread. There is an intercept
                strategy created in the Spring context that adds the security requirements before calling
                the endpoint.
                */
                from("direct:jms.test.input")
                        .routeId("camelJMSPublisher")
                        .to("jms:{{jms.destinationType}}:{{jms.destination}}");

                //to show we can get dynamic JMS destinations too
                from("direct:jms.test.dynamicRoute")
                        .routeId("camelJMSDynamicPublisher")
                        .recipientList(simple("jms:${properties:jms.destinationType}:${properties:jms.destination}"));
            }
        };
    }

    @DirtiesContext
    @Test
    public void pubCamelSubSpring() throws Exception {

        final String input = "pubCamelSubSpring";

        //make sure we're the only one listening to this queue
        DefaultMessageListenerContainer dmlc = this.applicationContext.getBean("simpleMessageListener",DefaultMessageListenerContainer.class);
        dmlc.start();
        context.stopRoute("camelJMSSubscriber");

        //this receives the message
        SimpleMDB simpleMDB = this.applicationContext.getBean("simpleMDB", SimpleMDB.class);

        //send it to JMS using the route above
        template.sendBody("direct:jms.test.input", input);

        //wait for message to arrive at the MDB
        Thread.sleep(5000);


        assertEquals(simpleMDB.messages.size(), 1);
        assertEquals(simpleMDB.messages.get(0), input);
    }

    @Test
    public void pubSpringSubCamel() throws Exception {

        final String input = "pubSpringSubCamel";

        //This bean will be used to carry out privilleged actions (sending to JMS)
        WeblogicSecurityBean weblogicSecurityBean = this.applicationContext.getBean("weblogicSecurityBean",WeblogicSecurityBean.class);

        //create requirements for the mock endpoint created above
        MockEndpoint mockEndpoint = getMockEndpoint("mock:testJmsDestination");
        mockEndpoint.expectedBodiesReceived(input);
        mockEndpoint.expectedMessageCount(1);

        //get the standard JMS template
        final JmsTemplate jmsTemplate = this.applicationContext.getBean("jmsTemplate", JmsTemplate.class);
        final String jmsDestination = this.context().resolvePropertyPlaceholders("{{jms.destination}}");

        final MessageCreator messageCreator = new MessageCreator() {
            @Override
            public Message createMessage(Session session) throws JMSException {
                return session.createTextMessage(input);
            }
        };

        //now publish the message as the authenticated user
        weblogicSecurityBean.runPrivilegedActionAsSubject(new PrivilegedAction() {
            @Override
            public Object run() {
                jmsTemplate.send(jmsDestination, messageCreator);
                return null;
            }
        });

        //now make sure we've received the message successfully
        mockEndpoint.assertIsSatisfied();
    }

    @Test
    public void pubCamelDynamicSubCamel() throws Exception {
        final String input = "pubCamelDynamicSubCamel";

        //create requirements for the mock endpoint created above
        MockEndpoint mockEndpoint = getMockEndpoint("mock:testJmsDestination");
        mockEndpoint.expectedBodiesReceived(input);
        mockEndpoint.expectedMessageCount(1);

        template.sendBody("direct:jms.test.dynamicRoute", input);

        mockEndpoint.assertIsSatisfied();

    }

}
