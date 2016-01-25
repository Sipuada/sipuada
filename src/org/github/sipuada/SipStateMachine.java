package org.github.sipuada;

import org.github.sipuada.events.SendRequestEvent;
import org.github.sipuada.events.SendResponseEvent;

import android.javax.sip.message.Request;
import android.javax.sip.message.Response;

public abstract class SipStateMachine {
	
	public abstract State getState();

	public abstract void requestSent(RequestVerb requestVerb);
	public abstract void responseSent(ResponseCode responseCode, Response response);

	public abstract void requestReceived(RequestVerb requestVerb, Request request);
	public abstract void responseReceived(ResponseCode responseCode, Response response);
	
	//TODO make all 4 methods above call computeNextStep below, keep them as the public interface
	//TODO the 2 methods above shall each call their corresponding handleNewStepAfter*Received method too
	
	private void computeNextStep() {
		
	}
	
	private void handleNewStepAfterRequestReceived(RequestVerb requestVerb, Request request, State newState) {
		//...//
		if (requestVerb == RequestVerb.INVITE && newState == State.INCOMING) {
			responseMustBeSent(new SendResponseEvent(ResponseCode.RINGING, request));
		}
		//...//
	}

	private void handleNewStepAfterResponseReceived(int responseCode, Response response, State newState) {
		//...//
		boolean authIsRequired = responseCode == ResponseCode.PROXY_AUTHENTICATION_REQUIRED
				|| responseCode == ResponseCode.UNAUTHORIZED;
		boolean currentStateIsRegisteringOrCalling = (newState == State.REGISTERING || newState == State.CALLING);
		if (authIsRequired && currentStateIsRegisteringOrCalling) {
			requestMustBeSent(new SendRequestEvent(RequestVerb.INVITE, response));
		}
		//...//
	}

	private void requestMustBeSent(SendRequestEvent event) {
		Sipuada.getEventBus().post(event);
	}

	private void responseMustBeSent(SendResponseEvent event) {
		Sipuada.getEventBus().post(event);
	}

}
