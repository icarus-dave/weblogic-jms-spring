package com.test.weblogic;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A simple MDB to receive messages to an internal list
 */
public class SimpleMDB implements MessageListener {

    public List<String> messages = Collections.synchronizedList(new ArrayList<String>());

    public void onMessage(Message message) {
        try {
            messages.add(((TextMessage)message).getText());
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }

}
