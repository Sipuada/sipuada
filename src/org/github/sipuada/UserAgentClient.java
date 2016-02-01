package org.github.sipuada;

import java.util.ListIterator;

import org.github.sipuada.Constants.ResponseClass;

import android.javax.sip.ClientTransaction;
import android.javax.sip.Dialog;
import android.javax.sip.IOExceptionEvent;
import android.javax.sip.InvalidArgumentException;
import android.javax.sip.ResponseEvent;
import android.javax.sip.SipException;
import android.javax.sip.SipProvider;
import android.javax.sip.TimeoutEvent;
import android.javax.sip.header.CSeqHeader;
import android.javax.sip.header.ViaHeader;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;

public class UserAgentClient {

	private final SipProvider provider;

	public UserAgentClient(SipProvider sipProvider) {
		provider = sipProvider;
	}
	
	public void sendRequest() {
		
	}
	
	public void processResponse(ResponseEvent responseEvent) {
		ClientTransaction clientTransaction = responseEvent.getClientTransaction();
		if (clientTransaction != null) {
			Response response = responseEvent.getResponse();
			int statusCode = response.getStatusCode();
			handleResponse(statusCode, response, clientTransaction);
		}
	}

	public void processTimeout(TimeoutEvent timeoutEvent) {
		if (!timeoutEvent.isServerTransaction()) {
			ClientTransaction clientTransaction = timeoutEvent.getClientTransaction();
			handleResponse(Response.REQUEST_TIMEOUT, null, clientTransaction);
		}
	}

	public void processFatalTransportError(IOExceptionEvent exceptionEvent) {
		handleResponse(Response.SERVICE_UNAVAILABLE, null, null);
	}
	
	private void handleResponse(int statusCode, Response response, ClientTransaction clientTransaction) {
		if (tryHandlingResponseGenerically(statusCode, response, clientTransaction)) {
			switch (Constants.getRequestMethod(clientTransaction.getRequest().getMethod())) {
				case INVITE:
					handleInviteResponse(statusCode, clientTransaction);
					break;
				case UNKNOWN:
				default:
					break;
			}
		}
	}
	
	private boolean tryHandlingResponseGenerically(int statusCode, Response response,
			ClientTransaction clientTransaction) {
		@SuppressWarnings("rawtypes")
		ListIterator iterator = response.getHeaders(ViaHeader.NAME);
		int count = 0;
		while (iterator.hasNext()) {
			iterator.next();
			count++;
		}
		if (count >= 1) {
			//Simply discarding the response (https://tools.ietf.org/html/rfc3261#section-8.1.3.3).
			return false;
		}
		switch (statusCode) {
			case Response.REQUEST_TIMEOUT:
			case Response.SERVICE_UNAVAILABLE:
				//No method-specific handling is required.
				//TODO handle these situations.
				return false;
			case Response.BAD_REQUEST:
			case Response.TRYING:
				//FIXME what about these? Is a method-specific handling required?
				//TODO handle these situations.
		}
		switch (Constants.getResponseClass(statusCode)) {
			case PROVISIONAL:
			case SUCCESS:
			case REDIRECT:
			case CLIENT_ERROR:
			case SERVER_ERROR:
			case GLOBAL_ERROR:
			case UNKNOWN:
		}
		return true;
	}

	private void handleInviteResponse(int statusCode, ClientTransaction clientTransaction) {
		if (ResponseClass.SUCCESS == Constants.getResponseClass(statusCode)) {
			Dialog dialog = clientTransaction.getDialog();
			if (dialog != null) {
				try {
					CSeqHeader cseqHeader = (CSeqHeader) clientTransaction
							.getRequest().getHeader(CSeqHeader.NAME);
					Request ackRequest = dialog.createAck(cseqHeader.getSeqNumber());
					dialog.sendAck(ackRequest);
				} catch (InvalidArgumentException ignore) {
				} catch (SipException ignore) {}
			}
		}
	}

}
