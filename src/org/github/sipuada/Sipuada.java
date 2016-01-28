package org.github.sipuada;

import org.github.sipuada.requester.SipConnection;
import org.github.sipuada.requester.SipRequester;
import org.github.sipuada.state.SipStateMachine;

import com.google.common.eventbus.EventBus;

import android.javax.sip.SipFactory;

public class Sipuada {

	
	public static final String sipuadaInstanceName = "sipuada";
	public static final String sipuadaInstanceLocalIpAddress = "192.168.1.10";
	public static final int sipuadaInstanceLocalSipPort = 5060;
	public static final String sipuadaInstanceTransport = "TCP";
	
	private static EventBus eventBus;
	private SipFactory sipFactory;
	private SipStateMachine sipStateMachine;
	private SipRequester sipRequester;
	private SipReceiver sipReceiver;
	private SipConnection sipConnection;
	
	public Sipuada() throws Exception {
		eventBus = new EventBus();
		sipFactory = SipFactory.getInstance();
		sipStateMachine = new SipStateMachine();
		sipReceiver = new SipReceiver(sipStateMachine);
		sipConnection = new SipConnection(sipuadaInstanceName, sipuadaInstanceLocalIpAddress, sipuadaInstanceLocalSipPort, sipuadaInstanceTransport, sipReceiver);
		sipRequester = new SipRequester(sipFactory, sipStateMachine, sipConnection);
		
	}
	
	public static EventBus getEventBus() {
		return eventBus;
	}
	
}
