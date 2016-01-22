package org.github.sipuada;

public interface SipStateMachine {
	
	public State getState();

	public void requestSent(RequestVerb request);
	public void responseSent(ResponseCode response);

	public void requestReceived(RequestVerb request);
	public void responseReceived(ResponseCode response);

}
