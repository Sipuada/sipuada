package org.github.sipuada;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import android.javax.sip.DialogTerminatedEvent;
import android.javax.sip.IOExceptionEvent;
import android.javax.sip.InvalidArgumentException;
import android.javax.sip.ListeningPoint;
import android.javax.sip.ObjectInUseException;
import android.javax.sip.PeerUnavailableException;
import android.javax.sip.RequestEvent;
import android.javax.sip.ResponseEvent;
import android.javax.sip.SipFactory;
import android.javax.sip.SipListener;
import android.javax.sip.SipProvider;
import android.javax.sip.SipStack;
import android.javax.sip.Timeout;
import android.javax.sip.TimeoutEvent;
import android.javax.sip.TransactionTerminatedEvent;
import android.javax.sip.TransportNotSupportedException;
import android.javax.sip.address.AddressFactory;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.message.MessageFactory;

public class UserAgent implements SipListener {

	private final int MIN_PORT = 5000;
	private final int MAX_PORT = 6000;

	private final Map<String, Map<String, String>> noncesCache;
	{
		noncesCache = new HashMap<>();
	}
	
	private UserAgentClient uac;
	
	public UserAgentClient getUac() {
		return uac;
	}

	public UserAgentServer getUas() {
		return uas;
	}

	private UserAgentServer uas;

	public UserAgent(String username, String domain, String password,
			String localIp, int localPort, String transport) {
		SipProvider provider;
		MessageFactory messenger;
		HeaderFactory headerMaker;
		AddressFactory addressMaker;
		Properties properties = new Properties();
		properties.setProperty("android.javax.sip.STACK_NAME", "UserAgent");
		SipStack stack;
		try {
			SipFactory factory = SipFactory.getInstance();
			stack = factory.createSipStack(properties);
			messenger = factory.createMessageFactory();
			headerMaker = factory.createHeaderFactory();
			addressMaker = factory.createAddressFactory();
			ListeningPoint listeningPoint;
			boolean listeningPointBound = false;
			while (!listeningPointBound) {
				try {
					listeningPoint = stack
							.createListeningPoint(localIp, localPort, transport);
					listeningPointBound = true;
					try {
						provider = stack.createSipProvider(listeningPoint);
						uac = new UserAgentClient(provider, messenger,
								headerMaker, addressMaker, noncesCache,
								username, domain, password,
								localIp, Integer.toString(localPort), transport);
						uas = new UserAgentServer(provider, messenger, headerMaker);
					} catch (ObjectInUseException e) {
						e.printStackTrace();
					}
				} catch (TransportNotSupportedException ignore) {
				} catch (InvalidArgumentException portUsed) {
					localPort = (int) ((MAX_PORT - MIN_PORT) * Math.random()) + MIN_PORT;
				}
			}
		} catch (PeerUnavailableException ignore) {}
	}
	
	@Override
	public void processRequest(RequestEvent requestEvent) {
		uas.processRequest(requestEvent);
	}

	@Override
	public void processResponse(ResponseEvent responseEvent) {
		uac.processResponse(responseEvent);
	}

	@Override
	public void processTimeout(TimeoutEvent timeoutOrRetransmissionEvent) {
		if (timeoutOrRetransmissionEvent.getTimeout() == Timeout.TRANSACTION) {
			uac.processTimeout(timeoutOrRetransmissionEvent);
		}
		else if (timeoutOrRetransmissionEvent.getTimeout() == Timeout.RETRANSMIT) {
			uas.processRetransmission(timeoutOrRetransmissionEvent);
		}
	}

	@Override
	public void processIOException(IOExceptionEvent exceptionEvent) {
		uac.processFatalTransportError(exceptionEvent);
	}

	@Override
	public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
		
	}

	@Override
	public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
		
	}

}
