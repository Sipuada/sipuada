package org.github.sipuada;

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
import android.javax.sip.TimeoutEvent;
import android.javax.sip.TransactionTerminatedEvent;
import android.javax.sip.TransportNotSupportedException;

public class UserAgent implements SipListener {

	private final int MIN_PORT = 5000;
	private final int MAX_PORT = 6000;
	
	SipProvider provider;
	UserAgentClient uac;
	UserAgentServer uas;
	
	public UserAgent() {
		Properties properties = new Properties();
		properties.setProperty("android.javax.sip.STACK_NAME", "UserAgent");
		SipStack stack;
		try {
			stack = SipFactory.getInstance().createSipStack(properties);
			ListeningPoint listeningPoint;
			boolean listeningPointBound = false;
			int localPort = 5060;
			while (!listeningPointBound) {
				try {
					listeningPoint = stack.createListeningPoint("192.168.1.10", localPort, "TCP");
					listeningPointBound = true;
					try {
						provider = stack.createSipProvider(listeningPoint);
					} catch (ObjectInUseException e) {
						e.printStackTrace();
					}
				} catch (TransportNotSupportedException ignore) {
				} catch (InvalidArgumentException portUsed) {
					localPort = (int) ((MAX_PORT - MIN_PORT) * Math.random()) + MIN_PORT;
				}
			}
		} catch (PeerUnavailableException ignore) {}
		uac = new UserAgentClient(provider);
		uas = new UserAgentServer(provider);
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
	public void processTimeout(TimeoutEvent timeoutEvent) {
		uac.processTimeout(timeoutEvent);
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
