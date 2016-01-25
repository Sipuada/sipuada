package org.github.sipuada.state;

import org.github.sipuada.RequestVerb;
import org.github.sipuada.Sipuada;
import org.github.sipuada.State;
import org.github.sipuada.events.SendRequestEvent;
import org.github.sipuada.events.SendResponseEvent;
import org.github.sipuada.events.StateChangedEvent;

import android.javax.sip.message.Message;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;

public abstract class AbstractSipStateMachine {
	
	public abstract State getState();

	public boolean canRequestBeSent(RequestVerb requestVerb, Request request) {
		return computeNextStep(MessageType.REQUEST, MessageDirection.OUTGOING, request);
	}

	public boolean canResponseBeSent(int responseCode, Response response) {
		return computeNextStep(MessageType.RESPONSE, MessageDirection.OUTGOING, response);
	}

	public boolean requestHasBeenReceived(RequestVerb requestVerb, Request request) {
		return computeNextStep(MessageType.REQUEST, MessageDirection.INCOMING, request);
	}

	public boolean responseHasBeenReceived(int responseCode, Response response) {
		return computeNextStep(MessageType.RESPONSE, MessageDirection.INCOMING, response);
	}

	protected enum MessageType {
		REQUEST, RESPONSE
	}

	protected enum MessageDirection {
		INCOMING, OUTGOING
	}

	protected abstract boolean computeNextStep(MessageType type, MessageDirection direction, Message message);
	
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
