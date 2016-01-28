package org.github.sipuada;

import org.github.sipuada.requester.SipConnection;
import org.github.sipuada.requester.SipRequester;
import org.github.sipuada.state.SipStateMachine;

import com.google.common.eventbus.EventBus;

import android.javax.sip.SipFactory;

public class Sipuada {

	public static final String sipuadaInstanceName = "sipuada";
	
	private static EventBus eventBus;
	private SipFactory sipFactory;
	private SipStateMachine sipStateMachine;
	private SipConnection sipConnection;
	private SipRequester sipRequester;
	private SipReceiver sipReceiver;
	
	public Sipuada() throws Exception {
		eventBus = new EventBus();
		sipFactory = SipFactory.getInstance();
		sipStateMachine = new SipStateMachine(); 
		sipConnection = new SipConnection(sipuadaInstanceName);
		sipRequester = new SipRequester(sipFactory, sipStateMachine, sipConnection);
		sipReceiver = new SipReceiver(sipStateMachine);
	}
	
	public static EventBus getEventBus() {
		return eventBus;
	}

}
