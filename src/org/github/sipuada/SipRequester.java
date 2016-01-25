package org.github.sipuada;

import org.github.sipuada.events.SendRequestEvent;
import org.github.sipuada.events.SendResponseEvent;

import android.javax.sip.ClientTransaction;
import android.javax.sip.Dialog;
import android.javax.sip.ListeningPoint;
import android.javax.sip.ServerTransaction;
import android.javax.sip.SipProvider;
import android.javax.sip.header.CallIdHeader;
import android.javax.sip.message.Request;

public class SipRequester {
	
	private String localIpAddress;
	private int localSipPort;
	private SipProvider provider;
	private ListeningPoint listeningPoint;
	private ServerTransaction serverTransaction;
	private ClientTransaction clientTransaction;
    private CallIdHeader currentCallId;
    private Dialog currentDialog;
    private long callSequence = 1L;
    private Request lastRequest;
    
	public SipRequester(String localIpAddress) {
		Sipuada.getEventBus().register(SendRequestEvent.class);
		Sipuada.getEventBus().register(SendResponseEvent.class);
		//TODO setup the Provider and Listening Point, as well as both Server and Client transactions.
		//TODO also create the current Call Id Header and current Dialog here?
	}
	
	public Request getLastRequest() {
		return lastRequest;
	}

	private void sendRegister() {
		
	}
	
	//...//
	
	private void sendBye() {
		
	}
	
	private void sendRequest(RequestVerb verb) {
		
	}

	public void onEvent(SendRequestEvent event) {
		//TODO send a new request of type event.getVerb() using last response in event.getResponse().
	}

	public void onEvent(SendResponseEvent event) {
		//TODO send a new response with code event.getCode() to answer request in event.getRequest().
		
	}

}
