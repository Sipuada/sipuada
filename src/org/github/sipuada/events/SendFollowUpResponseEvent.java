package org.github.sipuada.events;

import android.javax.sip.RequestEvent;

public class SendFollowUpResponseEvent {

	private int responseCode;
	private RequestEvent responseRequestEvent;
	
	public SendFollowUpResponseEvent(int code, RequestEvent requestEvent) {
		responseCode = code;
		responseRequestEvent = requestEvent;
	}

	public int getCode() {
		return responseCode;
	}

	public RequestEvent getRequestEvent() {
		return responseRequestEvent;
	}

}
