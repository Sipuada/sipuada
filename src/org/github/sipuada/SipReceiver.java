package org.github.sipuada;

import org.github.sipuada.requester.SipRequestVerb;
import org.github.sipuada.requester.SipResponseCode;
import org.github.sipuada.state.SipStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.javax.sip.DialogTerminatedEvent;
import android.javax.sip.IOExceptionEvent;
import android.javax.sip.RequestEvent;
import android.javax.sip.ResponseEvent;
import android.javax.sip.SipListener;
import android.javax.sip.TimeoutEvent;
import android.javax.sip.TransactionTerminatedEvent;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;

public class SipReceiver implements SipListener {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SipReceiver.class);
	private SipStateMachine stateMachine;
	
	public SipReceiver(SipStateMachine machine) {
		stateMachine = machine;
	}

	@Override
	public void processRequest(RequestEvent requestEvent) {
		Request request = requestEvent.getRequest();
		SipRequestVerb requestVerb = SipRequestVerb.valueOf(request.getMethod().toUpperCase());
		stateMachine.requestHasBeenReceived(requestVerb, requestEvent);
	}

	@Override
	public void processResponse(ResponseEvent responseEvent) {
		Response response = responseEvent.getResponse();
		if (!stateMachine.responseHasBeenReceived(response.getStatusCode(), responseEvent)) {
			int responseClass = (response.getStatusCode() / 100) * 1000;
			stateMachine.responseHasBeenReceived(responseClass, responseEvent);
		}
	}

	@Override
	public void processTimeout(TimeoutEvent timeoutEvent) {
		if (!stateMachine.responseHasBeenReceived(SipResponseCode.REQUEST_TIMEOUT, timeoutEvent)) {
			stateMachine.responseHasBeenReceived(SipResponseCode.ANY_CLIENT_ERROR, timeoutEvent);
		}
	}

	@Override
	public void processIOException(IOExceptionEvent exceptionEvent) {
		LOGGER.error("SipReceiver: IO exception: " + exceptionEvent.getTransport()
		+ " " + exceptionEvent.getHost() + ":" + exceptionEvent.getPort());
	}

	@Override
	public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {}

	@Override
	public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {}

}
