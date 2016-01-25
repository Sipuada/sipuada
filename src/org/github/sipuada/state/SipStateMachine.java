package org.github.sipuada.state;

import org.github.sipuada.RequestVerb;
import org.github.sipuada.ResponseCode;
import org.github.sipuada.State;
import org.github.sipuada.events.SendRequestEvent;
import org.github.sipuada.events.SendResponseEvent;

import android.javax.sip.message.Message;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;

public class SipStateMachine extends AbstractSipStateMachine {

	State currentState;

	@Override
	public State getState() {
		return currentState;
	}

	@Override
	protected boolean computeNextStep(MessageType type, MessageDirection direction, Message message) {
		//...//
		requestMustBeSent(new SendRequestEvent(RequestVerb.BYE, (Response) message));
		//...//
		responseMustBeSent(new SendResponseEvent(ResponseCode.RINGING, (Request) message));
		//...//
		return false;
	}

/*	@Override
	protected void handleNewStepAfterRequestReceived(RequestVerb requestVerb, Request request, State newState) {
		//...//
		//FIXME we must check previous state indeed, otherwise no matter where I was or am now, I'll send a 180 if I get an INCOMING request.
		if (requestVerb == RequestVerb.INVITE && newState == State.INCOMING) {
			responseMustBeSent(new SendResponseEvent(ResponseCode.RINGING, request));
		}
		//...//
		if (requestVerb == RequestVerb.BYE && newState == State.READY) {
			responseMustBeSent(new SendResponseEvent(ResponseCode.BAD_REQUEST, request));
		}
	}

	@Override
	protected void handleNewStepAfterResponseReceived(int responseCode, Response response, State newState) {
		//...//
		boolean authIsRequired = responseCode == ResponseCode.PROXY_AUTHENTICATION_REQUIRED
				|| responseCode == ResponseCode.UNAUTHORIZED;
		boolean currentStateIsRegisteringOrCalling = (newState == State.REGISTERING || newState == State.CALLING);
		if (authIsRequired && currentStateIsRegisteringOrCalling) {
			requestMustBeSent(new SendRequestEvent(RequestVerb.INVITE, response));
		}
		//...//
	}*/

}
