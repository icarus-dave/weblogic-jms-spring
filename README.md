WebLogic Server JMS Security for Spring and Camel
================================================

David MacDonald, July 2013

I've had quite a few problems combining Spring and Camel with WebLogic Server JMS due to the way that the thread based security model works with the WLS client. I've got some ways/workarounds for approaching this problem and provided test cases that illustrate how you can publish and subscribe to a JMS destination using both Camel and Spring.

I've tested this against Weblogic Server 10.3.6 using both standard and distributed destinations without problems, it will also recover when Weblogic Server fails and resurrects itself. It should also continue to work with transactions due to the fact that the same thread is used, however this hasn't been fully tested.

Getting Started
---------------
You'll first need to create a secured JMS destination on your WebLogic instance of choice. Unfortunately you'll have to do this a-priori to the tests because there's no reasonable scripted way to do this that I can be bothered with right now (grumble, grumble). Once you've done this, update the relevant default values in the file: _src/main/resources/META-INF/spring/defaults.properties_. You will also need to provide Maven with a location to a Weblogic client JAR file (this isn't in the central repositories... grumble, grumble); update the pom.xml dependency accordingly. Now you can run the tests using the standard `mvn test` command, where three tests are run:

* *pubCamelSubSpring* - this publishes a message to the secured JMS destination with Camel, and uses a stand-alone Spring JMS MessageListenerContainer to obtain the result
* *pubSpringSubCamel* - this uses the Spring JMS template to send a message to the secured JMS destination and uses the Camel JMS subscriber to pass the message through to a mock for testing
* *pubCamelDynamicSubCamel* - a quick Camel route to show how publishing to dynamic JMS routes works

How it Works
---------------
The main problem with using a JNDI context for Weblogic Server is that it's associated with a Thread, and this doesn't fly in the multi-threaded world we live in: [WLS Documentation](http://docs.oracle.com/cd/E13222_01/wls/docs61/jndi/jndi.html#467275). The alternative is that you need to authenticate a subject and use the WLS Security tooling to run a PrivilegedAction as this subject; this is exactly what the class _com.test.weblogic.WeblogicSecurityBean_ does. Exactly one instance is created in the Spring context file _src/main/resources/META-INF/spring/spring.xml_, and the initialization will authenticate the user and make itself available for carrying out PrivilegedAction calls. The following describes how we have to adjust the code to handle this change:

* **Publishing With Spring** - Using the WeblogicSecurityBean instance (created by Spring) the test pubSpringSubCamel runs the Spring JMS template using the code: 
```java
weblogicSecurityBean.runPrivilegedActionAsSubject(new PrivilegedAction() {
             @Override
             public Object run() {
                 jmsTemplate.send(jmsDestination, messageCreator);
                 return null;
             }
         });
```
* **Subscribing with Spring** - In this case we needed to create our own ThreadFactory _com.test.weblogic.WeblogicSecureThreadFactory_ that decorates the newThread Runnable with the PriviledgedAction; refer to the bean _weblogicThreadFactory_ in the Spring context. The intention here is that any thread listening for messages using DefaultMessageListenerContainer has permission to do so. Because we're using a non-standard thread factory we have to create a task executor ThreadPoolTaskExecutor instantiated as the bean _jmsTaskExecutor_, which is then assigned to the message listener container _simpleMessageListener_. This uses a MessageListener _com.test.weblogic.SimpleMDB_ for testing purposes
* **Subscribing with Camel** - This is very similar to the approach for subscribing with Spring (since they are the same technologies). In this case I set the taskExecutor parameter for the JmsConfiguration to _jmsTaskExecutor_
* **Publishing with Camel** - This proved to be the hardest to implement because I couldn't override the Camel thread factory for a custom executor service manager without copy/pasting a lot of code (DefaultExecutorServiceManager has set the method createThreadFactory to private, which would be better as protected). Furthermore, interception with delegated processors/interceptSendToEndpoint wasn't straight forward from what I could see. In the end I just created an Intercept Strategy _com.test.weblogic.WeblogicSecurityInterceptStrategy_ that adds the PrivilegedAction decoration to every target processor; this is expensive but I couldn't support dynamic routes by doing interpretation of the incoming processor. The intercept strategy is created as the Spring bean _jmsInterceptStrategy_ of which Camel automatically picks it up and adds it as a context-wide intercept strategy. One further improvement would be to add an intercept strategy to the route directly and therefore only muck up these processors

