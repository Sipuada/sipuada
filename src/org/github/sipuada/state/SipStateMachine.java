package org.github.sipuada.state;

import static org.github.sipuada.state.SipStateMachineBehavior.computeNextStepAfterRequest;
import static org.github.sipuada.state.SipStateMachineBehavior.computeNextStepAfterResponse;
import static org.github.sipuada.state.SipStateMachineBehavior.during;

import org.github.sipuada.events.SendRequestEvent;
import org.github.sipuada.events.SendResponseEvent;
import org.github.sipuada.requester.SipRequestVerb;
import org.github.sipuada.requester.SipResponseCode;
import org.github.sipuada.state.SipStateMachineBehavior.Step;

import android.javax.sip.message.Request;
import android.javax.sip.message.Response;

public class SipStateMachine extends AbstractSipStateMachine {

	private State currentState;

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
		during(State.IDLE).whenRequest(MessageDirection.OUTGOING).with(SipRequestVerb.REGISTER)
			.goTo(State.REGISTERING).andAllowAction();
		during(State.IDLE).whenRequest(MessageDirection.OUTGOING).with(SipRequestVerb.UNREGISTER)
			.goTo(State.IDLE).andDontAllowAction().thenSendResponse(SipResponseCode.BAD_REQUEST);
		during(State.READY).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.INVITE)
			.goTo(State.INCOMING).andAllowAction().thenSendResponse(SipResponseCode.RINGING);
		during(State.READY).whenRequest(MessageDirection.INCOMING).with(SipRequestVerb.BYE)
			.goTo(State.READY).andDontAllowAction().thenSendResponse(SipResponseCode.BAD_REQUEST);
		during(State.CALLING).whenResponse(MessageDirection.INCOMING).with(SipResponseCode.PROXY_AUTHENTICATION_REQUIRED)
			.goTo(State.CALLING).thenSendRequest(SipRequestVerb.INVITE);
		during(State.CALLING).whenResponse(MessageDirection.INCOMING).with(SipResponseCode.UNAUTHORIZED)
			.goTo(State.CALLING).thenSendRequest(SipRequestVerb.INVITE);
		during(State.CALLING).whenResponse(MessageDirection.INCOMING).with(SipResponseCode.RINGING)
			.goTo(State.RINGING).andAllowAction();
		during(State.CALLING).whenResponse(MessageDirection.INCOMING).with(SipResponseCode.OK)
			.goTo(State.ESTABLISHED).andAllowAction();
		during(State.RINGING).whenResponse(MessageDirection.INCOMING).with(SipResponseCode.DECLINE)
			.goTo(State.FINISHED).andAllowAction();
		during(State.RINGING).whenResponse(MessageDirection.INCOMING).with(SipResponseCode.OK)
			.goTo(State.ESTABLISHED).andAllowAction();
	}
	
	@Override
	public State getState() {
		return currentState;
	}

	@Override
	protected boolean handleRequestComputingNextStep(MessageDirection direction, SipRequestVerb requestVerb, Request request) {
		Step nextStep = computeNextStepAfterRequest(currentState, direction, requestVerb);
		currentState = nextStep.getNextState();
		if (nextStep.hasFollowUpResponse()) {
			responseMustBeSent(new SendResponseEvent(nextStep.getFollowUpResponseCode(), request));
		}
		return nextStep.actionIsAllowed();
	}
	
	@Override
	protected boolean handleResponseComputingNextStep(MessageDirection direction, int responseCode, Response response) {
		Step nextStep = computeNextStepAfterResponse(currentState, direction, responseCode);
		currentState = nextStep.getNextState();
		if (nextStep.hasFollowUpRequest()) {
			requestMustBeSent(new SendRequestEvent(nextStep.getFollowUpRequestVerb(), response));
		}
		return nextStep.actionIsAllowed();
	}
	
}
