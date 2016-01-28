package org.github.sipuada.events;

import java.util.EventObject;
import java.util.List;

import org.github.sipuada.requester.SipRequestVerb;

public class SendFollowUpRequestsEvent {

	private List<SipRequestVerb> requestVerbs;
	private EventObject requestResponseEvent;

	public SendFollowUpRequestsEvent(List<SipRequestVerb> verbs, EventObject responseEvent) {
		requestVerbs = verbs;
		requestResponseEvent = responseEvent;
	}

	public List<SipRequestVerb> getVerbs() {
		return requestVerbs;
	}

	public EventObject getResponseEvent() {
		return requestResponseEvent;
	}

}
