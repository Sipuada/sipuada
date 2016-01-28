package org.github.sipuada.state;

import java.util.EventObject;

import org.github.sipuada.Sipuada;
import org.github.sipuada.events.SendFollowUpRequestsEvent;
import org.github.sipuada.events.SendFollowUpResponseEvent;
import org.github.sipuada.events.StateChangedEvent;
import org.github.sipuada.requester.SipRequestVerb;

import android.javax.sip.RequestEvent;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;

public abstract class AbstractSipStateMachine {
	
	public abstract State getState();

	public boolean canRequestBeSent(SipRequestVerb verb, Request request) {
		return handleOutgoingRequestComputingNextStep(verb, request);
	}

	public boolean canResponseBeSent(int code, Response response) {
		return handleOutgoingResponseComputingNextStep(code, response);
	}

	public void requestHasBeenReceived(SipRequestVerb verb, RequestEvent requestEvent) {
		handleIncomingRequestComputingNextStep(verb, requestEvent);
	}

	public boolean responseHasBeenReceived(int code, EventObject responseEvent) {
		return handleIncomingResponseComputingNextStep(code, responseEvent);
	}

	protected enum MessageDirection {
		INCOMING, OUTGOING
	}

	protected abstract boolean handleOutgoingRequestComputingNextStep(SipRequestVerb verb, Request request);
	
	protected abstract boolean handleIncomingRequestComputingNextStep(SipRequestVerb verb, RequestEvent requestEvent);
	
	protected abstract boolean handleOutgoingResponseComputingNextStep(int code, Response response);

	protected abstract boolean handleIncomingResponseComputingNextStep(int code, EventObject responseEvent);
	
	protected void stateHasChanged(State oldState, State newState) {
		Sipuada.getEventBus().post(new StateChangedEvent(oldState, newState));
	}
	
	protected void requestsMustBeSent(SendFollowUpRequestsEvent event) {
		Sipuada.getEventBus().post(event);
	}

	protected void responseMustBeSent(SendFollowUpResponseEvent event) {
		Sipuada.getEventBus().post(event);
	}

}
