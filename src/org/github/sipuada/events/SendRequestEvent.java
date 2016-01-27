package org.github.sipuada.events;

import java.util.List;

import org.github.sipuada.requester.SipRequestVerb;

import android.javax.sip.message.Response;

public class SendRequestEvent {

	private List<SipRequestVerb> requestVerbs;
	private Response requestResponse;

	public SendRequestEvent(List<SipRequestVerb> verbs, Response response) {
		requestVerbs = verbs;
		requestResponse = response;
	}

	public List<SipRequestVerb> getVerbs() {
		return requestVerbs;
	}

	public Response getResponse() {
		return requestResponse;
	}

}
