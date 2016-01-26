package org.github.sipuada.state;

import static org.junit.Assert.assertTrue;

import org.github.sipuada.Sipuada;
import org.github.sipuada.events.SendResponseEvent;
import org.github.sipuada.requester.SipRequestVerb;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestSipStateMachine {

	@Before
	public void setUp() {
		Sipuada.getEventBus().register(this);
	}
	
	@Test
	public void test() {
		SipStateMachine machine = new SipStateMachine();
		assertTrue(machine.getState() == State.IDLE);
		machine.requestHasBeenReceived(SipRequestVerb.INVITE, null);
		assertTrue(machine.getState() == State.READY);
		SipStateMachine machine2 = new SipStateMachine();
		assertTrue(machine2.getState() == State.IDLE);
		machine2.requestHasBeenReceived(SipRequestVerb.INVITE, null);
	}
	
	public void onEvent(SendResponseEvent event) {
		
	}
	
	@After
	public void tearDown() {
		Sipuada.getEventBus().unregister(this);
	}

}
