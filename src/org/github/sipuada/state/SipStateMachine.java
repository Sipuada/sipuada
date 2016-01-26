package org.github.sipuada.state;

import org.github.sipuada.events.SendRequestEvent;
import org.github.sipuada.events.SendResponseEvent;
import org.github.sipuada.requester.SipRequestVerb;
import org.github.sipuada.requester.SipResponseCode;
import org.github.sipuada.state.SipStateMachineBehavior.During;
import org.github.sipuada.state.SipStateMachineBehavior.Step;

import android.javax.sip.message.Request;
import android.javax.sip.message.Response;

public class SipStateMachine extends AbstractSipStateMachine {

	private State currentState;
	private SipStateMachineBehavior currentBehavior;

	/*
	 * Requests:
	 * curr_state -> direction -> verb ->  new_state -> allowAction -> ReturnRequest -> ReturnResponse
	 * 	   IDLE   -> OUTGOING  -> REGISTER -> REGISTERING -> True -> True (verb REGISTER) -> False
	 * 	   IDLE   -> OUTGOING  -> UNREGISTER -> IDLE -> False -> False -> True (code 4XX - ERROR)
	 * 	   IDLE   -> OUTGOING  -> INVITE -> IDLE -> False -> False -> True (code 4XX - ERROR)
	 * 	   IDLE   -> INCOMING  -> INVITE -> INCOMING -> True -> False -> True (code 180 - RINGING)
	 * 	   IDLE   -> OUTGOING  -> MESSAGE -> IDLE -> False -> False -> True (code 4XX - ERROR)
	 * 	   IDLE   -> INCOMING  -> MESSAGE -> READY -> True -> False -> True (code 200 - OK)
	 * 	   IDLE   -> OUTGOING  ->   BYE   -> INCOMING -> False -> False -> True (code 4XX - ERROR)
	 * 	   IDLE   -> INCOMING  ->   BYE   -> INCOMING -> False -> False -> True (code 4XX - ERROR)
	 * 	   IDLE   -> OUTGOING  ->   CANCEL   -> INCOMING -> False -> False -> True (code 4XX - ERROR)
	 * 	   IDLE   -> INCOMING  ->   CANCEL   -> INCOMING -> False -> False -> True (code 4XX - ERROR)
	 * 	   IDLE   -> OUTGOING  ->   INFO   -> INCOMING -> False -> False -> True (code 4XX - ERROR)
	 * 	   IDLE   -> INCOMING  ->   INFO   -> INCOMING -> False -> False -> True (code 4XX - ERROR)
	 *     ...
	 *     INCOMING -> OUTGOING -> 200 OK -> ESTABLISHED -> True -> False -> False
	 *     INCOMING -> OUTGOING -> 603 DECLINE -> FINISHED -> True -> False -> False
	 */
	
	public SipStateMachine() {
		currentState = State.IDLE;
		currentBehavior = new SipStateMachineBehavior();
		whileIn(State.ESTABLISHED).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.INVITE)
			.goTo(State.ESTABLISHED).thenSendResponse(SipResponseCode.BUSY_HERE);
	}
	
	@Override
	public State getState() {
		return currentState;
	}

	@Override
	protected boolean handleRequestComputingNextStep(MessageDirection direction, SipRequestVerb requestVerb, Request request) {
		Step nextStep = currentBehavior.computeNextStepAfterRequest(currentState, direction, requestVerb);
		if (nextStep == null) {
			return false;
		}
		currentState = nextStep.getNextState();
		if (nextStep.hasFollowUpResponse()) {
			responseMustBeSent(new SendResponseEvent(nextStep.getFollowUpResponseCode(), request));
		}
		return nextStep.actionIsAllowed();
	}
	
	@Override
	protected boolean handleResponseComputingNextStep(MessageDirection direction, int responseCode, Response response) {
		Step nextStep = currentBehavior.computeNextStepAfterResponse(currentState, direction, responseCode);
		if (nextStep == null) {
			return false;
		}
		currentState = nextStep.getNextState();
		if (nextStep.hasFollowUpRequest()) {
			requestMustBeSent(new SendRequestEvent(nextStep.getFollowUpRequestVerb(), response));
		}
		return nextStep.actionIsAllowed();
	}
	
	public During whileIn(State currentState) {
		return currentBehavior.during(currentState);
	}
	
}
