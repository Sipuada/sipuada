package org.github.sipuada.state;

import org.github.sipuada.Sipuada;
import org.github.sipuada.events.SendRequestEvent;
import org.github.sipuada.events.SendResponseEvent;
import org.github.sipuada.events.StateChangedEvent;
import org.github.sipuada.requester.SipRequestVerb;

import android.javax.sip.message.Request;
import android.javax.sip.message.Response;

public abstract class AbstractSipStateMachine {
	
	public abstract State getState();

	public boolean canRequestBeSent(SipRequestVerb verb, Request request) {
		return handleRequestComputingNextStep(MessageDirection.OUTGOING, verb, request);
	}

	public boolean canResponseBeSent(int code, Response response) {
		return handleResponseComputingNextStep(MessageDirection.OUTGOING, code, response);
	}

	public boolean requestHasBeenReceived(SipRequestVerb verb, Request request) {
		return handleRequestComputingNextStep(MessageDirection.INCOMING, verb, request);
	}

	public boolean responseHasBeenReceived(int code, Response response) {
		return handleResponseComputingNextStep(MessageDirection.INCOMING, code, response);
	}

	protected enum MessageDirection {
		INCOMING, OUTGOING
	}

	protected abstract boolean handleRequestComputingNextStep(MessageDirection direction, SipRequestVerb verb, Request request);
	
	protected abstract boolean handleResponseComputingNextStep(MessageDirection direction, int code, Response response);
	
	protected void stateHasChanged(State oldState, State newState) {
		Sipuada.getEventBus().post(new StateChangedEvent(oldState, newState));
	}
	
	protected void requestMustBeSent(SendRequestEvent event) {
		Sipuada.getEventBus().post(event);
	}

	protected void responseMustBeSent(SendResponseEvent event) {
		Sipuada.getEventBus().post(event);
	}

}
