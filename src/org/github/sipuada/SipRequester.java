package org.github.sipuada;

import org.github.sipuada.events.SendRequestEvent;
import org.github.sipuada.events.SendResponseEvent;
import org.github.sipuada.state.SipStateMachine;

import android.javax.sip.ClientTransaction;
import android.javax.sip.Dialog;
import android.javax.sip.ListeningPoint;
import android.javax.sip.ServerTransaction;
import android.javax.sip.SipProvider;
import android.javax.sip.SipStack;
import android.javax.sip.address.AddressFactory;
import android.javax.sip.header.CallIdHeader;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.message.MessageFactory;
import android.javax.sip.message.Request;

public class SipRequester {
	
	private static String TAG = "tSipRequester";
	private AddressFactory addressFactory;
	private MessageFactory messageFactory;
	private HeaderFactory headerFactory;
	private SipStack sipStack;
	
	private SipStateMachine stateMachine;
	private SipReceiver receiver;
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
    
    
    
	public SipRequester(SipStateMachine machine, String localIpAddress) {
		stateMachine = machine;
		receiver = new SipReceiver(machine);
		Sipuada.getEventBus().register(SendRequestEvent.class);
		Sipuada.getEventBus().register(SendResponseEvent.class);
		//TODO setup the Provider and Listening Point, as well as both Server and Client transactions.
		//TODO also create the current Call Id Header and current Dialog here?
		//TODO register SipReceiver as a SipListener in the SipProvider: provider.addSipListener(receiver)
	}
	
	public Request getLastRequest() {
		return lastRequest;
	}

	private void sendRegister(SipRequestState state) {
		
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
